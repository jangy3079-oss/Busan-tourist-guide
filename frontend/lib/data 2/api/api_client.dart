// [공통] HTTP 클라이언트. 경로·오류 형식은 docs/API.md. 세 담당의 *_api.dart 가 이것만 쓴다.
import 'dart:convert';
import 'package:http/http.dart' as http;
import '../../core/i18n.dart';
import '../models/models.dart';

/// --dart-define=API_BASE=http://localhost:8080 로 지정. 비어 있으면 Mock 사용.
const String kApiBase = String.fromEnvironment('API_BASE');

/// 서버 오류 응답 {code, message}
class ApiException implements Exception {
  final int status;
  final String code, message;
  const ApiException(this.status, this.code, this.message);
  @override
  String toString() => 'ApiException($status $code: $message)';
}

class ApiClient {
  final String base;
  final http.Client _http;
  final Duration timeout;
  ApiClient(this.base, {http.Client? client, this.timeout = const Duration(seconds: 12)}) : _http = client ?? http.Client();

  Uri _u(String path, [Map<String, String>? q]) => Uri.parse('$base/api/v1$path').replace(queryParameters: q);

  Future<Map<String, dynamic>> get(String path, [Map<String, String>? q]) async =>
      _decode(await _http.get(_u(path, q)).timeout(timeout));

  Future<Map<String, dynamic>> post(String path, Map<String, dynamic> body) async => _decode(await _http
      .post(_u(path), headers: const {'Content-Type': 'application/json'}, body: jsonEncode(body..removeWhere((_, v) => v == null)))
      .timeout(timeout));

  Future<Map<String, dynamic>> multipart(String path, {required String field, required List<int> bytes, required String fileName, Map<String, String> fields = const {}}) async {
    final req = http.MultipartRequest('POST', _u(path))
      ..fields.addAll(fields)
      ..files.add(http.MultipartFile.fromBytes(field, bytes, filename: fileName));
    final res = await http.Response.fromStream(await _http.send(req).timeout(timeout));
    return _decode(res);
  }

  Map<String, dynamic> _decode(http.Response r) {
    final body = r.bodyBytes.isEmpty ? <String, dynamic>{} : jsonDecode(utf8.decode(r.bodyBytes));
    if (r.statusCode >= 200 && r.statusCode < 300) return body as Map<String, dynamic>;
    final m = body is Map<String, dynamic> ? body : const <String, dynamic>{};
    throw ApiException(r.statusCode, (m['code'] ?? 'INTERNAL') as String, (m['message'] ?? '') as String);
  }
}

// ---- 공통 변환 (서버 열거값은 대문자 스네이크) ----
String langCode(Lang l) => l.name;
String areaCode(Area a) => a == Area.gwangalli ? 'GWANGALLI' : 'HAEUNDAE';
Area areaFrom(String? s) => s == 'HAEUNDAE' ? Area.haeundae : Area.gwangalli;
String emTypeCode(EmType t) => t.name.toUpperCase();
EmType? emTypeFrom(String? s) => s == null ? null : EmType.values.firstWhere((t) => t.name.toUpperCase() == s, orElse: () => EmType.non);
List<String> strList(dynamic v) => v == null ? const [] : List<String>.from(v as List);
double dbl(dynamic v) => (v as num).toDouble();
