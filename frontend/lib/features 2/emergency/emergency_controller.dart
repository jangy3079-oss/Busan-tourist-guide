// [긴급 담당] 긴급 화면 상태. 이 폴더(features/emergency)는 긴급 담당만 수정한다.
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/i18n.dart';
import '../../data/models/models.dart';
import '../../state/providers.dart';

enum EmPhase { idle, loading, quick, ai }
enum EmSubmit { empty, needArea, started }

class EmState {
  final String text;
  final EmPhase phase;
  final EmType? type;
  final EmQuickResult? quick;
  final EmGuideResult? guide;
  final Area area;
  final bool locGranted, areaPicked, big, needTypeSheet;
  const EmState({this.text = '', this.phase = EmPhase.idle, this.type, this.quick, this.guide, this.area = Area.gwangalli, this.locGranted = true, this.areaPicked = false, this.big = false, this.needTypeSheet = false});
  String? get top => guide?.topContact ?? quick?.topContact;
  EmState copy({String? text, EmPhase? phase, Object? type = _k, Object? quick = _k, Object? guide = _k, Area? area, bool? locGranted, bool? areaPicked, bool? big, bool? needTypeSheet}) => EmState(
        text: text ?? this.text, phase: phase ?? this.phase,
        type: type == _k ? this.type : type as EmType?, quick: quick == _k ? this.quick : quick as EmQuickResult?, guide: guide == _k ? this.guide : guide as EmGuideResult?,
        area: area ?? this.area, locGranted: locGranted ?? this.locGranted, areaPicked: areaPicked ?? this.areaPicked, big: big ?? this.big, needTypeSheet: needTypeSheet ?? this.needTypeSheet);
}

const _k = Object();

class EmergencyController extends Notifier<EmState> {
  int _token = 0; // 이전 요청 응답 무시용 (화면을 떠났다 돌아온 경우)
  @override
  EmState build() {
    // 언어를 바꾸면 상세 안내를 그 언어로 다시 받는다(API-E02는 요청 언어로만 응답)
    ref.listen<Lang>(langProvider, (prev, next) { if (prev != next && state.guide != null && state.type != null) _guide(type: state.type, keepPhase: true); });
    return const EmState();
  }

  /// 긴급 화면 진입. prefill은 코스에서 넘어온 문장 (FR-C013)
  void open({String prefill = ''}) {
    _token++;
    state = state.copy(text: prefill, phase: EmPhase.idle, type: null, quick: null, guide: null, needTypeSheet: false);
  }

  void allowLocation() => state = state.copy(locGranted: true);
  void denyLocation() => state = state.copy(locGranted: false);
  void toggleBig() => state = state.copy(big: !state.big);
  void ackTypeSheet() => state = state.copy(needTypeSheet: false);

  EmSubmit submit(String raw) {
    final text = raw.trim();
    if (text.isEmpty) return EmSubmit.empty;
    state = state.copy(text: text);
    if (!state.locGranted && !state.areaPicked) return EmSubmit.needArea;
    _run(text);
    return EmSubmit.started;
  }

  void pickArea(Area a) {
    state = state.copy(area: a, areaPicked: true);
    _run(state.text.isEmpty ? '아파요' : state.text);
  }

  /// 유형 선택 시트(UI-E04)에서 직접 고른 경우 → API-E02를 type 지정으로 다시 호출 (FR-E008)
  Future<void> chooseType(EmType t) async {
    _token++;
    state = state.copy(phase: EmPhase.loading, type: null, guide: null, needTypeSheet: false);
    await _guide(type: t);
  }

  /// API-E01(연락 버튼 강조, 1초)과 API-E02(상세 안내)를 동시에 보낸다 (SDD SD-E01)
  Future<void> _run(String text) async {
    final my = ++_token;
    state = state.copy(phase: EmPhase.loading, type: null, quick: null, guide: null, needTypeSheet: false);
    final repo = ref.read(emergencyRepoProvider);
    final lang = ref.read(langProvider);
    // E01: 실패해도 연락 버튼은 기본 순서로 이미 보인다(FR-E004)
    repo.quick(text, lang).then((q) {
      if (my == _token && state.guide == null) state = state.copy(phase: EmPhase.quick, quick: q);
    }, onError: (_) {});
    await _guide();
  }

  Future<void> _guide({EmType? type, bool keepPhase = false}) async {
    final my = _token;
    // TODO(공용: 위치 서비스): 위치 허용 시 실제 좌표(lat/lng)를 보낸다. 지금은 대표 지역(area)만 보냄(FR-E002)
    try {
      final g = await ref.read(emergencyRepoProvider).guide(state.text, ref.read(langProvider), area: state.area, type: type);
      if (my != _token) return;
      if (g.needTypeSelection) {
        state = state.copy(phase: EmPhase.idle, needTypeSheet: true);
        return;
      }
      state = state.copy(phase: EmPhase.ai, type: g.type, guide: g);
    } catch (_) {
      if (my != _token) return;
      // 서버 장애: 유형 선택으로 넘기고 연락 버튼은 계속 보인다(NFR-E003, FR-E004)
      if (!keepPhase) state = state.copy(phase: EmPhase.idle, needTypeSheet: true);
    }
  }
}

final emergencyProvider = NotifierProvider<EmergencyController, EmState>(EmergencyController.new);
