# Task ID

TASK-BE-325

# Title

ADR-MONO-019 § 3.3 step 2 — 실 고객 테넌트(`acme-corp`) + N:M 도메인 구독(`acme-corp`→[finance, wms]) 시드. platform-console tenant 스위처가 도메인 슬러그가 아닌 **고객명**을 노출. keystone(BE-324) 의 producer 조회가 실 고객 구독으로 동작함을 실 DB 역조회로 증명.

# Status

done

> **완료 (2026-05-31)**: impl PR #970 (squash `b6f284f7`). ADR-MONO-019 § 3.3 **step 2** — 첫 실 고객 테넌트. account-service Flyway `V0020` 시드: `acme-corp`(ACTIVE, B2B_ENTERPRISE) + 구독 `acme-corp`→finance, `acme-corp`→wms(**gap 없음**[bindsAllTenants], **scm/erp 없음**[의도적 미-entitlement, 부정 케이스]; tenant insert→subscription FK 순서, INSERT IGNORE). 구독-기반 카탈로그(BE-322)가 스위처에 **고객명** 노출, keystone(BE-324) 역조회가 `acme-corp` 토큰에 `entitled_domains=[finance,wms]` 주입. **증명**: account `@DataJpaTest`(real DB, TenantJpaRepositoryTest 하니스 확장) `findByStatusAndTenantId("acme-corp")`={finance,wms} + `"wms"`={wms}(V0019 self-sub 무회귀) + 미존재=빈 + acme-corp 는 gap/scm/erp 제외. admin `ConsoleRegistryIntegrationTest`(WireMock IT) + `ConsoleRegistryUseCaseTest`(unit `@Nested RealCustomerTenantAcmeCorp` 3 case): acme-corp 가 finance/wms 노출, scm/erp 불포함, gap binds-all 포함, M6 isolation 보존. **net-positive(net-zero 아님)**: step 2 가 카탈로그를 의도적으로 확장(고객 등장), 기존 slug-tenant 단언 무변경(별 stub). **production code 0** — BE-322 derivation + BE-324 repository 가 이미 임의 고객 처리. **runtime 무변경**(acme-corp 토큰 발급 주체 미존재 → catalog-visible-but-runtime-inert; console-bff credential path 별건). **3차원**(MERGED `b6f284f7` / tip 일치 / pre-merge 0). **BE-299 re-stage** ✓. **CI 1-pass**: GAP Integration GREEN 2m21s(V0020 clean-migrate + acme-corp 카탈로그 IT + 역조회 @DataJpaTest) + Build GREEN. **scope-lock**: V0020 + account 역조회 test + admin 카탈로그 2 test + spec 노트 만. **후속**: 런타임 cross-domain E2E(console-bff per-domain credential 의 tenant-scoped 진화에 의존, step 3 잔여 console-bff 항목).

# Owner

backend

# Task Tags

- code
- multi-tenant

---

# Dependency Markers

- **depends on**: ADR-MONO-019 ACCEPTED(MONO-153) § 3.3 step 2 + step 1(BE-322 — `tenant_domain_subscription` 테이블 + 구독-기반 `ConsoleRegistryUseCase.selectableTenants`) + step 3 게이트 4/4(FIN/ERP/SCM-BE / BE-323) + keystone(BE-324 — 발급 시점 `entitled_domains` populate). step 1~3 + keystone 머지 후라 step 2 고객은 catalog-visible + (GAP↔도메인 토큰 경로) callable.
- **enables (후속)**: 런타임 cross-domain 활성화 E2E(고객 토큰이 구독 도메인 통과/미구독 403) — **console-bff per-domain credential 을 tenant-scoped 로 진화**(step 3 잔여 console-bff 항목)에 일부 의존. 본 task 는 데이터+카탈로그+역조회 증명까지.
- **orthogonal to**: ADR-005/TASK-BE-317.
- **model**: 분석=Opus 4.8 / **구현 권장=Sonnet** (시드 + 카탈로그 테스트 갱신 + 역조회 IT; ADR § 3.3 step 2 = Sonnet).

---

# Goal

ADR-019 § 3.3 step 2: BE-322 가 구독-기반으로 만든 카탈로그에 **실 고객 테넌트**를 시드해 platform-console tenant 스위처가 **고객명**(`acme-corp`)을 노출하게 한다. 지금까지 카탈로그/게이트가 slug-tenant(`wms`/`scm`/`erp`/`finance` self-subscription)만 보유 → 스위처가 도메인명만 보임(ADR § 6 driver 의 근본 증상). 본 task 는 실 고객 1개 + 그 고객의 다중-도메인 구독을 시드:

- 고객 테넌트 `acme-corp`(ACTIVE, B2B_ENTERPRISE) — account-service `tenants`(D1: 고객-테넌트 entity).
- 구독 `acme-corp`→`finance`, `acme-corp`→`wms`(D2 N:M, ACTIVE).

