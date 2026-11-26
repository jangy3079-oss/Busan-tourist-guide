// [코스 담당] UI-C01 코스 요청 · UI-C02 코스 결과 · UI-C03 근거 카드. 이 파일은 코스 담당만 수정한다.
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/i18n.dart';
import '../../core/theme.dart';
import '../../data/models/models.dart';
import '../../state/providers.dart';
import '../../widgets/common.dart';
import '../../widgets/error_card.dart';
import '../../widgets/map_view.dart';
import '../bridge.dart';
import 'course_controller.dart';
import 'course_text.dart';

/// 코스 입력창 → (코스 / 긴급 감지) 공통 반응. 코스 요청·결과 화면이 같이 쓴다.
void _listenHints(BuildContext context, WidgetRef ref) {
  ref.listen<CourseState>(courseProvider, (prev, next) {
    if (next.emergencyHint != null && prev?.emergencyHint != next.emergencyHint) {
      handleCourseEmergency(context, ref, next.emergencyHint!);
    }
  });
  // 화면이 만들어지기 전에 이미 감지된 경우(홈에서 바로 입력)도 처리
  final pending = ref.read(courseProvider).emergencyHint;
  if (pending != null) {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (context.mounted && ref.read(courseProvider).emergencyHint != null) handleCourseEmergency(context, ref, pending);
    });
  }
}

class CourseRequestScreen extends ConsumerWidget {
  const CourseRequestScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final st = ref.watch(courseProvider);
    final l = ref.watch(langProvider);
    ref.listen<CourseState>(courseProvider, (prev, next) {
      if (!next.loading && next.course != null && prev?.course != next.course) context.go('/course/result');
    });
    _listenHints(context, ref);
    final ctrl = ref.read(courseProvider.notifier);
    final asked = st.chat.any((m) => m.me); // 사용자가 보낸 문장이 있으면 예시 칩을 숨긴다
    Widget bubble(ChatMsg m) => Padding(
          padding: const EdgeInsets.only(top: 12),
          child: Row(mainAxisAlignment: m.me ? MainAxisAlignment.end : MainAxisAlignment.start, crossAxisAlignment: CrossAxisAlignment.start, children: [
            if (!m.me) ...[Container(width: 28, height: 28, decoration: const BoxDecoration(color: C.blueT, shape: BoxShape.circle), child: const Ic('spark', size: 15, color: C.blue)), const SizedBox(width: 8)],
            Flexible(child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              decoration: BoxDecoration(color: m.me ? C.blue : Colors.white, borderRadius: BorderRadius.circular(16), border: m.me ? null : Border.all(color: C.line)),
              child: Row(mainAxisSize: MainAxisSize.min, children: [
                if (m.typing) ...[const Spin(color: C.blue), const SizedBox(width: 8)],
                Flexible(child: Text(m.text, style: TextStyle(fontSize: 14.5, height: 1.55, color: m.me ? Colors.white : C.text))),
              ]),
            )),
          ]),
        );
    return ScreenFrame(
      head: AppHead(ct('title', l), ct('sub', l), backTo: '/'),
      body: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        bubble(ChatMsg(ct('hello', l))),
        for (final m in st.chat) bubble(m),
        if (!asked)
          Padding(
            padding: const EdgeInsets.only(left: 36, top: 14),
            // 출발 장소를 받아 왔으면 첫 칩은 "이대로 코스 만들기"(○○에서 출발, #226)
            child: Wrap(spacing: 8, runSpacing: 8, children: [for (final x in [if (st.startPlace != null && st.chat.isNotEmpty) tr('bFrom', l, [st.startPlace!.name]), ct('ex1', l), ct('ex2', l), ct('ex3', l)]) Material(
                color: Colors.white,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16), side: const BorderSide(color: C.line2)),
                clipBehavior: Clip.antiAlias,
                // 칩은 한 줄로 잘려 360px에서 영·일 예시가 끊긴다(#28). 폭이 모자라면 줄을 바꾼다
                child: InkWell(onTap: () => ctrl.start(x), child: Padding(padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8), child: Text(x, style: const TextStyle(fontSize: 13)))),
              )]),
          ),
        if (st.error != null) Padding(padding: const EdgeInsets.only(top: 12), child: ErrorCard(st.error!, onRetry: ctrl.retry)),
        const SizedBox(height: 16),
        _StartPlaceRow(
          place: st.startPlace,
          onPick: () async { final p = await showStartPlaceSheet(context, ref, current: st.startPlace); if (p != null) ctrl.setStartPlace(p); },
          onClear: st.startPlace == null ? null : () => ctrl.setStartPlace(null),
        ),
        const SizedBox(height: 10),
        PrivNote(ct('scope', l), icon: 'info'),
      ]),
      dock: Dock(hint: ct('hint', l), onSend: ctrl.start, onSos: () => openEmergency(context, ref), onCamera: () => startPhoto(context, ref), autofocus: st.startPlace != null && !asked),
    );
  }
}

