// [공통] 화면이 쓰는 모델. 서버 JSON(docs/API.md)과의 변환은 data/api/*_api.dart 가 맡는다.
// 필드를 바꾸려면 docs/API.md · 백엔드 *Dtos.java 와 같은 커밋에서 고친다.
import '../../core/i18n.dart';

enum Area { gwangalli, haeundae }

class AreaInfo {
  final String name;
  final List<String> nm; // ko, en, ja
  final double lat, lng;
  final String addr, addrEn, addrJa;
  const AreaInfo(this.name, this.nm, this.lat, this.lng, this.addr, this.addrEn, this.addrJa);
  String nameOf(Lang l) => nm[l.i];
  String addrOf(Lang l) => l == Lang.ko ? addr : (l == Lang.en ? addrEn : addrJa);
}

/// 좌표가 있는 지도 핀 (코스 정차지·도움처·식별된 가게 공통)
class MapPoint {
  final double lat, lng;
  final String? label; // 핀 안 글자 (기본: 순번)
  final bool isEr, isFixed;
  const MapPoint(this.lat, this.lng, {this.label, this.isEr = false, this.isFixed = false});
}

// ---------------- 긴급 (API-E01/E02) ----------------
enum EmType { urgent, mild, lost, threat, fire, way, non }
enum EmExtra { none, silent, embassy }

class HelpPlace {
  final String id, name, kind; // kind: 약국·편의점·응급실·지구대·관광안내소 (한글 표시명)
  final double lat, lng;
  final int distM;
  final String status, sub; // 오늘 운영시간·운영 상태 문구, 부가 문구(응급실 병상·경고 등)
  final bool isEr, openNow;
  final String? phone; // FR-E014 전화 걸기
  const HelpPlace(this.id, this.name, this.kind, this.lat, this.lng, this.distM, this.status, this.sub, {this.isEr = false, this.openNow = true, this.phone});
  MapPoint get point => MapPoint(lat, lng, isEr: isEr);
}

/// API-E01 즉시 응답: 키워드만으로 1초 안에 오는 연락 버튼 강조 정보
class EmQuickResult {
  final bool urgent;
  final String? topContact; // '119' | '112' | null(기본 순서)
  final EmType? keywordType;
  const EmQuickResult({this.urgent = false, this.topContact, this.keywordType});
}

/// API-E02 상세 안내: 분류·도움처·한국어 카드·지금 할 일 (요청한 언어로 옴)
class EmGuideResult {
  final EmType? type; // null 이면 needTypeSelection
  final bool needTypeSelection; // FR-E008 유형 직접 선택
  final bool outOfArea; // FR-E017 대상 지역 밖 → 연락 버튼만
  final String? topContact; // 최종 긴급도 반영. 비긴급이면 '1330'
  final List<HelpPlace> places;
  final EmExtra extra;
  final List<String> todo; // 지금 할 일 2~3줄
  final String todoSource;
  final String cardKo, cardTranslated, address; // 한국어 카드(FR-E015)
  const EmGuideResult({
    this.type, this.needTypeSelection = false, this.outOfArea = false, this.topContact,
    this.places = const [], this.extra = EmExtra.none, this.todo = const [], this.todoSource = '',
    this.cardKo = '', this.cardTranslated = '', this.address = '',
  });
}

// ---------------- 코스 (API-C01~C05) ----------------
class CourseStop {
  final String id, time, name, desc; // id = contentId
  final double lat, lng;
  final bool changed, fixed, hoursUncertain; // hoursUncertain → '방문 전 확인'(FR-C009)
  const CourseStop(this.id, this.time, this.name, this.desc, this.lat, this.lng, {this.changed = false, this.fixed = false, this.hoursUncertain = false});
  MapPoint get point => MapPoint(lat, lng, isFixed: fixed);
}

/// 가정 칩 하나 (FR-C003). key: visitDate | startTime | durationMin | area, value는 서버 값(예: '180', 'GWANGALLI')
class CourseAssumption {
  final String key, value, label;
  final bool isDefault; // 요청에 없어 기본값을 쓴 칩
  const CourseAssumption(this.key, this.value, this.label, {this.isDefault = false});
}

