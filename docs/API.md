# API 계약 v1.0 (확정안)

[공용] 이 문서, `frontend/lib/data/models/models.dart`, `backend/.../*Dtos.java` 세 곳은 한 묶음이다. 하나를 바꾸면 셋을 같은 커밋에서 고친다.

## 근거
- **경로·흐름**: SDD v0.5 4.2 시퀀스 다이어그램(SD-C01, C02, P01, P02, E01, E02, X01)에 적힌 경로를 그대로 썼다.
- **필드**: SRS v1.4 FR 항목이 화면에 표시하라고 요구한 값에서 뽑았다(표마다 근거 FR 표기).
- **SDD에 경로가 없던 ID**: API-C04, C05, C06, E04~E07은 SDD 추적표의 FR 연결만 있고 경로가 없어 이 문서에서 정했다. API-E03은 SDD 어디에도 연결된 FR이 없어 **미배정**으로 둔다.

## 공통 규칙
| 항목 | 규칙 |
|---|---|
| Base URL | `/api/v1` |
| 형식 | JSON(UTF-8). 사진 업로드만 `multipart/form-data` |
| 언어 | 요청 필드 `lang` = `ko` \| `en` \| `ja` (기본 `ko`) |
| 인증 | 없음(로그인 없음). 관리자 API(`/api/v1/admin/**`)는 `local-admin` 프로필에서만 존재 |
| 열거값 | 대문자 스네이크(`CONFIRMED`, `URGENT` …). 프론트는 소문자 enum으로 변환 |
| 시각 | `HH:mm`(현지 시각, 일정용) / ISO-8601(`2026-11-11T14:00:00+09:00`, 수집·갱신 시각) |
| 거리 | 정수 미터 `distanceM` |
| 오류 | HTTP 상태 + `{ "code": "INVALID_INPUT", "message": "..." }` (아래 오류 코드 표) |

**"정상 응답 안의 안내"와 "오류"를 구분한다.** 긴급 문장 감지, 범위 밖, 코스 없음, 수정 문장 이해 불가, 사진 OCR 실패·미등록은 모두 **200 + 상태값**으로 온다(화면이 안내를 보여야 하는 정상 흐름). HTTP 오류는 입력 검증 실패나 서버 장애에만 쓴다.

### 오류 코드
| code | HTTP | 언제 | 화면 처리 |
|---|---|---|---|
| `INVALID_INPUT` | 400 | 500자 초과, 빈 문장, 잘못된 lang·좌표 | 입력 안내(프론트에서 먼저 막는 것이 원칙) |
| `UNSUPPORTED_MEDIA` | 415 | jpg/jpeg/png 아님 | 사진 형식 안내(FR-P002) |
| `PAYLOAD_TOO_LARGE` | 413 | 사진 10MB 초과 | 사진 크기 안내(FR-P002) |
| `NOT_FOUND` | 404 | 없는 courseId·contentId | 처음부터 다시 |
| `AI_UNAVAILABLE` / `AI_TIMEOUT` | 503 / 504 | 재시도 후에도 LLM 실패이고 대체 경로도 없을 때(설명 API는 템플릿으로 대체하므로 보통 안 옴) | 일시 오류 안내 |
| `UPSTREAM_UNAVAILABLE` | 503 | 외부 API(OCR 등) 실패 | 일시 오류 안내 |
| `NOT_IMPLEMENTED` | 501 | 아직 구현 안 된 골격 | 개발 중에만 |
| `INTERNAL` | 500 | 그 외 | 일시 오류 안내. **긴급 화면은 오류여도 112·119·1330 유지(FR-E004)** |

---

## 화면 단계별 호출 순서

