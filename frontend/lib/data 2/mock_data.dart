// [공통·임시] 프로토타입의 예시 데이터. 백엔드가 붙으면 Mock*Repository에서만 쓰이고, 실제 구현은 Api*Repository가 대체한다.
import 'models/models.dart';

const kAreas = {
  Area.gwangalli: AreaInfo('광안리', ['광안리', 'Gwangalli', '広安里'], 35.1535, 129.1186, '부산 수영구 광안해변로 219 부근',
      'Near 219 Gwanganhaebyeon-ro, Suyeong-gu, Busan', '釜山 水営区 広安海辺路219 付近'),
  Area.haeundae: AreaInfo('해운대', ['해운대', 'Haeundae', '海雲台'], 35.1590, 129.1600, '부산 해운대구 해운대해변로 264 부근',
      'Near 264 Haeundaehaebyeon-ro, Haeundae-gu, Busan', '釜山 海雲台区 海雲台海辺路264 付近'),
};

const kPlaces = <String, HelpPlace>{
  'sejong': HelpPlace('sejong', '세종약국', '약국', 35.15489, 129.11777, 202, '운영 중 21:00–01:00', '심야 운영'),
  'cu': HelpPlace('cu', 'CU 광안아이빌점', '편의점', 35.153094, 129.117713, 81, '24시간', '상비약 판매 · 해열진통제'),
  'joeun': HelpPlace('joeun', '좋은강안병원 응급실', '응급실', 35.15016, 129.11072, 791, '가용 병상 7', '참고', isEr: true),
  'hanseo': HelpPlace('hanseo', '비에이치에스한서병원 응급실', '응급실', 35.16105, 129.11288, 1015, '가용 병상 12', '', isEr: true),
  'centum': HelpPlace('centum', '센텀종합병원 응급실', '응급실', 35.16637, 129.11467, 1507, '가용 병상 5', '', isEr: true),
  'gwangnam': HelpPlace('gwangnam', '광남지구대', '지구대', 35.154303, 129.115217, 331, '24시간', ''),
  'gwangmin': HelpPlace('gwangmin', '광민지구대', '지구대', 35.157422, 129.125379, 774, '24시간', '해변 쪽'),
  'gs25': HelpPlace('gs25', 'GS25 광안해변점', '편의점', 35.154232, 129.119225, 128, '24시간 · 직원 있음', ''),
  'info': HelpPlace('info', '광안리 관광안내소', '관광안내소', 35.1532, 129.1180, 63, '운영 중 10:00–19:00', '외국어 안내'),
  'seven': HelpPlace('seven', '세븐일레븐 부산해운대센터점', '편의점', 35.1600, 129.1597, 140, '24시간', '상비약 판매'),
  'cuhd': HelpPlace('cuhd', '씨유 해운대바다점', '편의점', 35.1597, 129.1612, 162, '24시간', '상비약 판매'),
  'paik': HelpPlace('paik', '인제대 해운대백병원 응급실', '응급실', 35.17334, 129.18218, 2563, '가용 병상 13', '참고', isEr: true),
};

class TypeMeta {
  final List<String> names; // ko,en,ja
  final String icon, top;
  final List<String> placeIds;
  final EmExtra extra;
  final RegExp kw;
  const TypeMeta(this.names, this.icon, this.top, this.placeIds, this.extra, this.kw);
}

