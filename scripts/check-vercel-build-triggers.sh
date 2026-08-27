#!/usr/bin/env bash
# =============================================================================
# check-vercel-build-triggers.sh — 무관한 커밋이 Vercel 배포를 굽지 않는다 (TASK-MONO-562)
# =============================================================================
# 저장소에 Vercel 프로젝트가 **셋** 이라 커밋 하나가 배포 **셋** 을 굽는다. 문서 전용 PR 도
# 예외가 아니어서 무료 플랜의 일일 한도에 닿았고, 24시간 동안 모든 PR 이 빨개졌다.
# (둘이던 시절의 사고다. 셋이 된 것은 `TASK-MONO-582` — web-store. 아래 FLOOR 참조.)
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
# 프로젝트에 트리거 규칙을 붙일 자리다.
#   2026-08-21 = 2 (kanggle-portfolio + kanggle-fan)
#   2026-08-26 = 3 (+ web-store — `TASK-MONO-582`, `ADR-MONO-067` 단계 2)
# 🔴 올리는 것을 잊으면 **새 프로젝트를 지워도 이 가드는 안 문다** — 하한이 남은 개수보다
#    낮으면 삭제가 그냥 통과하기 때문이다. 파일을 놓는 커밋과 **같은 커밋에서** 올려라.
FLOOR="${VERCEL_GUARD_FLOOR:-3}"

# 🔴🔴 Vercel 스키마는 명령 문자열에 **maxLength=256** 을 건다
# (https://openapi.vercel.sh/vercel.json — 2026-08-21 실측: 최상위 property 40개,
# `additionalProperties: false`). `TASK-MONO-562` 가 fan 의 `ignoreCommand` 에
# pathspec 5개를 직접 넣어 **261자**가 됐고, 그래서 `vercel.json` 이 거부되어
# **모든 배포가 0초에 죽었다** — 빌드 로그조차 남지 않았고 상태 문구는
# `Deployment failed.` + project-configuration 링크였다(557 이 모르는 키로 받았던
# 바로 그 링크). **557 은 모르는 키로, 562 는 길이로 같은 방에 들어갔다.**
# 이 칸이 없는 동안 5자 초과가 조용히 통과했다.
MAXLEN="${VERCEL_GUARD_MAXLEN:-256}"

COMMENT_KEY_RE='^[[:space:]]*"//'

fail=0
note() { echo "  $*"; }
bad()  { echo "  x $*"; fail=1; }

# --- ignoreCommand 에서 :/... pathspec 을 뽑는다 -----------------------------
extract_pathspecs() {
  # 작은따옴표로 감싼 :/... 토큰만 뽑는다. 셸 인용을 흉내내는 것이 아니라, 이 저장소가
  # 쓰기로 한 **한 가지 모양**만 인정하는 것이다 — 모양이 바뀌면 추출이 0건이 되고
  # 칸 (4)가 발화한다(조용히 통과하지 않는다).
  local f="$1" out w
  out="$(grep -o "':/[^']*'" "$f" 2>/dev/null | tr -d "'")"
  [ -n "$out" ] && { printf '%s
' "$out"; return 0; }

  # 🔵 목록이 JSON 안에 없으면 **프로젝트 소유 래퍼**에 있다(`TASK-MONO-563`).
  #    256자 제한 때문에 목록을 문자열 밖으로 뺐고, 가드는 그 자리를 따라가야 한다
  #    — 안 따라가면 추출 0건이 되어 칸 (4)가 **정상 설정에 오발화**한다.
  for w in $(grep -o '[A-Za-z0-9_./-]*vercel-ignore\.sh' "$f" 2>/dev/null | sort -u); do
    w="${w#/}"
    [ -f "$ROOT/$w" ] || continue
    grep -o "':/[^']*'" "$ROOT/$w" 2>/dev/null | tr -d "'"
  done
}

# --- 최상위 문자열 값의 **디코드된** 길이 -------------------------------------
# 🔴 원문 바이트가 아니라 값의 길이여야 한다. `\"` 는 원문 2자 · 값 1자이므로
#    grep/wc 로 세면 틀린 수가 나온다 — 그리고 이 검사의 임계는 5자 차이로 갈렸다.
_JSLEN=""
json_string_lengths() {
  if [ -z "$_JSLEN" ]; then
    _JSLEN="$(mktemp)"
    cat > "$_JSLEN" <<'JS'
const fs = require('fs');
const cfg = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
for (const [k, v] of Object.entries(cfg)) {
  if (k !== '$schema' && typeof v === 'string') console.log(k + '	' + v.length);
}
JS
  fi
  node "$_JSLEN" "$1"
}

