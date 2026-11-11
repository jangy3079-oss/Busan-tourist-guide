// [공통] 백엔드 DTO로 만든 견본 JSON(docs/contract/*.json)을 Api*Repository가 제대로 읽는지 검사.
// 백엔드 ContractFixtureTest가 같은 견본을 DTO와 비교한다 → 프론트·백엔드가 같은 계약을 쓰는지 보장.
import 'dart:convert';
import 'dart:io';
import 'package:busan_safetrip/core/i18n.dart';
import 'package:busan_safetrip/data/api/api_client.dart';
import 'package:busan_safetrip/data/api/course_api.dart';
import 'package:busan_safetrip/data/api/emergency_api.dart';
import 'package:busan_safetrip/data/api/photo_api.dart';
import 'package:busan_safetrip/data/models/models.dart';
import 'package:busan_safetrip/data/repositories/repositories.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

String fx(String name) => File('../docs/contract/$name.json').readAsStringSync();

/// (메서드 경로) → 견본 파일. 요청 내용은 calls 에 기록
ApiClient client(Map<String, String> routes, List<http.Request> calls) => ApiClient('http://t', client: MockClient((req) async {
      calls.add(req);
      final key = '${req.method} ${req.url.path}';
      final f = routes[key];
      if (f == null) return http.Response(jsonEncode({'code': 'NOT_FOUND', 'message': key}), 404);
      return http.Response.bytes(utf8.encode(fx(f)), 200, headers: {'content-type': 'application/json; charset=utf-8'});
    }));

