# Task ID

TASK-BE-142

# Title

ecommerce 10 backend service `overview.md` sibling-consistency batch enhancement (refactor-spec deferred polish — TASK-BE-141 pattern 확장)

# Status

ready

# Owner

ecommerce-microservices-platform

# Task Tags

- ecommerce
- spec
- overview
- be

---

# Goal

[TASK-BE-141](../done/TASK-BE-141-3-service-overview-key-invariants-backfill.md) (2026-05-14 merged) 의 직접 후속 polish.

BE-141 은 ecommerce 14 service 중 3 frontend-edge service (`gateway-service` / `web-store` / `admin-dashboard`) `overview.md` 를 fan-platform sibling-equivalent depth (60~90 line, Service identity table + Public surface + Key invariants + Out of scope (v1) 신규 4 섹션) 로 enhancement. BE-141 의 deferred polish row 명시:

> 나머지 10 service overview.md = sibling consistency, deferred polish

본 task = 그 10 service 일괄 enhancement. ecommerce 13 service overview.md 전체를 동일 depth 로 통일 (auth-service-deprecated 1개 제외, 본 service 는 v1 deprecation 상태로 enhancement 대상 아님 — § Out of Scope).

대상 10 service:

| Service | Service Type | Architecture Style |
|---|---|---|
| `order-service` | rest-api | DDD-style |
| `payment-service` | rest-api | Hexagonal |
| `product-service` | rest-api | DDD-style |
| `promotion-service` | rest-api | DDD-style |
| `review-service` | rest-api | DDD-style |
| `search-service` | rest-api | Hexagonal |
| `shipping-service` | rest-api | DDD-style |
| `user-service` | rest-api | Layered |
| `notification-service` | event-consumer | Hexagonal |
| `batch-worker` | batch-job | Layered |

provenance: `/refactor-spec all --dry-run` (2026-05-13~14) ecommerce HIGH-B finding closure 의 후속. BE-141 의 deferred polish row 직접 답습.

---

# Scope

## In Scope

### A. 10 service overview.md enhancement

10 service 각각 `overview.md` 를 fan-platform sibling-equivalent depth 로 enhancement (현 31~38 line → ~60~85 line). 신규 4 섹션 추가:

1. **`# <service> — Overview`** 헤더 + `> 1-pager:` 한 줄 설명
2. **`## Service identity` table** (Service name / Project / Service Type / Architecture Style / Stack / Deployable unit / Bounded Context / Persistent stores / Event publication 9 row)
3. **`## Responsibilities`** (기존 `## Responsibility` 복수형 정정 + bullet 화)
4. **`## Public surface`** table — REST endpoint / Kafka topic / scheduler job 의 1-pager 요약
5. **`## Key invariants`** (numbered, 4~6 항목 — "이것이 깨지면 service 가 깨졌다" 수준 hard rules)
6. **`## Out of scope (v1)`** — 의도된 미구현 + 소유권 boundary

기존 7 섹션 (`## Service` / `## Responsibility` / `## In Scope` / `## Out of Scope` / `## Owned Data` / `## Published Interfaces` / `## Dependent Systems`) 은 통합 또는 보존:

- `## Service` → `## Service identity` table 으로 흡수.
- `## Responsibility` → `## Responsibilities` 복수형 + bullet (architecture.md § Why This Architecture / § Internal Structure Rule 참조).
- `## In Scope` → `## Public surface` + `## Responsibilities` 로 흡수 (정보 손실 없음).
- `## Out of Scope` → `## Out of scope (v1)` 소문자 + v1 suffix.
- `## Owned Data` / `## Published Interfaces` / `## Dependent Systems` 3 row 는 **보존** (sibling 13 + fan-platform 5 service 의 공통 lower-half 패턴 → BE-141 hybrid 답습).

### B. 답습 source

10 file 모두 BE-141 의 3 file (`gateway-service/overview.md`, `web-store/overview.md`, `admin-dashboard/overview.md`) hybrid pattern 답습:

- 상단 4 신규 섹션 (Service identity table + Responsibilities + Public surface + Key invariants + Out of scope (v1))
- 하단 3 보존 섹션 (Owned Data + Published Interfaces + Dependent Systems)

### C. cross-ref 검증

- 10 file ↔ `architecture.md` 양방향 link 정상 (`[architecture.md § …](architecture.md)` 형식).
- ecommerce `PROJECT.md` 의 Service Map (있을 경우) 정합 — 본 task scope = entry 변경 0.
- HARDSTOP-03 PASS — 본 file 들은 ecommerce project-specific spec.

