#!/usr/bin/env bash
# =============================================================================
# check-public-domains.sh — 공개 호스트명이 정본 표와 어긋나지 않는다 (TASK-MONO-584)
# =============================================================================
# 데모의 공개 도메인은 최소 **다섯 곳**에 나타난다: terraform 의 `allowed_origins` 두 자리,
# 루트 README, IdP 의 `redirect_uri` 시드(TASK-MONO-574), 론처의 화면 링크(TASK-MONO-583).
# 이 저장소는 *"한 사실이 두 절에 있으면 한쪽만 고쳐진다"* 로 반복해서 데였고, 바로 직전
# `TASK-MONO-579` 가 론처의 **두 집**을 없애느라 티켓 하나를 통째로 썼다.
#
#   bash scripts/check-public-domains.sh [--self-test]
#
# -----------------------------------------------------------------------------
# 🔴 이 스크립트에는 도메인이 적혀 있지 않다 — 그게 요점이다
# -----------------------------------------------------------------------------
# 모집단은 `TEMPLATE.md` 의 정본 표에서 **파생**한다. 두 가지 이유가 겹친다:
#  1. 여기는 루트 `scripts/` 라 **project-agnostic 이어야 한다**(CLAUDE.md HARDSTOP-03).
#  2. 하드코딩한 모집단을 쓰는 가드는 **대상이 바뀌어도 자기가 적어둔 것을 계속 통과시킨다.**
#     도메인이 바뀌면(또는 서브도메인이 늘면) 이 가드는 그것을 봐야 한다.
#
# -----------------------------------------------------------------------------
# 🔴🔴 판정 축과 검색 축이 같은 매체다 — 자기 문서에 걸리는 함정
# -----------------------------------------------------------------------------
# 이 저장소는 (z12)·(z14)에서 *"문서를 설명하는 문장이 판별자에 걸리는"* 함정을 밟았다.
# 여기서도 `TEMPLATE.md` 와 티켓 본문이 도메인 문자열을 잔뜩 담는다. 그래서 술어를
# **"도메인이 나타나는가"** 가 아니라 **"나타난 것이 전부 선언된 이름인가"** 로 세운다 —
# 정상적으로 문서에 적힌 이름은 통과하고, **선언 안 된 이름만** 문다.
# =============================================================================
set -uo pipefail

SELF="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/$(basename "${BASH_SOURCE[0]}")"
ROOT="${PUBDOM_GUARD_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"

CANON="TEMPLATE.md"
BEGIN_RE='PUBLIC-HOSTNAMES-BEGIN'
END_RE='PUBLIC-HOSTNAMES-END'

# 🔴 하한 = 실제 화면 수. 2026-08-26 전수: launcher · fan · web-store · console.
#    표가 비거나 파싱이 깨지면 칸 (1)(2)가 **공허하게 통과**하므로 이 하한이 그것을 막는다.
#    화면이 늘면 올려라 — 그 순간이 새 호스트명을 배분할 자리다.
FLOOR="${PUBDOM_GUARD_FLOOR:-4}"

fail=0
note() { echo "  $*"; }
bad()  { echo "  x $*"; fail=1; }

# --- 정본 표 파싱 ------------------------------------------------------------
canon_block() {
  awk -v b="$BEGIN_RE" -v e="$END_RE" '
    $0 ~ b { inb = 1; next }
    $0 ~ e { inb = 0 }
    inb    { print }
  ' "$ROOT/$CANON" 2>/dev/null
}

# 🔴🔴 **열을 지정해서 뽑는다 — 행 전체를 grep 하면 안 된다.**
#    초판은 행에서 백틱 토큰을 전부 긁었고, 표의 «대응 `*.local`» 열까지 빨려 들어와
#    모집단이 11개가 되고 apex 가 `console.local` 로 판정됐다. **가드는 통과했다** —
#    엉뚱한 축을 재면서. 통과가 무효일 수 있다는 것이 이 저장소의 반복 교훈이다.
#    공개 호스트명은 **2번째 열**이다(파이프 구분: 앞의 빈 필드가 1번).
HOST_COL=3   # `| 화면 | 공개 호스트명 | ...` → awk -F'|' 기준 3번 필드

