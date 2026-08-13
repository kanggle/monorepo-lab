#!/usr/bin/env bash
# =============================================================================
# infra/demo/projects.sh — 통합 데모 프로젝트 맵 (단일 출처, TASK-MONO-341/344)
# =============================================================================
# demo-up.sh / demo-down.sh / verify-demo-wrapper.sh 가 공통으로 source 한다.
# 맵을 여기 한 곳에만 두어 드리프트를 원천 차단한다.
#
# COMPOSE[slug] 는 **공백으로 구분된 compose 파일 목록**이다 (ROOT 상대).
# 저장소에는 두 패턴이 공존하기 때문이다 (TASK-MONO-342):
#   패턴 1 — base = 인프라 전용, `docker-compose.e2e.yml` = 풀스택 하네스
#            → iam, wms  (base + e2e 를 함께 줘야 앱이 뜬다)
#   패턴 2 — base 가 앱까지 전부 포함
#            → scm, fan, finance, erp, ecommerce, console
#
# 패턴 1 프로젝트에 base 만 주면 **DB 만 뜨고 앱은 하나도 안 뜬다**. iam 은 이
# 모노레포의 OIDC IdP 이므로, 그 경우 나머지 전 도메인의 토큰 검증이 무너진다.
# verify-demo-wrapper.sh 의 "앱 서비스 ≥1" 가드(e)가 이 회귀를 CI 에서 잡는다.
#
# 신규 프로젝트를 추가하면 반드시 COMPOSE + FULL + DOWN_ORDER (+ 하드 의존이 있으면
# DEPS) 를 갱신할 것. 누락 시 커버리지 가드(d)가 FAIL 한다.
# =============================================================================

declare -A COMPOSE=(
  # iam 은 세 번째 파일을 받는다: e2e 오버레이(CI 소유)가 iam 앱을 `iam-e2e` 네트워크에
  # 가둬 두기 때문에, 데모에서는 traefik-net 합류 + Traefik 라우터 + 다른 도메인이 부르는
  # 이름의 alias 를 얹어야 한다. 이것이 없으면 96 컨테이너가 전부 healthy 로 떠도
  # **로그인이 불가능**하다(도달할 iam 엣지가 없다). 상세: iam-traefik.override.yml
  # `*-relay.override.yml` — 브로커에 **전역 유일 이름으로 광고하는 RELAY 리스너**를 하나
  # 더 단다(TASK-MONO-511 / ADR-MONO-062 B). 네 브로커가 전부 자기를 `kafka:9092` 로
  # 광고해서, 네 네트워크에 동시에 붙는 릴레이에게 그 이름이 **모호**하기 때문이다. 🔴 이
  # 모호함은 부트스트랩 주소를 컨테이너명으로 정확히 적어도 사라지지 않는다(클라이언트는
  # 응답의 advertised listener 로 다시 접속한다) — 증상이 연결 실패가 아니라 **조용한
  # 오배송**이라 특히 나쁘다. 브로커는 자기 네트워크에 그대로 있고, 소비자는 그대로
  # `kafka:9092` 를 읽는다. 전문: infra/demo/iam-relay.override.yml 헤더.
  [iam]="projects/iam-platform/docker-compose.yml projects/iam-platform/docker-compose.e2e.yml infra/demo/iam-traefik.override.yml infra/demo/iam-relay.override.yml"
  # `*-identity.override.yml` — 각 도메인의 **백엔드 리소스 서버**를 traefik-net 에 붙인다
  # (TASK-MONO-507). 붙이지 않으면 그 서비스들은 JWKS 를 fetch 할 주소(`iam-auth-service`,
  # traefik-net 위에만 있는 alias)를 해소하지 못하고, Spring 이 그 UnknownHost 를
  # fail-closed 로 **401 "Authentication required"** 로 바꾼다. 게이트웨이는 토큰을 정상
  # 수락한 뒤였으므로 증상은 "엣지가 좋은 토큰을 거부한다" 로 보인다. 상세 + 실측:
  # infra/demo/erp-identity.override.yml 헤더. 가드 (v) 가 이 정합을 강제한다.
  [wms]="projects/wms-platform/docker-compose.yml projects/wms-platform/docker-compose.e2e.yml infra/demo/wms-identity.override.yml infra/demo/wms-devseed.override.yml infra/demo/wms-relay.override.yml"
  [ecommerce]="projects/ecommerce-microservices-platform/docker-compose.yml infra/demo/ecommerce-relay.override.yml"
  [scm]="projects/scm-platform/docker-compose.yml infra/demo/scm-identity.override.yml infra/demo/scm-relay.override.yml"
  [fan]="projects/fan-platform/docker-compose.yml infra/demo/fan-identity.override.yml"
  [finance]="projects/finance-platform/docker-compose.yml infra/demo/finance-identity.override.yml"
  [erp]="projects/erp-platform/docker-compose.yml infra/demo/erp-identity.override.yml"
  [console]="projects/platform-console/docker-compose.yml"
)

