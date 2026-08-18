#!/usr/bin/env bash
# =============================================================================
# infra/demo/demo-status-publish.sh — 헬스 스냅샷을 SSM 에 발행한다 (TASK-MONO-477)
# =============================================================================
# `demo-status.sh` 가 만든 도메인별 헬스 JSON 을 SSM 파라미터에 쓴다.
# `demo-status.timer` 가 30초마다 이 스크립트를 부르고, 컨트롤 플레인 Lambda 의
# `GET /domains` 는 그 파라미터를 **읽기만** 한다.
#
# 왜 발행자가 따로 있는가 (TASK-MONO-477 § 어려운 부분 1)
# -----------------------------------------------------------------------------
# 페이지가 도메인 배지를 갱신하려면 헬스를 알아야 하는데, SSM SendCommand 는
# **비동기**다. 매 폴링마다 SendCommand → GetCommandInvocation 을 도는 것은 느리고
# 취약하다(명령 ID 추적, 완료 대기, 실패 재시도). 그래서 방향을 뒤집는다 —
# **인스턴스가 주기적으로 밀고, Lambda 는 당기지 않는다.**
#
# 🔴 이 파일은 AMI 에 구워진다. `terraform apply` 로는 도달하지 않는다.
#    (도달 경로 표: infra/demo/aws/README.md / TASK-MONO-399 § "고쳤는데 데모에는
#     도달하지 않았다") — 고치면 재굽기가 필요하다.
#
# 실패했을 때 무엇을 쓰는가 — **빈 오브젝트를 쓴다**
# -----------------------------------------------------------------------------
# 스냅샷을 못 만들었으면 **직전 값을 그대로 두지 않는다.** 그대로 두면 파라미터는
# 마지막으로 성공한 "전부 up" 을 영원히 들고 있고, 페이지는 죽은 도메인을 초록으로
# 그린다 — 이 티켓의 Failure Scenario 가 명시적으로 금지하는 fail-open 이다.
#
# `{}` 를 쓰면 `handler.py:domains()` 가 빈 dict 로 파싱하고 페이지는 그 도메인을
# **"확인 중"** 으로 그린다(= 모른다고 말한다). 이미 있는 소비자 동작을 그대로 쓴다.
#
# ⚠️ 반대로 **발행 자체가 실패하면**(자격증명·네트워크·스로틀) 우리가 할 수 있는 게
#    없다. 조용히 성공한 척하지 않고 non-zero 로 죽는다 — journald 에 남고 타이머가
#    30초 뒤 다시 시도한다. `systemctl status demo-status.timer` 로 보인다.
#
# 사용법:
#   bash infra/demo/demo-status-publish.sh          # 파라미터명은 아래 기본값
#   HEALTH_PARAM=/foo/bar bash …demo-status-publish.sh
# =============================================================================
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# -----------------------------------------------------------------------------
# 파라미터 이름 — 이 리터럴은 세 곳에 있다
# -----------------------------------------------------------------------------
# terraform `local.health_param` = "/${var.project}/domains-health" (project 기본값
# portfolio-demo) · `handler.py` 의 HEALTH_PARAM 환경변수 기본값 · 그리고 여기.
# 한 사실이 세 곳에 있으면 한 곳만 고쳐진다 — 그래서 가드 (z) 가 셋을 대조한다.
# 운영상으로는 유닛이 `Environment=HEALTH_PARAM=` 로 덮어쓸 수 있게 열어 둔다.
HEALTH_PARAM="${HEALTH_PARAM:-/portfolio-demo/domains-health}"

# SSM Standard 티어 상한. 넘으면 자르지 않고 실패한다 — 잘린 JSON 은 파싱에 실패해
# `{}` 로 읽히고, 그건 "발행이 깨졌다" 가 아니라 "도메인이 없다" 처럼 보인다.
MAX_BYTES=4096

# -----------------------------------------------------------------------------
# 리전 — 하드코딩하지 않는다
# -----------------------------------------------------------------------------
# 이 AMI 는 리전에 묶이지 않는다(다른 리전에서 복원될 수 있다). IMDSv2 로 자기
# 리전을 묻는다. demo-boot.sh 의 도메인 파생과 같은 이유·같은 방식이다 —
# 토큰 없이 치면 401 이고, 그 401 본문을 리전으로 쓰면 put-parameter 가 엉뚱한
# 곳을 향한다.
imds_region() {
  local token region
  token="$(curl -sf --max-time 2 -X PUT http://169.254.169.254/latest/api/token \
             -H 'X-aws-ec2-metadata-token-ttl-seconds: 300' 2>/dev/null)" || return 1
  [ -n "$token" ] || return 1
  region="$(curl -sf --max-time 2 -H "X-aws-ec2-metadata-token: $token" \
              http://169.254.169.254/latest/meta-data/placement/region 2>/dev/null)" || return 1
  # 형태를 믿지 않는다. 메타데이터가 에러 문서를 주면 여기서 걸러진다.
  case "$region" in
    '' | *[!a-z0-9-]*) return 1 ;;
  esac
  printf '%s' "$region"
}

