# 소스 소유권 가이드 (누가 무엇을 고치나)

기준: SDD v0.5 컴포넌트 번호(COMP-C/P/E) · 패키지 `kr.busan.safetrip.{common,course,photo,emergency}` · Flutter `lib/features/*`.
모든 소스 파일 맨 위(주석)에 `[코스 담당]` `[사진 담당]` `[긴급 담당]` `[공용]` 중 하나가 적혀 있습니다.

## 3줄 규칙
1. **내 영역**(아래 표 "수정해야 할")만 고친다. 다른 담당 영역은 읽기만 하고, 고칠 일이 생기면 그 담당에게 메신저로 요청한다.
2. **공용**을 고칠 때는 병합 메시지에 **명세 대조 3줄**(근거 문서·영향 범위·확인 방법)을 적고 해리가 명세서와 대조한 뒤 병합한다. (3인 전원 승인은 요구하지 않는다.)
3. 코스·사진·긴급 패키지는 서로를 import 하지 못한다 — `ArchitectureTest`가 `mvn test`에서 막는다. 같이 쓸 게 생기면 `common`으로 올리는 공용 변경으로 처리한다.

## 한눈에 보기

| 구분 | 코스 담당 | 사진 담당 | 긴급 담당 |
|---|---|---|---|
| 백엔드 패키지 | `course/**` | `photo/**` | `emergency/**` |
| 프론트 폴더 | `lib/features/course/**`, `lib/data/api/course_api.dart` | `lib/features/photo/**`, `lib/data/api/photo_api.dart` | `lib/features/emergency/**`, `lib/data/api/emergency_api.dart` |
| DB 마이그레이션 | `V10~V19` | `V20~V29` | `V30~V39` |
| 관리자 API | `CourseAdminController` (API-C06) | 없음 | `EmergencyAdminController` (API-E04/E05) |
| 로그 삭제(Purgeable) | `CoursePlanPurger` | `MatchLogger` | (request_logs 만들 때 추가) |

## 코스 담당
**수정해야 할 (내 소스)**
- `backend/.../course/**` : CourseController(C01), CourseService(C02), IntentParser(C03), CandidateRetriever(C04), CoursePlanner·RelaxationPolicy(C05), NarrationService·OutputValidator(C06), CourseDtos, CoursePlan 엔티티·레포지토리
- `backend/src/main/resources/db/migration/V10~V19`
- `frontend/lib/features/course/**` (UI-C01/C02/C03, course_controller)
- 프론트 `lib/data/api/course_api.dart`(`ApiCourseRepository`, 작성 완료 — 서버 응답이 바뀌면 여기서 변환)

**건드리면 안 되는 (남의 소스)**
- `photo/**`, `emergency/**`, `features/photo/**`, `features/emergency/**`, V20~V39

**공용 (명세 대조 3줄 + 해리 확인)** — 아래 "공용 목록" 참고. 단, **AIService 구현(SpringAiService, COMP-C08)** 은 코스 담당이 1차 구현하고 공용 변경 절차(명세 대조 3줄 + 해리 확인)를 따르는 것을 제안.

## 사진 담당
**수정해야 할**
- `backend/.../photo/**` : PhotoController(P02), PhotoMatchService(P03), ImagePreprocessor(P04), OcrEngine·Paddle·Cloud(P05), NameNormalizer(P06), PlaceMatcher(P07), PlaceNarrationService·OutputValidator(P08), MatchLogger(P09), PlaceSearchService(P10), PhotoDtos, PhotoMatchLog
- `V20~V29`
- `frontend/lib/features/photo/**` (UI-P01~P04), `lib/data/api/photo_api.dart`(`ApiPhotoRepository`)

**건드리면 안 되는**: `course/**`, `emergency/**`, `features/course/**`, `features/emergency/**`, V10~V19·V30~V39

**주의**: `NameNormalizer`의 규칙은 `place.name_norm` 적재(공용 KnowledgeIngestor)에도 쓰입니다. 규칙을 바꾸면 재적재가 필요하니 공용 담당(해리)에게 알릴 것.

## 긴급 담당
**수정해야 할**
- `backend/.../emergency/**` : EmergencyController(E02), EmergencyService(E03), KeywordRuleEngineImpl(E04), SituationClassifier(E05), DecisionTable(E06), HelpPlaceFinder·OpenNowEvaluator(E07), ErRealtimeClient(E08), KoreanCardBuilder·ReverseGeocoder(E09), GuidanceRetriever(E10), DataSyncScheduler(E11), EmergencyAdminController(E12), EmergencyDtos, 엔티티·레포지토리
- `V30~V39`
- `frontend/lib/features/emergency/**` (UI-E01~E06), `lib/data/api/emergency_api.dart`(`ApiEmergencyRepository`)

**건드리면 안 되는**: `course/**`, `photo/**`, `features/course/**`, `features/photo/**`, V10~V29

