package kr.busan.safetrip.emergency.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import kr.busan.safetrip.common.api.EmType;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.Urgency;
import kr.busan.safetrip.emergency.entity.EmergencyEventEntity;
import kr.busan.safetrip.emergency.repository.EmergencyEventRepository;

/**
 * [긴급 담당] 상황판 이벤트 저장기. 원문·정밀 좌표·주소는 입력으로 받지 않는다.
 */
@Component
public class EmergencyEventRecorder {
    private static final Logger log = LoggerFactory.getLogger(EmergencyEventRecorder.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> DISTRICTS = Set.of("수영구", "해운대구");
    private static final Set<String> SLOT_KEYS = Set.of("who", "symptom", "item", "how", "situation");
    private static final Set<String> CONTACTS = Set.of("112", "119", "1330");

    private final EmergencyEventRepository repository;
    private final DashboardConfig.Settings settings;
    private final Clock clock;
    private final Supplier<UUID> idSupplier;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public EmergencyEventRecorder(
            EmergencyEventRepository repository,
            DashboardConfig config,
            ApplicationEventPublisher eventPublisher) {
        this(repository, config, Clock.systemUTC(), UUID::randomUUID, eventPublisher);
    }

    EmergencyEventRecorder(
            EmergencyEventRepository repository,
            DashboardConfig config,
            Clock clock,
            Supplier<UUID> idSupplier) {
        this(repository, config, clock, idSupplier, event -> {});
    }

    EmergencyEventRecorder(
            EmergencyEventRepository repository,
            DashboardConfig config,
            Clock clock,
            Supplier<UUID> idSupplier,
            ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.settings = config.settings();
        this.clock = clock;
        this.idSupplier = idSupplier;
        this.eventPublisher = eventPublisher;
    }

    public Optional<UUID> record(
            EmType type,
            Urgency urgency,
            Lang lang,
            String district,
            String dong,
            String gridId,
            String topContact,
            Map<String, String> slotCodes,
            boolean userSelectedType,
            boolean llmFailed,
            boolean stepsFallback) {
        if (type == null || urgency == null || lang == null || topContact == null || !CONTACTS.contains(topContact)) {
            log.warn("emergency.dashboard record skipped: invalid required code");
            return Optional.empty();
        }
        try {
            UUID id = idSupplier.get();
            EmergencyEventEntity event = new EmergencyEventEntity(
                    id,
                    bucket(clock.instant(), settings.timeBucketSec()),
                    "E02",
                    type.name(),
                    urgency.name(),
                    lang.name().toLowerCase(Locale.ROOT),
                    district != null && DISTRICTS.contains(district) ? district : null,
                    DongExtractor.extract(dong),
                    GridUtil.isValidGridId(gridId) ? gridId : null,
                    topContact,
                    serializeSlots(slotCodes),
                    userSelectedType,
                    llmFailed,
                    stepsFallback);
            EmergencyEventEntity saved = repository.save(event);
            if (saved == null || saved.getId() == null) {
                return Optional.empty();
            }
            DashboardEvent dashboardEvent = new DashboardEvent(
                    saved.getId(), saved.getCreatedAt(), saved.getEmType(), saved.getUrgency(),
                    saved.getDistrict(), saved.getDong(), saved.getGridId(), saved.getTopContact(),
                    saved.isLlmFailed(), saved.isStepsFallback());
            try {
                eventPublisher.publishEvent(new DashboardEventRecorded(dashboardEvent));
            } catch (RuntimeException e) {
                log.warn("emergency.dashboard event publish failed: {}", e.getClass().getSimpleName());
            }
            return Optional.of(saved.getId());
        } catch (RuntimeException e) {
            log.warn("emergency.dashboard record failed: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static Instant bucket(Instant instant, int seconds) {
        long bucket = Math.floorDiv(instant.getEpochSecond(), seconds) * seconds;
        return Instant.ofEpochSecond(bucket);
    }

    private static String serializeSlots(Map<String, String> slotCodes) {
        Map<String, String> safeSlots = new LinkedHashMap<>();
        if (slotCodes != null) {
            slotCodes.forEach((key, value) -> {
                if (key != null && SLOT_KEYS.contains(key)
                        && value != null && value.matches("[A-Z0-9_]{1,32}")) {
                    safeSlots.put(key, value);
                }
            });
        }
        try {
            return JSON.writeValueAsString(safeSlots);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("상황판 슬롯 코드를 직렬화하지 못했습니다.", e);
        }
    }
}
