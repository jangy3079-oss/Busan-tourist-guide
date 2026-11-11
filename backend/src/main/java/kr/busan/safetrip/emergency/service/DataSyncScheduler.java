package kr.busan.safetrip.emergency.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.exception.NotImplementedYetException;
import kr.busan.safetrip.emergency.dto.EmergencyDtos;

/**
 * [긴급 담당] COMP-E11 DataSyncScheduler
 * 요구사항: FR-E019, NFR-E007, E008
 * 상태: TODO
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 매일 04:00 약국·응급실 동기화. 실패 시 기존 데이터 유지, sync_log 기록. API-E06 수동 실행도 이 메서드를 부른다.
 */
@Component
public class DataSyncScheduler {
    @Scheduled(cron = "0 0 4 * * *")
    public void scheduled() {
        sync();
    }

    public EmergencyDtos.SyncLog sync() {
        throw new NotImplementedYetException("긴급 담당", "DataSyncScheduler.sync");

    }

    public EmergencyDtos.SyncStatus recent() {
        throw new NotImplementedYetException("긴급 담당", "DataSyncScheduler.recent");

    }
}
