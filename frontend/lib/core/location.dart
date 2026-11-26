// [공통] 현재 위치 한 번 받기(#25, FR-P003, FR-E002). 저장하지 않고 요청 처리에만 쓴다.
import 'location_stub.dart' if (dart.library.js_interop) 'location_web.dart';

enum LocationFail { denied, timeout, unavailable }

class LocationResult {
  final double? lat, lng;
  final LocationFail? fail; // null 이면 성공
  const LocationResult.ok(double this.lat, double this.lng) : fail = null;
  const LocationResult.failed(LocationFail this.fail) : lat = null, lng = null;
  bool get ok => fail == null;
}

/// 부를 때마다 한 번 묻는다. 거부·시간 초과·미지원이면 fail 이 채워지므로, 부른 화면이 지역 직접 선택으로 넘긴다
Future<LocationResult> requestLocation({Duration timeout = const Duration(seconds: 8)}) => platformLocation(timeout);
