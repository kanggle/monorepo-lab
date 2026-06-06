# Task ID

TASK-MONO-180

# Title

Rename the residual **`gap` cosmetic + identity surfaces → `iam`** left after TASK-MONO-179: the platform-console **domain/registry slug** (`DomainTarget.GAP`, registry `productKey`/`baseRoute`/`displayName`), the **reserved tenant word** + console `tenant_id='gap'`, and the **OIDC provider id** `gap` + `/callback/gap` redirect_uris. One atomic cross-project PR (BE contract + 2 new Flyway migrations + console-web + 3 consumer frontends).

# Status

ready

# Owner

claude (Opus 4.8) — monorepo-level rename follow-up to TASK-MONO-179. One atomic PR (CLAUDE.md § Cross-Project Changes / Task Rules: shared `tasks/`, plus `projects/iam-platform/**`, `projects/platform-console/**`, `projects/fan-platform/**`, `projects/ecommerce-microservices-platform/**`).

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- api
- deploy
- test

---

# Dependency Markers

- **선행**: TASK-MONO-179 (gap→iam directory + alias + user-iss flip) — DONE, merged `a8515772`. This task cleans the residual `gap` surfaces 179 deliberately left out of scope (slug / provider id / reserved word are wire+persisted values, not code names).
- **decision (user, 2026-06-06)**: after 179 flipped `iss=iam`, the console wire-slug (`{"domain":"gap"}`), registry `productKey:"gap"` + display name `"Global Account Platform"`, and the OIDC provider id `gap` (`/callback/gap`) left a **split-brain** (token=iam, console/OAuth=gap). Audit revealed 4 distinct `gap` meanings; user chose **full** scope (all four → `iam`).
- **leaves unchanged (NOT in scope)**: Flyway migration *files* V0011/V0012/V0015 and their comment headers (historical record — renaming breaks checksums; the value changes land in a NEW forward migration V0024); `tasks/done/**` history; ADR pre-amendment body history; the OAuth `client_id` registrations (`ecommerce-web-store-client`, `fan-platform-user-flow-client`, `platform-console-web` — consumer/console-named, not `gap`); the English word "gap" in prose/Tailwind classes (`gap-4`, `flex gap-2`) / CSS (`gap:`) — **must be protected from the sweep** (see Edge Cases).

# Goal

After this task, no operator-visible or wire/persisted `gap` identifier remains for the IAM domain: the BFF emits `{"domain":"iam"}`, the registry product is `productKey:"iam"` / `"Identity & Access Management"` / `/iam`, the console's own OAuth client is `tenant_id='iam'` (and `iam` is a reserved tenant word), and consumer OIDC login uses provider id `iam` → `/api/auth/callback/iam` matched by the V0011/V0012 clients' redirect_uris. `gap` survives only as legitimate residue (Flyway file history / `tasks/done` / ADR pre-amendment / the English word). Main stays green; auth-flow e2e (fan + ecommerce login) and console e2e (operator-overview / domain-health cards) prove no regression.

# Scope

## (a) Registry product slug + display name — admin-service origin → console-web contract

1. `projects/iam-platform/apps/admin-service/.../application/console/ProductCatalog.java:47` — `Entry("gap","Global Account Platform",true,true,null,"/gap")` → `Entry("iam","Identity & Access Management",true,true,null,"/iam")`.
2. `admin-service` tests: `ConsoleRegistryUseCaseTest` (~10 `product(r,"gap")` / `"/gap"` assertions), `ConsoleRegistryIntegrationTest` (`productKey == "gap"`).
3. Contract spec `projects/iam-platform/specs/contracts/http/console-registry-api.md` (product catalog table) + `specs/features/multi-tenancy.md` product list (`gap/wms/...`).
4. console-web `src/shared/api/registry-types.ts` `ProductKeySchema = z.enum(['gap',...])` → `['iam',...]`; all `productKey: 'gap'` / `baseRoute: '/gap'` / `tile-gap` test fixtures (~40 files under `tests/unit`); `console-route` resolution (`gap → /accounts` stays — only the key changes).

## (b) Domain-health / operator-overview card slug — console-bff origin → console-web

