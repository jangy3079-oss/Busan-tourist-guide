// [긴급 담당] 긴급 API 연결(API-E01·E02). 경로·필드는 docs/API.md.
import '../../core/i18n.dart';
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
    return EmGuideResult(
      type: emTypeFrom((j['triage'] as Map<String, dynamic>?)?['type'] as String?),
      outOfArea: j['outOfArea'] as bool? ?? false,
      topContact: j['topContact'] as String?,
      places: [for (final p in (j['places'] as List? ?? const []).cast<Map<String, dynamic>>()) _place(p)],
      extra: extras.contains('SILENT_REPORT') ? EmExtra.silent : extras.contains('EMBASSY') ? EmExtra.embassy : EmExtra.none,
      todo: strList(j['steps']),
      todoSource: src.isEmpty ? '' : '근거: $src',
      cardKo: card['ko'] ?? '', cardTranslated: card['translated'] ?? '', address: card['address'] ?? '',
    );
  }

  HelpPlace _place(Map<String, dynamic> p) {
    const kind = {'ER': '응급실', 'PHARMACY': '약국', 'CONVENIENCE': '편의점', 'POLICE': '지구대', 'TOURIST_INFO': '관광안내소', 'HOSPITAL': '병원', 'FIRE_STATION': '소방서'};
    final er = p['er'] as Map<String, dynamic>?;
    final open = p['openStatus'] as String?;
    final sub = er == null
        ? (p['phone'] as String? ?? '')
        : [if (er['availableBeds'] != null) '가용병상 ${er['availableBeds']}', if (er['noTreatment'] == true) '⚠ 진료불가 메시지', if (er['message'] != null) er['message']].join(' · ');
    return HelpPlace(
      p['id'], p['name'], kind[p['kind']] ?? p['kind'], dbl(p['lat']), dbl(p['lng']), p['distanceM'] as int,
      [p['todayHours'] ?? '', if (open == 'UNKNOWN') '운영 미확인'].where((e) => e.isNotEmpty).join(' · '), sub,
      isEr: p['kind'] == 'ER', openNow: open == 'OPEN', phone: p['phone'] as String?,
    );
  }
}
