// [공통] 앱 진입점. 넓은 화면에서는 폰 폭(460px)으로 가운데 정렬한다.
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'core/theme.dart';
import 'router.dart';

void main() => runApp(const ProviderScope(child: App()));

class App extends StatelessWidget {
  const App({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp.router(
        title: '부산 안심여행',
        debugShowCheckedModeBanner: false,
        theme: buildTheme(),
        routerConfig: router,
        builder: (context, child) => ColoredBox(
          color: C.bg,
          child: Center(child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 460), child: ClipRect(child: child))),
        ),
      );
}
