# 긴급 데이터 적재·동기화 가이드 (`[긴급 담당]`)

부산 수영구·해운대구 긴급상황 대응 기능에서 사용하는 도움처(`help_place`), 공휴일(`holiday`), 지식베이스(`kb_document`) 기준 데이터의 출처·적재 순서·복사 방법을 정리함.
관련 요구사항: `FR-E010`, `FR-E019`, `FR-E021`, `FR-E022`, `NFR-E003`, `NFR-E005`, `NFR-E007`, `NFR-E008`, `DATA-E01~E11`, `#205`.

---

## 1. 긴급 데이터 종류와 출처

| 테이블 | 구분(`kind` / 용도) | 출처 | 적재 방식 | 기준 행 수 |
|---|---|---|---|---:|
| `help_place` | `PHARMACY` (약국) | 국립중앙의료원(NMC) 전국 약국 정보 조회 API | `API-E06` 동기화(`NMC`) | 285 |
| `help_place` | `ER` (응급실) | 국립중앙의료원(NMC) 전국 응급의료기관 정보 조회 API | `API-E06` 동기화(`NMC`) | 6 |
| `help_place` | `POLICE` (지구대·파출소) | 경찰청 전국 지구대·파출소 주소 현황 CSV (V-World 좌표 변환) | `API-E04` CSV 적재(`CSV`) | 16 |
| `help_place` | `TOURIST_INFO` (관광안내소) | visitbusan.net 관광안내소 안내 CSV (기본 운영시간 `10:00~19:00`) | `API-E04` CSV 적재(`CSV`) | 4 |
| `help_place` | `CONVENIENCE` (상비약 편의점) | 지방행정 인허가(LOCALDATA) 안전상비의약품 판매업소 CSV (`EPSG:5174`→`WGS84`) | `API-E04` CSV 적재(`CSV`) | 528 |
| `holiday` | 법정 공휴일 | 한국천문연구원(KASI) 특일 정보 API (`isHoliday=Y`) | `API-E06` 동기화(`KASI`) | 22 |
| `kb_document` | '지금 할 일' 지식베이스 | 저장소 원고 `backend/src/main/resources/emergency/kb/*.md` (13문서 × 3언어) | 서버 기동 시 자동 적재 / `API-E05` | 39 |

- `help_place` 합계는 **839행**(약국 285 + 응급실 6 + 지구대·파출소 16 + 관광안내소 4 + 편의점 528)임.
- 수집 원본 CSV와 API 키는 저장소·이슈·메신저에 올리지 않음.

---

## 2. 필요한 환경변수 (키)

`backend/.env`에 설정함(값은 개인 보관, 커밋 금지).

| 환경변수 | 용도 | 비고 |
|---|---|---|
| `NMC_API_KEY` | 국립중앙의료원 약국·응급실 동기화(`API-E06`) 및 실시간 병상 조회 | data.go.kr 인증키(일 1,000건 한도) |
| `KASI_API_KEY` | 한국천문연구원 특일(공휴일) 정보 조회(`API-E06`) | 비우면 `NMC_API_KEY` 사용 |
| `VWORLD_API_KEY` | 한국어 카드 좌표→주소 변환(`ReverseGeocoder`) | 적재 필수 아님(런타임 사용) |

- 지식베이스 임베딩(`kb_document.embedding`) 생성을 위해 로컬 Ollama(`bge-m3`, `:11434`)를 켜 두어야 함.

---

## 3. 처음부터 적재하는 순서와 확인 (`local-admin` 프로필)

관리자 API(`API-E04~E07`)는 `@Profile("local-admin")`에서만 활성화함(`NFR-E005`).

### 3.1 서버 실행 (로컬 DB 기준)
```bash
cd backend
set -a; . ./.env; set +a
mvn spring-boot:run -Dspring-boot.run.profiles=local-admin
```

### 3.2 적재 순서
```bash
# 1) 약국·응급실(NMC) 및 공휴일(KASI) 동기화 (API-E06)
curl -X POST http://localhost:8080/api/v1/admin/emergency/sync

# 2) 지구대·파출소 CSV 적재 (API-E04, kind=POLICE)
curl -X POST "http://localhost:8080/api/v1/admin/emergency/places/import?kind=POLICE" \
  -F "file=@/경로/help_places_geocoded.csv"

# 3) 관광안내소 CSV 적재 (API-E04, kind=TOURIST_INFO, 10:00~19:00 기본 시간 반영본)
curl -X POST "http://localhost:8080/api/v1/admin/emergency/places/import?kind=TOURIST_INFO" \
  -F "file=@/경로/help_places_geocoded.csv"

# 4) 상비약 판매 편의점 CSV 적재 (API-E04, kind=CONVENIENCE)
curl -X POST "http://localhost:8080/api/v1/admin/emergency/places/import?kind=CONVENIENCE" \
  -F "file=@/경로/건강_안전상비의약품 판매업소_부산광역시.csv"

# 5) 지식베이스·키워드·결정표·카드 템플릿 재적재 (API-E05, 서버 기동 시에도 자동 적재됨)
curl -X POST http://localhost:8080/api/v1/admin/emergency/knowledge/reload
```

