package kr.busan.safetrip.emergency.service;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.Triage;

/**
 * [긴급 담당] API-E02 끝에서 상황판 이벤트 한 건을 남긴다 (T17b, #176)
 * 요구사항: NFR-E004, 팀 결정(11/26 A안: 구·동·약 300m 격자)
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 좌표·V-World 주소는 이 안에서 격자·동으로만 바꾸고 버린다. 문장·좌표·주소·장소 이름은 저장기로 넘기지 않는다.
 * 실패하면 null(안내는 그대로).
 */
@Component
public class DashboardEventWriter {
    private static final Logger log = LoggerFactory.getLogger(DashboardEventWriter.class);

    private final EmergencyEventRecorder recorder;
    private final DashboardConfig config;

    public DashboardEventWriter(EmergencyEventRecorder recorder, DashboardConfig config) {
        this.recorder = recorder;
        this.config = config;
    }

    /**
     * @param lat,lng 실제 좌표일 때만(지역 선택이면 null → 격자 없음)
     * @param vworldAddress V-World 가 준 주소(대체 주소면 null → 동 없음)
     * @return eventId 또는 null
     */
    public String write(Triage triage, Lang lang, String district, Double lat, Double lng, String vworldAddress,
                        String topContact, boolean userSelectedType, boolean llmFailed, boolean stepsFallback) {
        try {
            String gridId = lat == null || lng == null ? null : GridUtil.gridId(lat, lng, config.settings());
            String dong = DongExtractor.extract(vworldAddress);
            Map<String, String> slots = triage.slots() == null ? Map.of() : triage.slots();
            return recorder.record(triage.type(), triage.urgency(), lang, district, dong, gridId, topContact, slots,
                            userSelectedType, llmFailed, stepsFallback)
                    .map(Object::toString)
                    .orElse(null);
        } catch (RuntimeException e) {
            log.warn("emergency.dashboard write failed: {}", e.getClass().getSimpleName());
            return null;
        }
    }
}
