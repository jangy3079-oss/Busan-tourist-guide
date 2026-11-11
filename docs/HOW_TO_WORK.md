# 작업 방법

부산 안심여행(소프트웨어공학 3조) 팀원용 안내서입니다. 처음이면 이 문서부터 읽으세요.

---

## 1. 지금 상태 한눈에

### 다 된 것
- **프론트 화면 전체**: 홈, 코스, 사진, 긴급 화면과 그 사이 이동(사진→코스, 코스→긴급, 긴급→코스)
  - 지금은 가짜 데이터(Mock)로 움직입니다. 서버에 붙이는 코드도 이미 있습니다.
  - 코스의 칩 바꾸기, 출발 장소 고르기, 대화로 고치기까지 됩니다.
- **백엔드 뼈대**: 모든 API 주소(URL)와 입력 검사(500자 등)는 동작합니다. 안쪽 기능은 비어 있습니다.
- **공용 기본기**: 오류 응답 형식, 개인정보 가리기(전화·이메일), 30일 지난 로그 삭제, 서버 상태 확인(`/health`)
- **자동 검사**: 남의 영역 침범, API 주소·응답 모양 어긋남, 서버가 안 뜨는 설정 실수를 `mvn test`와 `flutter test`가 잡아 줍니다.

### 안 된 것
비어 있는 메서드는 실행하면 `501 NOT_IMPLEMENTED`를 돌려줍니다. 코드에서 `NotImplementedYetException`을 검색하면 전부 나옵니다.

| 담당 | 개수 | 해야 할 것 |
|---|---|---|
| **공용 (해리)** | 4 | LLM 호출 통로(`SpringAiService`), 관광공사 API로 장소 모으기(`TourApiClient`, `KnowledgeIngestor`), 운영시간 문장 해석(`HoursParser`) |
| **코스** | 15 | 문장에서 조건 뽑기(`IntentParser`), 후보 장소 찾기(`CandidateRetriever`), 코스 짜기·조건 완화(`CoursePlanner`, `RelaxationPolicy`), 설명 만들기·검증(`NarrationService`, `OutputValidator`), 이것들을 잇는 `CourseService` 5개, 관리자 API 3개 |
| **사진** | 11 | 사진 검사·정리(`ImagePreprocessor`), 글자 읽기(`PaddleOcrEngine` 또는 `CloudOcrEngine`), 상호 정규화(`NameNormalizer`), 가게 맞추기(`PlaceMatcher`), 이름 검색(`PlaceSearchService`), 설명 만들기·검증(`PlaceNarrationService`, `OutputValidator`), 기록(`MatchLogger`), 이것들을 잇는 `PhotoMatchService` |
| **긴급** | 16 | 위급 키워드 판정(`KeywordRuleEngineImpl`), 상황 분류(`SituationClassifier`), 결정표(`DecisionTable`), 지금 운영 중인지(`OpenNowEvaluator`), 도움처 찾기(`HelpPlaceFinder`), 응급실 병상(`ErRealtimeClient`), 주소 변환(`ReverseGeocoder`), 한국어 카드(`KoreanCardBuilder`), 지금 할 일(`GuidanceRetriever`), 약국·응급실 동기화(`DataSyncScheduler`), 이것들을 잇는 `EmergencyService` 2개, 관리자 API 2개 |

화면 쪽 남은 일:
- **공용**: 진짜 지도(카카오맵), 위치 권한, 홈·코스·사진 화면 영어·일본어 번역
- **각 담당**: 서버 오류가 났을 때 내 화면에 안내 띄우기
- **긴급**: 전화·길찾기 버튼 실제 연결. 입력 화면 연락 버튼이 119 강조를 못 받는 문제(FR-E003) 고치기

---

## 2. 처음 받은 날 (1시간)

```bash
git clone https://github.com/jangy3079-oss/Busan-tourist-guide.git
cd Busan-tourist-guide
git checkout -b feat/emergency       # 코스: feat/course, 사진: feat/photo

docker compose up -d                 # DB 켜기 (Docker Desktop 필요)
cd backend && mvn spring-boot:run    # 서버 켜기
curl localhost:8080/api/v1/health    # {"status":"ok"} 나오면 성공
```

화면 보기 (다른 터미널):
```bash
cd frontend
flutter run -d chrome                                         # 가짜 데이터로
flutter run -d chrome --web-port 8099 --dart-define=API_BASE=http://localhost:8080   # 내 서버에 붙여서
```

---

## 3. 개발 순서

1. **표에서 내 할 일 고르기.** 다른 것에 덜 기대는 것부터 합니다.
   - 코스: `CoursePlanner`
   - 사진: `NameNormalizer`, `PlaceMatcher`
   - 긴급: `KeywordRuleEngineImpl`, `DecisionTable`(E01 즉시 응답)
   - LLM이나 관광 데이터가 필요한 부분은 해리의 공용 작업이 올라온 뒤에 시작합니다.
