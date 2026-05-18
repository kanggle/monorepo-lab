# Task ID

TASK-MONO-114

# Title

finance-platform bootstrap artifact (PR-B) — `rules/domains/fintech.md` + `projects/finance-platform/` direct-include tree + account-service skeleton + GAP V-slot seed + TASK-FIN-BE-001 + monorepo wiring (ADR-MONO-008 ACCEPTED, Option C)

# Status

review

# Owner

architect

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- onboarding
- code
- deploy

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **depends on**: TASK-MONO-113 (PR-A — ADR-MONO-008 PROPOSED→ACCEPTED, merged → main `1c98fab6`). ADR-MONO-008 § D4 procedure + § D6.2 PR-B definition govern this task. Branch base = post-#593 main (ADR-008 ACCEPTED present = dependency-correct base).
- **prerequisite for**: TASK-FIN-BE-001 (account-service implementation — authored by this task into `projects/finance-platform/tasks/ready/`, implementation deferred). erp bootstrap (ADR-MONO-009 candidate) is gated on finance closure.
- **precedent**: TASK-MONO-040 (scm-platform skeleton) + TASK-MONO-042 (scm GAP V0013/V0015 seed) — `project_scm_platform_bootstrap` (5번째 프로젝트 부트스트랩, 5 PR). This is the 6th project, same shape, Option C adds the external Template-fork repo.
- **monorepo-level**: touches shared paths (`rules/`, `.claude/config/`, root `settings.gradle`/`package.json`, `docs/project-overview.md`, root `README.md`, `scripts/sync-portfolio.sh`) + creates `projects/finance-platform/` + GAP-internal Flyway seed → root `tasks/`.

---

# Goal

ADR-MONO-008 is ACCEPTED (2026-05-18, Option C). Execute the § D4 bootstrap procedure's monorepo side + author the standalone-repo hand-off. Per § D1 Option C the deliverable = Template-fork external repo (`kanggle/finance-platform`) **+** monorepo `projects/finance-platform/` direct-include. The external `gh repo create` is outward-facing + classifier-blocked → handed to the user as exact commands (this task delivers everything in-monorepo + the precise hand-off script).

