# Task ID

TASK-MONO-179

# Title

Rename the project **`global-account-platform` (alias `gap`) → `iam-platform` (alias `iam`)** across the whole monorepo — directory, Gradle coords, Docker/compose, pnpm scripts, CI workflows, hostname (`gap.local → iam.local`), Java identifiers (`Gap*` classes + `gap.internal-client.*` property), cross-project OIDC issuer defaults, and the user-login JWT `iss` claim string (`global-account-platform → iam`). One atomic cross-project PR.

# Status

ready

# Owner

claude (Opus 4.8) — monorepo-level mechanical rename, one atomic PR (CLAUDE.md § Cross-Project Changes / Task Rules: shared paths `.github/`, `package.json`, `settings.gradle`, `docs/`, `TEMPLATE.md` + 6 consumer projects).

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- deploy
- adr

---

# Dependency Markers

- **no blocking dependency** — pure rename; no spec/contract semantics change beyond the issuer-string value and identifier names.
- **decision (user, 2026-06-06)**: the project has been promoted to a full OIDC AS/IdP; `global-account-platform`/`gap` no longer reflects its role. Rename to `iam-platform`/`iam` (industry-standard Identity & Access Management term; matches the `<name>-platform` convention + the AWS/GCP framing of platform-console). Scope = FULL rename including the user-token `iss` claim string, flipped atomically in this PR.
- **leaves unchanged (NOT in scope)**: Java packages `com.example.<service>.*` (never contained gap); DB schema names `auth_db`/`account_db`/etc (never contained gap); OAuth `client_id` registrations (consumer-named: `ecommerce-web-store-client`, `fan-platform-user-flow-client`, …); Flyway migration files + their comment headers (historical record — renaming breaks checksums); `tasks/done/**` historical filenames/bodies; ADR pre-amendment body history.

# Goal

After this task, the project lives at `projects/iam-platform/`, every operative reference to the short alias `gap` reads `iam`, the dev hostname is `iam.local`, the user-login JWT is minted and validated under `iss=iam`, and `git grep -i gap` returns only legitimate residue (historical task/ADR/migration records + the English word "gap"). Main stays green; the whole CI matrix runs because the PR touches `.github/workflows/**` + `settings.gradle` + `package.json`.

# Scope

## In Scope

