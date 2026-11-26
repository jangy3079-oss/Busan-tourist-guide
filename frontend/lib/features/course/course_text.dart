// [코스 담당] 코스 화면 문구. 값은 [한국어, English, 日本語] 순서(#26, #95).
import '../../core/i18n.dart';
import '../../data/models/models.dart';

const Map<String, List<String>> kCourseText = {
  'title': ['코스 만들기', 'Plan a course', 'コースを作る'],
  'sub': ['자연어 코스 추천', 'Just tell us what you want', '文章でコースを提案'],
  'hello': [
    '어떤 하루를 보내고 싶으세요? 날짜·시간·지역·취향을 편하게 말해 주세요. 빠진 조건은 기본값으로 채우고, 결과 위에서 바로 고칠 수 있어요.',
    'What kind of day would you like? Tell us the date, time, area and what you enjoy. We fill in anything missing, and you can change it on the result.',
    'どんな一日を過ごしたいですか?日付・時間・エリア・好みを自由に書いてください。足りない条件は基本値で補い、結果画面で変更できます。'
  ],
  'ex1': ['오후에 광안리에서 바다 보고 회 먹기, 3시간', 'Sea view and raw fish in Gwangalli this afternoon, 3 hours', '午後に広安里で海を見て刺身を食べる、3時間'],
  'ex2': ['비 오는 날 해운대 실내 코스', 'Rainy-day indoor course in Haeundae', '雨の日の海雲台、屋内コース'],
  'ex3': ['아이와 해운대 반나절', 'Half a day in Haeundae with a child', '子どもと海雲台で半日'],
  'scope': ['수영구·해운대구 안의 관광 코스만 만들어요. 예약·결제는 지원하지 않아요.', 'Courses cover Suyeong-gu and Haeundae-gu only. Booking and payment are not supported.', '水営区・海雲台区内の観光コースのみ作成します。予約・決済には対応していません。'],
  'hint': ['예: 오후에 바다 보고 회 먹기', 'e.g. See the sea, then raw fish', '例:午後に海を見て刺身を食べる'],
  'hintEdit': ['예: 2시간으로 줄여줘', 'e.g. Make it 2 hours', '例:2時間に短くして'],
  'finding': ['조건을 정리하고 그 시간에 운영하는 곳을 찾는 중', 'Finding places open at that time', '条件を整理し、その時間に営業している場所を探しています'],
  'result': ['코스 결과', 'Your course', 'コース結果'],
  'made': ['v{0} 코스를 만들었어요 · 바뀐 곳 강조', 'Made v{0} · changes highlighted', 'v{0}を作成しました・変更箇所を強調'],
  'default': ['기본값', 'default', '基本値'],
  'changed': ['바뀜', 'changed', '変更'],
  'fixed': ['출발지 고정', 'start point', '出発地に固定'],
  'uncertain': ['방문 전 확인', 'Check hours before visiting', '訪問前に営業時間を確認'],
  'replan': ['바꾼 조건으로 코스를 다시 짜는 중…', 'Planning again with your change…', '変更した条件でコースを作り直しています…'],
  'editing': ['요청하신 대로 코스를 고치는 중…', 'Updating your course…', 'ご要望に合わせてコースを変更しています…'],
  'relaxed': ['조건 완화: {0}', 'Conditions relaxed: {0}', '条件を緩和: {0}'],
  'revert': ['v{0}로 되돌리기', 'Back to v{0}', 'v{0}に戻す'],
  'writing': ['코스 설명을 쓰는 중…', 'Writing the description…', 'コースの説明を作成中…'],
  'generated': ['AI가 쓴 안내문 · 근거: 한국관광공사 관광정보 · 장소 이름을 누르면 원문과 수집일을 볼 수 있어요', 'Written by AI from Korea Tourism Organization data · Tap a place name for the original text and collection date', 'AIが作成した案内文・根拠:韓国観光公社の観光情報・場所名を押すと原文と収集日を確認できます'],
  'simple': ['간략 설명으로 표시 중이에요', 'Showing a short description', '簡単な説明を表示しています'],
  'noDesc': ['설명을 만들지 못했어요. 코스는 그대로 쓸 수 있어요.', 'We could not write the description. The course itself is ready to use.', '説明を作成できませんでした。コースはそのまま利用できます。'],
  'noCourse': ['바꾼 조건에 맞는 코스를 찾지 못했어요', 'No course matches that change', '変更した条件に合うコースが見つかりませんでした'],
  'ev': ['근거 카드 · 공식 관광정보 원문', 'Source card · official tourism text (Korean)', '根拠カード・公式観光情報の原文(韓国語)'],
  'evLoad': ['원문을 불러오는 중…', 'Loading the original text…', '原文を読み込み中…'],
  'evOverview': ['개요 원문', 'Overview', '概要(原文)'],
  'evHours': ['운영 원문', 'Hours', '営業(原文)'],
  'evSource': ['출처', 'Source', '出典'],
  'close': ['닫기', 'Close', '閉じる'],
  'start': ['출발 장소', 'Start', '出発地'],
  'startAuto': ['자동으로 정하기', 'Choose for me', '自動で決める'],
  'startPick': ['지도에서 고르기', 'Pick on map', '地図で選ぶ'],
  'startTitle': ['출발 장소 고르기', 'Pick a starting place', '出発地を選ぶ'],
  'startSub': ['고른 곳을 1번으로 고정하고 나머지를 이어서 짜요.', 'It becomes stop 1 and we plan the rest.', '選んだ場所を1番に固定し、続きを作ります。'],
  'startLoad': ['장소를 불러오는 중…', 'Loading places…', '場所を読み込み中…'],
  'startTap': ['핀을 눌러 고르세요', 'Tap a pin', 'ピンを押して選択'],
  'startNone': ['장소를 골라 주세요', 'Choose a place', '場所を選んでください'],
  'startGo': ['{0}에서 출발', 'Start from {0}', '{0}から出発'],
  'chg': ['{0} 바꾸기', 'Change {0}', '{0}を変更'],
  'chgDefault': ['요청에 없어서 기본값({0})으로 짰어요. 원하는 값을 고르세요.', 'Not in your request, so we used {0}. Pick what you want.', 'ご要望になかったため基本値({0})で作成しました。希望の値を選んでください。'],
  'chgNow': ['지금: {0}', 'Now: {0}', '現在: {0}'],
  'chgArea': ['권역을 바꾸면 그 권역에 없는 장소는 바뀔 수 있어요.', 'Places outside the new area may change.', 'エリアを変えると、そのエリアにない場所は変わることがあります。'],
  'chgKeep': ['지금 코스에서 이 조건만 바꿔 새 버전을 만들어요. 대화로 고친 내용은 그대로 유지돼요.', 'Only this condition changes. Your earlier edits stay.', 'この条件だけを変えて新しい版を作ります。会話で変更した内容は残ります。'],
  'chgNone': ['바꿀 값을 골라 주세요', 'Choose a new value', '変更する値を選んでください'],
  'chgGo': ['이 조건으로 다시 짜기', 'Plan again', 'この条件で作り直す'],
  'k.visitDate': ['방문일', 'date', '訪問日'], 'k.startTime': ['출발 시각', 'start time', '出発時刻'], 'k.durationMin': ['소요 시간', 'duration', '所要時間'], 'k.area': ['권역', 'area', 'エリア'], 'k.indoor': ['실내', 'indoor', '屋内'],
  'today': ['오늘', 'Today', '今日'], 'tomorrow': ['내일', 'Tomorrow', '明日'],
  'hours': ['{0}시간', '{0} h', '{0}時間'], 'minutes': ['{0}분', '{0} min', '{0}分'],
  'from': ['{0} 출발', 'from {0}', '{0}出発'],
  'GWANGALLI': ['광안리', 'Gwangalli', '広安里'], 'HAEUNDAE': ['해운대', 'Haeundae', '海雲台'], 'ALL': ['광안리·해운대 전체', 'Gwangalli & Haeundae', '広安里・海雲台全体'],
  'indoor': ['실내 위주', 'Indoor', '屋内中心'],
  'WALK': ['도보 {0}분', '{0} min walk', '徒歩{0}分'], 'TRANSIT': ['대중교통 {0}분', '{0} min by transit', '公共交通{0}分'], 'TAXI': ['택시 {0}분', '{0} min by taxi', 'タクシー{0}分'],
  'cat.관광지': ['관광지', 'Sightseeing', '観光地'], 'cat.문화시설': ['문화시설', 'Culture', '文化施設'], 'cat.쇼핑': ['쇼핑', 'Shopping', 'ショッピング'], 'cat.음식점': ['음식점', 'Restaurant', '飲食店'], 'cat.카페': ['카페', 'Cafe', 'カフェ'],
};

