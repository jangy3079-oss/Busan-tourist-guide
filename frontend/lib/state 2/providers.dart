// [공통] 의존성 주입 지점. --dart-define=API_BASE=http://localhost:8080 이면 실제 API, 없으면 Mock.
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../core/i18n.dart';
import '../data/api/api_client.dart';
import '../data/api/course_api.dart';
import '../data/api/emergency_api.dart';
import '../data/api/photo_api.dart';
import '../data/repositories/mock_repositories.dart';
import '../data/repositories/repositories.dart';

final apiClientProvider = Provider<ApiClient?>((ref) => kApiBase.isEmpty ? null : ApiClient(kApiBase));

final courseRepoProvider = Provider<CourseRepository>((ref) {
  final api = ref.watch(apiClientProvider);
  return api == null ? MockCourseRepository() : ApiCourseRepository(api);
});
final photoRepoProvider = Provider<PhotoRepository>((ref) {
  final api = ref.watch(apiClientProvider);
  return api == null ? MockPhotoRepository() : ApiPhotoRepository(api);
});
final emergencyRepoProvider = Provider<EmergencyRepository>((ref) {
  final api = ref.watch(apiClientProvider);
  return api == null ? MockEmergencyRepository() : ApiEmergencyRepository(api);
});

class LangNotifier extends Notifier<Lang> {
  @override
  Lang build() => Lang.ko;
  void set(Lang l) => state = l;
}

final langProvider = NotifierProvider<LangNotifier, Lang>(LangNotifier.new);
