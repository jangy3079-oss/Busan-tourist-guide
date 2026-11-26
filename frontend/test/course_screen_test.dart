// [코스 담당] 코스 화면 상태: 오류가 나도 멈추지 않고, 설명이 실패해도 코스는 남는다(#95)
import 'dart:async';
import 'package:busan_safetrip/core/i18n.dart';
import 'package:busan_safetrip/data/models/models.dart';
import 'package:busan_safetrip/data/repositories/repositories.dart';
import 'package:busan_safetrip/features/course/course_controller.dart';
import 'package:busan_safetrip/features/course/course_text.dart';
import 'package:busan_safetrip/state/providers.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

const _course = Course('c_1', 1, [], [CourseStop('1', '14:00 – 15:00', '해변', '', 35, 129, category: '관광지', moveMode: 'WALK', moveMin: 7)], [], Area.gwangalli,
    assumptions: [CourseAssumption('durationMin', '240', '4시간', isDefault: true)]);

class _Repo implements CourseRepository {
  Object? createError, editError, narrateError;
  int narrated = 0;
  final created = <(String, String?)>[];
  Lang? lang;
  EmType? emergency;
  @override
  Future<CourseResult> create(String query, Lang lang, {String? startPlaceId, CourseOverrides? overrides, bool notEmergency = false}) async {
    created.add((query, startPlaceId));
    if (createError != null) throw createError!;
    if (emergency != null && !notEmergency) return CourseResult(emergency: emergency);
    return const CourseResult(course: _course);
  }

  @override
  Future<CourseResult> edit(String courseId, Lang lang, {String? text, CourseOverrides? overrides, String? startPlaceId, bool notEmergency = false}) async {
    if (editError != null) throw editError!;
    if (emergency != null && !notEmergency) return CourseResult(emergency: emergency);
    return const CourseResult(course: _course);
  }

  @override
  Future<CourseNarration> narrate(String courseId, Lang lang) async {
    narrated++;
    this.lang = lang;
    if (narrateError != null) throw narrateError!;
    return const CourseNarration('제목', items: {'1': '설명'});
  }

  @override
  Future<List<PlacePin>> startCandidates(Area area) async => const [];
  @override
  Future<Evidence> evidence(String contentId, Lang lang) async => const Evidence('', '', '', '');
}

void main() {
  late _Repo repo;
  late ProviderContainer box;
  CourseState st() => box.read(courseProvider);
  CourseController ctrl() => box.read(courseProvider.notifier);
  setUp(() {
    repo = _Repo();
    box = ProviderContainer(overrides: [courseRepoProvider.overrideWithValue(repo)]);
    box.listen(courseProvider, (_, _) {});
  });
  tearDown(() => box.dispose());

  test('사진에서 넘어오면 출발 장소만 채우고 기다렸다가, 입력하면 그 가게에서 출발하도록 요청한다(#226)', () async {
    ctrl().fromPlace(const PlacePin('2789475', '수영팔도시장', '쇼핑', 35.17, 129.11), '수영팔도시장에서 출발해요');
    expect(repo.created, isEmpty); // 바로 보내지 않는다
    expect(st().startPlace?.contentId, '2789475');
    expect(st().chat.single.me, false);
    await ctrl().start('저녁 먹고 바다 보기');
    expect(repo.created.single, ('저녁 먹고 바다 보기', '2789475'));
    expect(st().course, isNotNull);
  });

  test('요청이 실패하면 로딩을 끝내고 다시 시도할 수 있다', () async {
    repo.createError = TimeoutException('늦음');
    await ctrl().start('광안리 3시간');
    expect(st().loading, false);
    expect(st().error, isA<TimeoutException>());
    expect(st().chat.any((m) => m.typing), false);
    expect(st().chat.first.text, '광안리 3시간'); // 보낸 문장은 남는다
    repo.createError = null;
    ctrl().retry();
    await Future<void>.delayed(Duration.zero);
    expect(st().error, null);
    expect(st().course, isNotNull);
  });

  test('긴급이 아니라고 하면 같은 문장으로 코스를 이어서 만든다', () async {
    repo.emergency = EmType.way;
    await ctrl().start("I don't speak Korean, 3 hours");
    expect(st().emergencyHint, EmType.way);
    expect(st().course, null);
    ctrl().continueCourse();
    await Future<void>.delayed(Duration.zero);
    expect(st().emergencyHint, null);
    expect(st().course, isNotNull);
    await ctrl().edit('길 잃기 쉬운 곳 빼줘');
    expect(st().emergencyHint, EmType.way);
    ctrl().continueCourse();
    await Future<void>.delayed(Duration.zero);
    expect(st().emergencyHint, null);
    expect(st().editing, false);
  });

  test('수정이 실패해도 지금 코스를 유지한다', () async {
    await ctrl().start('광안리');
    repo.editError = Exception('끊김');
    await ctrl().edit('2번 빼줘');
    expect(st().course, _course);
    expect(st().editing, false);
    expect(st().editError, isNotNull);
    await ctrl().changeAssumption('durationMin', '180');
    expect(st().replanning, false);
    expect(st().course, _course);
  });

  test('설명이 실패해도 코스는 남는다', () async {
    repo.narrateError = TimeoutException('늦음');
    await ctrl().start('광안리');
    await Future<void>.delayed(Duration.zero);
    expect(st().course, isNotNull);
    expect(st().narrationFailed, true);
  });

  test('언어를 바꾸면 설명을 새 언어로 다시 받는다', () async {
    await ctrl().start('광안리');
    await Future<void>.delayed(Duration.zero);
    expect(st().narration!.items['1'], '설명');
    box.read(langProvider.notifier).set(Lang.ja);
    await Future<void>.delayed(Duration.zero);
    expect(repo.narrated, 2);
    expect(repo.lang, Lang.ja);
  });

  test('문구는 세 언어가 모두 있고 값에서 만든다', () {
    for (final e in kCourseText.entries) {
      expect(e.value.length, 3, reason: e.key);
      for (final s in e.value) {
        expect(s.trim(), isNotEmpty, reason: e.key);
        expect(s.contains('{0}'), e.value.first.contains('{0}'), reason: e.key);
      }
    }
    final now = DateTime(2026, 11, 26);
    expect(assumptionText(const CourseAssumption('visitDate', '2026-11-28', ''), Lang.ko, now), '11/28(토)');
    expect(assumptionText(const CourseAssumption('visitDate', '2026-11-27', ''), Lang.en, now), 'Tomorrow');
    expect(assumptionText(const CourseAssumption('durationMin', '90', ''), Lang.ja, now), '90分');
    expect(assumptionText(const CourseAssumption('area', 'null', ''), Lang.en, now), 'Gwangalli & Haeundae');
    expect(stopText(_course.stops.first, Lang.en), 'Sightseeing · 7 min walk');
    expect(stopText(const CourseStop('1', '', '', '목업 문구', 0, 0), Lang.ko), '목업 문구');
  });
}
