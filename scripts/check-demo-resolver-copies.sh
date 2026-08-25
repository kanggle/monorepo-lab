#!/usr/bin/env bash
# =============================================================================
# check-demo-resolver-copies.sh — 데모 백엔드 **해석기가 두 번째로 생기는 순간** RED
#                                 (TASK-MONO-577 / ADR-MONO-068)
# =============================================================================
# ADR-MONO-068 의 결정은 **A(앱별 구현)** 다 — 공유 구조를 만들지 않는다. 근거는 하나:
# 결정 시점에 이 해석기의 소비자가 **하나**였다(단계 2 = web-store). 저장소 자신의 규칙
# (`CLAUDE.md` § Project-scoped shared modules)이 공유 모듈을 **둘 이상**일 때 도입한다고
# 적었고, 사본이 하나면 A 의 실패 모드("한 벌만 고쳐진다")는 **발동할 수 없다**.
#
# 🔴 그 논거는 **두 번째가 생기는 순간 무너진다.** 그런데 두 번째는 조용히 생긴다 —
#    다른 앱의 티켓에서, 다른 사람이, 옆 앱을 복사해서. 그때 아무 일도 일어나지 않으면
#    A 는 결정이 아니라 **미룸**이 된다.
#
# ⇒ 이 가드가 그 순간을 **소리나게** 만든다. 비용 0 인 "나중에 승격" 트리거는 자기 숫자를
#   명시하고 물어야 한다. 이 파일의 숫자는 **2** 다.
#
# -----------------------------------------------------------------------------
# 무엇을 세는가 — **앱**이지 파일이 아니다
# -----------------------------------------------------------------------------
# 한 앱의 해석기가 두 파일로 나뉘는 것은 정상이다(설정 + 호출). 공유 여부를 가르는 단위는
# **앱**이므로 `projects/<p>/apps/<a>` · `projects/<p>/web/<a>` 접두사로 접어서 센다.
#
# -----------------------------------------------------------------------------
# 🔴🔴 이 가드가 스스로에게 물어야 하는 것들 (하나라도 빠지면 조용한 초록이 된다)
# -----------------------------------------------------------------------------
#  (1) 승격 트리거    해석기를 가진 **앱이 2개 이상**이면 RED.
#  (2) 표시 없는 구현  내용 탐지에 걸렸는데 마커가 없으면 RED.
#                     → 마커만 세면 마커를 안 붙이는 것으로 우회할 수 있다.
#  (3) 🔴 탐지기 생존 대조군  내용 패턴(`DEMO_API_BASE`)이 **론처에서** 보이는지 확인한다.
#                     론처는 그 값으로 `/status` 를 부르고 **모집단 밖**이다. 0 건이면
#                     초록이 아니라 **판정 불가(exit 2)** — 패턴이 죽은 것이다.
#  (4) 🔴 모집단 하한  `next.config.*` 로 **유도한** 앱이 전부 모집단에 보여야 한다.
#                     개수 하한만으로는 한 앱이 통째로 빠져도 통과한다.
#
# 🔴 **오늘 (1) 의 실제 개수는 0 이다.** 단계 2 가 아직 착수되지 않았다. 그래서 라이브
#    실행은 지금 아무것도 증명하지 않는다 — (3)(4)가 있는 이유가 그것이고, 실제 물기는
#    `--self-test` 가 합성 트리로 증명한다.
#
# -----------------------------------------------------------------------------
# 🔴 첫 판이 틀렸던 곳 — 남겨 둔다
# -----------------------------------------------------------------------------
# 모집단을 `git ls-files 'projects/*/apps/*/src/**/*.ts'` 로 잡았다. 그 pathspec 은
# **`src/` 바로 아래 파일을 놓친다**(실측: 실제 저장소 672 vs 정직한 필터 1186).
# 해석기를 `src/demo-backend.ts` 에 두면 가드가 못 본다 — 술어가 재려던 것을 못 재는
# 결함이고, `--self-test` 의 합성 트리(파일이 `src/` 바로 아래에 있다)가 그것을 잡았다.
#
# -----------------------------------------------------------------------------
# 종료코드
# -----------------------------------------------------------------------------
#   0 = 해석기 0~1개 (결정이 아직 유효하다)
#   1 = 2개 이상, 또는 표시 없는 구현  → ADR-MONO-068 의 전제가 무너졌다
#   2 = 판정 불가 (탐지기가 죽었거나 모집단이 갈라졌다)
#
# 🔴 2 를 0 으로 접지 마라. "확인 못 했다" 를 "괜찮다" 로 번역하는 것이 이 저장소가
#    반복해서 당한 실패다.
# =============================================================================
set -uo pipefail