class Course {
  final String id; // courseId (설명·수정 요청에 사용)
  final int version; // 1부터. 수정할 때마다 +1
  final List<String> chips; // 가정 칩 라벨 (FR-C003)
  final List<CourseStop> stops;
  final List<MapPoint> path;
  final Area area;
  final int? changedChipIndex;
  final List<String> relaxed; // 조건 완화로 푼 항목 (FR-C007)
  final List<CourseAssumption> assumptions; // 바꿀 수 있는 가정 칩. 비어 있으면 chips를 표시만
  const Course(this.id, this.version, this.chips, this.stops, this.path, this.area, {this.changedChipIndex, this.relaxed = const [], this.assumptions = const []});
  bool get hasFixedStart => stops.any((s) => s.fixed); // 사진(FR-P015) 또는 지도(FR-C014)로 고른 출발 장소
  Course withChanged(int? chipIndex) => Course(id, version, chips, stops, path, area, changedChipIndex: chipIndex, relaxed: relaxed, assumptions: assumptions);
}

/// API-C01/C03 이 코스 대신 돌려주는 안내 (범위 밖·코스 없음·수정 문장 이해 불가)
class CourseNotice {
  final String message;
  final List<String> examples, suggestions;
  const CourseNotice(this.message, {this.examples = const [], this.suggestions = const []});
  String get text => [message, ...examples.map((e) => '예: $e'), ...suggestions].join('\n');
}

/// API-C01/C03 응답. course · emergency · notice 중 하나만 채워진다
class CourseResult {
  final Course? course;
  final EmType? emergency; // FR-C013 긴급 연결
  final CourseNotice? notice;
  const CourseResult({this.course, this.emergency, this.notice});
}

/// API-C02 코스 설명
class CourseNarration {
  final String title;
  final Map<String, String> items; // contentId → 60자 설명
  final bool template;
  const CourseNarration(this.title, {this.items = const {}, this.template = false});
  String get text => [title, ...items.values].where((s) => s.isNotEmpty).join('\n');
}

/// API-C04 출발 장소 후보 핀 (FR-C014)
class PlacePin {
  final String contentId, name, category;
  final double lat, lng;
  const PlacePin(this.contentId, this.name, this.category, this.lat, this.lng);
}

/// API-C05 근거 카드
class Evidence {
  final String name, overview, hours, source;
  const Evidence(this.name, this.overview, this.hours, this.source);
}

// ---------------- 사진 (API-P01~P03) ----------------
enum MatchDecision { confirmed, candidates, notFound, ocrFail }

class PhotoPlace {
  final String contentId, name, category, address, hours;
  final String? romanized; // 서버 계약에는 없음(FR-P010: 원문 그대로). 목업 표시용
  final double lat, lng;
  final int? distM; // 위치 없거나 이름 검색이면 null
  final bool openNow;
  final String? overview, source; // TourAPI 개요 원문, 출처·수집일
  const PhotoPlace(this.contentId, this.name, this.category, this.address, this.romanized, this.hours, this.lat, this.lng, this.distM, this.openNow, {this.overview, this.source});
}

class PhotoCandidate {
  final PhotoPlace place;
  final int score; // 0~100 (서버는 0~1)
  const PhotoCandidate(this.place, this.score);
}

class MatchResult {
  final MatchDecision decision;
  final List<PhotoCandidate> candidates; // confirmed면 1개, candidates면 최대 3개
  final String? matchId; // API-P02 에 넘겨 선택 로그(FR-P019)
  final bool lowAccuracy; // 위치 없이 찾음 → "정확도가 낮을 수 있음"(FR-P003)
  const MatchResult(this.decision, this.candidates, {this.matchId, this.lowAccuracy = false});
  PhotoPlace? get confirmed => decision == MatchDecision.confirmed ? candidates.first.place : null;
}
