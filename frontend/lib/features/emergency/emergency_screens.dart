// [긴급 담당] UI-E01 입력 · E02 결과 · E03 한국어 카드 · E04 유형 선택 · E05 지역 선택 · E06 비긴급 + 확장 시트.
// 이 파일은 긴급 담당만 수정한다. 112·119·1330 연락 바는 모든 긴급 화면·오류 상태에 항상 둔다(FR-E004).
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/i18n.dart';
import '../../core/theme.dart';
import '../../data/mock_data.dart';
import '../../data/models/models.dart';
import '../../state/providers.dart';
import '../../widgets/common.dart';
import '../../widgets/error_card.dart';
import '../../widgets/map_view.dart';
import '../bridge.dart';
import 'emergency_controller.dart';
import 'emergency_link.dart';
import 'emergency_text.dart';

const _typeOrder = [EmType.urgent, EmType.mild, EmType.lost, EmType.threat, EmType.fire, EmType.way, EmType.non];

/// 상단 119·112·1330 연락 바. topContact 를 맨 앞·강조 (E01 키워드로 먼저, E02 최종 긴급도로 다시 정해짐)
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
              child: Semantics(
                button: true, label: et('call', lang, [n]),
                child: Material(
                  color: top == n ? (n == '1330' ? C.blue : C.red) : C.redT, borderRadius: BorderRadius.circular(rMd),
                  child: InkWell(
                    key: ValueKey('call-$n'),
                    borderRadius: BorderRadius.circular(rMd),
                    onTap: () => ref.read(emLinkProvider)('tel:$n'),
                    child: Container(
                      constraints: const BoxConstraints(minHeight: 52, minWidth: 48), // NFR-E010 최소 48×48
                      padding: const EdgeInsets.symmetric(vertical: 7),
                      decoration: BoxDecoration(borderRadius: BorderRadius.circular(rMd), border: Border.all(color: top == n ? Colors.transparent : (n == '1330' ? C.blueT2 : C.redT2))),
                      child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
                        Row(mainAxisAlignment: MainAxisAlignment.center, children: [Ic('phone', size: 15, color: top == n ? Colors.white : (n == '1330' ? C.blue : C.red)), const SizedBox(width: 3), Text(n, style: TextStyle(fontSize: 17, fontWeight: FontWeight.w800, color: top == n ? Colors.white : (n == '1330' ? C.blueD : C.redD)))]),
                        Text(tr('c$n', lang), maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 10.5, color: top == n ? Colors.white70 : C.text3)),
                      ]),
                    ),
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
      case EmSubmit.empty: toast(context, et('empty', ref.read(langProvider)));
      case EmSubmit.needArea: context.go('/emergency/area');
      case EmSubmit.started: context.go('/emergency/result');
    }
  }

  Future<void> _allow() async {
    final ok = await ref.read(emergencyProvider.notifier).askLocation();
    if (!ok && mounted) context.go('/emergency/area');
  }

  Widget _locBox(EmState st, Lang l) {
    Widget line(String text, {Widget? trailing}) => Row(children: [const Ic('pin', size: 18, color: C.sea), const SizedBox(width: 8), Expanded(child: Text(text, style: const TextStyle(fontSize: 13.5, height: 1.45))), ?trailing]);
    return switch (st.loc) {
      EmLoc.asking => WaitRow(et('locAsking', l), spinColor: C.sea),
      EmLoc.granted => line('${tr('locOn', l)} · ${kAreas[st.area]!.nameOf(l)}'),
      EmLoc.denied when st.areaPicked => line('${et('locDenied', l)} · ${kAreas[st.area]!.nameOf(l)}',
          trailing: TextButton(onPressed: () => context.go('/emergency/area'), child: Text(et('change', l)))),
      _ => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          line(st.loc == EmLoc.denied ? et('locDenied', l) : et('locIdle', l)),
          const SizedBox(height: 10),
          Row(children: [
            if (st.loc == EmLoc.unknown) ...[Expanded(child: Btn(tr('allow', l), kind: BtnKind.primary, onTap: _allow)), const SizedBox(width: 8)],
            Expanded(child: Btn(tr('choose', l), onTap: () { ref.read(emergencyProvider.notifier).useArea(); context.go('/emergency/area'); })),
          ]),
        ]),
    };
  }

  @override
  Widget build(BuildContext context) {
    final l = ref.watch(langProvider);
    final st = ref.watch(emergencyProvider);
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
            Text(_c.text.characters.length < 500 ? '${_c.text.characters.length}/500' : tr('limit', l), style: TextStyle(fontSize: 11.5, color: _c.text.characters.length < 500 ? C.text4 : C.amber)),
          ]),
        ),
        const SizedBox(height: 12),
        Wrap(spacing: 8, runSpacing: 8, children: [
          for (var i = 0; i < chips.length; i++)
            ActionChip(onPressed: () => _submit(chips[i]), avatar: Ic(chipIcons[i], size: 16, color: C.red), label: Text(chips[i], style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w600)), backgroundColor: Colors.white, side: const BorderSide(color: C.line2)),
        ]),
        const SizedBox(height: 14),
        Container(padding: const EdgeInsets.all(12), decoration: cardDeco(), child: _locBox(st, l)),
        const SizedBox(height: 10),
        PrivNote(tr('store', l)),
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
    Widget tile(Area a, String s, List<Color> g) => Padding(
          padding: const EdgeInsets.only(bottom: 10),
          child: Material(
            color: Colors.white, borderRadius: BorderRadius.circular(rLg),
            child: InkWell(
              key: ValueKey('area-${a.name}'),
              borderRadius: BorderRadius.circular(rLg),
              onTap: () {
                ref.read(emergencyProvider.notifier).pickArea(a);
                context.go(ref.read(emergencyProvider).text.isEmpty ? '/emergency' : '/emergency/result');
              },
              child: Container(
                padding: const EdgeInsets.all(12), decoration: cardDeco(),
                child: Row(children: [
                  Container(width: 56, height: 56, decoration: BoxDecoration(borderRadius: BorderRadius.circular(rMd), gradient: LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter, colors: g, stops: const [0, .5, .51, 1]))),
                  const SizedBox(width: 12),
                  Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(kAreas[a]!.nameOf(l), style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700)), Text(s, style: const TextStyle(fontSize: 12.5, color: C.text3))])),
                  const Ic('right', color: C.text4),
                ]),
              ),
            ),
          ),
        );
    return ScreenFrame(
      head: _head(ref), bar: ContactBar(top: ref.watch(emergencyProvider).top),
      body: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(tr('areaT', l), style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
        const SizedBox(height: 4),
        Text(tr('areaS', l), style: const TextStyle(fontSize: 14, color: C.text3)),
        const SizedBox(height: 14),
        tile(Area.gwangalli, et('gwSub', l), const [Color(0xFFCFE3EE), Color(0xFFA7CFE2), Color(0xFFEADFC7), Color(0xFFD9C79C)]),
        tile(Area.haeundae, et('hdSub', l), const [Color(0xFFD7E8F0), Color(0xFFA7CFE2), Color(0xFFEADFC7), Color(0xFFD9C79C)]),
      ]),
    );
  }
}

