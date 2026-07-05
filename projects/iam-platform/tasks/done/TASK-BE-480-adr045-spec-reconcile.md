# Task ID

TASK-BE-480

# Title

ADR-MONO-045 세션(BE-478/PC-FE-187/BE-479) 구현→스펙 정합 — auth-service assume-tenant cross-org cap, admin data-model delegated_scope invite-enforcement, console-web feature 레지스트리(subscriptions+partnerships)

# Status

done

# Owner

backend

# Task Tags

- docs
- spec-reconcile

---

# Goal

이번 ADR-MONO-045 세션에서 코드로 반영된 동작이 **source-of-truth 스펙에 아직 미반영**된 갭을 정합한다(specs win — 구현이 앞서갔으면 스펙을 뒤따라 맞춘다). 순수 문서 정합(코드/계약 변경 0). 세 갭:

1. **auth-service `architecture.md`** — assume-tenant token-exchange 발급 경로 서술(현재 step 1~3)에 **BE-478 cross-org 파트너십 cap** 이 전무. `/internal/operator-assignments/check` 가 `delegatedScope` 를 반환하는 파트너십-파생 host reach 케이스에서 `entitled_domains = host-ACTIVE ∩ delegatedScope.domains`, `roles = delegatedScope.roles` verbatim 으로 캡됨(admin scope 미방출)을 추가.
2. **admin-service `data-model.md`** — `tenant_partnership.delegated_scope` cap 불변식(≤-own)이 "host 가 보유한 domain·role 만"으로만 서술. **BE-479 의 concrete invite-time enforcement**(도메인 ∈ host ACTIVE 구독 / role ∈ `DelegatableRoleCatalog` operator-tier 허용목록; account 장애 fail-CLOSED)를 명시하고 request-time host-holds = 의도적 unbounded 를 rbac.md cross-ref.
3. **platform-console `console-web/architecture.md`** — app/(console) 라우트 레지스트리 + features/ 트리에 **PC-FE-183 `subscriptions`(구독 self-enablement)** 및 **PC-FE-187 `partnerships`(cross-org 파트너십 관리)** 항목이 둘 다 부재. 두 '조직 설정' entitlement/admin-plane operator-gated 표면을 레지스트리 스타일로 추가.

# Scope

## In scope

- `projects/iam-platform/specs/services/auth-service/architecture.md` — assume-tenant 경로에 cross-org cap step 추가(BE-478; `delegatedScope` 소비, entitled_domains 교집합·roles verbatim, admin scope 불변, [auth-to-admin.md](../../contracts/http/internal/auth-to-admin.md) cross-ref).
- `projects/iam-platform/specs/services/admin-service/data-model.md` — `delegated_scope` cap 불변식(line ~169)에 BE-479 concrete enforcement + request-time unbounded 명시(rbac.md cross-ref).
- `projects/platform-console/specs/services/console-web/architecture.md` — app/(console) 라우트에 `subscriptions/`·`partnerships/`, features/ 트리에 `subscriptions/`·`partnerships/` 항목 추가(기존 `✅ (TASK-PC-FE-xxx)` 스타일; credential=operator token, '조직 설정' sidebar 그룹, ADR-023 평면 분리).

## Out of scope

- 코드·계약(admin-api.md/auth-to-admin.md)·데이터 변경 — 이미 정확(계약은 BE-476/477 에서 소비 규약까지 기술됨). 순수 서술 정합만.
- rbac.md — BE-479 에서 이미 정합됨(재변경 없음).
- 다른 미등록 feature 소급 정합(예: ecommerce-ops 하위 세부) — 이 세션 범위 밖.
- console-integration-contract.md § 2.4 신규 바인딩 절 — subscriptions/partnerships 는 IAM operator-token 표면(§§2.4.1–2.4.4 그룹 규칙 상속)이라 신규 per-domain 바인딩 절 불요(§2.4.5+ 는 non-IAM domain-facing 토큰용). 필요 판단 시 architecture.md 항목에서 그 상속을 명시.

# Acceptance Criteria

- [ ] **AC-1** auth-service architecture.md 의 assume-tenant 경로에 BE-478 cross-org cap(delegatedScope→entitled_domains 교집합·roles verbatim·admin scope 불변)이 서술되고 auth-to-admin.md 로 cross-ref.
- [ ] **AC-2** admin data-model.md 의 `delegated_scope` cap 불변식이 BE-479 concrete invite-time enforcement(도메인 ∈ 구독 / role ∈ operator-tier 허용목록 / fail-CLOSED)와 request-time unbounded 를 명시하고 rbac.md cross-ref.
- [ ] **AC-3** console-web architecture.md 의 라우트 레지스트리 + features/ 트리에 `subscriptions`·`partnerships` 항목이 기존 스타일로 추가(TASK-PC-FE-183/187, operator token, '조직 설정').
- [ ] **AC-4** 링크 정합 — 추가한 cross-ref 경로(auth-to-admin.md, rbac.md) 존재 확인. 기존 서술 byte-보존(추가만, 삭제/왜곡 없음).
- [ ] **AC-5** 코드·계약·스키마·테스트 변경 0(순수 doc). CI markdown fast-lane.

# Related Specs

- `docs/adr/ADR-MONO-045-cross-org-partner-delegation.md` — 정합 대상 세션의 ADR.
- `projects/iam-platform/specs/services/admin-service/rbac.md` § Cross-Org Partner Delegation Confinement (BE-479 정합 완료 — 이 task 는 data-model/auth-arch/console-arch 를 그에 맞춤).
- `projects/iam-platform/specs/contracts/http/internal/auth-to-admin.md` § delegatedScope(BE-476/477 기술 — auth-arch 가 이를 cross-ref).

# Related Contracts

- 변경 없음(소비만). admin-api.md § Partnership Management / auth-to-admin.md § delegatedScope 는 이미 정확.

# Edge Cases

- 추가 서술이 기존 numbered step/표 구조를 깨지 않도록(append/insert-in-place, 링크 anchor 보존).
- console-web architecture.md 는 조밀한 유지 레지스트리 — 신규 2항목은 인접 IAM-plane 항목(accounts/operators) 밀도에 맞춰 concise 하게.

# Failure Scenarios

- 스펙-only 라 런타임 실패 없음. CI = markdown lane(코드 잡 skip). 링크 깨짐만 주의(추가 cross-ref 경로 실재 확인).
