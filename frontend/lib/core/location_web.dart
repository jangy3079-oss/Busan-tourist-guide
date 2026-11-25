// [공통] 브라우저 Geolocation API.
import 'dart:async';
import 'dart:js_interop';
import 'location.dart';

@JS('navigator.geolocation')
external _Geo? get _geo;

extension type _Geo(JSObject _) implements JSObject {
  external void getCurrentPosition(JSFunction ok, JSFunction fail, _Options options);
}

extension type _Options._(JSObject _) implements JSObject {
  external factory _Options({bool enableHighAccuracy, int timeout, int maximumAge});
}

extension type _Position(JSObject _) implements JSObject {
  external _Coords get coords;
}

extension type _Coords(JSObject _) implements JSObject {
  external double get latitude;
  external double get longitude;
}

extension type _Error(JSObject _) implements JSObject {
  external int get code; // 1 = 거부, 2 = 알 수 없음, 3 = 시간 초과
}

Future<LocationResult> platformLocation(Duration timeout) {
  final geo = _geo;
  if (geo == null) return Future.value(const LocationResult.failed(LocationFail.unavailable));
  final done = Completer<LocationResult>();
  void end(LocationResult r) {
    if (!done.isCompleted) done.complete(r);
  }

  geo.getCurrentPosition(
    ((_Position p) => end(LocationResult.ok(p.coords.latitude, p.coords.longitude))).toJS,
    ((_Error e) => end(LocationResult.failed(switch (e.code) { 1 => LocationFail.denied, 3 => LocationFail.timeout, _ => LocationFail.unavailable }))).toJS,
    _Options(enableHighAccuracy: true, timeout: timeout.inMilliseconds, maximumAge: 0),
  );
  // 사용자가 권한 창에 답하지 않으면 브라우저의 시간 제한이 시작되지 않는다. 화면이 멈추지 않게 따로 끝낸다
  return done.future.timeout(timeout + const Duration(seconds: 7), onTimeout: () => const LocationResult.failed(LocationFail.timeout));
}