// ---------------- UI-E02 (대기 · 결과 · 오류 · 유형 선택) ----------------
class EmergencyResultScreen extends ConsumerWidget {
  const EmergencyResultScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = ref.watch(langProvider);
    final st = ref.watch(emergencyProvider);
    // 화면 전환·시트는 이 화면이 그려지기 전에 상태가 바뀌었어도 놓치지 않게 build 에서 확인한다
    void after(VoidCallback f) => WidgetsBinding.instance.addPostFrameCallback((_) { if (context.mounted) f(); });
    if (st.text.isEmpty) after(() => context.go('/emergency'));
    if (st.needArea) { // 위치 거부·시간 초과 → UI-E05
      after(() {
        if (!ref.read(emergencyProvider).needArea) return; // 같은 프레임에 두 번 그려진 경우
        ref.read(emergencyProvider.notifier).ackNeedArea();
        context.go('/emergency/area');
      });
    }
    if (st.needTypeSheet) { // FR-E008 → UI-E04
      after(() {
        if (!ref.read(emergencyProvider).needTypeSheet) return;
        ref.read(emergencyProvider.notifier).ackTypeSheet();
        showTypeSheet(context, ref);
      });
    }
    if (st.type == EmType.non) after(() => context.go('/emergency/non'));
    final g = st.guide;
    Widget frame(Widget body) => ScreenFrame(head: _head(ref), bar: ContactBar(top: st.top), body: body);

