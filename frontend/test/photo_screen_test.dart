// [사진 담당] 사진 화면 상태: 위치 허용·거부, 오류 뒤 다시 시도, 설명 실패, 이름 검색, 3개 국어 문구(#59, #62, #65)
import 'dart:async';
import 'package:busan_safetrip/core/i18n.dart';
import 'package:busan_safetrip/core/location.dart';
import 'package:busan_safetrip/data/mock_data.dart';
import 'package:busan_safetrip/data/models/models.dart';
import 'package:busan_safetrip/data/repositories/repositories.dart';
import 'package:busan_safetrip/features/photo/photo_controller.dart';
import 'package:busan_safetrip/features/photo/photo_text.dart';
import 'package:busan_safetrip/state/providers.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

class _Repo implements PhotoRepository {
  Object? matchError, narrateError, searchError;
  MatchResult result = const MatchResult(MatchDecision.confirmed, [PhotoCandidate(kPhotoPlace, 96)], matchId: 'm_1');
  double? lat, lng;
  String? narratedMatchId;
  bool template = false;
  int matched = 0;

  @override
  Future<MatchResult> match(List<int>? imageBytes, Lang lang, {String fileName = 'photo.jpg', double? lat, double? lng, String? sample}) async {
    matched++;
    this.lat = lat;
    this.lng = lng;
    if (matchError != null) throw matchError!;
    return result;
  }

  @override
  Future<PhotoNarration> narrate(String contentId, Lang lang, {String? matchId}) async {
    narratedMatchId = matchId;
    if (narrateError != null) throw narrateError!;
    return PhotoNarration('설명', template: template);
  }

  @override
  Future<MatchResult> search(String q, Lang lang) async {
    if (searchError != null) throw searchError!;
    return q == '없음' ? const MatchResult(MatchDecision.notFound, []) : MatchResult(MatchDecision.candidates, kPhotoCands);
  }
}

void main() {
  late _Repo repo;
  late ProviderContainer box;
  LocationResult loc = const LocationResult.ok(35.15, 129.11);
  PhotoState st() => box.read(photoProvider);
  PhotoController ctrl() => box.read(photoProvider.notifier);

  setUp(() {
    repo = _Repo();
    loc = const LocationResult.ok(35.15, 129.11);
    box = ProviderContainer(overrides: [
      photoRepoProvider.overrideWithValue(repo),
      photoLocationProvider.overrideWithValue(() async => loc),
    ]);
    box.listen(photoProvider, (_, _) {});
  });
  tearDown(() => box.dispose());

  Future<void> settle() => Future<void>.delayed(Duration.zero);

  test('위치를 허용하면 좌표를 함께 보내고, 확정이면 matchId 로 설명을 받는다', () async {
    await ctrl().identify(bytes: [1, 2, 3]);
    await settle();
    expect(repo.lat, 35.15);
    expect(st().loc, PhotoLoc.on);
    expect(st().selected, kPhotoPlace);
    expect(st().narration?.text, '설명');
    expect(st().narration?.template, false);
    expect(repo.narratedMatchId, 'm_1');
    expect(ctrl().me?.lat, 35.15); // 지도의 내 위치 점(#238)
  });

  test('템플릿 설명이면 template 을 그대로 넘긴다(#238)', () async {
    repo.template = true;
    await ctrl().identify(bytes: [1]);
    await settle();
    expect(st().narration?.template, true);
  });

  test('위치를 거부하면 좌표 없이 보낸다(FR-P003)', () async {
    loc = const LocationResult.failed(LocationFail.denied);
    await ctrl().identify(bytes: [1]);
    expect(repo.lat, isNull);
    expect(st().loc, PhotoLoc.off);
    expect(ctrl().me, isNull); // 위치가 없으면 내 위치 점을 그리지 않는다
  });

  test('샘플 시연은 위치를 묻지 않는다', () async {
    await ctrl().identify(sample: 'ok');
    expect(st().loc, PhotoLoc.idle);
  });

  test('식별이 실패하면 오류를 보여 주고 같은 사진으로 다시 시도한다', () async {
    repo.matchError = TimeoutException('늦음');
    await ctrl().identify(bytes: [9]);
    expect(st().phase, PhotoPhase.done);
    expect(st().error, isA<TimeoutException>());
    expect(st().result, isNull);
    repo.matchError = null;
    await ctrl().retry();
    expect(repo.matched, 2);
    expect(st().error, isNull);
    expect(st().result?.decision, MatchDecision.confirmed);
  });

  test('설명이 실패해도 가게 정보는 그대로', () async {
    repo.narrateError = Exception('끊김');
    await ctrl().identify(bytes: [1]);
    await settle();
    expect(st().selected, kPhotoPlace);
    expect(st().narrationFailed, true);
    expect(st().narration, isNull);
  });

  test('이름 검색: 결과 없음과 오류를 구분한다', () async {
    await ctrl().searchByName('없음');
    expect(st().result?.decision, MatchDecision.notFound);
    expect(st().lastQuery, '없음');
    repo.searchError = Exception('x');
    await ctrl().searchByName('갈비');
    expect(st().error, isNotNull);
  });

  test('후보를 고르면 그 가게 설명을 받는다', () async {
    repo.result = MatchResult(MatchDecision.candidates, kPhotoCands, matchId: 'm_2');
    await ctrl().identify(bytes: [1]);
    expect(st().selected, isNull);
    ctrl().select(kPhotoCands[1].place);
    await settle();
    expect(st().selected, kPhotoCands[1].place);
    expect(repo.narratedMatchId, 'm_2');
  });

  test('사진 문구는 모두 3개 국어가 있다', () {
    for (final e in kPhotoText.entries) {
      expect(e.value.length, 3, reason: e.key);
      expect(e.value.every((v) => v.trim().isNotEmpty), true, reason: e.key);
    }
    expect(pt('fromYou', Lang.en, [420]), '420 m from you');
  });

  test('올린 사진은 화면 표시용으로만 들고 있다가, 이름 검색·화면 이탈 때 버린다(#224, NFR-P002)', () async {
    await ctrl().identify(bytes: [1, 2, 3]);
    expect(ctrl().image, [1, 2, 3]);
    await ctrl().searchByName('갈비');
    expect(ctrl().image, isNull);
    await ctrl().identify(bytes: [4]);
    ctrl().reset();
    expect(ctrl().image, isNull);
    await ctrl().identify(sample: 'ok');
    expect(ctrl().image, isNull);
  });

  test('언어를 바꾸면 선택된 가게 설명을 다시 받는다(#225)', () async {
    await ctrl().identify(bytes: [1]);
    await settle();
    box.read(langProvider.notifier).set(Lang.en);
    ctrl().renarrate();
    expect(st().narration, isNull); // 새 언어 설명을 받는 동안 이전 언어 설명은 지운다
    await settle();
    expect(st().narration?.text, '설명');
  });

  test('업종은 요청 언어로, 모르는 업종은 원문 그대로(#225)', () {
    expect(photoCategory('쇼핑', Lang.en), 'Shopping');
    expect(photoCategory('음식점', Lang.ja), '飲食店');
    expect(photoCategory('숙박', Lang.en), '숙박');
  });
}
