# Task ID

TASK-SCM-BE-012

# Title

SCM 3 service `overview.md` skeleton authoring (portfolio-wide structural finding 마무리 — 5/5 일관성 100% 완성)

# Status

review

# Owner

scm-platform

# Task Tags

- scm
- spec
- skeleton
- be

---

# Goal

post-`/refactor-spec all --dry-run` (2026-05-13~14) portfolio-wide audit 의 마지막 잔여 — SCM 3 service `overview.md` 가 모두 missing. [TASK-BE-146](../../../wms-platform/tasks/done/TASK-BE-146-wms-7-service-overview-skeleton.md) (2026-05-14 merged, WMS 7 service authoring) 의 직접 후속.

| Project | service | overview.md 상태 |
|---|---|---|
| ecommerce-microservices-platform | 14 | 13/14 detailed ✅ (TASK-BE-141 + TASK-BE-142) |
| fan-platform | 4 | 4/4 detailed ✅ (TASK-FAN-BE-006) |
| global-account-platform | 8 | 8/8 detailed ✅ (사전 작성) |
| wms-platform | 7 | 7/7 detailed ✅ (TASK-BE-146) |
| **scm-platform** | **3** | **3/3 MISSING** ← 본 task |

본 task 완료 시 portfolio 5 운영 프로젝트 overview.md 일관성 **100% 완성**.

대상 3 service:

| Service | Service Type | Architecture Style |
|---|---|---|
| `gateway-service` | rest-api (edge gateway) | Layered (intentional exception) |
| `procurement-service` | rest-api (primary) | Hexagonal (Ports & Adapters) |
| `inventory-visibility-service` | rest-api + event-consumer | Hexagonal |

SCM 의 특징적 invariants (다른 프로젝트 답습과 구별):

- `procurement-service` 의 **S5 rule** — procurement-service 가 PO 결정을 위해 `inventory-visibility-service` 를 consult 하면 안 됨 (cross-service decision boundary).
- `inventory-visibility-service` 의 **S5 warning** — 모든 4 REST endpoint 응답 meta 에 "Not for procurement decisions" 명시.
- `procurement-service` 의 **S2 / S6 / S8** — Idempotency-Key dedup / AES-GCM encrypted credentials / vendor SDK infrastructure-only confinement.
- `inventory-visibility-service` 의 **ShedLock-guarded** scheduler (multi-replica safety).
- `gateway-service` 의 **tenant_id=scm** isolation + **fail-closed** tenant check.

provenance: post-/refactor-spec portfolio-wide structural finding closure 의 마지막 piece. TASK-BE-146 직접 답습 + SCM-specific S-rules (rules/domains/scm.md 또는 architecture.md § Forbidden Dependencies) 반영.

---

# Scope

## In Scope

### A. 3 신규 `overview.md` authoring

각 service `projects/scm-platform/specs/services/<name>/overview.md` 신규 file (~70-80 line). TASK-BE-146 의 7-section template 답습:

1. `# <service> — Overview` + `> 1-pager:` 한 줄
2. `## Service identity` table (9 row)
3. `## Responsibilities` (3-5 bullets)
4. `## Public surface` table (REST + Kafka + webhook 분류)
5. `## Key invariants` (numbered, 5-6 hard rules — SCM 의 S-series 인용)
6. `## Owned Data` + `## Published Interfaces` + `## Dependent Systems` (3 row)
7. `## Out of scope (v1)` + 의도된 v2 후보

### B. SCM-specific concerns

- `procurement-service` Key invariants — S2 (Idempotency-Key) / S5 (no inventory-visibility consult) / S6 (encrypted credentials) / S8 (vendor SDK infrastructure-only).
- `inventory-visibility-service` Key invariants — S5 warning + event dedupe + ShedLock + fail-open Redis cache.
- `gateway-service` Key invariants — fan-platform / wms gateway sibling-equivalent + tenant_id=scm.
- 3 service 의 cross-service rule (S5 cross-reference) 명시.

### C. cross-ref 검증

- 3 file ↔ `architecture.md` 양방향 link 정상.
- scm `PROJECT.md` 의 Service Map 정합.
- HARDSTOP-03 PASS — 본 file 들은 scm project-specific spec.

## Out of Scope

- `architecture.md` 본문 수정 — overview.md authoring 만.
- 다른 audit Medium/Low finding.
- v2 service 추가 (settlement / demand-planning / logistics 등 deferred).

---

# Acceptance Criteria

### Impl PR

- [x] `gateway-service/overview.md` 신규 (~70 line, Service identity + Routes + 5 Key invariants — JWT validation + tenant_id=scm + fail-open RL + IdentityHeaderStripFilter).
- [x] `procurement-service/overview.md` 신규 (~80 line, Service identity + REST + webhook + Kafka publish + 6 Key invariants — S2/S5/S6/S8 + PO state machine).
- [x] `inventory-visibility-service/overview.md` 신규 (~80 line, Service identity + REST + Kafka consume + scheduler + 6 Key invariants — S5 warning + event dedupe + ShedLock + fail-open cache).
- [x] cross-ref 검증 — 3 file 이 `architecture.md` 와 정상 연결.
- [x] HARDSTOP-03 PASS.
- [ ] CI self-CI PASS (path-filter scm markdown-only — 15 SKIP + 1 changes PASS 예상).
- [x] task lifecycle ready → review (in-progress 우회, BE-146 / BE-141 / BE-142 / FAN-BE-006 precedent).
- [x] scm tasks/INDEX.md 동기.

