#!/usr/bin/env bash
# =============================================================================
# infra/demo/demo-up.sh — 온디맨드 포트폴리오 데모 통합 기동 (TASK-MONO-336)
# =============================================================================
# 각 프로젝트를 자신의 "별도 compose 프로젝트"(-p <slug>)로 띄워 공유 external
# traefik-net 위에 올린다. 이렇게 하면 프로젝트들이 공유하는 제네릭 서비스 키
# (redis/kafka/postgres/mysql/grafana/notification-service …)가 프로젝트 네임스페이스로
# 분리되어 충돌하지 않는다.
#
# 왜 단일 include/-f 파일이 아닌가 (실측 근거):
#   docker compose 의 include: 와 -f 는 "같은 서비스 키"를 조용히 하나로 병합한다
#   (include=첫째 승, -f=마지막 승). 우리 8개 프로젝트는 서로 다른 컨테이너인데도
#   redis/kafka/postgres 같은 키를 공유하므로, 단일 병합 파일은 7개 redis 중 6개를
#   소리없이 잃는다. → 프로젝트당 별도 -p 만이 byte-unchanged 로 전부 살린다.
#
# 사용법:
#   bash infra/demo/demo-up.sh [demo-core|full]
#   DEMO_BUILD=1 bash infra/demo/demo-up.sh full   # 이미지 빌드(개발). AMI 는 prebaked → 미사용
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PROFILE="${1:-demo-core}"
BUILD="${DEMO_BUILD:-0}"

# 프로젝트 slug -> compose 파일(ROOT 상대)
declare -A COMPOSE=(
  [iam]="projects/iam-platform/docker-compose.yml"
  [ecommerce]="projects/ecommerce-microservices-platform/docker-compose.yml"
  [wms]="projects/wms-platform/docker-compose.yml"
  [scm]="projects/scm-platform/docker-compose.yml"
  [fan]="projects/fan-platform/docker-compose.yml"
  [finance]="projects/finance-platform/docker-compose.yml"
  [erp]="projects/erp-platform/docker-compose.yml"
  [console]="projects/platform-console/docker-compose.yml"
)

# 기동 순서: iam 먼저(모두가 OIDC 검증 대상인 IdP), console 마지막(federation 소비자)
FULL=(iam wms scm finance erp ecommerce fan console)
# demo-core: 면접 콜드스타트 최소화용 핵심 경로 (IdP + 풀스택 스토어프론트 + 도메인 1 + 콘솔)
# 콘솔은 core 에서 부분 federation(iam+wms), full 에서 5/5 로 렌더된다.
CORE=(iam ecommerce wms console)

case "$PROFILE" in
  full)      SET=("${FULL[@]}") ;;
  demo-core) SET=("${CORE[@]}") ;;
  *) echo "usage: demo-up.sh [demo-core|full]" >&2; exit 2 ;;
esac

build_flag=""
[ "$BUILD" = "1" ] && build_flag="--build"

echo "[demo] profile=$PROFILE  build=$BUILD"
echo "[demo] ensuring shared traefik-net + edge router"
docker compose -p traefik -f "$ROOT/infra/traefik/docker-compose.yml" up -d

for p in "${SET[@]}"; do
  echo "[demo] up: $p  (${COMPOSE[$p]})"
  # -f 를 ROOT 절대경로로 주면 project-directory 가 그 파일의 디렉터리로 잡혀
  # 각 프로젝트의 .env 로딩과 상대 build: 컨텍스트가 올바르게 해소된다.
  docker compose -p "$p" -f "$ROOT/${COMPOSE[$p]}" up -d $build_flag
done

echo "[demo] up complete — profile=$PROFILE"
echo "[demo] 호스트: console.local / web.ecommerce.local / <domain>.local (Traefik hostname routing)"
