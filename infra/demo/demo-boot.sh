#!/usr/bin/env bash
# =============================================================================
# infra/demo/demo-boot.sh — 부팅 진입점: 도메인을 파생하고 데모를 올린다
# =============================================================================
# TASK-MONO-366.
#
# 왜 이 파일이 따로 있는가
# -----------------------------------------------------------------------------
# `TASK-MONO-358` 은 저장소 쪽 계약을 이행했다 — **`DEMO_DOMAIN` 을 주면 그 도메인으로
# 뜨고 로그인까지 된다.** 그런데 **부팅 자동화가 그 계약을 쓰지 않았다.** systemd 유닛이
# `demo-up.sh` 를 직접 불렀고 `DEMO_DOMAIN` 은 어디에도 없었다:
#
#   ExecStart=/usr/bin/bash /opt/monorepo-lab/infra/demo/demo-up.sh ${DEMO_PROFILE}
#
# → `demo.env` 의 기본값 `local` 이 먹는다 → 라우터가 전부 `Host(`x.local`)` → 방문자
# 브라우저는 `Host: <공인IP>` 를 보내므로 **전 도메인 404**. 358 의 로그인 증명은 매번
# SSM 으로 들어가 손으로 재기동해서 얻은 것이고, **자동 경로는 한 번도 동작한 적이 없다.**
#
# 도메인 파생은 **부팅 전용 관심사**다(인스턴스 메타데이터). 그걸 `demo-up.sh` 에 섞으면
# 로컬 개발자의 래퍼가 AWS 를 알게 된다. 그래서 얇은 진입점을 하나 둔다:
#
#   systemd → demo-boot.sh → (DEMO_DOMAIN 파생) → demo-up.sh <profile>
#
# 사용법:
#   bash infra/demo/demo-boot.sh [demo-core|full|<domain...>]
#   DEMO_DOMAIN=1-2-3-4.sslip.io bash infra/demo/demo-boot.sh full   # 파생 건너뜀
#
# 인자를 **그대로 demo-up.sh 로 전달**한다(TASK-MONO-477). 그래서 컨트롤 플레인이
# SSM 으로 `demo-boot.sh fan console` 을 부르면, 도메인 파생이 여기서 일어난 뒤
# demo-up.sh 가 그 도메인들을 올린다 — per-domain 기동도 올바른 DEMO_DOMAIN 을 얻는다.
# =============================================================================
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# 무인자 부팅은 full (systemd 유닛이 DEMO_PROFILE=full 을 넘기지만 안전망으로 유지).
[ "$#" -eq 0 ] && set -- full
PROFILE="$*"

# -----------------------------------------------------------------------------
# 도메인 파생
# -----------------------------------------------------------------------------
# 인스턴스는 재시작마다 공인 IP 가 바뀐다(EIP 없음 — 정지 중에도 과금되므로 의도적으로
# 안 붙였다). 따라서 도메인은 **매 부팅** 다시 파생돼야 한다. IMDSv2 는 토큰 필수다 —
# 토큰 없이 `curl 169.254.169.254` 를 치면 401 이고, 그 401 본문을 그대로 쓰면
# `DEMO_DOMAIN` 이 쓰레기가 된다.
#
# `<a-b-c-d>.sslip.io` → `a.b.c.d` 로 해석되는 공개 와일드카드 DNS(도메인 구매·DNS
# 설정·비용 0). **하이픈 표기**를 쓴다 — `web.ecommerce.${DEMO_DOMAIN}` 처럼 이미 2단인
# 호스트명과 합쳐지므로 점 표기는 레이블이 불필요하게 길어진다.
#
# ⚠️ **빈 문자열이 가장 위험하다.** `DEMO_DOMAIN=""` 이면 라우터는 `Host(`console.`)` 가
# 되는데, **Traefik 은 이걸 거부하지 않는다** — 그냥 아무 요청과도 매치하지 않는다.
# 에러 로그 0건, 컨테이너 전부 healthy, 그런데 404. 358 이 내내 싸운 그 모양이다.
# 그래서 파생 실패는 **반드시 `local` 로 떨어지고, 그 사실을 말한다.**
derive_domain() {
  local token ip
  # -f: HTTP 에러를 본문으로 삼지 않는다. --max-time: EC2 밖에서는 링크로컬 주소가
  # 응답하지 않으므로 (라우팅 블랙홀) 짧게 끊는다 — 이게 없으면 로컬 실행이 멈춘다.
  token="$(curl -sf --max-time 2 -X PUT http://169.254.169.254/latest/api/token \
             -H 'X-aws-ec2-metadata-token-ttl-seconds: 300' 2>/dev/null)" || return 1
  [ -n "$token" ] || return 1

  ip="$(curl -sf --max-time 2 -H "X-aws-ec2-metadata-token: $token" \
          http://169.254.169.254/latest/meta-data/public-ipv4 2>/dev/null)" || return 1

  # 형태를 믿지 않고 검사한다. 메타데이터가 빈 값이나 에러 문서를 주면 여기서 걸러진다.
  case "$ip" in
    *[!0-9.]* | '' ) return 1 ;;
  esac
  printf '%s.sslip.io' "$(printf '%s' "$ip" | tr '.' '-')"
}