REGION="${AWS_REGION:-${AWS_DEFAULT_REGION:-}}"
if [ -z "$REGION" ]; then
  REGION="$(imds_region)" || {
    echo "[status-publish] FATAL: 리전을 알 수 없습니다 (AWS_REGION 미설정 + IMDSv2 응답 없음)." >&2
    echo "[status-publish]        EC2 밖에서 실행 중이거나 메타데이터가 막혀 있습니다." >&2
    exit 1
  }
fi

# -----------------------------------------------------------------------------
# aws CLI 부재는 조용히 넘어가지 않는다
# -----------------------------------------------------------------------------
# AMI 빌드가 CLI 설치를 놓치면 이 스크립트는 매 30초 죽고, 페이지는 영원히
# "확인 중" 을 보여준다 — 원인이 페이지 쪽에 있는 것처럼 보인다. 여기서 이름을 댄다.
command -v aws >/dev/null 2>&1 || {
  echo "[status-publish] FATAL: aws CLI 가 없습니다 — AMI 빌드가 설치하지 않았습니다." >&2
  echo "[status-publish]        packer 템플릿 1단계의 awscli 설치를 확인하세요." >&2
  exit 1
}

# -----------------------------------------------------------------------------
# 스냅샷 생산 → 검증 → 발행
# -----------------------------------------------------------------------------
# `|| snapshot=''` 이 없으면 set -e 가 여기서 스크립트를 죽여 아래의 fail-closed
# 경로(빈 오브젝트 발행)에 **도달하지 못한다**. 실패를 삼키는 게 아니라, 실패를
# 정직한 값으로 번역하기 위해 잡는다.
snapshot="$(bash "$HERE/demo-status.sh" 2>/dev/null)" || snapshot=''

# 모양을 믿지 않고 본다. 부분 출력·경고 섞임·과대 크기는 전부 "모른다"로 떨어뜨린다.
valid=1
case "$snapshot" in
  '{'*'}') : ;;
  *) valid=0 ;;
esac
[ "${#snapshot}" -le "$MAX_BYTES" ] || valid=0

if [ "$valid" -ne 1 ]; then
  echo "[status-publish] WARN: 스냅샷이 유효하지 않습니다 (${#snapshot} bytes) — {} 로 발행합니다." >&2
  snapshot='{}'
fi

# -----------------------------------------------------------------------------
# 발행 시각을 함께 싣는다 — TASK-MONO-551 결함 B
# -----------------------------------------------------------------------------
# 발행자가 죽어도 SSM 파라미터는 **마지막 값 그대로 남는다.** Lambda 는 그것을 읽어
# 타임스탬프 **없이** 반환했으므로 *"방금 잰 값"* 과 *"13분 전 값"* 이 **바이트 단위로
# 구별 불가**였다. 2026-08-17 실측: 파라미터가 Version 91(21:40:35Z)에서 멈춘 채
# 21:53:25Z 에 조회됐고 — **12.8분 묵은 스냅샷** — `99/102 정상` 으로 읽혔다.
# 그 순간 박스는 15분째 무응답이었고 HTTP 는 전멸이었다.
#
# 그래서 값의 **모양을 바꾼다**:
#     이전:  {"iam":{...}, "wms":{...}, ...}
#     이후:  {"published_at": <epoch UTC>, "domains": {"iam":{...}, ...}}
#
# 🔴 이 모양 변경 자체가 신선도 계약이다. `published_at` 이 **없는** 값은 — 구 AMI 가 쓴
#    평평한 스냅샷이든 terraform 이 미리 만들어 둔 `{}` 든 — 소비자가 **stale 로 읽어야
#    한다.** *부재를 신선함으로 읽지 않는다* 가 이 결함의 핵심 축이다.
#    (`handler.py:domains()` 가 두 모양을 다 파싱하고, 옛 모양은 stale 로 떨어뜨린다.)
#
# 시각은 **발행 시점**에 찍는다(스냅샷 생산 시점이 아니라). 소비자가 묻는 것은 "이 값이
# 파라미터에 언제 놓였는가" 이기 때문이다. `date -u +%s` = UTC epoch — 호스트 로컬 TZ 와
# 무관하다(이 저장소는 KST 호스트/UTC 러너 불일치로 이미 데였다).
published_at="$(date -u +%s)"
snapshot="{\"published_at\":${published_at},\"domains\":${snapshot}}"

# 크기 검사는 **감싼 뒤 다시** 한다. 감싸기가 40바이트쯤 더하므로 감싸기 전 값으로만 재면
# 상한 근처에서 잘린 JSON 을 발행하게 된다 — 그리고 잘린 JSON 은 "발행이 깨졌다" 가 아니라
# "도메인이 없다" 처럼 보인다(이 파일 머리가 이미 경고하는 실패 모드다).
if [ "${#snapshot}" -gt "$MAX_BYTES" ]; then
  echo "[status-publish] WARN: 감싼 값이 ${#snapshot} bytes 로 상한(${MAX_BYTES})을 넘습니다 — 빈 도메인으로 발행합니다." >&2
  snapshot="{\"published_at\":${published_at},\"domains\":{}}"
fi

aws ssm put-parameter \
  --region "$REGION" \
  --name "$HEALTH_PARAM" \
  --type String \
  --overwrite \
  --value "$snapshot" \
  >/dev/null

echo "[status-publish] $HEALTH_PARAM ($REGION) ← ${#snapshot} bytes"
