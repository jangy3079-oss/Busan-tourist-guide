// [긴급 담당] 전화(tel:)·문자(sms:)·길찾기·외부 목록 링크 (FR-E004, FR-E014). 화면은 emLinkProvider 로 열고, 테스트는 이것을 바꿔 끼운다.
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'emergency_link_stub.dart' if (dart.library.js_interop) 'emergency_link_web.dart';

final emLinkProvider = Provider<void Function(String url)>((ref) => platformOpenLink);

String telUrl(String phone) => 'tel:${phone.replaceAll(RegExp(r'[^0-9+]'), '')}';

/// 카카오맵 웹 길찾기(목적지 = 이름·좌표). 좌표는 서버 응답 그대로 쓰고 저장하지 않는다
String kakaoRouteUrl(String name, double lat, double lng) => 'https://map.kakao.com/link/to/${Uri.encodeComponent(name.replaceAll(',', ' '))},$lat,$lng';

/// 외교부 주한 외국공관 목록 (EMBASSY 확장)
const kEmbassyListUrl = 'https://www.mofa.go.kr/www/brd/m_4072/list.do';
