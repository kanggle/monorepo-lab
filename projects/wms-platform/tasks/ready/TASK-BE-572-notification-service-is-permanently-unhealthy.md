# Task ID

TASK-BE-572

# Title

`wms-notification-service` 는 영구히 unhealthy 다 — 헬스체크가 존재하지 않는 HTTP 엔드포인트를 찌른다(웹 스타터가 없다)

# Status

ready

# Owner

wms-platform

# Task Tags

- backend
- infra
- observability

---

# 배경

`TASK-MONO-510`(백오피스 시드) AC-8 이 발굴했다.

## 실측 (2026-08-05)

```
$ docker ps
wms-notification-service   Up 4 minutes (unhealthy)      ← 부팅은 정상, 헬스만 실패

$ docker inspect ... State.Health.Log
"curl: (7) Failed to connect to localhost port 8085 after 0 ms: Could not connect to server"

$ docker exec wms-notification-service sh -c \
    "curl -s -o /dev/null -w '%{http_code}' http://localhost:8085/actuator/health"
000                                            ← 8080 도 000

$ docker logs wms-notification-service | grep "Started"
Started NotificationServiceApplication in 159.551 seconds
                                               ← "Tomcat started on port" 줄이 **없다**
```

## 원인 — 선언과 배선이 갈렸다

`apps/notification-service/build.gradle`:

```gradle
// Spring Boot starters — note: no `web` starter (no REST surface in v1).
// Actuator alone exposes /actuator/health; the rest of the surface is
// event-driven only (Kafka in / Kafka-via-outbox out).
implementation 'org.springframework.boot:spring-boot-starter-actuator'
```

🔴 **주석의 두 번째 문장이 틀렸다.** `spring-boot-starter-actuator` 는 **웹 서버를 띄우지
않는다** — `web`/`webflux` 스타터가 없으면 Spring Boot 는 non-web 애플리케이션으로 뜨고
actuator 는 HTTP 표면을 갖지 못한다. 그런데 `application.yml` 은 `server.port` 를 잡고
있고(`${SERVER_PORT:8085}`), compose 헬스체크는 그 포트로 `curl` 한다:

```yaml
healthcheck:
  test: ["CMD-SHELL", "curl -fsS http://localhost:8085/actuator/health || exit 1"]
```

⇒ **구조적으로 절대 통과할 수 없는 헬스체크.**

## 왜 지금까지 아무도 안 걸렸나

이 서비스는 아무도 라우팅하지 않는다(게이트웨이 라우트 없음, Traefik 라벨 없음). 그리고
`depends_on: service_healthy` 로 이것을 기다리는 서비스가 없어 스택은 정상적으로 뜬다.
그래서 **"항상 빨간 등" 이 되어 아무도 보지 않는 상태**가 됐다 — 이 저장소가 이름 붙인
실패 모드 그대로다(꺼진 가드는 없는 가드보다 나쁘다; 여기서는 *항상 빨간* 가드다).

---

# Goal

이 서비스의 헬스 상태가 **실제 상태를 말한다** — 살아 있으면 healthy, 죽었으면 unhealthy.

---

# Scope

## In Scope

- 헬스체크 술어 또는 웹 표면 중 하나를 실제와 맞춘다
- `build.gradle` 주석과 `server.port` 설정의 정합

## Out of Scope

- REST 표면 신설(이벤트 기반 설계 자체는 유지한다)

---

# 두 방향

| 안 | 모양 | 대가 |
|---|---|---|
| A. 헬스체크를 프로세스/컨슈머 기준으로 바꾼다 | `pgrep java` 또는 Kafka 컨슈머 랙 기반. 웹 서버를 안 띄운다 | 관측이 얕다 — "살아 있음" 만 알고 "일하고 있음" 은 모른다 |
| B. actuator 만 여는 **관리 포트**를 띄운다 | `spring-boot-starter-web` 추가 + `management.server.port` 분리. 주석의 의도가 그대로 실현된다 | 의존성 하나 늘고 포트 하나 연다 |

**B 가 주석이 원래 말하려던 것**으로 보인다("Actuator alone exposes /actuator/health").
어느 쪽이든 `server.port` 설정과 헬스체크가 **같은 사실**을 가리켜야 한다.

---

# Acceptance Criteria

- [ ] **AC-0 (재측정)** — 위 세 줄(컨테이너 unhealthy · 컨테이너 안 curl 000 ·
      "Tomcat started" 부재)을 다시 받는다. 그리고 **같은 모양의 형제가 더 있는지 전수로 센다**
      — 웹 스타터 없이 `server.port` + HTTP 헬스체크를 가진 서비스가 이 저장소에 또 있는가
- [ ] **AC-1 (헬스가 실제를 말한다)** — 정상 기동 시 `healthy` 가 된다
- [ ] **AC-2 (음성 대조)** — 서비스를 **일부러 망가뜨리면**(DB 연결 차단 등) `unhealthy` 가
      된다. 🔴 A 안을 고르면 이 항목이 특히 중요하다 — `pgrep` 은 죽은 컨슈머를 못 본다.
      양성만 단언하면 "항상 초록" 을 만들어 지금과 대칭인 결함이 된다
- [ ] **AC-3 (주석 정합)** — `build.gradle` 의 문장이 실제 동작과 일치한다

---

# Related Specs

- `projects/wms-platform/apps/notification-service/build.gradle`
- `projects/wms-platform/apps/notification-service/src/main/resources/application.yml`
- `projects/wms-platform/docker-compose.e2e.yml` (`notification-service` 헬스체크)

# Edge Cases

- 데모/E2E 는 이 서비스를 기다리지 않으므로 수정해도 기동 순서는 바뀌지 않는다 —
  반대로 말하면 **지금 고쳐도 아무것도 깨지지 않는다**(위험이 낮다)
- Slack 어댑터는 도달 불가 스텁 URL 로 서킷브레이커 degrade 상태가 정상이다 —
  헬스 지표를 거기 묶으면 안 된다

# Failure Scenarios

- **헬스체크를 지운다** — 상태를 말하지 않는 것은 틀린 상태를 말하는 것보다 낫지 않다
- **A 안 + 양성만 테스트** — 항상 초록이 되어 지금의 거울상이 된다

# Definition of Done

- [ ] 결정 + 구현
- [ ] AC-1/AC-2 양방향 증거
- [ ] Ready for review
