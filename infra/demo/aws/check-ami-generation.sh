#!/usr/bin/env bash
# =============================================================================
# check-ami-generation.sh — 구워진 데모 호스트가 **서빙 중인 론처와 같은 세대인가**
#                            (TASK-MONO-628)
# =============================================================================
# 두 파일이 **하나의 계약**을 공유하는데 **서로 다른 속도로 배포된다**:
#
#   infra/demo/aws/site/index.html   → 머지하면 Vercel 이 몇 분 안에 배포한다
#   infra/demo/demo-up.sh 외 3종     → AMI 에 구워진다 (packer build ~55분 · 소유자 승인)
#
# 저장소 안의 두 파일은 **언제나 서로 맞는다** — 같은 커밋에서 읽으니까. 그래서 정적
# 가드((z14)(z15))는 이 어긋남을 **원리적으로** 못 본다. 어긋남은 **배포 경계에서만**
# 생기고, 이 파일이 그 경계를 재는 유일한 술어다.
#
#   bash infra/demo/aws/check-ami-generation.sh [옵션]
#
#     --ref <git-ref>        서빙 세대의 기준 (기본 origin/main)
#     --pin <file>           구운 세대의 기록 (기본 infra/demo/aws/deployed-ami.env)
#     --baked-commit <sha>   핀 파일을 무시하고 구운 세대를 직접 지정 (자가검사·주입용)
#     --with-aws             AWS 에 물어 핀이 **거짓말하지 않는지** 교차 확인
#     --self-test            판정자가 실제로 무는지 (주입 → 물기)
#
#   종료코드  0 = 같은 세대   1 = 어긋남   2 = 판정 불가
#
# 🔴 **2 를 0 으로 접지 마라.** 이 저장소가 반복해서 당한 실패가 «확인 못 했다» 를
#    «괜찮다» 로 번역하는 것이다. 형제 check-launcher-fresh.sh 헤더가 같은 문장을 적었다.
#
# -----------------------------------------------------------------------------
# 무엇을 재고, 무엇을 **안 재는가**
# -----------------------------------------------------------------------------
# 재는 것 = **계약 파일 집합**의 내용이 두 세대에서 같은가.
#
#   infra/demo/aws/site/index.html      선언(누가 어디서 서빙되는가 · 부팅 프로브 대상)
#                                       🔴 **이 파일만 «선언 표면» 으로 좁혀 본다** — 아래.
#   infra/demo/demo-up.sh               그 선언의 소비자(추출 술어 · 두 하한)
#   infra/demo/projects.sh              어떤 오버레이가 어느 프로젝트에 붙는가
#   infra/demo/*-vercel.override.yml    억제 — 데모 호스트가 그 화면을 **그만 서빙**한다
#
# 🔴 **오버레이 목록을 여기 적지 않는다.** 두 커밋의 트리에서 각각 뽑아 **합집합**을 본다.
#    상수로 박으면 넷째 억제가 생기는 날 이 가드는 **조용히** 그것을 안 본다. 합집합이라
#    «한쪽에만 있는 파일»(신설·삭제)도 어긋남으로 이름이 찍힌다 — 그게 지금 console 이다.
#
# 안 재는 것 = 앱 소스·이미지·그 밖의 모든 것. 그것들도 AMI 에 구워지지만, 어긋나도
#   **론처가 방문자에게 한 약속을 깨지 않는다.** 이 판정자를 «AMI 가 낡았나» 로 넓히면
#   재굽기 직후를 빼면 영원히 빨강이고, 영원한 빨강은 **꺼진 가드**다.
#   🔵 그 축이 궁금하면 아래 «동반 파일» 줄이 건수만 정보로 알려준다(판정에 안 넣는다).
#
# -----------------------------------------------------------------------------
# 🔴🔴 index.html 은 **선언 표면**으로만 비교한다 (TASK-MONO-649)
# -----------------------------------------------------------------------------
# 위 문단이 경계한 «영원한 빨강» 이 **계약 집합 안에서** 일어났다. `index.html` 은 이
# 영역에서 가장 자주 바뀌는 파일이고(머지 = 배포), 구운 호스트가 실제로 **소비하는 것**은
# 파일 전체가 아니라 `demo-up.sh` 가 뽑아 가는 **선언 몇 개**다. 그래서 문구·주석·상태기계
# 변경에도 판정이 빨개졌고, 그때 내는 처방은 **55분·과금·소유자 승인**인 재굽기였다.
# 🔴 **그 처방은 그런 변경을 닫지 못한다** — 재굽기해도 다음 론처 변경에 똑같이 빨개진다.
# 무시되는 처방을 단 가드는 꺼진 가드다.
#
# ⇒ 이 파일에 한해 판정 단위를 **추출된 선언 집합**으로 좁혔다. 나머지 계약 파일은 그대로
#   내용 전체다 — 그것들은 **구운 호스트가 실행하는 파일**이라 내용이 곧 계약이다.
#
# 🔴🔴 **추출기를 두 벌로 만들지 않았다.** `demo-up.sh` 안의 추출 블록을 **앵커로 잘라내
#   그대로 실행한다**(이 저장소가 (z34)/(z39) 에서 쓰는 방식과 같다). 두 벌이면 속성 이름이
#   바뀌는 날 한쪽만 고쳐지고, 그때 이 판정은 **초록으로 실패**한다.
#   🔵 `demo-up.sh` 에 마커 주석을 새로 넣지 **않은** 이유: 그 파일은 계약 집합에서 내용
#   전체로 비교되므로, 마커를 넣는 순간 이 가드가 **재굽기 없이는 못 닫는 빨강**이 된다 —
#   즉 이 티켓이 없애려는 바로 그 상태를 새로 만든다. 앵커가 못 맞으면 **판정 불가(2)** 로
#   멈추므로, 블록이 움직여도 조용히 틀리지는 않는다.
#
# 🔵 **어느 세대의 추출기를 쓰나 — 서빙(ref) 세대의 것 하나로 양쪽을 뽑는다.** 두 세대의
#   추출기로 각각 뽑아 비교하면 그 비교는 «선언» 이 아니라 **«추출기 두 벌»** 을 재게 된다.
#   🔴 그러면 추출기가 그 사이 바뀐 경우를 놓치느냐 — 아니다. `demo-up.sh` 자신이 계약
#   집합에 **내용 전체로** 들어 있어 그 축은 **다른 줄이 이미 문다.**
#
# 🔵 **이 좁힘이 못 보게 되는 것**: `index.html` 의 선언 밖 변경 중 구운 호스트가 정말로
#   신경 쓰는 것. 근거는 `demo-up.sh` 가 그 파일을 **`SURFACE_SRC` 로만** 읽는다는 사실이고
#   (다른 용도의 참조가 0건), 그 전제가 깨지면 — 즉 그 파일을 다른 목적으로도 읽게 되면 —
#   **이 좁힘도 함께 넓혀야 한다.** 다음 사람이 다시 확인할 수 있도록 근거를 여기 적는다.
#
# -----------------------------------------------------------------------------
# 🔴 핀 파일이 «구운 세대» 를 **읽어서** 아는 것이 아니다 (2026-09-06 실측)
# -----------------------------------------------------------------------------
# AMI 안을 직접 읽는 경로는 이 계정에 **없다**:
#   · ebs:ListSnapshotBlocks → AccessDenied (portfolio-demo-deployer 정책에 없음)
#   · SSM describe-instance-information → **0건** (인스턴스가 stopped 이면 관리 대상이 아니다)
#   · 남은 길은 «기동해서 SSM 으로 들어가기» 뿐이고 그건 소유자 승인 + 과금이다.
#
# 그래서 핀 파일의 REPO_COMMIT 은 **기록**이고, 그 기록의 출처를 PROVENANCE 가 말한다:
#   ami-tag         AMI 자신이 굽는 중에 `git rev-parse HEAD` 로 확증하고 EC2 태그로 발행한 값
#                   (packer 2단계의 test + source 의 RepoCommit 태그 — 9차 굽기부터)
#   operator-record 사람이 손으로 적은 값. **이미지를 읽은 것이 아니다.**
# 🔴 판정자는 둘 다 받아들이지만 **출처를 매번 찍는다.** operator-record 는 «그렇다고
#    한다» 이지 «그렇다» 가 아니고, 그 구별이 지워지면 이 파일도 산문이 된다.
# =============================================================================
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../../.." && pwd)"

