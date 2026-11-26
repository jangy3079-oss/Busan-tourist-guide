// [공통] 홈 — 세 기능의 입구. 홈 화면은 공통 담당(코스 담당이 대표 관리)이 수정한다.
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/i18n.dart';
import '../../core/theme.dart';
import '../../state/providers.dart';
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
    final l = ref.watch(langProvider);
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
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(tr('app', l), style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
            Text(tr('appSub', l), style: const TextStyle(fontSize: 12, color: C.text3)),
          ])),
          const LangSwitch(),
        ]),
      ),
      body: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(2, 22, 2, 16),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(tr('hAsk1', l), style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w800, height: 1.3)),
            Text(tr('hAsk2', l), style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w800, height: 1.3, color: C.blue)),
            const SizedBox(height: 8),
            Text(tr('hAskSub', l), style: const TextStyle(fontSize: 13.5, color: C.text3, height: 1.5)),
          ]),
        ),
        feat('route', tr('hCourse', l), tr('hCourseSub', l), () { ref.read(courseProvider.notifier).reset(); context.go('/course'); }, wide: true),
        const SizedBox(height: 10),
        feat('camera', tr('hPhoto', l), tr('hPhotoSub', l), () => startPhoto(context, ref), bg: C.seaT, fg: C.sea),
        const SizedBox(height: 10),
        feat('alert', tr('hEm', l), tr('hEmSub', l), () => openEmergency(context, ref), bg: C.redT, fg: C.red, border: C.redT2),
        SecTitle(tr('hTry', l)),
        for (final k in const ['hEx1', 'hEx2']) ex('chat', tr(k, l), () => _startCourse(context, ref, tr(k, l))),
        ex('camera', tr('hEx3', l), () => startPhoto(context, ref)),
        const SizedBox(height: 6),
        PrivNote(tr('hPriv', l)),
      ]),
      dock: Dock(
        hint: tr('hHint', l),
        onSend: (v) => _startCourse(context, ref, v),
        onSos: () => openEmergency(context, ref),
        onCamera: () => startPhoto(context, ref),
      ),
    );
  }
}
