package kr.busan.safetrip.emergency.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import kr.busan.safetrip.common.exception.BusinessException;
import kr.busan.safetrip.common.exception.ErrorCode;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.ReloadResult;
import kr.busan.safetrip.emergency.repository.KbDocumentRepository;
import kr.busan.safetrip.emergency.service.KnowledgeBaseLoader.NamedMarkdown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * [긴급 담당] API-E05 재적재: 성공 응답 숫자, 하나라도 깨지면 이전 상태 유지 (FR-E022, NFR-E009, #115)
 */
class KnowledgeReloadServiceTest {

    private final KbDocumentRepository repo = mock(KbDocumentRepository.class);
    private final List<String> events = new ArrayList<>();

    private KnowledgeReloadService service(KeywordRuleEngineImpl keywords, DecisionTable decision, List<NamedMarkdown> kbFiles) {
        when(repo.findAllHashStates()).thenReturn(Map.of());
        KnowledgeBaseLoader loader = new KnowledgeBaseLoader(repo, null, () -> kbFiles, Clock.systemUTC());
        KoreanCardBuilder card = new KoreanCardBuilder();
        return new KnowledgeReloadService(keywords, decision, card,
                new SituationClassifier(null, card), new FallbackSteps(), new EmergencyMessages(), new GuidanceRetriever(), loader);
    }

    @Test
    void reloadsEverythingAndReturnsDocumentKeywordAndTemplateCounts() {
        KeywordRuleEngineImpl keywords = new KeywordRuleEngineImpl();
        int keywordCount = keywords.reload();
        int templateCount = new KoreanCardBuilder().reload();

        ReloadResult r = service(keywords, new DecisionTable(), KnowledgeBaseLoaderTest.realDocs()).reloadAll();

        assertThat(r.documents()).isEqualTo(39); // 13문서 × 3언어
        assertThat(r.keywords()).isEqualTo(keywordCount).isPositive();
        assertThat(r.templates()).isEqualTo(templateCount).isPositive();
        verify(repo).deleteKeysNotIn(any());
    }

    @Test
    void brokenKnowledgeFileKeepsPreviousStateEverywhere() {
        KeywordRuleEngineImpl keywords = new KeywordRuleEngineImpl();
        DecisionTable decision = spy(new DecisionTable());
        when(decision.prepareReload()).thenAnswer(inv -> {
            StagedReload real = (StagedReload) inv.callRealMethod();
            return new StagedReload(real.count(), () -> { events.add("memory"); real.commit().run(); });
        });
        List<NamedMarkdown> broken = List.of(new NamedMarkdown("bad.md", "---\nkey: bad\ntypes: [MILD]\n---\n## ko\nsteps:\n- 하나\n"));

        assertThatThrownBy(() -> service(keywords, decision, broken).reloadAll())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).code()).isEqualTo(ErrorCode.INVALID_INPUT))
                .hasMessageContaining("bad.md");

        assertThat(events).isEmpty(); // 메모리 교체 없음
        verify(repo, never()).upsertWithEmbedding(any(), any(), any()); // DB 변경 없음
        verify(repo, never()).deleteKeysNotIn(any());
    }

    @Test
    void appliesKnowledgeBaseToDatabaseBeforeSwappingMemory() {
        DecisionTable decision = spy(new DecisionTable());
        when(decision.prepareReload()).thenAnswer(inv -> {
            StagedReload real = (StagedReload) inv.callRealMethod();
            return new StagedReload(real.count(), () -> { events.add("memory"); real.commit().run(); });
        });

        when(repo.deleteKeysNotIn(any())).thenAnswer(inv -> { events.add("db"); return 0; });
        service(new KeywordRuleEngineImpl(), decision, KnowledgeBaseLoaderTest.realDocs()).reloadAll();

        assertThat(events).containsExactly("db", "memory");
    }
}
