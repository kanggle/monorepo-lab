#!/usr/bin/env bash
#
# ami-bundle-capability.sh — TASK-MONO-647
#
# 「배포된 AMI 가 **묶음 기동을 아는가**」 하나만 답한다.
#
# ---------------------------------------------------------------------------
# 왜 이 판정이 저장소에 있나 — 런타임은 이 사실을 볼 수 없다
# ---------------------------------------------------------------------------
# TASK-MONO-647 이 막으려는 결함은 「`main` 이 앞서 나갔고 AMI 는 안 구워졌다」이다.
# 그 사실의 한쪽 항(`main`)은 **런타임에 존재하지 않는다.** 그래서 Lambda 는 스스로
# 판정할 수 없고, 판정은 여기서 하고 `terraform apply` 가 값을 실어 나른다.
#
# 티켓 AC-1 이 남긴 두 후보를 실측해서 떨어뜨렸다 (2026-09-10):
#
#   ② 인스턴스에서 파일을 직접 grep
#      → **running 이어야 한다.** 이 가드의 목적이 「켜기 전에 막는다」이므로 못 쓴다.
#        (stopped 이면 SSM 관리 대상도 아니다 — deployed-ami.env 머리말의 실측)
#
#   ① Lambda 가 런타임에 AMI 태그 RepoCommit 을 읽는다
#      → **두 겹으로 막힌다.**
#        (a) Lambda IAM 에 `ec2:DescribeImages` 가 **없다**. main.tf 의 정책은
#            StartInstances / StopInstances / DescribeInstances 뿐이고,
#            DescribeInstances 는 ImageId 는 주지만 **그 이미지의 태그는 안 준다.**
#        (b) 태그를 읽어도 Lambda 는 **git 조상 판정을 할 수 없다.** 「이 커밋이 묶음을
#            아는가」는 커밋 그래프 질문이고, 그 그래프는 저장소에만 있다.
#
# ⇒ 그래서 «세대를 읽는다»를 저장소에서 계산해 apply 로 발행한다.
#
# 🔴 **이것은 하드코딩 스위치가 아니다** (AC-1 이 금지한 것). 값이
#    `deployed-ami.env` 의 REPO_COMMIT 에서 **유도**되고, 그 파일은 bake.sh 가 기계로
#    쓴다. 재굽기를 하면 ami_id 교체 apply 가 **어차피 필수**이고, 그 apply 가 이 값을
#    다시 계산해 싣는다 ⇒ **저절로 풀린다. 다시 고칠 코드가 없다.**
#
# ---------------------------------------------------------------------------
# 임계 커밋
# ---------------------------------------------------------------------------
# 9f0fcd2d6 = ADR-MONO-070/071 본체
#   feat(demo): 공개 열람을 백엔드 없이 성립시키고, 부팅이 방문자가 고른 묶음만 올린다 (#3681)
# 그 커밋이 건드린 **인스턴스 실행 파일**이 정확히 셋이다 (실측, --stat):
#   infra/demo/demo-boot.sh (+49) · infra/demo/demo-stack.service (+21) · infra/demo/projects.sh (+66)
# 즉 「묶음 기동을 안다」 = 「그 세 파일의 그 판을 갖고 있다」 = 「이 커밋의 자손이다」.
#
# 🔵 override 가 있는 이유는 (z40) 이 **자기 완결**이어야 하기 때문이다 (TASK-MONO-658).
#    그 칸은 임시 디렉터리에 저장소를 직접 만들어 조상 관계를 세우고, 그 저장소의 커밋을
#    임계값으로 준다 ⇒ **이 저장소의 이력을 한 번도 안 읽는다.** 그래야 얕은 클론
#    (packer 의 `git clone --depth 1`, nightly 의 기본 체크아웃)에서도 판정이 성립한다.
#    🔴 운영 경로는 override 를 **안 쓴다** — 아래 기본값이 그 값이다.
BUNDLE_CAPABILITY_COMMIT="${AMI_BUNDLE_MIN_COMMIT:-9f0fcd2d6df70b3dee847163c82d4159cbe9d125}"
#
# ---------------------------------------------------------------------------
# 출력
# ---------------------------------------------------------------------------
#   --json  terraform `external` data source 용. **평평한 문자열 객체**여야 한다.
#   (기본)  사람용 한 줄 + rc (0=capable, 1=not capable, 2=unknown)
#
# 🔴🔴 **이 스크립트는 --json 모드에서 절대 실패하지 않는다.** terraform 의 external
#    data source 는 프로그램이 죽으면 **plan 자체를 죽인다.** 즉 여기서 실패하면
#    「묶음 기동을 못 한다」가 아니라 「아무것도 배포 못 한다」가 된다. 알 수 없으면
#    unknown 을 **성공적으로** 내고, 그 unknown 을 Lambda 가 거절로 읽는다.
#
# 🔵 AWS 를 부르지 않는다 (AC-3: 가드가 자격증명을 요구하면 CI 에서 영구 빨강이거나
#    영구 skip 이 된다). 읽는 것은 저장소 파일 하나와 git 그래프뿐이다.
#
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PIN="${AMI_PIN_FILE:-$HERE/deployed-ami.env}"

