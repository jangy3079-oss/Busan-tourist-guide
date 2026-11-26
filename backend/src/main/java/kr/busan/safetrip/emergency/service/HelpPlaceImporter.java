package kr.busan.safetrip.emergency.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import kr.busan.safetrip.common.exception.BusinessException;
import kr.busan.safetrip.common.exception.ErrorCode;
import kr.busan.safetrip.common.util.CoordConverter;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.ImportResult;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.ImportResult.Rejected;
import kr.busan.safetrip.emergency.entity.HelpPlaceEntity;
import kr.busan.safetrip.emergency.repository.HelpPlaceRepository;

/**
 * [긴급 담당] COMP-E12 HelpPlaceImporter
 * 요구사항: FR-E021, DATA-E05~E07, NFR-E004, NFR-E009 (#110)
 * 상태: 완료
 * 수정 규칙: 긴급 담당만 수정. 코스·사진 담당은 수정 금지(필요하면 긴급 담당에게 요청).
 *
 * 정적 도움처(지구대·파출소·관광안내소·상비약 판매 편의점) CSV를 메모리에서만 읽어
 * 인코딩 판별(UTF-8/CP949), 좌표 변환(EPSG:5174→WGS84), 대상 지역(수영구·해운대구) 필터링 후
 * (source='CSV', ext_id) 키로 help_place에 upsert한다.
 */
@Component
public class HelpPlaceImporter {
    private record CsvRecord(int lineNumber, List<String> fields) {}

    private record ColumnIndex(
            List<Integer> name,
            List<Integer> address,
            List<Integer> phone,
            List<Integer> hours,
            List<Integer> lat,
            List<Integer> lng,
            List<Integer> projX,
            List<Integer> projY,
            List<Integer> extId,
            List<Integer> status) {}

    private static final Logger log = LoggerFactory.getLogger(HelpPlaceImporter.class);
    private static final Charset CP949 = Charset.forName("MS949");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String SOURCE_CSV = "CSV";
    private static final int MAX_NAME_LEN = 200;
    private static final int MAX_ADDRESS_LEN = 300;
    private static final int MAX_PHONE_LEN = 40;
    private static final int MAX_EXT_ID_LEN = 40;

    private final ImportConfig importConfig;
    private final RegionResolver regionResolver;
    private final HelpPlaceRepository helpPlaceRepo;
    private final Clock clock;

    @Autowired
    public HelpPlaceImporter(
            ImportConfig importConfig,
            RegionResolver regionResolver,
            HelpPlaceRepository helpPlaceRepo) {
        this(importConfig, regionResolver, helpPlaceRepo, Clock.system(SEOUL));
    }

    HelpPlaceImporter(
            ImportConfig importConfig,
            RegionResolver regionResolver,
            HelpPlaceRepository helpPlaceRepo,
            Clock clock) {
        this.importConfig = importConfig;
        this.regionResolver = regionResolver;
        this.helpPlaceRepo = helpPlaceRepo;
        this.clock = clock == null ? Clock.system(SEOUL) : clock;
    }

