# Task ID

TASK-ERP-BE-005

# Title

ADR-MONO-019 § 3.3 step 3 복제 (erp) — erp masterdata-service 의 tenant 격리 게이트를 `tenant_id == erp` 고정에서 **entitlement-trust dual-accept** 로 진화. finance 파일럿(TASK-FIN-BE-006) blueprint 의 첫 복제.

# Status

done

> **완료 (2026-05-31)**: impl PR #962 (squash `b75fbed1`). ADR-MONO-019 § 3.3 step 3 복제 1/N (erp) — finance 파일럿(FIN-BE-006) blueprint 의 erp masterdata-service 복제(opus dispatch). erp `TenantClaimValidator`(decode)+`TenantClaimEnforcer`(filter, erp 에 신규 EnforcerTest)를 `tenant_id == erp` 고정 → **entitlement-trust dual-accept**: legacy(`tenant_id ∈ {erp,*}`) ∪ 서명 토큰 `entitled_domains ∋ erp`. 공유 정적 `isEntitled` 헬퍼 양 지점 적용, **거부 = !legacyOk && !entitled**(fail-closed). **net-zero**(claim 부재 시 legacy만). 격리 IT: entitled(wms+[erp])→2xx / non-entitled(scm)→403 TENANT_FORBIDDEN. architecture.md § Multi-tenancy + Failure #3 갱신. **3차원**(MERGED `b75fbed1` / tip 일치 / pre-merge 0 failing). **CI 1-pass**: erp Integration GREEN 1m39s. **scope-lock**: erp masterdata 만, 다른 도메인/console-bff/GAP populate/legacy 제거/step 2 = 0. erp validator 가 finance 와 byte-identical → 복제 near-mechanical(blueprint 일반화 확인). **남은 step 3 복제**: scm/wms(6-svc)/gap+console-bff + GAP `entitled_domains` populate(shared).

# Owner

backend

# Task Tags

- code
- security
- multi-tenant

---

# Dependency Markers

