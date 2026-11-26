package kr.busan.safetrip.emergency.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.emergency.dto.EmergencyDtos;

/**
 * [긴급 담당] COMP-E09 KoreanCardBuilder
 * 요구사항: FR-E015, AI-REQ-E01, AI-REQ-E04, AI-REQ-E07, ADR-E03
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 유형별 템플릿(/emergency/card-templates.json)과 슬롯 사전(/emergency/slots.json)을 조합해
 * 한국어 전달 카드 + 사용자 언어 번역(템플릿, LLM 미사용 — ADR-E03)을 생성한다.
 * 입력 문장·주소는 로그에 남기지 않는다(NFR-E004).
 */
@Component
public class KoreanCardBuilder {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record LocalizedText(String ko, String en, String ja) {
        String forLang(Lang lang) {
            return switch (lang) {
                case EN -> en;
                case JA -> ja;
                case KO -> ko;
            };
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawDefault(String code, String ko, String en, String ja) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawValues(
            Map<String, LocalizedText> who,
            Map<String, LocalizedText> item,
            Map<EmType, Map<String, LocalizedText>> symptom,
            Map<EmType, Map<String, LocalizedText>> situation) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawSlots(
            Map<EmType, List<String>> allowedSlots,
            RawValues values,
            Map<EmType, Map<String, RawDefault>> defaults,
            Map<EmType, Map<String, Map<String, String>>> groupHints) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawTemplate(EmType type, String group, String ko, String en, String ja) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawTemplates(List<RawTemplate> templates) {}

    private record CompiledData(
            Map<EmType, Set<String>> allowedSlots,
            Map<EmType, Map<String, Map<String, LocalizedText>>> valuesByTypeAndSlot,
            Map<EmType, Map<String, LocalizedText>> defaultsByTypeAndSlot,
            Map<EmType, Map<String, Map<String, String>>> groupHints,
            Map<EmType, LocalizedText> defaultTemplates,
            Map<EmType, Map<String, LocalizedText>> groupTemplates,
            int templateCount) {}

    private static final Logger log = LoggerFactory.getLogger(KoreanCardBuilder.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}]+)\\}");
    private static final String DEFAULT_SLOTS_RESOURCE = "/emergency/slots.json";
    private static final String DEFAULT_TEMPLATES_RESOURCE = "/emergency/card-templates.json";

    private final String slotsResourcePath;
    private final String templatesResourcePath;
    private volatile CompiledData data;

    public KoreanCardBuilder() {
        this(DEFAULT_SLOTS_RESOURCE, DEFAULT_TEMPLATES_RESOURCE);
    }

    KoreanCardBuilder(String slotsResourcePath, String templatesResourcePath) {
        this.slotsResourcePath = slotsResourcePath;
        this.templatesResourcePath = templatesResourcePath;
        reload();
    }

    KoreanCardBuilder(RawSlots rawSlots, RawTemplates rawTemplates) {
        this.slotsResourcePath = null;
        this.templatesResourcePath = null;
        this.data = compileAndValidate(rawSlots, rawTemplates);
    }

    /**
     * 유형·그룹·슬롯 코드·현재 주소·사용자 언어로 한국어 전달 카드를 생성한다.
     * NON 유형은 null을 돌려주며, 허용되지 않은 슬롯 키나 목록에 없는 코드값은 무시하고 기본값을 쓴다.
     */
    public EmergencyDtos.Card build(
            EmType type, String group, Map<String, String> slots, String address, Lang lang) {
        if (type == null || type == EmType.NON) {
            return null;
        }
        CompiledData current = this.data;
        LocalizedText template = resolveTemplate(current, type, group);
        Set<String> allowed = current.allowedSlots().getOrDefault(type, Set.of());
        Map<String, Map<String, LocalizedText>> slotValues = current.valuesByTypeAndSlot().getOrDefault(type, Map.of());
        Map<String, LocalizedText> slotDefaults = current.defaultsByTypeAndSlot().getOrDefault(type, Map.of());

        Map<String, LocalizedText> resolved = new HashMap<>(allowed.size());
        for (String slotKey : allowed) {
            LocalizedText chosen = null;
            if (slots != null) {
                String rawCode = slots.get(slotKey);
                if (rawCode != null && !rawCode.isBlank()) {
                    String normalizedCode = rawCode.trim().toUpperCase(Locale.ROOT);
                    chosen = slotValues.getOrDefault(slotKey, Map.of()).get(normalizedCode);
                }
            }
            resolved.put(slotKey, chosen != null ? chosen : slotDefaults.get(slotKey));
        }

        String ko = render(template.ko(), resolved, Lang.KO);
        Lang userLang = lang == null ? Lang.KO : lang;
        String translated = userLang == Lang.KO ? null : render(template.forLang(userLang), resolved, userLang);
        return new EmergencyDtos.Card(ko, translated, address);
    }

    /** 키워드 그룹 이름으로부터 카드 슬롯 힌트를 돌려준다(LLM 실패 시 폴백용). */
    public Map<String, String> slotsFromGroup(EmType type, String group) {
        if (type == null || type == EmType.NON || group == null || group.isBlank()) {
            return Map.of();
        }
        Map<String, Map<String, String>> byGroup = this.data.groupHints().get(type);
        if (byGroup == null) {
            return Map.of();
        }
        Map<String, String> hint = byGroup.get(group.trim().toUpperCase(Locale.ROOT));
        return hint == null ? Map.of() : hint;
    }

    /** slots.json과 card-templates.json을 다시 읽고 검증한 뒤 메모리를 교체한다. 로드된 템플릿 개수를 돌려준다(API-E05). */
    public int reload() {
        if (slotsResourcePath == null || templatesResourcePath == null) {
            throw new IllegalStateException("리소스 경로가 지정되지 않았습니다.");
        }
        RawSlots rawSlots = readResource(slotsResourcePath, RawSlots.class, "슬롯 사전");
        RawTemplates rawTemplates = readResource(templatesResourcePath, RawTemplates.class, "카드 템플릿");
        CompiledData compiled = compileAndValidate(rawSlots, rawTemplates);
        this.data = compiled;
        log.info("emergency card templates loaded: {} templates", compiled.templateCount());
        return compiled.templateCount();
    }

    /** T10 등 긴급 패키지 내부에서 유형별 허용 슬롯·코드 목록을 참조할 때 사용한다. */
    Map<String, Set<String>> allowedCodes(EmType type) {
        if (type == null || type == EmType.NON) {
            return Map.of();
        }
        Map<String, Map<String, LocalizedText>> bySlot = this.data.valuesByTypeAndSlot().getOrDefault(type, Map.of());
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, LocalizedText>> e : bySlot.entrySet()) {
            result.put(e.getKey(), Collections.unmodifiableSet(e.getValue().keySet()));
        }
        return Collections.unmodifiableMap(result);
    }

    private static LocalizedText resolveTemplate(CompiledData current, EmType type, String group) {
        if (group != null && !group.isBlank()) {
            Map<String, LocalizedText> overrides = current.groupTemplates().get(type);
            if (overrides != null) {
                LocalizedText specific = overrides.get(group.trim().toUpperCase(Locale.ROOT));
                if (specific != null) {
                    return specific;
                }
            }
        }
        return current.defaultTemplates().get(type);
    }

    private static String render(String template, Map<String, LocalizedText> resolvedSlots, Lang lang) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder(template.length() + 32);
        while (matcher.find()) {
            String key = matcher.group(1);
            LocalizedText text = resolvedSlots.get(key);
            if (text == null) {
                throw new IllegalStateException("해석할 수 없는 카드 슬롯: " + key);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(text.forLang(lang)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static CompiledData compileAndValidate(RawSlots rawSlots, RawTemplates rawTemplates) {
        if (rawSlots == null || rawSlots.allowedSlots() == null || rawSlots.values() == null || rawSlots.defaults() == null) {
            throw new IllegalStateException("슬롯 사전 필수 항목(allowedSlots, values, defaults)이 비어 있습니다.");
        }
        if (rawTemplates == null || rawTemplates.templates() == null || rawTemplates.templates().isEmpty()) {
            throw new IllegalStateException("카드 템플릿 목록이 비어 있습니다.");
        }

        // 1) 유형별 허용 슬롯·코드 사전 컴파일 및 검증
        Map<EmType, Set<String>> allowedSlots = new EnumMap<>(EmType.class);
        Map<EmType, Map<String, Map<String, LocalizedText>>> valuesByTypeAndSlot = new EnumMap<>(EmType.class);
        Map<EmType, Map<String, LocalizedText>> defaultsByTypeAndSlot = new EnumMap<>(EmType.class);

        for (EmType type : EmType.values()) {
            List<String> rawAllowed = rawSlots.allowedSlots().get(type);
            if (type == EmType.NON) {
                if (rawAllowed != null && !rawAllowed.isEmpty()) {
                    throw new IllegalStateException("NON 유형에는 허용 슬롯을 둘 수 없습니다.");
                }
                allowedSlots.put(EmType.NON, Set.of());
                continue;
            }
            if (rawAllowed == null || rawAllowed.isEmpty()) {
                throw new IllegalStateException("허용 슬롯 정의가 빠졌습니다: " + type);
            }
            Set<String> slotSet = new LinkedHashSet<>();
            for (String s : rawAllowed) {
                if (s == null || s.isBlank()) {
                    throw new IllegalStateException("빈 슬롯 키가 있습니다: " + type);
                }
                slotSet.add(s.trim());
            }
            allowedSlots.put(type, Collections.unmodifiableSet(slotSet));

            Map<String, Map<String, LocalizedText>> typeValues = new LinkedHashMap<>();
            Map<String, LocalizedText> typeDefaults = new LinkedHashMap<>();
            Map<String, RawDefault> rawTypeDefaults = rawSlots.defaults().get(type);
            if (rawTypeDefaults == null) {
                throw new IllegalStateException("기본 슬롯 정의가 빠졌습니다: " + type);
            }

            for (String slotKey : slotSet) {
                Map<String, LocalizedText> rawCodes = lookupRawCodes(rawSlots.values(), type, slotKey);
                if (rawCodes == null || rawCodes.isEmpty()) {
                    throw new IllegalStateException("슬롯 값 목록이 비어 있습니다: " + type + "." + slotKey);
                }
                Map<String, LocalizedText> normalizedCodes = new LinkedHashMap<>();
                for (Map.Entry<String, LocalizedText> entry : rawCodes.entrySet()) {
                    String code = normalizeKey(entry.getKey(), "슬롯 코드(" + type + "." + slotKey + ")");
                    LocalizedText text = validateLocalized(entry.getValue(), type + "." + slotKey + "." + code);
                    normalizedCodes.put(code, text);
                }
                typeValues.put(slotKey, Collections.unmodifiableMap(normalizedCodes));

                RawDefault rawDef = rawTypeDefaults.get(slotKey);
                if (rawDef == null) {
                    throw new IllegalStateException("기본 슬롯 값이 빠졌습니다: " + type + "." + slotKey);
                }
                LocalizedText resolvedDefault;
                if (rawDef.code() != null && !rawDef.code().isBlank()) {
                    String defCode = rawDef.code().trim().toUpperCase(Locale.ROOT);
                    resolvedDefault = normalizedCodes.get(defCode);
                    if (resolvedDefault == null) {
                        throw new IllegalStateException("기본 슬롯 코드가 사전에 없습니다: " + type + "." + slotKey + "=" + defCode);
                    }
                } else {
                    resolvedDefault = validateLocalized(
                            new LocalizedText(rawDef.ko(), rawDef.en(), rawDef.ja()),
                            "defaults." + type + "." + slotKey);
                }
                typeDefaults.put(slotKey, resolvedDefault);
            }
            valuesByTypeAndSlot.put(type, Collections.unmodifiableMap(typeValues));
            defaultsByTypeAndSlot.put(type, Collections.unmodifiableMap(typeDefaults));
        }

        // 2) groupHints 검증
        Map<EmType, Map<String, Map<String, String>>> compiledHints = new EnumMap<>(EmType.class);
        if (rawSlots.groupHints() != null) {
            for (Map.Entry<EmType, Map<String, Map<String, String>>> typeEntry : rawSlots.groupHints().entrySet()) {
                EmType type = typeEntry.getKey();
                if (type == null || type == EmType.NON) {
                    throw new IllegalStateException("groupHints에 잘못된 유형이 있습니다: " + type);
                }
                Set<String> allowed = allowedSlots.getOrDefault(type, Set.of());
                Map<String, Map<String, LocalizedText>> typeValues = valuesByTypeAndSlot.getOrDefault(type, Map.of());
                Map<String, Map<String, String>> byGroup = new LinkedHashMap<>();
                if (typeEntry.getValue() != null) {
                    for (Map.Entry<String, Map<String, String>> groupEntry : typeEntry.getValue().entrySet()) {
                        String groupName = normalizeKey(groupEntry.getKey(), "groupHints(" + type + ")");
                        Map<String, String> hintSlots = new LinkedHashMap<>();
                        if (groupEntry.getValue() != null) {
                            for (Map.Entry<String, String> slotEntry : groupEntry.getValue().entrySet()) {
                                String slotKey = slotEntry.getKey() == null ? "" : slotEntry.getKey().trim();
                                String code = normalizeKey(slotEntry.getValue(), "groupHints." + type + "." + groupName + "." + slotKey);
                                if (!allowed.contains(slotKey)) {
                                    throw new IllegalStateException("groupHints에 허용되지 않은 슬롯 키가 있습니다: " + type + "." + groupName + "." + slotKey);
                                }
                                if (!typeValues.getOrDefault(slotKey, Map.of()).containsKey(code)) {
                                    throw new IllegalStateException("groupHints에 사전에 없는 슬롯 코드가 있습니다: " + type + "." + groupName + "." + slotKey + "=" + code);
                                }
                                hintSlots.put(slotKey, code);
                            }
                        }
                        byGroup.put(groupName, Collections.unmodifiableMap(hintSlots));
                    }
                }
                compiledHints.put(type, Collections.unmodifiableMap(byGroup));
            }
        }

        // 3) 카드 템플릿 컴파일 및 자리표시자·다국어 검증
        Map<EmType, LocalizedText> defaultTemplates = new EnumMap<>(EmType.class);
        Map<EmType, Map<String, LocalizedText>> groupTemplates = new EnumMap<>(EmType.class);

        for (RawTemplate raw : rawTemplates.templates()) {
            if (raw.type() == null || raw.type() == EmType.NON) {
                throw new IllegalStateException("카드 템플릿에 잘못된 유형이 있습니다: " + raw.type());
            }
            String label = raw.type() + (raw.group() == null || raw.group().isBlank() ? "" : "(" + raw.group() + ")");
            LocalizedText text = validateLocalized(new LocalizedText(raw.ko(), raw.en(), raw.ja()), "template." + label);
            Set<String> expectedPlaceholders = allowedSlots.get(raw.type());
            validatePlaceholders(text.ko(), expectedPlaceholders, label + ".ko");
            validatePlaceholders(text.en(), expectedPlaceholders, label + ".en");
            validatePlaceholders(text.ja(), expectedPlaceholders, label + ".ja");

            if (raw.group() == null || raw.group().isBlank()) {
                if (defaultTemplates.put(raw.type(), text) != null) {
                    throw new IllegalStateException("기본 카드 템플릿이 중복되었습니다: " + raw.type());
                }
            } else {
                String groupName = raw.group().trim().toUpperCase(Locale.ROOT);
                Map<String, LocalizedText> overrides =
                        groupTemplates.computeIfAbsent(raw.type(), k -> new LinkedHashMap<>());
                if (overrides.put(groupName, text) != null) {
                    throw new IllegalStateException("그룹 카드 템플릿이 중복되었습니다: " + raw.type() + "/" + groupName);
                }
            }
        }

        for (EmType type : EmType.values()) {
            if (type != EmType.NON && !defaultTemplates.containsKey(type)) {
                throw new IllegalStateException("기본 카드 템플릿이 빠졌습니다: " + type);
            }
        }

        Map<EmType, Map<String, LocalizedText>> frozenGroupTemplates = new EnumMap<>(EmType.class);
        groupTemplates.forEach((k, v) -> frozenGroupTemplates.put(k, Collections.unmodifiableMap(v)));

        return new CompiledData(
                allowedSlots,
                valuesByTypeAndSlot,
                defaultsByTypeAndSlot,
                compiledHints,
                defaultTemplates,
                frozenGroupTemplates,
                rawTemplates.templates().size());
    }

    private static Map<String, LocalizedText> lookupRawCodes(RawValues values, EmType type, String slotKey) {
        return switch (slotKey) {
            case "who" -> values.who();
            case "item" -> values.item();
            case "symptom" -> values.symptom() == null ? null : values.symptom().get(type);
            case "situation" -> values.situation() == null ? null : values.situation().get(type);
            default -> throw new IllegalStateException("알 수 없는 슬롯 키입니다: " + type + "." + slotKey);
        };
    }

    private static String normalizeKey(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(label + " 값이 비어 있습니다.");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static LocalizedText validateLocalized(LocalizedText text, String label) {
        if (text == null
                || text.ko() == null || text.ko().isBlank()
                || text.en() == null || text.en().isBlank()
                || text.ja() == null || text.ja().isBlank()) {
            throw new IllegalStateException("ko/en/ja 문장이 비어 있습니다: " + label);
        }
        return text;
    }

    private static void validatePlaceholders(String template, Set<String> expected, String label) {
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(template);
        String stripped = matcher.replaceAll(mr -> {
            found.add(mr.group(1));
            return "";
        });
        if (stripped.indexOf('{') >= 0 || stripped.indexOf('}') >= 0) {
            throw new IllegalStateException("닫히지 않은 자리표시자 중괄호가 있습니다: " + label);
        }
        if (!found.equals(expected)) {
            throw new IllegalStateException(
                    "템플릿 자리표시자가 허용 슬롯과 일치하지 않습니다(" + label + "): expected=" + expected + ", found=" + found);
        }
    }

    private static <T> T readResource(String path, Class<T> type, String label) {
        URL url = KoreanCardBuilder.class.getResource(path);
        if (url == null) {
            throw new IllegalStateException(label + " 리소스를 찾을 수 없음: " + path);
        }
        try {
            URLConnection conn = url.openConnection();
            conn.setUseCaches(false);
            try (InputStream in = conn.getInputStream()) {
                return JSON.readValue(in, type);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(label + " 읽기 실패: " + path, e);
        }
    }
}
