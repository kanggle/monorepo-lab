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
while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run) DRY=1; shift ;;
    --ref)     REF="${2:-}"; shift 2 ;;
    *) echo "알 수 없는 인자: $1" >&2; exit 2 ;;
  esac
done

say() { echo "[bake] $*"; }
die() { echo "[bake] ✖ $*" >&2; exit 2; }

# -----------------------------------------------------------------------------
# 전제 — 🔴 굽기 55분을 태운 뒤에 알게 되는 것을 여기서 먼저 죽인다
# -----------------------------------------------------------------------------
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
LOCAL="$(git -C "$AWSDIR" rev-parse "origin/$REF" 2>/dev/null || echo '?')"
[ "$LOCAL" = "$SHA" ] || say "(참고) 로컬 origin/$REF = ${LOCAL:0:9} — origin 과 다릅니다. 굽는 것은 origin 쪽입니다."

CMD=(packer build -var "repo_ref=$REF" -var "repo_commit=$SHA" demo-ami.pkr.hcl)

if [ "$DRY" -eq 1 ]; then
  say "▶ --dry-run — 굽지 않습니다."
  say "   cd $HERE && packer init . && ${CMD[*]}"
  say "   굽기 뒤 이 스크립트가 할 일: AMI 태그 RepoCommit 을 되읽어 $PIN 을 다시 씁니다."
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
say "▶ packer build — ~55분. 이 창을 닫지 마세요."
cd "$HERE" || die "cd 실패: $HERE"
packer init . || die "packer init 실패"
"${CMD[@]}"; PRC=$?
[ "$PRC" -eq 0 ] || die "packer build 가 rc=$PRC 로 끝났습니다. 핀 파일은 **안 고칩니다.**"
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
EPOCH="${AMI_NAME##*-}"
case "$EPOCH" in
  [0-9]*) STARTED="$(date -u -d "@$EPOCH" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || echo unknown)" ;;
  *)      STARTED="unknown" ;;
esac

# 🔴 덮어쓰기는 임시 파일 + rename 이다. 중간에 죽으면 핀이 반쯤 쓰인 채 남는데,
#    그 상태는 판정자에게 «판정 불가» 가 아니라 **틀린 값**으로 읽힌다.
TMP="$PIN.tmp.$$"
sed -e "s#^AMI_ID=.*#AMI_ID=$AMI_ID#" \
    -e "s#^AMI_NAME=.*#AMI_NAME=$AMI_NAME#" \
    -e "s#^BAKE_STARTED_AT=.*#BAKE_STARTED_AT=$STARTED#" \
    -e "s#^REPO_COMMIT=.*#REPO_COMMIT=$SHA#" \
    -e "s#^REPO_COMMIT_PROVENANCE=.*#REPO_COMMIT_PROVENANCE=ami-tag#" \
    "$PIN" > "$TMP" || die "핀 파일 갱신 실패"
grep -q "^REPO_COMMIT=$SHA$" "$TMP" || { rm -f "$TMP"; die "갱신된 핀에 새 커밋이 안 들어갔습니다 — 안 바꿉니다."; }
grep -q "^REPO_COMMIT_PROVENANCE=ami-tag$" "$TMP" || { rm -f "$TMP"; die "provenance 승격이 안 됐습니다 — 안 바꿉니다."; }
mv "$TMP" "$PIN" || die "핀 파일 rename 실패"
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
