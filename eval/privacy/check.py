"""개인정보 비저장 점검(#36, NFR-C003·P002·P003·E004, AC-P05·E07).
표식을 넣은 문장·좌표·사진을 실제 서버에 보낸 뒤 서버 로그, 서버 디스크, DB 전체 덤프에서 표식을 찾는다.

사용: python eval/privacy/check.py <서버 컨테이너> <DB 컨테이너> [DB 이름 DB 사용자] [--retention]
서버와 DB 는 docker 로 떠 있어야 하고, 시험용 로컬 DB 에만 돌린다(공용 DB 금지). 남긴 코스 기록은 끝에 지운다.
"""
import json, subprocess, sys, uuid

sys.stdout.reconfigure(encoding='utf-8')
APP, DB = sys.argv[1], sys.argv[2]
NAME, USER = (sys.argv[3:5] + ['safetrip', 'safetrip'])[:2] if len(sys.argv) > 4 else ('safetrip', 'safetrip')
RUN = uuid.uuid4().hex[:6].translate(str.maketrans('0123456789', 'ghijklmnop'))  # 숫자가 섞이면 전화번호와 함께 가려진다
M = {k: f'{k}{RUN}' for k in ('COURSE', 'EDIT', 'EMERG', 'EMQUICK', 'EMGUIDE', 'BADJSON', 'LONG', 'PHOTO', 'FILE')}
PHONE, MAIL = '010-7412-8596', f'pii{RUN}@example.com'
LAT, LNG = '35.153741193', '129.118672229'


def sh(*a, stdin=None):
    r = subprocess.run(a, input=stdin, capture_output=True)
    return (r.stdout + r.stderr).decode('utf-8', 'replace')


def post(path, body, raw=False):
    data = body.encode() if raw else json.dumps(body, ensure_ascii=False).encode()
    out = sh('docker', 'exec', '-i', APP, 'curl', '-s', '-w', '\n%{http_code}', '-X', 'POST', 'http://localhost:8080/api/v1' + path,
             '-H', 'Content-Type: application/json', '--data-binary', '@-', stdin=data)
    text, _, code = out.rpartition('\n')
    try:
        return code, json.loads(text)
    except ValueError:
        return code, {}


sql = lambda q: sh('docker', 'exec', '-i', DB, 'psql', '-U', USER, '-d', NAME, '-At', '-c', q).strip()

# 1. 요청을 보낸다
sent = []
code, c = post('/courses', {'query': f'해운대 카페 3시간 {M["COURSE"]} {PHONE} {MAIL}', 'lang': 'ko'})
sent.append(('코스 생성', code, c.get('status')))
code, e = post(f'/courses/{c.get("courseId")}/edits', {'text': f'2번 빼줘 {M["EDIT"]} {PHONE}', 'lang': 'ko'})
sent.append(('코스 수정', code, e.get('status')))
code, _ = post(f'/courses/{c.get("courseId")}/narration', {'lang': 'ko'})
sent.append(('코스 설명', code, ''))
code, r = post('/courses', {'query': f'여권 잃어버렸어요 {M["EMERG"]}', 'lang': 'ko'})
sent.append(('코스에 긴급 문장', code, r.get('status')))
sent.append(('긴급 즉시', post('/emergency/quick', {'text': f'친구가 쓰러졌어요 {M["EMQUICK"]}', 'lang': 'ko'})[0], ''))
sent.append(('긴급 상세', post('/emergency/guide', {'text': f'배가 아파요 {M["EMGUIDE"]}', 'lang': 'ko', 'lat': float(LAT), 'lng': float(LNG)})[0], ''))
sent.append(('깨진 JSON', post('/courses', '{"query": "' + M['BADJSON'], raw=True)[0], ''))
sent.append(('500자 초과', post('/courses', {'query': M['LONG'] + '가' * 500, 'lang': 'ko'})[0], ''))
img = b'\xff\xd8\xff\xfe\x00' + bytes([len(M['PHOTO']) + 2]) + M['PHOTO'].encode() + b'\xff\xd9'
sh('docker', 'exec', '-i', APP, 'sh', '-c', 'cat > /root/pii_upload.jpg', stdin=img)
out = sh('docker', 'exec', APP, 'curl', '-s', '-w', '\n%{http_code}', '-X', 'POST', 'http://localhost:8080/api/v1/photo/match',
         '-F', f'image=@/root/pii_upload.jpg;filename={M["FILE"]}.jpg;type=image/jpeg', '-F', f'lat={LAT}', '-F', f'lng={LNG}', '-F', 'lang=ko')
