# Task ID

TASK-MONO-779

# Title

데모 이벤트 릴레이가 **한 번의 기동 호출에 iam·ecommerce·wms·scm 넷이 다 있을 때만** 뜬다 — 도메인을 나중에 더하면(`/domain/start` · 묶음 추가) 릴레이 없이 조용히 돈다

# Status

ready

# Owner

monorepo

# Task Tags

- demo
- infra

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — `demo-up.sh` 릴레이 블록과 `demo-down.sh` 대칭부, 가드 한 칸. 판단은 «넷이 모일 때마다 띄운다» 하나다.

---

# Dependency Markers

- 출처: 24차 데모 창(2026-10-08 UTC, `ami-01f1b4b56e4f9e51a`) — `TASK-MONO-765` AC-3 측정 중 발견.
- 선행 기록: `TASK-MONO-760`(done) § 정적 분석 표 «🔴 릴레이 — 기동 | 창 의존» 이 코드에서 **추론**만 했던 것을 이 창이 **관측**했다.
- 관련: `TASK-MONO-511`(릴레이 도입) · `ADR-MONO-062` B · `TASK-MONO-553` A(릴레이 격리).

# Goal

`infra/demo/demo-up.sh:363-381` 은 **한 번의 호출**에 `RELAY_DOMAINS`(`projects.sh:106` — iam ecommerce wms scm)가 모두 있어야 릴레이(`docker-compose.relay.yml`)를 띄운다. 묶음 기동(`/bundle/start`)은 방문자가 고른 묶음만 올리므로 기본 부팅에 scm 이 빠지는 일이 흔하고, 그 뒤 `/domain/start scm` 이나 묶음 추가로 넷이 **모여도** 릴레이는 뜨지 않는다. 그동안 크로스프로젝트 이벤트(iam→ecommerce 계정 · ecommerce→wms 풀필먼트 · wms→ecommerce · wms→scm · scm→wms)는 **한 건도 건너가지 않고 에러도 없다**.

24차 창 관측(2026-10-08 UTC):

| 시각 | 사실 |
|---|---|
| 15:20 부팅 | 선택 = iam · ecommerce · wms → 데모 로그 «⚠ 이벤트 릴레이 생략 — 이번 기동에 없는 도메인: scm» |
| 16:11 · 17:5x | `/domain/start erp` · `/domain/start scm` — 넷이 모였지만 릴레이 없음(`docker ps -a` 에 `demo-event-relay` 0) |
| 18:1x | 시드 주문 3건의 `ecommerce.fulfillment.requested.v1` = ecommerce-kafka **3** · wms-kafka **0** |
| 18:14 | 소유자가 SSM 으로 `docker compose -p relay -f infra/demo/docker-compose.relay.yml up -d` → 1분 안에 wms-kafka 3 · 소비 lag 0 · 출고 주문 3건 생성 |
| 18:32 재기동 | 묶음 `console · store · store-fulfillment · console-wms · console-scm` 로 켜자 부팅 로그에 릴레이가 같이 떴다(대조군) |

🔴 이 상태는 **다른 결함처럼 보인다** — 765 AC-3 를 «DLT 로 안 갔다 = 통과» 로 읽을 뻔했다(요청이 wms 에 **도착하지 않아서** 안 간 것).

# Scope

## In Scope

- 도메인을 올리는 모든 경로(`demo-up.sh <set>` · `domain_start` → `demo-boot.sh <domain>` · 묶음 추가)가 끝날 때 **지금 떠 있는 도메인 집합**을 보고 넷이 모였으면 릴레이를 올린다(이미 떠 있으면 no-op — `up -d` 는 멱등).
- `demo-down.sh:72-82` 의 대칭: 넷 중 하나를 내려 릴레이를 내렸다가 다시 올리면 릴레이도 다시 뜬다.
- 정적 가드 한 칸(데모 래퍼 스모크): «릴레이 기동 판정이 이번 호출의 `SET` 이 아니라 **떠 있는 도메인**을 본다» 를 문장 단위로 고정.

## Out of Scope

- 릴레이 healthcheck 가 `iam-kafka` 만 보는 것(`docker-compose.relay.yml:51`) — 별도 판단.
- 릴레이가 넷 미만에서도 부분적으로 도는 구조(external 네트워크 참조라 불가 — `demo-up.sh` 주석).

# Acceptance Criteria

- [ ] **AC-0** — 착수 시 재측정: 릴레이를 띄우는 곳 · 내리는 곳 · `domain_start` 가 부르는 경로(file:line 표).
- [ ] **AC-1** — 위 In Scope 1·2 구현. 넷이 모이는 순서 세 가지(한 번에 · 나중에 scm · 나중에 iam)에서 릴레이가 뜬다는 것을 래퍼 스모크(또는 셸 단위 시험)로 보인다.
- [ ] **AC-2** — 넷이 안 모였을 때는 지금처럼 «⚠ 이벤트 릴레이 생략» 을 말한다(침묵 금지 유지).
- [ ] **AC-3** — 가드 bite 1회(판정을 `SET` 기준으로 되돌리면 빨강).
- [ ] **AC-4** — ⚪ 재굽기 창: 기본 묶음으로 부팅 → `/domain/start scm` → `demo-event-relay` healthy · 시드 풀필먼트가 wms 로 건너감.

# Related Specs

- `infra/demo/demo-up.sh` · `infra/demo/demo-down.sh` · `infra/demo/projects.sh` § RELAY · `infra/demo/docker-compose.relay.yml` · `infra/demo/relay/mm2.properties`

# Related Contracts

- 없음(데모 배선).

# Edge Cases

- 릴레이가 이미 떠 있을 때 다시 `up -d` — 컨테이너 재생성 없이 no-op 이어야 한다(MM2 오프셋 유지).
- 넷 중 하나가 unhealthy 로 떠 있는 동안 — 지금 규칙과 같게(`failed+=relay` 후 계속).

# Failure Scenarios

1. 판정을 이번 호출의 `SET` 으로 남긴다 — 이 티켓의 결함 그대로.
2. 릴레이를 매번 재생성한다 — MM2 가 처음부터 다시 복제해 소비자 dedupe 에 짐을 지운다.
3. 내릴 때만 고치고 올릴 때를 안 고친다 — 내림·올림 한 바퀴 뒤 다시 침묵.
