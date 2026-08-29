#!/usr/bin/env bash
# =============================================================================
# scripts/vercel-should-build.sh — Vercel "Ignored Build Step" 판정 (TASK-MONO-562)
# =============================================================================
# 커밋 하나가 굽는 Vercel 배포 수를 줄인다. 이 저장소는 Vercel 프로젝트가 **셋**이라
# (`kanggle-portfolio` = 론처, `kanggle-fan` = fan 프런트, `kanggle-store` = web-store —
#  2026-08-29 생성, `TASK-MONO-582`. 그 전까지 셋째는 파일만 있고 프로젝트가 없었다)
# 커밋 하나가 배포 **셋**을 굽고,
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
#
# -----------------------------------------------------------------------------
# 🔴🔴 후보가 **HEAD 자신**이면 창은 0 커밋이고, 그것은 "변경 없음" 이 **아니다**
#      (TASK-MONO-594)
# -----------------------------------------------------------------------------
# 위 (a)(b)(c) 는 `cand == HEAD` 를 **셋 다 통과시킨다** — 커밋은 자기 자신의 조상이므로
# (c) 가 못 거른다. 그러면 `BASE..HEAD` 가 **빈 범위**가 되어 `git diff --quiet` 가 언제나
# 0(=차이 없음)을 내고, Vercel 규약상 그것은 **건너뜀**이다. 즉 그 배포는 무엇이 바뀌었든
# **반드시 취소된다.**
#
# 2026-08-27 라이브 관측(`dpl_9AZiTx4H6a7mtR7wEChGvm1Uw9Bq`, `kanggle-fan`, Production):
# `VERCEL_GIT_PREVIOUS_SHA` = `8e43a4db9…` 인데 클론된 커밋도 `8e43a4d` — **같은 커밋**이고,
# 로그가 스스로 `· 판정 창 = … · 0 커밋` 이라 적은 채 건너뛰었다. 그 배포가 **대시보드 수동
# Redeploy** 라는 지문은 둘이다: `Skipping build cache, deployment was triggered without
# cache`, 그리고 클론된 커밋이 그 시각 `main` 의 tip 이 아니었다는 것.
#
# 🔴 고칠 것은 "무시 규칙이 너무 넓다" 가 아니라 **창이 비었는데도 자신 있게 답했다** 는
#    것이다. `TASK-MONO-572` 와 **같은 클래스**(창의 크기)이고, 이 파일 맨 위가 못박은
#    *"고장은 반드시 「더 굽는」 쪽으로 나야 한다"* 의 **정확히 반대 방향**으로 고장 나 있었다.
#
# 🔵 **왜 "다음 후보" 가 아니라 `exit 1`(빌드) 인가** — 갈래가 둘이었고 결과가 다르다.
#   (B) 다음 후보(`HEAD^`)로 내려간다: 아래 merge-base 갈래와 "일관성" 은 얻는다. 🔴 그러나
#       **env 를 반영하려는 Redeploy 는 여전히 안 굽는다** — 그 커밋이 앱 경로를 안 건드렸다면
#       `HEAD^..HEAD` 도 "건너뜀" 이기 때문이다. 함정이 안 고쳐진다.
#   (A) 빈 창 = **판정 불가** ⇒ 이 파일이 이미 선언한 규약(*"판정할 수 없는 상황은 전부 빌드
#       진행"*)을 그대로 적용한다. 수동 Redeploy 는 **사람이 명시적으로 빌드를 요청한 행위**라
#       fail-open 방향과도 일치한다. 대가는 Redeploy 1건이 항상 배포 슬롯을 쓰는 것이고,
#       그것은 사람이 누른 비용이다.
#   ⇒ **(A) 를 골랐다.** 그래서 아래 검사는 `return 1` 이 아니라 `exit 1` 이다.
#
# 🔵 왜 이것이 실제로 다른 티켓을 막고 있었나: Vercel 의 **env 변경은 새 배포에서만 반영된다.**
#    소유자가 env 를 넣고 취할 자연스러운 다음 행동이 Settings → Redeploy 이고, 그 배포가
#    7초 만에 Canceled 된다. 관측되는 것은 *"env 를 넣고 재배포했는데 그대로다"* 이고,
#    **틀린 결론이 남의 티켓에 기록된다**(`TASK-FAN-FE-018` · `TASK-MONO-586`).
#
# 🔴🔴 **`TASK-MONO-590` 이 랜딩해도 이 갈래는 살아 있어야 한다.** 590 은 배포가 *만들어지는
#    것 자체*를 Deploy Hook 축에서 줄이는 티켓이고, 그 AC-3 은 훅 없는 프로젝트에서
#    `ignoreCommand` 를 살려 두기로 했다. 그리고 **수동 Redeploy 는 훅 경로를 지나가지 않는다.**
#    590 이 이 파일을 "거의 죽은 기전" 으로 만들어도 이 검사와
#    `check-vercel-build-triggers.sh` 의 칸 (13)은 **지우면 안 된다.**
#
# 🔵 2번 갈래(merge-base)에는 같은 검사가 **이미 있다**(아래 `MB = HEAD` elif). 즉 저자는
#    "창이 비면 전부 건너뛴다" 를 알고 있었고 **한 곳에만 적용했다.** 아래 (d) 는 1번 갈래의
#    몫이다 — 2번은 그 elif 가 `usable_base` 에 닿기 전에 가로챈다.