    /** multipart CSV 파일을 메모리에서만 읽어 검증·적재한다. */
    @Transactional
    public ImportResult importCsv(String rawKind, MultipartFile file) {
        ImportConfig.Settings cfg = importConfig.settings();
        String kind = validateKind(rawKind, cfg);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "CSV 파일이 비어 있습니다.");
        }
        if (file.getSize() > cfg.maxBytes()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "CSV 파일 크기가 허용 상한을 넘었습니다.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "CSV 파일을 읽을 수 없습니다.");
        }
        return importBytes(kind, bytes);
    }

    /** 바이트 배열 CSV를 직접 적재한다(테스트·내부 공통 경로). */
    @Transactional
    public ImportResult importBytes(String rawKind, byte[] bytes) {
        ImportConfig.Settings cfg = importConfig.settings();
        String kind = validateKind(rawKind, cfg);
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "CSV 파일이 비어 있습니다.");
        }
        if (bytes.length > cfg.maxBytes()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "CSV 파일 크기가 허용 상한을 넘었습니다.");
        }

        String text = decodeText(bytes);
        List<CsvRecord> records = parseCsv(text);
        if (records.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "CSV 헤더가 비어 있습니다.");
        }

        ColumnIndex idx = resolveColumns(records.get(0).fields(), cfg.columns());
        Instant now = clock.instant();

        // 기존 CSV 행을 ext_id 기준으로 한 번만 읽어 upsert + 파일 내 중복 처리
        Map<String, HelpPlaceEntity> existingByExtId = new HashMap<>();
        List<HelpPlaceEntity> existingList = helpPlaceRepo.findBySource(SOURCE_CSV);
        if (existingList != null) {
            for (HelpPlaceEntity e : existingList) {
                if (e.getExtId() != null) {
                    existingByExtId.put(e.getExtId(), e);
                }
            }
        }

        Map<String, HelpPlaceEntity> toSaveByExtId = new LinkedHashMap<>();
        List<Rejected> allRejected = new ArrayList<>();
        int inserted = 0;
        int updated = 0;
        int skippedStatus = 0;
        int skippedOutOfArea = 0;

        for (int i = 1; i < records.size(); i++) {
            CsvRecord rec = records.get(i);
            int line = rec.lineNumber();
            List<String> fields = rec.fields();

            // 1. 영업상태 필터 (설정된 정상 상태가 아니면 건너뛰기, 거절 아님)
            String status = firstNonBlank(fields, idx.status());
            if (status != null && !cfg.activeStatuses().isEmpty() && !cfg.activeStatuses().contains(status)) {
                skippedStatus++;
                continue;
            }

            // 2. 이름 필수
            String name = firstNonBlank(fields, idx.name());
            if (name == null) {
                allRejected.add(new Rejected(line, "이름 없음"));
                continue;
            }

            // 3. 좌표 추출·변환(WGS84 우선, 없으면 EPSG:5174 변환)
            CoordResult coord = resolveCoords(fields, idx);
            if (coord.error() != null) {
                allRejected.add(new Rejected(line, coord.error()));
                continue;
            }

            // 4. 대상 지역(수영구·해운대구) 필터 — 밖이면 건너뛰기만(거절 목록 제외)
            Optional<String> districtOpt = regionResolver.district(coord.lat(), coord.lng());
            if (districtOpt.isEmpty()) {
                skippedOutOfArea++;
                continue;
            }
            String district = districtOpt.get();

            String address = truncate(firstNonBlank(fields, idx.address()), MAX_ADDRESS_LEN);
            String phone = truncate(firstNonBlank(fields, idx.phone()), MAX_PHONE_LEN);
            String hoursText = firstNonBlank(fields, idx.hours());
            boolean alwaysOpen = resolveAlwaysOpen(kind, hoursText, cfg);
            String cleanName = truncate(name, MAX_NAME_LEN);
            String extId = resolveExtId(kind, cleanName, address, firstNonBlank(fields, idx.extId()));

            HelpPlaceEntity existing = existingByExtId.get(extId);
            if (existing != null) {
                existing.updateFromSource(
                        cleanName,
                        address,
                        phone,
                        coord.lat(),
                        coord.lng(),
                        hoursText,
                        now,
                        district,
                        existing.getHoursJson(),
                        alwaysOpen);
                toSaveByExtId.put(extId, existing);
                updated++;
            } else {
                HelpPlaceEntity created = new HelpPlaceEntity(
                        kind,
                        cleanName,
                        address,
                        phone,
                        coord.lat(),
                        coord.lng(),
                        hoursText,
                        now,
                        SOURCE_CSV,
                        extId,
                        district,
                        null,
                        alwaysOpen);
                existingByExtId.put(extId, created);
                toSaveByExtId.put(extId, created);
                inserted++;
            }
        }

        if (!toSaveByExtId.isEmpty()) {
            helpPlaceRepo.saveAll(toSaveByExtId.values());
        }

        log.info("emergency.import kind={} inserted={} updated={} rejected={} skippedStatus={} skippedOutOfArea={}",
                kind, inserted, updated, allRejected.size(), skippedStatus, skippedOutOfArea);

        return new ImportResult(inserted, updated, capRejected(allRejected, cfg.maxRejected()));
    }

    private static String validateKind(String rawKind, ImportConfig.Settings cfg) {
        if (rawKind == null || rawKind.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "kind 값이 비어 있습니다.");
        }
        String normalized = rawKind.trim().toUpperCase(Locale.ROOT);
        if (!cfg.allowedKinds().contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "허용되지 않는 도움처 종류입니다: " + rawKind.trim());
        }
        return normalized;
    }

    private record CoordResult(double lat, double lng, String error) {}

    private static CoordResult resolveCoords(List<String> fields, ColumnIndex idx) {
        String rawLat = firstNonBlank(fields, idx.lat());
        String rawLng = firstNonBlank(fields, idx.lng());
        if (rawLat != null || rawLng != null) {
            if (rawLat == null || rawLng == null) {
                return new CoordResult(0, 0, "좌표 없음");
            }
            Double lat = parseDouble(rawLat);
            Double lng = parseDouble(rawLng);
            if (lat == null || lng == null || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
                return new CoordResult(0, 0, "좌표 형식 오류");
            }
            return new CoordResult(lat, lng, null);
        }

        String rawX = firstNonBlank(fields, idx.projX());
        String rawY = firstNonBlank(fields, idx.projY());
        if (rawX != null || rawY != null) {
            if (rawX == null || rawY == null) {
                return new CoordResult(0, 0, "좌표 없음");
            }
            Double x = parseDouble(rawX);
            Double y = parseDouble(rawY);
            if (x == null || y == null) {
                return new CoordResult(0, 0, "좌표 형식 오류");
            }
            try {
                CoordConverter.LatLng converted = CoordConverter.epsg5174ToWgs84(x, y);
                return new CoordResult(converted.lat(), converted.lng(), null);
            } catch (IllegalArgumentException e) {
                return new CoordResult(0, 0, "좌표 변환 실패");
            }
        }

        return new CoordResult(0, 0, "좌표 없음");
    }

    private static boolean resolveAlwaysOpen(String kind, String hoursText, ImportConfig.Settings cfg) {
        if (hoursText != null && !hoursText.isBlank()) {
            String lower = hoursText.toLowerCase(Locale.ROOT);
            for (String kw : cfg.alwaysOpenKeywords()) {
                if (lower.contains(kw)) {
                    return true;
                }
            }
            return false;
        }
        return cfg.defaultAlwaysOpenKinds().contains(kind);
    }

    static String resolveExtId(String kind, String name, String address, String rawExtId) {
        if (rawExtId != null && !rawExtId.isBlank()) {
            String trimmed = rawExtId.trim();
            if (trimmed.length() <= MAX_EXT_ID_LEN) {
                return trimmed;
            }
            return sha1Prefix16(trimmed);
        }
        String seed = kind + "|" + name + "|" + (address == null ? "" : address);
        return sha1Prefix16(seed);
    }

    private static String sha1Prefix16(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", digest[i] & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 알고리즘을 사용할 수 없습니다.", e);
        }
    }

    private static List<Rejected> capRejected(List<Rejected> all, int maxRejected) {
        if (all.size() <= maxRejected) {
            return List.copyOf(all);
        }
        int kept = Math.max(0, maxRejected - 1);
        int overflow = all.size() - kept;
        List<Rejected> capped = new ArrayList<>(maxRejected);
        for (int i = 0; i < kept; i++) {
            capped.add(all.get(i));
        }
        capped.add(new Rejected(0, "외 " + overflow + "건"));
        return List.copyOf(capped);
    }

    /** UTF-8(BOM 제거)로 엄격하게 디코딩하고 깨지면 CP949(MS949)로 다시 읽는다. */
    static String decodeText(byte[] bytes) {
        try {
            String utf8 = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return stripBom(utf8);
        } catch (CharacterCodingException e) {
            return stripBom(new String(bytes, CP949));
        }
    }

    private static String stripBom(String text) {
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            return text.substring(1);
        }
        return text;
    }

    /** RFC-4180 CSV 파서(큰따옴표·이중따옴표 이스케이프·필드 내 쉼표/개행 지원, 1-based 행번호 유지). */
    static List<CsvRecord> parseCsv(String text) {
        List<CsvRecord> records = new ArrayList<>();
        int len = text.length();
        int i = 0;
        int line = 1;

        while (i < len) {
            // 완전히 빈 줄 건너뛰기
            if (text.charAt(i) == '\r' || text.charAt(i) == '\n') {
                if (text.charAt(i) == '\r' && i + 1 < len && text.charAt(i + 1) == '\n') {
                    i += 2;
                } else {
                    i++;
                }
                line++;
                continue;
            }

            int recordStartLine = line;
            List<String> fields = new ArrayList<>();
            boolean recordDone = false;

            while (!recordDone) {
                StringBuilder field = new StringBuilder();
                if (i < len && text.charAt(i) == '"') {
                    i++; // 여는 따옴표
                    while (i < len) {
                        char c = text.charAt(i);
                        if (c == '"') {
                            if (i + 1 < len && text.charAt(i + 1) == '"') {
                                field.append('"');
                                i += 2;
                            } else {
                                i++; // 닫는 따옴표
                                break;
                            }
                        } else {
                            if (c == '\r') {
                                if (i + 1 < len && text.charAt(i + 1) == '\n') {
                                    i++;
                                }
                                field.append('\n');
                                line++;
                            } else if (c == '\n') {
                                field.append('\n');
                                line++;
                            } else {
                                field.append(c);
                            }
                            i++;
                        }
                    }
                    // 닫는 따옴표 뒤 쉼표/개행 전까지 공백 무시
                    while (i < len && text.charAt(i) != ',' && text.charAt(i) != '\r' && text.charAt(i) != '\n') {
                        i++;
                    }
                } else {
                    while (i < len && text.charAt(i) != ',' && text.charAt(i) != '\r' && text.charAt(i) != '\n') {
                        field.append(text.charAt(i));
                        i++;
                    }
                }
                fields.add(field.toString());

                if (i < len && text.charAt(i) == ',') {
                    i++;
                } else {
                    if (i < len && text.charAt(i) == '\r') {
                        i++;
                        if (i < len && text.charAt(i) == '\n') {
                            i++;
                        }
                        line++;
                    } else if (i < len && text.charAt(i) == '\n') {
                        i++;
                        line++;
                    }
                    recordDone = true;
                }
            }

            if (hasAnyContent(fields)) {
                records.add(new CsvRecord(recordStartLine, fields));
            }
        }
        return records;
    }

    private static boolean hasAnyContent(List<String> fields) {
        for (String f : fields) {
            if (f != null && !f.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static ColumnIndex resolveColumns(List<String> headers, ImportConfig.Columns cfgCols) {
        Map<String, Integer> headerToIndex = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String norm = stripBom(headers.get(i)).trim().toLowerCase(Locale.ROOT);
            if (!norm.isEmpty()) {
                headerToIndex.putIfAbsent(norm, i);
            }
        }
        return new ColumnIndex(
                matchIndices(headerToIndex, cfgCols.name()),
                matchIndices(headerToIndex, cfgCols.address()),
                matchIndices(headerToIndex, cfgCols.phone()),
                matchIndices(headerToIndex, cfgCols.hours()),
                matchIndices(headerToIndex, cfgCols.lat()),
                matchIndices(headerToIndex, cfgCols.lng()),
                matchIndices(headerToIndex, cfgCols.projX()),
                matchIndices(headerToIndex, cfgCols.projY()),
                matchIndices(headerToIndex, cfgCols.extId()),
                matchIndices(headerToIndex, cfgCols.status()));
    }

    private static List<Integer> matchIndices(Map<String, Integer> headerToIndex, List<String> aliases) {
        List<Integer> matched = new ArrayList<>();
        for (String alias : aliases) {
            Integer idx = headerToIndex.get(alias);
            if (idx != null && !matched.contains(idx)) {
                matched.add(idx);
            }
        }
        return List.copyOf(matched);
    }

    private static String firstNonBlank(List<String> fields, List<Integer> indices) {
        for (int idx : indices) {
            if (idx >= 0 && idx < fields.size()) {
                String v = fields.get(idx);
                if (v != null && !v.isBlank()) {
                    return v.trim();
                }
            }
        }
        return null;
    }

    private static Double parseDouble(String raw) {
        try {
            double v = Double.parseDouble(raw.trim());
            return Double.isFinite(v) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }
}