table_rows() {
  # 헤더와 구분선(`|---|`)을 뺀 데이터 행만.
  canon_block | grep '^|' | grep -v '^|[[:space:]]*-\{1,\}' | grep -v '^| 화면 '
}

declared_hosts() {
  table_rows | awk -F'|' -v c="$HOST_COL" '{print $c}' \
    | grep -oE '`[a-z0-9.-]+\.[a-z]{2,}`' | tr -d '`' | sort -u
}

# `launcher` 행의 호스트명 — CORS 에 들어가야 하는 그 하나.
launcher_host() {
  table_rows | grep -i 'launcher' | awk -F'|' -v c="$HOST_COL" '{print $c}' \
    | grep -oE '`[a-z0-9.-]+\.[a-z]{2,}`' | tr -d '`' | head -1
}

# apex = 가장 짧은 선언 이름(라벨 수 최소). 검색 축을 이것으로 만든다.
apex_of() {
  local h best="" bn=99 n
  while IFS= read -r h; do
    [ -n "$h" ] || continue
    n="$(printf '%s' "$h" | tr -cd '.' | wc -c)"
    if [ "$n" -lt "$bn" ]; then bn="$n"; best="$h"; fi
  done
  printf '%s' "$best"
}

main() {
  echo "[public-domains] 공개 호스트명 정본 대조  (root=$ROOT)"

  [ -f "$ROOT/$CANON" ] || { bad "(0) 정본 파일이 없습니다: $CANON"; return 1; }

  local hosts=() h
  while IFS= read -r h; do [ -n "$h" ] && hosts+=("$h"); done < <(declared_hosts)

  # --- (0) 탐지기 생존 대조군 --------------------------------------------
  # 🔴 앵커를 못 찾거나 표가 비면 **통과가 아니라 실패**다. 못 읽은 것과 위반이 없는 것은
  #    다른 사건이고, 후자만 초록이어야 한다.
  if [ "${#hosts[@]}" -eq 0 ]; then
    bad "(0) $CANON 의 PUBLIC-HOSTNAMES 구간에서 호스트명을 하나도 못 뽑았습니다."
    bad "    → 0건은 '위반이 없다' 가 아니라 **파싱이 깨진 것**입니다(앵커/표 모양을 바꿨나요?)."
    return 1
  fi
  if [ "${#hosts[@]}" -lt "$FLOOR" ]; then
    bad "(0) 선언된 호스트명이 ${#hosts[@]}개뿐입니다 (하한 $FLOOR — 2026-08-26 전수: launcher·fan·web-store·console)."
    bad "    → 표가 줄었다면 그 화면은 **주소를 잃은 것**이고, 늘었다면 하한을 올리세요."
    return 1
  fi
  note "(0) 정본에서 호스트명 ${#hosts[@]}개 파생 (하한 $FLOOR)"

  local apex; apex="$(printf '%s\n' "${hosts[@]}" | apex_of)"
  [ -n "$apex" ] || { bad "(0) apex 를 판정하지 못했습니다."; return 1; }

  # --- (0b) 🔴🔴 모집단 동질성 — **이 칸이 초판의 결함을 잡는다** -----------
  # 선언된 이름은 전부 같은 apex 아래여야 한다. 초판은 표의 «대응 *.local» 열까지
  # 긁어 모집단에 `console.local` 이 섞였고, apex 가 그것으로 판정되면서
  # **가드가 엉뚱한 축을 재며 통과했다.** 그 통과는 아무것도 증명하지 않았다.
  # ⇒ 섞이면 조용히 다른 것을 재는 대신 **여기서 실패한다.**
  local stray=0
  for h in "${hosts[@]}"; do
    case "$h" in
      "$apex"|*".$apex") : ;;
      *) bad "(0b) 선언 목록에 apex('$apex') 밖의 이름이 섞였습니다: '$h'"; stray=1 ;;
    esac
  done
  if [ "$stray" -ne 0 ]; then
    bad "    → 모집단이 오염되면 apex 판정이 흔들리고, 이 가드는 **엉뚱한 축을 재면서 통과**합니다."
    bad "    → 정본 표의 **공개 호스트명 열**(${HOST_COL}번 필드)만 뽑히는지 확인하세요."
    return 1
  fi
  note "(0) apex = $apex · 선언 ${#hosts[@]}개 전부 그 아래 (0b 통과)"

  # --- (1) 트리에 나타난 모든 출현이 **선언된 이름**인가 -------------------
  # 🔵 술어를 이렇게 세우는 이유는 위 § 자기 문서 함정. 문서가 정상 이름을 적는 것은
  #    통과해야 하고, 오타·미선언 서브도메인만 물어야 한다.
  # 🔴🔴 **서술 디렉터리는 검색에서 뺀다 — 그러나 무엇을 못 보게 되는지 적는다.**
  #    `tasks/` 와 `docs/adr/` 는 **기각된 대안을 적는 것이 일**이다. TASK-MONO-584 만 해도
  #    «`demo.<apex>` 도 성립하지만 집은 하나여야 한다» 라고 쓴다 — 옳은 문장이고,
  #    가드를 통과시키려고 그 문장을 지우면 **기각 이유가 사라진다.** 가드의 눈을 피하려고
  #    문서를 고치는 습관이 오탐보다 나쁘다.
  #
  # 🔵 위 문장에서 예시를 `demo.<apex>` 로 **일반화해 적은 것도 의도된 것**이다: 초판은
  #    실제 호스트명을 리터럴로 적었고 **이 가드가 자기 주석에 걸렸다**(판별자가 자기
  #    문서에 매치되는 그 함정 — 이 저장소가 (z12)·(z14)에서 밟았다). 여기서는 리터럴이
  #    아무 정보도 더하지 않으므로 일반화가 맞다. 티켓 본문은 다르다 — 거기서는 기각된
  #    **그 이름**이 결정 기록이므로 지우지 않고, 대신 그 디렉터리를 검색에서 뺐다.
  # 🔴 제외는 구멍이다(«각각 옳은 두 제외가 합쳐져 구멍이 된다»). 그래서 아래 대조군이
  #    **제외가 실제 소비자까지 삼키지 않았는지** 단언한다. 이 둘이 유일한 제외다.
  #    ⇒ 안 덮이는 것: 티켓/ADR 본문의 오타. 그것들은 설정이 아니므로 배포를 안 바꾼다.
  local searched=() seen=() bad_hits=0 s
  while IFS= read -r s; do [ -n "$s" ] && searched+=("$s"); done < <(
    git -C "$ROOT" grep -lie "${apex//./\\.}" -- \
      ':!tasks/*' ':!docs/adr/*' ':!*.lock' ':!*pnpm-lock.yaml' ':!*/node_modules/*' 2>/dev/null
  )
  # 대조군 — 제외가 넓어져 실제 소비자를 삼키면 여기서 걸린다.
  local must f miss=0
  for must in "$CANON" "infra/demo/aws/terraform/terraform.tfvars.example"; do
    printf '%s\n' "${searched[@]}" | grep -qx "$must" || { bad "(1) 검색 모집단에 '$must' 이 없습니다 — 제외가 실제 소비자를 삼켰습니다."; miss=1; }
  done
  [ "$miss" -eq 0 ] || return 1
  note "(1) 검색 대상 ${#searched[@]}개 파일 (서술 디렉터리 tasks/·docs/adr/ 제외)"

  while IFS= read -r s; do [ -n "$s" ] && seen+=("$s"); done < <(
    for f in "${searched[@]}"; do
      grep -hoiE "[a-z0-9.-]*${apex//./\\.}" "$ROOT/$f" 2>/dev/null
    done | tr 'A-Z' 'a-z' | sed 's/^[.-]*//' | sort -u
  )
  for s in "${seen[@]}"; do
    if ! printf '%s\n' "${hosts[@]}" | grep -qx "$s"; then
      bad "(1) 정본에 없는 호스트명이 트리에 있습니다: '$s'"
      bad "    → 오타라면 그 화면만 죽고 나머지가 멀쩡해 **원인이 안 보입니다**."
      bad "    → 새 화면이라면 $CANON 의 정본 표에 먼저 선언하세요."
      bad_hits=$((bad_hits + 1))
    fi
  done
  # 🔴 대조군 — 출현이 0건이면 이 칸은 아무것도 안 재고 통과한다. 정본 파일 자신이
  #    apex 를 담고 있으므로 0건은 grep 이 죽었다는 뜻이다.
  if [ "${#seen[@]}" -eq 0 ]; then
    bad "(1) 트리에서 '$apex' 출현을 0건 찾았습니다 — 정본 파일조차 담고 있는데 0건이면 **검색이 죽은 것**입니다."
    return 1
  fi
  [ "$bad_hits" -eq 0 ] && note "(1) 트리의 '$apex' 출현 ${#seen[@]}종 전부 정본에 선언됨"

  # --- (2) launcher 오리진이 CORS 허용 목록에 있는가 -----------------------
  # 🔴 빠지면 브라우저가 컨트롤 API 를 못 부르고 **Start 버튼이 조용히 죽는다**.
  #    그 실패는 plan 에도 CI 에도 안 보이고 방문자에게만 보인다(TASK-MONO-579 § CORS 구멍).
  local lh tfv
  lh="$(launcher_host)"
  tfv="$ROOT/infra/demo/aws/terraform/terraform.tfvars.example"
  if [ -z "$lh" ]; then
    bad "(2) 정본 표에서 'launcher' 행을 찾지 못했습니다 — 어느 오리진이 CORS 에 들어가야 하는지 알 수 없습니다."
  elif [ ! -f "$tfv" ]; then
    bad "(2) tfvars.example 이 없습니다: $tfv"
  elif ! grep -q "https://${lh}\"" "$tfv"; then
    bad "(2) launcher 오리진 'https://${lh}' 가 allowed_origins 에 없습니다 ($tfv)."
    bad "    → CORS 목록은 TASK-MONO-579 이후 **이 변수 하나가 전부**입니다."
    bad "    → 빠지면 론처의 Start 버튼이 **조용히** 죽습니다 (plan 도 CI 도 못 봅니다)."
  else
    note "(2) launcher 오리진 https://${lh} 가 allowed_origins 에 있음"
  fi

  if [ "$fail" -eq 0 ]; then
    echo "[public-domains] ok — 정본 ${#hosts[@]}개 · 트리 출현 ${#seen[@]}종 전부 선언됨 · launcher 오리진 CORS 등재"
  else
    echo "[public-domains] 실패"
  fi
  return "$fail"
}

