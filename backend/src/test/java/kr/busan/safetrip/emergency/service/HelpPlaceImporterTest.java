package kr.busan.safetrip.emergency.service;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.busan.safetrip.common.exception.BusinessException;
import kr.busan.safetrip.common.exception.ErrorCode;
import kr.busan.safetrip.emergency.dto.EmergencyDtos.ImportResult;
import kr.busan.safetrip.emergency.entity.HelpPlaceEntity;
import kr.busan.safetrip.emergency.repository.HelpPlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.lenient;

/**
 * [긴급 담당] COMP-E12 HelpPlaceImporter 단위 테스트 (FR-E021, DATA-E07, NFR-E009, #110)
 * 실제 데이터 파일을 복사하지 않고 손으로 만든 작은 CSV 견본만 사용한다.
 */
@ExtendWith(MockitoExtension.class)
class HelpPlaceImporterTest {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Clock FIXED_CLOCK =
            Clock.fixed(ZonedDateTime.of(2026, 11, 26, 11, 0, 0, 0, SEOUL).toInstant(), SEOUL);
    private static final Charset CP949 = Charset.forName("MS949");

    @Mock
    private HelpPlaceRepository repo;

    private Map<String, HelpPlaceEntity> store;
    private HelpPlaceImporter importer;

    @BeforeEach
    void setUp() {
        store = new LinkedHashMap<>();
        lenient().when(repo.findBySource("CSV")).thenAnswer(inv -> new ArrayList<>(store.values()));
        lenient().when(repo.saveAll(anyIterable())).thenAnswer(inv -> {
            Iterable<HelpPlaceEntity> items = inv.getArgument(0);
            List<HelpPlaceEntity> saved = new ArrayList<>();
            for (HelpPlaceEntity e : items) {
                store.put(e.getExtId(), e);
                saved.add(e);
            }
            return saved;
        });

        importer = new HelpPlaceImporter(new ImportConfig(), new RegionResolver(), repo, FIXED_CLOCK);
    }

    @Test
    void importsWgs84CsvWithUtf8BomQuotedHoursAndAlwaysOpenRules() {
        String csv = "\uFEFF구분,구,이름,주소,운영,전화,위도,경도\r\n"
                + "지구대,수영구,샘플지구대,부산 수영구 샘플로 1,24시간,051-000-0001,35.1532,129.1186\r\n"
                + "치안센터,해운대구,샘플치안센터,부산 해운대구 샘플로 2,확인 필요,,35.1587,129.1604\r\n";

        ImportResult res = importer.importBytes("POLICE", csv.getBytes(StandardCharsets.UTF_8));

        assertThat(res.inserted()).isEqualTo(2);
        assertThat(res.updated()).isZero();
        assertThat(res.rejected()).isEmpty();
        assertThat(store).hasSize(2);

        List<HelpPlaceEntity> saved = new ArrayList<>(store.values());
        HelpPlaceEntity station = saved.stream().filter(e -> "샘플지구대".equals(e.getName())).findFirst().orElseThrow();
        HelpPlaceEntity center = saved.stream().filter(e -> "샘플치안센터".equals(e.getName())).findFirst().orElseThrow();

        assertThat(station.getDistrict()).isEqualTo("수영구");
        assertThat(station.isAlwaysOpen()).isTrue();
        assertThat(station.getHoursText()).isEqualTo("24시간");
        assertThat(station.getExtId()).hasSize(16);

        assertThat(center.getDistrict()).isEqualTo("해운대구");
        assertThat(center.isAlwaysOpen()).isFalse();
        assertThat(center.getHoursText()).isEqualTo("확인 필요");
    }

    @Test
    void importsQuotedTouristInfoHoursAsIs() {
        String csv = "구분,이름,주소,운영,전화,위도,경도\n"
                + "관광안내소,샘플안내소,부산 수영구 해변로 1,\"10:00~19:00 (7~8월 10:00~22:00), 연중무휴\",051-111-2222,35.1532,129.1186\n";

        ImportResult res = importer.importBytes("TOURIST_INFO", csv.getBytes(StandardCharsets.UTF_8));

        assertThat(res.inserted()).isEqualTo(1);
        assertThat(res.rejected()).isEmpty();
        HelpPlaceEntity saved = store.values().iterator().next();
        assertThat(saved.getKind()).isEqualTo("TOURIST_INFO");
        assertThat(saved.isAlwaysOpen()).isFalse();
        assertThat(saved.getHoursText()).isEqualTo("10:00~19:00 (7~8월 10:00~22:00), 연중무휴");
    }

