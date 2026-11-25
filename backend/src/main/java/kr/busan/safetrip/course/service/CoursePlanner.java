package kr.busan.safetrip.course.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.domain.Place;
import kr.busan.safetrip.common.util.GeoUtil;
import kr.busan.safetrip.course.dto.CourseDtos;

/**
 * [코스 담당] COMP-C05 CoursePlanner
 * 요구사항: FR-C006, C007, C009, NFR-C005, C006
 * 상태: 완료(운영시간 판정은 공용 HoursParser 완료 후 추가)
 * 수정 규칙: 코스 담당만 수정. 사진·긴급 담당은 수정 금지(필요하면 코스 담당에게 요청).
 *
 * 제약 검사·탐색·점수. 같은 입력이면 항상 같은 코스(NFR-C006) — 난수·현재시각·DB 를 직접 쓰지 말 것.
 * 탐색: 1번(고정 장소 또는 후보 1위)에서 시작해, 시간 안에 들어가는 후보 중 점수가 가장 높은 곳을 차례로 붙인다(탐욕법).
 * 점수 = 후보 순위 점수 - 거리 벌점 + 아직 못 채운 요청 유형 보너스. 가중치·체류시간은 course/planner.json(NFR-C005).
 */
@Component
public class CoursePlanner {
    /** course/planner.json 모양 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Config(int minStops, int maxStops, Map<String, Integer> stayMin, int defaultStayMin,
                         int walkMaxM, double walkMPerMin, double taxiMPerMin, int taxiBaseMin, double detour,
                         int maxNodes, double wStop, double wRank, double wDistPerKm, double wWantedCategory) {}

    private static final int DAY = 24 * 60;
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private final Config cfg;

    public CoursePlanner() {
        this(load("/course/planner.json"));
    }

    CoursePlanner(Config cfg) {
        this.cfg = cfg;
    }

    static Config load(String path) {
        try (InputStream in = CoursePlanner.class.getResourceAsStream(path)) {
            return new ObjectMapper().readValue(in, Config.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * DFS 로 3~5곳의 순서를 고른다(FR-C006, #78). 첫 장소는 순위가 높은 것부터 시도해 코스가 되는 첫 번째를 쓴다.
     * 탐색 횟수 한도(maxNodes)는 시간이 아니라 횟수라 결과가 달라지지 않는다. 난수·현재 시각을 쓰지 않으므로 같은 입력이면 같은 코스다(NFR-C006).
     *
     * @param candidates 좋은 순서로 정렬된 후보(CandidateRetriever 순위)
     * @param open       contentId → 방문일에 여는 시간들(분, [여는, 닫는]). PARSED 인데 없으면 휴무, PARSED 가 아니면 불확실이라 막지 않는다(FR-C009)
     * @return stops 가 비어 있으면 코스 없음(NO_COURSE) — 최소 장소 수 미달이거나 요청 유형을 다 못 채운 경우
     */
    public List<CourseDtos.Stop> plan(IntentParser.Intent intent, List<Place> candidates, Map<String, List<int[]>> open) {
        int start = intent.startTime().toSecondOfDay() / 60;
        int end = start + intent.durationMin();
        if (end >= DAY) return List.of(); // ponytail: 자정 넘는 코스는 미지원, 필요하면 LocalDateTime 으로

        List<String> wanted = intent.categories() == null ? List.of() : intent.categories().stream().distinct().toList();
        Search s = new Search(candidates, open, wanted, end);
        int all = (1 << wanted.size()) - 1;
        for (int i = 0; i < candidates.size(); i++) {
            if (intent.startPlaceId() != null && !intent.startPlaceId().equals(candidates.get(i).getContentId())) continue;
            int leave = start + s.stay[i];
            if (leave > end || !s.isOpen(i, start, leave)) continue;
            s.path[0] = i;
            s.used[i] = true;
            s.dfs(1, leave, all & ~s.wantedBit[i], 0);
            s.used[i] = false;
            if (s.best != null) return stops(s, start, intent.startPlaceId() != null);
        }
        return List.of();
    }

    /** 한 번의 탐색 상태. 후보가 20곳 안팎이라 가지치기한 전수 탐색으로 충분하다 */
    private final class Search {
        final List<Place> ps;
        final int end;
        final int[][][] spans; // 장소별 여는 시간. null = 불확실(막지 않음)
        final int[] lastArrive; // 이보다 늦게 도착하면 닫기 전에 다 못 머문다
        int nodes;
        final int[] stay, wantedBit, path = new int[cfg.maxStops()];
        final boolean[] used;
        final CourseDtos.Move[][] move;
        final double[][] gain; // 이동 거리 감점까지 넣은 한 칸 점수(요청 유형 점수 제외)
        final double maxGain = cfg.wStop() + cfg.wRank() + cfg.wWantedCategory();
        int[] best;
        double bestScore = Double.NEGATIVE_INFINITY;

