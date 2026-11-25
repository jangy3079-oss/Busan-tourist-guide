// [공통] 모든 기능이 쓰는 UI 부품. 수정 시 세 화면 모두 영향 → 변경 전에 팀에 공유
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../core/i18n.dart';
import '../core/theme.dart';
import '../state/providers.dart';

const _icons = <String, IconData>{
  'phone': Icons.call_outlined, 'pin': Icons.location_on_outlined, 'nav': Icons.navigation_outlined, 'camera': Icons.photo_camera_outlined,
  'send': Icons.send_rounded, 'alert': Icons.warning_amber_rounded, 'right': Icons.chevron_right, 'left': Icons.chevron_left, 'x': Icons.close, 'edit': Icons.edit_outlined,
  'route': Icons.route, 'pill': Icons.medication_outlined, 'cross': Icons.local_hospital_outlined, 'shield': Icons.shield_outlined,
  'police': Icons.local_police_outlined, 'bank': Icons.account_balance_outlined, 'flame': Icons.local_fire_department_outlined,
  'search': Icons.search, 'copy': Icons.content_copy, 'msg': Icons.chat_bubble_outline, 'mute': Icons.volume_off_outlined,
  'info': Icons.info_outline, 'clock': Icons.schedule, 'waves': Icons.waves, 'store': Icons.storefront_outlined,
  'heart': Icons.monitor_heart_outlined, 'thermo': Icons.thermostat, 'wallet': Icons.account_balance_wallet_outlined,
  'map': Icons.map_outlined, 'chat': Icons.chat_outlined, 'image': Icons.image_outlined, 'check': Icons.check, 'lock': Icons.lock_outline,
  'sun': Icons.wb_sunny_outlined, 'focus': Icons.center_focus_strong_outlined, 'move': Icons.open_in_full, 'refresh': Icons.refresh,
  'spark': Icons.auto_awesome, 'megaphone': Icons.campaign_outlined,
};

class Ic extends StatelessWidget {
  final String name;
  final double size;
  final Color? color;
  const Ic(this.name, {super.key, this.size = 20, this.color});
  @override
  Widget build(BuildContext context) => Icon(_icons[name] ?? Icons.circle_outlined, size: size, color: color);
}

void toast(BuildContext context, String msg) {
  final m = ScaffoldMessenger.of(context);
  m.hideCurrentSnackBar();
  m.showSnackBar(SnackBar(
    content: Text(msg, textAlign: TextAlign.center, style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w600, color: Colors.white)),
    duration: const Duration(milliseconds: 1800),
    behavior: SnackBarBehavior.floating,
    backgroundColor: C.text,
    margin: const EdgeInsets.fromLTRB(40, 0, 40, 96),
    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(99)),
  ));
}

class TagC extends StatelessWidget {
  final String text;
  final Color bg, fg;
  final String? icon;
  const TagC(this.text, {super.key, this.bg = C.subtle, this.fg = C.text3, this.icon});
  factory TagC.green(String t, {String? icon}) => TagC(t, bg: C.greenT, fg: C.green, icon: icon);
  factory TagC.amber(String t) => TagC(t, bg: C.amberT, fg: C.amber);
  factory TagC.sea(String t) => TagC(t, bg: C.seaT, fg: C.sea);
  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
        decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(99)),
        child: Row(mainAxisSize: MainAxisSize.min, children: [
          if (icon != null) ...[Ic(icon!, size: 12, color: fg), const SizedBox(width: 3)],
          Text(text, style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w700, color: fg)),
        ]),
      );
}

enum BtnKind { primary, secondary, red, dark }

class Btn extends StatelessWidget {
  final String label;
  final String? icon;
  final BtnKind kind;
  final VoidCallback? onTap;
  const Btn(this.label, {super.key, this.icon, this.kind = BtnKind.secondary, this.onTap});
  @override
  Widget build(BuildContext context) {
    final (bg, fg, bd) = switch (kind) {
      BtnKind.primary => (C.blue, Colors.white, C.blue),
      BtnKind.red => (C.red, Colors.white, C.red),
      BtnKind.dark => (C.text, Colors.white, C.text),
      BtnKind.secondary => (Colors.white, C.text, C.line2),
    };
    return SizedBox(
      width: double.infinity,
      child: Material(
      color: bg,
      borderRadius: BorderRadius.circular(rMd),
      child: InkWell(
        borderRadius: BorderRadius.circular(rMd),
        onTap: onTap,
        child: Container(
          constraints: const BoxConstraints(minHeight: 48),
          padding: const EdgeInsets.symmetric(horizontal: 14),
          decoration: BoxDecoration(borderRadius: BorderRadius.circular(rMd), border: Border.all(color: bd)),
          child: Row(mainAxisAlignment: MainAxisAlignment.center, mainAxisSize: MainAxisSize.min, children: [
            if (icon != null) ...[Ic(icon!, size: 18, color: fg), const SizedBox(width: 6)],
            Flexible(child: Text(label, textAlign: TextAlign.center, style: TextStyle(fontSize: 15, fontWeight: FontWeight.w700, color: fg))),
          ]),
        ),
      ),
    ));
  }
}

