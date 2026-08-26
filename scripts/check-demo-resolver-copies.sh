#!/usr/bin/env bash
# =============================================================================
# check-demo-resolver-copies.sh — 해석기 사본이 **갈라지는 것**을 막는다
#                                 (TASK-MONO-577 · 586 / ADR-MONO-068 § D5)
# =============================================================================
# 🔴 2026-08-26 에 **이 가드의 판정이 바뀌었다.** 아래 「이전 판」을 먼저 읽어라 —
#    옛 의미로 이 파일을 읽으면 숫자가 전부 어긋나 보인다.
#
# -----------------------------------------------------------------------------
# 이전 판 (TASK-MONO-577, 2026-08-26 이전) — **사본을 세어 2에서 RED**
# -----------------------------------------------------------------------------
# ADR-MONO-068 의 초기 결정은 **A(앱별 구현)** 였고 근거는 하나였다: 결정 시점에 소비자가
# **하나**였으므로 A 의 실패 모드("한 벌만 고쳐진다")가 **발동할 수 없었다**. 그 논거는
# 두 번째가 생기는 순간 무너지고, 두 번째는 조용히 생긴다 — 다른 앱의 티켓에서, 옆 앱을
# 복사해서. 그래서 상한을 1 로 두고 **2에서 RED** 를 냈다.
#
# -----------------------------------------------------------------------------
# 현재 판 (§ D5, 소유자 정확형 지정) — **사본은 허용하되 «갈라지는 것»을 막는다**
# -----------------------------------------------------------------------------
# 트리거는 예정대로 발화했고(TASK-MONO-586 이 두 번째를 썼다), 소유자가 **C** 를 골랐다:
# 사본 + **정규화 동일성 가드**. 그래서 판정이 둘로 갈린다:
#
#   (1a) 정규화 동일성   사본이 둘 이상이면 **프로젝트 고유 축을 지운 뒤 비교**한다.
#                        다르면 RED. 사본을 허용한 대가가 이것이다.
#   (1b) 승격 트리거     사본이 **3개 이상**이면 RED — C 는 무제한이 아니다(§ D5.4).
#
# 🔴 § D3 이 못 박은 대로, 결정이 바뀌면 이 가드는 **교체**된다(삭제가 아니라). 이 파일이
#    그 교체다. 다음에 또 바뀌면 이 절을 다시 쓰되 **이전 판을 지우지 마라** — 오늘
#    이 파일을 읽는 사람이 "왜 상한이 1이 아닌가" 를 물을 수 있어야 한다.
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
#  (1a) 정규화 동일성  사본이 둘 이상이면 정규화 후 **전부 같아야** 한다. 다르면 RED.
#                     🔴 비교 대상은 «마커를 가진 파일» 이 아니라 **구현 파일**이다 —
#                     마커는 호출부에도 붙고 호출부는 정당하게 다르다(§ IMPL_RE).
#  (1b) 승격 트리거    해석기를 가진 앱이 **3개 이상**이면 RED (§ D5.4).
#  (2) 표시 없는 구현  내용 탐지에 걸렸는데 마커가 없으면 RED.
#                     → 마커만 세면 마커를 안 붙이는 것으로 우회할 수 있다.
#  (3) 🔴 탐지기 생존 대조군  내용 패턴(`DEMO_API_BASE`)이 **론처에서** 보이는지 확인한다.
#                     론처는 그 값으로 `/status` 를 부르고 **모집단 밖**이다. 0 건이면
#                     초록이 아니라 **판정 불가(exit 2)** — 패턴이 죽은 것이다.
#  (4) 🔴 모집단 하한  `next.config.*` 로 **유도한** 앱이 전부 모집단에 보여야 한다.
#                     개수 하한만으로는 한 앱이 통째로 빠져도 통과한다.
#  (5) 🔴 마커는 있는데 구현이 안 잡히면 **판정 불가(exit 2)**. 비교를 조용히 건너뛰면
#                     «드리프트 없음» 과 «비교 못 함» 이 **같은 초록**이 된다.
#
# 🔵 사본이 1개 이하일 때 이 가드가 내는 초록은 «갈라지지 않았다» 가 아니라
#    **«비교할 것이 없다»** 이고, 요약 줄이 그것을 따로 말한다.
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
#   0 = 사본 0~2개이고 정규화 후 서로 같다 (§ D5 의 C 가 아직 성립한다)
#   1 = 정규화 후 다르다(드리프트) · 사본 3개 이상(승격) · 표시 없는 구현
#   2 = 판정 불가 (탐지기가 죽었거나 · 모집단이 갈라졌거나 · 구현 파일을 못 찾았다)
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
# 🔴🔴 마커는 **구현과 호출부 양쪽**에 붙는다 — 실측(2026-08-26): web-store 는
#    마커 파일이 4개인데 그중 3개는 소비자(`route.ts` · `config/api.ts` · Notice 위젯)다.
#    마커만으로 «구현» 을 고르면 소비자까지 비교하게 되고, 소비자는 **정당하게 다르다**.
#    🔵 그래서 구현은 파일명이 아니라 **모듈이 내보내는 API** 로 판별한다 —
#    이 함수를 정의하는 파일이 그 앱의 해석기다(실측: 앱당 정확히 1개).
IMPL_RE='export (async )?function resolveDemoBackend'
# 글롭이 통째로 갈라진 경우만 잡는 최소한. 진짜 축은 아래 declared_apps() 커버리지다.
MIN_APP_FILES=100
# 🔴 이 숫자가 이 가드의 결정이다. ADR-MONO-068 § D5.4 와 같은 값이어야 한다.
#    C 는 «사본 무제한» 이 아니다 — D5.4 가 *"세 번째 사본이 오면 그때 B 로 승격"* 이라
#    적었고, 그 시점엔 정규화 실측이 3표본이 되어 있다. 그래서 RED 는 **3에서** 난다.
PROMOTE_AT_APPS=3

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

