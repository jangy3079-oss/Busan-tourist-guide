# #77: 서버(DEBUG 로그 켬: --logging.level.kr.busan.safetrip.course.service.CandidateRetriever=DEBUG, 컨테이너 이름 st_chi)에 API-C01 로 같은 뜻 3개 국어 질의를 보내고, CandidateRetriever DEBUG 로그(top=...) 첫 검색 상위 10곳 겹침
import json, subprocess, sys, urllib.request, itertools, re
sys.stdout.reconfigure(encoding='utf-8')
P = [
 ('GWANGALLI', '광안리에서 바다 보고 회 먹기', 'See the sea and eat raw fish in Gwangalli', '広安里で海を見て刺身を食べる'),
 ('HAEUNDAE', '비 오는 날 해운대 실내 코스', 'Rainy day indoor course in Haeundae', '雨の日の海雲台、屋内コース'),
 ('HAEUNDAE', '해운대에서 카페와 디저트', 'Cafes and desserts in Haeundae', '海雲台でカフェとデザート'),
 ('GWANGALLI', '광안대교 야경 보는 코스', 'Course to see the Gwangan Bridge night view', '広安大橋の夜景を見るコース'),
 ('HAEUNDAE', '아이와 함께 수족관과 해변', 'Aquarium and beach with my child', '子どもと水族館とビーチ'),
 ('HAEUNDAE', '미술관과 전시 관람', 'Art museum and exhibitions', '美術館と展示を見る'),
 ('GWANGALLI', '전통시장 구경하고 간식 먹기', 'Look around a traditional market and eat snacks', '伝統市場を見て回っておやつを食べる'),
 ('HAEUNDAE', '기념품 쇼핑', 'Souvenir shopping', 'お土産の買い物'),
 ('HAEUNDAE', '온천과 한식당', 'Hot spring and Korean restaurant', '温泉と韓国料理店'),
 ('GWANGALLI', '조용한 카페에서 쉬기', 'Relax in a quiet cafe', '静かなカフェでゆっくりする'),
]
def top(q, lang, area, k=10):
    n0 = subprocess.run(['docker', 'logs', 'st_chi'], capture_output=True).stdout.decode('utf-8', 'replace').count('course.retrieve top=')
    body = {'query': q, 'lang': lang, 'overrides': {'visitDate': '2026-11-27', 'startTime': '14:00', 'durationMin': 180, 'area': area}}
    st = json.loads(urllib.request.urlopen(urllib.request.Request('http://localhost:8080/api/v1/courses', json.dumps(body).encode(), {'Content-Type': 'application/json'}), timeout=60).read())['status']
    lines = re.findall(r'course\.retrieve top=\[(.*?)\]', subprocess.run(['docker', 'logs', 'st_chi'], capture_output=True).stdout.decode('utf-8', 'replace'))
    if len(lines) <= n0: print('  검색 없음', lang, st, q); return []
    return lines[n0].split(', ')[:k]
rows = []
for area, ko, en, ja in P:
    t = {'ko': top(ko, 'ko', area), 'en': top(en, 'en', area), 'ja': top(ja, 'ja', area)}
    ov = {f'{a}-{b}': len(set(t[a]) & set(t[b])) / 10 for a, b in itertools.combinations(t, 2)}
    rows.append(ov); print(ko[:16].ljust(16), ov)
for k in rows[0]:
    print(k, f'평균 {sum(r[k] for r in rows) / len(rows):.0%}', f'70% 이상 {sum(r[k] >= .7 for r in rows)}/{len(rows)}')
allv = [v for r in rows for v in r.values()]; print('전체 평균', f'{sum(allv)/len(allv):.0%}')
