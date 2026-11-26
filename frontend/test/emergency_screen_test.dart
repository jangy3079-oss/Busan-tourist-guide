// [긴급 담당] 긴급 화면(UI-E01~E06): 연락 버튼 상시 표시·크기·강조 순서, 위치·지역 선택, 유형 선택, 비긴급, 대상 지역 밖, 한국어 카드, 언어 전환 (T20)
import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:busan_safetrip/core/i18n.dart';
import 'package:busan_safetrip/core/location.dart';
import 'package:busan_safetrip/data/api/api_client.dart';
import 'package:busan_safetrip/data/api/emergency_api.dart';
import 'package:busan_safetrip/data/models/models.dart';
import 'package:busan_safetrip/data/repositories/mock_repositories.dart';
import 'package:busan_safetrip/data/repositories/repositories.dart';
import 'package:busan_safetrip/features/course/course_controller.dart';
import 'package:busan_safetrip/features/emergency/emergency_controller.dart';
import 'package:busan_safetrip/features/emergency/emergency_link.dart';
import 'package:busan_safetrip/features/emergency/emergency_screens.dart';
import 'package:busan_safetrip/state/providers.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

/// 응답을 테스트가 정하는 저장소. 보낸 값은 calls 에 남긴다
class _Repo implements EmergencyRepository {
  Completer<EmQuickResult>? quickC;
  Completer<EmGuideResult>? guideC;
  EmQuickResult quickR = const EmQuickResult();
  Object? guideError;
  EmGuideResult guideR = const EmGuideResult(needTypeSelection: true);
  final calls = <Map<String, Object?>>[];
  @override
  Future<EmQuickResult> quick(String text, Lang lang) => quickC?.future ?? Future.value(quickR);
  @override
  Future<EmGuideResult> guide(String text, Lang lang, {double? lat, double? lng, Area? area, EmType? type}) async {
    calls.add({'lat': lat, 'lng': lng, 'area': area, 'type': type, 'lang': lang});
    if (guideError != null) throw guideError!;
    return guideC?.future ?? guideR;
  }
}

/// 계약 견본(docs/contract)을 돌려주는 실제 Api 저장소
ApiEmergencyRepository _contractRepo(List<http.Request> sent) => ApiEmergencyRepository(ApiClient('http://t', client: MockClient((req) async {
      sent.add(req);
      final f = req.url.path.endsWith('/quick') ? 'E01_quick' : 'E02_guide';
      return http.Response.bytes(utf8.encode(File('../docs/contract/$f.json').readAsStringSync()), 200, headers: {'content-type': 'application/json; charset=utf-8'});
    })));

const _here = LocationResult.ok(35.1532, 129.1186);
const _denied = LocationResult.failed(LocationFail.denied);

