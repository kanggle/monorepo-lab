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

# 🔴🔴 `--verify --quiet` 는 장식이 아니다. 맨 `git rev-parse <rev>:<path>` 는 경로가 그
#    커밋에 **없을 때 인자를 그대로 stdout 에 되뱉는다**(rc=128 인 채로):
#      $ git rev-parse afebc9371:infra/demo/console-vercel.override.yml 2>/dev/null
#      afebc9371:infra/demo/console-vercel.override.yml        ← 빈 문자열이 아니다
#    이 파일의 첫 판이 그것을 몰라서 «한쪽에만 있는 파일» 분기가 **한 번도 못 물었고**
#    (신설된 console 억제를 «내용 다름» 으로 오분류했다), 코어 경로 이름이 바뀌는 날
#    «판정 불가» 로 멈춰야 할 자리도 조용히 «어긋남» 이 됐을 것이다.
#    실측으로 잡았다(2026-09-06): 기대한 문구가 안 나오길래 두 트리를 직접 세어 봤다.
blob_at() { git -C "$ROOT" rev-parse --verify --quiet "${1}:${2}" 2>/dev/null; }

overlays_at() {
  git -C "$ROOT" ls-tree --name-only "$1" "$OVERLAY_DIR" 2>/dev/null | grep -E "$OVERLAY_RE"
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
  baked_sha="$(git -C "$ROOT" rev-parse "${baked}^{commit}" 2>/dev/null)"
  ref_sha="$(git -C "$ROOT" rev-parse "${ref}^{commit}" 2>/dev/null)"
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
  REF_SHA="$(git -C "$ROOT" rev-parse "${REF}^{commit}" 2>/dev/null)" \
    || undecidable "기준 ref '$REF' 를 못 읽습니다."
  [ -n "$REF_SHA" ] || undecidable "기준 ref '$REF' 를 못 읽습니다."
  REF_UP="$(blob_at "$REF_SHA" "infra/demo/demo-up.sh")"
  [ -n "$REF_UP" ] || undecidable "$REF 에 infra/demo/demo-up.sh 가 없습니다."

  # 주입 후보: demo-up.sh 를 바꾼 커밋들을 최신순으로 훑어 **blob 이 실제로 다른** 첫 커밋.
  # 🔴 HEAD~n 같은 상수를 쓰지 않는 이유는 형제가 적었다 — 그 사이에 무관한 커밋이 쌓이면
  #    «내용이 같은 옛 커밋» 을 집어 이 칸을 조용히 공허하게 만든다.
  INJECT=""
  while IFS= read -r c; do
    [ -n "$c" ] || continue
    if [ "$(blob_at "$c" "infra/demo/demo-up.sh")" != "$REF_UP" ]; then INJECT="$c"; break; fi
  done < <(git -C "$ROOT" log --format=%H -30 "$REF_SHA" -- ':/infra/demo/demo-up.sh' 2>/dev/null)

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
  REF_OV="$(overlays_at "$REF_SHA" | sort | tr '\n' ' ')"
  INJECT_OV=""
  while IFS= read -r c2; do
    [ -n "$c2" ] || continue
    have_commit "${c2}^" 2>/dev/null || continue
    if [ "$(overlays_at "${c2}^" | sort | tr '\n' ' ')" != "$REF_OV" ]; then
      INJECT_OV="$(git -C "$ROOT" rev-parse "${c2}^" 2>/dev/null)"; break
    fi
  done < <(git -C "$ROOT" log --format=%H --diff-filter=AD "$REF_SHA" -- ':/infra/demo/*-vercel.override.yml' 2>/dev/null)

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

  say "── 자가검사 결과: 같은커밋=$a  주입=$b  없는커밋=$c  오버레이=$d(분기적중=$d_named)   (기대 0 / 1 / 2 / 1·1)"
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