REF="origin/main"
PIN="$HERE/deployed-ami.env"
BAKED_OVERRIDE=""
WITH_AWS=0
SELFTEST=0

while [ $# -gt 0 ]; do
  case "$1" in
    --ref)           REF="${2:-}"; shift 2 ;;
    --pin)           PIN="${2:-}"; shift 2 ;;
    --baked-commit)  BAKED_OVERRIDE="${2:-}"; shift 2 ;;
    --with-aws)      WITH_AWS=1; shift ;;
    --self-test)     SELFTEST=1; shift ;;
    *) echo "알 수 없는 인자: $1" >&2; exit 2 ;;
  esac
done

say() { echo "[ami-gen] $*"; }
undecidable() { say "✖ $*"; exit 2; }

# -----------------------------------------------------------------------------
# 계약 파일 집합
# -----------------------------------------------------------------------------
CONTRACT_CORE=(
  "infra/demo/aws/site/index.html"
  "infra/demo/demo-up.sh"
  "infra/demo/projects.sh"
)
OVERLAY_DIR="infra/demo/"
OVERLAY_RE='\-vercel\.override\.yml$'

have_commit() { git -C "$ROOT" cat-file -e "${1}^{commit}" 2>/dev/null; }

# 🔵 얕은 체크아웃(CI 의 기본 fetch-depth=1)에서도 돌게 한다. 없으면 그 커밋만 한 겹
#    가져온다 — 트리만 있으면 blob 비교에 충분하다.
ensure_commit() {
  local c="$1"
  have_commit "$c" && return 0
  git -C "$ROOT" fetch --depth 1 origin "$c" >/dev/null 2>&1 || true
  have_commit "$c"
}

# 🔴🔴 `--verify --quiet` 는 장식이 아니다. 맨 `git rev-parse` 는 **해석에 실패해도 인자를
#    그대로 stdout 에 되뱉는다**(rc=128 인 채로). 경로 형태든 리비전 형태든 똑같다:
#      $ git rev-parse afebc9371:infra/demo/console-vercel.override.yml 2>/dev/null
#      afebc9371:infra/demo/console-vercel.override.yml        ← 빈 문자열이 아니다
#      $ git rev-parse origin/main^{commit} 2>/dev/null   # 그 ref 가 없는 얕은 체크아웃에서
#      origin/main^{commit}                                    ← 빈 문자열이 아니다
#
#    🔴🔴 **이 파일은 같은 결함을 두 번 냈다. 두 번째는 첫 번째를 «고친 뒤에» 났다.**
#      1차 — 경로 형태. «한쪽에만 있는 파일» 분기가 **한 번도 못 물었고** 신설된 console
#            억제를 «내용 다름» 으로 오분류했다(rc 는 둘 다 1 이라 rc 로는 구별 불가).
#      2차 — 리비전 형태. blob_at **하나만** 고쳤더니 형제 호출들이 낙오했고, REF_SHA 가
#            리터럴 `origin/main^{commit}` 을 받아 «비었나» 검사를 통과했다 ⇒ HEAD 로
#            떨어지는 폴백이 **영원히 안 도는** 채로 **CI 에서만** 죽었다.
#    ⇒ **이 파일의 모든 rev-parse 호출이 --verify --quiet 를 쓴다.** 한 자리만 고치면
#      형제가 낙오한다 — 고치기 전에 형제를 grep 하라는 규칙이 정확히 이 모양이다.
blob_at() { git -C "$ROOT" rev-parse --verify --quiet "${1}:${2}" 2>/dev/null; }

overlays_at() {
  git -C "$ROOT" ls-tree --name-only "$1" "$OVERLAY_DIR" 2>/dev/null | grep -E "$OVERLAY_RE"
}

# -----------------------------------------------------------------------------
# 선언 표면 추출 (TASK-MONO-649) — **demo-up.sh 의 블록을 잘라 그대로 실행한다**
# -----------------------------------------------------------------------------
# 🔴 앵커가 안 맞으면 «같다» 가 아니라 **판정 불가(2)** 다. 블록이 이름을 바꾸거나 옮겨진
#    상태에서 조용히 초록이 되면, 이 좁힘은 «아무것도 안 재는 것» 과 구별되지 않는다.
SURFACE_BEGIN_RE='^surfaces=\(\); surf_rows=0; surf_badsrc=\(\)$'
SURFACE_END_LIT="data-demo-boot-probe' \"\$SURFACE_SRC\""

