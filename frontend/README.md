# 부산 안심여행 프론트 (Flutter Web)

확정 프로토타입(김희찬) 기준. 기본은 `Mock*Repository`로 동작하고, `--dart-define=API_BASE=...`를 주면 `Api*Repository`(실제 백엔드)를 쓴다. API 계약은 `../docs/API.md`.

## 실행
```
flutter pub get
flutter run -d chrome          # 개발(Mock)
flutter run -d chrome --dart-define=API_BASE=http://localhost:8080   # 실제 백엔드
flutter test                   # API 계약 견본(../docs/contract) 읽기 검사
flutter build web              # build/web 생성
flutter analyze
```

## 폴더와 소유권 (자세한 규칙: ../docs/OWNERSHIP.md)
| 경로 | 소유 |
|---|---|
| `lib/core`, `lib/widgets`, `lib/state`, `lib/router.dart`, `lib/main.dart` | 공용 |
| `lib/data/models`, `lib/data/repositories/repositories.dart` | 공용 (API 계약, 바꾸려면 백엔드와 합의) |
| `lib/data/mock_data.dart`, `mock_repositories.dart` | 공용 (백엔드 연결 후 정리) |
| `lib/data/api/api_client.dart` | 공용 (HTTP·오류 형식) |
| `lib/data/api/course_api.dart` · `photo_api.dart` · `emergency_api.dart` | 코스 · 사진 · 긴급 담당 |
| `lib/features/bridge.dart` | 공용 (사진→코스, 코스→긴급, 긴급→코스 연결) |
| `lib/features/home` | 공용 |
| `lib/features/course` | 코스 담당 |
| `lib/features/photo` | 사진 담당 |
| `lib/features/emergency` | 긴급 담당 |

## 알려진 한계
- 지도는 임시 그림(CustomPainter). 카카오맵 연동 필요(COMP-C09)
- 번역은 긴급 문구만 적용(프로토타입 범위). SRS는 전 화면 ko/en/ja
- 위치 권한 서비스가 없어 긴급·사진 요청에 좌표 대신 지역(area)만 보냄(TODO)
- 가정 칩 바꾸기(FR-C003)·결과 화면의 출발 장소 바꾸기(FR-C014)는 대화형 수정과 같은 API-C03으로 보냄 → 지금 버전 위에 새 버전이 쌓임(이전 수정 유지)
- 분류 키워드는 목업용. 실제 판정은 백엔드 KeywordRuleEngine
