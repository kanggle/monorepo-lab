#!/usr/bin/env bash
# =============================================================================
# bake.sh — AMI 를 굽고, **구운 커밋을 기록으로 남긴다** (TASK-MONO-628)
# =============================================================================
# 맨 `packer build` 를 대체한다. 하는 일은 셋:
#
#   1) 굽을 커밋을 **origin 에서** 해석한다 (`git ls-remote`, 로컬 origin/main 아님 —
#      로컬은 낡을 수 있고, packer 는 GitHub 에서 클론하므로 기준도 GitHub 이어야 한다)
#   2) 그 SHA 를 `-var repo_commit=` 로 넘긴다 → packer 2단계가 클론된 HEAD 와 대조하고,
#      통과한 값만 EC2 태그 RepoCommit 으로 나간다
#   3) 굽기 성공 후 **AMI 태그에서 되읽어** infra/demo/aws/deployed-ami.env 를 다시 쓴다
#      ⇒ 그 파일의 REPO_COMMIT_PROVENANCE 가 operator-record → ami-tag 로 승격된다
#
#   bash infra/demo/aws/packer/bake.sh [--dry-run] [--ref <branch>]
#
# 🔴 **~55분 걸리고 AWS 과금이 발생한다(EC2 c6i.4xlarge + 100GB 스냅샷).**
#    소유자가 명시적으로 지목했을 때만 돌려라. 에이전트가 스스로 시작하지 않는다.
#
# 🔴 `--dry-run` 은 굽지 않는다. 해석·전제 확인·명령 출력까지만 한다. 이 스크립트가
#    실제 굽기 없이 검증될 수 있는 유일한 경로이므로 CI 가 이것을 돈다.
#
# -----------------------------------------------------------------------------
# 왜 «굽고 나서 태그를 되읽는가»
# -----------------------------------------------------------------------------
# 우리가 넘긴 값을 그대로 파일에 적으면 그것은 여전히 **우리가 한 말**이다. AMI 태그에서
# 되읽으면 그 값은 **이미지가 확증하고 EC2 가 보관한 값**이고, 둘이 다르면 파일을 안 쓴다.
# 그 구별이 이 파일 전체의 존재 이유다 — 「그렇다고 한다」와 「그렇다」는 다른 명제다.
# =============================================================================
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AWSDIR="$(cd "$HERE/.." && pwd)"
PIN="$AWSDIR/deployed-ami.env"

DRY=0
REF="main"
SELFTEST=0
RESCUE_ONLY=0
while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run)     DRY=1; shift ;;
    --self-test)   SELFTEST=1; shift ;;
    --rescue-only) RESCUE_ONLY=1; shift ;;
    --ref)         REF="${2:-}"; shift 2 ;;
    *) echo "알 수 없는 인자: $1" >&2; exit 2 ;;
  esac
done

say() { echo "[bake] $*"; }
die() { echo "[bake] ✖ $*" >&2; exit 2; }

