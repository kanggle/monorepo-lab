# Task ID

TASK-BE-579

# Title

inventory-service · outbound-service 가 HTTP 를 **한 건도** 서빙하지 않는 상태로 갇힌다 (Kafka 컨슈머는 계속 돈다)

# Status

review

# Owner

wms-platform

# Task Tags

- bug
- infra

---

# 배경 — `TASK-MONO-510` 이 발굴 (AC-8)

MONO-510 의 WMS 시드를 실기동 검증하다 재현했다. 이 결함이 MONO-510 의
원래 증상("ASN 생성이 500 으로 유지된다")을 낳은 상위 원인으로 보인다.

## 증상 (2026-08-06 실측)

컨테이너는 `Up` 이고 프로세스도 살아 있는데 **모든 HTTP 요청이 영원히 매달린다.**

```
docker exec wms-gateway-service wget -T 8 -qO- http://outbound-service:8084/actuator/health
  → wget: download timed out
docker exec wms-gateway-service wget -T 8 -qO- http://inbound-service:8082/actuator/health   (대조군)
  → {"status":"UP","groups":["liveness","readiness"]}
```

🔴 **인디케이터 문제가 아니다.** `/actuator/health/liveness`(순수 in-memory)와
존재하지 않는 경로 `/nope` 도 똑같이 매달린다. 즉 디스패처 이전에서 막힌다.

🔴 **토폴로지 문제도 아니다.** 같은 네트워크(`traefik-net` + `wms_wms-net`)에 있는
`inbound-service` · `master-service` 는 같은 순간 즉답한다. JWKS(`iam-auth-service:8081`)도
갇힌 컨테이너 **안에서** 정상 fetch 된다.

그러면서 **Kafka 컨슈머는 계속 동작한다** — 파티션 할당·하트비트 로그가 계속 찍힌다.
그래서 "죽었다" 로 보이지 않고, 로그만 보면 정상으로 읽힌다.

## 🔴 재현율 — `TASK-BE-580` 착수 중 추가 측정 (2026-08-06)

BE-580 을 검증하며 wms 스택을 **볼륨 삭제 후 신선 기동 4회** 했다. **4회 모두**
`outbound-service` 또는 `inventory-service`(때로는 둘 다)가 갇혔다.

| 회차 | 갇힌 서비스 |
|---|---|
| 1 | inventory |
| 2 | inventory + outbound |
| 3 | outbound |
| 4 | outbound + inventory |

⇒ **"가끔" 이 아니다.** AC-0 이 요구하는 비율은 이 표를 출발점으로 삼되, 신선 기동뿐
아니라 **재기동 후 재발**까지 세야 한다: `restart` 로 살아난 outbound 가 **몇 분 안에
다시 갇혔다**(실측 2회). 살아 있는 창을 잡으려면 3초 간격 폴링이 필요했고 살아난 시점은
16번째 폴링(≈48초)이었다.

## 🔴🔴 Docker 는 갇힌 동안에도 `healthy` 라고 보고한다

`docker ps` 가 `Up 4 minutes (healthy)` 를 내는 **바로 그 순간** 같은 컨테이너에 대한
직접 HTTP 프로브가 **타임아웃**했다(실측).

원인은 healthcheck 설정이다: `interval: 15s` · `retries: 12` ⇒ 12회 연속 실패해야
`unhealthy` 로 넘어간다 = **최대 3분간 거짓 초록**. 그 3분 동안 게이트웨이는 504 를 내고
운영자는 "헬스는 초록인데 왜 504냐" 를 보게 된다.

⇒ AC-4 의 성질이 바뀐다. "healthcheck 가 이 상태를 잡는가" 는 **결국 잡는다** 이지만
**늦게** 잡고, 그 지연 동안 상태 보고가 **틀린다**. 판정 근거로 `docker ps` 의 health 를
쓰지 말 것 — 직접 HTTP 를 물어라. [[env_empty_detector_output_is_not_absence]]

## 갇히는 방식

🔴 **기동 실패가 아니다 — 떴다가 나중에 갇힌다.** 같은 컨테이너를 실측:

```
기동 +3분   wms-inventory-service   Up (healthy)
기동 +9분   wms-inventory-service   Up (unhealthy)   ← 그 사이 아무 요청도 안 보냈다
```

첫 기동에서 바로 갇히는 경우도 있었다(비결정적). 그리고 **스스로 회복하지 않는다** —
healthcheck 는 retries 12 를 전부 소진하고 계속 실패한다.

🔵 **단독 `docker compose restart <서비스>` 로는 복구된다**(실측 2회). 즉 데이터·설정이
아니라 **런타임 상태**의 문제다.

