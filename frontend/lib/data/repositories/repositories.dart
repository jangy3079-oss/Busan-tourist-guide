// [공통] 백엔드 호출 경계. 화면·상태는 이 인터페이스만 안다. 메서드 하나 = API 하나(docs/API.md).
// 기본은 Mock*, --dart-define=API_BASE=... 를 주면 Api*Repository (state/providers.dart).
import '../models/models.dart';
import '../../core/i18n.dart';

/// FR-C003 사용자가 바꾼 가정 칩 값
class CourseOverrides {
  final String? visitDate, startTime;
  final int? durationMin;
  final Area? area;
  const CourseOverrides({this.visitDate, this.startTime, this.durationMin, this.area});
}

abstract class CourseRepository {
  /// API-C01 POST /courses
  Future<CourseResult> create(String query, Lang lang, {String? startPlaceId, CourseOverrides? overrides});
  /// API-C02 POST /courses/{id}/narration (코스와 분리)
  Future<CourseNarration> narrate(String courseId, Lang lang);
  /// API-C03 POST /courses/{id}/edits — 지금 버전에 대한 수정 → 새 버전(이전 수정 유지).
  /// text: 대화형 수정(FR-C015), overrides: 바꾼 가정 칩 값만(FR-C003), startPlaceId: 출발 장소 고정(FR-C014)
  Future<CourseResult> edit(String courseId, Lang lang, {String? text, CourseOverrides? overrides, String? startPlaceId});
  /// API-C04 GET /places (출발 장소 고르기용 핀)
  Future<List<PlacePin>> startCandidates(Area area);
  /// API-C05 GET /places/{contentId}
  Future<Evidence> evidence(String contentId, Lang lang);
}

abstract class PhotoRepository {
  /// API-P01 POST /photo/match. 사진은 서버에 저장되지 않음. sample은 목업 시연용
  Future<MatchResult> match(List<int>? imageBytes, Lang lang, {String fileName = 'photo.jpg', double? lat, double? lng, String? sample});
  /// API-P02 POST /places/{contentId}/narration
  Future<String> narrate(String contentId, Lang lang, {String? matchId});
  /// API-P03 GET /places/search
  Future<MatchResult> search(String q, Lang lang);
}

abstract class EmergencyRepository {
  /// API-E01 POST /emergency/quick (키워드만, 1초)
  Future<EmQuickResult> quick(String text, Lang lang);
  /// API-E02 POST /emergency/guide. 위치가 없으면 area, 유형을 직접 고르면 type
  Future<EmGuideResult> guide(String text, Lang lang, {double? lat, double? lng, Area? area, EmType? type});
}
