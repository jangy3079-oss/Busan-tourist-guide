// [코스 담당] UI-C01 코스 요청 · UI-C02 코스 결과 · UI-C03 근거 카드. 이 파일은 코스 담당만 수정한다.
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/theme.dart';
import '../../data/models/models.dart';
import '../../state/providers.dart';
import '../../widgets/common.dart';
import '../../widgets/map_view.dart';
import '../bridge.dart';
import 'course_controller.dart';

const _examples = ['오후에 광안리에서 바다 보고 회 먹기, 3시간', '비 오는 날 해운대 실내 코스', '아이와 해운대 반나절'];

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
    ref.listen<CourseState>(courseProvider, (prev, next) {
      if (!next.loading && next.course != null && prev?.course != next.course) context.go('/course/result');
    });
    _listenHints(context, ref);
    final ctrl = ref.read(courseProvider.notifier);
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
      head: const AppHead('코스 만들기', '자연어 코스 추천', backTo: '/'),
      body: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        bubble(const ChatMsg('어떤 하루를 보내고 싶으세요? 날짜·시간·지역·취향을 편하게 말해 주세요. 빠진 조건은 기본값으로 채우고, 결과 위에서 바로 고칠 수 있어요.')),
        if (st.chat.isEmpty)
          Padding(
            padding: const EdgeInsets.only(left: 36, top: 14),
            child: Wrap(spacing: 8, runSpacing: 8, children: [for (final x in _examples) ActionChip(label: Text(x, style: const TextStyle(fontSize: 13)), onPressed: () => ctrl.start(x), backgroundColor: Colors.white, side: const BorderSide(color: C.line2))]),
          ),
        for (final m in st.chat) bubble(m),
        const SizedBox(height: 16),
        _StartPlaceRow(
          place: st.startPlace,
          onPick: () async { final p = await showStartPlaceSheet(context, ref, current: st.startPlace); if (p != null) ctrl.setStartPlace(p); },
          onClear: st.startPlace == null ? null : () => ctrl.setStartPlace(null),
        ),
        const SizedBox(height: 10),
        const PrivNote('수영구·해운대구 안의 관광 코스만 만들어요. 예약·결제는 지원하지 않아요.', icon: 'info'),
      ]),
      dock: Dock(hint: '예: 오후에 바다 보고 회 먹기', onSend: ctrl.start, onSos: () => openEmergency(context, ref), onCamera: () => startPhoto(context, ref)),
    );
  }
}

