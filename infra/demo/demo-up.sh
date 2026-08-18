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

# 이미지가 코드보다 낡았는지 **기동 전에** 알린다 (TASK-MONO-533).
# 게이트가 아니라 고지이므로 실패해도 기동을 막지 않는다 — 근거는 check-image-freshness.sh 헤더.
#
# 🔴 `DEMO_BUILD=1` 이어도 **건너뛰지 않는다** (TASK-MONO-538). 이 스크립트는 **컴파일하지
# 않으므로**(위 헤더) 지금 굽는 이미지는 **디스크에 있는 jar 그대로**다. jar 이 코드보다
# 낡았다면 굽는 행위가 그것을 *새 이미지로 세탁*하고, 그 뒤로는 첫 축(이미지 vs 소스)이
# 영원히 초록이 된다. 검사의 두 번째 축(jar vs 소스)이 정확히 그 창을 본다 ⇒ **굽기 직전이
# 그것을 말할 수 있는 마지막 순간**이다.
if [ "$BUILD" = "1" ]; then
  # 🔵 무엇을 굽는지 이름을 댄다 — 하드 의존이 함께 구워지므로 사용자가 요청한 것보다 넓다.
  echo "[demo] DEMO_BUILD=1 — 다시 구울 도메인: ${SET[*]} (하드 의존 포함)"
  echo "[demo]   이 스크립트는 컴파일하지 않는다. jar 이 낡았다면 먼저 ./gradlew …:bootJar"
fi
bash "$HERE/check-image-freshness.sh" "${SET[@]}" || true

# TASK-MONO-548 — 🔴 이것은 고지가 아니라 **게이트**다. 위의 이미지 신선도 검사와 성격이
# 다르다: 낡은 이미지는 다시 구우면 되지만, 여기서 잘못된 비밀번호로 데이터 볼륨이
# 초기화되면 **재기동으로 절대 되돌릴 수 없다**(DB init 은 빈 데이터 디렉터리에서만 돈다).
# 그래서 실패를 삼키지 않는다 — 멈출 수 있는 마지막 순간이 기동 직전이다.
bash "$HERE/check-env-preflight.sh" "${SET[@]}" || exit 1

echo "[demo] profile=$PROFILE  build=$BUILD"
echo "[demo] ensuring shared traefik-net + edge router"
# 🔴 여기는 아래 루프와 달리 **격리하지 않는다.** 이 compose 가 `traefik-net` 을 *정의*하고
# 나머지 8개는 그것을 external 로 참조하므로, 실패하면 8개가 전부 같은 이유로 실패한다 —
# 격리해 봐야 재시도 예산만 태우고 결과는 같다. 여기서 멈추는 편이 진단이 정확하다.
docker compose -p traefik -f "$ROOT/$TRAEFIK_COMPOSE" up -d

# =============================================================================
# 프로젝트별 기동 — TASK-MONO-553
# =============================================================================
# 이전 모양은 `docker compose … up -d` 를 `set -e` 아래 그냥 불렀다. 그래서 **한 도메인의
# 기동 실패가 스크립트 전체를 끝냈다** — 나머지 7개는 손도 못 대고, 재시작 정책이 되살려
# 둔 **옛 라벨 컨테이너가 그대로 서빙**한다. 결과는 "스택은 도는데 새 주소가 전부 404".
#
# AC-0 재확인 (문서가 아니라 이 호출 지점에서):
#   · 이 스크립트는 `--wait` 도 `--wait-timeout` 도 주지 않는다 ⇒ **compose 레벨 대기
#     타임아웃이라는 것은 존재하지 않는다.** 대기는 전부 각 프로젝트 compose 의
#     `depends_on: condition: service_healthy` 가 하고, 그 한도는 **의존 대상 자신의
#     healthcheck** 다. iam-kafka 기준(projects/iam-platform/docker-compose.yml:146):
#         interval 15s · timeout 10s · retries 10 · start_period 30s
#     ⇒ start_period 를 지난 뒤 연속 10회 실패하면 `unhealthy` 가 되고, 그 순간 compose 가
#       `dependency failed to start: container iam-kafka is unhealthy` 로 **포기**한다.
#   · 그 healthcheck 는 `kafka-broker-api-versions.sh` — **JVM 을 새로 띄운다.** 8개 도메인이
#     동시에 재기동해 I/O 가 경합하면 10초 timeout 을 넘기는 것이 이상한 일이 아니다.
#     2026-08-17 실증에서 iam-kafka 는 `__consumer_offsets` 50 파티션을 로딩 중이었고
#     **1~2분 뒤 healthy 가 됐다** — 손상이 아니라 레이스다.
#
# 그래서 두 가지를 한다:
#   (A) 한 프로젝트의 실패가 나머지를 막지 않는다. 🔴 단 **삼키지 않는다** — 실패한 도메인
#       이름을 모아 두고 마지막에 비-0 로 끝낸다. `|| true` 로 넘기면 systemd 유닛이
#       초록이 되고, 이 저장소가 반복해서 당한 *"아무것도 안 보면서 초록"* 이 된다.
#   (B) 느린 의존성에는 재시도를 준다. 실패의 성격이 **레이스**이므로 같은 명령을 조금 뒤에
#       다시 부르면 성공한다(compose 는 멱등하다 — 이미 healthy 한 것은 두고 못 뜬 것만
#       다시 시도한다). 타임아웃을 늘리는 쪽은 택하지 않았다: healthcheck 파라미터는
#       8개 프로젝트 compose 에 흩어져 있고 CI 도 그 값을 쓰는데, 여기서 필요한 것은
#       **데모 부팅이라는 한 상황의 경합 내성**이기 때문이다.
#
# 🔴 재시도 예산을 **전역으로** 묶는다. 도메인마다 독립적으로 재시도하면 최악의 경우
#    8 × (attempts-1) × sleep 이 되어 `demo-stack.service` 의 TimeoutStartSec=1200 을
#    넘긴다 — 그러면 systemd 가 SIGTERM 을 보내고, "부분 실패를 견디는 고침" 이 오히려
#    **전체를 죽인다.** 예산이 바닥나면 더 기다리지 않고 실패로 기록하고 넘어간다.
UP_ATTEMPTS="${DEMO_UP_ATTEMPTS:-3}"
UP_RETRY_SLEEP="${DEMO_UP_RETRY_SLEEP:-60}"
retry_budget="${DEMO_UP_RETRY_BUDGET:-360}"

