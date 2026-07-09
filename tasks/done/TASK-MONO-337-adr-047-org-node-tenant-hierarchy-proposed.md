# Task ID

TASK-MONO-337

# Title

ADR-MONO-047 PROPOSED — org-node tenant hierarchy (회사=grouping node → 서비스=isolated tenant → 도메인=구독; deny-ceiling entitlement guardrail inherited down; M1 preserved by grouping-not-nesting; AWS OU→Account / GCP Folder→Project 앱-레벨 이식)

# Status

done

# Owner

architecture

# Task Tags

- docs
- adr

---

# Goal

**회사 → 서비스 → 도메인 3축 조직 계층**의 아키텍처 결정을 **PROPOSED ADR-MONO-047** 로 기록한다 (ADR-019/020/023/024/044/045/046 staged-child 패턴).

**해결하려는 갭 (2026-07-10 검증됨)**: 격리 축은 오직 `tenant_id` = 고객사(회사) 하나뿐이고(ADR-019 D1, M1), 도메인 권한은 한 tenant 안에서 이미 완전히 계층적이다(`tenant_domain_subscription` → `*_OPERATOR` → granular, ADR-035). **그러나 "회사"와 "도메인" 사이의 구조 계층이 없다** — 한 회사가 **각각 격리된 여러 서비스**를 두고(예: 물류·영업 서비스가 서로 데이터 경계 분리, 각기 다른 wms/erp/scm 조합 구독) 싶어도 표현할 수단이 없다. 유일한 격리 단위가 tenant이므로 (a) 회사 전체를 tenant 하나로 두거나(서비스 격리 불가), (b) 서비스마다 tenant로 쪼개되 **이들을 "회사"로 다시 묶는 객체가 없다**(회사 단위 과금/관리/감사 = 수동 roll-up, 회사 단위 entitlement 경계를 걸 자리 없음).

이 ADR은 그 중간 계층 — **데이터 없는 `org_node` 그룹핑 노드가 tenant *위에* 얹혀 한 회사가 여러 격리 service-tenant를 소유하고, 노드에 붙은 entitlement ceiling이 트리 아래로 deny-only guardrail로 상속되며(절대 grant 아님), tenant는 여전히 단일 평면 격리 키로 유지(`org_node`는 tenant를 *그룹핑*하되 *중첩하지 않음*)** — 를 결정한다. AWS Organizations(OU→Account)·GCP Resource Hierarchy(Folder→Project)의 앱-레벨 이식. 다운스트림(구독·role 파생·confinement)은 ADR-019/020/024/035 재사용; 오직 org-node 그룹핑 + ceiling 상속 + node-scoped admin만 신규.

**이 태스크는 문서 전용 PROPOSED 기록.** ACCEPTED 전환 + 구현은 별도 user-explicit-intent-gated 태스크다. **Self-ACCEPT 금지.**

---

# Scope

## In scope
- `docs/adr/ADR-MONO-047-org-node-tenant-hierarchy.md` PROPOSED 작성 — 결정 D1..D7 + CHOSEN/rejected 표 + 보안 불변식(M1 single-isolation-key 미약화=grouping-not-nesting·ceiling narrow-only·SUPER_ADMIN net-zero·no-escalation·ADR-023 plane 분리·deny-default·fail-closed) + zero-regression 로드맵 + 선행 ADR 관계.

## Out of scope
- 어떤 코드·스펙·계약·시드 변경도 없음 (PROPOSED = 결정 기록만).
- ACCEPTED 전환 (별도 태스크, user-gated).
- 구현(`org_node`/`tenant.org_node_id` DDL·ceiling-intersection 서비스·`TenantScopeGuard` subtree driver·`ORG_ADMIN` seed·org-hierarchy UI·마이그레이션).
- M1·ADR-024 within-tenant confinement·ADR-035 derivation·`tenant_id` 격리 변경 (net-zero 유지).
- role-level ceiling(D3-B)·grant-at-node(D2-C)·cross-owner consortium(ADR-045 D1-C) — 전부 additive 후속.

---

# Acceptance Criteria

