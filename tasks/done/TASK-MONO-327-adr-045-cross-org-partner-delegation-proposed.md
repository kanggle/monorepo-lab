# Task ID

TASK-MONO-327

# Title

ADR-MONO-045 PROPOSED — cross-org partner delegation (협력사/공급사 조직이 다른 회사 테넌트의 bounded slice를 운영; 관계-단위 오프보딩; ADR-024 within-tenant 위임을 조직 경계 너머로 확장)

# Status

done

# Owner

architecture

# Task Tags

- docs
- adr

---

# Goal

**cross-org 파트너 위임**의 아키텍처 결정을 **PROPOSED ADR-MONO-045** 로 기록한다 (ADR-019/020/023/024/030/042/044 staged-child 패턴).

**해결하려는 갭 (2026-07-04 검증됨)**: 한 사람의 통합 정체성은 이미 ① 소비자+운영자 facet(ADR-032/036), ② 자기 회사 테넌트 owner-admin(ADR-044), ③ 소속사 테넌트 operator-employee(ADR-020 `operator_tenant_assignment` + ADR-024 `admin_operator_roles.tenant_id` 다중행 + assume-tenant 재-스코프)를 **완전히 지원·confined**(BE-467/468, ADR-024 D2/D3, M1-M7). **그러나 ④ 협력사/공급사 조직이 다른 회사 테넌트를 운영하는 일급 모델이 없다** — 오늘날 유일한 방법은 B사의 *개별* operator를 A사 테넌트에 grant하는 것뿐이고, 이는 (a) A가 B의 인사(퇴사)를 추적·수동 회수해야 하고(고용 lifecycle 연동 없음, cascade 없음), (b) 관계를 bound/audit/terminate할 객체가 없다.

이 ADR은 그 네 번째 모자 — **host 테넌트 A가 partner 테넌트 B에게 bounded `{domains}×{roles}` slice를 위임하고, B가 자기 operator 중 누가 참여할지 관리하며, 파트너십 종료·B직원 이탈 시 A-접근이 관계 단위로 cascade 회수되는** — cross-org 위임 + 관계-단위 오프보딩을 결정한다. 다운스트림(operator·role·confinement)은 ADR-020/024 재사용; 오직 cross-org origination만 신규.

**이 태스크는 문서 전용 PROPOSED 기록.** ACCEPTED 전환 + 구현은 별도 user-explicit-intent-gated 태스크다. **Self-ACCEPT 금지.**

---

# Scope

## In scope
- `docs/adr/ADR-MONO-045-cross-org-partner-delegation.md` PROPOSED 작성 — 결정 D1..D8 + CHOSEN/rejected 표 + 보안 불변식(attenuation cap·two-sided consent·≤-own across org·no transitive re-delegation·cascade offboarding·M1-M7·SUPER_ADMIN net-zero) + zero-regression 로드맵 + 선행 ADR 관계.

## Out of scope
- 어떤 코드·스펙·계약·시드 변경도 없음 (PROPOSED = 결정 기록만).
- ACCEPTED 전환 (별도 태스크, user-gated).
- 구현(`tenant_partnership` aggregate/invite·accept/evaluator cross-org branch/cascade-revoke/UI).
- `SUPER_ADMIN`·ADR-024 within-tenant confinement·ADR-020 assignment 변경 (net-zero 유지).
- N-way consortia(D1-C)·broker gate(D2-C)·partner billing·ABAC per-resource cross-org data scope (전부 additive 후속).

---

# Acceptance Criteria

- [x] **AC-1**: `docs/adr/ADR-MONO-045-...md` 생성, `Status: PROPOSED`, staged 배너 포함.
- [x] **AC-2**: 결정 D1..D8 각각 CHOSEN(PROPOSED) + rejected 대안 표로 기록.
- [x] **AC-3**: 보안 불변식 명시 — cross-org 접근은 host-authored `delegated_scope`에 국한(A의 TENANT_ADMIN/SUPER_ADMIN 불가), ≤-own이 조직 경계 넘어 유지, two-sided consent, **no transitive re-delegation**(confused-deputy default deny), cascade offboarding, M1-M7·SUPER_ADMIN net-zero 미약화.
- [x] **AC-4**: 선행 ADR 관계 명시 — 024(within-tenant 위임 = 이 ADR가 조직 경계 너머로 확장, § D4-B additive amend / 다운스트림 = 024 재사용), 020(grant 기질 재사용, § D6 additive amend), 042(participant≠tenant 대조), 044(within-org origination 선례), 032/036(통합 신원), 023(plane 분리).
- [x] **AC-5**: `§ Status Transition History`에 PROPOSED 행만(ACCEPTED 행 없음). Self-ACCEPT 금지 문구.
- [ ] **AC-6**: doc lint/링크 정합 — 참조 ADR/스펙 경로 존재 확인.

---

# Related Specs / Contracts

- [ADR-MONO-024](../../docs/adr/ADR-MONO-024-tenant-admin-delegation.md) (within-tenant 위임 — 이 ADR가 조직 경계 너머로 확장, § D4-B additive amend), [ADR-MONO-020](../../docs/adr/ADR-MONO-020-operator-multitenant-assignment.md) (assignment 기질, § D6 additive amend), [ADR-MONO-044](../../docs/adr/ADR-MONO-044-self-service-tenant-onboarding.md) (within-org origination 선례), [ADR-MONO-042](../../docs/adr/ADR-MONO-042-ecommerce-seller-onboarding-iam-provisioning.md) (participant≠tenant 대조), [ADR-MONO-032](../../docs/adr/ADR-MONO-032-unified-identity-roles-model.md), [ADR-MONO-036](../../docs/adr/ADR-MONO-036-born-unified-identity-provisioning.md), [ADR-MONO-023](../../docs/adr/ADR-MONO-023-entitlement-iam-plane-separation.md)
- 재사용 프리미티브: `operator_tenant_assignment`(ADR-020), `admin_operator_roles.tenant_id` + `AdminGrantScopeEvaluator`(ADR-024/rbac.md), born-unified mint(ADR-036)

# Edge Cases / Failure Scenarios

- confused-deputy/전이 위임: B가 A의 `delegated_scope`를 제3조직 C에게 재위임 시도 → default deny(ADR-024 within-tenant sub-delegation confinement 미러).
- 파트너십 스팸/accept 사회공학 → D2 two-sided handshake + optional D2-C broker gate; invite rate-limit는 additive.
- cross-org 감사 legibility: B operator의 A 내 액션은 `(identity + A-tenant + partnership_id + delegated permission)` 기록(D5).
- 절반 상태(invite PENDING, B 미수락) → 접근 0(ACTIVE 전엔 derivation 없음).
- self-ACCEPT 시도 = 위반. ACCEPTED는 사용자 명시 intent gate (별도 태스크).
