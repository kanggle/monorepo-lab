# Task ID

TASK-MONO-208

# Title

ADR-MONO-024 (Tenant-Admin Delegation) PROPOSED publish — author the decision record for the "①" tenant-admin delegation axis ADR-MONO-023 repeatedly foreshadowed: a tenant-scoped `operator.manage`-class authority (`TENANT_ADMIN` role) letting a customer's own admin manage its operators/assignments WITHIN its tenant, with central scope-confinement and strict no-escalation. Doc-only (ADR + ADR-020 additive amendment + ADR-003a § 3 audit row); no implementation. Staged-child ADR pattern (sibling ADR-019/020/021/023): PROPOSED records the D1-D7 CHOSEN-PROPOSED direction; ACCEPTED + the 3-step execution roadmap are separate user-gated tasks.

# Status

ready

# Owner

backend

# Task Tags

- adr
- meta-policy
- multi-tenant
- rbac
- iam

---

# Dependency Markers

- **consumes**: ADR-MONO-023 D2/D3 (`subscription.manage` made separately-grantable — ADR-024 D5 consumes the separability as a v1 exclusion: the tenant-admin gets IAM-plane operator-management, NOT entitlement self-service).
- **amends (additive, HARDSTOP-04)**: ADR-MONO-020 § D1 (records the delegated-administration model + tenant-scope confinement for `operator_tenant_assignment`; D1-D6 bodies byte-unchanged).
- **gated by**: HARDSTOP-09 (undocumented authorization architecture — tenant-admin delegation model + scope-confinement + escalation boundary).
- **blocks**: the post-ACCEPTED 3-step execution roadmap (§ 3.3) — confinement evaluator → role+assign surface+grant-menu → delegation proof e2e — each a separate task, PAUSED until ACCEPTED.

# Goal

Record, as a committed ADR, the tenant-admin delegation model so the "customer manages its own operators" feature cannot be implemented with an implicitly-baked authorization/escalation model (HARDSTOP-09), and so the ADR-020 operator-assignment plane's missing delegated-administration dimension is decided in an ADR, not in code (HARDSTOP-04). This task is the PROPOSED publish only — no implementation.

# Scope

- NEW `docs/adr/ADR-MONO-024-tenant-admin-delegation.md` — Status PROPOSED; D1-D7 CHOSEN-PROPOSED:
  - D1 = new `TENANT_ADMIN` seed role holding `operator.manage`, tenant-scoped by the grant row's `admin_operator_roles.tenant_id` (`'*'`=platform, SUPER_ADMIN net-zero).
  - D2 = central target-tenant confinement in the evaluator (`target tenant ∈ effective admin-scope`, `'*'`=all, request-time DB, no claim) — the crux.
  - D3 = new assign/unassign `operator_tenant_assignment` surface + grant-menu confinement (no role-above-own, no platform role, no `TENANT_ADMIN` self-grant) = privilege-escalation prevention.
  - D4 = only SUPER_ADMIN grants `TENANT_ADMIN`; no sub-delegation in v1 (B deferred).
  - D5 = `TENANT_ADMIN` does NOT include `subscription.manage` (entitlement/billing platform-controlled; ADR-023 separability consumed as exclusion).
  - D6 = no token claim; admin-service-local request-time resolution; assume-tenant pipeline untouched (net-zero).
  - D7 = staged net-zero migration (confinement net-zero → role+surface+menu → delegation proof e2e).
- `docs/adr/ADR-MONO-020-...md` — additive § History/D1 "Additive note" blockquote (D1-D6 bodies byte-unchanged).
- `docs/adr/ADR-MONO-003a-...md` § 3 — append audit row #31 (Meta-policy; one-off, does NOT add to § D1; staged-child ADR).

# Acceptance Criteria

- **AC-1** ADR-MONO-024 exists with Status PROPOSED, D1-D7 tables (CHOSEN + rejected alternatives), § Consequences (invariants/not-do/roadmap), § Relationship, § History, § Provenance — ADR-023 template parity.
- **AC-2** ADR-MONO-020 amended additively only (HARDSTOP-04): a single "Additive note" blockquote after D1; the D1-D6 decision bodies are byte-unchanged.
- **AC-3** ADR-003a § 3 audit row #31 appended AFTER row #30 (append-only order preserved); ADR-019/021/023 byte-unchanged.
- **AC-4** No implementation: no `TENANT_ADMIN` migration, no evaluator change, no endpoints — all post-ACCEPTED (§ 3.3). Doc-only PR.

# Related Specs

- `docs/adr/ADR-MONO-023-entitlement-iam-plane-separation.md` (the sibling that foreshadowed this axis + made `subscription.manage` separable)
- `docs/adr/ADR-MONO-020-operator-multitenant-assignment.md` (the amended operator-assignment IAM plane)
- `projects/iam-platform/specs/services/admin-service/rbac.md` (Seed Roles + Permission Evaluation Algorithm the ADR extends)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` (the operator-management surface whose authority the ADR tenant-scopes; mutated only at post-ACCEPTED step 2)

# Edge Cases

- HARDSTOP-04: ADR-020 amendment MUST be additive (blockquote note) — re-decision of D1-D6 bodies is a violation.
- ADR-003a is append-only — row #31 after the existing #30 (which landed in the prior ADR-023 ACCEPTED PR); rows #1–#30 byte-unchanged.
- PROPOSED only — the ACCEPTED transition is a separate user-explicit-intent-gated task (sibling MONO-206); execution PAUSED until then.

# Failure Scenarios

- If the ADR's D2 confinement were specified as per-endpoint rather than central, escalation holes become likely — the ADR fixes it as one central evaluator rule (D2-A) precisely to avoid this.
- If D5 bundled `subscription.manage`, the IAM and entitlement delegation planes would re-couple — the ADR keeps them independent (ADR-023 separability).
- If the ADR-020 amendment edited D1-D6 bodies → HARDSTOP-04 violation; the task keeps it to an additive note.
