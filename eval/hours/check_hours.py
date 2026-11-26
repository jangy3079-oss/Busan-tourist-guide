"""운영시간 구조화 표본 50건(#12, AI-REQ-C06). 정답 GOLD 는 원문만 보고 파서 결과를 보기 전에 적었다.
표본: place 중 hours_text 가 있는 곳을 md5(content_id || 'sample50') 순으로 50곳. 실행: python eval/hours/check_hours.py
"""
import subprocess, sys
sys.stdout.reconfigure(encoding='utf-8')
WD, WE, ALL = [1, 2, 3, 4, 5], [6, 7], [1, 2, 3, 4, 5, 6, 7]
def g(*parts, rest=()):
    r = {}
    for days, *spans in parts:
        for d in days:
            if d not in rest: r[d] = sorted([int(a[:2]) * 60 + int(a[3:]), int(b[:2]) * 60 + int(b[3:])] for a, b in spans)
    return r
A = lambda a, b, rest=(): g((ALL, (a, b)), rest=rest)
U = None  # 정할 수 없음 → '불확실'이어야 정답
GOLD = {
 '2903793': A('10:30', '20:00'), '2914111': A('10:00', '20:00'), '2929116': A('10:30', '21:30'),
 '2869936': g((WD, ('11:30', '15:00'), ('17:00', '22:00')), (WE, ('11:30', '16:00'), ('17:00', '22:00'))),
 '126080': A('00:00', '24:00'), '4135919': A('10:00', '21:00', (7,)), '2782714': g((WD, ('14:00', '18:00')), ([6], ('10:00', '17:30')), rest=(7, 1, 2)),
 '2907548': A('10:00', '22:30'), '229912': g((WD, ('10:00', '19:00')), (WE, ('10:00', '20:00'))),
 '3543105': g((WD, ('11:30', '14:30'), ('18:00', '21:00')), (WE, ('12:00', '15:00'), ('18:00', '21:00')), rest=(3,)),
 '3027575': U, '127925': A('00:00', '24:00'), '2788406': g((ALL, ('10:00', '15:00'), ('16:00', '21:00')), rest=(7,)), '3027780': A('00:00', '24:00'),
 '2910557': A('10:30', '20:00'), '2782767': A('09:30', '22:00'), '2783360': A('11:00', '17:00', (1, 2)), '2911624': A('11:00', '23:00'),
 '4118033': g((WD + [7], ('10:00', '22:00')), ([6], ('10:00', '23:00')), rest=(3,)), '2861178': A('07:00', '15:00', (1,)), '3083429': A('10:30', '18:00', (1, 2, 3, 4)),
 '4011305': A('10:00', '20:00'), '2860782': A('12:00', '25:00'), '4011307': A('10:30', '20:00'), '2989656': g((ALL, ('12:00', '14:00'), ('17:40', '21:30')), rest=(3,)),
 '2916567': A('10:00', '21:00'), '134204': g(([1, 2, 3, 4], ('10:00', '20:50')), ([5, 6, 7], ('10:00', '22:00')), rest=(3,)), '2869323': A('11:00', '20:00', (7,)),
 '2891868': A('14:30', '26:30', (2,)), '3028653': A('10:00', '18:00', (1,)), '4026601': A('10:30', '20:00'), '4008107': A('10:00', '22:30'), '4008790': A('10:30', '20:00'),
 '2869896': A('08:30', '22:00'), '2925483': A('10:00', '20:00'), '130766': U, '2782626': A('00:00', '24:00'), '2910438': A('10:00', '20:00'), '2891826': A('11:00', '22:00'),
 '2861291': A('11:00', '22:30'), '4026606': A('10:00', '22:00'), '4008121': A('09:00', '22:00'), '2902005': A('10:00', '22:30'), '1303784': U, '3033472': A('00:00', '24:00'),
 '4026598': A('10:30', '20:00'), '2901373': A('10:30', '20:00'), '2812078': g((ALL, ('11:00', '14:30'), ('16:00', '20:00'))), '702551': A('09:00', '22:00'), '2822343': A('00:00', '24:00'),
}
assert len(GOLD) == 50
def sql(q): return [l.split('|') for l in subprocess.run(['docker', 'exec', '-i', 'busan-tourist-guide-db-1', 'psql', '-U', 'safetrip', '-d', 'safetrip_common', '-At'], input=q.encode(), capture_output=True).stdout.decode('utf-8').replace('\r', '').strip().split('\n') if l]
ids = ','.join(f"'{k}'" for k in GOLD)
ST = {r[0]: (r[1], r[2]) for r in sql(f'select content_id, hours_status, name from place where content_id in ({ids})')}
H = {}
for c, d, o, e in sql(f'select content_id, day_of_week, open_min, close_min from place_hours h join place p on p.id=h.place_id where content_id in ({ids})'): H.setdefault(c, {}).setdefault(int(d), []).append([int(o), int(e)])
ok = un = 0; f = {'여는 시각': [0, 0], '닫는 시각': [0, 0], '휴무 요일': [0, 0]}; wrong = []
for c, gold in GOLD.items():
    got = None if ST[c][0] != 'PARSED' else {d: sorted(s) for d, s in H.get(c, {}).items()}
    if got is None: un += 1
    good = got == gold; ok += good
    if not good: wrong.append((c, ST[c][1], gold, got))
    for k, fn in (('여는 시각', lambda x: {d: [s[0] for s in v] for d, v in x.items()}), ('닫는 시각', lambda x: {d: [s[1] for s in v] for d, v in x.items()}), ('휴무 요일', lambda x: sorted(set(ALL) - set(x)))):
        f[k][1] += 1; f[k][0] += (fn(gold) if gold is not None else None) == (fn(got) if got is not None else None)
print('완전 일치', f'{ok}/50', '불확실로 둔 것', un, {k: f'{a}/{b}' for k, (a, b) in f.items()})
pv = [c for c in GOLD if GOLD[c] is not None and ST[c][0] == 'PARSED']
print('구조화한 것 중 정답', sum({d: sorted(s) for d, s in H.get(c, {}).items()} == GOLD[c] for c in pv), '/', sum(ST[c][0] == 'PARSED' for c in GOLD))
for w in wrong: print(w)
