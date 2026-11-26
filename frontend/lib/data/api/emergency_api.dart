// [긴급 담당] 긴급 API 연결(API-E01·E02). 경로·필드는 docs/API.md.
// 서버 값 → 화면 문구(종류·운영 상태·병상·출처·기준 시각)는 여기서만 요청 언어로 바꾼다. 문구는 features/emergency/emergency_text.dart.
import '../../core/i18n.dart';
import '../../features/emergency/emergency_text.dart';
import '../models/models.dart';
import '../repositories/repositories.dart';
import 'api_client.dart';

class ApiEmergencyRepository implements EmergencyRepository {
  final ApiClient api;
  ApiEmergencyRepository(this.api);

  @override
  Future<EmQuickResult> quick(String text, Lang lang) async {
    final j = await api.post('/emergency/quick', {'text': text, 'lang': langCode(lang)});
    return EmQuickResult(urgent: j['urgent'] as bool? ?? false, topContact: j['topContact'] as String?, keywordType: emTypeFrom(j['keywordType'] as String?));
  }

  @override
  Future<EmGuideResult> guide(String text, Lang lang, {double? lat, double? lng, Area? area, EmType? type}) async {
    final j = await api.post('/emergency/guide', {
      'text': text, 'lang': langCode(lang), 'lat': lat, 'lng': lng,
      'area': area == null ? null : areaCode(area), 'type': type == null ? null : emTypeCode(type),
    });
    if (j['needTypeSelection'] == true) return const EmGuideResult(needTypeSelection: true);
    final card = j['card'] as Map<String, dynamic>? ?? const {};
    final extras = strList(j['extras']);
    final src = (j['sources'] as List? ?? const []).map((s) => s['title'] as String).join(' · ');
    final fallback = j['stepsFallback'] as bool? ?? false;
    return EmGuideResult(
      type: emTypeFrom((j['triage'] as Map<String, dynamic>?)?['type'] as String?),
      outOfArea: j['outOfArea'] as bool? ?? false,
      topContact: j['topContact'] as String?,
      places: [for (final p in (j['places'] as List? ?? const []).cast<Map<String, dynamic>>()) _place(p, lang)],
      extra: extras.contains('SILENT_REPORT') ? EmExtra.silent : extras.contains('EMBASSY') ? EmExtra.embassy : EmExtra.none,
      todo: strList(j['steps']),
      // FR-E016: 근거 문서 제목, 근거가 없어 저장된 기본 문구면 그 사실을 표시
      todoSource: [if (fallback) et('stepsFallback', lang), if (src.isNotEmpty) et('stepsSrc', lang, [src])].join(' · '),
      cardKo: card['ko'] ?? '', cardTranslated: card['translated'] ?? '', address: card['address'] ?? '',
      dataAsOf: _asOf(j['dataAsOf'] as Map<String, dynamic>?, lang),
      eventId: j['eventId'] as String?,
    );
  }

  @override
  Future<void> callEvent(String? eventId, String number, Lang lang) async {
    await api.post('/emergency/call-event', {'eventId': eventId, 'number': number, 'lang': langCode(lang)});
  }

  HelpPlace _place(Map<String, dynamic> p, Lang lang) {
    final kind = p['kind'] as String? ?? '';
    final er = p['er'] as Map<String, dynamic>?;
    final open = p['openStatus'] as String? ?? 'UNKNOWN';
    final noTreat = er?['noTreatment'] == true;
    final sub = er == null
        ? ''
        : [if (er['availableBeds'] != null) et('beds', lang, [er['availableBeds']]), if (noTreat) et('noTreat', lang), if (er['message'] != null) er['message'] as String].join(' · ');
    return HelpPlace(
      p['id'], p['name'], kEmText.containsKey('kind.$kind') ? et('kind.$kind', lang) : kind, dbl(p['lat']), dbl(p['lng']), p['distanceM'] as int,
      // 오늘 운영시간은 서버가 요청 언어로 준다(FR-E010). 운영 상태를 앞에 붙인다
      [et('open.$open', lang), p['todayHours'] as String? ?? ''].where((e) => e.isNotEmpty).join(' · '), sub,
      isEr: kind == 'ER', openNow: open == 'OPEN', phone: p['phone'] as String?, noTreatment: noTreat,
    );
  }

  /// NFR-E008: "2026-11-11T04:00:00+09:00" → "2026-11-11 04:00" (현지 시각 그대로) + 출처
  String _asOf(Map<String, dynamic>? d, Lang lang) {
    if (d == null) return '';
    final iso = d['places'] as String?;
    final at = iso == null || iso.length < 16 ? null : iso.substring(0, 16).replaceFirst('T', ' ');
    final src = d['source'] as String?;
    if (at != null && src != null) return et('asOf', lang, [at, src]);
    if (at != null) return et('asOfTime', lang, [at]);
    return src == null ? '' : et('asOfSrc', lang, [src]);
  }
}
