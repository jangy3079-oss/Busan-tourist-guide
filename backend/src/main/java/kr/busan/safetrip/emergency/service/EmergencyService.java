package kr.busan.safetrip.emergency.service;

import java.util.Optional;
import org.springframework.stereotype.Service;
import kr.busan.safetrip.common.exception.NotImplementedYetException;
import kr.busan.safetrip.emergency.dto.EmergencyDtos;

/**
 * [긴급 담당] COMP-E03 EmergencyService
 * 요구사항: FR-E003, E005, E007, E017, E018, NFR-E001~E005
 * 상태: 진행중 (quick 완료, guide TODO)
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 흐름 조율(Facade). quick: 키워드만. guide: 분류 -> 최종 긴급도(LLM OR 키워드) -> 결정표 -> 도움처 -> 카드 -> 할 일. LLM·공공 API 실패해도 저장 데이터로 응답(NFR-E003). 입력·좌표 저장 금지(NFR-E004).
 */
@Service
public class EmergencyService {
    private final KeywordRuleEngineImpl keywords;
    private final SituationClassifier classifier;
    private final DecisionTable decisionTable;
    private final HelpPlaceFinder finder;
    private final KoreanCardBuilder cardBuilder;
    private final GuidanceRetriever guidance;

    public EmergencyService(KeywordRuleEngineImpl keywords, SituationClassifier classifier, DecisionTable decisionTable,
                            HelpPlaceFinder finder, KoreanCardBuilder cardBuilder, GuidanceRetriever guidance) {
        this.keywords = keywords;
        this.classifier = classifier;
        this.decisionTable = decisionTable;
        this.finder = finder;
        this.cardBuilder = cardBuilder;
        this.guidance = guidance;
    }

    /** API-E01 즉시 응답(FR-E003, E005, NFR-E001): 키워드 사전·결정표만 사용해 1초 이내 응답. LLM·DB·외부 API 호출 금지. */
    public EmergencyDtos.QuickResponse quick(EmergencyDtos.QuickRequest req) {
        Optional<KeywordRuleEngineImpl.Match> found = keywords.match(req.text());
        if (found.isEmpty()) {
            return new EmergencyDtos.QuickResponse(false, null, null);
        }
        KeywordRuleEngineImpl.Match match = found.get();
        if (match.urgent()) {
            String topContact = decisionTable.plan(match.type(), match.group()).topContact();
            return new EmergencyDtos.QuickResponse(true, topContact, match.type());
        }
        return new EmergencyDtos.QuickResponse(false, null, match.type());
    }

    public EmergencyDtos.GuideResponse guide(EmergencyDtos.GuideRequest req) {
        throw new NotImplementedYetException("긴급 담당", "EmergencyService.guide");
    }
}
