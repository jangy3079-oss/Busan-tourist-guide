// [공통·임시] 프로토타입과 같은 지연·데이터를 흉내 내는 Mock. 백엔드 없이 화면을 돌려보는 용도.
import '../../core/i18n.dart';
import '../mock_data.dart';
import '../models/models.dart';
import 'repositories.dart';

Future<void> _wait(int ms) => Future<void>.delayed(Duration(milliseconds: ms));

bool _isEmergency(EmType? t) => t != null && t != EmType.non;

class MockCourseRepository implements CourseRepository {
  final _store = <String, Course>{}; // courseId → 코스 (서버의 course_plan 버전 저장을 흉내)
  int _n = 10;

  Course _save(Course c) => _store[c.id] = c;

  @override
  Future<CourseResult> create(String query, Lang lang, {String? startPlaceId, CourseOverrides? overrides}) async {
    final ty = mockClassify(query);
    if (_isEmergency(ty)) return CourseResult(emergency: ty);
    await _wait(1100);
    if (ty == EmType.non && RegExp('예약|reservation|予約').hasMatch(query)) {
      return const CourseResult(notice: CourseNotice('광안리·해운대 관광 코스만 도와드려요.', examples: ['광안리에서 바다 보면서 3시간']));
    }
    final gw = kCourses['v1']!, hd = kCourses['photo']!;
    final inGw = gw.stops.any((s) => s.id == startPlaceId);
    final area = overrides?.area ?? (startPlaceId == null || inGw ? Area.gwangalli : Area.haeundae);
    final base = area == Area.gwangalli ? gw : hd;
    final plain = Course(base.id, 0, base.chips, [for (final s in base.stops) CourseStop(s.id, s.time, s.name, s.desc, s.lat, s.lng)], base.path, area, assumptions: base.assumptions);
    return CourseResult(course: _save(_derive(plain, overrides: overrides, startPlaceId: startPlaceId, markChanged: false)));
  }

  /// 지금 버전 위에 수정을 쌓아 새 버전을 만든다 (서버의 API-C03 흉내)
  @override
  Future<CourseResult> edit(String courseId, Lang lang, {String? text, CourseOverrides? overrides, String? startPlaceId}) async {
    if (text != null) {
      final ty = mockClassify(text);
      if (_isEmergency(ty)) return CourseResult(emergency: ty);
    }
    await _wait(text != null ? 300 : 900);
    final cur = _store[courseId] ?? kCourses['v1']!;
    if (text != null && !RegExp(r'\d|줄|늘|빼|넣|바꿔|shorter|longer|remove|add').hasMatch(text)) {
      return const CourseResult(notice: CourseNotice('어떻게 바꿀지 알아듣지 못했어요.', examples: ['2시간으로 줄여줘', '2번 빼고 카페 넣어줘']));
    }
    // 대화 수정 목업: "N시간" 이 있으면 그 시간으로, 아니면 2시간으로 줄임
    final h = text == null ? null : int.tryParse(RegExp(r'(\d)\s*시간').firstMatch(text)?.group(1) ?? '2');
    final o = text == null ? overrides : CourseOverrides(durationMin: (h ?? 2) * 60);
    return CourseResult(course: _save(_derive(cur, overrides: o, startPlaceId: startPlaceId, markChanged: true)));
  }

  Course _derive(Course cur, {CourseOverrides? overrides, String? startPlaceId, required bool markChanged}) {
    final o = overrides;
    // 출발 시각을 바꾸면 시간표를 그만큼 민다 (목업)
    String v0(String k) => cur.assumptions.firstWhere((a) => a.key == k).value;
    int mins(String hhmm) => int.parse(hhmm.split(':')[0]) * 60 + int.parse(hhmm.split(':')[1]);
    final delta = o?.startTime == null ? 0 : mins(o!.startTime!) - mins(v0('startTime'));
    String fmt(int m) => '${(m ~/ 60).toString().padLeft(2, '0')}:${(m % 60).toString().padLeft(2, '0')}';
    final times = [for (final s in cur.stops) s.time.split(' – ').map((t) => fmt(mins(t) + delta)).join(' – ')];
    var stops = [...cur.stops];
    final pick = stops.indexWhere((s) => s.id == startPlaceId || (startPlaceId == 'MOCK-1' && s.id == 'a'));
    if (pick >= 0) {
      final p = stops.removeAt(pick);
      stops = [CourseStop(p.id, p.time, p.name, '고른 출발 장소', p.lat, p.lng, fixed: true), ...[for (final s in stops) CourseStop(s.id, s.time, s.name, s.desc, s.lat, s.lng, changed: s.changed, hoursUncertain: s.hoursUncertain)]];
    }
    final shift = o?.durationMin != null || o?.startTime != null || pick >= 0;
    stops = [
      for (var i = 0; i < stops.length; i++)
        CourseStop(stops[i].id, times[i], stops[i].name, stops[i].desc, stops[i].lat, stops[i].lng,
            fixed: stops[i].fixed, hoursUncertain: stops[i].hoursUncertain, changed: markChanged && (shift || stops[i].changed)),
    ];
    String v(String k) => cur.assumptions.firstWhere((a) => a.key == k).value;
    String l(String k) => cur.assumptions.firstWhere((a) => a.key == k).label;
    final area = o?.area ?? cur.area;
    final as = mockAssumptions(
      date: o?.visitDate == null ? l('visitDate') : _dateLabel(o!.visitDate!),
      time: o?.startTime ?? v('startTime'),
      min: o?.durationMin ?? int.parse(v('durationMin')),
      area: area,
      timeDefault: o?.startTime == null && cur.assumptions[1].isDefault,
    );
    return Course('mock-c${_n++}', cur.version + 1, [for (final a in as) a.label], stops, area == Area.gwangalli ? kCourses['v1']!.path : const [], area, assumptions: as);
  }