failed=()
for p in "${SET[@]}"; do
  mapfile -t ARGS < <(compose_args "$p")
  attempt=1
  while :; do
    if [ "$attempt" -eq 1 ]; then
      echo "[demo] up: $p  (${COMPOSE[$p]})"
    else
      echo "[demo] up: $p  재시도 $attempt/$UP_ATTEMPTS (남은 재시도 예산 ${retry_budget}s)"
    fi
    # -f 를 ROOT 절대경로로 주면 project-directory 가 첫 파일의 디렉터리로 잡혀
    # 각 프로젝트의 .env 로딩과 상대 build: 컨텍스트가 올바르게 해소된다.
    if docker compose -p "$p" "${ARGS[@]}" up -d $build_flag; then
      [ "$attempt" -eq 1 ] || echo "[demo] ✔ $p — 재시도 $attempt 회차에 기동 성공"
      break
    fi
    if [ "$attempt" -ge "$UP_ATTEMPTS" ] || [ "$retry_budget" -lt "$UP_RETRY_SLEEP" ]; then
      failed+=("$p")
      echo "[demo] ✖ $p 기동 실패 — 나머지 도메인은 계속 진행합니다(이 실패는 마지막에 비-0 로 보고됩니다)" >&2
      break
    fi
    attempt=$(( attempt + 1 ))
    retry_budget=$(( retry_budget - UP_RETRY_SLEEP ))
    echo "[demo] … $p 기동 실패 — ${UP_RETRY_SLEEP}s 뒤 다시 시도합니다(느린 의존성의 healthcheck 가 아직 안 붙었을 수 있습니다)" >&2
    sleep "$UP_RETRY_SLEEP"
  done
done

# 크로스프로젝트 이벤트 릴레이 (TASK-MONO-511 / ADR-MONO-062 B). 브로커가 전부 뜬 뒤
# 마지막에 올린다 — `depends_on` 은 compose 프로젝트 경계를 넘지 못하므로 순서가 대신한다.
# 🔴 릴레이가 없으면 크로스프로젝트 이벤트는 **한 건도 건너가지 않는다.** 그것이 이 티켓이
# 고친 결함이고, 그 상태는 어떤 에러도 내지 않으므로 여기서 소리를 내야 한다.
relay_missing=()
for d in "${RELAY_DOMAINS[@]}"; do
  [[ " ${SET[*]} " == *" $d "* ]] || relay_missing+=("$d")
