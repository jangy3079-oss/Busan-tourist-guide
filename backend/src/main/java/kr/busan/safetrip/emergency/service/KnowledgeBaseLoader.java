package kr.busan.safetrip.emergency.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import kr.busan.safetrip.common.ai.EmbeddingService;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.emergency.repository.KbDocumentRepository;
import kr.busan.safetrip.emergency.repository.KbDocumentRepository.HashState;
import kr.busan.safetrip.emergency.repository.KbDocumentRepository.KbDoc;
import kr.busan.safetrip.emergency.repository.KbDocumentRepository.KeyLang;

/**
 * [긴급 담당] COMP-E12 KnowledgeBaseLoader
 * 요구사항: FR-E016, FR-E022, DATA-E09, ADR-E08, NFR-E003 (#111 #115)
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * resources/emergency/kb/*.md 원고(YAML 머리말 + ## ko|en|ja steps/facts)를 검증·적재한다.
 * content_hash가 바뀐 행만 공용 EmbeddingService로 임베딩을 다시 만들며, 임베딩 장애 시 NULL로 저장한다.
 * 시작 시에는 오류가 있는 문서만 건너뛰고 경고 로그를 남기며, API-E05 재적재 시에는 엄격 검증해 실패하면 이전 상태를 유지한다.
 */
@Component
public class KnowledgeBaseLoader {
    public record NamedMarkdown(String filename, String content) {}

    public record ParseOutcome(List<KbDoc> rows, Set<String> activeKeys, List<String> errors) {}

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseLoader.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String DEFAULT_PATTERN = "classpath*:emergency/kb/*.md";
    private static final List<String> REQUIRED_LANGS = List.of("ko", "en", "ja");
    private static final int MIN_STEPS = 2;
    private static final int MAX_STEPS = 4;
    private static final Pattern LANG_HEADING = Pattern.compile("^##\\s+(ko|en|ja)\\s*$", Pattern.MULTILINE);

    private final KbDocumentRepository repo;
    private final EmbeddingService embeddingService;
    private final Supplier<List<NamedMarkdown>> sourceSupplier;
    private final Clock clock;

    @Autowired
    public KnowledgeBaseLoader(KbDocumentRepository repo, EmbeddingService embeddingService) {
        this(repo, embeddingService, () -> readClasspathResources(DEFAULT_PATTERN), Clock.system(SEOUL));
    }

    KnowledgeBaseLoader(
            KbDocumentRepository repo,
            EmbeddingService embeddingService,
            Supplier<List<NamedMarkdown>> sourceSupplier,
            Clock clock) {
        this.repo = repo;
        this.embeddingService = embeddingService;
        this.sourceSupplier = sourceSupplier;
        this.clock = clock == null ? Clock.system(SEOUL) : clock;
    }