const _weekdays = [
  ['월', '화', '수', '목', '금', '토', '일'],
  ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'],
  ['月', '火', '水', '木', '金', '土', '日']
];

String ct(String k, Lang l, [List<Object> args = const []]) => trIn(kCourseText, k, l, args);

String dateText(DateTime d, DateTime now, Lang l) {
  final diff = DateTime(d.year, d.month, d.day).difference(DateTime(now.year, now.month, now.day)).inDays;
  if (diff == 0) return ct('today', l);
  if (diff == 1) return ct('tomorrow', l);
  return '${d.month}/${d.day}(${_weekdays[l.i][d.weekday - 1]})';
}

String durationText(int min, Lang l) => min % 60 == 0 ? ct('hours', l, [min ~/ 60]) : ct('minutes', l, [min]);

/// 칩 라벨은 서버가 만든 언어로 오므로, 화면 언어를 바꿔도 맞게 값에서 다시 만든다. 모르는 키는 서버 라벨
String assumptionText(CourseAssumption a, Lang l, DateTime now) {
  switch (a.key) {
    case 'visitDate':
      final d = DateTime.tryParse(a.value);
      return d == null ? a.label : dateText(d, now, l);
    case 'startTime':
      return ct('from', l, [a.value]);
    case 'durationMin':
      final m = int.tryParse(a.value);
      return m == null ? a.label : durationText(m, l);
    case 'area':
      return ct(kCourseText.containsKey(a.value) ? a.value : 'ALL', l);
    case 'indoor':
      return ct('indoor', l);
  }
  return a.label;
}

/// 정차지 한 줄: 유형 · 이동. 서버 값이 없으면(목업) 원래 문구
String stopText(CourseStop s, Lang l) {
  if (s.category == null) return s.desc;
  final cat = kCourseText.containsKey('cat.${s.category}') ? ct('cat.${s.category}', l) : s.category!;
  final move = s.moveMode == null || !kCourseText.containsKey(s.moveMode) ? null : ct(s.moveMode!, l, [s.moveMin ?? 0]);
  return [cat, ?move].join(' · ');
}
