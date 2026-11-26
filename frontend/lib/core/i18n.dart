// [공통] 다국어 (ko / en / ja). 프로토타입에서 번역이 있는 범위(긴급·한국어 카드)를 그대로 옮김.
// 공통·홈 문구는 여기(L)에, 기능 화면 문구는 각 기능 폴더의 *_text.dart 에 같은 모양의 맵으로 두고 trIn() 으로 읽는다(#26).
// 세 담당이 한 파일을 같이 고치지 않게 하기 위해서다. 값은 [한국어, English, 日本語] 순서.
enum Lang { ko, en, ja }

extension LangX on Lang {
  int get i => index;
  String get code => name;
  String get label => const {Lang.ko: '한', Lang.en: 'EN', Lang.ja: '日'}[this]!;
}

const Map<String, List<String>> L = {
  'eTitle': ['긴급 도움', 'Emergency help', '緊急サポート'],
  'eSub': ['외국인 긴급상황 안내', 'Help for visitors in Busan', '外国人旅行者のための案内'],
  'eAsk': ['무슨 일이 있나요?', "What's happening?", '何がありましたか?'],
  'eAskSub': ['편한 언어로 지금 상황을 적어 주세요.', 'Describe it in any language you like.', '話しやすい言語で状況を書いてください。'],
  'ePh': ['예: 친구가 열이 나요', 'e.g. My friend has a fever', '例：友達が熱を出しました'],
  'eFind': ['도움 찾기', 'Find help', '助けを探す'],
  'locOn': ['현재 위치 사용 중', 'Using your location', '現在地を使用中'],
  'locAsk': ['가까운 도움처를 찾으려면 위치가 필요해요', 'Your location helps us find help nearby', '近くの支援先を探すには位置情報が必要です'],
  'allow': ['위치 허용', 'Allow', '許可する'],
  'choose': ['지역 직접 선택', 'Choose area', '地域を選ぶ'],
  'store': [
    '긴급 안내가 시작되면 상황 유형·대략 위치·시각이 부산 관광안전 통계에 저장돼요. 일반 대화는 저장하지 않아요.',
    'When emergency help starts, the situation type, approximate location and time are saved for Busan visitor-safety statistics. Normal chats are not saved.',
    '緊急案内が始まると、状況の種類・おおよその位置・時刻が釜山の観光安全統計に保存されます。通常の会話は保存されません。'
  ],
  'c119': ['구급·화재', 'Ambulance · Fire', '救急・火災'],
  'c112': ['경찰', 'Police', '警察'],
  'c1330': ['관광통역 24시간', 'Travel help 24h', '観光通訳 24時間'],
  'quick': ['빠른 안내 · 0.6초', 'Quick result · 0.6s', '速報 · 0.6秒'],
  'ai': ['AI 확인 완료', 'Checked by AI', 'AI 確認済み'],
  'outOfArea': ['광안리·해운대 밖이라 주변 도움처는 안내하지 못해요. 위 번호로 바로 연락하세요.', 'You are outside Gwangalli/Haeundae, so we cannot list nearby help. Please call a number above.', '広安里・海雲台の外のため周辺の支援先は案内できません。上の番号に連絡してください。'],
  'analyzing': ['상황을 자세히 살펴보는 중…', 'Looking at your situation more closely…', '状況を詳しく確認しています…'],
  'helpNow': ['지금 운영 중인 도움처', 'Open now near you', '営業中の支援先'],
  'helpSub': ['거리순 · 1km 안', 'By distance · within 1 km', '距離順・1km以内'],
  'todo': ['지금 할 일', 'What to do now', '今すべきこと'],
  'cardT': ['한국어 카드', 'Korean card', '韓国語カード'],
  'cardHint': ['약사·직원에게 보여 주세요', 'Show this to staff nearby', '近くのスタッフに見せてください'],
  'full': ['전체 화면', 'Full screen', '全画面'],
  'showThis': ['이 화면을 보여 주세요 · Please read this', '이 화면을 보여 주세요 · Please read this', '이 화면을 보여 주세요 · こちらをご覧ください'],
  'bigger': ['글자 크게', 'Larger text', '文字を大きく'],
  'close': ['닫기', 'Close', '閉じる'],
  'copyLoc': ['위치 복사', 'Copy location', '位置をコピー'],
  'nowLoc': ['현재 위치', 'Current location', '現在地'],
  'silent': ['소리 없이 신고', 'Report silently', '声を出さずに通報'],
  'silentSub': ['말하기 어렵다면 112에 문자로 신고할 수 있어요', "Can't talk? You can text 112 instead", '話せない時は112にメッセージで通報できます'],
  'embassy': ['우리나라 대사관·영사관', 'Your embassy or consulate', '自国の大使館・領事館'],
  'embassySub': ['여권 분실·재발급 안내', 'Lost passport · reissue', 'パスポート紛失・再発行'],
  'tpol': ['관광경찰', 'Tourist police', '観光警察'],
  'tpolSub': ['외국어 상담 · 관광 불편·피해 도움', 'Help in foreign languages', '外国語で相談できます'],
  'nonT': ['긴급 상황은 아닌 것 같아요', "This doesn't look like an emergency", '緊急ではないようです'],
  'nonS': ['관광 관련 문의는 1330이 24시간 통역과 함께 도와드려요.', '1330 helps travellers 24/7 with interpretation.', '観光の問い合わせは1330が24時間、通訳付きで対応します。'],
  'complain': ['택시·쇼핑 불편 신고', 'Report taxi or shopping problems', 'タクシー・買い物の苦情'],
  'complainSub': ['1330 관광불편신고로 연결', 'Connect to 1330 complaint desk', '1330 観光苦情窓口へ'],
  'findCourse': ['관광 코스 찾기', 'Find a tour course', '観光コースを探す'],
  'findCourseSub': ['코스 추천 화면으로 이동 · 입력한 문장은 넘기지 않아요', 'Opens course planner · your text is not passed on', 'コース作成へ移動・入力文は引き継ぎません'],
  'typeT': ['어떤 상황인지 골라 주세요', 'Which fits best?', '近い状況を選んでください'],
  'typeS': ['문장만으로는 판단하기 어려워요. 가장 가까운 것을 눌러 주세요.', 'We could not tell from the text. Pick the closest one.', '文章だけでは判断できませんでした。近いものを選んでください。'],
  'areaT': ['지금 어디 근처에 있나요?', 'Where are you now?', 'いまどのあたりですか?'],
  'areaS': ['위치 권한 없이도 지역 기준으로 안내해요.', 'We can guide you by area without location access.', '位置情報なしでも地域を基準に案内します。'],
  'data': [
    '국립중앙의료원·경찰청·관광공사 공개 데이터 · 2026-10-12 기준 · 방문 전 전화로 확인하세요',
    'Public data (NMC · Police · KTO) as of 2026-10-12 · Call before you go',
    '公開データ（国立中央医療院・警察庁・観光公社）2026-10-12時点・訪問前に電話で確認を'
  ],
  'areaSel': ['지역 선택', 'Area chosen', '地域選択'],
  // ---- 공통
  'back': ['뒤로', 'Back', '戻る'],
  'send': ['보내기', 'Send', '送信'],
  'photo': ['사진', 'Photo', '写真'],
  'retry': ['다시 시도', 'Try again', '再試行'],
  'limit': ['500자까지 쓸 수 있어요', 'Up to 500 characters', '500文字まで入力できます'],
  'errTimeout': ['응답이 늦어지고 있어요. 잠시 후 다시 시도해 주세요.', 'This is taking too long. Please try again in a moment.', '応答に時間がかかっています。しばらくしてからもう一度お試しください。'],
  'errServer': ['지금은 처리할 수 없어요. 잠시 후 다시 시도해 주세요.', 'We cannot handle this right now. Please try again in a moment.', '現在処理できません。しばらくしてからもう一度お試しください。'],
  'errNetwork': ['인터넷 연결을 확인해 주세요.', 'Please check your internet connection.', 'インターネット接続を確認してください。'],
  'errInput': ['입력한 내용을 확인해 주세요.', 'Please check what you entered.', '入力内容を確認してください。'],
  // ---- 홈
  'app': ['부산 안심여행', 'Busan Safe Trip', '釜山あんしん旅'],
  'appSub': ['광안리·해운대 관광 안내', 'Gwangalli & Haeundae guide', '広安里・海雲台の観光案内'],
  'hAlert': ['풍랑주의보(예시)', 'High-wave advisory (sample)', '波浪注意報(例)'],
  'hAlertSub': [' · 광안리·해운대 해수욕장 입수 통제 중', ' · No swimming at Gwangalli and Haeundae', ' ・広安里・海雲台海水浴場は遊泳禁止'],
  'hAsk1': ['광안리·해운대에서', 'In Gwangalli & Haeundae', '広安里・海雲台で'],
  'hAsk2': ['무엇을 도와드릴까요?', 'how can we help?', '何をお手伝いしましょうか?'],
  'hAskSub': ['공식 관광 데이터를 근거로 한국어·English·日本語로 안내해요.', 'Based on official tourism data, in 한국어 · English · 日本語.', '公式観光データをもとに、한국어・English・日本語でご案内します。'],
  'hCourse': ['코스 만들기', 'Plan a course', 'コースを作る'],
  'hCourseSub': ['날짜·시간·취향을 말하면 그 시간에 운영하는 곳으로 코스를 짜요', 'Tell us when and what you like. We pick places open at that time', '日時や好みを伝えると、その時間に営業している場所でコースを作ります'],
  'hPhoto': ['간판 사진으로 찾기', 'Find by sign photo', '看板の写真で探す'],
  'hPhotoSub': ['한글 간판 한 장으로 가게 정보 확인', 'One photo of a Korean sign shows the place', 'ハングルの看板1枚でお店の情報を確認'],
  'hEm': ['긴급 도움', 'Emergency help', '緊急サポート'],
  'hEmSub': ['약국·응급실·경찰, 한국어 카드', 'Pharmacy, ER, police, Korean card', '薬局・救急・警察、韓国語カード'],
  'hTry': ['이렇게 물어보세요', 'Try asking', 'こんなふうに聞いてみてください'],
  'hEx1': ['오후에 광안리에서 바다 보고 회 먹기, 3시간', 'Sea view and raw fish in Gwangalli this afternoon, 3 hours', '午後に広安里で海を見て刺身を食べる、3時間'],
  'hEx2': ['비 오는 날 해운대 실내 코스', 'Rainy-day indoor course in Haeundae', '雨の日の海雲台、屋内コース'],
  'hEx3': ['이 간판 가게가 어디예요?', 'Which place is this sign?', 'この看板のお店はどこですか?'],
  'hPriv': ['일반 대화와 사진은 저장하지 않아요. 긴급 안내가 시작된 경우에만 상황 유형·대략 위치·시각이 관광안전 통계에 저장돼요.', 'Normal chats and photos are not saved. Only when emergency help starts, the situation type, approximate location and time are saved for safety statistics.', '通常の会話と写真は保存しません。緊急案内が始まった場合のみ、状況の種類・おおよその位置・時刻が観光安全統計に保存されます。'],
  'hHint': ['무엇이든 물어보세요', 'Ask us anything', '何でも聞いてください'],
  // ---- 기능 연동(bridge)
  'bUrgent': ['위급 문장 감지 → 긴급 안내로 바로 전환', 'Emergency detected → switching to emergency help', '緊急の内容を検知 → 緊急案内に切り替えます'],
  'bAsk': ['긴급 안내로 이동할까요?', 'Go to emergency help?', '緊急案内に移動しますか?'],
  'bAskSub': ['"{0}" 문장에서 도움이 필요한 상황이 보였어요. 이 문장은 코스 대화에 남지 않고, 긴급 입력에 미리 채워져요.', '"{0}" sounds like you may need help. It will not stay in the course chat and will be filled in for emergency help.', '「{0}」から助けが必要な状況が見られました。この文はコースの会話には残らず、緊急の入力欄に入ります。'],
  'bGo': ['긴급 안내 보기', 'Open emergency help', '緊急案内を見る'],
  'bStay': ['코스 계속 만들기', 'Keep planning', 'コース作成を続ける'],
  'bFrom': ['{0}에서 출발', 'Start from {0}', '{0}から出発'],
  'bFixed': ['이 가게를 1번으로 고정하고 코스를 짜는 중', 'Planning a course starting from this place', 'このお店を1番に固定してコースを作成中'],
};