void main() {
  late ProviderContainer box;
  late List<String> links;

  Future<void> pumpApp(WidgetTester t, EmergencyRepository repo, {LocationResult loc = _here, String at = '/emergency'}) async {
    // 공용 AppHead 의 알려진 디버그 assert(Container 에 color·decoration 동시 지정)만 넘긴다. 공용 담당에게 수정 요청함(T20 보고)
    final onError = FlutterError.onError, builder = ErrorWidget.builder;
    FlutterError.onError = (d) {
      if ('${d.exception}'.contains('color == null || decoration == null') && '${d.stack}'.contains('AppHead.build')) return;
      onError!(d);
    };
    ErrorWidget.builder = (_) => const SizedBox.shrink();
    addTearDown(() { FlutterError.onError = onError; ErrorWidget.builder = builder; });
    t.view.physicalSize = const Size(800, 1800); // 테스트 글꼴(Ahem)은 실제보다 넓어 영어 제목이 넘친다
    t.view.devicePixelRatio = 1;
    addTearDown(t.view.reset);
    links = [];
    box = ProviderContainer(overrides: [
      emergencyRepoProvider.overrideWithValue(repo),
      emLocateProvider.overrideWithValue(() async => loc),
      emLinkProvider.overrideWithValue(links.add),
    ]);
    addTearDown(box.dispose);
    final router = GoRouter(initialLocation: at, routes: [
      GoRoute(path: '/', builder: (_, _) => const Scaffold(body: Text('home'))),
      GoRoute(path: '/course', builder: (_, _) => const Scaffold(body: Text('course-screen'))),
      GoRoute(path: '/emergency', builder: (_, _) => const EmergencyInputScreen()),
      GoRoute(path: '/emergency/area', builder: (_, _) => const EmergencyAreaScreen()),
      GoRoute(path: '/emergency/result', builder: (_, _) => const EmergencyResultScreen()),
      GoRoute(path: '/emergency/non', builder: (_, _) => const EmergencyNonScreen()),
    ]);
    await t.pumpWidget(UncontrolledProviderScope(container: box, child: MaterialApp.router(routerConfig: router)));
    await t.pump();
  }

  Future<void> send(WidgetTester t, String text) async {
    await t.enterText(find.byType(TextField), text);
    await t.tap(find.text(tr('eFind', box.read(langProvider))));
    await t.pump();
    await t.pump();
  }

  void expectThreeButtons() {
    for (final n in ['119', '112', '1330']) {
      expect(find.byKey(ValueKey('call-$n')), findsOneWidget, reason: n);
    }
  }

  List<String> buttonOrder(WidgetTester t) {
    final xs = {for (final n in ['119', '112', '1330']) n: t.getTopLeft(find.byKey(ValueKey('call-$n'))).dx};
    return xs.keys.toList()..sort((a, b) => xs[a]!.compareTo(xs[b]!));
  }

  testWidgets('연락 버튼은 48×48 이상이고 tel: 로 건다 (FR-E004, NFR-E010)', (t) async {
    await pumpApp(t, _Repo());
    expectThreeButtons();
    for (final n in ['119', '112', '1330']) {
      final s = t.getSize(find.byKey(ValueKey('call-$n')));
      expect(s.width, greaterThanOrEqualTo(48));
      expect(s.height, greaterThanOrEqualTo(48));
    }
    await t.tap(find.byKey(const ValueKey('call-112')));
    expect(links, ['tel:112']);
  });

  testWidgets('서버 오류·시간 초과면 오류 카드와 유형 버튼, 연락 버튼 3개가 남는다 (FR-E004, NFR-E003)', (t) async {
    final repo = _Repo()..guideError = TimeoutException('늦음');
    await pumpApp(t, repo);
    await send(t, '아파요');
    await t.pumpAndSettle();
    expect(find.text(tr('errTimeout', Lang.ko)), findsOneWidget);
    expectThreeButtons();
    expect(find.byKey(const ValueKey('type-mild')), findsOneWidget);
    // 유형을 고르면 type 을 지정해 E02 를 다시 부른다(FR-E008)
    repo.guideError = null;
    repo.guideR = const EmGuideResult(type: EmType.mild, topContact: '1330', places: [HelpPlace('p1', '광안약국', '약국', 35.15, 129.11, 300, '운영 중', '')]);
    await t.tap(find.byKey(const ValueKey('type-mild')));
    await t.pumpAndSettle();
    expect(repo.calls.last['type'], EmType.mild);
    expect(find.text('광안약국'), findsOneWidget);
  });

  testWidgets('E01 이 먼저 오면 topContact 를 맨 앞에 강조하고, E02 가 오면 최종값으로 바꾼다 (FR-E003, FR-E007)', (t) async {
    final repo = _Repo()..quickC = Completer()..guideC = Completer();
    await pumpApp(t, repo);
    expect(buttonOrder(t).first, '1330'); // 감지 전 기본 순서
    await send(t, '친구가 쓰러졌어요');
    repo.quickC!.complete(const EmQuickResult(urgent: true, topContact: '119'));
    await t.pump();
    await t.pump();
    expect(buttonOrder(t), ['119', '112', '1330']); // E02 를 기다리지 않음
    await t.tap(find.byKey(const ValueKey('call-119')));
    expect(links, ['tel:119']);
    repo.guideC!.complete(const EmGuideResult(type: EmType.threat, topContact: '112'));
    await t.pumpAndSettle();
    expect(buttonOrder(t), ['112', '119', '1330']);
  });

  testWidgets('위치 허용이면 좌표를 보내고, 거부하면 지역 선택 후 area 로 보낸다 (FR-E002)', (t) async {
    final ok = _Repo()..guideR = const EmGuideResult(type: EmType.mild, topContact: '1330');
    await pumpApp(t, ok);
    await send(t, '열이 나요');
    await t.pumpAndSettle();
    expect(ok.calls.single, containsPair('lat', 35.1532));
    expect(ok.calls.single['area'], isNull);

    final no = _Repo()..guideR = const EmGuideResult(type: EmType.mild, topContact: '1330');
    await pumpApp(t, no, loc: _denied);
    await send(t, '열이 나요');
    await t.pumpAndSettle();
    expect(find.text(tr('areaT', Lang.ko)), findsOneWidget); // UI-E05
    expectThreeButtons();
    expect(no.calls, isEmpty);
    await t.tap(find.byKey(const ValueKey('area-haeundae')));
    await t.pumpAndSettle();
    expect(no.calls.single['area'], Area.haeundae);
    expect(no.calls.single['lat'], isNull);
  });

  testWidgets('needTypeSelection 이면 유형 시트가 열린다 (FR-E008, Mock 저장소)', (t) async {
    await pumpApp(t, MockEmergencyRepository());
    await send(t, '???');
    await t.pump(const Duration(seconds: 2));
    await t.pumpAndSettle();
    expect(find.byKey(const ValueKey('type-sheet')), findsOneWidget);
    expectThreeButtons();
    await t.tap(find.byKey(const ValueKey('type-lost')).last);
    await t.pump(const Duration(seconds: 2));
    await t.pumpAndSettle();
    expect(find.text(tr('embassy', Lang.ko)), findsOneWidget); // EMBASSY 확장
  });

  testWidgets('비긴급이면 1330 을 맨 앞에 두고, 관광 코스 찾기는 문장을 넘기지 않는다 (FR-E018, AC-X03)', (t) async {
    await pumpApp(t, MockEmergencyRepository());
    await send(t, '해운대 맛집 추천해 주세요');
    await t.pump(const Duration(seconds: 2));
    await t.pumpAndSettle();
    expect(find.byKey(const ValueKey('find-course')), findsOneWidget);
    expect(buttonOrder(t).first, '1330');
    await t.tap(find.byKey(const ValueKey('find-course')));
    await t.pumpAndSettle();
    expect(find.text('course-screen'), findsOneWidget);
    final c = box.read(courseProvider);
    expect(c.lastText, '');
    expect(c.chat, isEmpty);
  });

  testWidgets('대상 지역 밖이면 도움처 없이 안내와 연락 버튼만 (FR-E017)', (t) async {
    final repo = _Repo()..guideR = const EmGuideResult(type: EmType.urgent, outOfArea: true, topContact: '119');
    await pumpApp(t, repo);
    await send(t, '친구가 쓰러졌어요');
    await t.pumpAndSettle();
    expect(find.byKey(const ValueKey('out-of-area')), findsOneWidget);
    expect(find.byType(PlaceRow), findsNothing);
    expectThreeButtons();
    expect(buttonOrder(t).first, '119');
  });

  testWidgets('계약 견본: 도움처·병상·전화·길찾기·기준 시각, 한국어 카드 24pt 이상 (FR-E012·E014·E015, NFR-E008·E010)', (t) async {
    final sent = <http.Request>[];
    await pumpApp(t, _contractRepo(sent));
    await send(t, '친구가 쓰러졌어요');
    await t.pumpAndSettle();
    final body = jsonDecode(sent.last.body) as Map;
    expect(body['lat'], 35.1532);
    expect(body.containsKey('area'), false);
    expect(find.text('좋은강안병원 응급실'), findsOneWidget);
    expect(find.text('응급실 · 640 m'), findsOneWidget);
    expect(find.textContaining('가용병상 3'), findsOneWidget);
    expect(find.textContaining('데이터 기준 2026-11-11 04:00 · 출처 국립중앙의료원'), findsOneWidget);
    expect(find.textContaining('근거: 심폐소생술 안내(질병관리청)'), findsOneWidget);
    await t.tap(find.bySemanticsLabel('전화').first);
    await t.tap(find.bySemanticsLabel('길찾기').first);
    expect(links, ['tel:0516109000', 'https://map.kakao.com/link/to/${Uri.encodeComponent('좋은강안병원 응급실')},35.16,129.12']);
    expect(buttonOrder(t).first, '119');

    await t.tap(find.text(tr('full', Lang.ko)));
    await t.pumpAndSettle();
    final ko = t.widget<Text>(find.byKey(const ValueKey('card-ko')));
    expect(ko.style!.fontSize, greaterThanOrEqualTo(24));
    expect(t.widget<Text>(find.byKey(const ValueKey('card-addr'))).style!.fontSize, greaterThanOrEqualTo(24));
    expect(find.text('부산 수영구 광안해변로 219'), findsOneWidget);
    expect(find.byKey(const ValueKey('card-trans')), findsNothing); // 한국어 요청이면 번역 줄 없음
    expect(find.byKey(const ValueKey('call-119')), findsWidgets); // 카드 화면에도 연락 버튼
    await t.tap(find.text(tr('bigger', Lang.ko)));
    await t.pump();
    expect(t.widget<Text>(find.byKey(const ValueKey('card-ko'))).style!.fontSize, greaterThan(ko.style!.fontSize!));
  });

  testWidgets('언어를 바꾸면 화면 문구와 서버 표시값이 그 언어로 바뀐다 (NFR-E006)', (t) async {
    final sent = <http.Request>[];
    await pumpApp(t, _contractRepo(sent));
    await send(t, '친구가 쓰러졌어요');
    await t.pumpAndSettle();
    box.read(langProvider.notifier).set(Lang.en);
    await t.pumpAndSettle();
    expect((jsonDecode(sent.last.body) as Map)['lang'], 'en');
    expect(find.text('Police'), findsOneWidget);
    expect(find.text('Emergency room · 640 m'), findsOneWidget);
    expect(find.textContaining('Free beds: 3'), findsOneWidget);
    expect(find.textContaining('Data as of 2026-11-11 04:00'), findsOneWidget);
    box.read(langProvider.notifier).set(Lang.ja);
    await t.pumpAndSettle();
    expect(find.text('警察'), findsOneWidget);
    await t.tap(find.text(tr('full', Lang.ja)));
    await t.pumpAndSettle();
    expect(find.text('日本語'), findsOneWidget);
    expect(find.byKey(const ValueKey('card-trans')), findsOneWidget);
  });

  testWidgets('SILENT_REPORT 는 sms:112 를 연다', (t) async {
    final sent = <http.Request>[];
    await pumpApp(t, _contractRepo(sent));
    await send(t, '친구가 쓰러졌어요');
    await t.pumpAndSettle();
    await t.tap(find.text(tr('silent', Lang.ko)));
    await t.pumpAndSettle();
    await t.tap(find.text('문자 보내기'));
    expect(links.last, 'sms:112');
  });
}
