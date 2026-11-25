package kr.busan.safetrip.course.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.ai.AIService;
import kr.busan.safetrip.common.ai.AiRequest;
import kr.busan.safetrip.common.api.Area;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.common.exception.NotImplementedYetException;
import kr.busan.safetrip.common.keyword.KeywordRuleEngine;
import kr.busan.safetrip.course.dto.CourseDtos;

/**
 * [코스 담당] COMP-C03 IntentParser
 * 요구사항: FR-C003, C012, C013, C015, NFR-C002, AI-REQ-C
 * 상태: 완료
 * 수정 규칙: 코스 담당만 수정. 사진·긴급 담당은 수정 금지(필요하면 코스 담당에게 요청).
 *
 * 1) 긴급 문장은 KeywordRuleEngine 으로 먼저 거른다(FR-C013, LLM 안 부름).
 * 2) 조건 추출은 로컬 LLM(AIService). 3) LLM 이 실패하거나 엉뚱한 JSON 을 주면 규칙(intent.json 키워드)으로 대체(NFR-C002).
 * 사용자 문장은 로그에 남기지 않는다.
 */
@Component
public class IntentParser {
    /**
     * 코스 조건. null = 문장에 없었음(CourseService 가 기본값·칩 값으로 채움, FR-C003).
     * area = null 이면 권역 전체, categories = 꼭 넣어야 할 장소 유형, startPlaceId = 1번 고정(FR-C014).
     */
    public record Intent(LocalDate visitDate, LocalTime startTime, Integer durationMin, Area area,
                         List<String> categories, String startPlaceId, boolean outOfScope, Optional<EmType> emergency) {
        public Intent with(LocalDate d, LocalTime t, Integer dur, Area a, List<String> cats, String start) {
            return new Intent(d, t, dur, a, cats, start, outOfScope, emergency);
        }
    }

    /** FR-C015 수정 명령. order = 지금 코스의 장소 번호(1부터) */
    public enum Op { REMOVE, REPLACE, ADD, SET_DURATION, KEEP }
    public record EditOp(Op op, Integer order, String category, Integer durationMin) {}
    /** emergency 가 있으면 ops 는 비어 있다 */
    public record Edit(List<EditOp> ops, Optional<EmType> emergency) {}

