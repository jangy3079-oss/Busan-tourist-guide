// [긴급 담당] UI-E01 입력 · E02 결과(1·2단계) · E03 한국어 카드 · E04 유형 시트 · E05 지역 선택 · E06 비긴급 + 확장 시트.
// 이 파일은 긴급 담당만 수정한다.
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/i18n.dart';
import '../../core/theme.dart';
import '../../data/mock_data.dart';
import '../../data/models/models.dart';
import '../../state/providers.dart';
import '../../widgets/alert_sheet.dart';
import '../../widgets/common.dart';
import '../../widgets/map_view.dart';
import '../bridge.dart';
import 'emergency_controller.dart';

const _typeIcons = {'heart': 'heart', 'thermo': 'thermo', 'wallet': 'wallet', 'shield': 'shield', 'flame': 'flame', 'map': 'map', 'chat': 'chat'};

/// 상단 119·112·1330 연락 바. top 연락처를 맨 앞·강조 (키워드로 먼저 정해짐)
class ContactBar extends ConsumerWidget {
  final String? top;
  const ContactBar({super.key, this.top});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final lang = ref.watch(langProvider);
    final order = top == '119' ? ['119', '112', '1330'] : top == '112' ? ['112', '119', '1330'] : ['1330', '119', '112'];
    return Container(
      color: Colors.white, padding: const EdgeInsets.fromLTRB(12, 8, 12, 10),
      child: Row(children: [
        for (final n in order)
          Expanded(
            flex: top == n ? 14 : 10,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 3),
              child: Material(
                color: top == n ? (n == '1330' ? C.blue : C.red) : C.redT, borderRadius: BorderRadius.circular(rMd),
                child: InkWell(
                  borderRadius: BorderRadius.circular(rMd),
                  onTap: () => toast(context, '$n에 전화 (시연에서는 걸리지 않음)'),
                  child: Container(
                    padding: const EdgeInsets.symmetric(vertical: 9),
                    decoration: BoxDecoration(borderRadius: BorderRadius.circular(rMd), border: Border.all(color: top == n ? Colors.transparent : (n == '1330' ? C.blueT2 : C.redT2))),
                    child: Column(children: [
                      Row(mainAxisAlignment: MainAxisAlignment.center, children: [Ic('phone', size: 15, color: top == n ? Colors.white : (n == '1330' ? C.blue : C.red)), const SizedBox(width: 3), Text(n, style: TextStyle(fontSize: 17, fontWeight: FontWeight.w800, color: top == n ? Colors.white : (n == '1330' ? C.blueD : C.redD)))]),
                      Text(tr('c$n', lang), maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 10.5, color: top == n ? Colors.white70 : C.text3)),
                    ]),
                  ),
                ),
              ),
            ),
          ),
      ]),
    );
  }
}

Widget _head(WidgetRef ref, {String backTo = '/emergency'}) {
  final l = ref.watch(langProvider);
  return AppHead(tr('eTitle', l), tr('eSub', l), backTo: backTo);
}

// ---------------- UI-E01 ----------------
class EmergencyInputScreen extends ConsumerStatefulWidget {
  const EmergencyInputScreen({super.key});
  @override
  ConsumerState<EmergencyInputScreen> createState() => _EmInState();
}

class _EmInState extends ConsumerState<EmergencyInputScreen> {
  late final TextEditingController _c = TextEditingController(text: ref.read(emergencyProvider).text);
  @override
  void dispose() { _c.dispose(); super.dispose(); }

