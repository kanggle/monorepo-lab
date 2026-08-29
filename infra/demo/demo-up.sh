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

# ---------------------------------------------------------------------------
# 🔴 TASK-MONO-559 결함 B — 예산이 선착순이라 **목록 앞이 전부 먹었다**
# ---------------------------------------------------------------------------
# 위 문단의 "전역 예산" 판단 자체는 옳다(독립 재시도는 TimeoutStartSec 을 넘긴다).
# 틀린 것은 **상한이 아니라 배분**이었다. 2026-08-19 라이브 실측:
#
#   iam 재시도 2/3 (남은 300s) → 3/3 (남은 240s)   ← iam 이 120s 를 먹고 실패
#   wms 재시도 2/3 (남은 180s) → 성공
#   fan 재시도 2/3 (남은 120s) → 3/3 (남은  60s)   ← fan 은 60s 를 남기고 통과
#
# `fan` 은 경계에서 **60s** 떨어져 있었다. 한 도메인만 더 느렸다면, 또는 `fan` 뒤의
# `console` 이 한 번이라도 재시도가 필요했다면, 그 도메인은 **재시도를 단 한 번도 받지
# 못한 채** 실패로 기록된다 — 자기가 느려서가 아니라 **앞이 먼저 썼기 때문에.**
# `SET` 배열 순서가 곧 우선순위였고 iam 이 첫 번째다.
#
# 고침: **뒤에 남은 도메인 수만큼을 항상 예약해 둔다.** 어떤 도메인에게 재시도를 주기
# 전에 `풀 - sleep >= sleep × (뒤에 남은 도메인 수)` 를 요구하면, 마지막 도메인까지
# 각자 최소 1회가 보장된다. 앞 도메인이 1회에 성공하면 그 몫은 풀에 남아 **필요한
# 도메인에게 흘러간다** — 하한을 주면서 유휴 용량을 버리지 않는다.
#
# 🔴 총 상한은 **상수가 아니라 도메인 수에서 파생**한다. 상수로 두면 프로필이 커질 때
#    TimeoutStartSec 을 넘겨 systemd 가 SIGTERM 을 보낸다(위 문단이 경고한 그 모양).
#
#    산술 (`TimeoutStartSec=1200`, demo-stack.service):
#      재시도 최대 총합 = 도메인 수 × UP_RETRY_SLEEP = 8 × 60 = **480s**
#      기동 자체        = **540s**  ← 2026-08-19 실측(총 840s 중 sleep 300s 를 뺀 값)
#      합               = 1020s ≤ 1200s   (여유 180s)
#
#    ⚠️ 540s 는 **관측 1건**이지 상수가 아니다. 그래서 이 산술을 주석에만 두지 않고
#    `verify-demo-wrapper.sh` 가드가 `FULL` 크기로 다시 계산해 상한을 넘으면 FAIL 한다.
#    프로필이 커지면 여기서가 아니라 **CI 에서** 먼저 빨개진다.
n_domains=${#SET[@]}
retry_pool="${DEMO_UP_RETRY_BUDGET:-$(( n_domains * UP_RETRY_SLEEP ))}"

# 🔴 하한이 **산술적으로 불가능한** 경우를 조용히 넘기지 않는다.
# 풀이 `도메인 수 × sleep` 보다 작으면 모두에게 1회씩 줄 수 없다. 그때 예약 규칙은
# 편향을 없애는 게 아니라 **앞에서 뒤로 옮길 뿐**이다(앞 도메인이 예약을 못 채워 거절되고
# 뒤가 받는다). 그 상태를 말없이 돌리면 다음 사람은 "공평해졌다" 고 믿는다.
# 기본값은 파생되므로 이 경고는 **누가 명시적으로 낮춰 잡았을 때만** 나온다.
if [ "$retry_pool" -lt $(( n_domains * UP_RETRY_SLEEP )) ]; then
  echo "[demo] ⚠ 재시도 풀 ${retry_pool}s < 도메인 ${n_domains}개 × ${UP_RETRY_SLEEP}s = $(( n_domains * UP_RETRY_SLEEP ))s" >&2
  echo "[demo]   ⇒ **도메인당 1회 하한을 보장할 수 없습니다.** 이 설정에서는 예약 규칙이 편향을" >&2
  echo "[demo]     없애지 못하고 앞에서 뒤로 옮깁니다. DEMO_UP_RETRY_BUDGET 을 낮춰 잡았다면 의도한 것인지 확인하세요." >&2
fi

failed=()
declare -A retries_used=()   # 도메인 → 실제로 쓴 재시도 횟수
declare -A denied=()         # 도메인 → 예약 규칙이 막은 재시도 횟수 (AC-2 의 신호)
dom_idx=0
for p in "${SET[@]}"; do
  dom_idx=$(( dom_idx + 1 ))
  mapfile -t ARGS < <(compose_args "$p")
  attempt=1
  while :; do
    if [ "$attempt" -eq 1 ]; then
      echo "[demo] up: $p  (${COMPOSE[$p]})"
    else
      echo "[demo] up: $p  재시도 $attempt/$UP_ATTEMPTS (남은 재시도 예산 ${retry_pool}s)"
    fi
    # -f 를 ROOT 절대경로로 주면 project-directory 가 첫 파일의 디렉터리로 잡혀
    # 각 프로젝트의 .env 로딩과 상대 build: 컨텍스트가 올바르게 해소된다.
    if docker compose -p "$p" "${ARGS[@]}" up -d $build_flag; then
      [ "$attempt" -eq 1 ] || echo "[demo] ✔ $p — 재시도 $attempt 회차에 기동 성공"
      break
    fi
    # 뒤에 남은 도메인 각자의 1회분을 예약한다 — 이것이 하한을 만든다.
    reserve=$(( (n_domains - dom_idx) * UP_RETRY_SLEEP ))
    if [ "$attempt" -ge "$UP_ATTEMPTS" ]; then
      failed+=("$p")
      echo "[demo] ✖ $p 기동 실패 — 재시도 $UP_ATTEMPTS 회를 다 썼습니다(나머지 도메인은 계속 진행합니다)" >&2
      break
    fi
    if [ $(( retry_pool - UP_RETRY_SLEEP )) -lt "$reserve" ]; then
      # 🔴 이 분기는 "안 떠서" 가 아니라 "예산 배분이 막아서" 다. 마지막 요약에서
      #    반드시 구별해 보고한다(AC-2) — 두 사유가 섞이면 진단이 통째로 틀어진다.
      denied[$p]=$(( ${denied[$p]:-0} + 1 ))
      failed+=("$p")
      echo "[demo] ✖ $p 기동 실패 — **재시도 예산이 남지 않아** 여기서 포기합니다" >&2
      echo "[demo]   (풀 ${retry_pool}s · 뒤에 남은 $(( n_domains - dom_idx ))개 도메인 예약 ${reserve}s — 이 도메인이 느린 것이 아닙니다)" >&2
      break
    fi
    attempt=$(( attempt + 1 ))
    retry_pool=$(( retry_pool - UP_RETRY_SLEEP ))
    retries_used[$p]=$(( ${retries_used[$p]:-0} + 1 ))
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
# 🔴🔴 종료코드를 포착한다 (TASK-MONO-552 AC-3 구현 중 발견).
# 이 호출은 `set -e` 아래에서 **rc 를 안 받고** 있었다. 그래서 이 시드가 실패하면
# (실측: DB 가 안 뜬 판에서 5분 대기 후 exit 1) `demo-up.sh` 가 **그 자리서 죽고**,
# 아래의 최종 판정 블록 — 도메인 재측정 보고 · HTTP 표면 검사 · 예산 고갈 경고 —
# 이 **한 줄도 실행되지 않는다.** 유닛은 `[seed] !!!` 한 줄만 남기고 failed 가 된다.
#
# 🔵 바로 아래 형제 호출(`seed/seed.sh`)은 이미 그 계약을 갖고 있다:
#    *"실패해도 이미 떠 있는 스택을 내리지 않는다 — `|| true` 가 아니라 종료코드를
#    보존해 마지막 줄에서 알린다"*. **이 호출만 낙오해 있었다.**
domain_seed_rc=0
if [[ " ${SET[*]} " == *" iam "* ]]; then
  bash "$HERE/seed-demo-domain.sh" || domain_seed_rc=$?
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
[ "$domain_seed_rc" -eq 0 ] || echo "[demo] ⚠ OIDC 리다이렉트 URI 등록이 실패했습니다(위 [seed] 로그 참조) — 로그인이 데모 도메인에서 되돌아오지 못할 수 있습니다"
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
# -----------------------------------------------------------------------------
# 🔴 TASK-MONO-559 결함 A — 판정을 한 순간에 찍고 다시는 안 봤다
# -----------------------------------------------------------------------------
# `failed` 는 compose 가 **포기한 시각**의 기록이다. 그런데 그 포기는 손상이 아니라
# 레이스인 경우가 많다(위 § AC-0 주석: kafka healthcheck 창이 열려 있는 동안 compose 가
# 먼저 포기한다). 2026-08-19 실측이 그것을 그대로 보여줬다:
#
#   11:57:40  [demo] ✖ iam 기동 실패        → 12:07:17 exit 1 → 유닛 failed
#   같은 시각 /domains: iam {"state":"up","healthy":15,"total":15}
#   docker inspect iam-kafka: healthy · **restarts=0**
#
# `restarts=0` 이 핵심이다 — kafka 는 죽지도 되살아나지도 않았다. 즉 **스택이 완전히
# 정상인 채로 유닛이 `failed`** 였다. 그래서 여기서 **다시 잰다.**
#
# 🔴 삼키는 것과 재는 것은 다르다. `|| true` 가 아니다 — 재측정이고, 진짜로 안 뜬
#    도메인은 **여전히 비-0** 으로 끝난다.
# 🔴 판정 술어는 새로 쓰지 않고 `demo-status.sh` 를 쓴다(TASK-MONO-551 이 고친 그 술어).
#    두 번째 술어를 만들면 둘이 갈라지고, 갈라진 순간 어느 쪽이 맞는지 아무도 모른다.
# 🔴 SSM 헬스 스냅샷은 쓰지 않는다 — 그건 `demo-status` 타이머가 쓰는 값이고 부팅 종료
#    시점에 최신이라는 보장이 없다(551 이 `health_stale` 을 만든 이유가 그것이다).
late=(); still=(); undecidable=()
if [ ${#failed[@]} -gt 0 ]; then
  # 🔴🔴 재측정에는 **생존 프로브**가 먼저 필요하다.
  # `demo-status.sh` 는 설계상 도커가 없어도 에러를 내지 않고 **전 도메인 `down`** 을
  # 돌려준다(그 파일 헤더가 그렇게 적어 뒀고, 가드로 쓰기 위한 의도적 선택이다).
  # 그래서 그 출력만 보면 *"도커가 죽어서 못 쟀다"* 와 *"정말 안 떴다"* 가
  # **바이트 단위로 구별 불가**다 — 계측 실패가 도메인 판정으로 번역된다.
  # rc 는 어차피 비-0 이라 넘어가고 싶어지지만, 그러면 다음 사람은 "안 떴다" 는
  # **틀린 사유**를 물려받는다. 그래서 도커에게 한 번 직접 묻는다.
  docker_alive=1
  docker ps -a -q >/dev/null 2>&1 || docker_alive=0
  status_json=""
  [ "$docker_alive" = 1 ] && { status_json="$(bash "$HERE/demo-status.sh" 2>/dev/null)" || status_json=""; }
  for p in "${failed[@]}"; do
    if [ "$p" = "relay" ]; then still+=("$p"); continue; fi   # 릴레이는 도메인이 아니다
    if [ "$docker_alive" != 1 ]; then undecidable+=("$p"); continue; fi
    frag="$(printf '%s' "$status_json" | grep -o "\"$p\":{[^}]*}" || true)"
    st="$(printf '%s' "$frag" | sed -n 's/.*"state":"\([a-z]*\)".*/\1/p')"
    case "$st" in
      up)           late+=("$p") ;;
      down|partial) still+=("$p") ;;
      # 🔴 (4) 재측정이 이 도메인에 대해 아무 말도 안 했다. 이걸 빼면 "안 뜬 것"이
      #    "늦게 수렴"으로 오분류되어 **영구 초록**이 된다. 판정 불가는 판정 불가다.
      *)            undecidable+=("$p") ;;
    esac
  done