## Out of Scope

- `auth-service-deprecated/overview.md` enhancement — v1 deprecation 상태, 후속 service (GAP 또는 신규 auth) 로 대체 예정. enhancement = obsolete.
- `architecture.md` 본문 수정 — overview.md authoring 만.
- 다른 audit finding (Medium / Low level — 별 task 영역).
- 신규 service 추가 / 기존 service 제거.
- v2 backlog service (rules/domains/ecommerce.md 에 명시된 v2 service 들) overview.md 작성.

---

# Acceptance Criteria

### Impl PR

- [ ] `order-service/overview.md` enhancement (~70 line, Service identity + Public surface (REST + Kafka consume) + 5 Key invariants).
- [ ] `payment-service/overview.md` enhancement (~70 line, Service identity + Public surface (REST + Kafka consume) + 5 Key invariants — outbox atomic, refund precondition).
- [ ] `product-service/overview.md` enhancement (~70 line, Service identity + Public surface (REST + Kafka publish) + 5 Key invariants — stock non-negative, search coupling event-only).
- [ ] `promotion-service/overview.md` enhancement (~70 line, Service identity + Public surface (REST + sync HTTP + Kafka consume) + 5 Key invariants).
- [ ] `review-service/overview.md` enhancement (~70 line, Service identity + Public surface (REST + Kafka publish + sync HTTP) + 5 Key invariants — one-review-per-user, soft-delete only).
- [ ] `search-service/overview.md` enhancement (~70 line, Service identity + Public surface (REST + Kafka consume) + 5 Key invariants — derived index, product-service is SoT).
- [ ] `shipping-service/overview.md` enhancement (~70 line, Service identity + Public surface (REST + Kafka consume + Kafka publish) + 5 Key invariants — unidirectional transition, idempotent creation).
- [ ] `user-service/overview.md` enhancement (~70 line, Service identity + Public surface (REST + Kafka consume + Kafka publish) + 5 Key invariants — no credential ownership).
- [ ] `notification-service/overview.md` enhancement (~75 line, Service identity + Public surface (Kafka consume from 4 services + REST admin) + 5 Key invariants — event_id idempotency, channel opt-out).
- [ ] `batch-worker/overview.md` enhancement (~70 line, Service identity + Public surface (Spring Scheduler jobs + Kafka publish, no HTTP) + 5 Key invariants — idempotent jobs, no cross-DB access).
- [ ] cross-ref 검증 — 10 file 이 `architecture.md` 와 정상 연결.
- [ ] HARDSTOP-03 PASS.
- [ ] CI self-CI PASS (path-filter ecommerce markdown-only — 15 SKIP + 1 changes PASS 예상).
- [ ] task lifecycle ready → review (in-progress 우회, BE-141 / FAN-BE-006 / MONO-084 precedent — mechanical batch single-PR closure).
- [ ] ecommerce tasks/INDEX.md 동기.

### Close chore PR

- [ ] task Status review → done.
- [ ] git mv tasks/review → tasks/done.
- [ ] ecommerce tasks/INDEX.md ## review 제거, ## done append outcome.

---

# Related Specs

- `projects/ecommerce-microservices-platform/specs/services/<name>/architecture.md` × 10 (content source — § Service Type, § Architecture Style, § Why This Architecture, § Allowed/Forbidden Dependencies).
- `projects/ecommerce-microservices-platform/specs/services/gateway-service/overview.md` (BE-141 직접 답습 source — REST + edge pattern).
- `projects/ecommerce-microservices-platform/specs/services/web-store/overview.md` (BE-141 직접 답습 source — frontend hybrid pattern).
- `projects/ecommerce-microservices-platform/specs/services/admin-dashboard/overview.md` (BE-141 직접 답습 source).
- `projects/ecommerce-microservices-platform/specs/contracts/events/*.md` (Kafka topic 명세 cross-ref).
- `projects/ecommerce-microservices-platform/specs/contracts/http/*.md` (REST API 명세 cross-ref).
- `projects/fan-platform/specs/services/community-service/overview.md` (sibling reference — rest-api + DDD style).
- `projects/fan-platform/specs/services/artist-service/overview.md` (sibling reference — rest-api + DDD style).

---

# Related Contracts

