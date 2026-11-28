// [긴급 담당] 긴급 화면 상태. 이 폴더(features/emergency)는 긴급 담당만 수정한다.
// 좌표·입력 문장은 이 상태(메모리)에만 두고 저장하지 않는다(NFR-E004).
import 'dart:math' as math;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/i18n.dart';
import '../../core/location.dart';
import '../../data/mock_data.dart';
import '../../data/models/models.dart';
import '../../state/providers.dart';

enum EmPhase { idle, loading, quick, ai }
enum EmSubmit { empty, needArea, started }
/// 위치 상태 (FR-E002). unknown: 아직 묻지 않음, asking: 브라우저에 묻는 중, granted: 좌표 있음, denied: 거부·시간 초과·미지원 → 지역 선택
enum EmLoc { unknown, asking, granted, denied }

class EmState {
  final String text;
  final EmPhase phase;
  final EmType? type;
  final EmQuickResult? quick;
  final EmGuideResult? guide;
  final Area area;
  final EmLoc loc;
  final double? lat, lng;
  final Object? error; // API-E02 실패(서버 오류·시간 초과) → 오류 카드 + 연락 버튼 + 유형 버튼
  final bool areaPicked, big, needArea, userPickedType;
  const EmState({this.text = '', this.phase = EmPhase.idle, this.type, this.quick, this.guide, this.area = Area.gwangalli, this.loc = EmLoc.unknown, this.lat, this.lng, this.error, this.areaPicked = false, this.big = false, this.needArea = false, this.userPickedType = false});
  String? get top => guide?.topContact ?? quick?.topContact;
  bool get hasCoords => lat != null && lng != null && !areaPicked;
  EmState copy({String? text, EmPhase? phase, Object? type = _k, Object? quick = _k, Object? guide = _k, Area? area, EmLoc? loc, Object? lat = _k, Object? lng = _k, Object? error = _k, bool? areaPicked, bool? big, bool? needArea, bool? userPickedType}) => EmState(
        text: text ?? this.text, phase: phase ?? this.phase,
        type: type == _k ? this.type : type as EmType?, quick: quick == _k ? this.quick : quick as EmQuickResult?, guide: guide == _k ? this.guide : guide as EmGuideResult?,
        area: area ?? this.area, loc: loc ?? this.loc, lat: lat == _k ? this.lat : lat as double?, lng: lng == _k ? this.lng : lng as double?, error: error == _k ? this.error : error,
        areaPicked: areaPicked ?? this.areaPicked, big: big ?? this.big, needArea: needArea ?? this.needArea, userPickedType: userPickedType ?? this.userPickedType);
}

const _k = Object();

/// 위치 한 번 받기. 테스트에서 바꿔 끼운다
final emLocateProvider = Provider<Future<LocationResult> Function()>((ref) => () => requestLocation());

/// 좌표에서 가까운 대표 지역(지도 범위·지역 표시용). 서버 판정(대상 지역 밖 등)은 좌표로 따로 한다
Area nearestArea(double lat, double lng) {
  double d(Area a) => math.pow(kAreas[a]!.lat - lat, 2) + math.pow(kAreas[a]!.lng - lng, 2).toDouble();
  return d(Area.haeundae) < d(Area.gwangalli) ? Area.haeundae : Area.gwangalli;
}

class EmergencyController extends Notifier<EmState> {
  int _token = 0; // 이전 요청 응답 무시용 (화면을 떠났다 돌아온 경우)
  @override
  EmState build() {
    // 언어를 바꾸면 상세 안내를 그 언어로 다시 받는다(API-E02는 요청 언어로만 응답).
    // 사용자가 유형을 직접 고른 경우에만 type 을 유지하고, 일반 분류 결과였다면 type=null 로 보내 슬롯을 유지한다(#208).
    ref.listen<Lang>(langProvider, (prev, next) {
      if (prev != next && state.guide != null && state.type != null) {
        _guide(type: state.userPickedType ? state.type : null, keepPhase: true);
      }
    });
    return const EmState();
  }

  /// 긴급 화면 진입. prefill은 코스에서 넘어온 문장 (FR-C013)
  void open({String prefill = ''}) {
    _token++;
    state = state.copy(text: prefill, phase: EmPhase.idle, type: null, quick: null, guide: null, error: null, needArea: false, userPickedType: false);
  }

