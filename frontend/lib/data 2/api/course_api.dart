// [코스 담당] 코스 API 연결(API-C01~C05). 경로·필드는 docs/API.md.
import '../../core/i18n.dart';
import '../models/models.dart';
import '../repositories/repositories.dart';
import 'api_client.dart';

class ApiCourseRepository implements CourseRepository {
  final ApiClient api;
  ApiCourseRepository(this.api);

  @override
  Future<CourseResult> create(String query, Lang lang, {String? startPlaceId, CourseOverrides? overrides}) async =>
      _result(await api.post('/courses', {'query': query, 'lang': langCode(lang), 'startPlaceId': startPlaceId, 'overrides': _overrides(overrides)}));

  @override
  Future<CourseResult> edit(String courseId, Lang lang, {String? text, CourseOverrides? overrides, String? startPlaceId}) async =>
      _result(await api.post('/courses/$courseId/edits', {'text': text, 'lang': langCode(lang), 'overrides': _overrides(overrides), 'startPlaceId': startPlaceId}));

  Map<String, dynamic>? _overrides(CourseOverrides? o) => o == null
      ? null
      : ({'visitDate': o.visitDate, 'startTime': o.startTime, 'durationMin': o.durationMin, 'area': o.area == null ? null : areaCode(o.area!)}
        ..removeWhere((_, v) => v == null));

  @override
  Future<CourseNarration> narrate(String courseId, Lang lang) async {
    final j = await api.post('/courses/$courseId/narration', {'lang': langCode(lang)});
    return CourseNarration(j['title'] as String? ?? '',
        items: {for (final i in (j['items'] as List? ?? const [])) i['contentId'] as String: i['text'] as String},
        template: j['template'] as bool? ?? false);
  }

  @override
  Future<List<PlacePin>> startCandidates(Area area) async {
    final j = await api.get('/places', {'area': areaCode(area)});
    return [for (final p in j['places'] as List) PlacePin(p['contentId'], p['name'], p['category'] ?? '', dbl(p['lat']), dbl(p['lng']))];
  }

  @override
  Future<Evidence> evidence(String contentId, Lang lang) async {
    final j = await api.get('/places/$contentId', {'lang': langCode(lang)});
    final collected = (j['collectedAt'] as String?)?.split('T').first;
    return Evidence(j['name'] ?? '', j['overview'] ?? '', j['hoursText'] ?? '', [j['source'] ?? 'ⓒ한국관광공사', if (collected != null) '수집일 $collected'].join(' · '));
  }

  CourseResult _result(Map<String, dynamic> j) {
    final status = j['status'] as String;
    final n = j['notice'] as Map<String, dynamic>?;
    if (status == 'EMERGENCY') return CourseResult(emergency: emTypeFrom(n?['emergencyType'] as String?) ?? EmType.urgent);
    if (status != 'OK') {
      return CourseResult(notice: CourseNotice(n?['message'] ?? '', examples: strList(n?['examples']), suggestions: strList(n?['suggestions'])));
    }
    final assumptions = (j['assumptions'] as List? ?? const []).cast<Map<String, dynamic>>();
    final stops = [
      for (final s in (j['stops'] as List).cast<Map<String, dynamic>>())
        CourseStop(
          s['contentId'], '${s['arriveAt']} – ${s['departAt']}', s['name'],
          [s['category'], if (s['moveFromPrev'] != null) _move(s['moveFromPrev'])].whereType<String>().where((e) => e.isNotEmpty).join(' · '),
          dbl(s['lat']), dbl(s['lng']),
          changed: s['changed'] ?? false, fixed: s['fixed'] ?? false, hoursUncertain: s['hoursUncertain'] ?? false,
        ),
    ];
    final areaA = assumptions.where((a) => a['key'] == 'area').map((a) => a['value'] as String?).firstOrNull;
    return CourseResult(course: Course(
      j['courseId'], j['version'] ?? 1,
      [for (final a in assumptions) a['label'] as String],
      stops,
      [for (final p in (j['path'] as List? ?? const [])) MapPoint(dbl(p['lat']), dbl(p['lng']))],
      areaFrom(areaA),
      relaxed: strList(j['relaxed']),
      assumptions: [for (final a in assumptions) CourseAssumption(a['key'], '${a['value']}', a['label'], isDefault: a['isDefault'] ?? false)],
    ));
  }

  String _move(Map<String, dynamic> m) {
    const label = {'WALK': '도보', 'TRANSIT': '대중교통', 'TAXI': '택시'};
    return '${label[m['mode']] ?? m['mode']} ${m['minutes']}분';
  }
}