  void _submit(String v) {
    final r = ref.read(emergencyProvider.notifier).submit(v);
    switch (r) {
      case EmSubmit.empty: toast(context, '상황을 한 줄만 적어 주세요');
      case EmSubmit.needArea: context.go('/emergency/area');
      case EmSubmit.started: context.go('/emergency/result');
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = ref.watch(langProvider);
    final st = ref.watch(emergencyProvider);
    if (st.needTypeSheet) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        ref.read(emergencyProvider.notifier).ackTypeSheet();
        showTypeSheet(context, ref);
      });
    }
    final chips = trChips(l);
    const chipIcons = ['thermo', 'cross', 'pill', 'wallet', 'shield', 'map'];
    return ScreenFrame(
      head: AppHead(tr('eTitle', l), tr('eSub', l), backTo: '/'),
      bar: const ContactBar(),
      body: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(tr('eAsk', l), style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
        const SizedBox(height: 4),
        Text(tr('eAskSub', l), style: const TextStyle(fontSize: 14, color: C.text3)),
        const SizedBox(height: 14),
        Container(
          decoration: cardDeco(), padding: const EdgeInsets.fromLTRB(14, 10, 14, 8),
          child: Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
            TextField(controller: _c, maxLength: 500, minLines: 3, maxLines: 5, onChanged: (_) => setState(() {}), decoration: InputDecoration(hintText: tr('ePh', l), border: InputBorder.none, counterText: ''), style: const TextStyle(fontSize: 16, height: 1.5)),
            Text('${_c.text.length}/500', style: const TextStyle(fontSize: 11.5, color: C.text4)),
          ]),
        ),
        const SizedBox(height: 12),
        Wrap(spacing: 8, runSpacing: 8, children: [
          for (var i = 0; i < chips.length; i++)
            ActionChip(onPressed: () => _submit(chips[i]), avatar: Ic(chipIcons[i], size: 16, color: C.red), label: Text(chips[i], style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w600)), backgroundColor: Colors.white, side: const BorderSide(color: C.line2)),
        ]),
        const SizedBox(height: 14),
        Container(
          padding: const EdgeInsets.all(12), decoration: cardDeco(),
          child: st.locGranted
              ? Row(children: [const Ic('pin', size: 18, color: C.sea), const SizedBox(width: 8), Expanded(child: Text.rich(TextSpan(children: [TextSpan(text: '${tr('locOn', l)} · '), TextSpan(text: kAreas[st.area]!.nameOf(l), style: const TextStyle(fontWeight: FontWeight.w700))]), style: const TextStyle(fontSize: 13.5)))])
              : Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Row(children: [const Ic('pin', size: 18, color: C.sea), const SizedBox(width: 8), Expanded(child: Text(tr('locAsk', l), style: const TextStyle(fontSize: 13.5)))]),
                  const SizedBox(height: 10),
                  Row(children: [
                    Expanded(child: Btn(tr('allow', l), kind: BtnKind.primary, onTap: () => ref.read(emergencyProvider.notifier).allowLocation())),
                    const SizedBox(width: 8),
                    Expanded(child: Btn(tr('choose', l), onTap: () { ref.read(emergencyProvider.notifier).denyLocation(); context.go('/emergency/area'); })),
                  ]),
                ]),
        ),
        const SizedBox(height: 10),
        PrivNote(tr('store', l)),
        // 프로토타입에서 위치 허용 전 상태를 시연하려면 아래 줄 주석을 풀어 denyLocation으로 시작
      ]),
      dock: Container(
        padding: const EdgeInsets.fromLTRB(12, 10, 12, 12),
        decoration: const BoxDecoration(color: Colors.white, border: Border(top: BorderSide(color: C.line))),
        child: Btn(tr('eFind', l), icon: 'search', kind: BtnKind.red, onTap: () => _submit(_c.text)),
      ),
    );
  }
}

// ---------------- UI-E05 ----------------
class EmergencyAreaScreen extends ConsumerWidget {
  const EmergencyAreaScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = ref.watch(langProvider);
    Widget tile(Area a, String t, String s, List<Color> g) => Padding(
          padding: const EdgeInsets.only(bottom: 10),
          child: Material(
            color: Colors.white, borderRadius: BorderRadius.circular(rLg),
            child: InkWell(
              borderRadius: BorderRadius.circular(rLg),
              onTap: () { ref.read(emergencyProvider.notifier).pickArea(a); context.go('/emergency/result'); },
              child: Container(
                padding: const EdgeInsets.all(12), decoration: cardDeco(),
                child: Row(children: [
                  Container(width: 56, height: 56, decoration: BoxDecoration(borderRadius: BorderRadius.circular(rMd), gradient: LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter, colors: g, stops: const [0, .5, .51, 1]))),
                  const SizedBox(width: 12),
                  Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(t, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700)), Text(s, style: const TextStyle(fontSize: 12.5, color: C.text3))])),
                  const Ic('right', color: C.text4),
                ]),
              ),
            ),
          ),
        );
    return ScreenFrame(
      head: _head(ref), bar: const ContactBar(),
      body: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(tr('areaT', l), style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
        const SizedBox(height: 4),
        Text(tr('areaS', l), style: const TextStyle(fontSize: 14, color: C.text3)),
        const SizedBox(height: 14),
        tile(Area.gwangalli, '광안리 · Gwangalli', '수영구 · 광안리해수욕장 기준', const [Color(0xFFCFE3EE), Color(0xFFA7CFE2), Color(0xFFEADFC7), Color(0xFFD9C79C)]),
        tile(Area.haeundae, '해운대 · Haeundae', '해운대구 · 해운대해수욕장 기준', const [Color(0xFFD7E8F0), Color(0xFFA7CFE2), Color(0xFFEADFC7), Color(0xFFD9C79C)]),
      ]),
    );
  }
}

