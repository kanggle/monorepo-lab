# Task ID

TASK-MONO-035

# Title

spec drift cleanup — TASK-MONO-030 의 W4-W8 follow-up 일괄 정리

# Status

review

# Owner

backend

# Task Tags

- chore
- spec

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Edge Cases
- Failure Scenarios

---

# Goal

TASK-MONO-030 (spec drift audit, PR #160) 에서 발견된 5건 Warning (W4-W8) 을 일괄 정리한다. 모두 형식 일관성 / stale 설명 보강 수준.

---

# Scope

## In Scope

### W4-W6 — wms / fan-platform OIDC issuer/JWKS URI 정렬

- wms `specs/integration/gap-integration.md`: OIDC Issuer URL `localhost:8081` → `gap.local` (다른 두 프로젝트와 일치)
- wms gap-integration.md: JWKS URI `/.well-known/jwks.json` → `/oauth2/jwks` (실제 GAP endpoint)
- fan-platform gap-integration.md: JWKS URI 동일 정렬

### W7 — ecommerce 이벤트 발행 서비스 architecture.md 의 Outbox 섹션 추가

ecommerce 의 이벤트 발행 서비스 (order-service, payment-service, promotion-service, review-service, shipping-service) 의 `specs/services/<svc>/architecture.md` 에 Outbox 섹션 추가:
- Pattern: Transactional Outbox
- Table: `outbox` (libs/java-messaging 의 표준 schema)
- Polling scheduler: `OutboxPollingScheduler` (libs base + project concrete)
- Topic 매핑

### W8 — wms architecture.md 섹션명 표준화

wms 5 active service 의 `## Architecture Style: Hexagonal` (값 inline) → `## Architecture Style` 헤더 + 별도 줄에 값. ecommerce / fan-platform 패턴과 정렬.

## Out of Scope

- Architecture 변경 자체 (Layered ↔ Hexagonal 등) — 본 task 는 spec 표기만
- 새 Outbox 패턴 도입 — 이미 존재하는 Outbox 를 spec 에 명시만

---

# Acceptance Criteria

- [ ] W4-W6: wms + fan-platform 의 gap-integration.md 의 issuer/JWKS URI 정렬 완료.
- [ ] W7: ecommerce 5 service 의 architecture.md 에 Outbox 섹션 추가.
- [ ] W8: wms 5 service 의 architecture.md 섹션명 표준화.
- [ ] sample build (4 프로젝트 각 1 service `:check`) PASS.

---

# Related Specs

- `tasks/done/TASK-MONO-030-spec-drift-audit.md` (audit 결과)
- 각 프로젝트 `specs/services/<svc>/architecture.md` + `specs/integration/gap-integration.md`

---

# Edge Cases

- ecommerce 의 Outbox 가 실제로 사용 중인지 코드 확인 후 spec 명시 (사용 안 하면 spec 추가 안 함).
- wms gap-integration.md 의 issuer 변경이 실제 코드 / docker-compose 와 일치하는지 확인.

---

# Failure Scenarios

- spec 변경이 실제 동작과 어긋남 → spec 작성자가 코드 확인 후 수정.

---

# Test Requirements

- spec docs-only 변경 → sample build 로 영향 없음 확인.

---

# Definition of Done

- [ ] W4-W8 모두 fix.
- [ ] sample build PASS.
- [ ] Ready for review.

---

# Prerequisites

- ✅ TASK-MONO-030 완료
