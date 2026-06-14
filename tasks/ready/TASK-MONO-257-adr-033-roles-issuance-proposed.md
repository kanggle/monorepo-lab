# Task ID

TASK-MONO-257

# Title

Author **ADR-MONO-033 PROPOSED** — Roles-issuance resolution model (where the `roles` claim's values come from at token-issue time; ADR-MONO-032 D5 step 2 mechanics). Records the **source + aud-scoping + failure-policy** of the `roles` claim that ADR-032 D5 step 2 requires but D6-A left underspecified. Investigation at base `632f88206` found D6-A's "assemble from the account's grants (RBAC store)" assertion is factually incomplete — there is **no store mapping an identity → its domain-platform roles** (`WMS_OPERATOR`, …), `roles` is **never emitted today**, and three disjoint identity/role stores exist. Choosing the source in code would bake the issuance model → HARDSTOP-09. Doc-only; ACCEPTED + implementation are separate user-explicit-intent tasks (sibling ADR-019/020/021/023/024/032 staged-child pattern).

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

- **child of**: ADR-MONO-032 (ACCEPTED — unified-identity model). ADR-033 resolves ONE underspecified execution point inside ADR-032 D5 step 2 + D6-A (the concrete roles *source*); it does NOT re-decide ADR-032 D1-D6.
- **triggered by**: user-explicit selection 2026-06-14 (AskUserQuestion "Roles 소스" = "소스-충실 + aud 기본값 시드", option A) after the three-store / no-domain-platform-role-source finding was surfaced. Implementing the roles source without recording the decision would bake the issuance model (HARDSTOP-09).
- **mirrors**: ADR-MONO-019 `entitled_domains` keystone (issuance-time cross-service lookup + fail-soft + recursion guard — the S4/S5 template).
- **keeps disjoint**: `admin-service.admin_operator_roles` (admin-console RBAC, ADR-002/024) — NOT folded into the JWT domain `roles` claim.
- **does NOT amend** `platform/contracts/jwt-standard-claims.md` — the `roles` claim *shape* is unchanged; this ADR decides only how the IdP *sources* the claim (not a contract concern; the contract is consumer-facing).

# Goal

Publish ADR-MONO-033 PROPOSED so ADR-032 D5 step 2 (roles-only issuance) can be implemented against a recorded source decision (`account_roles` authoritative + aud-default seed + fail-soft, with the admin-console RBAC kept disjoint) rather than an implicit one — with ACCEPTED and execution gated as separate user-explicit-intent tasks.

# Scope

- `docs/adr/ADR-MONO-033-roles-issuance-resolution-model.md` (NEW, Status PROPOSED) — S1 roles-resolution strategy (source-faithful + aud-default seed; reject convention-only B + new per-platform RBAC store C) + S2 authoritative store (`account_roles`) + internal read EP + `admin_operator_roles` disjoint + S3 role→aud convention scoping + aud-default seed table (explicit aud column deferred) + S4 resolution locus (`TenantClaimTokenCustomizer` base token recursion-safe + assume-tenant augmentation) + S5 fail-soft (net-zero; gateway is the deny point) + S6 additive net-zero migration within ADR-032 D5 step 2.
- `docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md` § 3 audit table — append row #35 (Meta-policy: ADR-033 PROPOSED publish; same one-off pre-author category as rows #13/#18/#22/#25/#27/#29/#31/#33; does NOT add to § D1).
- Doc-only. NO contract/schema/code change (HARDSTOP-09 remediation: record the decision, PAUSE until ACCEPTED).

# Acceptance Criteria

- **AC-1** ADR-MONO-033 exists with Status PROPOSED, S1-S6 CHOSEN-PROPOSED, the three-store finding evidence (§ 1.1), and the § 3.3 execution roadmap.
- **AC-2** The decision driver names the concrete gap (no store maps an identity → its domain-platform roles; `roles` never emitted; D6-A's "from the account's grants" incomplete) + the step-1 gateways' role-presence expectation + the user-explicit option-A selection.
- **AC-3** S1-B (convention-only) and S1-C (new per-platform RBAC store) are recorded as rejected with reasons.
- **AC-4** ADR-033 explicitly positions itself as a **child of ADR-032** (resolves D5-step-2 / D6-A roles-source gap; does NOT re-decide D1-D6) and records that it does **not** amend `jwt-standard-claims.md` (claim shape unchanged).
- **AC-5** ADR-003a § 3 audit row #35 appended (append-only; rows #1-#34 byte-unchanged).
- **AC-6** Doc-only diff (no `apps/` code, no `platform/contracts/` change, no migrations).

# Related Specs

- `platform/contracts/jwt-standard-claims.md` (§ Standard Claims `roles` Required + aud-scoped; § Role Strategy — the target the issuance must satisfy)
- `projects/iam-platform/specs/services/admin-service/data-model.md` (`admin_operator_roles` / `admin_roles` — the admin-console RBAC kept disjoint)
- `projects/iam-platform/specs/services/auth-service/architecture.md` (the issuance locus — `TenantClaimTokenCustomizer`)

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` § Standard Claims (`roles`) — satisfied (sourceable), not amended.
- `projects/iam-platform/specs/contracts/http/internal/auth-to-admin.md` (the existing `/internal/**` IAM `client_credentials` edge pattern the new account-service roles read EP mirrors).

# Edge Cases

- ADR-003a audit table is append-only — verify rows #1-#34 byte-unchanged.
- ADR-033 must NOT change `jwt-standard-claims.md` — the claim *shape* is unchanged; only the IdP-internal *source* is decided (no § Change Rule trigger).
- ADR-033 must NOT re-decide ADR-032 D1-D6 — it fills the D6-A roles-source gap only; D1-D6 stay byte-unchanged.
- ADR-033 is PROPOSED only — no ACCEPTED self-flip, no code (the `account_roles` read EP / customizer `roles` leg are post-ACCEPTED execution tasks per § 3.3).

# Failure Scenarios

- If the ADR is authored AND code/contract is implemented in the same task → violates HARDSTOP-09 remediation (decision must precede + be ACCEPTED). This task is doc-only.
- If ADR-033 is self-ACCEPTED in the same task → violates the staged-child pattern (ACCEPTED is a separate user-explicit-intent task). PROPOSED only.
- If `admin_operator_roles` is proposed as the JWT domain-`roles` source → wrong namespace (admin-console permissions, not domain roles); ADR-033 keeps the two role spaces disjoint (S2).