// ---------------- UI-E02 (로딩 · 1단계 · 2단계) ----------------
class EmergencyResultScreen extends ConsumerWidget {
  const EmergencyResultScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = ref.watch(langProvider);
    final st = ref.watch(emergencyProvider);
    ref.listen<EmState>(emergencyProvider, (prev, next) {
      if (next.needTypeSheet) context.go('/emergency'); // 분류 불가 → 입력 화면에서 유형 선택 시트
      if (next.type == EmType.non && prev?.type != EmType.non) context.go('/emergency/non');
    });
    if (st.phase == EmPhase.idle && !st.needTypeSheet) {
      WidgetsBinding.instance.addPostFrameCallback((_) { if (context.mounted) context.go('/emergency'); });
    }
    if (st.phase == EmPhase.loading || st.type == null || st.type == EmType.non) {
      return ScreenFrame(head: _head(ref), bar: ContactBar(top: st.top), body: Padding(padding: const EdgeInsets.only(top: 28), child: WaitRow(tr('analyzing', l))));
    }
    final ty = st.type!;
    final meta = kTypes[ty]!;
    final places = st.guide?.places ?? const <HelpPlace>[];
    final ai = st.phase == EmPhase.ai;
    final area = kAreas[st.area]!;
    final extra = st.guide?.extra ?? EmExtra.none;