| 화면 단계 | 호출 | 비고 |
|---|---|---|
| 홈 | 없음 | |
| **코스** UI-C01 요청 입력 → 결과 | `API-C01` → (`status=OK`면) `API-C02` | 설명은 지도·시간표 표시 **뒤** 따로(NFR-C001) |
| 코스 결과: 가정 칩 값 변경(FR-C003) | `API-C03`(`overrides`) → `API-C02` | 지금 버전 기준 새 버전(대화 수정 유지) |
| 코스 결과: 장소 누름 → 근거 카드(FR-C011) | `API-C05` | |
| 코스 결과: 대화형 수정(FR-C015) | `API-C03` → `API-C02` | `courseId`는 현재 버전 |
| 코스 결과: 되돌리기 | 없음(직전 응답을 프론트가 보관) | 이후 수정은 되돌린 버전의 `courseId`로 |
| 코스: 지도에서 출발 장소 고르기(FR-C014) | `API-C04` → 요청 전이면 `API-C01`(`startPlaceId`), 결과에서 바꾸면 `API-C03`(`startPlaceId`) | |
| 코스 입력/수정에 긴급 문장(FR-C013) | `API-C01`/`C03`이 `status=EMERGENCY` → 긴급 화면 이동 → `API-E01` → `API-E02` | 원문은 메모리로만 전달(URL 금지) |
| **사진** UI-P01 촬영·선택 → 분석 | `API-P01` | 위치는 허용 시 1회만 |
| 사진 결과 `CONFIRMED` | `API-P02`(`matchId` 포함) | 정보·지도 먼저, 설명은 뒤에 |
| 사진 결과 `CANDIDATES` → 1곳 선택(FR-P009) | `API-P02`(`matchId`, 고른 `contentId`) | 선택 로그(FR-P019) |
| 사진 결과 `NOT_FOUND`/`OCR_FAIL` → 이름 검색(FR-P016) | `API-P03` → 선택 시 `API-P02` | |
| 사진 → "여기서 출발하는 코스"(FR-P015) | 코스 화면으로 이동 → `API-C01`(`startPlaceId`) | |
| **긴급** UI-E01 입력 제출 | `API-E01` (≤1초) 와 `API-E02` **동시에** 호출 | E01로 연락 버튼 강조 먼저, E02로 도움처·카드·할 일 |
| 긴급 위치 거부 → 지역 선택(FR-E002) | `API-E02`(`area`) | 좌표 대신 대표 지역 |
| 긴급 `needTypeSelection=true` → 유형 선택(FR-E008) | `API-E02` 재호출(`type` 지정) | |
| 긴급 `triage.type=NON`(FR-E018) → "관광 코스 찾기" | 코스 화면으로 이동(빈 입력창) | 입력 문장 넘기지 않음 |
| 한국어 카드 전체화면, 전화, 길찾기, 카카오맵 열기(FR-P018) | 없음 | `tel:` / 지도 링크를 프론트가 만듦 |
| 관리자: 장소 수집·정규화 | `API-C06` | local-admin |
| 관리자: 지구대·안내소 CSV, 지식베이스 반영 | `API-E04`, `API-E05` | local-admin |
| 관리자: 약국·응급실 동기화 수동 실행·상태 | `API-E06`, `API-E07` | 평소에는 매일 04:00 자동 |

---

## 코스 (코스 담당)

### API-C01 코스 생성 — `POST /api/v1/courses`
근거: FR-C001~C009, C012~C014, SD-C01, SD-X01
```json
{
  "query": "광안리에서 바다 보면서 3시간, 저녁은 회",   // 1~500자
  "lang": "ko",
  "startPlaceId": "2789475",                            // 선택. 사진·지도에서 고른 출발 장소(1번 고정)
  "overrides": {                                        // 선택. 처음부터 정해 둔 조건. 결과 화면의 칩 변경은 API-C03
    "visitDate": "2026-11-12", "startTime": "15:00", "durationMin": 180, "area": "GWANGALLI"
  }
}
```
응답 `CourseResponse` (200):
```json
{
  "status": "OK",                     // OK | EMERGENCY | OUT_OF_SCOPE | NO_COURSE
  "courseId": "c_8f2a", "version": 1, "parentId": null,
  "assumptions": [                    // 가정 칩(FR-C003)
    {"key": "visitDate", "value": "2026-11-11", "label": "오늘", "isDefault": true},
    {"key": "startTime", "value": "14:00", "label": "지금 14:00", "isDefault": true},
    {"key": "durationMin", "value": "180", "label": "3시간", "isDefault": false},
    {"key": "area", "value": "GWANGALLI", "label": "광안리", "isDefault": false}
  ],
  "relaxed": ["실내 조건 해제"],      // 조건 완화로 푼 항목(FR-C007), 없으면 []
  "stops": [{
    "order": 1, "contentId": "126081", "name": "광안리해수욕장", "category": "관광지",
    "lat": 35.1532, "lng": 129.1186,
    "arriveAt": "14:00", "departAt": "15:10",
    "moveFromPrev": null,              // 1번은 null. 이후 {"mode": "WALK|TRANSIT|TAXI", "minutes": 12}
    "hoursUncertain": false,           // true면 '방문 전 확인' 배지(FR-C009)
    "fixed": false,                    // startPlaceId로 고정된 장소(FR-C014)
    "changed": false                   // 수정으로 바뀐 장소(API-C03)
  }],
  "path": [{"lat": 35.1532, "lng": 129.1186}],   // 지도 순서선(도로 경로 아님)
  "notice": null
}
```
`status`가 OK가 아니면 코스 필드는 비고 `notice`만 온다(코스 저장 안 함):
```json
{ "status": "EMERGENCY",    "notice": {"emergencyType": "URGENT", "message": "...", "examples": [], "suggestions": []} }
{ "status": "OUT_OF_SCOPE", "notice": {"message": "광안리·해운대 관광 코스만 도와드려요", "examples": ["해운대에서 2시간 산책 코스"]} }   // FR-C012
{ "status": "NO_COURSE",    "notice": {"message": "조건에 맞는 코스가 없어요(이유)", "suggestions": ["시간을 4시간으로 늘려보세요"]} }  // FR-C007
```