5. `console-bff` `domain/credential/DomainTarget.java` enum `GAP` → `IAM` (drives `.name().toLowerCase()` wire slug); ripple: `LegOutcome`, `CompositionEngine`, `DomainHealthController`, `OperatorOverviewController`, `CredentialSelectionPort` selector predicate + all console-bff tests; `console-integration-contract.md § 2.4.9` selector doc (`GAP → OperatorToken`).
6. console-web `features/domain-health/api/types.ts` + `features/operator-overview/api/operator-overview-types.ts` `CARD_ORDER = ['gap',...]` → `['iam',...]`; `DomainCard.tsx` (`case 'gap':`, label `gap:'GAP 계정'`, `card.domain==='gap'`, drilldown), `DomainHealthCard.tsx` (label `gap:'GAP'`); test-ids `operator-overview-card-gap*` / `domain-health-card-gap-*` / `gap-detail-back-link` / `gap-login`; all `domain:'gap'` fixtures + e2e specs + page-comment refs.

## (c) Reserved tenant word + console `tenant_id` — auth/admin origin

7. `admin-service` `application/tenant/CreateTenantUseCase.java` `RESERVED` set — **ADD `iam`** (keep `gap` reserved: harmless + prevents a future `gap` tenant hijacking historical tokens). `multi-tenancy.md` reserved-word list (line ~60) + tests `CreateTenantUseCaseTest` / `TenantAdminIntegrationTest` (add an `iam` reserved case).
8. console OAuth client `tenant_id='gap'` → `'iam'` via **new migration V0024** (NOT editing V0015). `multi-tenancy.md` `tenant_id='gap'` scope prose. auth-service ITs that assert the seeded value: `PlatformConsoleOidcClientSeedIntegrationTest` (`tenantId=="gap"`), `AssumeTenantAuthenticationProviderTest` (`claim tenant_id "gap"`).

## (d) OIDC provider id + redirect_uris — auth origin → 3 frontends

9. 3 frontends `src/shared/auth/auth.ts` provider `id:'gap'` → `id:'iam'`, `name:'Global Account Platform'` → `name:'IAM'`: `fan-platform/web/fan-platform-web`, `ecommerce/apps/web-store`, `ecommerce/apps/admin-dashboard`. Each app's `signIn('gap')` (login page) → `signIn('iam')`; `middleware.ts` / comment refs to `/api/auth/callback/gap`; e2e helper `callback/gap`.
10. **new migration V0024** UPDATEs redirect_uris `/callback/gap` → `/callback/iam` for `fan-platform-user-flow-client` (V0011, 2 URIs) + `ecommerce-web-store-client` + `ecommerce-admin-dashboard-client` (V0012, 4 URIs). auth-service `OAuthClientPostLogoutRedirectUriSeedIntegrationTest` (`/callback/gap` assertions) → `/callback/iam`.
11. Specs: `auth-api.md` client table (3 rows redirect_uris), ecommerce + fan `specs/integration/iam-integration.md` redirect_uris rows, fan `fan-platform-web` overview.md / architecture.md `signIn('gap')` + callback path.

## (e) New forward migration

12. `projects/iam-platform/apps/auth-service/src/main/resources/db/migration/V0024__rename_gap_slug_to_iam.sql` — single forward migration: (i) `UPDATE oauth_clients SET tenant_id='iam' WHERE client_id='platform-console-web'`; (ii) `UPDATE oauth_clients SET redirect_uris = REPLACE(redirect_uris,'/api/auth/callback/gap','/api/auth/callback/iam') WHERE client_id IN ('fan-platform-user-flow-client','ecommerce-web-store-client','ecommerce-admin-dashboard-client')`. Verify no `post_logout_redirect_uris` carry `/callback/gap` (those are app-root landings, not callbacks — confirm during impl).

## Out of Scope

- Flyway files V0011/V0012/V0015 bodies/headers (checksum) — value changes go to V0024.
- OAuth `client_id`s, Java packages, DB schema names, the `iss` claim (already `iam` from 179).
- Tailwind `gap-*` utility classes + CSS `gap:` + the English word "gap" in prose/tests.

# Acceptance Criteria