void main() {
  test('API-C01 OK → 코스, 요청 필드가 계약과 같다', () async {
    final calls = <http.Request>[];
    final repo = ApiCourseRepository(client({'POST /api/v1/courses': 'C01_course_ok'}, calls));
    final r = await repo.create('광안리 3시간', Lang.ko, startPlaceId: '2789475', overrides: const CourseOverrides(startTime: '15:00', durationMin: 180, area: Area.gwangalli));
    final c = r.course!;
    expect(c.id, 'c_8f2a');
    expect(c.version, 1);
    expect(c.chips, ['3시간', '광안리']);
    expect(c.assumptions.map((a) => '${a.key}=${a.value}'), ['durationMin=180', 'area=GWANGALLI']); // 가정 칩 바꾸기용(FR-C003)
    expect(c.relaxed, ['실내 조건 해제']);
    expect(c.stops[1].hoursUncertain, true);
    expect(c.stops[1].desc, contains('도보 12분'));
    expect(c.stops[0].time, '14:00 – 15:10');
    expect(c.path.length, 2);
    final sent = jsonDecode(calls.single.body);
    final want = jsonDecode(fx('REQ_C01_create'));
    (want['overrides'] as Map).removeWhere((_, v) => v == null); // 프론트는 null 필드를 보내지 않는다
    expect(sent, want);
  });

  test('API-C01 EMERGENCY / OUT_OF_SCOPE', () async {
    final a = ApiCourseRepository(client({'POST /api/v1/courses': 'C01_course_emergency'}, []));
    expect((await a.create('친구가 쓰러졌어요', Lang.ko)).emergency, EmType.urgent);
    final b = ApiCourseRepository(client({'POST /api/v1/courses': 'C01_course_out_of_scope'}, []));
    final n = (await b.create('주식 추천', Lang.ko)).notice!;
    expect(n.examples, ['해운대에서 2시간 산책 코스']);
  });

  test('API-C02 / C03 / C04 / C05 경로', () async {
    final calls = <http.Request>[];
    final repo = ApiCourseRepository(client({
      'POST /api/v1/courses/c_8f2a/narration': 'C02_narration',
      'POST /api/v1/courses/c_8f2a/edits': 'C01_course_ok',
      'GET /api/v1/places': 'C04_place_pins',
      'GET /api/v1/places/126081': 'C05_place_detail',
    }, calls));
    final n = await repo.narrate('c_8f2a', Lang.en);
    expect(n.title, '바다 보며 걷다 회로 마무리');
    expect(n.items['126081'], isNotEmpty);
    expect(jsonDecode(calls.last.body), {'lang': 'en'});
    expect((await repo.edit('c_8f2a', Lang.ko, text: '2번 빼줘')).course, isNotNull);
    expect(jsonDecode(calls.last.body), {'text': '2번 빼줘', 'lang': 'ko'});
    // 가정 칩 변경도 같은 수정 API로, 바꾼 값만 보낸다 (FR-C003)
    await repo.edit('c_8f2a', Lang.ko, overrides: const CourseOverrides(durationMin: 120));
    final want = jsonDecode(fx('REQ_C03_edit_chip'))..removeWhere((_, v) => v == null);
    (want['overrides'] as Map).removeWhere((_, v) => v == null);
    expect(jsonDecode(calls.last.body), want);
    await repo.edit('c_8f2a', Lang.ko, startPlaceId: '264570');
    expect(jsonDecode(calls.last.body), {'lang': 'ko', 'startPlaceId': '264570'});
    expect((await repo.startCandidates(Area.gwangalli)).single.contentId, '126081');
    expect(calls.last.url.queryParameters['area'], 'GWANGALLI');
    final e = await repo.evidence('126081', Lang.ko);
    expect(e.source, 'ⓒ한국관광공사 · 수집일 2026-10-12');
  });

  test('API-P01 / P02 / P03', () async {
    final calls = <http.Request>[];
    final repo = ApiPhotoRepository(client({
      'POST /api/v1/photo/match': 'P01_match_confirmed',
      'POST /api/v1/places/2789475/narration': 'P02_narration',
      'GET /api/v1/places/search': 'P03_search_not_found',
    }, calls));
    final r = await repo.match([1, 2, 3], Lang.ko, fileName: 'a.jpg', lat: 35.16, lng: 129.16);
    expect(r.decision, MatchDecision.confirmed);
    expect(r.matchId, 'm_51c0');
    expect(r.candidates.single.score, 96);
    expect(r.confirmed!.distM, 420);
    expect(r.confirmed!.openNow, true);
    expect(calls.last.headers['content-type'], startsWith('multipart/form-data'));
    expect(await repo.narrate('2789475', Lang.ko, matchId: 'm_51c0'), isNotEmpty);
    expect(jsonDecode(calls.last.body), {'lang': 'ko', 'matchId': 'm_51c0'});
    expect((await repo.search('없는가게', Lang.ko)).decision, MatchDecision.notFound);
    expect(calls.last.url.queryParameters['q'], '없는가게');
  });

  test('API-E01 / E02 / 유형 선택 필요', () async {
    final calls = <http.Request>[];
    final repo = ApiEmergencyRepository(client({'POST /api/v1/emergency/quick': 'E01_quick', 'POST /api/v1/emergency/guide': 'E02_guide'}, calls));
    final q = await repo.quick('친구가 쓰러졌어요', Lang.ko);
    expect(q.topContact, '119');
    expect(q.keywordType, EmType.urgent);
    final g = await repo.guide('아파요', Lang.en, area: Area.haeundae, type: EmType.mild);
    expect(jsonDecode(calls.last.body), jsonDecode(fx('REQ_E02_guide'))..removeWhere((_, v) => v == null));
    expect(g.type, EmType.urgent);
    expect(g.places.single.isEr, true);
    expect(g.places.single.sub, contains('가용병상 3'));
    expect(g.places.single.phone, '051-610-9000');
    expect(g.cardTranslated, startsWith('My friend'));
    expect(g.todo.length, 2);
    expect(g.extra, EmExtra.silent);
    final need = ApiEmergencyRepository(client({'POST /api/v1/emergency/guide': 'E02_guide_need_type'}, []));
    expect((await need.guide('???', Lang.ko, area: Area.gwangalli)).needTypeSelection, true);
  });

  test('오류 응답은 ApiException', () async {
    final repo = ApiCourseRepository(client({}, []));
    expect(() => repo.create('a', Lang.ko), throwsA(isA<ApiException>().having((e) => e.code, 'code', 'NOT_FOUND')));
  });
}
