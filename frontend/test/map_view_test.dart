// [공통] MapView '내 위치' 인자 (#184). 키가 없는 테스트에서는 그림 지도로 그린다
import 'package:busan_safetrip/data/models/models.dart';
import 'package:busan_safetrip/widgets/map_view.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  Future<void> show(WidgetTester t, MapView v) async {
    await t.pumpWidget(MaterialApp(home: Scaffold(body: SizedBox(width: 340, child: v))));
    await t.pumpAndSettle();
  }

  testWidgets('me 를 주면 그 위치로, 안 주면 예전처럼 그린다', (t) async {
    const pins = [MapPoint(35.1532, 129.1186)];
    for (final v in [
      const MapView(pins: pins, area: Area.gwangalli),
      const MapView(pins: pins, area: Area.gwangalli, me: MapPoint(35.1600, 129.1300)),
      const MapView(pins: pins, area: Area.gwangalli, showMe: false, me: MapPoint(35.1600, 129.1300)),
      const MapView(pins: [], area: Area.haeundae, me: MapPoint(35.1587, 129.1604)),
    ]) {
      await show(t, v);
      expect(t.takeException(), isNull);
      expect(find.byType(CustomPaint), findsWidgets);
    }
  });
}
