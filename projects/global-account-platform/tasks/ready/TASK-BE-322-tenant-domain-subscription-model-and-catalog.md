# Task ID

TASK-BE-322

# Title

ADR-MONO-019 § 3.3 step 1 — GAP backward-compatible customer-tenant model + subscription-driven console catalog: `tenant_domain_subscription` N:M 테이블 + Flyway migration (account-service) + 하위호환 시드 + admin-service `ConsoleRegistryUseCase` 구독-기반 `selectableTenants` 재작성, **카탈로그 동작 byte-identical**(net-zero) 보장.

# Status

ready

# Owner

backend

# Task Tags

- code
- migration
- multi-tenant

---

# Dependency Markers

- **depends on**: ADR-MONO-019 **ACCEPTED** (TASK-MONO-153 #955 squash `d15d20a6`, main 2026-05-31). 본 task = ADR-019 § 3.3 **step 1** (execution roadmap 의 첫 단계; ACCEPTED 로 UNPAUSED).
- **prerequisite for**: step 2 (real customer tenants + N:M subscriptions seed) → step 3 (per-domain `TenantClaimValidator` dual-accept + isolation IT, 5 domains + console-bff, **Opus**) → step 4 (cleanup). **step 1 은 net-zero — step 2 가 본 모델 위에 실 고객을 얹기 전까지 카탈로그 동작 변화 0.**
- **orthogonal to**: ADR-005 (GAP) / TASK-BE-317 (service-to-service workload identity). 공유 파일 없음.
- **model**: 분석=Opus 4.8 / **구현 권장=Opus** (ADR-019 § 3.3 step 1 명시 — registry + catalog resolution + isolation-adjacent; cross-service account↔admin 경로).

---

# Goal

ADR-MONO-019 가 결정한 production customer-tenant 모델의 **첫 실행 단계**: customer/tenant ↔ product/domain 관계를 GAP account-service 의 **`tenant_domain_subscription` N:M 테이블**(entitlement authority, ADR-019 **D2**)로 표현하고, admin-service `ConsoleRegistryUseCase.selectableTenants()` 를 그 구독을 기반으로 한 derivation 으로 **재작성**(ADR-019 **D4**)한다.

**핵심 불변(net-zero)**: 기존 도메인-슬러그 테넌트(`wms`/`scm`/`erp`/`finance`) 각각이 **자기 도메인에 구독**하는 **하위호환 시드**를 함께 넣어, 이 step 1 머지 후에도 console registry 응답이 **현재와 byte-identical** 이어야 한다(switcher 는 여전히 도메인-슬러그를 보여줌 — 실 고객 이름은 step 2 가 도입). 즉 본 단계는 **모델 도입 + 카탈로그 derivation 전환**이며 **동작 변화 0**.

현재 결합(ADR-019 § 1.1): `ProductCatalog` 가 도메인 product 를 `tenantSlug == 도메인명` 으로 바인딩하고, `ConsoleRegistryUseCase.selectableTenants()` 가 `activeTenants.contains(entry.tenantSlug()) ? [tenantSlug] : []` 로 해소 → 데모 DB 가 도메인-이름 테넌트를 시드하므로 switcher 가 도메인 이름을 노출. 본 task 는 그 derivation 을 **구독 조회**로 바꾸되 시드로 결과를 동일하게 유지한다.

# Scope

## In scope

**account-service** (entitlement authority — ADR-019 D2):
1. **`tenant_domain_subscription` N:M 테이블** + Flyway migration (**다음 free 버전 = `V0019`**; 현재 최고 `V0018__seed_erp_tenant.sql`). 최소 컬럼: `tenant_id`(FK→`tenants.tenant_id`), `domain_key`(`gap`/`wms`/`scm`/`erp`/`finance` — product catalog key), `status`(ACTIVE 등), `created_at`/`updated_at`; UNIQUE(`tenant_id`,`domain_key`). engine/charset 는 기존 `tenants`(V0009) 패턴 답습.
2. **하위호환 시드**: 도메인-슬러그 테넌트 각각이 자기 도메인에 ACTIVE 구독 — `(wms,wms)`,`(scm,scm)`,`(erp,erp)`,`(finance,finance)`. (`gap` 은 `ProductCatalog.bindsAllTenants=true` 로 admin-side 에서 전체 테넌트 federate → 구독행 불요; net-zero 유지를 위해 `gap` 구독은 시드하지 않음.) `INSERT IGNORE`/idempotent (기존 seed 패턴).
3. **내부 읽기 표면**: admin-service 가 구독을 조회할 수 있는 `/internal/**` 엔드포인트(또는 기존 tenant 조회 확장) — 도메인별 ACTIVE 구독 테넌트 목록 제공. 인증=GAP client_credentials Bearer JWT(BE-319b 수신측 정합; 신규 `/internal` 경로면 동일 게이트).

**admin-service** (catalog projection — ADR-019 D4):
4. `ConsoleRegistryUseCase.selectableTenants()` **재작성**: 도메인 product 의 `tenants[]` = "그 `domain_key` 에 ACTIVE 구독한 테넌트" ∩ 운영자 scope ∩ ACTIVE-registered — 기존 `activeTenants.contains(entry.tenantSlug())` 바인딩을 구독-조회로 교체. `gap`(`bindsAllTenants`) 분기 **불변**. 새 account 내부 조회용 port/client 추가(`ListTenantsUseCase` 동형 패턴).
5. `ProductCatalog` 의 `tenantSlug` 필드 운명: step 1 에서는 **유지 가능**(derivation 이 더 이상 그것에 의존하지 않더라도 시드가 `domain_key==slug` 라 동일); 완전 제거는 step 4 cleanup. step 1 은 derivation 전환에 집중.

**specs/contracts**:
6. `specs/contracts/http/console-registry-api.md` + `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.2: registry **envelope shape 불변**(ADR-019 D4 — zero console-web change). 구독-기반 derivation 을 문서화(값은 step 1 에서 여전히 도메인-슬러그 — 실 고객명은 step 2). 신규 account 내부 구독 엔드포인트는 `specs/contracts/http/internal/` 에 계약 추가.

**테스트** (ADR-019 § 3.3 step 1 명시 "unit/IT proving net-zero catalog change"):
7. **net-zero IT**: 하위호환 시드 상태에서 console registry 응답이 **현재와 byte-identical**(product 5종 + 각 `tenants[]` 값 동일; platform-scope + single-tenant operator 양 케이스). 구독 테이블/엔드포인트 unit + admin `ConsoleRegistryUseCase` 재작성 unit(구독 mock).

## Out of scope (후속 step)

- **step 2**: 실 customer 테넌트 + N:M 구독 + operator 할당 시드, switcher 가 고객명 노출, 구독 관리 admin 표면.
- **step 3**: 도메인 `TenantClaimValidator` 의 `tenant_id == slug` → entitlement-trust dual-accept (5 domains + console-bff) + cross-tenant-leak regression IT (**Opus, 최고위험, dual-accept window**).
- **step 4**: legacy slug-tenant + legacy fixed-slug validator 분기 제거, `ProductCatalog.tenantSlug` 완전 제거, contract § 2.4.x cleanup, `operator_tenant_assignment` N:M(D3-B).
- ADR-019 D3(operator↔tenant) 변경 — 본 step 은 tenant↔domain(D2/D4)만.

# Acceptance Criteria

- **AC-1**: account-service `V0019__create_tenant_domain_subscription.sql` — N:M 테이블(UNIQUE(tenant_id,domain_key), FK→tenants) + 하위호환 시드(`wms`/`scm`/`erp`/`finance` self-domain ACTIVE). Flyway clean migrate GREEN.
- **AC-2**: account-service 내부 구독 조회 표면(도메인별 ACTIVE 구독 테넌트), GAP client_credentials Bearer JWT 인증(BE-319b 정합), 계약 spec 추가.
- **AC-3**: admin-service `ConsoleRegistryUseCase.selectableTenants()` 가 구독-기반으로 재작성됨(도메인 product binding = 구독 조회); `gap` bindsAllTenants 분기 + 운영자 scope 교집합 + ACTIVE-registered 교집합 **불변**.
- **AC-4 (net-zero, 핵심)**: 하위호환 시드 상태에서 console registry 응답이 **현재 main 과 byte-identical**(IT 로 pre/post 동치 단언; platform-scope + single-tenant operator). multi-tenant M3/M6(타 테넌트 슬러그 비노출) 보존.
- **AC-5**: `console-registry-api.md` + `console-integration-contract.md` § 2.2 envelope shape 불변 + 구독-derivation 문서화; 신규 internal 구독 계약 추가.
- **AC-6**: 전 서비스 IT + gap e2e smoke GREEN(CI Linux 권위 게이트). 회귀 0.
- **AC-7 (scope-lock)**: step 2/3/4 artifact(실 고객 시드 / `TenantClaimValidator` 변경 / cleanup) 0 — diff 는 account 테이블+migration+내부표면 + admin catalog derivation + 계약/테스트만.

# Related Specs

- `docs/adr/ADR-MONO-019-platform-console-customer-tenant-model.md` § 2 D2/D4 + § 3.3 step 1 (authoritative).
- `projects/global-account-platform/specs/features/multi-tenancy.md` "Platform Console".
- `rules/traits/multi-tenant.md` M1-M7 (gate 가 allowed-set 을 GAP entitlement authority 하에 widen — isolation layer 미제거).

# Related Contracts

- `specs/contracts/http/console-registry-api.md` (registry producer — envelope shape 불변, derivation 문서화).
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.2 (registry envelope — shape 보존; 값은 step 1 에서 여전히 도메인-슬러그).
- `specs/contracts/http/internal/*.md` (신규 account 내부 구독 조회 계약 — Bearer JWT).

# Related Code

- `admin-service/.../application/console/ProductCatalog.java` (Entry: `gap` bindsAllTenants / `wms`·`scm`·`erp`·`finance` tenantSlug==domain) + `ConsoleRegistryUseCase.java` (`selectableTenants()` 재작성 대상; `activeRegisteredTenantSlugs()` via `ListTenantsUseCase`).
- `account-service/.../db/migration/V0009__create_tenants.sql`(tenants 스키마 패턴) + `V0016__seed_wms_tenant.sql`(seed 패턴) — 신규 V0019 답습 reference.

# Edge Cases

- **net-zero 검증**: 하위호환 시드가 `domain_key==slug` 라야 derivation 결과가 기존과 동일 — 시드 1행이라도 누락/오타면 그 도메인 product `tenants[]` 가 빈 배열로 회귀(switcher 에서 사라짐). net-zero IT 가 5 product 전부 단언.
- **`gap` bindsAllTenants**: `gap` 은 구독행 없이 admin-side 에서 전체 federate — 구독 테이블에 `gap` 행을 넣으면 이중계산/의미혼동. `gap` 분기는 구독 조회를 타지 않음(불변).
- **single-tenant operator**: 구독-기반 binding 후에도 운영자 scope 교집합(M3/M6)이 먼저/함께 적용돼 타 테넌트 슬러그 비노출 — 재작성이 이 순서를 깨면 isolation 회귀.
- **account 내부 엔드포인트 인증**: 신규 `/internal` 경로는 account `InternalApiFilter`(BE-319b, JWT 전용, e2e 정합 BE-321) 게이트 하 — admin→account 호출은 이미 Bearer(BE-318b). 신규 경로도 동일 게이트 자동 적용 확인.
- **Flyway 버전 충돌**: account-service 다음 free = V0019(auth-service 의 V0019 와 무관 — 서비스별 독립 시퀀스).

# Failure Scenarios

- **빅뱅 유혹**: 본 step 에서 도메인 게이트(step 3)나 실 고객(step 2)을 함께 건드리면 5 도메인 전반 transient-broken main(ADR-019 § 4 + BE-303 CI-RED-at-merge 교훈). step 1 은 net-zero 로만 한정.
- **net-zero 깨짐**: 시드 누락/derivation 버그로 registry 응답 변화 → AC-4 IT RED. byte-identical 단언이 게이트.
- **isolation 회귀**: selectableTenants 재작성이 operator-scope 교집합을 누락 → cross-tenant 슬러그 노출(M6 위반) → STOP.
- **envelope shape 변경**: registry 응답 구조(키/중첩)를 바꾸면 console-web zero-change 불변(D4) 위반 → reject(값만 derivation 경유, shape 동일).

---

# Implementation Design Notes

- 순서: account 테이블+migration+시드 → account 내부 구독 조회 표면+계약 → admin port/client → `ConsoleRegistryUseCase.selectableTenants()` 재작성 → net-zero IT(byte-identical) → 전 서비스 IT + e2e.
- **net-zero 가 본 단계의 전부** — 모델/derivation 을 도입하되 동작은 그대로. switcher 가 고객명을 보이는 건 step 2.
- 로컬 Testcontainers 는 Rancher npipe 회귀로 간헐 스킵 → **CI Linux 가 IT/e2e 권위 게이트**([[project_testcontainers_docker_desktop_blocker]]).
- 구현은 **Opus** 권장(ADR-019 § 3.3 step 1).

---

# Notes

- ADR-MONO-019 § 3.3 4-step execution roadmap 의 **step 1**(ACCEPTED 로 UNPAUSED). step 2/3/4 는 본 task 머지된 main 을 dependency-correct base 로 하는 별 future tasks.
- 본 task 는 **작성만**(authoring) — 구현은 별도 Opus 세션. ready/ 에서 대기.
