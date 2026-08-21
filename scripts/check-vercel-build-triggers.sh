#!/usr/bin/env bash
# =============================================================================
# check-vercel-build-triggers.sh — 무관한 커밋이 Vercel 배포를 굽지 않는다 (TASK-MONO-562)
# =============================================================================
# 저장소에 Vercel 프로젝트가 **둘** 이라 커밋 하나가 배포 **둘** 을 굽는다. 문서 전용 PR 도
# 예외가 아니어서 무료 플랜의 일일 한도에 닿았고, 24시간 동안 모든 PR 이 빨개졌다.
# 🔴 진짜 피해는 색깔이 아니라 **그동안 론처가 낡은 판을 계속 서빙한 것**이다.
#
#   bash scripts/check-vercel-build-triggers.sh [--self-test]
#
# -----------------------------------------------------------------------------
# 🔴 모집단은 트리에서 **발견**한다 — 여기에 목록을 적지 않는다
# -----------------------------------------------------------------------------
# 두 가지 이유가 겹친다:
#  1. 여기는 루트 scripts/ 라 **project-agnostic 이어야 한다**(CLAUDE.md HARDSTOP-03).
#     프로젝트 경로를 리터럴로 적는 순간 이 파일이 규칙을 어긴다.
#  2. 하드코딩한 모집단을 쓰는 가드는 **대상이 사라져도 자기가 적어둔 것을 계속 테스트하고
#     통과한다.** 세 번째 Vercel 프로젝트가 생기면 이 가드는 그것을 봐야 한다.
#
# => git ls-files 로 vercel.json 을 전수로 찾고, **그 개수에 하한**을 둔다(FLOOR).
#
# -----------------------------------------------------------------------------
# 🔴🔴 판정은 실행이다 — "ignoreCommand 라는 낱말이 있는가" 가 아니다
# -----------------------------------------------------------------------------
# 문자열이 있는지 grep 하는 가드는 **잘 문서화할수록 더 확실히 초록**이 된다. 여기서는
# 임시 git 저장소를 만들어 **판정자를 실제로 돌리고 종료코드를 읽는다.**
#
# 칸 (AC-1):
#   (1) 자기 경로가 바뀐 커밋      -> 빌드함  (rc=1)
#   (2) 무관한 경로만 바뀐 커밋    -> 건너뜀  (rc=0)
#   (3) 부모 커밋이 없는 경우      -> 빌드함  (rc=1)   <- fail-open 대조군
#   (4) pathspec 을 하나도 못 뽑음 -> **가드 실패**     <- 추출이 빈 껍데기가 아님을 증명
#
# 🔴 (2)만 있고 (1)이 없으면 "전부 건너뛰기" 로 고장 나도 이 가드는 초록이다. 무시 규칙은
# 언제나 **과하게 무시하는 방향**으로 고장 나므로 (1)이 이 가드의 본체다.
# =============================================================================
set -uo pipefail

SELF="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/$(basename "${BASH_SOURCE[0]}")"
ROOT="${VERCEL_GUARD_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"

# 🔴 하한은 **provenance 와 함께** 박는다. 늘어나면 올려야 하고, 그 순간이 새 Vercel
# 프로젝트에 트리거 규칙을 붙일 자리다. 2026-08-21 기준 = kanggle-portfolio + kanggle-fan.
FLOOR="${VERCEL_GUARD_FLOOR:-2}"

COMMENT_KEY_RE='^[[:space:]]*"//'

fail=0
note() { echo "  $*"; }
bad()  { echo "  x $*"; fail=1; }

# --- ignoreCommand 에서 :/... pathspec 을 뽑는다 -----------------------------
extract_pathspecs() {
  # 작은따옴표로 감싼 :/... 토큰만 뽑는다. 셸 인용을 흉내내는 것이 아니라, 이 저장소가
  # 쓰기로 한 **한 가지 모양**만 인정하는 것이다 — 모양이 바뀌면 추출이 0건이 되고
  # 칸 (4)가 발화한다(조용히 통과하지 않는다).
  grep -o "':/[^']*'" "$1" 2>/dev/null | tr -d "'"
}

