#!/usr/bin/env bash
# =============================================================================
# check-demo-resolver-copies.sh — 해석기 **구현이 앱 안으로 돌아오는 것**을 막는다
#                                 (TASK-MONO-614 / ADR-MONO-068 § D6 = B2)
# =============================================================================
# 🔴🔴 **이 가드의 명제는 두 번 바뀌었다.** 파일 이름은 «copies» 지만 오늘 이것이 재는 것은
#      «사본이 몇 개인가» 가 아니다. 아래 두 「이전 판」을 먼저 읽어라 — 옛 의미로 읽으면
#      숫자와 메시지가 전부 어긋나 보인다. (이름을 안 바꾼 이유는 그 이름이 `ci.yml` 의
#      잡 이름과 문서 여러 곳에 박혀 있고, 이름 변경은 그 자체로 한 건의 위험이기
#      때문이다 — `TASK-MONO-599` 가 체크 이름 드리프트로 데인 자리다.)
#
# -----------------------------------------------------------------------------
# 이전 판 ① (TASK-MONO-577, ~2026-08-26) — **사본을 세어 2에서 RED**
# -----------------------------------------------------------------------------
# ADR-MONO-068 의 초기 결정은 **A(앱별 구현)** 였고 근거는 하나였다: 결정 시점에 소비자가
# **하나**였으므로 A 의 실패 모드("한 벌만 고쳐진다")가 **발동할 수 없었다**. 그 논거는
# 두 번째가 생기는 순간 무너지고, 두 번째는 조용히 생긴다 — 다른 앱의 티켓에서, 옆 앱을
# 복사해서. 그래서 상한을 1 로 두고 **2에서 RED** 를 냈다.
#
# -----------------------------------------------------------------------------
# 이전 판 ② (§ D5, 2026-08-26) — **사본은 허용하되 «갈라지는 것»을 막는다**
# -----------------------------------------------------------------------------
# 트리거가 예정대로 발화했고(TASK-MONO-586 이 두 번째를 썼다) 소유자가 **C** 를 골랐다:
# 사본 + **정규화 동일성 가드**. 판정이 둘로 갈렸다 —
#   (1a) 정규화 동일성  사본이 둘 이상이면 프로젝트 고유 축을 지운 뒤 비교, 다르면 RED.
#   (1b) 승격 트리거     사본이 **3개 이상**이면 RED (§ D5.4 — C 는 무제한이 아니다).
# 🔵 그 판의 정규화가 실측한 것이 오늘의 설계를 낳았다: 두 사본은 **코드 71줄 중 4줄**만
#    달랐고 그 4줄이 전부 «프로젝트 고유 축» 이었다(SERVICE_PREFIX · 폴백 env 이름 2개 ·
#    폴백 기본값). 그래서 공유 패키지가 받는 설정이 **정확히 그 셋**이다.
#
# -----------------------------------------------------------------------------
# 현재 판 (§ D6 = B2, 2026-09-01 소유자 정확형 지정) — **앱은 구현을 갖지 않는다**
# -----------------------------------------------------------------------------
# 세 번째 소비자(`TASK-MONO-610` 의 포워더)가 도착해 (1b)가 발화했고, § D3 이 **완화를
# 미리 금지**했으므로 승격이 실행됐다. 구현은 이제 **`infra/demo/backend-resolver` 하나**이고
# 앱들은 그것을 import 해 자기 설정 셋만 건넨다.
#
# 🔴🔴 **그래서 옛 명제는 «공허하게 참» 이 된다** — 사본이 하나뿐이면 «사본들이 서로
#      같은가» 는 잴 것이 없어 자동으로 통과한다. § D5.3 이 *"결정이 바뀌면 이 가드는
#      삭제가 아니라 **교체**"* 라고 못 박은 이유가 이것이다.
#
# 🔵 **주장이 아니라 실측이다.** 두 가드를 같은 두 세계에 돌렸다 (2026-09-02):
#      세계 A(승격 완료)        옛 **rc=0** — 스스로 *"정규화 비교 0 쌍"* 이라 출력한다
#      세계 B(앱이 되찾아옴)    옛 **rc=2** — 사유가 *"판별 패턴이 죽었습니다"* 로 **틀렸다**
#    🔴🔴 B 가 더 나쁘다. 옛 가드는 침묵하지 않고 **가드 자신을 의심하라고 말한다** — 그
#    오진의 자연스러운 다음 행동이 **가드 완화**이고, § D3 이 금지한 바로 그것이다.
#    ⇒ 교체의 이유는 «안 문다» 가 아니라 **«물어도 틀린 곳을 가리킨다»** 다.
#
#   이전 판 ②            →  현재 판
#   ------------------------------------------------------------------
#   사본들이 서로 같은가   →  **앱이 자기 구현을 갖지 않는가**
#   정규화 다름·앱 3개↑    →  **패키지 밖에 구현이 하나라도 있으면 RED**
#   마커 = 세기 위한 표시   →  **마커 = 「여기 구현이 있다」. 패키지 밖이면 금지**
#
# 🔵 마커는 § D2 가 요구한 그대로 **유지**한다. 의미만 바뀌었다. 그리고 소비자에게 붙던
#    마커는 `DEMO-RESOLVER-CONSUMER:` 로 갈랐다 — 소비자는 정당하게 여럿이고, 구현 마커와
#    같은 문자열을 쓰면 이 가드가 소비자를 구현으로 잘못 센다(이전 판이 마커만으로 «구현» 을
#    고르지 못했던 것과 같은 이유다: 실측 당시 마커 파일 4개 중 3개가 소비자였다).
#
# -----------------------------------------------------------------------------
# 무엇을 세는가 — **앱**이지 파일이 아니다. 그리고 «앱» 은 **선언**으로 정한다
# -----------------------------------------------------------------------------
# 한 앱의 해석기가 두 파일로 나뉘는 것은 정상이다(설정 + 호출). 공유 여부를 가르는 단위는
# **앱**이므로 앱 디렉터리로 접어서 센다.
#
# 🔴 **모집단의 권위는 «경로 규약» 이 아니라 «선언» 이다** (TASK-MONO-613, 2026-09-01).
#    그 이전 판은 `APP_RE='^projects/[^/]+/(apps|web)/[^/]+'` 로 앱을 **경로 모양**으로
#    정의했고, 그러면 `projects/` 밖의 Next 앱은 모집단 밖이라 **판정이 조용히 안 물었다**.
#    실측 bite: `infra/demo/_x/{next.config.ts,src/demo-backend.ts}` 를 스테이지해도
#    그 판은 «앱 2 개 · rc=0» 을 그대로 냈다.
#    ⇒ `next.config.*` 가 있는 디렉터리는 **어디에 있든 앱**이고, 탐지 모집단은 그보다도
#      넓다(**어떤 `src/` 아래의 TS 든**). 가드는 「무는 쪽」으로 실패해야 한다.
#
# -----------------------------------------------------------------------------
# 🔴🔴 이 가드가 스스로에게 물어야 하는 것들 (하나라도 빠지면 조용한 초록이 된다)
# -----------------------------------------------------------------------------
#  (1) 🔴 **패키지 밖 구현**  모집단 안에서 구현 지문이 보이는 파일이 `PACKAGE_DIR` 밖에
#                     하나라도 있으면 RED. 이것이 오늘의 본 명제다.
#  (2) 🔵 지문은 **셋**이고 OR 다 — ① 계약 env 를 **직접 읽는다**(`process.env.DEMO_API_BASE`)
#                     ② 구현 마커를 달았다 ③ 옛 구현 시그니처를 내보낸다.
#                     마커만 세면 «마커를 안 붙이는 것» 으로 우회되고, 시그니처만 세면
#                     «이름을 바꾸는 것» 으로 우회된다. ①은 **계약**이라 못 바꾼다.
#  (3) 🔴 탐지기 생존 대조군  내용 패턴이 **론처에서** 보이는지 확인한다. 론처는 그 값으로
#                     `/status` 를 부르고 **모집단 밖**이다. 0 건이면 초록이 아니라
#                     **판정 불가(exit 2)** — 패턴이 죽은 것이다.
#  (3b) 🔴 그 론처가 모집단 **안**으로 들어오면 (3)은 자기 자신을 재게 된다 → exit 2.
#  (4) 🔴 모집단 하한 + `next.config.*` 로 유도한 앱이 **전부** 모집단에 보이는가.
#                     개수 하한만으로는 한 앱이 통째로 빠져도 통과한다.
#  (5) 🔴🔴 **패키지 자신이 구현을 갖는가**  `PACKAGE_DIR` 안에 구현 지문이 0 건이면
#                     **판정 불가(exit 2)**. 이것이 없으면 «전부 공유했다» 와
#                     **«해석기를 통째로 지웠다»** 가 같은 초록이 된다 — 그리고 후자는
#                     데모 백엔드 해석이 사라진 상태다.
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
#   0 = 구현이 `PACKAGE_DIR` 안에만 있다 (§ D6 의 B2 가 성립한다)
#   1 = 패키지 **밖**에 구현이 있다 (앱이 자기 구현을 되찾아왔다)
#   2 = 판정 불가 (탐지기가 죽었거나 · 모집단이 갈라졌거나 · **패키지에 구현이 없다**)
#
# 🔴 2 를 0 으로 접지 마라. "확인 못 했다" 를 "괜찮다" 로 번역하는 것이 이 저장소가
#    반복해서 당한 실패다.
# =============================================================================
set -uo pipefail