class CourseResultScreen extends ConsumerWidget {
  const CourseResultScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final st = ref.watch(courseProvider);
    final l = ref.watch(langProvider);
    final now = DateTime.now();
    final c = st.course;
    ref.listen<CourseState>(courseProvider, (prev, next) {
      if (next.course != null && next.course!.version > 1 && (prev?.course?.version ?? 0) < next.course!.version) toast(context, ct('made', ref.read(langProvider), [next.course!.version]));
      if (next.editError != null && prev?.editError != next.editError) { toast(context, tr(errorKey(next.editError!), ref.read(langProvider))); ref.read(courseProvider.notifier).clearHint(); }
      if (next.editNotice != null && prev?.editNotice != next.editNotice) { toast(context, next.editNotice!); ref.read(courseProvider.notifier).clearHint(); }
    });
    _listenHints(context, ref);
    if (c == null) {
      // 직접 URL로 들어온 경우 등: 요청 화면으로
      WidgetsBinding.instance.addPostFrameCallback((_) { if (context.mounted) context.go('/course'); });
      return const ScreenFrame(body: SizedBox());
    }
    final ctrl = ref.read(courseProvider.notifier);
    final busy = st.replanning || st.editing;
    // 부제: 권역 · 소요 시간 · 버전. 칩 값에서 만들므로 실제 코스와 다를 수 없다
    final subtitle = [for (final k in const ['area', 'durationMin']) for (final a in c.assumptions) if (a.key == k) assumptionText(a, l, now), 'v${c.version}'].join(' · ');
    return ScreenFrame(
      head: AppHead(ct('result', l), subtitle, backTo: '/course'),
      body: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        // 가정 칩 (FR-C003): 누르면 그 조건만 바꿔 다시 짠다. 기본값으로 채운 칩은 점선 테두리
        Wrap(spacing: 6, runSpacing: 6, children: [
          for (var i = 0; i < (c.assumptions.isEmpty ? c.chips.length : c.assumptions.length); i++)
            Builder(builder: (_) {
              final a = c.assumptions.isEmpty ? null : c.assumptions[i];
              final label = a == null ? c.chips[i] : assumptionText(a, l, now);
              final changed = c.changedChipIndex == i;
              return ActionChip(
                onPressed: a == null || a.key == 'indoor' || busy ? null : () => showAssumptionSheet(context, ref, a), // 실내 칩은 보기 전용(API-C03 에 바꾸는 값이 없음)
                avatar: a == null || a.key == 'indoor' ? null : Ic('edit', size: 13, color: changed ? C.blueD : C.text3),
                label: Text(changed ? '$label · ${ct('changed', l)}' : (a?.isDefault ?? false) ? '$label · ${ct('default', l)}' : label, style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600, color: changed ? C.blueD : C.text2)),
                backgroundColor: changed ? C.blueT2 : Colors.white, side: BorderSide(color: changed ? C.blue : C.line2),
              );
            }),
        ]),
        const SizedBox(height: 8),
        _StartPlaceRow(
          place: st.startPlace ?? _fixedPin(c),
          onPick: busy ? null : () async { final p = await showStartPlaceSheet(context, ref, current: st.startPlace ?? _fixedPin(c), area: c.area); if (p != null) ctrl.changeStartPlace(p); },
        ),
        if (busy) Padding(padding: const EdgeInsets.only(top: 10), child: WaitRow(ct(st.editing ? 'editing' : 'replan', l), spinColor: C.blue)),
        if (c.relaxed.isNotEmpty) Padding(padding: const EdgeInsets.only(top: 8), child: Text(ct('relaxed', l, [c.relaxed.join(', ')]), style: const TextStyle(fontSize: 12.5, color: C.amber))), // FR-C007
        const SizedBox(height: 12),
        MapView(pins: [for (final s in c.stops) s.point], path: c.path.isEmpty ? null : c.path, route: true, showMe: false, area: c.area, height: 200),
        const SizedBox(height: 12),
        for (var i = 0; i < c.stops.length; i++) _StopRow(i + 1, c.stops[i], st.narration?.items[c.stops[i].id]),
        if (c.version > 1 && ctrl.canRevert)
          Container(
            margin: const EdgeInsets.only(top: 6), padding: const EdgeInsets.all(10), decoration: cardDeco(color: C.blueT, border: C.blueT2),
            child: Row(children: [
              const Ic('refresh', size: 14, color: C.blueD), const SizedBox(width: 6),
              Text('v${c.version - 1}', style: const TextStyle(fontSize: 12.5, color: C.text3, decoration: TextDecoration.lineThrough)),
              const SizedBox(width: 6), Text('→ v${c.version}${c.changedChipIndex != null && c.changedChipIndex! < c.assumptions.length ? ' · ${assumptionText(c.assumptions[c.changedChipIndex!], l, now)}' : ''}', style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700, color: C.blueD)),
              const Spacer(),
              GestureDetector(onTap: ctrl.revert, child: TagC(ct('revert', l, [c.version - 1]))),
            ]),
          ),
        const SizedBox(height: 12),
        Container(
          width: double.infinity, padding: const EdgeInsets.all(14), decoration: cardDeco(),
          child: st.narration != null
              ? Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  if (st.narration!.template) Padding(padding: const EdgeInsets.only(bottom: 4), child: Text(ct('simple', l), style: const TextStyle(fontSize: 12, color: C.amber, fontWeight: FontWeight.w600))), // AI-REQ-C02
                  Text(st.narration!.title, style: const TextStyle(fontSize: 15, height: 1.5, fontWeight: FontWeight.w700)),
                  if (!st.narration!.template) Src(ct('generated', l)),
                ])
              : st.narrationFailed
                  ? Text(ct('noDesc', l), style: const TextStyle(fontSize: 13.5, color: C.text3, height: 1.5))
                  : Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text(ct('writing', l), style: const TextStyle(fontSize: 12.5, color: C.text3)), const Skel(.92), const Skel(.7),
                    ]),
        ),
      ]),
      dock: Dock(hint: ct('hintEdit', l), onSend: ctrl.edit, onSos: () => openEmergency(context, ref), onCamera: () => startPhoto(context, ref)),
    );
  }
}