# =============================================================================
# 구조(rescue) — 굽기가 **AMI 등록 뒤에** 죽었을 때 (TASK-MONO-709)
# =============================================================================
# 2026-09-17 12차 굽기 실측: 37분 55초에 `Waiting for AMI to become ready...` 에서
# packer 가 `unexpected EOF` 로 죽었다. 그 시점에 **이미 끝나 있던 것**:
#   · 2단계 HEAD 대조(`cloned HEAD=<sha> expected=<sha>`)  · 인스턴스 안 정적 검증 PASS
#   · `CreateImage`(AMI 는 등록됐고 available 로 갔다)
# **안 된 것**: RepoCommit 태그(태그는 ready 뒤에 붙는다) · 핀 파일 · 빌더 정리.
# ⇒ 사람이 손으로 태그를 붙여 살렸고(#3905), 그 절차가 **어디에도 적혀 있지 않았다**.
#
# 🔴 **provenance 는 `operator-record` 다.** 태그를 우리가 붙였기 때문이다 —
#    「이미지가 스스로 한 말」과 「사람이 적은 말」의 구별이 핀 파일의 존재 이유다
#    (`deployed-ami.env` 머리말 · `TASK-MONO-628`).
# 🔴 고아(빌더 인스턴스 · SG · 키페어)는 **보고만** 한다. 지우지 않는다 — 원인 조사의
#    유일한 증거가 그 디스크일 때가 있다.
# -----------------------------------------------------------------------------
# 판정만 떼어 둔 함수 — AWS 없이 `--self-test` 가 이 술어를 잰다.
#   입력: describe-images 한 줄들("<ImageId>\t<Name>\t<State>\t<RepoCommit|None>"), 기대 SHA
#   출력: "<verdict>\t<ImageId>\t<Name>"  verdict ∈ rescue | already-tagged | none | ambiguous
decide_rescue() {
  local rows="$1" sha="$2"
  local n=0 id="" name="" state="" tag="" vid="" vname="" vstate="" vtag=""
  while IFS=$'\t' read -r id name state tag; do
    [ -n "${id:-}" ] || continue
    n=$((n + 1)); vid="$id"; vname="$name"; vstate="$state"; vtag="$tag"
  done <<EOF
$rows
EOF
  if [ "$n" -eq 0 ]; then printf 'none\t\t\n'; return 0; fi
  if [ "$n" -gt 1 ]; then printf 'ambiguous\t\t\n'; return 0; fi
  if [ "$vtag" = "$sha" ]; then printf 'already-tagged\t%s\t%s\n' "$vid" "$vname"; return 0; fi
  if [ -n "$vtag" ] && [ "$vtag" != "None" ]; then printf 'ambiguous\t%s\t%s\n' "$vid" "$vname"; return 0; fi
  case "$vstate" in
    available|pending) printf 'rescue\t%s\t%s\n' "$vid" "$vname" ;;
    *)                 printf 'none\t%s\t%s\n' "$vid" "$vname" ;;
  esac
}

# 굽기가 시작된 뒤(±여유) 만들어진 `portfolio-demo-*` self AMI 를 찾는다.
# 🔵 이름의 숫자는 packer `{{timestamp}}` = **빌드 시작 epoch** 이므로, 그것으로 «이 굽기의
#    산출물인가» 를 판정한다(우리가 재는 시각이 아니라 packer 가 박은 값이다).
rescue_rows() {
  local since="$1"
  aws ec2 describe-images --owners self \
    --filters "Name=name,Values=portfolio-demo-*" \
    --query "Images[].[ImageId,Name,State,Tags[?Key=='RepoCommit']|[0].Value]" \
    --output text 2>/dev/null |
    awk -v since="$since" -F'\t' '{ n=$2; sub(/^portfolio-demo-/,"",n); if (n+0 >= since+0) print }'
}

report_orphans() {
  say "고아 리소스 — **지우지 않습니다**(원인 조사의 증거일 수 있습니다). 확인 후 손으로 지우세요:"
  aws ec2 describe-instances \
      --filters "Name=key-name,Values=packer_*" "Name=instance-state-name,Values=pending,running,stopping,stopped" \
      --query 'Reservations[].Instances[].[InstanceId,InstanceType,State.Name,KeyName]' --output text 2>/dev/null |
    while IFS=$'\t' read -r i t s k; do [ -n "${i:-}" ] && say "   인스턴스 $i ($t · $s · key=$k)"; done
  aws ec2 describe-security-groups --filters "Name=group-name,Values=packer_*" \
      --query 'SecurityGroups[].[GroupId,GroupName]' --output text 2>/dev/null |
    while IFS=$'\t' read -r g gn; do [ -n "${g:-}" ] && say "   보안그룹 $g ($gn)"; done
  aws ec2 describe-key-pairs --filters "Name=key-name,Values=packer_*" \
      --query 'KeyPairs[].KeyName' --output text 2>/dev/null |
    tr '\t' '\n' | while read -r kn; do [ -n "${kn:-}" ] && say "   키페어 $kn"; done
  say "   (순서: 인스턴스 종료 → 보안그룹 → 키페어. 인스턴스가 살아 있으면 SG 삭제가 거부됩니다.)"
}

