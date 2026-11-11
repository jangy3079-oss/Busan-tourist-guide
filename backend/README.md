# 부산 안심여행 백엔드 (Spring Boot 골격)

Java 17+ / Spring Boot 3.4 / PostgreSQL + pgvector / Flyway

- 실행(처음 한 번): 저장소 루트에서 `docker compose up -d` (PostgreSQL 16 + pgvector, 계정·DB 모두 `safetrip`)
- 서버: `cd backend && mvn spring-boot:run` → `curl localhost:8080/api/v1/health` 가 `{"status":"ok"}` 이면 성공
- 프론트를 이 서버에 붙이기: `cd frontend && flutter run -d chrome --web-port 8099 --dart-define=API_BASE=http://localhost:8080` (CORS 허용 포트: 8099, 3000)
- 테스트: `mvn test` (소유권 경계 검사 `ArchitectureTest` 포함)
- 관리자 API는 `--spring.profiles.active=local-admin`일 때만 켜진다.
- 아직 구현되지 않은 메서드는 `NotImplementedYetException`을 던지고 HTTP 501로 응답한다. 구현하면서 이 줄을 지워 나간다.
- 누가 어디를 고치는지는 `../docs/OWNERSHIP.md`, 추가로 필요한 함수는 `../docs/EXTRA_FUNCTIONS.md`.