  String _dateLabel(String iso) {
    final d = DateTime.parse(iso), today = DateTime.now();
    final diff = DateTime(d.year, d.month, d.day).difference(DateTime(today.year, today.month, today.day)).inDays;
    return diff == 0 ? '오늘' : diff == 1 ? '내일' : '${d.month}/${d.day}';
  }

  @override
  Future<CourseNarration> narrate(String courseId, Lang lang) async {
    await _wait(1500);
    return const CourseNarration('바다를 오래 보고 싶다는 요청에 맞춰 해변 산책을 먼저 두고, 해 질 무렵 활어직판장에서 식사하도록 짰어요. 세 곳 모두 지금 운영 중이고 걸어서 이동해요.');
  }

  @override
  Future<List<PlacePin>> startCandidates(Area area) async {
    await _wait(400);
    final c = kCourses[area == Area.gwangalli ? 'v1' : 'photo']!;
    return [for (final s in c.stops) PlacePin(s.id, s.name, s.desc.split(' · ').first, s.lat, s.lng)];
  }

  @override
  Future<Evidence> evidence(String contentId, Lang lang) async {
    final name = [for (final c in kCourses.values) ...c.stops].firstWhere((s) => s.id == contentId, orElse: () => kCourses['v1']!.stops.first).name;
    return Evidence(name, '부산을 대표하는 해변 관광지로, 바다 위 광안대교 야경과 사계절 축제가 열리는 곳이다.(원문 일부)', '연중 개방 · 해수욕장 개장 기간 별도', 'ⓒ한국관광공사 · 수집일 2026-10-12');
  }
}

class MockPhotoRepository implements PhotoRepository {
  @override
  Future<MatchResult> match(List<int>? imageBytes, Lang lang, {String fileName = 'photo.jpg', double? lat, double? lng, String? sample}) async {
    await _wait(1400);
    switch (sample) {
      case 'cand':
        return const MatchResult(MatchDecision.candidates, kPhotoCands, matchId: 'mock-m1');
      case 'fail':
        return const MatchResult(MatchDecision.ocrFail, [], matchId: 'mock-m1');
      default:
        return const MatchResult(MatchDecision.confirmed, [PhotoCandidate(kPhotoPlace, 96)], matchId: 'mock-m1');
    }
  }

  @override
  Future<String> narrate(String contentId, Lang lang, {String? matchId}) async {
    await _wait(1500);
    return '해운대 해변 근처에서 오래 영업해 온 소갈비 전문점이에요. 양념한 소갈비를 숯불에 구워 먹는 방식이라, 주문할 때 인원수만큼 갈비를 고르면 돼요.';
  }

  @override
  Future<MatchResult> search(String q, Lang lang) async {
    await _wait(500);
    final hit = kPhotoCands.where((c) => c.place.name.contains(q) || q.contains('갈비') || q.contains('국밥')).toList();
    return hit.isEmpty ? const MatchResult(MatchDecision.notFound, []) : MatchResult(MatchDecision.candidates, hit);
  }
}

class MockEmergencyRepository implements EmergencyRepository {
  List<HelpPlace> _places(EmType ty, Area area) {
    var ids = kTypes[ty]!.placeIds;
    if (area == Area.haeundae && ty == EmType.mild) ids = ['seven', 'cuhd', 'paik'];
    return [for (final i in ids) kPlaces[i]!];
  }

  @override
  Future<EmQuickResult> quick(String text, Lang lang) async {
    await _wait(300);
    final ty = mockClassify(text);
    if (!_isEmergency(ty)) return const EmQuickResult();
    final top = kTypes[ty]!.top;
    return EmQuickResult(urgent: ty == EmType.urgent || ty == EmType.fire || ty == EmType.threat, topContact: top == '1330' ? null : top, keywordType: ty);
  }

  @override
  Future<EmGuideResult> guide(String text, Lang lang, {double? lat, double? lng, Area? area, EmType? type}) async {
    await _wait(1700);
    final ty = type ?? mockClassify(text);
    if (ty == null) return const EmGuideResult(needTypeSelection: true);
    if (ty == EmType.non) return const EmGuideResult(type: EmType.non, topContact: '1330');
    final a = area ?? Area.gwangalli;
    final card = kCard[ty] ?? const ['', '', ''];
    return EmGuideResult(
      type: ty, topContact: kTypes[ty]!.top, places: _places(ty, a), extra: kTypes[ty]!.extra,
      todo: [for (final t in kTodo[ty] ?? const <List<String>>[]) t[lang.i]], todoSource: kTodoSrc[ty] ?? '',
      cardKo: card[0], cardTranslated: card[lang == Lang.ja ? 2 : 1], address: kAreas[a]!.addr,
    );
  }
}