# 🔴 유일하게 구현이 허용되는 자리. `ADR-MONO-068 § D6 = B2` 가 정한 것이고, 자리 선택의
#    근거(그리고 기각한 후보 셋)는 `infra/demo/backend-resolver/README.md § 자리` 에 있다.
PACKAGE_DIR='infra/demo/backend-resolver'

# 🔵 론처는 모집단 **밖**이어야 한다 — 칸 (3)이 그 사실 위에 서 있다. (3b)가 지킨다.
LAUNCHER_DIR='infra/demo/aws/site'

# --- 구현 지문 셋 (OR) --------------------------------------------------------
# ① 컨트롤 플레인 베이스 주소를 **읽는 행위**. 앱이 이것을 직접 읽으면 그 앱은 해석을
#    자기가 하고 있는 것이다. 이름 자체는 `infra/demo/aws/site/build.sh` 가 정한 **계약**
#    이라 우회하려면 계약을 깨야 한다 — 그래서 셋 중 가장 튼튼하다.
#
# 🔴🔴 **`process.env.` 까지가 지문이다. 이름만으로는 안 된다** (2026-09-02, 착수 중 실측).
#    첫 판은 `CONTENT_RE='DEMO_API_BASE'` 였고, 그러자 **소비자 위젯 둘이 RED** 로 나왔다.
#    열어 보니 구현이 아니라 **주석의 산문**이었다 — *"판정이 `DEMO_API_BASE`(비공개 env)에
#    달려 있고…"*. 이 저장소가 이미 이름 붙여 둔 실패다: **판별자가 자기 설명 문구에 걸린다.**
#    🔵 그리고 그 오발화는 «가드가 너무 엄격» 이 아니라 **술어가 다른 것을 재고 있었다** 는
#    뜻이다 — 그대로 뒀다면 「구현을 되찾아왔다」 는 메시지가 **주석 한 줄**에 붙었을 것이다.
IMPL_ENV_RE='process\.env\.DEMO_API_BASE'
# ② 구현 마커 (ADR-MONO-068 § D2). 🔴 `DEMO-RESOLVER-CONSUMER:` 는 **매치되지 않는다**
#    (`DEMO-RESOLVER` 다음 글자가 `:` 가 아니라 `-` 다) — 소비자는 정당하게 여럿이다.
MARKER_RE='DEMO-RESOLVER:'
# ③ 옛 구현이 내보내던 시그니처. 패키지로 옮긴 뒤에도 남겨 두는 이유는, 앱이 옛 파일을
#    통째로 되살리는 것이 가장 흔한 회귀 경로이기 때문이다.
LEGACY_IMPL_RE='export (async )?function resolveDemoBackend'

