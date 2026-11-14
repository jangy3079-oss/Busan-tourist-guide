package kr.busan.safetrip.course.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
                         double wRank, double wDistPerKm, double wWantedCategory) {}

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
     * @param candidates 좋은 순서로 정렬된 후보(CandidateRetriever 순위). 같은 목록이면 같은 결과.
     * @return stops 가 비어 있으면 코스 없음(NO_COURSE) — 최소 장소 수 미달이거나 요청 유형을 다 못 채운 경우
     */
    public List<CourseDtos.Stop> plan(IntentParser.Intent intent, List<Place> candidates) {
        LocalTime end = intent.startTime().plusMinutes(intent.durationMin());
        boolean crossesMidnight = end.isBefore(intent.startTime());
        if (crossesMidnight) return List.of(); // ponytail: 자정 넘는 코스는 미지원, 필요하면 LocalDateTime 으로

        List<Place> pool = new ArrayList<>(candidates);
        Set<String> wanted = new HashSet<>(intent.categories() == null ? List.of() : intent.categories());
        List<CourseDtos.Stop> stops = new ArrayList<>();

        Place first = intent.startPlaceId() == null ? (pool.isEmpty() ? null : pool.get(0))
                : pool.stream().filter(p -> p.getContentId().equals(intent.startPlaceId())).findFirst().orElse(null);
        if (first == null) return List.of();
        LocalTime t = intent.startTime();
        LocalTime departFirst = t.plusMinutes(stay(first));
        if (departFirst.isAfter(end)) return List.of();
        stops.add(stop(1, first, t, departFirst, null, intent.startPlaceId() != null));
        wanted.remove(first.getCategory());
        pool.remove(first);
        Place prev = first;
        t = departFirst;

        while (stops.size() < cfg.maxStops()) {
            Place best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            CourseDtos.Move bestMove = null;
            for (int i = 0; i < pool.size(); i++) {
                Place p = pool.get(i);
                CourseDtos.Move mv = move(prev, p);
                if (t.plusMinutes(mv.minutes() + stay(p)).isAfter(end)) continue;
                double score = cfg.wRank() * (1.0 - (double) i / pool.size())
                        - cfg.wDistPerKm() * GeoUtil.distanceM(prev.getLat(), prev.getLng(), p.getLat(), p.getLng()) / 1000.0
                        + (wanted.contains(p.getCategory()) ? cfg.wWantedCategory() : 0);
                // 동점이면 contentId 사전순(결정성)
                if (score > bestScore || (score == bestScore && p.getContentId().compareTo(best.getContentId()) < 0)) {
                    best = p;
                    bestScore = score;
                    bestMove = mv;
                }
            }
            if (best == null) break;
            LocalTime arrive = t.plusMinutes(bestMove.minutes());
            LocalTime depart = arrive.plusMinutes(stay(best));
            stops.add(stop(stops.size() + 1, best, arrive, depart, bestMove, false));
            wanted.remove(best.getCategory());
            pool.remove(best);
            prev = best;
            t = depart;
        }
        return stops.size() < cfg.minStops() || !wanted.isEmpty() ? List.of() : stops;
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
        // ponytail: 운영시간 판정 전이라 원문이 없으면 '방문 전 확인'만 표시. HoursParser(공용) 완료 후 영업 중 필터 추가
        boolean hoursUncertain = p.getHoursText() == null || p.getHoursText().isBlank();
        return new CourseDtos.Stop(order, p.getContentId(), p.getName(), p.getCategory(), p.getLat(), p.getLng(),
                arrive.format(HM), depart.format(HM), mv, hoursUncertain, fixed, false);
    }
}
