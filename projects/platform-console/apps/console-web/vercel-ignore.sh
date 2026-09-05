#!/usr/bin/env bash
# =============================================================================
# kanggle-console 의 Vercel 무시 규칙 — **경로 목록의 집**  (TASK-MONO-585)
# =============================================================================
# `vercel.json` 의 `ignoreCommand` 가 이 파일을 부른다. 왜 목록이 여기 있는가:
#
# 🔴 **Vercel 스키마는 `ignoreCommand` 를 256자로 제한한다.** `TASK-MONO-562` 는
#    pathspec 5개를 그 문자열에 직접 넣었고 **261자**가 됐다. 5자 초과다.
#    그 결과 `vercel.json` 이 거부되고 **모든 배포가 0초에 죽었다** — 빌드 로그도
#    남지 않고, 상태 문구는 `Deployment failed.` + project-configuration 링크였다.
#    형제의 `VERCEL.md` 가 첫 문단에서 경고한 그 클래스이고(557 은 모르는 키로),
#    562 는 **다른 문으로 같은 방에 들어갔다.**
#
# 🔵 그래서 목록을 문자열 밖으로 뺀다. 여기엔 길이 제한이 없고, 경로를 하나 더해도
#    `vercel.json` 은 길어지지 않는다. 제한 자체는
#    `scripts/check-vercel-build-triggers.sh` 의 칸 (5)가 지킨다.
#
# 이 파일은 **프로젝트 소유**다 — 목록이 platform-console 의 사실이기 때문이다.
# 공용 판정자(`/scripts/vercel-should-build.sh`)는 프로젝트를 몰라야 하므로
# pathspec 을 인자로 받는다(HARDSTOP-03).
#
# 종료코드 규약은 판정자와 같다: **0 = 건너뜀 · 1 = 빌드**. 판정 불가는 빌드다.
# -----------------------------------------------------------------------------
set -uo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || {
  echo "[console-ignore] ✖ git 저장소를 찾지 못했습니다 — 빌드를 진행합니다." >&2
  exit 1
}

# --- 이 앱의 빌드에 실제로 들어가는 것 ---------------------------------------
#  apps/console-web/          이 앱 전부 (vercel.json · package.json · lockfile 포함)
#  infra/demo/backend-resolver 해석기 구현이 사는 자리
#  scripts/vercel-*.sh        판정자와 이 래퍼. 고쳤으면 한 번은 행사돼야 한다
#
# 🔴 형제 둘과 달리 이 앱은 **pnpm 워크스페이스 멤버가 아니다** — `pnpm-lock.yaml` 이
#    앱 디렉터리 안에 있고 `pnpm-workspace.yaml` 은 존재하지 않는다. 그래서 fan 이
#    별도로 나열하는 세 줄(`package.json`·`pnpm-lock.yaml`·`pnpm-workspace.yaml`)이
#    여기서는 첫 줄에 이미 포함된다. 없는 파일을 나열하면 «모집단이 넓어 안전» 이 아니라
#    **목록이 무엇을 말하는지 알 수 없게** 된다.
#
# 🔴 `projects/platform-console/apps/console-bff/**` 는 일부러 뺐다 — Spring Boot BFF 라
#    Next 빌드에 들어가지 않는다. 🔴 목록을 좁히는 쪽이 위험하다: 빠뜨린 경로는
#    "배포 실패" 가 아니라 **"조용히 건너뜀"** 으로 나타난다.
#
# 🔴🔴 `infra/demo/backend-resolver` 가 없으면 그 패키지만 바뀐 커밋이 **배포를 조용히
#    건너뛰고**, 앱은 낡은 해석기를 계속 서빙한다(fan 이 `TASK-MONO-614` 에서 같은 줄을
#    같은 이유로 넣었다).
SPECS=(
  ':/projects/platform-console/apps/console-web'
  ':/infra/demo/backend-resolver'
  ':/scripts/vercel-should-build.sh'
)

exec bash "$ROOT/scripts/vercel-should-build.sh" "${SPECS[@]}"
