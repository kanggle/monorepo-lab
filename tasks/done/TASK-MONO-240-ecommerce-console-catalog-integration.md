# Task ID

TASK-MONO-240

# Title

ecommerce를 platform-console 도메인 카탈로그에 6번째 구독가능 도메인 제품으로 노출 (ADR-MONO-030 Step 4 facet (a) — 콘솔 통합 수직 슬라이스). `tenant_domain_subscription` `domain_key='ecommerce'` 시드 + admin-service `ProductCatalog` Entry + console-web `ProductKeySchema` Zod enum 확장 + 계약/테스트 정합. **cross-project atomic PR** (iam-platform + platform-console). ADR §1.3/§3.1의 "콘솔 코드 변경 없음(zero console-web change)" 전제는 **새 productKey 추가에 한해 거짓**임을 발견 — ADR §6에 factual correction 기록.

# Status

done

# Owner

backend

# Task Tags

- monorepo
- cross-project
- multi-tenant
- console-integration
- adr-mono-030

---

# Dependency Markers

- **선행 (prerequisite, 전부 done)**:
  - `TASK-MONO-230/231/232` — ADR-MONO-030 PROPOSED→ACCEPTED + D4 정정 (done).
  - `TASK-BE-356` — Step 1 스펙 기준 (`specs/features/multi-tenancy-and-marketplace.md`, done).
  - `TASK-BE-357` — Step 2 바깥 축: ecommerce gateway `TenantClaimValidator` 가 **고정슬러그→entitlement-trust** 로 이미 진화 (done — 본 태스크는 게이트를 건드리지 않는다, 검증만).
  - `TASK-BE-363` — Step 3 안쪽 축: seller `seller_id` 귀속 (done — 본 슬라이스 범위 밖, 카탈로그 렌더는 seller 축과 무관).
  - `TASK-BE-322` — ADR-019 D2/D4 subscription-driven domain binding: admin-service 가 `tenant_domain_subscription` 의 ACTIVE 행으로 도메인↔테넌트 바인딩을 도출 (done — 본 태스크의 시드가 이 메커니즘에 얹힌다).
- **근거 (ADR)**: [ADR-MONO-030](../../docs/adr/ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md) §3.4 Step 4 (D5/D6) "콘솔 통합 — 원래 동기, deferred follow-up"; §1.3 "구독행 + 게이트가 존재하면 콘솔이 byte-stable 카탈로그 envelope 로 렌더".
- **scope 결정 (user, 2026-06-13)**: facet **(a) 콘솔 통합** 선택. 슬라이스 = **catalog 렌더만** (health 카드/드릴인 페이지·console-bff `DomainTarget` fanout = 명시적 후속). federation-e2e 고객 시드(V9005) = 제외, 단위/슬라이스+통합 test 로 충분.
- **model**: 분석=Opus 4.8 / **구현 권장=Opus** (cross-project tenancy 통합 + ADR factual correction + 계약 정합; 단순 docs 가 아니라 entitlement seed + Zod 게이트 + 다계층 계약/테스트 동기화).

---

# Goal

ecommerce 가 platform-console 도메인 카탈로그에 **6번째 구독가능 도메인 제품**으로 렌더되게 한다 (ADR-MONO-030 의 원래 동기 — "웹스토어 어드민을 콘솔에서 보고 싶다" — 의 카탈로그-가시성 절반을 닫는다). 콘솔은 `GET /api/admin/console/registry` 의 동적 리스트로 렌더하므로 렌더 로직 변경은 없으나, **새 productKey 를 막는 두 개의 고정-멤버십 게이트**(console-web `ProductKeySchema` Zod enum + admin-service `ProductCatalog`)를 6개로 확장하고, 그 위에 얹힌 계약·테스트 단언을 5→6 으로 정합한다. 동시에 ADR §1.3/§3.1 의 "콘솔 코드 변경 없음" 전제가 **새 도메인 추가에 한해 부정확**(Zod 멤버십 enum 갱신 1줄이 필수)함을 ADR §6 에 factual correction 으로 기록한다 (MONO-232 D4 정정 선례).

# Scope

본 변경은 **두 프로젝트를 원자적으로** 건드린다 (cross-project → 1 atomic PR, CLAUDE.md § Cross-Project Changes).

## In scope

### A. 계약 (contract-first — 구현 전 갱신)

