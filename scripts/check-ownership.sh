#!/usr/bin/env bash
# 병합 전에 "내 영역만 고쳤는지" 검사한다. PR 없이 브랜치 병합할 때 쓰는 안전장치.
# 사용: scripts/check-ownership.sh <course|photo|emergency|common> [기준브랜치=main]
# 종료코드: 0 = 통과(공용 변경이 있으면 안내 출력), 1 = 남의 영역 수정 발견
set -euo pipefail
OWNER="${1:-}"; BASE="${2:-main}"
case "$OWNER" in course|photo|emergency|common) ;; *) echo "사용법: $0 <course|photo|emergency|common> [base]"; exit 2;; esac

B=backend/src
owner_of() {
  case "$1" in
    $B/main/java/kr/busan/safetrip/course/*|$B/test/java/kr/busan/safetrip/course/*|frontend/lib/features/course/*|frontend/lib/data/api/course_api.dart) echo course; return;;
    $B/main/java/kr/busan/safetrip/photo/*|$B/test/java/kr/busan/safetrip/photo/*|frontend/lib/features/photo/*|frontend/lib/data/api/photo_api.dart) echo photo; return;;
    $B/main/java/kr/busan/safetrip/emergency/*|$B/test/java/kr/busan/safetrip/emergency/*|frontend/lib/features/emergency/*|frontend/lib/data/api/emergency_api.dart) echo emergency; return;;
  esac
  if [[ "$1" =~ ^backend/src/(main|test)/resources/(course|photo|emergency)/ ]]; then echo "${BASH_REMATCH[2]}"; return; fi
  if [[ "$1" =~ ^backend/src/main/resources/db/migration/V1[0-9]__ ]]; then echo course; return; fi
  if [[ "$1" =~ ^backend/src/main/resources/db/migration/V2[0-9]__ ]]; then echo photo; return; fi
  if [[ "$1" =~ ^backend/src/main/resources/db/migration/V3[0-9]__ ]]; then echo emergency; return; fi
  echo common
}

bad=(); common=()
while IFS= read -r f; do
  [ -z "$f" ] && continue
  o=$(owner_of "$f")
  if [ "$o" = "$OWNER" ]; then continue; fi
  if [ "$o" = "common" ]; then common+=("$f"); else bad+=("$f  ($o 담당)"); fi
done < <(git diff --name-only "$BASE"...HEAD)

if [ ${#bad[@]} -gt 0 ]; then
  echo "❌ 남의 영역 파일이 수정되었습니다:"; printf '   %s\n' "${bad[@]}"
  echo "   → 해당 담당에게 요청하거나, 이 변경을 되돌리세요."; exit 1
fi
if [ ${#common[@]} -gt 0 ] && [ "$OWNER" != "common" ]; then
  echo "⚠️  공용 파일이 수정되었습니다. 병합 메시지에 '명세 대조' 3줄(근거/영향/확인)을 적고 해리에게 알리세요:"
  printf '   %s\n' "${common[@]}"
elif [ ${#common[@]} -gt 0 ]; then
  echo "ℹ️  공용 변경입니다. 병합 메시지에 '명세 대조' 3줄(근거/영향/확인)을 적으세요:"; printf '   %s\n' "${common[@]}"
fi
echo "✅ 소유권 검사 통과 ($OWNER)"