    Widget placeRow(int i, HelpPlace p) => Container(
          margin: const EdgeInsets.only(bottom: 8), padding: const EdgeInsets.all(12), decoration: cardDeco(),
          child: Row(children: [
            Container(width: 28, height: 28, alignment: Alignment.center, decoration: BoxDecoration(color: p.isEr ? const Color(0xFFC2410C) : C.text, shape: BoxShape.circle), child: Text('${i + 1}', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w800, fontSize: 13))),
            const SizedBox(width: 12),
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(p.name, style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w700)),
              const SizedBox(height: 3),
              Wrap(crossAxisAlignment: WrapCrossAlignment.center, spacing: 4, children: [
                Text('${trWord(p.kind, l)} · ${p.distM} m ·', style: const TextStyle(fontSize: 12.5, color: C.text3)),
                p.isEr ? TagC.amber(trWord(p.status, l)) : TagC.green(trWord(p.status, l)),
                if (p.sub.isNotEmpty) Text('· ${trWord(p.sub, l)}', style: const TextStyle(fontSize: 12.5, color: C.text3)),
              ]),
            ])),
            _sq('phone', () => toast(context, '전화 연결 (시연)')), const SizedBox(width: 6), _sq('nav', () => toast(context, '길찾기 (시연)')),
          ]),
        );

    final guide = st.guide;
    return ScreenFrame(
      head: _head(ref), bar: ContactBar(top: st.top),
      body: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Container(width: 44, height: 44, decoration: BoxDecoration(color: C.redT, borderRadius: BorderRadius.circular(rMd)), child: Ic(_typeIcons[meta.icon]!, color: C.red)),
          const SizedBox(width: 12),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(meta.names[l.i], style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800)), Text('${st.locGranted ? tr('locOn', l) : tr('areaSel', l)} · ${area.nameOf(l)}', style: const TextStyle(fontSize: 12.5, color: C.text3))])),
          ai ? TagC.green(tr('ai', l)) : TagC.amber(tr('quick', l)),
        ]),
        if (ty == EmType.fire) const AlertBanner(boldPart: '풍랑주의보', text: ' 발효 중 · 해안 접근 자제', margin: EdgeInsets.only(top: 14)),
        if (extra == EmExtra.silent) ...[
          XBtn(icon: 'mute', title: tr('silent', l), sub: tr('silentSub', l), iconBg: C.redT, iconFg: C.red, borderColor: C.redT2, onTap: () => showSilentSheet(context, ref)),
          XBtn(icon: 'police', title: tr('tpol', l), sub: tr('tpolSub', l), iconBg: C.seaT, iconFg: C.sea, titleExtra: TagC.sea('확장'), onTap: () => toast(context, '관광경찰 안내 (확장 · 연동 예정)')),
        ],
        if (extra == EmExtra.embassy) XBtn(icon: 'bank', title: tr('embassy', l), sub: tr('embassySub', l), iconBg: C.seaT, iconFg: C.sea, titleExtra: TagC.sea('확장'), onTap: () => showEmbassySheet(context, ref)),
        if (places.isNotEmpty) ...[
          SecTitle(tr('helpNow', l), sub: tr('helpSub', l)),
          MapView(pins: [for (final p in places) p.point], area: st.area, height: 170, caption: '${area.name} 기준 · 예시'),
          const SizedBox(height: 8),
          if (guide?.outOfArea ?? false) Padding(padding: const EdgeInsets.symmetric(vertical: 8), child: Text(tr('outOfArea', l), style: const TextStyle(fontSize: 13.5, color: C.text2, height: 1.5))), // FR-E017
          for (var i = 0; i < places.length; i++) placeRow(i, places[i]),
        ],
        const SizedBox(height: 6),
        if (!ai) ...[
          WaitRow(tr('analyzing', l)),
          Container(width: double.infinity, padding: const EdgeInsets.all(14), decoration: cardDeco(), child: const Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Skel(.4), Skel(.95), Skel(.8)])),
        ] else if (guide != null) ...[
          Container(
            padding: const EdgeInsets.all(14), decoration: cardDeco(border: C.line2),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(tr('cardT', l), style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: C.text3)),
              const SizedBox(height: 6),
              Text('${guide.cardKo}\n현재 위치: ${guide.address}', style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700, height: 1.5)),
              const SizedBox(height: 6),
              Text(guide.cardTranslated, style: const TextStyle(fontSize: 13, color: C.text3, height: 1.5)),
              const SizedBox(height: 10),
              Row(children: [Expanded(child: Text(tr('cardHint', l), style: const TextStyle(fontSize: 12.5, color: C.text3))), SizedBox(width: 132, child: Btn(tr('full', l), icon: 'move', kind: BtnKind.dark, onTap: () => showKoreanCard(context)))]),
            ]),
          ),
          const SizedBox(height: 10),
          Container(
            padding: const EdgeInsets.all(14), decoration: cardDeco(),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [const Ic('check', size: 17, color: C.green), const SizedBox(width: 6), Text(tr('todo', l), style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700))]),
              const SizedBox(height: 6),
              for (var i = 0; i < guide.todo.length; i++) Padding(padding: const EdgeInsets.symmetric(vertical: 3), child: Text('${i + 1}. ${guide.todo[i]}', style: const TextStyle(fontSize: 14, height: 1.55, color: C.text2))),
              Src(guide.todoSource),
            ]),
          ),
        ],
        Src(tr('data', l)),
      ]),
    );
  }
}

Widget _sq(String icon, VoidCallback onTap) => Material(
      color: C.subtle, borderRadius: BorderRadius.circular(rMd),
      child: InkWell(borderRadius: BorderRadius.circular(rMd), onTap: onTap, child: SizedBox(width: 38, height: 38, child: Ic(icon, size: 18, color: C.text2))),
    );