Classification (ADR-008 § D2/D3, finalised in PR-A):
- **domain**: `fintech` · **traits**: `[transactional, regulated, audit-heavy]` (NO `event-driven` — not one of the 11 taxonomy traits; ADR § D2's "optional" is excluded to avoid HARDSTOP-02) · **service_types**: `[rest-api, event-consumer]` · compliance `[]` · data_sensitivity `confidential` (financial) · scale_tier `startup` · taxonomy_version `0.1`.
- **first service**: `account-service` (Hexagonal; Account lifecycle — KYC / balance / hold-release). `ledger-service` (double-entry) deferred to v2.
- **framing reconcile** (memory `project_portfolio_7axis_architecture` says finance = 분개/GL/AP accounting): ADR-008 is SoT (Source-of-Truth Priority: platform/ADR > memory). PROJECT.md frames v1 as fintech Account/Wallet/Transaction with the accounting/ledger depth explicitly **v2 (ledger-service)**. The 7-axis "auto-journal on domain events" narrative is recorded as the v2/future integration story, not v1 scope.

# Scope

## In Scope (monorepo, this PR-B)

1. **`rules/domains/fintech.md`** (NEW, on-demand domain rule per `rules/README.md`) — Mandatory Rules `F1..Fn` modeled on `rules/domains/scm.md` (214 lines, S1-S8 shape): financial-transaction atomicity+idempotency, double-entry/balance invariants (forward-decl for ledger v2), KYC/AML compliance gates, money representation (minor-units integer / `BigDecimal`, never float), immutable audit trail on every money-affecting op, regulated PII handling, reconciliation discrepancy → operator queue (no silent auto-close). Project-agnostic (no finance-platform service names — HARDSTOP-03).
2. **`.claude/config/activation-rules.md`** — `### fintech` L198: `*(file to be created when a project declares this domain)*` → `[\`rules/domains/fintech.md\`](../../rules/domains/fintech.md)` (mirror scm L260 form). Only that one line.
3. **`projects/finance-platform/` tree** (direct-include, scm-platform shape):
   - `PROJECT.md` — frontmatter (above classification) + Purpose + Domain/Trait Rationale + Service Map (v1: gateway-service + account-service; v2 deferred: ledger/wallet/kyc/notification/admin) + GAP IdP Integration (tenant + client_credentials) + Local Network (`finance.local`) + v1 IN/OUT slice + Out of Scope (incl. why not `pg`/`banking`/`securities`, why not `multi-tenant`/`integration-heavy`/`batch-heavy`) + Overrides (none).
   - `tasks/INDEX.md` (project lifecycle, scm shape) + `tasks/{ready,review,done,archive,in-progress}/.gitkeep`.
   - `docker-compose.yml` (Traefik `finance.local` label, GAP-style; MySQL/Redis `expose:` only — Local Network Convention, no host ports, no PORT_PREFIX).
   - `.env.example`, `build.gradle`, `README.md`.
   - `apps/.gitkeep`, `specs/{services,contracts,features,use-cases,integration}/.gitkeep`, `knowledge/.gitkeep`, `docs/.gitkeep`, `infra/.gitkeep`.
   - `specs/integration/gap-integration.md` (OAuth2 RS pattern + `tenant_id=finance` gate + token example — scm precedent).
4. **`account-service` minimal skeleton** at `projects/finance-platform/apps/account-service/`: `build.gradle` (Spring Boot, libs deps per scm), `src/main/java/com/example/finance/account/AccountServiceApplication.java` (minimal `@SpringBootApplication`), `src/main/resources/application.yml` (port via Traefik, `jdbc:mysql` finance_db, GAP JWKS RS256 resource-server), `src/main/resources/db/migration/.gitkeep`, `src/test/.gitkeep`. Goal: `./gradlew :projects:finance-platform:apps:account-service:tasks` resolves (ADR-008 § D4 step 16). Business implementation deferred to TASK-FIN-BE-001.
5. **`projects/finance-platform/tasks/ready/TASK-FIN-BE-001-account-service-bootstrap.md`** — first service implementation task (Hexagonal account-service: KYC/balance/hold-release; required sections; implementation deferred — this PR only authors the task).
6. **GAP V-slot seed** (GAP-internal, scm TASK-MONO-042 precedent):
   - `projects/global-account-platform/apps/account-service/src/main/resources/db/migration/V0017__seed_finance_tenant.sql` — `tenants` row `tenant_id='finance'` (type per GAP convention; scm used `B2B_ENTERPRISE` — finance v1 internal-services = same).
   - `projects/global-account-platform/apps/auth-service/src/main/resources/db/migration/V0017__seed_finance_oidc_client.sql` — `oauth_clients` `finance-platform-internal-services-client` (client_credentials, redirect_uris `[]`, scopes `finance.read`/`finance.write`) + `oauth_scopes` 2 rows + BCrypt(strength=10) hash for secret `"finance-dev"` (regenerate; do NOT reuse scm's hash). access_token TTL 1800s (V0010/V0013 pattern).
   - Update GAP `specs/contracts/http/auth-api.md` client table (scm precedent updated it) if it enumerates clients.
7. **root `settings.gradle`** — include `projects:finance-platform:apps:account-service` (+ comment, scm style).
8. **root `package.json`** — `finance:{up,down,ps,logs,docker}` 5 scripts (mirror scm L55-59).
9. **`docs/project-overview.md`** — new `### 2.x finance-platform` roster entry + bump `## 2. 프로젝트 카탈로그 (N 도메인 …)` count; add to GAP §2.2 registered-tenant list (`wms / scm / fan-platform` → `+ finance`).
10. **root `README.md`** — portfolio hub: add finance-platform. **HARDSTOP-03 caution**: do not embed a raw `projects/finance-platform/` path-token into the shared README in a way the hook flags; use ADR-MONO-008 reference + prose (BE-299 lesson — reference the ADR/domain, not the project path-token).
11. **`scripts/sync-portfolio.sh`** PROJECT_REMOTES — register `finance-platform` → `kanggle/finance-platform` (Option C only; scm precedent deferred this to first v1 publish, but Option C's standalone repo exists at bootstrap, so register now).
12. **External repo hand-off block** (this task's § Verification + PR-B description): exact `gh repo create kanggle/finance-platform --template kanggle/project-template --public --clone --description "…"` + standalone-population steps (ADR-008 § D4 steps 4-12). Classifier-blocked → user runs in their shell; PR-B does NOT attempt it.
13. **ADR-MONO-008 § 6 + ADR-MONO-003a § 3 row #15** PR-B-# backfill: `#TBD-MONO-114` → actual PR-B # (in PR-B itself or its close chore — append-only completion of self-authored placeholder).
14. This task lifecycle ready → review (PR-B) → done (close chore).

## Out of Scope

- account-service **business implementation** (domain model, KYC logic, ledger) → TASK-FIN-BE-001 (deferred; this PR only skeleton + task).
- `ledger-service`, wallet/kyc/notification/admin services → v2 (separate future tasks).
- frontend — v1 backend-only (platform-console renders finance per ADR-MONO-013 §3.3 "backend-only + 콘솔 렌더"; no `frontend-app` service_type — GAP/scm precedent).
- The external `kanggle/finance-platform` repo creation itself (handed to user; classifier-blocked).
- Changing ADR-MONO-008 decisions (fixed; PR-A recorded them).
- erp / mes (ADR-MONO-009/010 future).
- CI workflow path-filter new-project entry **if** scm's pattern already globs `projects/**` — verify; only add a finance filter entry if the path-filter is per-project (CLAUDE.md CI path-filter constraint: pure-positive, no negation).

# Acceptance Criteria

- **AC-1 (classification valid)**: `projects/finance-platform/PROJECT.md` frontmatter parses; `domain: fintech` ∈ taxonomy (L201); each trait ∈ the 11 taxonomy traits; `event-driven` absent. No HARDSTOP-02.
- **AC-2 (domain rule project-agnostic)**: `rules/domains/fintech.md` contains zero finance-platform service names / API paths / entity names (HARDSTOP-03 hook PASS); structurally parallels `scm.md` (Mandatory Rules list + checklist).
- **AC-3 (tree complete)**: `projects/finance-platform/` has PROJECT.md + tasks/INDEX.md + docker-compose.yml + .env.example + build.gradle + README.md + the .gitkeep scaffold + `specs/integration/gap-integration.md` + account-service skeleton + TASK-FIN-BE-001 in tasks/ready/.
- **AC-4 (gradle resolves)**: `./gradlew :projects:finance-platform:apps:account-service:tasks` exits 0 (ADR-008 § D4 step 16); `./gradlew projects` lists finance-platform; no regression to other projects' build.
- **AC-5 (GAP seed correct)**: account-service V0017 + auth-service V0017 are valid MySQL Flyway DDL; BCrypt hash verifies against `"finance-dev"` (strength=10, NOT scm's hash); scopes `finance.read`/`finance.write`; `grep -ril postgres` GAP runtime stays 0. GAP Testcontainers IT still green (V0017 applies cleanly on top of V0016).
- **AC-6 (Local Network)**: docker-compose uses Traefik `finance.local` host label; backing services `expose:` only (no host ports, no `PORT_PREFIX`) — TEMPLATE.md Local Network Convention.
- **AC-7 (wiring)**: settings.gradle + package.json finance:* + docs/project-overview.md roster (+count bump + GAP tenant list) + root README hub (HARDSTOP-03 PASS) + sync-portfolio.sh PROJECT_REMOTES all updated; `node -e "require('./package.json')"` parses.
- **AC-8 (no cross-project regression)**: existing 5 projects' build/CI unaffected; `git diff` touches only the enumerated shared files + new `projects/finance-platform/` + GAP V0017 ×2 + auth-api.md.
- **AC-9 (ADR recordings)**: ADR-MONO-008 § 6 row + ADR-MONO-003a § 3 row #15 `#TBD-MONO-114` → PR-B # backfilled (append-only). ADR-MONO-008 § D4 steps 18-20 recordings done (memory `project_portfolio_7axis_architecture` + `project_monorepo_template_strategy` finance = bootstrapped + Template first-use confirmed).
- **AC-10 (external hand-off)**: PR-B description + this task § Verification contain the exact `gh repo create … --template kanggle/project-template …` + standalone-population command block; PR-B itself does NOT create the external repo (classifier-respect).
- **AC-11 (CI)**: code+config change → full pipeline; new account-service skeleton compiles (no tests yet — skeleton). GAP IT green (V0017). Self-review APPROVED.

# Related Specs

- `docs/adr/ADR-MONO-008-finance-platform-bootstrap.md` § D1-D6 (governing), `docs/adr/ADR-MONO-002` § D4, `docs/adr/ADR-MONO-013` §3.3 (backend-only + console renders), `TEMPLATE.md` § Local Network Convention + § Phase 6+.
- `rules/taxonomy.md` § Financial Services (`fintech` L201) + § Traits (11). `rules/domains/scm.md` (domain-rule template). `rules/README.md` (on-demand domain-file policy).
- `projects/scm-platform/` (full bootstrap precedent — PROJECT.md / docker-compose / tasks/INDEX shape). memory `project_scm_platform_bootstrap`, `project_portfolio_7axis_architecture`, `project_monorepo_template_strategy`.
- GAP `apps/{account,auth}-service` V0013/V0015/V0016 (Flyway seed precedent), `specs/contracts/http/auth-api.md`.

# Related Contracts

- GAP `oauth_clients` / `oauth_scopes` / `tenants` seed (V0017 ×2) — GAP-internal client-credentials provisioning, scm V0013/V0015 precedent. New OIDC client `finance-platform-internal-services-client` is a GAP-side contract addition (scopes `finance.read`/`finance.write`); auth-api.md client enumeration updated if present. No finance-platform HTTP/event contract authored in this PR (deferred to TASK-FIN-BE-001 / v1 service specs).

# Edge Cases

- **HARDSTOP-03 on shared files**: `rules/domains/fintech.md` must be project-agnostic; root `README.md` must reference the ADR/domain, not a raw `projects/finance-platform/` path-token (the hook auto-detects project-token-in-shared — BE-299 lesson). `.claude/config/activation-rules.md` edit is the generic domain-link form (matches scm).
- **`event-driven` trap**: ADR § D2 lists it "optional" but it is NOT a taxonomy trait → PROJECT.md must NOT declare it (HARDSTOP-02). Stack fixed at `[transactional, regulated, audit-heavy]`.
- **BCrypt hash reuse**: must regenerate for `"finance-dev"`; reusing scm's `$2a$10$Eck9…` hash (matches `"scm-dev"`) would mis-authenticate. Verify with a BCrypt check.
- **Flyway V-number collision**: account-service & auth-service GAP dirs both currently end at V0016 → both new files are **V0017** (independent migration histories per service — not a global counter). Confirm no V0017 exists in either before writing.
- **framing tension (fintech vs accounting)**: PROJECT.md must reconcile per ADR-008 SoT (account-service v1; ledger/accounting depth = v2) — do not let the 7-axis "분개/GL/AP" memory narrative pull v1 scope into ledger territory.
- **settings.gradle churn-clock**: per ADR-MONO-003a § D2.1 this bootstrap intentionally resets the libs/ churn clock — expected, recorded in ADR-008 § 3.3, not a violation.
- **CI path-filter**: if `.github/workflows` path-filter is per-project (not `projects/**` glob), add a finance entry using a **pure-positive** pattern (no negation — CLAUDE.md CI path-filter constraint, MONO-074/075 lesson). Verify before editing.

# Failure Scenarios

- **PROJECT.md declares `event-driven` or an unknown domain** → HARDSTOP-02 at classification; AC-1 rejects. Mitigation: stack pinned, domain `fintech` taxonomy-verified.
- **`rules/domains/fintech.md` names a finance-platform service / path** → HARDSTOP-03 hook blocks commit; AC-2. Mitigation: project-agnostic authoring, scm.md parallel.
- **BCrypt hash mismatch** → finance client cannot obtain a token; integration broken later. AC-5 BCrypt verify gate.
- **`./gradlew` regression** (skeleton build.gradle wrong / settings.gradle typo) → AC-4/AC-8; other projects' build breaks. Mitigation: mirror scm build.gradle exactly, `./gradlew projects` + `:account-service:tasks` sanity before commit.
- **External repo attempted by agent** → classifier block (correct). AC-10: hand off, do not bypass — STOP + give user exact command (admin-web/branch-delete discipline).
- **GAP V0017 breaks GAP Flyway validate** (bad DDL / duplicate key) → GAP Testcontainers IT red. AC-5: validate DDL + apply-on-V0016 mentally; CI authoritative.
- **Bundsling vs ADR § D6.2**: ADR says PR-B is one artifact PR; if size warrants splitting (e.g. GAP seed separate like scm's MONO-042), that is acceptable per `feedback_pr_bundling` provided each sub-PR cites ADR-008 § D4 + this task — but default is single PR-B.

# Verification

1. `grep "^domain:\|^traits:\|^service_types:" projects/finance-platform/PROJECT.md` → `fintech` / `[transactional, regulated, audit-heavy]` / `[rest-api, event-consumer]`; cross-check taxonomy (AC-1).
2. HARDSTOP-03 hook PASS on commit (fintech.md + README) (AC-2/AC-7).
3. `./gradlew :projects:finance-platform:apps:account-service:tasks` exit 0 + `./gradlew projects | grep finance-platform` + no other-project regression (AC-4/AC-8).
4. BCrypt verify `"finance-dev"` vs the V0017 auth hash (small Java/online check) ; `grep -rn finance.read projects/global-account-platform/apps/auth-service/.../V0017*` (AC-5).
5. `docker compose --project-directory projects/finance-platform config` valid; Traefik `finance.local` label present; no host `ports:` on backing services (AC-6).
6. `node -e "require('./package.json')"` ; settings.gradle includes finance module ; project-overview count bumped + GAP tenant list ; README HARDSTOP-03 PASS (AC-7).
7. `git diff --stat` scoped to enumerated shared files + `projects/finance-platform/` + GAP V0017 ×2 + auth-api.md only (AC-8).
8. ADR-008 §6 / ADR-003a #15 PR-B# backfilled ; memory updated (AC-9).
9. External hand-off command block present in PR-B body + this § (AC-10). CI: full pipeline + GAP IT green (AC-11).

**External repo hand-off (user runs in their shell — classifier-blocked, do NOT let the agent attempt; ADR-008 § D4 steps 4-12)**:

```
gh repo create kanggle/finance-platform --template kanggle/project-template --public --clone --description "Finance platform — fintech domain (account-service v1) bootstrapped from kanggle/project-template, ADR-MONO-008 ACCEPTED 2026-05-18"
# then populate the clone with the finance-platform PROJECT.md + account-service skeleton + TASK-FIN-BE-001
# (hoist projects/finance-platform/* content to the standalone repo root per ADR-008 §D4 step 6 — Option C keeps both the monorepo direct-include and this fork in sync)
```

분석=Opus 4.7 / 구현 권장=Opus 4.7 (new-project bootstrap — domain-rule authoring + PROJECT.md classification + GAP Flyway seed correctness + HARDSTOP-03/02 governance are judgement-heavy; scaffolding/wiring mechanical. Large + well-specified → may dispatch backend-engineer(opus) for scaffold/wiring with dispatcher independently verifying fintech.md/PROJECT.md/GAP-seed/HARDSTOP per BE-301 pattern) / 리뷰=Opus 4.7 (classification + HARDSTOP + Flyway + gradle-regression + ADR-recording discipline).
