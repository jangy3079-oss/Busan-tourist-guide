// [코스 담당] 코스 화면 상태. 이 폴더(features/course)는 코스 담당만 수정한다.
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/i18n.dart';
import '../../data/models/models.dart';
import '../../data/repositories/repositories.dart';
import '../../state/providers.dart';
import 'course_text.dart';

class ChatMsg {
  final bool me;
  final String text;
  final bool typing;
  const ChatMsg(this.text, {this.me = false, this.typing = false});
}

class CourseState {
  final List<ChatMsg> chat;
  final bool loading;
  final Course? course;
  final CourseNarration? narration; // API-C02
  final bool narrationFailed; // 설명을 못 받아도 코스는 그대로 쓴다
  final Object? error; // 코스 요청 실패(타임아웃·서버·네트워크). 화면이 다시 시도 버튼을 보여 준다
  final Object? editError; // 수정 실패. 지금 코스는 유지
  final EmType? emergencyHint; // 코스 입력에서 긴급 문장 감지 (FR-C013) → 화면이 소비 후 clearHint
  final String? editNotice; // 수정 문장을 명령으로 못 바꿈(EDIT_UNCLEAR) → 화면이 토스트 후 clearHint
  final String lastText;
  final PlacePin? startPlace; // 사용자가 고른 출발 장소 (FR-C014)
  final bool replanning; // 결과 화면에서 조건을 바꿔 다시 짜는 중
  final bool editing; // 대화로 고치는 중
  const CourseState({this.chat = const [], this.loading = false, this.course, this.narration, this.narrationFailed = false, this.error, this.editError, this.emergencyHint, this.editNotice, this.lastText = '', this.startPlace, this.replanning = false, this.editing = false});
  CourseState copy({List<ChatMsg>? chat, bool? loading, Object? course = _k, Object? narration = _k, bool? narrationFailed, Object? error = _k, Object? editError = _k, Object? emergencyHint = _k, Object? editNotice = _k, String? lastText, Object? startPlace = _k, bool? replanning, bool? editing}) => CourseState(
      chat: chat ?? this.chat, loading: loading ?? this.loading, course: course == _k ? this.course : course as Course?,
      narration: narration == _k ? this.narration : narration as CourseNarration?, narrationFailed: narrationFailed ?? this.narrationFailed,
      error: error == _k ? this.error : error, editError: editError == _k ? this.editError : editError, emergencyHint: emergencyHint == _k ? this.emergencyHint : emergencyHint as EmType?,
      editNotice: editNotice == _k ? this.editNotice : editNotice as String?, lastText: lastText ?? this.lastText,
      startPlace: startPlace == _k ? this.startPlace : startPlace as PlacePin?,
      replanning: replanning ?? this.replanning, editing: editing ?? this.editing);
}

const _k = Object();

class CourseController extends Notifier<CourseState> {
  int _token = 0;
  @override
  CourseState build() {
    // 결과를 보는 중에 언어를 바꾸면 설명만 새 언어로 다시 받는다(칩·시간표는 화면에서 바꾼다)
    ref.listen<Lang>(langProvider, (prev, next) {
      if (prev != next && state.course != null) {
        state = state.copy(narration: null, narrationFailed: false);
        _narrate(++_token);
      }
    });
    return const CourseState();
  }

  void reset() { _token++; state = const CourseState(); }

  /// 코스 요청 화면에서 출발 장소를 고르거나(null이면 해제) 미리 정해 둔다. 다음 start()에 쓰인다
  void setStartPlace(PlacePin? p) => state = state.copy(startPlace: p);
  void clearHint() => state = state.copy(emergencyHint: null, editNotice: null, editError: null);

  /// API-C01 → (OK면) API-C02. 결과 화면의 칩·출발 장소 변경은 API-C03(changeAssumption/changeStartPlace)
  Future<void> start(String text, {String? startPlaceId, List<ChatMsg>? seedChat, CourseOverrides? overrides, bool notEmergency = false}) async {
    final my = ++_token;
    _previous = null;
    final startId = startPlaceId ?? state.startPlace?.contentId;
    final lang = ref.read(langProvider);
    _retry = () => start(text, startPlaceId: startPlaceId, seedChat: seedChat, overrides: overrides, notEmergency: notEmergency);
    _continue = () => start(text, startPlaceId: startPlaceId, seedChat: seedChat, overrides: overrides, notEmergency: true);
    state = state.copy(
      chat: seedChat ?? [ChatMsg(text, me: true), ChatMsg(ct('finding', lang), typing: true)],
      loading: true, course: null, narration: null, narrationFailed: false, error: null, emergencyHint: null, lastText: text,
      startPlace: startPlaceId != null ? null : state.startPlace);
    final CourseResult res;
    try {
      res = await ref.read(courseRepoProvider).create(text, lang, startPlaceId: startId, overrides: overrides, notEmergency: notEmergency);
    } catch (e) {
      if (my == _token) state = state.copy(loading: false, error: e, chat: [for (final m in state.chat) if (!m.typing) m]);
      return;
    }
    if (my != _token) return;
    if (res.emergency != null) {
      state = state.copy(loading: false, chat: [], emergencyHint: res.emergency);
      return;
    }
    if (res.notice != null) { // 범위 밖·코스 없음 (FR-C012, C007)
      state = state.copy(loading: false, chat: [ChatMsg(text, me: true), ChatMsg(res.notice!.text)]);
      return;
    }
    state = state.copy(loading: false, course: res.course);
    _narrate(my);
  }

