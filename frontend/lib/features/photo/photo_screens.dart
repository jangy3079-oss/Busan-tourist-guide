// [사진 담당] UI-P01 사진 선택 · P02 식별 결과 · P03 후보 선택 · P04 실패 안내. 이 파일은 사진 담당만 수정한다.
import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:image_picker/image_picker.dart';
import '../../core/i18n.dart';
import '../../core/theme.dart';
import '../../data/models/models.dart';
import '../../state/providers.dart';
import '../../widgets/common.dart';
import '../../widgets/error_card.dart';
import '../../widgets/map_view.dart';
import '../bridge.dart';
import 'photo_controller.dart';
import 'photo_link.dart';
import 'photo_text.dart';

// SRS FR-P002: JPG·PNG, 10MB 이하. HEIC 는 미지원(#39 결정 전까지 SRS 대로)
const kAllowedExt = ['jpg', 'jpeg', 'png'];
const kMaxBytes = 10 * 1024 * 1024;

Widget _signBox(String text, {double h = 190, bool blur = false}) => Container(
      height: h, width: double.infinity,
      decoration: BoxDecoration(borderRadius: BorderRadius.circular(rLg), gradient: const LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter, colors: [Color(0xFF2C3138), Color(0xFF1B1E22)])),
      alignment: Alignment.center,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 10),
        decoration: BoxDecoration(color: const Color(0xFF8E1C1C), borderRadius: BorderRadius.circular(8)),
        child: Text(text, style: TextStyle(color: Colors.white, fontSize: h < 150 ? 20 : 24, fontWeight: FontWeight.w800, shadows: blur ? [const Shadow(blurRadius: 8, color: Colors.white54)] : null)),
      ),
    );

/// 올린 사진이 있으면 그 사진, 없으면(샘플·이름 검색) 예시 간판 그림(#224)
Widget _photoOrSign(Uint8List? img, String text, {double h = 190, bool blur = false}) => img == null
    ? _signBox(text, h: h, blur: blur)
    : ClipRRect(borderRadius: BorderRadius.circular(rLg), child: Container(height: h, width: double.infinity, color: const Color(0xFF1B1E22), child: Image.memory(img, fit: BoxFit.contain, gaplessPlayback: true)));

class PhotoPickScreen extends ConsumerWidget {
  const PhotoPickScreen({super.key});

  Future<void> _pick(BuildContext context, WidgetRef ref, ImageSource src) async {
    final f = await ImagePicker().pickImage(source: src, maxWidth: 2048);
    if (f == null) return;
    final ext = f.name.split('.').last.toLowerCase();
    final bytes = await f.readAsBytes();
    if (!context.mounted) return;
    final l = ref.read(langProvider);
    if (!kAllowedExt.contains(ext)) { toast(context, pt('badExt', l)); return; }
    if (bytes.length > kMaxBytes) { toast(context, pt('tooBig', l)); return; }
    // 개발 중 Mock은 사진 내용과 상관없이 "확정"을 돌려준다(sample 'ok'). Api 모드에서는 sample을 무시하고 실제로 전송.
    ref.read(photoProvider.notifier).identify(bytes: bytes, fileName: f.name, sample: 'ok');
    context.go('/photo/scan');
  }