## 계측된 것

| 서비스 | `@KafkaListener` | 관측 결과 |
|---|---|---|
| outbound-service | 16 | 갇힘 |
| inbound-service | 11 | 정상 |
| inventory-service | 10 | 갇힘 |
| admin-service | 5 | 정상 |
| master-service | 0 | 정상 |

상관은 있으나 **단조롭지 않다**(inbound 11 은 정상인데 inventory 10 은 갇힘) —
어노테이션 수는 컨슈머 인스턴스 수가 아니므로(`concurrency` 설정) 이 표는
**가설의 근거이지 결론이 아니다.**

스레드 덤프(`kill -3`, 갇힌 outbound):

- `http-nio-8084-Acceptor` · `http-nio-8084-Poller` 는 **둘 다 RUNNABLE** —
  연결은 정상적으로 accept 된다.
- `spring.threads.virtual.enabled: true` 라 요청은 가상 스레드에서 실행된다.
  캐리어 16개가 `Carrying virtual thread`, 6개가 `ForkJoinPool.awaitWork` 로 **놀고 있다**
  → 단순한 캐리어 고갈로는 설명되지 않는다.
- `BLOCKED` 8개는 전부 `ConsumerCoordinator` 모니터 경합(Kafka 내부)이다.

🔴 **가상 스레드의 스택은 SIGQUIT 덤프에 나오지 않는다.** 그래서 "요청 스레드가
무엇을 기다리는가" 는 이 덤프로는 **알 수 없다** — 이 티켓이 그것을 밝히는 일이다.

---

# 🟢 착수 (2026-08-06) — 원인 규명 완료

## ⓪ 계측 수단 확보 (AC-1) — jcmd 를 **런타임 이미지에 안 넣고** 붙였다

런타임은 `eclipse-temurin:21-jre-alpine` 기반이라 `jcmd` 가 없고, `bin/jcmd` 만
복사해도 **`jdk.jcmd` 모듈이 jlink 되어 있지 않아** 뜨지 않는다
(`FindException: Module jdk.jcmd not found`). 이미지를 바꾸지 않고 붙이는 법:

```bash
docker run --rm --pid=container:wms-outbound-service --user 100:101 \
  eclipse-temurin:21-jdk-alpine \
  sh -c 'jcmd 7 Thread.dump_to_file -format=json -overwrite /proc/7/root/tmp/vt.json'
docker cp wms-outbound-service:/tmp/vt.json ./vt.json
```

PID 네임스페이스를 공유하고 **대상과 같은 UID(`app`=100)** 로 실행하면 JDK 21 jcmd 가
`/proc/<pid>/root/tmp` 를 통해 컨테이너 안 JVM 에 attach 한다. 이미지·재기동 불필요.

## ① 재현 (AC-0) — 그리고 **1차 계측기는 틀렸다**

🔴 1차 폴러는 3초마다 `docker exec` 를 새로 띄웠고, 9분간 ~350회가 되자 Windows 호스트의
**Hyper-V 소켓이 고갈**되어 daemon 오류가 났다. 그것을 "서비스 갇힘" 으로 읽었다 —
`/nope` 를 다시 물으니 **401 을 정상 반환**했다. 판정에 필요한 술어는 "요청이 실패했나" 가
아니라 **"서비스가 응답을 안 했나"** 이고, 둘은 구분돼야 한다
([[env_hyperv_socket_exhaustion_docker_churn]] · [[env_empty_detector_output_is_not_absence]]).

수정: `docker exec` 를 **1회만** 띄우고 루프를 컨테이너 안에서 돌리며, wget 결과를
`ok` / `HANG`(timed out) / `NETERR` 3분류하고 대조군(inbound)을 **같은 exec 안에서**
함께 찍는다. 이 계측기로:

```
재시작 후 90초   outbound = wget: download timed out
같은 exec, 같은 순간   inventory = {"status":"UP"}   inbound = {"status":"UP"}
```

## ② 🔴🔴 원인 (AC-2) — 로그 한 줄이 스케줄러를 잠근다

갇힌 outbound 의 가상 스레드 덤프(110 스레드):

```
tomcat-handler-N   19개 중 18개가 **스택이 비어 있다** — 생성·큐잉됐고 한 번도 마운트된 적 없음
kafka-N (가상)      4개가 VirtualThread.parkOnCarrierThread  ← 캐리어를 붙든 채 park = 핀
ForkJoinPool-1     17 워커 (파킹된 캐리어 + 보상 스레드)
```

핀된 4개의 스택은 전부 같다:

