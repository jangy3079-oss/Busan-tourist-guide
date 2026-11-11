# AGENTS.md — AI 에이전트용 작업 규칙

부산 외국인 관광 안내 서비스(소프트웨어공학 3조). Flutter Web 프론트 + Spring Boot 백엔드.
이 저장소는 **3명이 영역을 나눠** 작업한다. 에이전트는 코드를 고치기 전에 아래를 먼저 확인한다.

## 0. 시작 전 확인 (필수)
1. **지금 누구의 작업인가?** 사용자에게 `코스 / 사진 / 긴급 / 공용` 중 어느 담당인지 확인한다. 브랜치명(`feat/course-*` 등)이 힌트가 될 수 있으나 확실하지 않으면 묻는다.
2. 고칠 파일의 **맨 위 주석**(`[코스 담당]` `[사진 담당]` `[긴급 담당]` `[공용]`)을 읽는다. 담당과 다르면 **수정하지 않는다**.
3. 상세 규칙은 `docs/OWNERSHIP.md`, API는 `docs/API.md`, 추가로 필요한 함수는 `docs/EXTRA_FUNCTIONS.md`.

## 1. 경로별 소유권 (요약)
| 경로 | 소유 | 에이전트가 해도 되는 일 |
|---|---|---|
| `backend/src/main/java/kr/busan/safetrip/course/**`, `frontend/lib/features/course/**`, `db/migration/V10~V19` | 코스 | 코스 담당 작업일 때만 수정 |
| `.../photo/**`, `frontend/lib/features/photo/**`, `V20~V29` | 사진 | 사진 담당 작업일 때만 수정 |
| `.../emergency/**`, `frontend/lib/features/emergency/**`, `V30~V39` | 긴급 | 긴급 담당 작업일 때만 수정 |
| `.../common/**`, `application.yml`, `pom.xml`, `V1~V9`, `frontend/lib/{core,widgets,state,data/models}`, `data/repositories/repositories.dart`, `features/bridge.dart`, `features/home`, `router.dart`, `main.dart`, `docs/**`, `.github/**` | 공용 | **공용 변경 절차(§3)를 따를 때만** 수정 |

## 2. 하지 말 것
- 다른 담당 영역 파일 수정. 필요해 보이면 **수정하지 말고** 사용자에게 "누구에게 무엇을 요청해야 하는지" 한 줄로 알린다.
- 코스·사진·긴급 패키지가 서로를 import 하기. (`ArchitectureTest`가 실패한다. 공유가 필요하면 `common`으로 올리는 공용 변경으로 처리)
- LLM 클라이언트를 직접 만들기. **모든 LLM 호출은 `common/ai/AIService`만 경유**한다.
- 사진 바이트·좌표·사용자 입력 문장을 DB/로그/파일에 저장하기(NFR-P002/P003/E004). 로그에 사용자 문장이 필요하면 `PiiMasker.mask`를 거친다.
- 로직 안에서 난수·`now()`를 직접 쓰기(코스 탐색기·사진 매칭·운영 중 판정은 테스트 가능한 순수 함수로, 시각은 인자로 받는다 — NFR-C006/P006).
- 점수 가중치·임계값·반경·키워드·결정표·카드 템플릿을 코드에 하드코딩하기(설정/데이터로 — NFR-C005/P005/E009).
- 요청받지 않은 리팩터링, 인접 코드 정리, 포맷 변경. 바뀐 모든 줄은 요청과 연결돼야 한다.
- `git commit` / `git push`. **변경 내용과 실행할 명령만 사용자에게 제시**한다(사용자가 직접 실행). 커밋 메시지 끝에는 사용자가 정한 attribution 줄이 있으면 그대로 붙인다.

## 3. 공용 파일을 꼭 고쳐야 할 때
이 팀은 **PR 없이 브랜치를 병합**한다. 병합 전 검사가 사람 리뷰를 대신하므로 아래를 지킨다.
1. 먼저 사용자에게 알리고 동의를 받는다.
2. 변경은 최소로. 함께 고쳐야 하는 묶음:
   - API 필드: `docs/API.md` + `backend/.../*Dtos.java` + `frontend/lib/data/api/*_api.dart`(필요하면 `models.dart`) + `docs/contract/*.json`(`mvn test -Dcontract.write=true`로 갱신)
   - DB 스키마: `db/migration`의 **새 파일**을 담당 번호 대역에 추가(기존 파일 수정 금지)