    @Test
    void importsLocaldataEpsg5174CoordinatesAndSkipsClosedAndOutOfArea() {
        // CoordConverterTest 알려진 점: 광안리(392984.229,185836.829), 해운대(396780.622,186529.052), 서울시청(197986.727,451579.894)
        String csv = "관리번호,영업상태명,사업장명,도로명전체주소,소재지전체주소,소재지전화,좌표정보(x),좌표정보(y)\n"
                + "LD-001,영업/정상,샘플편의점A,부산 수영구 광안로 10,,051-100-0001,392984.229,185836.829\n"
                + "LD-002,영업/정상,샘플편의점B,,부산 해운대구 중동 123,,396780.622,186529.052\n"
                + "LD-003,폐업,폐업편의점,부산 수영구 광안로 11,,,392984.229,185836.829\n"
                + "LD-004,영업/정상,서울편의점,서울 중구 세종대로 110,,,197986.727,451579.894\n";

        ImportResult res = importer.importBytes("CONVENIENCE", csv.getBytes(StandardCharsets.UTF_8));

        assertThat(res.inserted()).isEqualTo(2);
        assertThat(res.updated()).isZero();
        assertThat(res.rejected()).isEmpty(); // 폐업·지역 밖은 건너뛰기만(거절 아님)

        HelpPlaceEntity a = store.get("LD-001");
        HelpPlaceEntity b = store.get("LD-002");
        assertThat(a).isNotNull();
        assertThat(a.getDistrict()).isEqualTo("수영구");
        assertThat(a.getLat()).isCloseTo(35.1532, within(1e-4));
        assertThat(a.getLng()).isCloseTo(129.1186, within(1e-4));
        assertThat(a.isAlwaysOpen()).isTrue(); // DATA-E07 CONVENIENCE 기본 24시간

        assertThat(b).isNotNull();
        assertThat(b.getDistrict()).isEqualTo("해운대구");
        assertThat(b.getAddress()).isEqualTo("부산 해운대구 중동 123"); // 도로명 비었을 때 지번 사용
        assertThat(b.getLat()).isCloseTo(35.1587, within(1e-4));
        assertThat(b.getLng()).isCloseTo(129.1604, within(1e-4));
    }

    @Test
    void decodesCp949CsvAutomaticallyWhenUtf8Fails() {
        String csv = "관리번호,영업상태명,사업장명,도로명전체주소,좌표정보(x),좌표정보(y)\n"
                + "CP-001,영업/정상,광안리편의점,부산 수영구 광안해변로 100,392984.229,185836.829\n";

        ImportResult res = importer.importBytes("CONVENIENCE", csv.getBytes(CP949));

        assertThat(res.inserted()).isEqualTo(1);
        assertThat(res.rejected()).isEmpty();
        assertThat(store.get("CP-001").getName()).isEqualTo("광안리편의점");
        assertThat(store.get("CP-001").getDistrict()).isEqualTo("수영구");
    }

    @Test
    void rejectsMissingNameMissingCoordsAndInvalidProjectedCoordsAndSkipsBusanOutsideDistricts() {
        String csv = "이름,주소,운영,위도,경도,x,y\n"
                + ",부산 수영구 1,24시간,35.1532,129.1186,,\n"          // 2행: 이름 없음
                + "좌표없음소,부산 수영구 2,24시간,,,,\n"                  // 3행: 좌표 없음
                + "변환실패소,부산 수영구 3,24시간,,,0,0\n"                // 4행: 투영 좌표 변환 실패
                + "서면지구대,부산 부산진구 4,24시간,35.1578,129.0591,,\n"  // 5행: 대상 지역 밖 → 건너뛰기
                + "정상지구대,부산 수영구 5,24시간,35.1532,129.1186,,\n";   // 6행: 정상

        ImportResult res = importer.importBytes("POLICE", csv.getBytes(StandardCharsets.UTF_8));

        assertThat(res.inserted()).isEqualTo(1);
        assertThat(res.updated()).isZero();
        assertThat(res.rejected()).containsExactly(
                new ImportResult.Rejected(2, "이름 없음"),
                new ImportResult.Rejected(3, "좌표 없음"),
                new ImportResult.Rejected(4, "좌표 변환 실패"));
    }

    @Test
    void reimportingSameFileOnlyUpdatesExistingRows() {
        String csv = "이름,주소,운영,위도,경도\n"
                + "샘플지구대,부산 수영구 샘플로 1,24시간,35.1532,129.1186\n"
                + "샘플파출소,부산 해운대구 샘플로 2,24시간,35.1587,129.1604\n";
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

        ImportResult first = importer.importBytes("POLICE", bytes);
        ImportResult second = importer.importBytes("POLICE", bytes);

        assertThat(first.inserted()).isEqualTo(2);
        assertThat(first.updated()).isZero();
        assertThat(second.inserted()).isZero();
        assertThat(second.updated()).isEqualTo(2);
        assertThat(store).hasSize(2);
    }

    @Test
    void rejectsDisallowedKindsAndOversizedFileWith400() {
        byte[] valid = "이름,위도,경도\n샘플,35.1532,129.1186\n".getBytes(StandardCharsets.UTF_8);

        for (String badKind : List.of("PHARMACY", "ER", "HOSPITAL", "UNKNOWN", "")) {
            assertThatThrownBy(() -> importer.importBytes(badKind, valid))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).code())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }

        // 크기 상한 초과 모의 MultipartFile (실제 20MB 배열 할당 없이 size만 초과 반환)
        MockMultipartFile oversized = new MockMultipartFile("file", "big.csv", "text/csv", valid) {
            @Override
            public long getSize() {
                return 20L * 1024 * 1024 + 1;
            }
        };
        assertThatThrownBy(() -> importer.importCsv("POLICE", oversized))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void capsRejectedListAt50WithOverflowSummaryLine() {
        StringBuilder sb = new StringBuilder("이름,위도,경도\n");
        for (int i = 0; i < 55; i++) {
            sb.append(",35.1532,129.1186\n"); // 2행~56행 모두 이름 없음 (55건)
        }

        ImportResult res = importer.importBytes("POLICE", sb.toString().getBytes(StandardCharsets.UTF_8));

        assertThat(res.inserted()).isZero();
        assertThat(res.rejected()).hasSize(50);
        assertThat(res.rejected().get(0)).isEqualTo(new ImportResult.Rejected(2, "이름 없음"));
        assertThat(res.rejected().get(48)).isEqualTo(new ImportResult.Rejected(50, "이름 없음"));
        assertThat(res.rejected().get(49)).isEqualTo(new ImportResult.Rejected(0, "외 6건"));
    }
}