class Skel extends StatelessWidget {
  final double widthFactor;
  const Skel(this.widthFactor, {super.key});
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(top: 8),
        child: FractionallySizedBox(
          widthFactor: widthFactor,
          alignment: Alignment.centerLeft,
          child: Container(height: 12, decoration: BoxDecoration(color: C.line, borderRadius: BorderRadius.circular(6))),
        ),
      );
}

class Spin extends StatelessWidget {
  final Color color;
  const Spin({super.key, this.color = C.red});
  @override
  Widget build(BuildContext context) => SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2.4, color: color));
}

class WaitRow extends StatelessWidget {
  final String text;
  final Color spinColor;
  const WaitRow(this.text, {super.key, this.spinColor = C.red});
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 10),
        child: Row(children: [Spin(color: spinColor), const SizedBox(width: 10), Expanded(child: Text(text, style: const TextStyle(fontSize: 13.5, color: C.text3)))]),
      );
}

class PrivNote extends StatelessWidget {
  final String text;
  final String icon;
  const PrivNote(this.text, {super.key, this.icon = 'lock'});
  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(color: C.subtle, borderRadius: BorderRadius.circular(rMd)),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Padding(padding: const EdgeInsets.only(top: 1), child: Ic(icon, size: 15, color: C.text3)),
          const SizedBox(width: 8),
          Expanded(child: Text(text, style: const TextStyle(fontSize: 12.5, height: 1.5, color: C.text3))),
        ]),
      );
}

class SecTitle extends StatelessWidget {
  final String title;
  final String? sub;
  const SecTitle(this.title, {super.key, this.sub});
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(top: 20, bottom: 8),
        child: Row(crossAxisAlignment: CrossAxisAlignment.baseline, textBaseline: TextBaseline.alphabetic, children: [
          Text(title, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700)),
          if (sub != null) ...[const SizedBox(width: 8), Text(sub!, style: const TextStyle(fontSize: 12.5, color: C.text3))],
        ]),
      );
}

/// 아이콘 + 제목 + 부제 + 화살표 카드 (프로토타입 .xbtn)
class XBtn extends StatelessWidget {
  final String icon, title;
  final String? sub;
  final Color iconBg, iconFg, borderColor;
  final Widget? titleExtra;
  final VoidCallback onTap;
  const XBtn({super.key, required this.icon, required this.title, this.sub, required this.iconBg, required this.iconFg, required this.onTap, this.borderColor = C.line, this.titleExtra});
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(top: 10),
        child: Material(
          color: Colors.white,
          borderRadius: BorderRadius.circular(rLg),
          child: InkWell(
            borderRadius: BorderRadius.circular(rLg),
            onTap: onTap,
            child: Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(borderRadius: BorderRadius.circular(rLg), border: Border.all(color: borderColor)),
              child: Row(children: [
                Container(width: 40, height: 40, decoration: BoxDecoration(color: iconBg, borderRadius: BorderRadius.circular(rMd)), child: Ic(icon, size: 20, color: iconFg)),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Row(children: [Flexible(child: Text(title, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700))), if (titleExtra != null) ...[const SizedBox(width: 6), titleExtra!]]),
                    if (sub != null) Padding(padding: const EdgeInsets.only(top: 2), child: Text(sub!, style: const TextStyle(fontSize: 12.5, color: C.text3, height: 1.4))),
                  ]),
                ),
                const Ic('right', color: C.text4),
              ]),
            ),
          ),
        ),
      );
}

class LangSwitch extends ConsumerWidget {
  const LangSwitch({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final cur = ref.watch(langProvider);
    return Container(
      padding: const EdgeInsets.all(2),
      decoration: BoxDecoration(color: C.subtle, borderRadius: BorderRadius.circular(99), border: Border.all(color: C.line)),
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        for (final l in Lang.values)
          GestureDetector(
            onTap: () => ref.read(langProvider.notifier).set(l),
            child: Container(
              width: 32, height: 28, alignment: Alignment.center,
              decoration: BoxDecoration(color: cur == l ? C.text : Colors.transparent, borderRadius: BorderRadius.circular(99)),
              child: Text(l.label, style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: cur == l ? Colors.white : C.text3)),
            ),
          ),
      ]),
    );
  }
}

/// 상단 헤더. 뒤로가기 목적지는 화면마다 명시 (프로토타입 backH)
class AppHead extends StatelessWidget {
  final String title, sub;
  final String? backTo;
  final VoidCallback? onBack;
  const AppHead(this.title, this.sub, {super.key, this.backTo, this.onBack});
  @override
  Widget build(BuildContext context) => Container(
        color: Colors.white,
        padding: const EdgeInsets.fromLTRB(8, 10, 12, 10),
        decoration: const BoxDecoration(color: Colors.white, border: Border(bottom: BorderSide(color: C.line))),
        child: Row(children: [
          IconButton(onPressed: onBack ?? () => context.go(backTo ?? '/'), icon: const Ic('left', size: 24), tooltip: '뒤로'),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(title, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
            Text(sub, style: const TextStyle(fontSize: 12, color: C.text3)),
          ])),
          const LangSwitch(),
        ]),
      );
}

