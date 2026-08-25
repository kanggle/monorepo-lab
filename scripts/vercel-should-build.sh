#!/usr/bin/env bash
# =============================================================================
# scripts/vercel-should-build.sh — Vercel "Ignored Build Step" 판정 (TASK-MONO-562)
# =============================================================================
# 커밋 하나가 굽는 Vercel 배포 수를 줄인다. 이 저장소는 Vercel 프로젝트가 **둘**이라
# (`kanggle-portfolio` = 론처, `kanggle-fan` = fan 프런트) 커밋 하나가 배포 **둘**을 굽고,
# 문서 전용 PR 도 예외가 아니었다 ⇒ 무료 플랜의 일일 한도에 닿아 24시간 동안 모든 PR 이
# 빨개지고, 그동안 **론처는 낡은 판을 계속 서빙했다.**
#
# -----------------------------------------------------------------------------
# 🔴 이 스크립트는 경로를 하나도 모른다 — 그게 요점이다
# -----------------------------------------------------------------------------
# 여기는 저장소 루트 `scripts/` 이고 **project-agnostic 이어야 한다**(CLAUDE.md HARDSTOP-03).
# 그래서 "어느 경로가 어느 Vercel 프로젝트를 굽는가" 는 **인자로 받는다.** 각 프로젝트의
# 경로 목록은 그 프로젝트의 `vercel.json` 에 있다 — 즉 목록은 자기 집에 산다.
#
#   bash scripts/vercel-should-build.sh <pathspec> [<pathspec> ...]
#
# pathspec 은 **`:/` 로 시작하는 저장소 루트 기준**으로 쓴다. Vercel 은 ignoreCommand 를
# **Root Directory 에서** 실행하므로 상대경로는 프로젝트마다 다르게 해석된다.
#
# -----------------------------------------------------------------------------
# 🔴 종료코드가 뒤집혀 있다 (Vercel 규약)
# -----------------------------------------------------------------------------
#   exit 0  → 빌드를 **건너뛴다**
#   exit 1  → 빌드를 **진행한다**
# 직관과 반대다. `set -e` 로 중간에 죽으면 **0 이 아니므로 빌드가 진행된다** — 그 방향이
# 안전한 쪽이라 의도적으로 그렇게 뒀다. 아래 § 참조.
#
# -----------------------------------------------------------------------------
# 🔴🔴 고장은 반드시 "더 굽는" 쪽으로 나야 한다
# -----------------------------------------------------------------------------
# 무시 규칙은 **과하게 무시하는 방향으로 고장 난다.** 그 고장의 증상은 "배포가 실패했다" 가
# 아니라 **"배포가 조용히 건너뛰어졌다"** 이고, CI 는 초록이며, 사이트는 마지막 성공 배포를
# 계속 서빙하므로 URL 을 찔러도 200 이 나온다. 아무도 안 본다.
# ⇒ 판정할 수 없는 상황(얕은 clone 이라 부모 커밋이 없다 / git 이 없다 / 인자가 없다)은
#   전부 **빌드 진행**으로 떨어뜨린다. 배포 한 건 더 굽는 비용 < 낡은 판을 모르고 서빙하는 비용.
# =============================================================================

log() { echo "[vercel-ignore] $*" >&2; }

# --- 판정 불가 → 진행 (fail-open) -------------------------------------------
if [ "$#" -eq 0 ]; then
  log "✖ pathspec 인자가 없습니다 — 판정할 수 없으므로 빌드를 진행합니다."
  exit 1
fi

if ! command -v git >/dev/null 2>&1; then
  log "✖ git 이 없습니다 — 판정할 수 없으므로 빌드를 진행합니다."
  exit 1
fi

if ! git rev-parse --verify --quiet HEAD^ >/dev/null 2>&1; then
  log "✖ 부모 커밋(HEAD^)이 없습니다 (얕은 clone / 최초 커밋) — 빌드를 진행합니다."
  exit 1
fi

