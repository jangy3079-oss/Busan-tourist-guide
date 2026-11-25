package kr.busan.safetrip.course.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.ai.AIService;
import kr.busan.safetrip.common.ai.AiRequest;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.common.domain.Place;
import kr.busan.safetrip.common.domain.PlaceRepository;
import kr.busan.safetrip.course.dto.CourseDtos;
import kr.busan.safetrip.course.repository.CoursePlaceRepository;

/**
 * [코스 담당] COMP-C06 NarrationService
 * 요구사항: FR-C010, AI-REQ-C01, AI-REQ-C02, NFR-C002
 * 상태: 완료
 * 수정 규칙: 코스 담당만 수정. 사진·긴급 담당은 수정 금지(필요하면 코스 담당에게 요청).
 *
 * 코스 제목·장소별 설명(60자) 생성 -> OutputValidator 검증 -> 실패 시 템플릿 대체(template=true).
 * LLM 에는 코스 장소의 TourAPI 원문만 넣는다(사용자 문장 없음).
 */
@Component
public class NarrationService {
    private static final Logger log = LoggerFactory.getLogger(NarrationService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    /** 프롬프트에 넣는 개요 최대 글자 수(토큰 절약) */
    private static final int OVERVIEW_CHARS = 300;
    private static final int SCAN_LIMIT = 5000;

    private final AIService ai;
    private final PlaceRepository places;
    private final CoursePlaceRepository coursePlaces;
    private final OutputValidator validator;
    private final CourseSettings settings;

    public NarrationService(AIService ai, PlaceRepository places, CoursePlaceRepository coursePlaces,
                            OutputValidator validator, CourseSettings settings) {
        this.ai = ai;
        this.places = places;
        this.coursePlaces = coursePlaces;
        this.validator = validator;
        this.settings = settings;
    }

    public CourseDtos.NarrationResponse narrate(String courseId, List<CourseDtos.Stop> stops, Lang lang) {
        List<Place> grounding = new ArrayList<>();
        for (CourseDtos.Stop s : stops) places.findByContentId(s.contentId()).ifPresent(grounding::add);
        try {
            CourseDtos.NarrationResponse r = fromLlm(courseId, stops, grounding, lang);
            if (r != null) return r;
            log.warn("course.narration 검증 실패 → 템플릿");
        } catch (RuntimeException e) {
            log.warn("course.narration LLM 실패 → 템플릿: {}", e.getClass().getSimpleName());
        }
        return template(courseId, stops, lang);
    }

    /** 검증을 통과하면 응답, 아니면 null. ponytail: 검증 실패 재시도 없음(NFR-C001 10초 안에 두려고). 필요하면 1회 재호출 */
    private CourseDtos.NarrationResponse fromLlm(String courseId, List<CourseDtos.Stop> stops, List<Place> grounding, Lang lang) {
        int itemMax = settings.intent().narrationMaxChars();
        int titleMax = settings.intent().titleMaxChars();
        // LLM 은 글자 수를 조금씩 넘긴다. 줄인 값을 알려 주고 검증은 원래 한도로 한다
        int titleAsk = (int) (titleMax * settings.intent().promptLengthRatio());
        int itemAsk = (int) (itemMax * settings.intent().promptLengthRatio());
        String prompt = settings.narrationPrompt().replace("{lang}", languageName(lang))
                .replace("{titleMax}", String.valueOf(titleAsk)).replace("{itemMax}", String.valueOf(itemAsk));
        StringBuilder data = new StringBuilder("<places>\n");
        for (Place p : grounding) {
            String ov = p.getOverview() == null ? "" : p.getOverview();
            data.append("contentId=").append(p.getContentId()).append(" | name=").append(p.getName())
                    .append(" | category=").append(p.getCategory())
                    .append(" | overview=").append(ov, 0, Math.min(ov.length(), OVERVIEW_CHARS)).append('\n');
        }
        data.append("</places>\n").append(settings.msg(lang, "narration.reminder", titleAsk, itemAsk)); // 원문이 한국어라 끝에서 다시 말하지 않으면 한국어로 답한다(#141)
        String raw = ai.complete(new AiRequest("course.narration", prompt, data.toString(), true, Duration.ofSeconds(8)));

        JsonNode n;
        try {
            n = JSON.readTree(raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1));
        } catch (Exception e) {
            return null;
        }
        Set<String> courseIds = stops.stream().map(CourseDtos.Stop::contentId).collect(Collectors.toSet());
        Set<String> others = coursePlaces.find(null, null, SCAN_LIMIT).stream()
                .filter(p -> !courseIds.contains(p.getContentId())).map(Place::getName).collect(Collectors.toSet());

        String title = n.path("title").asText("");
        if (!validator.isValid(title, grounding, others, lang, titleMax)) return null;
        List<CourseDtos.NarrationItem> items = new ArrayList<>();
        for (CourseDtos.Stop s : stops) {
            String text = null;
            for (JsonNode it : n.path("items")) if (s.contentId().equals(it.path("contentId").asText())) text = it.path("text").asText(null);
            if (!validator.isValid(text, grounding, others, lang, itemMax)) return null;
            items.add(new CourseDtos.NarrationItem(s.contentId(), text));
        }
        return new CourseDtos.NarrationResponse(courseId, title, items, false);
    }

    /** AI-REQ-C02 대체: 첫 장소 이름 제목 + "장소명 (유형)" */
    CourseDtos.NarrationResponse template(String courseId, List<CourseDtos.Stop> stops, Lang lang) {
        String title = settings.msg(lang, "narration.title", stops.isEmpty() ? "" : stops.get(0).name());
        List<CourseDtos.NarrationItem> items = stops.stream()
                .map(s -> new CourseDtos.NarrationItem(s.contentId(), s.name() + " (" + settings.msg(lang, "cat." + s.category()) + ")"))
                .toList();
        return new CourseDtos.NarrationResponse(courseId, title, items, true);
    }

    private static String languageName(Lang lang) {
        return switch (lang) {
            case KO -> "Korean";
            case EN -> "English";
            case JA -> "Japanese";
        };
    }
}
