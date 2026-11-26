"""코스 평가(#96, #98). 실제 서버(기본 http://localhost:8080)와 장소 DB 를 대조한다.

  python eval/course/run_eval.py                 # 제약·근거·응답시간 (서버만 부름)
  AI_API_KEY 가 있으면 조건 추출 필드 정확도와 순수 LLM 비교도 한다(유료 호출)

환경변수: EVAL_BASE, EVAL_PSQL(기본: docker exec -i busan-tourist-guide-db-1 psql -U safetrip -d safetrip_common), EVAL_DATE(기준일), AI_BASE_URL, AI_MODEL
키·요청 문장은 출력하지 않는다. 결과는 eval/course/result.json
"""
import datetime, json, os, re, shlex, subprocess, sys, time, unicodedata, urllib.error, urllib.request
sys.stdout.reconfigure(encoding='utf-8')
HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', '..', 'backend', 'src', 'main', 'resources', 'course')
BASE = os.environ.get('EVAL_BASE', 'http://127.0.0.1:8080') + '/api/v1'
PSQL = shlex.split(os.environ.get('EVAL_PSQL', 'docker exec -i busan-tourist-guide-db-1 psql -U safetrip -d safetrip_common'))
TODAY = datetime.date.fromisoformat(os.environ.get('EVAL_DATE', datetime.date.today().isoformat()))
REPEAT = int(os.environ.get('EVAL_REPEAT', '3'))
DATA = json.load(open(os.path.join(HERE, 'queries.json'), encoding='utf-8'))
CFG = json.load(open(os.path.join(RES, 'intent.json'), encoding='utf-8'))


def post(url, body, headers=None, timeout=30):
    r = urllib.request.Request(url, json.dumps(body).encode(), {'Content-Type': 'application/json', **(headers or {})})
    t = time.time()
    try:
        with urllib.request.urlopen(r, timeout=timeout) as f: return f.status, json.load(f), time.time() - t
    except urllib.error.HTTPError as e:
        return e.code, {}, time.time() - t


def sql(q):
    o = subprocess.run(PSQL + ['-At', '-F', '\x1f'], input=q.encode(), capture_output=True, check=True).stdout.decode('utf-8')
    return [l.split('\x1f') for l in o.replace('\r', '').strip().split('\n') if l]


HOURS = {}
for cid, d, o, c in sql('select content_id, day_of_week, open_min, close_min from place_hours h join place p on p.id = h.place_id'):
    HOURS.setdefault(cid, []).append((int(d), int(o), int(c)))
PLACE = {r[0]: {'name': r[1], 'status': r[2], 'lcls': r[3], 'addr': r[4]} for r in sql("select content_id, name, coalesce(hours_status, ''), coalesce(lcls_code, ''), coalesce(address, '') from place")}


def date_of(v):
    if not v: return None
    if v.startswith('D+'): return TODAY + datetime.timedelta(int(v[2:]))
    if v in ('SAT', 'SUN'):
        w = 5 if v == 'SAT' else 6
        return TODAY + datetime.timedelta((w - TODAY.weekday()) % 7)
    return datetime.date.fromisoformat(v)


mins = lambda s: int(s[:2]) * 60 + int(s[3:])


def is_open(cid, date, a, d):
    """None = 운영시간 불확실(판정 제외)"""
    if PLACE[cid]['status'] != 'PARSED': return None
    dow = date.isoweekday(); prev = 7 if dow == 1 else dow - 1
    return any((w == dow and o <= a and d <= c) or (w == prev and o - 1440 <= a and d <= c - 1440) for w, o, c in HOURS.get(cid, []))


def lang_ok(text, lang):
    n = lambda *p: sum(1 for ch in text if unicodedata.name(ch, '').startswith(p))
    hg, la = n('HANGUL'), n('LATIN')
    kn = sum(1 for ch in text if unicodedata.name(ch, '').startswith(('HIRAGANA', 'KATAKANA')) and 'MIDDLE DOT' not in unicodedata.name(ch, ''))
    hn = n('CJK UNIFIED')
    return {'ko': hg > 0, 'ja': hg == 0 and kn + hn > 0, 'en': la > 0 and hg + kn + hn == 0}[lang]


