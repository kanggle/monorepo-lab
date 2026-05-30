# Task ID

TASK-FIN-BE-006

# Title

ADR-MONO-019 § 3.3 step 3 **파일럿 (1 도메인 = finance)** — finance account-service 의 tenant 격리 게이트를 `tenant_id == slug` 고정에서 **entitlement-trust dual-accept** 로 진화. GAP-서명 토큰의 `entitled_domains` claim 에 `finance` 가 포함되면(또는 legacy `tenant_id ∈ {finance,*}`) 수용 — net-additive, GAP claim 미발급 시 net-zero. 5-도메인+console-bff 코호트의 **blueprint**.

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

- **depends on**: ADR-MONO-019 ACCEPTED (TASK-MONO-153 #955) + step 1 (TASK-BE-322 #958, `tenant_domain_subscription` 모델 + 구독 조회 표면). 본 task = § 3.3 **step 3** 의 1-도메인 파일럿(사용자 선택 "step 3 파일럿 1도메인", 2026-05-31).
- **blueprint for**: 나머지 4 도메인(wms/scm/erp/gap) 의 동일 dual-accept 게이트 + console-bff `tenant_id` pass-through IT — 본 파일럿 패턴 CI-GREEN 검증 후 복제.
- **paired shared follow-up (별 task, 미완)**: GAP auth-service 가 토큰에 `entitled_domains` claim 을 **populate** (authorization_code/refresh 발급 시 고객 테넌트의 `tenant_domain_subscription` 조회 → entitled 도메인 목록). auth↔account 는 이미 login 시 `/internal/accounts/tenant-info` 호출(BE-318c) → 같은 경로로 BE-322 의 `/internal/tenant-domain-subscriptions` 조회 가능. **본 파일럿은 수신측(finance) 게이트의 claim-trust 만 — claim populate 는 step 2(실 고객) 와 함께.** claim 부재 시 finance 는 legacy 경로만(net-zero).
- **orthogonal to**: ADR-005/TASK-BE-317.
- **model**: 분석=Opus 4.8 / **구현 권장=Opus** (격리 게이트 — 버그 시 cross-tenant 누출, ADR-MONO-013 § D6 "isolation → Opus" + ADR-018 D5 선례).

---

# Goal

ADR-MONO-019 **D5** (도메인 게이트 `tenant_id == slug` → entitlement-trust, dual-accept window) 를 **finance 도메인 1개에 파일럿**으로 구현해, 나머지 4 도메인 + console-bff 가 복제할 **blueprint** 를 확정한다.

finance account-service 는 **defense-in-depth 이중 enforcement** 로 tenant 를 검증한다: ① `TenantClaimValidator`(decode-time `OAuth2TokenValidator`, `ServiceLevelOAuth2Config` 등록), ② `TenantClaimEnforcer`(presentation `OncePerRequestFilter`). 둘 다 현재 `tenant_id ∈ {finance, *}` 만 통과시킨다(단일 고정 slug = 사실상 single-tenant).

**dual-accept 규칙** (양 enforcement 지점 동일 적용):
- (legacy) `tenant_id ∈ {finance, *}` → 수용 (변경 없음, dual-accept window 동안 유지).
- (entitlement-trust, 신규) 토큰의 `entitled_domains` claim(list) 에 `finance` 포함 → 수용. 이후 row-level isolation 은 `tenant_id` 로(기존 ActorContext 경로 그대로).
- 둘 다 불충족 → 403 `TENANT_FORBIDDEN` (fail-closed, 기존 동일).

**안전성**: `entitled_domains` 는 GAP JWKS(RS256) 로 **서명된** 토큰의 claim 이라 위조 불가 — GAP 가 넣은 경우에만 존재. 따라서 서명-검증된 `entitled_domains` 를 신뢰하는 것은 안전하며, GAP 가 아직 populate 안 하면 claim 부재 → legacy 경로만 → **production net-zero**.

# Scope

## In scope (finance account-service)

1. **`infrastructure/security/TenantClaimValidator.java`**: dual-accept — 기존 `expectedTenantId`/`*` 검사에 더해, 실패 시 `entitled_domains` claim(list of string) 에 `expectedTenantId`(finance) 포함 여부 확인 후 성공 처리. 신규 상수 `CLAIM_ENTITLED_DOMAINS = "entitled_domains"`.
2. **`presentation/filter/TenantClaimEnforcer.java`**: 동일 dual-accept 로직(defense-in-depth 일관 — 한쪽만 고치면 다른 쪽이 막아 불일치). `entitled_domains` 추출 헬퍼 공유(TenantClaimValidator 의 상수/정적 헬퍼 재사용).
3. **테스트**:
   - `TenantClaimValidatorTest`: 신규 케이스 — entitled 타테넌트(tenant_id=acme + entitled_domains=[finance]) 성공 / non-entitled(tenant_id=acme + entitled_domains=[wms] 또는 부재) 실패 / legacy(finance, *) 성공 유지 / claim 형 안전성(비-list, null 원소).
   - `CrossTenantHttpIntegrationTest`: `token()` 에 `entitled_domains` 변형 추가 — entitled 타테넌트 → **403 아님**(게이트 통과; 404/200) / non-entitled wms(기존) → 403 유지 / claim 부재 legacy → 기존 단언 유지.
4. **`specs/services/account-service/architecture.md` § Multi-tenancy**: 게이트를 entitlement-trust dual-accept 로 문서화(legacy slug ∪ 서명된 `entitled_domains`; row isolation 보존; GAP 가 entitlement authority; dual-accept window → step 4 에서 legacy 분기 제거). Failure #3 (cross-tenant 403) 서술 갱신.

## Out of scope

- **GAP auth-service `entitled_domains` claim populate** — 별 shared follow-up(step 2 와 함께). 본 파일럿은 수신측 claim-trust 만; claim 부재 시 net-zero.
- 나머지 4 도메인(wms/scm/erp/gap) + console-bff 게이트 — 본 blueprint 복제(별 task 코호트).
- `tenant_id == slug` legacy 분기 **제거** — step 4 cleanup (dual-accept window 동안 유지).
- step 2(실 고객 시드) — gated, 별 task.
- `tenant_domain_subscription` 스키마/admin catalog — BE-322 done.

# Acceptance Criteria

- **AC-1**: `TenantClaimValidator` + `TenantClaimEnforcer` 양쪽이 dual-accept — legacy(`tenant_id ∈ {finance,*}`) **또는** 서명 토큰 `entitled_domains ∋ finance` 시 통과; 둘 다 불충족 → 403 `TENANT_FORBIDDEN`.
- **AC-2 (net-zero)**: `entitled_domains` claim 부재 시 기존 동작 byte-identical — `CrossTenantHttpIntegrationTest` 기존 단언(crossTenant wms → 403, missingToken → 401) 무변경 통과.
- **AC-3 (entitlement-trust)**: tenant_id=acme + signed `entitled_domains=[finance]` → 게이트 통과(403 아님). tenant_id=acme + `entitled_domains=[wms]`(또는 부재) → 403. (양 enforcement 지점에서 일관.)
- **AC-4 (claim 안전성)**: `entitled_domains` 가 비-list / null / 빈 list / 비-string 원소여도 NPE/통과 없이 fail-closed(legacy 경로만).
- **AC-5**: architecture.md § Multi-tenancy 가 dual-accept entitlement-trust 로 갱신(GAP authority, row isolation 보존, dual-accept window).
- **AC-6**: finance account-service 컴파일 + 전 테스트 GREEN — **CI Linux finance Integration(Testcontainers)** 권위 게이트. M6 cross-tenant-leak 회귀 0.
- **AC-7 (scope-lock)**: 다른 도메인/ console-bff/ GAP claim populate/ legacy 제거/ step 2 artifact 0. diff = finance account-service 만.

# Related Specs

- `docs/adr/ADR-MONO-019-platform-console-customer-tenant-model.md` § 2 D5 + § 3.3 step 3 (authoritative).
- `projects/finance-platform/apps/account-service/.../specs/services/account-service/architecture.md` § Multi-tenancy + Failure #3.
- `rules/traits/multi-tenant.md` M2/M3/M6 (isolation invariants — allowed-set 을 GAP entitlement authority 하에 widen, isolation layer 미제거).

# Related Contracts

- GAP 토큰 claim `entitled_domains`(list of domain keys) — 본 task 가 **수신측 신뢰 계약**을 확정(producer populate 는 GAP follow-up). `tenant_id`/`tenant_type` claim 기존 그대로.

# Related Code

- `infrastructure/security/TenantClaimValidator.java` (decode-time gate) + `ServiceLevelOAuth2Config.java`(등록) + `presentation/filter/TenantClaimEnforcer.java`(filter gate) + `infrastructure/security/SecurityErrorHandler.java`(`TENANT_FORBIDDEN` 매핑) + `ActorContextJwtAuthenticationConverter.java`(row isolation 용 tenant_id 추출 — 변경 없음). `required-tenant-id` = `${financeplatform.oauth2.required-tenant-id:finance}` (application.yml/-test.yml).
- GAP `auth-service/.../oauth2/TenantClaimTokenCustomizer.java` — entitled_domains populate 대상(본 task out of scope, follow-up reference).

# Edge Cases

- **이중 enforcement 불일치**: validator 만 고치고 enforcer 미수정 시, decode 는 통과하나 filter 가 403 → entitled 타테넌트가 막힘. **양쪽 동일 로직 필수**.
- **서명 신뢰 경계**: `entitled_domains` 는 RS256-검증된 토큰에서만 읽음(resource-server decode 후) — validator 는 decode 체인 내부라 이미 서명검증 후. enforcer 는 `jwtAuth.getToken()` 의 검증된 claim 사용. 미검증 입력 없음.
- **claim 형 변이**: `getClaimAsStringList`/instanceof 가드로 비-list/null 안전 처리.
- **wildcard 보존**: `*`(SUPER_ADMIN platform-scope) 는 legacy 경로로 계속 통과.
- **net-zero 회귀**: 기존 finance IT(같은 SecurityConfig 체인) 가 claim 없는 토큰으로 기존 단언 유지해야 — dual-accept 추가가 legacy 거부를 약화하면 안 됨(거부는 "legacy 불충족 AND entitlement 불충족" 시에만).

# Failure Scenarios

- **blanket-trust 버그**: entitled_domains 부재를 "통과"로 잘못 처리 → 모든 tenant 통과(격리 붕괴) → AC-4 + crossTenant IT 가 게이트.
- **enforcer 누락**: 한 enforcement 지점만 수정 → 불일치(통과/거부 split) → AC-3 양지점 IT.
- **GAP claim 위조 우려**: 불가 — entitled_domains 는 서명 토큰 claim, JWKS 검증 후에만 신뢰. 비서명/비신뢰 발급자는 AllowedIssuersValidator 가 선차단.
- **빅뱅**: 5 도메인+console-bff 동시 변경 → transient-broken main(ADR § 4) → 파일럿 1 도메인으로 한정(본 task).

---

# Implementation Design Notes

- 양 enforcement 지점(`TenantClaimValidator` decode + `TenantClaimEnforcer` filter)에 동일 dual-accept. `entitled_domains` 추출 + `contains(expectedTenantId)` 헬퍼를 `TenantClaimValidator` 정적 메서드로 두고 enforcer 가 재사용(단일 진실).
- net-zero: legacy 경로 무변경 + entitlement 경로는 OR 추가(거부 조건 = legacy 불충족 **AND** entitlement 불충족).
- 로컬 Testcontainers 는 Rancher npipe 회귀로 스킵 → **CI Linux finance Integration 권위 게이트**. 컴파일만 로컬.
- 구현 = **Opus**.

---

# Notes

- ADR-MONO-019 § 3.3 step 3 의 1-도메인 파일럿. CI-GREEN 검증 후 wms/scm/erp/gap + console-bff 복제(별 코호트) + GAP `entitled_domains` populate(별 shared follow-up, step 2 와 함께). dependency-correct base = 본 머지 main.
- finance 가 파일럿 도메인인 이유: 이미 `CrossTenantHttpIntegrationTest`(ADR-018 D5) 보유 → 격리 IT 확장 최소 마찰.
