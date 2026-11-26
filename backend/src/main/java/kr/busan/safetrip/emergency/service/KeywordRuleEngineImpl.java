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
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.common.keyword.KeywordRuleEngine;

/**
 * [긴급 담당] COMP-E04 KeywordRuleEngineImpl
 * 요구사항: FR-E005, FR-C013, NFR-E009, #165
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 위급 키워드 판정 구현(공용 인터페이스 KeywordRuleEngine).
 * 사전은 클래스패스의 /emergency/keywords.json에서 읽고 API-E05로 다시 적재할 수 있다(NFR-E009).
 * - 제외 표현(excludes)을 먼저 제거한 뒤 띄어쓰기 유지본과 공백 제거본을 만든다.
 * - 영어(라틴) 키워드: 띄어쓰기 유지본에서 단어 경계(\b)로 매칭.
 * - 한글 2글자 이하 키워드: 띄어쓰기 유지본에서 어절 시작(앞 글자가 한글이 아님)만 인정.
 * - 한글 3글자 이상·일본어 키워드: 공백 제거본에서 포함 검사.
 * - '+' AND 규칙: 순서 무관하게 모든 조각이 있어야 하며 조각 사이 거리가 andMaxGap(cjk 글자수 / latinWords 단어수) 이하.
 * 사용자 입력 문장은 로그에 남기지 않는다(NFR-E004).
 */
@Component
public class KeywordRuleEngineImpl implements KeywordRuleEngine {
    public record Match(EmType type, String group, boolean urgent) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AndMaxGap(Integer cjk, Integer latinWords) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawExcludes(List<String> ko, List<String> en, List<String> ja) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawConfig(
            List<EmType> priority,
            List<EmType> urgentTypes,
            AndMaxGap andMaxGap,
            RawExcludes excludes,
            List<RawGroup> groups) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawGroup(EmType type, String group, List<String> ko, List<String> en, List<String> ja) {}

    private enum RuleMode {
        LATIN_SINGLE,
        LATIN_AND,
        HANGUL_SHORT_START,
        CJK_SINGLE,
        CJK_AND
    }

    private record Span(int start, int end) {}

