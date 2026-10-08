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
#            → scm, fan, finance, erp, ecommerce
#
# 🔴 platform-console 은 **데모 도메인이 아니다** (TASK-MONO-757 / ADR-MONO-081).
#    방문자 콘솔(console-web)은 Vercel 에서 돌고(ADR-MONO-067 단계 3), 그 서버의
#    합성 레이어였던 Spring BFF 는 은퇴했다(ADR-MONO-081). 그 compose 에 남은 서비스는
#    console-web 하나뿐인데 데모 호스트는 그것을 띄우지 않으므로, 체인으로 등록하면
#    **서비스 0개**를 렌더한다(`no service selected` — 부팅 실패 + 상태가 영구 `down`).
#    ⇒ COMPOSE 에서 뺐고, 커버리지 가드 (d) 가 «잊었다» 와 구별하도록 아래
#    `NOT_DEMO_COMPOSE` 에 **사유와 함께** 적는다.
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
  # `ecommerce-vercel.override.yml` — 방문자 스토어가 Vercel 로 옮겨갔으므로
  # (ADR-MONO-067 단계 2) 데모에서는 web-store 를 **띄우지 않는다**. 억제는 그
  # 파일 한 곳에 선언돼 있고, 로컬(base 단독)과 CI 는 영향받지 않는다 — 어느 CI
  # 잡도 이 compose 의 web-store 서비스를 띄우지 않는다(TASK-MONO-604 AC-0 ②
  # 전수). 효력은 가드 (z19)가 렌더로 확인한다.
  # `ecommerce-mail.override.yml` — notification-service 의 SMTP(이미 있던 발송기)를 iam 의 Mailpit
  # (`iam-mailpit`)으로 보낸다(TASK-MONO-770). 메일 서버가 저장소에 하나도 없어 그 발송은 늘 연결 실패였다.
  [ecommerce]="projects/ecommerce-microservices-platform/docker-compose.yml infra/demo/ecommerce-relay.override.yml infra/demo/ecommerce-vercel.override.yml infra/demo/ecommerce-mail.override.yml"
  [scm]="projects/scm-platform/docker-compose.yml infra/demo/scm-identity.override.yml infra/demo/scm-relay.override.yml"
  # `fan-vercel.override.yml` — 방문자 팬 화면이 Vercel 로 옮겨갔으므로
  # (ADR-MONO-067 단계 4) 데모에서는 fan-platform-web 을 **띄우지 않는다**. 억제는
  # 그 파일 한 곳에 선언돼 있고, 로컬(base 단독)과 CI 는 영향받지 않는다 — CI 의
  # Frontend E2E smoke 는 compose 가 아니라 `pnpm --filter fan-platform-web build`
  # 로 돈다(TASK-MONO-618 AC-0 ② 전수). 효력은 (z27)이 렌더로, TASK-MONO-617 의
  # 판정자가 도는 컨테이너로 확인한다.
  [fan]="projects/fan-platform/docker-compose.yml infra/demo/fan-identity.override.yml infra/demo/fan-vercel.override.yml"
  [finance]="projects/finance-platform/docker-compose.yml infra/demo/finance-identity.override.yml"
  [erp]="projects/erp-platform/docker-compose.yml infra/demo/erp-identity.override.yml"
  # 🔴 `[console]` 은 **없다** (TASK-MONO-757, 2026-10). 예전에는
  #    `platform-console/docker-compose.yml + console-vercel.override.yml` 체인이었고, 그
  #    오버라이드가 console-web 을 억제해 남은 서비스는 옛 BFF(공개 라우터 없음) 하나였다.
  #    BFF 가 은퇴(ADR-MONO-081)하자 체인이 **0 서비스**를 렌더했다 — 실측:
  #      docker compose -f …/platform-console/docker-compose.yml -f …/console-vercel.override.yml
  #        --dry-run up -d  →  rc=1 `no service selected`
  #    억제할 것도, 띄울 것도 남지 않았으므로 도메인째 뺐다. 콘솔이 데모 호스트에서 쓰는
  #    것은 IdP(iam) 하나이고, 그것은 묶음 `console` 이 푼다(§ BUNDLES).
)

