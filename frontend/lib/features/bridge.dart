// [공통·연동] 기능 사이를 잇는 코드만 둔다. 세 연동(①사진→코스 ②코스→긴급 ③긴급→코스)의 이음매.
// 수정하려면 연결되는 두 기능의 담당자가 함께 본다. (SDD SD-X01)
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../core/theme.dart';
import '../data/models/models.dart';
import '../widgets/common.dart';
import 'course/course_controller.dart';
import 'emergency/emergency_controller.dart';
import 'photo/photo_controller.dart';

/// ② 코스 → 긴급 (FR-C013). 위급·화재·위협은 바로 전환, 그 밖은 확인 시트.
Future<void> handleCourseEmergency(BuildContext context, WidgetRef ref, EmType type) async {
  final text = ref.read(courseProvider).lastText;
  ref.read(courseProvider.notifier).clearHint();
  if (type == EmType.urgent || type == EmType.fire || type == EmType.threat) {
    toast(context, '위급 문장 감지 → 긴급 안내로 바로 전환');
    await Future<void>.delayed(const Duration(milliseconds: 700));
    if (!context.mounted) return;
    _openEmergency(context, ref, text, submit: true);
    return;
  }
  await showAppSheet(
    context,
    Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      const Text('긴급 안내로 이동할까요?', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
      const SizedBox(height: 6),
      Text('"$text" 문장에서 도움이 필요한 상황이 보였어요. 이 문장은 코스 대화에 남지 않고, 긴급 입력에 미리 채워져요.', style: const TextStyle(fontSize: 14, color: C.text2, height: 1.55)),
      const SizedBox(height: 16),
      Btn('긴급 안내 보기', icon: 'alert', kind: BtnKind.red, onTap: () {
        Navigator.of(context).pop();
        _openEmergency(context, ref, text, submit: false);
      }),
      const SizedBox(height: 8),
      Btn('코스 계속 만들기', onTap: () => Navigator.of(context).pop()),
    ]),
  );
}

void _openEmergency(BuildContext context, WidgetRef ref, String text, {required bool submit}) {
  final em = ref.read(emergencyProvider.notifier)..open(prefill: text);
  if (!submit) {
    context.go('/emergency');
    return;
  }
  final r = em.submit(text);
  context.go(r == EmSubmit.needArea ? '/emergency/area' : '/emergency/result');
}

/// 긴급 화면 열기 (SOS·홈 카드·특보 등 공통 진입)
void openEmergency(BuildContext context, WidgetRef ref) {
  ref.read(emergencyProvider.notifier).open();
  context.go('/emergency');
}

/// ① 사진 → 코스 (FR-P015, FR-C014): 식별된 가게를 1번 정차지로 고정해 코스 시작
void photoToCourse(BuildContext context, WidgetRef ref, PhotoPlace place) {
  ref.read(courseProvider.notifier).start(
    '${place.name}에서 출발',
    startPlaceId: place.contentId,
    seedChat: [ChatMsg('${place.name}에서 출발', me: true), const ChatMsg('이 가게를 1번으로 고정했어요. 몇 시간 정도 둘러볼까요?'), const ChatMsg('2시간 걸을래요', me: true), const ChatMsg('조건을 정리하고 지금 운영 중인 곳을 찾는 중', typing: true)],
  );
  context.go('/course');
}

/// ③ 긴급 → 코스 (FR-E018): 입력한 문장은 넘기지 않는다
void emergencyToCourse(BuildContext context, WidgetRef ref) {
  ref.read(courseProvider.notifier).reset();
  context.go('/course');
}

void startPhoto(BuildContext context, WidgetRef ref) {
  ref.read(photoProvider.notifier).reset();
  context.go('/photo');
}
