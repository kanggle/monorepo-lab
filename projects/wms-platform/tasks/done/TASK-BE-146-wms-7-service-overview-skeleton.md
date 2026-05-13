# Task ID

TASK-BE-146

# Title

WMS 7 service `overview.md` skeleton authoring (refactor-spec all 2026-05-14 portfolio-wide structural finding — WMS service 의 1-pager 진입 자료 100% missing)

# Status

done

# Owner

wms-platform

# Task Tags

- wms
- spec
- skeleton
- be

---

# Goal

`/refactor-spec all --dry-run` (2026-05-13~14) 의 후속 portfolio-wide consistency audit 결과 **WMS 7 service 의 `overview.md` 가 모두 missing**. portfolio 비교:

| Project | service | overview.md 상태 |
|---|---|---|
| ecommerce-microservices-platform | 14 | 13/14 detailed (TASK-BE-141 + TASK-BE-142 종결) ✅ |
| fan-platform | 4 | 4/4 detailed (TASK-FAN-BE-006 종결) ✅ |
| global-account-platform | 8 | 8/8 detailed (58-75 line, 사전 작성) ✅ |
| **wms-platform** | **7** | **7/7 MISSING** ⚠️ |
| scm-platform | 3 | 3/3 MISSING (별 task — 후속) |

WMS = portfolio 의 가장 큰 자산 (138 → 182 unit / I1-I9 production-level / transactional T1/T3/T6/T8 검증 완성, project_wms_v1_published 메모리 참조). overview.md 부재 = portfolio 평가자가 dense `architecture.md` (각 150~300 line) 만 읽고 service 의도를 추론해야 하는 상태. **1-pager 진입 자료 부재 = portfolio 의 가장 visible 한 결손**.

본 task = 7 신규 file authoring. ecommerce TASK-BE-141 / TASK-BE-142 의 hybrid pattern (기존 stub 보존) 과 달리, 본 task 는 **fan-platform TASK-FAN-BE-006 의 신규 authoring pattern** 직접 답습 (보존할 stub 자체가 없음).

대상 7 service:

| Service | Service Type | Architecture Style |
|---|---|---|
| `master-service` | rest-api | Hexagonal (Ports & Adapters) |
| `inventory-service` | rest-api + event-consumer | Hexagonal |
| `inbound-service` | rest-api + webhook receiver | Hexagonal |
| `outbound-service` | rest-api + event-consumer (saga orchestrator) | Hexagonal |
| `notification-service` | event-consumer | Hexagonal |
| `admin-service` | rest-api + event-consumer (CQRS read-side) | Layered (deliberate exception) |
| `gateway-service` | rest-api (edge gateway) | Layered |

provenance: post-`/refactor-spec` portfolio-wide structural finding (TASK-BE-142 closure 직후, 2026-05-14 발견).

---

# Scope

## In Scope

### A. 7 신규 `overview.md` 신규 authoring

각 service `projects/wms-platform/specs/services/<name>/overview.md` 신규 file (~70-80 line). 7 section 표준 (fan-platform sibling pattern + BE-141/142 답습):

1. **`# <service> — Overview`** + `> 1-pager:` 한 줄
2. **`## Service identity` table** (9 row: Service name / Project / Service Type / Architecture Style / Stack / Deployable unit / Bounded Context / Persistent stores / Event publication)
3. **`## Responsibilities`** (3-5 bullets, architecture.md § Why This Architecture / § Internal Structure Rule 답습)
4. **`## Public surface`** table (REST endpoint / Kafka topic consume/publish / webhook 분류)
5. **`## Key invariants`** (numbered, 4-6 hard rules — WMS 의 핵심 T1/T3/T8/W1/W2/W3/W4 invariants 인용)
6. **`## Owned Data`** + **`## Published Interfaces`** + **`## Dependent Systems`** (3 row — fan-platform pattern)
7. **`## Out of scope (v1)`** (의도된 v1 미구현 + v2 후보)

### B. WMS-specific concerns

- 본 7 file 은 portfolio 의 핵심 자산 진입 자료. invariants 표기가 **rules/domains/wms.md** 의 W-series + traits/transactional.md 의 T-series 와 정합해야 함.
- `notification-service` overview.md 는 [TASK-BE-145](../done/TASK-BE-145-notification-service-idempotency-spec-and-dlt-replay-runbook.md) 이 작성한 `idempotency.md` + `runbooks/dlt-replay.md` 와 cross-link (Key invariants 의 idempotency 항목).
- `outbound-service` 의 saga + outbox + ADR-MONO-005 § D6 ACCEPTED reference.
- `admin-service` 의 Layered exception (CQRS read-side) 정당화 — architecture.md § Architecture Style Rationale 인용.
- `gateway-service` overview.md 는 fan-platform `gateway-service/overview.md` (TASK-FAN-BE-006) + ecommerce `gateway-service/overview.md` (TASK-BE-141) 와 sibling-equivalent pattern.

