// [긴급 담당] 긴급 화면·긴급 API 표시 문구. 값은 [한국어, English, 日本語] 순서(#26). 공용 i18n.dart 의 L 표에는 넣지 않는다.
import '../../core/i18n.dart';

const Map<String, List<String>> kEmText = {
  // UI-E01 입력·위치 (FR-E001, FR-E002)
  'empty': ['상황을 한 줄만 적어 주세요', 'Please describe what happened in a line', '状況を一行で書いてください'],
  'locIdle': ['도움 찾기를 누르면 위치를 한 번 물어봐요. 위치는 저장하지 않아요.', 'We ask for your location once when you tap Find help. It is not saved.', '「助けを探す」を押すと位置情報を一度だけ確認します。保存はしません。'],
  'locAsking': ['위치를 확인하는 중…', 'Checking your location…', '位置情報を確認しています…'],
  'locDenied': ['위치를 쓸 수 없어 고른 지역 기준으로 안내해요', 'Location is off, so we guide you by the area you chose', '位置情報が使えないため、選んだ地域を基準に案内します'],
  'change': ['바꾸기', 'Change', '変更'],
  // UI-E05 지역 선택
  'gwSub': ['수영구 · 광안리해수욕장 기준', 'Suyeong-gu · around Gwangalli Beach', '水営区・広安里海水浴場周辺'],
  'hdSub': ['해운대구 · 해운대해수욕장 기준', 'Haeundae-gu · around Haeundae Beach', '海雲台区・海雲台海水浴場周辺'],
  // 연락 버튼 (FR-E003, FR-E004)
  'call': ['{0}에 전화', 'Call {0}', '{0}に電話'],
  // UI-E02 결과
  'hereMe': ['현재 위치 기준', 'Around you', '現在地周辺'],
  'hereArea': ['{0} 기준', 'Around {0}', '{0}周辺'],
  'noPlaces': ['지금 안내할 수 있는 도움처를 찾지 못했어요. 위 번호로 바로 연락하세요.', 'We could not find a place to send you right now. Please call a number above.', '今ご案内できる支援先が見つかりませんでした。上の番号に連絡してください。'],
  'callFirst': ['방문 전 전화로 확인하세요', 'Call before you go', '訪問前に電話で確認してください'],
  'phone': ['전화', 'Call', '電話'],
  'nav': ['길찾기', 'Directions', 'ルート'],
  'errPick': ['상황에 가까운 유형을 고르면 다시 찾아볼게요', 'Pick the closest type and we will try again', '近い状況を選ぶと、もう一度探します'],
  // 도움처 표시 (emergency_api.dart 에서 화면 언어로 바꿀 때 사용)
  'kind.ER': ['응급실', 'Emergency room', '救急外来'],
  'kind.PHARMACY': ['약국', 'Pharmacy', '薬局'],
  'kind.CONVENIENCE': ['상비약 판매 편의점', 'Convenience store (basic medicine)', 'コンビニ(常備薬)'],
  'kind.POLICE': ['지구대·파출소', 'Police station', '交番・派出所'],
  'kind.TOURIST_INFO': ['관광안내소', 'Tourist information', '観光案内所'],
  'kind.HOSPITAL': ['병원', 'Hospital', '病院'],
  'kind.FIRE_STATION': ['소방서', 'Fire station', '消防署'],
  'open.OPEN': ['운영 중', 'Open now', '営業中'],
  'open.CLOSED': ['운영 안 함', 'Closed now', '営業時間外'],
  'open.UNKNOWN': ['운영 미확인', 'Hours not confirmed', '営業未確認'],
  'beds': ['가용병상 {0}', 'Free beds: {0}', '空き病床 {0}'],
  'noTreat': ['진료불가 메시지 있음', 'Notice: may not treat patients now', '診療不可のお知らせあり'],
  'stepsSrc': ['근거: {0}', 'Source: {0}', '根拠: {0}'],
  'stepsFallback': ['기본 안내 문구 (맞는 근거 문서를 찾지 못함)', 'Standard advice (no matching source document)', '標準の案内文(該当する根拠文書なし)'],
  'asOf': ['데이터 기준 {0} · 출처 {1}', 'Data as of {0} · Source: {1}', 'データ基準 {0}・出典 {1}'],
  'asOfTime': ['데이터 기준 {0}', 'Data as of {0}', 'データ基準 {0}'],
  'asOfSrc': ['출처 {0}', 'Source: {0}', '出典 {0}'],
  // 면책 문구 (#127)
  'disclaimer': [
    '이 안내는 의료 판단이 아닙니다. 위급하면 바로 119에 전화하세요.',
    'This guide is not medical advice. In an emergency, call 119 immediately.',
    'この案内は医療判断ではありません。緊急時はすぐ119に電話してください。',
  ],
  // UI-E03 한국어 카드
  'transLabel': ['', 'English', '日本語'],
  'copied': ['주소를 복사했어요', 'Address copied', '住所をコピーしました'],
  // 확장: 소리 없이 신고 · 대사관
  'silentHow': ['아래 한국어 문장을 112에 문자로 보내세요.', 'Send the Korean message below to 112 by text.', '下の韓国語の文を112にメッセージで送ってください。'],
  'silentTo': ['받는 사람: 112', 'To: 112', '宛先: 112'],
  'copyMsg': ['문구 복사', 'Copy text', '文をコピー'],
  'msgCopied': ['문구를 복사했어요', 'Text copied', '文をコピーしました'],
  'sendSms': ['문자 보내기', 'Open messages', 'メッセージを開く'],
  'silentSrc': ['112 문자 신고를 여는 기능이에요. 접수 시스템과 직접 연결되지는 않아요.', 'This opens a text to 112. It is not connected to the police system directly.', '112へのメッセージを開く機能です。受付システムとは直接つながっていません。'],
  'embassyHow': ['여권을 잃어버렸다면 자국 대사관·영사관에서 재발급이나 여행증명서를 받아야 해요.', 'If you lost your passport, your embassy or consulate can reissue it or give you a travel document.', 'パスポートをなくした場合は、自国の大使館・領事館で再発行や渡航書の手続きをします。'],
  'embassyAsk1330': ['자국 대사관 연락처를 1330에 물어보세요', 'Ask 1330 for your embassy\'s contact details', '自国の大使館の連絡先を1330に聞いてください'],
  'embassyList': ['외교부 주한공관 목록(한국어)', 'MOFA list of embassies in Korea (Korean)', '韓国外交部の駐韓公館一覧(韓国語)'],
};

String et(String k, Lang l, [List<Object> args = const []]) => trIn(kEmText, k, l, args);
