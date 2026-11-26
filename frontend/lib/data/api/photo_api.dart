// [사진 담당] 사진 API 연결(API-P01~P03). 경로·필드는 docs/API.md.
import '../../core/i18n.dart';
import '../models/models.dart';
import '../repositories/repositories.dart';
import 'api_client.dart';

class ApiPhotoRepository implements PhotoRepository {
  final ApiClient api;
  ApiPhotoRepository(this.api);

  @override
  Future<MatchResult> match(List<int>? imageBytes, Lang lang, {String fileName = 'photo.jpg', double? lat, double? lng, String? sample}) async {
    if (imageBytes == null) return const MatchResult(MatchDecision.ocrFail, []); // 샘플 시연은 Mock 전용
    final j = await api.multipart('/photo/match', field: 'image', bytes: imageBytes, fileName: fileName, fields: {
      'lang': langCode(lang),
      if (lat != null) 'lat': '$lat',
      if (lng != null) 'lng': '$lng',
    });
    return _result(j, lang);
  }

  @override
  Future<String> narrate(String contentId, Lang lang, {String? matchId}) async {
    final j = await api.post('/places/$contentId/narration', {'lang': langCode(lang), 'matchId': matchId});
    return j['text'] as String? ?? '';
  }

  @override
  Future<MatchResult> search(String q, Lang lang) async => _result(await api.get('/places/search', {'q': q, 'lang': langCode(lang)}), lang);

  MatchResult _result(Map<String, dynamic> j, Lang lang) {
    const dec = {'CONFIRMED': MatchDecision.confirmed, 'CANDIDATES': MatchDecision.candidates, 'NOT_FOUND': MatchDecision.notFound, 'OCR_FAIL': MatchDecision.ocrFail};
    return MatchResult(
      dec[j['decision']] ?? MatchDecision.notFound,
      [
        for (final c in (j['candidates'] as List? ?? const []).cast<Map<String, dynamic>>())
          PhotoCandidate(_place(c['place'] as Map<String, dynamic>, c['distanceM'] as int?, lang), (dbl(c['score']) * 100).round()),
      ],
      matchId: j['matchId'] as String?,
      lowAccuracy: j['lowAccuracy'] as bool? ?? false,
    );
  }

  PhotoPlace _place(Map<String, dynamic> p, int? dist, Lang lang) {
    return PhotoPlace(
      p['contentId'], p['name'], p['category'] ?? '', p['address'] ?? '', null, p['hoursText'] ?? '',
      dbl(p['lat']), dbl(p['lng']), dist, p['openStatus'] == 'OPEN',
      overview: p['overview'] as String?,
      source: sourceLine(p['source'] as String?, p['collectedAt'] as String?, lang), // 출처 표기는 원문, 수집일은 요청 언어(#225, #169)
    );
  }
}
