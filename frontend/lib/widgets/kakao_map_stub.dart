// [공통] 카카오맵이 없는 환경(테스트·웹 아님). 항상 그림 지도를 쓴다.
import 'package:flutter/widgets.dart';
import '../data/models/models.dart';

final _none = Future.value(false);

Future<bool> loadKakaoMap() => _none;

Widget kakaoMap({required List<MapPoint> pins, required List<MapPoint> path, MapPoint? me, void Function(int index)? onPinTap}) => const SizedBox();