# 🔴 커밋 그래프를 어느 저장소에서 읽는가. 기본은 **이 스크립트가 있는 자리**이고, 그것이
#    운영 경로다(terraform 이 저장소 안의 이 파일을 부른다).
# 🔵 override 가 있는 이유는 (z40) 의 bite 때문이다: 세대 판정을 무력화한 변형본을 만들려면
#    스크립트를 복사해야 하는데, 복사본은 저장소 밖이라 기본값으로는 «git 저장소 밖»
#    으로 떨어져 **판정이 아니라 위치를 재게 된다.** 그 상태에서도 결과는 unknown(=거절)
#    이라 안전하지만, 안전한 것과 **시험 가능한 것**은 다르다.
#    🔴 실제로 (z40) bite-1 이 이것을 물어서 알았다 — 가드가 먼저 자기 대상을 고쳤다.
GITROOT="${AMI_GIT_ROOT:-$HERE}"

MODE="human"
[ "${1:-}" = "--json" ] && MODE="json"

capable="unknown"
reason=""
repo_commit=""

if [ ! -f "$PIN" ]; then
    reason="핀 파일이 없습니다: $PIN"
else
    # 🔴 source 하지 않는다 — 이 파일은 주석이 본문보다 길고, 언젠가 명령이 들어가면
    #    terraform plan 이 그것을 실행하게 된다. 필요한 한 줄만 뽑는다.
    repo_commit="$(sed -n 's/^REPO_COMMIT=\([0-9a-f]\{7,40\}\)[[:space:]]*$/\1/p' "$PIN" | head -1)"
    if [ -z "$repo_commit" ]; then
        reason="deployed-ami.env 에 REPO_COMMIT 이 없습니다"
    elif ! git -C "$GITROOT" rev-parse --git-dir >/dev/null 2>&1; then
        reason="git 저장소 밖에서 실행됐습니다"
    elif ! git -C "$GITROOT" cat-file -e "${repo_commit}^{commit}" 2>/dev/null; then
        # 얕은 클론이거나 그 커밋이 아직 안 받아졌다. 🔴 「모르면 허용」으로 가지 않는다.
        reason="구운 커밋 ${repo_commit:0:9} 가 이 클론에 없습니다(얕은 클론?)"
    elif ! git -C "$GITROOT" cat-file -e "${BUNDLE_CAPABILITY_COMMIT}^{commit}" 2>/dev/null; then
        reason="임계 커밋 ${BUNDLE_CAPABILITY_COMMIT:0:9} 가 이 클론에 없습니다"
    elif git -C "$GITROOT" merge-base --is-ancestor "$BUNDLE_CAPABILITY_COMMIT" "$repo_commit" 2>/dev/null; then
        capable="yes"
        reason="구운 커밋 ${repo_commit:0:9} 가 ${BUNDLE_CAPABILITY_COMMIT:0:9} 의 자손입니다"
    else
        capable="no"
        reason="구운 커밋 ${repo_commit:0:9} 가 ${BUNDLE_CAPABILITY_COMMIT:0:9} 보다 앞섭니다 — 그 AMI 는 묶음 기동을 모릅니다"
    fi
fi

if [ "$MODE" = "json" ]; then
    # terraform external: 평평한 string 맵. 개행·따옴표를 넣지 않는다.
    printf '{"capable":"%s","repo_commit":"%s","min_commit":"%s","reason":"%s"}\n' \
        "$capable" "$repo_commit" "$BUNDLE_CAPABILITY_COMMIT" "${reason//\"/}"
    exit 0
fi

printf '[ami-bundle] capable=%s  구운커밋=%s  임계=%s\n' \
    "$capable" "${repo_commit:0:9}" "${BUNDLE_CAPABILITY_COMMIT:0:9}"
printf '[ami-bundle] %s\n' "$reason"
case "$capable" in
    yes) exit 0 ;;
    no)  exit 1 ;;
    *)   exit 2 ;;
esac
