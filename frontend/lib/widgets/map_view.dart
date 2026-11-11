// [공통] 지도 위젯 (COMP-C09). 지금은 프로토타입과 같은 그림용 지도(CustomPainter).
// TODO(코스 담당): 카카오맵 JS SDK(HtmlElementView)로 교체. 아래 MapView의 생성자 인터페이스(핀·경로·영역)는 그대로 유지하면
// 코스·사진·긴급 화면은 수정이 필요 없다.
import 'dart:math' as math;
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import '../core/theme.dart';
import '../data/mock_data.dart';
import '../data/models/models.dart';

class MapView extends StatelessWidget {
  final List<MapPoint> pins;
  final List<MapPoint>? path;
  final bool route, showMe;
  final Area area;
  final double height;
  final String caption;
  /// 핀을 누르면 그 핀의 순번(0부터). null이면 지도는 보기 전용 (FR-C014 출발 장소 고르기에서 사용)
  final void Function(int index)? onPinTap;
  const MapView({super.key, required this.pins, this.path, this.route = false, this.showMe = true, required this.area, this.height = 190, this.caption = '공통 지도 위젯 (COMP-C09) · 예시', this.onPinTap});

  int? _hit(Offset local, Size size) {
    final xy = _projector(pins, area, height);
    final p = Offset(local.dx * _MapPainter.W / size.width, local.dy * height / size.height);
    int? best;
    var bestD = 20.0;
    for (var i = 0; i < pins.length; i++) {
      final q = xy(pins[i].lat, pins[i].lng);
      final d = (Offset(q.dx, q.dy - 14) - p).distance; // 핀 머리 중심
      if (d < bestD) { bestD = d; best = i; }
    }
    return best;
  }

  @override
  Widget build(BuildContext context) => ClipRRect(
        borderRadius: BorderRadius.circular(rLg),
        child: AspectRatio(
          aspectRatio: 340 / height,
          child: Stack(children: [
            Positioned.fill(child: LayoutBuilder(builder: (ctx, box) {
              final paint = CustomPaint(painter: _MapPainter(pins, path, route, showMe, area, height));
              if (onPinTap == null) return paint;
              return GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTapUp: (d) { final i = _hit(d.localPosition, box.biggest); if (i != null) onPinTap!(i); },
                child: paint,
              );
            })),
            Positioned(
              left: 8, bottom: 8,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 3),
                decoration: BoxDecoration(color: Colors.white.withValues(alpha: .9), borderRadius: BorderRadius.circular(6)),
                child: Text(caption, style: const TextStyle(fontSize: 10.5, color: C.text3, fontWeight: FontWeight.w600)),
              ),
            ),
          ]),
        ),
      );
}

/// 위경도 → 340×H 그림 좌표. 그림과 핀 누르기 판정이 같은 식을 쓴다.
Offset Function(double, double) _projector(List<MapPoint> pins, Area area, double H) {
  const W = _MapPainter.W;
  final b = kAreas[area]!;
  final all = [MapPoint(b.lat, b.lng), ...pins];
  List<double> pm(double lat, double lng) => [(lng - b.lng) * math.cos(35.15 * math.pi / 180) * 111320, (lat - b.lat) * 110540];
  final ms = [for (final p in all) pm(p.lat, p.lng)];
  double mn(int i) => ms.map((m) => m[i]).reduce(math.min);
  double mx(int i) => ms.map((m) => m[i]).reduce(math.max);
  final cx = (mn(0) + mx(0)) / 2, cy = (mn(1) + mx(1)) / 2;
  final dx = ms.map((m) => (m[0] - cx).abs()).reduce(math.max), dy = ms.map((m) => (m[1] - cy).abs()).reduce(math.max);
  final sx = dx == 0 ? 300.0 : dx, sy = dy == 0 ? 200.0 : dy;
  final k = math.min(math.min((W / 2 - 28) / sx, (H / 2 - 26) / sy), 0.5);
  return (lat, lng) {
    final m = pm(lat, lng);
    return Offset(W / 2 + (m[0] - cx) * k, H / 2 - (m[1] - cy) * k);
  };
}

class _MapPainter extends CustomPainter {
  final List<MapPoint> pins;
  final List<MapPoint>? path;
  final bool route, showMe;
  final Area area;
  final double H;
  _MapPainter(this.pins, this.path, this.route, this.showMe, this.area, this.H);
  static const double W = 340;