# ---------------------------------------------------------------------------
# 정규화 — **프로젝트 고유 축을 지우고 나머지는 그대로 둔다** (ADR-MONO-068 § D5.1)
#
# 🔴 문자 동일성 가드는 성립할 수 없다. § D1 이 *"앱마다 자기 스택으로"* 라 했다.
#    2026-08-26 실측: 두 사본은 **정규화 전 코드 71줄 중 4줄**만 달랐고, 그 4줄이 전부
#    아래 축이다 — ① `SERVICE_PREFIX` ② 폴백 env 이름 2개 ③ 폴백 기본값 URL.
#    ④ 마커의 앱 이름은 주석이라 아래에서 함께 제거된다.
#
# 🔵 `DEMO_API_BASE` 는 **정규화하지 않는다.** 그 이름은 앱마다 다른 값이 아니라
#    `infra/demo/aws/site/build.sh` 가 정한 **계약**이다. 한쪽만 바꾸면 그 앱은 데모에서
#    조용히 죽는데, env 이름을 통째로 정규화하면 가드가 그것을 **못 본다**.
#
# 🔴🔴 **이 정규화가 덮지 않는 것 — 주석이다.**
#    주석 전용 줄은 전부 버린다. 두 사본의 주석은 **정당하게 갈린다**(각자 자기 앱에서
#    관측한 것을 적는다). 그 대가로 **누군가 한 사본의 판단 근거를 통째로 지워도 이 가드는
#    초록**이다. 사본 하나가 177줄 중 **87줄(49%)이 주석**이라는 것을 생각하면 작은 구멍이
#    아니다. 이것은 «못 본 것» 이 아니라 **고른 것**이고, 여기 이름을 적어 둔다.
# ---------------------------------------------------------------------------
normalize_resolver() {
  awk '/^[[:space:]]*(\/\/|\/\*|\*)/ {next} /^[[:space:]]*$/ {next} {print}' \
  | sed -E \
      -e 's/process\.env\.DEMO_API_BASE/process.env.@@CTLPLANE@@/g' \
      -e "s/(SERVICE_PREFIX[[:space:]]*=[[:space:]]*)'[^']*'/\1'<PREFIX>'/" \
      -e 's/process\.env\.[A-Z0-9_]+/process.env.<ENV>/g' \
      -e "s#'https?://[^']*'#'<DEFAULT>'#g" \
      -e 's/@@CTLPLANE@@/DEMO_API_BASE/g'
}

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

  # (1) 사본 판정 — 앱 단위로 접어서 센다
  local apps n_apps
  apps="$(printf '%s\n' "$markers" | grep . | fold_to_app | sort -u || true)"
  n_apps="$(printf '%s' "$apps" | grep -c . || true)"

  # (1b) 🔴 승격 트리거 — **세 번째**에서 RED (ADR-MONO-068 § D5.4)
  if [ "$n_apps" -ge "$PROMOTE_AT_APPS" ]; then
    say "해석기를 가진 앱 $n_apps 개:"
    printf '%s\n' "$apps" | sed 's/^/    /'
    die1 "ADR-MONO-068 § D5.4 의 전제가 소진됐습니다 — 사본이 **$PROMOTE_AT_APPS 개 이상**입니다.