class CourseResultScreen extends ConsumerWidget {
  const CourseResultScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final st = ref.watch(courseProvider);
    final c = st.course;
    ref.listen<CourseState>(courseProvider, (prev, next) {
      if (next.course != null && next.course!.version > 1 && (prev?.course?.version ?? 0) < next.course!.version) toast(context, 'v${next.course!.version} 코스를 만들었어요 · 바뀐 곳 강조');
      if (next.editNotice != null && prev?.editNotice != next.editNotice) { toast(context, next.editNotice!); ref.read(courseProvider.notifier).clearHint(); }
    });
    _listenHints(context, ref);
    if (c == null) {
      // 직접 URL로 들어온 경우 등: 요청 화면으로
      WidgetsBinding.instance.addPostFrameCallback((_) { if (context.mounted) context.go('/course'); });
      return const ScreenFrame(body: SizedBox());
    }
    final fixedStart = c.hasFixedStart;
    final ctrl = ref.read(courseProvider.notifier);
    return ScreenFrame(
      head: AppHead('코스 결과', fixedStart ? '출발 장소를 고정한 코스 · v${c.version}' : '오후 바다 + 회 · v${c.version}', backTo: '/course'),
      body: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        // 가정 칩 (FR-C003): 누르면 그 조건만 바꿔 다시 짠다. 기본값으로 채운 칩은 점선 테두리
        Wrap(spacing: 6, runSpacing: 6, children: [
          for (var i = 0; i < (c.assumptions.isEmpty ? c.chips.length : c.assumptions.length); i++)
            Builder(builder: (_) {
              final a = c.assumptions.isEmpty ? null : c.assumptions[i];
              final label = a?.label ?? c.chips[i];
              final changed = c.changedChipIndex == i;
              return ActionChip(
                onPressed: a == null || st.replanning ? null : () => showAssumptionSheet(context, ref, a),
                avatar: a == null ? null : Ic('edit', size: 13, color: changed ? C.blueD : C.text3),
                label: Text(changed ? '$label · 바뀜' : (a?.isDefault ?? false) ? '$label · 기본값' : label, style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600, color: changed ? C.blueD : C.text2)),
                backgroundColor: changed ? C.blueT2 : Colors.white, side: BorderSide(color: changed ? C.blue : C.line2),
              );
            }),
        ]),
        const SizedBox(height: 8),
        _StartPlaceRow(
          place: st.startPlace ?? _fixedPin(c),
          onPick: st.replanning ? null : () async { final p = await showStartPlaceSheet(context, ref, current: st.startPlace ?? _fixedPin(c), area: c.area); if (p != null) ctrl.changeStartPlace(p); },
        ),
        if (st.replanning) const Padding(padding: EdgeInsets.only(top: 10), child: WaitRow('바꾼 조건으로 코스를 다시 짜는 중…', spinColor: C.blue)),
        if (c.relaxed.isNotEmpty) Padding(padding: const EdgeInsets.only(top: 8), child: Text('조건 완화: ${c.relaxed.join(', ')}', style: const TextStyle(fontSize: 12.5, color: C.amber))), // FR-C007
        const SizedBox(height: 12),
        MapView(pins: [for (final s in c.stops) s.point], path: c.path.isEmpty ? null : c.path, route: true, showMe: false, area: c.area, height: 200),
        const SizedBox(height: 12),
        for (var i = 0; i < c.stops.length; i++) _StopRow(i + 1, c.stops[i]),
        if (c.version > 1 && ctrl.canRevert)
          Container(
            margin: const EdgeInsets.only(top: 6), padding: const EdgeInsets.all(10), decoration: cardDeco(color: C.blueT, border: C.blueT2),
            child: Row(children: [
              const Ic('refresh', size: 14, color: C.blueD), const SizedBox(width: 6),
              Text('v${c.version - 1}', style: const TextStyle(fontSize: 12.5, color: C.text3, decoration: TextDecoration.lineThrough)),
              const SizedBox(width: 6), Text('→ v${c.version}${c.changedChipIndex != null && c.changedChipIndex! < c.assumptions.length ? ' · ${c.assumptions[c.changedChipIndex!].label}' : ''}', style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700, color: C.blueD)),
              const Spacer(),
              GestureDetector(onTap: ctrl.revert, child: TagC('v${c.version - 1}로 되돌리기')),
            ]),
          ),
        const SizedBox(height: 12),
        Container(
          width: double.infinity, padding: const EdgeInsets.all(14), decoration: cardDeco(),
          child: st.explanation != null
              ? Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(st.explanation!, style: const TextStyle(fontSize: 14, height: 1.6, color: C.text2)),
                  const Src('근거: 한국관광공사 관광정보 · 수집일 2026-10-12 · 장소 이름을 누르면 원문을 볼 수 있어요'),
                ])
              : const Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text('코스 설명을 쓰는 중…', style: TextStyle(fontSize: 12.5, color: C.text3)), Skel(.92), Skel(.7),
                ]),
        ),
      ]),
      dock: Dock(hint: '예: 2시간으로 줄여줘', onSend: ctrl.edit, onSos: () => openEmergency(context, ref), onCamera: () => startPhoto(context, ref)),
    );
  }
}

class _StopRow extends ConsumerWidget {
  final int n;
  final CourseStop s;
  const _StopRow(this.n, this.s);
  @override
  Widget build(BuildContext context, WidgetRef ref) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Container(width: 28, height: 28, alignment: Alignment.center, decoration: BoxDecoration(color: s.fixed ? C.sea : C.text, shape: BoxShape.circle), child: Text('$n', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w800, fontSize: 13))),
          const SizedBox(width: 12),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text('${s.time}${s.changed ? ' · 바뀜' : ''}${s.fixed ? ' · 출발지 고정' : ''}${s.hoursUncertain ? ' · 방문 전 확인' : ''}', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: s.changed ? C.blue : C.text3)),
            GestureDetector(onTap: () => showEvidenceSheet(context, ref, s.id, s.name), child: Text(s.name, style: const TextStyle(fontSize: 15.5, fontWeight: FontWeight.w700, decoration: TextDecoration.underline, decorationColor: C.line2))),
            Text(s.desc, style: const TextStyle(fontSize: 13, color: C.text3, height: 1.45)),
          ])),
        ]),
      );
}