done
if [ ${#relay_missing[@]} -eq 0 ]; then
  echo "[demo] up: relay  ($RELAY_COMPOSE)  — 크로스프로젝트 이벤트 릴레이"
  # 릴레이도 격리한다(TASK-MONO-553 A). 네 브로커 중 하나가 못 떠서 릴레이가 실패해도
  # 이미 뜬 도메인들을 여기서 끝낼 이유가 없다 — 다만 **조용히 넘기지도 않는다.**
  docker compose -p relay -f "$ROOT/$RELAY_COMPOSE" up -d || {
    failed+=("relay")
    echo "[demo] ✖ relay 기동 실패 — 크로스프로젝트 이벤트가 한 건도 건너가지 않습니다" >&2
  }
else
  echo "[demo] ⚠ 이벤트 릴레이 생략 — 이번 기동에 없는 도메인: ${relay_missing[*]}"
  echo "[demo]   ⇒ 크로스프로젝트 이벤트가 한 건도 건너가지 않습니다(iam→ecommerce 계정 3종 ·"
  echo "[demo]     wms→ecommerce · wms→scm · ecommerce→wms · scm→wms). 릴레이는 네 도메인"
  echo "[demo]     (${RELAY_DOMAINS[*]})이 모두 필요합니다 — external 네트워크 참조이기 때문입니다."
  echo "[demo]     전부 띄우려면: bash infra/demo/demo-up.sh full"
fi

# OIDC 클라이언트의 redirect_uri 는 마이그레이션에 `.local` 로 박혀 있다. 데모 도메인은
# 부팅 때 정해지므로 마이그레이션이 알 수 없다 → 여기서 등록한다. DEMO_DOMAIN=local 이면
# no-op. (자세한 근거는 seed-demo-domain.sh 헤더. 가드 (k) 가 이 호출을 지킨다.)
if [[ " ${SET[*]} " == *" iam "* ]]; then
  bash "$HERE/seed-demo-domain.sh"
fi

# 도메인 데이터 시드 (TASK-MONO-506). 계정과 배선이 갖춰져도 화면이 비어 있으면
# 데모는 아무것도 증명하지 못한다. `DEMO_SEED=0` 으로 끌 수 있고, 실패해도 이미 떠 있는
# 스택을 내리지 않는다(비-0 로 끝나되 기동은 유지) — 그래서 `|| true` 가 아니라
# 종료코드를 보존해 마지막 줄에서 알린다. 가드 (z) 가 이 호출을 지킨다.
seed_rc=0
bash "$HERE/seed/seed.sh" "${SET[@]}" || seed_rc=$?

# 라벨 드리프트 판정 (TASK-MONO-553 C) — 이 결함의 **직접적인 술어**다. 왜 다른 신호로는
# 안 되는지, 왜 모집단을 둘로 나누는지는 check-label-drift.sh 헤더에 있다. 별도 스크립트인
# 이유는 **그것만 따로 물릴 수 있어야** 하기 때문이다(AC-3 bite).
drift_rc=0
bash "$HERE/check-label-drift.sh" "${SET[@]}" || drift_rc=$?

echo "[demo] up complete — profile=$PROFILE"
[ "$seed_rc" -eq 0 ] || echo "[demo] ⚠ 도메인 데이터 시드가 일부 실패했습니다(위 [seed] 로그 참조) — 해당 화면은 빌 수 있습니다"
echo "[demo] 호스트: console.${DEMO_DOMAIN} / web.ecommerce.${DEMO_DOMAIN} / wms.${DEMO_DOMAIN} / <domain>.${DEMO_DOMAIN} (Traefik)"

# -----------------------------------------------------------------------------
# 최종 종료코드 — 🔴 여기서 삼키면 위의 모든 보고가 장식이 된다 (TASK-MONO-553 A)
# -----------------------------------------------------------------------------
# `demo-boot.sh` 가 이 스크립트를 `exec` 하므로 이 종료코드가 곧 `demo-stack.service` 의
# 결과다(Type=oneshot). 비-0 로 끝나도 **이미 뜬 컨테이너는 내리지 않는다** — 유닛이
# `failed` 로 표시될 뿐이고, 그것이 정확히 우리가 원하는 것이다: 부분 실패는 부분 실패로
# 보여야 한다.
#
# 🔵 시드 실패(`seed_rc`)도 여기 포함시킨다. 바로 위 주석은 이미 *"비-0 로 끝나되 기동은
#    유지"* 라고 계약을 적어 두었는데 **코드는 그렇게 하지 않고 있었다**(마지막 echo 의
#    종료코드 0 으로 끝났다). 주석과 코드가 어긋나 있었고, 어긋난 쪽은 코드였다.
final_rc=0
if [ ${#failed[@]} -gt 0 ]; then
  final_rc=1
  echo "[demo] ✖ 기동 실패 도메인: ${failed[*]}" >&2
fi
[ "$drift_rc" -eq 0 ] || final_rc=1
[ "$seed_rc"  -eq 0 ] || final_rc=1
exit "$final_rc"