EXTRACT_WHY=""
# extract_decls <index.html> <demo-up.sh> → 정규화된 선언들을 stdout · rc 0 / 2
extract_decls() {
  local html="$1" up="$2" b e blk out
  EXTRACT_WHY=""
  [ -s "$html" ] || { EXTRACT_WHY="index.html 이 비었습니다"; return 2; }
  [ -s "$up" ]   || { EXTRACT_WHY="demo-up.sh 가 비었습니다"; return 2; }
  b="$(grep -nE "$SURFACE_BEGIN_RE" "$up" | head -1 | cut -d: -f1)"
  e="$(grep -nF "$SURFACE_END_LIT" "$up" | head -1 | cut -d: -f1)"
  if [ -z "$b" ] || [ -z "$e" ] || [ "$e" -le "$b" ]; then
    EXTRACT_WHY="demo-up.sh 에서 추출 블록 앵커를 못 찾았습니다 (시작=${b:-없음} 끝=${e:-없음})"
    return 2
  fi
  blk="$(sed -n "${b},${e}p" "$up")"
  # 🔵 블록이 밖에서 읽는 변수는 SURFACE_SRC **하나뿐**이다(나머지는 블록 안에서 산다).
  out="$(
    SURFACE_SRC="$html"
    surfaces=(); surf_rows=0; surf_badsrc=()
    eval "$blk" || exit 9
    printf 'rows=%s\n' "$surf_rows"
    for x in ${surf_badsrc[@]+"${surf_badsrc[@]}"}; do printf 'bad=%s\n' "$x"; done
    for x in ${surfaces[@]+"${surfaces[@]}"};   do printf 'surface=%s\n' "$x"; done
  )" || { EXTRACT_WHY="추출 블록 실행이 실패했습니다"; return 2; }

  local rows nbad nsurf
  rows="$(printf '%s\n'  "$out" | sed -n 's/^rows=//p' | head -1)"
  nbad="$(printf '%s\n'  "$out" | grep -c '^bad=' || true)"
  nsurf="$(printf '%s\n' "$out" | grep -c '^surface=' || true)"
  # 🔴 «선언은 있는데 모르는 값» 은 demo-up.sh 안에서도 판정 불가다. 여기서도 같다 —
  #    속성 이름이 그 사이 바뀐 옛 세대를 «같다» 로 접으면 진짜 어긋남이 숨는다.
  if [ "${nbad:-0}" -gt 0 ]; then
    EXTRACT_WHY="모르는 선언 $(printf '%s\n' "$out" | sed -n 's/^bad=//p' | tr '\n' ' ')"
    return 2
  fi
  # 🔴🔴 **빈 집합끼리는 서로 동의한다.** 하한이 없으면 추출이 통째로 죽어도 «같은 세대» 가
  #    나온다 — 좁힌 술어가 공허하게 초록이 되는 정확히 그 자리다.
  if [ "${rows:-0}" -lt 1 ] || [ "${nsurf:-0}" -lt 1 ]; then
    EXTRACT_WHY="추출이 공허합니다 (행 ${rows:-0} · 표면 ${nsurf:-0}) — 0건은 «같다» 가 아닙니다"
    return 2
  fi
  # 정규화: 표면 선언은 정렬한다(마크업 순서는 계약이 아니다). 행 수는 함께 싣는다.
  { printf '%s\n' "$out" | sed -n 's/^rows=/rows=/p'
    printf '%s\n' "$out" | sed -n 's/^surface=/surface=/p' | LC_ALL=C sort; }
  return 0
}

# surface_decls_at <선언을 읽을 sha> <추출기를 읽을 sha>
surface_decls_at() {
  local sha="$1" upsha="$2" d rc
  d="$(mktemp -d)" || { EXTRACT_WHY="임시 디렉터리 실패"; return 2; }
  git -C "$ROOT" show "$sha:infra/demo/aws/site/index.html" > "$d/index.html" 2>/dev/null || {
    rm -rf "$d"; EXTRACT_WHY="${sha:0:9} 에서 index.html 을 못 읽습니다"; return 2; }
  git -C "$ROOT" show "$upsha:infra/demo/demo-up.sh" > "$d/demo-up.sh" 2>/dev/null || {
    rm -rf "$d"; EXTRACT_WHY="${upsha:0:9} 에서 demo-up.sh 를 못 읽습니다"; return 2; }
  extract_decls "$d/index.html" "$d/demo-up.sh"; rc=$?
  rm -rf "$d"
  return $rc
}

# -----------------------------------------------------------------------------
# 핀 파일 읽기 — 🔴 source 하지 않는다(임의 코드 실행). 키만 뽑는다.
# -----------------------------------------------------------------------------
pin_get() { # <file> <key>
  sed -n "s/^[[:space:]]*$2[[:space:]]*=[[:space:]]*//p" "$1" 2>/dev/null \
    | head -1 | tr -d '"'"'"' \r'
}

# =============================================================================
# 판정
# =============================================================================
# 인자: <baked-commit> <ref> <label>
# 반환: 0 같은 세대 · 1 어긋남 · 2 판정 불가
# 🔵 마지막 판정이 **무엇을 이름으로 찍었는지** 를 자가검사가 읽는다. 「rc 가 1 이다」는
#    어느 분기가 물었는지 말해 주지 않고, 이 파일은 이미 그 구별을 한 번 놓쳤다
#    (blob_at 의 --verify 주석 참조 — 신설 오버레이가 «내용 다름» 으로 잘못 분류돼도
#    rc 는 똑같이 1 이었다).
LAST_DRIFT=()

