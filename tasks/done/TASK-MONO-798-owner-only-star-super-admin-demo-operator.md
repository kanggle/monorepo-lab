# TASK-MONO-798 — seed one owner-only, unconfined `'*'` SUPER_ADMIN demo operator (IdP login, SUSPENDED by default)

- **Status**: done
- **Type**: feat (demo infra + iam dev seed)
- **Created**: 2026-10-17 UTC
- **Scope class**: monorepo-level (`infra/demo/**`, `.github/workflows/ci.yml`) + `projects/iam-platform/` dev seeds and tests (one atomic PR)

# Owner decision (2026-10-17 UTC — verbatim)

> seed ONE owner-only, unconfined `'*'` SUPER_ADMIN operator with an IdP login on the demo, in this exact form:
> 1. Unconfined `'*'` scope, IdP (auth-service credential) login, NOT listed in README / demo guide / public seed docs (public demo accounts stay as they are; `platform@demo.com` stays confined to `fan-platform` per TASK-MONO-751).
> 2. No fixed password in any seed or repo file: generated at boot, stored in SSM Parameter Store as a SecureString (same pattern as the mailpit basic-auth credential — find how `/portfolio-demo/mailpit/ui-basicauth-users` is generated/stored and the demo instance role's SSM permissions; add only what is needed, least privilege).
> 3. Seeded in SUSPENDED state; an owner-run wrapper (`infra/demo/...` script, run over SSM like the 28th-window wrappers) turns it ACTIVE for a measurement window and back to SUSPENDED afterwards (and reports the state). Measurements read the password from SSM; it is never printed.
>
> Purpose: makes TASK-MONO-672 «Still open» items 1–4 measurable ('*' × PENDING/SUSPENDED assume refusal, AC-6 subscription cancel, 795 wildcard card, 773 /tenants screen). It rides the NEXT bake (seeds are baked into the AMI).

# Goal

Make `TASK-MONO-672` § Order «Still open» 1–4 measurable in the next window, without adding a public credential:
one `'*'` SUPER_ADMIN with an IdP login whose password exists only in SSM, generated on the instance, and which is
closed (SUSPENDED) except while the owner holds a window open.

# Scope

- `projects/iam-platform/apps/admin-service/src/main/resources/db/migration-dev/R__seed_demo_star_owner_operator.sql` (new) — the row.
- `…/migration-dev/R__seed_zz_demo_domain_permission_sets.sql` — the `'*'` legacy-compat row for the new operator (same shape as `demo-platform`).
- `infra/demo/owner-operator.sh` (new) — `provision` (boot) · `activate` · `deactivate` · `status` · `--check-undisclosed` · `--self-test`.
- `infra/demo/OwnerOperatorHash.java` (new) — the boot-time hash, by the login path's own hasher.
- `infra/demo/demo-up.sh` — one `post_up_call` in the iam block + one summary line.
- `infra/demo/aws/terraform/main.tf` — `ssm:PutParameter` on exactly one parameter ARN.
- `.github/workflows/ci.yml` — the self-test step in `demo-wrapper-smoke`; a new `owner-operator-undisclosed` job (not required).
- Tests: admin-service `DemoOperatorSeedIntegrationTest` (4 new cases + the legacy-compat map); auth-service `DemoStarOwnerOperatorSeedTest` (new).
- `tasks/ready/TASK-MONO-672-*.md` — the next-window line and the ❓ → decided.
- **Not** in scope: README / demo guides / launcher page (by decision — the guard keeps them clean), any app code, contracts, the public demo accounts.

# Acceptance Criteria

- **AC-0 — measure how it is done today before designing** (recorded below, § AC-0 findings).
- **AC-1 — the row**: `demo-star-owner` · home `'*'` · `confined_tenant_id` NULL · `SUPER_ADMIN` at `'*'` · no membership · `password_hash` NULL · status `SUSPENDED` · `oidc_subject` = the account id the boot step writes. A re-run of the seed resets status to `SUSPENDED` (never to `ACTIVE`). Pinned by `DemoOperatorSeedIntegrationTest` (SUSPENDED → admission and assume gate refuse; ACTIVE → admitted and may assume `ecommerce` / `demo-corp` / `fan-platform`).
- **AC-2 — no fixed password**: no repo file holds a password or hash for this account; no auth-service dev seed names it. The credential is written at boot with an Argon2id hash computed on the instance by `Argon2idPasswordHasher` (the login path's class). Pinned by `DemoStarOwnerOperatorSeedTest` (incl. compiling `OwnerOperatorHash.java` and verifying its output with a fresh hasher) and self-test (1).
- **AC-3 — boot provisioning, fail-closed, never printed**: once per boot (boot_id marker); forces SUSPENDED + revokes admin refresh tokens; SSM put (SecureString, `--overwrite`, value from a 0600 file) **before** the credential upsert; a refused put leaves the credential unchanged and says what to apply; the password is in no stdout / stderr / argv / SQL. Pinned by self-test (2)–(5), (6e).
- **AC-4 — owner wrapper**: `activate` → ACTIVE + report; `deactivate` → SUSPENDED + refresh tokens revoked + report; `status` read-only; none of them touches SSM. Self-test (6).
- **AC-5 — not in public docs**: `owner-operator.sh --check-undisclosed` (CI job `owner-operator-undisclosed`) — the email · operator id · account id appear in no tracked file outside the allowlist; bites a synthetic README / guide (self-test (8)).
- **AC-6 — least privilege**: the instance role gains only `ssm:PutParameter` on `parameter/portfolio-demo/owner-operator/password` (no Get, no wildcard, no kms). No terraform-managed value (nothing in state).
- **AC-7 — no regression**: CI lanes green from their logs; `nightly-e2e.yml` and `federation-hardening-e2e.yml` dispatched on the branch show no new failure versus `main`; demo guards (`verify-demo-wrapper.sh` static, the three required guards + `check-required-check-names.sh`) pass on a staged tree.
- **AC-8 — handoff**: `TASK-MONO-672` gets the next-window line «once baked: activate via wrapper → measure items 1–4 → deactivate» and its ❓ becomes «decided 2026-10-17: seeded owner-only, SUSPENDED by default».

# AC-0 findings (2026-10-17 UTC, measured in the tree at `492fe0619`)

1. **How demo operators are seeded.** admin-service `db/migration-dev/R__seed_demo_*.sql` (repeatable, loaded in the default · e2e · test profiles; prod is `db/migration` only): `admin_operators` row with `INSERT … ON DUPLICATE KEY UPDATE`, roles in `admin_operator_roles`, memberships in `operator_tenant_assignment`; `R__seed_zz_demo_domain_permission_sets.sql` gives each seeded operator its legacy-compat set row (`demo-platform` → `'*'`). The login half is an auth-service `db/migration-dev/R__seed_demo_*_credential.sql` row in tenant `iam` with a **literal Argon2id digest of the published demo password** (`Demo1234!`) — that is the «fixed password» this decision rules out, so this operator gets **no** credential seed.
2. **`platform@demo.com` confinement**: `admin_operators.confined_tenant_id = 'fan-platform'` (V0046), checked in `OperatorAssignmentCheckUseCase` step 1b before the `'*'` step. The new row leaves it NULL.
3. **Dev `admin` / dev1 `'*'` rows**: V0014 / V0023 (fixed Argon2id `devpassword123!` in `password_hash` — the break-glass `/api/admin/auth/login` path) and V0028 (placeholder `oidc_subject`, no auth credential). The new row's `password_hash` is NULL so the break-glass path has no way in.
4. **SUPER_ADMIN 2FA on the demo**: `db/migration-demo/R__demo_relax_super_admin_require_2fa.sql` (OD-5), loaded only by `infra/demo/iam-traefik.override.yml`; `DemoOnlyRequire2faRelaxationTest` pins it. The new SUPER_ADMIN rides that relaxation; nothing new is relaxed.
5. **Where boot secrets come from.** 🔴 The mailpit credential is **not generated at boot**: the owner registers it once (`htpasswd -nbB …` → `aws ssm put-parameter --type SecureString`, `infra/demo/aws/README.md` § 데모 메일함) and `infra/demo/fetch-oauth-secrets.sh` only **reads** it at boot; the instance role has only `ssm:GetParameter` on `parameter/portfolio-demo/mailpit/*` (`main.tf` `aws_iam_role_policy.ec2_health`), and that SecureString read works without any kms grant (AWS-managed `alias/aws/ssm`). So «same pattern» holds for the store (SecureString, owner reads with own credentials, terraform holds no value), while «generated at boot» needs the one thing mailpit does not have: a **write** permission for the instance — added as `ssm:PutParameter` on one ARN. Nothing else under `infra/demo` generates secrets at boot (`provision-demo-env.sh` copies `.env.example` defaults; user-data only triggers the unit; packer bakes no secret).
6. **Status semantics**: `OperatorConsoleAdmission.admits` = `status = ACTIVE ∧ ('*' ∨ membership)`; `OperatorAssignmentCheckUseCase` refuses non-ACTIVE. 🔴 Admin refresh does **not** re-check status (`AdminRefreshTokenService.refresh`), which is why the API suspend path (`PatchOperatorStatusUseCase`) revokes refresh tokens — `deactivate` does the same (`revoke_reason = FORCE_LOGOUT`). An operator access token already minted stays valid to its `exp` (`ADMIN_JWT_ACCESS_TTL`, default 3600 s) — same as the API path; recorded, not changed.
7. **Hash tooling on the host**: credentials are Argon2id only (`CredentialHash.argon2id`), hashed by `libs/java-security` `Argon2idPasswordHasher` (password4j 1.8.2, needs slf4j-api). The AMI has `openjdk-21-jdk-headless` + `unzip` (packer step 1) and keeps the baked boot jars in the clone (step 3); the boot jar carries `BOOT-INF/lib/{password4j-1.8.2,slf4j-api-2.0.16,java-security}.jar` (measured on a local `bootJar`). The runtime containers are JRE-only (no source launcher), so the hash runs on the host JDK. Measured locally: helper prints `$argon2id$v=16$m=65536,t=3,p=1$…`, rc=2 on a short input.
8. **Ids**: next free account id `0199de70-0000-7000-8000-00000000ad09` (ad03–ad08 used); operator id `demo-star-owner` (grep: `demo-owner` collides with seed-finance's `demo-owner-a`).

## Design choices beyond the three points (none changes what the owner decided)

- **Rotation = once per boot** (boot_id marker in `/run`, the `TASK-MONO-753` shape): every boot = a new password + SUSPENDED; a later per-domain start in the same boot does nothing (otherwise a visitor's «start fan» would rotate and suspend in the middle of an owner window).
- **SSM before credential**: a failed put leaves the DB as it was (fail-closed).
- **Seed in `db/migration-dev`** like every demo operator (so CI and dev DBs also hold a SUSPENDED, credential-less row — inert), not `db/migration-demo` (an IT that loads that location would also relax 2FA for every other test in the shared container).
- **legacy-compat `'*'` set row**, as `demo-platform` has — without it an assumed token carries no domain role and item 2 («re-exchange carries no role of that domain») has nothing to lose.
- **provision failure does not fail the unit** (owner-only tool; one summary line instead).

# Related Specs

- `platform/deployment-policy.md` (IaC only, no secret in repo).
- `projects/iam-platform/specs/services/admin-service/security.md` § Second-Factor Requirement (OD-5 relaxation stays demo-only).
- `TASK-MONO-751` (confinement of `platform@demo.com` — unchanged), `TASK-MONO-771` S4 (OD-5).

# Related Contracts

None changed. The seed uses existing tables; no API or event change.

# Edge Cases

- Instance stopped by the idle timer while ACTIVE → next boot forces SUSPENDED (step 2 of provision).
- Two `demo-up.sh` runs in one boot (MONO-753) → the second is a no-op.
- `terraform apply` not yet run → put `AccessDenied` → credential unchanged; warning names the policy.
- An AMI without the baked boot jar (e.g. a pulled checkout) → hash refused, nothing written.
- Local dev / CI harness (`DEMO_DOMAIN=local`) → `provision` skips (also keeps `verify-demo-wrapper.sh` shimmed harnesses from waiting).
- Long-lived dev DB: the R__ re-run inserts the row SUSPENDED; no credential exists outside the demo host.

# Failure Scenarios

- Hash format not accepted by the login path → caught by `DemoStarOwnerOperatorSeedTest` (verifies with a fresh `Argon2idPasswordHasher`) and by the helper's own verify before printing.
- The identity drifts between the seed and the script → self-test (1) and the auth test (link key) go red.
- Someone lists the operator in a guide → `owner-operator-undisclosed` job red.
- The new SUSPENDED row breaks an existing count-based test or e2e → AC-7 runs.

# Implementation record

- `4bcc07436` (2026-10-17 UTC) — seed + wrapper + hash helper + terraform + ci + tests. Local: wrapper self-test 34/34; five mutations each turned it red (put failure ignored · value on argv · no once-per-boot guard · seed `ACTIVE` · password echoed); auth `DemoStarOwnerOperatorSeedTest` 3/3; real helper run against the jars of a local `auth-service.jar` bootJar → `$argon2id$v=16$…`, rc=2 on a short input.
- 🔴 **First push was red** — CI `38987335186` «Demo wrapper smoke» and nightly `38987347441` «Demo compose image liveness», both `FAIL: (z15) 대조군 실패 — 표면이 전부 200 인데 rc=1` (main green at `492fe0619`). Cause measured from the log: `provision` ran right after the OIDC registration, waited its whole budget on the harness's docker shim (a query that succeeds and returns nothing), and the domain seeds / drift / suppression checks were skipped as «post-up budget exhausted» → rc=1. Federation `38987350141` was green.
- `b76b5d941` — fix: the step is the **last** post-up step, is not a `post_up_call`, uses only the post-up budget left (≤ 180 s, no new time), never feeds `final_rc`; a query that returns no number bails at once (a failed query still waits, default 300 → 120 s). Self-test (5b) — 35/35.
- After the fix (2026-10-18 UTC): CI `39110094469` **success** (43 pass · 27 skipping · 0 fail; `Demo wrapper smoke` log has `(z15)` and no FAIL, `[self-test] pass=35 fail=0`; `Owner-only demo operator is in no public doc` success; iam integration ran the four `798:` cases PASSED; `auth-service:test` ran in Build & Test). Nightly `39110115364` **success**, all 15 jobs. Federation: `38987350141` success on `4bcc07436`; not re-run on `b76b5d941` (the fix touches only `infra/demo/demo-up.sh` / `owner-operator.sh`, which federation does not exercise).
- Guards on the staged tree: index-queue-drift · task-id-collision · walkthrough-ledger-drift · required-check-names · outside-module-input-filter-coverage · artifact-retention · libs-ci-coverage · ls-files-guard-count · dev-seed-migration-band · flyway-version-collision — all rc=0. Nothing added or deleted under `scripts/`.
- ⚪ Not measured here: the live boot (needs a bake + `terraform apply`), the SSM put from the instance role (the kms path is inferred from the oauth SecureString read, not measured), an actual IdP login with the generated password.

# Live record (29th window, 2026-10-20 UTC — `TASK-MONO-672` § 29th window — harvest)

All three ⚪ above are now measured, on `ami-01681ef55def7d15c` (`RepoCommit` `e44142d34`, merged #4404 is an ancestor):

- **Live boot (AC-3)** ✅ — the provision step ran as the last post-up step: `[owner-operator] provisioned: new password in SSM /portfolio-demo/owner-operator/password (SecureString) · credential updated · operator SUSPENDED`, then `[demo] up complete`. `status`: SUSPENDED · credential present 06:24:06Z · 0 unrevoked refresh tokens · «password provisioned this boot: yes». Once per boot held: the second boot of the day (09:23Z, after an idle stop with the operator `ACTIVE`) came up **SUSPENDED** with a new credential (09:25:14Z) — Edge Case 1, live.
- **SSM put from the instance role (AC-6)** ✅ — parameter `SecureString`, version 1, LastModified 06:24:06Z. The plan showed the policy change as exactly one `ssm:PutParameter` on that one ARN (no Get, no wildcard, no kms statement) and the put succeeded, so the AWS-managed key path works without a grant — **measured, no longer inferred**.
- **IdP login with the generated password (AC-1 · AC-4)** ✅ — after the owner's `activate` (wrapper output: `ACTIVE — measurement window open` + status ACTIVE): `star-owner@demo.com` logged in through `platform-console-web` PKCE (password from SSM into a 0600 file, never printed) → base token `sub` `0199de70-…-ad09` · `tenant_id=iam`; admin-service exchange → `sub=demo-star-owner` `token_type=admin`; assume `'*'` → 200; it then created / suspended tenants as `SUPER_ADMIN` (672 item 1).
- ⚪ `deactivate` was **not** run live (the reboot forced SUSPENDED before it was needed) — covered by self-test (6) only.

What it was for: 672 «Still open» 1 and 3 closed in the same window; 2 and 4 stay with 672 (the owner's second `activate` did not come). That is 672's list, not an AC of this ticket.