/// 모든 화면의 공통 골격: 헤더 · (긴급 연락 바) · 스크롤 본문 · 하단 도크
class ScreenFrame extends StatelessWidget {
  final Widget? head, bar, dock;
  final Widget body;
  final EdgeInsets padding;
  final ScrollController? controller;
  const ScreenFrame({super.key, this.head, this.bar, required this.body, this.dock, this.padding = const EdgeInsets.fromLTRB(16, 16, 16, 24), this.controller});
  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: C.bg,
        body: SafeArea(
          child: Column(children: [
            if (head != null) head!,
            if (bar != null) bar!,
            Expanded(child: SingleChildScrollView(controller: controller, padding: padding, child: body)),
            if (dock != null) dock!,
          ]),
        ),
      );
}

/// 하단 도크: 입력창 + 카메라 + 보내기 + SOS. 홈·코스 입력·코스 결과에만 둔다 (사진 화면 제외)
class Dock extends StatefulWidget {
  final String hint;
  final void Function(String) onSend;
  final VoidCallback onSos, onCamera;
  const Dock({super.key, required this.hint, required this.onSend, required this.onSos, required this.onCamera});
  @override
  State<Dock> createState() => _DockState();
}

class _DockState extends State<Dock> {
  final _c = TextEditingController();
  void _send() {
    final v = _c.text.trim();
    if (v.isEmpty) return;
    _c.clear();
    widget.onSend(v);
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.fromLTRB(12, 10, 12, 12),
        decoration: const BoxDecoration(color: Colors.white, border: Border(top: BorderSide(color: C.line))),
        child: Row(children: [
          Expanded(
            child: Container(
              height: 48,
              decoration: BoxDecoration(color: C.subtle, borderRadius: BorderRadius.circular(99), border: Border.all(color: C.line)),
              child: Row(children: [
                IconButton(onPressed: widget.onCamera, icon: const Ic('camera', color: C.text2), tooltip: '사진'),
                Expanded(
                  child: TextField(
                    controller: _c, maxLength: 500, onSubmitted: (_) => _send(),
                    decoration: InputDecoration(hintText: widget.hint, border: InputBorder.none, counterText: '', hintStyle: const TextStyle(color: C.text4, fontSize: 14.5), isDense: true),
                    style: const TextStyle(fontSize: 14.5),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.only(right: 4),
                  child: IconButton.filled(onPressed: _send, style: IconButton.styleFrom(backgroundColor: C.blue, minimumSize: const Size(38, 38)), icon: const Ic('send', size: 18, color: Colors.white), tooltip: '보내기'),
                ),
              ]),
            ),
          ),
          const SizedBox(width: 10),
          Material(
            color: C.red, borderRadius: BorderRadius.circular(99),
            child: InkWell(
              borderRadius: BorderRadius.circular(99), onTap: widget.onSos,
              child: const SizedBox(width: 62, height: 48, child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [Ic('alert', size: 17, color: Colors.white), SizedBox(width: 3), Text('SOS', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w800, fontSize: 13))])),
            ),
          ),
        ]),
      );
}

/// 바텀시트 공통 껍데기 (손잡이 + 내용). 시트 종류: 근거 카드·특보·유형 선택·소리 없이 신고·대사관·긴급 확인
Future<T?> showAppSheet<T>(BuildContext context, Widget child) => showModalBottomSheet<T>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.white,
      constraints: const BoxConstraints(maxWidth: 460),
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(22))),
      builder: (_) => SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(20, 10, 20, 20),
          child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
            Center(child: Container(width: 40, height: 4, margin: const EdgeInsets.only(bottom: 14), decoration: BoxDecoration(color: C.line2, borderRadius: BorderRadius.circular(4)))),
            child,
          ]),
        ),
      ),
    );

class Src extends StatelessWidget {
  final String text;
  const Src(this.text, {super.key});
  @override
  Widget build(BuildContext context) => Padding(padding: const EdgeInsets.only(top: 10), child: Text(text, style: const TextStyle(fontSize: 11.5, color: C.text3, height: 1.5)));
}

class KV extends StatelessWidget {
  final List<(String, Widget)> rows;
  const KV(this.rows, {super.key});
  @override
  Widget build(BuildContext context) => Column(children: [
        for (final r in rows)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 5),
            child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              SizedBox(width: 64, child: Text(r.$1, style: const TextStyle(fontSize: 13, color: C.text3))),
              Expanded(child: DefaultTextStyle(style: const TextStyle(fontSize: 14, color: C.text, height: 1.45, fontFamily: 'Pretendard'), child: r.$2)),
            ]),
          ),
      ]);
}

BoxDecoration cardDeco({Color color = Colors.white, Color border = C.line}) =>
    BoxDecoration(color: color, borderRadius: BorderRadius.circular(rLg), border: Border.all(color: border));