class _StopRow extends ConsumerWidget {
  final int n;
  final CourseStop s;
  final String? about; // 장소별 설명(FR-C010). 늦게 온다
  const _StopRow(this.n, this.s, this.about);
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = ref.watch(langProvider);
    return Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Container(width: 28, height: 28, alignment: Alignment.center, decoration: BoxDecoration(color: s.fixed ? C.sea : C.text, shape: BoxShape.circle), child: Text('$n', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w800, fontSize: 13))),
          const SizedBox(width: 12),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text([s.time, if (s.changed) ct('changed', l), if (s.fixed) ct('fixed', l)].join(' · '), style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: s.changed ? C.blue : C.text3)),
            GestureDetector(onTap: () => showEvidenceSheet(context, ref, s.id, s.name), child: Text(s.nameIn(l), style: const TextStyle(fontSize: 15.5, fontWeight: FontWeight.w700, decoration: TextDecoration.underline, decorationColor: C.line2))),
            Text(stopText(s, l), style: const TextStyle(fontSize: 13, color: C.text3, height: 1.45)),
            if (about != null) Padding(padding: const EdgeInsets.only(top: 2), child: Text(about!, style: const TextStyle(fontSize: 13.5, color: C.text2, height: 1.5))),
            if (s.hoursUncertain) Padding(padding: const EdgeInsets.only(top: 4), child: TagC.amber(ct('uncertain', l))), // FR-C009
          ])),
        ]),
      );
  }
}