```
VirtualThread.parkOnCarrierThread
  ReentrantLock.lock
    ch.qos.logback.core.OutputStreamAppender.writeBytes(:211)
      org.apache.kafka.common.utils.LogContext$LocationAwareKafkaLogger.info
        SubscriptionState.maybeSeekUnvalidated(:398)
          ... ConsumerNetworkClient.poll → KafkaConsumer.position
            KafkaMessageListenerContainer$ListenerConsumer.seekPartitions(:1296)
              ConsumerCoordinator.onJoinComplete(:424)      ← 리밸런스 중
```

인과:

1. `spring.threads.virtual.enabled: true` ⇒ Spring Boot 가 **Kafka 리스너 컨테이너를
   가상 스레드**에 올린다. 실측 증거: 스레드 이름이 `kafka-N`(`VirtualThreadTaskExecutor`)
   이고 클래식 `KafkaListenerEndpointContainer#N-0-C-1` 은 **0건**.
2. Kafka 클라이언트는 **자기 `synchronized` 구간 안에서 로그를 찍는다**(리밸런스·오프셋
   리셋). JDK 21 에서 `synchronized` 안의 가상 스레드는 **언마운트할 수 없다 = 핀**.
3. 그 상태로 로그백 appender 의 `ReentrantLock` 을 기다리면 **캐리어째로 park** 한다
   (`parkOnCarrierThread`).
4. 가상 스레드 스케줄러의 parallelism 기본값 = `availableProcessors` = **4**
   (컨테이너가 보는 CPU 4개). ⇒ **네 개면 캐리어가 전부 소진된다.**
5. 그리고 그 락을 **실제로 쥔 스레드도 가상 스레드**라 마운트될 수 없다 ⇒ 락이 영원히
   안 풀린다. **자가 회복 불가**(실측: 19분 경과 후에도 갇힘). `restart` 만이 복구.
6. Tomcat 의 Acceptor/Poller 는 **플랫폼 스레드**라 연결은 계속 accept 된다 → 요청마다
   가상 스레드가 생기지만 **하나도 실행되지 않는다**(빈 스택 18개). 그래서
   `/actuator/health/liveness` 도 `/nope` 도 매달린다.
7. Kafka 하트비트도 플랫폼 스레드라 계속 뛴다 ⇒ **로그와 `docker ps` 는 살아 보인다.**

🔵 이로써 배경의 "단조롭지 않다"(inbound 11 정상 / inventory 10 갇힘)가 설명된다 —
결정 변수는 리스너 **개수**가 아니라 **동시에 로그를 쓰는 핀된 스레드 수**다.

🔵 그리고 배경이 "캐리어 6개가 놀고 있어 고갈로 설명 안 됨" 이라 적은 것도 설명된다 —
노는 워커는 **보상(compensation) 스레드**이고, 스케줄러의 활성 병렬도는 이미
parallelism(4) 만큼 핀으로 차 있어 큐잉된 가상 스레드를 집어가지 못한다.

## ③ 🔴 healthcheck 가 장애의 일부였다 (AC-4)

```
health=healthy   failingStreak=11   (retries: 12 라 아직 unhealthy 아님)
Health check exceeded timeout (5s)  ← 로그 5건 전부
컨테이너 안에 살아 있는 curl 프로세스 = 11개
```

`curl -fsS ... || exit 1` 에 **`-m` 이 없다.** Docker 의 `timeout: 5s` 는 체크를
실패로 기록하지만 **자식 `curl` 을 항상 죽이지는 않는다** ⇒ 인터벌마다 curl 이 하나씩
쌓이고, 각각이 연결과 요청 스레드를 하나씩 물고 있다. **프로브가 장애의 일부가 된다.**
그리고 `retries: 12` 라 그 사이 내내 **`healthy` 로 보고된다**(실측 19분).

## ④ 형제 전수 (AC-5) — **wms 밖 0건**

모집단을 좁히지 않으려고 `application.yml` 이 있는 서비스 **44개 전부**를 세었다:

| | 개수 |
|---|---|
| 가상 스레드 언급 | 6 |
| `@KafkaListener` 보유 | 24 |
| **둘 다 (= 이 결함의 필요조건)** | **5 — 전부 wms** |

admin(6) · inbound(11) · inventory(10) · notification(11) · outbound(16).
scm/erp/finance/ecommerce/fan/iam 은 **0건**(가상 스레드를 켜지 않는다).

## ⑤ 수정 (AC-3) — 필요조건을 끊는다