verdict_for() {
  local baked="$1" ref="$2" label="${3:-}"
  local baked_sha ref_sha p bb rb drift=() n_checked=0
  LAST_DRIFT=()

  ensure_commit "$baked" || { say "✖ 구운 세대 커밋 ${baked:0:12} 를 못 찾습니다(로컬에도 origin 에도) ⇒ 판정 불가"; return 2; }
  baked_sha="$(git -C "$ROOT" rev-parse --verify --quiet "${baked}^{commit}" 2>/dev/null)"
  ref_sha="$(git -C "$ROOT" rev-parse --verify --quiet "${ref}^{commit}" 2>/dev/null)"
  [ -n "$ref_sha" ] || { say "✖ 기준 ref '$ref' 를 못 읽습니다 ⇒ 판정 불가"; return 2; }

  say "── 구운 세대 ${baked_sha:0:9}  vs  서빙 세대 ${ref_sha:0:9} ${label}"

  # ---- 코어 3종: 두 커밋 **모두**에 있어야 한다 -----------------------------
  # 🔴 한쪽에 없으면 «어긋남» 이 아니라 **이 가드의 경로 지도가 낡은 것**이다. 파일이
  #    이름을 바꿨는데 판정자가 그것을 어긋남으로 보고하면, 다음 사람은 재굽기로 못 닫는
  #    빨강을 보게 되고 결국 가드를 끈다. 그 경우는 판정 불가로 **멈춘다.**
  for p in "${CONTRACT_CORE[@]}"; do
    bb="$(blob_at "$baked_sha" "$p")"
    rb="$(blob_at "$ref_sha" "$p")"
    if [ -z "$bb" ] || [ -z "$rb" ]; then
      say "✖ 계약 코어 경로 '$p' 가 ${baked_sha:0:9}=${bb:-없음} / ${ref_sha:0:9}=${rb:-없음} 입니다."
      say "  → 파일이 옮겨졌거나 이름이 바뀌었습니다. **이 스크립트의 CONTRACT_CORE 를 고치세요.**"
      say "    (어긋남으로 보고하지 않습니다 — 재굽기로 닫을 수 없는 빨강이기 때문입니다.)"
      return 2
    fi
    n_checked=$((n_checked + 1))
    # 🔴 index.html 만 «선언 표면» 으로 좁힌다 (TASK-MONO-649 — 머리말 참조).
    if [ "$p" = "infra/demo/aws/site/index.html" ]; then
      if [ "$bb" = "$rb" ]; then continue; fi   # blob 이 같으면 선언도 같다
      local bdec rdec
      bdec="$(surface_decls_at "$baked_sha" "$ref_sha")" || {
        say "✖ 구운 세대 ${baked_sha:0:9} 의 선언을 못 뽑습니다: $EXTRACT_WHY ⇒ 판정 불가"
        return 2; }
      rdec="$(surface_decls_at "$ref_sha" "$ref_sha")" || {
        say "✖ 서빙 세대 ${ref_sha:0:9} 의 선언을 못 뽑습니다: $EXTRACT_WHY ⇒ 판정 불가"
        return 2; }
      if [ "$bdec" != "$rdec" ]; then
        # 🔴 파일 이름만 찍지 않는다 — **어느 선언이** 달라졌는지 말해야 처방을 검증할 수 있다.
        local dl
        dl="$(diff <(printf '%s\n' "$bdec") <(printf '%s\n' "$rdec") | sed -n 's/^[<>] /&/p' | tr '\n' ' ')"
        drift+=("$p (**선언이 다름**: ${dl:-차이를 못 렌더했습니다})")
      else
        say "   ◑ $p 는 내용이 다르지만 **선언 표면은 같다** — 구운 호스트가 소비하는 것은 안 바뀌었습니다."
        say "     (문구·주석·스크립트 변경. 재굽기로 닫을 수 없고 닫을 필요도 없습니다 — TASK-MONO-649)"
      fi
      continue
    fi
    if [ "$bb" != "$rb" ]; then drift+=("$p (내용 다름)"); fi
  done

  # ---- 억제 오버레이: 두 트리의 **합집합** ---------------------------------
  local ov_all ov
  ov_all="$( { overlays_at "$baked_sha"; overlays_at "$ref_sha"; } | sort -u )"
  if [ -n "$ov_all" ]; then
    while IFS= read -r ov; do
      [ -n "$ov" ] || continue
      n_checked=$((n_checked + 1))
      bb="$(blob_at "$baked_sha" "$ov")"
      rb="$(blob_at "$ref_sha" "$ov")"
      if   [ -z "$bb" ] && [ -n "$rb" ]; then drift+=("$ov (**구운 세대에 없다** — 그 화면이 데모 호스트에서 아직 안 죽었다)")
      elif [ -n "$bb" ] && [ -z "$rb" ]; then drift+=("$ov (구운 세대에만 있다 — 저장소에서 회수됐다)")
      elif [ "$bb" != "$rb" ]; then          drift+=("$ov (내용 다름)")
      fi
    done <<< "$ov_all"
  fi

  # 🔴 «아무것도 안 봤는데 초록» 을 막는다. 코어 3종이 위에서 강제되므로 여기 도달하면
  #    최소 3이지만, 그 전제가 바뀌는 날 이 줄이 먼저 발화한다.
  if [ "$n_checked" -lt "${#CONTRACT_CORE[@]}" ]; then
    say "✖ 비교한 계약 파일이 ${n_checked}개뿐입니다 ⇒ 판정 불가"
    return 2
  fi
  say "   계약 파일 ${n_checked}개 비교 (코어 ${#CONTRACT_CORE[@]} + 억제 오버레이 합집합)"

  # ---- 동반 파일: 정보일 뿐, 판정이 아니다 ---------------------------------
  local companion
  companion="$(git -C "$ROOT" diff --name-only "$baked_sha" "$ref_sha" -- infra/demo/ 2>/dev/null | wc -l | tr -d ' ')"
  say "   (참고) infra/demo/ 전체로는 ${companion}개 파일이 다릅니다 — 판정에는 안 넣습니다."

  if [ "${#drift[@]}" -eq 0 ]; then
    say "   ✔ 같은 세대 — 계약 파일이 전부 일치합니다."
    return 0
  fi

  say "   ✖ **어긋남 ${#drift[@]}건** — 구운 데모 호스트는 서빙 중인 론처가 약속하는 것을 서빙하지 않습니다:"
  for p in "${drift[@]}"; do say "       · $p"; done
  LAST_DRIFT=("${drift[@]}")
  return 1
}