### Close chore PR

- [ ] task Status review → done.
- [ ] git mv tasks/review → tasks/done.
- [ ] scm tasks/INDEX.md ## review 제거, ## done append outcome.

---

# Related Specs

- `projects/scm-platform/specs/services/gateway-service/architecture.md` (content source).
- `projects/scm-platform/specs/services/procurement-service/architecture.md` (content source — S-rules).
- `projects/scm-platform/specs/services/inventory-visibility-service/architecture.md` (content source — S5 warning, ShedLock).
- `projects/scm-platform/specs/contracts/http/gateway-public-routes.md` + `procurement-api.md` + `inventory-visibility-api.md` (Public surface cross-ref).
- `projects/scm-platform/specs/contracts/events/scm-procurement-events.md` + `inventory-visibility-subscriptions.md` (Kafka topic cross-ref).
- `projects/wms-platform/specs/services/master-service/overview.md` + 6 sibling (TASK-BE-146, 2026-05-14 merged) — sibling skeleton source.
- `projects/fan-platform/specs/services/gateway-service/overview.md` (gateway sibling pattern).
- `rules/domains/scm.md` (S-series invariants source).
- `rules/traits/transactional.md` + `traits/integration-heavy.md` + `traits/batch-heavy.md` (SCM 3-trait stack).

---

# Related Contracts

본 task = 1-pager overview spec authoring. HTTP API / event payload 변경 0. 단, Public surface 섹션이 contracts/ 와 정합해야 함 (spot-check).

---

# Target Service

3 service:

- `projects/scm-platform/apps/gateway-service/`
- `projects/scm-platform/apps/procurement-service/`
- `projects/scm-platform/apps/inventory-visibility-service/`

---

# Architecture

SCM v1 의 3 service 진입 자료 일괄 authoring. portfolio 5 운영 프로젝트 overview.md 일관성 **100% 완성** (마지막 piece).

본 task 완료 시:

- ecommerce 13/14 ✅
- fan-platform 4/4 ✅
- GAP 8/8 ✅
- WMS 7/7 ✅
- **SCM 3/3 ✅ (본 task)**

portfolio Phase 5 (Template 추출, ADR-MONO-003b) unlock 직전의 마지막 polish.

---

# Implementation Notes

## 답습 template — TASK-BE-146 직접 답습

```markdown
# <service> — Overview

> 1-pager: responsibilities, public surface, key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `<name>` |
| Project | `scm-platform` |
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

| Channel | Endpoint / Topic / Job | Auth | Purpose |
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

mechanical batch (TASK-BE-146 직접 답습) → ready → review 직접 (in-progress 우회). BE-141/142 / FAN-BE-006 / BE-146 / MONO-084 precedent.

---

# Edge Cases

- `inventory-visibility-service` 는 `wms-platform` 의 Kafka topic 을 consume (cross-project). overview Public surface 의 Kafka consume row 가 `wms.inventory.{received,adjusted,transferred}.v1` 명시.
- `procurement-service` webhook (supplier-ack + ASN) 은 `X-Supplier-Signature` HMAC 검증 — gateway-service 의 webhook bypass 와 정합.
- `gateway-service` 의 architecture style 표기 = "**Layered (intentional exception — no domain aggregates)**".

---

# Failure Scenarios

- overview.md content 가 architecture.md 와 stack / style 표기 mismatch → spec drift. spot-check 강제.
- S5 rule 표기가 procurement-service / inventory-visibility-service 양쪽에서 일관되지 않으면 → portfolio 평가자 혼선. 같은 문구 동일하게 인용.

---

# Test Requirements

- HARDSTOP-03 hook PASS.
- CI self-CI PASS (markdown-only path-filter).
- 3 신규 file cross-ref 정상.
- production code = 0.

---

# Definition of Done

### Impl PR

- [ ] AC 완료.
- [x] task lifecycle ready → review.

### Close chore PR

- [ ] review → done, INDEX 동기.

---

# Provenance

- post-/refactor-spec portfolio-wide structural finding 의 마지막 piece (TASK-BE-146 closure 직후, 2026-05-14).
- Direct precedent: TASK-BE-146 (2026-05-14 merged, WMS 7 신규 overview.md).
- Hybrid pattern source: TASK-BE-141 / TASK-BE-142 (ecommerce 13 service) + TASK-FAN-BE-006 (fan-platform 2 service).
- Sibling closure pattern 답습: TASK-MONO-083 / TASK-BE-280 / TASK-BE-281 / TASK-SCM-BE-011 / TASK-MONO-084 / TASK-FAN-BE-006 / TASK-BE-145 / TASK-BE-141 / TASK-BE-142 / TASK-BE-146 — 모두 same-day single-PR closure (본 task 가 10번째 entry).
- 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (smallest mechanical batch, BE-146 패턴 직접 답습).