# -----------------------------------------------------------------------------
# 🔴🔴 판정 창은 **푸시된 범위**여야 한다 — `HEAD^..HEAD` 는 한 커밋뿐이다 (TASK-MONO-572)
# -----------------------------------------------------------------------------
# 원래 판정은 `HEAD^..HEAD`, 즉 **마지막 커밋 하나**만 봤다. 커밋 여러 개를 한 번에 push 하면
# 앞 커밋의 앱 변경이 그 창 밖으로 나가고, 배포가 **조용히 건너뛰어진다.**
#
# 2026-08-23 에 실제로 발생했다: `[프로브 라우트 커밋, tasks/INDEX 커밋]` 을 함께 push →
# Vercel 이 head 에서 판정 → fan 경로에 변경 없음 → `Canceled by Ignored Build Step`.
# **프리뷰에 그 프로브가 없었고**, 상태는 실패가 아니라 **성공**이며 PR 체크는 초록이었다.
#
# 🔴 이 파일 맨 위가 경고한 바로 그 모양인데(*"증상은 배포가 조용히 건너뛰어졌다… 아무도 안
#    본다"*), **판정 불가가 아니어서** fail-open 울타리를 그냥 통과했다. 스크립트는 자신 있게
#    "변경 없음"이라고 답했다. **틀린 것은 fail-open 이 아니라 창의 크기였다.**
#
# 🔵 `main` 은 squash 머지라 한 커밋에 전부 담긴다 ⇒ **프로덕션은 원래도 옳았다.**
#    뚫리는 것은 **프리뷰**이고, 그래서 프리뷰에서 재는 모든 측정이 틀린 산출물을 잰다.
#
# 🔴🔴 **직전 배포는 첫 push 에 존재하지 않는다 — 그리고 첫 push 가 이 결함의 본거지다** (실측)
#
# 2026-08-25, 이 수정의 1차 판을 `[scripts 커밋, tasks 커밋]` 배치로 올려 라이브에서 쟀다.
# 결과: **여전히 `Canceled by Ignored Build Step`.** 고쳐진 스크립트가 돌았는데도 그랬다.
# 이유는 결함이 아니라 정의였다 — **새 브랜치의 첫 push 에는 "직전 배포"가 없다.**
# 그런데 이 저장소는 태스크마다 브랜치를 새로 만들고, **커밋 여러 개를 한 번에 올리는 것은
# 정확히 그 첫 push** 다. 즉 1차 판은 **결함이 가장 잘 나는 경우를 못 덮었다.**
#
# PR 브랜치에서 올바른 기준점은 *직전 배포*가 아니라 **`main` 과의 merge-base** 다 —
# "이 브랜치가 보태는 전부"라는 뜻이고, **첫 push 에도 정의된다.**
#
# 기준점 선택 순서 (위에서부터, 쓸 수 있는 첫 번째):
#   1. `VERCEL_GIT_PREVIOUS_SHA`      — 두 번째 이후 push. Vercel 이 주면 가장 정확하다.
#   2. `merge-base(<기본브랜치>, HEAD)` — 브랜치가 보태는 전부. **첫 push 를 덮는다.**
#   3. `HEAD^`                        — 기존 동작. **더 나빠지지 않는다.**
#
# 🔴 어느 후보든 쓰기 전에 **세 가지를 확인**한다. 하나라도 아니면 다음으로 내려간다 —
#    조용히 이상한 집합을 비교하느니 좁게 보는 편이 낫고, 좁게 보는 것이 지금까지의 동작이다.
#   (a) 비어 있지 않다   (b) 이 클론에 객체가 실재한다(얕은 clone 이면 없다)
#   (c) HEAD 의 **조상**이다 — force-push 뒤엔 조상이 아니고 `A..B` 가 무의미해진다.
#
# 🔵 2번은 **production 브랜치에서는 쓰면 안 된다.** 거기서 merge-base 는 HEAD 자신이라
#    창이 비고 **모든 것이 건너뛰어진다** — 정확히 반대 방향의 고장이다. main 은 squash 머지라
#    한 커밋에 전부 담기므로 `HEAD^` 가 이미 옳다.

# 후보가 쓸 만한지 본다. 쓸 만하면 0, 아니면 1(과 이유 로그).
usable_base() {
  local cand="$1" why="$2"
  [ -n "$cand" ] || return 1
  if ! git cat-file -e "${cand}^{commit}" 2>/dev/null; then
    log "· ${why}=${cand} 가 이 클론에 없습니다 (얕은 clone?) — 다음 후보로."
    return 1
  fi
  if ! git merge-base --is-ancestor "$cand" HEAD 2>/dev/null; then
    log "· ${why}=${cand} 가 HEAD 의 조상이 아닙니다 (force-push?) — 다음 후보로."
    return 1
  fi
  return 0
}

# Vercel 의 production 브랜치. 안 주면 `main` 으로 본다.
PROD_REF="${VERCEL_GIT_REPO_DEFAULT_BRANCH:-main}"
CUR_REF="${VERCEL_GIT_COMMIT_REF:-}"

BASE="HEAD^"
BASE_WHY="HEAD^ (기본)"