- [x] **AC-1**: `docs/adr/ADR-MONO-047-...md` 생성, `Status: PROPOSED`, staged 배너 포함.
- [x] **AC-2**: 결정 D1..D7 각각 CHOSEN(PROPOSED) + rejected 대안 표로 기록 (D1 group-vs-nest / D2 deny-ceiling-vs-accumulate / D3 domain-vs-role granularity / D4 tree shape / D5 ORG_ADMIN / D6 derivation 합성 / D7 migration).
- [x] **AC-3**: 보안 불변식 명시 — **M1 단일 격리 키 미약화**(org_node는 group, sub-tenant nest 아님 → row-isolation guard byte-unchanged, 토큰 단일 `tenant_id`), ceiling은 **narrow-only**(오설정 시 fail-closed), `ORG_ADMIN` ≤-own ∧ ≤ node-ceiling ∧ SUPER_ADMIN 불가, ADR-023 plane 분리 유지.
- [x] **AC-4**: 선행 ADR 관계 명시 — 019(customer-tenant = 격리 키, § D1 additive amend: optional parent node), 024(within-tenant admin → subtree로 확장, § D2 additive amend), 035(derivation ∩ ceiling, § O1 additive amend), 020(assignment = switcher 기질 재사용), 025(org_scope = intra-tenant 데이터스코프 대조/직교), 045 D1-C(cross-owner consortium = 형제, deferred).
- [x] **AC-5**: `§ History`에 PROPOSED 행만(ACCEPTED 행 없음). Self-ACCEPT 금지 문구.
- [x] **AC-6**: doc lint/링크 정합 — 참조 ADR/스펙 경로 존재 확인 (ADR-019/020/023/024/025/035/045 파일명 실재 검증 완료).

---

# Related Specs / Contracts

- [ADR-MONO-019](../../docs/adr/ADR-MONO-019-platform-console-customer-tenant-model.md) (customer-tenant = 격리 키, § D1 additive amend — optional parent `org_node`), [ADR-MONO-024](../../docs/adr/ADR-MONO-024-tenant-admin-delegation.md) (within-tenant 위임 → subtree 확장, § D2 additive amend), [ADR-MONO-035](../../docs/adr/ADR-MONO-035-operator-auth-unification-model.md) (subscription→role derivation, § O1 additive amend — ∩ ceiling), [ADR-MONO-020](../../docs/adr/ADR-MONO-020-operator-multitenant-assignment.md) (`operator_tenant_assignment` = 다중 service-tenant switcher 기질), [ADR-MONO-025](../../docs/adr/ADR-MONO-025-abac-data-scope-generalization.md) (`org_scope` = intra-tenant 데이터스코프 — 직교/대조), [ADR-MONO-023](../../docs/adr/ADR-MONO-023-entitlement-iam-plane-separation.md) (plane 분리), [ADR-MONO-045](../../docs/adr/ADR-MONO-045-cross-org-partner-delegation.md) § D1-C (cross-owner consortium = 형제, deferred)
- 재사용 프리미티브: `tenant`(ADR-019 격리 잎, 불변), `tenant_domain_subscription`(ADR-019/023), assume-tenant derivation(ADR-035), `TenantScopeGuard`+no-escalation(ADR-024/rbac.md)

# Edge Cases / Failure Scenarios

- **M1 오해 방지**: `org_node`는 tenant를 그룹핑하되 절대 중첩(sub-tenant)하지 않음 → 토큰은 여전히 단일 `tenant_id`, 모든 row-isolation guard 무변경. sub-tenant(D1-B)는 M1 위반으로 기각.
- **ceiling 오설정 = 안전한 실패**: deny-only이므로 오설정 시 reach가 **줄어듦**(fail-closed), 절대 확장 안 됨(D2-A). allow-accumulate(D2-B)는 오설정 시 over-grant라 기각.
- **child ⊄ parent ceiling**: write 시 강제(child ⊆ parent). 위반 입력 거부.
- **트리 cycle / 무한 깊이**: `parent_id` self-ref cycle-check + `max_depth` cap(D4)로 write 시 거부.
- **ORG_ADMIN 에스컬레이션**: node ceiling·granter holdings 초과 grant 시도 → deny(ADR-024 D2/D3 재사용). SUPER_ADMIN mint 불가.
- **기존 tenant 무영향**: `org_node_id` nullable ⟹ ungrouped singleton 합법 → 마이그레이션은 behavioural no-op, lazy 가능(D7).
- **org_scope 혼동**: ADR-025 `org_scope`(부서 서브트리, intra-tenant 데이터필터)와 이 ADR `org_node`(tenant 위 그룹핑)는 별개 축 — ADR에 명시 대조.
- self-ACCEPT 시도 = 위반. ACCEPTED는 사용자 명시 intent gate (별도 태스크).