**모든 root appender 를 `AsyncAppender`(`neverBlock=true`) 로 감쌌다.** 로그를 찍는
스레드는 인메모리 큐에 offer 만 하고, stdout 으로의 **블로킹 쓰기는 로그백 자신의 플랫폼
워커 스레드**에서 일어난다 ⇒ 3번(핀된 채 appender 락 대기)이 성립하지 않는다.
`neverBlock=true` 는 계약의 일부다 — 큐가 차면 **로그를 버린다**. 로그 한 줄을 잃는 것이
서비스를 잃는 것보다 낫다.

적용 기준은 **"리스너가 있나" 가 아니라 "가상 스레드가 켜져 있나"** 로 그었다 ⇒
wms 6개 서비스(master 포함). `gateway-service` 는 가상 스레드를 켜지 않아 조건 자체가
성립하지 않으므로 **제외했다**.

같이 한 것: healthcheck 의 `curl` 에 **`-m 5`**(compose 7곳 + Dockerfile 7곳).

🔵 **하지 않은 것도 적는다**: `jdk.virtualThreadScheduler.parallelism` 상향은 **넣지
않았다** — 핀 자체를 없애지 못하고 발생 확률만 낮추는 완화책이라, 원인을 끊은 위에
얹으면 회귀를 늦게 발견하게 만든다. JDK 24 의 JEP 491 이 `synchronized` 핀을 제거하지만
런타임 업그레이드는 이 티켓의 범위 밖이다.

## ⑥ 수정 후 라이브 검증

`outbound-service` · `inventory-service` 를 재빌드·재배포하고 **같은 계측기**로 관측:

| | 수정 전 | 수정 후 |
|---|---|---|
| 첫 HANG 까지 | **90초** | — |
| 관측 시간 | 갇힌 뒤 **19분 이상 자가회복 없음** | **25분** |
| 샘플 | — | **500** |
| HANG | 발생 | **0건** |
| NETERR | — | 15건 (전부 기동 직후 connect 불가 — 서비스 응답 여부와 무관) |

🔵 **정직하게**: 이 결함은 비결정적이므로 25분 무사고 **1회**가 "다시는 안 난다" 를
증명하지는 않는다. 다만 같은 조건에서 수정 전 인스턴스는 90초에 갇혔고 19분 뒤에도
갇힌 채였다 — 대조는 분명하다. 그리고 판단의 근거는 이 관측이 아니라 **덤프가 보여 준
인과**이며, 관측은 그 인과가 끊겼는지를 확인하는 역할이다
([[feedback_local_proves_behaviour_not_performance]]).

🔵 나머지 4개(admin·inbound·master·notification)는 같은 변경을 받았지만 **재배포·관측은
하지 않았다** — 재현되던 대상이 이 둘이었기 때문이고, 그 사실을 적어 둔다.

## 파급 — 조용하지 않다

- 게이트웨이가 **504** 를 낸다. 그런데 🔴 **쓰기는 성공해 있을 수 있다**: ASN 생성이
  504 를 받았는데 `inbound_db.asn` 에 `status=CREATED` 행이 실제로 있었고, 출고 주문도
  504 뒤 재시도에서 `409 ORDER_NO_DUPLICATE` 가 났다(= 첫 요청이 성공했다는 뜻).
  ⇒ 클라이언트가 504 를 "안 만들어졌다" 로 읽으면 **중복 생성**하거나 **없는 실패**를 보고한다.
- 콘솔의 해당 도메인 화면이 비거나 오류로 degrade 한다.

---

# Goal

`inventory-service` · `outbound-service` 가 기동 후 임의 시점에 HTTP 서빙을 멈추지
않는다. 멈추는 조건이 남아 있다면 **healthcheck 가 그것을 잡고 컨테이너가 재기동된다.**

---

# Scope

## In Scope

- 가상 스레드에서 요청이 무엇에 막히는지 규명 (`jcmd Thread.dump_to_file -format=json`
  이 필요하다 — 현재 런타임 이미지는 JRE 라 `jcmd`/`jstack` 이 **없다**)
- 원인 제거 또는 fail-fast 전환
- 회귀 테스트

## Out of Scope

- 게이트웨이 타임아웃 값 조정 — 증상 은폐다. 다만 위 "504인데 쓰기는 성공" 은
  별개로 다룰 값어치가 있다(멱등 키가 이미 있으므로 재시도는 안전해야 한다)
- 다른 도메인(scm/erp/finance) — 같은 패턴인지 미측정

---

# Acceptance Criteria

- [x] **AC-0 (재현)** — 절차 확정(§①). 🔴 **1차 계측기는 틀렸고 그것을 먼저 고쳤다** —
      `docker exec` 반복이 Hyper-V 소켓을 고갈시켜 daemon 오류를 "갇힘" 으로 읽었다.
      고친 계측기(exec 1회 + 대조군 동시 관측 + 3분류)로 **재시작 후 90초에 재현**.
      🔵 비율: 배경의 신선 기동 4/4 + 이번 재시작 1/1(수정 전). 수정 후는 AC-3 참조.