/// UI-C03 근거 카드 (사진 결과 화면에서도 같은 시트를 쓸 수 있음)
Future<void> showEvidenceSheet(BuildContext context, WidgetRef ref, String contentId, String placeName) {
  final l = ref.read(langProvider);
  final f = ref.read(courseRepoProvider).evidence(contentId, l);
  return showAppSheet(
    context,
    FutureBuilder<Evidence>(
      future: f,
      builder: (ctx, snap) {
        final e = snap.data;
        return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          _EvidenceImage(e?.imageUrl),
          const SizedBox(height: 12),
          Text(placeName, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
          Text(ct('ev', l), style: const TextStyle(fontSize: 13.5, color: C.text3)),
          const SizedBox(height: 8),
          if (snap.hasError) ErrorCard(snap.error!) else if (e == null) WaitRow(ct('evLoad', l), spinColor: C.blue) else KV([
            // 영·일이면 번역(참고)을 먼저, 원문은 그대로 아래에(FR-C011, API-C07)
            if (e.overviewTr != null) (ct('evOverviewTr', l), Text(e.overviewTr!)),
            if (e.hoursTr != null) (ct('evHoursTr', l), Text(e.hoursTr!)),
            (ct('evOverview', l), Text(e.overview)), (ct('evHours', l), Text(e.hours)), (ct('evSource', l), Text(e.source)),
          ]),
          const SizedBox(height: 10),
          Btn(ct('close', l), onTap: () => Navigator.of(ctx).pop()),
        ]);
      },
    ),
  );
}

/// 대표 이미지. 변경 금지(Type3) 사진이 있어 자르지 않고 전체를 보여 준다(DATA-C04). 없거나 못 불러오면 기본 이미지
class _EvidenceImage extends StatelessWidget {
  final String? url;
  const _EvidenceImage(this.url);
  static const _base = DecoratedBox(decoration: BoxDecoration(gradient: LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter, colors: [Color(0xFFBFDDEB), Color(0xFF8FC3DB), Color(0xFFE7D7B2), Color(0xFFD9C79C)], stops: [0, .55, .56, 1])));
  @override
  Widget build(BuildContext context) {
    final u = url?.replaceFirst('http://', 'https://');
    final has = u != null && u.isNotEmpty;
    return ClipRRect(
      borderRadius: BorderRadius.circular(rMd),
      child: SizedBox(
        height: has ? 200 : 120, width: double.infinity,
        child: !has
            ? _base
            : ColoredBox(
                color: C.subtle,
                child: Image.network(
                  u,
                  fit: BoxFit.contain,
                  semanticLabel: '대표 이미지',
                  webHtmlElementStrategy: WebHtmlElementStrategy.prefer, // 이미지 서버에 CORS 헤더가 없어 <img> 로 그린다
                  errorBuilder: (_, _, _) => _base,
                ),
              ),
      ),
    );
  }
}

PlacePin? _fixedPin(Course c) {
  final f = c.stops.where((s) => s.fixed).firstOrNull;
  return f == null ? null : PlacePin(f.id, f.name, '', f.lat, f.lng);
}

