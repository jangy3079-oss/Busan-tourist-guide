// [공통] 오류·오프라인 안내(#27). 세 기능이 같이 쓴다. 긴급 화면은 이 카드와 별개로 112·119·1330 버튼을 항상 보여 준다(FR-E004).
import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../core/i18n.dart';
import '../core/theme.dart';
import '../data/api/api_client.dart';
import '../state/providers.dart';
import 'common.dart';

/// 예외 → 문구 키. 서버 메시지는 한국어뿐이고 내부 사정이 섞일 수 있어 보여 주지 않는다
String errorKey(Object e) => switch (e) {
      TimeoutException() => 'errTimeout',
      ApiException(:final status) when status == 400 || status == 413 || status == 422 => 'errInput',
      ApiException(:final code) when code == 'AI_TIMEOUT' => 'errTimeout',
      ApiException() => 'errServer',
      FormatException() => 'errServer', // JSON 이 아닌 응답
      _ => 'errNetwork',
    };

class ErrorCard extends ConsumerWidget {
  final Object error;
  final VoidCallback? onRetry;
  const ErrorCard(this.error, {super.key, this.onRetry});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = ref.watch(langProvider);
    return Semantics(
      liveRegion: true,
      child: Container(
        width: double.infinity, padding: const EdgeInsets.all(14), decoration: cardDeco(color: C.amberT, border: C.amberT),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Ic('alert', size: 18, color: C.amber), const SizedBox(width: 8),
            Expanded(child: Text(tr(errorKey(error), l), style: const TextStyle(fontSize: 14, color: C.text2, height: 1.5))),
          ]),
          if (onRetry != null) ...[const SizedBox(height: 10), Btn(tr('retry', l), icon: 'refresh', onTap: onRetry)],
        ]),
      ),
    );
  }
}