// 키워드 사전은 백엔드(KeywordRuleEngine, COMP-E04)가 원본. 아래는 Mock용 복사본.
final kTypes = <EmType, TypeMeta>{
  EmType.urgent: TypeMeta(['의료-위급', 'Medical · urgent', '医療・緊急'], 'heart', '119', ['joeun', 'hanseo', 'centum'], EmExtra.none,
      RegExp(r"쓰러|의식|숨[을을 ]*(못|안|쉬기)|피가 (많|멈추)|collaps|unconscious|not breathing|can.?t breathe|bleeding|倒れ|意識|息が|出血", caseSensitive: false)),
  EmType.mild: TypeMeta(['의료-경증', 'Medical · minor', '医療・軽症'], 'thermo', '1330', ['sejong', 'cu', 'joeun'], EmExtra.none,
      RegExp(r"열이|열 나|아파|두통|배탈|설사|약이|약 필요|다쳤|상처|fever|sick|headache|medicine|pain|injur|熱|痛|薬|けが|具合", caseSensitive: false)),
  EmType.lost: TypeMeta(['분실·도난', 'Lost or stolen', '紛失・盗難'], 'wallet', '1330', ['gwangnam', 'gwangmin', 'info'], EmExtra.embassy,
      RegExp(r"잃어버|분실|여권|지갑|도난|훔|소매치기|lost (my|a|the)? ?(wallet|passport|phone|bag)|lost something|stolen|passport|なくし|紛失|盗", caseSensitive: false)),
  EmType.threat: TypeMeta(['위협·범죄', 'Threat or crime', '脅威・犯罪'], 'shield', '112', ['gs25', 'gwangnam', 'gwangmin'], EmExtra.silent,
      RegExp(r"따라와|따라오|위협|폭행|추행|무서|following|threat|attack|harass|scared|つけ|暴行|怖", caseSensitive: false)),
  EmType.fire: TypeMeta(['화재·재난', 'Fire or disaster', '火災・災害'], 'flame', '119', [], EmExtra.none,
      RegExp(r"불이|화재|연기|지진|해일|fire|smoke|earthquake|tsunami|火事|煙|地震|津波", caseSensitive: false)),
  EmType.way: TypeMeta(['길 잃음·언어', 'Lost way · language', '道迷い・言葉'], 'map', '1330', ['info', 'gwangnam'], EmExtra.none,
      RegExp(r"길을 잃|길 잃|숙소를 못|말이 안 통|I.?m lost|lost my way|can.?t find my (hotel|way)|道に迷|迷子|言葉が", caseSensitive: false)),
  EmType.non: TypeMeta(['비긴급', 'Not urgent', '緊急ではない'], 'chat', '1330', [], EmExtra.none,
      RegExp(r"맛집|추천|예약|영업시간|코스|어디 가|recommend|restaurant|reservation|おすすめ|予約", caseSensitive: false)),
};
const kTypeOrder = [EmType.urgent, EmType.fire, EmType.threat, EmType.way, EmType.lost, EmType.mild, EmType.non];

EmType? mockClassify(String text) {
  if (text.isEmpty) return null;
  for (final k in kTypeOrder) {
    if (kTypes[k]!.kw.hasMatch(text)) return k;
  }
  return null;
}