run_cell() {
  # run_cell <설명> <기대rc> <임시저장소> <judge> <pathspec...>
  local desc="$1" want="$2" repo="$3" judge="$4"; shift 4
  local got
  ( cd "$repo" && bash "$judge" "$@" >/dev/null 2>&1 ); got=$?
  if [ "$got" -ne "$want" ]; then
    bad "$desc — rc=$got 인데 $want 를 기대했습니다."
    return 1
  fi
  note "ok: $desc (rc=$got)"
  return 0
}

main() {
  local judge="$ROOT/scripts/vercel-should-build.sh"
  echo "[vercel-triggers] Vercel 빌드 트리거 규칙 검사  (root=$ROOT)"

  local configs=()
  while IFS= read -r line; do [ -n "$line" ] && configs+=("$line"); done \
    < <(git -C "$ROOT" ls-files '*vercel.json' 2>/dev/null | sort)

  if [ "${#configs[@]}" -lt "$FLOOR" ]; then
    bad "vercel.json 을 ${#configs[@]}개만 찾았습니다 (하한 $FLOOR — 2026-08-21 전수로 확정)."
    bad "  줄었다면 트리거 규칙이 사라진 것이고, 늘었다면 하한을 올려라."
    return 1
  fi
  note "발견한 vercel.json ${#configs[@]}개 (하한 $FLOOR)"

  [ -f "$judge" ] || { bad "판정자가 없습니다: scripts/vercel-should-build.sh"; return 1; }

  local cfg abs specs=() s p first_file tmp
  for cfg in "${configs[@]}"; do
    abs="$ROOT/$cfg"
    echo "-- $cfg"

    # 🔴 557 이 깨뜨린 그 모양. JSON 에는 주석이 없고 Vercel 스키마는 모르는 최상위 키를
    # 거부한다 — 설명을 슬래시 두 개로 시작하는 키에 끼워 넣으면 **배포가 죽는데 사이트는
    # 멀쩡해 보인다.**
    if grep -qE "$COMMENT_KEY_RE" "$abs"; then
      bad "설명용 주석 키가 있습니다 — Vercel 스키마가 거부합니다 (TASK-MONO-557)."
    fi

    if ! grep -q '"ignoreCommand"' "$abs"; then
      bad "ignoreCommand 가 없습니다 — 이 프로젝트는 모든 커밋에 배포를 굽습니다."
      continue
    fi

    specs=()
    while IFS= read -r line; do [ -n "$line" ] && specs+=("$line"); done \
      < <(extract_pathspecs "$abs")
    if [ "${#specs[@]}" -eq 0 ]; then
      bad "(4) ignoreCommand 에서 ':/...' pathspec 을 하나도 못 뽑았습니다 — 추출이 죽었거나 모양이 바뀌었습니다."
      continue
    fi
    note "pathspec ${#specs[@]}개: ${specs[*]}"

    # --- 임시 저장소로 판정자를 실제로 돌린다 -------------------------------
    tmp="$(mktemp -d)"
    git -C "$tmp" init -q
    git -C "$tmp" config user.email guard@local
    git -C "$tmp" config user.name guard

    first_file=""
    for s in "${specs[@]}"; do
      p="${s#:/}"
      case "$p" in
        *.*) : ;;                    # 이미 파일을 가리킨다
        *)   p="$p/f.txt" ;;         # 디렉터리를 가리킨다
      esac
      mkdir -p "$tmp/$(dirname "$p")"
      echo base > "$tmp/$p"
      [ -n "$first_file" ] || first_file="$p"
    done
    mkdir -p "$tmp/tasks/ready"
    echo base > "$tmp/tasks/ready/unrelated.md"

    git -C "$tmp" add -A >/dev/null
    git -C "$tmp" commit -qm base

    # (3) fail-open — 부모 커밋이 없다. 판정 불가는 **빌드 진행**이어야 한다.
    run_cell "(3) 부모 커밋 없음 -> 빌드 (fail-open)" 1 "$tmp" "$judge" "${specs[@]}"

    # (2) 무관한 경로만 바뀐 커밋 -> 건너뜀
    echo changed > "$tmp/tasks/ready/unrelated.md"
    git -C "$tmp" commit -qam unrelated
    run_cell "(2) tasks/ 만 바뀐 커밋 -> 건너뜀" 0 "$tmp" "$judge" "${specs[@]}"

    # (1) 자기 경로가 바뀐 커밋 -> 빌드함.  <- 이 가드의 본체
    echo changed > "$tmp/$first_file"
    git -C "$tmp" commit -qam own
    run_cell "(1) $first_file 이 바뀐 커밋 -> 빌드" 1 "$tmp" "$judge" "${specs[@]}"

    rm -rf "$tmp"
  done

  [ "$fail" -eq 0 ] || return 1
  echo "[vercel-triggers] ok — 발견한 ${#configs[@]}개 전부 자기 경로에 빌드하고 무관한 커밋을 건너뜁니다."
  return 0
}