# =============================================================================
# --self-test — 진짜 트리의 사본을 망가뜨려 **무는지** 본다
# =============================================================================
# 🔴 통과한다는 사실만으로는 아무것도 모른다 — 통과가 무효일 수 있다(틀린 입력도 통과하는가?).
self_test() {
  local src="$(cd "$(dirname "$SELF")/.." && pwd)"
  local rc=0 t out

  _mk() {
    local d; d="$(mktemp -d)"
    ( cd "$src" && git ls-files 'TEMPLATE.md' 'infra/demo/aws/terraform/*' 'README.md' ) \
      | while IFS= read -r f; do mkdir -p "$d/$(dirname "$f")"; cp "$src/$f" "$d/$f"; done
    git -C "$d" init -q; git -C "$d" config user.email t@l; git -C "$d" config user.name t
    git -C "$d" add -A >/dev/null; git -C "$d" commit -qm base
    echo "$d"
  }
  _run() { PUBDOM_GUARD_ROOT="$1" bash "$SELF" >/dev/null 2>&1; echo $?; }
  _expect() {
    local what="$1" want="$2" got="$3"
    if [ "$got" = "$want" ]; then echo "  ok: $what (rc=$got)"
    else echo "  x  $what — rc=$got 인데 $want 를 기대했습니다."; rc=1; fi
  }

  echo "[public-domains] --self-test — 진짜 트리의 사본을 망가뜨려 문는지 확인합니다"

  t="$(_mk)"; _expect "무망가 사본은 통과" 0 "$(_run "$t")"; rm -rf "$t"

  # (a) 미선언 서브도메인을 심는다 -> 문다. 이 가드의 본체.
  t="$(_mk)"
  out="$(cd "$t" && git ls-files 'README.md' | head -1)"
  apex="$(PUBDOM_GUARD_ROOT="$t" bash "$SELF" 2>/dev/null | sed -n 's/.*apex = //p' | head -1)"
  if [ -z "$apex" ]; then
    echo "  x  (a) 주입 실패 — apex 를 못 읽어 심을 이름을 만들 수 없습니다."; rc=1
  else
    printf '\ntypo.%s\n' "$apex" >> "$t/$out"
    # 🔴🔴 무는지 읽기 전에 **주입이 들어갔는지** 단언한다. 0건이면 "안 물었다" 와
    #    "시험한 적이 없다" 가 구별되지 않고, 후자는 초록으로 보인다.
    if ! grep -q "typo\.${apex}" "$t/$out"; then
      echo "  x  (a) 주입 실패 — 이 칸은 아무것도 시험하지 않았습니다."; rc=1
    else
      git -C "$t" commit -qam mutate
      _expect "(a) 미선언 서브도메인 -> 문다" 1 "$(_run "$t")"
    fi
  fi
  rm -rf "$t"

  # (b) allowed_origins 에서 launcher 오리진을 뺀다 -> 문다.
  t="$(_mk)"
  if [ -f "$t/infra/demo/aws/terraform/terraform.tfvars.example" ]; then
    lh="$(PUBDOM_GUARD_ROOT="$t" bash "$SELF" 2>/dev/null | sed -n 's/.*오리진 https:\/\/\([^ ]*\) 가.*/\1/p' | head -1)"
    if [ -z "$lh" ]; then
      echo "  x  (b) 주입 실패 — launcher 오리진을 못 읽었습니다."; rc=1
    else
      sed -i "\|https://${lh}\"|d" "$t/infra/demo/aws/terraform/terraform.tfvars.example"
      if grep -q "https://${lh}\"" "$t/infra/demo/aws/terraform/terraform.tfvars.example"; then
        echo "  x  (b) 주입 실패 — 줄이 안 지워졌습니다."; rc=1
      else
        git -C "$t" commit -qam mutate
        _expect "(b) launcher 오리진을 CORS 에서 제거 -> 문다" 1 "$(_run "$t")"
      fi
    fi
  else
    echo "  x  (b) tfvars.example 이 사본에 없습니다 — 이 칸이 아무것도 시험하지 않습니다."; rc=1
  fi
  rm -rf "$t"

  # (c) 정본 표를 비운다 -> **하한**으로 문다. 0건이 "위반 없음" 으로 보이면 안 된다.
  t="$(_mk)"
  before="$(grep -c '`' "$t/TEMPLATE.md" || true)"
  awk -v b="$BEGIN_RE" -v e="$END_RE" '
    $0 ~ b { print; inb = 1; next }
    $0 ~ e { inb = 0 }
    !inb   { print }
  ' "$t/TEMPLATE.md" > "$t/TEMPLATE.md.new" && mv "$t/TEMPLATE.md.new" "$t/TEMPLATE.md"
  after="$(grep -c '`' "$t/TEMPLATE.md" || true)"
  if [ "$after" -ge "$before" ]; then
    echo "  x  (c) 주입 실패 (before=$before after=$after) — 표가 안 지워졌습니다."; rc=1
  else
    git -C "$t" commit -qam mutate
    _expect "(c) 정본 표를 비움 -> 하한/파싱으로 문다" 1 "$(_run "$t")"
  fi
  rm -rf "$t"

  [ "$rc" -eq 0 ] && echo "[public-domains] --self-test ok" || echo "[public-domains] --self-test 실패"
  return "$rc"
}

# 🔵 TASK-MONO-602 — 정본 표의 launcher 호스트를 **조회만** 하는 모드.
#    `infra/demo/aws/site/check-launcher-fresh.sh` 가 자기 기본 오리진을 여기서 얻는다.
#    🔴 파서를 그쪽으로 **복사하지 않는다** — 표의 열 구조가 바뀌면 한쪽만 고쳐지고,
#    그때 낡은 쪽은 «틀린 답» 이 아니라 **조용한 통과**를 낸다(이 파일 § HOST_COL 주석이
#    바로 그 사고를 기록해 두었다).
#    exit 3 = 못 찾음. 부르는 쪽이 fail-closed 로 다루라는 뜻이다.
if [ "${1:-}" = "--print-launcher-host" ]; then
  _lh="$(launcher_host)"
  if [ -z "$_lh" ]; then
    echo "정본 표($CANON)에서 'launcher' 행의 호스트명을 찾지 못했습니다." >&2
    exit 3
  fi
  printf '%s\n' "$_lh"; exit 0
fi

if [ "${1:-}" = "--self-test" ]; then
  self_test; exit $?
fi
main; exit $?
