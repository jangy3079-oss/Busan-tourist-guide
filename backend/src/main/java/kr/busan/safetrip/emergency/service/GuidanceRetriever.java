package kr.busan.safetrip.emergency.service;

import java.util.List;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.exception.NotImplementedYetException;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.emergency.dto.EmergencyDtos;

/**
 * [긴급 담당] COMP-E10 GuidanceRetriever
 * 요구사항: FR-E016, AI-REQ-E03
 * 상태: TODO
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 지식베이스 Top-3 검색 -> 근거만으로 2~3줄. 근거 없으면 저장된 기본 문구(stepsFallback=true, FR-E016).
 */
@Component
public class GuidanceRetriever {
    public record Guidance(List<String> steps, boolean fallback, List<EmergencyDtos.Source> sources) {}

    public Guidance retrieve(EmType type, String text, Lang lang) {
        throw new NotImplementedYetException("긴급 담당", "GuidanceRetriever.retrieve");

    }
}
