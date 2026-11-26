// [공통·연동] 기능 사이를 잇는 코드만 둔다. 세 연동(①사진→코스 ②코스→긴급 ③긴급→코스)의 이음매.
// 수정하려면 연결되는 두 기능의 담당자가 함께 본다. (SDD SD-X01)
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../core/i18n.dart';
import '../core/theme.dart';
import '../data/models/models.dart';
import '../state/providers.dart';
import '../widgets/common.dart';
import 'course/course_controller.dart';
import 'emergency/emergency_controller.dart';
import 'photo/photo_controller.dart';

/// ② 코스 → 긴급 (FR-C013). 위급·화재·위협은 바로 전환, 그 밖은 확인 시트.
Future<void> handleCourseEmergency(BuildContext context, WidgetRef ref, EmType type) async {
  final l = ref.read(langProvider);
  final text = ref.read(courseProvider).lastText;
  ref.read(courseProvider.notifier).clearHint();
  if (type == EmType.urgent || type == EmType.fire || type == EmType.threat) {
    toast(context, tr('bUrgent', l));
    await Future<void>.delayed(const Duration(milliseconds: 700));
    if (!context.mounted) return;
    _openEmergency(context, ref, text, submit: true);
    return;
  }
  await showAppSheet(
    context,
    Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text(tr('bAsk', l), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
      const SizedBox(height: 6),
      Text(tr('bAskSub', l, [text]), style: const TextStyle(fontSize: 14, color: C.text2, height: 1.55)),
      const SizedBox(height: 16),
      Btn(tr('bGo', l), icon: 'alert', kind: BtnKind.red, onTap: () {
        Navigator.of(context).pop();
        _openEmergency(context, ref, text, submit: false);
      }),
      const SizedBox(height: 8),
      Btn(tr('bStay', l), onTap: () {
        Navigator.of(context).pop();
        ref.read(courseProvider.notifier).continueCourse();
      }),
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

/// ① 사진 → 코스 (FR-P015, FR-C014): 식별된 가게를 출발 장소로 채우고, 하고 싶은 것을 입력하길 기다린다(#226)
void photoToCourse(BuildContext context, WidgetRef ref, PhotoPlace place) {
  final l = ref.read(langProvider);
  ref.read(courseProvider.notifier).fromPlace(PlacePin(place.contentId, place.name, place.category, place.lat, place.lng), tr('bFixed', l, [place.name]));
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
