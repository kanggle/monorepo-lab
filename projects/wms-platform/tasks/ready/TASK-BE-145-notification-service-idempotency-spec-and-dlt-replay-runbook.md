# Task ID

TASK-BE-145

# Title

`notification-service/idempotency.md` + `notification-service/runbooks/dlt-replay.md` authoring — Open Items #4/#5 backfill (refactor-spec all 2026-05-13~14 WMS critical #1+2)

# Status

ready

# Owner

wms-platform

# Task Tags

- wms
- notification-service
- spec
- runbook
- backfill

---

# Goal

`/refactor-spec all --dry-run` (2026-05-13~14) WMS audit critical #1+2 finding closure.

`projects/wms-platform/specs/services/notification-service/architecture.md` 의 § Open Items (L451-468) 가 2 file 가리키나 미존재:

- **#4** `specs/services/notification-service/idempotency.md` — dedupe + delivery key + retry budget tabulated
- **#5** `specs/services/notification-service/runbooks/dlt-replay.md` — DLT drain + manual delivery retry procedure

본 task = 2 file 신규 authoring + Open Items checkbox 갱신.

content source:
- architecture.md § Idempotency (L269-285) — inbound Kafka eventId dedupe + outbound delivery key
- architecture.md § Concurrency Control (L288-294) — JPA optimistic lock + SKIP LOCKED
- architecture.md § Key Domain Invariants (L298-306) — attempt_count cap, state transition, idempotency uniqueness
- architecture.md § Persistence (L310-321) — table schema reference
- architecture.md § Kafka Consumption (L181-188) — consumer group + DLT convention
- architecture.md § Observability (L325-) — metrics

sibling pattern source:
- `projects/wms-platform/specs/services/master-service/idempotency.md` — HTTP request idempotency pattern (다른 영역이지만 file 구조 답습)

provenance: `/refactor-spec all --dry-run` 2026-05-13~14 WMS audit critical #1+2 (notification-service Open Items #4 / #5 — 미존재 file reference 가 spec 의 internal commitment 와 어긋남, TASK-BE-043 bootstrap 머지 후 backfill 안 됨).

---

# Scope

## In Scope

### A. `notification-service/idempotency.md` 신규 authoring

신규 file `projects/wms-platform/specs/services/notification-service/idempotency.md`. content = consumer-pattern (event-driven) idempotency 명세. master-service idempotency.md (HTTP request pattern) 와 다른 영역 — event-id 기반 dedupe.

section 후보:
1. **Scope** — 본 file 의 적용 범위 (event consumption + channel dispatch)
2. **Inbound idempotency (Kafka)** — `notification_event_dedupe` table schema + outcome enum (QUEUED / FILTERED) + 30-day retention + replay 동작
3. **Outbound idempotency (channel)** — `delivery_idempotency_key = sha256(eventId + channelId + recipient)` + per-row `attempt_count` 의 in-place increment
4. **Retry budget** — `attempt_count ≤ max_attempts (5 in v1)` + scheduled_retry_at + exponential backoff + DLT terminal
5. **Concurrency control** — JPA `@Version` + `SELECT … FOR UPDATE SKIP LOCKED`
6. **Failure modes** — Postgres 장애 (fail-closed retry), channel adapter 장애 (DLPR vs row terminal), DB row level lock starvation 등
7. **Observability** — Micrometer metric (`notification.dedupe.outcome` / `notification.delivery.attempt` / `notification.delivery.terminal`) + log + trace span attributes
8. **Testing requirements** — happy / replay / DLT / Postgres outage / Slack 5xx 시나리오
9. **References** — architecture.md / rules/traits/transactional.md / ADR-MONO-005

### B. `notification-service/runbooks/dlt-replay.md` 신규 authoring

신규 file `projects/wms-platform/specs/services/notification-service/runbooks/dlt-replay.md`. content = operator playbook for DLT drain + manual redelivery. directory `runbooks/` 자체도 신규.

