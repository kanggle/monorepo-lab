# Task ID

TASK-MONO-258

# Title

Author **ADR-MONO-034 PROPOSED → ACCEPTED** (same-session, same PR #1538, user-explicit *"진행"*) — Account/credential unification model (one person → one central `identities` registry → consumer account + operator extension; ADR-MONO-032 D5 **step 3** / D6-A mechanics). Records the **unification anchor + cross-store link mechanism + migration phasing** that ADR-032 D5 step 3 ("account/credential unify — opt-in link, no forced merge") + D6-A require but leave underspecified. Investigation at base `c6d754922` (ADR-032 step 2 complete) found the three identity stores are **three physically separate MySQL databases** with no shared identity key spanning all three: the consumer pair (`auth_db.credentials` ↔ `account_db.accounts`) is already linked by a shared account UUID, but the operator store (`admin_db.admin_operators`) is a **wholly independent identity** — own UUID space, own `password_hash`, own login/token path, bridged only by the optional, usually-NULL `oidc_subject`. Choosing the anchor/link/migration shape in code would bake the identity-storage model (HARDSTOP-09) and risk a forced/silent identity merge (a security hazard D6-A forbids). Doc-only; ACCEPTED + implementation are separate user-explicit-intent tasks (sibling ADR-019/020/021/023/024/032/033 staged-child pattern). **Self-ACCEPT prohibited.**

# Status

ready

# Owner

architecture

# Task Tags

- docs
- adr
- security

---

# Dependency Markers

- **child of**: ADR-MONO-032 (ACCEPTED — unified-identity model). ADR-034 executes ADR-032 **D5 step 3** and resolves the **D6-A** account/credential-unification gap (the "opt-in link" without a specified anchor/mechanism/staging); it does NOT re-decide ADR-032 D1-D6.
- **sibling of**: ADR-MONO-033 (ACCEPTED — roles-issuance resolution; resolved the D5-step-2 roles-*source* gap). ADR-034 is the step-3 *identity/credential* counterpart and **reaffirms** ADR-033's two-role-namespace disjointness (U5).
- **triggered by**: user-explicit selection 2026-06-14 (AskUserQuestion "step3 범위" = 링크-우선 / "링크 seam" = 신규 중앙 identities 테이블/서비스) after the three-database / disjoint-operator finding was surfaced. Implementing the unification without recording the decision would bake the identity-storage model (HARDSTOP-09) and risk a silent merge.
- **mirrors**: ADR-MONO-019 `entitled_domains` keystone (cross-DB value-convention + fail-soft) + ADR-MONO-020/014 (`oidc_subject` + assume-tenant token-exchange — the existing opt-in seam U1/U3 formalize).
- **keeps disjoint**: `admin-service.admin_operator_roles` (admin-console RBAC, ADR-002/024) and `account_roles` (JWT domain roles, ADR-033) — identity unification does NOT merge the role namespaces.
- **does NOT amend** `platform/contracts/jwt-standard-claims.md` — identity storage is IdP-internal; the claim shape is unchanged.
- **defers to ADR-032 step 4**: operator login/credential consolidation, `admin_operators.password_hash` demotion/removal, `account_type` column drop, optional `account_roles` re-key to `identity_id` (link-first scope, U2).

# Goal

Publish ADR-MONO-034 PROPOSED so ADR-032 D5 step 3 (account/credential unify) can be implemented against a recorded decision (a new central `identities` registry as the anchor + an opt-in/audited/reversible link with email-match necessary-not-sufficient + a link-first additive migration that defers the operator login/credential consolidation to step 4) rather than an implicit one — with ACCEPTED and execution gated as separate user-explicit-intent tasks.

# Scope

- `docs/adr/ADR-MONO-034-account-credential-unification-model.md` (NEW, Status PROPOSED) — U1 unification anchor (new central `identities` registry; reject reuse-`accounts.id` B + merge-into-accounts C) + U2 link-first step-3 scope (defer login/credential consolidation to step 4; reject full-consolidation-in-step-3) + U3 opt-in/authorized/audited/reversible link with email-match necessary-not-sufficient (reject auto-link + no-link) + U4 unified new-operator provisioning + U5 role-namespace disjointness reaffirm (ADR-033) + U6 additive net-zero migration across three DBs (3a registry+backfill → 3b cross-store `identity_id` → 3c link surface → 3d unified provisioning) + U7 safety invariants.
- `docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md` § 3 audit table — append row #37 (Meta-policy: ADR-034 PROPOSED publish; same one-off pre-author category as rows #13/#18/#22/#25/#27/#29/#31/#33/#35; does NOT add to § D1).
- Doc-only. NO contract/schema/code change (HARDSTOP-09 remediation: record the decision, PAUSE until ACCEPTED).

# Acceptance Criteria

- **AC-1** ADR-MONO-034 exists with Status PROPOSED, U1-U7 CHOSEN-PROPOSED, the three-database / disjoint-operator finding evidence (§ 1.1), and the § 3.3 execution roadmap.
- **AC-2** The decision driver names the concrete gap (three separate DBs, no shared identity key spanning all three; operator = independent UUID + own `password_hash` + own login/token path; only bridge = nullable `oidc_subject`; no same-person record) + the "opt-in link, no forced merge" constraint + the user-explicit selection (U1 = central identities registry, U2 = link-first).
- **AC-3** U1-B (reuse `accounts.id`), U1-C (merge into accounts), U2-B (full consolidation in step 3), U3-B (auto-link), U3-C (no-link) are recorded as rejected with reasons (U3-B's rejection names the cross-tenant email-collision escalation vector).
- **AC-4** ADR-034 positions itself as a **child of ADR-032** (executes D5 step 3 / resolves the D6-A account/credential gap; does NOT re-decide D1-D6) and a **sibling of ADR-033** (reaffirms the role-namespace disjointness, U5), and records that it does **not** amend `jwt-standard-claims.md`.
- **AC-5** ADR-003a § 3 audit rows #37 (PROPOSED) + #38 (ACCEPTED) appended (append-only; rows #1-#37 byte-unchanged before #38).
- **AC-6** Doc-only diff (no `apps/` code, no `platform/contracts/` change, no migrations).
- **AC-7** ADR-034 lands as **ACCEPTED** in this PR (Status ACCEPTED + § 6 PROPOSED + ACCEPTED rows + § 3.3 UNPAUSED), the ACCEPTED gate satisfied by the user-explicit *"진행"* (sibling ADR-033/MONO-257 same-PR PROPOSED→ACCEPTED). U1-U7 finalised byte-unchanged. Execution (§ 3.3: identities registry → cross-store `identity_id` → opt-in link surface → unified provisioning; operator login/credential consolidation deferred to ADR-032 step 4) remains separate post-ACCEPTED tasks — **no code in this PR**.

# Related Specs

- `docs/adr/ADR-MONO-032-unified-identity-roles-model.md` (§ D5 step 3 + D6-A — the parent decision this ADR executes)
- `docs/adr/ADR-MONO-033-roles-issuance-resolution-model.md` (§ S2 — the role-namespace disjointness this ADR reaffirms)
- `projects/iam-platform/specs/services/admin-service/data-model.md` (`admin_operators` / `admin_operator_roles` — the operator extension U1 links + the admin-console RBAC kept disjoint)
- `projects/iam-platform/specs/services/account-service/data-model.md` (`accounts` / `account_roles` — the consumer identity anchor U1 layers `identities` above)
- `projects/iam-platform/specs/services/auth-service/architecture.md` (the credential store + SAS login path)

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` — **not amended** (identity storage is IdP-internal; claim shape unchanged).
- `projects/iam-platform/specs/contracts/http/internal/auth-to-admin.md` (the existing `/internal/**` IAM `client_credentials` edge pattern the future registry resolve/link EPs mirror).

# Edge Cases

- ADR-003a audit table is append-only — verify rows #1-#36 byte-unchanged.
- ADR-034 must NOT change `jwt-standard-claims.md` — identity storage is IdP-internal; the claim shape is unchanged (no § Change Rule trigger).
- ADR-034 must NOT re-decide ADR-032 D1-D6 or ADR-033 S1-S6 — it fills the D5-step-3 / D6-A account/credential gap only.
- ADR-034 is PROPOSED only — no ACCEPTED self-flip, no code/migration (the `identities` registry / link surface / provisioning change are post-ACCEPTED execution tasks per § 3.3).
- The link mechanism must record email-match as **necessary-not-sufficient** (no auto-link) — a silent same-email merge is the cross-tenant privilege-escalation vector D6-A forbids.

# Failure Scenarios

- If the ADR is authored AND code/migration is implemented in the same task → violates HARDSTOP-09 remediation (decision must precede + be ACCEPTED). This task is doc-only.
- If ADR-034 is **self**-ACCEPTED (AI-decided, without user-explicit intent) → violates the staged-child pattern. The same-PR PROPOSED→ACCEPTED here is gated by the user-explicit *"진행"* (sibling ADR-033/MONO-257), NOT a self-ACCEPT.
- If the design proposes auto-link by matching email → re-introduces the cross-tenant email-collision escalation vector; ADR-034 requires explicit, audited, reversible, email-match-necessary-not-sufficient linking (U3).
- If the design folds `admin_operator_roles` or `account_roles` across the identity link → violates the ADR-033 role-namespace disjointness; ADR-034 unifies *identity*, not *authorization* (U5).
- If step 3 is scoped to consolidate the operator login/credential → violates the link-first scope (U2); that consolidation is ADR-032 step 4.