### 3.3 적재 결과 확인
```bash
# 동기화 최근 이력 확인 (API-E07)
curl http://localhost:8080/api/v1/admin/emergency/sync/status
```
```sql
SELECT kind, source, count(*) FROM help_place GROUP BY 1, 2 ORDER BY 1, 2;
SELECT count(*) FROM holiday;
SELECT count(*) FROM kb_document;
```
- 정상 기준 숫자: `PHARMACY(NMC)=285`, `ER(NMC)=6`, `POLICE(CSV)=16`, `TOURIST_INFO(CSV)=4`, `CONVENIENCE(CSV)=528` (합계 `839`), `holiday=22`, `kb_document=39`.

---

## 4. 자동 동기화 (매일 04:00)

- `DataSyncScheduler`가 매일 `04:00` (`Asia/Seoul`, `@Scheduled(cron = "0 0 4 * * *")`)에 NMC 약국·응급실과 KASI 공휴일을 자동 동기화함(`FR-E019`, `NFR-E008`).
- 구별 API 실패 또는 기존 행 대비 급감 시 해당 종류의 기존 데이터를 유지해 장애 시 안내를 보존함(`NFR-E003`).
- 동기화 이력은 `sync_log`에 기록하며 `API-E07`로 최근 10건을 조회함.

---

## 5. 공용 DB 적재 규칙 (`#140`)

- 스키마 변경(Flyway)은 김동현 담당만 수행함. 긴급 기준 데이터를 공용 DB에 적재할 때는 반드시 `SPRING_FLYWAY_ENABLED=false`로 서버를 띄움.
- 공용 DB에는 기준 데이터(`help_place`, `holiday`, `kb_document`)만 적재함.
- 테스트 데이터, 상황판 이벤트(`emergency_event`), 요청 기록(`request_log`), 전화 클릭 기록(`call_event`)은 공용 DB에 넣지 않으며, 적재 작업 중 공용 DB를 대상으로 `API-E02`·`API-E08`을 호출하지 않음(`E02` 동작 확인은 로컬 DB에서만 수행함).

```bash
# 공용 DB 대상 실행 예시 (접속 정보 파일은 저장소 밖에 보관)
cd backend && set -a && . ./.env && . /경로/supabase.env && set +a
SPRING_FLYWAY_ENABLED=false mvn spring-boot:run \
  -Dspring-boot.run.profiles=local-admin \
  -Dspring-boot.run.arguments=--server.port=8085
```

---

## 6. 키·CSV 없이 로컬 DB로 복사하는 방법

### 방법 A: 공용 DB에서 로컬 Docker DB로 복사
- 2026-11-26 기준 공용 DB에는 긴급 데이터를 올리지 않았음(로컬 위주 개발). 공용 DB에 적재한 뒤에만 쓸 수 있음. 지금은 방법 B를 씀
```bash
pg_dump --data-only --column-inserts -t help_place -t holiday "$공용URL" > em_seed.sql
docker exec -i busan-tourist-guide-db-1 psql -v ON_ERROR_STOP=1 -U safetrip -d safetrip < em_seed.sql
```

### 방법 B: 전달받은 `em_seed.sql` 파일로 로컬 DB에 적재
1. `docker compose up -d`로 로컬 DB를 켜고, 서버를 한 번 띄워 Flyway 스키마(`V1`~`V35`)를 생성함.
2. 개인적으로 전달받은 `em_seed.sql`(저장소·단톡 공유 금지)을 로컬 Docker DB에 적재함.
```bash
docker exec -i busan-tourist-guide-db-1 psql -v ON_ERROR_STOP=1 -U safetrip -d safetrip < em_seed.sql
```
3. `kb_document`(39행)는 서버를 켜면 저장소 원고(`kb/*.md`)에서 자동 적재되므로 별도 덤프가 필요 없음.
4. 위 3.3절 확인 쿼리로 `help_place` 839행·`holiday` 22행·`kb_document` 39행을 확인함.