    // 서버 오류·시간 초과: 공용 오류 카드 + 유형 버튼, 연락 바는 그대로 (FR-E004, NFR-E003)
    if (st.error != null) {
      return frame(Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        ErrorCard(st.error!, onRetry: () => ref.read(emergencyProvider.notifier).retry()),
        SecTitle(tr('typeT', l)),
        Text(et('errPick', l), style: const TextStyle(fontSize: 13.5, color: C.text2, height: 1.5)),
        const SizedBox(height: 10),
        TypeGrid(onPick: (t) => _pickType(context, ref, t)),
      ]));
    }
    // 분류 불가(needTypeSelection): 시트를 닫아도 같은 유형 버튼이 본문에 남는다
    if (st.phase == EmPhase.idle && g == null) {
      return frame(Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(tr('typeT', l), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
        const SizedBox(height: 6),
        Text(tr('typeS', l), style: const TextStyle(fontSize: 14, color: C.text2, height: 1.5)),
        const SizedBox(height: 14),
        TypeGrid(onPick: (t) => _pickType(context, ref, t)),
      ]));
    }
    if (g == null || st.type == null || st.type == EmType.non) {
      return frame(Padding(padding: const EdgeInsets.only(top: 28), child: WaitRow(st.loc == EmLoc.asking ? et('locAsking', l) : tr('analyzing', l))));
    }
    final meta = kTypes[st.type!]!;
    final area = kAreas[st.area]!;
    final places = g.places;

    return frame(Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Row(children: [
        Container(width: 44, height: 44, decoration: BoxDecoration(color: C.redT, borderRadius: BorderRadius.circular(rMd)), child: Ic(meta.icon, color: C.red)),
        const SizedBox(width: 12),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(meta.names[l.i], style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800)),
          Text(st.hasCoords ? tr('locOn', l) : '${tr('areaSel', l)} · ${area.nameOf(l)}', style: const TextStyle(fontSize: 12.5, color: C.text3)),
        ])),
      ]),
      if (g.outOfArea) // FR-E017: 도움처 없이 연락 버튼과 안내만
        Padding(padding: const EdgeInsets.only(top: 16), child: Container(
          key: const ValueKey('out-of-area'),
          width: double.infinity, padding: const EdgeInsets.all(14), decoration: cardDeco(color: C.amberT, border: C.amberT),
          child: Text(tr('outOfArea', l), style: const TextStyle(fontSize: 14, color: C.text2, height: 1.5)),
        ))
      else ...[
        if (g.extra == EmExtra.silent)
          XBtn(icon: 'mute', title: tr('silent', l), sub: tr('silentSub', l), iconBg: C.redT, iconFg: C.red, borderColor: C.redT2, onTap: () => showSilentSheet(context, ref)),
        if (g.extra == EmExtra.embassy)
          XBtn(icon: 'bank', title: tr('embassy', l), sub: tr('embassySub', l), iconBg: C.seaT, iconFg: C.sea, onTap: () => showEmbassySheet(context, ref)),
        if (places.isNotEmpty) ...[
          SecTitle(tr('helpNow', l), sub: tr('helpSub', l)),
          MapView(pins: [for (final p in places) p.point], area: st.area, showMe: !st.hasCoords, height: 170,
              caption: st.hasCoords ? et('hereMe', l) : et('hereArea', l, [area.nameOf(l)])),
          const SizedBox(height: 8),
          for (var i = 0; i < places.length; i++) PlaceRow(i, places[i]),
        ] else if (st.type != EmType.fire)
          Padding(padding: const EdgeInsets.only(top: 14), child: Text(et('noPlaces', l), style: const TextStyle(fontSize: 13.5, color: C.text2, height: 1.5))),
        const SizedBox(height: 6),
        if (g.cardKo.isNotEmpty) ...[
          Container(
            padding: const EdgeInsets.all(14), decoration: cardDeco(border: C.line2),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(tr('cardT', l), style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: C.text3)),
              const SizedBox(height: 6),
              // 카드 본문은 상대에게 보여 주는 한국어라 화면 언어와 상관없이 한국어로 둔다(FR-E015)
              Text([g.cardKo, if (g.address.isNotEmpty) '현재 위치: ${g.address}'].join('\n'), style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700, height: 1.5)),
              if (l != Lang.ko && g.cardTranslated.isNotEmpty) ...[
                const SizedBox(height: 6),
                Text(g.cardTranslated, style: const TextStyle(fontSize: 13, color: C.text3, height: 1.5)),
              ],
              const SizedBox(height: 10),
              Row(children: [Expanded(child: Text(tr('cardHint', l), style: const TextStyle(fontSize: 12.5, color: C.text3))), SizedBox(width: 132, child: Btn(tr('full', l), icon: 'move', kind: BtnKind.dark, onTap: () => showKoreanCard(context)))]),
            ]),
          ),
          const SizedBox(height: 10),
        ],
        if (g.todo.isNotEmpty)
          Container(
            padding: const EdgeInsets.all(14), decoration: cardDeco(),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [const Ic('check', size: 17, color: C.green), const SizedBox(width: 6), Text(tr('todo', l), style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700))]),
              const SizedBox(height: 6),
              for (var i = 0; i < g.todo.length; i++) Padding(padding: const EdgeInsets.symmetric(vertical: 3), child: Text('${i + 1}. ${g.todo[i]}', style: const TextStyle(fontSize: 14, height: 1.55, color: C.text2))),
              if (g.todoSource.isNotEmpty) Src(g.todoSource),
            ]),
          ),
        // NFR-E008 기준 시각·출처 (#109)
        if (places.isNotEmpty) Src([if (g.dataAsOf.isNotEmpty) g.dataAsOf, et('callFirst', l)].join(' · ')),
      ],
    ]));
  }
}

