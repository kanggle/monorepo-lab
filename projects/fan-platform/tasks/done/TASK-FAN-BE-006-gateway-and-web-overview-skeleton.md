# Task ID

TASK-FAN-BE-006

# Title

`gateway-service/overview.md` + `fan-platform-web/overview.md` skeleton authoring (refactor-spec all 2026-05-13~14 fan-platform critical #1+2)

# Status

done

# Owner

fan-platform

# Task Tags

- fan-platform
- spec
- skeleton
- be
- fe

---

# Goal

`/refactor-spec all --dry-run` (2026-05-13~14) fan-platform audit critical #1+2 finding closure.

fan-platform 4 service 중 sibling 2 (artist-service + community-service) 는 `overview.md` + `architecture.md` 둘 다 보유 — 하지만 **gateway-service 와 fan-platform-web 는 `overview.md` 미존재** (only `architecture.md`). 그 결과:

- gateway-service `architecture.md` = 306 line (dense routes / JWT validation / failure modes / testing / references) — 1-pager 진입 자료 부재.
- fan-platform-web `architecture.md` = dense FSD layer 구조 + provider config + boundary rules — 1-pager 부재.

본 task = sibling 2 (artist-service + community-service) overview.md skeleton 답습으로 2 file authoring.

### sibling overview.md skeleton (artist-service + community-service)

7 section 표준:
1. `# <service> — Overview` + `> 1-pager:` 한 줄 설명
2. `## Service identity` table (Service name / Project / Service Type / Architecture Style / Stack / Bounded Context / Deployable unit / Persistent stores / Event publication)
3. `## Responsibilities` (bulleted)
4. `## Public API surface (요약)` table (or analogous = pages for frontend)
5. `## Key invariants` (numbered)
6. `## Out of scope (v1)` (bulleted)

provenance: `/refactor-spec all --dry-run` 2026-05-13~14 fan-platform audit critical #1+2 (gateway-service + fan-platform-web overview.md 부재). audit 의 fan-platform-section H1/H2 evidence 와 일치.

---

# Scope

## In Scope

### A. `gateway-service/overview.md` authoring

신규 file `projects/fan-platform/specs/services/gateway-service/overview.md`. content source = `gateway-service/architecture.md` 의 § Identity + § Role + § Routes + § Architecture Style Rationale 영역 발췌.

7 section 답습:
- **Identity**: rest-api (edge gateway), Layered, Spring Cloud Gateway (reactive), Java 21 / Spring Boot 3.4, stateless + Redis (rate limit only), event publication = none
- **Responsibilities**: edge entry point / JWT validation (GAP JWKS) / tenant isolation (`tenant_id=fan-platform`) / identity header strip + enrichment / rate limit (per `(account, route)`) / RewritePath / error envelope normalize / OTel trace propagation
- **Public API surface**: 4 routes (community / artists / artist-groups / fandoms) — `architecture.md` 의 Routes 표 from TASK-FAN-BE-005
- **Key invariants**: GAP JWKS validation / tenant gate / 외부 진입점 단일 / business logic 부재 / stateless
- **Out of scope (v1)**: membership / notification / admin routing (v2), business logic, persistence, domain events

### B. `fan-platform-web/overview.md` authoring

신규 file `projects/fan-platform/specs/services/fan-platform-web/overview.md`. content source = `fan-platform-web/architecture.md` 의 § Identity + § Architecture Style Rationale + § Package Layout 영역 발췌.

7 section 답습 (frontend 특성):
- **Identity**: frontend-app, FSD lite, Next.js 15 App Router / React 19 / TypeScript 5, gateway + GAP IdP backend deps
- **Responsibilities**: 5 페이지 (feed / artists directory / artist detail / post detail / membership stub) + login + middleware redirect / next-auth v5 PKCE (`/api/auth/[...nextauth]`) / Server Components data fetch / OAuth2 callback
- **Public surface**: pages (UI routes) + 1 next-auth handler — public/gated 분류
- **Key invariants**: HttpOnly cookie auth only (no localStorage tokens), accessToken 은 Server Components / Server Actions / route handlers 안에서만 (`getFanSession()`), tenant locked = `fan-platform`, MEMBERS_ONLY/PREMIUM 시각화 v1 stub
- **Out of scope (v1)**: 댓글 composer / 모더레이션 UI / 멤버십 결제 flow / artist self-service / 미디어 업로드

### C. cross-ref 검증

- 본 2 file 이 sibling 의 architecture.md 와 cross-reference 정상 (overview ↔ architecture 양방향).
- fan-platform `PROJECT.md` 의 Service Map table 에 본 2 service 가 entry 보유 (현재 그러함 — 확인 only).

## Out of Scope

- architecture.md 본문 수정 (overview.md authoring 만).
- 다른 audit finding (CRIT-3+4 GAP / CRIT-2 WMS notification 등 — 별 task 영역).
- 다른 audit medium/low finding (fan-platform 의 multi-line consistency 등).

---

# Acceptance Criteria

### Impl PR

- [x] `gateway-service/overview.md` 신규 file 7 section skeleton authoring (~60 line, sibling 답습 + edge gateway 특성).
- [x] `fan-platform-web/overview.md` 신규 file 7 section skeleton authoring (~80 line, sibling 답습 + frontend 특성 — pages table, HttpOnly cookie auth 등).
- [x] cross-ref 검증 — 본 2 file 이 architecture.md 와 정상 연결 (in-file relative link).
- [x] HARDSTOP-03 hook PASS (project-specific content = fan-platform 자기 자신, 다른 project name leak 0).
- [ ] CI self-CI PASS (path-filter fan-platform markdown-only — 15 SKIP + 1 changes PASS 예상).
- [x] task lifecycle ready → review (in-progress 우회, mechanical batch single-PR closure 패턴, TASK-BE-281 / TASK-MONO-084 precedent).
- [x] fan-platform tasks/INDEX.md 동기.

### Close chore PR

- [ ] task Status review → done.
- [ ] git mv tasks/review → tasks/done.
- [ ] fan-platform tasks/INDEX.md ## review 제거, ## done append 1-line outcome.

---

# Related Specs

- `projects/fan-platform/specs/services/artist-service/overview.md` (sibling skeleton source).
- `projects/fan-platform/specs/services/community-service/overview.md` (sibling skeleton source).
- `projects/fan-platform/specs/services/gateway-service/architecture.md` (gateway overview content source).
- `projects/fan-platform/specs/services/fan-platform-web/architecture.md` (web overview content source).
- `projects/fan-platform/PROJECT.md` (Service Map cross-ref).

---

# Related Contracts

본 task = 1-pager overview spec authoring. HTTP API / event payload 변경 0.

---

# Target Service

- `projects/fan-platform/apps/gateway-service/` (gateway-service overview 대상).
- `projects/fan-platform/web/fan-platform-web/` (frontend overview 대상).

---

# Architecture

fan-platform v1 의 4 service 중 2 미커버 service (gateway / fan-platform-web) 의 1-pager overview skeleton. portfolio 평가자 진입 자료 일관성.

---

# Implementation Notes

## sibling overview.md 7-section template

```markdown
# <service> — Overview

> 1-pager: responsibilities, public API surface, key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `<name>` |
| Project | `fan-platform` |
| Service Type | `<type>` |
| Architecture Style | **<style>** |
| Stack | <stack> |
| Deployable unit | `<path>` |
| Bounded Context | `<context>` |
| Persistent stores | <stores> |
| Event publication | <topics or none> |

## Responsibilities

- <bullet 1>
- ...

## Public API surface (요약) (or "Pages" for frontend)

자세한 스펙은 `specs/contracts/...` (또는 architecture.md) 참조.

| Method | Path | 설명 | Auth |
|---|---|---|---|
| ... |

## Key invariants

1. ...
2. ...

## Out of scope (v1)

- ...
```

## 본 task 의 lifecycle 단축

mechanical skeleton fill (sibling pattern 답습) → ready → review 직접 (in-progress 우회). TASK-BE-281 / TASK-MONO-084 precedent.

---

# Edge Cases

- fan-platform-web 의 경우 frontend 라 "Public API surface" 가 pages (UI routes). sibling 의 REST endpoint table 과 다른 shape — section 이름 정정 ("Pages (요약)" 등) 가능.
- gateway-service 의 경우 stateless + no domain event — sibling 의 "Event publication" row 가 `none` 으로 명시.
- Architecture Style 표기는 sibling 과 동일 italic-bold (예: **Layered** / **FSD lite**).

---

# Failure Scenarios

- overview.md content 가 architecture.md 와 conflict (예: stack 표기 mismatch) → spec drift. spot-check 강제 (architecture.md L11 stack vs overview.md stack 동일).
- sibling pattern 너무 엄격하게 따르려다 frontend 특성 무시 → 본 task scope = sibling 패턴 + frontend 특수성 둘 다 고려.

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

- `/refactor-spec all --dry-run` 2026-05-13~14 fan-platform audit critical #1+2 (gateway-service + fan-platform-web `overview.md` 부재 finding).
- Sibling skeleton source: artist-service / community-service overview.md.
- Sibling closure pattern 답습: TASK-MONO-083 / TASK-BE-280 / TASK-BE-281 / TASK-SCM-BE-011 / TASK-MONO-084 — 모두 same-day single-PR closure.
- 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (mechanical skeleton authoring, sibling pattern 답습).
