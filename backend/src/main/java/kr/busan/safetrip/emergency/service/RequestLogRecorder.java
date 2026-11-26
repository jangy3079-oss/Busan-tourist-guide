package kr.busan.safetrip.emergency.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.Urgency;
import kr.busan.safetrip.emergency.entity.RequestLogEntity;
import kr.busan.safetrip.emergency.repository.RequestLogRepository;

/**
 * [긴급 담당] 요청 기록 request_log (T13, #118)
 * 요구사항: NFR-E004, NFR-E003, NFR-E001, AC-E07
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * API·유형·긴급도·처리시간·대체 경로만 남긴다. 문장·좌표·주소·장소 이름은 인자로도 받지 않는다.
 * 저장은 작은 백그라운드 큐에서 하고 실패·큐 초과는 버린다. DB가 느리거나 죽어도 E01(1초)·E02 응답을 막지 않는다.
 * 30일 지난 행은 RequestLogPurger 가 지운다.
 */
@Component
public class RequestLogRecorder {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawConfig(Integer maxFallbackChars, Integer queueCapacity, List<String> fallbackCodes) {}

    private static final Logger log = LoggerFactory.getLogger(RequestLogRecorder.class);
    private static final String DEFAULT_RESOURCE = "/emergency/request-log.json";

    private final RequestLogRepository repo;
    private final Clock clock;
    private final Executor executor;
    private final List<String> codes;
    private final int maxFallbackChars;

    @Autowired
    public RequestLogRecorder(RequestLogRepository repo) {
        this(repo, Clock.systemUTC(), null, DEFAULT_RESOURCE);
    }

    /** executor 가 null 이면 설정의 queueCapacity 로 데몬 스레드 1개짜리 큐를 만든다 */
    RequestLogRecorder(RequestLogRepository repo, Clock clock, Executor executor, String resource) {
        RawConfig cfg = read(resource);
        if (cfg == null || cfg.fallbackCodes() == null || cfg.fallbackCodes().isEmpty()
                || cfg.maxFallbackChars() == null || cfg.maxFallbackChars() <= 0) {
            throw new IllegalStateException("request-log.json 설정이 올바르지 않습니다: " + resource);
        }
        this.repo = repo;
        this.clock = clock;
        this.codes = List.copyOf(cfg.fallbackCodes());
        this.maxFallbackChars = cfg.maxFallbackChars();
        this.executor = executor != null ? executor : queue(cfg.queueCapacity() == null ? 200 : cfg.queueCapacity());
    }

    /** api: "E01" | "E02". fallback: 이 요청에서 일어난 대체 코드(목록에 없는 값은 버림) */
    public void record(String api, EmType emType, Urgency urgency, long elapsedMs, Collection<String> fallback) {
        RequestLogEntity row = new RequestLogEntity(
                api,
                emType == null ? null : emType.name(),
                urgency == null ? null : urgency.name(),
                (int) Math.min(Math.max(elapsedMs, 0), Integer.MAX_VALUE),
                joinFallback(fallback),
                clock.instant());
        try {
            executor.execute(() -> save(row));
        } catch (RejectedExecutionException e) {
            log.warn("emergency.requestLog dropped: queueFull");
        }
    }

    private void save(RequestLogEntity row) {
        try {
            repo.save(row);
        } catch (RuntimeException e) {
            log.warn("emergency.requestLog failed: {}", e.getClass().getSimpleName());
        }
    }

    /** 설정 순서대로 잇고, 길이 상한을 넘는 코드부터 뺀다. 없으면 null */
    String joinFallback(Collection<String> fallback) {
        if (fallback == null || fallback.isEmpty()) {
            return null;
        }
        List<String> kept = new ArrayList<>();
        int len = 0;
        for (String code : codes) {
            if (!fallback.contains(code)) continue;
            int add = (kept.isEmpty() ? 0 : 1) + code.length();
            if (len + add > maxFallbackChars) continue;
            kept.add(code);
            len += add;
        }
        return kept.isEmpty() ? null : String.join(",", kept);
    }

    private static Executor queue(int capacity) {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(capacity), r -> {
            Thread t = new Thread(r, "emergency-request-log");
            t.setDaemon(true);
            return t;
        });
        return pool;
    }

    private static RawConfig read(String path) {
        try (InputStream in = RequestLogRecorder.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("요청 기록 설정을 찾을 수 없음: " + path);
            }
            return new ObjectMapper().readValue(in, RawConfig.class);
        } catch (IOException e) {
            throw new UncheckedIOException("요청 기록 설정 읽기 실패: " + path, e);
        }
    }
}
