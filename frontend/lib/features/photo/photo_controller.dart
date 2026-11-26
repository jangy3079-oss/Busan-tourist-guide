// [사진 담당] 사진 화면 상태. 이 폴더(features/photo)는 사진 담당만 수정한다.
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/location.dart';
import '../../data/models/models.dart';
import '../../state/providers.dart';

enum PhotoPhase { idle, scanning, done }

/// 위치 상태(FR-P003): 아직 안 물어봄 / 받음 / 거부·실패(위치 없이 2개 구 전체에서 찾음)
enum PhotoLoc { idle, on, off }

class PhotoState {
  final PhotoPhase phase;
  final String? sample; // 시연용 샘플 종류 (ok|cand|fail). 실제 사진이면 null
  final MatchResult? result;
  final PhotoPlace? selected; // 확정되었거나 후보에서 고른 가게
  final String? narration;
  final bool narrationFailed; // 설명을 못 받아도 정보·지도는 그대로 보여 준다(NFR-P004)
  final Object? error; // 식별·검색 요청 실패(타임아웃·서버·네트워크). 화면이 다시 시도 버튼을 보여 준다(#65)
  final PhotoLoc loc;
  final String? lastQuery; // 이름 검색어(결과 없음 안내용)
  const PhotoState({this.phase = PhotoPhase.idle, this.sample, this.result, this.selected, this.narration, this.narrationFailed = false, this.error, this.loc = PhotoLoc.idle, this.lastQuery});
  PhotoState copy({PhotoPhase? phase, Object? sample = _k, Object? result = _k, Object? selected = _k, Object? narration = _k, bool? narrationFailed, Object? error = _k, PhotoLoc? loc, Object? lastQuery = _k}) => PhotoState(
      phase: phase ?? this.phase, sample: sample == _k ? this.sample : sample as String?, result: result == _k ? this.result : result as MatchResult?,
      selected: selected == _k ? this.selected : selected as PhotoPlace?, narration: narration == _k ? this.narration : narration as String?,
      narrationFailed: narrationFailed ?? this.narrationFailed, error: error == _k ? this.error : error, loc: loc ?? this.loc,
      lastQuery: lastQuery == _k ? this.lastQuery : lastQuery as String?);
}

const _k = Object();

/// 위치 받기. 테스트에서 바꿔 끼울 수 있게 provider 로 둔다. 좌표는 요청에만 쓰고 저장하지 않는다(NFR-P003)
final photoLocationProvider = Provider<Future<LocationResult> Function()>((ref) => requestLocation);

class PhotoController extends Notifier<PhotoState> {
  int _token = 0;
  List<int>? _bytes; // 다시 시도용. 화면을 떠나면(reset) 바로 버린다(NFR-P002)
  String _fileName = 'photo.jpg';

  @override
  PhotoState build() => const PhotoState();

  void reset() { _token++; _bytes = null; state = PhotoState(loc: state.loc); }

  /// API-P01 → (확정이면) API-P02. 실제 사진이면 찍을 때마다 위치를 한 번 묻는다(FR-P003). 거부·실패면 위치 없이 보낸다
  Future<void> identify({List<int>? bytes, String fileName = 'photo.jpg', String? sample}) async {
    final my = ++_token;
    _bytes = bytes;
    _fileName = fileName;
    state = PhotoState(phase: PhotoPhase.scanning, sample: sample, loc: state.loc);
    try {
      double? lat, lng;
      if (bytes != null) {
        final loc = await ref.read(photoLocationProvider)();
        if (my != _token) return;
        lat = loc.lat;
        lng = loc.lng;
        state = state.copy(loc: loc.ok ? PhotoLoc.on : PhotoLoc.off);
      }
      final r = await ref.read(photoRepoProvider).match(bytes, ref.read(langProvider), fileName: fileName, lat: lat, lng: lng, sample: sample);
      if (my != _token) return;
      state = state.copy(phase: PhotoPhase.done, result: r, selected: r.confirmed);
      if (r.confirmed != null) _narrate(my, r.confirmed!);
    } catch (e) {
      if (my == _token) state = state.copy(phase: PhotoPhase.done, error: e, result: null);
    }
  }

  /// 식별 실패 뒤 다시 시도: 같은 사진(또는 샘플)으로 한 번 더
  Future<void> retry() => identify(bytes: _bytes, fileName: _fileName, sample: state.sample);

  /// 후보 카드에서 가게를 고른 경우
  void select(PhotoPlace p) {
    final my = ++_token;
    state = state.copy(selected: p, narration: null, narrationFailed: false);
    _narrate(my, p);
  }

  /// API-P03 이름 직접 검색(FR-P016)
  Future<void> searchByName(String name) async {
    final my = ++_token;
    state = state.copy(error: null, lastQuery: name);
    try {
      final r = await ref.read(photoRepoProvider).search(name, ref.read(langProvider));
      if (my != _token) return;
      state = state.copy(phase: PhotoPhase.done, result: r, selected: null);
    } catch (e) {
      if (my == _token) state = state.copy(error: e);
    }
  }

  Future<void> _narrate(int my, PhotoPlace p) async {
    try {
      final s = await ref.read(photoRepoProvider).narrate(p.contentId, ref.read(langProvider), matchId: state.result?.matchId);
      if (my != _token) return;
      state = state.copy(narration: s, narrationFailed: s.trim().isEmpty);
    } catch (_) {
      if (my == _token) state = state.copy(narrationFailed: true);
    }
  }
}

final photoProvider = NotifierProvider<PhotoController, PhotoState>(PhotoController.new);
