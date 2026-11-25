package kr.busan.safetrip.emergency.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLConnection;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.common.keyword.KeywordRuleEngine;

/**
 * [긴급 담당] COMP-E04 KeywordRuleEngineImpl
 * 요구사항: FR-E005, FR-C013, NFR-E009
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 위급 키워드 판정 구현(공용 인터페이스 KeywordRuleEngine).
 * 사전은 클래스패스의 /emergency/keywords.json에서 읽고 API-E05로 다시 적재할 수 있다(NFR-E009).
 * 사용자 입력 문장은 로그에 남기지 않는다(NFR-E004).
 */
@Component
public class KeywordRuleEngineImpl implements KeywordRuleEngine {
    public record Match(EmType type, String group, boolean urgent) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawConfig(List<EmType> priority, List<EmType> urgentTypes, List<RawGroup> groups) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawGroup(EmType type, String group, List<String> ko, List<String> en, List<String> ja) {}

    private record CompiledGroup(String group, List<String> keywords) {}

    private record Dictionary(List<EmType> priority, Set<EmType> urgentTypes,
                              Map<EmType, List<CompiledGroup>> groupsByType) {}

    private static final Logger log = LoggerFactory.getLogger(KeywordRuleEngineImpl.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final String DEFAULT_RESOURCE = "/emergency/keywords.json";

    private final String resourcePath;
    private volatile Dictionary dictionary;

    public KeywordRuleEngineImpl() {
        this(DEFAULT_RESOURCE);
    }

    KeywordRuleEngineImpl(String resourcePath) {
        this.resourcePath = resourcePath;
        reload();
    }

    @Override
    public Optional<EmType> detect(String text) {
        return match(text).map(Match::type);
    }

    /** 정규화(NFKC → 소문자 → 공백 제거) 후 priority·파일 순으로 첫 매칭 그룹을 돌려준다. */
    public Optional<Match> match(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        Dictionary dict = this.dictionary;
        for (EmType type : dict.priority()) {
            List<CompiledGroup> groups = dict.groupsByType().get(type);
            if (groups == null) {
                continue;
            }
            for (CompiledGroup g : groups) {
                for (String kw : g.keywords()) {
                    if (normalized.contains(kw)) {
                        return Optional.of(new Match(type, g.group(), dict.urgentTypes().contains(type)));
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** 사전 파일을 다시 읽고 메모리를 교체한다. 전체 키워드 개수를 돌려준다(API-E05). */
    public int reload() {
        RawConfig raw = readConfig(resourcePath);
        List<EmType> priority = raw.priority() == null ? List.of() : List.copyOf(raw.priority());
        Set<EmType> urgentTypes = raw.urgentTypes() == null || raw.urgentTypes().isEmpty()
                ? Set.of()
                : EnumSet.copyOf(raw.urgentTypes());

        Map<EmType, List<CompiledGroup>> byType = new EnumMap<>(EmType.class);
        int totalKeywords = 0;
        if (raw.groups() != null) {
            for (RawGroup g : raw.groups()) {
                if (g.type() == null) {
                    continue;
                }
                LinkedHashSet<String> normalized = new LinkedHashSet<>();
                totalKeywords += collect(g.ko(), normalized);
                totalKeywords += collect(g.en(), normalized);
                totalKeywords += collect(g.ja(), normalized);
                byType.computeIfAbsent(g.type(), k -> new ArrayList<>())
                        .add(new CompiledGroup(g.group(), List.copyOf(normalized)));
            }
        }
        Map<EmType, List<CompiledGroup>> frozen = new EnumMap<>(EmType.class);
        byType.forEach((k, v) -> frozen.put(k, List.copyOf(v)));

        this.dictionary = new Dictionary(priority, urgentTypes, frozen);
        log.info("emergency keywords loaded: {} entries", totalKeywords);
        return totalKeywords;
    }

    static String normalize(String text) {
        String nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC);
        String lower = nfkc.toLowerCase(Locale.ROOT);
        return WHITESPACE.matcher(lower).replaceAll("");
    }

    private static int collect(List<String> terms, Set<String> sink) {
        if (terms == null) {
            return 0;
        }
        int count = 0;
        for (String term : terms) {
            if (term == null || term.isBlank()) {
                continue;
            }
            String norm = normalize(term);
            if (!norm.isEmpty() && sink.add(norm)) {
                count++;
            }
        }
        return count;
    }

    private static RawConfig readConfig(String path) {
        URL url = KeywordRuleEngineImpl.class.getResource(path);
        if (url == null) {
            throw new IllegalStateException("키워드 사전 리소스를 찾을 수 없음: " + path);
        }
        try {
            URLConnection conn = url.openConnection();
            conn.setUseCaches(false);
            try (InputStream in = conn.getInputStream()) {
                return JSON.readValue(in, RawConfig.class);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("키워드 사전 읽기 실패: " + path, e);
        }
    }
}
