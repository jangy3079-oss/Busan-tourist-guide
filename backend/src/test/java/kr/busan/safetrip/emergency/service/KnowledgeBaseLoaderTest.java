package kr.busan.safetrip.emergency.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import kr.busan.safetrip.common.ai.EmbeddingService;
import kr.busan.safetrip.emergency.repository.KbDocumentRepository;
import kr.busan.safetrip.emergency.repository.KbDocumentRepository.HashState;
import kr.busan.safetrip.emergency.repository.KbDocumentRepository.KbDoc;
import kr.busan.safetrip.emergency.repository.KbDocumentRepository.KeyLang;
import kr.busan.safetrip.emergency.service.KnowledgeBaseLoader.NamedMarkdown;
import kr.busan.safetrip.emergency.service.KnowledgeBaseLoader.ParseOutcome;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [긴급 담당] COMP-E12 KnowledgeBaseLoader: 원고 검증·언어별 행·해시 기반 재임베딩 (FR-E022, DATA-E09, #111 #115)
 */
class KnowledgeBaseLoaderTest {
    private static final Path KB_DIR = Path.of("src/main/resources/emergency/kb");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-26T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    static List<NamedMarkdown> realDocs() {
        try (Stream<Path> files = Files.list(KB_DIR)) {
            List<NamedMarkdown> out = new ArrayList<>();
            for (Path p : files.filter(f -> f.toString().endsWith(".md")).sorted().toList()) {
                out.add(new NamedMarkdown(p.getFileName().toString(), Files.readString(p, StandardCharsets.UTF_8)));
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static String doc(String key, String langs, int steps) {
        StringBuilder sb = new StringBuilder("---\nkey: " + key + "\ntypes: [MILD]\ngroups: [GENERAL]\nstatus: ✅\nchecked: 2026-11-26\nsources:\n  - 테스트 출처\n---\n");
        for (String lang : langs.split(",")) {
            sb.append("## ").append(lang).append("\nsteps:\n");
            for (int i = 1; i <= steps; i++) {
                sb.append("- ").append(lang).append(" step ").append(i).append('\n');
            }
            sb.append("facts:\n- ").append(lang).append(" fact\n\n");
        }
        return sb.toString();
    }

    @Test
    void parsesAllThirteenRepositoryDocumentsInThreeLanguages() {
        ParseOutcome out = KnowledgeBaseLoader.parseAll(realDocs());

        assertThat(out.errors()).isEmpty();
        assertThat(out.activeKeys()).hasSize(13).contains("contact-119", "mild-pharmacy", "way-lost");
        assertThat(out.rows()).hasSize(39);
        KbDoc mildNightJa = out.rows().stream()
                .filter(d -> d.docKey().equals("mild-night") && d.lang().equals("ja")).findFirst().orElseThrow();
        assertThat(mildNightJa.title()).isEqualTo("薬局が閉まっているとき");
        assertThat(mildNightJa.types()).containsExactly("MILD");
        assertThat(mildNightJa.groups()).containsExactly("GENERAL", "NEEDS_MEDS");
        assertThat(mildNightJa.status()).isEqualTo("⚠️");
        assertThat(mildNightJa.steps()).hasSize(3);
        assertThat(mildNightJa.body()).contains(mildNightJa.steps().get(0)).contains("2026年11月時点");
        assertThat(mildNightJa.contentHash()).hasSize(64);
    }

    @Test
    void skipsOnlyTheDocumentMissingALanguage() {
        ParseOutcome out = KnowledgeBaseLoader.parseAll(List.of(
                new NamedMarkdown("a.md", doc("doc-a", "ko,en,ja", 2)),
                new NamedMarkdown("b.md", doc("doc-b", "ko,en", 2))));

        assertThat(out.activeKeys()).containsExactly("doc-a");
        assertThat(out.rows()).hasSize(3);
        assertThat(out.errors()).singleElement().asString().contains("b.md").contains("## ja");
    }

    @Test
    void rejectsDuplicateKeyAndStepCountOutsideTwoToFour() {
        ParseOutcome dup = KnowledgeBaseLoader.parseAll(List.of(
                new NamedMarkdown("a.md", doc("same", "ko,en,ja", 2)),
                new NamedMarkdown("b.md", doc("same", "ko,en,ja", 3))));
        assertThat(dup.rows()).hasSize(3);
        assertThat(dup.errors()).singleElement().asString().contains("중복");

        ParseOutcome counts = KnowledgeBaseLoader.parseAll(List.of(
                new NamedMarkdown("one.md", doc("one", "ko,en,ja", 1)),
                new NamedMarkdown("five.md", doc("five", "ko,en,ja", 5)),
                new NamedMarkdown("four.md", doc("four", "ko,en,ja", 4))));
        assertThat(counts.activeKeys()).containsExactly("four");
        assertThat(counts.errors()).hasSize(2).allSatisfy(e -> assertThat(e).contains("steps"));
    }

    @Test
    void rejectsUnknownEmType() {
        ParseOutcome out = KnowledgeBaseLoader.parseAll(List.of(
                new NamedMarkdown("x.md", doc("x", "ko,en,ja", 2).replace("[MILD]", "[SICK]"))));
        assertThat(out.rows()).isEmpty();
        assertThat(out.errors()).singleElement().asString().contains("SICK");
    }

    @Test
    void doesNotCallEmbeddingWhenHashUnchangedAndEmbeddingExists() {
        KbDocumentRepository repo = mock(KbDocumentRepository.class);
        EmbeddingService emb = mock(EmbeddingService.class);
        List<NamedMarkdown> files = List.of(new NamedMarkdown("a.md", doc("doc-a", "ko,en,ja", 2)));
        Map<KeyLang, HashState> existing = new HashMap<>();
        for (KbDoc d : KnowledgeBaseLoader.parseAll(files).rows()) {
            existing.put(new KeyLang(d.docKey(), d.lang()), new HashState(d.contentHash(), true));
        }
        when(repo.findAllHashStates()).thenReturn(existing);

        int rows = new KnowledgeBaseLoader(repo, emb, () -> files, CLOCK).reloadStrict();

        assertThat(rows).isEqualTo(3);
        verify(emb, never()).embedAll(anyList());
        verify(repo, times(3)).upsertKeepingEmbedding(any(), eq(CLOCK.instant()));
        verify(repo, never()).upsertWithEmbedding(any(), any(), any());
        verify(repo).deleteKeysNotIn(eq(java.util.Set.of("doc-a")));
    }

    @Test
    void reembedsOnlyChangedRowsAndStoresNullWhenEmbeddingServerIsDown() {
        KbDocumentRepository repo = mock(KbDocumentRepository.class);
        EmbeddingService emb = mock(EmbeddingService.class);
        List<NamedMarkdown> files = List.of(new NamedMarkdown("a.md", doc("doc-a", "ko,en,ja", 2)));
        List<KbDoc> rows = KnowledgeBaseLoader.parseAll(files).rows();
        when(repo.findAllHashStates()).thenReturn(Map.of(
                new KeyLang("doc-a", "ko"), new HashState(rows.get(0).contentHash(), true),
                new KeyLang("doc-a", "en"), new HashState("old-hash", true)));
        when(emb.embedAll(anyList())).thenThrow(new IllegalStateException("embedding down"));

        new KnowledgeBaseLoader(repo, emb, () -> files, CLOCK).reloadStrict();

        verify(emb).embedAll(eq(List.of(rows.get(1).body(), rows.get(2).body()))); // en(바뀜) + ja(새 행)
        verify(repo).upsertKeepingEmbedding(eq(rows.get(0)), any());
        verify(repo, times(2)).upsertWithEmbedding(any(), isNull(), any());
    }

    @Test
    void strictModeFailsOnAnyInvalidDocumentWhileLenientModeLoadsTheRest() {
        KbDocumentRepository repo = mock(KbDocumentRepository.class);
        when(repo.findAllHashStates()).thenReturn(Map.of());
        List<NamedMarkdown> files = List.of(
                new NamedMarkdown("a.md", doc("doc-a", "ko,en,ja", 2)),
                new NamedMarkdown("b.md", doc("doc-b", "ko", 2)));
        KnowledgeBaseLoader loader = new KnowledgeBaseLoader(repo, null, () -> files, CLOCK);

        assertThatThrownBy(loader::parseStrict).isInstanceOf(IllegalStateException.class).hasMessageContaining("b.md");
        assertThat(loader.loadLenient()).isEqualTo(3);
        verify(repo, times(3)).upsertWithEmbedding(any(), isNull(), any());
    }
}
