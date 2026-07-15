# Task ID

TASK-BE-511

# Title

Kafka 토픽 초기화 목록이 이벤트 계약과 드리프트 — 생산되는 토픽 다수가 미초기화(+DLQ 짝 없음)

# Status

review

# Owner

backend

# Task Tags

- code
- infra

---

# 🔴 발견 경위 (2026-07-15 라이브 풀스택 스윕)

auth-service 로그에 `Error while fetching metadata ... {auth.session.created=UNKNOWN_TOPIC_OR_PARTITION}`. 브로커의 실제 토픽 목록을 세어 보니, iam 이 **생산하는** 여러 토픽이 compose 의 `kafka-init` 명시 목록에 없어 **Kafka auto-create 로만 존재**하고 **DLQ 짝이 없다**. auto-create 를 끈 운영 클러스터에선 이 토픽 발행이 실패한다. 아래는 실측 스냅샷 — **착수 시 생산자·초기화 목록을 다시 세라(모집단 재측정, 내 목록을 물려받지 말 것).**

---

# Goal

`kafka-init`(양쪽 compose)의 토픽 생성 목록을 iam 이 **실제로 생산하는 토픽 집합**과 정합화하고, 각 메인 토픽에 대응하는 `.dlq` 를 빠짐없이 생성한다.

## 실측 근거 (2026-07-15, 재측정 대상)

**브로커에 존재하나 초기화 목록에 없어 auto-create 로만 생긴 토픽(=DLQ 짝 없음):**
`auth.session.created`, `tenant.created`, `tenant.updated`, `tenant.subscription.changed`

**코드가 생산하나(퍼블리셔 상수 grep) 초기화 목록/브로커에서 확인 필요한 추가 후보:**
`auth.session.revoked`, `auth.token.tenant.mismatch`, `security.pii.masked`, `account.roles.changed`, `tenant.suspended`, `tenant.reactivated`, `partnership.invited/accepted/suspended/reactivated/terminated`

→ `docker-compose.e2e.yml` 은 15 main 만, `docker-compose.yml` 은 14 main 만 명시. 이벤트 계약(`specs/contracts/events/*.md`)은 **30 토픽**을 선언. **초기화 목록이 계약·생산자 양쪽에 뒤처져 있다.**

# Root Cause

`kafka-init` 의 `TOPICS=(...)` 배열이 **하드코딩된 고정 목록**이고, 새 이벤트(테넌트 라이프사이클·파트너십·세션·PII 마스킹 등)가 추가될 때 함께 갱신되지 않았다. 가드가 없어 드리프트가 조용히 누적됨(선언↔진실 축).

# Scope

## In Scope
- iam 이 **실제로 생산하는** 토픽을 전수 재열거 — 퍼블리셔/아웃박스 relay 코드 + `specs/contracts/events/*.md` 를 **둘 다** 근거로. (내 스냅샷 목록은 가설이다. 재측정.)
- `docker-compose.e2e.yml` + `docker-compose.yml` 의 `kafka-init` 목록을 그 집합으로 갱신, 각 메인 토픽에 `.dlq` 동반 생성.
- 두 compose 의 목록이 **서로도 일치**하도록(현재 15 vs 14 로도 어긋남).
- 가능하면 드리프트 재발 방지 가드(생산자 상수 ↔ init 목록 대조 스크립트, 또는 계약 기반 생성) 검토 — 최소한 실패 시 눈에 띄게.

## Out of Scope
- 이벤트 계약 자체의 토픽 추가/삭제(계약이 SoT — 계약을 바꾸는 게 아니라 init 을 계약에 맞춤).
- outbox v2 relay 로직(정상).
- **소비만 하고 생산 안 하는** 외부 토픽까지 iam 이 만들 필요는 없음 — 생산 집합만 대상(과생성 금지).

# Acceptance Criteria

- [ ] **AC-0 (착수=재측정)**: 스택 기동 후 브로커 토픽 목록을 나열하고, 코드 퍼블리셔 상수 + 이벤트 계약과 대조해 **미초기화 생산 토픽 집합을 새로 산출**(내 스냅샷과 다르면 코드/브로커가 이긴다).
- [ ] iam 이 생산하는 모든 토픽이 `kafka-init` 에 **명시적으로** 생성되고(더 이상 auto-create 의존 없음), 각 메인에 `.dlq` 존재.
- [ ] `docker-compose.e2e.yml` 과 `docker-compose.yml` 의 토픽 목록이 상호 일치.
- [ ] 스택 재기동 후 로그에 `UNKNOWN_TOPIC_OR_PARTITION` 0.
- [ ] (권장) init 목록 ↔ 생산자 상수 드리프트 가드 또는 재발 시 감지 수단.

# Related Specs

> Before reading: `platform/entrypoint.md` Step 0.

- `specs/contracts/events/auth-events.md`, `account-events.md`, `session-events.md`, `tenant-events.md`, `partnership-events.md`, `security-events.md`, `admin-events.md`
- `specs/services/*/architecture.md` (각 서비스가 생산하는 이벤트)

# Related Contracts

- `specs/contracts/events/*.md` (7 파일 — 선언된 토픽이 초기화 목록의 정합 기준)

# Target Service

- infra (`docker-compose.e2e.yml`, `docker-compose.yml` 의 `kafka-init`)

# Edge Cases

- Kafka auto-create 가 켜져 있으면 로컬에선 결함이 **가려진다**(토픽이 생겨버림) — 운영/CI 에서 auto-create off 가정. 라이브 검증은 로그의 UNKNOWN_TOPIC 경고로.
- DLQ 는 파티션/보존이 메인과 다름(현재 DLQ=1 파티션, 30일 보존) — 새 DLQ 도 동일 규칙 적용.
- 파트너십/테넌트 이벤트는 admin-service 생산 — auth 뿐 아니라 전 서비스의 퍼블리셔를 봐야 함.

# Failure Scenarios

- 내 스냅샷 목록을 그대로 복붙하면 그사이 추가된 토픽을 또 놓친다(모집단 재측정 필수 — 이 결함의 근본이 "목록을 안 세고 물려받음").
- 소비 전용 토픽까지 만들면 과생성(무해하지만 정합성 흐림).

# Definition of Done

- [ ] AC-0 로 생산 토픽 집합 재산출
- [ ] 두 compose init 목록 정합 + DLQ 완비
- [ ] 재기동 시 UNKNOWN_TOPIC 0
- [ ] (권장) 드리프트 가드
- [ ] Ready for review
