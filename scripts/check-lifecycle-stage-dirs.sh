#!/usr/bin/env bash
# =============================================================================
# check-lifecycle-stage-dirs.sh — 선언한 라이프사이클 단계는 **비어도 살아남는다**
#                                 (TASK-MONO-592)
# =============================================================================
# git 은 빈 디렉터리를 추적하지 않는다. 그래서 큐가 비는 순간 그 디렉터리는 체크아웃에서
# 사라지고, `projects/*/tasks/<stage>` 로 순회하는 모든 것 — 스크립트·에이전트·사람 — 이
# 그 프로젝트를 **목록에서 통째로 뺀 채 정상 종료한다.** 에러도 경고도 없다.
#
# 2026-08-27 에 실제로 당했다: `for d in projects/*/tasks/ready` 가 7개를 냈고 그것이
# **전수로 보고됐다.** 8개였다. `wms-platform` 은 `ready/` 가 비어서 디렉터리가 없었다.
#
#   bash scripts/check-lifecycle-stage-dirs.sh [--self-test]
#
# -----------------------------------------------------------------------------
# 🔴🔴 술어: «keeper 가 추적되는가» — «디렉터리가 있는가» 도, «.md 가 몇 개인가» 도 아니다
# -----------------------------------------------------------------------------
# 세 후보가 있고 둘은 틀렸다.
#
#  (a) `git ls-files "<dir>/*.md" | wc -l`  ← `check-index-queue-drift.sh:461` 의 술어.
#      «디렉터리가 없다» 와 «디렉터리가 비었다» 가 **똑같이 0** 이다. 이 성질을
#      구조적으로 못 본다 — 고장이 아니라 범위 밖이다. 첫날부터 영원히 초록.
#  (b) `[ -d "<dir>" ]`  ← 파일시스템. 로컬에는 커밋되지 않은 빈 디렉터리가 남아 있을 수
#      있으므로 **로컬 초록 / CI 빨강**을 만든다. 재는 것이 «신선한 체크아웃에서 살아남나»
#      인데 (b)는 «내 디스크에 지금 있나» 를 잰다. 다른 질문이다.
#  (c) **추적되는 non-`.md` 파일이 하나라도 있는가**  ← 이것을 쓴다.
#      `.md` 는 설계상 단계 사이를 **떠난다.** 그러니 큐가 비었을 때 디렉터리를 붙잡아 두는
#      것은 오직 `.md` 가 아닌 keeper 뿐이다. keeper 이름을 박아두지 않는 이유는, 박으면
#      `.keep` 같은 다른 관행이 조용히 거짓 빨강이 되기 때문이다(권고는 `.gitkeep`).
#
# 🔵 (c)가 **잠복**까지 잡는 이유: `fan/ready` 는 지금 디렉터리가 있다 — 티켓 `.md` 하나가
#    붙잡고 있어서다. (b)로는 초록이고, 그 티켓이 나가는 순간 사라진다. (c)는 오늘 문다.
#
# 🔴 `git ls-files` 는 **스테이지된 상태**를 읽는다. 로컬에서 keeper 를 새로 만들고
#    `git add` 전에 돌리면 여전히 빨강이다 — fail-closed 라 방향은 안전하다. 반대로
#    스테이지 안 한 *삭제*는 못 본다. CI 는 항상 커밋된 트리를 보므로 그쪽이 권위다.
#
# -----------------------------------------------------------------------------
# 🔴 모집단은 `PROJECT.md` 에서 잡는다 — 글롭은 **이 결함 자신에게 당한다**
# -----------------------------------------------------------------------------
# `projects/*/tasks/ready` 로 모집단을 만들면 디렉터리가 없는 프로젝트를 **스스로 건너뛴다.**
# 가드가 자기가 찾는 결함에 걸려 조용히 통과하는 것이다. 그래서 반드시 존재하는 파일 —
# `projects/*/PROJECT.md` — 로 프로젝트를 세고, 거기서 `tasks/INDEX.md` 로 내려간다.
# 부수 효과로 `projects/bin/` (PROJECT.md 도 tasks/ 도 없음)이 자동으로 빠진다.
#
# -----------------------------------------------------------------------------
# 🔴 단계 목록의 출처 = **각 INDEX 의 `# Lifecycle` 선언** (박아두지 않는다)
# -----------------------------------------------------------------------------
# 루트 `tasks/` 는 4단계(`ready → in-progress → review → done`)이고 프로젝트는 6단계
# (`backlog → … → archive`)다. 스크립트에 6단계를 박으면 **루트가 거짓 빨강**이 되고,
# 그러면 가드는 꺼진다. 그래서 각 INDEX 가 선언한 것을 읽는다.
#
# 🔴🔴 파싱하는 쪽의 대가: 파서가 죽으면 **0단계 검사 → 초록**이다. 그래서 0 은 통과가
#    아니라 **판정 불가로 실패**다 — INDEX 수 하한, INDEX 당 단계 수 하한 둘 다 건다.
#
# -----------------------------------------------------------------------------
# 범위 밖 (의도적)
# -----------------------------------------------------------------------------
# 선언되지 않은 여분 디렉터리(`tasks/<something>/`)는 **안 본다.** 루트 `tasks/templates/`
# 가 그 모양이라 거짓 빨강이 된다. 이 가드의 축은 «선언한 것이 사라졌나» 한 방향뿐이다.
# =============================================================================
set -uo pipefail

