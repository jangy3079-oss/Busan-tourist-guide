# [사진 담당] PaddleOCR 로컬 서버 실행 방법 (#41, #192)

사진 기능(간판 사진 → 가게 찾기)은 이 OCR 서버가 켜져 있어야 동작합니다.
서버가 꺼져 있으면 사진은 모두 "간판 글자를 읽지 못했어요"(`OCR_FAIL`)로 끝납니다. 백엔드는 정상으로 뜹니다.

- 엔진: PaddleOCR 3.7 (`PP-OCRv5_server_det` 검출 + `korean_PP-OCRv5_mobile_rec` 인식). 선택 근거는 #40
- 사진은 이 PC 안에서만 처리하고 파일·로그에 남기지 않습니다(NFR-P002)
- 속도(맥, 애플 실리콘): 장당 중간값 2.4초, 최대 4.0초. 백엔드 제한시간은 8초

## 1. 설치 (처음 한 번)
파이썬 **3.9~3.13**이 필요합니다(PaddlePaddle 3.3.1 배포 범위). 가상환경은 저장소 밖에 만듭니다.

macOS·Linux
```
python3 --version
python3 -m venv ~/.venvs/paddle-ocr
~/.venvs/paddle-ocr/bin/pip install -r backend/src/main/resources/photo/ocr-server/requirements.txt
```

Windows (PowerShell, 저장소 루트에서)
```
py --version
py -m venv $HOME\.venvs\paddle-ocr
& $HOME\.venvs\paddle-ocr\Scripts\pip install -r backend\src\main\resources\photo\ocr-server\requirements.txt
```

## 2. 실행 (시연·개발할 때마다, 백엔드와 다른 터미널 창에서)
macOS·Linux
```
cd backend/src/main/resources/photo/ocr-server
~/.venvs/paddle-ocr/bin/python paddle_ocr_server.py
```

Windows
```
cd backend\src\main\resources\photo\ocr-server
& $HOME\.venvs\paddle-ocr\Scripts\python paddle_ocr_server.py
```

- **첫 실행**은 모델(약 100MB)을 `~/.paddlex/official_models`에 내려받느라 1~2분 걸리고, 로그가 많이 나옵니다. 정상입니다
- 마지막 줄에 `PaddleOCR 준비 완료 ... → http://127.0.0.1:8866`이 뜨면 준비된 것입니다. 이 창은 켜 둡니다
- 끌 때는 `Ctrl+C`
- 포트를 바꾸려면 `--port 8877`처럼 줍니다(백엔드 주소도 3번처럼 같이 바꿈)

## 3. 백엔드가 보는 주소
기본은 `http://127.0.0.1:8866`(`photo/matching.yml`의 `ocr.paddleUrl`)입니다. 환경변수 `PHOTO_OCR_URL`이 있으면 그 값을 씁니다.

- 포트를 바꾼 경우: 백엔드를 켤 때 `PHOTO_OCR_URL=http://127.0.0.1:8877`
- 백엔드를 Docker 컨테이너에서 돌리는 경우: 컨테이너 안의 127.0.0.1은 컨테이너 자신이라 닿지 않습니다
  - Docker Desktop(맥·윈도우): `PHOTO_OCR_URL=http://host.docker.internal:8866`
  - 그래도 안 되면 OCR 서버를 `--host 0.0.0.0`으로 켭니다(같은 네트워크에서 접속할 수 있게 되므로 시연 때만)

## 4. 확인
```
curl -s http://127.0.0.1:8866/health
```
→ `{"ok": true}`

간판 사진 한 장으로 확인(아무 jpg):
```
curl -s --data-binary @사진.jpg http://127.0.0.1:8866/ocr
```
→ `{"blocks": [{"text": ..., "conf": ...}], "ms": 2400}` 처럼 글자와 처리 시간(ms)이 나옵니다.
그다음 앱의 사진 화면에서 간판 사진을 올려 `CONFIRMED`(찾은 가게)가 나오면 끝입니다.

## 자주 막히는 것
| 증상 | 원인·해결 |
|---|---|
| `health`에 아무 응답이 없음 | 서버가 아직 준비 중이거나 꺼짐. 2번 창의 마지막 줄 확인 |
| 사진이 모두 "글자를 읽지 못했어요" | OCR 서버가 꺼져 있거나 주소가 다름(3번). 백엔드 로그에 `photo.ocr fail`이 찍힘 |
| `No matching distribution found for paddlepaddle` | 파이썬 버전이 3.9~3.13 밖. 그 범위의 파이썬으로 가상환경을 다시 만듦 |
| 리눅스에서 `NotImplementedError ... oneDNN` | 서버 코드에서 이미 `enable_mkldnn=False`로 끔. 직접 PaddleOCR를 부를 때만 주의 |
