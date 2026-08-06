# Task ID

TASK-BE-579

# Title

inventory-service · outbound-service 가 HTTP 를 **한 건도** 서빙하지 않는 상태로 갇힌다 (Kafka 컨슈머는 계속 돈다)

# Status

ready

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

- [ ] **AC-0 (재현)** — 재현 절차를 확정한다. 🔴 비결정적이므로 "한 번 정상" 은 증거가
      아니다. 최소 3회 기동 중 몇 회 갇히는지 **비율로** 적는다
- [ ] **AC-1 (계측 수단)** — 진단 이미지에 `jcmd` 를 넣거나
      `-Djdk.trackAllThreads=true` + 덤프 경로를 확보한다. 가상 스레드 스택을 **실제로**
      본 기록을 남긴다(이 티켓의 배경은 그것을 못 봐서 가설에 머물렀다)
- [ ] **AC-2 (원인)** — 막는 지점을 코드/라이브러리 수준으로 특정한다.
      "Kafka 리스너가 많아서" 는 상관이지 원인이 아니다
- [ ] **AC-3 (수정)** — 원인 제거. 제거가 불가하면 **fail-fast**(갇히면 죽어서 재기동)로
      전환한다. 조용히 매달리는 것보다 낫다
- [ ] **AC-4 (탐지)** — healthcheck 가 이 상태를 잡는지 확인한다. 🔴 현재 healthcheck 는
      `curl -fsS ... || exit 1` 에 `-m` 이 없어 **curl 프로세스가 컨테이너 안에 쌓인다**
      (실측: 6분 넘게 살아 있는 curl 2개). 타임아웃을 명시할 것
- [ ] **AC-5 (형제 확인)** — scm/erp/finance 서비스가 같은 조합(가상 스레드 + 다수 Kafka
      리스너)인지 세고, 같다면 같은 증상이 있는지 **측정한다**. 0건이면 "0건" 이라고 적는다

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

- [ ] 재현 비율 기록 + 가상 스레드 스택 증거
- [ ] 원인 특정 + 수정(또는 fail-fast) + 회귀 테스트
- [ ] healthcheck 타임아웃 명시
- [ ] 형제 서비스 측정 결과 기록
- [ ] Ready for review