효과:
- **카탈로그**: finance product `tenants[]` = [finance, acme-corp], wms product = [wms, acme-corp](platform-scope operator). 스위처가 고객명 노출(ADR § 3.3 step 2 deliverable, "switcher now shows customer names").
- **keystone 연결**: keystone(BE-324) 이 `acme-corp` 토큰 발급 시 account 역조회 → `entitled_domains=[finance, wms]` 주입. 본 task 의 실 DB 역조회 IT 가 그 데이터 의존성을 검증(real seed → `findActiveByTenantId("acme-corp")` = [finance, wms]).
- **net-positive (net-zero 아님)**: step 2 는 의도적으로 카탈로그를 바꾼다(고객 등장). 기존 slug-tenant 동작/단언은 보존(additive). 런타임 도메인 트래픽은 acme-corp 토큰을 발급/사용하는 주체가 아직 없어 무변경.

# Scope

## In scope

1. **account-service Flyway `V0020__seed_acme_corp_customer_tenant.sql`**:
   - `INSERT IGNORE INTO tenants (...) VALUES ('acme-corp', 'Acme Corporation', 'B2B_ENTERPRISE', 'ACTIVE', NOW(6), NOW(6));`(V0014~V0018 시드 패턴 답습; tenant_id regex `^[a-z][a-z0-9-]{1,31}$` 충족).
   - `INSERT IGNORE INTO tenant_domain_subscription (tenant_id, domain_key, status, created_at, updated_at)` 2 행: `('acme-corp','finance','ACTIVE',...)`, `('acme-corp','wms','ACTIVE',...)`. FK(tenants) 충족 — 같은 migration 의 tenant insert 가 선행하도록 동일 파일 내 순서. `gap` 행 없음(bindsAllTenants federate; 단 gap 은 acme-corp 를 자동 federate 하므로 별 시드 불요). `scm`/`erp` 구독 없음(고객이 미구독 → 그 게이트에서 거부될 대상).
   - 헤더 주석: ADR-019 step 2, 실 고객, V0019 backward-compat self-subscription 과 구분.
2. **account-service 역조회 검증 IT/test**(real DB, Testcontainers): `findActiveByTenantId("acme-corp")` = {finance, wms}(domainKey 집합), `findActiveByTenantId("wms")` = {wms}(기존 self-sub 무회귀), 미존재 테넌트 = 빈. 기존 `TenantDomainSubscription` repository IT 가 있으면 케이스 추가, 없으면 Flyway-seed 검증 테스트(BE-322 가 V0019 검증 테스트를 두었으면 그 패턴 답습). real seed 가 keystone 의 역조회 producer 를 실제로 먹임을 증명.
3. **admin-service `ConsoleRegistryIntegrationTest`**: 새 테스트 케이스 1개 — `acme-corp` 가 ACTIVE-registered(`/internal/tenants` stub 에 추가) + finance/wms 구독(`/internal/tenant-domain-subscriptions` stub 에 `{"tenantId":"acme-corp","domainKey":"finance"}`,`{"tenantId":"acme-corp","domainKey":"wms"}` 추가) 인 상황에서 platform-scope(SUPER_ADMIN) operator 가 GET registry → finance product `tenants` 에 `acme-corp` 포함 + wms product `tenants` 에 `acme-corp` 포함 + scm/erp product `tenants` 에 `acme-corp` **불포함**(미구독). **기존 net-zero 케이스(slug-only stub)들은 무변경**(별 stub 상수). isolation 단언(M6) 보존.
4. **admin-service `ConsoleRegistryUseCaseTest`**(단위): 동형 케이스 — 구독 맵에 acme-corp→{finance,wms} 주입 시 finance/wms selectableTenants 에 acme-corp 포함, scm/erp 불포함, gap bindsAllTenants 분기 + operator scope 불변.
5. **(선택) contract/spec 노트**: `console-registry-api.md`(또는 `account-tenant-domain-subscriptions.md`)에 "step 2 부터 실 고객 테넌트가 카탈로그 tenants[] 에 도메인-슬러그와 함께 등장(values 만 변화, envelope shape 불변 — ADR-019 D4)" 1~2줄. 기존 net-zero 노트 보존.

## Out of scope

- **런타임 cross-domain E2E**(acme-corp 토큰이 실제 wms/finance 호출 통과, scm/erp 403) — console-bff per-domain credential 의 tenant-scoped 진화에 의존(별 task; step 3 잔여 console-bff).
- keystone(BE-324) 코드 변경 — 이미 머지, 본 task 는 그 데이터 의존성을 먹일 뿐.
- operator↔tenant 다중 할당 join 테이블(D3-B, step 4 deferred) — `acme-corp` 는 platform-scope('*') operator 가 구독-기반 카탈로그로 자동 노출(별 operator assignment 시드 불요; single-value `admin_operators.tenant_id` MVP 유지).
- 도메인 게이트/JwtTokenGenerator/구독 mutation 표면/legacy slug 제거(step 4).
- 추가 고객(globex 등) — 본 task 는 acme-corp 1개.