  void _sample(BuildContext context, WidgetRef ref, String kind) {
    ref.read(photoProvider.notifier).identify(sample: kind);
    context.go('/photo/scan');
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = ref.watch(langProvider);
    final loc = ref.watch(photoProvider.select((s) => s.loc));
    Widget sample(String label, String sub, Color bg, String kind) => Expanded(
          child: GestureDetector(
            onTap: () => _sample(context, ref, kind),
            child: Column(children: [
              Container(height: 64, alignment: Alignment.center, decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(rMd)), child: Text(label, style: const TextStyle(color: Colors.white, fontSize: 12.5, fontWeight: FontWeight.w700))),
              const SizedBox(height: 4), Text(sub, style: const TextStyle(fontSize: 12, color: C.text3)),
            ]),
          ),
        );
    // 사진 → 글자 → 가게 3단계. 처음 쓰는 사람이 무슨 기능인지 바로 알게 한다(시안 C)
    Widget step(String icon, String t) => SizedBox(width: 88, child: Column(children: [
          Container(width: 48, height: 48, alignment: Alignment.center, decoration: const BoxDecoration(color: C.seaT, shape: BoxShape.circle), child: Ic(icon, size: 24, color: C.sea)),
          const SizedBox(height: 6),
          Text(t, textAlign: TextAlign.center, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700)),
        ]));
    return ScreenFrame(
      head: AppHead(pt('title', l), pt('sub', l), backTo: '/'),
      body: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        // 카메라 미리보기처럼 보이던 어두운 사각형 대신 과정 안내. 촬영은 버튼으로 폰 카메라를 연다(FR-P001)
        Container(
          width: double.infinity, padding: const EdgeInsets.fromLTRB(16, 18, 16, 16), decoration: cardDeco(),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(pt('howTitle', l), style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700)),
            const SizedBox(height: 14),
            Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, crossAxisAlignment: CrossAxisAlignment.start, children: [
              step('camera', pt('step1', l)),
              const Padding(padding: EdgeInsets.only(top: 12), child: Ic('right', color: C.text3)),
              step('focus', pt('step2', l)),
              const Padding(padding: EdgeInsets.only(top: 12), child: Ic('right', color: C.text3)),
              step('store', pt('step3', l)),
            ]),
            const SizedBox(height: 14),
            Container(
              width: double.infinity, padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              decoration: BoxDecoration(color: C.bg, borderRadius: BorderRadius.circular(rMd)),
              child: Text(pt('tip', l), style: const TextStyle(fontSize: 13, color: C.text2, height: 1.5)),
            ),
          ]),
        ),
        const SizedBox(height: 14),
        Row(children: [
          Expanded(child: Btn(pt('shoot', l), icon: 'camera', kind: BtnKind.primary, onTap: () => _pick(context, ref, ImageSource.camera))),
          const SizedBox(width: 8),
          Expanded(child: Btn(pt('album', l), icon: 'image', onTap: () => _pick(context, ref, ImageSource.gallery))),
        ]),
        // 샘플은 이미지가 없는 Mock 전용 시연이다. 실제 서버 모드에서는 항상 실패 화면이 나와서 숨긴다(#199)
        if (ref.watch(apiClientProvider) == null) ...[
          SecTitle(pt('samples', l), sub: pt('demo', l)),
          Row(children: [
            sample('해운대암소갈비집', pt('sOk', l), const Color(0xFF8E1C1C), 'ok'), const SizedBox(width: 8),
            sample('…소갈비', pt('sCand', l), const Color(0xFF5A4A3A), 'cand'), const SizedBox(width: 8),
            sample('■■■', pt('sFail', l), const Color(0xFF3B4048), 'fail'),
          ]),
        ],
        const SizedBox(height: 14),
        // FR-P003: 찍을 때마다 위치를 한 번 묻는다. 거부되면 위치 없이 2개 구 전체에서 찾는다
        Container(padding: const EdgeInsets.all(12), decoration: cardDeco(), child: Row(children: [Ic('pin', size: 18, color: loc == PhotoLoc.off ? C.amber : C.sea), const SizedBox(width: 8), Expanded(child: Text(pt(switch (loc) { PhotoLoc.idle => 'locIdle', PhotoLoc.on => 'locOn', PhotoLoc.off => 'locOff' }, l), style: const TextStyle(fontSize: 13)))])),
        const SizedBox(height: 10),
        PrivNote(pt('priv', l)),
      ]),
    );
  }
}

class PhotoScanScreen extends ConsumerWidget {
  const PhotoScanScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final st = ref.watch(photoProvider);
    final l = ref.watch(langProvider);
    ref.listen<PhotoState>(photoProvider, (prev, next) {
      if (next.phase == PhotoPhase.done && next.result != null) {
        context.go(switch (next.result!.decision) { MatchDecision.confirmed => '/photo/result', MatchDecision.candidates => '/photo/candidates', MatchDecision.notFound || MatchDecision.ocrFail => '/photo/fail' });
      }
    });
    return ScreenFrame(
      head: AppHead(pt('title', l), pt('sub', l), backTo: '/photo'),
      body: Column(children: [
        _photoOrSign(ref.read(photoProvider.notifier).image, st.sample == 'cand' ? '…소갈비' : '해운대암소갈비집', blur: st.sample == 'fail'),
        const SizedBox(height: 14),
        if (st.error != null) ...[
          ErrorCard(st.error!, onRetry: () => ref.read(photoProvider.notifier).retry()),
          const SizedBox(height: 10),
          Btn(pt('retake', l), icon: 'camera', onTap: () => context.go('/photo')),
        ] else
          WaitRow(pt('scanning', l), spinColor: C.blue),
      ]),
    );
  }
}