본 task = 1-pager overview spec batch enhancement. HTTP API / event payload 변경 0. 단, overview.md 의 Public surface 섹션이 contracts/ 의 endpoint / event list 와 정합해야 함 (spot-check).

---

# Target Service

10 service:

- `projects/ecommerce-microservices-platform/apps/order-service/`
- `projects/ecommerce-microservices-platform/apps/payment-service/`
- `projects/ecommerce-microservices-platform/apps/product-service/`
- `projects/ecommerce-microservices-platform/apps/promotion-service/`
- `projects/ecommerce-microservices-platform/apps/review-service/`
- `projects/ecommerce-microservices-platform/apps/search-service/`
- `projects/ecommerce-microservices-platform/apps/shipping-service/`
- `projects/ecommerce-microservices-platform/apps/user-service/`
- `projects/ecommerce-microservices-platform/apps/notification-service/`
- `projects/ecommerce-microservices-platform/apps/batch-worker/`

---

# Architecture

ecommerce v1 의 13 service `overview.md` 일관성 완성 task. BE-141 이 끝낸 3 frontend-edge 와 본 task 의 10 backend 모두 fan-platform sibling-equivalent depth 보유 → portfolio 평가자 진입 자료 통일.

---

# Implementation Notes

## 답습 template — BE-141 hybrid pattern

```markdown
# <service> — Overview

> 1-pager: responsibilities, public surface, key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `<name>` |
| Project | `ecommerce-microservices-platform` |
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

- <one-liner>

## Published Interfaces

- <contract file refs>

## Dependent Systems

- <comma-separated>

## Out of scope (v1)

- ...
```

## 본 task 의 lifecycle 단축

mechanical batch (BE-141 패턴 직접 답습) → ready → review 직접 (in-progress 우회). BE-141 / FAN-BE-006 / MONO-084 precedent.

## 10 file 일괄 작성 효율화

10 service 의 정보 recon (architecture.md + 기존 overview.md skim) 은 본 task spec 작성 단계에서 이미 완료 (Goal § 대상 10 service table). impl 단계 = 각 service 별 ~50 line 추가 + ~30 line 보존 = 800 line addition.

---

# Edge Cases

- `notification-service` 는 `event-consumer` type — Public surface 의 REST 는 admin endpoint 한정, 주력은 Kafka consume from 4 services (order / payment / shipping / auth).
- `batch-worker` 는 `batch-job` type — no HTTP server, Public surface 는 Spring Scheduler job 목록 + Kafka publish.
- `search-service` 는 PostgreSQL 미사용 (Elasticsearch only) — Persistent stores row 는 "Elasticsearch (derived index, not authoritative)" 명시.
- `user-service` 는 outbox 미사용 (Layered Architecture, 단순 publish) — Stack row 에서 "libs/java-messaging" 제외, Persistent stores 는 "PostgreSQL" 만.

---

# Failure Scenarios

- overview.md content 가 architecture.md 와 stack 표기 mismatch → spec drift. spot-check 강제 — architecture.md L11/L21 (stack/internal structure) vs overview.md Service identity stack row 동일.
- Public surface 섹션의 endpoint / event 이 contracts/ 와 불일치 → spec drift. contracts/http/<name>-api.md + contracts/events/<name>-events.md 와 일치 검증.
- Architecture style 표기 inconsistency (BE-141 의 `**Layered**` italic-bold 와 다른 style) → manual spot-check.

---

# Test Requirements

- HARDSTOP-03 hook PASS — 10 file 모두 project-specific.
- CI self-CI PASS (markdown-only path-filter — 자연 15 SKIP + 1 PASS).
- 10 신규 file 의 cross-ref 정상.
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

- `/refactor-spec all --dry-run` 2026-05-13~14 ecommerce HIGH-B 의 deferred polish row 직접 답습.
- Direct precedent: TASK-BE-141 (2026-05-14 merged) — 3 frontend-edge overview.md enhancement.
- Sibling pattern source: BE-141 의 hybrid pattern + fan-platform community-service / artist-service overview.md.
- Sibling closure pattern 답습: TASK-MONO-083 / TASK-BE-280 / TASK-BE-281 / TASK-SCM-BE-011 / TASK-MONO-084 / TASK-FAN-BE-006 / TASK-BE-145 / TASK-BE-141 — 모두 same-day single-PR closure.
- 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (large mechanical batch, BE-141 패턴 직접 답습).