1. **Directory**: `git mv projects/global-account-platform projects/iam-platform` (history-preserving).
2. **settings.gradle**: 8 includes `projects:global-account-platform:apps:* + :tests:e2e` → `projects:iam-platform:...` + comment.
3. **Internal (`projects/iam-platform/`)**: `docker-compose.yml` (`name:`, container names `gap-* → iam-*`, network `gap-net → iam-net`, Traefik IDs, hostnames `*.gap.local → *.iam.local`, `KAFKA_CLUSTERS_0_NAME`); `docker-compose.e2e.yml` (network `gap-e2e → iam-e2e`, `gap.internal-client.* → iam.internal-client.*`, `COMPOSE_PROJECT_NAME`); Java class renames `GapClientCredentialsTokenProvider → IamClientCredentialsTokenProvider` (×4: auth/security/admin/membership) + admin `GapOidc{Properties,SubjectTokenValidator,JwksSubjectTokenValidator} → IamOidc*` (+ tests, via `git mv`); property `gap.internal-client.* → iam.internal-client.*` (@Value + application.yml + @ConfigurationProperties + ≈6 integration tests' `registry.add`); `auth.jwt.issuer: global-account-platform → iam` (mint side).
4. **Cross-project consumers (atomic)**: issuer host default `http://gap.local → http://iam.local` (fan ×3, scm ×3, erp ×4, finance ×1 — issuer-uri + jwk-set-uri + allowed-issuers first entry); **iss flip** gateway `JWT_EXPECTED_ISSUER: global-account-platform → iam` + every consumer `allowed-issuers` second entry `global-account-platform → iam`; platform-console BFF `Gap*` ports/adapters → `Iam*` + env `CONSOLE_BFF_GAP_ISSUER_URL`/`CONSOLE_BFF_OUTBOUND_GAP_BASE_URL`/`GAP_ADMIN_API_BASE` → `..._IAM_...`/`IAM_ADMIN_API_BASE`; ecommerce `auth.ts`/`federated-logout.ts`/gateway-comment `gap.local → iam.local`; `tests/federation-hardening-e2e/**` (rename `gap-golden-path.spec.ts`, compose anchors `*gap-service-env → *iam-service-env`, env, fixtures, README); 6 × `git mv specs/integration/gap-integration.md iam-integration.md` + bodies + fix cross-ref links.
5. **CI**: `ci.yml` (filter key `gap → iam`, glob `→ projects/iam-platform/**`, keep pure-positive `code-changed` AND, NO negation gate; jobs `gap-integration-tests → iam-integration-tests`, `gap-platform-e2e-smoke → iam-platform-e2e-smoke`; outputs/needs/artifacts/steps/gradle coords); `nightly-e2e.yml` (`gap-e2e-full → iam-e2e-full`); `federation-hardening-e2e.yml` (path globs).
6. **Root**: `package.json` 6 `gap:* → iam:*` scripts + project-directory; `scripts/dev-setup.{sh,ps1}` hosts entries; `README.md`, `docs/project-overview.md`, `TEMPLATE.md` (Local Network Convention — `iam.local`), `tasks/INDEX.md`; ADR amendments (append-only) on ADR-MONO-013/014/018 + any GAP-referencing ADR.
7. **USER-applied (classifier-blocked)**: `.claude/hooks/hardstop-detect.ps1` `$shortAliases` map `'global-account-platform' = @(...,'gap') → 'iam-platform' = @('iam-platform','iam')` — handed to user, committed onto this branch before merge.

## Out of Scope

- Java packages, DB schemas, OAuth client_ids, Flyway migration files (see Dependency Markers).
- The English word "gap" in `.claude/skills|commands|workflows` and prose (coverage/harness/time gap).
- Any behavioural change beyond renaming.

# Acceptance Criteria

- AC-1: `projects/iam-platform/` exists, `projects/global-account-platform/` gone; `git status` records renames (R).
- AC-2: `./gradlew :projects:iam-platform:apps:{auth,account,admin,security,gateway,community,membership}-service:check :projects:iam-platform:tests:e2e:check` compiles + unit-tests green.
- AC-3: Consumer compile green: `./gradlew :projects:{fan,scm,erp,finance}-platform:...:check :projects:platform-console:apps:console-bff:check`.
- AC-4: CI `iam-integration-tests` (Testcontainers) + `iam-platform-e2e-smoke` pass; federation-hardening-e2e (nightly/dispatch) green — proves the iss-claim atomic flip holds end-to-end.
- AC-5: Residue grep (`git grep -ni "global-account-platform|gap-|gap\.|\bGap[A-Z]|gap:up|gap-net|gap\.local|gap\.internal-client"`) returns only legitimate-residue buckets; NO `global-account-platform` remains in live `auth.jwt.issuer` / `JWT_EXPECTED_ISSUER` / `allowed-issuers`.
- AC-6: `.claude/hooks/hardstop-detect.ps1` alias-map edit committed onto this branch before merge.

# Related Specs

- `projects/iam-platform/specs/**` (renamed from global-account-platform)
- 6 × `projects/*/specs/integration/iam-integration.md` (renamed)
- `TEMPLATE.md` § Local Network Convention

# Related Contracts

- `projects/iam-platform/specs/contracts/http/*` (admin-api / console-registry-api — `gap.internal-client` references)
- consumer `application.yml` allowed-issuers contracts (issuer value change)

# Edge Cases

- Two distinct "old name" strings: directory `global-account-platform` (rename) vs user-token `iss` value `global-account-platform` (also rename → `iam`, atomic). Do NOT blind-S&R `global-account-platform` — it would corrupt Flyway comment headers + `tasks/done/**` history.
- Flyway checksum break if any migration file/SQL is edited — exclude `**/db/migration/**`.
- Stale local Docker `gap-*` networks/volumes after rename — `docker compose down -v` + fresh `up` for local devs (CI ephemeral).
- Missed `upload-artifact` `path:` glob → silent empty upload (green-but-masked) — verify each.
- `specs/contracts/**` edits widen CI (forces full e2e) — expected/desirable.

# Failure Scenarios

- **iss flip not atomic** → 401 on every user login (mint `iam` vs validator `global-account-platform` mismatch). Mint + all validators MUST be in the same commit; federation-hardening-e2e is the proof.
- **`.claude` alias edit lands after merge** → HARDSTOP-03 leak detection briefly blind to the project. Bundle into the same PR before merge.
- **Negation reintroduced into CI path-filter** → file misclassified "in" (MONO-074/075 quirk). Keep pure-positive `code-changed` AND only.