/// 출발 장소 한 줄 (코스 요청·결과 화면 공용). onPick이 null이면 비활성
class _StartPlaceRow extends ConsumerWidget {
  final PlacePin? place;
  final VoidCallback? onPick, onClear;
  const _StartPlaceRow({required this.place, required this.onPick, this.onClear});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = ref.watch(langProvider);
    return Material(
        color: Colors.white, borderRadius: BorderRadius.circular(rMd),
        child: InkWell(
          borderRadius: BorderRadius.circular(rMd), onTap: onPick,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            decoration: BoxDecoration(borderRadius: BorderRadius.circular(rMd), border: Border.all(color: place != null ? C.sea : C.line2)),
            child: Row(children: [
              Ic('pin', size: 17, color: place != null ? C.sea : C.text3),
              const SizedBox(width: 8),
              Expanded(child: Text.rich(TextSpan(children: [
                TextSpan(text: '${ct('start', l)}  ', style: const TextStyle(fontSize: 12.5, color: C.text3)),
                TextSpan(text: place?.name ?? ct('startAuto', l), style: TextStyle(fontSize: 14, fontWeight: FontWeight.w700, color: place != null ? C.text : C.text2)),
              ]))),
              if (onClear != null) GestureDetector(onTap: onClear, child: const Padding(padding: EdgeInsets.all(4), child: Ic('x', size: 16, color: C.text3))),
              Text(ct('startPick', l), style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700, color: onPick == null ? C.text4 : C.blue)),
            ]),
          ),
        ),
      );
  }
}

/// FR-C003 가정 칩 하나를 바꾸는 시트. 고르면 처음 요청 문장 + 바꾼 값으로 코스를 다시 짠다(API-C01)
Future<void> showAssumptionSheet(BuildContext context, WidgetRef ref, CourseAssumption a) {
  final now = DateTime.now();
  final lg = ref.read(langProvider);
  String two(int n) => n.toString().padLeft(2, '0');
  final (title, options) = switch (a.key) {
    'visitDate' => (ct('k.visitDate', lg), [
        for (var i = 0; i < 7; i++)
          (() { final d = now.add(Duration(days: i)); return ('${d.year}-${two(d.month)}-${two(d.day)}', dateText(d, now, lg)); })(),
      ]),
    'startTime' => (ct('k.startTime', lg), [for (var h = 8; h <= 21; h++) ('${two(h)}:00', '${two(h)}:00')]),
    'durationMin' => (ct('k.durationMin', lg), [for (var h = 1; h <= 8; h++) ('${h * 60}', durationText(h * 60, lg))]),
    'area' => (ct('k.area', lg), [for (final v in const ['GWANGALLI', 'HAEUNDAE']) (v, ct(v, lg))]),
    _ => (a.label, const <(String, String)>[]),
  };
  var picked = a.value;
  return showAppSheet(context, StatefulBuilder(builder: (ctx, set) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(ct('chg', lg, [title]), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
        const SizedBox(height: 4),
        Text(ct(a.isDefault ? 'chgDefault' : 'chgNow', lg, [assumptionText(a, lg, now)]), style: const TextStyle(fontSize: 13.5, color: C.text3, height: 1.5)),
        if (a.key == 'area') Padding(padding: const EdgeInsets.only(top: 4), child: Text(ct('chgArea', lg), style: const TextStyle(fontSize: 12.5, color: C.amber))),
        const SizedBox(height: 14),
        Wrap(spacing: 8, runSpacing: 8, children: [
          for (final (v, l) in options)
            ChoiceChip(
              label: Text(l, style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w600, color: picked == v ? Colors.white : C.text2)),
              selected: picked == v, showCheckmark: false, selectedColor: C.blue, backgroundColor: Colors.white,
              side: BorderSide(color: picked == v ? C.blue : C.line2),
              onSelected: (_) => set(() => picked = v),
            ),
        ]),
        const SizedBox(height: 16),
        Text(ct('chgKeep', lg), style: const TextStyle(fontSize: 12, color: C.text3, height: 1.5)),
        const SizedBox(height: 10),
        Btn(ct(picked == a.value ? 'chgNone' : 'chgGo', lg), kind: picked == a.value ? BtnKind.secondary : BtnKind.primary, onTap: picked == a.value ? null : () {
          Navigator.of(ctx).pop();
          ref.read(courseProvider.notifier).changeAssumption(a.key, picked);
        }),
      ])));
}