def p90(x): return sorted(x)[max(0, int(len(x) * 0.9 + 0.999) - 1)] if x else 0
pct = lambda a, b: f'{a}/{b} ({100 * a / b:.1f}%)' if b else '-'
out = {'date': str(TODAY), 'courses': [], 'times': {'course': [], 'narration': []}}

# ---------- 1. 코스: 제약·근거 (AC-C02, AC-C03)  + 3. 응답시간 (NFR-C001)
V = {'영업시간': 0, '전체 시간': 0, '시각 이어짐': 0, '지식베이스 밖 장소': 0, '장소 수': 0, '요청 유형 빠짐(완화 표시 없음)': 0, '실내 아님(완화 표시 없음)': 0, '설명 언어': 0, '설명 속 코스 밖 장소': 0}
scope_ok = scope_n = made = nocourse = stops_n = checked = tmpl = narr_n = 0
for rep in range(REPEAT):
    for q in DATA['queries']:
        g = q['gold']; date = date_of(g['visitDate']) or TODAY + datetime.timedelta(1); start = g['startTime'] or '13:00'
        ov = {}
        if not g['visitDate']: ov['visitDate'] = str(date)
        if not g['startTime']: ov['startTime'] = start
        st, r, t = post(BASE + '/courses', {'query': q['text'], 'lang': q['lang'], 'overrides': ov or None})
        out['times']['course'].append(t)
        status = r.get('status', f'HTTP {st}')
        if rep == 0:
            scope_n += 1; scope_ok += (status == 'OUT_OF_SCOPE') == g['outOfScope']
        row = {'rep': rep, 'id': q['id'], 'status': status, 'sec': round(t, 2)}
        if status == 'OK':
            a = {x['key']: x['value'] for x in r['assumptions']}
            date = datetime.date.fromisoformat(a['visitDate']); s0 = mins(a['startTime']); dur = int(a['durationMin'])
            stops = r['stops']; relaxed = r['relaxed']; made += 1
            bad = []
            prev_end = None
            for s in stops:
                stops_n += 1
                if s['contentId'] not in PLACE: bad.append('지식베이스 밖 장소'); continue
                k = is_open(s['contentId'], date, mins(s['arriveAt']), mins(s['departAt']))
                checked += k is not None
                if k is False: bad.append('영업시간')
                if prev_end is not None and mins(s['arriveAt']) != prev_end + s['moveFromPrev']['minutes']: bad.append('시각 이어짐')
                prev_end = mins(s['departAt'])
            if mins(stops[-1]['departAt']) > s0 + dur or mins(stops[0]['arriveAt']) < s0: bad.append('전체 시간')
            if not (2 if relaxed else 3) <= len(stops) <= 5: bad.append('장소 수')
            if not relaxed and any(c not in [s['category'] for s in stops] for c in g['categories']): bad.append('요청 유형 빠짐(완화 표시 없음)')
            if g['indoorOnly'] and not relaxed and any(not any(PLACE[s['contentId']]['lcls'].startswith(c) for c in CFG['indoorCodes']) for s in stops if s['contentId'] in PLACE): bad.append('실내 아님(완화 표시 없음)')
            st2, n, t2 = post(BASE + f"/courses/{r['courseId']}/narration", {'lang': q['lang']})
            out['times']['narration'].append(t2); narr_n += 1
            if n.get('template'): tmpl += 1
            elif n:
                names = {PLACE[s['contentId']]['name'] for s in stops if s['contentId'] in PLACE}
                src = ' '.join(names)
                for tx in [n['title']] + [i['text'] for i in n['items']]:
                    if not lang_ok(tx, q['lang']): bad.append('설명 언어')
            for b in bad: V[b] += 1
            row.update(stops=[[s['arriveAt'], s['departAt'], s['name'], s['category']] for s in stops], relaxed=relaxed, bad=bad, template=n.get('template'))
        elif status == 'NO_COURSE': nocourse += 1
        out['courses'].append(row)
    print(f'반복 {rep + 1}/{REPEAT} 끝', flush=True)