# ---------------------------------------------------------------------------
# NOT_DEMO_COMPOSE — `projects/*/docker-compose.yml` 중 **일부러** 데모 도메인이 아닌 것
# ---------------------------------------------------------------------------
# 커버리지 가드 (d) 는 모든 프로젝트 compose 가 COMPOSE 에 등록됐는지 본다 — «새 프로젝트를
# 맵에 안 넣고 잊었다» 가 데모에서 조용한 누락이 되기 때문이다. 이 표는 그 질문에
# «잊은 것이 아니라 안 띄우는 것» 이라고 **사유를 대고** 답하는 유일한 자리다.
# 🔴 사유 없는 항목을 두지 마라 — 빈 값은 (d) 가 문다. 그리고 여기 적힌 파일이 COMPOSE 에도
#    있으면 (d) 가 문다(두 표가 같은 파일을 두고 반대 말을 하면 한쪽은 거짓이다).
declare -A NOT_DEMO_COMPOSE=(
  [projects/platform-console/docker-compose.yml]="console-web 은 Vercel 에서 돈다(ADR-MONO-067 단계 3) · BFF 은퇴(ADR-MONO-081) — 데모 호스트에서 띄울 서비스가 없다(TASK-MONO-757)"
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
# 🔴 `demo-core` 는 scm 을 포함하지 않는다(CORE=iam ecommerce wms) ⇒ 기본 데모
# 프로파일에서는 릴레이가 **뜨지 않는다.** 조용히 넘기지 않고 demo-up.sh 가 어느 도메인이
# 빠졌는지 이름을 대며 알린다. 이 티켓이 고친 결함이 정확히 *"배선이 없는데 아무도 모른다"*
# 였으므로, 릴레이가 없다는 사실 자체가 침묵해서는 안 된다.
RELAY_COMPOSE="infra/demo/docker-compose.relay.yml"
RELAY_DOMAINS=(iam ecommerce wms scm)

# 기동 순서: iam 먼저(모두가 OIDC 검증 대상인 IdP).
# 🔵 예전에는 console 이 마지막(federation 소비자)이었다. 콘솔이 데모 도메인에서 빠지면서
#    (TASK-MONO-757) 마지막 원소는 fan 이다 — 순서에 load-bearing 인 것은 «iam 먼저» 뿐이다.
FULL=(iam wms scm finance erp ecommerce fan)

# demo-core: 면접 콜드스타트 최소화용 핵심 경로
# 🔵 console 이 빠졌다(TASK-MONO-757). Vercel 콘솔이 데모 호스트에서 쓰는 iam 은 이미 여기 있고,
#    콘솔의 업무 화면이 쓰는 ecommerce·wms 도 여기 있다 ⇒ demo-core 로 콘솔이 쓸 수 있는 범위는
#    그대로다.
CORE=(iam ecommerce wms)

# 종료 순서 = FULL 역순
DOWN_ORDER=(fan ecommerce erp finance scm wms iam)

# ---------------------------------------------------------------------------
# 화면 묶음 (BUNDLES) — 방문자가 고르는 단위 (TASK-MONO-634 / ADR-MONO-071)
# ---------------------------------------------------------------------------
# `DOMAINS` 는 **구현 단위**이고, 방문자가 아는 단위는 **화면**이다. 론처는 «팬 플랫폼을
# 쓰겠다» 를 받지 «fan 과 iam 을 올려라» 를 받지 않는다. 그 번역을 여기서 한 번만 한다.
#
# 🔴 왜 `DEPS` 로 충분하지 않은가 — 두 표가 **다른 것을 잰다**:
#     DEPS[fan]="iam"   = «fan 이 기능하려면 iam 이 떠 있어야 한다»  (하드 의존)
#     BUNDLES[fan]="fan" = «"팬 플랫폼" 이라는 화면은 fan 도메인이다» (제품 단위)
#   묶음을 풀 때 `resolve_deps` 가 iam 을 얹으므로 여기에 iam 을 **적지 않는다.** 적으면
#   같은 사실이 두 곳에 생기고, DEPS 가 바뀌는 날 한쪽만 고쳐진다.
#   🔴 **예외 하나 — `console`** (TASK-MONO-757). 위 규칙의 근거는 «묶음에 자기 도메인이
#   있고, iam 은 그 도메인의 하드 의존으로 따라온다» 이다. 콘솔 묶음에는 **자기 도메인이
#   없다**: 화면(console-web)은 Vercel 에서 돌고 BFF 는 은퇴했으므로(ADR-MONO-081) 데모
#   호스트에서 콘솔이 쓰는 것은 IdP 하나다. 그래서 iam 은 «얹히는 의존» 이 아니라 그 묶음의
#   **내용 그 자체**이고, 여기 적지 않으면 묶음이 빈 집합이 되어 `resolve_bundles console`
#   이 «묶음이 지정되지 않았습니다» 로 실패한다. DEPS 와 두 집이 되는 것도 아니다 — DEPS 는
#   «X 가 기능하려면» 을 말하고, 이 줄은 «콘솔이 데모 호스트에서 쓰는 것» 을 말한다.
#
# 🔴🔴 **이 표가 Lambda 의 화이트리스트와 같아야 한다.** 컨트롤 플레인은 방문자 입력을
#   SSM RunShellScript 로 넘기므로 화이트리스트가 **주입 방어**이기도 하다. 두 곳에 있는
#   같은 사실이므로 가드 (z32)가 이 표와 `handler.py` 의 `BUNDLES` 를 대조한다.
#
# 🔵 콘솔의 업무 도메인은 **묶음이 아니라 애드온**이다(§ BUNDLE_ADDONS). 콘솔은 그것들
#   없이도 뜨고, 안 뜬 도메인은 그 섹션만 "서비스 시작 필요" 가 된다 — 소프트 의존을
#   묶음에 넣으면 「콘솔 하나 켜기」가 전 스택을 끌어와 선택의 존재 이유를 없앤다.
declare -A BUNDLES=(
  [fan]="fan"
  [store]="ecommerce"
  [console]="iam"
)

# 애드온 — 방문자가 «그 기능» 을 쓸 때 **추가로** 올리는 것. 묶음과 합집합으로 쓰인다.
#   store-fulfillment : 스토어의 출고·배송 연계(이커머스 → WMS → SCM 이벤트 흐름)
#   console-<domain>  : 콘솔의 업무 도메인 화면
# 🔴 애드온을 기본 묶음에 접어 넣지 마라 — 그것이 «불필요한 전체 기동» 으로 가는 길이다.
declare -A BUNDLE_ADDONS=(
  [store-fulfillment]="wms scm"
  [console-ecommerce]="ecommerce"
  [console-wms]="wms"
  [console-scm]="scm"
  [console-erp]="erp"
  [console-finance]="finance"
)

# ---------------------------------------------------------------------------
# resolve_bundles <name...> — 묶음/애드온 이름 집합을 **도메인 집합**으로 푼다.
#   · 이름이 하나라도 모르는 것이면 stderr 로 알리고 return 1 (조용한 무시 금지 —
#     오타가 «켰다고 생각했는데 안 켜진» 상태를 만들고, 그 상태는 방문자에게 «고장» 이다).
#   · 하드 의존은 `resolve_deps` 가 얹는다. 여기서 iam 을 적지 않는 이유(위 § 참조 —
#     `console` 만 예외이고 그 이유도 거기 있다).
#   · 출력 순서 = FULL(iam 먼저). 기동 순서가 load-bearing 이다.
# 호출: set="$(resolve_bundles fan console)" || exit 2
# ---------------------------------------------------------------------------
resolve_bundles() {
  local n unknown="" doms=""
  for n in "$@"; do
    if [ -n "${BUNDLES[$n]+x}" ]; then
      doms="$doms ${BUNDLES[$n]}"
    elif [ -n "${BUNDLE_ADDONS[$n]+x}" ]; then
      doms="$doms ${BUNDLE_ADDONS[$n]}"
    else
      unknown="$unknown $n"
    fi
  done
  [ -z "$unknown" ] || {
    echo "resolve_bundles: 알 수 없는 묶음:$unknown (유효: ${!BUNDLES[*]} ${!BUNDLE_ADDONS[*]})" >&2
    return 1
  }
  [ -n "$doms" ] || { echo "resolve_bundles: 묶음이 지정되지 않았습니다" >&2; return 1; }
  # shellcheck disable=SC2086
  resolve_deps $doms
}

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
# **소프트 의존은 여기 넣지 않는다.** wms↔ecommerce 풀필먼트는 런타임 이벤트 연동이지
# 기동 전제가 아니다. 소프트 의존을 하드로 선언하면 "도메인 하나 켜기" 가 전 스택을 끌어와
# **도메인 선택의 존재 이유를 없앤다.** (콘솔의 업무 도메인이 그 예였고, 지금은 묶음
# 애드온으로 표현된다 — § BUNDLE_ADDONS. 콘솔 자신은 더 이상 데모 도메인이 아니다.)
#
# iam 자신은 의존이 없다(선언 생략 = 의존 없음).
declare -A DEPS=(
  [wms]="iam"
  [ecommerce]="iam"
  [scm]="iam"
  [fan]="iam"
  [finance]="iam"
  [erp]="iam"
)

# ---------------------------------------------------------------------------
# resolve_deps <slug...> — 선택 집합의 하드-의존 전이 폐포를 FULL 순서로 출력.
#   · 미지의 slug 는 stderr 로 알리고 return 1 (조용한 무시 금지 — 오타가 데모를 반쪽
#     띄운다). 유효 slug 가 하나도 없어도 return 1.
#   · 출력 순서 = FULL(iam 먼저). 기동 순서가 load-bearing 이다.
# 호출: resolved="$(resolve_deps fan erp)" || exit 2; mapfile -t SET <<<"$resolved"
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
  # `FULL` 의 마지막 원소에 대한 `[ -n ... ]` 테스트다. 요청 집합에 그 원소가 없으면
  # 테스트가 거짓이라 `&&` 가 단락되고, 그 1 이 함수의 반환값이 되어 나간다 —
  # **출력은 완벽하게 맞는데** 호출자는 실패로 읽는다.
  #
  # demo-up.sh 는 이 반환값을 보고 usage 를 찍고 exit 2 하므로, 결과적으로
  # **마지막 원소를 포함하지 않는 모든 부분 기동이 불가능했다**(실측 — 당시 FULL 의
  # 마지막은 console 이었다. TASK-MONO-757 이후로는 fan 이고, 결함의 모양은 같다):
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

# ---------------------------------------------------------------------------
# domain_running <slug> — 그 compose 프로젝트(-p <slug>)에 컨테이너가 하나라도 있으면
# "떠 있음"으로 본다 (단일 출처 — TASK-MONO-779).
# ---------------------------------------------------------------------------
# 전에는 `demo-down.sh` 안에 똑같은 정의(`is_running`)가 **따로** 있었다. 이 함수가
# 지금은 세 자리에서 쓰인다: demo-down.sh 의 부분 종료 잔존 가드(「아직 떠 있는 r」) ·
# demo-down.sh 의 릴레이 선(先) 종료 판정(「relay 프로젝트가 떠 있는가」) ·
# demo-up.sh 의 릴레이 기동 판정(「이 RELAY_DOMAINS 원소가 떠 있는가」, 아래). 세 자리가
# 같은 질문("이 compose 프로젝트에 컨테이너가 남아 있는가")을 각자 answer 하게 두면
# 한쪽만 고쳐지고 갈라진다 — 이 파일의 머리글이 "드리프트를 원천 차단한다" 라고 적은
# 바로 그 모양이다.
#
# 🔴 **왜 `-a`(전 상태)인가, "running 뿐"(`-q` 단독)이 아니라** — 세 호출자 모두 원하는
# 질문은 "이 도메인이 기동된 뒤 아직 안 내려갔는가"이지 "이 순간 healthcheck 를
# 통과했는가"가 아니다. 후자는 이미 각 compose 의 `depends_on: condition:
# service_healthy`(기동 중)와 릴레이 자신의 `up -d` 성공 여부(`failed+=relay`, 기동 뒤)가
# 따로 본다. `Exited (0)` 인 init 컨테이너(예: `iam-kafka-init`)가 있는 평범한 정상
# 상태를 "안 떠 있다"로 오판하면 그 도메인은 **어떤 상태에서도 "떠 있음"이 될 수 없다**
# (TASK-MONO-551 A 가 같은 함정을 다른 자리에서 겪었다) — 그래서 `-aq`.
domain_running() { [ -n "$(docker ps -aq --filter "label=com.docker.compose.project=$1" 2>/dev/null)" ]; }