class PhotoResultScreen extends ConsumerWidget {
  const PhotoResultScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final st = ref.watch(photoProvider);
    final l = ref.watch(langProvider);
    // 언어를 바꾸면 설명을 그 언어로 다시 받는다(FR-P011, #225)
    ref.listen<Lang>(langProvider, (prev, next) { if (prev != next) ref.read(photoProvider.notifier).renarrate(); });
    final p = st.selected;
    if (p == null) {
      WidgetsBinding.instance.addPostFrameCallback((_) { if (context.mounted) context.go('/photo'); });
      return const ScreenFrame(body: SizedBox());
    }
    return ScreenFrame(
      head: AppHead(pt('found', l), pt('sub', l), backTo: '/photo'),
      body: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        _photoOrSign(ref.read(photoProvider.notifier).image, p.name, h: 160),
        const SizedBox(height: 12),
        Container(
          padding: const EdgeInsets.all(14), decoration: cardDeco(),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            if (st.result?.decision == MatchDecision.confirmed) TagC.green(pt('confirmed', l), icon: 'check'),
            const SizedBox(height: 8),
            Text(p.name, style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
            if (l != Lang.ko) Text(pt('origName', l), style: const TextStyle(fontSize: 12.5, color: C.text3)), // FR-P010: 상호는 원문 그대로
            const SizedBox(height: 8),
            KV([
              (pt('kCategory', l), Text(photoCategory(p.category, l))),
              (pt('kWhere', l), Text([p.address, if (p.distM != null) pt('fromYou', l, [p.distM!]), if (st.result?.lowAccuracy ?? false) pt('lowAcc', l)].join(' · '))), // FR-P003
              (pt('kHours', l), Row(children: [p.openNow ? TagC.green(pt('open', l)) : TagC.amber(pt('check', l)), const SizedBox(width: 6), Flexible(child: Text(p.hours))])),
              if (p.overview?.isNotEmpty ?? false) (pt('overview', l), Text(p.overview!, maxLines: 4, overflow: TextOverflow.ellipsis)),
            ]),
          ]),
        ),
        const SizedBox(height: 10),
        MapView(pins: [MapPoint(p.lat, p.lng, label: '★', name: p.name, isFixed: true)], area: Area.haeundae, height: 150),
        const SizedBox(height: 10),
        Container(
          width: double.infinity, padding: const EdgeInsets.all(14), decoration: cardDeco(),
          // FR-P011: 생성된 설명은 원문과 구분해 표시. 설명이 늦거나 실패해도 위의 정보·지도는 그대로(NFR-P001·P004)
          child: st.narrationFailed
              ? Text(pt('noDesc', l), style: const TextStyle(fontSize: 13, color: C.text3))
              : st.narration != null
                  ? Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(st.narration!, style: const TextStyle(fontSize: 14, height: 1.6, color: C.text2)), Src('${pt('generated', l)} · ${p.source ?? 'ⓒ한국관광공사'}')])
                  : Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(pt('writing', l), style: const TextStyle(fontSize: 12.5, color: C.text3)), const Skel(.9), const Skel(.66)]),
        ),
        XBtn(icon: 'route', title: pt('toCourse', l), sub: pt('toCourseSub', l), iconBg: C.blueT, iconFg: C.blue, onTap: () => photoToCourse(context, ref, p)),
        // FR-P018: 좌표로 카카오맵 열기. 좌표는 링크에만 쓰고 저장하지 않는다
        XBtn(icon: 'map', title: pt('kakaoMap', l), sub: pt('kakaoMapSub', l), iconBg: C.seaT, iconFg: C.sea, onTap: () => ref.read(photoLinkProvider)(kakaoMapUrl(p.name, p.lat, p.lng))),
      ]),
    );
  }
}