sent.append(('사진 매칭', out.rsplit('\n', 1)[-1], ''))
sh('docker', 'exec', APP, 'rm', '/root/pii_upload.jpg')
# 최소 1px 실제 이미지로도 한 번 더 보낸다(형식 검사를 통과해 OCR 까지 가는 경로)
sh('docker', 'exec', APP, 'sh', '-c', "echo iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg== | base64 -d > /root/p.png")
out = sh('docker', 'exec', APP, 'curl', '-s', '-w', '\n%{http_code}', '-X', 'POST', 'http://localhost:8080/api/v1/photo/match',
         '-F', f'image=@/root/p.png;filename={M["FILE"]}.png;type=image/png', '-F', f'lat={LAT}', '-F', f'lng={LNG}')
sent.append(('사진 매칭(실제 png)', out.rsplit('\n', 1)[-1], ''))
sh('docker', 'exec', APP, 'rm', '/root/p.png')

# 2. 남은 곳을 찾는다
logs = sh('docker', 'logs', APP)
dump = sh('docker', 'exec', DB, 'pg_dump', '-U', USER, '-d', NAME, '--data-only', '--column-inserts')
assert 'course_plan' in dump, '덤프 실패'
needles = list(M.values()) + [PHONE, PHONE.replace('-', ''), MAIL, LAT[:9], LNG[:10]]
disk = sh('docker', 'exec', APP, 'sh', '-c', "grep -rlaE '" + '|'.join(needles).replace('.', '\\.') + "' /tmp /var /root /home /app 2>/dev/null")
imgs = sh('docker', 'exec', APP, 'sh', '-c', "find /tmp /var /root /home -type f \\( -name '*.jpg' -o -name '*.png' -o -name 'upload_*' \\) -newer /proc/1/cmdline 2>/dev/null").strip()

ALLOWED_DB = {M['COURSE'], M['EDIT']}  # 관광 요청·수정 문장은 마스킹해 30일 보관(DATA-C07, FR-C015). 긴급 문장·좌표·사진은 어디에도 남으면 안 된다
bad = 0
print('보낸 요청:', *[f'{n}={c}{("/" + s) if s else ""}' for n, c, s in sent], sep='\n  ')
for n in needles:
    l, d = logs.count(n), dump.count(n)
    ok = l == 0 and (d == 0 or n in ALLOWED_DB)
    bad += not ok
    print(f'{"통과" if ok else "실패"}  {n[:14]:<14} 로그 {l} DB {d}{" (허용: 마스킹한 관광 문장)" if d and n in ALLOWED_DB else ""}')
print('디스크에 표식이 있는 파일:', disk.strip() or '없음', '| 남은 이미지·업로드 파일:', imgs or '없음')
bad += bool(disk.strip()) + bool(imgs)
row = sql(f"select query_text from course_plan where query_text like '%{M['COURSE']}%' limit 1")
print('저장된 관광 문장:', row)
bad += '[phone]' not in row or '[email]' not in row

# 3. 30일 삭제: 서버를 APP_RETENTION_CRON='*/5 * * * * *' 로 띄웠을 때만 확인한다
if '--retention' in sys.argv:
    import time
    for d in (29, 31):
        sql(f"insert into course_plan(query_text, created_at) values ('OLD{d}{RUN}', now() - interval '{d} days')")
        sql(f"insert into photo_match_log(ocr_text, decision, created_at) values ('OLD{d}{RUN}', 'NOT_FOUND', now() - interval '{d} days')")
    time.sleep(12)
    for tb, col in (('course_plan', 'query_text'), ('photo_match_log', 'ocr_text')):
        left = sql(f"select {col} from {tb} where {col} like 'OLD%{RUN}'")
        ok = left == f'OLD29{RUN}'
        bad += not ok
        print(f'{"통과" if ok else "실패"}  30일 삭제 {tb}: 남은 행 {left or "없음"}')
    sql(f"delete from photo_match_log where ocr_text like 'OLD%{RUN}'")
sql(f"delete from course_plan where query_text like '%{RUN}%'")
print('결과:', '통과' if not bad else f'실패 {bad}건')
sys.exit(bool(bad))
