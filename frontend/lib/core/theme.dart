// [공통] 디자인 토큰 — 프로토타입(부산 안심여행) CSS 변수와 1:1 대응
import 'package:flutter/material.dart';

class C {
  static const bg = Color(0xFFEEF1F5), surface = Colors.white, subtle = Color(0xFFF5F7FA);
  static const line = Color(0xFFE4E8ED), line2 = Color(0xFFCDD3DA);
  static const text = Color(0xFF16191D), text2 = Color(0xFF434A53), text3 = Color(0xFF69727D), text4 = Color(0xFF8E97A1);
  static const blue = Color(0xFF256EF4), blueD = Color(0xFF0B50D0), blueT = Color(0xFFECF2FE), blueT2 = Color(0xFFD8E5FD);
  static const sea = Color(0xFF0A7C8A), seaT = Color(0xFFE5F4F5);
  static const red = Color(0xFFD7372B), redD = Color(0xFFB1241A), redT = Color(0xFFFDEEEC), redT2 = Color(0xFFF9D3CE);
  static const green = Color(0xFF1E7A3C), greenT = Color(0xFFE8F5EC);
  static const amber = Color(0xFF8A5C00), amberT = Color(0xFFFFF3DB);
}

const double rSm = 8, rMd = 12, rLg = 16, rXl = 20;

ThemeData buildTheme() => ThemeData(
      useMaterial3: true,
      fontFamily: 'Pretendard',
      scaffoldBackgroundColor: C.bg,
      colorScheme: ColorScheme.fromSeed(seedColor: C.blue, surface: C.surface),
      textTheme: const TextTheme().apply(bodyColor: C.text, displayColor: C.text),
      splashFactory: NoSplash.splashFactory,
    );