# =============================================================================
# --self-test — 판정자가 **주입에 무는가**
# =============================================================================
# 🔴 형제 check-launcher-fresh.sh 가 적은 규율 그대로다: 대조군이 «결함의 존재» 에
#    의존하면 결함이 고쳐지는 날 대조군이 죽는다. 그래서 **역사에서 주입한다** —
#    demo-up.sh 를 실제로 바꾼 직전 커밋. 재굽기로 지금 어긋남이 사라져도 이 칸은 산다.
# 🔴 그리고 **읽기 전에 주입을 단언한다**: 그 두 커밋에서 demo-up.sh 의 blob 이 실제로
#    다른가. 안 다르면 «안 물었다» 가 아니라 **아무것도 주입 안 된 것**이고, 그 둘을
#    구별 못 하면 self-test 는 자기가 무는지 증명한 적이 없는 게 된다.
if [ "$SELFTEST" -eq 1 ]; then
  say "▶ 자가검사 — 주입한 세대에 무는가 (기준 ref=$REF)"

  ensure_commit "$REF" >/dev/null 2>&1 || true
  REF_SHA="$(git -C "$ROOT" rev-parse --verify --quiet "${REF}^{commit}" 2>/dev/null)"
  # 🔴🔴 **얕은 PR 체크아웃에는 `origin/main` 이 없다** — `actions/checkout@v4` 는 기본으로
  #    PR ref 하나만 가져오므로 원격 추적 브랜치가 안 생긴다. 이 자리를 실측으로 잡았다:
  #    로컬에서 초록이던 자가검사가 CI 에서 «기준 ref 를 못 읽습니다» rc=2 로 죽었고,
  #    `git clone --depth 1 --branch <이 브랜치>` 로 그 모양을 재현해 확인했다.
  # 🔵 자가검사에서 기준이 무엇인지는 **재는 축이 아니다**(재는 것은 «판정자가 칸을
  #    가르는가»). 그래서 여기서만 HEAD 로 떨어진다. 🔴 실판정은 안 떨어진다 — 거기서
  #    기준이 틀리면 답이 틀리므로 rc=2 로 멈춘다.
  if [ -z "$REF_SHA" ]; then
    REF_SHA="$(git -C "$ROOT" rev-parse --verify --quiet "HEAD^{commit}" 2>/dev/null)"
    [ -n "$REF_SHA" ] || undecidable "기준 ref '$REF' 도 HEAD 도 못 읽습니다."
    say "   '$REF' 를 못 읽어 **HEAD**(${REF_SHA:0:9})를 기준으로 씁니다 — 얕은 체크아웃으로 보입니다."
  fi
  REF_UP="$(blob_at "$REF_SHA" "infra/demo/demo-up.sh")"
  [ -n "$REF_UP" ] || undecidable "$REF 에 infra/demo/demo-up.sh 가 없습니다."

  # 주입 후보: demo-up.sh 를 바꾼 커밋들을 최신순으로 훑어 **blob 이 실제로 다른** 첫 커밋.
  # 🔴 HEAD~n 같은 상수를 쓰지 않는 이유는 형제가 적었다 — 그 사이에 무관한 커밋이 쌓이면
  #    «내용이 같은 옛 커밋» 을 집어 이 칸을 조용히 공허하게 만든다.
  REF_OV="$(overlays_at "$REF_SHA" | sort | tr '\n' ' ')"

  # 두 후보를 **한 번에** 찾는다(칸②·칸④). 얕은 체크아웃에서는 역사를 더 가져와 다시 찾는다.
  find_injections() {
    INJECT=""; INJECT_OV=""
    local c c2
    while IFS= read -r c; do
      [ -n "$c" ] || continue
      if [ "$(blob_at "$c" "infra/demo/demo-up.sh")" != "$REF_UP" ]; then INJECT="$c"; break; fi
    done < <(git -C "$ROOT" log --format=%H -30 "$REF_SHA" -- ':/infra/demo/demo-up.sh' 2>/dev/null)
    while IFS= read -r c2; do
      [ -n "$c2" ] || continue
      have_commit "${c2}^" 2>/dev/null || continue
      if [ "$(overlays_at "${c2}^" | sort | tr '\n' ' ')" != "$REF_OV" ]; then
        INJECT_OV="$(git -C "$ROOT" rev-parse --verify --quiet "${c2}^" 2>/dev/null)"; break
      fi
    done < <(git -C "$ROOT" log --format=%H --diff-filter=AD "$REF_SHA" -- ':/infra/demo/*-vercel.override.yml' 2>/dev/null)
    [ -n "$INJECT" ] && [ -n "$INJECT_OV" ]
  }

  # 🔴🔴 **얕은 체크아웃에는 역사가 없다.** `actions/checkout@v4` 는 기본 fetch-depth=1 이라
  #    `git log` 가 커밋 하나만 돌려주고, 그러면 두 후보를 못 찾아 자가검사가 «대조군 성립
  #    불가»(2)로 죽는다 — **판정자가 틀려서가 아니라 재는 곳의 역사가 없어서** 다.
  #    🔵 워크플로에 fetch-depth: 0 을 박는 대신 여기서 필요한 만큼만 깊게 판다: 그 잡은
  #    이것 말고도 여러 가드를 도는 15분짜리이고, 전체 역사 클론을 그 전부에 물릴 이유가 없다.
  #    (nightly 잡은 실판정 때문에 어차피 fetch-depth: 0 이다.)
  if ! find_injections; then
    if [ "$(git -C "$ROOT" rev-parse --is-shallow-repository 2>/dev/null)" = "true" ]; then
      for d in 50 200 1000; do
        say "   얕은 체크아웃 — 역사를 ${d} 커밋 더 가져와 다시 찾습니다."
        git -C "$ROOT" fetch --deepen="$d" >/dev/null 2>&1 || break
        find_injections && break
      done
    fi
  fi

  [ -n "$INJECT" ] || undecidable "demo-up.sh 의 blob 이 다른 옛 커밋을 못 찾았습니다 ⇒ 대조군 성립 불가."

  # 🔴 주입 단언 — 읽기 전에.
  I_UP="$(blob_at "$INJECT" "infra/demo/demo-up.sh")"
  [ -n "$I_UP" ]            || undecidable "주입 커밋 ${INJECT:0:9} 에 demo-up.sh 가 없습니다."
  [ "$I_UP" != "$REF_UP" ]  || undecidable "주입이 안 됐습니다 — 두 커밋의 demo-up.sh blob 이 같습니다."
  say "   주입 확인: ${INJECT:0:9} 의 demo-up.sh blob ${I_UP:0:9} ≠ ${REF_SHA:0:9} 의 ${REF_UP:0:9}"
  say "   그 밖의 축은 손대지 않았다 — 같은 저장소·같은 판정자·바꾼 것은 «구운 세대» 하나뿐."

  # 칸 ①: 같은 커밋을 양쪽에 대면 **같은 세대(0)** 여야 한다.
  verdict_for "$REF_SHA" "$REF_SHA" "[칸① 같은 커밋]"; a=$?
  # 칸 ②: 주입한 옛 세대는 **어긋남(1)** 이어야 한다.
  verdict_for "$INJECT" "$REF_SHA" "[칸② 주입]"; b=$?
  # 칸 ③: 존재하지 않는 커밋은 **판정 불가(2)** 여야 한다 — «못 쟀다» 가 «괜찮다» 로 접히지 않는지.
  verdict_for "0000000000000000000000000000000000000000" "$REF_SHA" "[칸③ 없는 커밋]"; c=$?

  # ---------------------------------------------------------------------------
  # 칸 ④ — **억제 오버레이의 신설/삭제** 분기가 무는가
  # ---------------------------------------------------------------------------
  # 🔴 이 칸을 따로 두는 이유: 칸 ②는 «내용이 다르다» 분기만으로도 rc=1 을 낸다. 즉
  #    합집합 분기가 **한 번도 안 물어도 자가검사는 초록**이었고, 실제로 그랬다 —
  #    맨 rev-parse 가 인자를 되뱉는 바람에 신설 파일이 «내용 다름» 으로 잘못 분류됐다.
  # 🔴 주입 대상은 **위치가 아니라 성질로** 고른다: 「오버레이 **집합**이 기준과 다른
  #    커밋」. 상수 SHA 를 박으면 그 커밋이 역사에서 의미를 잃는 날 조용히 공허해진다.
  # 🔴🔴 후보를 «최근 N개 커밋» 에서 찾지 않는다. 그건 **예약된 자살**이다: main 이
  #    N 개만 더 자라면 창 밖으로 밀려나 이 칸이 어느 날 갑자기 판정 불가가 된다
  #    (이 저장소가 여러 번 밟은 «줄어드는 모집단 위의 하한» 과 같은 모양).
  #    대신 **오버레이를 실제로 추가/삭제한 커밋**을 역사에서 찾고 그 **부모**를 쓴다.
  #    그 사건들은 과거에 일어난 일이라 개수가 줄지 않는다.
  #    (후보 탐색 자체는 위 find_injections 에서 칸② 후보와 함께 한다 — 얕은 체크아웃에서
  #     역사를 더 가져오는 재시도를 두 후보가 공유해야 하기 때문이다.)
  if [ -z "$INJECT_OV" ]; then
    say "✖ 억제 오버레이를 추가/삭제한 커밋의 부모 중 오버레이 집합이 기준과 다른 것을 못 찾았습니다"
    say "  ⇒ 칸④ 대조군 성립 불가. (역사는 줄지 않으므로 보통 성립합니다 — 안 되면"
    say "   억제 오버레이의 경로 규칙(OVERLAY_RE)이나 이름 규약이 바뀐 것입니다.)"
    exit 2
  fi
  # 🔴 주입 단언 — 읽기 전에. 집합이 실제로 다른가.
  say "   칸④ 주입 확인: ${INJECT_OV:0:9} 의 오버레이 집합 [$(overlays_at "$INJECT_OV" | sed 's#.*/##' | tr '\n' ' ')]"
  say "                  ≠ ${REF_SHA:0:9} 의 [$(echo "$REF_OV" | sed 's#infra/demo/##g')]"
  verdict_for "$INJECT_OV" "$REF_SHA" "[칸④ 오버레이 집합 주입]"; d=$?
  d_named=0
  for entry in "${LAST_DRIFT[@]}"; do
    case "$entry" in *"없다"*|*"구운 세대에만"*) d_named=1 ;; esac
  done

  # ---------------------------------------------------------------------------
  # 칸 ⑤⑥⑦ — 좁힌 술어가 **좁힌 대로** 무는가 (TASK-MONO-649)
  # ---------------------------------------------------------------------------
  # 🔴 위 네 칸은 «판정자가 세대를 가르는가» 를 잰다. 좁힘은 그 축에서 **안 보인다** —
  #    칸②는 projects.sh 하나만으로도 빨개지므로, index.html 판정이 통째로 죽어도 초록이다.
  # 🔴🔴 그리고 좁힘은 **초록으로 실패한다.** 잘못 좁히면 아무도 모른다. 그래서 세 칸이
  #    직접 추출기를 두드린다: 선언을 바꾸면 달라지는가 · 선언 밖만 바꾸면 같은가 ·
  #    추출이 0건이면 «같다» 가 아니라 판정 불가인가.
  # 🔵 역사에서 못 주입한다 — 이 창의 역사에는 선언이 바뀐 세대가 없다(실측: 9차 핀
  #    3bc182ecd 로 내려가도 선언은 같고 projects.sh 가 빨강을 만든다). 그래서 여기서만
  #    합성 입력을 쓴다. 🔴 각 칸은 **읽기 전에 주입을 단언한다**.
  SD="$(mktemp -d)"
  git -C "$ROOT" show "$REF_SHA:infra/demo/aws/site/index.html" > "$SD/base.html" 2>/dev/null \
    || { rm -rf "$SD"; undecidable "기준 세대의 index.html 을 못 읽습니다."; }
  git -C "$ROOT" show "$REF_SHA:infra/demo/demo-up.sh" > "$SD/up.sh" 2>/dev/null \
    || { rm -rf "$SD"; undecidable "기준 세대의 demo-up.sh 를 못 읽습니다."; }

  BASE_DEC="$(extract_decls "$SD/base.html" "$SD/up.sh")" || {
    rm -rf "$SD"; undecidable "기준 선언을 못 뽑습니다: $EXTRACT_WHY (좁힌 술어가 아예 안 돕니다)"; }
  say "   칸⑤⑥⑦ 기준 선언 $(printf '%s\n' "$BASE_DEC" | grep -c '^surface=')건 · $(printf '%s\n' "$BASE_DEC" | sed -n 's/^rows=/행 /p')"

  # 칸 ⑤ — **선언 속성**을 바꾼다 ⇒ 달라져야 한다(좁혀도 진짜 어긋남은 여전히 문다).
  sed 's/data-demo-probe="/data-demo-probe="zz-/' "$SD/base.html" > "$SD/decl.html"
  if cmp -s "$SD/base.html" "$SD/decl.html"; then
    rm -rf "$SD"; undecidable "칸⑤ 주입 실패 — data-demo-probe 선언이 안 바뀌었습니다(속성 이름이 바뀌었습니까?)."
  fi
  E5="$(extract_decls "$SD/decl.html" "$SD/up.sh")"; r5=$?
  # 칸 ⑥ — **선언 밖**만 바꾼다(주석 한 줄) ⇒ 같아야 한다. **이 티켓의 본체다.**
  { cat "$SD/base.html"; printf '%s\n' '<!-- (칸⑥) 선언 밖 변경 — 구운 호스트는 이것을 안 읽는다 -->'; } > "$SD/cmt.html"
  if cmp -s "$SD/base.html" "$SD/cmt.html"; then
    rm -rf "$SD"; undecidable "칸⑥ 주입 실패 — 파일이 안 바뀌었습니다."
  fi
  E6="$(extract_decls "$SD/cmt.html" "$SD/up.sh")"; r6=$?
  # 칸 ⑦ — 추출이 **0건**이면 판정 불가(2). 빈 집합끼리는 서로 동의하므로 이 칸이 없으면
  #        좁힌 술어가 **공허하게 초록**이 될 수 있다.
  grep -v 'data-surface' "$SD/base.html" | grep -v 'data-demo-boot-probe' > "$SD/empty.html"
  if cmp -s "$SD/base.html" "$SD/empty.html"; then
    rm -rf "$SD"; undecidable "칸⑦ 주입 실패 — 선언 행이 안 지워졌습니다."
  fi
  extract_decls "$SD/empty.html" "$SD/up.sh" >/dev/null; r7=$?
  E7_WHY="$EXTRACT_WHY"
  rm -rf "$SD"

  e5=0; [ "$r5" -eq 0 ] && [ "$E5" != "$BASE_DEC" ] && e5=1
  e6=0; [ "$r6" -eq 0 ] && [ "$E6" =  "$BASE_DEC" ] && e6=1
  e7=0; [ "$r7" -eq 2 ] && e7=1

  # ---------------------------------------------------------------------------
  # 칸 ⑧ — **판정자가 그 좁힘을 실제로 쓰는가** (술어가 아니라 배선)
  # ---------------------------------------------------------------------------
  # 🔴🔴 칸⑤⑥⑦ 은 `extract_decls` 를 **직접** 두드린다. 그래서 추출기가 완벽해도
  #    `verdict_for` 가 그것을 안 쓰면 세 칸 다 초록이다 — 실측으로 확인했다: 좁힘 분기를
  #    `if false` 로 죽였더니 `--self-test` 가 **rc=0 으로 통과**했다. 「무는가」와
  #    「물 기회를 얻는가」는 다른 질문이고, 이 칸이 뒤엣것이다.
  # 🔴 주입은 역사에서 고른다: **index.html blob 은 다른데 선언은 같은** 옛 커밋. 그런
  #    커밋은 이 영역에서 가장 흔하다(머지 = 배포). 상수 SHA 를 박지 않는 이유는 칸④ 와 같다.
  REF_HTML="$(blob_at "$REF_SHA" "infra/demo/aws/site/index.html")"
  NARROW_C=""
  while IFS= read -r nc; do
    [ -n "$nc" ] || continue
    ncb="$(blob_at "$nc" "infra/demo/aws/site/index.html")"
    [ -n "$ncb" ] && [ "$ncb" != "$REF_HTML" ] || continue
    ncd="$(surface_decls_at "$nc" "$REF_SHA")" || continue
    [ "$ncd" = "$BASE_DEC" ] || continue
    NARROW_C="$nc"; break
  done < <(git -C "$ROOT" log --format=%H -50 "$REF_SHA" -- ':/infra/demo/aws/site/index.html' 2>/dev/null)

  if [ -z "$NARROW_C" ]; then
    say "✖ 칸⑧ 대조군 성립 불가 — index.html 이 **다르면서 선언은 같은** 옛 커밋을 50개 안에서 못 찾았습니다."
    say "  ⇒ 좁힘이 판정에 **배선됐는지**를 증명할 수 없습니다. 초록으로 넘기지 않습니다."
    exit 2
  fi
  say "   칸⑧ 주입 확인: ${NARROW_C:0:9} 의 index.html blob ${ncb:0:9} ≠ ${REF_SHA:0:9} 의 ${REF_HTML:0:9} · **선언은 같다**"
  verdict_for "$NARROW_C" "$REF_SHA" "[칸⑧ 배선]" >/dev/null; w=$?
  e8=1
  for entry in ${LAST_DRIFT[@]+"${LAST_DRIFT[@]}"}; do
    case "$entry" in *"index.html"*) e8=0 ;; esac
  done

  say "── 자가검사 결과: 같은커밋=$a  주입=$b  없는커밋=$c  오버레이=$d(분기적중=$d_named)   (기대 0 / 1 / 2 / 1·1)"
  say "   좁힘 칸: 선언바꿈=$e5  선언밖만바꿈=$e6  추출0건=$e7  배선=$e8(rc=$w)   (기대 1 / 1 / 1 / 1)"
  if [ "$e5" -ne 1 ] || [ "$e6" -ne 1 ] || [ "$e7" -ne 1 ] || [ "$e8" -ne 1 ]; then
    say "✖ 좁힌 술어(TASK-MONO-649)가 좁힌 대로 안 뭅니다."
    [ "$e5" -ne 1 ] && say "  · 칸⑤(선언 속성을 바꿨는데 같다고 함, rc=$r5) → **좁히다가 축을 통째로 죽였습니다.**"
    [ "$e6" -ne 1 ] && say "  · 칸⑥(주석만 바꿨는데 다르다고 함, rc=$r6) → 좁힘이 안 먹었습니다 — 이 티켓 이전 상태입니다."
    [ "$e7" -ne 1 ] && say "  · 칸⑦(추출 0건인데 rc=$r7, 기대 2) → **빈 집합끼리 동의**해서 공허하게 초록이 됩니다. ($E7_WHY)"
    [ "$e8" -ne 1 ] && say "  · 칸⑧(선언이 같은데 index.html 이 어긋남에 찍혔다) → **판정자가 좁힘을 안 씁니다.** 추출기는 맞는데 배선이 없습니다."
    exit 2
  fi
  if [ "$d" -ne 1 ] || [ "$d_named" -ne 1 ]; then
    say "✖ 오버레이 신설/삭제 분기가 **이름을 찍지 못했습니다.**"
    say "  rc 만 보면 «내용 다름» 으로도 1 이 나오므로, 이 칸은 **어느 분기가 물었는지**를 봅니다."
    say "  → blob_at 이 --verify --quiet 를 쓰는지 확인하세요(맨 rev-parse 는 실패해도 인자를 되뱉습니다)."
    exit 2
  fi
  if [ "$a" -ne 0 ] || [ "$b" -ne 1 ] || [ "$c" -ne 2 ]; then
    say "✖ 판정자가 세 칸을 가르지 못했습니다."
    say "  · 같은커밋≠0 → 판정자가 무엇을 대도 어긋났다고 말한다."
    say "  · 주입≠1     → 판정자가 무엇을 대도 같다고 말한다(= 아무것도 안 잰다)."
    say "  · 없는커밋≠2 → «못 쟀다» 를 «괜찮다» 로 접고 있다."
    exit 2
  fi
  say "✔ 판정자가 주입에 뭅니다 (같은커밋=같은세대 / 주입=어긋남 / 없는커밋=판정불가)."
  say "✔ 좁힌 술어가 좁힌 대로 뭅니다 (선언 변경=어긋남 / 선언 밖 변경=같음 / 추출 0건=판정불가 / 판정자가 실제로 그것을 쓴다)."
  exit 0