fi

# =============================================================================
# 🔴🔴 TASK-MONO-552 AC-3 — 부팅 완료 판정이 **HTTP 표면**을 본다
# =============================================================================
# 이 티켓의 발견이 그것이다: 컨테이너 **99/102 가 healthy 인 채로 표면이 전멸**했다.
# 그래서 컨테이너 수도, 컨테이너 헬스의 재측정도 이 명제의 증거가 아니다 —
# **방문자가 여는 것은 HTTP 다.** 위 재측정(559)은 인접 축을 덮었지 이 축이 아니다.
#
# 🔴 **목록을 여기 적지 않는다.** 출처는 **방문자에게 약속하는 그 목록** — 론처의
#    `data-surface` 항목이다. 두 벌이면 하나만 고쳐지고, 그 어긋남은 로그가 아니라
#    **"약속했는데 안 열리는 화면"** 으로 나타난다.
# 🔴 **추출 0건은 판정 불가다.** 빈 목록이면 이 검사는 아무것도 안 하면서 초록이 된다
#    — 이 저장소가 반복해서 당한 모양이다. 하한을 provenance 와 함께 박는다.
# 🔴 **도메인이 안 뜬 표면은 찌르지 않는다.** 그건 이미 위에서 실패로 세어졌고, 여기서
#    또 세면 한 결함이 두 줄로 보고돼 원인이 두 개인 것처럼 읽힌다.
#
# 🔴🔴 TASK-MONO-583 — **찌를 것만 찌른다.** `ADR-MONO-067` 이 일부 화면을 Vercel 로
#    옮겼고, 그 화면은 **데모 호스트에 존재하지 않는다.** 그대로 두면 부팅 판정이
#    영원히 열리지 않는 표면을 12번 재시도하며 기다리고, 그 실패는 **"데모가 안 떴다"**
#    로 읽힌다. 그래서 마크업의 `data-served` 선언을 **여기서도 읽어** 데모 호스트 행만
#    찌른다(선언의 출처는 여전히 한 벌이다 — 두 벌이면 하나만 고쳐진다).
# 🔴 선언이 없거나 모르는 값인 행은 **판정 불가**다. 조용히 건너뛰면 새 출처가 생겼을 때
#    그 화면이 부팅 판정에서 **소리 없이 빠진다**.
# -----------------------------------------------------------------------------
SURFACE_SRC="${DEMO_SURFACE_SRC:-$HERE/aws/site/index.html}"
# 하한 ①: **론처가 약속하는 화면의 총 수** = 3 (console · web.ecommerce · web.fan-platform,
# 2026-08-21 전수). 서빙 출처가 갈려도 줄지 않는다 — 이 값은 «추출이 깨졌는가» 를 잰다.
# 가드 (z14) 의 `z14_floor` 와 **같은 축·같은 값**이다.
SURFACE_ROW_FLOOR="${DEMO_SURFACE_ROW_FLOOR:-3}"
# 하한 ②: **부팅 때 실제로 찌를 표면의 수** = 2 (console · web.fan-platform).
# provenance: 위 3 에서 web.ecommerce 를 뺀 값 — TASK-MONO-583 이 그 행을 Vercel 로
# 옮겼고(ADR-MONO-067 단계 2), Vercel 표면은 데모 호스트가 꺼져 있어도 뜨므로 부팅
# 완료의 증거가 될 수 없다. 🔴 **다른 축이다** — 화면이 늘어도 그것이 Vercel 이면
# 여기는 안 오른다. 데모 호스트 화면이 늘 때만 올려라.
SURFACE_FLOOR="${DEMO_SURFACE_FLOOR:-2}"
SURFACE_ATTEMPTS="${DEMO_SURFACE_ATTEMPTS:-12}"
SURFACE_SLEEP="${DEMO_SURFACE_SLEEP:-10}"

