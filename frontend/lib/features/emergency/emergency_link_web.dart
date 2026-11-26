// [긴급 담당] 브라우저에서 tel:·sms: 는 같은 창으로(전화·문자 앱이 열림), 웹 링크는 새 창으로 연다.
import 'dart:js_interop';

@JS('window.open')
external JSAny? _open(String url, String target);

void platformOpenLink(String url) {
  final app = url.startsWith('tel:') || url.startsWith('sms:');
  _open(url, app ? '_self' : '_blank');
}
