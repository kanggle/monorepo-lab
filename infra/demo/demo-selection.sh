#!/usr/bin/env bash
# =============================================================================
# infra/demo/demo-selection.sh — **부팅 선택**을 읽는다 (TASK-MONO-634 / ADR-MONO-071)
# =============================================================================
# 방문자가 론처에서 «팬 플랫폼만» 을 고르면 그 선택은 **인스턴스가 꺼져 있는 동안** 살아
# 있어야 한다. 그러지 않으면 다음 부팅이 다시 `full` 로 뜨고, 선택은 아무 일도 안 한 것이
# 된다 — 이 티켓이 고치는 결함이 정확히 그 모양이다(systemd 유닛에 `DEMO_PROFILE=full` 이
# 상수로 박혀 있었다).
#
# -----------------------------------------------------------------------------
# 왜 SSM 파라미터인가 — 그리고 왜 인스턴스 안의 파일이 아닌가
# -----------------------------------------------------------------------------
# 선택을 쓰는 것은 **Lambda** 이고 읽는 것은 **인스턴스**다. 그 둘이 동시에 살아 있는
# 구간이 없다: 방문자가 «켜기» 를 누르는 순간 인스턴스는 `stopped` 이므로 SSM
# RunShellScript 로 파일을 쓸 수 없다(그 채널은 인스턴스가 떠 있어야 한다).
#
# ⇒ 쓰기는 인스턴스 **밖**에 있어야 하고, 읽기는 부팅 **중**이어야 한다. SSM Parameter
#   Store 는 이미 이 데모의 상태 채널이고(heartbeat·usage·health 가 전부 거기 있다),
#   인스턴스 역할에 그 읽기 권한이 이미 있다. 새 리소스를 만들지 않는다.
#
# 🔴 이 파일은 **AMI 에 구워진다.** `terraform apply` 로는 도달하지 않는다 —
#    고치면 재굽기가 필요하다(도달 경로 표: infra/demo/aws/README.md).
#
# -----------------------------------------------------------------------------
# 🔴🔴 «없다» 를 «전부» 로 읽지 않는다
# -----------------------------------------------------------------------------
# 파라미터가 없거나 깨졌을 때 무엇으로 뜰 것인가는 **결정**이다. 두 후보가 있었다:
#
#   (a) `full` 로 폴백  — 옛 동작과 같다. 그러나 방문자가 «팬만» 을 골랐는데 파라미터
#                        읽기가 한 번 실패하면 **8개 프로젝트 96 컨테이너가 뜬다.**
#                        비용·웜업 시간 모두 방문자의 의도와 정반대다.
#   (b) `demo-core` 로 폴백 — 더 작지만 여전히 «안 고른 것을 켠다».
#
# ⇒ 고른 것은 **(b)** 다. 이유: 폴백이 도는 상황은 «선택을 모른다» 이고, 그때 아무것도
#   안 띄우면 방문자는 빈 데모를 보며 원인을 알 수 없다(그리고 론처의 부팅 프로브가
#   iam 을 찌르므로 iam 은 어차피 필요하다). `demo-core` 는 이미 이 저장소가 «핵심 경로»
#   로 정의해 둔 집합이고, `full` 보다 **작다**. 안전한 쪽은 작은 쪽이다.
# 🔴 그리고 폴백이 도는 것을 **말한다** — 조용히 폴백하면 «선택이 저장되지 않는다» 는
#   결함이 영영 안 보인다.
#
# 사용법 (source 해서 쓴다):
#   source infra/demo/demo-selection.sh
#   mapfile -t BUNDLES < <(read_boot_selection)     # 실패해도 폴백 목록을 출력한다
# =============================================================================

# 🔵 파라미터 이름은 terraform(`local.selection_param`)·`handler.py`·여기 **세 곳**에 있다.
#    같은 사실이 세 곳에 있으면 한 곳만 고쳐진다 — 가드 (z33)이 셋을 대조한다.
#    (`HEALTH_PARAM` 이 이미 같은 모양이고 (z) 가 그것을 지킨다. 같은 규율을 쓴다.)
SELECTION_PARAM="${SELECTION_PARAM:-/portfolio-demo/boot-selection}"

# 파라미터를 못 읽었을 때 뜰 것 — § 위 결정. 🔴 `full` 이 **아니다.**
SELECTION_FALLBACK="demo-core"