1. **`projects/iam-platform/specs/contracts/http/console-registry-api.md`** (producer 계약):
   - Item shape 표(`productKey` 행): `iam | wms | scm | erp | finance` → **`ecommerce` 추가**.
   - "Product catalog (static, registry-driven)" 표: **`ecommerce` 행 추가** (`available=true`, displayName, 바인딩 규칙). "5 product keys" / "All 5 federated domains" 류 문구 → 6 으로 정합.
   - **"zero console-web code change" 문구 정정** (line ~252-254): *기존 카탈로그 멤버의 `available` 플립·displayName/tenants 변경*은 콘솔 코드 변경 0 이지만, **새 `productKey` 추가는 console-web `ProductKeySchema` Zod enum 1줄 갱신을 요구**한다(의도된 고정-멤버십 가드; `registry-contract.test.ts` "rejects unknown productKey" 가 단언). ADR-019 D5 의 정밀한 의미 = "렌더는 data-driven 0-change, 멤버십 enum 은 명시적 확장".
   - § Change Rule (3) "keep both in sync" 에 `ecommerce` 추가가 console-web enum 갱신을 동반함을 명시.

2. **`projects/platform-console/specs/contracts/console-integration-contract.md § 2.2`** (consumer 계약):
   - Item shape 의 `productKey` 허용 집합에 `ecommerce` 추가. console-web Zod 가 이 계약의 런타임 파서임을 유지.

3. **`projects/iam-platform/specs/features/multi-tenancy.md`** "Platform Console Registry":
   - 카탈로그 멤버십 표/문구에 `ecommerce` 추가 (console-registry-api.md 와 동일 PR 내 동기 — Change Rule 3).

### B. iam-platform 구현

4. **account-service Flyway 시드** — 신규 `V0022__seed_ecommerce_domain_subscription.sql`:
   - `INSERT IGNORE INTO tenant_domain_subscription (tenant_id, domain_key, status, created_at, updated_at) VALUES ('ecommerce', 'ecommerce', 'ACTIVE', NOW(6), NOW(6));`
   - V0019 의 self-subscription 패턴(`(wms,wms)` 등)과 동형. `ecommerce` 테넌트 행은 V0014 에 이미 존재(`tenant_type='B2C_CONSUMER'`, ACTIVE) — 신규 테넌트 시드 불요, 구독행만.
   - **버전 충돌 주의**: 머지 직전 account-service `db/migration/` 최신 V 번호 재확인. 동시 세션이 V0022 를 선점했으면 next-free 로 리넘버.

5. **admin-service `ProductCatalog.java`** (`apps/admin-service/.../application/console/ProductCatalog.java:46-52`):
   - 6번째 `Entry` 추가: `new Entry("ecommerce", "E-Commerce Marketplace", true, false, "ecommerce", "/ecommerce")`.
   - Javadoc("Five product keys" / "zero console-web code change") 를 6 + A.1 의 정정 문구와 정합.

6. **admin-service `ConsoleRegistryIntegrationTest.java`**:
   - `$.products.length()` `value(5)` 단언(≈4개 메서드) → **6**.
   - 가능하면 `ecommerce` 제품 항목 존재 + `available=true` + `tenants` 바인딩(operator scope 에 따라 `["ecommerce"]` 또는 platform-scope 전체 포함) 단언 1개 추가.
   - cross-tenant 격리(M6) 단언은 기존 유지 — single-tenant operator 응답에 타 테넌트 슬러그 누출 0 (ecommerce 항목 포함).

### C. platform-console 구현

7. **console-web `registry-types.ts:16`**:
   - `ProductKeySchema = z.enum(['iam','wms','scm','erp','finance'])` → **`'ecommerce'` 추가**.

8. **console-web `registry-contract.test.ts`**:
   - `toHaveLength(5)` → **6**; 키 배열 단언에 `'ecommerce'` 추가(순서는 ProductCatalog 순서와 일치).
   - "rejects unknown productKey" 테스트는 `'crm'` 같은 **여전히 미등록** 키로 유지(정상). `ecommerce` 는 이제 통과해야 함을 확인하는 케이스 추가(선택).

### D. ADR factual correction (shared docs)

