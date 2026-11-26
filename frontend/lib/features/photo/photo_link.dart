// [사진 담당] 외부 지도앱 열기 (FR-P018, #64). 화면은 photoLinkProvider 로 열고, 테스트는 이것을 바꿔 끼운다.
// 긴급의 emergency_link 와 같은 방식이지만 기능끼리 서로 import 하지 않으려고 사진 폴더에 따로 둔다.
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'photo_link_stub.dart' if (dart.library.js_interop) 'photo_link_web.dart';

final photoLinkProvider = Provider<void Function(String url)>((ref) => platformOpenLink);

/// 카카오맵 장소 보기(이름·위도·경도). 도로 경로는 그리지 않고 지도앱에 맡긴다. 형식: https://apis.map.kakao.com/web/guide/ '지도 URL'
String kakaoMapUrl(String name, double lat, double lng) =>
    'https://map.kakao.com/link/map/${Uri.encodeComponent(name.replaceAll(',', ' '))},$lat,$lng';