  Future<void> edit(String text, {bool notEmergency = false}) async {
    _continue = () => edit(text, notEmergency: true);
    final my = ++_token;
    final before = state.course;
    state = state.copy(emergencyHint: null, editError: null, lastText: text);
    if (before == null) return;
    state = state.copy(editing: true, replanning: false);
    final CourseResult res;
    try {
      res = await ref.read(courseRepoProvider).edit(before.id, ref.read(langProvider), text: text, notEmergency: notEmergency);
    } catch (e) {
      _editFailed(my, e);
      return;
    }
    if (my != _token) return;
    state = state.copy(editing: false);
    if (res.emergency != null) {
      state = state.copy(emergencyHint: res.emergency);
      return;
    }
    if (res.notice != null) { // EDIT_UNCLEAR: 코스 유지
      state = state.copy(editNotice: res.notice!.text);
      return;
    }
    _previous = before;
    state = state.copy(course: _markChanged(before, res.course!), narration: null, narrationFailed: false);
    _narrate(my);
  }

  /// 결과 화면에서 가정 칩 하나를 바꿈 (FR-C003). 지금 버전에 대한 수정(API-C03)이라 대화로 고친 내용이 유지된다
  Future<void> changeAssumption(String key, String value) => _apply(overrides: CourseOverrides(
        visitDate: key == 'visitDate' ? value : null,
        startTime: key == 'startTime' ? value : null,
        durationMin: key == 'durationMin' ? int.parse(value) : null,
        area: key == 'area' ? (value == 'HAEUNDAE' ? Area.haeundae : Area.gwangalli) : null,
      ));

  /// 결과 화면에서 출발 장소를 고름 (FR-C014). 지금 버전에서 그 장소를 1번으로 고정한 새 버전
  Future<void> changeStartPlace(PlacePin p) {
    state = state.copy(startPlace: p);
    return _apply(startPlaceId: p.contentId);
  }

  Future<void> _apply({CourseOverrides? overrides, String? startPlaceId}) async {
    final before = state.course;
    if (before == null) return;
    final my = ++_token;
    state = state.copy(replanning: true, editing: false, editNotice: null, editError: null);
    final CourseResult res;
    try {
      res = await ref.read(courseRepoProvider).edit(before.id, ref.read(langProvider), overrides: overrides, startPlaceId: startPlaceId);
    } catch (e) {
      _editFailed(my, e);
      return;
    }
    if (my != _token) return;
    if (res.course == null) { // 코스 없음 → 지금 코스 유지하고 안내
      state = state.copy(replanning: false, editNotice: res.notice?.text ?? ct('noCourse', ref.read(langProvider)));
      return;
    }
    _previous = before;
    state = state.copy(replanning: false, course: _markChanged(before, res.course!), narration: null, narrationFailed: false);
    _narrate(my);
  }

  /// 이전 버전과 라벨이 달라진 첫 가정 칩을 "바뀜"으로 표시
  Course _markChanged(Course before, Course after) {
    if (after.changedChipIndex != null || after.assumptions.isEmpty) return after;
    for (var i = 0; i < after.assumptions.length; i++) {
      final old = before.assumptions.where((a) => a.key == after.assumptions[i].key).firstOrNull;
      if (old != null && old.value != after.assumptions[i].value) return after.withChanged(i);
    }
    return after;
  }

  Course? _previous; // 수정 직전 버전 (되돌리기용)

  /// 직전 버전으로 되돌리기. 직전 응답(courseId 포함)을 그대로 쓰므로 API 호출 없음. 이후 수정은 그 courseId 기준.
  void revert() {
    if (_previous == null) return;
    final my = ++_token;
    state = state.copy(course: _previous, narration: null, narrationFailed: false);
    _previous = null;
    _narrate(my);
  }

  bool get canRevert => _previous != null;

  void Function()? _retry, _continue;

  /// 긴급 확인 시트에서 "코스 계속 만들기": 같은 문장을 긴급이 아니라고 알리고 다시 보낸다 (FR-C013)
  void continueCourse() => _continue?.call();

  /// 실패한 코스 요청을 같은 내용으로 다시 보낸다
  void retry() => _retry?.call();

  /// 수정 실패: 지금 코스를 유지하고 이유만 알린다. 출발 장소 선택도 지금 코스의 것으로 되돌린다
  void _editFailed(int my, Object e) {
    if (my == _token) state = state.copy(editing: false, replanning: false, editError: e, startPlace: null);
  }

  Future<void> _narrate(int my) async {
    final c = state.course;
    if (c == null) return;
    try {
      final n = await ref.read(courseRepoProvider).narrate(c.id, ref.read(langProvider));
      if (my == _token) state = state.copy(narration: n);
    } catch (_) {
      if (my == _token) state = state.copy(narrationFailed: true);
    }
  }
}

final courseProvider = NotifierProvider<CourseController, CourseState>(CourseController.new);
