#!/usr/bin/env bash
# Docker 디스크 청소 — 안 쓰는 컨테이너/이미지/빌드캐시 일괄 회수.
#
# 이 호스트(Windows + Rancher Desktop WSL2)는 vhdx 가 한 번 커지면 prune 만으론
# C: 로 안 돌아온다(=프로젝트 메모리 env_rancher_desktop_vhdx_no_shrink). 이 스크립트는
# "VM 내부" 를 비워 vhdx 증식을 억제한다. 실제 C: 회수는 compact-rd-vhdx.ps1(관리자) 별건.
#
# 안전: 모두 "안 쓰는 것만" 지운다(실행 중 컨테이너·그 이미지·named 볼륨은 보호).
#   - container prune : 멈춘 컨테이너(Testcontainers 고아 등)
#   - image prune     : dangling(<none>) 이미지 — 재빌드로 태그 떨어진 옛 이미지
#   - builder prune   : 빌드 캐시
# 사용:
#   ./scripts/docker-cleanup.sh            # 기본(컨테이너+dangling이미지+캐시)
#   ./scripts/docker-cleanup.sh --images   # + 안 쓰는 "태그된" 이미지까지(-a, 더 공격적)
#   ./scripts/docker-cleanup.sh --dry-run  # 회수 가능량만 표시
set -euo pipefail

AGGRESSIVE=0; DRY=0
for a in "$@"; do
  case "$a" in
    --images) AGGRESSIVE=1 ;;
    --dry-run) DRY=1 ;;
    -h|--help) grep '^#' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
  esac
done

echo "=== 청소 전 ==="
docker system df

if [ "$DRY" = "1" ]; then
  echo "[dry-run] 위 RECLAIMABLE 열이 회수 가능량. 실제 실행은 옵션 없이."
  exit 0
fi

echo "=== 멈춘 컨테이너 정리 ==="
docker container prune -f
echo "=== dangling 이미지 정리 ==="
docker image prune -f
echo "=== 빌드 캐시 정리 ==="
docker builder prune -f
if [ "$AGGRESSIVE" = "1" ]; then
  echo "=== 안 쓰는 태그 이미지까지 정리 (-a) ==="
  docker image prune -af
fi

echo "=== 청소 후 ==="
docker system df
echo ""
echo ">>> VM 내부를 비웠습니다. C: 실제 회수는 관리자에서 compact-rd-vhdx.ps1 필요"
echo "    (이유: WSL2 vhdx 는 prune 후에도 호스트에서 안 줄어듦)."