fi

# =============================================================================
# 실판정
# =============================================================================
if [ -n "$BAKED_OVERRIDE" ]; then
  BAKED="$BAKED_OVERRIDE"
  say "구운 세대를 인자로 받았습니다: ${BAKED:0:12} (핀 파일을 안 읽습니다)"
  PROV="cli-override"
  AMI_ID=""
else
  [ -f "$PIN" ] || undecidable "핀 파일이 없습니다: $PIN (구운 세대를 알 방법이 없습니다)"
  BAKED="$(pin_get "$PIN" REPO_COMMIT)"
  PROV="$(pin_get "$PIN" REPO_COMMIT_PROVENANCE)"
  AMI_ID="$(pin_get "$PIN" AMI_ID)"
  case "$BAKED" in
    [0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f]*) : ;;
    *) undecidable "핀 파일의 REPO_COMMIT 이 커밋 SHA 로 안 보입니다: '${BAKED:-없음}'" ;;
  esac
  [ -n "$PROV" ] || undecidable "핀 파일에 REPO_COMMIT_PROVENANCE 가 없습니다 — 기록의 출처를 모르면 판정도 모릅니다."
  say "핀: $PIN"
  say "   AMI = ${AMI_ID:-(없음)} · 구운 커밋 = ${BAKED:0:12} · **출처 = $PROV**"
  case "$PROV" in
    ami-tag) : ;;
    operator-record)
      say "   🔴 출처가 operator-record 입니다 — 이 값은 **이미지를 읽은 것이 아니라 사람이 적은 것**입니다."
      say "      (AMI 안을 직접 읽는 경로가 이 계정에 없습니다: ebs:ListSnapshotBlocks=AccessDenied ·"
      say "       stopped 인스턴스는 SSM 관리 대상 0건. 9차 굽기부터 ami-tag 로 바뀝니다.)" ;;
    *) undecidable "REPO_COMMIT_PROVENANCE 가 모르는 값입니다: '$PROV' (ami-tag | operator-record)" ;;
  esac