# 핀 파일 쓰기 — provenance 를 인자로 받는다(정상 경로 `ami-tag` · 구조 경로 `operator-record`).
write_pin() {
  local ami_id="$1" ami_name="$2" started="$3" sha="$4" prov="$5"
  local tmp="$PIN.tmp.$$"
  sed -e "s#^AMI_ID=.*#AMI_ID=$ami_id#" \
      -e "s#^AMI_NAME=.*#AMI_NAME=$ami_name#" \
      -e "s#^BAKE_STARTED_AT=.*#BAKE_STARTED_AT=$started#" \
      -e "s#^REPO_COMMIT=.*#REPO_COMMIT=$sha#" \
      -e "s#^REPO_COMMIT_PROVENANCE=.*#REPO_COMMIT_PROVENANCE=$prov#" \
      "$PIN" > "$tmp" || die "핀 파일 갱신 실패"
  grep -q "^REPO_COMMIT=$sha$" "$tmp" || { rm -f "$tmp"; die "갱신된 핀에 새 커밋이 안 들어갔습니다 — 안 바꿉니다."; }
  grep -q "^REPO_COMMIT_PROVENANCE=$prov$" "$tmp" || { rm -f "$tmp"; die "provenance 가 '$prov' 로 안 적혔습니다 — 안 바꿉니다."; }
  mv "$tmp" "$PIN" || die "핀 파일 rename 실패"
}

started_at_from_name() {
  local epoch="${1##*-}"
  case "$epoch" in
    [0-9]*) date -u -d "@$epoch" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || echo unknown ;;
    *)      echo unknown ;;
  esac
}

# 굽기 실패(또는 `--rescue-only`) 뒤 호출된다. rc: 0=구조함 · 1=구조할 것이 없음
rescue() {
  local since="$1" sha="$2"
  local rows verdict ami_id ami_name
  rows="$(rescue_rows "$since")"
  IFS=$'\t' read -r verdict ami_id ami_name <<EOF
$(decide_rescue "$rows" "$sha")
EOF
  case "$verdict" in
    none)
      say "구조할 AMI 가 없습니다 — 등록 전에 죽었거나(정상 실패) 이름이 다릅니다."
      return 1 ;;
    ambiguous)
      say "🔴 후보가 여럿이거나 **다른 커밋의 태그**가 붙어 있습니다 — 고르지 않습니다. 손으로 확인하세요:"
      printf '%s\n' "$rows" | while IFS=$'\t' read -r i n s t; do [ -n "${i:-}" ] && say "   $i  $n  $s  RepoCommit=${t:-없음}"; done
      return 1 ;;
    already-tagged)
      say "AMI $ami_id ($ami_name) 에 이미 RepoCommit=$sha 태그가 있습니다 — 구조 불필요, 핀만 씁니다."
      ;;
    rescue)
      say "▶ 구조 대상: $ami_id ($ami_name) — 태그 없음. available 을 기다립니다(스냅샷 100GB, 수십 분 걸릴 수 있습니다)."
      aws ec2 wait image-available --image-ids "$ami_id" || die "AMI $ami_id 가 available 이 되지 않았습니다 — 태그도 핀도 안 씁니다."
      local snap
      snap="$(aws ec2 describe-images --image-ids "$ami_id" --query 'Images[0].BlockDeviceMappings[0].Ebs.SnapshotId' --output text 2>/dev/null)"
      aws ec2 create-tags --resources "$ami_id" ${snap:+$snap} \
          --tags Key=Name,Value=portfolio-demo Key=Project,Value=monorepo-lab "Key=RepoCommit,Value=$sha" \
        || die "태그를 못 붙였습니다 — 핀은 안 씁니다."
      local back
      back="$(aws ec2 describe-images --image-ids "$ami_id" --query "Images[0].Tags[?Key=='RepoCommit']|[0].Value" --output text 2>/dev/null)"
      [ "$back" = "$sha" ] || die "태그를 되읽었더니 '$back' 입니다 — 핀은 안 씁니다."
      say "✔ 태그 3종을 붙였습니다(되읽어 확인). 🔴 이것은 **사람이 붙인 값**입니다."
      ;;
  esac
  write_pin "$ami_id" "$ami_name" "$(started_at_from_name "$ami_name")" "$sha" "operator-record"
  say "✔ $PIN 갱신 (provenance = **operator-record** — 이미지가 확증한 값이 아닙니다)"
  report_orphans
  cat <<MSG

[bake] ───────────────────────────────────────────────────────────────────────
[bake] 구조했습니다. 남은 것은 사람 몫입니다:
[bake]   1) terraform.tfvars 의 ami_id 를 $ami_id 로 (⚠️ 인스턴스 **교체** = docker 볼륨 소멸)
[bake]   2) terraform plan -out=… → **apply 는 소유자**
[bake]   3) 핀 파일 커밋 — $PIN (🔴 provenance 가 operator-record 인 이유도 커밋 메시지에)
[bake]   4) bash infra/demo/aws/check-ami-generation.sh --with-aws → rc=0
[bake]   5) 위 고아 리소스 확인 후 정리(인스턴스 → SG → 키페어 순서)
[bake] ───────────────────────────────────────────────────────────────────────
MSG
  return 0
}

