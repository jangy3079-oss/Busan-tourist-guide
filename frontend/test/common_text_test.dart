// [공통] 문구 맵과 오류 문구(#26, #27)
import 'dart:async';
import 'package:busan_safetrip/core/i18n.dart';
import 'package:busan_safetrip/core/location.dart';
import 'package:busan_safetrip/data/api/api_client.dart';
import 'package:busan_safetrip/widgets/error_card.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('공통 문구는 세 언어가 모두 있고 {0} 자리가 맞는다', () {
    for (final e in L.entries) {
      expect(e.value.length, 3, reason: e.key);
      for (final s in e.value) {
        expect(s.trim(), isNotEmpty, reason: e.key);
        expect(s.contains('{0}'), e.value.first.contains('{0}'), reason: e.key);
      }
    }
    expect(tr('bFrom', Lang.en, ['Cafe']), 'Start from Cafe');
    expect(tr('없는키', Lang.ko), '없는키');
  });

  test('예외를 사용자 문구로 바꾼다. 서버 메시지는 쓰지 않는다', () {
    expect(errorKey(TimeoutException('x')), 'errTimeout');
    expect(errorKey(const ApiException(500, 'INTERNAL', '내부 오류')), 'errServer');
    expect(errorKey(const ApiException(504, 'AI_TIMEOUT', '')), 'errTimeout');
    expect(errorKey(const ApiException(400, 'INVALID_INPUT', '')), 'errInput');
    expect(errorKey(Exception('연결 실패')), 'errNetwork');
    for (final k in ['errTimeout', 'errServer', 'errInput', 'errNetwork']) {
      expect(L.containsKey(k), true);
    }
  });

  test('위치를 쓸 수 없는 환경이면 실패로 알린다', () async {
    final r = await requestLocation();
    expect(r.ok, false);
    expect(r.fail, LocationFail.unavailable);
  });
}
