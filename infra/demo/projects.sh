#!/usr/bin/env bash
# =============================================================================
# infra/demo/projects.sh — 통합 데모 프로젝트 맵 (단일 출처, TASK-MONO-341)
# =============================================================================
# demo-up.sh / demo-down.sh / verify-demo-wrapper.sh 가 공통으로 source 한다.
# 맵을 여기 한 곳에만 두어 드리프트를 원천 차단한다.
#
# 신규 프로젝트를 추가하면 반드시 COMPOSE + FULL + DOWN_ORDER 를 갱신할 것.
# 누락 시 verify-demo-wrapper.sh 의 커버리지 가드가 CI 에서 FAIL 한다.
# =============================================================================

# 프로젝트 slug -> compose 파일 (repo ROOT 상대)
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

# 공유 edge (traefik-net 정의자) — 항상 선행 기동
TRAEFIK_COMPOSE="infra/traefik/docker-compose.yml"

# 기동 순서: iam 먼저(모두가 OIDC 검증 대상인 IdP), console 마지막(federation 소비자)
FULL=(iam wms scm finance erp ecommerce fan console)

# demo-core: 면접 콜드스타트 최소화용 핵심 경로
# 콘솔은 core 에서 부분 federation(iam+wms), full 에서 5/5 로 렌더된다.
CORE=(iam ecommerce wms console)

# 종료 순서 = FULL 역순
DOWN_ORDER=(console fan ecommerce erp finance scm wms iam)
