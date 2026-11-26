// [사진 담당] 확정 화면의 두 버튼: '여기서 출발하는 코스'는 그 가게를 출발 장소로 채우고(FR-P015, TC-P011, #63, #226),
// '카카오맵에서 열기'는 이름·좌표로 카카오맵 링크를 연다(FR-P018, TC-P014, #64). 샘플 사진은 Mock 모드에서만 보인다(#199)
import 'package:busan_safetrip/core/i18n.dart';
import 'package:busan_safetrip/data/api/api_client.dart';
import 'package:busan_safetrip/data/mock_data.dart';
import 'package:busan_safetrip/data/models/models.dart';
import 'package:busan_safetrip/data/repositories/repositories.dart';
import 'package:busan_safetrip/features/course/course_controller.dart';
import 'package:busan_safetrip/features/photo/photo_controller.dart';
import 'package:busan_safetrip/features/photo/photo_link.dart';
import 'package:busan_safetrip/features/photo/photo_screens.dart';
import 'package:busan_safetrip/state/providers.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

class _PhotoRepo implements PhotoRepository {
  @override
  Future<MatchResult> match(List<int>? imageBytes, Lang lang, {String fileName = 'photo.jpg', double? lat, double? lng, String? sample}) async =>
      const MatchResult(MatchDecision.confirmed, [PhotoCandidate(kPhotoPlace, 96)], matchId: 'm_1');
  @override
  Future<String> narrate(String contentId, Lang lang, {String? matchId}) async => '설명';
  @override
  Future<MatchResult> search(String q, Lang lang) async => const MatchResult(MatchDecision.notFound, []);
}

class _CourseRepo implements CourseRepository {
  String? startPlaceId;
  @override
  Future<CourseResult> create(String query, Lang lang, {String? startPlaceId, CourseOverrides? overrides, bool notEmergency = false}) async {
    this.startPlaceId = startPlaceId;
    return const CourseResult();
  }
  @override
  Future<CourseResult> edit(String courseId, Lang lang, {String? text, CourseOverrides? overrides, String? startPlaceId, bool notEmergency = false}) async => const CourseResult();
  @override
  Future<CourseNarration> narrate(String courseId, Lang lang) async => const CourseNarration('', items: {});
  @override
  Future<List<PlacePin>> startCandidates(Area area) async => const [];
  @override
  Future<Evidence> evidence(String contentId, Lang lang) async => const Evidence('', '', '', '');
}

void main() {
  test('카카오맵 링크는 이름·위도·경도 순서이고, 이름의 쉼표는 지운다', () {
    expect(kakaoMapUrl('해운대암소갈비집', 35.163, 129.1636),
        'https://map.kakao.com/link/map/${Uri.encodeComponent('해운대암소갈비집')},35.163,129.1636');
    expect(kakaoMapUrl('가,나', 1, 2), contains('${Uri.encodeComponent('가 나')},1.0,2.0'));
  });

  late _CourseRepo course;
  late List<String> opened;
  late ProviderContainer box;

  Future<void> pumpResult(WidgetTester tester) async {
    course = _CourseRepo();
    opened = [];
    box = ProviderContainer(overrides: [
      photoRepoProvider.overrideWithValue(_PhotoRepo()),
      courseRepoProvider.overrideWithValue(course),
      photoLinkProvider.overrideWithValue(opened.add),
    ]);
    addTearDown(box.dispose);
    await box.read(photoProvider.notifier).identify(sample: 'ok');
    final router = GoRouter(initialLocation: '/photo/result', routes: [
      GoRoute(path: '/photo/result', builder: (_, _) => const PhotoResultScreen()),
      GoRoute(path: '/course', builder: (_, _) => const Text('COURSE')),
    ]);
    await tester.pumpWidget(UncontrolledProviderScope(container: box, child: MaterialApp.router(routerConfig: router)));
    await tester.pumpAndSettle();
  }

  testWidgets('카카오맵에서 열기 → 확정 가게의 이름·좌표 링크', (tester) async {
    await pumpResult(tester);
    final btn = find.text('카카오맵에서 열기');
    await tester.ensureVisible(btn);
    await tester.tap(btn);
    expect(opened, [kakaoMapUrl(kPhotoPlace.name, kPhotoPlace.lat, kPhotoPlace.lng)]);
  });

  testWidgets('여기서 출발하는 코스 → 그 가게를 출발 장소로 채우고, 바로 보내지 않은 채 코스 화면으로(#226)', (tester) async {
    await pumpResult(tester);
    final btn = find.text('여기서 출발하는 코스');
    await tester.ensureVisible(btn);
    await tester.tap(btn);
    await tester.pumpAndSettle();
    expect(find.text('COURSE'), findsOneWidget);
    expect(course.startPlaceId, isNull); // 사용자가 입력하기 전에는 코스를 요청하지 않음
    expect(box.read(courseProvider).startPlace?.contentId, kPhotoPlace.contentId);
  });

  Future<void> pumpPick(WidgetTester tester, {required bool api}) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [apiClientProvider.overrideWithValue(api ? ApiClient('http://localhost:8080') : null)],
      child: const MaterialApp(home: PhotoPickScreen()),
    ));
    await tester.pumpAndSettle();
  }

  testWidgets('샘플 사진은 Mock 모드에서만 보이고 실제 서버 모드에서는 숨긴다', (tester) async {
    await pumpPick(tester, api: false);
    expect(find.text('해운대암소갈비집'), findsOneWidget);
    await pumpPick(tester, api: true);
    expect(find.text('해운대암소갈비집'), findsNothing);
  });
}