# -----------------------------------------------------------------------------
# 전제 — 🔴 굽기 55분을 태운 뒤에 알게 되는 것을 여기서 먼저 죽인다
# -----------------------------------------------------------------------------
# -----------------------------------------------------------------------------
# `--self-test` — 구조 **판정**을 AWS 없이 잰다 (TASK-MONO-709 AC-3)
# -----------------------------------------------------------------------------
# 🔴 실제 굽기로는 시험하지 않는다(55분 + 과금). 잴 수 있는 것은 `decide_rescue()` 의
#    술어이고, 그것이 이 구조 경로의 «어느 길로 갈까» 를 혼자 정한다.
if [ "$SELFTEST" -eq 1 ]; then
  SHA_FIX="af0018aa6504cc5e4ebe4b4505e386081fe2ca66"
  bad=0
  ran=0
  check() { # <이름> <기대 verdict> <rows>
    local name="$1" want="$2" rows="$3" got
    ran=$((ran + 1))
    got="$(decide_rescue "$rows" "$SHA_FIX" | cut -f1)"
    if [ "$got" = "$want" ]; then echo "  ✔ $name  want=$want"; else echo "  ✗ $name  want=$want got=$got"; bad=$((bad + 1)); fi
  }
  # ① 등록은 됐는데 태그가 없다 — 2026-09-17 의 그 상태(구조 대상)
  check 'ami-without-tag-available' rescue "$(printf 'ami-02613b0378621b124\tportfolio-demo-1789658583\tavailable\tNone')"
  # ①' 아직 pending — 기다렸다 태그를 붙이면 된다(구조 대상)
  check 'ami-without-tag-pending'   rescue "$(printf 'ami-02613b0378621b124\tportfolio-demo-1789658583\tpending\tNone')"
  # ② 이미 태그가 있다 — 정상 경로가 이미 끝난 상태. 태그를 **다시 붙이지 않는다**
  check 'ami-already-tagged'        already-tagged "$(printf 'ami-0d30513151d07e163\tportfolio-demo-1789630615\tavailable\t%s' "$SHA_FIX")"
  # ③ 등록 전에 죽었다 — 구조할 것이 없다(오늘과 같은 실패로 끝난다)
  check 'no-ami-at-all'             none "$(printf '')"
  # ④ 후보가 여럿 — 🔴 고르지 않는다
  check 'two-candidates'            ambiguous "$(printf 'ami-a\tportfolio-demo-1\tavailable\tNone\nami-b\tportfolio-demo-2\tavailable\tNone')"
  # ⑤ **다른 커밋**의 태그가 붙어 있다 — 남의 산출물일 수 있다. 고르지 않는다
  check 'tagged-with-other-commit'  ambiguous "$(printf 'ami-c\tportfolio-demo-3\tavailable\tdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef')"
  # ⑥ 실패한 AMI — 태그를 붙이지 않는다
  check 'ami-failed-state'          none "$(printf 'ami-d\tportfolio-demo-4\tfailed\tNone')"
  # 🔴 **칸 수를 손으로 적지 않는다** — 세어서 찍는다(처음 판이 7칸을 «6/6» 이라고 말했다).
  #    그리고 판정 종류가 한쪽으로 쏠리면 «전부 통과» 가 공허하므로 그것도 센다.
  kinds="$(printf 'rescue\nrescue\nalready-tagged\nnone\nambiguous\nambiguous\nnone\n' | sort -u | wc -l)"
  [ "$kinds" -ge 3 ] || { echo "[bake] ✖ self-test 픽스처가 공허합니다 — 판정 종류 $kinds 가지" >&2; exit 1; }
  if [ "$bad" -eq 0 ]; then echo "[bake] self-test $ran/$ran — 구조 판정 술어 (판정 종류 $kinds 가지)"; exit 0; fi
  echo "[bake] ✖ self-test $((ran - bad))/$ran — 실패 $bad 건" >&2; exit 1
fi

command -v git >/dev/null 2>&1 || die "git 이 없습니다."
[ -f "$PIN" ] || die "핀 파일이 없습니다: $PIN (이 스크립트가 갱신할 대상입니다)"
[ -f "$HERE/demo-ami.pkr.hcl" ] || die "packer 템플릿이 없습니다: $HERE/demo-ami.pkr.hcl"

