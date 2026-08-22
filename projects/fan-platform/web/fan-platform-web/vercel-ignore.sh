#!/usr/bin/env bash
# =============================================================================
# kanggle-fan 의 Vercel 무시 규칙 — **경로 목록의 집**  (TASK-MONO-563)
# =============================================================================
# `vercel.json` 의 `ignoreCommand` 가 이 파일을 부른다. 왜 목록이 여기 있는가:
#
# 🔴 **Vercel 스키마는 `ignoreCommand` 를 256자로 제한한다.** `TASK-MONO-562` 는
#    pathspec 5개를 그 문자열에 직접 넣었고 **261자**가 됐다. 5자 초과다.
#    그 결과 `vercel.json` 이 거부되고 **모든 배포가 0초에 죽었다** — 빌드 로그도
#    남지 않고, 상태 문구는 `Deployment failed.` + project-configuration 링크였다.
#    같은 파일의 `VERCEL.md` 가 첫 문단에서 경고한 그 클래스이고(557 은 모르는 키로),
#    562 는 **다른 문으로 같은 방에 들어갔다.**
#
# 🔵 그래서 목록을 문자열 밖으로 뺐다. 여기엔 길이 제한이 없고, 경로를 하나 더해도
#    `vercel.json` 은 길어지지 않는다. 제한 자체는
#    `scripts/check-vercel-build-triggers.sh` 의 칸 (5)가 지킨다.
#
# 이 파일은 **프로젝트 소유**다 — 목록이 fan-platform 의 사실이기 때문이다.
# 공용 판정자(`/scripts/vercel-should-build.sh`)는 프로젝트를 몰라야 하므로
# pathspec 을 인자로 받는다(HARDSTOP-03).
#
# 종료코드 규약은 판정자와 같다: **0 = 건너뜀 · 1 = 빌드**. 판정 불가는 빌드다.
# -----------------------------------------------------------------------------
set -uo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || {
  echo "[fan-ignore] ✖ git 저장소를 찾지 못했습니다 — 빌드를 진행합니다." >&2
  exit 1
}

# --- 이 앱의 빌드에 실제로 들어가는 것 ---------------------------------------
#  web/                     이 앱 + pnpm 워크스페이스 `web/*` 멤버 전부 (vercel.json 포함)
#  package.json             워크스페이스 루트. `packageManager` 가 여기 있다
#  pnpm-lock.yaml           install 이 해석되는 자리
#  pnpm-workspace.yaml      멤버 목록
#  scripts/vercel-*.sh      판정자와 이 래퍼. 고쳤으면 한 번은 행사돼야 한다
#
# 🔴 `projects/fan-platform/apps/**` 는 일부러 뺐다 — Java 서비스라 Next 빌드에
#    들어가지 않는다. 🔴 목록을 좁히는 쪽이 위험하다: 빠뜨린 경로는 "배포 실패" 가
#    아니라 **"조용히 건너뜀"** 으로 나타난다.
# 🔴🔴 **이 목록에는 소비자가 둘이다** (TASK-MONO-564):
#   ① Vercel 의 `ignoreCommand` — 이 커밋에 배포를 구울지 결정한다.
#   ② `check-fan-fresh.sh` — "서빙 중인 판이 최신인가" 의 **기대값**을 이 목록으로 계산한다.
# 둘은 **같은 모집단이어야 한다.** 어긋나면 판정자가 *건강한 배포에 빨간불을 켜거나*
# (기대값이 트리거보다 넓을 때) *죽은 배포를 신선하다고* 한다(좁을 때).
# 그래서 목록은 여기 한 벌만 있고, 판정자는 이 파일을 **읽는다**(복사하지 않는다).
SPECS=(
  ':/projects/fan-platform/web'
  ':/projects/fan-platform/package.json'
  ':/projects/fan-platform/pnpm-lock.yaml'
  ':/projects/fan-platform/pnpm-workspace.yaml'
  ':/scripts/vercel-should-build.sh'
)

exec bash "$ROOT/scripts/vercel-should-build.sh" "${SPECS[@]}"