const kTodo = <EmType, List<List<String>>>{
  EmType.urgent: [
    ['바로 119에 전화하세요. 통화가 어렵다면 통역 연결을 요청하세요.', 'Call 119 now. Ask for an interpreter if needed.', 'すぐに119へ電話してください。必要なら通訳を頼んでください。'],
    ['의식과 호흡을 확인하고, 호흡이 없으면 가슴 압박을 시작하세요.', 'Check breathing. If none, start chest compressions.', '意識と呼吸を確認し、呼吸がなければ胸骨圧迫を始めてください。'],
    ['주변 사람에게 자동심장충격기(AED)를 가져와 달라고 하세요.', 'Ask someone nearby to bring an AED.', '周りの人にAEDを持ってきてもらってください。'],
  ],
  EmType.mild: [
    ['해열진통제는 24시간 상비약 판매 편의점에서 살 수 있어요.', 'Fever and pain relievers are sold at 24-hour convenience stores.', '解熱鎮痛剤は24時間営業のコンビニで買えます。'],
    ['숨쉬기 어렵거나 의식이 흐려지면 바로 119를 누르세요.', 'If breathing gets hard or they faint, call 119 at once.', '息苦しさや意識の低下があれば、すぐ119へ。'],
  ],
  EmType.lost: [
    ['도난이면 112, 단순 분실이면 가까운 지구대에 분실 신고를 하세요.', 'If stolen, call 112. If lost, report it at the nearest police box.', '盗難なら112、紛失なら近くの交番に届けてください。'],
    ['여권은 대사관·영사관에서 재발급이나 여행증명서를 받아야 해요.', 'For a passport, contact your embassy or consulate.', 'パスポートは大使館・領事館で再発行や渡航書の手続きが必要です。'],
    ['카드는 카드사에 바로 사용 정지를 요청하세요.', 'Ask your card company to block your cards.', 'カードはすぐにカード会社へ利用停止を依頼してください。'],
  ],
  EmType.threat: [
    ['사람이 많고 밝은 곳(24시간 편의점 등)으로 바로 이동하세요.', 'Move to a bright, busy place such as a 24-hour store.', '明るく人の多い場所（24時間コンビニなど）へ移動してください。'],
    ['112에 전화하고, 말하기 어려우면 문자로 신고하세요.', 'Call 112. If you cannot talk, send a text.', '112へ電話を。話せない時はメッセージで通報してください。'],
  ],
  EmType.fire: [
    ['119에 신고하고 바로 건물 밖으로 대피하세요.', 'Call 119 and leave the building now.', '119へ通報し、すぐに建物の外へ避難してください。'],
    ['엘리베이터 대신 계단을 이용하세요.', 'Use the stairs, not the elevator.', 'エレベーターではなく階段を使ってください。'],
    ['연기가 있으면 젖은 천으로 코와 입을 막고 낮은 자세로 이동하세요.', 'In smoke, cover nose and mouth and stay low.', '煙がある時は濡れた布で口と鼻を覆い、低い姿勢で移動してください。'],
  ],
  EmType.way: [
    ['숙소 주소를 한국어 카드로 보여 주세요.', 'Show your hotel address with the Korean card.', '宿の住所を韓国語カードで見せてください。'],
    ['1330에 전화하면 통역과 길 안내를 받을 수 있어요.', 'Call 1330 for interpretation and directions.', '1330に電話すると通訳と道案内を受けられます。'],
  ],
};
const kTodoSrc = <EmType, String>{
  EmType.urgent: '출처: 질병관리청 심폐소생술 가이드라인',
  EmType.mild: '출처: 응급의료에 관한 법률 시행규칙 별표 1 · 보건복지부 안전상비의약품 안내',
  EmType.lost: '출처: 경찰청 유실물 안내 · 외교부 재외공관 안내',
  EmType.threat: '출처: 경찰청 112 신고 안내',
  EmType.fire: '출처: 행정안전부 국민재난안전포털 행동요령',
  EmType.way: '출처: 한국관광공사 1330 관광통역안내',
};
const kCard = <EmType, List<String>>{
  EmType.urgent: ['외국인 관광객입니다. 일행이 쓰러졌습니다. 119를 불러 주세요.', 'I am a foreign tourist. My companion has collapsed. Please call 119.', '外国人観光客です。同行者が倒れました。119を呼んでください。'],
  EmType.mild: ['외국인 관광객입니다. 열이 납니다. 해열제가 필요합니다.', 'I am a foreign tourist. I have a fever and need fever medicine.', '外国人観光客です。熱があります。解熱剤が必要です。'],
  EmType.lost: ['외국인 관광객입니다. 여권과 지갑을 잃어버렸습니다. 분실 신고를 도와주세요.', 'I am a foreign tourist. I lost my passport and wallet. Please help me report it.', '外国人観光客です。パスポートと財布をなくしました。届け出を手伝ってください。'],
  EmType.threat: ['외국인 관광객입니다. 누군가 저를 따라오고 있습니다. 112에 신고해 주세요.', 'I am a foreign tourist. Someone is following me. Please call 112.', '外国人観光客です。誰かにつけられています。112に通報してください。'],
  EmType.fire: ['외국인 관광객입니다. 불이 났습니다. 119에 신고해 주세요.', 'I am a foreign tourist. There is a fire. Please call 119.', '外国人観光客です。火事です。119に通報してください。'],
  EmType.way: ['외국인 관광객입니다. 숙소를 찾지 못하고 있습니다. 길을 알려 주세요.', 'I am a foreign tourist. I cannot find my hotel. Please help me with directions.', '外国人観光客です。宿が見つかりません。道を教えてください。'],
};

