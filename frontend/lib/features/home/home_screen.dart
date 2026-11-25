// [공통] 홈 — 세 기능의 입구. 홈 화면은 공통 담당(코스 담당이 대표 관리)이 수정한다.
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/theme.dart';
import '../../widgets/alert_sheet.dart';
import '../../widgets/common.dart';
import '../bridge.dart';
import '../course/course_controller.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  void _startCourse(BuildContext context, WidgetRef ref, String text) {
    ref.read(courseProvider.notifier).start(text);
    context.go('/course');
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    Widget feat(String icon, String title, String sub, VoidCallback onTap, {Color bg = C.blueT, Color fg = C.blue, Color border = C.line, bool wide = false}) => Material(
          color: Colors.white, borderRadius: BorderRadius.circular(rLg),
          child: InkWell(
            borderRadius: BorderRadius.circular(rLg), onTap: onTap,
            child: Container(
              padding: const EdgeInsets.all(14), decoration: cardDeco(border: border),
              child: Row(children: [
                Container(width: 44, height: 44, decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(rMd)), child: Ic(icon, color: fg)),
                const SizedBox(width: 12),
                Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(title, style: const TextStyle(fontSize: 15.5, fontWeight: FontWeight.w700)),
                  const SizedBox(height: 2),
                  Text(sub, style: const TextStyle(fontSize: 12.5, color: C.text3, height: 1.4)),
                ])),
                if (wide) const Ic('right', color: C.text4),
              ]),
            ),
          ),
        );
    Widget ex(String icon, String text, VoidCallback onTap) => Padding(
          padding: const EdgeInsets.only(bottom: 8),
          child: Material(
            color: Colors.white, borderRadius: BorderRadius.circular(rMd),
            child: InkWell(
              borderRadius: BorderRadius.circular(rMd), onTap: onTap,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13), decoration: cardDeco(),
                child: Row(children: [Ic(icon, size: 18, color: C.text3), const SizedBox(width: 10), Expanded(child: Text(text, style: const TextStyle(fontSize: 14))), const Ic('right', size: 18, color: C.text4)]),
              ),
            ),
          ),
        );

    return ScreenFrame(
      head: Container(
        padding: const EdgeInsets.fromLTRB(16, 12, 12, 12),
        decoration: const BoxDecoration(color: Colors.white, border: Border(bottom: BorderSide(color: C.line))),
        child: Row(children: [
          Container(width: 38, height: 38, decoration: BoxDecoration(color: C.blue, borderRadius: BorderRadius.circular(rMd)), child: const Ic('waves', color: Colors.white)),
          const SizedBox(width: 10),
          const Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text('부산 안심여행', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
            Text('광안리·해운대 관광 안내', style: TextStyle(fontSize: 12, color: C.text3)),
          ])),
          const LangSwitch(),
        ]),
      ),
      body: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const AlertBanner(boldPart: '풍랑주의보', text: ' · 광안리·해운대 해수욕장 입수 통제 중'),
        const Padding(
          padding: EdgeInsets.fromLTRB(2, 22, 2, 16),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text('광안리·해운대에서', style: TextStyle(fontSize: 24, fontWeight: FontWeight.w800, height: 1.3)),
            Text('무엇을 도와드릴까요?', style: TextStyle(fontSize: 24, fontWeight: FontWeight.w800, height: 1.3, color: C.blue)),
            SizedBox(height: 8),
            Text('공식 관광 데이터를 근거로 한국어·English·日本語로 안내해요.', style: TextStyle(fontSize: 13.5, color: C.text3, height: 1.5)),
          ]),
        ),
        feat('route', '코스 만들기', '날짜·시간·취향을 말하면 지금 운영 중인 곳으로 3~5곳 코스를 짜요', () { ref.read(courseProvider.notifier).reset(); context.go('/course'); }, wide: true),
        const SizedBox(height: 10),
        feat('camera', '간판 사진으로 찾기', '한글 간판 한 장으로 가게 정보 확인', () => startPhoto(context, ref), bg: C.seaT, fg: C.sea),
        const SizedBox(height: 10),
        feat('alert', '긴급 도움', '약국·응급실·경찰, 한국어 카드', () => openEmergency(context, ref), bg: C.redT, fg: C.red, border: C.redT2),
        const SecTitle('이렇게 물어보세요'),
        ex('chat', '오후에 광안리에서 바다 보고 회 먹기, 3시간', () => _startCourse(context, ref, '오후에 광안리에서 바다 보고 회 먹기, 3시간')),
        ex('chat', '비 오는 날 해운대 실내 코스', () => _startCourse(context, ref, '비 오는 날 해운대 실내 코스')),
        ex('camera', '이 간판 가게가 어디예요?', () => startPhoto(context, ref)),
        const SizedBox(height: 6),
        const PrivNote('일반 대화와 사진은 저장하지 않아요. 긴급 안내가 시작된 경우에만 상황 유형·대략 위치·시각이 관광안전 통계에 저장돼요.'),
      ]),
      dock: Dock(
        hint: '무엇이든 물어보세요',
        onSend: (v) => _startCourse(context, ref, v),
        onSos: () => openEmergency(context, ref),
        onCamera: () => startPhoto(context, ref),
      ),
    );
  }
}
