# ADR-MONO-034 — Account/credential unification model (one person → one central identity → consumer account + operator extension; ADR-MONO-032 D5 step 3 / D6-A mechanics)

**Status:** ACCEPTED

**Date:** 2026-06-14

**History:** PROPOSED 2026-06-14 (TASK-MONO-258 — records the **central-identity model + cross-store link mechanism + migration phasing** that ADR-MONO-032 **D5 step 3** ("account/credential unify") + **D6-A** ("one account = one credential = a set of role grants … existing separate accounts are not force-merged — opt-in link") require but leave underspecified. Investigation at the dependency-correct base (`origin/main` `c6d754922`, ADR-032 step 2 complete) found the three identity stores are **three physically separate MySQL databases** with **no shared identity key spanning all three** (§ 1.1): the consumer pair (`auth_db.credentials` ↔ `account_db.accounts`) is already linked by a shared account UUID, but the operator store (`admin_db.admin_operators`) is a **wholly independent identity** — its own UUID space, its own `password_hash`, its own login path (not the SAS form-login), bridged only by the optional, usually-NULL `admin_operators.oidc_subject` pointer. Choosing the unification anchor / link mechanism / migration shape in code would silently bake the identity-storage model (HARDSTOP-09) and risk a forced or silent identity merge (a security hazard D6-A explicitly forbids). **CHOSEN-PROPOSED** direction per the reasoning below (U1 = new central `identities` registry, option A; U2 = link-first step 3 with login/credential consolidation deferred to step 4). Doc-only; ACCEPTED + implementation are separate user-explicit-intent-gated tasks (sibling staged-child pattern, ADR-019/020/021/023/024/032/033). **Self-ACCEPT prohibited.**) · ACCEPTED 2026-06-14 (TASK-MONO-258 — user-explicit *"진행"* after the offered "ADR-034 ACCEPTED 승급" recommendation on the PROPOSED #1538 draft; sibling ADR-033/MONO-257 same-session PROPOSED→ACCEPTED. U1-U7 CHOSEN-PROPOSED direction **finalised byte-unchanged** — ACCEPTED *finalises*, does not re-decide; § 1 Context + § 2 Decision tables + § 3 Consequences + § 4 Alternatives + § 5/§ 7 byte-identical; flip = Status + this clause + § 6 ACCEPTED row + the § 3.3 execution roadmap UNPAUSED. **Delivered in the same PR #1538 as the PROPOSED record** — the user ACCEPTED before the PROPOSED record independently merged, so the staged-child governance trail is preserved *within* the PR (both § 6 rows + ADR-003a audit rows #37 PROPOSED / #38 ACCEPTED) rather than across two PRs, mirroring ADR-033/MONO-257. ADR-032 D1-D6 + ADR-033 S1-S6 not re-litigated.)

**Parent:** [ADR-MONO-032](ADR-MONO-032-unified-identity-roles-model.md) (ACCEPTED 2026-06-14) — the unified-identity model (single account → `roles` set as the sole authorization axis). ADR-032 decided **D1-D6** (the model) and sequenced its execution in **D5** (step 0 contract-first → step 1 dual-read gateways → step 2 roles-only issuance → **step 3 account/credential unify** → step 4 drop legacy → step 5 e2e). Steps 0-2 are complete (contract rewrite MONO-255; dual-read gateways MONO-256; roles-only issuance ADR-033 + BE-368/369/370). ADR-034 resolves **D5 step 3 + D6-A**: *how* the disjoint credential/account stores are unified and linked. ADR-034 does **not** re-decide ADR-032 D1-D6 or ADR-033 S1-S6 — it fills the storage/link/migration gap D6-A left open ("opt-in link" without specifying the link's anchor, mechanism, or staging).

**Sibling:** [ADR-MONO-033](ADR-MONO-033-roles-issuance-resolution-model.md) (ACCEPTED 2026-06-14) — resolved the *roles-source* gap of D5 step 2 (`account_roles` authoritative for the JWT `roles` claim; `admin_operator_roles` kept disjoint). ADR-034 is the *identity/credential* counterpart for step 3 and **reaffirms ADR-033's two-role-namespace disjointness** (U5): unifying the identity does NOT merge the role spaces.

**Decision driver:** ADR-032 D6-A asserts "one account = one credential = a set of role grants" and D5 step 3 says "merge the two-credential model toward one-account-many-roles — opt-in account-link first, no forced merge of existing separate accounts." But the base-state investigation shows the unification is **not a refactor of one store** — it is a reconciliation across **three separate databases** where the genuinely-disjoint identity is the **operator** (`admin_db.admin_operators`): independent UUID space, a **second password store** (`admin_operators.password_hash`), and a **parallel login + token-issuance path** (`OperatorAccessTokenIssuer`, not the auth-service SAS flow). Nothing today prevents the same email existing as a consumer *and* an operator (three separate per-DB `(tenant_id, email)` unique constraints), and there is **no table recording that two identities are the same person** — only the optional `admin_operators.oidc_subject` bridge (a consumer `account_id`, populated by a dev seed / provisioning surface, NULL by default). Resolving "what is the unified-identity anchor, how is an operator linked to it without a forced or silent merge, and how does that land migration-safe across three DBs" is therefore a HARDSTOP-09 architecture decision that must precede the unification code. This ADR is that decision record.

**Related:** [ADR-MONO-032](ADR-MONO-032-unified-identity-roles-model.md) D5/D6 (the parent decision this ADR executes), [ADR-MONO-033](ADR-MONO-033-roles-issuance-resolution-model.md) S2 (the disjoint role-namespace ruling this ADR reaffirms), [ADR-MONO-019](ADR-MONO-019-platform-console-customer-tenant-model.md) (`entitled_domains` keystone — the cross-service / cross-DB value-convention + fail-soft pattern this ADR's link reuses), [ADR-MONO-020](ADR-MONO-020-operator-multitenant-assignment.md) + [ADR-MONO-014](ADR-MONO-014-platform-console-operator-auth-token-exchange.md) (the assume-tenant token-exchange + `oidc_subject` bridge the link formalizes), `platform/contracts/jwt-standard-claims.md` (the consumer-facing claim contract — **unchanged** by this ADR; identity storage is an IdP-internal concern), `projects/iam-platform/apps/account-service/.../AccountJpaEntity.java` (the consumer identity anchor U1 layers `identities` above), `projects/iam-platform/apps/auth-service/.../CredentialJpaEntity.java` + `.../security/CredentialAuthenticationProvider.java` (the credential store + principal-details builder), `projects/iam-platform/apps/admin-service/.../rbac/AdminOperatorJpaEntity.java` (the operator extension U1 links by `identity_id`), `projects/iam-platform/apps/admin-service/.../CreateOperatorUseCase.java` (the operator-provisioning path U4 rewrites), `projects/iam-platform/apps/account-service/.../SignupUseCase.java` (the consumer-provisioning path the operator path will mirror).

---

## 1. Context

### 1.1 The three identity/credential stores are three separate databases (the finding)

| Store | Owner / DB | Identity key | Credential | Login + token path | Cross-store link today |
|---|---|---|---|---|---|
| `credentials` | auth-service / `auth_db` | `account_id` VARCHAR(36) (= `accounts.id`) | `credential_hash` (argon2id) | SAS form-login (`CredentialAuthenticationProvider`) → OIDC tokens | `account_id` = `accounts.id`; `account_type`; unique `(tenant_id, email)` + unique `account_id` |
| `accounts` + `account_roles` | account-service / `account_db` | `accounts.id` VARCHAR(36) UUID (app-minted) | — (delegates to auth-service) | — | `account_roles.account_id` → `accounts.id` (FK, in-DB); JWT `roles` source (ADR-033) |
| `admin_operators` + `admin_operator_roles` | admin-service / `admin_db` | `operator_id` VARCHAR(36) UUID v7 (**independent** space) + `id` BIGINT surrogate | **own** `password_hash` | **own** `OperatorAccessTokenIssuer` (not SAS) | **none structural** — optional `oidc_subject` (a consumer `account_id`; NULL by default) |

Three consequences:

1. **The consumer side is already unified.** `credentials.account_id` = `accounts.id` = `account_roles.account_id` is one identity, one credential, one UUID, provisioned together (§ 1.2). D6-A's "one account = one credential" *already holds* for consumers.
2. **The operator is the genuinely-disjoint identity.** `admin_operators` has its own UUID space, its **own password** (a second credential — exactly the "two-credential model" D5 step 3 names), and a **parallel login + token issuer**. It is not reachable from the consumer identity except via the optional `oidc_subject` pointer (which already powers platform-console's assume-tenant token-exchange — the existing, opt-in seam).
3. **Nothing prevents — or records — one person being both.** The three `(tenant_id, email)` unique constraints live in three separate databases, so the same email can be a consumer *and* an operator with zero conflict, and **no table records that they are the same person**. There is no identity registry; there is only a value-convention shared UUID on the consumer side and a nullable bridge on the operator side.

### 1.2 How identities are provisioned today

- **Consumer** (`account-service.SignupUseCase`): account-service mints the account UUID, writes `accounts` + `profiles` in its local transaction, then **synchronously** calls auth-service `POST /internal/auth/credentials` with the *same* UUID + `account_type=CONSUMER` (idempotent on `(accountId, email)`, TASK-BE-247/330). Atomic-by-rollback (no saga): a credential-write failure rolls back the account insert. Then `publishAccountCreated` (outbox).
- **Operator** (`admin-service.CreateOperatorUseCase`): **entirely self-contained in admin-service** — hashes the password locally, writes `admin_operators` (own `operator_id` UUID + own `password_hash`) + `admin_operator_roles` + an `admin_actions` audit row. **No `credentials` row, no `accounts` row, no auth-service call.** The operator's password lives only in `admin_db`.

So the operator provisioning path is a complete parallel to the consumer one — a separate identity, separate credential, separate login. That parallelism is exactly what step 3 must converge.

### 1.3 What "opt-in link, no forced merge" must mean (the safety constraint)

D6-A is explicit: "existing separate accounts are not force-merged (opt-in link)." The base state makes the *why* concrete: because email uniqueness is per-DB and per-tenant, an automatic "same email ⇒ same person" merge would be a **privilege-escalation vector** — anyone who can register a consumer account with an email that collides with an operator's email would inherit (or be inherited by) the operator identity. The link must therefore be **explicit, authorized, audited, and reversible**, with email-match a *necessary but not sufficient* condition. This ADR makes that the U3 ruling.

### 1.4 The underspecified points this ADR must resolve (HARDSTOP-09)

"Unify the account/credential" (D5 step 3 / D6-A) is underspecified in five ways, each baking the storage model if chosen in code: **(a)** what is the unified-identity *anchor* — reuse the consumer `accounts` UUID, or a new central identity registry, or merge the operator into `accounts`? (U1); **(b)** how far does step 3 go — does it consolidate the operator's separate login/credential now, or only establish the link and defer login consolidation? (U2); **(c)** what is the opt-in link *mechanism* such that no forced or silent merge occurs? (U3); **(d)** how does *new* operator provisioning attach to the unified identity going forward? (U4); **(e)** how does this land across three separate databases, additively, reversibly, with no mis-authorization window? (U6). This ADR resolves all five. The `jwt-standard-claims.md` contract is **not** touched — identity storage is an IdP-internal concern, the claim shape is unchanged (same reasoning as ADR-033 § 2).

---

## 2. Decision

> Direction is **CHOSEN-PROPOSED**; finalised (byte-unchanged) at ACCEPTED. Each decision lists the chosen option + the rejected alternatives. **No code/contract/schema change in this ADR.** The `jwt-standard-claims.md` claim shape is unchanged (identity storage is IdP-internal; the contract is consumer-facing). U1/U2 were selected by the user (2026-06-14, AskUserQuestion "step3 범위" = link-first / "링크 seam" = new central identities registry) after the three-database / disjoint-operator finding was surfaced.

### U1 — unification anchor (the crux): a new central `identities` registry

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Introduce a new central `identities` registry — the canonical per-person identity (`identity_id` UUID, primary email, status, timestamps). Each store gains an `identity_id` reference: `accounts.identity_id`, `credentials.identity_id`, `admin_operators.identity_id`. One identity → a consumer account (existing `accounts` row) + an operator extension (existing `admin_operators` row). The registry is owned by account-service (the existing identity-platform service that already mints the canonical UUID) as a new identity layer *above* `accounts` — NOT a net-new microservice (a dedicated identity-service is a deferred/optional later evolution, U6 note).** | The registry is the single, explicit "these principals are the same person" record the base state lacks. `identity_id` becomes the cross-store correlation key (value-convention across the three DBs — no cross-DB FK is possible; a real FK exists only within `account_db` from `accounts` to `identities`). The operator extension links by `identity_id`, formalizing today's optional `oidc_subject` bridge into a first-class column (backfilled from `oidc_subject` where populated). Roles stay where ADR-033 put them (U5); the registry holds **no roles** — it is pure identity correlation. | **CHOSEN (user-selected)** — gives a genuine single identity registry (the Google-IAM "one identity" the parent ADR models) with an explicit link record, rather than overloading the consumer-scoped `accounts.id` as if it were the person; the registry is additive (backfill one identity per existing account/operator, net-zero, U6) and forward-compatible with a later dedicated identity-service; keeps each store's bounded context intact (account-service owns identity + consumer account, admin-service owns the operator extension) — no cross-DB data move. |
| B. Reuse `accounts.id` as the anchor (no new table); `admin_operators` gains an `account_id` link column. | Smallest migration; the consumer pair already shares this UUID. | **Rejected (user weighed against A)** — overloads a *consumer-account* identifier as the *person* identifier (an operator who is not also a consumer would need a synthetic `accounts` row purely to have an identity, conflating "is a customer" with "is a person"); leaves identity correlation implicit in a column on the consumer table rather than an explicit registry; harder to evolve toward a real identity service. A's explicit registry is the cleaner single-identity model the user chose. |
| C. Merge `admin_operators` rows into `accounts` (operators become accounts with operator-extension columns). | One physical identity table. | **Rejected** — collapses two bounded contexts (account-service identity vs admin-service operator RBAC) into one table and forces a cross-database data move of the operator store; breaks service ownership; far heavier than a link-first step 3 (U2). |

> **U1 sub-decision — registry owner:** account-service hosts `identities` (it already mints the canonical consumer UUID and is the identity-platform anchor). A dedicated net-new *identity-service* is **deferred** (heavier; not required for step 3; recorded as a future evolution in § 3.3). The `identities` table physically lives in `account_db`; `auth_db`/`admin_db` reference `identity_id` by value-convention (the same cross-DB pattern as today's `credentials.account_id`).

### U2 — step-3 scope: link-first; defer login/credential consolidation to step 4

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Step 3 = (1) introduce the `identities` registry + backfill, (2) add `identity_id` references + the opt-in link surface (U3), (3) make NEW operator provisioning attach to a unified identity (U4). Operator login + `admin_operators.password_hash` + the parallel `OperatorAccessTokenIssuer` are LEFT UNCHANGED in step 3 (dual-path preserved). The actual credential/login consolidation, `password_hash` demotion/removal, and the `account_type` column drop are deferred to ADR-032 step 4.** | Step 3 establishes the *identity link* without touching the operator's authentication security path (TOTP/2FA, the admin login flow). Existing operators keep logging in exactly as today; existing consumers unchanged; the link is opt-in (U3); only *new* operator provisioning changes (U4). Each sub-step is additive and reversible. | **CHOSEN (user-selected)** — faithful to D6-A's "opt-in, no forced merge"; smallest blast radius (the operator login + 2FA path is high-risk and is not in scope); lets the heavy login/credential convergence land as a separate, well-isolated step 4 task after the link model is proven; keeps step 3 main-GREEN and reversible. |
| B. Full consolidation in step 3 (migrate operator login to the unified credential — OIDC/SAS + token-exchange — and demote `password_hash` to break-glass, all in step 3). | More complete in one step. | **Rejected (user weighed against A)** — drags the operator login security path + TOTP/2FA into step 3, a much larger and riskier change; couples the identity-link decision (low risk, additive) with the authentication-path migration (high risk); against the staged discipline every prior cross-cutting ADR followed. The convergence is real but belongs in step 4. |

### U3 — opt-in link mechanism: explicit, authorized, audited, reversible (no forced/silent merge)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. A single, explicit link operation associates an existing operator (`admin_operators` row) with an existing unified identity (an `identities` row, typically the person's consumer identity) by setting `admin_operators.identity_id`. It is: authorized (an admin action gated by the appropriate permission), audited (`admin_actions` row), idempotent, and reversible (an explicit unlink that clears `identity_id`). Email-match between the operator and the target identity is a NECESSARY pre-condition surfaced to the actor, but NOT sufficient on its own — the link requires explicit confirmation. No bulk/auto-link runs.** | The link is a deliberate, traceable act. The cross-tenant email-collision escalation vector (§ 1.3) is closed because matching emails never auto-merge; a human (or a scoped automated provisioning step, U4) must confirm. Unlink makes it reversible until step 4. | **CHOSEN** — directly implements D6-A "opt-in link, no forced merge"; closes the silent-merge security hazard; mirrors the existing audited admin-action pattern (`CreateOperatorUseCase` already writes `admin_actions`). |
| B. Auto-link by matching email across the three stores at migration time. | Zero manual effort; "obviously the same person." | **Rejected** — silent identity merge = the § 1.3 privilege-escalation vector (email reuse across tenants / a colliding consumer registration inheriting operator capability); D6-A explicitly forbids forced merge. |
| C. No linking at all — unify only for net-new identities, leave all existing operators permanently un-unified. | Simplest; no link surface. | **Rejected** — defeats the purpose for every *existing* dual-role person (the marketplace MD who already has both a consumer account and an operator account can never converge); D5 step 3's "opt-in link" presupposes a link mechanism exists. |

### U4 — new operator provisioning attaches to a unified identity

| Option | Mechanics | Verdict |
|---|---|---|
| **A. `CreateOperatorUseCase` resolves-or-creates a central `identities` row first, then writes the `admin_operators` extension with `identity_id` set. If an identity with that email already exists in the tenant, it is REUSED via the U3 opt-in confirmation (not silently); otherwise a fresh identity is created. The operator's *credential* is NOT changed in step 3 (per U2 — the operator still authenticates via the admin-service password path until step 4); only the identity link is established at provisioning.** | Every operator provisioned after step 3 is born linked to a central identity, so the divergence stops growing; existing operators converge via the U3 opt-in link on demand. The credential consolidation (making the operator authenticate via the unified credential) is deferred to step 4, consistent with U2. | **CHOSEN** — stops new divergence immediately while honoring the link-first scope (U2); reuses the audited admin-action path; the reuse-on-email-match goes through the same opt-in confirmation as U3 (no silent merge). |
| B. Leave operator provisioning self-contained; link only retroactively. | No provisioning change. | **Rejected** — every new operator created after step 3 would be another un-linked identity, perpetuating exactly the divergence step 3 exists to stop. |

### U5 — identity unification does NOT merge the role namespaces (reaffirm ADR-033)

- **The `identities` registry holds NO roles.** It is pure identity correlation (who is the same person). It carries no `roles`, no permissions, no aud dimension.
- **JWT domain `roles` stay sourced from `account_roles`** (ADR-033 S2), keyed by the consumer `account_id` as today — **step 3 does not re-key `account_roles` to `identity_id`** (that is a deferred option, U6 note). Token issuance is unchanged by this ADR.
- **`admin_operator_roles` stays the admin-console-internal RBAC namespace** (ADR-033 S2 / ADR-002/024), keyed by the operator, gating admin-service/platform-console endpoints — **not** folded into the JWT `roles` claim. Linking an operator to a unified identity does **not** copy admin-console grants into the consumer's domain roles, and does **not** copy domain roles into admin-console RBAC. The two role spaces remain disjoint; ADR-034 unifies *identity*, not *authorization*.

### U6 — migration phasing (within ADR-032 D5 step 3; additive, net-zero, reversible)

| Sub-step | Change | Net effect |
|---|---|---|
| **3a — identities registry + backfill** | New `identities` table in `account_db` (account-service) + `accounts.identity_id` (FK, in-DB) backfilled one-identity-per-account. | Additive; no caller depends on it yet → net-zero. |
| **3b — cross-store `identity_id` references** | Add `identity_id` to `auth_db.credentials` (value-convention) and `admin_db.admin_operators` (value-convention); backfill `credentials.identity_id` from `accounts.identity_id` via the shared `account_id`; backfill `admin_operators.identity_id` from the existing `oidc_subject` where populated (it already holds a consumer `account_id`). | Additive columns; backfill is best-effort (NULL where no bridge exists) → net-zero. |
| **3c — opt-in link/unlink surface** | Audited, authorized, reversible link operation (U3) setting/clearing `admin_operators.identity_id`. | No auto-link → net-zero until invoked. |
| **3d — unified operator provisioning** | `CreateOperatorUseCase` resolves/creates an identity + sets `identity_id` (U4). | Behavior change for NEW operators only; existing untouched. |
| **(deferred → step 4)** | Operator login/credential consolidation onto the unified credential, `admin_operators.password_hash` demotion/removal, `account_type` column drop, and the optional re-keying of `account_roles` to `identity_id`. | Out of scope for step 3 (U2). |
| **(folds into ADR-032 step 5)** | e2e: one identity holding both a consumer role and an operator capability obtains both tokens in one session. | Verification. |

- **No cross-DB FK** — the three stores are three databases; `identity_id` is a value-convention reference across DBs (real FK only within `account_db`), exactly as `credentials.account_id` is today.
- Each sub-step is independently main-GREEN and reversible until step 4 begins the credential consolidation.
- **Deferred (recorded, not decided here):** a dedicated identity-service (vs the account-service-hosted table); re-keying `account_roles` to `identity_id`; making the operator authenticate via the unified credential. All are step-4-or-later concerns.

### U7 — safety invariants

- **No forced or silent merge** (U3) — link is explicit, authorized, audited, idempotent, reversible; email-match is necessary-not-sufficient (closes the § 1.3 cross-tenant escalation vector).
- **Operator authentication is untouched in step 3** (U2) — TOTP/2FA, the admin login flow, and `OperatorAccessTokenIssuer` are unchanged; operator login availability is preserved (dual-path).
- **Operator-specific attributes stay on the operator extension** — `totp_secret_encrypted`, `finance_default_account_id`, `oidc_subject`, status, etc. remain on `admin_operators`; they are NOT moved to `identities` or `accounts`.
- **Role namespaces stay disjoint** (U5) — identity unification ≠ authorization merge.
- **IAM remains the sole issuance authority** (ADR-001); token issuance is unchanged by this ADR (the `identities` registry is consumed by provisioning/link logic, not by the token customizer in step 3).
- **Additive + reversible** (U6) — every step-3 sub-step is net-zero until explicitly invoked, and reversible until step 4.

---

## 3. Consequences

### 3.1 Hard invariants this ADR carries

- **There is, for the first time, an explicit registry recording that two principals are the same person** (`identities` + `identity_id`), replacing the absence of any such record (§ 1.1).
- **The consumer side stays as-is** (already unified); the **operator** gains an opt-in link to a central identity without changing its login/credential in step 3.
- **No forced/silent merge** — the cross-tenant email-collision escalation vector is structurally closed (U3/U7).
- **Identity unification is orthogonal to authorization** — the ADR-033 role-namespace disjointness is reaffirmed, not relaxed (U5).
- **Migration is additive, net-zero, reversible across three separate databases** (U6); contract-unchanged (identity storage is IdP-internal).

### 3.2 What this ADR does NOT do (deferred to ACCEPTED + post-ACCEPTED execution)

- No implementation: no migrations, no registry table, no link surface, no provisioning change, no e2e — all post-ACCEPTED execution tasks (§ 3.3).
- No operator login/credential consolidation, no `password_hash` removal, no `account_type` column drop (deferred to ADR-032 step 4, U2).
- No re-keying of `account_roles` to `identity_id`; no folding of `admin_operator_roles` into the JWT `roles` claim (U5).
- No dedicated identity-service (the registry is account-service-hosted; a dedicated service is a deferred evolution, U1 sub-decision).
- No `jwt-standard-claims.md` change (claim shape unchanged; identity storage is IdP-internal).
- No forced merge of existing consumer/operator account pairs (U3 opt-in only).

### 3.3 Future-self (post-ACCEPTED execution roadmap — sketch, finalised at ACCEPTED)

1. **`TASK-…`** (U6 step 3a, S2-style additive) — `identities` registry table (`account_db`) + `accounts.identity_id` (FK) + one-identity-per-account backfill + account-service internal resolve/read. Model = **Opus** (identity-storage schema).
2. **`TASK-…`** (U6 step 3b) — `identity_id` columns on `auth_db.credentials` + `admin_db.admin_operators` (value-convention) + backfill (`oidc_subject` → `identity_id`). Model = **Opus**.
3. **`TASK-…`** (U6 step 3c, U3) — opt-in audited link/unlink surface (admin action; email-match necessary-not-sufficient; reversible). Model = **Opus** (security-sensitive identity link).
4. **`TASK-…`** (U6 step 3d, U4) — `CreateOperatorUseCase` resolve/create identity + set `identity_id`. Model = **Opus**.
5. **(deferred → ADR-032 step 4)** operator login/credential consolidation onto the unified credential + `password_hash` demotion + `account_type` column drop + optional `account_roles` re-key to `identity_id`. Model = **Opus**.
6. **(folds into ADR-032 step 5)** e2e: one identity, both tokens, one session (now also asserting the link). Model = **Sonnet**.
- **Optional follow-ups:** a dedicated identity-service (re-home the `identities` registry); `account_roles` re-keyed to `identity_id`; a self-service "link my accounts" UX (a user-initiated variant of the U3 admin link, with email-verification as the necessary-not-sufficient gate).

---

## 4. Alternatives Considered

- **Reuse `accounts.id` as the identity anchor (U1-B).** Rejected (user weighed against the central registry) — overloads a consumer-account id as the person id; implicit correlation; harder to evolve toward an identity service.
- **Merge `admin_operators` into `accounts` (U1-C).** Rejected — collapses bounded contexts; cross-DB data move; far heavier than link-first step 3.
- **Full credential/login consolidation in step 3 (U2-B).** Rejected (user weighed against link-first) — drags the operator authentication + TOTP path into step 3; couples low-risk identity-link with high-risk auth-path migration; belongs in step 4.
- **Auto-link by email (U3-B).** Rejected — silent identity merge = cross-tenant privilege-escalation vector; D6-A forbids forced merge.
- **No linking, net-new only (U3-C).** Rejected — leaves every existing dual-role person permanently un-unified.
- **Re-key `account_roles` to `identity_id` now.** Rejected for step 3 — token issuance (ADR-033) is keyed on `account_id` and works; re-keying is a deferred optimization, not required for the identity link.
- **Dedicated identity-service now.** Rejected for step 3 — a net-new service heavier than the link-first scope; the account-service-hosted registry is forward-compatible with extracting one later.

## 5. Relationship to ADR-032 / ADR-033 / the contract / the RBAC family

| | ADR-MONO-032 | ADR-MONO-033 | `jwt-standard-claims.md` | `admin_operator_roles` (ADR-002/024) |
|---|---|---|---|---|
| Relationship | **Child** — executes D5 step 3 + resolves the D6-A account/credential-unify gap; does not re-decide D1-D6 | **Sibling** — ADR-033 resolved the step-2 *roles-source* gap; ADR-034 resolves the step-3 *identity/credential* gap; ADR-034 **reaffirms** ADR-033's role-namespace disjointness (U5) | **Unchanged** — identity storage is IdP-internal; the claim shape is not touched (same reasoning as ADR-033) | **Keeps disjoint** — admin-console RBAC stays admin-service-internal; identity link does NOT merge it into the JWT `roles` claim |

## 6. Status Transition History

| Date | Transition | Decision summary | Trigger | PR |
|---|---|---|---|---|
| 2026-06-14 | created PROPOSED | U1 = new central `identities` registry (canonical per-person identity; each store references `identity_id`; account-service-hosted, dedicated identity-service deferred); reject reuse-`accounts.id` (overloads consumer id) + merge-into-accounts (collapses contexts). U2 = link-first step 3 (registry + link + unified new-operator provisioning); defer operator login/credential consolidation + `password_hash` removal + `account_type` drop to ADR-032 step 4 (reject full-consolidation-in-step-3). U3 = opt-in, authorized, audited, reversible link; email-match necessary-not-sufficient (reject auto-link = escalation vector; reject no-link). U4 = new operator provisioning resolves/creates an identity + sets `identity_id`. U5 = identity unification does NOT merge role namespaces (`identities` holds no roles; `account_roles`/`admin_operator_roles` disjoint per ADR-033). U6 = additive net-zero migration across three DBs (3a registry+backfill → 3b cross-store `identity_id` → 3c link surface → 3d unified provisioning; login/credential consolidation deferred to step 4). U7 = safety invariants (no forced/silent merge; operator auth untouched in step 3; role namespaces disjoint). **Child of ADR-032 (D5 step 3 / D6-A); sibling of ADR-033.** No contract change. | User-explicit selection of the link-first scope + central-identities anchor (2026-06-14, AskUserQuestion "step3 범위" = 링크-우선 / "링크 seam" = 신규 중앙 identities 테이블/서비스) after the three-database / disjoint-operator finding was surfaced at base `c6d754922` | #1538 |
| 2026-06-14 | PROPOSED → ACCEPTED | U1-U7 CHOSEN-PROPOSED direction **finalised byte-unchanged** (ACCEPTED *finalises*, does not re-decide); § 1 Context + § 2 Decision tables + § 3 Consequences + § 4 Alternatives + § 5 Relationship + § 7 Provenance byte-identical to the PROPOSED commit; flip = Status + History ACCEPTED clause + this row + § 3.3 execution roadmap UNPAUSED. Child-of-ADR-032 / sibling-of-ADR-033 scope unchanged (D1-D6 / S1-S6 not re-litigated). Delivered in the same PR #1538 as PROPOSED (user ACCEPTED before the PROPOSED record independently merged); governance trail preserved in-PR (both § 6 rows + ADR-003a audit rows #37/#38), mirroring ADR-033/MONO-257. | "진행" (TASK-MONO-258 — user-explicit intent after the offered "ADR-034 ACCEPTED 승급" recommendation; sibling ADR-033/MONO-257 same-session PROPOSED→ACCEPTED) | #1538 |

> **ACCEPTED 2026-06-14 (TASK-MONO-258).** The § 3.3 execution roadmap is now **UNPAUSED**; the execution steps proceed dependency-correct from this ACCEPTED main, beginning with the `identities` registry + backfill (U6 step 3a). Each remains a separate task; U1-U7 are finalised and not re-litigated at execution. **ADR-032 D1-D6 and ADR-033 S1-S6 are not re-litigated here** — ADR-034 only fills the D5-step-3 / D6-A account/credential-unification gap. The operator login/credential consolidation + `password_hash` removal + `account_type` column drop remain **deferred to ADR-032 step 4** (U2 link-first scope).

## 7. Provenance

- `origin/main` `c6d754922` investigation (ADR-032 step 2 complete): **three separate MySQL databases** — `auth_db.credentials` (`V0001` create + `V0006` email + `V0007` tenant_id/`uk_credentials_tenant_email` + `V0022` `account_type`; PK `id` BIGINT, unique `account_id`, unique `(tenant_id,email)`); `account_db.accounts` (`V0001` + `V0010` tenant_id + `V0013` `uk_accounts_tenant_id_id`) + `account_db.account_roles` (`V0013` composite PK `(tenant_id,account_id,role_name)`, FK → `accounts(tenant_id,id)`); `admin_db.admin_operators` (`V0007` BIGINT PK + `operator_id` UUID v7 + own `password_hash` + `V0025` tenant_id + `V0027` `oidc_subject` + `V0029` `finance_default_account_id`) + `admin_db.admin_operator_roles` (PK `(operator_id BIGINT, role_id)` → normalized `admin_roles`).
- Linking finding: `credentials.account_id` = `accounts.id` = `account_roles.account_id` (one consumer identity); `admin_operators` independent UUID space + own credential + own `OperatorAccessTokenIssuer` (not SAS); only bridge = nullable `admin_operators.oidc_subject` (a consumer `account_id`, dev-seeded `V0028`, NULL by default). Three per-DB `(tenant_id,email)` unique constraints → same email can be consumer + operator with no conflict and no same-person record.
- Provisioning: `account-service.SignupUseCase` (mint UUID → `accounts`+`profiles` → sync `POST /internal/auth/credentials` idempotent → outbox) vs `admin-service.CreateOperatorUseCase` (self-contained: local `password_hash` + `admin_operators` + `admin_operator_roles` + `admin_actions`; no auth/account call).
- `CredentialAuthenticationProvider` principal details map = `{tenant_id, tenant_type, account_type, account_id}` (HashMap for SAS Jackson allowlist).
- Flyway next-free versions: auth-service **V0025**, account-service **V0023** (below V9000 dev band), admin-service **V0036** (14/23/28 taken by `db/migration-dev/`).
- ADR-MONO-032 D5 step 3 ("account/credential unify — opt-in account-link first, no forced merge") + D6-A ("one account = one credential = a set of role grants … existing separate accounts are not force-merged").
- ADR-MONO-033 S2 (the two-role-namespace disjointness this ADR reaffirms) + ADR-MONO-019 `entitled_domains` keystone (cross-DB value-convention + fail-soft) + ADR-MONO-020/014 (`oidc_subject` + assume-tenant token-exchange — the existing opt-in seam U1/U3 formalize).

분석=Opus 4.8 / 구현=Opus 4.8 (account/credential-storage unification across three separate databases under HARDSTOP-09; child of the ADR-032 unified-identity model executing D5 step 3 / D6-A; opt-in link with a no-silent-merge security constraint; staged-child ADR pattern per ADR-019/020/021/023/024/032/033; link-first scope deferring the operator login/credential consolidation to ADR-032 step 4).