# Acceptance Criteria

- **AC-1**: account-service 에 `acme-corp` tenant(ACTIVE) + `acme-corp`→finance, `acme-corp`→wms 구독(ACTIVE) Flyway 시드. clean-migrate GREEN(FK 충족, idempotent INSERT IGNORE).
- **AC-2 (역조회 증명)**: real DB 에서 `findActiveByTenantId("acme-corp")` = {finance, wms}; `findActiveByTenantId("wms")` = {wms}(무회귀); 미존재 = 빈.
- **AC-3 (카탈로그 고객명)**: platform-scope operator GET registry → finance product tenants 에 `acme-corp` 포함 + wms product tenants 에 `acme-corp` 포함 + scm/erp product tenants 에 `acme-corp` 불포함.
- **AC-4 (기존 무회귀)**: 기존 net-zero 카탈로그 케이스(slug-only) 단언 무변경; M6 isolation 단언 보존; envelope shape(`tenants: string[]`) 불변(values 만 변화, ADR-019 D4).
- **AC-5**: account-service + admin-service 컴파일 + 전 테스트 GREEN — **CI Linux GAP Integration(Testcontainers + WireMock)** 권위 게이트. 회귀 0.
- **AC-6 (scope-lock)**: 변경 = account Flyway V0020 + account 역조회 테스트 + admin 카탈로그 2 테스트(+ 선택 spec 노트) 만. keystone 코드/도메인 게이트/추가 고객/operator join/mutation 표면 0.

# Related Specs

- `docs/adr/ADR-MONO-019-...md` § 2 D1/D2/D4 + § 3.3 step 2(line 130) + line 99(step 2 = catalog-visible).
- `projects/global-account-platform/tasks/done/TASK-BE-322-...md`(V0019 backward-compat 시드 + 구독-기반 카탈로그) + `TASK-BE-324-...md`(keystone — 역조회 consumer).
- `projects/global-account-platform/specs/contracts/console-registry-api.md` + `.../http/internal/account-tenant-domain-subscriptions.md`.
- `rules/traits/multi-tenant.md` M3/M6.

# Related Contracts

- 카탈로그 `tenants: string[]` envelope shape 불변, values 에 고객 id 등장(ADR-019 D4 — sixth confirmation).

# Related Code

- account: `db/migration/V0020__...sql`(신규) + `TenantDomainSubscription{Repository,RepositoryImpl,JpaRepository}`(BE-324 의 `findActiveByTenantId` 재사용 — 코드 변경 없이 테스트만) + 기존 V0019 시드 패턴.
- admin: `ConsoleRegistryIntegrationTest` + `ConsoleRegistryUseCaseTest`(stub/단언 추가) + `ConsoleRegistryUseCase`(코드 무변경 — 구독-기반 derivation 이 이미 임의 고객 처리).

# Edge Cases

- **FK 순서**: 같은 migration 내 tenant insert → subscription insert 순서. INSERT IGNORE idempotent.
- **gap product**: bindsAllTenants 라 acme-corp 자동 federate(gap tenants 에 acme-corp 등장) — gap 구독 행 시드 금지(이중계산).
- **operator scope**: platform('*') operator 만 acme-corp 노출 검증; single-tenant operator 는 자기 슬러그만(M6 보존, 기존 케이스).
- **net-positive**: step 2 는 카탈로그를 의도적으로 바꿈 — 새 케이스로 검증, 기존 net-zero 케이스는 별 stub 으로 보존.
- **런타임**: acme-corp 토큰 발급 주체 미존재 → 도메인 런타임 무변경(catalog-visible-but-runtime-inert until console-bff credential path).

# Failure Scenarios

- FK 위반(subscription before tenant) → migration 실패 → 순서 보장.
- 기존 net-zero stub 오염 → 기존 카탈로그 단언 회귀 → 새 케이스는 별 stub.
- gap 구독 시드 → 이중계산 → 금지.
- scm/erp 구독 잘못 시드 → 미구독 거부 시맨틱 깨짐 → finance+wms 만.

---

# Implementation Design Notes

- Flyway V0020 = V0014~V0019 시드 패턴 답습(INSERT IGNORE, 주석 헤더). tenant 먼저, 구독 2행.
- 카탈로그 derivation 코드는 BE-322 에서 이미 임의 고객 처리(구독 ∩ activeTenants ∩ scope) → 코드 변경 0, 테스트만 추가.
- 역조회 IT = keystone 데이터 의존성의 real-seed 증명(WireMock 아닌 real DB).
- CI Linux GAP Integration 권위. 구현 = Sonnet.

---

# Notes

- ADR-019 § 3.3 step 2(실 고객). step 0 ACCEPTED → step 1 BE-322 → step 3 게이트 4/4 → keystone BE-324 → **step 2 본 task** → (런타임 cross-domain E2E + console-bff credential path) → step 4 cleanup. dependency-correct base = BE-324 머지 main.
