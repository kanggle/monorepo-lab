# Task ID

TASK-BE-141

# Title

`gateway-service` + `web-store` + `admin-dashboard` overview.md enhancement (key invariants + service identity table backfill — refactor-spec all 2026-05-14 ecommerce HIGH-B)

# Status

review

# Owner

ecommerce-microservices-platform

# Task Tags

- ecommerce
- spec
- overview
- be
- fe

---

# Goal

`/refactor-spec all --dry-run` (2026-05-13~14) ecommerce HIGH-B finding closure.

ecommerce 의 13 service `overview.md` 는 모두 30~38 line stub 수준 (Service / Responsibility / In Scope / Out of Scope / Owned Data / Published Interfaces / Dependent Systems 7 섹션). 동일-tier portfolio 프로젝트 **fan-platform** 의 신규 작성 overview.md (TASK-FAN-BE-006, 2026-05-14 merged) 는 다음을 추가 보유:

- `## Service identity` table (Stack / Architecture Style / Bounded Context / Persistent stores / Event publication 명시)
- `## Key invariants` (numbered, hard rules — "이것이 깨지면 service 가 깨졌다" 수준)
- `## Public API surface` (route table — frontend 의 경우 pages table)
- `## Out of scope (v1)` (의도된 미구현 명시)

본 task = audit 에서 지목한 ecommerce **3 service overview.md** 를 fan-platform sibling-equivalent depth (60~90 line) 로 enhancement. 남은 10 service overview.md 는 deferred polish (별 task 영역).

3 service 선택 근거:

| Service | Service Type | 선택 이유 |
|---|---|---|
| `gateway-service` | rest-api (edge gateway) | 외부 진입점 — invariants 가 가장 portfolio 평가자에게 가시적 |
| `web-store` | frontend-app | 고객-facing storefront — Next.js / SSR / 인증 flow 의 hard rules 가 invariants |
| `admin-dashboard` | frontend-app | 내부 admin — auth guard / 데이터 ownership 의 invariants |

provenance: `/refactor-spec all --dry-run` 2026-05-13~14 ecommerce HIGH-B (3 frontend-edge overview.md depth 부족 finding). TASK-FAN-BE-006 sibling-equivalent depth 답습.

---

# Scope

## In Scope

### A. `gateway-service/overview.md` enhancement

기존 33 line → ~70 line. 추가 섹션:

- **`## Service identity` table** (Stack / Architecture Style / Service Type / Bounded Context / Persistent stores / Event publication) — `architecture.md` § Identity 발췌.
- **`## Key invariants`** (numbered) — JWT validation, downstream 도달 가드, rate-limit fail-open, stateless, no domain logic.
- **`## Out of scope (v1)`** — business logic ownership, token issuance.

기존 7 section 은 통합/유지 (Responsibility → Responsibilities, In Scope → Public API surface, Dependent Systems 유지).

### B. `web-store/overview.md` enhancement

기존 34 line → ~80 line. 추가 섹션:

- **`## Service identity` table** (Stack / Architecture Style — Next.js App Router / Bounded Context — `ecommerce-storefront` / Persistent stores — none / Backend dependencies).
- **`## Public surface (pages)`** table — 7+ 페이지 (browse / detail / cart / checkout / login / signup / order history / profile) + auth 분류.
- **`## Key invariants`** — HttpOnly cookie auth (또는 NextAuth session) / 인증 사용자만 cart 노출 / payment widget 통한 PG 위임 / no token in localStorage.
- **`## Out of scope (v1)`** — admin, SEO for non-product, marketplace seller UI.

### C. `admin-dashboard/overview.md` enhancement

기존 32 line → ~70 line. 추가 섹션:

- **`## Service identity` table** (Stack / Architecture Style / Bounded Context — `ecommerce-admin` / Persistent stores — none / Backend dependencies).
- **`## Public surface (pages)`** table — product 관리 / order 관리 / user 관리 / dashboard summary / auth guard.
- **`## Key invariants`** — admin-only access (인증 가드 hard fail-close) / 모든 데이터 backend service 위임 / no business logic in frontend / no public page (client-rendered behind auth).
- **`## Out of scope (v1)`** — customer storefront, public SEO, marketplace.

### D. cross-ref 검증

- 3 file ↔ `architecture.md` 양방향 link 정상.
- ecommerce `PROJECT.md` 와 정합 — 본 task scope = service Map entry 추가/수정 0.
- HARDSTOP-03 PASS — 본 file 들은 project-specific spec, 다른 project name leak 0.

## Out of Scope

- 나머지 10 service `overview.md` enhancement (auth-service-deprecated / batch-worker / notification-service / order-service / payment-service / product-service / promotion-service / review-service / search-service / shipping-service / user-service) — sibling consistency 일관성 유지, future polish task.
- `architecture.md` 본문 수정 (overview.md enhancement 만).
- 다른 audit finding (HIGH-A / CRIT-* 등 — 별 task 영역, 다수 closure 완료).

---

# Acceptance Criteria

### Impl PR