// ---------------- UI-E06 ----------------
class EmergencyNonScreen extends ConsumerWidget {
  const EmergencyNonScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = ref.watch(langProvider);
    return ScreenFrame(
      head: _head(ref), bar: const ContactBar(top: '1330'),
      body: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Container(width: 44, height: 44, decoration: BoxDecoration(color: C.blueT, borderRadius: BorderRadius.circular(rMd)), child: const Ic('chat', color: C.blue)),
          const SizedBox(width: 12),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(tr('nonT', l), style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800)), Text(tr('nonS', l), style: const TextStyle(fontSize: 12.5, color: C.text3, height: 1.45))])),
        ]),
        const SizedBox(height: 6),
        XBtn(icon: 'phone', title: '1330 · ${tr('c1330', l)}', sub: '한국어 · English · 日本語 · 中文', iconBg: C.blue, iconFg: Colors.white, onTap: () => toast(context, '1330 연결 (시연)')),
        XBtn(icon: 'megaphone', title: tr('complain', l), sub: tr('complainSub', l), iconBg: C.amberT, iconFg: C.amber, onTap: () => toast(context, '1330 관광불편신고 (시연)')),
        XBtn(icon: 'route', title: tr('findCourse', l), sub: tr('findCourseSub', l), iconBg: C.blueT, iconFg: C.blue, onTap: () => emergencyToCourse(context, ref)),
      ]),
    );
  }
}

// ---------------- 시트 · 카드 ----------------
/// UI-E04 유형 선택 시트
Future<void> showTypeSheet(BuildContext context, WidgetRef ref) {
  final l = ref.read(langProvider);
  Widget item(EmType t) {
    final m = kTypes[t]!;
    return Material(
      color: t == EmType.non ? C.blueT : C.redT, borderRadius: BorderRadius.circular(rMd),
      child: InkWell(
        borderRadius: BorderRadius.circular(rMd),
        onTap: () {
          Navigator.of(context).pop();
          ref.read(emergencyProvider.notifier).chooseType(t);
          context.go(t == EmType.non ? '/emergency/non' : '/emergency/result');
        },
        child: Padding(padding: const EdgeInsets.all(12), child: Row(children: [Ic(_typeIcons[m.icon]!, size: 20, color: t == EmType.non ? C.blue : C.red), const SizedBox(width: 8), Expanded(child: Text(m.names[l.i], style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w700)))])),
      ),
    );
  }
  return showAppSheet(
    context,
    Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text(tr('typeT', l), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
      const SizedBox(height: 6),
      Text(tr('typeS', l), style: const TextStyle(fontSize: 14, color: C.text2, height: 1.5)),
      const SizedBox(height: 14),
      GridView.count(crossAxisCount: 2, shrinkWrap: true, physics: const NeverScrollableScrollPhysics(), mainAxisSpacing: 8, crossAxisSpacing: 8, childAspectRatio: 3.1, children: [for (final t in [EmType.urgent, EmType.mild, EmType.lost, EmType.threat, EmType.fire, EmType.way, EmType.non]) item(t)]),
    ]),
  );
}

/// 확장: 소리 없이 신고 (위협 유형)
Future<void> showSilentSheet(BuildContext context, WidgetRef ref) {
  final l = ref.read(langProvider);
  final a = kAreas[ref.read(emergencyProvider).area]!;
  return showAppSheet(
    context,
    Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text(tr('silent', l), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
      const SizedBox(height: 6),
      Text('${tr('silentSub', l)}. 아래 한국어 문장을 그대로 보내면 돼요.', style: const TextStyle(fontSize: 14, color: C.text2, height: 1.5)),
      const SizedBox(height: 12),
      Container(width: double.infinity, padding: const EdgeInsets.all(14), decoration: cardDeco(color: C.subtle), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text('받는 사람: 112', style: TextStyle(fontSize: 12, color: C.text3)), const SizedBox(height: 4),
        Text('외국인 관광객입니다. 누군가 저를 따라오고 있습니다. 현재 위치: ${a.addr}. 말을 할 수 없어 문자로 신고합니다.', style: const TextStyle(fontSize: 15, height: 1.55)),
      ])),
      const SizedBox(height: 14),
      Row(children: [Expanded(child: Btn('문구 복사', icon: 'copy', onTap: () => toast(context, '문구를 복사했어요'))), const SizedBox(width: 8), Expanded(child: Btn('문자 보내기', icon: 'msg', kind: BtnKind.red, onTap: () => toast(context, '문자 앱 열기 (시연)')))]),
      const Src('말하기 어려운 상황의 112 문자 신고를 참고한 기능 · 접수 시스템과 직접 연동하지 않음'),
    ]),
  );
}

