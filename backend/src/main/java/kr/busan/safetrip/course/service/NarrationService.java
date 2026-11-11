package kr.busan.safetrip.course.service;

import java.util.List;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.exception.NotImplementedYetException;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.course.dto.CourseDtos;

/**
 * [코스 담당] COMP-C06 NarrationService
 * 요구사항: FR-C010, AI-REQ-C01
 * 상태: TODO
 * 수정 규칙: 코스 담당만 수정. 사진·긴급 담당은 수정 금지(필요하면 코스 담당에게 요청).
 *
 * 코스 제목·장소별 설명(60자) 생성 -> OutputValidator 검증 -> 실패 시 템플릿 대체.
 */
@Component
public class NarrationService {
    public CourseDtos.NarrationResponse narrate(String courseId, List<CourseDtos.Stop> stops, Lang lang) {
        throw new NotImplementedYetException("코스 담당", "NarrationService.narrate");

    }
}
