package kr.busan.safetrip.common.ai;

import org.springframework.stereotype.Service;
import kr.busan.safetrip.common.exception.NotImplementedYetException;
import kr.busan.safetrip.common.ai.AiRequest;

/**
 * [공용] COMP-C08 SpringAiService
 * 상태: TODO
 * 수정 규칙: 3인 합의 후 수정(PR 리뷰 3명 모두 승인). 혼자 고치지 말 것.
 *
 * AIService 구현체 자리. provider 설정(ollama|openai)에 따라 호출 대상을 바꾼다. 월 비용 한도(NFR-C009) 초과 방지도 여기서.
 */
@Service
public class SpringAiService implements AIService {
    // TODO(공용·구현 제안: 코스 담당): Spring AI Ollama/OpenAI 연결, 8초 타임아웃·1회 재시도, 호출 로그, 월 한도 체크
    @Override
    public String complete(AiRequest request) {
        throw new NotImplementedYetException("공용", "AIService.complete");

    }
}
