#!/usr/bin/env python3
# [공용] SRS·SDD Word 원본(.docx) → 저장소 사본(docs/spec/*.md) 변환기
# 규칙: docs/spec/README.md
# 사용: python3 scripts/docs/docx2md.py srs <원본.docx> v1.5 2026-12-01
#       python3 scripts/docs/docx2md.py sdd <원본.docx> v0.6 2026-12-01
# 필요: pandoc 3.x (brew install pandoc)
import hashlib, os, re, shutil, subprocess, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SPEC = os.path.join(ROOT, "docs", "spec")
TITLES = {"srs": "SRS (소프트웨어 요구사항 명세서)", "sdd": "SDD (소프트웨어 설계 명세서)"}


def main():
    if len(sys.argv) != 5 or sys.argv[1] not in TITLES:
        sys.exit("사용: docx2md.py {srs|sdd} <원본.docx> <버전 vX.Y> <날짜 YYYY-MM-DD>")
    kind, src, version, date = sys.argv[1:]
    if not re.fullmatch(r"v\d+\.\d+", version) or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", date):
        sys.exit("버전은 vX.Y, 날짜는 YYYY-MM-DD 형식")
    sha = hashlib.sha256(open(src, "rb").read()).hexdigest()
    media = os.path.join(SPEC, "media", kind)
    shutil.rmtree(media, ignore_errors=True)
    out = subprocess.run(
        ["pandoc", os.path.abspath(src), "-t", "gfm", "--wrap=none", f"--extract-media=media/{kind}"],
        cwd=SPEC, check=True, capture_output=True, text=True).stdout
    pv = subprocess.run(["pandoc", "--version"], capture_output=True, text=True).stdout.split("\n")[0]
    # 1) 공개 저장소이므로 학번(7자리 괄호)은 지움
    out = re.sub(r"\((\d{7})\)", "", out)
    # 2) 버전마다 조금씩 바뀌어 diff를 어지럽히는 그림 크기 속성 제거
    out = re.sub(r'\s+style="width:[^"]*"', "", out)
    # 3) 줄 끝 공백 제거
    out = "\n".join(l.rstrip() for l in out.splitlines()) + "\n"
    head = (
        "---\n"
        f"doc: {kind.upper()}\n"
        f"version: {version}\n"
        f"date: {date}\n"
        f"source: {os.path.basename(src)}\n"
        f"source_sha256: {sha}\n"
        f"generated_by: scripts/docs/docx2md.py ({pv})\n"
        "---\n\n"
        f"> **자동 변환본 — 직접 고치지 않음.** {TITLES[kind]} {version} Word 원본을 변환한 사본임.\n"
        "> 바꿀 내용은 `docs/spec/PENDING.md`에 적고, Word 원본을 고친 뒤 이 스크립트로 다시 만듦. 규칙: `docs/spec/README.md`\n\n"
    )
    with open(os.path.join(SPEC, f"{kind}.md"), "w", encoding="utf-8") as f:
        f.write(head + out)
    print(f"docs/spec/{kind}.md ← {os.path.basename(src)} ({version}, sha256 {sha[:12]}…)")


if __name__ == "__main__":
    main()