surfaces=(); surf_rows=0; surf_badsrc=()
while IFS= read -r sline; do
  sdom="$(printf '%s' "$sline" | sed -n 's/.*data-domain="\([^"]*\)".*/\1/p')"
  [ -n "$sdom" ] || continue
  surf_rows=$((surf_rows + 1))
  ssrc="$(printf '%s' "$sline" | sed -n 's/.*data-served="\([^"]*\)".*/\1/p')"
  shost="$(printf '%s' "$sline" | sed -n 's/.*data-host="\([^"]*\)".*/\1/p')"
  case "$ssrc" in
    demo-host)
      if [ -n "$shost" ]; then surfaces+=("$sdom $shost"); else surf_badsrc+=("$sdom:host없음"); fi ;;
    vercel)    : ;;   # 데모 호스트에 없다 — 찌르지 않는다(그것이 이관의 목적이다)
    *)         surf_badsrc+=("$sdom:출처='${ssrc:-없음}'") ;;
  esac
done < <(grep '<a [^>]*data-surface' "$SURFACE_SRC" 2>/dev/null)

surf_bad=(); surf_undecidable=(); surf_skipped=(); surf_ok=()
if [ "$surf_rows" -lt "$SURFACE_ROW_FLOOR" ]; then
  surf_undecidable+=("행추출:${surf_rows}/${SURFACE_ROW_FLOOR}")
elif [ "${#surf_badsrc[@]}" -gt 0 ]; then
  # 🔴 모르는 출처를 건너뛰기로 처리하면 그 화면이 판정에서 조용히 빠진다.
  surf_undecidable+=("출처선언:${surf_badsrc[*]}")
elif [ "${#surfaces[@]}" -lt "$SURFACE_FLOOR" ]; then
  surf_undecidable+=("추출:${#surfaces[@]}/${SURFACE_FLOOR}")
elif ! command -v curl >/dev/null 2>&1; then
  # 🔴 계측기가 없는 것과 표면이 죽은 것은 다른 사건이다.
  surf_undecidable+=("curl-없음")
elif [ -z "${DEMO_DOMAIN:-}" ] || [ "${DEMO_DOMAIN}" = "local" ]; then
  # AWS 밖이다 — 호스트명이 해석되지 않으므로 **잴 수 없다.** 조용히 넘어가지 않는다.
  echo "[demo] ◑ HTTP 표면 검사 건너뜀 — DEMO_DOMAIN='${DEMO_DOMAIN:-}' (AWS 밖에서는 호스트명이 해석되지 않습니다)"
else
  for entry in "${surfaces[@]}"; do
    sdom="${entry%% *}"; shost="${entry#* }"
    skip=0
    for d in "${still[@]}" "${undecidable[@]}"; do [ "$d" = "$sdom" ] && skip=1; done
    if [ "$skip" = 1 ]; then surf_skipped+=("$shost($sdom)"); continue; fi

    code=""; n=0
    while [ "$n" -lt "$SURFACE_ATTEMPTS" ]; do
      n=$((n + 1))
      # 🔴 리다이렉트를 **따라가지 않는다.** 콘솔은 `/login` 으로 302 를 내고 그것이
      #    정상 응답이다(AC-1: "2xx/3xx 를 낸다"). 따라가면 실패 모드만 늘어난다.
      code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "http://${shost}.${DEMO_DOMAIN}/" 2>/dev/null || echo 000)"
      case "$code" in 2??|3??) break ;; esac
      [ "$n" -lt "$SURFACE_ATTEMPTS" ] && sleep "$SURFACE_SLEEP"
    done
    case "$code" in
      2??|3??) surf_ok+=("$shost=$code") ;;
      *)       surf_bad+=("$shost.${DEMO_DOMAIN}=$code") ;;
    esac
  done
  # 🔵 `if` 로 쓴 이유는 **가독성**이다. 초판 주석은 이것을 `set -e` 함정이라고 적었는데
  #    **틀렸다 — 재봤다**: AND 리스트의 앞 명령이 실패해도 `set -e` 는 발동하지 않는다
  #    (`set -euo pipefail; a=(); [ "${#a[@]}" -gt 0 ] && echo x; echo 도달` → 도달한다).
  #    다만 그 형태가 **스크립트의 마지막 문장**이 되면 종료코드가 1 이 되므로, 이 블록이
  #    나중에 파일 끝으로 옮겨질 때를 대비한 보험이기도 하다. 틀린 근거는 남기지 않는다.
  if [ "${#surf_ok[@]}" -gt 0 ]; then
    echo "[demo] ✔ HTTP 표면 ${#surf_ok[@]}/${#surfaces[@]}: ${surf_ok[*]}"
  fi