if [ -n "${DEMO_DOMAIN:-}" ]; then
  echo "[boot] DEMO_DOMAIN 이 이미 주어졌다 — 파생 건너뜀: $DEMO_DOMAIN"
elif DERIVED="$(derive_domain)"; then
  export DEMO_DOMAIN="$DERIVED"
  echo "[boot] IMDSv2 로 도메인 파생: $DEMO_DOMAIN"
else
  # AWS 밖(로컬 개발자가 실수로 실행)이거나 메타데이터가 응답하지 않는 경우.
  # 조용히 빈 값이 되게 두지 않는다 — 위 주석의 이유로 그건 진단 불가능한 404 가 된다.
  export DEMO_DOMAIN="local"
  echo "[boot] 인스턴스 메타데이터에 도달하지 못함 — DEMO_DOMAIN=local 로 폴백" >&2
  echo "[boot] (AWS 밖에서는 정상이다. EC2 데모 호스트에서 이 줄이 보이면 IMDSv2 를 확인하라.)" >&2
fi

echo "[boot] profile=$PROFILE  DEMO_DOMAIN=$DEMO_DOMAIN"

# 🔴 `.env` 프로비저닝은 `demo-up.sh` **앞**이어야 한다 (TASK-MONO-550).
# -----------------------------------------------------------------------------
# `demo-up.sh` 는 `check-env-preflight.sh`(MONO-548)를 부르고, 그 가드는 `.env` 가 없으면
# wms·ecommerce 의 기동을 **중단**한다 — compose 폴백 자격이 데이터 볼륨에 각인되기
# 때문이다. AMI 는 fresh clone 이고 `.env` 는 gitignored 라 존재할 수 없으므로,
# 이 줄이 없으면 부팅은 **컨테이너 0개로 조용히 끝난다**(2026-08-17 실측: 9개 도메인
# 전부 `total:0`, 에러는 systemd 저널에만).
#
# 순서가 load-bearing 이다. 나중에 만들면 preflight 은 이미 지나갔고, 더 나쁘게는
# 볼륨이 폴백 자격으로 초기화된 **뒤에** 의도한 값이 나타나 앱이 영구히 인증 실패한다
# — 가드가 막으려던 바로 그 상태다.
bash "$HERE/provision-demo-env.sh"

