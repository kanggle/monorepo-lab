# Task ID

TASK-SCM-INT-004

# Title

inbound-expected 폐루프 cross-service E2E — scm PO CONFIRMED → wms InboundExpectation (+federation live leg) (ADR-MONO-050 Phase 2)

# Status

done

# Owner

scm / wms / integration

# Task Tags

- e2e
- integration
- scm
- wms

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

[ADR-MONO-050](../../../../docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md) §3 Phase 2: 폐루프를 end-to-end 검증한다. **Phase 1 두 lane(SCM-BE-035 발행 + BE-507 소비) 완료 후 재합류** task.

두 층: (a) **PR-gated 결정적 Testcontainers E2E**(권위 가드) + (b) **federation-hardening-e2e live leg**(nightly, 실 scm 발행→실 wms 반응).

---

# Scope

## In Scope

1. **Testcontainers cross-service E2E**: scm procurement PO `CONFIRMED` → `scm.procurement.inbound-expected.v1` → wms `inbound-service` → `InboundExpectation(EXPECTED)` 등장 단언. 케이스: happy / eventId 멱등 / `(poNumber,line)` business dedup / **멀티창고 라우팅**(2 warehouseId→2 창고) / 미지창고 fail-closed / 3PL nodeType reject / cancel→CANCELLED.
2. **federation-hardening-e2e(root) live leg**: 실 scm+wms 컨테이너로 PO CONFIRMED→wms InboundExpectation 확인. ADR-027 SCM-INT-002 선례 배선.
3. PR-gated 결정적 레인을 권위 가드로(federation-e2e 는 nightly=silent regress 가능, ADR-050 §5/027 §5 caveat).

## Out of Scope

- 발행/소비 impl 0 (SCM-BE-035/BE-507).
- 하류 입고 흐름 E2E 0 (기존 wms 커버리지).

---

# Acceptance Criteria

1. Testcontainers E2E 가 폐루프(CONFIRMED→InboundExpectation) 전 케이스(happy/멱등/dedup/멀티창고/fail-closed/3PL/cancel) 단언, PR-gated GREEN.
2. federation live leg 배선(nightly).
3. **멀티+단일창고 둘 다 E2E 로 실증**(D3 사용자 요구 최종 검증).
4. 두 프로젝트 impl 완료 의존(SCM-BE-035 + BE-507 머지 후).

---

# Related Specs

- [ADR-MONO-050](../../../../docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md) §3 Phase 2 / §5 caveat
- [TASK-SCM-BE-035](TASK-SCM-BE-035-publish-inbound-expected-on-po-confirmed.md) + [TASK-BE-507](../../../wms-platform/tasks/ready/TASK-BE-507-inbound-service-consume-inbound-expected.md) — 선행 impl
- ADR-MONO-027 SCM-INT-002 — 동형 cross-service E2E + federation live leg 선례
- `tests/federation-hardening-e2e/` (root)

---

# Related Contracts

- `scm-procurement-events.md` / `scm-inbound-expected-subscriptions.md`

---

# Target Service / Component

- `projects/scm-platform/tests/e2e/` (Testcontainers)
- `tests/federation-hardening-e2e/` (root, live leg)

---

# Edge Cases

1. federation-e2e 는 nightly(silent regress) → PR-gated Testcontainers 가 권위 가드(027 fed-e2e trap #1).
2. 멀티창고 E2E = master 에 2창고 시드 필요.

---

# Failure Scenarios

## A. live leg 만 있고 PR-gated 없음

→ nightly RED 가 초록으로 보임(알림없는 RED). PR-gated 결정적 레인 필수.

---

# Test Requirements

- Testcontainers E2E 전 케이스 GREEN(멀티+단일창고 포함) + federation live leg 배선.

---

# Definition of Done

- [ ] Testcontainers cross-service E2E(전 케이스, 멀티+단일창고)
- [ ] federation live leg
- [ ] PR-gated 권위 가드

---

# Notes

- **Recommended impl model**: **Opus** (cross-service E2E 오케스트레이션). 선행=SCM-BE-035 + BE-507 **양자 완료**(Phase 1 재합류). 병렬 아님.
