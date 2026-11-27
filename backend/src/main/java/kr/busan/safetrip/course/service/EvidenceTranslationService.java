package kr.busan.safetrip.course.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import kr.busan.safetrip.common.ai.AIService;
import kr.busan.safetrip.common.ai.AiRequest;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.common.domain.Place;
import kr.busan.safetrip.common.domain.PlaceRepository;
import kr.busan.safetrip.common.exception.BusinessException;
import kr.busan.safetrip.common.exception.ErrorCode;
import kr.busan.safetrip.course.dto.CourseDtos;

/**
 * [코스 담당] API-C07 근거 카드 번역
 * 요구사항: FR-C011(근거 원문 표시), FR-C010(요청 언어)
 * 상태: 완료
 * 수정 규칙: 코스 담당만 수정. 사진·긴급 담당은 수정 금지(필요하면 코스 담당에게 요청).
 *
 * 근거 카드의 TourAPI 국문 개요·운영시간을 요청 언어로 번역한 참고 문장. 원문(API-C05)은 바꾸지 않고 따로 준다.
 * LLM 은 AIService 로만. 실패하거나 한글이 많이 남으면 translated=false(화면은 원문만). 성공한 번역은 장소·언어별로 메모리에 둔다.
 */
@Service
public class EvidenceTranslationService {
    private static final Logger log = LoggerFactory.getLogger(EvidenceTranslationService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<Lang, String> LANG_NAME = Map.of(Lang.EN, "English", Lang.JA, "Japanese");

    private final AIService ai;
    private final PlaceRepository places;
    private final CourseSettings settings;
    private final String prompt;
    private final Map<String, Object> cfg;
    private final Map<String, CourseDtos.Translation> cache = new ConcurrentHashMap<>();

    public EvidenceTranslationService(AIService ai, PlaceRepository places, CourseSettings settings) {
        this.ai = ai;
        this.places = places;
        this.settings = settings;
        this.prompt = text("/course/translate-prompt.txt");
        this.cfg = read("/course/translate.json");
    }

    public CourseDtos.Translation translate(String contentId, Lang lang) {
        Place p = places.findByContentId(contentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "장소 없음: " + contentId));
        String code = lang.name().toLowerCase();
        if (lang == Lang.KO) return new CourseDtos.Translation(contentId, code, null, null, false);
        CourseDtos.Translation hit = cache.get(contentId + ":" + code);
        if (hit != null) return hit;
        CourseDtos.Translation t = call(p, lang, code);
        if (t.translated()) cache.put(contentId + ":" + code, t);
        return t;
    }

    private CourseDtos.Translation call(Place p, Lang lang, String code) {
        String overview = p.getOverview() == null ? null : p.getOverview().replaceAll("<[^>]*>", " ").strip();
        String hours = CourseService.hoursText(p);
        CourseDtos.Translation none = new CourseDtos.Translation(p.getContentId(), code, null, null, false);
        try {
            String user = JSON.writeValueAsString(Map.of("overview", overview == null ? "" : overview, "hours", hours == null ? "" : hours));
            int timeout = ((Number) cfg.getOrDefault("timeoutSec", 10)).intValue();
            // JSON 모드를 쓰지 않고 여기서 읽는다: 실패하면 응답 앞부분을 남겨 원인을 본다(입력·출력이 공개 관광 문장이라 개인정보 없음, #242)
            String raw = ai.complete(new AiRequest("course.evidence-translate", prompt.replace("{lang}", LANG_NAME.get(lang)), user, false, Duration.ofSeconds(timeout)));
            JsonNode n;
            try {
                n = JSON.readTree(raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1));
            } catch (IOException | RuntimeException e) {
                log.info("course.translate JSON 아님 lang={} head={}", code, raw.substring(0, Math.min(raw.length(), 200)).replace('\n', ' '));
                return none;
            }
            String ov = settings.fixTerms(blankToNull(n.path("overview").asText(null)), lang);
            String hr = settings.fixTerms(blankToNull(n.path("hours").asText(null)), lang);
            if (!ok(overview, ov) || !ok(hours, hr)) {
                log.info("course.translate 거절 lang={} 한글비율 overview={} hours={} (원문만 표시)", code, ratio(ov), ratio(hr));
                return none;
            }
            return new CourseDtos.Translation(p.getContentId(), code, ov, hr, true);
        } catch (IOException | RuntimeException e) {
            log.warn("course.translate 실패 → 원문만: {}", e.getClass().getSimpleName());
            return none;
        }
    }

    /** 원문이 있으면 번역도 있어야 하고, 한글 비율이 설정값 이하여야 한다(장소 이름 정도만 남음) */
    boolean ok(String source, String translated) {
        if (source == null || source.isBlank()) return true;
        if (translated == null) return false;
        long letters = translated.codePoints().filter(Character::isLetter).count();
        long hangul = translated.codePoints().filter(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HANGUL).count();
        double max = ((Number) cfg.getOrDefault("maxHangulRatio", 0.2)).doubleValue();
        return letters > 0 && (double) hangul / letters <= max;
    }

    private static String ratio(String s) {
        if (s == null) return "-";
        long letters = s.codePoints().filter(Character::isLetter).count();
        long hangul = s.codePoints().filter(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HANGUL).count();
        return letters == 0 ? "0" : String.format("%.2f", (double) hangul / letters);
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() || "null".equals(s) ? null : s.strip(); }

    private static String text(String path) {
        try (InputStream in = EvidenceTranslationService.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException | NullPointerException e) {
            throw new IllegalStateException(path, e);
        }
    }

    private static Map<String, Object> read(String path) {
        try {
            return JSON.readValue(text(path), new TypeReference<>() {});
        } catch (IOException e) {
            throw new IllegalStateException(path, e);
        }
    }
}
