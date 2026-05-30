# Task ID

TASK-BE-323

# Title

ADR-MONO-019 § 3.3 step 3 복제 (wms) — wms-platform 의 6 서비스 tenant 격리 게이트(`TenantClaimValidator` ×6)를 `tenant_id == wms` 고정에서 **entitlement-trust dual-accept** 로 진화. finance/erp/scm blueprint 의 wms 복제 (validator-only, 최대 단일 도메인).

# Status

done

> **완료 (2026-05-31)**: impl PR #966 (squash `0e20b02a`). ADR-MONO-019 § 3.3 step 3 복제 3/N (wms, 6 서비스 validator-only; opus dispatch). **6 `TenantClaimValidator`**(gateway/admin/inbound/inventory/master/outbound)를 `tenant_id==wms` strict → **entitlement-trust dual-accept**: legacy strict equals(`tenant_id==wms`, **wildcard `*` 미도입** — finance/erp/scm blueprint 의 `WILDCARD_TENANT` 분기 의도적 제외, wms net-zero) ∪ 서명 `entitled_domains ∋ wms`, **거부=!legacyOk && !entitled**(fail-closed). **서비스별 로컬 isEntitled/safeStringList**(모듈 경계). `TenantClaimExtractor`/notification-service 무변경. **net-zero**(claim 부재 시 legacy strict 만). 6 validator 각 단위테스트 3 케이스(entitled cross-slug 통과 / wrong-domain 거부 / malformed fail-closed). **admin-service `TenantClaimValidatorTest` 신규**(기존 부재 — dual-accept 커버리지 확보). gateway-service overview.md § JWT-validation dual-accept 갱신(타 서비스 spec 은 v1-deferred forward-decl → 무변경). **3차원**(MERGED `0e20b02a` / tip 일치 / pre-merge 0 non-success). **BE-299 re-stage** ✓. **CI**: 1차 `LocationIntegrationTest:142` 선재 flake(`shortSuffix()`=`10+random*890` 충돌 → 중복 warehouseCode 409; validator 무관 — 깨졌으면 28개 전 인증 IT 실패, 1개만 + legacy 경로 무변경) → `--failed` 재실행 GREEN(2m44s). **scope-lock**: wms 6 validator+test+gateway spec 만. **남은 step 3**: gap+console-bff + GAP `entitled_domains` populate(shared). **메타**: wms validator 는 strict-equals(no wildcard)였으므로 blueprint 의 wildcard 분기를 복사하면 net-zero 위반 — 도메인별 기존 legacy semantics 보존이 복제의 1순위 제약.

# Owner

backend

# Task Tags

- code
- security
- multi-tenant

---

# Dependency Markers