### API-C02 코스 설명 — `POST /api/v1/courses/{courseId}/narration`
근거: FR-C010, AI-REQ-C01, SD-C01 21~27
요청 `{"lang": "ko"}` → 응답:
```json
{ "courseId": "c_8f2a", "title": "바다 보며 걷다 회로 마무리",
  "items": [{"contentId": "126081", "text": "60자 이내 설명"}],
  "template": false }            // 검증 실패로 템플릿 설명이면 true
```

### API-C03 코스 수정 — `POST /api/v1/courses/{courseId}/edits`
근거: FR-C015(대화형 수정), FR-C003(가정 칩 변경), FR-C014(출발 장소), SD-C02
**지금 버전(`courseId`)에 대한 수정**으로 새 버전을 만든다. 이전 수정 내용은 지금 버전에 이미 들어 있으므로 그대로 유지된다(대화 수정 → 칩 변경 → 대화 수정 … 이 계속 쌓임).
요청(셋 중 하나 이상, 아무것도 없으면 400):
```json
{ "text": "2번 빼고 카페 넣어줘",                  // 대화형 수정 → REMOVE/REPLACE/ADD/SET_DURATION/KEEP 명령으로 변환
  "overrides": {"durationMin": 120},              // 가정 칩 변경. 바꾼 칩 값만 보낸다(API-C01의 overrides와 같은 모양)
  "startPlaceId": "264570",                       // 출발 장소를 이 장소로 고정(1번)
  "lang": "ko" }
```
응답 `CourseResponse`(새 버전: `version`+1, `parentId`=원래 courseId, 바뀐 장소는 `changed=true`).
추가 상태: `"status": "EDIT_UNCLEAR"` + `notice.examples`(명령으로 못 바꾼 경우, 코스 유지). 긴급 문장이면 `EMERGENCY`, 바꾼 조건으로 코스가 없으면 `NO_COURSE`(코스 유지).

### API-C04 출발 장소 후보 — `GET /api/v1/places?area=GWANGALLI&category=&limit=50`
근거: FR-C014 (SDD에 경로 없음 → 이 문서에서 배정)
응답 `{"places": [{"contentId", "name", "category", "lat", "lng"}]}` — 지도에 핀으로 띄우고 고르게 한다.

### API-C05 근거 카드 — `GET /api/v1/places/{contentId}?lang=ko`
근거: FR-C011, FR-P010(공유)
```json
{ "contentId": "126081", "name": "광안리해수욕장", "category": "관광지", "address": "부산 수영구 …",
  "lat": 35.1532, "lng": 129.1186,
  "overview": "TourAPI 개요 원문", "hoursText": "운영시간 원문", "hoursUncertain": false,
  "imageUrl": "https://…", "source": "ⓒ한국관광공사", "collectedAt": "2026-10-12T04:00:00+09:00" }
```
이 `PlaceDetail` 모양은 사진 결과의 장소 정보에도 그대로 쓴다.

### API-C06 지식베이스 수집 (관리자) — local-admin
근거: FR-C017, C018, FR-P020
| 경로 | 동작 |
|---|---|
| `POST /api/v1/admin/knowledge/ingest` | TourAPI 수집·운영시간 구조화. 한도로 멈추면 다음 실행 때 이어서 |
| `POST /api/v1/admin/knowledge/name-norm` | 모든 장소 `name_norm` 재생성(정규화 규칙 변경 시) |
| `GET /api/v1/admin/knowledge/status` | 진행 상황 |
응답 `{"processed": 120, "remaining": 340, "failed": 2, "state": "RUNNING|PAUSED_QUOTA|DONE"}`

