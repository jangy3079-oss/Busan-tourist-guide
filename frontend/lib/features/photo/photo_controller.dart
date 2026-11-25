// [사진 담당] 사진 화면 상태. 이 폴더(features/photo)는 사진 담당만 수정한다.
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../data/models/models.dart';
import '../../state/providers.dart';

enum PhotoPhase { idle, scanning, done }

class PhotoState {
  final PhotoPhase phase;
  final String? sample; // 시연용 샘플 종류 (ok|cand|fail). 실제 사진이면 null
  final MatchResult? result;
  final PhotoPlace? selected; // 확정되었거나 후보에서 고른 가게
  final String? narration;
  const PhotoState({this.phase = PhotoPhase.idle, this.sample, this.result, this.selected, this.narration});
  PhotoState copy({PhotoPhase? phase, Object? sample = _k, Object? result = _k, Object? selected = _k, Object? narration = _k}) => PhotoState(
      phase: phase ?? this.phase, sample: sample == _k ? this.sample : sample as String?, result: result == _k ? this.result : result as MatchResult?,
      selected: selected == _k ? this.selected : selected as PhotoPlace?, narration: narration == _k ? this.narration : narration as String?);
}

const _k = Object();

class PhotoController extends Notifier<PhotoState> {
  int _token = 0;
  @override
  PhotoState build() => const PhotoState();

  void reset() { _token++; state = const PhotoState(); }

  /// API-P01 → (확정이면) API-P02. 위치는 허용 시 1회만(FR-P003, TODO: 위치 서비스 연결 후 lat/lng 전달)
  Future<void> identify({List<int>? bytes, String fileName = 'photo.jpg', String? sample}) async {
    final my = ++_token;
    state = PhotoState(phase: PhotoPhase.scanning, sample: sample);
    final r = await ref.read(photoRepoProvider).match(bytes, ref.read(langProvider), fileName: fileName, sample: sample);
    if (my != _token) return;
    state = state.copy(phase: PhotoPhase.done, result: r, selected: r.confirmed);
    if (r.confirmed != null) _narrate(my, r.confirmed!);
  }

  /// 후보 카드에서 가게를 고른 경우
  void select(PhotoPlace p) {
    final my = ++_token;
    state = state.copy(selected: p, narration: null);
    _narrate(my, p);
  }

  Future<void> searchByName(String name) async {
    final my = ++_token;
    final r = await ref.read(photoRepoProvider).search(name, ref.read(langProvider));
    if (my != _token) return;
    state = state.copy(phase: PhotoPhase.done, result: r, selected: null);
  }

  Future<void> _narrate(int my, PhotoPlace p) async {
    final s = await ref.read(photoRepoProvider).narrate(p.contentId, ref.read(langProvider), matchId: state.result?.matchId);
    if (my != _token) return;
    state = state.copy(narration: s);
  }
}

final photoProvider = NotifierProvider<PhotoController, PhotoState>(PhotoController.new);
