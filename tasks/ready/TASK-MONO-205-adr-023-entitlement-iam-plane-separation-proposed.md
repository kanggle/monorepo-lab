# Task ID

TASK-MONO-205

# Title

Author **ADR-MONO-023 PROPOSED** — Entitlement/Subscription Plane ↔ IAM Plane Separation + subscription lifecycle state machine. Records the production form of ADR-MONO-019 § D2's `tenant_domain_subscription.status` column (state set + transitions), realizes its deferred § 3.3-step-2 *"(optional) admin surface"*, and decides the **plane-separation invariant** (a non-ACTIVE subscription affects entitlement only; operator assignments + RBAC are preserved — GCP billing↔IAM parity). Resolves the HARDSTOP-09 surfaced when the user, after the AWS/GCP IAM-comparison discussion, chose ("추천대로 진행") to proceed with the recommended first item ③ "구독↔IAM 평면 분리". Doc-only; ACCEPTED + implementation are separate user-gated tasks (sibling ADR-019/020/021 staged-child pattern).

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

- **triggered by**: HARDSTOP-09 — formalizing the subscription lifecycle + deciding the plane-separation invariant + the mutation authority requires an architecture decision not in any spec/ADR (ADR-019 D2 left `status`, the admin surface, and the entitlement↔IAM relationship under-specified).
- **amends (additive, HARDSTOP-04)**: ADR-MONO-019 § D2 — additive § "Additive note" recording that D2's `status` column + deferred § 3.3-step-2 admin surface production form is decided in ADR-023; D1-D6 byte-unchanged.
- **protects**: ADR-MONO-020 (`operator_tenant_assignment` IAM plane) + ADR-MONO-002 (RBAC) — D2's invariant guarantees these survive an entitlement change.
- **orthogonal**: ADR-MONO-021 (`account_type`, the person axis).
- **composes with (future)**: the not-yet-authored tenant-admin delegation ADR (the "①" axis) — D3's `subscription.manage` permission is deliberately separately-grantable.

# Goal

Publish ADR-MONO-023 PROPOSED so the subscription lifecycle, the plane-separation invariant, and the subscription admin/authorization surface can be implemented to a recorded decision (entitlement plane ↔ IAM plane, one-way dependency, GCP parity) rather than an implicit one — with ACCEPTED and execution gated as separate user-explicit-intent tasks.

# Scope

- `docs/adr/ADR-MONO-023-entitlement-iam-plane-separation.md` (NEW, Status PROPOSED) — D1 lifecycle state machine (`ACTIVE`/`SUSPENDED`/`CANCELLED`(+`PENDING`); reject free-form / per-domain status) + D2 plane-separation invariant (two planes one-way dependency; suspend affects entitlement only; reject cascade / per-request) + D3 account-service-owned admin API gated by NEW `subscription.manage` ≠ `operator.manage` (reject admin-service-owned / reuse `operator.manage`) + D4 live-read + `tenant.subscription.changed` outbox event + short-TTL natural expiry (reject sync push / eager revocation) + D5 billing OUT of scope, future driver (reject model-billing-now) + D6 staged net-zero migration.
- `docs/adr/ADR-MONO-019-...md` § D2 — append additive "Additive note" blockquote (ADR-023 forward-reference; D1-D6 bodies byte-unchanged — HARDSTOP-04).
- `docs/adr/ADR-MONO-003a-...md` § 3 audit table — append row #29 (Meta-policy: ADR-023 PROPOSED publish; same one-off category as rows #13/#18/#22/#25/#27 — does NOT add to § D1).
- Doc-only. NO schema/code change (HARDSTOP-09 remediation option 2: record the decision, PAUSE until ACCEPTED).
- INDEX.md NOT touched — established precedent (ADR-013..022 unregistered; recent-ADR authoring does not update the stale index; a separate reconcile is out of scope).

# Acceptance Criteria

- **AC-1** ADR-MONO-023 exists with Status PROPOSED, D1-D6 CHOSEN-PROPOSED, the GCP billing↔IAM parity framing, and the staged § 3.3 roadmap.
- **AC-2** The decision driver names the concrete gap (ADR-019 D2 left `status`/admin-surface/plane-relationship unspecified) + the HARDSTOP-09.
- **AC-3** D2's plane-separation invariant is explicit: suspend/cancel affects the entitlement plane only (catalog + next-token `entitled_domains`); operator assignments + RBAC preserved; re-activate without re-grant. The cascade (D2-B) and per-request (D2-C) alternatives are recorded as rejected with reasons.
- **AC-4** D3 records `subscription.manage` as a NEW permission DISTINCT from `operator.manage`, owned by account-service; reuse-`operator.manage` (D3-C) and admin-service-owned (D3-B) rejected.
- **AC-5** ADR-019 § D2 additive note appended (D1-D6 byte-unchanged); ADR-003a § 3 audit row #29 appended (append-only; rows #1-#28 byte-unchanged, order oldest-first preserved).
- **AC-6** Doc-only diff (no `apps/` code, no migrations).

# Related Specs

- `docs/adr/ADR-MONO-019-platform-console-customer-tenant-model.md` § D2 / § D4 / § D5 (the parent decision this formalizes)
- `docs/adr/ADR-MONO-020-operator-multitenant-assignment.md` (the IAM plane protected)
- `rules/traits/multi-tenant.md` M1-M7 (untouched — entitlement lifecycle, not row isolation)

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` (`entitled_domains` — the ACTIVE-filtered read path D4 keeps byte-stable)

# Edge Cases

- ADR-003a audit table is append-only AND oldest-first — verify rows #1-#28 byte-unchanged and that #29 follows #28 chronologically (2026-06-02 before 2026-06-10).
- The entitlement plane and IAM plane must NOT be conflated — the ADR makes the one-way dependency explicit (IAM reads entitlement; entitlement never reads/mutates IAM).
- `subscription.manage` must NOT be folded into `operator.manage` — the separate permission is the plane separation at the authorization layer.

# Failure Scenarios

- If the ADR is authored AND code is implemented in the same task → violates HARDSTOP-09 remediation (decision must precede + be ACCEPTED). This task is doc-only.
- If D2 chose the cascade model (suspend revokes assignments) → loses the GCP-parity reversibility; the ADR must reject it.