### C. cross-ref 검증

- 7 file ↔ `architecture.md` 양방향 link 정상.
- WMS `PROJECT.md` 의 Service Map (있을 경우) 정합.
- HARDSTOP-03 PASS — 본 file 들은 wms project-specific spec.

## Out of Scope

- SCM 3 service `overview.md` authoring (별 task — TASK-SCM-BE-012 등 후속 후보).
- `architecture.md` 본문 수정 — overview.md authoring 만.
- 다른 audit Medium/Low finding.
- v2 service 추가 (W7/W8 보류 service 등).

---

# Acceptance Criteria

### Impl PR

- [x] `master-service/overview.md` 신규 (~70 line, Service identity + REST + Kafka publish + 5 Key invariants — W3 + W6 + T3).
- [x] `inventory-service/overview.md` 신규 (~80 line, Service identity + REST + Kafka consume + Kafka publish + 6 Key invariants — W1 + W2 + W4 + T3 + T8).
- [x] `inbound-service/overview.md` 신규 (~75 line, Service identity + REST + webhook + Kafka publish + 5 Key invariants — T3 + T4 + I6).
- [x] `outbound-service/overview.md` 신규 (~80 line, Service identity + REST + webhook + Kafka consume + Kafka publish + 6 Key invariants — saga T3/T4/T8, ADR-MONO-005 § D6 reference).
- [x] `notification-service/overview.md` 신규 (~70 line, Service identity + Kafka consume (6 topics) + 5 Key invariants — T8 + idempotency.md cross-link).
- [x] `admin-service/overview.md` 신규 (~75 line, Service identity + REST + Kafka consume + Kafka publish + 5 Key invariants — projection idempotency, Layered exception 정당화).
- [x] `gateway-service/overview.md` 신규 (~70 line, Service identity + Routes + 6 Key invariants — JWT validation, fail-open rate limit, no business logic, sibling-equivalent with fan-platform/ecommerce gateway).
- [x] cross-ref 검증 — 7 file 이 `architecture.md` 와 정상 연결.
- [x] HARDSTOP-03 PASS.
- [x] CI self-CI PASS (path-filter wms markdown-only — 15 SKIP + 1 changes PASS 예상).
- [x] task lifecycle ready → review (in-progress 우회, BE-141 / BE-142 / FAN-BE-006 / MONO-084 precedent).
- [x] wms tasks/INDEX.md 동기.

### Close chore PR

- [x] task Status review → done.
- [x] git mv tasks/review → tasks/done.
- [x] wms tasks/INDEX.md ## review 제거, ## done append outcome.

---

# Related Specs

- `projects/wms-platform/specs/services/<name>/architecture.md` × 7 (content source).
- `projects/wms-platform/specs/contracts/events/master-events.md` + `inventory-events.md` + `inbound-events.md` + `outbound-events.md` + `notification-events.md` + `notification-subscriptions.md` + `admin-events.md` (Kafka topic catalog cross-ref).
- `projects/wms-platform/specs/contracts/http/*.md` (REST API cross-ref).
- `projects/wms-platform/specs/contracts/webhooks/erp-asn-webhook.md` + `erp-order-webhook.md` (webhook cross-ref).
- `projects/wms-platform/specs/services/notification-service/idempotency.md` (TASK-BE-145 신규, notification overview Key invariants 항목 cross-link).
- `projects/wms-platform/specs/services/notification-service/runbooks/dlt-replay.md` (TASK-BE-145 신규).
- `docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md` (outbound saga reference).
- `projects/fan-platform/specs/services/gateway-service/overview.md` (TASK-FAN-BE-006 sibling skeleton source).
- `projects/ecommerce-microservices-platform/specs/services/order-service/overview.md` (TASK-BE-142 sibling skeleton — DDD-style rest-api pattern).
- `rules/domains/wms.md` (W-series invariants source).
- `rules/traits/transactional.md` (T-series invariants source).

---

# Related Contracts

본 task = 1-pager overview spec authoring. HTTP API / event payload 변경 0. 단, Public surface 섹션이 contracts/ 와 정합해야 함 (spot-check).

---

# Target Service

7 service:

- `projects/wms-platform/apps/master-service/`
- `projects/wms-platform/apps/inventory-service/`
- `projects/wms-platform/apps/inbound-service/`
- `projects/wms-platform/apps/outbound-service/`
- `projects/wms-platform/apps/notification-service/`
- `projects/wms-platform/apps/admin-service/`
- `projects/wms-platform/apps/gateway-service/`

---

# Architecture