// ---------------- 코스 ----------------
const _path = [
  MapPoint(35.1532, 129.1186), MapPoint(35.1552, 129.1232), MapPoint(35.1566, 129.1264),
  MapPoint(35.1568, 129.1300), MapPoint(35.1553, 129.1328), MapPoint(35.1567, 129.1295),
];
List<CourseAssumption> mockAssumptions({String date = '오늘', String time = '14:00', int min = 180, Area area = Area.gwangalli, bool timeDefault = false}) => [
      CourseAssumption('visitDate', '2026-11-11', date, isDefault: date == '오늘'),
      CourseAssumption('startTime', time, '$time 출발', isDefault: timeDefault),
      CourseAssumption('durationMin', '$min', '${min ~/ 60}시간${min % 60 == 0 ? '' : ' ${min % 60}분'}'),
      CourseAssumption('area', area == Area.gwangalli ? 'GWANGALLI' : 'HAEUNDAE', area == Area.gwangalli ? '광안리 권역' : '해운대 권역'),
    ];

final kCourses = <String, Course>{
  'v1': Course('mock-c1', 1, ['오늘 14:00 출발', '3시간', '광안리 권역', '도보'], [
    CourseStop('g', '14:00 – 15:00', '광안리해수욕장', '모래사장 산책 · 광안대교 보기', 35.1532, 129.1186),
    CourseStop('m', '15:15 – 15:45', '민락수변공원', '바다 앞 계단 쉼터 · 도보 12분', 35.1553, 129.1328),
    CourseStop('f', '16:00 – 17:00', '민락어민활어직판장', '회 · 1층에서 고르고 2층에서 식사 · 도보 6분', 35.1567, 129.1295),
  ], _path, Area.gwangalli, assumptions: mockAssumptions()),
  'v2': Course('mock-c2', 2, ['오늘 14:00 출발', '2시간', '광안리 권역', '도보'], [
    CourseStop('g', '14:00 – 14:30', '광안리해수욕장', '모래사장 산책 · 광안대교 보기', 35.1532, 129.1186, changed: true),
    CourseStop('m', '14:45 – 15:05', '민락수변공원', '바다 앞 계단 쉼터 · 도보 12분', 35.1553, 129.1328, changed: true),
    CourseStop('f', '15:15 – 16:00', '민락어민활어직판장', '회 · 1층에서 고르고 2층에서 식사 · 도보 6분', 35.1567, 129.1295, changed: true),
  ], _path, Area.gwangalli, changedChipIndex: 2, assumptions: mockAssumptions(min: 120)),
  'photo': Course('mock-c3', 1, ['지금 출발', '2시간', '해운대 권역', '도보', '출발지 고정'], [
    CourseStop('a', '14:20 – 15:20', '해운대암소갈비집', '사진으로 찾은 출발 장소 · 식사', 35.1630, 129.1636, fixed: true),
    CourseStop('h', '15:30 – 16:00', '해운대해수욕장', '해변 산책 · 도보 8분', 35.1587, 129.1604),
    CourseStop('d', '16:15 – 16:20', '동백섬 · 누리마루 APEC하우스', '해안 산책로 · 도보 15분', 35.1531, 129.1525),
  ], [], Area.haeundae, assumptions: mockAssumptions(time: '14:20', min: 120, area: Area.haeundae, timeDefault: true)),
};

// ---------------- 사진 ----------------
const kPhotoPlace = PhotoPlace('MOCK-1', '해운대암소갈비집', '한식 · 소갈비', '부산 해운대구 중동', 'Haeundae Amso Galbijip', '11:30–22:00', 35.1630, 129.1636, 420, true,
    overview: '해운대 해변 근처의 소갈비 전문점.(원문 일부)', source: 'ⓒ한국관광공사 · 수집일 2026-10-12');
const kPhotoCands = [
  PhotoCandidate(kPhotoPlace, 82),
  PhotoCandidate(PhotoPlace('MOCK-2', '해운대원조할매국밥', '한식', '부산 해운대구', '', '', 35.1620, 129.1630, 610, true), 41),
  PhotoCandidate(PhotoPlace('MOCK-3', '금수복국 해운대본점', '한식', '부산 해운대구', '', '', 35.1610, 129.1640, 880, true), 37),
];
