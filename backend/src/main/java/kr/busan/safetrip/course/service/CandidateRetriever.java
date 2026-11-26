package kr.busan.safetrip.course.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.ai.EmbeddingService;
import kr.busan.safetrip.common.domain.Place;
import kr.busan.safetrip.common.domain.PlaceRepository;
import kr.busan.safetrip.course.repository.CoursePlaceRepository;

/**
 * [코스 담당] COMP-C04 CandidateRetriever
 * 요구사항: FR-C004, C005, C014, AI-REQ-C05, NFR-C002
 * 상태: 완료
 * 수정 규칙: 코스 담당만 수정. 사진·긴급 담당은 수정 금지(필요하면 코스 담당에게 요청).
 *
 * SQL 필터(권역·방문 요일 휴무)와 실내 조건, 사후면세점 조정 → 취향 문장과 뜻이 가까운 순으로 상위 topK. 요청 유형은 유형마다 wantedPerCategory 곳을 꼭 넣는다.
 * 임베딩이 실패하면 contentId 순으로 대체한다(NFR-C002). 고정 출발지는 항상 후보에 넣는다.
 */
@Component
public class CandidateRetriever {
    private static final Logger log = LoggerFactory.getLogger(CandidateRetriever.class);

    private final CoursePlaceRepository coursePlaces;
    private final PlaceRepository places;
    private final CourseSettings settings;
    private final EmbeddingService embeddings;
    private final Pattern noise;

    public CandidateRetriever(CoursePlaceRepository coursePlaces, PlaceRepository places, CourseSettings settings, EmbeddingService embeddings) {
        this.coursePlaces = coursePlaces;
        this.places = places;
        this.settings = settings;
        this.embeddings = embeddings;
        this.noise = Pattern.compile(settings.intent().preferenceNoise(), Pattern.CASE_INSENSITIVE);
    }

    public List<Place> retrieve(IntentParser.Intent intent) {
        String addr = intent.area() == null ? null : settings.intent().areaAddress().get(intent.area());
        List<String> wanted = intent.categories() == null ? List.of() : intent.categories();
        List<Place> ranked = new ArrayList<>(pick(demote(intent, indoor(intent, coursePlaces.candidates(addr, intent.visitDate().getDayOfWeek().getValue(), vector(intent)))), wanted,
                settings.intent().topK(), settings.intent().wantedPerCategory()));
        if (intent.startPlaceId() != null && ranked.stream().noneMatch(p -> p.getContentId().equals(intent.startPlaceId()))) {
            places.findByContentId(intent.startPlaceId()).ifPresent(p -> ranked.add(0, p));
        }
        if (log.isDebugEnabled()) log.debug("course.retrieve top={}", ranked.stream().map(Place::getContentId).toList()); // #77 겹침률 측정용(장소 ID만)
        return ranked;
    }

    /** FR-C002 실내 조건: 분류 코드가 실내 목록에 있는 곳만. 코드가 없으면 실내인지 모르므로 뺀다 */
    private List<Place> indoor(IntentParser.Intent intent, List<Place> all) {
        if (!intent.indoorOnly()) return all;
        List<String> codes = settings.intent().indoorCodes();
        return all.stream().filter(p -> p.getLclsCode() != null && codes.stream().anyMatch(p.getLclsCode()::startsWith)).toList();
    }

    /** 사후면세점(#151): 쇼핑을 요청하지 않았으면 빼고, 요청했으면 백화점·시장·기념품점 뒤로 보낸다. 장소 데이터는 그대로다 */
    private List<Place> demote(IntentParser.Intent intent, List<Place> all) {
        CourseSettings.Demoted d = settings.intent().demoted();
        String text = intent.preference() == null ? "" : intent.preference().toLowerCase(Locale.ROOT);
        if (d.keywords().stream().anyMatch(text::contains)) return all;
        Predicate<Place> low = p -> p.getLclsCode() != null && d.codes().stream().anyMatch(p.getLclsCode()::startsWith);
        List<Place> r = new ArrayList<>(all.stream().filter(low.negate()).toList());
        if (intent.categories() != null && intent.categories().contains(d.category())) all.stream().filter(low).forEach(r::add);
        return r;
    }

    /** 순위 목록에서 topK 곳. 요청 유형을 먼저 채우고 나머지를 순위대로 채운다. 결과는 원래 순위 순 */
    static List<Place> pick(List<Place> ranked, List<String> wanted, int topK, int perCategory) {
        Set<Place> chosen = new LinkedHashSet<>();
        for (String c : wanted) ranked.stream().filter(p -> c.equals(p.getCategory())).limit(perCategory).forEach(chosen::add);
        for (Place p : ranked) {
            if (chosen.size() >= topK) break;
            chosen.add(p);
        }
        return ranked.stream().filter(chosen::contains).limit(topK).toList();
    }

    /** 시간·지역 말은 장소의 뜻과 상관없어 검색을 흐린다(intent.json preferenceNoise) */
    String clean(String preference) {
        return preference == null ? "" : noise.matcher(preference).replaceAll(" ").replaceAll("[\\s,、]+", " ").strip();
    }

    /** ponytail: 조건을 풀 때마다 다시 부른다(로컬 bge-m3 수십 ms). 느려지면 요청당 한 번만 계산해 넘긴다 */
    private String vector(IntentParser.Intent intent) {
        String text = clean(intent.preference());
        if (text.isEmpty()) return null;
        try {
            return EmbeddingService.literal(embeddings.embed(text));
        } catch (RuntimeException e) {
            log.warn("course.retrieve 임베딩 실패 → 순위 대체: {}", e.getClass().getSimpleName());
            return null;
        }
    }
}