# --- (12) 워크스페이스 의존이 전부 트리거 목록에 덮이는가 --------------------
# 🔴 이 칸이 없는 동안 **목록에서 `packages/` 를 통째로 빼도 가드는 통과했다**(실측:
#    TASK-MONO-582 의 bite 2, rc=0). 다른 칸들은 *목록에 적힌 경로가 옳게 동작하는가*만
#    보고, **적혀야 하는데 안 적힌 것**은 아무도 안 봤다. 그 구멍의 증상은 "배포 실패" 가
#    아니라 **"배포가 조용히 건너뛰어짐"** 이고 CI 는 초록이다.
#
# 🔴 모집단은 **앱의 package.json 에서 파생**한다 — 여기 목록을 적지 않는다(HARDSTOP-03,
#    그리고 하드코딩한 모집단은 대상이 바뀌어도 자기가 적어둔 것을 계속 통과시킨다).
# 🔴 이름 해석은 **그 앱의 워크스페이스 안에서만** 한다. 프로젝트가 여럿이라 `@repo/ui`
#    같은 이름이 두 워크스페이스에 각각 있을 수 있고, 전역으로 찾으면 **엉뚱한 디렉터리**를
#    덮여 있다고 판정한다.
_WSCOV=""
workspace_dep_coverage() {
  # workspace_dep_coverage <config-rel-path> <pathspec...>   (stdin: 추적되는 package.json 목록)
  if [ -z "$_WSCOV" ]; then
    _WSCOV="$(mktemp)"
    cat > "$_WSCOV" <<'WSJS'
const fs = require('fs'), path = require('path');
const [, , root, cfgRel, ...specs] = process.argv;
// 🔴 백슬래시 리터럴을 쓰지 않는다 — 이 페이로드는 이스케이프를 세 겹 지나고,
//    한 겹이 벗겨지면 node 가 SyntaxError 로 죽는다(TASK-MONO-582 에서 실제로 죽었다).
const SEP = String.fromCharCode(92);
const norm = (s) => s.split(SEP).join('/');
const dir = path.dirname(cfgRel);
const pkgPath = path.join(root, dir, 'package.json');
if (!fs.existsSync(pkgPath)) { console.log('SKIP	package.json 이 없는 프로젝트입니다'); process.exit(0); }
let pkg;
try { pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf8')); }
catch (e) { console.log('UNRESOLVED	' + dir + '/package.json 파싱 실패'); process.exit(0); }
const deps = Object.entries({ ...(pkg.dependencies || {}), ...(pkg.devDependencies || {}) })
  .filter(([, v]) => typeof v === 'string' && v.startsWith('workspace:'))
  .map(([k]) => k);
if (!deps.length) { console.log('SKIP	workspace:* 의존이 없습니다'); process.exit(0); }
// 워크스페이스 루트를 위로 걸어 찾는다 — 이름 해석의 범위다.
let ws = dir;
for (;;) {
  if (fs.existsSync(path.join(root, ws, 'pnpm-workspace.yaml'))) break;
  const up = path.dirname(ws);
  if (up === ws || ws === '.') { ws = null; break; }
  ws = up;
}
if (!ws) { console.log('UNRESOLVED	' + dir + ' 위에서 pnpm-workspace.yaml 을 못 찾았습니다'); process.exit(0); }
const prefix = norm(ws) + '/';
const byName = new Map();
const NL = String.fromCharCode(10);
for (const rel of fs.readFileSync(0, 'utf8').split(NL).filter(Boolean)) {
  const r = norm(rel);
  if (!r.startsWith(prefix)) continue;             // 다른 워크스페이스는 보지 않는다
  try {
    const n = JSON.parse(fs.readFileSync(path.join(root, r), 'utf8')).name;
    if (n && !byName.has(n)) byName.set(n, norm(path.dirname(r)));
  } catch (e) { /* 파싱 못 하는 것은 이름을 제공하지 않는다 */ }
}
const covered = specs.map((s) => (s.indexOf(':/') === 0 ? s.slice(2) : s));
let ok = 0;
for (const d of deps) {
  const dd = byName.get(d);
  if (!dd) { console.log('UNRESOLVED	' + d + ' 의 디렉터리를 워크스페이스에서 못 찾았습니다'); continue; }
  if (covered.some((c) => dd === c || dd.startsWith(c + '/'))) ok++;
  else console.log('UNCOVERED	' + d + '	' + dd);
}
console.log('COUNT	' + deps.length + '	' + ok);
WSJS
  fi
  node "$_WSCOV" "$ROOT" "$@"
}

run_cell() {
  # run_cell <설명> <기대rc> <임시저장소> <judge> <pathspec...>
  #
  # 🔴 판정 **창**을 재는 칸((6)(7))은 `CELL_PREV_SHA` 를 세팅한 뒤 부른다. 비어 있으면
  #    환경변수를 **명시적으로 지우고** 부른다 — 호스트에 그 변수가 살아 있으면 창 칸이 아닌
  #    칸들까지 넓은 창으로 판정되어, 통과가 무엇을 증명한 것인지 알 수 없게 된다.
  local desc="$1" want="$2" repo="$3" judge="$4"; shift 4
  local got
  # `CELL_REF`/`CELL_PROD` 는 브랜치 축((9)(10)(11))을 재는 칸이 세운다. 세우지 않은 칸에서는
  # 셋 다 **명시적으로 지운다** — 호스트에 남아 있으면 그 칸이 무엇을 증명했는지 알 수 없다.
  local pre=(env -u VERCEL_GIT_PREVIOUS_SHA -u VERCEL_GIT_COMMIT_REF -u VERCEL_GIT_REPO_DEFAULT_BRANCH)
  [ -n "${CELL_PREV_SHA:-}" ] && pre+=("VERCEL_GIT_PREVIOUS_SHA=$CELL_PREV_SHA")
  [ -n "${CELL_REF:-}" ]      && pre+=("VERCEL_GIT_COMMIT_REF=$CELL_REF")
  [ -n "${CELL_PROD:-}" ]     && pre+=("VERCEL_GIT_REPO_DEFAULT_BRANCH=$CELL_PROD")
  ( cd "$repo" && "${pre[@]}" bash "$judge" "$@" >/dev/null 2>&1 ); got=$?
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
    bad "vercel.json 을 ${#configs[@]}개만 찾았습니다 (하한 $FLOOR — provenance 는 위 FLOOR 선언 참조)."
    bad "  줄었다면 트리거 규칙이 사라진 것이고, 늘었다면 하한을 올려라."
    return 1
  fi
  note "발견한 vercel.json ${#configs[@]}개 (하한 $FLOOR)"

  [ -f "$judge" ] || { bad "판정자가 없습니다: scripts/vercel-should-build.sh"; return 1; }

  # 🔴 칸 (5)는 JSON 을 실제로 파싱해야 한다. node 가 없으면 **조용히 건너뛰지 말고**
  #    크게 실패한다 — 검사기가 죽은 것과 위반이 없는 것은 다른 사건이다.
  command -v node >/dev/null 2>&1 || { bad "node 가 없습니다 — 칸 (5)(스키마 길이)를 수행할 수 없습니다."; return 1; }

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

    # --- (5) 스키마 길이 제한. 562 가 여기서 깨졌고 아무 가드도 이 축을 안 봤다. ---
    local kv k v
    while IFS=$'	' read -r k v; do
      [ -n "$k" ] || continue
      if [ "$v" -gt "$MAXLEN" ]; then
        bad "(5) $k 가 ${v}자입니다 — Vercel 스키마 한도 ${MAXLEN}자 초과 ⇒ vercel.json 이 거부되고 배포가 0초에 죽습니다 (TASK-MONO-563)."
      fi
    done < <(json_string_lengths "$abs")

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

    # --- (12) 워크스페이스 의존 커버리지 -----------------------------------
    # 🔴🔴 **fail-closed.** 초판은 이 자리를 파이프로만 읽었고, node 가 SyntaxError 로
    #    죽었을 때 `while read` 가 빈 스트림을 돌아 **아무 줄도 안 내고 rc=0** 이 됐다.
    #    죽은 검사기가 초록을 보고한 것이다 — 이 파일의 헤더가 경고하는 바로 그 부류다.
    #    ⇒ 종료코드를 읽고, **판정 줄(COUNT/SKIP)이 하나도 없으면 실패**시킨다.
    local wsout wsrc wskind wsa wsb wstot="" wsok=""
    wsout="$(mktemp)"
    git -C "$ROOT" ls-files '*package.json'       | workspace_dep_coverage "$cfg" "${specs[@]}" > "$wsout" 2>&1
    wsrc=$?
    if [ "$wsrc" -ne 0 ]; then
      bad "(12) 워크스페이스 의존 검사가 죽었습니다 (rc=$wsrc) — 위반이 없는 것과 다른 사건입니다:"
      while IFS= read -r wsa; do bad "     $wsa"; done < <(head -5 "$wsout")
    elif ! grep -qE '^(COUNT|SKIP)' "$wsout"; then
      bad "(12) 검사가 판정 줄(COUNT/SKIP)을 하나도 내지 않았습니다 — **판정 불가**입니다."
    else
    while IFS=$'	' read -r wskind wsa wsb; do
      case "$wskind" in
        SKIP)       note "(12) 워크스페이스 의존 검사 해당 없음 — $wsa" ;;
        UNRESOLVED) bad "(12) $wsa"
                    bad "     → 해석이 죽은 것과 위반이 없는 것은 다른 사건입니다. 조용히 통과시키지 않습니다." ;;
        UNCOVERED)  bad "(12) 워크스페이스 의존 '$wsa' ($wsb) 가 트리거 목록에 없습니다."
                    bad "     → 그 패키지만 바뀐 커밋은 **배포가 조용히 건너뛰어집니다** — CI 는 초록이고"
                    bad "       사이트는 마지막 성공 배포를 계속 서빙하므로 URL 을 찔러도 200 입니다." ;;
        COUNT)      wstot="$wsa"; wsok="$wsb" ;;
      esac
    done < "$wsout"
    [ -z "$wstot" ] || note "(12) workspace:* 의존 ${wstot}개 중 ${wsok}개가 트리거 목록에 덮임"
    fi
    rm -f "$wsout"

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

    # -------------------------------------------------------------------------
    # (6)(7) 판정 **창** — TASK-MONO-572
    # -------------------------------------------------------------------------
    # 위 칸들은 전부 **커밋 하나**를 본다. 창이 한 커밋으로 좁아도 전부 통과한다.
    # 실제로 그랬다: 판정이 `HEAD^..HEAD` 였고, 커밋 **여럿을 한 번에 push** 하면 앞 커밋의
    # 앱 변경이 창 밖으로 나가 배포가 **조용히 건너뛰어졌다**. 2026-08-23 관측 —
    # `[프로브 라우트, tasks/INDEX]` 를 함께 push 했더니 프리뷰에 프로브가 없었고, 상태는
    # 실패가 아니라 `Canceled by Ignored Build Step` = **성공**이었다.
    #
    # 🔴 그래서 이 두 칸의 단위는 커밋이 아니라 **배치**다.
    local base_sha
    base_sha="$(git -C "$tmp" rev-parse HEAD)"

    # (6) bite — 앱 변경이 **앞** 커밋에 있고 뒤에 무관한 커밋이 얹힌다 -> 빌드해야 한다.
    echo win > "$tmp/$first_file"
    git -C "$tmp" commit -qam "own (배치 앞)"
    echo win > "$tmp/tasks/ready/unrelated.md"
    git -C "$tmp" commit -qam "unrelated (배치 뒤)"
    CELL_PREV_SHA="$base_sha" \
      run_cell "(6) [자기경로, 무관] 배치 -> 빌드 (창이 배치를 덮는가)" 1 "$tmp" "$judge" "${specs[@]}"

    # 🔵 같은 배치를 창 없이 재면 **건너뛴다** — 이것이 고치기 전의 동작이고,
    #    (6)이 창을 재고 있다는 증거다(둘이 갈리지 않으면 (6)은 아무것도 증명하지 않는다).
    run_cell "(6b) 같은 배치, PREVIOUS_SHA 없음 -> 건너뜀 (옛 동작)" 0 "$tmp" "$judge" "${specs[@]}"

    # (7) 🔴 대조군 — 무관한 커밋만 있는 배치는 **여전히 건너뛰어야** 한다.
    #     이 칸이 없으면 *"항상 빌드"* 라는 자명한 오답이 (6)을 통과하고, 그것은 이 스크립트를
    #     태어나게 한 배포 rate limit 을 도로 불러온다(2026-08-23 하루에 두 번 물렸다).
    local base2
    base2="$(git -C "$tmp" rev-parse HEAD)"
    echo c1 > "$tmp/tasks/ready/unrelated.md"; git -C "$tmp" commit -qam "unrelated 1"
    echo c2 > "$tmp/tasks/ready/unrelated.md"; git -C "$tmp" commit -qam "unrelated 2"
    CELL_PREV_SHA="$base2" \
      run_cell "(7) [무관, 무관] 배치 -> 건너뜀 (대조군)" 0 "$tmp" "$judge" "${specs[@]}"

    # (8) 폴백 — 쓸 수 없는 PREVIOUS_SHA 는 창을 넓히지 말고 `HEAD^` 로 내려가야 한다.
    #     넓히기가 실패했을 때도 **기존 동작**이 남는 것이 안전한 방향이다.
    CELL_PREV_SHA="0000000000000000000000000000000000000000" \
      run_cell "(8) 존재하지 않는 PREVIOUS_SHA -> HEAD^ 로 폴백 (건너뜀)" 0 "$tmp" "$judge" "${specs[@]}"

    # -------------------------------------------------------------------------
    # (13) PREVIOUS_SHA 가 **HEAD 자신** — 창이 0 커밋이다 (TASK-MONO-594)
    # -------------------------------------------------------------------------
    # 🔴 "나쁜 PREVIOUS_SHA" 의 모집단을 **둘**로 봤던 것이 이 칸이 없던 이유다:
    #      (6b) 값이 **없다**   ·   (8) 값이 **가짜다**(객체가 실재하지 않는다)
    #    셋째가 있다 — **문법적으로 완벽한데 창이 비는 값.** 대시보드 수동 Redeploy 에서
    #    Vercel 이 "직전 배포" 로 **그 배포 자신의 커밋**을 준다. 비었나/객체가 있나/HEAD 의
    #    조상인가 를 **셋 다 통과**하므로(커밋은 자기 자신의 조상이다) (8)이 못 잡는다.
    #    ⇒ `BASE..HEAD` 가 빈 범위가 되고 `git diff --quiet` 는 늘 "차이 없음" ⇒ **건너뜀.**
    #    라이브 1건: `dpl_9AZiTx4H6a7mtR7wEChGvm1Uw9Bq` (`kanggle-fan`, 2026-08-27 10:08Z).
    #
    # 🔵 **자기 경로를 건드린 커밋**을 HEAD 에 둬야 한다. 무관한 커밋으로 세우면 고쳐진 판과
    #    고장 난 판이 **같은 rc** 를 내고, 이 칸은 아무것도 증명하지 못한다.
    #    (고장 난 판: 빈 창 -> 건너뜀 rc=0 · 고친 판: 판정 불가 -> 빌드 rc=1.)
    #
    # 🔴🔴 **`TASK-MONO-590` 이 랜딩해도 이 칸을 지우지 마라.** 590 은 Deploy Hook 축에서
    #    배포 *생성*을 줄이는 티켓이고, **수동 Redeploy 는 훅 경로를 지나가지 않는다.**
    echo self > "$tmp/$first_file"
    git -C "$tmp" commit -qam "own (Redeploy 대상 커밋)"
    CELL_PREV_SHA="$(git -C "$tmp" rev-parse HEAD)" \
      run_cell "(13) PREVIOUS_SHA == HEAD (수동 Redeploy) -> 빌드 (빈 창은 판정 불가)" 1 "$tmp" "$judge" "${specs[@]}"

    # 🔵 **픽스처 대조군.** 고친 판에서 (13)은 커밋 내용과 무관하게 rc=1 을 낸다(빈 창이면
    #    바로 빌드다). 그러니 (13) 혼자서는 *"HEAD 가 정말 자기 경로를 건드렸는가"* 를 증명하지
    #    못한다 — 픽스처가 조용히 무관한 커밋으로 바뀌어도 (13)은 초록으로 남는다.
    #    이 칸이 그 축을 잡는다: **같은 HEAD 를 정상 창(1 커밋)으로 재도 빌드**여야 한다.
    #    둘 사이에서 달라지는 것은 창의 크기뿐이므로, (13)이 재는 것이 창이라는 뜻이 된다.
    CELL_PREV_SHA="$(git -C "$tmp" rev-parse HEAD^)" \
      run_cell "(13b) 같은 HEAD, 창이 1 커밋 -> 빌드 (창 축만 다르다)" 1 "$tmp" "$judge" "${specs[@]}"

    # -------------------------------------------------------------------------
    # (9)(10) **첫 push** — 직전 배포가 없을 때 (TASK-MONO-572, 2026-08-25 라이브 실측)
    # -------------------------------------------------------------------------
    # 🔴 (6)은 `PREVIOUS_SHA` 가 **주어졌을 때**만 통과한다. 그런데 라이브에서 재 보니
    #    **새 브랜치의 첫 push 에는 직전 배포가 없어서** 창이 안 넓어졌다 — 그리고 이 저장소는
    #    태스크마다 브랜치를 새로 만들므로 **커밋 여럿을 한 번에 올리는 것이 바로 그 첫 push** 다.
    #    즉 (6)만 있으면 **결함이 가장 잘 나는 경우가 안 덮인다.**
    #    PR 브랜치의 기준점은 *직전 배포*가 아니라 **merge-base(main)** 여야 한다.
    local mb_repo="$tmp-mb"
    rm -rf "$mb_repo"; git init -q "$mb_repo"
    git -C "$mb_repo" config user.email t@t; git -C "$mb_repo" config user.name t
    git -C "$mb_repo" config commit.gpgsign false; git -C "$mb_repo" config core.autocrlf false
    mkdir -p "$mb_repo/$(dirname "$first_file")" "$mb_repo/tasks/ready"
    echo base > "$mb_repo/$first_file"; echo base > "$mb_repo/tasks/ready/unrelated.md"
    git -C "$mb_repo" add -A >/dev/null; git -C "$mb_repo" commit -qm base
    git -C "$mb_repo" branch -M main
    git -C "$mb_repo" checkout -q -b feature
    echo w1 > "$mb_repo/$first_file";            git -C "$mb_repo" commit -qam "app (배치 앞)"
    echo w2 > "$mb_repo/tasks/ready/unrelated.md"; git -C "$mb_repo" commit -qam "docs (배치 뒤)"

    # (9) bite — PREVIOUS_SHA 가 **없는** 첫 push 인데도 배치를 덮어야 한다.
    CELL_REF="feature" CELL_PROD="main" \
      run_cell "(9) 첫 push [자기경로, 무관] (PREVIOUS_SHA 없음) -> 빌드" 1 "$mb_repo" "$judge" "${specs[@]}"

    # (10) 🔴 대조군 — **production 브랜치에서는 merge-base 를 쓰면 안 된다.**
    #      거기서 merge-base 는 HEAD 자신이라 창이 비고 **전부 건너뛴다** — 반대 방향의 고장이다.
    #      main 은 squash 머지라 `HEAD^` 가 이미 옳다. 이 칸은 그 축이 지켜지는지 본다:
    #      main 위에서 자기 경로를 바꾼 커밋은 **여전히 빌드**해야 한다.
    git -C "$mb_repo" checkout -q main
    echo prod > "$mb_repo/$first_file"; git -C "$mb_repo" commit -qam "app on main"
    CELL_REF="main" CELL_PROD="main" \
      run_cell "(10) production 브랜치의 자기경로 커밋 -> 빌드 (대조군)" 1 "$mb_repo" "$judge" "${specs[@]}"

    # (11) 대조군 — production 브랜치에서 무관한 커밋은 건너뛴다(창이 비지 않았음을 확인).
    echo prod2 > "$mb_repo/tasks/ready/unrelated.md"; git -C "$mb_repo" commit -qam "docs on main"
    CELL_REF="main" CELL_PROD="main" \
      run_cell "(11) production 브랜치의 무관 커밋 -> 건너뜀 (대조군)" 0 "$mb_repo" "$judge" "${specs[@]}"

    rm -rf "$mb_repo"
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
    # 🔴 `*package.json` 과 `pnpm-workspace.yaml` 도 복제한다 — 칸 (12)가 그것으로
    #    모집단을 파생하기 때문이다. 안 복제하면 (12)는 사본에서 늘 SKIP 이 되고,
    #    **self-test 는 (12)를 한 번도 행사하지 않으면서 초록**이 된다.
    #    (실측 2026-08-26: 13 + 2 개 파일이라 복제 비용은 무시할 수준이다.)
    local d; d="$(mktemp -d)"
    ( cd "$src" && git ls-files '*vercel.json' '*vercel-ignore.sh' scripts/vercel-should-build.sh \
        '*package.json' '*pnpm-workspace.yaml' ) | while IFS= read -r f; do
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

  # (c) 모집단을 하한 **아래로** 떨어뜨린다.
  # 🔴 "1개만 지운다" 로 썼다가 이 칸이 **조용히 무의미해졌다**: 설정이 2개일 때는 1개만
  #    지워도 하한(2)을 깼지만, 세 번째 vercel.json 을 추가하자 3-1=2 로 여전히 하한을
  #    만족해 칸이 통과했다. 모집단을 바꾸면 그 모집단에 기대는 칸도 같이 썩는다.
  #    ⇒ 지울 개수를 **하한에서 계산**한다. 모집단이 몇 개든 이 칸은 항상 하한을 깬다.
  t="$(_mk)"
  local total del
  total="$(cd "$t" && git ls-files '*vercel.json' | wc -l)"
  del=$(( total - FLOOR + 1 ))
  (cd "$t" && git ls-files '*vercel.json' | head -n "$del" | xargs -r git rm -q --)
  git -C "$t" commit -qam mutate
  _expect "(c) 설정 ${del}개 삭제(총 ${total}, 하한 ${FLOOR}) -> 하한 위반으로 문다" 1 "$(_run "$t")"; rm -rf "$t"

  # (d) pathspec 모양을 바꾼다 -> 추출 0건. **조용히 통과하면 안 된다.**
  t="$(_mk)"
  out="$(cd "$t" && git ls-files '*vercel.json' | head -1)"
  sed -i "s/':\//'X\//g" "$t/$out"
  git -C "$t" commit -qam mutate
  _expect "(d) pathspec 모양 변경 -> 추출 0건으로 문다" 1 "$(_run "$t")"; rm -rf "$t"

  # (e) 명령 문자열을 한도 위로 늘린다 -> 스키마가 거부할 모양. **이것이 562 의 결함이다.**
  # 🔴 261자가 몇 주 동안 조용히 통과했다. 칸이 없으면 위반은 "실패" 가 아니라 **무음**이다.
  t="$(_mk)"
  out="$(cd "$t" && git ls-files '*vercel.json' | head -1)"
  pad="$(printf '#%.0s' $(seq 1 $((MAXLEN + 8))))"
  sed -i "s|\"ignoreCommand\": \"|\"ignoreCommand\": \"$pad |" "$t/$out"
  # 🔴🔴 **무는지 읽기 전에 주입됐는지 단언한다.** 주입이 0건이면 "안 물었다" 와
  #    "시험한 적이 없다" 가 구별되지 않고, 후자는 초록으로 보인다.
  if ! grep -q '##########' "$t/$out"; then
    echo "  x  (e) 주입 실패 — 이 칸은 아무것도 시험하지 않았습니다."; rc=1
  else
    git -C "$t" commit -qam mutate
    _expect "(e) 명령 문자열이 한도(${MAXLEN}자) 초과 -> 문다" 1 "$(_run "$t")"
  fi
  rm -rf "$t"

  # (f) **래퍼 쪽** pathspec 모양을 바꾼다 -> 가드가 래퍼를 따라가지 않으면 조용히 통과한다.
  # 🔵 (d)는 JSON 안에 목록이 있는 설정만 시험한다. 563 이 목록을 래퍼로 옮겼으므로
  #    그 자리도 같은 칸이 필요하다 — 아니면 fan 쪽 추출이 죽어도 아무도 모른다.
  t="$(_mk)"
  out="$(cd "$t" && git ls-files '*vercel-ignore.sh' | head -1)"
  if [ -n "$out" ]; then
    sed -i "s/':\//'X\//g" "$t/$out"
    git -C "$t" commit -qam mutate
    _expect "(f) 래퍼의 pathspec 모양 변경 -> 추출 0건으로 문다" 1 "$(_run "$t")"
  else
    echo "  x  (f) 래퍼(*vercel-ignore.sh)를 찾지 못했습니다 — 이 칸이 아무것도 시험하지 않습니다."; rc=1
  fi
  rm -rf "$t"

  # (g) 워크스페이스 의존을 덮던 pathspec 을 **하나 지운다** -> 칸 (12)가 물어야 한다.
  # 🔴 이 칸이 왜 있는가: TASK-MONO-582 에서 목록에서 `packages/` 를 통째로 빼고 가드를
  #    돌렸더니 **rc=0 이었다.** 다른 칸들은 *적힌 경로가 옳게 동작하는가* 만 보고,
  #    **적혀야 하는데 안 적힌 것**은 아무도 안 봤다. 그 구멍의 증상은 배포 실패가 아니라
  #    **무음**이다.
  t="$(_mk)"
  out="$(cd "$t" && git ls-files '*vercel-ignore.sh' | head -1)"
  if [ -n "$out" ]; then
    # 워크스페이스 의존을 덮는 pathspec = `packages` 로 끝나는 줄. 그것만 지운다.
    local before after
    before="$(grep -c "packages'" "$t/$out" || true)"
    sed -i "/packages'/d" "$t/$out"
    after="$(grep -c "packages'" "$t/$out" || true)"
    # 🔴🔴 **무는지 읽기 전에 주입됐는지 단언한다.** (e)가 세운 규율이고, 여기서도 같다:
    #    지운 줄이 0이면 "안 물었다" 와 "시험한 적이 없다" 가 구별되지 않는다.
    if [ "$before" -eq 0 ] || [ "$after" -ne 0 ]; then
      echo "  x  (g) 주입 실패 (before=$before after=$after) — 이 칸은 아무것도 시험하지 않았습니다."; rc=1
    else
      git -C "$t" commit -qam mutate
      _expect "(g) 워크스페이스 의존을 덮던 pathspec 삭제 -> 칸 (12)가 문다" 1 "$(_run "$t")"
    fi
  else
    echo "  x  (g) 래퍼(*vercel-ignore.sh)를 찾지 못했습니다 — 이 칸이 아무것도 시험하지 않습니다."; rc=1
  fi
  rm -rf "$t"

  # (h) 🔴🔴 **수정을 되돌린 사본으로 칸 (13)이 무는지 본다** (TASK-MONO-594)
  # 🔵 위 (a)~(g)는 전부 *설정*(vercel.json / 래퍼)을 망가뜨린다. 이 칸만 **판정자 자신**을
  #    되돌린다 — 칸 (13)이 지키는 결함은 설정이 아니라 판정자 안에 살았기 때문이다.
  # 🔴 이 칸이 없으면 (13)은 "넣었다" 로 끝나고 **죽은 채 초록**일 수 있다. 이 저장소는 그
  #    모양을 칸 (12)에서 이미 한 번 겪었다.
  t="$(_mk)"
  out="scripts/vercel-should-build.sh"
  if [ -f "$t/$out" ]; then
    local g_before g_after
    g_before="$(grep -c 'MONO-594-EMPTY-WINDOW-GUARD' "$t/$out" || true)"
    sed -i '/>>> MONO-594-EMPTY-WINDOW-GUARD/,/<<< MONO-594-EMPTY-WINDOW-GUARD/d' "$t/$out"
    g_after="$(grep -c 'MONO-594-EMPTY-WINDOW-GUARD' "$t/$out" || true)"
    # 🔴🔴 **무는지 읽기 전에 주입이 착지했는지 단언한다**((e)(g)가 세운 규율).
    #    표식은 정확히 2개(`>>>` 와 `<<<`)여야 하고 삭제 뒤엔 0개여야 한다. 그렇지 않으면
    #    "안 물었다" 와 "시험한 적이 없다" 가 구별되지 않고, 후자는 초록으로 보인다.
    if [ "$g_before" -ne 2 ] || [ "$g_after" -ne 0 ]; then
      echo "  x  (h) 주입 실패 (before=$g_before after=$g_after) — 이 칸은 아무것도 시험하지 않았습니다."; rc=1
    elif ! bash -n "$t/$out" 2>/dev/null; then
      # 🔵 되돌린 사본이 문법 오류면 가드가 아니라 **sed 를** 시험하게 된다. 그때도 rc=1 이
      #    나오므로 통과처럼 보인다 — 그래서 여기서 갈라놓는다.
      echo "  x  (h) 되돌린 사본이 문법 오류입니다 — 가드가 아니라 sed 를 시험하게 됩니다."; rc=1
    else
      git -C "$t" commit -qam mutate
      _expect "(h) 빈 창 가드(MONO-594)를 되돌림 -> 칸 (13)이 문다" 1 "$(_run "$t")"
    fi
  else
    echo "  x  (h) 판정자($out)를 사본에서 찾지 못했습니다 — 이 칸이 아무것도 시험하지 않습니다."; rc=1
  fi
  rm -rf "$t"

  [ "$rc" -eq 0 ] && echo "[vercel-triggers] --self-test ok" || echo "[vercel-triggers] --self-test 실패"
  return "$rc"
}

if [ "${1:-}" = "--self-test" ]; then
  self_test; exit $?
fi
main; exit $?