  @override
  void paint(Canvas canvas, Size size) {
    canvas.scale(size.width / W, size.height / H);
    final b = kAreas[area]!;
    final xy = _projector(pins, area, H);

    canvas.drawRect(Rect.fromLTWH(0, 0, W, H), Paint()..color = const Color(0xFFEFEDE7));
    final road = Paint()..color = Colors.white..strokeCap = StrokeCap.butt;
    for (var i = -6; i < 12; i++) {
      road.strokeWidth = i % 3 != 0 ? 3 : 6;
      canvas.drawLine(Offset(i * 38.0, -20), Offset(i * 38.0 + 120, H + 20), road);
    }
    for (var j = -2; j < 8; j++) {
      road.strokeWidth = j % 2 != 0 ? 3 : 5;
      canvas.drawLine(Offset(-20, j * 34.0), Offset(W + 20, j * 34.0 - 60), road);
    }
    final coast = area == Area.haeundae
        ? const [[35.1625, 129.140], [35.1600, 129.1470], [35.1560, 129.1510], [35.1545, 129.1540], [35.1575, 129.1560], [35.1583, 129.1600], [35.1590, 129.1650], [35.1600, 129.1700], [35.1570, 129.1760], [35.1560, 129.1900]]
        : const [[35.1400, 129.1060], [35.1445, 129.1110], [35.1478, 129.1142], [35.1510, 129.1170], [35.1532, 129.1194], [35.1550, 129.1226], [35.1562, 129.1262], [35.1560, 129.1290], [35.1545, 129.1320], [35.1535, 129.1345], [35.1560, 129.1380], [35.1620, 129.1420]];
    final cp = [for (final c in coast) xy(c[0], c[1])];
    final sea = Path()..moveTo(cp.first.dx, cp.first.dy);
    for (final p in cp.skip(1)) { sea.lineTo(p.dx, p.dy); }
    sea..lineTo(W + 400, H + 400)..lineTo(-400, H + 400)..close();
    canvas.drawPath(sea, Paint()..color = const Color(0xFFCFE3EE));
    final shore = Path()..moveTo(cp.first.dx, cp.first.dy);
    for (final p in cp.skip(1)) { shore.lineTo(p.dx, p.dy); }
    canvas.drawPath(shore, Paint()..color = const Color(0xFFE8D9B5)..style = PaintingStyle.stroke..strokeWidth = 7..strokeCap = StrokeCap.round..strokeJoin = StrokeJoin.round);

    if (area == Area.gwangalli) {
      final a = xy(35.1440, 129.1190), c2 = xy(35.1600, 129.1430);
      canvas.drawLine(a, c2, Paint()..color = const Color(0xFF8AA4B8)..strokeWidth = 3);
      _text(canvas, '광안대교', Offset((a.dx + c2.dx) / 2 + 6, (a.dy + c2.dy) / 2 + 14), 9.5, const Color(0xFF5B7388), FontWeight.w600);
    }
    final rp = path ?? pins;
    if (route && rp.length > 1) {
      final pth = Path()..moveTo(xy(rp.first.lat, rp.first.lng).dx, xy(rp.first.lat, rp.first.lng).dy);
      for (final p in rp.skip(1)) { final o = xy(p.lat, p.lng); pth.lineTo(o.dx, o.dy); }
      _dashed(canvas, pth, Paint()..color = C.blue..style = PaintingStyle.stroke..strokeWidth = 3..strokeCap = StrokeCap.round, 6, 5);
    }
    if (showMe) {
      final me = xy(b.lat, b.lng);
      canvas.drawCircle(me, 16, Paint()..color = C.blue.withValues(alpha: .14));
      canvas.drawCircle(me, 6.5, Paint()..color = C.blue);
      canvas.drawCircle(me, 6.5, Paint()..color = Colors.white..style = PaintingStyle.stroke..strokeWidth = 2.5);
    }
    for (var i = 0; i < pins.length; i++) {
      final p = pins[i];
      final q = xy(p.lat, p.lng);
      final col = p.isEr ? const Color(0xFFC2410C) : (p.isFixed ? C.sea : C.text);
      canvas.save();
      canvas.translate(q.dx, q.dy);
      final pin = Path()
        ..moveTo(0, 0)
        ..cubicTo(-3, -6, -11, -9, -11, -17)
        ..arcToPoint(const Offset(11, -17), radius: const Radius.circular(11), clockwise: true, largeArc: true)
        ..cubicTo(11, -9, 3, -6, 0, 0)
        ..close();
      canvas.drawPath(pin, Paint()..color = col);
      canvas.drawPath(pin, Paint()..color = Colors.white..style = PaintingStyle.stroke..strokeWidth = 2);
      _text(canvas, p.label ?? '${i + 1}', const Offset(0, -13), 11, Colors.white, FontWeight.w800, center: true);
      canvas.restore();
    }
  }

  void _text(Canvas c, String s, Offset at, double size, Color col, FontWeight w, {bool center = false}) {
    final tp = TextPainter(text: TextSpan(text: s, style: TextStyle(fontSize: size, color: col, fontWeight: w, fontFamily: 'Pretendard')), textDirection: ui.TextDirection.ltr)..layout();
    tp.paint(c, center ? Offset(at.dx - tp.width / 2, at.dy - tp.height / 2) : at);
  }

  void _dashed(Canvas canvas, Path p, Paint paint, double on, double off) {
    for (final m in p.computeMetrics()) {
      var d = 0.0;
      while (d < m.length) {
        canvas.drawPath(m.extractPath(d, math.min(d + on, m.length)), paint);
        d += on + off;
      }
    }
  }

  @override
  bool shouldRepaint(covariant _MapPainter o) => o.pins != pins || o.area != area || o.route != route || o.path != path;
}