---

## 사진 (사진 담당)

### API-P01 간판 매칭 — `POST /api/v1/photo/match` (multipart)
근거: FR-P001~P010, P013, P014, P017, SD-P01
| 파트 | 필수 | 설명 |
|---|---|---|
| `image` | O | jpg/jpeg/png, ≤10MB. 서버는 메모리에서만 처리, 응답 후 폐기(NFR-P002) |
| `lat`, `lng` | - | 허용 시 1회 위치. 저장 안 함(NFR-P003) |
| `lang` | - | 기본 ko |
응답:
```json
{
  "matchId": "m_51c0",                 // 이후 API-P02에 넘겨 선택 로그(FR-P019)를 남김. 개인정보 없음
  "decision": "CONFIRMED",             // CONFIRMED | CANDIDATES | NOT_FOUND | OCR_FAIL (FR-P008)
  "lowAccuracy": false,                // 위치 없이 2개 구 전체로 찾았으면 true → "정확도가 낮을 수 있음"(FR-P003)
  "candidates": [{                     // CONFIRMED면 1개, CANDIDATES면 최대 3개(+다중 간판 FR-P017), 나머지는 []
    "place": { …PlaceDetail(API-C05와 같은 모양)…, "openStatus": "OPEN|CLOSED|UNKNOWN" },
    "score": 0.82,                     // 0~1. 화면에는 82점으로
    "distanceM": 420,                  // 위치 없으면 null
    "matchedText": "해운대암소갈비"     // 매칭된 간판 텍스트 블록(다중 간판 구분용)
  }]
}
```

### API-P02 가게 설명 — `POST /api/v1/places/{contentId}/narration`
근거: FR-P011, P012, P019, SD-P01 19~26, SD-P02 8
요청 `{"lang": "ko", "matchId": "m_51c0"}` (`matchId`는 사진에서 왔을 때만) → 응답 `{"contentId", "text", "template": false}`.
`template=true`면 상호·업종·주소로 만든 대체 설명. 화면은 "생성된 안내문"으로 원문과 구분 표시.

### API-P03 이름 직접 검색 — `GET /api/v1/places/search?q=암소갈비&lang=ko`
근거: FR-P016, SD-P02 10~14
응답 = API-P01과 같은 모양(`decision`은 `CANDIDATES` 또는 `NOT_FOUND`, `distanceM`은 null, `matchId`는 null).

---

## 긴급 (긴급 담당)

### API-E01 즉시 응답 — `POST /api/v1/emergency/quick`
근거: FR-E003, E005, NFR-E001(1초), SD-E01 1단계
요청 `{"text": "친구가 쓰러졌어요", "lang": "ko"}` → 응답:
```json
{ "urgent": true, "topContact": "119", "keywordType": "URGENT" }
```
- `topContact`: 위급 키워드가 119 계열이면 `"119"`, 112 계열이면 `"112"`, 감지 안 되면 `null`(버튼 3개를 기본 순서로).
- LLM을 부르지 않는다. 화면은 이 응답만으로 연락 버튼을 강조한다.