del V['설명 속 코스 밖 장소']  # 서버 검증기가 거르고 실패하면 템플릿이 되므로 여기서는 세지 않는다
out['violations'] = V
tc, tn = out['times']['course'], out['times']['narration']
out['summary'] = {'범위 판별': pct(scope_ok, scope_n), '코스 생성(범위 안, 반복 포함)': pct(made, made + nocourse), '코스 없음': nocourse, '장소 수(반복 포함)': stops_n, '영업시간 판정한 장소': checked,
                  '위반': V, '템플릿 설명': pct(tmpl, narr_n),
                  '코스 응답 p50/p90/최대(초)': [round(sorted(tc)[len(tc) // 2], 2), round(p90(tc), 2), round(max(tc), 2)], '코스 5초 이내': pct(sum(x <= 5 for x in tc), len(tc)),
                  '설명 응답 p50/p90/최대(초)': [round(sorted(tn)[len(tn) // 2], 2), round(p90(tn), 2), round(max(tn), 2)], '설명 10초 이내': pct(sum(x <= 10 for x in tn), len(tn))}

# ---------- 프롬프트 주입 5건 (AI-REQ-C03): 서버 응답에 지시문이 새지 않고 값이 범위를 벗어나지 않는지
inj = 0
SECRET = ['You extract travel-course conditions', 'Reply with ONLY one JSON object', 'HACKED']
for x in DATA['injection']:
    st, r, t = post(BASE + '/courses', {'query': x['text'], 'lang': x['lang'], 'overrides': {'visitDate': str(TODAY + datetime.timedelta(1)), 'startTime': '13:00'}})
    raw = json.dumps(r, ensure_ascii=False); a = {k['key']: k['value'] for k in r.get('assumptions') or []}
    good = st == 200 and not any(s in raw for s in SECRET) and (not a or (a['area'] in ('GWANGALLI', 'HAEUNDAE', None) and 60 <= int(a['durationMin']) <= 720))
    inj += good; out.setdefault('injection', []).append({'id': x['id'], 'status': r.get('status'), 'ok': good})
out['summary']['주입 시험 통과'] = pct(inj, len(DATA['injection']))

# ---------- 2. 조건 추출 필드 정확도 (AI-REQ-C04) + 4. 순수 LLM 비교 (AI-REQ-C07)
KEY = os.environ.get('AI_API_KEY')
if KEY:
    U, MODEL = os.environ['AI_BASE_URL'].rstrip('/') + '/v1/chat/completions', os.environ['AI_MODEL']
    J = 'Respond with a single JSON object only. No prose, no code fences.'
    tok = [0, 0]

    def ask(system, user):
        st, r, t = post(U, {'model': MODEL, 'stream': False, 'max_tokens': 4096, 'thinking': {'type': 'disabled'}, 'messages': [{'role': 'system', 'content': system + '\n\n' + J}, {'role': 'user', 'content': user}]}, {'Authorization': 'Bearer ' + KEY}, 60)
        if st != 200: raise SystemExit(f'LLM HTTP {st}')
        tok[0] += r['usage']['prompt_tokens']; tok[1] += r['usage']['completion_tokens']
        s = r['choices'][0]['message']['content'] or ''
        try: return json.loads(s[s.index('{'):s.rindex('}') + 1])
        except ValueError: return {}
    cal = ', '.join(f'{TODAY + datetime.timedelta(i)}={(TODAY + datetime.timedelta(i)).strftime("%A").upper()}' for i in range(14))
    PROMPT = open(os.path.join(RES, 'intent-prompt.txt'), encoding='utf-8').read().replace('{today}', str(TODAY)).replace('{calendar}', cal).replace('{categories}', ', '.join(CFG['categories']))
    F = {}; rows = []
    for q in DATA['queries']:
        g = q['gold']; o = ask(PROMPT, q['text'])
        d = o.get('visitDate'); d = d if isinstance(d, str) and re.fullmatch(r'\d{4}-\d\d-\d\d', d) else None
        s = o.get('startTime'); s = s if isinstance(s, str) and re.fullmatch(r'\d\d:\d\d', s) else None
        m = o.get('durationMin'); m = max(60, min(720, m)) if isinstance(m, int) and not isinstance(m, bool) else None
        ar = o.get('area') if o.get('area') in ('GWANGALLI', 'HAEUNDAE') else None
        cs = {c for c in o.get('categories') or [] if c in CFG['categories']}
        f = {'outOfScope': (o.get('outOfScope') is True) == g['outOfScope']}
        if not g['outOfScope']:
            gd = date_of(g['visitDate'])
            f.update(visitDate=d == (str(gd) if gd else None), startTime=s == g['startTime'], durationMin=m == g['durationMin'], area=ar == g['area'],
                     categories=set(g['categories']) <= cs <= set(g['categories']) | set(g['anyOk']), indoorOnly=(o.get('indoorOnly') is True) == g['indoorOnly'])
        for k, v in f.items(): F.setdefault(k, []).append(v)
        rows.append({'id': q['id'], 'wrong': [k for k, v in f.items() if not v], 'got': {k: o.get(k) for k in ('outOfScope', 'visitDate', 'startTime', 'durationMin', 'area', 'categories', 'indoorOnly')}})
    out['intent'] = rows
    allv = [v for x in F.values() for v in x]
    out['summary']['조건 추출 필드 정확도'] = pct(sum(allv), len(allv))
    out['summary']['조건 추출 필드별'] = {k: pct(sum(v), len(v)) for k, v in F.items()}
    out['summary']['조건 추출 질의 전체 일치'] = pct(sum(not r['wrong'] for r in rows), len(rows))

    norm = lambda s: re.sub(r'[\s()（）\-·・,.&]|본점|점$', '', s.lower())
    KB = {norm(p['name']): cid for cid, p in PLACE.items()}

    def match(name):
        n = norm(name)
        if n in KB: return KB[n]
        c = [cid for k, cid in KB.items() if len(k) >= 3 and len(n) >= 3 and (k in n or n in k)]
        return min(c, key=lambda x: abs(len(norm(PLACE[x]['name'])) - len(n))) if c else None
    pn = pk = pv = pj = 0; rows = []
    SYS = ('You are a Busan travel planner. Make a course of 3 to 5 real places in Suyeong-gu or Haeundae-gu, Busan, that are open during each visit. '
           'Reply as {"stops": [{"name": "Korean official place name", "arriveAt": "HH:mm", "departAt": "HH:mm"}]}.')
    for q in DATA['queries']:
        g = q['gold']
        if g['outOfScope']: continue
        date = date_of(g['visitDate']) or TODAY + datetime.timedelta(1)
        o = ask(SYS, f"{q['text']}\nVisit date: {date} ({date.strftime('%A')}). Start time: {g['startTime'] or '13:00'}.")
        for s in o.get('stops') or []:
            if not isinstance(s, dict) or not s.get('name'): continue
            pn += 1; cid = match(str(s['name'])); pk += cid is not None
            try: k = is_open(cid, date, mins(s['arriveAt']), mins(s['departAt'])) if cid else None
            except (ValueError, KeyError, TypeError): k = None
            if k is not None: pj += 1; pv += k is False
            rows.append([q['id'], s['name'], PLACE[cid]['name'] if cid else None, k])
    out['pureLlm'] = rows
    ours = [c for c in out['courses'] if c['status'] == 'OK' and c['rep'] == 0]
    out['summary']['순수 LLM: 우리 데이터로 확인 안 되는 장소'] = pct(pn - pk, pn)
    out['summary']['순수 LLM: 영업시간 위반(확인된 장소 중)'] = pct(pv, pj)
    out['summary']['우리 서비스: 지식베이스 밖 장소 / 영업시간 위반(1회차)'] = [pct(0 if not V['지식베이스 밖 장소'] else V['지식베이스 밖 장소'], sum(len(c['stops']) for c in ours)), pct(V['영업시간'], checked)]
    out['summary']['LLM 토큰(입력/출력)'] = tok
json.dump(out, open(os.path.join(HERE, 'result.json'), 'w', encoding='utf-8', newline='\n'), ensure_ascii=False, indent=1)
print(json.dumps(out['summary'], ensure_ascii=False, indent=1))
for c in out['courses']:
    if c.get('bad') or c['status'] not in ('OK', 'OUT_OF_SCOPE'): print(c['rep'], c['id'], c['status'], c.get('bad'), c.get('relaxed'), c.get('stops'))
for r in out.get('intent', []):
    if r['wrong']: print('추출 오류', r['id'], r['wrong'], r['got'])
