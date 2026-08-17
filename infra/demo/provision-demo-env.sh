#!/usr/bin/env bash
# =============================================================================
# infra/demo/provision-demo-env.sh — 데모 호스트의 `.env` 를 프로비저닝한다 (TASK-MONO-550)
# =============================================================================
# `.env.example` 를 가진 각 프로젝트에 `.env` 가 없으면 **예제에서 그대로 복사**한다.
# 이미 있으면 건드리지 않는다(멱등).
#
# 왜 필요한가 — 데모는 이것 없이 뜨지 못했다
# -----------------------------------------------------------------------------
# `check-env-preflight.sh`(TASK-MONO-548)는 `.env` 없이 뜨면 **compose 폴백 자격이
# 데이터 볼륨에 각인**되는 도메인(wms · ecommerce)의 기동을 중단시킨다. 정당한 가드다.
# 그런데 **AMI 는 fresh clone 이고 `projects/*/.env` 는 gitignored 라 존재할 수 없다.**
#
# 2026-08-17 실측: 재굽기한 AMI 로 `terraform apply` 한 첫 부팅에서 **컨테이너 0개**.
# 9개 도메인 전부 `{"state":"down","total":0}` 이었고 아무도 에러를 보지 못했다 —
# `demo-stack.service` 만 조용히 실패해 있었다.
#
# 🔴 **가드를 끄는 방향으로 고치지 않는다.** 가드가 막는 위험(볼륨에 각인된 잘못된
# 비밀번호는 재기동으로 절대 고쳐지지 않는다)은 실재한다. 가드가 요구하는 것을
# **정당하게 충족**한다 — 그 스크립트 자신이 안내하는 처방(`cp .env.example .env`)이
# 바로 이것이고, 사람이 손으로 하던 것을 부팅 경로가 하게 만든다.
#
# 왜 packer 가 아니라 여기인가
# -----------------------------------------------------------------------------
# `demo-boot.sh` 가 부팅 계약의 소유자다(MONO-366). 계약을 packer 로 빼면 저장소가
# 계약을 바꿔도 AMI 는 모른다 — 366 이 정확히 그 드리프트를 고쳤다. 여기에 두면
# CI 가드가 **실제로 실행해서** 검증할 수 있다(가드 (z3)).
#
# 그리고 이 경로는 **데모 호스트 전용**이다. 로컬 개발자가 `demo-up.sh` 를 직접 부르면
# 이 스크립트를 거치지 않으므로 preflight 의 보호를 그대로 받는다 — 자기 머신의
# 데이터 볼륨은 영속적이고, 자격은 의도해서 만들어야 한다.
#
# 자격 강도에 대해
# -----------------------------------------------------------------------------
# `.env.example` 의 값은 예시 자격이다. 그러나 **이 변경은 보안 수준을 낮추지 않는다** —
# 지금까지 데모 호스트는 그보다 나을 것 없는 **compose 폴백**으로 떠 왔다. 강한 자격은
# 별개 판단이고, 그때는 `.env.example` 이 아니라 생성기가 출처여야 한다.
#
# 사용법:
#   bash infra/demo/provision-demo-env.sh          # 없는 것만 만든다
#   PROVISION_ENV_ROOT=/tmp/tree bash …            # 다른 트리에 대고 (가드가 쓴다)
# =============================================================================
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="${PROVISION_ENV_ROOT:-$(cd "$HERE/../.." && pwd)}"

created=0
skipped=0
missing=0

# `.env.example` 을 가진 프로젝트만 대상이다. 목록을 손으로 나열하지 않는다 —
# 새 프로젝트가 생기면 자동으로 범위에 들어와야 하고, 하드코딩한 목록은 트리와
# 어긋나도 아무도 모른다(이 저장소가 이미 두 번 데인 실패 모드).
shopt -s nullglob
for example in "$ROOT"/projects/*/.env.example; do
  dir="$(dirname "$example")"
  env_file="$dir/.env"
  name="$(basename "$dir")"

  if [ -f "$env_file" ]; then
    skipped=$(( skipped + 1 ))
    continue
  fi

  # 생성원은 `.env.example` 하나여야 한다. 여기서 값을 바꾸면 preflight 이 막으려던
  # 바로 그 갈라짐(예제 ≠ 실제)을 새로 만드는 것이다.
  cp "$example" "$env_file"
  created=$(( created + 1 ))
  echo "[provision-env] $name/.env 생성 (.env.example 에서 복사)"
done
shopt -u nullglob

if [ "$created" -eq 0 ] && [ "$skipped" -eq 0 ]; then
  # 0건은 "할 일이 없다" 가 아니라 "아무것도 못 찾았다" 일 수 있다. 트리가 예상과
  # 다르면(경로 변경·잘못된 ROOT) 조용히 통과시키지 않는다 — 그러면 부팅은 다시
  # preflight 에서 죽고, 이 스크립트는 성공했다고 보고한 뒤다.
  echo "[provision-env] ✖ projects/*/.env.example 을 하나도 찾지 못했습니다 (ROOT=$ROOT)" >&2
  echo "[provision-env]   경로가 바뀌었거나 ROOT 가 잘못됐습니다. 0건을 '할 일 없음' 으로 읽지 않습니다." >&2
  missing=1
fi

echo "[provision-env] 요약 — 생성 $created · 기존유지 $skipped"
exit "$missing"