→ C(사본 + 정규화 가드)를 고른 근거는 '되돌리기가 싸고, 세 번째가 오면 그때 B 로 간다' 였습니다.
→ 이 가드를 완화해서 통과시키지 마세요. 지금이 **B(공유 패키지)를 실제로 결정**할 때이고,
   그 결정의 근거가 될 정규화 실측이 이제 **3표본** 있습니다.
→ 결정하면 이 가드는 그 결정에 맞게 **교체**됩니다(삭제가 아니라)."
  fi

  # (1a) 🔴 정규화 동일성 — 사본이 둘 이상이면 **드리프트를 잡는다**
  #      🔵 사본이 하나 이하면 잴 것이 없다. 그 사실을 아래 요약에 적는다 —
  #      '비교 0쌍' 을 '동일함' 으로 읽으면 안 된다.
  local pairs=0 impls
  impls="$(printf '%s\n' "$files" | tr '\n' '\0' | xargs -0 -r git -C "$ROOT" grep -lE "$IMPL_RE" -- 2>/dev/null || true)"
  if [ "$n_apps" -ge 2 ]; then
    # 🔴 마커를 가진 앱인데 구현 파일이 안 잡히면 **판정 불가**다.
    #    비교를 조용히 건너뛰면 «드리프트 없음» 과 «비교 못 함» 이 같은 초록이 된다.
    local a2
    while IFS= read -r a2; do
      [ -n "$a2" ] || continue
      printf '%s\n' "$impls" | grep -q "^$a2/" \
        || die2 "구현 파일을 못 찾았습니다 ($a2) — 마커는 있는데 '$IMPL_RE' 가 0건입니다. 해석기의 내보내는 API 가 바뀌었거나 판별 패턴이 죽었습니다. **판정 불가**입니다."
    done <<EOF