void _pickType(BuildContext context, WidgetRef ref, EmType t) {
  ref.read(emergencyProvider.notifier).chooseType(t);
  context.go(t == EmType.non ? '/emergency/non' : '/emergency/result');
}

/// 도움처 한 줄 (FR-E011·E012·E014): 이름·종류·거리·운영 상태와 오늘 운영시간·응급실 병상/경고·전화·길찾기
class PlaceRow extends ConsumerWidget {
  final int index;
  final HelpPlace p;
  const PlaceRow(this.index, this.p, {super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = ref.watch(langProvider);
    final open = ref.read(emLinkProvider);
    return Container(
      margin: const EdgeInsets.only(bottom: 8), padding: const EdgeInsets.all(12),
      decoration: cardDeco(border: p.noTreatment ? C.redT2 : C.line),
      child: Row(children: [
        Container(width: 28, height: 28, alignment: Alignment.center, decoration: BoxDecoration(color: p.isEr ? const Color(0xFFC2410C) : C.text, shape: BoxShape.circle), child: Text('${index + 1}', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w800, fontSize: 13))),
        const SizedBox(width: 12),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(p.name, style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w700)),
          const SizedBox(height: 3),
          Text('${trWord(p.kind, l)} · ${p.distM} m', style: const TextStyle(fontSize: 12.5, color: C.text3)),
          const SizedBox(height: 4),
          Wrap(crossAxisAlignment: WrapCrossAlignment.center, spacing: 4, runSpacing: 4, children: [
            if (p.status.isNotEmpty) p.openNow ? TagC.green(trWord(p.status, l)) : TagC.amber(trWord(p.status, l)),
            if (p.noTreatment) TagC(et('noTreat', l), key: const ValueKey('no-treatment'), bg: C.redT, fg: C.red, icon: 'alert'),
            if (p.sub.isNotEmpty) Text(trWord(p.sub, l), style: TextStyle(fontSize: 12.5, color: p.noTreatment ? C.red : C.text3)),
          ]),
        ])),
        if (p.phone != null && p.phone!.isNotEmpty) ...[_sq('phone', et('phone', l), () => open(telUrl(p.phone!))), const SizedBox(width: 6)],
        _sq('nav', et('nav', l), () => open(kakaoRouteUrl(p.name, p.lat, p.lng))),
      ]),
    );
  }
}

Widget _sq(String icon, String label, VoidCallback onTap) => Semantics(
      button: true, label: label,
      child: Material(
        color: C.subtle, borderRadius: BorderRadius.circular(rMd),
        child: InkWell(borderRadius: BorderRadius.circular(rMd), onTap: onTap, child: SizedBox(width: 48, height: 48, child: Tooltip(message: label, child: Ic(icon, size: 20, color: C.text2)))),
      ),
    );

