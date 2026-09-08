#!/usr/bin/env bash
# =============================================================================
# 공개 카탈로그의 두 벌이 갈라지지 않는가 — TASK-MONO-638
# =============================================================================
# 🔴🔴 이 저장소의 상품 카탈로그는 **세 곳**에 있고, 세 곳 다 «진짜» 다:
#
#   ① 번들 시드   infra/demo/public-data/snapshots/store.json
#                 → 백엔드가 꺼져 있어도 방문자가 보는 것 (ADR-MONO-070)
#   ② 실제 시드   product-service db/migration/V8 + V19          (postgres)
#   ③ 실제 시드   product-service db/migration-h2/V8 + V12       (h2 로컬 프로파일)
#
# 한쪽만 늘리면 아무 에러도 안 난다. 대신 방문자가 **«로그인했더니 카탈로그가 줄었다»**
# 를 겪는다 — 기동 전에는 24개를 보고, 기동해서 로그인하면 8개를 본다. 그 화면은 고장으로
# 읽히지만 로그도 테스트도 아무 말을 안 한다.
#
# 🔵 선례: `verify-demo-wrapper.sh` 의 (z32) 가 `projects.sh` ↔ `handler.py` 의 묶음 표를
#    같은 이유로 대조한다. 두 벌인 것은 피할 수 없고(다른 런타임), 피할 수 있는 것은
#    **갈라지는 것**이다.
#
# 하한을 둘 것인가 — **두지 않는다.** 그 판단의 근거를 적는다
# -----------------------------------------------------------------------------
# 이 모집단(카탈로그 상품)은 «늘어나는 것이 목표» 라 하한이 성립할 것처럼 보인다. 그러나
# 상품 수는 **제품 결정**이고 줄어들 수도 있다(단종·개편). 거기에 하한을 걸면 그 결정이
# 도착하는 날 «성공이 고장난다» — 이 저장소가 일곱 번 밟은 함정이다.
# 🔴 대신 **비공허성**에 건다: 세 출처가 각각 **0건이 아니어야** 한다. 0건은 «상품이 없다»
#    가 아니라 **추출식이 깨진 것**이고, 그때 세 빈 집합은 서로 «일치» 하며 조용히 통과한다.
#
# 🔵 카테고리 하한(AC-1: 카테고리마다 4개 이상)은 **여기서 재지 않는다.** 그것은 제품
#    결정이고 이 가드의 축(두 벌이 같은가)과 다르다. 섞으면 카테고리를 개편하는 날 이
#    가드가 빨개지고, 그 빨강의 사유가 «갈라졌다» 로 잘못 읽힌다.
#
# 사용
# -----------------------------------------------------------------------------
#   bash scripts/check-seed-catalogue-parity.sh              # 대조
#   bash scripts/check-seed-catalogue-parity.sh --self-test  # 실트리를 변형해 무는지 증명
# =============================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SELFTEST=0
for a in "$@"; do
  case "$a" in
    --self-test) SELFTEST=1 ;;
    *) echo "알 수 없는 옵션: $a" >&2; exit 2 ;;
  esac
done

BUNDLE="infra/demo/public-data/snapshots/store.json"
PG_DIR="projects/ecommerce-microservices-platform/apps/product-service/src/main/resources/db/migration"
H2_DIR="projects/ecommerce-microservices-platform/apps/product-service/src/main/resources/db/migration-h2"

# ---------------------------------------------------------------------------
# 추출 — 상품 id 만. 변형(c0000000-)·카테고리(a0000000-)는 접두사로 갈린다.
# ---------------------------------------------------------------------------
# 🔴 파일 이름을 고정하지 않는다. `V*seed*.sql` 을 전부 훑는다 — 다음 사람이 V20 을 더할 때
#    이 스크립트를 고쳐야 한다면, 안 고치는 날 그 파일은 **대조에서 빠진 채** 초록이 된다.
seed_ids() {  # $1 = 마이그레이션 디렉터리
  local d="$1" f
  for f in "$d"/V*seed*.sql; do
    [ -e "$f" ] || continue
    sed -n "s/^  ('\(b0000000-[0-9a-f-]*\)'.*/\1/p" "$f"
  done | sort -u
}

bundle_ids() {
  node -e '
    const fs = require("fs");
    const d = JSON.parse(fs.readFileSync(process.argv[1], "utf8")).data;
    for (const p of d.products) console.log(p.id);
  ' "$BUNDLE" | sort -u
}