/// 확장: 대사관·영사관 (분실 유형)
Future<void> showEmbassySheet(BuildContext context, WidgetRef ref) {
  final l = ref.read(langProvider);
  return showAppSheet(
    context,
    Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text(tr('embassy', l), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
      const SizedBox(height: 6),
      const Text('여권을 잃어버리면 대사관·영사관에서 재발급이나 여행증명서를 받아야 해요. 국적을 고르면 연락처를 보여드려요.', style: TextStyle(fontSize: 14, color: C.text2, height: 1.5)),
      const SizedBox(height: 14),
      for (final e in const [('🇺🇸', '미국', '주한 미국대사관 · 서울'), ('🇯🇵', '일본', '주부산 일본국 총영사관 · 부산'), ('🇨🇳', '중국', '주부산 중국 총영사관 · 부산')])
        Container(margin: const EdgeInsets.only(bottom: 8), padding: const EdgeInsets.all(12), decoration: cardDeco(), child: Row(children: [
          Text(e.$1, style: const TextStyle(fontSize: 22)), const SizedBox(width: 12),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(e.$2, style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w700)), Text(e.$3, style: const TextStyle(fontSize: 12.5, color: C.text3))])),
          _sq('phone', () => toast(context, '전화 연결 (시연)')),
        ])),
      const Src('확장 기능 · 연락처는 외교부 공개 목록과 연동 예정 (시안에서는 번호 미표시)'),
    ]),
  );
}

/// UI-E03 한국어 카드 전체 화면
Future<void> showKoreanCard(BuildContext context) => showGeneralDialog(
      context: context,
      barrierDismissible: false,
      barrierLabel: 'card',
      pageBuilder: (ctx, _, _) => Consumer(builder: (ctx, ref, _) {
        final l = ref.watch(langProvider);
        final st = ref.watch(emergencyProvider);
        final a = kAreas[st.area]!;
        final g = st.guide;
        final big = st.big;
        return Scaffold(
          backgroundColor: C.text,
          body: SafeArea(
            child: Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 460),
                child: Padding(
                  padding: const EdgeInsets.all(20),
                  child: Column(children: [
                    Row(children: [Expanded(child: Text(tr('showThis', l), style: const TextStyle(color: Colors.white70, fontSize: 13.5, fontWeight: FontWeight.w600))), IconButton(onPressed: () => Navigator.of(ctx).pop(), icon: const Ic('x', color: Colors.white))]),
                    Expanded(child: SingleChildScrollView(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      const SizedBox(height: 10),
                      Text((g?.cardKo ?? '').replaceAll('. ', '.\n'), style: TextStyle(color: Colors.white, fontSize: big ? 38 : 30, fontWeight: FontWeight.w800, height: 1.4)),
                      const SizedBox(height: 20),
                      Text(tr('nowLoc', l), style: const TextStyle(color: Colors.white54, fontSize: 12.5)),
                      Text(g?.address ?? a.addr, style: TextStyle(color: Colors.white, fontSize: big ? 24 : 19, fontWeight: FontWeight.w700)),
                      const SizedBox(height: 20),
                      Text(l == Lang.ja ? '日本語' : 'English', style: const TextStyle(color: Colors.white54, fontSize: 12.5)),
                      Text('${g?.cardTranslated ?? ''} ${a.addrOf(l == Lang.ko ? Lang.en : l)}', style: const TextStyle(color: Colors.white70, fontSize: 15, height: 1.5)),
                    ]))),
                    Row(children: [
                      Expanded(child: Btn(tr('bigger', l), onTap: () => ref.read(emergencyProvider.notifier).toggleBig())), const SizedBox(width: 8),
                      Expanded(child: Btn(tr('copyLoc', l), icon: 'copy', onTap: () => toast(ctx, '주소를 복사했어요'))), const SizedBox(width: 8),
                      Expanded(child: Btn(tr('close', l), kind: BtnKind.dark, onTap: () => Navigator.of(ctx).pop())),
                    ]),
                  ]),
                ),
              ),
            ),
          ),
        );
      }),
    );