## 공용 목록 (명세 대조 3줄 + 해리 확인)
**백엔드 `common/**` + 설정**
| 파일 | 설명 | 상태 |
|---|---|---|
| `SafetripApplication`, `application.yml`, `pom.xml` | 앱·설정·의존성 | 완료 |
| `common/config/AppProperties, WebConfig` | 설정 바인딩, CORS | 완료 |
| `common/exception/*` | ErrorCode, BusinessException, NotImplementedYetException, ErrorResponse, GlobalExceptionHandler | 완료 |
| `common/api/Lang, EmType, MapPointDto` | 세 모듈 공통 값 | 완료 |
| `common/util/GeoUtil, PiiMasker, Purgeable, RetentionScheduler` | 거리·마스킹·30일 삭제 | 완료 |
| `common/web/HealthController` | 헬스체크 | 완료 |
| `common/ai/AIService, AiRequest, SpringAiService` (COMP-C08) | LLM 단일 통로 | 인터페이스 완료 / 구현 TODO |
| `common/domain/Place, PlaceRepository` (COMP-C09) | 장소 | 골격 |
| `common/tourapi/TourApiClient, HoursParser, KnowledgeIngestor` (COMP-C07) | TourAPI 수집·운영시간 파서 | TODO |
| `common/keyword/KeywordRuleEngine` | 위급 키워드 계약(구현은 긴급) | 인터페이스 |
| `db/migration/V1~V9` | place, pgvector | 완료(임베딩 TODO) |
| `ArchitectureTest` | 소유권 경계 검사 | 완료 |
| `ApiContractTest`, `ContractFixtureTest` | 계약 경로 매핑·DTO JSON 모양 검사 | 완료 |
| `common/api/Area, PlaceDetail` | 지역 값, 장소 상세(코스 근거 카드·사진 결과 공용) | 완료 |

**프론트 공용**: `lib/core/**`, `lib/data/models/**`(화면 모델), `lib/data/api/api_client.dart`(HTTP), `test/api_contract_test.dart`, `lib/data/repositories/repositories.dart`(인터페이스), `lib/state/providers.dart`, `lib/widgets/**`, `lib/features/bridge.dart`(기능 간 연결), `lib/features/home/**`, `lib/router.dart`, `lib/main.dart`, `pubspec.yaml`

**문서 공용**: `docs/API.md`, `docs/contract/*.json`(계약 견본), `docs/OWNERSHIP.md`

## 브랜치 병합 절차 (PR 없이)
브랜치: 담당별 `feat/course`, `feat/photo`, `feat/emergency`(필요하면 `feat/course-xxx`처럼 세분), 공용은 `chore/common-xxx`. `main`에는 직접 커밋하지 않고 병합만 한다.

**작업 중**
- 하루 한 번쯤 `git merge main`으로 최신 `main`을 내 브랜치에 가져와 충돌을 미리 푼다.
- 내 영역 밖 파일이 바뀌면 안 된다. 필요하면 담당자에게 요청한다.

**`main`에 병합하기 전 (병합하는 사람이 실행)**
```
scripts/check-ownership.sh <course|photo|emergency|common>   # 남의 영역 수정 검사 (실패하면 병합 금지)
cd backend && mvn test                                         # 소유권 경계(ArchitectureTest) 포함
cd frontend && flutter analyze                                 # 프론트 변경 시
git checkout main && git pull && git merge --no-ff feat/<영역>
```
검사 스크립트가 "공용 파일 수정" 경고를 내면 아래 공용 변경 절차를 따른다.

## 공용 변경 절차 (병합 시 명세서 대조)
1. 공용 파일은 `chore/common-*` 브랜치에서 최소 변경으로 고친다. 가능하면 **해리가 먼저 `main`에 병합**해 두고, 나머지는 `git merge main`으로 받아 간다(공용이 제각각 갈라지는 것을 막기 위함).
2. 병합 커밋 메시지에 3줄을 적는다.
```
근거: SRS FR-E010, SDD COMP-E07            (요구사항/컴포넌트/API 번호)
영향: DecisionTable.topContact 시그니처 변경, 긴급 담당 영향   (바뀐 것·영향받는 담당)
확인: mvn test 통과, 긴급 화면 수동 확인                       (돌린 테스트·확인한 화면)
```
3. 해리가 병합 직전에 근거 항목을 SRS/SDD 원문과 대조한다. 영향받는 담당에게는 메신저로 알리고 "확인했어요" 답만 받으면 된다(승인 절차 없음).

내 영역만 고친 병합은 담당자 본인이 해도 된다.

## API 필드를 바꾸고 싶을 때
`docs/API.md` · 백엔드 `*Dtos.java` · 프론트 `data/api/*_api.dart`(+필요하면 `models.dart`) · `docs/contract/*.json`이 한 묶음이다. 같은 커밋에서 고치고 영향받는 담당에게 알린다(위 공용 변경 절차 적용).
- 견본 갱신: `cd backend && mvn test -Dcontract.write=true` → `docs/contract`가 새 DTO 모양으로 바뀐다(diff로 바뀐 필드 확인).
- 확인: `mvn test`(DTO = 견본) + `flutter test`(견본을 Api*Repository가 읽음). 둘 다 통과해야 프론트·백엔드가 같은 계약이다.

## AI 에이전트(Claude Code 등)로 작업할 때
루트의 `AGENTS.md`를 먼저 읽게 한다(`CLAUDE.md`는 그 파일을 가리킨다). 담당·금지 경로·검증 명령·병합 전 체크가 에이전트용으로 정리돼 있다.