// ---------------- UI-E06 ----------------
class EmergencyNonScreen extends ConsumerWidget {
  const EmergencyNonScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = ref.watch(langProvider);
    final call = ref.read(emLinkProvider);
    return ScreenFrame(
      head: _head(ref), bar: const ContactBar(top: '1330'),
      body: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Container(width: 44, height: 44, decoration: BoxDecoration(color: C.blueT, borderRadius: BorderRadius.circular(rMd)), child: const Ic('chat', color: C.blue)),
          const SizedBox(width: 12),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(tr('nonT', l), style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800)), Text(tr('nonS', l), style: const TextStyle(fontSize: 12.5, color: C.text3, height: 1.45))])),
        ]),
        const SizedBox(height: 6),
        XBtn(icon: 'phone', title: '1330 · ${tr('c1330', l)}', sub: '한국어 · English · 日本語 · 中文', iconBg: C.blue, iconFg: Colors.white, onTap: () => call('tel:1330')),
        XBtn(icon: 'megaphone', title: tr('complain', l), sub: tr('complainSub', l), iconBg: C.amberT, iconFg: C.amber, onTap: () => call('tel:1330')),
        // FR-E018, AC-X03: 코스 요청 화면을 빈 입력창으로 연다(입력 문장을 넘기지 않음)
        XBtn(key: const ValueKey('find-course'), icon: 'route', title: tr('findCourse', l), sub: tr('findCourseSub', l), iconBg: C.blueT, iconFg: C.blue, onTap: () => emergencyToCourse(context, ref)),
      ]),
    );
  }
}

// ---------------- 시트 · 카드 ----------------
/// UI-E04 유형 7개 버튼 (시트와 오류 화면이 같이 쓴다)
class TypeGrid extends ConsumerWidget {
  final void Function(EmType) onPick;
  const TypeGrid({super.key, required this.onPick});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = ref.watch(langProvider);
    Widget item(EmType t) {
      final m = kTypes[t]!;
      return Material(
        color: t == EmType.non ? C.blueT : C.redT, borderRadius: BorderRadius.circular(rMd),
        child: InkWell(
          key: ValueKey('type-${t.name}'),
          borderRadius: BorderRadius.circular(rMd),
          onTap: () => onPick(t),
          child: Padding(padding: const EdgeInsets.all(12), child: Row(children: [Ic(m.icon, size: 20, color: t == EmType.non ? C.blue : C.red), const SizedBox(width: 8), Expanded(child: Text(m.names[l.i], style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w700)))])),
        ),
      );
    }
    return GridView.count(crossAxisCount: 2, shrinkWrap: true, physics: const NeverScrollableScrollPhysics(), mainAxisSpacing: 8, crossAxisSpacing: 8, childAspectRatio: 3.1, children: [for (final t in _typeOrder) item(t)]);
  }
}

/// UI-E04 유형 선택 시트
Future<void> showTypeSheet(BuildContext context, WidgetRef ref) {
  final l = ref.read(langProvider);
  return showAppSheet(
    context,
    Column(key: const ValueKey('type-sheet'), crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text(tr('typeT', l), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
      const SizedBox(height: 6),
      Text(tr('typeS', l), style: const TextStyle(fontSize: 14, color: C.text2, height: 1.5)),
      const SizedBox(height: 14),
      TypeGrid(onPick: (t) {
        Navigator.of(context).pop();
        _pickType(context, ref, t);
      }),
    ]),
  );
}

/// 확장: 소리 없이 신고 (SILENT_REPORT) — 112 문자 신고 sms:112
Future<void> showSilentSheet(BuildContext context, WidgetRef ref) {
  final l = ref.read(langProvider);
  final st = ref.read(emergencyProvider);
  final addr = st.guide?.address.isNotEmpty == true ? st.guide!.address : kAreas[st.area]!.addr;
  // 112가 읽을 한국어 문장이라 화면 언어와 상관없이 한국어
  final msg = '외국인 관광객입니다. 위협을 받고 있습니다. 현재 위치: $addr. 말을 할 수 없어 문자로 신고합니다.';
  return showAppSheet(
    context,
    Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text(tr('silent', l), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
      const SizedBox(height: 6),
      Text('${tr('silentSub', l)}. ${et('silentHow', l)}', style: const TextStyle(fontSize: 14, color: C.text2, height: 1.5)),
      const SizedBox(height: 12),
      Container(width: double.infinity, padding: const EdgeInsets.all(14), decoration: cardDeco(color: C.subtle), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(et('silentTo', l), style: const TextStyle(fontSize: 12, color: C.text3)), const SizedBox(height: 4),
        Text(msg, style: const TextStyle(fontSize: 15, height: 1.55)),
      ])),
      const SizedBox(height: 14),
      Row(children: [
        Expanded(child: Btn(et('copyMsg', l), icon: 'copy', onTap: () { Clipboard.setData(ClipboardData(text: msg)); toast(context, et('msgCopied', l)); })),
        const SizedBox(width: 8),
        Expanded(child: Btn(et('sendSms', l), icon: 'msg', kind: BtnKind.red, onTap: () => ref.read(emLinkProvider)('sms:112'))),
      ]),
      Src(et('silentSrc', l)),
    ]),
  );
}