- [x] `gateway-service/overview.md` enhancement (~70 line, Service identity table + Key invariants + Out of scope).
- [x] `web-store/overview.md` enhancement (~80 line, Service identity table + Public surface (pages) + Key invariants + Out of scope).
- [x] `admin-dashboard/overview.md` enhancement (~70 line, Service identity table + Public surface (pages) + Key invariants + Out of scope).
- [x] cross-ref 검증 — 3 file 이 `architecture.md` 와 정상 연결 (relative link).
- [x] HARDSTOP-03 PASS — 본 file 들은 ecommerce project-specific spec.
- [ ] CI self-CI PASS (path-filter ecommerce markdown-only — 15 SKIP + 1 changes PASS 예상).
- [x] task lifecycle ready → review (in-progress 우회, mechanical batch single-PR closure 패턴, TASK-FAN-BE-006 precedent).
- [x] ecommerce tasks/INDEX.md 동기.

### Close chore PR

- [ ] task Status review → done.
- [ ] git mv tasks/review → tasks/done.
- [ ] ecommerce tasks/INDEX.md ## review 제거, ## done append 1-line outcome.

---

# Related Specs

- `projects/ecommerce-microservices-platform/specs/services/gateway-service/architecture.md` (content source — § Identity, § Routes).
- `projects/ecommerce-microservices-platform/specs/services/web-store/architecture.md` (content source — § Identity, § Pages).
- `projects/ecommerce-microservices-platform/specs/services/admin-dashboard/architecture.md` (content source — § Identity, § Pages).
- `projects/fan-platform/specs/services/gateway-service/overview.md` (sibling-equivalent depth reference).
- `projects/fan-platform/specs/services/fan-platform-web/overview.md` (sibling-equivalent depth reference — frontend pattern).

---

# Related Contracts

본 task = 1-pager overview spec enhancement. HTTP API / event payload 변경 0.

---

# Target Service

- `projects/ecommerce-microservices-platform/apps/gateway-service/` (gateway overview 대상).
- `projects/ecommerce-microservices-platform/apps/web-store/` (storefront overview 대상).
- `projects/ecommerce-microservices-platform/apps/admin-dashboard/` (admin overview 대상).

---

# Architecture

ecommerce v1 의 13 service 중 audit-에 지목된 3 frontend-edge service (gateway + web-store + admin) 의 1-pager overview enhancement. portfolio 평가자 진입 자료의 fan-platform sibling-equivalent depth.

---

# Implementation Notes

## 답습 template — TASK-FAN-BE-006 sibling pattern

```markdown
# <service> — Overview

> 1-pager: responsibilities, public surface, key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `<name>` |
| Project | `ecommerce-microservices-platform` |
| Service Type | `<type>` |
| Architecture Style | **<style>** |
| Stack | <stack> |
| Deployable unit | `<path>` |
| Bounded Context | `<context>` |
| Persistent stores | <stores> |
| Event publication | <topics or none> |

## Responsibilities

- ...

## Public surface (pages|routes|API)

| Path | Pattern | Auth | Purpose |
|---|---|---|---|
| ... |

## Key invariants

1. ...

## Out of scope (v1)

- ...
```

## 본 task 의 lifecycle 단축

mechanical enhancement (fan-platform sibling 답습) → ready → review 직접 (in-progress 우회). TASK-FAN-BE-006 / TASK-BE-281 / TASK-MONO-084 precedent.

---

# Edge Cases

- web-store / admin-dashboard 가 frontend-app type — "Public API surface" 는 "Public surface (pages)" 로 명명 (fan-platform-web 패턴 답습).
- gateway-service 의 경우 stateless + no domain event — "Event publication" row 가 `none`, "Persistent stores" 는 Redis (rate-limit only).
- 기존 stub 의 일부 문구 (예: "X-User-Id, X-User-Email, X-User-Role headers") 는 그대로 유지 또는 architecture.md 와 reconciliation 가능.

---

# Failure Scenarios

- overview.md content 가 architecture.md 와 conflict (예: stack 표기 mismatch) → spec drift. spot-check 강제 — architecture.md L11/L15 stack vs overview.md stack 동일.
- 기존 stub 의 `## Owned Data` / `## Published Interfaces` / `## Dependent Systems` 섹션 통합 시 정보 손실 우려 → 통합 대신 보존, 신규 섹션 추가만 (기존 7 섹션 유지 + 신규 4 섹션 append).

---

# Test Requirements

- HARDSTOP-03 hook PASS.
- CI self-CI PASS (markdown-only path-filter — 자연 SKIP 가능).
- 3 신규 file 의 cross-ref 정상.
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

- `/refactor-spec all --dry-run` 2026-05-13~14 ecommerce HIGH-B (3 frontend-edge service overview.md depth 부족 finding).
- Sibling depth source: TASK-FAN-BE-006 신규 overview.md (gateway-service + fan-platform-web, 2026-05-14 merged).
- Sibling closure pattern 답습: TASK-MONO-083 / TASK-BE-280 / TASK-BE-281 / TASK-SCM-BE-011 / TASK-MONO-084 / TASK-FAN-BE-006 — 모두 same-day single-PR closure.
- 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (mechanical enhancement, sibling pattern 답습).