- AC-1: `./gradlew :projects:iam-platform:apps:{admin,auth}-service:check :projects:platform-console:apps:console-bff:check` green — ProductCatalog/DomainTarget/RESERVED renames compile + unit/IT pass (incl. updated seed-value ITs).
- AC-2: console-web `pnpm test` (vitest) + `pnpm lint` + `tsc` green — `ProductKeySchema`/`CARD_ORDER`/`DomainCard` + ~40 fixtures consistent; NO Tailwind `gap-*` / CSS `gap:` mangled (git diff shows only slug edits).
- AC-3: 3 consumer frontends lint + unit green; provider id `iam` consistent with V0024 redirect_uris.
- AC-4: CI `iam-integration-tests` (Testcontainers, V0024 applies) green; **fan-platform + ecommerce login e2e** green (proves `/callback/iam` ↔ redirect_uris match — no `redirect_uri_mismatch`); **console e2e** operator-overview/domain-health cards render under slug `iam`.
- AC-5: Residue grep — every remaining `\bgap\b` / `"gap"` / `/gap` / `callback/gap` sits in a legitimate bucket (Flyway V0011/12/15 + headers, `tasks/done`, ADR pre-amendment, English-word, Tailwind/CSS). NO live `productKey:"gap"` / `DomainTarget.GAP` / provider `id:'gap'` / console `tenant_id='gap'` seed-value remains.
- AC-6: V0024 applies cleanly on a fresh Flyway run (IT proves it); `flyway validate` (no checksum break on V0001–V0023).

# Related Specs

- `projects/iam-platform/specs/contracts/http/console-registry-api.md`, `.../auth-api.md`
- `projects/iam-platform/specs/features/multi-tenancy.md` (reserved word + tenant_id='gap' scope + product list)
- `projects/platform-console/specs/**/console-integration-contract.md` § 2.4.9 (DomainTarget selector)
- `projects/fan-platform/specs/services/fan-platform-web/{overview,architecture}.md`, 2× `specs/integration/iam-integration.md`

# Related Contracts

- console-registry-api (productKey/baseRoute/displayName) — registry contract change, BE+FE atomic.
- auth-api OAuth client table (redirect_uris) — value change via V0024.
- BFF domain-health/operator-overview JSON `domain` field (wire slug) — BE+FE atomic.

# Edge Cases

- **Tailwind/CSS `gap` collision (MONO-179 corruption class #1)**: `gap-3`/`flex gap-2`/`gap-x-2`/`gap-y-1` classes + CSS `gap:` property are PERVASIVE in console-web. NEVER blind-S&R `gap`. Sweep ONLY: `'gap'`, `"gap"`, `productKey: 'gap'`, `baseRoute: '/gap'`, `domain: 'gap'`, `case 'gap'`, `=== 'gap'`, the `-gap` test-id suffixes, `enum(['gap'`, `['gap',`. Verify post-sweep `git diff` touches no `className`/`gap-`/`gap:` token.
- **Registry productKey is a BE-defined contract**, not Flyway-seeded — flips in `ProductCatalog.java` + must flip console-web `ProductKeySchema` in the SAME PR (zod enum rejects unknown key → registry fetch breaks otherwise).
- **DomainTarget enum vs registry productKey are SEPARATE `gap` strings** (different enums, same slug) — both must flip; they feed different console-web arrays (`CARD_ORDER` vs `ProductKeySchema`).
- **Provider id ↔ redirect_uris atomicity**: `id:'iam'` makes NextAuth use `/api/auth/callback/iam`; if V0024 redirect_uris not updated in the same PR → `redirect_uri_mismatch` on every consumer login. Frontends + V0024 MUST land together; login e2e is the proof.
- **`gap` reserved word kept**: ADD `iam`, do not remove `gap` — a historical operator/console token may still carry `tenant_id='gap'` until V0024 + redeploy; keeping `gap` reserved prevents a consumer registering it mid-transition.

# Failure Scenarios

- **redirect_uris not migrated** → consumer login `redirect_uri_mismatch` (400). V0024 + 3 frontends atomic; fan/ecommerce login e2e is the gate.
- **console-web `ProductKeySchema` not flipped with `ProductCatalog`** → registry parse error → blank catalog. BE+FE same PR.
- **Tailwind `gap-*` mangled by over-broad sweep** (MONO-179 recurrence) → console-web layout breaks, vitest snapshot/RTL fails. Scoped sweep + `git diff` audit before commit.
- **V0024 edits an existing migration instead of adding a new file** → Flyway checksum break → every IT fails on `validate`. New file only.