# 공유 edge (traefik-net 정의자) — 항상 선행 기동
TRAEFIK_COMPOSE="infra/traefik/docker-compose.yml"

# ---------------------------------------------------------------------------
# 크로스프로젝트 이벤트 릴레이 (TASK-MONO-511 / ADR-MONO-062 B)
# ---------------------------------------------------------------------------
# 릴레이는 자기 compose 프로젝트(`-p relay`)로 뜨고 네 프로젝트 네트워크에 external 로
# 참여한다. 그래서 **네 도메인이 전부 떠 있을 때만** 기동할 수 있다 — 없는 네트워크를
# external 로 참조하면 compose 가 거부한다.
#
# 🔴 `demo-core` 는 scm 을 포함하지 않는다(CORE=iam ecommerce wms console) ⇒ 기본 데모
# 프로파일에서는 릴레이가 **뜨지 않는다.** 조용히 넘기지 않고 demo-up.sh 가 어느 도메인이
# 빠졌는지 이름을 대며 알린다. 이 티켓이 고친 결함이 정확히 *"배선이 없는데 아무도 모른다"*
# 였으므로, 릴레이가 없다는 사실 자체가 침묵해서는 안 된다.
RELAY_COMPOSE="infra/demo/docker-compose.relay.yml"
RELAY_DOMAINS=(iam ecommerce wms scm)

# 기동 순서: iam 먼저(모두가 OIDC 검증 대상인 IdP), console 마지막(federation 소비자)
FULL=(iam wms scm finance erp ecommerce fan console)

# demo-core: 면접 콜드스타트 최소화용 핵심 경로
CORE=(iam ecommerce wms console)

# 종료 순서 = FULL 역순
DOWN_ORDER=(console fan ecommerce erp finance scm wms iam)

# ---------------------------------------------------------------------------
# 도메인 하드 의존 (DEPS) — 단일 출처 (TASK-MONO-477)
# ---------------------------------------------------------------------------
# DEPS[slug] = 이 도메인이 기능하려면 **반드시 함께 떠 있어야 하는** 도메인들(공백 구분).
#
# 여기 있는 것은 **하드(기동) 의존**뿐이다: 없으면 그 도메인이 조용히 못 쓰게 되는 것.
#   · iam = 이 모노레포의 OIDC IdP. 모든 앱 도메인의 게이트웨이가 iam 이 발급한 토큰을
#     검증하므로, iam 없이 어떤 도메인을 띄워도 로그인·인증이 무너진다 — 96 컨테이너가
#     healthy 여도 로그인 불가라는, 이 저장소가 반복해서 당한 실패 모드(MONO-358).
#
# **소프트 의존은 여기 넣지 않는다.** console 은 다른 도메인을 프록시하지만, 그것들이
# 없으면 해당 섹션만 "degraded" 로 보일 뿐 console 자체는 뜬다. wms↔ecommerce 풀필먼트도
# 런타임 이벤트 연동이지 기동 전제가 아니다. 소프트 의존을 하드로 선언하면 "console 하나
# 켜기" 가 전 스택을 끌어와 **도메인 선택의 존재 이유를 없앤다.**
#
# iam 자신은 의존이 없다(선언 생략 = 의존 없음).
declare -A DEPS=(
  [wms]="iam"
  [ecommerce]="iam"
  [scm]="iam"
  [fan]="iam"
  [finance]="iam"
  [erp]="iam"
  [console]="iam"
)