# --- 탐지기 생존 대조군의 패턴 (지문과 **다르다**) -----------------------------
# 🔵 이것은 구현 지문이 아니라 **계약 이름 자체**다. 론처(`index.html`)는 그 값을
#    `window.DEMO_API_BASE` 로 읽지 `process.env` 로 읽지 않으므로, 지문 ①을 그대로 쓰면
#    대조군이 **언제나 0건**이 되어 가드가 매일 exit 2 를 낸다.
# 🔴 그러면 이 칸이 재는 것은 *"이름이 아직 살아 있는가"* 다 — `build.sh` 가 계약 이름을
#    바꾸면 지문 ①이 조용히 아무것도 안 물게 되는데, 그것을 여기서 잡는다.
#    지문 자체의 생존은 칸 (5)가 **모집단 안의 알려진 양성**(패키지)으로 잰다.
CONTRACT_RE='DEMO_API_BASE'

# 글롭이 통째로 갈라진 경우만 잡는 최소한. 진짜 축은 아래 declared_apps() 커버리지다.
MIN_APP_FILES=100

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SELFTEST=0
[ "${1:-}" = "--self-test" ] && SELFTEST=1

say()  { echo "[resolver-copies] $*"; }
die1() { say "✗ $*"; exit 1; }
die2() { say "? $*"; exit 2; }