SELF="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/$(basename "${BASH_SOURCE[0]}")"
ROOT="${LIFECYCLE_GUARD_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"

# 🔴 하한 = 2026-08-28 실측(프로젝트 8 + 루트 1 = 9). 이것은 «정확히 9» 가 아니라
#    **파서 사망 탐지기**다. 프로젝트가 늘면 그냥 통과하고(10 ≥ 9), 줄면 의도적으로
#    내려야 한다 — 그 순간이 «프로젝트가 하나 사라졌다» 를 사람이 확인할 자리다.
INDEX_FLOOR="${LIFECYCLE_GUARD_INDEX_FLOOR:-9}"
# 🔴 INDEX 하나가 최소 몇 단계를 선언해야 «읽혔다» 로 볼 것인가. 루트가 4단계이므로
#    2 는 넉넉한 하한이고, 파서가 화살표 줄을 못 찾으면 0 이 되어 여기서 죽는다.
STAGE_FLOOR="${LIFECYCLE_GUARD_STAGE_FLOOR:-2}"

fail=0
note() { echo "  $*"; }
bad()  { echo "  x $*"; fail=1; }

# --- 모집단 ------------------------------------------------------------------
# 프로젝트 = `projects/*/PROJECT.md` 를 가진 디렉터리. 글롭이 아니라 **선언 파일**이다.
project_dirs() {
  git -C "$ROOT" ls-files 'projects/*/PROJECT.md' 2>/dev/null \
    | sed 's|/PROJECT\.md$||' | sort -u
}

# 검사 대상 INDEX = 루트 하나 + 프로젝트마다 하나.
index_files() {
  echo "tasks/INDEX.md"
  local p
  while IFS= read -r p; do
    [ -n "$p" ] && echo "$p/tasks/INDEX.md"
  done < <(project_dirs)
}

# --- 단계 파싱 ---------------------------------------------------------------
# `# Lifecycle` 헤더 다음에 오는 **첫 화살표 줄**을 읽는다. 두 INDEX 형식이 모두 이 모양:
#   # Lifecycle
#   (빈 줄)
#   backlog → ready → in-progress → review → done → archive
stages_of() {
  local idx="$1"
  awk '
    /^#+[[:space:]]*Lifecycle[[:space:]]*$/ { inb = 1; next }
    inb && /→/ { print; exit }
    inb && /^#/ { exit }          # 화살표 줄을 못 만난 채 다음 헤더 -> 아무것도 안 낸다
  ' "$ROOT/$idx" 2>/dev/null \
    | sed 's/→/\n/g' \
    | sed 's/^[[:space:]]*//; s/[[:space:]]*$//' \
    | grep -E '^[a-z][a-z-]*$'
}

# --- 술어 --------------------------------------------------------------------
# 추적되는 non-`.md` 파일이 하나라도 있으면 그 디렉터리는 신선한 체크아웃에서 살아남는다.
keeper_of() {
  local dir="$1"
  git -C "$ROOT" ls-files "$dir/" 2>/dev/null \
    | grep -v '\.md$' | head -1
}