/// 확장: 대사관·영사관 (EMBASSY) — 외교부 주한 외국공관 연락처 목록 링크
Future<void> showEmbassySheet(BuildContext context, WidgetRef ref) {
  final l = ref.read(langProvider);
  return showAppSheet(
    context,
    Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text(tr('embassy', l), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
      const SizedBox(height: 6),
      Text(et('embassyHow', l), style: const TextStyle(fontSize: 14, color: C.text2, height: 1.5)),
      const SizedBox(height: 14),
      Btn(et('embassyOpen', l), icon: 'bank', kind: BtnKind.primary, onTap: () => ref.read(emLinkProvider)(kEmbassyListUrl)),
      Src(et('embassySrc', l)),
    ]),
  );
}

/// UI-E03 한국어 카드 전체 화면 (FR-E015, NFR-E010: 한국어 24pt 이상, 크게 키우기)
Future<void> showKoreanCard(BuildContext context) => showGeneralDialog(
      context: context,
      barrierDismissible: false,
      barrierLabel: 'card',
      pageBuilder: (ctx, _, _) => Consumer(builder: (ctx, ref, _) {
        final l = ref.watch(langProvider);
        final st = ref.watch(emergencyProvider);
        final g = st.guide;
        final big = st.big;
        final addr = g?.address ?? '';
        return Scaffold(
          backgroundColor: C.text,
          body: SafeArea(
            child: Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 460),
                child: Padding(
                  padding: const EdgeInsets.all(20),
                  child: Column(children: [
                    Row(children: [Expanded(child: Text(tr('showThis', l), style: const TextStyle(color: Colors.white70, fontSize: 13.5, fontWeight: FontWeight.w600))), IconButton(onPressed: () => Navigator.of(ctx).pop(), tooltip: tr('close', l), icon: const Ic('x', color: Colors.white))]),
                    Expanded(child: SingleChildScrollView(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      const SizedBox(height: 10),
                      Text((g?.cardKo ?? '').replaceAll('. ', '.\n'), key: const ValueKey('card-ko'), style: TextStyle(color: Colors.white, fontSize: big ? 38 : 30, fontWeight: FontWeight.w800, height: 1.4)),
                      if (addr.isNotEmpty) ...[
                        const SizedBox(height: 20),
                        Text(tr('nowLoc', Lang.ko), style: const TextStyle(color: Colors.white54, fontSize: 12.5)),
                        Text(addr, key: const ValueKey('card-addr'), style: TextStyle(color: Colors.white, fontSize: big ? 30 : 24, fontWeight: FontWeight.w700)),
                      ],
                      if (l != Lang.ko && (g?.cardTranslated ?? '').isNotEmpty) ...[
                        const SizedBox(height: 20),
                        Text(et('transLabel', l), style: const TextStyle(color: Colors.white54, fontSize: 12.5)),
                        Text(g!.cardTranslated, key: const ValueKey('card-trans'), style: TextStyle(color: Colors.white70, fontSize: big ? 20 : 16, height: 1.5)),
                      ],
                    ]))),
                    Row(children: [
                      Expanded(child: Btn(tr('bigger', l), onTap: () => ref.read(emergencyProvider.notifier).toggleBig())), const SizedBox(width: 8),
                      Expanded(child: Btn(tr('copyLoc', l), icon: 'copy', onTap: () { Clipboard.setData(ClipboardData(text: addr)); toast(ctx, et('copied', l)); })), const SizedBox(width: 8),
                      Expanded(child: Btn(tr('close', l), kind: BtnKind.dark, onTap: () => Navigator.of(ctx).pop())),
                    ]),
                    const SizedBox(height: 10),
                    ClipRRect(borderRadius: BorderRadius.circular(rMd), child: ContactBar(top: st.top)), // FR-E004
                  ]),
                ),
              ),
            ),
          ),
        );
      }),
    );