# 템플릿이 repo_commit 을 **정말로 요구하는가**. 이 스크립트만 고치고 템플릿이 옛 판이면
# 굽기는 통과하고 태그만 조용히 안 붙는다 — 그러면 이 스크립트는 3단계에서야 죽는다.
grep -q 'variable "repo_commit"' "$HERE/demo-ami.pkr.hcl" \
  || die "템플릿에 variable \"repo_commit\" 이 없습니다 — 태그가 안 붙는 판입니다."
grep -q 'RepoCommit' "$HERE/demo-ami.pkr.hcl" \
  || die "템플릿이 RepoCommit 태그를 안 붙입니다 — 되읽을 값이 생기지 않습니다."

# -----------------------------------------------------------------------------
# 1) 굽을 커밋 해석 — **origin 에서**
# -----------------------------------------------------------------------------
# 🔴 로컬 origin/main 을 쓰지 않는다. packer 는 github.com 에서 클론하므로, 로컬이
#    낡아 있으면 우리가 넘긴 SHA 와 클론된 HEAD 가 달라 2단계가 빌드를 죽인다.
SHA="$(git ls-remote "https://github.com/kanggle/monorepo-lab.git" "refs/heads/$REF" 2>/dev/null | awk '{print $1}')"
case "$SHA" in
  [0-9a-f]*) [ "${#SHA}" -eq 40 ] || die "해석된 SHA 가 40자가 아닙니다: '$SHA'" ;;
  *) die "origin 에서 refs/heads/$REF 를 해석하지 못했습니다 (네트워크? 브랜치 이름?)" ;;
esac
say "굽을 커밋: $SHA  (origin refs/heads/$REF)"

# 🔵 정보일 뿐 게이트가 아니다 — 로컬이 낡아도 굽기는 origin 기준으로 옳다.
LOCAL="$(git -C "$AWSDIR" rev-parse --verify --quiet "origin/$REF" 2>/dev/null || echo '?')"
[ "$LOCAL" = "$SHA" ] || say "(참고) 로컬 origin/$REF = ${LOCAL:0:9} — origin 과 다릅니다. 굽는 것은 origin 쪽입니다."

CMD=(packer build -var "repo_ref=$REF" -var "repo_commit=$SHA" demo-ami.pkr.hcl)

if [ "$DRY" -eq 1 ]; then
  say "▶ --dry-run — 굽지 않습니다."
  say "   cd $HERE && packer init . && ${CMD[*]}"
  say "   굽기 뒤 이 스크립트가 할 일: AMI 태그 RepoCommit 을 되읽어 $PIN 을 다시 씁니다."
  say "   굽기가 **AMI 등록 뒤에** 죽으면: 그 AMI 를 찾아 available 을 기다렸다 태그를 붙이고"
  say "   핀을 provenance=**operator-record** 로 씁니다(사람이 붙인 태그이므로). 고아는 보고만 합니다."
  say "   그 판정만 따로 재려면: bash bake.sh --self-test   ·  이전 실패를 구조하려면: --rescue-only --ref <branch>"
  # 🔴 dry-run 도 «아무것도 안 하고 rc=0» 이 되면 안 된다. 위 전제 검사가 전부 돈 것이
  #    이 rc 의 내용이다. 해석된 SHA 를 마지막 줄로 찍어 호출자가 확인할 수 있게 한다.
  echo "$SHA"
  exit 0
fi

command -v packer >/dev/null 2>&1 || die "packer 가 없습니다."
command -v aws    >/dev/null 2>&1 || die "aws CLI 가 없습니다 — 3단계(태그 되읽기)를 못 합니다."

# -----------------------------------------------------------------------------
# 2) 굽기
# -----------------------------------------------------------------------------
# 🔵 `--rescue-only` — **이전 실행**이 AMI 등록 뒤에 죽었을 때 쓰는 입구(굽지 않는다).
#    기준 시각은 «가장 최근 portfolio-demo-* AMI 의 이름 epoch» 로 잡는다.
if [ "$RESCUE_ONLY" -eq 1 ]; then
  command -v aws >/dev/null 2>&1 || die "aws CLI 가 없습니다."
  LAST="$(aws ec2 describe-images --owners self --filters "Name=name,Values=portfolio-demo-*" \
            --query 'sort_by(Images,&CreationDate)[-1].Name' --output text 2>/dev/null)"
  SINCE="${LAST##*-}"
  case "$SINCE" in [0-9]*) : ;; *) die "최근 AMI 이름에서 epoch 을 못 읽었습니다: '$LAST'" ;; esac
  say "▶ --rescue-only — 굽지 않습니다. 기준 epoch=$SINCE ($LAST)"
  rescue "$SINCE" "$SHA" && exit 0
  die "구조할 것이 없습니다 — 위 사유를 보세요."