    private record Rule(RuleMode mode, List<String> canonicalParts, List<Pattern> patterns) {
        boolean matches(String spaced, String compact, int maxCjkGap, int maxLatinWords) {
            return switch (mode) {
                case LATIN_SINGLE, HANGUL_SHORT_START -> patterns.get(0).matcher(spaced).find();
                case CJK_SINGLE -> compact.contains(canonicalParts.get(0));
                case LATIN_AND -> matchesLatinAnd(spaced, patterns, maxLatinWords);
                case CJK_AND -> matchesCjkAnd(compact, canonicalParts, maxCjkGap);
            };
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Rule other)) return false;
            return mode == other.mode && canonicalParts.equals(other.canonicalParts);
        }

        @Override
        public int hashCode() {
            return 31 * mode.hashCode() + canonicalParts.hashCode();
        }
    }

    private record CompiledGroup(String group, List<Rule> rules) {}

    private record Dictionary(
            List<EmType> priority,
            Set<EmType> urgentTypes,
            int maxCjkGap,
            int maxLatinWords,
            List<Pattern> excludePatterns,
            Map<EmType, List<CompiledGroup>> groupsByType) {}

    private static final Logger log = LoggerFactory.getLogger(KeywordRuleEngineImpl.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern LATIN_WORD = Pattern.compile("[a-z0-9]+(?:'[a-z0-9]+)?");
    private static final int DEFAULT_MAX_CJK_GAP = 4;
    private static final int DEFAULT_MAX_LATIN_WORDS = 3;
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

    KeywordRuleEngineImpl(RawConfig config) {
        this.resourcePath = null;
        compileAndSwap(config);
    }

    @Override
    public Optional<EmType> detect(String text) {
        return match(text).map(Match::type);
    }

    /** 제외 표현 제거·정규화 후 priority·파일 순으로 첫 매칭 그룹을 돌려준다. */
    public Optional<Match> match(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Dictionary dict = this.dictionary;
        String spaced = applyExcludes(normalizeSpaced(text), dict.excludePatterns());
        if (spaced.isEmpty()) {
            return Optional.empty();
        }
        String compact = WHITESPACE.matcher(spaced).replaceAll("");
        if (compact.isEmpty()) {
            return Optional.empty();
        }

        for (EmType type : dict.priority()) {
            List<CompiledGroup> groups = dict.groupsByType().get(type);
            if (groups == null) {
                continue;
            }
            for (CompiledGroup g : groups) {
                for (Rule rule : g.rules()) {
                    if (rule.matches(spaced, compact, dict.maxCjkGap(), dict.maxLatinWords())) {
                        return Optional.of(new Match(type, g.group(), dict.urgentTypes().contains(type)));
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** 사전 파일을 다시 읽고 메모리를 교체한다. 전체 키워드 개수를 돌려준다(API-E05). */
    public int reload() {
        if (resourcePath == null) {
            throw new IllegalStateException("리소스 경로가 지정되지 않았습니다.");
        }
        RawConfig raw = readConfig(resourcePath);
        int totalKeywords = compileAndSwap(raw);
        log.info("emergency keywords loaded: {} entries", totalKeywords);
        return totalKeywords;
    }

    private int compileAndSwap(RawConfig raw) {
        List<EmType> priority = raw.priority() == null ? List.of() : List.copyOf(raw.priority());
        Set<EmType> urgentTypes = raw.urgentTypes() == null || raw.urgentTypes().isEmpty()
                ? Set.of()
                : EnumSet.copyOf(raw.urgentTypes());

        int maxCjkGap = raw.andMaxGap() != null && raw.andMaxGap().cjk() != null
                ? raw.andMaxGap().cjk() : DEFAULT_MAX_CJK_GAP;
        int maxLatinWords = raw.andMaxGap() != null && raw.andMaxGap().latinWords() != null
                ? raw.andMaxGap().latinWords() : DEFAULT_MAX_LATIN_WORDS;

        List<Pattern> excludes = compileExcludes(raw.excludes());

        Map<EmType, List<CompiledGroup>> byType = new EnumMap<>(EmType.class);
        int totalKeywords = 0;
        if (raw.groups() != null) {
            for (RawGroup g : raw.groups()) {
                if (g.type() == null) {
                    continue;
                }
                LinkedHashSet<Rule> compiled = new LinkedHashSet<>();
                totalKeywords += collect(g.ko(), false, compiled);
                totalKeywords += collect(g.en(), true, compiled);
                totalKeywords += collect(g.ja(), false, compiled);
                byType.computeIfAbsent(g.type(), k -> new ArrayList<>())
                        .add(new CompiledGroup(g.group(), List.copyOf(compiled)));
            }
        }
        Map<EmType, List<CompiledGroup>> frozen = new EnumMap<>(EmType.class);
        byType.forEach((k, v) -> frozen.put(k, List.copyOf(v)));

        this.dictionary = new Dictionary(priority, urgentTypes, maxCjkGap, maxLatinWords, excludes, frozen);
        return totalKeywords;
    }

    /** 띄어쓰기 유지본: NFKC → 소문자 → 연속 공백 1칸. */
    static String normalizeSpaced(String text) {
        String nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC);
        String lower = nfkc.toLowerCase(Locale.ROOT);
        return WHITESPACE.matcher(lower).replaceAll(" ").trim();
    }

    /** 공백 제거본: NFKC → 소문자 → 공백 모두 제거. */
    static String normalize(String text) {
        String nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC);
        String lower = nfkc.toLowerCase(Locale.ROOT);
        return WHITESPACE.matcher(lower).replaceAll("");
    }

    private static String applyExcludes(String spaced, List<Pattern> excludePatterns) {
        if (spaced.isEmpty() || excludePatterns.isEmpty()) {
            return spaced;
        }
        String current = spaced;
        for (Pattern p : excludePatterns) {
            current = p.matcher(current).replaceAll(" ");
        }
        return WHITESPACE.matcher(current).replaceAll(" ").trim();
    }

    private static List<Pattern> compileExcludes(RawExcludes raw) {
        if (raw == null) {
            return List.of();
        }
        List<Pattern> patterns = new ArrayList<>();
        addExcludes(raw.ko(), false, patterns);
        addExcludes(raw.en(), true, patterns);
        addExcludes(raw.ja(), false, patterns);
        return List.copyOf(patterns);
    }

    private static void addExcludes(List<String> phrases, boolean latin, List<Pattern> sink) {
        if (phrases == null) {
            return;
        }
        for (String phrase : phrases) {
            if (phrase == null || phrase.isBlank()) {
                continue;
            }
            String spaced = normalizeSpaced(phrase);
            if (spaced.isEmpty()) {
                continue;
            }
            if (latin) {
                sink.add(compileLatinPhrase(spaced));
            } else {
                // CJK 제외 표현은 띄어쓰기가 있어도/없어도 제거되도록 내부 공백을 \s* 로 허용
                String[] tokens = WHITESPACE.split(spaced);
                StringBuilder regex = new StringBuilder();
                for (int i = 0; i < tokens.length; i++) {
                    if (i > 0) regex.append("\\s*");
                    regex.append(Pattern.quote(tokens[i]));
                }
                sink.add(Pattern.compile(regex.toString()));
            }
        }
    }

    private static int collect(List<String> terms, boolean latin, Set<Rule> sink) {
        if (terms == null) {
            return 0;
        }
        int count = 0;
        for (String term : terms) {
            Rule rule = parseRule(term, latin);
            if (rule != null && sink.add(rule)) {
                count++;
            }
        }
        return count;
    }

    private static Rule parseRule(String term, boolean latin) {
        if (term == null || term.isBlank()) {
            return null;
        }
        boolean isAnd = term.indexOf('+') >= 0;
        if (latin) {
            if (!isAnd) {
                String spaced = normalizeSpaced(term);
                if (spaced.isEmpty()) return null;
                return new Rule(RuleMode.LATIN_SINGLE, List.of(spaced), List.of(compileLatinPhrase(spaced)));
            }
            String[] rawParts = term.split("\\+");
            List<String> canonical = new ArrayList<>(rawParts.length);
            List<Pattern> patterns = new ArrayList<>(rawParts.length);
            for (String rawPart : rawParts) {
                String spaced = normalizeSpaced(rawPart);
                if (!spaced.isEmpty()) {
                    canonical.add(spaced);
                    patterns.add(compileLatinPhrase(spaced));
                }
            }
            if (canonical.isEmpty()) return null;
            RuleMode mode = canonical.size() == 1 ? RuleMode.LATIN_SINGLE : RuleMode.LATIN_AND;
            return new Rule(mode, List.copyOf(canonical), List.copyOf(patterns));
        }

        // Korean / Japanese
        if (!isAnd) {
            String spaced = normalizeSpaced(term);
            String compact = WHITESPACE.matcher(spaced).replaceAll("");
            if (compact.isEmpty()) return null;
            if (compact.length() <= 2 && containsHangul(compact)) {
                // 한글 2글자 이하: 띄어쓰기 유지본에서 어절 시작에서만 인정(앞 글자가 한글이면 불일치)
                String[] tokens = WHITESPACE.split(spaced);
                StringBuilder regex = new StringBuilder("(?<![가-힣])");
                for (int i = 0; i < tokens.length; i++) {
                    if (i > 0) regex.append("\\s*");
                    regex.append(Pattern.quote(tokens[i]));
                }
                return new Rule(RuleMode.HANGUL_SHORT_START, List.of(spaced), List.of(Pattern.compile(regex.toString())));
            }
            return new Rule(RuleMode.CJK_SINGLE, List.of(compact), List.of());
        }

        String[] rawParts = term.split("\\+");
        List<String> parts = new ArrayList<>(rawParts.length);
        for (String rawPart : rawParts) {
            String norm = normalize(rawPart);
            if (!norm.isEmpty()) {
                parts.add(norm);
            }
        }
        if (parts.isEmpty()) return null;
        if (parts.size() == 1) {
            String only = parts.get(0);
            if (only.length() <= 2 && containsHangul(only)) {
                return new Rule(RuleMode.HANGUL_SHORT_START, List.of(only),
                        List.of(Pattern.compile("(?<![가-힣])" + Pattern.quote(only))));
            }
            return new Rule(RuleMode.CJK_SINGLE, List.of(only), List.of());
        }
        return new Rule(RuleMode.CJK_AND, List.copyOf(parts), List.of());
    }

    private static Pattern compileLatinPhrase(String spaced) {
        String[] tokens = WHITESPACE.split(spaced);
        StringBuilder regex = new StringBuilder("\\b");
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) regex.append("\\s+");
            regex.append(Pattern.quote(tokens[i]));
        }
        regex.append("\\b");
        return Pattern.compile(regex.toString());
    }

    private static boolean containsHangul(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\uAC00' && c <= '\uD7A3') {
                return true;
            }
        }
        return false;
    }

    /** 공백 제거본에서 모든 조각이 순서 무관하게 존재하며 인접 조각 사이 글자 수가 maxGap 이하인지 검사. */
    private static boolean matchesCjkAnd(String compact, List<String> parts, int maxGap) {
        List<List<Span>> occurrences = new ArrayList<>(parts.size());
        for (String part : parts) {
            List<Span> found = new ArrayList<>();
            int idx = compact.indexOf(part);
            while (idx >= 0) {
                found.add(new Span(idx, idx + part.length()));
                idx = compact.indexOf(part, idx + 1);
            }
            if (found.isEmpty()) {
                return false;
            }
            occurrences.add(found);
        }
        return existsWithinCjkGap(occurrences, 0, new Span[parts.size()], maxGap);
    }

    private static boolean existsWithinCjkGap(List<List<Span>> occurrences, int depth, Span[] chosen, int maxGap) {
        if (depth == occurrences.size()) {
            List<Span> sorted = new ArrayList<>(List.of(chosen));
            sorted.sort(Comparator.comparingInt(Span::start));
            for (int i = 0; i < sorted.size() - 1; i++) {
                int gap = sorted.get(i + 1).start() - sorted.get(i).end();
                if (gap > maxGap) {
                    return false;
                }
            }
            return true;
        }
        for (Span s : occurrences.get(depth)) {
            chosen[depth] = s;
            if (existsWithinCjkGap(occurrences, depth + 1, chosen, maxGap)) {
                return true;
            }
        }
        return false;
    }

    /** 띄어쓰기 유지본에서 모든 영어 조각이 단어 경계로 존재하며 인접 조각 사이 단어 수가 maxWords 이하인지 검사. */
    private static boolean matchesLatinAnd(String spaced, List<Pattern> patterns, int maxWords) {
        List<List<Span>> occurrences = new ArrayList<>(patterns.size());
        for (Pattern p : patterns) {
            List<Span> found = new ArrayList<>();
            Matcher m = p.matcher(spaced);
            while (m.find()) {
                found.add(new Span(m.start(), m.end()));
            }
            if (found.isEmpty()) {
                return false;
            }
            occurrences.add(found);
        }
        return existsWithinLatinGap(spaced, occurrences, 0, new Span[patterns.size()], maxWords);
    }

    private static boolean existsWithinLatinGap(
            String spaced, List<List<Span>> occurrences, int depth, Span[] chosen, int maxWords) {
        if (depth == occurrences.size()) {
            List<Span> sorted = new ArrayList<>(List.of(chosen));
            sorted.sort(Comparator.comparingInt(Span::start));
            for (int i = 0; i < sorted.size() - 1; i++) {
                int from = sorted.get(i).end();
                int to = sorted.get(i + 1).start();
                if (from < to && countLatinWords(spaced.substring(from, to)) > maxWords) {
                    return false;
                }
            }
            return true;
        }
        for (Span s : occurrences.get(depth)) {
            chosen[depth] = s;
            if (existsWithinLatinGap(spaced, occurrences, depth + 1, chosen, maxWords)) {
                return true;
            }
        }
        return false;
    }

    private static int countLatinWords(String slice) {
        int count = 0;
        Matcher m = LATIN_WORD.matcher(slice);
        while (m.find()) {
            count++;
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