# 내용 탐지 패턴. 앱이 컨트롤 플레인에 닿으려면 이 베이스 주소가 필요하고, 그 이름은
# `infra/demo/aws/site/build.sh` 가 정한 계약이다(환경변수 `DEMO_API_BASE`).
CONTENT_RE='DEMO_API_BASE'
# 각 구현이 달아야 하는 기계 판독 마커. ADR-MONO-068 이 요구한다.
MARKER_RE='DEMO-RESOLVER:'
# 글롭이 통째로 갈라진 경우만 잡는 최소한. 진짜 축은 아래 declared_apps() 커버리지다.
MIN_APP_FILES=100
# 🔴 이 숫자가 이 가드의 결정이다. ADR-MONO-068 § Decision 과 같은 값이어야 한다.
MAX_APPS_WITH_RESOLVER=1

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SELFTEST=0
[ "${1:-}" = "--self-test" ] && SELFTEST=1

say()  { echo "[resolver-copies] $*"; }
die1() { say "✗ $*"; exit 1; }
die2() { say "? $*"; exit 2; }

APP_RE='^projects/[^/]+/(apps|web)/[^/]+'

# 모집단 — 세 앱의 소스. `git ls-files` 다(파일시스템이 아니라 **커밋된 것**을 본다).
# 🔴 호출자는 스테이지 뒤에 돌려야 한다 — 이 저장소가 네 번 데인 자리다.
app_files() {
  git -C "$ROOT" ls-files -- projects 2>/dev/null \
    | grep -E "${APP_RE}/src/.*\.(ts|tsx)$" \
    | grep -vE '(__tests__|\.test\.|\.spec\.)'
}

# 앱 목록의 권위 = `next.config.*` 가 있는 디렉터리. 모집단이 이들을 **전부** 담아야 한다.
declared_apps() {
  git -C "$ROOT" ls-files -- projects 2>/dev/null \
    | grep -E "${APP_RE}/next\.config\.[a-z]+$" \
    | sed -E 's#/next\.config\.[a-z]+$##' | sort -u
}

fold_to_app() { sed -E "s#(${APP_RE#^})/.*#\1#"; }

run_guard() {
  local files n_files
  files="$(app_files)"
  n_files="$(printf '%s' "$files" | grep -c . || true)"

  # (4a) 파일 수 하한
  [ "$n_files" -ge "$MIN_APP_FILES" ] \
    || die2 "모집단 하한 미달: 앱 소스 $n_files 개 (하한 $MIN_APP_FILES). 글롭이 갈라졌거나 스테이지 전에 돌렸습니다."

  # (4b) 🔴 선언된 앱이 전부 모집단에 보이는가 — 개수 하한이 못 잡는 축이고, 앱이 늘어도 따라간다.
  local declared covered missing=""
  declared="$(declared_apps)"
  [ -n "$declared" ] || die2 "선언된 앱이 0개입니다(next.config.* 를 못 찾음) — 모집단의 권위가 사라졌습니다."
  covered="$(printf '%s\n' "$files" | fold_to_app | sort -u)"
  while IFS= read -r a; do
    [ -n "$a" ] || continue
    printf '%s\n' "$covered" | grep -qxF "$a" || missing="$missing  $a"$'\n'
  done <<EOF
$declared
EOF
  [ -z "$missing" ] || die2 "선언된 앱인데 모집단에 안 보입니다:
$missing→ 글롭이 그 앱의 소스 배치를 놓칩니다. 그 앱에 해석기가 생겨도 이 가드는 **못 봅니다**."

  # (3) 탐지기 생존 대조군 — 모집단 밖의 알려진 사용처(론처)에서 같은 패턴이 보여야 한다
  local control
  control="$(git -C "$ROOT" grep -lE "$CONTENT_RE" -- 'infra/demo/aws/site/' 2>/dev/null | grep -c . || true)"
  [ "$control" -ge 1 ] \
    || die2 "대조군 실패: 내용 패턴 '$CONTENT_RE' 이 론처(infra/demo/aws/site/)에서도 0건입니다. 패턴이 죽었으므로 앱 쪽 0건은 **없음이 아니라 못 봤음**입니다."

  # 내용/마커 탐지 — 모집단 안에서만
  local hits markers
  hits="$(printf '%s\n' "$files" | tr '\n' '\0' | xargs -0 -r git -C "$ROOT" grep -lE "$CONTENT_RE" -- 2>/dev/null || true)"
  markers="$(printf '%s\n' "$files" | tr '\n' '\0' | xargs -0 -r git -C "$ROOT" grep -lE "$MARKER_RE" -- 2>/dev/null || true)"

  # (2) 표시 없는 구현
  local unmarked=""
  while IFS= read -r f; do
    [ -n "$f" ] || continue
    printf '%s\n' "$markers" | grep -qxF "$f" || unmarked="$unmarked  $f"$'\n'
  done <<EOF
$hits
EOF
  [ -z "$unmarked" ] || die1 "표시 없는 해석기 구현이 있습니다 (ADR-MONO-068 이 마커를 요구합니다):
$unmarked→ 소스에 '$MARKER_RE <app>' 주석을 넣으세요. 마커가 없으면 승격 트리거가 그 구현을 못 셉니다."

  # (1) 승격 트리거 — 앱 단위로 접어서 센다
  local apps n_apps
  apps="$(printf '%s\n' "$markers" | grep . | fold_to_app | sort -u || true)"
  n_apps="$(printf '%s' "$apps" | grep -c . || true)"

  if [ "$n_apps" -gt "$MAX_APPS_WITH_RESOLVER" ]; then
    say "해석기를 가진 앱 $n_apps 개:"
    printf '%s\n' "$apps" | sed 's/^/    /'
    die1 "ADR-MONO-068 의 전제가 무너졌습니다 — 소비자가 **둘 이상**입니다.
→ 그 ADR 이 A(앱별 구현)를 고른 근거는 '소비자가 하나' 였습니다. 그 근거가 지금 사라졌습니다.
→ 이 가드를 완화해서 통과시키지 마세요. TASK-MONO-577 의 A/B/C 를 **실제로 결정**할 때입니다.
→ 결정하면 이 가드는 그 결정에 맞게 **교체**됩니다(삭제가 아니라)."
  fi

  say "OK — 해석기를 가진 앱 $n_apps 개 (상한 $MAX_APPS_WITH_RESOLVER) · 앱 소스 $n_files 개 · 선언 앱 $(printf '%s' "$declared" | grep -c .) 개 전부 커버 · 대조군 $control 건"
  if [ "$n_apps" -eq 0 ]; then
    say "  🔵 0 개입니다 — 단계 2 가 아직 착수되지 않았습니다. 이 통과는 '아직 안 생겼다' 이지 '못 본다' 가 아닙니다((3)(4)가 그것을 갈라 줍니다)."
  fi
  return 0
}