# ---------------------------------------------------------------------------
# resolve_deps <slug...> — 선택 집합의 하드-의존 전이 폐포를 FULL 순서로 출력.
#   · 미지의 slug 는 stderr 로 알리고 return 1 (조용한 무시 금지 — 오타가 데모를 반쪽
#     띄운다). 유효 slug 가 하나도 없어도 return 1.
#   · 출력 순서 = FULL(iam 먼저, console 마지막). 기동 순서가 load-bearing 이다.
# 호출: resolved="$(resolve_deps fan console)" || exit 2; mapfile -t SET <<<"$resolved"
# ---------------------------------------------------------------------------
resolve_deps() {
  local -A want=()
  local s d unknown="" changed=1
  for s in "$@"; do
    if [ -n "${COMPOSE[$s]+x}" ]; then want["$s"]=1; else unknown="$unknown $s"; fi
  done
  [ -z "$unknown" ] || { echo "resolve_deps: 알 수 없는 도메인:$unknown (유효: ${!COMPOSE[*]})" >&2; return 1; }
  [ "${#want[@]}" -gt 0 ] || { echo "resolve_deps: 도메인이 지정되지 않았습니다" >&2; return 1; }
  # 하드-의존 전이 확장 — 고정점까지 반복(현재 DEPS 는 1패스로 수렴하나 일반화해 둔다).
  while [ "$changed" = 1 ]; do
    changed=0
    for s in "${!want[@]}"; do
      for d in ${DEPS[$s]:-}; do
        if [ -z "${want[$d]+x}" ]; then want["$d"]=1; changed=1; fi
      done
    done
  done
  for s in "${FULL[@]}"; do
    [ -n "${want[$s]+x}" ] && printf '%s\n' "$s"
  done
  # 🔴 이 `return 0` 은 장식이 아니다 (TASK-MONO-505).
  #
  # 함수의 종료 상태는 **마지막으로 실행된 명령**의 것이고, 위 루프의 마지막 명령은
  # `FULL` 의 마지막 원소(`console`)에 대한 `[ -n ... ]` 테스트다. 요청 집합에
  # console 이 없으면 그 테스트가 거짓이라 `&&` 가 단락되고, 그 1 이 함수의 반환값이
  # 되어 나간다 — **출력은 완벽하게 맞는데** 호출자는 실패로 읽는다.
  #
  # demo-up.sh 는 이 반환값을 보고 usage 를 찍고 exit 2 하므로, 결과적으로
  # **console 을 포함하지 않는 모든 부분 기동이 불가능했다**(실측):
  #
  #   resolve_deps iam         → rc=1  (출력은 "iam" 으로 정확했다)
  #   resolve_deps erp         → rc=1  (출력은 "iam erp")
  #   resolve_deps console erp → rc=0  ← console 이 FULL 의 마지막이라 우연히 통과
  #
  # 증상은 "가장 흔한 부분 기동(iam 만, 도메인 하나만)이 안 된다" 인데 메시지는
  # "알 수 없는 도메인" 계열 usage 라 원인을 정반대로 가리켰다. `full`/`demo-core` 는
  # 이 함수를 거치지 않아 멀쩡했고, 그래서 지금까지 눈에 띄지 않았다.
  return 0
}

# ---------------------------------------------------------------------------
# compose_args <slug> — "-f\n<abs>" 쌍을 개행 구분으로 출력.
# 호출자: mapfile -t ARGS < <(compose_args iam)
# ROOT 는 호출 스크립트가 정의한다.
# ---------------------------------------------------------------------------
compose_args() {
  local slug="$1" f
  for f in ${COMPOSE[$slug]}; do
    printf -- '-f\n%s\n' "$ROOT/$f"
  done
}

# compose_files <slug> — ROOT 상대 경로를 한 줄에 하나씩 (가드용)
compose_files() {
  local slug="$1" f
  for f in ${COMPOSE[$slug]}; do printf '%s\n' "$f"; done
}