  void toggleBig() => state = state.copy(big: !state.big);
  void ackNeedArea() => state = state.copy(needArea: false);

  /// 입력 화면의 '위치 허용' 버튼: 제출 전에 미리 묻는다. 실패하면 false → 지역 선택 화면으로
  Future<bool> askLocation() => _locate();

  /// 지역 직접 선택으로 바꾸기 (위치를 쓰지 않음)
  void useArea() => state = state.copy(loc: EmLoc.denied);

  EmSubmit submit(String raw) {
    final text = raw.trim();
    if (text.isEmpty) return EmSubmit.empty;
    state = state.copy(text: text);
    // 이미 위치를 쓸 수 없다고 확인했고 지역도 안 골랐으면 바로 지역 선택(FR-E002)
    if (state.loc == EmLoc.denied && !state.areaPicked) return EmSubmit.needArea;
    _run(text);
    return EmSubmit.started;
  }

  void pickArea(Area a) {
    state = state.copy(area: a, areaPicked: true, loc: EmLoc.denied, needArea: false);
    if (state.text.isEmpty) return; // 입력 전에 지역만 고른 경우
    _run(state.text);
  }

  /// 유형 선택 화면(UI-E04)에서 직접 고른 경우 → API-E02를 type 지정으로 다시 호출 (FR-E008)
  Future<void> chooseType(EmType t) async {
    _token++;
    state = state.copy(phase: EmPhase.loading, type: null, guide: null, error: null, userPickedType: true);
    await _guide(type: t);
  }

  /// 오류 카드의 '다시 시도'
  Future<void> retry() => _run(state.text);

  /// API-E01(연락 버튼 강조, 1초)과 API-E02(상세 안내)를 동시에 보낸다 (SDD SD-E01).
  /// E01은 위치를 기다리지 않는다. E02는 좌표(허용) 또는 지역(거부)이 정해진 뒤 보낸다
  Future<void> _run(String text) async {
    final my = ++_token;
    state = state.copy(phase: EmPhase.loading, type: null, quick: null, guide: null, error: null, needArea: false, userPickedType: false);
    final repo = ref.read(emergencyRepoProvider);
    final lang = ref.read(langProvider);
    // E01: 실패해도 연락 버튼은 기본 순서로 이미 보인다(FR-E004)
    repo.quick(text, lang).then((q) {
      if (my == _token && state.guide == null) state = state.copy(phase: state.phase == EmPhase.loading ? EmPhase.quick : state.phase, quick: q);
    }, onError: (_) {});
    if (!state.areaPicked && !state.hasCoords) {
      final ok = await _locate();
      if (my != _token) return;
      if (!ok) {
        // 거부·시간 초과·미지원 → 지역 선택(UI-E05). 고르면 pickArea가 다시 보낸다
        state = state.copy(needArea: true);
        return;
      }
    }
    await _guide();
  }

  Future<bool> _locate() async {
    state = state.copy(loc: EmLoc.asking);
    final r = await ref.read(emLocateProvider)();
    if (r.ok) {
      state = state.copy(loc: EmLoc.granted, lat: r.lat, lng: r.lng, area: nearestArea(r.lat!, r.lng!), areaPicked: false);
      return true;
    }
    state = state.copy(loc: EmLoc.denied, lat: null, lng: null);
    return false;
  }

  Future<void> _guide({EmType? type, bool keepPhase = false}) async {
    final my = _token;
    final s = state;
    try {
      final g = await ref.read(emergencyRepoProvider).guide(s.text, ref.read(langProvider),
          lat: s.hasCoords ? s.lat : null, lng: s.hasCoords ? s.lng : null, area: s.hasCoords ? null : s.area, type: type);
      if (my != _token) return;
      if (g.needTypeSelection) {
        state = state.copy(phase: EmPhase.idle);
        return;
      }
      state = state.copy(phase: EmPhase.ai, type: g.type, guide: g, error: null);
    } catch (e) {
      if (my != _token) return;
      // 서버 장애·시간 초과: 오류 카드와 유형 버튼을 보여 주고 연락 버튼은 계속 보인다(NFR-E003, FR-E004, FR-E008)
      if (!keepPhase) state = state.copy(phase: EmPhase.idle, error: e);
    }
  }
}

final emergencyProvider = NotifierProvider<EmergencyController, EmState>(EmergencyController.new);