- **depends on**: ADR-MONO-019 ACCEPTED(MONO-153) + step 1(BE-322) + 파일럿 **FIN-BE-006**(#960 `df1efa5a`) + **ERP-BE-005**(#962 `b75fbed1`) + **SCM-BE-019**(#964 `0eab72c4`) — blueprint.
- **paired shared follow-up (미완)**: GAP auth-service `entitled_domains` claim populate. claim 부재 시 wms 는 legacy 만 → net-zero.
- **orthogonal to**: ADR-005/TASK-BE-317.
- **model**: 분석=Opus 4.8 / **구현 권장=Opus** (격리 게이트, 6 서비스 다중).

---

# Goal

finance/erp/scm blueprint 의 **entitlement-trust dual-accept** 를 wms 의 모든 tenant 격리 지점에 복제한다. wms 는 `tenant_id == wms` 를 **6 서비스 각 `TenantClaimValidator`**(decode-time `OAuth2TokenValidator<Jwt>`, defense-in-depth)에서 강제:

- **gateway-service** `security/TenantClaimValidator.java` (edge, reactive chain 등록 — 단 validator 자체는 sync `OAuth2TokenValidator<Jwt>`).
- **admin-service** `infra/security/TenantClaimValidator.java`.
- **inbound-service** `config/security/TenantClaimValidator.java`.
- **inventory-service** `config/security/TenantClaimValidator.java`.
- **master-service** `config/security/TenantClaimValidator.java`.
- **outbound-service** `config/security/TenantClaimValidator.java`.

각 지점을 dual-accept 로:

- (legacy) `tenant_id == wms` → 통과 (**무변경, wildcard 도입 금지** — 아래 net-zero 주의).
- (entitlement-trust) 서명 토큰 `entitled_domains` claim ∋ wms → 통과.
- 거부 = **legacy 불충족 AND entitlement 불충족** (fail-closed; blank/null → "tenant_id claim is required", 그 외 불일치 → "not allowed" 메시지 보존).

`entitled_domains` 는 RS256/JWKS 검증 토큰 claim → 위조 불가. GAP populate 전엔 claim 부재 → legacy 만 → **production net-zero**.

**서비스 간 모듈 공유 불가** → 각 서비스가 **로컬 `isEntitled` 헬퍼**(+ `CLAIM_ENTITLED_DOMAINS` 상수 + `safeStringList`)를 자체 보유. 모듈 경계를 넘는 공유 의존 추가 금지. wms 는 enforcer 필터가 없으므로(validator-only) 서비스당 1 지점.

## ⚠️ net-zero 주의 — wms 는 wildcard `*` 도입 금지

finance/erp/scm blueprint validator 는 legacy 에 `tenant_id ∈ {slug, "*"}`(SUPER_ADMIN platform-scope wildcard)를 포함하지만, **현재 wms 6 validator 는 strict equals(`expectedTenantId.equals(tenantId)`, wildcard 없음)**. net-zero 는 *기존 동작 byte-identical* 을 요구하므로 wms 의 legacy 분기는 **strict equals 그대로 보존**해야 한다 — `"*"` 수용을 추가하면 과거 거부되던 `*` 토큰이 통과되어 동작이 바뀐다(scope 위반). 따라서 wms dual-accept = **strict-equals legacy (무변경) ∪ entitlement OR-branch (신규)**. `WILDCARD_TENANT` 상수/분기를 복사하지 말 것.

# Scope

## In scope (wms — 6 서비스, validator-only)

1. 6 `TenantClaimValidator.java` 각각:
   - `CLAIM_ENTITLED_DOMAINS = "entitled_domains"` 상수 추가.
   - `public static boolean isEntitled(Jwt jwt, String domain)` + `private static List<String> safeStringList(Jwt)` (null/non-list/non-string element → empty list, fail-closed; `getClaimAsStringList` 의 non-string throw 회피용 방어 순회) — finance 머지본 byte-동형.
   - `validate()` 재작성: `legacyOk = tenantId != null && !tenantId.isBlank() && expectedTenantId.equals(tenantId)` (**strict, wildcard 없음**) → success; else `isEntitled(jwt, expectedTenantId)` → success; else 기존 failure 메시지(blank → "required", else "not allowed") 보존.
2. **테스트**: 6 `TenantClaimValidatorTest.java` 각각에 케이스 추가 — (a) `entitled_domains=[wms]` + `tenant_id=fan-platform` → success(403 아님); (b) `entitled_domains=[scm]` + `tenant_id=fan-platform` → mismatch error; (c) `entitled_domains` non-list/null/빈 → fail-closed(기존 거부 유지). 기존 단언(`tenant_id=wms`→pass, cross-tenant→error, missing/blank→error) **무변경**.
3. **architecture.md / overview.md § Multi-tenancy**: tenant 게이트를 문서화한 spec(master-service/admin-service `architecture.md`, gateway-service `overview.md` 확인됨 — Glob 으로 그 외 서비스 spec 의 동일 섹션 유무 확인) 에 dual-accept(legacy strict ∪ entitled_domains) 갱신. finance/erp/scm 의 문구 패턴 답습.

## Out of scope

- GAP `entitled_domains` populate (별 shared follow-up).
- 다른 도메인(gap/console-bff) 복제.
- **wildcard `*` 수용 추가** (위 주의 — net-zero 위반).
- legacy strict-equals 분기 제거 (step 4).
- step 2 / `tenant_domain_subscription` / admin catalog.
- notification-service (tenant_id row-isolation 참조는 있으나 `TenantClaimValidator` 게이트 **없음** — 무변경).

# Acceptance Criteria

- **AC-1**: 6 서비스(gateway/admin/inbound/inventory/master/outbound) `TenantClaimValidator` 모두 dual-accept (legacy strict `tenant_id == wms` ∪ 서명 `entitled_domains ∋ wms`; 거부 = 둘 다 불충족).
- **AC-2 (net-zero)**: claim 부재 시 기존 동작 byte-identical — 기존 단위/IT cross-tenant 단언(타테넌트 → 거부, 무토큰 → 401/거부) 무변경. **wildcard `*` 미도입**.
- **AC-3 (entitlement-trust)**: `entitled_domains=[wms]` + 타테넌트 tenant_id → 통과; `entitled_domains=[scm]`/부재 → 거부. 6 지점 일관.
- **AC-4 (claim 안전성)**: 비-list/null/빈/비-string element → fail-closed (NPE·blanket-trust 없음).
- **AC-5**: wms tenant-게이트 문서화 spec(architecture.md/overview.md) § Multi-tenancy dual-accept 갱신.
- **AC-6**: wms 6 서비스 컴파일 + 전 테스트 GREEN — **CI Linux wms Integration(Testcontainers)** 권위 게이트. 회귀 0.
- **AC-7 (scope-lock)**: 다른 도메인/console-bff/GAP populate/legacy 제거/step 2/wildcard 도입/notification 변경 0. diff = wms 6 validator + 그 test + 해당 spec 만.

# Related Specs

- `docs/adr/ADR-MONO-019-...md` § 2 D5 + § 3.3 step 3.
- `projects/finance-platform/tasks/done/TASK-FIN-BE-006-...md` + `projects/erp-platform/tasks/done/TASK-ERP-BE-005-...md` + `projects/scm-platform/tasks/done/TASK-SCM-BE-019-...md` (blueprint).
- `rules/traits/multi-tenant.md` M2/M3/M6.

# Related Contracts

- GAP 토큰 claim `entitled_domains` 수신측 신뢰(producer populate 는 GAP follow-up).

# Related Code

- 6 `TenantClaimValidator.java`: gateway `com.wms.gateway.security` / admin `com.wms.admin.infra.security` / inbound `com.wms.inbound.config.security` / inventory `com.wms.inventory.config.security` / master `com.wms.master.config.security` / outbound `com.wms.outbound.config.security`. 템플릿 = finance 머지본(`isEntitled` + dual-accept, **단 wildcard 분기 제외**).

# Edge Cases

- **서비스별 로컬 헬퍼**: 모듈 공유 불가 → 각 서비스 자체 `isEntitled`/상수. (wms 는 validator 내부에서 자기완결, enforcer 없음.)
- **gateway reactive**: validator 는 sync `OAuth2TokenValidator<Jwt>` — finance 와 동일 편집(reactive SecurityWebFilterChain 등록부 무변경).
- **wildcard 변이**: blueprint 의 `WILDCARD_TENANT`/`"*"` 분기를 복사하지 말 것 (wms net-zero).
- **claim 형 변이 / net-zero**: finance 와 동일 가드.
- **notification-service**: tenant_id row-isolation 만, 게이트 아님 — 무변경.

# Failure Scenarios

- wildcard 도입 → 과거 거부 `*` 토큰 통과 → net-zero 위반 → AC-2.
- blanket-trust → 격리 붕괴 → AC-4.
- 일부 validator 누락 → split → AC-3.
- 빅뱅 → wms 1 도메인으로 한정.

---

# Implementation Design Notes

- finance 머지본을 템플릿으로 6 validator 에 dual-accept 복제 — **wildcard 분기만 제외, legacy strict-equals 보존**. 각 validator 자체 `isEntitled`(모듈 경계).
- net-zero: legacy strict 무변경 + entitlement OR.
- CI Linux wms Integration 권위 게이트. 로컬은 6 서비스 각 compileJava+compileTestJava + 해당 validator unit test.
- 구현 = Opus.

---

# Notes

- ADR-MONO-019 § 3.3 step 3 복제 3/N (finance pilot → erp → scm → **wms**). 후속: gap+console-bff + GAP populate(shared). dependency-correct base = 본 머지 main.