    private static final Logger log = LoggerFactory.getLogger(IntentParser.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern HOURS = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(시간|hours?|hrs?|h\\b|時間)", Pattern.CASE_INSENSITIVE);
    /** 규칙 대체용 "N번 빼" / "remove 2" / "2番を外して" */
    private static final Pattern REMOVE = Pattern.compile(
            "(\\d+)\\s*번\\S*\\s*(?:빼|제외|없애|삭제|말고|대신)|(?:remove|drop|skip|replace)\\s+(?:stop\\s*|#|no\\.?\\s*)?(\\d+)|(\\d+)\\s*番\\S*?\\s*(?:外|除|削除|代わり|やめ)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MINUTES = Pattern.compile("(\\d+)\\s*(분|min(?:ute)?s?|分)", Pattern.CASE_INSENSITIVE);

    private final KeywordRuleEngine keywords;
    private final AIService ai;
    private final CourseSettings settings;

    public IntentParser(KeywordRuleEngine keywords, AIService ai, CourseSettings settings) {
        this.keywords = keywords;
        this.ai = ai;
        this.settings = settings;
    }

    public Intent parse(String text, LocalDate today) {
        Optional<EmType> em = detectEmergency(text);
        if (em.isPresent()) return new Intent(null, null, null, null, List.of(), null, false, em);
        try {
            String prompt = settings.intentPrompt().replace("{today}", today.toString())
                    .replace("{categories}", String.join(", ", settings.intent().categories()));
            return fromLlm(ai.complete(new AiRequest("course.intent", prompt, text, true, Duration.ofSeconds(8))));
        } catch (RuntimeException e) {
            log.warn("course.intent LLM 실패 → 규칙 대체: {}", e.getClass().getSimpleName());
            return byRules(text);
        }
    }

    /** 긴급 키워드 판정. ponytail: 긴급 담당 KeywordRuleEngine 미구현이면 긴급 아님으로 진행(로컬 시험용), 구현되면 이 catch 는 안 탄다 */
    private Optional<EmType> detectEmergency(String text) {
        try {
            return keywords.detect(text);
        } catch (NotImplementedYetException e) {
            log.warn("KeywordRuleEngine 미구현 → 긴급 판정 건너뜀");
            return Optional.empty();
        }
    }

    /** FR-C015 수정 문장 → 명령. 긴급 먼저, LLM, 실패하면 규칙. 명령이 없으면 ops = [] (EDIT_UNCLEAR) */
    public Edit parseEdit(String text, List<CourseDtos.Stop> current) {
        Optional<EmType> em = detectEmergency(text);
        if (em.isPresent()) return new Edit(List.of(), em);
        try {
            StringBuilder list = new StringBuilder();
            for (CourseDtos.Stop s : current) list.append(s.order()).append(". ").append(s.name()).append(" (").append(s.category()).append(")\n");
            String prompt = settings.editPrompt().replace("{stops}", list.toString().strip())
                    .replace("{categories}", String.join(", ", settings.intent().categories()));
            return new Edit(editFromLlm(ai.complete(new AiRequest("course.edit", prompt, text, true, Duration.ofSeconds(8))), current.size()),
                    Optional.empty());
        } catch (RuntimeException e) {
            log.warn("course.edit LLM 실패 → 규칙 대체: {}", e.getClass().getSimpleName());
            return new Edit(editByRules(text, current.size()), Optional.empty());
        }
    }

    /** LLM JSON → 명령. 범위 밖 번호·모르는 유형은 버린다 */
    List<EditOp> editFromLlm(String raw, int stops) {
        JsonNode n;
        try {
            n = JSON.readTree(raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1));
        } catch (Exception e) {
            throw new IllegalStateException("LLM JSON 아님");
        }
        if (!n.path("ops").isArray()) throw new IllegalStateException("ops 없음");
        List<EditOp> ops = new ArrayList<>();
        for (JsonNode o : n.get("ops")) {
            Op op;
            try { op = Op.valueOf(o.path("op").asText()); } catch (IllegalArgumentException e) { continue; }
            Integer order = o.path("order").isInt() && o.get("order").asInt() >= 1 && o.get("order").asInt() <= stops ? o.get("order").asInt() : null;
            String cat = settings.intent().categories().contains(o.path("category").asText()) ? o.get("category").asText() : null;
            Integer dur = o.path("durationMin").isInt() ? clampDuration(o.get("durationMin").asInt()) : null;
            boolean ok = switch (op) {
                case REMOVE, KEEP, REPLACE -> order != null;
                case ADD -> cat != null;
                case SET_DURATION -> dur != null;
            };
            if (ok) ops.add(new EditOp(op, order, cat, dur));
        }
        return ops;
    }

    /** NFR-C002 규칙 대체: "N번 빼", 장소 유형 키워드(→ADD), 시간(→SET_DURATION)만 본다 */
    List<EditOp> editByRules(String text, int stops) {
        String t = text.toLowerCase(Locale.ROOT);
        List<EditOp> ops = new ArrayList<>();
        Matcher r = REMOVE.matcher(t);
        while (r.find()) {
            String g = r.group(1) != null ? r.group(1) : r.group(2) != null ? r.group(2) : r.group(3);
            int order = Integer.parseInt(g);
            if (order >= 1 && order <= stops) ops.add(new EditOp(Op.REMOVE, order, null, null));
        }
        settings.intent().categoryKeywords().forEach((cat, ks) -> {
            if (ks.stream().anyMatch(k -> t.contains(k.toLowerCase(Locale.ROOT)))) ops.add(new EditOp(Op.ADD, null, cat, null));
        });
        Integer dur = duration(t);
        if (dur != null) ops.add(new EditOp(Op.SET_DURATION, null, null, dur));
        return ops;
    }

    /** LLM JSON → Intent. 허용 범위 밖 값은 버린다(없던 것으로) */
    Intent fromLlm(String raw) {
        JsonNode n;
        try {
            n = JSON.readTree(raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1));
        } catch (Exception e) {
            throw new IllegalStateException("LLM JSON 아님");
        }
        Area area = null;
        String a = n.path("area").asText("");
        for (Area x : Area.values()) if (x.name().equals(a)) area = x;
        Integer dur = n.path("durationMin").isInt() ? clampDuration(n.get("durationMin").asInt()) : null;
        List<String> cats = new ArrayList<>();
        n.path("categories").forEach(c -> {
            if (settings.intent().categories().contains(c.asText()) && !cats.contains(c.asText())) cats.add(c.asText());
        });
        LocalDate date = null;
        LocalTime time = null;
        try { if (n.path("visitDate").isTextual()) date = LocalDate.parse(n.get("visitDate").asText()); } catch (Exception ignored) { }
        try { if (n.path("startTime").isTextual()) time = LocalTime.parse(n.get("startTime").asText()); } catch (Exception ignored) { }
        return new Intent(date, time, dur, area, cats, null, n.path("outOfScope").asBoolean(false), Optional.empty());
    }

    /** NFR-C002 규칙 대체: 시간·지역·장소 유형·범위 밖 키워드만 본다 */
    Intent byRules(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        CourseSettings.Intent cfg = settings.intent();
        boolean out = cfg.outOfScopeKeywords().stream().anyMatch(k -> t.contains(k.toLowerCase(Locale.ROOT)));
        Area area = null;
        for (Map.Entry<Area, List<String>> e : cfg.areaKeywords().entrySet()) {
            if (e.getValue().stream().anyMatch(k -> t.contains(k.toLowerCase(Locale.ROOT)))) {
                area = area == null ? e.getKey() : null; // 두 지역 다 나오면 전체
                if (area == null) break;
            }
        }
        List<String> cats = new ArrayList<>();
        cfg.categoryKeywords().forEach((cat, ks) -> {
            if (ks.stream().anyMatch(k -> t.contains(k.toLowerCase(Locale.ROOT)))) cats.add(cat);
        });
        return new Intent(null, null, duration(t), area, cats, null, out, Optional.empty());
    }

    /** "3시간", "90분", "2 hours" → 분. 없으면 null */
    private Integer duration(String t) {
        Integer dur = null;
        Matcher h = HOURS.matcher(t);
        Matcher m = MINUTES.matcher(t);
        if (h.find()) dur = (int) Math.round(Double.parseDouble(h.group(1)) * 60);
        if (m.find()) dur = (dur == null ? 0 : dur) + Integer.parseInt(m.group(1));
        return dur == null ? null : clampDuration(dur);
    }

    private int clampDuration(int min) {
        return Math.max(settings.intent().minDurationMin(), Math.min(settings.intent().maxDurationMin(), min));
    }
}