if usable_base "${VERCEL_GIT_PREVIOUS_SHA:-}" "VERCEL_GIT_PREVIOUS_SHA"; then
  BASE="$VERCEL_GIT_PREVIOUS_SHA"
  BASE_WHY="VERCEL_GIT_PREVIOUS_SHA (직전 배포)"
elif [ -n "$CUR_REF" ] && [ "$CUR_REF" != "$PROD_REF" ]; then
  # PR 브랜치다. `main` 을 여러 이름으로 찾아본다 — 얕은/부분 clone 에서 무엇이 있는지 모른다.
  #
  # 🔴🔴 **2차 판도 라이브에서 실패했다** (2026-08-25, 두 번째 측정).
  #    `tasks/` 만 건드리는 커밋을 이 브랜치에 올렸다 — merge-base 가 살아 있으면 브랜치 범위에
  #    `scripts/` 변경이 들어 있으므로 **빌드**해야 했다. 결과는 **양쪽 다 건너뜀**이었다.
  #
  #    좁혀진 원인: `HEAD^` 는 되는데(판정이 실제로 나오고 있다) 기준 브랜치를 못 찾는다.
  #    **Vercel 의 빌드 클론은 얕고, 배포 대상 ref 만 가져온다** — `origin/main` 이 없으면
  #    merge-base 를 계산할 대상 자체가 없다.
  #
  # ⇒ 그러면 **가져오면 된다.** 아래 fetch 는 fail-safe 다: 실패하면 그냥 없는 것이고, 루프가
  #    못 찾아 `HEAD^` 로 내려간다. **더 나빠지지 않는다.**
  #    🔵 `--depth` 를 넉넉히 주는 이유 — 얕게 가져오면 공통 조상까지 못 닿아 merge-base 가
  #    빈손으로 끝나고, 그건 "기준 브랜치가 없다" 와 **구별되지 않는 실패**가 된다.
  if ! git rev-parse --verify --quiet "origin/$PROD_REF" >/dev/null 2>&1; then
    log "· 기준 브랜치가 클론에 없습니다 — origin/${PROD_REF} 를 가져와 봅니다(실패해도 진행)."
    git fetch --quiet --depth=200 origin "+refs/heads/${PROD_REF}:refs/remotes/origin/${PROD_REF}" 2>/dev/null \
      || log "· fetch 실패 — 기준 브랜치 없이 판정합니다."
  fi

  MB=""
  for ref in "origin/$PROD_REF" "$PROD_REF" "refs/remotes/origin/$PROD_REF"; do
    git rev-parse --verify --quiet "$ref" >/dev/null 2>&1 || continue
    MB="$(git merge-base "$ref" HEAD 2>/dev/null)" || MB=""
    [ -n "$MB" ] && break
  done
  if [ -z "$MB" ]; then
    log "· 기본 브랜치(${PROD_REF})를 이 클론에서 찾지 못했습니다 — HEAD^ 로 판정합니다."
  elif [ "$MB" = "$(git rev-parse HEAD 2>/dev/null)" ]; then
    log "· merge-base 가 HEAD 자신입니다 (브랜치가 기본과 같음) — HEAD^ 로 판정합니다."
  elif usable_base "$MB" "merge-base(${PROD_REF})"; then
    BASE="$MB"
    BASE_WHY="merge-base(${PROD_REF}) — 이 브랜치가 보태는 전부"
  fi
else
  log "· production 브랜치(${PROD_REF}) 이거나 ref 를 모릅니다 — HEAD^ 로 판정합니다(squash 머지라 그것이 옳다)."
fi

log "· 판정 창 = ${BASE_WHY} · $(git rev-list --count "$BASE..HEAD" 2>/dev/null || echo '?') 커밋"

# --- 판정 -------------------------------------------------------------------
# `git diff --quiet` 는 차이가 없으면 0, 있으면 1 을 낸다 — Vercel 규약과 **그대로 맞는다.**
# 그러니 종료코드를 뒤집지 마라. 우연이 아니라 이 규약이 그렇게 설계돼 있다.
if git diff --quiet "$BASE" HEAD -- "$@"; then
  log "↷ 건너뜀 — ${BASE}..HEAD 에 다음 경로의 변경이 없습니다: $*"
  exit 0
fi

log "▶ 빌드 — ${BASE}..HEAD 가 다음 경로를 건드렸습니다: $*"
git diff --name-only "$BASE" HEAD -- "$@" | sed 's/^/[vercel-ignore]   /' >&2
exit 1
