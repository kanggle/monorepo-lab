#!/usr/bin/env bash
# =============================================================================
# web-store 의 Vercel 무시 규칙 — **경로 목록의 집**  (TASK-MONO-582)
# =============================================================================
# `vercel.json` 의 `ignoreCommand` 가 이 파일을 부른다. 왜 목록이 JSON 안이 아니라
# 여기 있는가 — 형제가 **그 자리에서 죽었기 때문이다**:
#
# 🔴 **Vercel 스키마는 명령 문자열에 `maxLength: 256` 을 건다.** `TASK-MONO-562` 가
#    `kanggle-fan` 의 `ignoreCommand` 에 pathspec 5개를 직접 넣어 **261자**(+5)가 됐고,
#    그래서 `vercel.json` 이 거부되어 **모든 배포가 0초에 죽었다** — 빌드 로그조차 남지
#    않고 상태 문구는 `Deployment failed.` + project-configuration 링크였다.
#    `TASK-MONO-563` 이 목록을 문자열 밖으로 빼서 261 → 99자로 줄였고, 이 파일은 그
#    교훈을 **처음부터** 따른다. 여기엔 길이 제한이 없다.
#
# 🔵 론처(`infra/demo/aws/site/vercel.json`)는 아직 인라인 방식이다. 그것은 *"짧아서
#    아직 안 죽은"* 쪽이지 본받을 쪽이 아니다 — 새 프로젝트는 래퍼로 간다.
#
# 이 파일은 **프로젝트 소유**다: 목록이 ecommerce 의 사실이기 때문이다. 공용 판정자
# (`/scripts/vercel-should-build.sh`)는 프로젝트를 몰라야 하므로 pathspec 을 인자로
# 받는다(CLAUDE.md HARDSTOP-03).
#
# 종료코드 규약은 판정자와 같다: **0 = 건너뜀 · 1 = 빌드**. 판정 불가는 빌드다.
# -----------------------------------------------------------------------------
set -uo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || {
  echo "[web-store-ignore] ✖ git 저장소를 찾지 못했습니다 — 빌드를 진행합니다." >&2
  exit 1
}

# --- 이 앱의 빌드에 실제로 들어가는 것 ---------------------------------------
#  apps/web-store         이 앱 (`vercel.json` 과 이 파일 포함)
#  packages/              워크스페이스 멤버 전부 — `@repo/{api-client,types,ui,utils}` 는
#                         `transpilePackages` 로 **소스가 그대로 빌드에 들어간다**.
#                         `@repo/tsconfig` 도 빌드 경로다.
#  package.json           워크스페이스 루트. `packageManager` 가 여기 있다
#  pnpm-lock.yaml         install 이 해석되는 자리
#  pnpm-workspace.yaml    멤버 목록 (`apps/*` + `packages/*`)
#  scripts/vercel-should-build.sh  판정자. 고쳤으면 한 번은 행사돼야 한다
#
# 🔴 **형제 앱(`apps/` 의 나머지)은 일부러 뺐다** — 같은 워크스페이스에 있지만 web-store 의
#    산출물에 들어가지 않는다. 의존이 생기면 `packages/` 를 거치므로 그때도 여기가 문다.
#    (`pnpm-lock.yaml` 이 바뀌는 변경은 어차피 위 줄이 잡는다.)
# 🔴 **Java 서비스(`projects/ecommerce-microservices-platform/apps/*-service`)도 뺐다** —
#    Next 빌드에 들어가지 않는다.
#
# 🔴🔴 **목록을 좁히는 쪽이 위험하다.** 빠뜨린 경로의 증상은 *"배포가 실패했다"* 가 아니라
#    **"배포가 조용히 건너뛰어졌다"** 이고, CI 는 초록이며 사이트는 마지막 성공 배포를 계속
#    서빙하므로 URL 을 찔러도 200 이다. **아무도 안 본다.** 의심스러우면 넣어라.
PATHSPECS=(
  ':/projects/ecommerce-microservices-platform/apps/web-store'
  ':/projects/ecommerce-microservices-platform/packages'
  ':/projects/ecommerce-microservices-platform/package.json'
  ':/projects/ecommerce-microservices-platform/pnpm-lock.yaml'
  ':/projects/ecommerce-microservices-platform/pnpm-workspace.yaml'
  ':/scripts/vercel-should-build.sh'
)

exec bash "$ROOT/scripts/vercel-should-build.sh" "${PATHSPECS[@]}"