main() {
  echo "[lifecycle-dirs] 선언된 단계가 신선한 체크아웃에서 살아남는지 대조  (root=$ROOT)"

  local indexes=() i
  while IFS= read -r i; do [ -n "$i" ] && indexes+=("$i"); done < <(index_files)

  # --- (0) 탐지기 생존 대조군 -------------------------------------------------
  # 🔴 못 읽은 것과 위반이 없는 것은 다른 사건이다. 후자만 초록이어야 한다.
  if [ "${#indexes[@]}" -lt "$INDEX_FLOOR" ]; then
    bad "(0) 검사 대상 INDEX 가 ${#indexes[@]}개뿐입니다 (하한 $INDEX_FLOOR — 2026-08-28 실측: 프로젝트 8 + 루트 1)."
    bad "    → 0/적음은 '위반이 없다' 가 아니라 **모집단이 안 잡힌 것**입니다(PROJECT.md 가 사라졌나요?)."
    return 1
  fi

  local missing_idx=0
  for i in "${indexes[@]}"; do
    if [ ! -f "$ROOT/$i" ]; then
      bad "(0) $i 이 없습니다 — 이 프로젝트의 라이프사이클은 **선언 자체가 없어** 판정 불가."
      missing_idx=1
    fi
  done
  [ "$missing_idx" -eq 0 ] || return 1
  note "(0) INDEX ${#indexes[@]}개 (하한 $INDEX_FLOOR) — 전부 존재"

  # --- (1) 단계별 keeper 대조 -------------------------------------------------
  local total_stages=0 gone=()
  for i in "${indexes[@]}"; do
    local base; base="$(dirname "$i")"
    local stages=() s
    while IFS= read -r s; do [ -n "$s" ] && stages+=("$s"); done < <(stages_of "$i")

    # 🔴 파서가 죽으면 0단계 검사가 조용히 초록이 된다. 여기서 죽인다.
    if [ "${#stages[@]}" -lt "$STAGE_FLOOR" ]; then
      bad "(1) $i 에서 단계를 ${#stages[@]}개밖에 못 읽었습니다 (하한 $STAGE_FLOOR)."
      bad "    → '# Lifecycle' 헤더 + 그 뒤의 '→' 줄을 찾습니다. 모양이 바뀌었다면 **판정 불가**입니다."
      continue
    fi

    local row="" bad_here=0
    for s in "${stages[@]}"; do
      total_stages=$((total_stages + 1))
      local k; k="$(keeper_of "$base/$s")"
      if [ -n "$k" ]; then
        row="$row ○$s"
      else
        row="$row ×$s"
        gone+=("$base/$s")
        bad_here=1
      fi
    done
    if [ "$bad_here" -eq 0 ]; then
      note "(1) $base  ${#stages[@]}단계 —$row"
    else
      bad "(1) $base  ${#stages[@]}단계 —$row"
    fi
  done

  if [ "$total_stages" -eq 0 ]; then
    bad "(1) 단계를 하나도 검사하지 못했습니다 — **판정 불가**(0건은 통과가 아닙니다)."
    return 1
  fi

  if [ "${#gone[@]}" -gt 0 ]; then
    echo
    bad "keeper 가 없는 단계 ${#gone[@]}개 — 이 큐들은 **비는 순간 디렉터리째 사라집니다**:"
    local g
    for g in "${gone[@]}"; do bad "    $g/"; done
    bad "  고치는 법:  touch <위 경로>/.gitkeep && git add -f <위 경로>/.gitkeep"
    bad "  🔴 지금 디렉터리가 보이더라도 안심하지 마세요 — .md 하나가 붙잡고 있을 뿐이고,"
    bad "     그 티켓이 다음 단계로 옮겨가는 순간 디렉터리가 사라집니다(잠복)."
  fi

  if [ "$fail" -eq 0 ]; then
    echo "[lifecycle-dirs] ok — INDEX ${#indexes[@]}개 · 선언 단계 ${total_stages}개 전부 keeper 보유"
  else
    echo "[lifecycle-dirs] 실패"
  fi
  return "$fail"
}

