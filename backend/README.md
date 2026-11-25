# 부산 안심여행 백엔드 (Spring Boot 골격)

Java 17+ / Spring Boot 3.4 / PostgreSQL + pgvector / Flyway

- 실행(처음 한 번): 저장소 루트에서 `docker compose up -d` (PostgreSQL 16 + pgvector, 계정·DB 모두 `safetrip`)
- 서버: `cd backend && mvn spring-boot:run` → `curl localhost:8080/api/v1/health` 가 `{"status":"ok"}` 이면 성공
- 프론트를 이 서버에 붙이기: `cd frontend && flutter run -d chrome --web-port 8099 --dart-define=API_BASE=http://localhost:8080` (CORS 허용 포트: 8099, 3000)
- 테스트: `mvn test` (소유권 경계 검사 `ArchitectureTest` 포함)
- 관리자 API는 `--spring.profiles.active=local-admin`일 때만 켜진다.
- 아직 구현되지 않은 메서드는 `NotImplementedYetException`을 던지고 HTTP 501로 응답한다. 구현하면서 이 줄을 지워 나간다.
- 누가 어디를 고치는지는 `../docs/OWNERSHIP.md`, 추가로 필요한 함수는 `../docs/EXTRA_FUNCTIONS.md`.

## 키와 환경변수
- `.env.example`을 `.env`로 복사해 값을 채운다(`.env`는 커밋되지 않는다). 서버가 자동으로 읽지 않으므로 `set -a; . ./.env; set +a; mvn spring-boot:run` 으로 켠다.
- **`DB_URL`을 넣고 켜면 그 DB에 Flyway 마이그레이션이 실행된다.** 로컬 DB를 쓰면 `DB_*` 줄을 지운다.
- 공용 DB에 붙을 때는 `SPRING_FLYWAY_ENABLED=false`로 켠다. 테이블 변경은 `main`에 병합된 것만 공용 담당(동현)이 올리고, 테스트 데이터는 로컬에만 넣는다(#140).
- LLM: 아무것도 안 넣으면 로컬 Ollama `qwen3.5:4b`(`ollama pull qwen3.5:4b`). 첫 호출은 모델을 올리느라 8초를 넘겨 대체 응답이 나올 수 있으니 시연 전에 `ollama run qwen3.5:4b`로 한 번 불러 둔다.
- LLM 제한 시간: 한 번 8초, 재시도 1회를 합쳐 총 10초(`total-timeout-sec`). 모듈이 더 짧게 정할 수 있다(긴급 5초·0회).
- 임베딩: LLM과 별개로 로컬 Ollama `bge-m3`를 쓴다(`ollama pull bge-m3`). 없으면 코스 후보 검색이 벡터 없이 대체된다.
- 유료 API: `AI_PROVIDER=openai`(Claude면 `claude`, `AI_BASE_URL=https://api.anthropic.com`)와 키·모델·단가(`AI_*_KRW_PER_MTOK`)를 넣는다. 이 PC의 월 누적이 `AI_MONTHLY_BUDGET_KRW`를 넘으면 호출을 막고 각 모듈이 대체 응답을 한다. 누적은 PC마다 따로 세므로 발급처 사이트의 사용량 한도도 건다.
- 발급처: TourAPI·국립중앙의료원은 data.go.kr 활용신청, V-World는 vworld.kr 인증키, LLM은 해당 업체 콘솔. 키는 채팅·이슈에 올리지 말고 개인적으로 전달한다.