# `demo-up.sh` 는 `demo.env` 를 스스로 source 한다. 거기서 `DEMO_DOMAIN` 은 반드시
# `${DEMO_DOMAIN:-local}` 형태여야 한다 — bare 대입이면 `set -a; source` 가 **여기서
# export 한 값을 덮어쓴다**(358 에서 실제로 당했다). 가드 (n) 이 그 형태를 지킨다.
# -----------------------------------------------------------------------------
# 🔴🔴 TASK-MONO-615 B4 — 부팅 경합: up 전에 잔존 스택을 내린다 (후보 ⓐ)
# -----------------------------------------------------------------------------
# 무엇이 일어나고 있었나 (2026-09-03 기동 창, 손대지 않은 판으로 **부팅 2회 전부 재현**):
#
#   dockerd 가 뜨면 `restart: unless-stopped` 컨테이너 103개가 **한꺼번에** 살아난다
#   → 1분 안에 loadavg 104 (boot #1) / 191 (boot #2)
#   → `iam-kafka` 의 healthcheck 는 `kafka-broker-api-versions.sh`, 즉 **JVM 기동**이다.
#     평소 1.5~3.3s 인 그 프로브가 이 부하에서 도커의 `timeout: 10s` 를 넘겨 죽는다
#     (실측 health log: `exit=137`(SIGKILL) 과 `exit=-1` 이 10.7 / 11.6 / 13.2 / 11.0s)
#   → `docker compose up -d` 는 `depends_on: condition: service_healthy` 에서 그것을
#     «unhealthy» 로 읽고 포기한다
#   → 라벨에 DEMO_DOMAIN 이 박힌 서비스는 매 부팅 recreate 대상이므로, 그 셋이
#     **`Created` 상태로 남는다**: iam-auth-service-1 · iam-gateway-service-1 · iam-kafka-ui
#
# 🔴 **브로커는 멀쩡했다.** 같은 시각 로그는 컨슈머 그룹 리밸런스를 정상 처리하고 있다.
#    실패한 것은 브로커가 아니라 **프로브**다. 그래서 «의존 대기 조건을 조정»(후보 ⓑ)은
#    레버가 어긋나 있다 — 넓혀야 할 것은 compose 의 대기가 아니라 프로브가 끝날 여유이고,
#    그 여유를 만드는 가장 싼 방법은 **폭풍 자체를 없애는 것**이다.
#
# ⓐ 를 고른 이유 (실측 비교, 같은 인스턴스·연속 4회 부팅):
#
#   | 부팅 | 판 | iam | 남은 Created | 유닛 |
#   |---|---|---|---|---|
#   | #1 | 손대지 않음 | 98s 뒤 실패 → 포기 | 3 | timeout(SIGTERM) |
#   | #2 | 손대지 않음 | 210s 뒤 실패 → 포기 | 3 | timeout(SIGTERM) |
#   | #3 | ⓐ | **10s 만에 성공, 재시도 0** | **0** | 정상 종료 |
#   | #4 | ⓐ | **성공** | **0** | 정상 종료 |
#
# 🔵 ⓒ(restart 정책)·ⓓ(라벨에서 도메인 제거)를 고르지 않은 이유: 둘 다 8개 프로젝트의
#    compose 를 건드려 **로컬·CI 의 기동 의미까지 바꾼다**. 여기서 필요한 것은
#    «데모 호스트의 부팅» 이라는 한 상황이고, ⓐ 는 그 한 자리에만 산다.
#
# 🔴 ⓐ 가 공짜는 아니다. down 단계 자체가 실측 184s 를 먹고 그만큼 뒤가 밀린다 —
#    그래서 이 PR 은 `demo-stack.service` 의 TimeoutStartSec 과 demo-up.sh 의 단계별
#    예산을 **함께** 재산정한다. 안 하면 ⓐ 는 경합을 고치면서 요약을 다시 잃게 만든다.
#
# 🔴 `DEMO_BOOT_RESET` 이 없으면 **아무것도 하지 않는다.** 이 스크립트는 컨트롤 플레인의
#    per-domain 기동 경로(`demo-boot.sh fan`, 화이트리스트에 `full` 도 포함)에서도 불리고,
#    거기서 전체 down 은 방문자가 보고 있는 데모를 내리는 것이 된다. 부팅인지 아닌지를
#    아는 것은 systemd 유닛뿐이라 플래그가 거기서 온다. 가드 (z24)가 그 쌍을 묶는다.
if [ "${DEMO_BOOT_RESET:-0}" = "1" ]; then
  echo "[boot] 잔존 스택 정리 (DEMO_BOOT_RESET=1) — dockerd 가 되살린 컨테이너를 먼저 내립니다"
  # 🔴 볼륨은 건드리지 않는다. `demo-down.sh` 는 `down --remove-orphans` 이고 `-v` 가
  #    없다 — 데이터가 사라지면 이것은 고침이 아니라 파괴다.
  # 🔴 down 도 매달릴 수 있다. up 과 같은 이유로 시간으로 묶고, 못 묶으면 말한다.
  if command -v timeout >/dev/null 2>&1; then
    timeout --foreground -k 15 "${DEMO_DOWN_BUDGET:-300}" bash "$HERE/demo-down.sh" || {
      down_rc=$?
      if [ "$down_rc" -eq 124 ]; then
        echo "[boot] ⏱ 잔존 스택 정리가 ${DEMO_DOWN_BUDGET:-300}s 안에 끝나지 않아 **끊었습니다** — 그대로 기동을 계속합니다" >&2
      else
        echo "[boot] ⚠ 잔존 스택 정리가 rc=$down_rc 로 끝났습니다 — 그대로 기동을 계속합니다" >&2
      fi
    }
  else
    echo "[boot] ⚠ coreutils 'timeout' 이 없습니다 — 잔존 스택 정리를 시간으로 묶지 못합니다" >&2
    bash "$HERE/demo-down.sh" || echo "[boot] ⚠ 잔존 스택 정리 실패 — 그대로 기동을 계속합니다" >&2
  fi
else
  echo "[boot] 잔존 스택 정리 건너뜀 (DEMO_BOOT_RESET 미설정 — 부팅이 아닌 호출입니다)"
fi

exec bash "$HERE/demo-up.sh" "$@"
