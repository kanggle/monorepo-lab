#!/usr/bin/env bash
# =============================================================================
# infra/demo/demo-up.sh — 온디맨드 포트폴리오 데모 통합 기동 (TASK-MONO-336/344)
# =============================================================================
# 각 프로젝트를 자신의 "별도 compose 프로젝트"(-p <slug>)로 띄워 공유 external
# traefik-net 위에 올린다. 프로젝트들이 공유하는 제네릭 서비스 키
# (redis/kafka/postgres/mysql/grafana/notification-service …)가 프로젝트
# 네임스페이스로 분리되어 충돌하지 않는다.
#
# 왜 단일 include/-f 파일이 아닌가 (실측 근거):
#   docker compose 의 include: 와 -f 는 "같은 서비스 키"를 조용히 병합한다
#   (include=첫째 승, -f=마지막 승). 8개 프로젝트는 서로 다른 컨테이너인데도
#   redis/kafka/postgres 같은 키를 공유하므로, 단일 병합 파일은 7개 redis 중
#   6개를 소리없이 잃는다. → 프로젝트당 별도 -p 만이 전부 살린다.
#
# 프로젝트당 compose 파일이 여러 개일 수 있다 (projects.sh 참조):
#   iam / wms 는 base(인프라) + docker-compose.e2e.yml(앱) 을 함께 줘야 앱이 뜬다.
#
# 사전 요구 (MONO-342):
#   Java 서비스 Dockerfile 은 `COPY build/libs/<svc>.jar` 다 — 도커 안에서
#   컴파일하지 않는다. DEMO_BUILD=1 로 빌드하려면 먼저
#     ./gradlew <각 서비스>:bootJar   +  monorepo/java-service-base:v1 이미지
#   가 준비돼 있어야 한다. 데모 호스트 AMI 는 이를 prebake 한다.
#
# 사용법:
#   bash infra/demo/demo-up.sh [demo-core|full]
#   bash infra/demo/demo-up.sh <domain...>        # 예: iam fan console (하드 의존 자동 포함)
#   DEMO_BUILD=1 bash infra/demo/demo-up.sh full
#
# 도메인 리스트 모드 (TASK-MONO-477): 임의 도메인을 골라 부분 기동한다. projects.sh 의
# resolve_deps 가 하드 의존(전원→iam)을 자동 포함하고 FULL 순서로 정렬하므로, `console`
# 하나만 줘도 iam 이 함께 뜬다(없으면 로그인 불가 — MONO-358).
# =============================================================================
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
# shellcheck source=infra/demo/projects.sh
source "$HERE/projects.sh"

# 데모 전용 cross-project env (redis 무비밀번호, wms→iam OIDC, 스텁 URL …)
# shellcheck source=infra/demo/demo.env
set -a; source "$HERE/demo.env"; set +a

BUILD="${DEMO_BUILD:-0}"

# 인자 해석: 프로파일 키워드(full|demo-core) 또는 임의 도메인 리스트(TASK-MONO-477).
if [ "$#" -eq 0 ]; then
  PROFILE="demo-core"; SET=("${CORE[@]}")
elif [ "$#" -eq 1 ] && [ "$1" = "full" ]; then
  PROFILE="full"; SET=("${FULL[@]}")
elif [ "$#" -eq 1 ] && [ "$1" = "demo-core" ]; then
  PROFILE="demo-core"; SET=("${CORE[@]}")
else
  # 도메인 리스트 — 하드 의존을 자동 포함하고 FULL 순서로 정렬한다.
  # (resolve_deps 의 exit code 를 잡아야 미지 도메인이 조용히 무시되지 않는다 —
  #  process substitution 은 exit code 를 전파하지 않으므로 command substitution 을 쓴다.)
  if ! RESOLVED="$(resolve_deps "$@")"; then
    echo "usage: demo-up.sh [demo-core|full|<domain...>]  (유효 도메인: ${!COMPOSE[*]})" >&2
    exit 2
  fi
  mapfile -t SET <<<"$RESOLVED"
  PROFILE="domains:$*"
  echo "[demo] 요청: $*  →  기동 대상(하드 의존 포함, 순서화): ${SET[*]}"
fi

build_flag=""
[ "$BUILD" = "1" ] && build_flag="--build"

echo "[demo] profile=$PROFILE  build=$BUILD"
echo "[demo] ensuring shared traefik-net + edge router"
docker compose -p traefik -f "$ROOT/$TRAEFIK_COMPOSE" up -d

for p in "${SET[@]}"; do
  mapfile -t ARGS < <(compose_args "$p")
  echo "[demo] up: $p  (${COMPOSE[$p]})"
  # -f 를 ROOT 절대경로로 주면 project-directory 가 첫 파일의 디렉터리로 잡혀
  # 각 프로젝트의 .env 로딩과 상대 build: 컨텍스트가 올바르게 해소된다.
  docker compose -p "$p" "${ARGS[@]}" up -d $build_flag
done

# OIDC 클라이언트의 redirect_uri 는 마이그레이션에 `.local` 로 박혀 있다. 데모 도메인은
# 부팅 때 정해지므로 마이그레이션이 알 수 없다 → 여기서 등록한다. DEMO_DOMAIN=local 이면
# no-op. (자세한 근거는 seed-demo-domain.sh 헤더. 가드 (k) 가 이 호출을 지킨다.)
if [[ " ${SET[*]} " == *" iam "* ]]; then
  bash "$HERE/seed-demo-domain.sh"
fi

echo "[demo] up complete — profile=$PROFILE"
echo "[demo] 호스트: console.${DEMO_DOMAIN} / web.ecommerce.${DEMO_DOMAIN} / wms.${DEMO_DOMAIN} / <domain>.${DEMO_DOMAIN} (Traefik)"