fi

verdict_for "$BAKED" "$REF"; RC=$?

# -----------------------------------------------------------------------------
# --with-aws — 핀이 **거짓말하지 않는지** 교차 확인
# -----------------------------------------------------------------------------
# 🔴 이것은 «어긋남» 과 **다른 축**이다. 위 판정은 «구운 세대가 무엇인가» 를 핀이 말한
#    대로 믿고 잰다. 이 절은 그 핀 자신을 잰다: AMI 태그가 같은 말을 하는가, 그리고
#    실제로 배포된(인스턴스가 물고 있는) AMI 가 핀이 적은 그 AMI 인가.
# 🔴 CI 에는 AWS 자격증명이 없다. 그래서 이 절은 **기본값이 아니고**, 요청했는데 못 돌면
#    0 이 아니라 **2**다 — 요청한 교차 확인을 못 했으면 그것은 통과가 아니다.
if [ "$WITH_AWS" -eq 1 ]; then
  say "▶ AWS 교차 확인"
  if ! command -v aws >/dev/null 2>&1; then
    undecidable "aws CLI 가 없습니다 — --with-aws 를 요청했으나 교차 확인을 못 했습니다."
  fi
  [ -n "$AMI_ID" ] || undecidable "핀에 AMI_ID 가 없어 교차 확인할 대상이 없습니다."

  TAGGED="$(aws ec2 describe-images --image-ids "$AMI_ID" \
              --query 'Images[0].Tags[?Key==`RepoCommit`].Value | [0]' --output text 2>/dev/null)"
  if [ -z "$TAGGED" ] || [ "$TAGGED" = "None" ]; then
    say "   ◑ AMI $AMI_ID 에 RepoCommit 태그가 **없습니다** — 8차까지의 AMI 는 커밋을 안 담습니다."
    say "     ⇒ 핀의 REPO_COMMIT 을 이미지로 확증할 수 없습니다(그래서 출처가 operator-record 입니다)."
    if [ "$PROV" = "ami-tag" ]; then
      say "✖ 그런데 핀은 출처를 ami-tag 라고 적고 있습니다 — **핀이 거짓말하고 있습니다.**"
      RC=1
    fi
  else
    say "   AMI 태그 RepoCommit = ${TAGGED:0:12}"
    if [ "$TAGGED" != "$BAKED" ]; then
      say "✖ 핀의 REPO_COMMIT(${BAKED:0:12}) 과 AMI 태그(${TAGGED:0:12})가 **다릅니다** — 핀이 낡았습니다."
      RC=1
    fi
  fi

  # 🔴 이름 태그는 terraform 이 파생한다: main.tf 의 `tags = { Name = "${local.name}-host" }`
  #    ⇒ 실제 값은 portfolio-demo**-host** 다. 이 술어의 첫 판은 'portfolio-demo' 로 등호
  #    비교해서 **0건**을 냈고, 0건은 「인스턴스가 없다」와 모양이 같아 «못 쟀다» 로 조용히
  #    지나갔다(실측 2026-09-06: 인스턴스는 stopped 로 **있었다**). 그래서 접두사 와일드카드로
  #    재고, **0건이면 관측기를 바꿔 한 번 더 잰다** — 계정 전체의 non-terminated 대수를
  #    세서 「진짜 없다」와 「내 태그 규약이 낡았다」를 가른다.
  DEPLOYED="$(aws ec2 describe-instances \
                --filters "Name=tag:Name,Values=portfolio-demo*" \
                --query 'Reservations[].Instances[?State.Name!=`terminated`].ImageId' \
                --output text 2>/dev/null | tr '\t' '\n' | sort -u | grep -v '^$')"
  if [ -z "$DEPLOYED" ]; then
    ANY="$(aws ec2 describe-instances \
             --query 'Reservations[].Instances[?State.Name!=`terminated`].InstanceId' \
             --output text 2>/dev/null | tr '\t' '\n' | grep -c '^i-')"
    say "   ◑ Name=portfolio-demo* 인스턴스가 0건입니다 — 배포된 AMI 축은 **못 쟀습니다.**"
    if [ "${ANY:-0}" -gt 0 ]; then
      say "     🔴 그런데 계정에는 non-terminated 인스턴스가 ${ANY}대 있습니다 — «없다» 가 아니라"
      say "        **이 술어의 이름 규약이 낡은 것**일 수 있습니다(main.tf 의 Name 태그를 보세요)."
      RC=2
    fi
  else
    say "   인스턴스가 물고 있는 AMI = $(echo "$DEPLOYED" | tr '\n' ' ')"
    if [ "$DEPLOYED" != "$AMI_ID" ]; then
      say "✖ 배포된 AMI 가 핀($AMI_ID)과 **다릅니다** — 핀이 실제 배포를 안 가리킵니다."
      RC=1
    fi
  fi
