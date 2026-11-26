// [공통] AppHead 가 디버그 빌드에서 assert 없이 그려진다 (color 와 decoration 동시 지정 금지)
import 'package:busan_safetrip/widgets/common.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('AppHead 가 오류 없이 그려진다', (t) async {
    await t.pumpWidget(const ProviderScope(child: MaterialApp(home: Scaffold(body: AppHead('제목', '부제')))));
    expect(t.takeException(), isNull);
    expect(find.text('제목'), findsOneWidget);
  });
}