# ---------------------------------------------------------------------------
# 리전 — 하드코딩하지 않는다. `demo-status-publish.sh` 의 `imds_region` 과 같은 방식이다.
# 🔴 사본이 두 벌이 되지 않게, 이미 정의돼 있으면 다시 정의하지 않는다.
# ---------------------------------------------------------------------------
if ! declare -F imds_region >/dev/null 2>&1; then
  imds_region() {
    local token region
    token="$(curl -sf --max-time 2 -X PUT http://169.254.169.254/latest/api/token \
               -H 'X-aws-ec2-metadata-token-ttl-seconds: 300' 2>/dev/null)" || return 1
    [ -n "$token" ] || return 1
    region="$(curl -sf --max-time 2 -H "X-aws-ec2-metadata-token: $token" \
                http://169.254.169.254/latest/meta-data/placement/region 2>/dev/null)" || return 1
    case "$region" in
      '' | *[!a-z0-9-]*) return 1 ;;
    esac
    printf '%s' "$region"
  }
fi

# ---------------------------------------------------------------------------
# read_boot_selection — 선택된 **묶음 이름**을 한 줄에 하나씩 출력한다.
#
# 🔴 이 함수는 **실패하지 않는다** — 부팅 경로에 있으므로 실패는 «아무것도 안 뜸» 이 된다.
#    대신 왜 폴백했는지를 stderr 로 **반드시** 말한다.
# 🔴 값 검증은 여기서 하지 않는다. 이름이 유효한 묶음인지는 `projects.sh` 의
#    `resolve_bundles` 가 판정하고, 그것이 유일한 판정자여야 한다(두 곳에서 검증하면
#    한쪽만 갱신된다). 여기서는 **셸 메타문자만** 거른다 — 이 값이 곧 명령줄이 되므로
#    그것은 판정이 아니라 **주입 방어**이고, 방어는 가장 바깥에 한 겹 더 있어야 한다.
# ---------------------------------------------------------------------------
read_boot_selection() {
  local region raw names

  if ! command -v aws >/dev/null 2>&1; then
    echo "[selection] aws CLI 가 없습니다 — '$SELECTION_FALLBACK' 로 폴백합니다." >&2
    printf '%s\n' "$SELECTION_FALLBACK"
    return 0
  fi

  region="${AWS_REGION:-${AWS_DEFAULT_REGION:-}}"
  if [ -z "$region" ]; then
    region="$(imds_region)" || {
      echo "[selection] 리전을 알 수 없습니다(EC2 밖이거나 IMDSv2 차단) — '$SELECTION_FALLBACK' 로 폴백합니다." >&2
      printf '%s\n' "$SELECTION_FALLBACK"
      return 0
    }
  fi

  raw="$(aws ssm get-parameter --region "$region" --name "$SELECTION_PARAM" \
           --query 'Parameter.Value' --output text 2>/dev/null)" || raw=""
  if [ -z "$raw" ] || [ "$raw" = "None" ]; then
    echo "[selection] $SELECTION_PARAM 이 비어 있습니다(최초 부팅이거나 아직 선택 없음) — '$SELECTION_FALLBACK'." >&2
    printf '%s\n' "$SELECTION_FALLBACK"
    return 0
  fi

  # 모양: {"bundles":["fan","console"],"updatedAt":1757203200}
  # 🔵 `jq` 를 쓰지 않는다 — AMI 에 있다는 보장이 없고, 없을 때 이 경로가 죽으면
  #    «선택이 무시된다» 가 된다. 값의 모양은 우리가 쓰는 것이므로 grep 으로 충분하다.
  names="$(printf '%s' "$raw" \
    | tr ',' '\n' \
    | sed -n 's/.*"\([a-z][a-z0-9-]*\)".*/\1/p' \
    | grep -v '^bundles$' \
    | grep -v '^updatedAt$' || true)"

  # 🔴 셸 메타문자 방어 — 위 sed 가 이미 `[a-z0-9-]` 로 좁혔지만, 그 정규식이 느슨해지는
  #    날 이 줄이 마지막 방어선이다. 「가드 한 겹」은 그 한 겹이 빠진 날 조용하다.
  names="$(printf '%s\n' "$names" | grep -E '^[a-z][a-z0-9-]{0,31}$' || true)"

  if [ -z "$names" ]; then
    echo "[selection] $SELECTION_PARAM 을 해석하지 못했습니다(값: ${raw:0:120}) — '$SELECTION_FALLBACK'." >&2
    printf '%s\n' "$SELECTION_FALLBACK"
    return 0
  fi

  printf '%s\n' "$names"
}