class PhotoCandidatesScreen extends ConsumerWidget {
  const PhotoCandidatesScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final cands = ref.watch(photoProvider).result?.candidates ?? const [];
    final l = ref.watch(langProvider);
    return ScreenFrame(
      head: AppHead(pt('similar', l), pt('sub', l), backTo: '/photo'),
      body: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(pt('which', l), style: const TextStyle(fontSize: 19, fontWeight: FontWeight.w800)),
        const SizedBox(height: 4),
        Text(pt('whichSub', l), style: const TextStyle(fontSize: 13.5, color: C.text3, height: 1.5)),
        const SizedBox(height: 12),
        for (var i = 0; i < cands.length; i++)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Material(
              color: Colors.white, borderRadius: BorderRadius.circular(rLg),
              child: InkWell(
                borderRadius: BorderRadius.circular(rLg),
                onTap: () { ref.read(photoProvider.notifier).select(cands[i].place); context.go('/photo/result'); },
                child: Container(
                  padding: const EdgeInsets.all(14), decoration: cardDeco(),
                  child: Row(children: [
                    Container(width: 28, height: 28, alignment: Alignment.center, decoration: const BoxDecoration(color: C.text, shape: BoxShape.circle), child: Text('${i + 1}', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w800, fontSize: 13))),
                    const SizedBox(width: 12),
                    Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(cands[i].place.name, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700)), Text([photoCategory(cands[i].place.category, l), if (cands[i].place.distM != null) '${cands[i].place.distM}m'].join(' · '), style: const TextStyle(fontSize: 12.5, color: C.text3))])),
                    Text.rich(TextSpan(children: [TextSpan(text: '${cands[i].score}', style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16, color: C.blue)), TextSpan(text: pt('pts', l), style: const TextStyle(fontSize: 12, color: C.text3))])),
                  ]),
                ),
              ),
            ),
          ),
        const SizedBox(height: 6),
        Btn(pt('notListed', l), icon: 'search', onTap: () => context.go('/photo/fail')),
      ]),
    );
  }
}

class PhotoFailScreen extends ConsumerStatefulWidget {
  const PhotoFailScreen({super.key});
  @override
  ConsumerState<PhotoFailScreen> createState() => _PhotoFailState();
}

class _PhotoFailState extends ConsumerState<PhotoFailScreen> {
  final _c = TextEditingController();
  @override
  void dispose() { _c.dispose(); super.dispose(); }

  Future<void> _search() async {
    final v = _c.text.trim();
    if (v.isEmpty) return;
    await ref.read(photoProvider.notifier).searchByName(v);
    if (!mounted) return;
    final r = ref.read(photoProvider).result;
    context.go(r != null && r.decision == MatchDecision.candidates ? '/photo/candidates' : '/photo/fail');
  }

  @override
  Widget build(BuildContext context) {
    final st = ref.watch(photoProvider);
    final l = ref.watch(langProvider);
    final notFound = st.result?.decision == MatchDecision.notFound;
    return ScreenFrame(
        head: AppHead(pt('notFoundT', l), pt('sub', l), backTo: '/photo'),
        body: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Center(child: Column(children: [
            const SizedBox(height: 18),
            Container(width: 56, height: 56, decoration: BoxDecoration(color: C.amberT, borderRadius: BorderRadius.circular(rLg)), child: const Ic('image', size: 26, color: C.amber)),
            const SizedBox(height: 14),
            // FR-P014: OCR_FAIL 이면 재촬영 안내, NOT_FOUND 이면 미등록 안내
            Text(pt(notFound ? 'nfTitle' : 'ocrTitle', l), style: const TextStyle(fontSize: 19, fontWeight: FontWeight.w800)),
            const SizedBox(height: 6),
            Text(pt(notFound ? 'nfBody' : 'ocrBody', l), textAlign: TextAlign.center, style: const TextStyle(fontSize: 14, color: C.text3, height: 1.55)),
          ])),
          const SizedBox(height: 16),
          Btn(pt('retake', l), icon: 'camera', kind: BtnKind.primary, onTap: () => context.go('/photo')),
          SecTitle(pt('byName', l)),
          Container(
            decoration: cardDeco(), padding: const EdgeInsets.symmetric(horizontal: 6),
            child: Row(children: [
              const Padding(padding: EdgeInsets.all(8), child: Ic('search', color: C.text3)),
              Expanded(child: TextField(controller: _c, onSubmitted: (_) => _search(), decoration: InputDecoration(hintText: pt('nameHint', l), border: InputBorder.none))),
              IconButton(onPressed: _search, icon: const Ic('right', color: C.blue)),
            ]),
          ),
          if (st.error != null) Padding(padding: const EdgeInsets.only(top: 10), child: ErrorCard(st.error!, onRetry: _search))
          else if (notFound && st.lastQuery != null) Padding(padding: const EdgeInsets.only(top: 8), child: Text(pt('searchNone', l, [st.lastQuery!]), style: const TextStyle(fontSize: 13, color: C.text3))),
        ]),
      );
  }
}