- [x] **AC-1 (계측 수단)** — 런타임 이미지를 **바꾸지 않고** JDK 사이드카
      (`--pid=container:` + 같은 UID)로 attach 해 `Thread.dump_to_file -format=json`
      을 얻었다(§⓪). 가상 스레드 스택을 **실제로 봤고** 그 덤프가 AC-2 의 근거다.
- [x] **AC-2 (원인)** — 특정했다(§②): **Kafka 클라이언트가 `synchronized` 안에서 로그를
      찍고 → 가상 스레드가 핀되고 → 동기 로그백 appender 락을 캐리어째 기다리며 →
      parallelism(=CPU 4) 만큼 소진되면 락 보유자마저 마운트될 수 없어 영구 교착.**
      "리스너가 많아서" 는 상관이었고, 실제 결정 변수는 **동시에 로그를 쓰는 핀된 스레드 수** 다.
- [x] **AC-3 (수정)** — 원인 제거(§⑤): 모든 root appender → `AsyncAppender(neverBlock=true)`.
      회귀 가드 `LoggingIsAsyncWhileVirtualThreadsAreOnTest` 를 해당 6개 서비스에 넣었고,
      🔴 **무는지 확인했다** — 동기 appender 로 되돌리면 FAILED, `neverBlock=false` 로만
      바꿔도 FAILED, 복구하면 PASS.
- [x] **AC-4 (탐지)** — 실측: `health=healthy` 인데 `failingStreak=11`, 컨테이너 안 curl
      **11개** 누적, **19분간 거짓 초록**(§③). `-m 5` 를 compose 7곳 + Dockerfile 7곳에
      명시했다. 🔵 정직하게: `-m` 은 curl 누적과 지연을 없애지만 `retries: 12` 자체는
      그대로라 **여전히 최대 ~3분의 거짓 초록 창**이 남는다. retries 를 줄이는 것은 CI
      e2e 기동 여유와 맞물려 있어 이 티켓에서 바꾸지 않았다.
- [x] **AC-5 (형제 확인)** — `application.yml` 이 있는 **44개 전수**를 셌다(§④).
      필요조건(가상 스레드 + `@KafkaListener`)을 만족하는 서비스는 **5개, 전부 wms** 다.
      scm/erp/finance/ecommerce/fan/iam **0건** — 좁은 술어의 0건이 아니라, 모집단을
      넓혀(가상 스레드 언급 6 · 리스너 보유 24) 교집합을 센 결과다.

---

# Related Specs

- `projects/wms-platform/specs/services/inventory-service/architecture.md`
- `projects/wms-platform/specs/services/outbound-service/architecture.md`
- `platform/service-types/` — 해당 서비스의 선언된 Service Type

# Related Contracts

- `projects/wms-platform/specs/contracts/http/outbound-service-api.md`
- `projects/wms-platform/specs/contracts/http/inventory-service-api.md`

# Edge Cases

- 갇힌 상태에서도 Kafka 컨슈머가 커밋을 계속하므로, 재기동해도 **이미 소비된 이벤트는
  다시 오지 않는다** — 그 사이 HTTP 로만 관측 가능하던 상태는 유실될 수 있다
- `docker compose restart` 로 복구되므로, 운영자가 그것을 먼저 해 버리면 증거가 사라진다.
  AC-0 은 **덤프를 먼저 뜨고** 재기동할 것

# Failure Scenarios

- **healthcheck 만 고치고 원인을 안 고침** → 컨테이너가 주기적으로 재기동되며 "동작하는
  것처럼" 보인다. 그 사이 인메모리 상태와 컨슈머 오프셋이 흔들린다
- **한 번 정상 기동으로 "해결됨" 판정** → 비결정적이라 반드시 재발한다. AC-0 이 막는다
- **게이트웨이 타임아웃을 늘려 덮음** → 504 는 사라지고 요청은 더 오래 매달린다

# Definition of Done

- [x] 재현 비율 기록 + 가상 스레드 스택 증거 (계측기를 먼저 고쳐야 했다)
- [x] 원인 특정 + 수정 + 회귀 테스트 (가드가 **무는 것** 확인)
- [x] healthcheck 타임아웃 명시 (`-m 5`; 남은 거짓-초록 창은 명시적으로 남겨 둠)
- [x] 형제 서비스 측정 결과 기록 (44개 전수 → wms 5개, 타 도메인 0건)
- [x] Ready for review