3. 사용자에게 **병합 커밋 메시지용 '명세 대조' 3줄**을 제시한다:
   `근거:` SRS FR/NFR·SDD COMP/API 번호 / `영향:` 바뀐 것과 영향받는 담당 / `확인:` 돌린 테스트·확인한 화면.

## 4. 구현할 때 규칙
- 스켈레톤 메서드는 `NotImplementedYetException`을 던진다. **구현하면 그 줄을 지운다.** 파일 머리 주석의 `상태:`도 `완료`로 고친다.
- 클래스 머리 주석의 `COMP-xxx`, `요구사항:`을 근거로 삼아 `docs/` 및 SRS/SDD의 해당 항목을 확인하고 구현한다. 명세가 비어 있거나 모순되면 **추측해서 만들지 말고** 사용자에게 묻는다(예: SDD 6장 API는 제안안 상태).
- 컨트롤러는 입출력·검증만, 로직은 Service/하위 컴포넌트로. 컨트롤러 → Service → 컴포넌트 순서를 유지한다.
- 새 설정 키는 `AppProperties`(공용)에 추가해야 하므로 공용 변경 절차를 따른다.
- 프론트는 화면·상태가 `Repository` 인터페이스만 안다(메서드 하나 = API 하나). `Api*Repository`는 이미 있고, `--dart-define=API_BASE=http://localhost:8080`을 주면 Mock 대신 쓰인다. 서버 JSON → 화면 모델 변환은 각 `data/api/*_api.dart`에서만 한다.
- API 경로·필드는 `docs/API.md`가 기준이다(SDD 4.2 시퀀스 다이어그램 경로를 따름). 임의로 경로·필드를 만들지 않는다.

## 5. 검증 (끝내기 전에 실행하고 결과를 보고)
```
scripts/check-ownership.sh <내 담당>   # 소유권 검사
# 백엔드 (backend/)
mvn test                      # 소유권 경계 + API 경로 매핑 + DTO JSON 모양(계약 견본) 검사
# 프론트 (frontend/)
flutter analyze
flutter test                  # 계약 견본(docs/contract)을 Api*Repository가 읽는지
```
- 자기 영역 밖 파일이 바뀌지 않았는지 확인: `scripts/check-ownership.sh <course|photo|emergency|common>` 를 실행한다(기준 브랜치 `main`). 실패하면 남의 영역 변경을 되돌린다. 공용 경고가 나오면 §3을 따른다.
- 실패한 테스트·분석 이슈를 숨기거나 무시하지 않는다.

## 6. 보고 형식 (작업 끝에 사용자에게)
- 바꾼 파일 목록과 각 파일의 소유자
- 근거 항목(FR/NFR/COMP/API 번호)
- 실행한 검증 명령과 결과
- 남은 TODO와, 다른 담당에게 요청해야 할 일
- 병합 전 실행할 명령(`scripts/check-ownership.sh`, `mvn test`, `git merge --no-ff ...`)과, 공용 변경이 있으면 '명세 대조' 3줄

## 7. 프로젝트 사실 (헷갈리기 쉬운 것)
- 로그인 없음. 지원 언어 ko/en/ja. 대상 지역: 수영구·해운대구(광안리·해운대).
- 긴급: 모든 긴급 화면·오류 응답에 112/119/1330 연락 정보가 항상 있어야 한다(FR-E004). LLM/공공API가 죽어도 저장 데이터로 안내를 계속한다(NFR-E003).
- 코스: 설명(API-C02)은 코스(API-C01)와 분리해 늦게 와도 코스를 먼저 보여준다. 긴급 문장·범위 밖·코스 없음은 200 + `status`로 응답(오류 아님).
- 긴급: 프론트는 API-E01(키워드, 1초)과 API-E02(상세)를 동시에 보낸다. 도움처·카드·할 일은 E02에서만 온다.
- 사진: 허용 형식 jpg/jpeg/png, 10MB(SRS FR-P002). HEIC는 미결정(질문할 것).
- DB: PostgreSQL + pgvector, 스키마는 Flyway로만 변경(`ddl-auto: none`).
- 관리자 API는 `@Profile("local-admin")`에서만 활성화.