        Search(List<Place> ps, Map<String, List<int[]>> open, List<String> wanted, int end) {
            this.ps = ps;
            this.end = end;
            int n = ps.size();
            stay = new int[n];
            wantedBit = new int[n];
            used = new boolean[n];
            spans = new int[n][][];
            lastArrive = new int[n];
            move = new CourseDtos.Move[n][n];
            gain = new double[n][n];
            for (int i = 0; i < n; i++) {
                Place a = ps.get(i);
                stay[i] = stay(a);
                int w = wanted.indexOf(a.getCategory());
                wantedBit[i] = w < 0 ? 0 : 1 << w;
                spans[i] = "PARSED".equals(a.getHoursStatus()) ? open.getOrDefault(a.getContentId(), List.of()).toArray(int[][]::new) : null;
                lastArrive[i] = spans[i] == null ? end : Integer.MIN_VALUE;
                if (spans[i] != null) for (int[] h : spans[i]) lastArrive[i] = Math.max(lastArrive[i], h[1] - stay[i]);
                lastArrive[i] = Math.min(lastArrive[i], end - stay[i]);
                for (int j = 0; j < n; j++) {
                    Place b = ps.get(j);
                    move[i][j] = move(a, b);
                    gain[i][j] = cfg.wStop() + cfg.wRank() * (1.0 - (double) j / n)
                            - cfg.wDistPerKm() * GeoUtil.distanceM(a.getLat(), a.getLng(), b.getLat(), b.getLng()) / 1000.0;
                }
            }
        }

        boolean isOpen(int i, int arrive, int depart) {
            if (spans[i] == null) return true;
            for (int[] h : spans[i]) if (h[0] <= arrive && depart <= h[1]) return true;
            return false;
        }

        /** 남은 요청 유형을 지금부터 채울 수 있는가 */
        boolean reachable(int now, int wantedLeft) {
            int can = 0;
            for (int i = 0; i < ps.size(); i++) if (!used[i] && lastArrive[i] >= now) can |= wantedBit[i];
            return (wantedLeft & ~can) == 0;
        }

        /** 점수가 같으면 먼저 찾은 쪽(순위가 높은 쪽)을 남긴다 */
        void dfs(int size, int now, int wantedLeft, double score) {
            if (size >= cfg.minStops() && wantedLeft == 0 && score > bestScore) {
                bestScore = score;
                best = Arrays.copyOf(path, size);
            }
            int room = cfg.maxStops() - size;
            if (room == 0 || ++nodes > cfg.maxNodes() || Integer.bitCount(wantedLeft) > room || score + room * maxGain <= bestScore) return;
            if (wantedLeft != 0 && !reachable(now, wantedLeft)) return;
            int prev = path[size - 1];
            for (int i = 0; i < ps.size(); i++) {
                if (used[i]) continue;
                int arrive = now + move[prev][i].minutes();
                int depart = arrive + stay[i];
                if (depart > end || !isOpen(i, arrive, depart)) continue;
                path[size] = i;
                used[i] = true;
                dfs(size + 1, depart, wantedLeft & ~wantedBit[i], score + gain[prev][i] + ((wantedLeft & wantedBit[i]) != 0 ? cfg.wWantedCategory() : 0));
                used[i] = false;
            }
        }
    }

    private List<CourseDtos.Stop> stops(Search s, int start, boolean fixedFirst) {
        List<CourseDtos.Stop> out = new ArrayList<>();
        int now = start;
        for (int k = 0; k < s.best.length; k++) {
            CourseDtos.Move mv = k == 0 ? null : s.move[s.best[k - 1]][s.best[k]];
            int arrive = mv == null ? now : now + mv.minutes();
            now = arrive + s.stay[s.best[k]];
            out.add(stop(k + 1, s.ps.get(s.best[k]), time(arrive), time(now), mv, k == 0 && fixedFirst));
        }
        return out;
    }

    private static LocalTime time(int minutes) {
        return LocalTime.ofSecondOfDay(minutes * 60L);
    }

    private int stay(Place p) {
        return cfg.stayMin().getOrDefault(p.getCategory(), cfg.defaultStayMin());
    }

    /** B2 이동시간 추정: 직선거리 × 우회계수. walkMaxM 이하는 도보, 넘으면 택시 */
    CourseDtos.Move move(Place a, Place b) {
        double m = GeoUtil.distanceM(a.getLat(), a.getLng(), b.getLat(), b.getLng()) * cfg.detour();
        if (m <= cfg.walkMaxM()) return new CourseDtos.Move("WALK", (int) Math.ceil(m / cfg.walkMPerMin()));
        return new CourseDtos.Move("TAXI", cfg.taxiBaseMin() + (int) Math.ceil(m / cfg.taxiMPerMin()));
    }

    private static CourseDtos.Stop stop(int order, Place p, LocalTime arrive, LocalTime depart, CourseDtos.Move mv, boolean fixed) {
        boolean hoursUncertain = !"PARSED".equals(p.getHoursStatus());
        return new CourseDtos.Stop(order, p.getContentId(), p.getName(), p.getCategory(), p.getLat(), p.getLng(),
                arrive.format(HM), depart.format(HM), mv, hoursUncertain, fixed, false);
    }
}