### API-E02 상세 안내 — `POST /api/v1/emergency/guide`
근거: FR-E001, E002, E006~E018, NFR-E002, E003, E008, SD-E01 2단계, SD-X01 ③
```json
{ "text": "친구가 쓰러졌어요", "lang": "ko",
  "lat": 35.1532, "lng": 129.1186,   // 위치 허용 시
  "area": null,                      // 위치 거부 시 "GWANGALLI" | "HAEUNDAE" (FR-E002). lat/lng와 둘 중 하나는 필수
  "type": null }                     // 사용자가 유형을 직접 고른 경우만(FR-E008)
```
응답:
```json
{
  "triage": { "type": "URGENT", "urgency": "URGENT", "confidence": 0.91, "slots": {"symptom": "의식 없음"} },
                                     // type: URGENT|MILD|LOST|THREAT|FIRE|WAY|NON, urgency: URGENT|NORMAL
  "needTypeSelection": false,        // true면 나머지는 비어 있고 유형 선택 화면(FR-E008)
  "outOfArea": false,                // true면 places 비움, 연락 버튼만(FR-E017)
  "topContact": "119",               // 최종 긴급도 반영(FR-E007). NON이면 "1330"(FR-E018)
  "places": [{
    "id": "hp_102", "name": "좋은강안병원 응급실", "kind": "ER",
                                     // ER|PHARMACY|CONVENIENCE|POLICE|TOURIST_INFO|HOSPITAL|FIRE_STATION
    "lat": 35.16, "lng": 129.12, "distanceM": 640, "phone": "051-…",
    "todayHours": "24시간", "openStatus": "OPEN",     // OPEN|CLOSED|UNKNOWN(미확인, FR-E010)
    "er": { "availableBeds": 3, "message": null, "noTreatment": false, "fetchedAt": "…" }   // 응급실만(FR-E012)
  }],                                // 최대 3곳, 거리순(진료불가 응급실은 뒤로)
  "card": { "ko": "제 친구가 쓰러졌어요. 119를 불러주세요.", "translated": "My friend collapsed …",
            "address": "부산 수영구 광안해변로 219" },          // FR-E015 (템플릿, LLM 아님)
  "steps": ["숨을 쉬는지 확인하세요", "…"],                    // 2~3줄(FR-E016)
  "stepsFallback": false,            // 근거가 없어 저장된 기본 문구면 true
  "sources": [{"id": "kb_cpr_01", "title": "심폐소생술 안내(질병관리청)"}],
  "extras": ["SILENT_REPORT"],       // 화면 확장 버튼: SILENT_REPORT(문자신고) | EMBASSY
  "dataAsOf": { "places": "2026-11-11T04:00:00+09:00", "source": "국립중앙의료원" }   // NFR-E008
}
```
- 화면은 **E01과 E02를 동시에** 보내고, E01이 먼저 오면 버튼 강조, E02가 오면 도움처·카드·할 일을 채운다.
- LLM이 실패해도 `needTypeSelection=true`로 정상 응답한다(FR-E008). 공공 API가 죽어도 저장 데이터로 `places`를 채운다(NFR-E003).
- 입력 문장·좌표는 DB/로그에 저장하지 않는다(NFR-E004).

### API-E03 — 미배정
SDD 추적표에 연결된 FR이 없다. 필요해지면 이 번호를 쓴다.

### API-E04 정적 도움처 적재 (관리자) — `POST /api/v1/admin/emergency/places/import` (multipart)
근거: FR-E021. 파트 `kind`(POLICE|TOURIST_INFO|CONVENIENCE), `file`(CSV). 좌표계 변환 포함.
응답 `{"inserted": 40, "updated": 3, "rejected": [{"line": 12, "reason": "좌표 없음"}]}`

### API-E05 지식베이스·사전 반영 (관리자) — `POST /api/v1/admin/emergency/knowledge/reload`
근거: FR-E022, NFR-E009. 저장소의 지식베이스 문서·위급 키워드·카드 템플릿·결정표를 다시 읽는다.
응답 `{"documents": 24, "keywords": 310, "templates": 7}`

### API-E06 약국·응급실 동기화 수동 실행 (관리자) — `POST /api/v1/admin/emergency/sync`
근거: FR-E019, NFR-E007. 평소에는 매일 04:00 스케줄러가 같은 일을 한다.
응답 `{"source": "NMC", "ok": true, "rows": 212, "ranAt": "…"}`

### API-E07 동기화 상태 (관리자) — `GET /api/v1/admin/emergency/sync/status`
근거: FR-E019, NFR-E008. 응답 `{"logs": [{"source", "ok", "rows", "ranAt"}]}`(최근 10건)

---

## 공용
`GET /api/v1/health` → `{"status": "ok"}`

## 프론트 연결 방법
- 기본은 Mock. 백엔드에 붙이려면 `flutter run -d chrome --dart-define=API_BASE=http://localhost:8080` (값이 있으면 `Api*Repository` 사용).
- 열거값 변환, 점수(0~1 → 점수 표시), `PlaceDetail` → 화면 모델 변환은 각 `Api*Repository` 안에서 한다.

## 열린 결정
1. 사진 HEIC 허용 여부(SRS FR-P002는 jpg/png만, 프로토타입은 HEIC 언급) — 현재 계약은 jpg/jpeg/png.
2. 상호 로마자 표기: FR-P010은 "번역·음역하지 않고 원문 그대로" → 계약에 로마자 필드 없음(프로토타입의 영문 표기 줄은 빠짐).
3. 프로토타입의 기상특보 배너(화재·재난)는 SRS FR이 없어 계약에 넣지 않았다.
