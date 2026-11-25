// [공통] 다국어 (ko / en / ja). 프로토타입에서 번역이 있는 범위(긴급·한국어 카드)를 그대로 옮김.
// TODO(전원): 홈·코스·사진 화면 문구도 SRS상 ko/en/ja 지원 대상 → 여기에 키 추가 후 tr() 사용
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
};

const Map<String, List<List<String>>> kChips = {
  'chips': [
    ['아파요', '다쳤어요', '약이 필요해요', '잃어버렸어요', '누가 따라와요', '길을 잃었어요'],
    ['I feel sick', "I'm injured", 'I need medicine', 'I lost something', 'Someone is following me', "I'm lost"],
    ['具合が悪い', 'けがをした', '薬が必要', 'なくし物をした', 'つけられている', '道に迷った']
  ]
};

String tr(String k, Lang l) => L[k]?[l.i] ?? k;
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