WMS v1 의 7 service 진입 자료 (1-pager overview.md) 일괄 authoring. portfolio Phase 5 (Template 추출, ADR-MONO-003b) unlock 직전의 가장 visible 한 polish — 평가자가 architecture.md 진입 전 service 의도를 한 페이지로 파악할 수 있는 상태로 전환.

본 task 완료 시 portfolio 5 운영 프로젝트 (gap + ecommerce + fan-platform + wms + scm) 중 **wms 까지 4/5 overview.md 일관성 완성**. scm 3 service 는 후속 별 task (TASK-SCM-BE-012 후보, smaller batch).

---

# Implementation Notes

## 답습 template — fan-platform sibling pattern (TASK-FAN-BE-006 / TASK-BE-141 / TASK-BE-142)

```markdown
# <service> — Overview

> 1-pager: responsibilities, public surface, key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `<name>` |
| Project | `wms-platform` |
| Service Type | `<type>` |
| Architecture Style | **<style>** — see [architecture.md § …](architecture.md) |
| Stack | <stack> |
| Deployable unit | `apps/<name>/` |
| Bounded Context | `<context>` |
| Persistent stores | <stores> |
| Event publication | <topics or none> |

## Responsibilities

- ...

## Public surface

| Channel | Endpoint / Topic / Webhook | Auth | Purpose |
|---|---|---|---|
| ... |

## Key invariants

1. ...

## Owned Data

- ...

## Published Interfaces

- <contract refs>

## Dependent Systems

- ...

## Out of scope (v1)

- ...
```

## 본 task 의 lifecycle 단축

mechanical batch (fan-platform / ecommerce sibling pattern 답습) → ready → review 직접 (in-progress 우회). BE-141 / BE-142 / FAN-BE-006 / MONO-084 precedent.

## 7 file 일괄 작성 효율화

7 service 의 정보 recon (architecture.md skim + contracts catalog) 은 본 task spec 작성 단계에서 이미 완료 (Goal § 대상 7 service table). impl 단계 = 각 service 별 ~70-80 line 신규 = ~510 line addition.

---

# Edge Cases

- `notification-service` 는 v1 에서 REST surface 0 (event-consumer pure) — Public surface table 은 Kafka consume + publish only.
- `admin-service` 는 Layered exception — architecture style 표기 "**Layered**" + "(deliberate exception — CQRS read-side)" 명시.
- `outbound-service` 의 saga orchestrator 역할은 ADR-MONO-005 § D6 reference + invariants 의 saga T4 항목으로 표현.
- `gateway-service` 는 fan-platform / ecommerce gateway sibling-equivalent — Bounded Context = n/a, Event publication = none, "Layered (no domain aggregates)" 표기 정합.
- `inbound-service` + `outbound-service` 의 webhook channel 은 Public surface table 에 별 row (HMAC, no JWT) — `gateway-service` 의 webhook route 와 정합.

---

# Failure Scenarios

- overview.md content 가 architecture.md 와 stack / style 표기 mismatch → spec drift. spot-check 강제 (architecture.md L1-30 vs overview.md Service identity).
- Public surface 섹션의 endpoint / event 이 contracts/ 와 불일치 → spec drift. contracts/http/<name>-api.md + contracts/events/<name>-events.md 와 일치 검증.
- WMS T-series / W-series invariants 가 portfolio rule library 와 mismatch → rules/domains/wms.md + rules/traits/transactional.md 와 정합 확인.

---

# Test Requirements

- HARDSTOP-03 hook PASS — 7 file 모두 wms project-specific.
- CI self-CI PASS (markdown-only path-filter — 자연 15 SKIP + 1 PASS).
- 7 신규 file 의 cross-ref 정상.
- production code = 0.

---

# Definition of Done

### Impl PR

- [x] AC 완료.
- [x] task lifecycle ready → review.

### Close chore PR

- [x] review → done, INDEX 동기.

---

# Provenance

- `/refactor-spec all --dry-run` 2026-05-13~14 후속 portfolio-wide structural finding — WMS 7/7 overview.md missing (TASK-BE-142 closure 직후 2026-05-14 발견).
- Direct precedent: TASK-FAN-BE-006 (2026-05-14 merged, 2 신규 overview.md authoring).
- Hybrid pattern source: TASK-BE-141 + TASK-BE-142 (ecommerce 13 service overview.md sibling-equivalent depth, 2026-05-14 merged).
- Sibling closure pattern 답습: TASK-MONO-083 / TASK-BE-280 / TASK-BE-281 / TASK-SCM-BE-011 / TASK-MONO-084 / TASK-FAN-BE-006 / TASK-BE-145 / TASK-BE-141 / TASK-BE-142 — 모두 same-day single-PR closure.
- 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (large mechanical batch, fan-platform / ecommerce sibling 답습).