scan() {  # 위반을 찍고 마지막 줄에 COUNT=<n>
  local b pg h2 nb npg nh2 viol=0
  b="$(bundle_ids)"; pg="$(seed_ids "$PG_DIR")"; h2="$(seed_ids "$H2_DIR")"
  nb="$(printf '%s\n' "$b"  | grep -c . || true)"
  npg="$(printf '%s\n' "$pg" | grep -c . || true)"
  nh2="$(printf '%s\n' "$h2" | grep -c . || true)"

  # 🔴 비공허성부터. 0건이면 세 빈 집합이 «일치» 하며 조용히 통과한다.
  if [ "$nb" = "0" ] || [ "$npg" = "0" ] || [ "$nh2" = "0" ]; then
    echo "POP_FAIL=추출이 0건입니다 — 번들 $nb · postgres $npg · h2 $nh2 (추출식이 깨졌습니까?)"
    echo "COUNT=-1"; return
  fi

  local only
  only="$(comm -23 <(printf '%s\n' "$b") <(printf '%s\n' "$pg"))"
  if [ -n "$only" ]; then
    echo "번들에만 있고 postgres 실제 시드에 없는 상품:"; printf '  %s\n' $only
    echo "    → 기동해서 로그인하면 이 상품들이 사라집니다."
    viol=$((viol + 1))
  fi
  only="$(comm -13 <(printf '%s\n' "$b") <(printf '%s\n' "$pg"))"
  if [ -n "$only" ]; then
    echo "postgres 실제 시드에만 있고 번들에 없는 상품:"; printf '  %s\n' $only
    echo "    → 백엔드가 꺼져 있는 동안 이 상품들이 안 보입니다."
    viol=$((viol + 1))
  fi
  only="$(comm -3 <(printf '%s\n' "$pg") <(printf '%s\n' "$h2"))"
  if [ -n "$only" ]; then
    echo "postgres 와 h2 실제 시드가 다릅니다(한쪽에만 있는 id):"; printf '  %s\n' $only
    echo "    → h2 는 로컬 프로파일이 씁니다. 한쪽만 고치면 그 경로에서만 조용히 갈라집니다."
    viol=$((viol + 1))
  fi

  echo "SETS=번들 $nb · postgres $npg · h2 $nh2"
  echo "COUNT=$viol"
}

# ---------------------------------------------------------------------------
# --self-test — 실트리를 변형해서 무는 것을 증명한다
# ---------------------------------------------------------------------------
if [ "$SELFTEST" = 1 ]; then
  probe() { scan | tail -1 | sed 's/COUNT=//'; }

  c1="$(probe)"
  [ "$c1" = "0" ] || { echo "self-test (1) 대조군 실패: 손대지 않은 트리에서 위반 $c1 건" >&2; scan >&2; exit 1; }

  tmp="$ROOT/.selftest-parity"
  cleanup() { rm -rf "$tmp" "$PG_DIR/V99__selftest_seed.sql" "$H2_DIR/V99__selftest_seed.sql"; }
  trap cleanup EXIT
  mkdir -p "$tmp"

  # (2) postgres 실제 시드에만 상품을 하나 더한다 → 물어야 한다
  printf "%s\n" "INSERT INTO products (id) VALUES" "  ('b0000000-0000-0000-0000-0000000000ff');" \
    > "$PG_DIR/V99__selftest_seed.sql"
  grep -q "0000000000ff" "$PG_DIR/V99__selftest_seed.sql" || { echo "self-test (2) 주입 실패" >&2; exit 1; }
  c2="$(probe)"
  [ "$c2" != "0" ] || { echo "self-test (2) bite 실패: postgres 에만 상품을 더했는데 안 물었습니다" >&2; exit 1; }
  rm -f "$PG_DIR/V99__selftest_seed.sql"

  # (3) 🔴 h2 에만 더한다 — (2)와 **다른 축**이다. (2)만 있으면 h2 축은 한 번도 안 재어진다.
  printf "%s\n" "INSERT INTO products (id) VALUES" "  ('b0000000-0000-0000-0000-0000000000fe');" \
    > "$H2_DIR/V99__selftest_seed.sql"
  c3="$(probe)"
  [ "$c3" != "0" ] || { echo "self-test (3) bite 실패: h2 에만 상품을 더했는데 안 물었습니다" >&2; exit 1; }
  rm -f "$H2_DIR/V99__selftest_seed.sql"

  # (4) 대조군 — 되돌리면 다시 초록이어야 한다(위 두 칸이 파일을 남기지 않았는가)
  c4="$(probe)"
  [ "$c4" = "0" ] || { echo "self-test (4) 대조군 실패: 되돌렸는데 위반 $c4 건 — 주입 파일이 남았습니까?" >&2; exit 1; }

  cleanup; trap - EXIT
  echo "check-seed-catalogue-parity --self-test: OK — 대조군 2칸 · bite 2칸(postgres 축 · h2 축)"
  exit 0
fi

out="$(scan)"
count="$(printf '%s\n' "$out" | tail -1 | sed 's/COUNT=//')"

if printf '%s\n' "$out" | grep -q '^POP_FAIL='; then
  printf '%s\n' "$out" | grep '^POP_FAIL=' | sed 's/^POP_FAIL=/check-seed-catalogue-parity: FAIL — /' >&2
  echo "  → 0건을 훑고 «세 집합이 일치» 로 통과하는 것이 이 부류 가드의 표준 고장입니다." >&2
  exit 1
fi

if [ "$count" != "0" ]; then
  echo "check-seed-catalogue-parity: FAIL — 공개 카탈로그의 두 벌이 갈라졌습니다." >&2
  printf '%s\n' "$out" | grep -vE '^(COUNT|SETS)=' >&2
  exit 1
fi

echo "check-seed-catalogue-parity: OK — $(printf '%s\n' "$out" | sed -n 's/^SETS=//p') · 세 출처의 상품 id 집합이 같습니다."
echo "  (상품 수에는 하한을 두지 않습니다 — 그것은 제품 결정입니다. 하한은 «추출이 0건이 아닌가» 에만 겁니다.)"