# 후보가 쓸 만한지 본다. 쓸 만하면 0, 아니면 1(과 이유 로그).
usable_base() {
  local cand="$1" why="$2"
  [ -n "$cand" ] || return 1
  if ! git cat-file -e "${cand}^{commit}" 2>/dev/null; then
    log "· ${why}=${cand} 가 이 클론에 없습니다 (얕은 clone?) — 다음 후보로."
    return 1
  fi
  # >>> MONO-594-EMPTY-WINDOW-GUARD
  # 🔴 이 두 표식(`>>>` / `<<<`)은 장식이 아니다. `check-vercel-build-triggers.sh` 의
  #    `--self-test` 칸 (h)가 **이 사이를 통째로 지운 사본**으로 가드를 돌려, 칸 (13)이
  #    실제로 무는지(= 고치기 전 판에서 빨개지는지) 증명한다. 표식을 지우면 그 칸은
  #    "주입 실패" 로 빨개진다 — 조용히 초록이 되지는 않는다. 이름도 ASCII 로 고정한다.
  #
  # 🔴🔴 (d) 후보가 **HEAD 자신**인가 — 창이 0 커밋이면 "변경 없음" 이 아니라 **판정 불가**다.
  #     (TASK-MONO-594. 위 § 참조 — `return 1`(다음 후보)이 아니라 `exit 1`(빌드)인 이유도.)
  #
  # 🔴 문자열이 아니라 `git rev-parse` 로 **정규화해서** 비교한다. `VERCEL_GIT_PREVIOUS_SHA`
  #    는 40자 full SHA 로 오고 HEAD 는 ref 다. 축약형/ref 표기가 섞이면 같은 커밋을 다르다고
  #    읽어 이 검사가 **조용히 안 문다** — 그 실패는 고치기 전과 구별되지 않는다.
  local cand_sha head_sha
  cand_sha="$(git rev-parse --verify --quiet "${cand}^{commit}" 2>/dev/null)" || cand_sha=""
  head_sha="$(git rev-parse --verify --quiet 'HEAD^{commit}' 2>/dev/null)" || head_sha=""
  if [ -n "$cand_sha" ] && [ "$cand_sha" = "$head_sha" ]; then
    log "· ${why}=${cand} 가 HEAD 자신입니다 — 판정 창이 **0 커밋**입니다 (수동 Redeploy 의 지문)."
    log "✖ 빈 창으로는 판정할 수 없습니다 — 빌드를 진행합니다 (TASK-MONO-594)."
    exit 1
  fi
  # <<< MONO-594-EMPTY-WINDOW-GUARD

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