$apps
EOF
    local ref_app="" ref="" cur="" a f norm_tmp
    norm_tmp="$(mktemp -d)"
    while IFS= read -r a; do
      [ -n "$a" ] || continue
      # 한 앱의 해석기가 여러 파일이면 경로 순으로 이어 붙여 하나로 본다.
      : > "$norm_tmp/cur"
      while IFS= read -r f; do
        [ -n "$f" ] || continue
        case "$f" in "$a"/*) ;; *) continue ;; esac
        git -C "$ROOT" show ":$f" 2>/dev/null | normalize_resolver >> "$norm_tmp/cur"
      done <<EOF
$(printf '%s\n' "$impls" | grep . | sort)
EOF
      if [ ! -s "$norm_tmp/cur" ]; then
        rm -rf "$norm_tmp"
        die2 "정규화 결과가 빈 파일입니다 ($a) — 스테이지 전에 돌렸거나 정규화가 전부를 지웠습니다. **판정 불가**입니다."
      fi
      if [ -z "$ref_app" ]; then
        ref_app="$a"; cp "$norm_tmp/cur" "$norm_tmp/ref"
      else
        pairs=$((pairs+1))
        if ! diff -q "$norm_tmp/ref" "$norm_tmp/cur" >/dev/null 2>&1; then
          say "정규화 후에도 다릅니다 — $ref_app  vs  $a"
          diff -u "$norm_tmp/ref" "$norm_tmp/cur" | sed 's/^/    /' | head -40
          rm -rf "$norm_tmp"
          die1 "해석기 사본이 **드리프트했습니다** (ADR-MONO-068 § D5).
→ C 를 고른 대가가 이것입니다: 사본은 허용하되 **갈라지는 것은 허용하지 않습니다**.
→ 위 diff 를 없애세요. 프로젝트 고유 축(SERVICE_PREFIX · 폴백 env 이름 · 폴백 기본값)은
   이미 정규화에서 지워졌으므로, 여기 남은 차이는 **진짜 로직 차이**입니다.
→ 한쪽이 옳다면 **양쪽을 함께** 고치세요. 한 벌만 고치는 것이 A 의 실패 모드였습니다."
        fi
      fi
    done <<EOF
$apps
EOF
    rm -rf "$norm_tmp"
  fi

  say "OK — 해석기를 가진 앱 $n_apps 개 (승격 $PROMOTE_AT_APPS) · 정규화 비교 $pairs 쌍 · 앱 소스 $n_files 개 · 선언 앱 $(printf '%s' "$declared" | grep -c .) 개 전부 커버 · 대조군 $control 건"
  if [ "$n_apps" -le 1 ]; then
    say "  🔵 사본이 $n_apps 개라 **동일성은 재지 않았습니다**(비교 0쌍). 이 통과는 '갈라지지 않았다' 가 아니라 '비교할 것이 없다' 입니다."
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

  # 🔵 합성 해석기 — 프로젝트 고유 축이 **서로 다르게** 들어 있다. 정규화가 그것들을
  #    지우고 나면 같아져야 한다. 축을 안 넣으면 이 칸은 "같은 파일 두 개" 를 잴 뿐이다.
  local IMPL_A="// DEMO-RESOLVER: web-store
const SERVICE_PREFIX = 'ecommerce';
const b = process.env.DEMO_API_BASE;
export async function resolveDemoBackend() { return SERVICE_PREFIX + b; }
export async function resolveUpstreamBaseUrl() {
  return process.env.API_URL_INTERNAL ?? 'http://localhost:8080';
}"
  local IMPL_B="// DEMO-RESOLVER: fan
const SERVICE_PREFIX = 'fan-platform';
const b = process.env.DEMO_API_BASE;
export async function resolveDemoBackend() { return SERVICE_PREFIX + b; }
export async function resolveUpstreamBaseUrl() {
  return process.env.GATEWAY_URL_INTERNAL ?? 'http://fan-platform.local';
}"

  cell "🔵 2개 · 정규화 동일 = 통과" 0 "정규화 비교 1 쌍" \
    "projects/p1/apps/a1/src/demo-backend.ts:$IMPL_A" \
    "projects/p2/web/a2/src/demo-backend.ts:$IMPL_B"

  # 🔴 bite — 프로젝트 고유가 **아닌** 한 줄(TTL)만 갈라 놓는다. 위 칸과 이 칸의 차이는
  #    그 한 줄뿐이므로, 이 칸이 물면 가드가 재는 것이 "정규화된 로직" 임이 증명된다.
  cell "🔴 2개 · 로직 드리프트 = RED" 1 "드리프트했습니다" \
    "projects/p1/apps/a1/src/demo-backend.ts:$IMPL_A" \
    "projects/p2/web/a2/src/demo-backend.ts:$IMPL_B
const TTL = 60000;"

  # 🔴 3개 = 승격. C 는 사본 무제한이 아니다 (ADR-MONO-068 § D5.4).
  cell "🔴 3개 = B 로 승격" 1 "D5.4 의 전제가 소진" \
    "projects/p1/apps/a1/src/demo-backend.ts:$IMPL_A" \
    "projects/p2/web/a2/src/demo-backend.ts:$IMPL_B" \
    "projects/p3/apps/a3/src/demo-backend.ts:$IMPL_A"

  # 🔴 마커는 있는데 구현이 없다 → 초록이 아니라 **판정 불가**.
  #    🔴 칸 이름에 `/` 를 넣지 마라 — `build_case` 가 `$tmp/$name` 을 디렉터리로
  #    쓰므로 경로가 갈라져 모집단이 0이 되고, rc 는 2로 **맞지만 이유가 틀린다**.
  #    비교를 조용히 건너뛰면 "드리프트 없음" 과 "비교 못 함" 이 같은 초록이 된다.
  cell "🔴 마커는 있고 구현 없음 = 판정불가" 2 "구현 파일을 못 찾았습니다" \
    "projects/p1/apps/a1/src/demo-backend.ts:// DEMO-RESOLVER: web-store
const b = process.env.DEMO_API_BASE;" \
    "projects/p2/web/a2/src/demo-backend.ts:// DEMO-RESOLVER: fan
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