    /** 애플리케이션 기동 후 KB 문서를 적재한다. 오류가 있는 문서는 건너뛰고 전부 실패해도 시작을 막지 않는다. */
    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        try {
            int count = loadLenient();
            log.info("emergency.kb startup loaded rows={}", count);
        } catch (RuntimeException e) {
            log.warn("emergency.kb startup failed (will use FallbackSteps): {}", e.getClass().getSimpleName());
        }
    }

    /** 관대 모드 적재(시작 시): 위반 문서는 경고 로그 후 건너뛴다. */
    @Transactional
    public int loadLenient() {
        ParseOutcome outcome = parseAll(sourceSupplier.get());
        for (String err : outcome.errors()) {
            log.warn("emergency.kb skipped invalid doc: {}", err);
        }
        if (outcome.rows().isEmpty()) {
            log.warn("emergency.kb no valid documents loaded; guidance will use FallbackSteps");
            return 0;
        }
        return syncToDb(outcome);
    }

    /** 엄격 모드 사전 파싱·검증(API-E05): 하나라도 오류가 있거나 문서가 비어 있으면 예외를 던진다. */
    public ParseOutcome parseStrict() {
        List<NamedMarkdown> files = sourceSupplier.get();
        if (files == null || files.isEmpty()) {
            throw new IllegalStateException("지식베이스(.md) 파일이 하나도 없습니다.");
        }
        ParseOutcome outcome = parseAll(files);
        if (!outcome.errors().isEmpty()) {
            throw new IllegalStateException("지식베이스 검증 실패: " + String.join("; ", outcome.errors()));
        }
        if (outcome.rows().isEmpty()) {
            throw new IllegalStateException("유효한 지식베이스 문서가 없습니다.");
        }
        return outcome;
    }

    /** 사전 검증된 KB 문서를 DB에 반영하고 총 행 수를 돌려준다. */
    @Transactional
    public int applyParsed(ParseOutcome outcome) {
        return syncToDb(outcome);
    }

    /** 엄격 검증 후 DB 반영(단독 호출용). */
    @Transactional
    public int reloadStrict() {
        ParseOutcome outcome = parseStrict();
        return syncToDb(outcome);
    }

    private int syncToDb(ParseOutcome outcome) {
        Map<KeyLang, HashState> existing = repo.findAllHashStates();
        Instant now = clock.instant();

        List<KbDoc> unchanged = new ArrayList<>();
        List<KbDoc> needsEmbed = new ArrayList<>();
        for (KbDoc doc : outcome.rows()) {
            HashState state = existing.get(new KeyLang(doc.docKey(), doc.lang()));
            if (state != null && Objects.equals(state.contentHash(), doc.contentHash()) && state.hasEmbedding()) {
                unchanged.add(doc);
            } else {
                needsEmbed.add(doc);
            }
        }

        for (KbDoc doc : unchanged) {
            repo.upsertKeepingEmbedding(doc, now);
        }

        List<String> vectors = computeEmbeddingsSafely(needsEmbed);
        for (int i = 0; i < needsEmbed.size(); i++) {
            repo.upsertWithEmbedding(needsEmbed.get(i), vectors.get(i), now);
        }

        int deleted = repo.deleteKeysNotIn(outcome.activeKeys());
        log.info("emergency.kb synced rows={} embedded={} unchanged={} deletedKeys={}",
                outcome.rows().size(), needsEmbed.size(), unchanged.size(), deleted);
        return outcome.rows().size();
    }

    private List<String> computeEmbeddingsSafely(List<KbDoc> docs) {
        List<String> nulls = new ArrayList<>(docs.size());
        for (int i = 0; i < docs.size(); i++) {
            nulls.add(null);
        }
        if (docs.isEmpty() || embeddingService == null) {
            return nulls;
        }
        try {
            List<String> texts = docs.stream().map(KbDoc::body).toList();
            List<float[]> vectors = embeddingService.embedAll(texts);
            if (vectors == null || vectors.size() != docs.size()) {
                log.warn("emergency.kb embedding size mismatch; storing NULL embeddings");
                return nulls;
            }
            List<String> literals = new ArrayList<>(docs.size());
            for (float[] v : vectors) {
                literals.add(v == null ? null : EmbeddingService.literal(v));
            }
            return literals;
        } catch (RuntimeException e) {
            log.warn("emergency.kb embedding unavailable ({}); storing rows with NULL embedding",
                    e.getClass().getSimpleName());
            return nulls;
        }
    }

    /** 마크다운 목록 전체를 파싱·검증한다. */
    static ParseOutcome parseAll(List<NamedMarkdown> files) {
        List<KbDoc> rows = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>();
        List<String> errors = new ArrayList<>();

        if (files == null) {
            return new ParseOutcome(List.of(), Set.of(), List.of("파일 목록이 null입니다."));
        }

        for (NamedMarkdown file : files) {
            if (file == null || file.filename() == null || "README.md".equalsIgnoreCase(file.filename())) {
                continue;
            }
            try {
                List<KbDoc> parsed = parseSingle(file.filename(), file.content(), seenKeys);
                seenKeys.add(parsed.get(0).docKey());
                rows.addAll(parsed);
            } catch (IllegalArgumentException e) {
                errors.add(file.filename() + ": " + e.getMessage());
            }
        }
        return new ParseOutcome(List.copyOf(rows), Set.copyOf(seenKeys), List.copyOf(errors));
    }

    private static List<KbDoc> parseSingle(String filename, String rawContent, Set<String> seenKeys) {
        if (rawContent == null || rawContent.isBlank()) {
            throw new IllegalArgumentException("내용이 비어 있습니다.");
        }
        String content = rawContent.replace("\r\n", "\n").replace("\r", "\n");
        if (!content.startsWith("---\n")) {
            throw new IllegalArgumentException("YAML 머리말 시작(---)이 없습니다.");
        }
        int endFront = content.indexOf("\n---\n", 4);
        if (endFront < 0) {
            throw new IllegalArgumentException("YAML 머리말 끝(---)이 없습니다.");
        }
        String yamlText = content.substring(4, endFront);
        String bodyPart = content.substring(endFront + 5);

        Map<String, Object> front = parseYamlMap(yamlText);
        String key = asNonBlankString(front.get("key"));
        if (key == null) {
            throw new IllegalArgumentException("key가 비어 있습니다.");
        }
        if (seenKeys.contains(key)) {
            throw new IllegalArgumentException("중복된 문서 키입니다: " + key);
        }

        List<String> types = parseTypes(front.get("types"));
        List<String> groups = parseStringList(front.get("groups"));
        String status = truncate(asNonBlankString(front.get("status")), 4);
        LocalDate checked = parseDate(front.get("checked"));
        List<String> sources = parseStringList(front.get("sources"));
        Map<String, String> titles = parseTitles(front.get("title"), key);

        Map<String, String> sections = splitLangSections(bodyPart);
        for (String lang : REQUIRED_LANGS) {
            if (!sections.containsKey(lang)) {
                throw new IllegalArgumentException("필수 언어 절이 빠졌습니다: ## " + lang);
            }
        }

        List<KbDoc> docs = new ArrayList<>(REQUIRED_LANGS.size());
        for (String lang : REQUIRED_LANGS) {
            SectionLists lists = parseSectionLists(sections.get(lang));
            if (lists.steps().size() < MIN_STEPS || lists.steps().size() > MAX_STEPS) {
                throw new IllegalArgumentException(
                        lang + " steps 개수가 " + MIN_STEPS + "~" + MAX_STEPS + " 범위를 벗어났습니다: " + lists.steps().size());
            }
            String title = titles.getOrDefault(lang, key);
            String body = buildSearchBody(lists.steps(), lists.facts());
            String contentHash = sha256Hex(key + "|" + lang + "|" + title + "|" + types + "|" + groups + "|" + body);
            docs.add(new KbDoc(
                    key, lang, title, types, groups, status, checked, sources,
                    lists.steps(), body, contentHash, false));
        }
        return docs;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseYamlMap(String yamlText) {
        try {
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Object obj = yaml.load(yamlText);
            if (!(obj instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("YAML 머리말이 객체 형식이 아닙니다.");
            }
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k != null) {
                    out.put(k.toString().trim(), v);
                }
            });
            return out;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("YAML 파싱 실패: " + e.getClass().getSimpleName());
        }
    }

    private static List<String> parseTypes(Object raw) {
        List<String> list = parseStringList(raw);
        if (list.isEmpty()) {
            throw new IllegalArgumentException("types가 비어 있습니다.");
        }
        List<String> validated = new ArrayList<>(list.size());
        for (String item : list) {
            try {
                validated.add(EmType.valueOf(item.toUpperCase(Locale.ROOT)).name());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("알 수 없는 EmType입니다: " + item);
            }
        }
        return List.copyOf(validated);
    }

    private static Map<String, String> parseTitles(Object raw, String fallbackKey) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> map) {
            map.forEach((k, v) -> {
                if (k != null && v != null && !v.toString().isBlank()) {
                    out.put(k.toString().trim().toLowerCase(Locale.ROOT), v.toString().trim());
                }
            });
        } else if (raw != null && !raw.toString().isBlank()) {
            String single = raw.toString().trim();
            for (String lang : REQUIRED_LANGS) {
                out.put(lang, single);
            }
        }
        for (String lang : REQUIRED_LANGS) {
            out.putIfAbsent(lang, fallbackKey);
        }
        return out;
    }

    private static List<String> parseStringList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> items) {
            List<String> out = new ArrayList<>();
            for (Object item : items) {
                if (item != null && !item.toString().isBlank()) {
                    out.add(item.toString().trim());
                }
            }
            return List.copyOf(out);
        }
        String single = raw.toString().trim();
        return single.isEmpty() ? List.of() : List.of(single);
    }

    private static LocalDate parseDate(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Date d) {
            return d.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        }
        if (raw instanceof LocalDate ld) {
            return ld;
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            throw new IllegalArgumentException("checked 날짜 형식이 잘못되었습니다: " + s);
        }
    }

    private static Map<String, String> splitLangSections(String bodyPart) {
        Matcher m = LANG_HEADING.matcher(bodyPart);
        List<String> langs = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        List<Integer> contentStarts = new ArrayList<>();
        while (m.find()) {
            langs.add(m.group(1));
            starts.add(m.start());
            contentStarts.add(m.end());
        }
        Map<String, String> sections = new LinkedHashMap<>();
        for (int i = 0; i < langs.size(); i++) {
            int from = contentStarts.get(i);
            int to = (i + 1 < starts.size()) ? starts.get(i + 1) : bodyPart.length();
            sections.put(langs.get(i), bodyPart.substring(from, to));
        }
        return sections;
    }

    private record SectionLists(List<String> steps, List<String> facts) {}

    private static SectionLists parseSectionLists(String sectionBody) {
        List<String> steps = new ArrayList<>();
        List<String> facts = new ArrayList<>();
        String mode = null;
        for (String line : sectionBody.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if ("steps:".equals(trimmed)) {
                mode = "steps";
                continue;
            }
            if ("facts:".equals(trimmed)) {
                mode = "facts";
                continue;
            }
            if (trimmed.startsWith("- ") && mode != null) {
                String item = trimmed.substring(2).trim();
                if (!item.isEmpty()) {
                    if ("steps".equals(mode)) {
                        steps.add(item);
                    } else {
                        facts.add(item);
                    }
                }
            }
        }
        return new SectionLists(List.copyOf(steps), List.copyOf(facts));
    }

    private static String buildSearchBody(List<String> steps, List<String> facts) {
        StringBuilder sb = new StringBuilder();
        for (String s : steps) {
            if (!sb.isEmpty()) sb.append('\n');
            sb.append(s);
        }
        for (String f : facts) {
            if (!sb.isEmpty()) sb.append('\n');
            sb.append(f);
        }
        return sb.toString();
    }

    private static List<NamedMarkdown> readClasspathResources(String pattern) {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources(pattern);
            Arrays.sort(resources, Comparator.comparing(r -> r.getFilename() == null ? "" : r.getFilename()));
            List<NamedMarkdown> out = new ArrayList<>();
            for (Resource r : resources) {
                String name = r.getFilename();
                if (name == null || "README.md".equalsIgnoreCase(name)) {
                    continue;
                }
                try (InputStream in = r.getInputStream()) {
                    String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    out.add(new NamedMarkdown(name, content));
                }
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("지식베이스 리소스 목록 읽기 실패: " + pattern, e);
        }
    }

    private static String asNonBlankString(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
