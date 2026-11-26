# 실행·시연 가이드

배포는 하지 않습니다. 시연은 **로컬에서 띄운 화면을 녹화한 영상**으로 합니다(#22).

## 1. DB는 두 가지
| | 로컬 DB (각자 Docker) | 공용 DB (Supabase) |
|---|---|---|
| 언제 | 개발, 테스트, **영상 촬영** | 병합된 `main`의 기준 데이터를 볼 때 |
| 켜기 | `docker compose up -d` | `DB_URL`·`DB_USER`·`DB_PASSWORD`를 환경변수로 (값은 개인적으로 전달, 저장소에 적지 않기) |
| 테이블 변경 | 자유 | **동현 한 명만, `main`에 병합된 것만.** 다른 사람은 반드시 `SPRING_FLYWAY_ENABLED=false` |
| 테스트 데이터 | 자유 | 넣지 않기 |

결정: #140 (2026-11-26, 과반수). 공용 DB에는 장소 349곳, 운영시간, 벡터, 분류 코드가 들어 있습니다.

## 2. 처음 켜기
필요한 것: Docker Desktop, Java 17, Maven, Flutter 3.50 이상, Ollama

```bash
docker compose up -d                                   # 1. 로컬 DB
ollama pull bge-m3                                     # 2. 벡터 검색용 임베딩 (로컬)
cd backend && cp .env.example .env                     # 3. 키를 채운다. DB_* 줄은 지운다(로컬 DB 사용)
set -a; . ./.env; set +a; mvn spring-boot:run          # 4. 서버 → curl localhost:8080/api/v1/health
```
```bash
cd frontend                                            # 5. 화면 (다른 터미널)
echo "KAKAO_MAP_JS_KEY=받은키" > .env                   #    커밋되지 않는다
flutter run -d chrome --web-port 8099 --dart-define=API_BASE=http://localhost:8080 --dart-define-from-file=.env
```
- 카카오맵은 `http://localhost:8099`와 `http://localhost:3000`에서만 보입니다(도메인 등록). 다른 주소나 키가 없으면 그림 지도가 나옵니다.
- 화면만 볼 때는 `flutter run -d chrome`(가짜 데이터).

### Java·Flutter를 설치하지 않았다면 (Docker만)
```bash
docker run --rm -v "$PWD:/src" -v m2cache:/root/.m2 -w /src/backend maven:3.9-eclipse-temurin-17 mvn -B test
docker run --rm -v "$PWD:/src" -w /src/frontend ghcr.io/cirruslabs/flutter:stable bash -c "flutter pub get && flutter test && flutter build web --dart-define=API_BASE=http://localhost:8080 --dart-define-from-file=.env"
```

## 3. 장소 데이터 넣기 (관리자)
로컬 DB는 처음에 비어 있습니다. `TOUR_API_KEY`가 필요합니다.
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local-admin
curl -X POST localhost:8080/api/v1/admin/knowledge/ingest      # 시작 (뒤에서 돈다)
curl localhost:8080/api/v1/admin/knowledge/status              # state=DONE, processed=349 이면 끝
```
- 처음에는 TourAPI를 약 710번 부릅니다(하루 한도 1,000). 한도에 걸리면 `PAUSED_QUOTA`가 되고, 다음 날 같은 명령으로 이어집니다.
- 다시 돌리면 수정일이 바뀐 장소만 상세를 다시 받습니다. **주 1회 수동 실행**(NFR-C008).
- 관리자 API는 `local-admin` 프로필에서만 켜집니다.

### 긴급 데이터 넣기 (도움처·공휴일·지식베이스)
- 긴급 상세 안내(`API-E02`)에 도움처가 나오려면 `help_place`(839행)·`holiday`(22행)가 필요함(`kb_document` 39행은 서버 기동 시 저장소 원고로 자동 적재됨).
- 키·CSV 없이 로컬 DB에 넣으려면 개인 전달받은 `em_seed.sql`을 `psql`로 적재함(공용 DB에는 긴급 데이터를 아직 올리지 않음).
- 처음부터 `local-admin` API(`API-E04`~`E07`)로 적재·동기화하는 절차와 공용 DB 규칙(`SPRING_FLYWAY_ENABLED=false`)은 [긴급 데이터 적재 가이드](../backend/src/main/resources/emergency/README.md)를 따름.

## 4. LLM
| | 설정 |
|---|---|
| 기본(시연·평가) | Claude API `claude-haiku-5-5`. `.env`에 `AI_API_KEY`만 넣는다 |
| 키 없이 | LLM 없이 규칙·템플릿으로 응답 |
| 로컬 LLM 시험 | `AI_PROVIDER=ollama`, `AI_BASE_URL=http://localhost:11434`, `AI_MODEL=qwen3.5:4b` |

Ollama는 임베딩(`bge-m3`, 장소 벡터 검색)에 계속 씁니다. LLM을 Claude로 써도 Ollama는 켜 둡니다.

키가 없거나 실패해도 코스는 규칙과 템플릿으로 나옵니다(AC-C05). 호출은 재시도를 포함해 총 10초 안에 끝납니다.

## 5. 시연 영상 촬영 전 확인
- [ ] `main` 최신, `mvn test`와 `flutter test` 통과
- [ ] 로컬 DB에 장소 349곳 (`/api/v1/admin/knowledge/status`)
- [ ] 임베딩 모델을 한 번 불러 두기: 코스 요청을 한 번 보내면 됨(첫 요청은 버림)
- [ ] 카카오 지도가 보이는지 (`localhost:8099`)
- [ ] 긴급 문장을 코스에 넣었을 때 긴급 화면으로 가는지 (#101)
- [ ] **출발 시각은 낮으로.** '지금'을 기본값으로 쓰므로 밤에 찍으면 문 닫은 곳이 많아 코스가 짧아집니다. 문장에 "내일 오전 11시부터"처럼 시각을 넣거나 칩으로 바꿉니다
- [ ] 자정을 넘기는 코스는 만들지 않습니다(저녁 8시 이후 4시간 등)
- [ ] 화면에 키·개인정보가 없는지

### 순서 예시
1. **코스**: "내일 오전 11시부터 4시간, 해운대 바다 산책하고 카페" → 지도·시간표 → 설명 → 근거 카드 → "2번 빼고 음식점 넣어줘" → 칩으로 시간 바꾸기 → 일본어로 전환
2. **사진**: 간판 사진 → 식별 → "여기서 출발하는 코스"
3. **긴급**: 코스 입력창에 긴급 문장 → 긴급 화면 → 한국어 카드

## 6. 막혔을 때
| 증상 | 원인과 해결 |
|---|---|
| 지도가 그림으로 나옴 | 키 없음, 또는 8099·3000이 아닌 주소 |
| 첫 요청만 느리거나 후보가 엉뚱함 | 임베딩 모델을 올리는 중. 한 번 더 요청 |
| 설명이 "간략 설명으로 표시 중" | LLM 키·단가·월 한도 확인(서버 로그의 `ai purpose=... result=...`) |
| 코스가 없음 | 너무 늦은 시각이거나 자정을 넘김. 시작 시각을 바꿈 |
| 서버가 안 뜸: Flyway 오류 | 예전 로컬 DB. 버려도 되는 데이터면 `docker compose down -v` 뒤 다시(**로컬 데이터가 지워집니다**) |
| 화면에서 서버에 못 붙음 | CORS는 8099·3000만 허용 |
