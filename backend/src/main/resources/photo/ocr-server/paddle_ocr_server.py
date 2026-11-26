# [사진 담당] COMP-P05 PaddleOCR 로컬 서버 (PaddleOcrEngine 이 호출한다)
# 요구사항: FR-P004, NFR-P002, NFR-P005 / ADR-P05(로컬 OCR), #40 PoC 결과(server 검출 + 한국어 인식)
# 수정 규칙: 사진 담당만 수정.
#
# 실행: python3 paddle_ocr_server.py [--port 8866] [--device gpu:0]
#   POST /ocr    본문 = 이미지 바이트(jpg/png) → {"blocks":[{"text","conf","x","y","w","h"}], "ms"}
#   GET  /health → {"ok": true}
# 사진은 메모리에서만 다루고 파일·로그에 남기지 않는다(NFR-P002). 로그에는 상태 코드와 처리 시간만 쓴다.
import argparse
import json
import time
from http.server import BaseHTTPRequestHandler, HTTPServer

import cv2
import numpy as np
from paddleocr import PaddleOCR

MAX_BYTES = 10 * 1024 * 1024  # FR-P002 과 같은 상한

ocr = None


def recognize(data):
    img = cv2.imdecode(np.frombuffer(data, np.uint8), cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("이미지를 읽지 못함")
    blocks = []
    for r in ocr.predict(img):
        d = r.json["res"]
        for text, score, box in zip(d["rec_texts"], d["rec_scores"], d["rec_boxes"]):
            x1, y1, x2, y2 = (int(v) for v in box)
            blocks.append({"text": text, "conf": round(float(score), 4), "x": x1, "y": y1, "w": x2 - x1, "h": y2 - y1})
    return blocks


class Server(HTTPServer):
    def handle_error(self, request, client_address):
        print("ocr 요청 처리 오류", flush=True)  # 기본 동작(전체 traceback 출력)은 쓰지 않는다


class Handler(BaseHTTPRequestHandler):
    def _send(self, code, body):
        raw = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.send_header("Connection", "close")  # 요청마다 연결을 닫는다(한 번에 한 요청만 처리하므로 재사용하지 않음)
        self.end_headers()
        try:
            self.wfile.write(raw)
        except BrokenPipeError:
            pass  # 백엔드가 제한시간으로 먼저 끊은 경우

    def do_GET(self):
        if self.path == "/health":
            self._send(200, {"ok": True})
        else:
            self._send(404, {"error": "not found"})

    def do_POST(self):
        if self.path != "/ocr":
            self._send(404, {"error": "not found"})
            return
        n = int(self.headers.get("Content-Length") or 0)
        if n <= 0 or n > MAX_BYTES:
            self._send(413 if n > MAX_BYTES else 400, {"error": "bad size"})
            return
        data = self.rfile.read(n)
        t0 = time.time()
        try:
            blocks = recognize(data)
        except ValueError:
            self._send(400, {"error": "bad image"})
            return
        finally:
            del data  # 사진 바이트를 바로 버린다(NFR-P002)
        self._send(200, {"blocks": blocks, "ms": int((time.time() - t0) * 1000)})

    def log_message(self, fmt, *args):
        pass  # 기본 접근 로그는 쓰지 않는다

    def log_request(self, code="-", size="-"):
        print(f"ocr {self.command} {self.path} {code}", flush=True)


def main():
    global ocr
    p = argparse.ArgumentParser()
    p.add_argument("--host", default="127.0.0.1")
    p.add_argument("--port", type=int, default=8866)
    p.add_argument("--det", default="PP-OCRv5_server_det")  # PoC: mobile 검출은 간판을 거의 못 잡았다(#40)
    p.add_argument("--rec", default="korean_PP-OCRv5_mobile_rec")
    p.add_argument("--device", default="cpu")  # NVIDIA GPU 가 있으면 gpu:0 (paddlepaddle-gpu 필요, README). #199: CPU 11초 → GPU 0.08초
    a = p.parse_args()
    t0 = time.time()
    ocr = PaddleOCR(text_detection_model_name=a.det, text_recognition_model_name=a.rec,
                    use_doc_orientation_classify=False, use_doc_unwarping=False,
                    use_textline_orientation=False, enable_mkldnn=False, device=a.device)
    ocr.predict(np.full((64, 64, 3), 255, np.uint8))  # 첫 요청이 느리지 않게 미리 한 번 돌린다
    print(f"PaddleOCR 준비 완료({a.device}) {time.time() - t0:.1f}s → http://{a.host}:{a.port}", flush=True)
    Server((a.host, a.port), Handler).serve_forever()  # 한 번에 한 요청(PaddleOCR 는 스레드 안전하지 않음)


if __name__ == "__main__":
    main()