# ---------------------------------------------------------------------------
# --self-test — 합성 트리로 **물기**를 증명한다.
#
# 🔴 실제 모집단의 해석기는 오늘 0 건이라 라이브 실행은 아무것도 증명하지 않는다. 그래서
#    자기점검이 이 가드의 **본체**다.
# 🔵 왜 임시 저장소인가: 이 가드는 `git ls-files` 를 읽으므로 커밋된 트리가 필요하다.
#    실제 저장소를 변형하면 남의 작업분을 건드린다(이 저장소가 그렇게 데인 적이 있다).
# 🔵 합성 트리의 소스는 **`src/` 바로 아래**에 둔다 — 첫 판의 pathspec 결함이 정확히 거기서
#    드러났으므로, 그 배치를 유지하는 것이 이 자기점검의 일부다.
# ---------------------------------------------------------------------------
selftest() {
  local tmp fails=0 passes=0
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' RETURN

  build_case() {
    local name="$1"; shift
    local d="$tmp/$name"
    rm -rf "$d"; mkdir -p "$d/scripts" "$d/infra/demo/aws/site"
    cp "$ROOT/scripts/check-demo-resolver-copies.sh" "$d/scripts/"
    printf 'window.DEMO_API_BASE = "x";\n' > "$d/infra/demo/aws/site/index.html"
    local i app
    for app in projects/p1/apps/a1 projects/p2/web/a2; do
      mkdir -p "$d/$app/src"
      printf 'export default {};\n' > "$d/$app/next.config.ts"
      # 🔵 앱당 120 개인 이유: 마지막 대조군이 한 앱의 `src/` 를 통째로 지운다. 60 개였을
      #    때는 남은 60 개가 파일 하한(100)에 먼저 걸려 **(4a)가 발화**했고, 그러면 rc 는
      #    맞지만 **엉뚱한 이유로** 맞는 것이 된다. 하네스의 크기가 판정 대상을 바꾼 사례다.
      for i in $(seq 1 120); do printf 'export const x%d = %d;\n' "$i" "$i" > "$d/$app/src/f$i.ts"; done
    done
    while [ $# -gt 0 ]; do
      local spec="$1"; shift
      local p="${spec%%:*}" body="${spec#*:}"
      mkdir -p "$d/$(dirname "$p")"
      printf '%s\n' "$body" > "$d/$p"
    done
    ( cd "$d" && git init -q && git -c core.autocrlf=false add -A \
        && git -c user.email=t@t -c user.name=t commit -qm x ) >/dev/null 2>&1
    printf '%s' "$d"
  }

  cell() {
    local name="$1" want="$2" needle="$3"; shift 3
    local d out rc mark="✗"
    d="$(build_case "$name" "$@")"
    out="$(bash "$d/scripts/check-demo-resolver-copies.sh" 2>&1)"; rc=$?
    if [ "$rc" = "$want" ] && { [ -z "$needle" ] || printf '%s' "$out" | grep -qF "$needle"; }; then
      mark="✓"; passes=$((passes+1))
    else
      fails=$((fails+1))
    fi
    printf '  %s %-32s 기대rc=%s 실제rc=%s\n' "$mark" "$name" "$want" "$rc"
    [ "$mark" = "✗" ] && printf '%s\n' "$out" | sed 's/^/       /'
    return 0
  }

  echo "[resolver-copies] --self-test"
  cell "0개(오늘의 상태)" 0 "해석기를 가진 앱 0 개"

  cell "1개(단계 2 착수 후)" 0 "해석기를 가진 앱 1 개" \
    "projects/p1/apps/a1/src/demo-backend.ts:// DEMO-RESOLVER: web-store
const b = process.env.DEMO_API_BASE;"

  cell "🔴 2개 = 승격 트리거" 1 "소비자가 **둘 이상**" \
    "projects/p1/apps/a1/src/demo-backend.ts:// DEMO-RESOLVER: web-store
const b = process.env.DEMO_API_BASE;" \
    "projects/p2/web/a2/src/demo-backend.ts:// DEMO-RESOLVER: console
const b = process.env.DEMO_API_BASE;"

  cell "🔴 마커 없는 구현" 1 "표시 없는 해석기 구현" \
    "projects/p1/apps/a1/src/demo-backend.ts:const b = process.env.DEMO_API_BASE;"

  cell "🔵 한 앱 두 파일" 0 "해석기를 가진 앱 1 개" \
    "projects/p1/apps/a1/src/demo-backend.ts:// DEMO-RESOLVER: web-store
const b = process.env.DEMO_API_BASE;" \
    "projects/p1/apps/a1/src/demo-backend-cache.ts:// DEMO-RESOLVER: web-store
const c = process.env.DEMO_API_BASE;"

  # 대조군 — 가드가 **스스로 죽었을 때** 초록을 내지 않는가
  local d out rc
  d="$(build_case "ctl-detector-dead")"
  rm -f "$d/infra/demo/aws/site/index.html"
  ( cd "$d" && git -c core.autocrlf=false add -A && git -c user.email=t@t -c user.name=t commit -qm x2 ) >/dev/null 2>&1
  out="$(bash "$d/scripts/check-demo-resolver-copies.sh" 2>&1)"; rc=$?
  if [ "$rc" = 2 ] && printf '%s' "$out" | grep -qF "대조군 실패"; then
    printf '  ✓ %-32s 기대rc=2 실제rc=%s\n' "🔴 탐지기 사망 → 판정 불가" "$rc"; passes=$((passes+1))
  else
    printf '  ✗ %-32s 기대rc=2 실제rc=%s\n' "🔴 탐지기 사망 → 판정 불가" "$rc"; fails=$((fails+1))
    printf '%s\n' "$out" | sed 's/^/       /'
  fi

  d="$(build_case "ctl-app-invisible")"
  rm -rf "$d/projects/p2/web/a2/src"
  ( cd "$d" && git -c core.autocrlf=false add -A && git -c user.email=t@t -c user.name=t commit -qm x3 ) >/dev/null 2>&1
  out="$(bash "$d/scripts/check-demo-resolver-copies.sh" 2>&1)"; rc=$?
  if [ "$rc" = 2 ] && printf '%s' "$out" | grep -qF "모집단에 안 보입니다"; then
    printf '  ✓ %-32s 기대rc=2 실제rc=%s\n' "🔴 선언 앱이 안 보임 → 판정 불가" "$rc"; passes=$((passes+1))
  else
    printf '  ✗ %-32s 기대rc=2 실제rc=%s\n' "🔴 선언 앱이 안 보임 → 판정 불가" "$rc"; fails=$((fails+1))
    printf '%s\n' "$out" | sed 's/^/       /'
  fi

  echo "[resolver-copies] self-test: $passes passed, $fails failed"
  [ "$fails" -eq 0 ]
}

if [ "$SELFTEST" = 1 ]; then selftest; exit $?; fi
run_guard
