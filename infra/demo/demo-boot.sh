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

# `demo-up.sh` 는 `demo.env` 를 스스로 source 한다. 거기서 `DEMO_DOMAIN` 은 반드시
# `${DEMO_DOMAIN:-local}` 형태여야 한다 — bare 대입이면 `set -a; source` 가 **여기서
# export 한 값을 덮어쓴다**(358 에서 실제로 당했다). 가드 (n) 이 그 형태를 지킨다.
exec bash "$HERE/demo-up.sh" "$@"