fi

# -----------------------------------------------------------------------------
# 닫는 말 — 🔴 «예상된 빨강» 이라고 적지 않는다
# -----------------------------------------------------------------------------
# 어긋남은 실제 상태다: 데모 호스트가 서빙하는 것과 론처가 방문자에게 약속하는 것이
# 다르다. 그 빨강에 미리 면죄부를 주면 진짜 빨강도 같이 무시된다(TASK-MONO-627 이
# «없는 예고» 를 랜딩했다가 되돌린 그 실패 모드).
case "$RC" in
  0) say "✔ 같은 세대입니다." ;;
  1) say ""
     say "  ⇒ **닫는 행위는 재굽기다**: bash infra/demo/aws/packer/bake.sh (~55분 · 소유자 승인)"
     say "     그 뒤 terraform.tfvars 의 ami_id 갱신 + apply, 그리고 이 핀 파일 커밋."
     say "  ⇒ 🔴 부팅 로그에서 기다릴 지문은 **구운 세대의 것**입니다. 저장소 최신 주석이"
     say "     약속하는 문자열을 기다리면 창이 영원히 안 열립니다. 구운 세대가 무엇을 찌르는지는"
     say "     git show ${BAKED:0:12}:infra/demo/aws/site/index.html | grep demo-probe 로 보세요." ;;
  2) say "◑ 판정 불가 — 위 사유를 보세요. 초록이 아닙니다." ;;
esac
exit "$RC"
