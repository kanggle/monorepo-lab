# Task ID

TASK-MONO-260

# Title

Author **ADR-MONO-035 PROPOSED** — Operator authentication unification + operator domain-role JWT issuance (ADR-MONO-032 D5 **step 4** mechanics). Records (a) the **operator JWT-domain-role source** that unblocks the `account_type` drop, and (b) the **operator login/credential consolidation** ADR-MONO-034 **U2** deferred to step 4. Executing ADR-032 D5 step 4 against base `9d4e878c3` (ADR-034 step 3 complete) surfaced a blocking gap none of D5/U2 named: console/federation operators carry **no JWT `roles`** and pass the domain gateways **only** via the legacy `account_type=OPERATOR` dual-read leg (no `account_roles` seeded for operators; `RoleSeedPolicy.seed('gap',…)=[]` since `platform-console-web` is `tenant_id='gap'`; assume-tenant preserves an empty base-token role set). So `account_type` **cannot be dropped** without first giving operators a JWT domain-role source — and "where operators' JWT domain roles come from" is a HARDSTOP-09 decision (ADR-033 S2 kept `admin_operator_roles` disjoint from the JWT claim). The deferred login/credential consolidation additionally touches the operator **auth/TOTP security path** = HARDSTOP-09. Doc-only; **ACCEPTED + implementation are separate user-explicit-intent-gated tasks** (sibling ADR-019/020/021/023/024/032/033/034 staged-child pattern). **Self-ACCEPT prohibited.**

# Status

done

> **완료 (2026-06-14, close-chore TASK-MONO-264)**: ADR-MONO-035 ACCEPTED, PR #1572 squash `9ba66e6d3`, 3-dim verified (state=MERGED, mergeCommit=origin/main tip, doc-only path-filter SKIPPED+SUCCESS). ready→done.
>
> **PROPOSED → ACCEPTED delivered in this PR (2026-06-14), user-explicit ACCEPT gate honored.** The PROPOSED O1-O6 decisions were **presented for review first** (the explicitly-required gate, self-ACCEPT prohibited); the user then gave explicit intent *"진행"* → ADR-MONO-035 flipped PROPOSED → ACCEPTED in the same PR (Status + § 6 ACCEPTED row + § 3.3 UNPAUSED), ADR-003a audit rows #39 (PROPOSED) + #40 (ACCEPTED) appended (sibling ADR-033/MONO-257 + ADR-034/MONO-258 same-PR pattern). Authored worktree-isolated on `task/mono-260-operator-auth-unification-adr` (main parked). Doc-only (no `apps/` code, no `platform/contracts/` change, no migrations). **Lifecycle close (ready → done) follows merge + 3-dim verification** (sibling MONO-258's #1541 close-chore pattern). § 3.3 execution roadmap UNPAUSED — next = 4a operator domain-role derivation (O1, the `account_type`-drop unblocker), iam BE-376+ (avoid BE-375 held by the concurrent ecommerce session).

# Owner

architecture

# Task Tags

- docs
- adr
- security

---

# Dependency Markers

- **child of**: ADR-MONO-032 (ACCEPTED — unified-identity model). ADR-035 executes ADR-032 **D5 step 4** ("drop legacy `account_type`") and resolves its two open mechanics; it does NOT re-decide ADR-032 D1-D6.
- **sibling of**: ADR-MONO-033 (ACCEPTED — roles-issuance) — ADR-035 **refines S4** for the operator assume-tenant path (preserve-from-base → derive-from-selected-tenant-domain, because the base operator token structurally has no domain-role set to preserve) and reaffirms S2/S5.
- **sibling of**: ADR-MONO-034 (ACCEPTED — account/credential unification) — ADR-035 **completes U2** (the deferred operator login/credential consolidation) + **U6 step 3b** (the `credentials.identity_id` leg re-sequenced to step 4) and reaffirms U5.
- **triggered by**: base-state investigation at `9d4e878c3` (operators ride the `account_type` leg with no JWT role source) + user-explicit steer 2026-06-14 (AskUserQuestion "재시퀀싱" = "B 먼저, ADR-035 범위확장"). Implementing step 4 without recording the operator-role-source + login-convergence decision would bake the operator-auth model (HARDSTOP-09).
- **finalizes** `platform/contracts/jwt-standard-claims.md` `account_type` removal (Deprecated → removed) **at execution (4b)** per § Change Rule — the only one of ADR-033/034/035 that touches the contract (the body was prepared at step 0, MONO-255).
- **keeps disjoint**: `admin_operator_roles` (admin-console RBAC) — the operator domain role is **derived** from the assignment + selected-tenant domain, never copied from admin RBAC (O1-C rejected).
- **defers (follow-ups)**: `password_hash` full removal; per-assignment operator domain roles (O1-B); `account_roles` re-key to `identity_id`; dedicated identity-service; OIDC-side operator step-up auth (ADR-032 D4-B).

# Goal

Publish ADR-MONO-035 PROPOSED so ADR-032 D5 step 4 can be implemented against a recorded decision — (O1) operators get a JWT domain-role source by deriving it at assume-tenant from the selected tenant's entitled domains (the operator-role mirror of `RoleSeedPolicy`), and (O2) the two operator credentials converge on the unified OIDC credential with `admin_operators.password_hash` demoted to break-glass — rather than an implicit one, with ACCEPTED and execution gated as separate user-explicit-intent tasks.

# Scope

