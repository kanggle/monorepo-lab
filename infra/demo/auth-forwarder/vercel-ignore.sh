#!/usr/bin/env bash
# =============================================================================
# kanggle-auth 의 Vercel 무시 규칙 — **경로 목록의 집**  (TASK-MONO-610)
# =============================================================================
# `vercel.json` 의 `ignoreCommand` 가 이 파일을 부른다. 왜 목록이 여기 있는가:
#
# 🔴 **Vercel 스키마는 `ignoreCommand` 를 256자로 제한한다.** `TASK-MONO-562` 는
#    pathspec 5개를 그 문자열에 직접 넣어 **261자**가 됐고, `vercel.json` 이 거부되어
#    **모든 배포가 0초에 죽었다** — 빌드 로그조차 남지 않았다. 그래서 목록은 문자열
#    밖에 산다. 제한 자체는 `scripts/check-vercel-build-triggers.sh` 칸 (5)가 지킨다.
#
# 이 파일은 **프로젝트 소유**다. 공용 판정자(`/scripts/vercel-should-build.sh`)는
# 프로젝트를 몰라야 하므로 pathspec 을 인자로 받는다(HARDSTOP-03).
#
# 종료코드 규약은 판정자와 같다: **0 = 건너뜀 · 1 = 빌드**. 판정 불가는 빌드다.
# -----------------------------------------------------------------------------
set -uo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || {
  echo "[auth-ignore] ✖ git 저장소를 찾지 못했습니다 — 빌드를 진행합니다." >&2
  exit 1
}

# --- 이 앱의 빌드에 실제로 들어가는 것 ---------------------------------------
#  infra/demo/auth-forwarder    앱 자신 (`package.json` · `pnpm-lock.yaml` · `vercel.json` 포함)
#  infra/demo/backend-resolver  🔴 그날 IP 를 얻는 **유일한 구현** (ADR-MONO-068 § D6 = B2).
#                               없으면 그 패키지만 바뀐 커밋이 배포를 **조용히 건너뛰고**,
#                               현관은 낡은 해석기를 계속 쓴다.
#  scripts/vercel-should-build.sh  공용 판정자. 고쳤으면 한 번은 행사돼야 한다.
#
# 🔴 목록을 좁히는 쪽이 위험하다 — 빠뜨린 경로는 «배포 실패» 가 아니라 **«조용히 건너뜀»**
#    으로 나타나고, 증상은 **CI 초록 · 사이트는 낡은 판**이라 아무도 안 본다.
# 🔵 이 앱은 어느 pnpm 워크스페이스의 멤버도 아니다(`B2` 가 루트 워크스페이스를 만들지
#    않기로 한 결과다) ⇒ 형제들과 달리 `pnpm-workspace.yaml` 항목이 **없다**.
SPECS=(
  ':/infra/demo/auth-forwarder'
  ':/infra/demo/backend-resolver'
  ':/scripts/vercel-should-build.sh'
)

exec bash "$ROOT/scripts/vercel-should-build.sh" "${SPECS[@]}"