# =============================================================================
# --self-test — 진짜 트리의 사본을 망가뜨려 **무는지** 본다
# =============================================================================
# 🔴 초록만 보고 «작동한다» 로 적지 않는다. 각 칸은 무는지 읽기 **전에 주입이 들어갔는지**
#    단언한다 — 0건이면 «안 물었다» 와 «시험한 적이 없다» 가 구별되지 않고, 후자는
#    초록으로 보인다.
self_test() {
  local src; src="$(cd "$(dirname "$SELF")/.." && pwd)"
  local rc=0 t

  # 🔵 파일별 `cp` 루프 대신 tar 한 번. msys 에서 프로세스 기동이 비싸고, 이 함수가
  #    자기시험 칸마다 불린다 — 루프판은 로컬에서 칸당 ~45초였다.
  _mk() {
    local d; d="$(mktemp -d)"
    ( cd "$src" && git ls-files -z 'tasks/INDEX.md' 'tasks/*/.gitkeep' \
                                   'projects/*/PROJECT.md' 'projects/*/tasks/INDEX.md' \
                                   'projects/*/tasks/*/.gitkeep' \
        | tar cf - --null -T - ) | ( cd "$d" && tar xf - )
    git -C "$d" init -q; git -C "$d" config user.email t@l; git -C "$d" config user.name t
    git -C "$d" add -Af >/dev/null; git -C "$d" commit -qm base
    echo "$d"
  }
  _run() { LIFECYCLE_GUARD_ROOT="$1" bash "$SELF" >/dev/null 2>&1; echo $?; }
  _expect() {
    local what="$1" want="$2" got="$3"
    if [ "$got" = "$want" ]; then echo "  ok: $what (rc=$got)"
    else echo "  x  $what — rc=$got 인데 $want 를 기대했습니다."; rc=1; fi
  }

  echo "[lifecycle-dirs] --self-test — 진짜 트리의 사본을 망가뜨려 무는지 확인합니다"

  # (0) 🔵 음성 대조군. 이 칸이 빨강이면 아래 칸들의 빨강은 아무 의미가 없다.
  #     사본에는 keeper 만 담기고 티켓 .md 는 안 담긴다 — 즉 **모든 큐가 빈 상태**이고,
  #     그래도 초록이어야 한다. 그게 이 가드가 지키려는 바로 그 성질이다.
  #     🔴 그래서 이 칸은 본 검사의 중복이 아니다: 본 검사는 `.md` 가 큐를 붙잡고 있는
  #     실제 트리를 보지만, 여기는 **전부 비워 놓고** 본다. 잠복이 남아 있으면 여기서만
  #     드러난다. (개발 중 실측: 6개 결손이 남은 트리에서 이 칸이 정확히 빨강이었다.)
  t="$(_mk)"; _expect "(0) 무망가 사본(모든 큐가 빈 상태) -> 통과" 0 "$(_run "$t")"; rm -rf "$t"

  # (a) keeper 하나를 지운다 -> 문다. 이 가드의 본체.
  t="$(_mk)"
  victim="$(git -C "$t" ls-files 'projects/*/tasks/ready/.gitkeep' | head -1)"
  if [ -z "$victim" ]; then
    echo "  x  (a) 주입 실패 — 사본에 지울 keeper 가 없습니다."; rc=1
  else
    git -C "$t" rm -q "$victim"
    if [ -n "$(git -C "$t" ls-files "$victim")" ]; then
      echo "  x  (a) 주입 실패 — keeper 가 안 지워졌습니다. 이 칸은 아무것도 시험하지 않았습니다."; rc=1
    else
      git -C "$t" commit -qm mutate
      _expect "(a) keeper 삭제 ($victim) -> 문다" 1 "$(_run "$t")"
    fi
  fi
  rm -rf "$t"

  # (b) 🔴🔴 **이 칸이 (a)와 다른 것을 시험한다** — 결함의 실제 모양이다.
  #     keeper 를 지우고 그 자리에 티켓 `.md` 를 넣는다. 디렉터리는 디스크에 **있고**,
  #     `git ls-files "<dir>/*.md"` 는 1을 센다. 옛 술어(a)/(b)였다면 **초록**이다.
  #     여기가 빨강이어야 이 가드가 새 축을 재고 있다는 증거가 된다.
  t="$(_mk)"
  victim="$(git -C "$t" ls-files 'projects/*/tasks/ready/.gitkeep' | head -1)"
  if [ -z "$victim" ]; then
    echo "  x  (b) 주입 실패 — 사본에 지울 keeper 가 없습니다."; rc=1
  else
    vdir="$(dirname "$victim")"
    git -C "$t" rm -q "$victim"
    # 🔴 `git rm` 이 마지막 파일을 지우면 **디렉터리 자체가 사라진다** — 이 티켓이 말하는
    #    바로 그 현상이다. 잠복 상태를 만들려면 디렉터리를 다시 세워야 한다.
    mkdir -p "$t/$vdir"
    echo '# TASK-XXX' > "$t/$vdir/TASK-LATENT-001-placeholder.md"
    git -C "$t" add -Af "$vdir" >/dev/null
    latent_md="$(git -C "$t" ls-files "$vdir/*.md" | wc -l)"
    still_keep="$(git -C "$t" ls-files "$victim")"
    if [ "$latent_md" -lt 1 ] || [ -n "$still_keep" ] || [ ! -d "$t/$vdir" ]; then
      echo "  x  (b) 주입 실패 (md=$latent_md keeper='$still_keep' dir=$([ -d "$t/$vdir" ] && echo yes || echo no)) — 잠복 상태가 안 만들어졌습니다."; rc=1
    else
      git -C "$t" commit -qm mutate
      _expect "(b) 잠복(.md 만 있고 keeper 없음, 디렉터리는 존재) -> 문다" 1 "$(_run "$t")"
    fi
  fi
  rm -rf "$t"

  # (c) Lifecycle 선언을 망가뜨린다 -> **판정 불가로 실패**. 0단계가 초록이면 안 된다.
  t="$(_mk)"
  vidx="$(git -C "$t" ls-files 'projects/*/tasks/INDEX.md' | head -1)"
  if [ -z "$vidx" ]; then
    echo "  x  (c) 주입 실패 — 사본에 INDEX 가 없습니다."; rc=1
  else
    before="$(LIFECYCLE_GUARD_ROOT="$t" bash "$SELF" 2>/dev/null | grep -c "$(dirname "$(dirname "$vidx")")")"
    sed -i 's/→/-then-/g' "$t/$vidx"
    if grep -q '→' "$t/$vidx"; then
      echo "  x  (c) 주입 실패 — 화살표가 안 지워졌습니다."; rc=1
    else
      git -C "$t" commit -qam mutate
      _expect "(c) Lifecycle 화살표 줄 파괴 -> 판정 불가로 실패 (before-hit=$before)" 1 "$(_run "$t")"
    fi
  fi
  rm -rf "$t"

  # (d) 모집단을 지운다 (PROJECT.md 제거) -> **하한**으로 문다.
  #     🔴 이 칸이 없으면 «가드가 프로젝트를 못 찾아서 통과» 가 초록으로 보인다.
  t="$(_mk)"
  vproj="$(git -C "$t" ls-files 'projects/*/PROJECT.md' | head -1)"
  if [ -z "$vproj" ]; then
    echo "  x  (d) 주입 실패 — 사본에 PROJECT.md 가 없습니다."; rc=1
  else
    git -C "$t" rm -q "$vproj"
    if [ -n "$(git -C "$t" ls-files "$vproj")" ]; then
      echo "  x  (d) 주입 실패 — PROJECT.md 가 안 지워졌습니다."; rc=1
    else
      git -C "$t" commit -qm mutate
      _expect "(d) PROJECT.md 제거로 모집단 축소 -> 하한이 문다" 1 "$(_run "$t")"
    fi
  fi
  rm -rf "$t"

  # (e) 🔵 루트가 6단계를 강요당하지 않는지 — Edge Case 1행.
  #     루트 `tasks/` 에는 backlog/archive 가 **없고 없는 것이 정상**이다. (0)이 통과한
  #     것으로 이미 증명되지만, 근거를 눈에 보이게 남긴다.
  t="$(_mk)"
  rootline="$(LIFECYCLE_GUARD_ROOT="$t" bash "$SELF" 2>/dev/null | grep -E '\(1\) tasks ')"
  if echo "$rootline" | grep -q '4단계' && ! echo "$rootline" | grep -q 'backlog'; then
    echo "  ok: (e) 루트는 자기가 선언한 4단계만 검사됨 —$(echo "$rootline" | sed 's/^ *//')"
  else
    echo "  x  (e) 루트 판정이 예상과 다릅니다: '${rootline:-<없음>}'"; rc=1
  fi
  rm -rf "$t"

  [ "$rc" -eq 0 ] && echo "[lifecycle-dirs] --self-test ok" || echo "[lifecycle-dirs] --self-test 실패"
  return "$rc"
}

if [ "${1:-}" = "--self-test" ]; then
  self_test; exit $?
fi
main; exit $?