fi

final_rc=0
if [ ${#still[@]} -gt 0 ]; then
  final_rc=1
  echo "[demo] ✖ 기동 실패 도메인: ${still[*]}" >&2
  for p in "${still[@]}"; do
    echo "[demo]   $p — 받은 재시도 ${retries_used[$p]:-0}회 / 예산이 막은 재시도 ${denied[$p]:-0}회" >&2
  done
fi
if [ ${#undecidable[@]} -gt 0 ]; then
  final_rc=1
  echo "[demo] ✖ 판정 불가 도메인: ${undecidable[*]} — 재측정이 상태를 돌려주지 않았습니다" >&2
  echo "[demo]   (도커가 응답하지 않았거나 demo-status.sh 가 실패했습니다. '늦게 수렴' 으로 읽지 않습니다.)" >&2
fi
if [ ${#late[@]} -gt 0 ]; then
  # 🔵 초록이지만 침묵하지 않는다 — compose 는 포기했는데 스택은 수렴했다는 사실 자체가
  #    healthcheck 창이 빠듯하다는 신호다.
  echo "[demo] ◑ 늦게 수렴: ${late[*]} — 기동 중에는 실패로 기록됐으나 재측정에서 up 입니다(실패로 세지 않습니다)"
fi

if [ ${#surf_bad[@]} -gt 0 ]; then
  final_rc=1
  echo "[demo] ✖ HTTP 표면 미도달: ${surf_bad[*]}" >&2
  echo "[demo]   (도메인은 up 인데 방문자가 여는 주소가 응답하지 않습니다 — 컨테이너 헬스로는 안 보이는 상태입니다.)" >&2
fi
if [ ${#surf_undecidable[@]} -gt 0 ]; then
  final_rc=1
  echo "[demo] ✖ HTTP 표면 판정 불가: ${surf_undecidable[*]}" >&2
  echo "[demo]   (표면 목록을 못 읽었거나 계측기가 없습니다. '표면이 정상' 으로 읽지 않습니다.)" >&2
fi
if [ ${#surf_skipped[@]} -gt 0 ]; then
  echo "[demo] ◑ HTTP 표면 미검사: ${surf_skipped[*]} — 해당 도메인이 위에서 이미 실패로 보고됐습니다"
fi

# 🔴 AC-2 — 예산 고갈은 **AC-1 의 재측정 결과와 무관하게** 남긴다.
#    A 의 고침이 B 의 유일한 증상을 지우기 때문이다: 예산이 없어 재시도를 못 받은
#    도메인도 어차피 나중에 수렴하므로 위에서 "늦게 수렴" 초록이 되고, 그러면 운영자는
#    배분이 빠듯했다는 사실을 **알 방법이 없다.**
if [ ${#denied[@]} -gt 0 ]; then
  for p in "${!denied[@]}"; do
    echo "[demo] ⚠ 재시도 배분: $p 는 예산이 없어 재시도 ${denied[$p]}회를 못 받았습니다(느려서가 아닙니다)" >&2
  done
  echo "[demo]   ⇒ 재시도 총 상한 = 도메인 ${n_domains}개 × ${UP_RETRY_SLEEP}s. 이 값이 빠듯하면 UP_RETRY_SLEEP 이나 healthcheck 창을 보세요." >&2
fi

[ "$domain_seed_rc" -eq 0 ] || final_rc=1
[ "$drift_rc" -eq 0 ] || final_rc=1
[ "$seed_rc"  -eq 0 ] || final_rc=1
exit "$final_rc"