/// UI-C03 근거 카드 (사진 결과 화면에서도 같은 시트를 쓸 수 있음)
Future<void> showEvidenceSheet(BuildContext context, WidgetRef ref, String contentId, String placeName) {
  final f = ref.read(courseRepoProvider).evidence(contentId, ref.read(langProvider));
  return showAppSheet(
    context,
    FutureBuilder<Evidence>(
      future: f,
      builder: (ctx, snap) {
        final e = snap.data;
        return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Container(height: 120, decoration: BoxDecoration(borderRadius: BorderRadius.circular(rMd), gradient: const LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter, colors: [Color(0xFFBFDDEB), Color(0xFF8FC3DB), Color(0xFFE7D7B2), Color(0xFFD9C79C)], stops: [0, .55, .56, 1]))),
          const SizedBox(height: 12),
          Text(placeName, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
          const Text('근거 카드 · 공식 관광정보 원문', style: TextStyle(fontSize: 13.5, color: C.text3)),
          const SizedBox(height: 8),
          if (e == null) const WaitRow('원문을 불러오는 중…', spinColor: C.blue) else KV([('개요 원문', Text(e.overview)), ('운영 원문', Text(e.hours)), ('출처', Text(e.source))]),
          const SizedBox(height: 10),
          Btn('닫기', onTap: () => Navigator.of(ctx).pop()),
        ]);
      },
    ),
  );
}

PlacePin? _fixedPin(Course c) {
  final f = c.stops.where((s) => s.fixed).firstOrNull;
  return f == null ? null : PlacePin(f.id, f.name, '', f.lat, f.lng);
}

/// 출발 장소 한 줄 (코스 요청·결과 화면 공용). onPick이 null이면 비활성
class _StartPlaceRow extends StatelessWidget {
  final PlacePin? place;
  final VoidCallback? onPick, onClear;
  const _StartPlaceRow({required this.place, required this.onPick, this.onClear});
  @override
  Widget build(BuildContext context) => Material(
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
                const TextSpan(text: '출발 장소  ', style: TextStyle(fontSize: 12.5, color: C.text3)),
                TextSpan(text: place?.name ?? '자동으로 정하기', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w700, color: place != null ? C.text : C.text2)),
              ]))),
              if (onClear != null) GestureDetector(onTap: onClear, child: const Padding(padding: EdgeInsets.all(4), child: Ic('x', size: 16, color: C.text3))),
              Text('지도에서 고르기', style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700, color: onPick == null ? C.text4 : C.blue)),
            ]),
          ),
        ),
      );
}

/// FR-C003 가정 칩 하나를 바꾸는 시트. 고르면 처음 요청 문장 + 바꾼 값으로 코스를 다시 짠다(API-C01)
Future<void> showAssumptionSheet(BuildContext context, WidgetRef ref, CourseAssumption a) {
  final now = DateTime.now();
  String two(int n) => n.toString().padLeft(2, '0');
  const wd = ['월', '화', '수', '목', '금', '토', '일'];
  final (title, options) = switch (a.key) {
    'visitDate' => ('방문일', [
        for (var i = 0; i < 7; i++)
          (() { final d = now.add(Duration(days: i)); return ('${d.year}-${two(d.month)}-${two(d.day)}', i == 0 ? '오늘' : i == 1 ? '내일' : '${d.month}/${d.day}(${wd[d.weekday - 1]})'); })(),
      ]),
    'startTime' => ('출발 시각', [for (var h = 8; h <= 21; h++) ('${two(h)}:00', '${two(h)}:00')]),
    'durationMin' => ('소요 시간', [for (var h = 1; h <= 8; h++) ('${h * 60}', '$h시간')]),
    'area' => ('권역', const [('GWANGALLI', '광안리'), ('HAEUNDAE', '해운대')]),
    _ => (a.label, const <(String, String)>[]),
  };
  var picked = a.value;
  return showAppSheet(context, StatefulBuilder(builder: (ctx, set) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text('$title 바꾸기', style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
        const SizedBox(height: 4),
        Text(a.isDefault ? '요청에 없어서 기본값(${a.label})으로 짰어요. 원하는 값을 고르세요.' : '지금: ${a.label}', style: const TextStyle(fontSize: 13.5, color: C.text3, height: 1.5)),
        if (a.key == 'area') const Padding(padding: EdgeInsets.only(top: 4), child: Text('권역을 바꾸면 그 권역에 없는 장소는 바뀔 수 있어요.', style: TextStyle(fontSize: 12.5, color: C.amber))),
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
        const Text('지금 코스에서 이 조건만 바꿔 새 버전을 만들어요. 대화로 고친 내용은 그대로 유지돼요.', style: TextStyle(fontSize: 12, color: C.text3, height: 1.5)),
        const SizedBox(height: 10),
        Btn(picked == a.value ? '바꿀 값을 골라 주세요' : '이 조건으로 다시 짜기', kind: picked == a.value ? BtnKind.secondary : BtnKind.primary, onTap: picked == a.value ? null : () {
          Navigator.of(ctx).pop();
          ref.read(courseProvider.notifier).changeAssumption(a.key, picked);
        }),
      ])));
}