9. **`docs/adr/ADR-MONO-030-...md`**:
   - §6 Status Transition History 에 행 추가 (append-only): "ACCEPTED — factual correction (Step 4 console-fit premise)". 결정 역전 없음 — §1.3/§3.1 의 "콘솔 코드 변경 없음(console fit falls out for free)" 은 *기존 카탈로그 멤버* 에 대해서만 참이고, **새 도메인 추가는 console-web `ProductKeySchema` Zod enum + 그 계약 테스트의 1줄 확장을 요구**한다는 사실을 기록. D1 reuse 프레이밍·D4·D5 결정 불변 (오히려 정밀화: "렌더 0-change, 멤버십 enum 명시 확장").
   - §1.3 / §3.1 의 해당 문구에 정정 각주(또는 인라인 보정) 1개 — MONO-232 의 §1.1/D4 정정 각주와 동일 형식.

## Out of scope (명시적 후속 — ADR §3.4 Step 4 잔여)

- **operator-overview health/overview 카드 + console-bff `DomainTarget.ECOMMERCE` fanout + `/ecommerce` 드릴인 페이지** — ecommerce 가 health/overview 표면을 BFF 에 노출해야 하는 별도 슬라이스. 본 태스크는 카탈로그 **타일 렌더**까지만; 타일의 `baseRoute=/ecommerce` 드릴인 페이지는 후속(클릭 시 미존재 라우트는 후속에서 추가).
- **federation-hardening-e2e 고객-테넌트 시드(V9005) + Playwright 렌더 단언** — nightly e2e 증명. 본 슬라이스는 admin-service 통합테스트 + console-web 계약테스트로 렌더를 증명.
- 셀러 정산/수수료, 나머지 11개 서비스 `tenant_id` 전파, ADR-022 이행 이벤트 `tenant_id` 스레딩, M7 quota, 셀러 온보딩 — 전부 Step 4 잔여 follow-up.

# Acceptance Criteria

- **AC-1 (계약 우선)**: console-registry-api.md · console-integration-contract.md §2.2 · multi-tenancy.md 가 `ecommerce` 를 카탈로그 멤버로 포함하도록 **구현 전** 갱신되고 셋이 상호 정합(productKey 집합·카탈로그 표·"5→6").
- **AC-2 (시드)**: `V0022__seed_ecommerce_domain_subscription.sql` 가 `(ecommerce, ecommerce, ACTIVE)` 구독행을 멱등(`INSERT IGNORE`) 시드. account-service Flyway validate/migrate GREEN.
- **AC-3 (catalog Entry)**: `ProductCatalog.entries()` 가 `ecommerce` Entry(available=true, tenantSlug=ecommerce, baseRoute=/ecommerce)를 6번째로 반환.
- **AC-4 (Zod 멤버십)**: console-web `ProductKeySchema` 가 `ecommerce` 를 포함 → registry 응답의 `productKey:'ecommerce'` 항목이 `RegistryResponseSchema.parse` 를 **통과**(degraded 회피). `registry-contract.test.ts` 6개 단언 GREEN.
- **AC-5 (통합 단언)**: `ConsoleRegistryIntegrationTest` 의 `products.length()` 단언이 6 으로 GREEN; ecommerce 항목이 available + 올바른 tenants 바인딩으로 노출.
- **AC-6 (격리 불변)**: cross-tenant 격리(M6) 회귀 — single-tenant operator 응답의 어떤 제품(ecommerce 포함) `tenants` 에도 타 테넌트 슬러그 누출 0 (기존 단언 유지·GREEN).
- **AC-7 (net-zero degrade)**: ecommerce 구독행이 부재한 환경(standalone / 시드 미적용)에서 카탈로그는 ecommerce 를 단지 미표시(또는 `tenants:[]`)할 뿐 깨지지 않음 — 기존 5 도메인 렌더 byte-unchanged.
- **AC-8 (ADR 정정)**: ADR §6 에 factual-correction 행 + §1.3/§3.1 정정 각주. 결정 역전 0 (D1/D4/D5 불변).
- **AC-9 (빌드 GREEN)**: 영향받는 서비스 `:check` GREEN — admin-service(`:projects:iam-platform:apps:admin-service:check`) + account-service(Flyway) + console-web(`pnpm test` 계약테스트). HARDSTOP-03 N/A (각 변경은 해당 프로젝트 내부; shared `docs/adr` + root `tasks/` 만 monorepo-level).

# Related Specs / Code