fi

say "▶ packer build — ~55분. 이 창을 닫지 마세요."
cd "$HERE" || die "cd 실패: $HERE"
packer init . || die "packer init 실패"
# 🔵 빌드 시작 epoch — packer 의 `{{timestamp}}` 와 같은 기준이라 구조가 «이 굽기의 산출물» 을 짚을 수 있다.
BUILD_STARTED="$(date -u +%s)"
"${CMD[@]}"; PRC=$?
if [ "$PRC" -ne 0 ]; then
  say "✖ packer build 가 rc=$PRC 로 끝났습니다 — **AMI 가 이미 등록됐는지** 봅니다(TASK-MONO-709)."
  if rescue "$((BUILD_STARTED - 120))" "$SHA"; then
    exit 0
  fi
  die "packer build 실패, 구조할 AMI 도 없습니다. 핀 파일은 **안 고칩니다.**"
fi
say "✔ packer build rc=0"

# -----------------------------------------------------------------------------
# 3) 태그에서 되읽어 핀 파일 갱신
# -----------------------------------------------------------------------------
# 🔴 「방금 구운 것」을 이름이나 시각으로 짚지 않는다 — 그건 또 하나의 유도값이다.
#    RepoCommit 태그가 우리가 넘긴 SHA 와 **같은** AMI 를 찾는다. 없으면 안 쓴다.
read -r AMI_ID AMI_NAME <<EOF
$(aws ec2 describe-images --owners self \
    --filters "Name=tag:RepoCommit,Values=$SHA" \
    --query 'sort_by(Images,&CreationDate)[-1].[ImageId,Name]' --output text 2>/dev/null)
EOF
case "${AMI_ID:-}" in
  ami-*) : ;;
  *) die "RepoCommit=$SHA 태그를 가진 self AMI 를 못 찾았습니다 — 핀 파일을 **안 고칩니다.**
       (굽기는 성공했는데 태그가 없다면 템플릿의 tags 블록을 보세요.)" ;;
esac
say "새 AMI: $AMI_ID ($AMI_NAME) — 태그 RepoCommit 이 $SHA 와 일치합니다."

# {{timestamp}} = 빌드 시작 epoch. 이름에서 되읽는다(우리가 재는 것이 아니라 packer 가 박은 값).
# 🔴 덮어쓰기는 임시 파일 + rename 이다(`write_pin`). 중간에 죽으면 핀이 반쯤 쓰인 채 남는데,
#    그 상태는 판정자에게 «판정 불가» 가 아니라 **틀린 값**으로 읽힌다.
# 🔵 구조 경로와 **같은 함수**를 쓴다 — 두 경로가 갈라지면 한쪽만 고쳐진다(TASK-MONO-709).
write_pin "$AMI_ID" "$AMI_NAME" "$(started_at_from_name "$AMI_NAME")" "$SHA" "ami-tag"
say "✔ $PIN 갱신 (provenance = ami-tag)"

cat <<MSG

[bake] ───────────────────────────────────────────────────────────────────────
[bake] 남은 것은 사람 몫입니다:
[bake]   1) infra/demo/aws/terraform/terraform.tfvars 의 ami_id 를 $AMI_ID 로
[bake]      (⚠️ 인스턴스를 **교체**합니다 = docker 볼륨 소멸)
[bake]   2) cd infra/demo/aws/terraform && terraform apply
[bake]   3) **핀 파일을 커밋하세요** — infra/demo/aws/deployed-ami.env
[bake]      커밋해야 CI 의 판정자가 새 세대를 봅니다. 안 하면 nightly 는 계속
[bake]      옛 세대를 기준으로 어긋남을 보고합니다(그리고 그건 옳은 보고입니다).
[bake]   4) bash infra/demo/aws/check-ami-generation.sh --with-aws  → 0 이어야 합니다
[bake] ───────────────────────────────────────────────────────────────────────
MSG
exit 0
