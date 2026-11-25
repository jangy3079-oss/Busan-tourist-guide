package kr.busan.safetrip.photo.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.ai.AIService;
import kr.busan.safetrip.common.ai.AiRequest;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.common.domain.Place;
import kr.busan.safetrip.common.exception.BusinessException;
import kr.busan.safetrip.common.exception.ErrorCode;
import kr.busan.safetrip.photo.dto.PhotoDtos;
import kr.busan.safetrip.photo.repository.PhotoPlaces;

/**
 * [사진 담당] COMP-P08 PlaceNarrationService
 * 요구사항: FR-P011, P012, P019, AI-REQ-P02, AI-REQ-P03, AI-REQ-P04
 * 상태: 완료
 * 수정 규칙: 사진 담당만 수정. 코스·긴급 담당은 수정 금지(필요하면 사진 담당에게 요청).
 *
 * 확정 장소 원문으로 설명 생성 -> OutputValidator 검증 -> 실패 시 템플릿. matchId 가 있으면 선택한 contentId 를 MatchLogger 에 남긴다(FR-P019).
 * LLM 에는 이 장소의 TourAPI 원문(상호·업종·주소·개요·운영시간)만 보낸다. 간판 OCR 텍스트·사진·좌표는 보내지 않는다(AI-REQ-P04).
 * LLM 실패·한도 초과·검증 실패면 재시도 없이 템플릿(응답시간 NFR-P001). 모든 호출은 AIService 만 거친다.
 */
@Component
public class PlaceNarrationService {
    private static final Logger log = LoggerFactory.getLogger(PlaceNarrationService.class);
    static final String PROMPT_RESOURCE = "photo/narration-prompt.txt";

    private final AIService ai;
    private final PlaceDetails details;
    private final PhotoPlaces photoPlaces;
    private final OutputValidator validator;
    private final MatchLogger logger;
    private final MatchConfig.Narration cfg;
    private final String prompt;

    /** 공용 ApiContractTest 가 경로 매핑만 볼 때 쓰는 생성자 */
    public PlaceNarrationService() {
        this(null, null, null, null, null, null);
    }

    @Autowired
    public PlaceNarrationService(AIService ai, PlaceDetails details, PhotoPlaces photoPlaces, OutputValidator validator,
                                 MatchLogger logger, MatchConfig config) {
        this.ai = ai;
        this.details = details;
        this.photoPlaces = photoPlaces;
        this.validator = validator;
        this.logger = logger;
        this.cfg = config == null ? null : config.narration();
        this.prompt = config == null ? null : readPrompt();
    }

    public PhotoDtos.NarrationResponse narrate(String contentId, Lang lang, String matchId) {
        Place p = details.place(contentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "장소 없음: " + contentId));
        logger.recordSelection(matchId, contentId);

        String source = source(p);
        try {
            String system = prompt.replace("{lang}", languageName(lang)).replace("{maxChars}", String.valueOf(cfg.maxChars()));
            String text = ai.complete(new AiRequest("photo.narration", system, "<place>\n" + source + "\n</place>", false, null)).strip();
            List<String> others = photoPlaces.names();
            if (validator.isValid(text, source, lang, cfg.maxChars(), p.getName(), others)) {
                return new PhotoDtos.NarrationResponse(contentId, text, false);
            }
            log.info("photo.narration 검증 실패 → 템플릿");
        } catch (BusinessException e) {
            log.info("photo.narration LLM 사용 불가({}) → 템플릿", e.code());
        }
        return new PhotoDtos.NarrationResponse(contentId, template(p, lang), true);
    }

    /** FR-P012 템플릿: 상호·업종·주소 */
    String template(Place p, Lang lang) {
        String code = lang.name().toLowerCase();
        Map<String, String> names = cfg.categoryNames() == null ? null : cfg.categoryNames().get(p.getCategory());
        String category = names != null && names.get(code) != null ? names.get(code) : nz(p.getCategory());
        return cfg.templates().get(code)
                .replace("{name}", nz(p.getName()))
                .replace("{category}", category)
                .replace("{address}", nz(p.getAddress()));
    }

    /** LLM 에 줄 근거 = 이 장소의 TourAPI 원문만 */
    static String source(Place p) {
        StringBuilder sb = new StringBuilder();
        line(sb, "name", p.getName());
        line(sb, "category", p.getCategory());
        line(sb, "address", p.getAddress());
        line(sb, "hours", p.getHoursText());
        line(sb, "closed", p.getRestText());
        line(sb, "overview", p.getOverview());
        return sb.toString().strip();
    }

    private static void line(StringBuilder sb, String key, String value) {
        if (value != null && !value.isBlank()) sb.append(key).append(": ").append(value.replaceAll("<[^>]*>", " ").strip()).append('\n');
    }

    private static String languageName(Lang lang) {
        return switch (lang) {
            case KO -> "Korean";
            case EN -> "English";
            case JA -> "Japanese";
        };
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String readPrompt() {
        try (InputStream in = PlaceNarrationService.class.getClassLoader().getResourceAsStream(PROMPT_RESOURCE)) {
            if (in == null) throw new IllegalStateException("사진 설명 프롬프트가 없습니다: " + PROMPT_RESOURCE);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("사진 설명 프롬프트를 읽지 못했습니다", e);
        }
    }
}