- `docs/adr/ADR-MONO-035-operator-auth-unification-model.md` (NEW, Status PROPOSED) — O1 operator JWT-domain-role source (derive at assume-tenant from the selected tenant's entitled domains; reject per-assignment roles [deferred] + admin_operator_roles→JWT mapping [disjointness violation]) + O2 operator login/credential convergence (unified OIDC credential, demote `password_hash` to break-glass; reject keep-two + big-bang) + O3 `credentials.identity_id` (ADR-034 3b deferred leg) + O4 TOTP/attributes stay admin-service-internal + O5 staged step 4 (4a operator roles → 4b `account_type` drop → 4c credential convergence → 4d `identity_id`; 4a-before-4b ordering invariant) + O6 safety invariants.
- `docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md` § 3 audit table — append row #39 (Meta-policy: ADR-035 PROPOSED publish; same one-off pre-author category as rows #13/#18/#22/#25/#27/#29/#31/#33/#35/#37; does NOT add to § D1). Row #40 (ACCEPTED) appended at the gated ACCEPTED transition.
- Doc-only. NO contract/schema/code change (HARDSTOP-09 remediation: record the decision, PAUSE until ACCEPTED).

# Acceptance Criteria

- **AC-1** ADR-MONO-035 exists with Status PROPOSED, O1-O6 CHOSEN-PROPOSED, the operator-no-JWT-roles finding evidence (§ 1.1: seed=[] for `gap`, no `account_roles` for operators, assume-tenant preserve empty), and the § 3.3 execution roadmap.
- **AC-2** The decision driver names the concrete gap (operators admit only via the `account_type` leg; ADR-033 made `admin_operator_roles` disjoint; `account_roles` empty for operators) + the deferred-login-consolidation HARDSTOP-09 + the user-explicit steer (B-first / expand ADR-035 scope).
- **AC-3** O1-B (per-assignment roles), O1-C (admin_operator_roles→JWT), O2-B (keep two passwords), O2-C (big-bang), and "drop `account_type` before operators have roles" are recorded as rejected with reasons (O1-C names the ADR-033/034 disjointness; the ordering one names the mis-auth window).
- **AC-4** ADR-035 positions itself as a **child of ADR-032** (executes D5 step 4; does NOT re-decide D1-D6), a **sibling of ADR-033** (refines S4 for the operator assume-tenant path), and a **sibling of ADR-034** (completes U2 + U6-3b; reaffirms U5), and records that it **finalizes** the `jwt-standard-claims.md` `account_type` removal at 4b.
- **AC-5** ADR-003a § 3 audit row #39 (PROPOSED) appended (append-only; rows #1-#38 byte-unchanged).
- **AC-6** Doc-only diff (no `apps/` code, no `platform/contracts/` change, no migrations).
- **AC-7 (gated)** ADR-035 lands as PROPOSED first; the ACCEPTED transition (Status ACCEPTED + § 6 ACCEPTED row + § 3.3 UNPAUSED + ADR-003a row #40) is a **separate step gated on user-explicit intent** — NOT a same-PR self-flip. **Self-ACCEPT prohibited** (the user explicitly required the gate).

# Related Specs

- `docs/adr/ADR-MONO-032-unified-identity-roles-model.md` (§ D5 step 4 — the parent step this ADR executes)
- `docs/adr/ADR-MONO-033-roles-issuance-resolution-model.md` (§ S4 — the assume-tenant role handling this ADR refines)
- `docs/adr/ADR-MONO-034-account-credential-unification-model.md` (§ U2 + § U6 step 3b — the deferred consolidation + `credentials.identity_id` leg this ADR completes)
- `projects/iam-platform/specs/services/auth-service/architecture.md` (the credential store + SAS login + assume-tenant exchange)
- `projects/iam-platform/specs/services/admin-service/data-model.md` (`admin_operators` / `admin_operator_roles` — the operator extension + the admin-console RBAC kept disjoint)

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` — **not amended in this PROPOSED ADR**; its `account_type` removal (Deprecated → removed) is finalized at execution (4b) per its § Change Rule.
- `projects/iam-platform/specs/contracts/http/internal/auth-internal.md` (the `accountType` provisioning field whose disposition 4b decides).

# Edge Cases

- ADR-003a audit table is append-only — verify rows #1-#38 byte-unchanged.
- ADR-035 is PROPOSED only — no ACCEPTED self-flip, no code/migration (the operator role derivation / `account_type` drop / credential convergence / `identity_id` are post-ACCEPTED execution tasks per § 3.3).
- O1 must derive the operator domain role from the assignment + selected-tenant domain, NOT copy `admin_operator_roles` — else it violates ADR-033 S2 / ADR-034 U5 disjointness.
- O5 must keep the 4a-before-4b ordering invariant — dropping the `account_type` leg before operators carry `roles` is the mis-auth window the ADR exists to avoid.
- O2 must demote (not remove) `password_hash` in step 4 — keep a break-glass margin; full removal is a deferred follow-up.

# Failure Scenarios

- If the ADR is authored AND code/migration is implemented in the same task → violates HARDSTOP-09 remediation (decision must precede + be ACCEPTED). This task is doc-only.
- If ADR-035 is **self**-ACCEPTED (AI-decided, without user-explicit intent) → violates the explicit gate the user required. The ACCEPTED transition is a separate user-explicit-intent step.
- If the design maps `admin_operator_roles` into the JWT `roles` claim → violates the ADR-033/034 role-namespace disjointness; ADR-035 derives the operator domain role from the assignment + tenant domain (O1-A).
- If the design drops `account_type` before operators carry `roles` (4b before 4a) → re-introduces the operator mis-authorization window; ADR-035 requires 4a strictly precedes 4b (O5/O6 ordering invariant).
- If the design consolidates the operator credential by a big-bang `password_hash` removal + TOTP merge → drags the operator auth security path into one un-bisectable change; ADR-035 demotes-before-removing with a break-glass margin (O2-A).
