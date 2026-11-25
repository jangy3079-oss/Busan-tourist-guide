// [사진 담당] UI-P01 사진 선택 · P02 식별 결과 · P03 후보 선택 · P04 실패 안내. 이 파일은 사진 담당만 수정한다.
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:image_picker/image_picker.dart';
import '../../core/theme.dart';
import '../../data/models/models.dart';
import '../../widgets/common.dart';
import '../../widgets/map_view.dart';
import '../bridge.dart';
import 'photo_controller.dart';

// SRS FR-P002: JPG·PNG, 10MB 이하. (프로토타입 문구는 "HEIC도 가능" → SRS와 불일치, 팀 확인 필요)
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

class PhotoPickScreen extends ConsumerWidget {
  const PhotoPickScreen({super.key});

  Future<void> _pick(BuildContext context, WidgetRef ref, ImageSource src) async {
    final f = await ImagePicker().pickImage(source: src, maxWidth: 2048);
    if (f == null) return;
    final ext = f.name.split('.').last.toLowerCase();
    final bytes = await f.readAsBytes();
    if (!context.mounted) return;
    if (!kAllowedExt.contains(ext)) { toast(context, 'JPG 또는 PNG 사진만 올릴 수 있어요'); return; }
    if (bytes.length > kMaxBytes) { toast(context, '사진은 10MB 이하만 올릴 수 있어요'); return; }
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
    Widget sample(String label, String sub, Color bg, String kind) => Expanded(
          child: GestureDetector(
            onTap: () => _sample(context, ref, kind),
            child: Column(children: [
              Container(height: 64, alignment: Alignment.center, decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(rMd)), child: Text(label, style: const TextStyle(color: Colors.white, fontSize: 12.5, fontWeight: FontWeight.w700))),
              const SizedBox(height: 4), Text(sub, style: const TextStyle(fontSize: 12, color: C.text3)),
            ]),
          ),
        );
    Widget guide(String icon, String t) => Expanded(child: Column(children: [Ic(icon, color: C.sea), const SizedBox(height: 4), Text(t, style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600))]));
    return ScreenFrame(
      head: const AppHead('간판 사진으로 찾기', '사진 기반 안내', backTo: '/'),
      body: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Container(
          height: 190, width: double.infinity, alignment: Alignment.center,
          decoration: BoxDecoration(borderRadius: BorderRadius.circular(rLg), gradient: const LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter, colors: [Color(0xFF2C3138), Color(0xFF1B1E22)])),
          child: const Column(mainAxisSize: MainAxisSize.min, children: [Ic('focus', size: 44, color: Colors.white70), SizedBox(height: 6), Text('간판이 화면 가운데 오도록 찍어 주세요', style: TextStyle(color: Colors.white70, fontSize: 13.5))]),
        ),
        const SizedBox(height: 12),
        Row(children: [guide('move', '가까이'), guide('focus', '정면으로'), guide('sun', '밝게')]),
        const SizedBox(height: 14),
        Row(children: [
          Expanded(child: Btn('촬영하기', icon: 'camera', kind: BtnKind.primary, onTap: () => _pick(context, ref, ImageSource.camera))),
          const SizedBox(width: 8),
          Expanded(child: Btn('앨범에서 고르기', icon: 'image', onTap: () => _pick(context, ref, ImageSource.gallery))),
        ]),
        const SecTitle('샘플 사진으로 해 보기', sub: '시연용'),
        Row(children: [
          sample('해운대암소갈비집', '확정', const Color(0xFF8E1C1C), 'ok'), const SizedBox(width: 8),
          sample('…소갈비', '후보 여러 곳', const Color(0xFF5A4A3A), 'cand'), const SizedBox(width: 8),
          sample('■■■', '인식 실패', const Color(0xFF3B4048), 'fail'),
        ]),
        const SizedBox(height: 14),
        Container(padding: const EdgeInsets.all(12), decoration: cardDeco(), child: const Row(children: [Ic('pin', size: 18, color: C.sea), SizedBox(width: 8), Expanded(child: Text.rich(TextSpan(children: [TextSpan(text: '위치를 쓰면 가까운 가게부터 찾아요 · '), TextSpan(text: '허용됨', style: TextStyle(fontWeight: FontWeight.w700))]), style: TextStyle(fontSize: 13)))])),
        const SizedBox(height: 10),
        const PrivNote('사진은 분석 후 바로 지우고 저장하지 않아요. 최대 10MB, JPG·PNG만 올릴 수 있어요.'),
      ]),
    );
  }
}

class PhotoScanScreen extends ConsumerWidget {
  const PhotoScanScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final st = ref.watch(photoProvider);
    ref.listen<PhotoState>(photoProvider, (prev, next) {
      if (next.phase == PhotoPhase.done && next.result != null) {
        context.go(switch (next.result!.decision) { MatchDecision.confirmed => '/photo/result', MatchDecision.candidates => '/photo/candidates', MatchDecision.notFound || MatchDecision.ocrFail => '/photo/fail' });
      }
    });
    return ScreenFrame(
      head: const AppHead('간판 사진으로 찾기', '사진 기반 안내', backTo: '/photo'),
      body: Column(children: [
        _signBox(st.sample == 'cand' ? '…소갈비' : '해운대암소갈비집', blur: st.sample == 'fail'),
        const SizedBox(height: 14),
        const WaitRow('간판 글자를 읽고 공식 관광정보와 맞춰 보는 중…', spinColor: C.blue),
      ]),
    );
  }
}