# 앱 목록의 **권위** = `next.config.*` 가 있는 디렉터리. 경로 접두사를 묻지 않는다.
# 🔴 `git ls-files` 다(파일시스템이 아니라 **커밋된 것**). 호출자는 스테이지 뒤에 돌려야
#    한다 — 이 저장소가 네 번 데인 자리다.
declared_apps() {
  git -C "$ROOT" ls-files 2>/dev/null \
    | grep -E '(^|/)next\.config\.[a-z]+$' \
    | sed -E 's#(^|/)next\.config\.[a-z]+$##' \
    | awk 'NF == 0 { print "."; next } { print }' \
    | sort -u
}

# 모집단 — **어떤 `src/` 아래의 TS 든** 전부다. 선언된 앱으로 좁히지 않는다.
#
# 🔴🔴 **가드는 「무는 쪽」으로 실패해야 한다** (TASK-MONO-613). 모집단을 «선언된 앱» 으로
#    좁히면, `next.config.*` 가 **없는** 디렉터리(= Next 가 아닌 앱·서비스)에 해석기를 두는
#    것으로 판정을 또 피할 수 있다 — 방금 메운 구멍과 **같은 모양의 새 구멍**이다.
#    그래서 탐지는 넓게 하고, «앱» 으로 접는 일은 fold_to_app 이 (선언 우선 + 폴백) 처리한다.
#
# 🔵 이 모집단에는 `PACKAGE_DIR` 도 **들어온다**. 들어와야 한다 — 칸 (5)가 «패키지가
#    구현을 갖는가» 를 **같은 탐지기로** 재기 때문이다. 패키지를 모집단에서 빼면 그 칸은
#    다른 기전으로 재게 되고, 그러면 «탐지기가 죽었을 때» 를 못 가른다.
#
# 🔴 `git ls-files` 다(파일시스템이 아니라 **커밋된 것**). 스테이지 뒤에 돌려야 한다.
app_files() {
  git -C "$ROOT" ls-files 2>/dev/null \
    | grep -E '(^|/)src/.*\.(ts|tsx)$' \
    | grep -vE '(__tests__|\.test\.|\.spec\.)'
}

