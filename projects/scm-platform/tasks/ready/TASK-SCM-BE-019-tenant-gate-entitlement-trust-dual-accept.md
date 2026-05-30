# Task ID

TASK-SCM-BE-019

# Title

ADR-MONO-019 § 3.3 step 3 복제 (scm) — scm-platform 의 tenant 격리 게이트(gateway + procurement + inventory-visibility, 4 enforcement 지점)를 `tenant_id == scm` 고정에서 **entitlement-trust dual-accept** 로 진화. finance/erp blueprint 의 scm 복제 (다중 서비스).

# Status

ready

# Owner

backend

# Task Tags

- code
- security
- multi-tenant

---

# Dependency Markers

- **depends on**: ADR-MONO-019 ACCEPTED(MONO-153) + step 1(BE-322) + 파일럿 **FIN-BE-006**(#960 `df1efa5a`) + erp 복제 **ERP-BE-005**(#962 `b75fbed1`) — blueprint.
- **paired shared follow-up (미완)**: GAP auth-service `entitled_domains` claim populate. claim 부재 시 scm 은 legacy 만 → net-zero.
- **orthogonal to**: ADR-005/TASK-BE-317.
- **model**: 분석=Opus 4.8 / **구현 권장=Opus** (격리 게이트, 다중 서비스).

---

# Goal

finance/erp blueprint 의 **entitlement-trust dual-accept** 를 scm 의 모든 tenant 격리 지점에 복제한다. scm 은 `tenant_id == scm` 을 **4 지점**에서 강제(defense-in-depth):
- **gateway-service** `TenantClaimValidator` (edge, OAuth2TokenValidator — finance/erp validator 와 byte-identical).
- **procurement-service** `TenantClaimValidator` (decode) + `TenantClaimEnforcer` (filter).
- **inventory-visibility-service** `TenantClaimEnforcer` (filter; 이 서비스엔 validator 없음).

각 지점을 dual-accept 로:
- (legacy) `tenant_id ∈ {scm, *}` → 통과 (무변경).
- (entitlement-trust) 서명 토큰 `entitled_domains` claim ∋ scm → 통과.
- 거부 = **legacy 불충족 AND entitlement 불충족** (fail-closed).

`entitled_domains` 는 RS256/JWKS 검증 토큰 claim → 위조 불가. GAP populate 전엔 claim 부재 → legacy 만 → **production net-zero**.

**서비스 간 모듈 공유 불가** → 각 서비스가 **로컬 `isEntitled` 헬퍼**(또는 상수)를 가짐(서비스 내 validator+enforcer 는 공유 — procurement). 모듈 경계를 넘는 공유 의존 추가 금지.

# Scope

## In scope (scm — 3 서비스)

1. **gateway-service** `security/TenantClaimValidator.java`: dual-accept (`CLAIM_ENTITLED_DOMAINS` 상수 + 로컬 `isEntitled` + validate() OR-분기). gateway 가 edge 라 entitled 타테넌트를 통과시켜야 내부 서비스 도달.
2. **procurement-service** `infrastructure/security/TenantClaimValidator.java` (dual-accept, 로컬 isEntitled) + `presentation/filter/TenantClaimEnforcer.java` (동일 dual-accept, validator 의 isEntitled 재사용 — 동일 모듈).
3. **inventory-visibility-service** `adapter/inbound/web/filter/TenantClaimEnforcer.java`: dual-accept (이 서비스엔 validator 없음 → 로컬 isEntitled 헬퍼). `TenantClaimExtractor` 는 **게이트 아님**(row-isolation 용 tenant_id 추출, 기본 "scm") → **무변경**.
4. **테스트**: 각 서비스의 기존 validator/enforcer 단위 + cross-tenant IT(`inventory-visibility` `CrossTenantIsolationIntegrationTest`, scm e2e `CrossTenantIsolationE2ETest` 는 가능 범위) 에 entitled(타테넌트+[scm])→통과 / non-entitled→403 케이스 추가. 기존 cross-tenant 단언 무변경(net-zero).
5. **architecture.md**: scm 의 § Multi-tenancy(해당 spec 위치 Glob 확인) dual-accept 갱신.

## Out of scope

- GAP `entitled_domains` populate (별 shared follow-up).
- 다른 도메인(wms/gap)+console-bff (별 복제).
- legacy `tenant_id == slug` 분기 제거 (step 4).
- step 2 / `tenant_domain_subscription` / admin catalog.
- `TenantClaimExtractor` 변경(게이트 아님).

# Acceptance Criteria

- **AC-1**: gateway `TenantClaimValidator` + procurement `TenantClaimValidator`/`TenantClaimEnforcer` + inventory-visibility `TenantClaimEnforcer` 4 지점 모두 dual-accept (legacy `tenant_id ∈ {scm,*}` ∪ 서명 `entitled_domains ∋ scm`; 거부 = 둘 다 불충족).
- **AC-2 (net-zero)**: claim 부재 시 기존 동작 byte-identical — 기존 cross-tenant 단언(타테넌트 → 403, 무토큰 → 401) 무변경.
- **AC-3 (entitlement-trust)**: 타테넌트 + `entitled_domains=[scm]` → 게이트 통과(403 아님); `entitled_domains=[wms]`/부재 → 403. 4 지점 일관.
- **AC-4 (claim 안전성)**: 비-list/null/빈/비-string → fail-closed.
- **AC-5**: scm architecture.md § Multi-tenancy dual-accept 갱신.
- **AC-6**: scm 3 서비스 컴파일 + 전 테스트 GREEN — **CI Linux scm Integration(Testcontainers)** 권위 게이트. M6 회귀 0.
- **AC-7 (scope-lock)**: 다른 도메인/console-bff/GAP populate/legacy 제거/step 2/`TenantClaimExtractor` 변경 0. diff = scm gateway+procurement+inventory-visibility 만.

# Related Specs

- `docs/adr/ADR-MONO-019-...md` § 2 D5 + § 3.3 step 3.
- `projects/finance-platform/tasks/done/TASK-FIN-BE-006-...md` + `projects/erp-platform/tasks/done/TASK-ERP-BE-005-...md` (blueprint).
- `rules/traits/multi-tenant.md` M2/M3/M6.

# Related Contracts

- GAP 토큰 claim `entitled_domains` 수신측 신뢰(producer populate 는 GAP follow-up).

# Related Code

- gateway `security/TenantClaimValidator.java`; procurement `infrastructure/security/TenantClaimValidator.java` + `presentation/filter/TenantClaimEnforcer.java`; inventory-visibility `adapter/inbound/web/filter/TenantClaimEnforcer.java`(+ `TenantClaimExtractor.java` 무변경). 템플릿 = finance/erp 머지본(`isEntitled` + dual-accept).

# Edge Cases

- **서비스별 로컬 헬퍼**: 모듈 공유 불가 → 각 서비스 자체 `isEntitled`(상수 `entitled_domains`). procurement 내부는 validator↔enforcer 공유.
- **gateway reactive 컨텍스트**: `OAuth2TokenValidator<Jwt>` 는 sync — finance/erp 와 동일 편집(gateway 의 reactive chain 등록부 무변경).
- **이중/삼중 enforcement 일관**: gateway+decode+filter 모두 동일 dual-accept(한 지점만 고치면 split).
- **claim 형 변이 / wildcard / net-zero**: finance 와 동일 가드. `TenantClaimExtractor` 의 기본 "scm" 은 게이트 아님 — 무변경.

# Failure Scenarios

- blanket-trust → 격리 붕괴 → AC-4.
- 일부 지점 누락 → split → AC-3.
- `TenantClaimExtractor` 오변경 → row-isolation 회귀(게이트 아님) → 건드리지 말 것.
- 빅뱅 → scm 1 도메인으로 한정.

---

# Implementation Design Notes

- finance/erp 머지본을 템플릿으로, scm 4 지점에 dual-accept 복제. 각 서비스 로컬 `isEntitled`(모듈 경계).
- net-zero: legacy 무변경 + entitlement OR.
- CI Linux scm Integration 권위 게이트. 컴파일만 로컬(gateway/procurement/inventory-visibility 각 compileJava+compileTestJava).
- 구현 = Opus.

---

# Notes

- ADR-MONO-019 § 3.3 step 3 복제 2/N (finance pilot → erp → **scm**). 후속: wms(6-svc)/gap+console-bff + GAP populate. dependency-correct base = 본 머지 main.