/// FR-C014 지도에서 출발 장소 고르기 (API-C04). 핀이나 목록을 눌러 고르고 "여기서 출발"
Future<PlacePin?> showStartPlaceSheet(BuildContext context, WidgetRef ref, {PlacePin? current, Area area = Area.gwangalli}) {
  final l = ref.read(langProvider);
  var ar = area;
  var sel = current;
  Future<List<PlacePin>> load() => ref.read(courseRepoProvider).startCandidates(ar);
  var future = load();
  return showAppSheet<PlacePin>(context, StatefulBuilder(builder: (ctx, set) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(ct('startTitle', l), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
        const SizedBox(height: 4),
        Text(ct('startSub', l), style: const TextStyle(fontSize: 13.5, color: C.text3)),
        const SizedBox(height: 12),
        SegmentedButton<Area>(
          segments: [ButtonSegment(value: Area.gwangalli, label: Text(ct('GWANGALLI', l))), ButtonSegment(value: Area.haeundae, label: Text(ct('HAEUNDAE', l)))],
          selected: {ar}, showSelectedIcon: false,
          onSelectionChanged: (v) => set(() { ar = v.first; sel = null; future = load(); }),
        ),
        const SizedBox(height: 12),
        FutureBuilder<List<PlacePin>>(
          future: future,
          builder: (_, snap) {
            if (snap.hasError) return ErrorCard(snap.error!, onRetry: () => set(() => future = load()));
            final pins = snap.data;
            if (pins == null) return WaitRow(ct('startLoad', l), spinColor: C.blue);
            return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              MapView(
                pins: [for (var i = 0; i < pins.length; i++) MapPoint(pins[i].lat, pins[i].lng, isFixed: pins[i].contentId == sel?.contentId)],
                showMe: false, area: ar, height: 180, caption: ct('startTap', l),
                onPinTap: (i) => set(() => sel = pins[i]),
              ),
              // #214 확정 버튼은 지도 바로 아래(목록 위)에 둔다. 목록 행은 누르면 바로 확정
              const SizedBox(height: 10),
              Btn(sel == null ? ct('startNone', l) : ct('startGo', l, [sel!.name]), kind: sel == null ? BtnKind.secondary : BtnKind.primary, onTap: sel == null ? null : () => Navigator.of(ctx).pop(sel)),
              const SizedBox(height: 10),
              for (var i = 0; i < pins.length; i++)
                Material(
                  color: pins[i].contentId == sel?.contentId ? C.seaT : Colors.transparent, borderRadius: BorderRadius.circular(rMd),
                  child: InkWell(
                    borderRadius: BorderRadius.circular(rMd), onTap: () => Navigator.of(ctx).pop(pins[i]),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 9),
                      child: Row(children: [
                        Container(width: 24, height: 24, alignment: Alignment.center, decoration: BoxDecoration(color: pins[i].contentId == sel?.contentId ? C.sea : C.text, shape: BoxShape.circle), child: Text('${i + 1}', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w800, fontSize: 12))),
                        const SizedBox(width: 10),
                        Expanded(child: Text(pins[i].name, style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w600))),
                        if (pins[i].category.isNotEmpty) Text(kCourseText.containsKey('cat.${pins[i].category}') ? ct('cat.${pins[i].category}', l) : pins[i].category, style: const TextStyle(fontSize: 12.5, color: C.text3)),
                      ]),
                    ),
                  ),
                ),
            ]);
          },
        ),
      ])));
}
