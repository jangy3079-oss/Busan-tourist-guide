// [공통] 화면 경로. 새 화면을 추가할 때는 해당 기능 담당이 자기 구역(아래 주석)에만 추가한다.
import 'package:go_router/go_router.dart';
import 'features/course/course_screens.dart';
import 'features/emergency/emergency_screens.dart';
import 'features/home/home_screen.dart';
import 'features/photo/photo_screens.dart';

final router = GoRouter(
  routes: [
    GoRoute(path: '/', builder: (_, _) => const HomeScreen()),
    // ---- 코스 담당 구역
    GoRoute(path: '/course', builder: (_, _) => const CourseRequestScreen()),
    GoRoute(path: '/course/result', builder: (_, _) => const CourseResultScreen()),
    // ---- 사진 담당 구역
    GoRoute(path: '/photo', builder: (_, _) => const PhotoPickScreen()),
    GoRoute(path: '/photo/scan', builder: (_, _) => const PhotoScanScreen()),
    GoRoute(path: '/photo/result', builder: (_, _) => const PhotoResultScreen()),
    GoRoute(path: '/photo/candidates', builder: (_, _) => const PhotoCandidatesScreen()),
    GoRoute(path: '/photo/fail', builder: (_, _) => const PhotoFailScreen()),
    // ---- 긴급 담당 구역
    GoRoute(path: '/emergency', builder: (_, _) => const EmergencyInputScreen()),
    GoRoute(path: '/emergency/area', builder: (_, _) => const EmergencyAreaScreen()),
    GoRoute(path: '/emergency/result', builder: (_, _) => const EmergencyResultScreen()),
    GoRoute(path: '/emergency/non', builder: (_, _) => const EmergencyNonScreen()),
  ],
);