section 후보:
1. **Purpose** — DLT routing 동작 요약 + 본 runbook 의 use case (vendor 장애 후 backlog drain / 잘못된 routing rule 으로 정체된 event 복구)
2. **Prerequisites** — Kafka CLI / DB 접근 권한 + 운영자 role
3. **Identify DLT backlog** — `kafka-consumer-groups.sh` 명령 + 토픽별 message count 확인
4. **Diagnostic checklist** — root cause 분류 (vendor outage / malformed event / unknown routing rule / dedupe conflict)
5. **Replay procedure** — 시나리오별 (a) vendor outage post-recovery replay / (b) manual delivery retry via DB row update / (c) DLT message 제거 + outbox forge
6. **Verification** — `notification_delivery.status` 갱신 확인 + observability metric (`delivery.success.count`) 추적
7. **Rollback / safety** — replay 가 unique constraint 충돌 시 (`delivery_idempotency_key` UQ) → dedupe table 결과 보존
8. **References** — architecture.md / on-call escalation

### C. Open Items checkbox + architecture.md align

architecture.md L451-468 Open Items 영역:
- #4 `idempotency.md` → 신규 file 추가 후 ✅ 표기 (또는 별 정정 — admin/inbound sibling 의 ✅ 패턴 답습)
- #5 `runbooks/dlt-replay.md` → 동일 ✅

## Out of Scope

- production code 변경 (notification-service 의 dedupe / retry / DLT 구현 무관 — 모두 BE-043 머지 완료된 production code, spec 만 backfill).
- Open Items #1/#2/#3 (이미 완료된 file: domain-model.md / notification-subscriptions.md / notification-events.md) 영역.
- Open Items #6 (`platform/error-handling.md` 의 5 error code registry) — 이미 등재 가능성, 별 audit.
- 다른 service 의 idempotency.md / runbooks/ 신규 (별 task).

---

# Acceptance Criteria

### Impl PR

- [ ] `notification-service/idempotency.md` 신규 file authoring (~100-150 line, 9 section).
- [ ] `notification-service/runbooks/dlt-replay.md` 신규 file authoring (~60-80 line, 8 section, runbooks/ directory 신설).
- [ ] architecture.md § Open Items #4 + #5 → ✅ 표기 (admin/inbound sibling 패턴 답습).
- [ ] cross-ref 검증 — 본 2 file 이 architecture.md / rules / platform error-handling 등 정상 cite.
- [ ] HARDSTOP-03 hook PASS (project-specific content 잔존 0 — 본 file 은 wms-platform 자기 자신 service 영역).
- [ ] CI self-CI PASS (path-filter wms-platform markdown-only — 15 SKIP + 1 changes PASS 예상).
- [ ] task lifecycle ready → review (in-progress 우회, spec-only single-PR closure 패턴).
- [ ] wms tasks/INDEX.md 동기 (root INDEX 무영향).

### Close chore PR

- [ ] task Status review → done.
- [ ] git mv tasks/review → tasks/done.
- [ ] wms tasks/INDEX.md ## review 제거, ## done append 1-line outcome.

---

# Related Specs

- `projects/wms-platform/specs/services/notification-service/architecture.md` (source content for idempotency + DLT 영역, 부분 정정 대상).
- `projects/wms-platform/specs/services/master-service/idempotency.md` (sibling file structure pattern, 다른 영역 = HTTP request).
- `projects/wms-platform/specs/services/inbound-service/architecture.md` § Open Items (✅ marker sibling pattern).
- `projects/wms-platform/specs/services/admin-service/architecture.md` § Open Items (✅ marker sibling pattern).
- `rules/traits/transactional.md` T1 (idempotency) + T8 (dedupe).
- `platform/event-driven-policy.md` (consumer rules, DLT convention).
- `docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md` (Category C single-step retry+DLT reference, notification-service 가 이 카테고리 사례).

---

# Related Contracts

본 task = service-internal spec authoring only. HTTP API / event payload 변경 0. cross-service contract 무관.

---

# Target Service

`projects/wms-platform/apps/notification-service/` (spec backfill 대상, production code 무관).

---

# Architecture