class PhotoResultScreen extends ConsumerWidget {
  const PhotoResultScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final st = ref.watch(photoProvider);
    final p = st.selected;
    if (p == null) {
      WidgetsBinding.instance.addPostFrameCallback((_) { if (context.mounted) context.go('/photo'); });
      return const ScreenFrame(body: SizedBox());
    }
    return ScreenFrame(
      head: const AppHead('찾은 가게', '사진 기반 안내', backTo: '/photo'),
      body: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        _signBox(p.name, h: 110),
        const SizedBox(height: 12),
        Container(
          padding: const EdgeInsets.all(14), decoration: cardDeco(),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            TagC.green('확정 · 일치도 높음', icon: 'check'),
            const SizedBox(height: 8),
            Text(p.name, style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
            if (p.romanized?.isNotEmpty ?? false) Text('${p.romanized} · 한글 원문 상호', style: const TextStyle(fontSize: 12.5, color: C.text3)),
            const SizedBox(height: 8),
            KV([
              ('업종', Text(p.category)),
              ('위치', Text([p.address, if (p.distM != null) '내 위치에서 ${p.distM}m', if (st.result?.lowAccuracy ?? false) '정확도가 낮을 수 있음'].join(' · '))), // FR-P003
              ('운영', Row(children: [p.openNow ? TagC.green('영업 중') : TagC.amber('방문 전 확인'), const SizedBox(width: 6), Flexible(child: Text(p.hours))])),
            ]),
          ]),
        ),
        const SizedBox(height: 10),
        MapView(pins: [MapPoint(p.lat, p.lng, label: '★', isFixed: true)], area: Area.haeundae, height: 150),
        const SizedBox(height: 10),
        Container(
          width: double.infinity, padding: const EdgeInsets.all(14), decoration: cardDeco(),
          child: st.narration != null
              ? Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(st.narration!, style: const TextStyle(fontSize: 14, height: 1.6, color: C.text2)), Src('근거: 한국관광공사 관광정보 원문 · ${p.source ?? 'ⓒ한국관광공사'}')])
              : const Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text('가게 설명을 쓰는 중… (정보·지도 먼저 표시)', style: TextStyle(fontSize: 12.5, color: C.text3)), Skel(.9), Skel(.66)]),
        ),
        XBtn(icon: 'route', title: '여기서 출발하는 코스', sub: '이 가게를 1번으로 고정해 코스를 만들어요', iconBg: C.blueT, iconFg: C.blue, onTap: () => photoToCourse(context, ref, p)),
      ]),
    );
  }
}

class PhotoCandidatesScreen extends ConsumerWidget {
  const PhotoCandidatesScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final cands = ref.watch(photoProvider).result?.candidates ?? const [];
    return ScreenFrame(
      head: const AppHead('비슷한 가게', '사진 기반 안내', backTo: '/photo'),
      body: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text('어느 가게인가요?', style: TextStyle(fontSize: 19, fontWeight: FontWeight.w800)),
        const SizedBox(height: 4),
        const Text('간판 글자가 일부만 읽혀 점수가 높은 3곳을 보여드려요.', style: TextStyle(fontSize: 13.5, color: C.text3, height: 1.5)),
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
                    Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(cands[i].place.name, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700)), Text([cands[i].place.category, if (cands[i].place.distM != null) '${cands[i].place.distM}m'].join(' · '), style: const TextStyle(fontSize: 12.5, color: C.text3))])),
                    Text.rich(TextSpan(children: [TextSpan(text: '${cands[i].score}', style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16, color: C.blue)), const TextSpan(text: '점', style: TextStyle(fontSize: 12, color: C.text3))])),
                  ]),
                ),
              ),
            ),
          ),
        const SizedBox(height: 6),
        Btn('목록에 없어요 · 이름으로 찾기', icon: 'search', onTap: () => context.go('/photo/fail')),
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

  bool get _notFound => ref.watch(photoProvider).result?.decision == MatchDecision.notFound;

  @override
  Widget build(BuildContext context) => ScreenFrame(
        head: const AppHead('찾지 못했어요', '사진 기반 안내', backTo: '/photo'),
        body: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Center(child: Column(children: [
            const SizedBox(height: 18),
            Container(width: 56, height: 56, decoration: BoxDecoration(color: C.amberT, borderRadius: BorderRadius.circular(rLg)), child: const Ic('image', size: 26, color: C.amber)),
            const SizedBox(height: 14),
            // FR-P014: OCR_FAIL 이면 재촬영 안내, NOT_FOUND 이면 미등록 안내
            Text(_notFound ? '등록된 가게를 찾지 못했어요' : '간판 글자를 읽지 못했어요', style: const TextStyle(fontSize: 19, fontWeight: FontWeight.w800)),
            const SizedBox(height: 6),
            Text(_notFound ? '공식 관광데이터에 없는 가게일 수 있어요.\n가게 이름으로 다시 찾아보세요.' : '흔들리거나 어두우면 잘 읽히지 않아요.\n밝은 곳에서 간판을 정면으로 찍어 주세요.', textAlign: TextAlign.center, style: const TextStyle(fontSize: 14, color: C.text3, height: 1.55)),
          ])),
          const SizedBox(height: 16),
          Btn('다시 찍기', icon: 'camera', kind: BtnKind.primary, onTap: () => context.go('/photo')),
          const SecTitle('가게 이름으로 찾기'),
          Container(
            decoration: cardDeco(), padding: const EdgeInsets.symmetric(horizontal: 6),
            child: Row(children: [
              const Padding(padding: EdgeInsets.all(8), child: Ic('search', color: C.text3)),
              Expanded(child: TextField(controller: _c, onSubmitted: (_) => _search(), decoration: const InputDecoration(hintText: '예: 암소갈비', border: InputBorder.none))),
              IconButton(onPressed: _search, icon: const Ic('right', color: C.blue)),
            ]),
          ),
        ]),
      );
}
