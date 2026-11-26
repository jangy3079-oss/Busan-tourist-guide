#!/usr/bin/env python3
"""
[긴급 담당] NFR-E001(E01 <= 1s, 20회 최대값) · NFR-E002(E02 60문장 p95 <= 8s) HTTP 성능 측정 스크립트 (#129).
사용: python3 backend/src/test/resources/emergency/perf/measure_perf.py --base-url http://localhost:8083
"""
import argparse
import csv
import datetime
import json
import math
import pathlib
import time
import urllib.error
import urllib.request


E01_SAMPLES = [
    ("ko", "엄마가 갑자기 쓰러지셨어요"),
    ("ko", "호텔에 불이 났어요"),
    ("ko", "누가 계속 따라와요"),
    ("ko", "여권 분실했어요"),
    ("ko", "해운대 맛집 추천해 주세요"),
    ("en", "My husband collapsed"),
    ("en", "There's a fire in the building"),
    ("en", "A man is following me"),
    ("en", "I have a headache"),
    ("en", "I want to see fireworks at Gwangalli"),
    ("ja", "父が倒れました"),
    ("ja", "火事です"),
    ("ja", "誰かにつけられています"),
    ("ja", "熱があります"),
    ("ja", "海雲台でおいしい刺身"),
    ("ko", "바다에 사람이 빠졌어요"),
    ("ko", "친구가계단에서떨어져피가안멈춰요"),
    ("en", "Someone stole my wallet"),
    ("ja", "強い地震が起きて津波警報が出ました"),
    ("ko", "숙소를 못 찾겠어요"),
]


def post_json(url: str, payload: dict, timeout: float = 30.0):
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            body = json.loads(resp.read().decode("utf-8"))
            ms = (time.perf_counter() - t0) * 1000.0
            return resp.status, ms, body
    except urllib.error.HTTPError as e:
        ms = (time.perf_counter() - t0) * 1000.0
        return e.code, ms, None


def percentile(sorted_vals: list[float], p: float) -> float:
    if not sorted_vals:
        return 0.0
    idx = min(len(sorted_vals) - 1, max(0, math.ceil(len(sorted_vals) * p) - 1))
    return sorted_vals[idx]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:8083")
    parser.add_argument("--csv", default=str(pathlib.Path(__file__).resolve().parent.parent / "eval-60.csv"))
    parser.add_argument("--out", default="")
    args = parser.parse_args()

    started_at = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=9))).isoformat(timespec="seconds")
    base = args.base_url.rstrip("/")

    # Warm-up
    post_json(f"{base}/api/v1/emergency/quick", {"text": "열이 나요", "lang": "ko"})
    post_json(
        f"{base}/api/v1/emergency/guide",
        {"text": "열이 나요", "lang": "ko", "lat": 35.1532, "lng": 129.1186},
    )

    # 1) E01 20 calls (NFR-E001: max <= 1000ms)
    e01_ms = []
    e01_non200 = 0
    for lang, text in E01_SAMPLES:
        status, ms, _ = post_json(f"{base}/api/v1/emergency/quick", {"text": text, "lang": lang})
        e01_ms.append(ms)
        if status != 200:
            e01_non200 += 1

    # 2) E02 60 cases (NFR-E002: p95 <= 8000ms)
    with open(args.csv, encoding="utf-8", newline="") as f:
        rows = list(csv.DictReader(f))

    e02_ms = []
    e02_non200 = 0
    e02_over8s = 0
    e02_need_type = 0
    e02_steps_fallback = 0
    e02_details = []
    for row in rows:
        status, ms, body = post_json(
            f"{base}/api/v1/emergency/guide",
            {"text": row["text"], "lang": row["lang"], "lat": 35.1532, "lng": 129.1186},
        )
        e02_ms.append(ms)
        if status != 200:
            e02_non200 += 1
        if ms > 8000.0:
            e02_over8s += 1
        if body:
            if body.get("needTypeSelection"):
                e02_need_type += 1
            if body.get("stepsFallback"):
                e02_steps_fallback += 1
            triage = body.get("triage") or {}
            e02_details.append({
                "id": row["id"],
                "lang": row["lang"],
                "ms": round(ms, 1),
                "status": status,
                "gotType": triage.get("type"),
                "gotUrgency": triage.get("urgency"),
                "topContact": body.get("topContact"),
                "stepsFallback": body.get("stepsFallback"),
            })

    e01_sorted = sorted(e01_ms)
    e02_sorted = sorted(e02_ms)
    finished_at = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=9))).isoformat(timespec="seconds")

    summary = {
        "startedAt": started_at,
        "finishedAt": finished_at,
        "baseUrl": base,
        "e01": {
            "count": len(e01_sorted),
            "non200": e01_non200,
            "minMs": round(e01_sorted[0], 1),
            "p50Ms": round(percentile(e01_sorted, 0.50), 1),
            "p95Ms": round(percentile(e01_sorted, 0.95), 1),
            "maxMs": round(e01_sorted[-1], 1),
            "passNfrE001": e01_sorted[-1] <= 1000.0 and e01_non200 == 0,
        },
        "e02": {
            "count": len(e02_sorted),
            "non200": e02_non200,
            "minMs": round(e02_sorted[0], 1),
            "p50Ms": round(percentile(e02_sorted, 0.50), 1),
            "p95Ms": round(percentile(e02_sorted, 0.95), 1),
            "maxMs": round(e02_sorted[-1], 1),
            "over8000Ms": e02_over8s,
            "needTypeSelection": e02_need_type,
            "stepsFallback": e02_steps_fallback,
            "passNfrE002": percentile(e02_sorted, 0.95) <= 8000.0 and e02_non200 == 0,
        },
        "e02Top5Slowest": sorted(e02_details, key=lambda x: x["ms"], reverse=True)[:5],
    }

    print(json.dumps(summary, ensure_ascii=False, indent=2))
    if args.out:
        pathlib.Path(args.out).write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