2. **클래스 맨 위 주석 보기.** 어떤 요구사항(FR 번호)인지 적혀 있습니다. 기능명세서에서 그 번호를 찾아 읽습니다.
3. **테스트 먼저 쓰기.** 예: "친구가 쓰러졌어요"를 넣으면 "119"가 나와야 한다.
4. **구현하기.** 끝나면 `NotImplementedYetException` 줄을 지우고, 맨 위 주석의 `상태:`를 `완료`로 바꿉니다.
5. **서버에 직접 요청해 보고, 화면에도 붙여 보기.**
6. **올리기** (아래 4번 순서대로)

기능 하나 끝날 때마다 작게 자주 올리세요. 몰아서 올리면 충돌이 커집니다.

---

## 4. 올리는 방법 (PR 없이 브랜치 병합)

```bash
git merge main                                # 1. 최신 main 받기
./scripts/check-ownership.sh emergency        # 2. 남의 영역 건드렸는지 검사 (내 담당 이름)
(cd backend && mvn test)                      # 3. 백엔드 검사
(cd frontend && flutter analyze && flutter test)   #    화면을 고쳤다면
git checkout main && git pull
git merge --no-ff feat/emergency              # 4. 합치기
git push origin main
```

2번이나 3번이 실패하면 올리지 마세요.

---

## 5. 꼭 지킬 것

**영역**
- 내 영역만 고칩니다. 남의 영역은 고치지 말고 단톡에 요청하세요.
  - 코스: `course/`, 사진: `photo/`, 긴급: `emergency/` 폴더 (백엔드·프론트 모두)
- 공용 파일(`common/`, 화면 공통 부품, 문서 등)을 고쳤다면 병합 메시지에 3줄을 쓰고 해리에게 알립니다.
  ```
  근거: FR-E010, COMP-E07      (어떤 요구사항 때문에)
  영향: 무엇이 바뀌었고 누구에게 영향이 있는지
  확인: 돌려 본 테스트
  ```
- `main`에 직접 커밋하지 않습니다.

**API**
- API 주소나 응답 필드는 혼자 바꾸지 않습니다. 먼저 단톡에 말하세요.
- 바꿀 때는 `docs/API.md`, 백엔드 `*Dtos.java`, 프론트 `data/api/*_api.dart`, `docs/contract/` 견본을 한 번에 고칩니다.

**개인정보**
- 사용자가 쓴 문장, 위치, 사진은 DB나 로그에 남기지 않습니다.
- 꼭 남겨야 하면 `PiiMasker`로 가린 뒤에 남깁니다.

**코드**
- LLM은 `AIService`로만 부릅니다. 각자 따로 만들지 않습니다.
- 키워드, 점수 가중치, 기준값, 카드 문구는 코드에 박지 말고 데이터 파일(`backend/src/main/resources/<내담당>/`)이나 설정으로 뺍니다.
- DB 표를 바꿀 때는 기존 마이그레이션 파일을 고치지 말고 내 번호대에 새 파일을 추가합니다.
  - 코스 `V10~V19`, 사진 `V20~V29`, 긴급 `V30~V39`
- 비밀키(API 키)는 커밋하지 않습니다. 환경변수로 넣습니다.

---

## 6. 문서 안내

| 문서 | 내용 | 언제 보나 |
|---|---|---|
| `docs/HOW_TO_WORK.md` | 지금 이 문서. 상태, 할 일, 순서, 규칙 | 처음, 헷갈릴 때 |
| `docs/OWNERSHIP.md` | 파일별 주인 상세 목록 | "이 파일 내가 고쳐도 되나?" 싶을 때 |
| `docs/API.md` | 모든 API의 주소, 보내는 값, 받는 값. 화면 단계별로 어떤 API를 부르는지 | API 만들거나 화면에 붙일 때 |
| `docs/contract/*.json` | API 응답 견본. 프론트·백엔드 테스트가 이걸로 서로 맞는지 검사 | 직접 고칠 일은 거의 없음 |
| `docs/EXTRA_FUNCTIONS.md` | 기능명세서에는 없지만 만들어야 하는 것(재시도, 캐시, 호출 한도 등) | 내 기능 다 만들었다 싶을 때 |
| `AGENTS.md` / `CLAUDE.md` | AI 도구(Claude Code 등)가 읽는 작업 규칙 | AI로 코딩할 때 (자동으로 읽힘) |
| `frontend/README.md`, `backend/README.md` | 각각 실행 방법 | 실행이 안 될 때 |

---

## 7. 막히면
- 서버가 안 뜸 → `docker compose up -d` 했는지, `mvn test`가 통과하는지 확인
- 남의 코드가 필요함 → 직접 고치지 말고 단톡에 요청
- 명세가 비어 있거나 서로 다름 → 추측해서 만들지 말고 단톡에 질문