- ADR: `docs/adr/ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md` §1.3 / §3.1 / §3.4 Step 4 / §6.
- 스펙: `projects/ecommerce-microservices-platform/specs/features/multi-tenancy-and-marketplace.md` §5(계약 영향) / §7(보류 — 콘솔 통합).
- producer 계약: `projects/iam-platform/specs/contracts/http/console-registry-api.md` (Product catalog 표 · Item shape · Subscription-driven binding · Change Rule).
- consumer 계약: `projects/platform-console/specs/contracts/console-integration-contract.md §2.2`.
- feature: `projects/iam-platform/specs/features/multi-tenancy.md` "Platform Console Registry".
- 코드:
  - `projects/iam-platform/apps/account-service/src/main/resources/db/migration/` (next-free `V0022`).
  - `projects/iam-platform/apps/admin-service/.../application/console/ProductCatalog.java`.
  - `projects/iam-platform/apps/admin-service/.../ConsoleRegistryIntegrationTest.java`.
  - `projects/platform-console/apps/console-web/src/shared/api/registry-types.ts`.
  - `projects/platform-console/apps/console-web/.../registry-contract.test.ts`.
- 참조 구현(선례): ADR-019 D2/D4 subscription binding(TASK-BE-322), V0019 self-subscription 시드.

# Related Contracts

- `console-registry-api.md` (producer) + `console-integration-contract.md §2.2` (consumer) — productKey 멤버십 확장. 두 계약 + multi-tenancy.md 를 **동일 PR 내 동기**(Change Rule 3).
- 이벤트 계약: 없음 (카탈로그 렌더는 동기 read 경로; 이벤트 무관).

# Edge Cases / Failure Scenarios

- **Zod degrade 함정**: `ProductCatalog` 에 ecommerce 를 추가했는데 console-web `ProductKeySchema` 를 누락하면, registry 응답이 `RegistryResponseSchema.parse` 에서 throw → `getCatalog()` catch → `{products:[], degraded:true}` — **전 카탈로그가 degraded** 로 보임(크래시 아님). 두 변경은 반드시 같은 PR(atomic). 이 함정이 곧 ADR 정정의 핵심.
- **통합테스트 5→6 회귀**: admin-service `ConsoleRegistryIntegrationTest` 의 `value(5)` 단언(≈4 메서드)을 빠짐없이 6 으로. 누락 시 admin-service `:check` RED.
- **tenant_type 차이**: ecommerce 테넌트는 `B2C_CONSUMER`(V0014), 타 도메인은 B2B. 카탈로그 바인딩은 구독+ACTIVE 테넌트만 보므로 tenant_type 무관 — 단, `ecommerce` 테넌트가 `tenants` 에 ACTIVE 인지 확인(V0014 시드 상태).
- **`/ecommerce` 드릴인 부재**: 타일은 렌더되나 `baseRoute=/ecommerce` 라우트는 후속 슬라이스 — 클릭 시 미존재 라우트. 본 슬라이스 AC 는 "카탈로그에 타일 렌더"까지(드릴인 out of scope, 정직히 명시).
- **마이그레이션 버전 동시-선점**: 동시 세션이 V0022 를 먼저 머지하면 충돌 — 머지 직전 `db/migration` 재스캔 후 next-free 리넘버.
- **registry "rejects unknown productKey" 테스트**: `crm` 등 여전히 미등록 키로 유지(고정-멤버십 설계 의도 보존). `ecommerce` 만 추가.
- **standalone degrade(D8)**: 플랫폼 IAM 부재 ecommerce standalone 배포는 콘솔 자체가 없으므로 본 변경 무영향. ecommerce 게이트는 absent-claim→default-tenant 로 이미 degrade(Step 2).

# Notes

- ⚠️ JDT.LS OOM 리스크 — facet 1건(catalog 렌더)만 종결하고 마무리. health 카드/드릴인/정산 등은 별 세션.
- 구현은 격리 worktree `mlab-mono239`(브랜치 `task/mono-240-ecommerce-console-catalog-integration`, base=origin/main `6ff7f5fc5`)에서만. 메인 체크아웃 미접촉.
- ecommerce IT 트랩(`-PrunIntegration`·bare-@SpringBootTest multiple-@SpringBootConfiguration)은 본 태스크 무관 — ecommerce 코드 미변경(게이트는 Step 2 done). admin-service/account-service/console-web 만 건드림.
- 머지 후 close-chore: review→done 은 Status 만, closure narrative 는 커밋 메시지에(HARDSTOP-05). 3-dim 검증(state=MERGED / origin tip 일치 / pre-merge 0 failing).