WMS notification-service v1 의 internal idempotency + DLT replay 운영 spec 추출. production code 는 BE-043 머지 완료된 상태 — architecture.md 의 design 을 별 file 로 정리.

---

# Implementation Notes

## sibling Open Items ✅ marker 패턴

inbound-service architecture.md + admin-service architecture.md 의 § Open Items 영역이 모두 ✅ marker 사용 (audit finding 의 High-1 = "notification 만 ✅ 마커 없음" 회복 동시 처리).

## idempotency.md content source mapping

| section | source (architecture.md) |
|---|---|
| Inbound (Kafka) | L271-276 § Idempotency / Inbound |
| Outbound (channel) | L278-284 § Idempotency / Outbound |
| Retry budget | L302 `attempt_count ≤ max_attempts (5 in v1)` |
| Concurrency | L290-294 § Concurrency Control |
| Failure modes | L380-385 § Cross-Cutting Concerns (transactional T1/T3/T4/T8) |
| Observability | L325-? § Observability |
| Testing | L417-422 § Consumer (Testcontainers Kafka) |
| Persistence ref | L317-321 (table schema) |

## runbooks/dlt-replay.md content source mapping

| section | source (architecture.md) |
|---|---|
| DLT routing | L188 `<source-topic>.DLT per Spring Kafka convention. Operator drains` |
| Diagnostic | L380-385 (Cross-Cutting Concerns), Failure mode L429-431 |
| Replay procedure | L430-431 (vendor down → backlog drains) + ADR-MONO-005 Category C reference |
| Verification | L325-? § Observability metric |

## D4 churn impact

- 2 file 신규 (spec-only)
- ADR-MONO-003a § D1.1 IN-scope (B common rule cleanup 연장선) — D4 OVERRIDE 적용
- production code 무변경 → D2 시계 영향 0

---

# Edge Cases

- `runbooks/` directory 신설 — sibling service 들에 `runbooks/` 가 일반적인지 확인 spot-check (없을 수도, 본 service 가 첫 사례).
- architecture.md § Open Items 의 ✅ marker 가 task 자체 명시 (BE-043 spec 본문 안) — wms tasks/done/TASK-BE-043 정정 의무 vs out of scope 판단 (본 task scope 밖, architecture.md 영역만 정정).
- DLT replay 의 시나리오별 procedure 가 정확해야 함 — production code 의 실제 DLT routing 동작 확인 필요 (spot-check).

---

# Failure Scenarios

- idempotency.md 가 architecture.md design 과 어긋남 → spec drift. spot-check 강제.
- DLT replay runbook 의 명령 (kafka-consumer-groups.sh) 가 실제 wms operations 와 다름 (e.g. cluster 접근 방법) → 운영자 가 사용 불가. spec 본문에 "환경별 명령 차이는 운영자 환경 변수 참조" 명시.

---

# Test Requirements

- HARDSTOP-03 hook PASS.
- CI self-CI PASS (markdown-only path-filter — 자연 SKIP 가능).
- 2 신규 file 의 cross-ref 정상.
- production code = 0.

---

# Definition of Done

### Impl PR

- [ ] AC 완료.
- [ ] task lifecycle ready → review.

### Close chore PR

- [ ] review → done, INDEX 동기.

---

# Provenance

- `/refactor-spec all --dry-run` 2026-05-13~14 WMS audit critical #1+2 (notification-service Open Items #4 + #5 = 미존재 file reference).
- TASK-BE-043 (notification-service bootstrap, PR #269 spec + #276 impl + #279 chore close 2026-05-08) 의 backfill — Open Items 자체 정책 ("before any TASK-BE-* moves from tasks/ready/ to tasks/in-progress/") 위반된 상태로 머지됨.
- Sibling 답습 패턴: TASK-MONO-083 / TASK-BE-280/281 / TASK-SCM-BE-011 / TASK-MONO-084 / TASK-FAN-BE-006 — 모두 same-day single-PR closure 검증된 패턴.
- 분석=Opus 4.7 / 구현 권장=Opus 4.7 (spec authoring + cross-ref + 운영 playbook — judgment-heavy, mechanical 아님).