# =============================================================================
# --self-test — **진짜 트리의 사본**을 망가뜨려 가드가 무는지 본다
# =============================================================================
# 🔴 가드가 통과한다는 사실만으로는 아무것도 모른다. 통과가 무효일 수 있기 때문이다
# (틀린 입력도 통과하는가?). 여기서는 실제 vercel.json 들을 임시 저장소로 복제한 뒤
# 세 가지로 망가뜨리고, **각각이 exit 1 을 내는지** 확인한다. 무망가 사본은 exit 0.
self_test() {
  local src="$(cd "$(dirname "$SELF")/.." && pwd)"
  local rc=0 t out

  _mk() {   # 진짜 트리의 vercel.json + 판정자를 임시 저장소로 복제
    local d; d="$(mktemp -d)"
    ( cd "$src" && git ls-files '*vercel.json' scripts/vercel-should-build.sh ) | while IFS= read -r f; do
      mkdir -p "$d/$(dirname "$f")"; cp "$src/$f" "$d/$f"
    done
    git -C "$d" init -q; git -C "$d" config user.email t@l; git -C "$d" config user.name t
    git -C "$d" add -A >/dev/null; git -C "$d" commit -qm base
    echo "$d"
  }
  _run() { VERCEL_GUARD_ROOT="$1" bash "$SELF" >/dev/null 2>&1; echo $?; }
  _expect() {
    local what="$1" want="$2" got="$3"
    if [ "$got" = "$want" ]; then echo "  ok: $what (rc=$got)";
    else echo "  x  $what — rc=$got 인데 $want 를 기대했습니다."; rc=1; fi
  }

  echo "[vercel-triggers] --self-test — 진짜 트리의 사본을 망가뜨려 문는지 확인합니다"

  t="$(_mk)"; _expect "무망가 사본은 통과" 0 "$(_run "$t")"; rm -rf "$t"

  # (a) ignoreCommand 를 지운다 -> 모든 커밋이 배포를 굽는 상태
  t="$(_mk)"
  out="$(cd "$t" && git ls-files '*vercel.json' | head -1)"
  sed -i '/"ignoreCommand"/d' "$t/$out"
  git -C "$t" commit -qam mutate
  _expect "(a) ignoreCommand 제거 -> 문다" 1 "$(_run "$t")"; rm -rf "$t"

  # (b) 주석 키를 끼워 넣는다 -> 557 이 배포를 깨뜨린 그 모양
  t="$(_mk)"
  out="$(cd "$t" && git ls-files '*vercel.json' | head -1)"
  sed -i '2i\  "//note": "설명",' "$t/$out"
  git -C "$t" commit -qam mutate
  _expect "(b) 주석 키 삽입 -> 문다" 1 "$(_run "$t")"; rm -rf "$t"

  # (c) 설정 하나를 지운다 -> 모집단이 하한 아래로
  t="$(_mk)"
  out="$(cd "$t" && git ls-files '*vercel.json' | head -1)"
  git -C "$t" rm -q "$out"; git -C "$t" commit -qam mutate
  _expect "(c) 설정 1개 삭제 -> 하한 위반으로 문다" 1 "$(_run "$t")"; rm -rf "$t"

  # (d) pathspec 모양을 바꾼다 -> 추출 0건. **조용히 통과하면 안 된다.**
  t="$(_mk)"
  out="$(cd "$t" && git ls-files '*vercel.json' | head -1)"
  sed -i "s/':\//'X\//g" "$t/$out"
  git -C "$t" commit -qam mutate
  _expect "(d) pathspec 모양 변경 -> 추출 0건으로 문다" 1 "$(_run "$t")"; rm -rf "$t"

  [ "$rc" -eq 0 ] && echo "[vercel-triggers] --self-test ok" || echo "[vercel-triggers] --self-test 실패"
  return "$rc"
}

if [ "${1:-}" = "--self-test" ]; then
  self_test; exit $?
fi
main; exit $?
