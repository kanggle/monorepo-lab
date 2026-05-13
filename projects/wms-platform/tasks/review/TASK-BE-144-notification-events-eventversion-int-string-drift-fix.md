# Task ID

TASK-BE-144

# Title

`notification-events.md` `eventVersion` int-vs-string drift fix (refactor-spec critical finding — wire-format incompatibility)

# Status

review

# Owner

wms-platform

# Task Tags

- be
- notification-service
- contract
- event
- spec
- fix

---

# Goal

`/refactor-spec all --dry-run` (2026-05-13) WMS audit Top 1 critical finding: **`eventVersion` field type drift**.

- 5 event contracts (`master-events.md`, `inventory-events.md`, `inbound-events.md`, `outbound-events.md`, `admin-events.md`) 모두 `"eventVersion": 1` (integer) + 명시 `int`.
- **`notification-events.md` 만 `"eventVersion": "v1"`** (string).

같은 logical field 가 두 wire format. **cross-service consumer (admin-service 가 5 contract 모두 project) 는 envelope parser 를 공유 불가**. admin-service / search-service / 등 future consumer 가 type mismatch 로 Jackson deserialization fail 또는 unsafe runtime cast.

fix path = `notification-events.md` 의 envelope schema 를 5 sibling contract 와 일관성 갖도록 `integer` 로 정정. notification-service producer code 의 wire emission 확인 + 정정 (production code 변경 가능성).

provenance: WMS audit Top 1 finding. wire-format consistency contract violation.

---

# Scope

## In Scope

### A. `notification-events.md` envelope schema 정정

`projects/wms-platform/specs/contracts/events/notification-events.md` 의 envelope JSON + field table 에서 `"eventVersion": "v1"` → `"eventVersion": 1` (integer). 5 sibling contract 의 spec pattern 답습.

### B. notification-service producer code 검증 + 정정

`projects/wms-platform/apps/notification-service/src/main/java/com/wms/notification/.../...EventPublisher.java` (또는 outbox publisher) 의 emission 시 `eventVersion` 값이 string 인지 int 인지 확인. spec 과 mismatch 시 production code 정정. integration test 확인.

### C. notification-events 의 consumer 영향 검증

notification-events 의 consumer = admin-service (현재 v1 시점 알려진 consumer). admin-service IT 의 Jackson POJO 또는 deserializer 가 `eventVersion` 을 int 로 expect 확인. spec/code mismatch 가 production 에서 silent failure 인지 검증.

### D. envelope schema 공유 contract 후보 (검토)

5 sibling event contract + notification-events 가 동일 envelope shape (`eventId/eventType/eventVersion/occurredAt/producer/aggregateType/aggregateId/traceId/actorId/payload`) — 본 task scope 밖이지만 future shared schema 추출 후보 (WMS audit duplication #1 finding).

## Out of Scope

- envelope schema 추출 + shared contract file 생성 (별도 task 후보).
- notification-events 의 다른 field type 검토 (본 task = `eventVersion` 단일).
- 5 sibling event contract 변경.
- consumer side admin-service 의 implementation 변경 (검증 후 별도 task 필요 시).

---

# Acceptance Criteria

### Impl PR

- [ ] `notification-events.md` 의 envelope JSON 예제 `"eventVersion": "v1"` → `"eventVersion": 1` 정정.
- [ ] field table 의 `eventVersion` type column `string` → `int` (또는 `integer`) 정정.
- [ ] notification-service producer code (publisher) 의 emission 검증 + spec 과 일치 보장.
- [ ] PR-time wms `Integration (master-service + notification-service)` PASS.
- [ ] notification-service unit + IT regression 0.
- [ ] CI self-CI 16/16 PASS.
- [ ] task lifecycle ready → in-progress → review.
- [ ] wms tasks/INDEX.md 동기.

### Close chore PR

- [ ] review → done, wms tasks/INDEX.md 동기.

---

# Related Specs

- `projects/wms-platform/specs/contracts/events/notification-events.md` (수정 대상).
- `projects/wms-platform/specs/contracts/events/master-events.md` (sibling reference).
- `projects/wms-platform/specs/contracts/events/inventory-events.md` (sibling reference).
- `projects/wms-platform/specs/contracts/events/inbound-events.md` (sibling reference).
- `projects/wms-platform/specs/contracts/events/outbound-events.md` (sibling reference).
- `projects/wms-platform/specs/contracts/events/admin-events.md` (sibling reference).
- `platform/event-driven-policy.md` (canonical event policy).
- `/refactor-spec all --dry-run` 2026-05-13 WMS audit Top 1 finding (consistency #3).

# Related Skills

`.claude/skills/messaging/outbox-pattern/SKILL.md`.

---

# Related Contracts

본 task 가 `notification-events.md` contract 자체 정정. wire-format 일관성 회복.

---

# Target Service

`notification-service` (event publisher) + `admin-service` (consumer 영향 검증).

---

# Architecture

WMS notification-service 의 outbox event publishing. ADR-MONO-005 saga category C single-step retry+DLT 영역.

---

# Implementation Notes

## producer code 검증 path

```bash
grep -rn "eventVersion" projects/wms-platform/apps/notification-service/src/main/java/
```

만약 `setEventVersion("v1")` 또는 `eventVersion: "v1"` 등 string emission 발견 시 production code 변경 필요. integer `1` 사용 권장.

만약 envelope BaseEventPublisher / libs/java-messaging 의 generic class 가 emission 처리 시: code 무변경 + spec 만 정정 (spec drift 였음).

## admin-service consumer 영향

admin-service 가 notification-events 를 consume 하는지 검증 — WMS notification 은 push-only 일 수도 있음 (consumer 없음). consumer 부재면 wire-format mismatch 가 silent 였음 → spec 정정 만으로 충분.

`grep -rn "notification\." projects/wms-platform/apps/admin-service/` + IT log 검토.

## 메타 학습 — envelope shared schema 후보

WMS audit duplication #1 finding 이 6 event contract 의 envelope JSON 을 byte-identical 로 명시. shared `contracts/events/_envelope.md` 추출 후보 — 본 task 의 후속 follow-up. envelope drift 발생 시 즉시 catch 하는 single source of truth 보장.

---

# Edge Cases

- notification-events 의 envelope schema 본문 외 다른 file (architecture.md / domain-model.md) 에서 wire format 인용 시: 동기.
- admin-events 가 `int` 명시했지만 producer 가 string emission 인 hidden inverse case: 별도 audit.
- semver 형식 (`"1.0.0"`) 으로 향후 evolve 의도면 int → semver string 표준화 별도 spec 필요.

---

# Failure Scenarios

- producer code 가 string emission, integration test 가 catch 안 함: WireMock 또는 contract test 도입 후 retry.
- notification-events 의 다른 field type drift: separate finding, separate task.
- admin-service consumer 가 string expect: bi-directional fix + breaking change marker.

---

# Test Requirements

- notification-service unit test + IT PASS.
- WMS Integration (master + notification) PASS.
- spec 과 code emission 일치 검증.

---

# Definition of Done

### Impl PR

- [ ] AC 완료.
- [ ] task lifecycle ready → in-progress → review.

### Close chore PR

- [ ] review → done, wms tasks/INDEX.md 동기.

---

# Provenance

- `/refactor-spec all --dry-run` 2026-05-13 WMS audit (43 file / 43 finding) Top 1 risk-weighted finding (cross-service consumer wire-format incompatibility).
- 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (1-line spec edit + producer code verify, 변경 작음).
