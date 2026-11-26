package kr.busan.safetrip.emergency.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import kr.busan.safetrip.common.exception.BusinessException;
import kr.busan.safetrip.common.exception.ErrorCode;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.ReloadResult;

/**
 * [긴급 담당] COMP-E12 API-E05 지식베이스·사전 반영
 * 요구사항: FR-E022, NFR-E009 (#115)
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 키워드·결정표·슬롯·카드 템플릿·분류 설정·대체 단계·메시지·지식베이스 문서를 모두 읽고 검증한 뒤에만 바꾼다.
 * 하나라도 실패하면 이전 상태를 그대로 두고 INVALID_INPUT(400)으로 이유를 돌려준다.
 * 지식베이스 DB 반영(트랜잭션)을 먼저 하고, 성공했을 때만 메모리를 바꾼다.
 */
@Service
public class KnowledgeReloadService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeReloadService.class);

    private final KeywordRuleEngineImpl keywords;
    private final DecisionTable decisionTable;
    private final KoreanCardBuilder cardBuilder;
    private final SituationClassifier classifier;
    private final FallbackSteps fallbackSteps;
    private final EmergencyMessages messages;
    private final KnowledgeBaseLoader kbLoader;

    public KnowledgeReloadService(
            KeywordRuleEngineImpl keywords,
            DecisionTable decisionTable,
            KoreanCardBuilder cardBuilder,
            SituationClassifier classifier,
            FallbackSteps fallbackSteps,
            EmergencyMessages messages,
            KnowledgeBaseLoader kbLoader) {
        this.keywords = keywords;
        this.decisionTable = decisionTable;
        this.cardBuilder = cardBuilder;
        this.classifier = classifier;
        this.fallbackSteps = fallbackSteps;
        this.messages = messages;
        this.kbLoader = kbLoader;
    }

    public ReloadResult reloadAll() {
        StagedReload kw;
        StagedReload templates;
        List<Runnable> commits;
        KnowledgeBaseLoader.ParseOutcome kb;
        try {
            kw = keywords.prepareReload();
            templates = cardBuilder.prepareReload();
            commits = List.of(
                    kw.commit(),
                    decisionTable.prepareReload().commit(),
                    templates.commit(),
                    classifier.prepareReload(),
                    fallbackSteps.prepareReload().commit(),
                    messages.prepareReload());
            kb = kbLoader.parseStrict();
        } catch (RuntimeException e) {
            log.warn("emergency.reload rejected: {}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.INVALID_INPUT, "재적재 검증 실패(이전 상태 유지): " + e.getMessage());
        }
        int documents = kbLoader.applyParsed(kb);
        commits.forEach(Runnable::run);
        log.info("emergency.reload ok documents={} keywords={} templates={}", documents, kw.count(), templates.count());
        return new ReloadResult(documents, kw.count(), templates.count());
    }
}
