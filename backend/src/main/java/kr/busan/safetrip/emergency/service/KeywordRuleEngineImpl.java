package kr.busan.safetrip.emergency.service;

import java.util.Optional;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.exception.NotImplementedYetException;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.common.keyword.KeywordRuleEngine;

/**
 * [긴급 담당] COMP-E04 KeywordRuleEngineImpl
 * 요구사항: FR-E005, NFR-E009
 * 상태: TODO
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 위급 키워드 판정 구현(공용 인터페이스 KeywordRuleEngine). 사전은 DB 데이터로(코드 수정 없이 변경, NFR-E009).
 */
@Component
public class KeywordRuleEngineImpl implements KeywordRuleEngine {
    @Override
    public Optional<EmType> detect(String text) {
        throw new NotImplementedYetException("긴급 담당", "KeywordRuleEngineImpl.detect");

    }
}