const Map<String, List<List<String>>> kChips = {
  'chips': [
    ['아파요', '다쳤어요', '약이 필요해요', '잃어버렸어요', '누가 따라와요', '길을 잃었어요'],
    ['I feel sick', "I'm injured", 'I need medicine', 'I lost something', 'Someone is following me', "I'm lost"],
    ['具合が悪い', 'けがをした', '薬が必要', 'なくし物をした', 'つけられている', '道に迷った']
  ]
};

String tr(String k, Lang l, [List<Object> args = const []]) => trIn(L, k, l, args);

/// 기능별 문구 맵에서 읽는다. {0}, {1} 자리에 args 를 넣는다. 없는 키는 키 이름을 그대로 보여 빠진 것이 눈에 띄게 한다
String trIn(Map<String, List<String>> m, String k, Lang l, [List<Object> args = const []]) {
  var s = m[k]?[l.i] ?? k;
  for (var i = 0; i < args.length; i++) {
    s = s.replaceAll('{$i}', '${args[i]}');
  }
  return s;
}
List<String> trChips(Lang l) => kChips['chips']![l.i];

// 도움처 종류·상태 번역 (프로토타입 TRF)
const Map<String, List<String>> kTrWord = {
  '약국': ['Pharmacy', '薬局'], '편의점': ['Convenience store', 'コンビニ'], '응급실': ['ER', '救急外来'],
  '지구대': ['Police box', '交番'], '관광안내소': ['Tourist info', '観光案内所'], '24시간': ['24h', '24時間'],
  '운영 중 21:00–01:00': ['Open 21:00–01:00', '営業中 21:00–01:00'], '운영 중 10:00–19:00': ['Open 10:00–19:00', '営業中 10:00–19:00'],
  '24시간 · 직원 있음': ['24h · staffed', '24時間・店員あり'], '심야 운영': ['Late-night', '深夜営業'],
  '상비약 판매 · 해열진통제': ['Basic medicine · fever reducers', '常備薬・解熱鎮痛剤'], '상비약 판매': ['Basic medicine', '常備薬あり'],
  '참고': ['For reference', '参考'], '해변 쪽': ['Beach side', '海辺側'], '외국어 안내': ['Foreign languages', '外国語対応'],
};

String trWord(String x, Lang l) {
  if (l == Lang.ko) return x;
  final i = l == Lang.en ? 0 : 1;
  final t = kTrWord[x];
  if (t != null) return t[i];
  final m = RegExp(r'가용 병상 (\d+)').firstMatch(x);
  if (m != null) return (i == 1 ? '空き病床 ' : 'Beds ') + m.group(1)!;
  return x;
}