- **depends on**: ADR-MONO-019 ACCEPTED (MONO-153) + step 1 (BE-322) + **step 3 파일럿 (TASK-FIN-BE-006 #960 `df1efa5a`, finance — blueprint)**. 본 task = 파일럿 패턴의 erp 복제.
- **blueprint**: `TASK-FIN-BE-006` (finance) — erp masterdata `TenantClaimValidator` 는 finance 와 **byte-identical** 구조(validator decode + `TenantClaimEnforcer` filter + `CrossTenantHttpIntegrationTest`). 동일 dual-accept 편집.
- **paired shared follow-up (미완)**: GAP auth-service `entitled_domains` claim populate (step 2 와 함께). claim 부재 시 erp 는 legacy 만 → net-zero.
- **orthogonal to**: ADR-005/TASK-BE-317.
- **model**: 분석=Opus 4.8 / **구현 권장=Opus** (격리 게이트).

---

# Goal

finance 파일럿(FIN-BE-006)이 확정한 **entitlement-trust dual-accept** blueprint 를 erp masterdata-service 에 복제한다. erp 게이트(`TenantClaimValidator` decode + `TenantClaimEnforcer` filter, finance 와 byte-identical 구조)를:
- (legacy) `tenant_id ∈ {erp, *}` → 통과 (무변경).
- (entitlement-trust) 서명 토큰 `entitled_domains` claim ∋ erp → 통과.
- 거부 = **legacy 불충족 AND entitlement 불충족** (fail-closed).

`entitled_domains` 는 RS256/JWKS 검증 토큰 claim → 위조 불가. GAP populate 전엔 claim 부재 → legacy 만 → **production net-zero**.

# Scope

## In scope (erp masterdata-service)

1. `infrastructure/security/TenantClaimValidator.java`: `CLAIM_ENTITLED_DOMAINS` 상수 + 정적 `isEntitled(Jwt, String)` 헬퍼(null/비-list/비-string/빈 → false, fail-closed) + `validate()` dual-accept. **finance FIN-BE-006 머지본과 동일 편집.**
2. `presentation/filter/TenantClaimEnforcer.java`: 동일 dual-accept, `isEntitled` 재사용(단일 진실; decode-pass/filter-block split 방지).
3. 테스트: `TenantClaimValidatorTest`(+entitled/non-entitled/legacy/claim-안전성) / `CrossTenantHttpIntegrationTest`(entitled 타테넌트 → 403 아님 / non-entitled → 403; 기존 단언 무변경) / enforcer test 있으면 동일.
4. `specs/services/masterdata-service/architecture.md` § Multi-tenancy + Failure(cross-tenant) 서술 dual-accept 갱신.

## Out of scope

- GAP `entitled_domains` populate (별 shared follow-up).
- 다른 도메인(wms/scm/gap)+console-bff (별 복제 task).
- legacy `tenant_id == slug` 분기 제거 (step 4).
- step 2 / `tenant_domain_subscription` / admin catalog.

# Acceptance Criteria

- **AC-1**: erp `TenantClaimValidator` + `TenantClaimEnforcer` dual-accept (legacy `tenant_id ∈ {erp,*}` ∪ 서명 `entitled_domains ∋ erp`; 거부 = 둘 다 불충족).
- **AC-2 (net-zero)**: claim 부재 시 기존 동작 byte-identical — 기존 `CrossTenantHttpIntegrationTest` 단언 무변경 통과.
- **AC-3 (entitlement-trust)**: tenant_id=acme + `entitled_domains=[erp]` → 게이트 통과(403 아님); `entitled_domains=[wms]`/부재 → 403. 양 enforcement 지점 일관.
- **AC-4 (claim 안전성)**: 비-list/null/빈/비-string → fail-closed(legacy 만).
- **AC-5**: architecture.md § Multi-tenancy dual-accept 갱신.
- **AC-6**: erp masterdata 컴파일 + 전 테스트 GREEN — **CI Linux erp Integration(Testcontainers)** 권위 게이트. M6 회귀 0.
- **AC-7 (scope-lock)**: 다른 도메인/console-bff/GAP populate/legacy 제거/step 2 artifact 0. diff = erp masterdata-service 만.

# Related Specs

- `docs/adr/ADR-MONO-019-...md` § 2 D5 + § 3.3 step 3.
- `projects/finance-platform/tasks/done/TASK-FIN-BE-006-...md` (blueprint).
- `rules/traits/multi-tenant.md` M2/M3/M6.

# Related Contracts

- GAP 토큰 claim `entitled_domains` 수신측 신뢰 계약(producer populate 는 GAP follow-up).

# Related Code

- erp `infrastructure/security/TenantClaimValidator.java` + `presentation/filter/TenantClaimEnforcer.java` + `CrossTenantHttpIntegrationTest.java` (finance 와 byte-identical 구조).
- 템플릿 = finance 머지본 (`projects/finance-platform/apps/account-service/.../TenantClaimValidator.java` + `TenantClaimEnforcer.java`, main `df1efa5a`).

# Edge Cases

- **이중 enforcement 불일치**: 양쪽 동일 로직 필수(공유 헬퍼).
- **erp required-tenant-id 프로퍼티**: erp 의 `${...required-tenant-id:erp}` (정확 키는 구현 시 Grep 확인).
- **claim 형 변이 / wildcard / net-zero 회귀**: finance 와 동일 가드.

# Failure Scenarios

- blanket-trust(claim 부재→통과) → 격리 붕괴 → AC-4 게이트.
- enforcer 누락 → split → AC-3 양지점.
- 빅뱅(전 도메인 동시) → 본 task 는 erp 1개로 한정.

---

# Implementation Design Notes

- finance FIN-BE-006 머지본(main `df1efa5a`)을 정확한 템플릿으로 복제. erp `TenantClaimValidator` 는 finance 와 byte-identical → 동일 편집 + erp 의 expectedTenantId(=erp) 자동 반영.
- net-zero: legacy 무변경 + entitlement OR 추가.
- CI Linux erp Integration 권위 게이트. 컴파일만 로컬.
- 구현 = Opus.

---

# Notes

- ADR-MONO-019 § 3.3 step 3 복제 1/N (finance 파일럿 후 첫 복제). 후속: scm/wms/gap+console-bff + GAP populate. dependency-correct base = 본 머지 main.
