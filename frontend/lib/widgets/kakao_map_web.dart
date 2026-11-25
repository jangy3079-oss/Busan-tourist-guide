// [공통] 카카오맵 JS SDK 연결(COMP-C09, #24). 실제 그리기는 web/kakao_map.js.
import 'dart:async';
import 'dart:convert';
import 'dart:js_interop';
import 'package:flutter/widgets.dart';
import '../data/models/models.dart';

const _key = String.fromEnvironment('KAKAO_MAP_JS_KEY');

@JS('busanKakaoMap.load')
external JSPromise<JSBoolean> _load(String key);
@JS('busanKakaoMap.render')
external void _render(JSObject el, String options, JSFunction? onPinTap);
@JS('busanKakaoMap.dispose')
external void _dispose(JSObject el);

Future<bool>? _loading;

/// 키가 없거나 SDK 를 못 불러오면(도메인 미등록·오프라인) false. 한 번만 시도한다
Future<bool> loadKakaoMap() => _loading ??= _tryLoad();

Future<bool> _tryLoad() async {
  if (_key.isEmpty) return false;
  try {
    return (await _load(_key).toDart).toDart;
  } catch (_) {
    return false;
  }
}

Widget kakaoMap({required List<MapPoint> pins, required List<MapPoint> path, MapPoint? me, void Function(int index)? onPinTap}) =>
    _KakaoMap(pins, path, me, onPinTap);

class _KakaoMap extends StatefulWidget {
  final List<MapPoint> pins, path;
  final MapPoint? me;
  final void Function(int index)? onPinTap;
  const _KakaoMap(this.pins, this.path, this.me, this.onPinTap);
  @override
  State<_KakaoMap> createState() => _KakaoMapState();
}

class _KakaoMapState extends State<_KakaoMap> {
  JSObject? _el;
  String? _drawn;

  Map<String, Object?> _pt(MapPoint p) => {'lat': p.lat, 'lng': p.lng, 'label': p.label, 'name': p.name, 'er': p.isEr, 'fixed': p.isFixed};

  void _draw() {
    final el = _el;
    if (el == null) return;
    final opt = jsonEncode({
      'pins': [for (final p in widget.pins) _pt(p)],
      'path': [for (final p in widget.path) _pt(p)],
      'me': widget.me == null ? null : _pt(widget.me!),
    });
    if (opt == _drawn) return; // 같은 내용이면 다시 그리지 않아 사용자가 옮긴 지도를 유지한다
    _drawn = opt;
    final tap = widget.onPinTap == null ? null : ((int i) => widget.onPinTap?.call(i)).toJS;
    _render(el, opt, tap);
  }

  @override
  void didUpdateWidget(_KakaoMap old) {
    super.didUpdateWidget(old);
    _draw();
  }

  @override
  void dispose() {
    final el = _el;
    if (el != null) _dispose(el);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => HtmlElementView.fromTagName(
        tagName: 'div',
        onElementCreated: (e) {
          _el = e as JSObject;
          _draw();
        },
      );
}
