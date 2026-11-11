// [공통] 특보 시트 — 홈 특보 배너와 긴급(화재) 결과에서 함께 연다.
// TODO(전원): 기상청 특보·재난문자 연동은 확장 기능. 지금은 예시 문구.
import 'package:flutter/material.dart';
import '../core/theme.dart';
import 'common.dart';

class AlertBanner extends StatelessWidget {
  final String text, boldPart;
  final EdgeInsets margin;
  const AlertBanner({super.key, required this.boldPart, required this.text, this.margin = EdgeInsets.zero});
  @override
  Widget build(BuildContext context) => Padding(
        padding: margin,
        child: Material(
          color: C.amberT, borderRadius: BorderRadius.circular(rMd),
          child: InkWell(
            borderRadius: BorderRadius.circular(rMd),
            onTap: () => showAlertSheet(context),
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Row(children: [
                const Ic('alert', size: 18, color: C.amber), const SizedBox(width: 8),
                Expanded(child: Text.rich(TextSpan(children: [TextSpan(text: boldPart, style: const TextStyle(fontWeight: FontWeight.w800)), TextSpan(text: text)]), style: const TextStyle(fontSize: 13.5, color: C.amber, height: 1.4))),
                const Ic('right', size: 18, color: C.amber),
              ]),
            ),
          ),
        ),
      );
}

Future<void> showAlertSheet(BuildContext context) => showAppSheet(
      context,
      Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        TagC.amber('기상특보 · 예시'),
        const SizedBox(height: 8),
        const Text('풍랑주의보 · 부산 앞바다', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
        const SizedBox(height: 6),
        const Text('광안리·해운대 해수욕장 입수가 통제되고 있어요. 방파제·갯바위 출입을 피하세요.', style: TextStyle(fontSize: 14, color: C.text2, height: 1.55)),
        const SizedBox(height: 14),
        Container(
          padding: const EdgeInsets.all(14), decoration: cardDeco(color: C.subtle),
          child: const Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text('지금 할 일', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w700)),
            SizedBox(height: 6),
            Text('1. 해안 산책로와 방파제에 가까이 가지 마세요.\n2. 해수욕장 안전요원·방송 안내를 따르세요.\n3. 위험한 상황을 보면 119로 신고하세요.', style: TextStyle(fontSize: 13.5, height: 1.7, color: C.text2)),
            Src('기상청 특보 · 행정안전부 재난문자 연동 (확장) · 문구는 예시'),
          ]),
        ),
        const SizedBox(height: 14),
        Btn('확인', onTap: () => Navigator.of(context).pop()),
      ]),
    );
