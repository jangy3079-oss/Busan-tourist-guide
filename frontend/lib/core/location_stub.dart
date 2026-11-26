// [공통] 웹이 아닌 환경(테스트). 위치를 쓸 수 없다.
import 'location.dart';

Future<LocationResult> platformLocation(Duration timeout) async => const LocationResult.failed(LocationFail.unavailable);