/// FR-C014 지도에서 출발 장소 고르기 (API-C04). 핀이나 목록을 눌러 고르고 "여기서 출발"
Future<PlacePin?> showStartPlaceSheet(BuildContext context, WidgetRef ref, {PlacePin? current, Area area = Area.gwangalli}) {
  var ar = area;
  var sel = current;
  Future<List<PlacePin>> load() => ref.read(courseRepoProvider).startCandidates(ar);
  var future = load();
  return showAppSheet<PlacePin>(context, StatefulBuilder(builder: (ctx, set) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text('출발 장소 고르기', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
        const SizedBox(height: 4),
        const Text('고른 곳을 1번으로 고정하고 나머지를 이어서 짜요.', style: TextStyle(fontSize: 13.5, color: C.text3)),
        const SizedBox(height: 12),
        SegmentedButton<Area>(
          segments: const [ButtonSegment(value: Area.gwangalli, label: Text('광안리')), ButtonSegment(value: Area.haeundae, label: Text('해운대'))],
          selected: {ar}, showSelectedIcon: false,
          onSelectionChanged: (v) => set(() { ar = v.first; sel = null; future = load(); }),
        ),
        const SizedBox(height: 12),
        FutureBuilder<List<PlacePin>>(
          future: future,
          builder: (_, snap) {
            if (snap.hasError) return const Text('장소를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.', style: TextStyle(color: C.text3));
            final pins = snap.data;
            if (pins == null) return const WaitRow('장소를 불러오는 중…', spinColor: C.blue);
            return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              MapView(
                pins: [for (var i = 0; i < pins.length; i++) MapPoint(pins[i].lat, pins[i].lng, isFixed: pins[i].contentId == sel?.contentId)],
                showMe: false, area: ar, height: 180, caption: '핀을 눌러 고르세요',
                onPinTap: (i) => set(() => sel = pins[i]),
              ),
              const SizedBox(height: 10),
              for (var i = 0; i < pins.length; i++)
                Material(
                  color: pins[i].contentId == sel?.contentId ? C.seaT : Colors.transparent, borderRadius: BorderRadius.circular(rMd),
                  child: InkWell(
                    borderRadius: BorderRadius.circular(rMd), onTap: () => set(() => sel = pins[i]),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 9),
                      child: Row(children: [
                        Container(width: 24, height: 24, alignment: Alignment.center, decoration: BoxDecoration(color: pins[i].contentId == sel?.contentId ? C.sea : C.text, shape: BoxShape.circle), child: Text('${i + 1}', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w800, fontSize: 12))),
                        const SizedBox(width: 10),
                        Expanded(child: Text(pins[i].name, style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w600))),
                        if (pins[i].category.isNotEmpty) Text(pins[i].category, style: const TextStyle(fontSize: 12.5, color: C.text3)),
                      ]),
                    ),
                  ),
                ),
            ]);
          },
        ),
        const SizedBox(height: 12),
        Btn(sel == null ? '장소를 골라 주세요' : '${sel!.name}에서 출발', kind: sel == null ? BtnKind.secondary : BtnKind.primary, onTap: sel == null ? null : () => Navigator.of(ctx).pop(sel)),
      ])));
}
