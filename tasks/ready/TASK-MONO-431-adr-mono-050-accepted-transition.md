# Task ID

TASK-MONO-431

# Title

ADR-MONO-050 PROPOSED → ACCEPTED 전환 — scm→wms inbound-expected 폐루프 governance flip + Phase 1/2 task 스폰 (user-explicit ADR-naming intent, self-ACCEPT 아님)

# Status

ready

# Owner

architecture / docs

# Task Tags

- docs
- governance
- adr

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

[ADR-MONO-050](../../docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md) 을 PROPOSED → **ACCEPTED** 로 전환한다. 트리거: 사용자가 PROPOSED 문서 검토 후 **지목형 intent "ADR-MONO-050 ACCEPTED"** 발화 (2026-07-19). 이는 [`platform/architecture-decision-rule.md` § The ACCEPTED Gate](../../platform/architecture-decision-rule.md) 가 요구하는 exact-form(ADR 이름 지목) intent 로, **맨 "진행"은 앞 턴에서 정확히 거부**된 뒤 도착했다 (게이트 규율 준수, self-ACCEPT 아님 — 제안 에이전트가 비준하지 않고 사용자가 지시).

전환으로 Phase 1 병렬 task(SCM-BE-034/035 ∥ BE-506/507) + Phase 2(SCM-INT-004) 의 blocked 상태가 해소된다.

---

# Scope

## In Scope

1. **`docs/adr/ADR-MONO-050-...md` Status flip**: `PROPOSED` → `ACCEPTED`; Date 라인에 `ACCEPTED 2026-07-19` + 지목형 intent 근거; §6 Status history 에 ACCEPTED row append (PROPOSED row 불변, append-only).
2. **Phase 1/2 task 스폰** (별 spec PR 또는 본 흐름): scm queue 에 SCM-BE-034(scm 권위 이벤트 스키마)·SCM-BE-035(procurement CONFIRMED 발행 impl)·SCM-INT-004(E2E); wms queue 에 BE-506(consumer-driven 구독 컨트랙트)·BE-507(inbound-service consumer + InboundExpectation impl). 각 큐 INDEX 갱신.
3. root INDEX: 430 완료 표기 + 431 done 이동(lifecycle).

## Out of Scope

- ADR-050 §2 결정 본문 변경 0 (flip 만; 결정은 PROPOSED 시점 확정).
- Phase 1/2 실 구현 0 (각 스폰 task 소관).

---

# Acceptance Criteria

1. ADR-050 Status = **ACCEPTED**, Date 라인 + §6 ACCEPTED row 에 지목형 intent("ADR-MONO-050 ACCEPTED") 명시, "NOT self-ACCEPT / 사용자 지시" 근거 포함.
2. §6 PROPOSED row 불변 (append-only — ACCEPTED row 만 추가).
3. Phase 1/2 task 5건이 각 큐 ready/ 에 스폰 + INDEX 등록 (SCM-BE-034/035·SCM-INT-004 scm, BE-506/507 wms).
4. doc/governance-only: 코드/projects 코드/빌드/CI 0 (task 파일 + ADR flip + INDEX 만).
5. self-ACCEPT 0 확인: 전환 근거 = 사용자 지목형 intent (에이전트 unilateral 아님).

---

# Related Specs

- [ADR-MONO-050](../../docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md) — 전환 대상
- [`platform/architecture-decision-rule.md`](../../platform/architecture-decision-rule.md) § The ACCEPTED Gate — exact-form intent 규율
- [TASK-MONO-430](TASK-MONO-430-adr-mono-050-scm-wms-inbound-expected-proposed.md) — PROPOSED 작성(선행)
- ADR-MONO-027 §6 (TASK-MONO-220 accept) — 동형 PROPOSED→ACCEPTED 2단계 선례

---

# Related Contracts

- 없음 (governance flip + task 스폰). 실 contract 는 SCM-BE-034 / BE-506 소관.

---

# Target Service / Component

- `docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md` (Status flip)
- Phase 1/2 task 파일 (scm/wms 큐 신규) + INDEX
- (no production code change)

---

# Edge Cases

1. **맨 "진행" 재수신**: exact-form 아니면 flip 금지 (앞 턴 실제로 거부함). ADR 이름 지목 필수.
2. **§6 append-only 위반 위험**: PROPOSED row rewrite 금지, ACCEPTED row 만 append.
3. **Phase 1 병렬성**: scm/wms lane disjoint 확인 (공유파일=D7 문서 cross-ref 1회) — 병렬 스폰.

---

# Failure Scenarios

## A. 사용자가 §2 수정 요청하며 ACCEPT

→ flip 금지. ADR-050 을 먼저 개정(PROPOSED 유지) 후 재검토 → 지목형 ACCEPT 재수신 시 전환.

## B. self-ACCEPT 유혹

→ 제안 에이전트는 비준 못 함. 사용자 지목형 intent 없으면 PROPOSED 고수.

---

# Test Requirements

- ADR-050 `grep "Status.*ACCEPTED"` 1건 + §6 ACCEPTED row 실재.
- doc/task-only diff scope.
- Phase 1/2 task 5건 ready/ 실재 + INDEX 등록.

---

# Definition of Done

- [ ] ADR-050 Status → ACCEPTED (Date + §6 append, 지목형 intent 근거)
- [ ] Phase 1/2 task 5건 스폰 + INDEX
- [ ] self-ACCEPT 0 (사용자 지시 근거)
- [ ] doc-only diff scope

---

# Notes

- **Recommended impl model**: Sonnet (governance flip + task 스폰 = 기계적; 판단 요소는 ADR-050 작성 시 소진). 단 Phase 1 impl 스폰 task 들은 Opus 권장(event-driven outbox/consumer, cross-project).
- **dependency**: `선행`=TASK-MONO-430(PROPOSED). `후속`=Phase 1 병렬(SCM-BE-034/035 ∥ BE-506/507) → Phase 2 SCM-INT-004.