# 파일 → 앱 디렉터리. **선언 우선, 폴백은 `src/` 의 부모.**
#
# 🔵 선언(`next.config.*`)이 있으면 **가장 긴** 접두사를 고른다 — 앱이 중첩돼도 안쪽이 이긴다.
# 🔴 선언이 없으면 **버리지 않는다.** `<dir>/src/...` 의 `<dir>` 로 접는다 — 선언 없는
#    디렉터리에 구현을 두는 것으로 판정을 피하지 못하게 한다(§ app_files 의 「무는 쪽」).
fold_to_app() {
  local apps
  apps="$(declared_apps)"
  awk -v apps="$apps" '
    BEGIN { n = split(apps, A, "\n") }
    {
      best = ""
      for (i = 1; i <= n; i++)
        if (A[i] != "" && index($0, A[i] "/") == 1 && length(A[i]) > length(best)) best = A[i]
      if (best != "") { print best; next }
      if (match($0, /\/src\//)) { print substr($0, 1, RSTART - 1); next }
      # `src/` 도 없다 — 파일이 있는 디렉터리로 접는다. 세지 못하는 것보다 낫다.
      if (match($0, /\/[^\/]*$/)) print substr($0, 1, RSTART - 1)
    }'
}

# 주어진 파일 목록에서 **구현 지문**을 가진 것만 남긴다 (지문 셋의 OR).
impl_hits() {
  local files="$1"
  [ -n "$files" ] || return 0
  printf '%s\n' "$files" | tr '\n' '\0' \
    | xargs -0 -r git -C "$ROOT" grep -lE "$IMPL_ENV_RE|$MARKER_RE|$LEGACY_IMPL_RE" -- 2>/dev/null \
    | sort -u || true
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

  # (3b) 🔴 대조군 보호 — 론처가 모집단 **안**으로 들어오면 칸 (3)은 자기 자신을 재게 된다.
  #      오늘 론처는 정적 HTML 이라 `next.config.*` 가 없어 선언 기반 모집단에 안 들어온다.
  #      🔴 그 안전은 **우연이다** — 론처가 Next 로 바뀌는 날 이 가드는 조용히 눈이 먼다.
  local launcher_in
  launcher_in="$(printf '%s\n' "$declared" | grep -c "^${LAUNCHER_DIR}\(/\|$\)" || true)"
  [ "$launcher_in" = "0" ] \
    || die2 "론처($LAUNCHER_DIR)가 모집단 **안**에 들어왔습니다 — 칸 (3)의 대조군이 무효입니다.
→ (3)은 '모집단 밖의 알려진 사용처에서도 패턴이 보이는가' 로 탐지기 생존을 잽니다.
   대상이 모집단 안이면 그 질문은 자기 자신을 재는 것이 되어 **아무것도 증명하지 않습니다**.
→ 론처가 Next 앱이 됐다면 대조군을 **다른 모집단 밖 사용처**로 옮기세요."

  # (3) 탐지기 생존 대조군 — 모집단 밖의 알려진 사용처(론처)에서 같은 패턴이 보여야 한다
  local control
  control="$(git -C "$ROOT" grep -lE "$CONTRACT_RE" -- "$LAUNCHER_DIR/" 2>/dev/null | grep -c . || true)"
  [ "$control" -ge 1 ] \
    || die2 "대조군 실패: 계약 이름 '$CONTRACT_RE' 이 론처($LAUNCHER_DIR)에서도 0건입니다. 계약 이름이 바뀌었다면 지문 ① 은 **아무것도 안 뭅니다** — 앱 쪽 0건은 '없음' 이 아니라 '못 봤음' 입니다."

  # --- 구현 지문 탐지 -------------------------------------------------------
  local hits inside outside n_in n_out
  hits="$(impl_hits "$files")"
  inside="$(printf '%s\n' "$hits" | grep -E "^${PACKAGE_DIR}/" || true)"
  outside="$(printf '%s\n' "$hits" | grep . | grep -vE "^${PACKAGE_DIR}/" || true)"
  n_in="$(printf '%s' "$inside" | grep -c . || true)"
  n_out="$(printf '%s' "$outside" | grep -c . || true)"

  # (5) 🔴🔴 패키지 자신이 구현을 갖는가 — 「전부 공유했다」 와 「통째로 지웠다」 를 가른다
  [ "$n_in" -ge 1 ] \
    || die2 "공유 패키지($PACKAGE_DIR)에 구현이 **0 건**입니다 — **판정 불가**입니다.
→ 이 가드의 초록은 '앱이 구현을 갖지 않는다' 인데, 구현이 **아무 데도 없어도** 그 문장은 참입니다.
   그 둘을 구별하지 못하면 해석기를 통째로 지운 상태가 조용히 통과합니다.
→ 패키지가 옮겨졌다면 이 스크립트의 PACKAGE_DIR 을 함께 옮기세요(ADR-MONO-068 § D6).
→ 지문 셋이 하나도 안 걸린 것이라면 그것은 **탐지기가 죽은 것**입니다."

  # (1) 🔴 본 명제 — 패키지 **밖**의 구현은 하나도 없어야 한다
  if [ "$n_out" -ge 1 ]; then
    say "패키지 밖에서 구현 지문이 보이는 파일 $n_out 개:"
    printf '%s\n' "$outside" | sed 's/^/    /'
    say "그 파일들이 접히는 앱:"
    printf '%s\n' "$outside" | fold_to_app | sort -u | sed 's/^/    /'
    die1 "앱이 자기 해석기 구현을 갖고 있습니다 (ADR-MONO-068 § D6 = B2).
→ 승격의 요점은 '한 벌만 고쳐지는 것' 을 **구조적으로 불가능**하게 만드는 것입니다.
   앱 안에 구현이 하나라도 있으면 그 앱은 다시 따로 고쳐질 수 있습니다.
→ 고치는 법: 그 파일을 '@demo/backend-resolver' 를 import 하는 얇은 배선으로 되돌리세요.
   앱마다 다른 값 셋(servicePrefix · 폴백 env 이름 · 폴백 기본값)은 **설정으로** 넘깁니다.
→ 🔴 이 가드를 완화해서 통과시키지 마세요. § D3 이 그것을 금지합니다. 결정을 바꾸려면
   ADR-MONO-068 을 다시 열고, 그 결정에 맞게 이 가드를 **교체**하세요(삭제가 아니라)."
  fi

  say "OK — 구현은 패키지 1곳뿐($PACKAGE_DIR, 파일 $n_in 개) · 앱 안 구현 0 건 · 앱 소스 $n_files 개 · 선언 앱 $(printf '%s' "$declared" | grep -c .) 개 전부 커버 · 대조군 $control 건"
  say "  🔵 이 초록의 뜻은 '사본이 갈라지지 않았다' 가 **아니라** '앱이 자기 구현을 갖지 않는다' 입니다."
  return 0
}

# ---------------------------------------------------------------------------
# --self-test — 합성 트리로 **물기**를 증명한다.
#
# 🔴 실제 모집단의 앱 안 구현은 오늘 0 건이라 라이브 실행은 «안 물었다» 만 보여 준다.
#    그래서 자기점검이 이 가드의 **본체**다.
# 🔵 왜 임시 저장소인가: 이 가드는 `git ls-files` 를 읽으므로 커밋된 트리가 필요하다.
#    실제 저장소를 변형하면 남의 작업분을 건드린다(이 저장소가 그렇게 데인 적이 있다).
# 🔵 합성 트리의 소스는 **`src/` 바로 아래**에 둔다 — 첫 판의 pathspec 결함이 정확히 거기서
#    드러났으므로, 그 배치를 유지하는 것이 이 자기점검의 일부다.
# ---------------------------------------------------------------------------
selftest() {
  local tmp fails=0 passes=0
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' RETURN

  # 🔵 픽스처의 「오늘의 상태」 = 패키지에만 구현이 있다. 칸들은 여기서 **더하거나 뺀다**.
  local PKG_IMPL="// DEMO-RESOLVER: @demo/backend-resolver
const b = process.env.DEMO_API_BASE;
export function createDemoBackendResolver(c) { return { b, c }; }"

  # 앱이 구현을 되찾아온 모양 — 세 지문을 **각각** 갖는 변종들.
  local BACK_ALL="// DEMO-RESOLVER: web-store
const b = process.env.DEMO_API_BASE;
export async function resolveDemoBackend() { return b; }"
  local BACK_NO_MARKER="const b = process.env.DEMO_API_BASE;
export const resolve = () => b;"
  local BACK_LEGACY_ONLY="export async function resolveDemoBackend() { return null; }"
  local CONSUMER="// DEMO-RESOLVER-CONSUMER: web-store
import { createDemoBackendResolver } from '@demo/backend-resolver';
export const r = createDemoBackendResolver({ servicePrefix: 'ecommerce' });"

  build_case() {
    local name="$1"; shift
    local d="$tmp/$name"
    rm -rf "$d"; mkdir -p "$d/scripts" "$d/infra/demo/aws/site" "$d/infra/demo/backend-resolver/src"
    cp "$ROOT/scripts/check-demo-resolver-copies.sh" "$d/scripts/"
    printf 'window.DEMO_API_BASE = "x";\n' > "$d/infra/demo/aws/site/index.html"
    # 🔴 패키지 구현을 **기본으로** 깐다 — 칸 (5)의 전제다. 이것이 없으면 모든 칸이
    #    rc=2 로 죽고, 그 rc 는 «맞지만 이유가 틀린» 값이 된다.
    printf '%s\n' "$PKG_IMPL" > "$d/infra/demo/backend-resolver/src/index.ts"
    # 🔴 앱 목록을 **하드코딩하지 않는다** (TASK-MONO-613). 인자로 받은 파일 경로에서
    #    앱 디렉터리를 **유도**한다 — 픽스처가 자기가 만든 것을 선언하지 않으면 모집단
    #    밖으로 빠진다. 🔵 단 패키지는 앱이 아니므로 유도에서 제외한다.
    local i app derived
    derived="$(for spec in "$@"; do
        p="${spec%%:*}"
        case "$p" in
          infra/demo/backend-resolver/*) ;;
          */src/*) printf '%s\n' "${p%%/src/*}" ;;
        esac
      done | sort -u)"
    for app in $(printf '%s\n%s\n' "projects/p1/apps/a1
projects/p2/web/a2" "$derived" | grep . | sort -u); do
      mkdir -p "$d/$app/src"
      printf 'export default {};\n' > "$d/$app/next.config.ts"
      # 🔵 앱당 120 개인 이유: 대조군 하나가 한 앱의 `src/` 를 통째로 지운다. 60 개였을
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
    printf '  %s %-40s 기대rc=%s 실제rc=%s\n' "$mark" "$name" "$want" "$rc"
    [ "$mark" = "✗" ] && printf '%s\n' "$out" | sed 's/^/       /'
    return 0
  }

  # 대조군 헬퍼 — 픽스처를 만든 뒤 **변형하고 다시 커밋**한다(가드는 커밋된 트리를 읽는다).
  mutate_cell() {
    local name="$1" want="$2" needle="$3" label="$4"; shift 4
    local d out rc
    d="$(build_case "$name")"
    ( cd "$d" && eval "$*" ) >/dev/null 2>&1
    ( cd "$d" && git -c core.autocrlf=false add -A \
        && git -c user.email=t@t -c user.name=t commit -qm mutate ) >/dev/null 2>&1
    out="$(bash "$d/scripts/check-demo-resolver-copies.sh" 2>&1)"; rc=$?
    if [ "$rc" = "$want" ] && printf '%s' "$out" | grep -qF "$needle"; then
      printf '  ✓ %-40s 기대rc=%s 실제rc=%s\n' "$label" "$want" "$rc"; passes=$((passes+1))
    else
      printf '  ✗ %-40s 기대rc=%s 실제rc=%s\n' "$label" "$want" "$rc"; fails=$((fails+1))
      printf '%s\n' "$out" | sed 's/^/       /'
    fi
  }

  echo "[resolver-copies] --self-test"

  # --- 통과해야 하는 세계 ---------------------------------------------------
  cell "🔵 오늘의 상태 — 패키지에만 구현" 0 "구현은 패키지 1곳뿐"

  cell "🔵 소비자 배선은 구현이 아니다" 0 "앱 안 구현 0 건" \
    "projects/p1/apps/a1/src/demo-backend.ts:$CONSUMER" \
    "projects/p2/web/a2/src/demo-backend.ts:$CONSUMER"

  # 🔴🔴 **착수 중 실제로 당해서 넣는 칸** (2026-09-02). 첫 판의 지문 ①은 계약 «이름» 이었고
  #    (`CONTENT_RE='DEMO_API_BASE'`), 그러자 **소비자 위젯 둘이 RED** 로 나왔다 — 구현이
  #    아니라 *"판정이 `DEMO_API_BASE`(비공개 env)에 달려 있고…"* 라는 **주석의 산문**이었다.
  #    지문을 «읽는 행위»(`process.env.` 까지)로 좁혀 고쳤고, 이 칸이 그것을 얼린다.
  # 🔵 아래 본문은 그 위젯이 실제로 갖고 있던 두 줄을 그대로 옮긴 것이다 — 산문 언급 +
  #    옛 시그니처의 **정규식 원문 인용**. 둘 다 «구현» 이 아니다.
  cell "🔵 산문의 이름 언급은 안 문다" 0 "앱 안 구현 0 건" \
    "projects/p1/apps/a1/src/demo-backend.ts:// 판정이 DEMO_API_BASE(비공개 env)에 달려 있다.
// 옛 지문은 \`export (async )?function resolveDemoBackend\` 였다.
$CONSUMER"

  # --- 🔴 본 명제의 bite ----------------------------------------------------
  # 🔴 이 칸이 **이 교체의 존재 이유**다. 두 가드를 **같은 두 세계에 실제로 돌려** 재 봤다
  #    (2026-09-02, A/B):
  #
  #      세계 A = 승격 완료(구현은 패키지에만)   옛: **rc=0** · 새: rc=0
  #      세계 B = 앱 하나가 구현을 되찾아옴      옛: **rc=2** · 새: **rc=1**
  #
  #    🔴 세계 A 에서 옛 가드는 *"정규화 비교 **0 쌍** · 이 통과는 '갈라지지 않았다' 가 아니라
  #    '비교할 것이 없다'"* 를 스스로 출력하며 초록을 낸다 — **공허한 초록**이 정확히 이것이다.
  #    🔴🔴 그리고 세계 B 에서 옛 가드는 조용하지 않다. **rc=2 를 내되 사유가 틀렸다**:
  #    *"해석기의 내보내는 API 가 바뀌었거나 **판별 패턴이 죽었습니다**"*. 즉 읽는 사람에게
  #    «앱이 규칙을 어겼다» 가 아니라 **«가드가 고장 났다»** 로 읽힌다 — 그 오진의 자연스러운
  #    다음 행동은 **가드를 고치는 것**이고, 그것이 § D3 이 금지한 완화다.
  #    ⇒ 교체의 이유는 «옛 가드가 안 문다» 가 아니라 **«물어도 틀린 곳을 가리킨다»** 다.
  cell "🔴 앱이 구현을 되찾아옴 = RED" 1 "앱이 자기 해석기 구현을 갖고 있습니다" \
    "projects/p1/apps/a1/src/demo-backend.ts:$BACK_ALL"

  # 🔵 지문 셋이 **각각** 물어야 한다 — 하나만 물면 나머지 둘로 우회된다.
  cell "🔴 마커도 옛이름도 없이 계약 env 만" 1 "앱이 자기 해석기 구현을 갖고 있습니다" \
    "projects/p1/apps/a1/src/demo-backend.ts:$BACK_NO_MARKER"

  cell "🔴 계약 env 없이 옛 시그니처만" 1 "앱이 자기 해석기 구현을 갖고 있습니다" \
    "projects/p1/apps/a1/src/demo-backend.ts:$BACK_LEGACY_ONLY"

  cell "🔴 두 앱이 다 되찾아옴 = RED" 1 "앱이 자기 해석기 구현을 갖고 있습니다" \
    "projects/p1/apps/a1/src/demo-backend.ts:$BACK_ALL" \
    "projects/p2/web/a2/src/demo-backend.ts:$BACK_ALL"

  # --- 🔴 TASK-MONO-613 이 연 축 — 모집단은 `projects/` 와 선언에 갇히지 않는다 ------
  cell "🔴 projects 밖 앱도 문다" 1 "앱이 자기 해석기 구현을 갖고 있습니다" \
    "infra/demo/auth-gw/src/demo-backend.ts:$BACK_ALL"

  cell "🔴 선언 없는 디렉터리도 문다" 1 "앱이 자기 해석기 구현을 갖고 있습니다" \
    "services/notifier/src/demo-backend.ts:$BACK_ALL"

  # --- 🔴 대조군 — 「전부 공유했다」 와 「아무것도 못 봤다」 를 가르는가 ----------------
  mutate_cell "ctl-package-empty" 2 "구현이 **0 건**" "🔴 패키지가 비면 판정 불가" \
    'rm -f infra/demo/backend-resolver/src/index.ts'

  mutate_cell "ctl-detector-dead" 2 "대조군 실패" "🔴 탐지기 사망 → 판정 불가" \
    'rm -f infra/demo/aws/site/index.html'

  mutate_cell "ctl-app-invisible" 2 "모집단에 안 보입니다" "🔴 선언 앱이 안 보임 → 판정 불가" \
    'rm -rf projects/p2/web/a2/src'

  # 🔴 (3b) — 론처가 Next 앱이 되면 칸 (3)이 자기 자신을 재게 된다 → 판정 불가.
  mutate_cell "ctl-launcher-inside" 2 "대조군이 무효입니다" "🔴 론처가 모집단 안 → 판정 불가" \
    'mkdir -p infra/demo/aws/site/src && printf "export default {};\n" > infra/demo/aws/site/next.config.ts && printf "export const x = 1;\n" > infra/demo/aws/site/src/f1.ts'

  echo "[resolver-copies] self-test: $passes passed, $fails failed"
  [ "$fails" -eq 0 ]
}

if [ "$SELFTEST" = 1 ]; then selftest; exit $?; fi
run_guard
