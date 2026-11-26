// [사진 담당] 브라우저에서 새 창으로 연다.
import 'dart:js_interop';

@JS('window.open')
external JSAny? _open(String url, String target);

void platformOpenLink(String url) {
  _open(url, '_blank');
}
