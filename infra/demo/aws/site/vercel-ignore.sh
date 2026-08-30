#!/usr/bin/env bash
# =============================================================================
# infra/demo/aws/site/vercel-ignore.sh — 론처(`kanggle-portfolio`)의 빌드 판정 진입점
# =============================================================================
# TASK-MONO-607.
#
# 왜 이 파일이 생겼나 — **pathspec 이 두 곳에 있으면 한 곳만 고쳐진다**
# -----------------------------------------------------------------------------
# 이 프로젝트만 pathspec 을 `vercel.json` 의 `ignoreCommand` 에 **인라인**으로 들고 있었다.
# 형제 둘(`fan-platform-web` · `web-store`)은 이미 래퍼 스크립트를 쓴다.
#
# `TASK-MONO-607` 이 **GitHub Actions 워크플로에도 같은 판정**을 붙인다(훅을 쏠지 말지).
# 그 워크플로가 인라인 목록을 **복제**하면 그 순간 같은 사실이 두 곳에 생기고, 이 저장소가
# 반복해서 데인 모양이 된다 — 한쪽만 고쳐지고 다른 쪽은 조용히 낡는다.
#
# ⇒ **진입점을 하나로 만든다.** `vercel.json` 의 `ignoreCommand` 도, 워크플로도 이 파일을
#   부른다. 목록은 여기 한 곳에만 있다.
#
# 🔵 부수 효과 — `vercel.json` 의 명령 문자열이 짧아진다. Vercel 스키마는 명령에
#   **maxLength=256** 을 걸고, `TASK-MONO-562` 가 그 한도를 넘겨 **모든 배포가 0초에
#   죽은** 적이 있다(빌드 로그조차 없었다). 래퍼는 그 위험도 같이 없앤다.
#
# 종료코드 (Vercel 규약 — 직관과 반대다)
# -----------------------------------------------------------------------------
#   exit 0 → 빌드를 **건너뛴다**
#   exit 1 → 빌드를 **진행한다**
# 판정 로직과 fail-open 규약은 전부 `scripts/vercel-should-build.sh` 에 있다.
# 🔴 이 파일은 **경로 목록만** 들고 있어야 한다 — 판정을 여기서 하지 마라.
#
# 사용처 (둘. 둘 다 이 파일을 부른다 — 그게 요점이다)
# -----------------------------------------------------------------------------
#   1. `vercel.json` 의 `ignoreCommand`      — Vercel 이 만든 배포를 취소할지
#   2. `.github/workflows/vercel-deploy.yml` — Deploy Hook 을 **쏠지 말지**
# =============================================================================
set -uo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || {
  echo "[site-ignore] ✖ git 저장소를 찾지 못했습니다 — 빌드를 진행합니다." >&2
  exit 1
}

# 🔴 `:/` 로 시작하는 **저장소 루트 기준** pathspec 이어야 한다. Vercel 은 ignoreCommand 를
#    Root Directory 에서 실행하므로 상대경로는 프로젝트마다 다르게 해석된다.
PATHSPECS=(
  ':/infra/demo/aws/site'
  ':/scripts/vercel-should-build.sh'
)

exec bash "$ROOT/scripts/vercel-should-build.sh" "${PATHSPECS[@]}"
