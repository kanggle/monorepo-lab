# Task ID

TASK-MONO-266

# Title

Author **ADR-MONO-036 PROPOSED** — Born-unified identity provisioning (ADR-MONO-032 **D6-A** realization for new records). Records the decision to **mint the central `identities` row at record birth** on every account/operator/credential creation path (generalizing ADR-MONO-034 **U4**), so a new record's `identity_id` is populated in its home store from the start and never needs an after-the-fact link. The 2026-06-15 live verification (operator↔identity link demo) surfaced the gap: in the running stack the `identities` registry was **EMPTY** behind seed-injected `accounts.identity_id`, `credentials.identity_id` was **NULL for every credential** (ADR-035 **O3** column has no production writer), and only new **operators** are born-linked (one creation path, one store) — so new records are still born split on two of three stores. Decides (P1) the mint point, (P2) the mint's availability stance — **fail-soft** vs fail-closed, a genuine trade-off ADR-034 already took a position on (HARDSTOP-09), (P3) the `credentials.identity_id` writer ADR-035 O3 left open, (P4) the legacy strategy (seed-rewrite the demo + **design — not build** the production cross-DB reconciliation backfill; record why production cannot wipe real users), (P5/P6) staging + safety invariants. Doc-only; **ACCEPTED + implementation are separate user-explicit-intent-gated tasks** (staged-child pattern, ADR-019/020/021/023/024/032/033/034/035). **Self-ACCEPT prohibited.**

# Status

ready

> **PROPOSED → ACCEPTED in this PR (2026-06-15), user-explicit ACCEPT gate honored.** ADR-MONO-036 authored Status PROPOSED on `task/mono-266-born-unified-identity-adr` (worktree-isolated, main parked); the P1-P6 decisions were **presented for review first** (the explicitly-required gate, self-ACCEPT prohibited); the user then gave explicit intent *"진행"* → ADR-MONO-036 flipped PROPOSED → ACCEPTED in the same PR (Status + History ACCEPTED clause + § 6 ACCEPTED row + § 3.3 UNPAUSED), ADR-003a audit rows #41 (PROPOSED) + #42 (ACCEPTED) appended (sibling ADR-035/MONO-260 same-PR pattern). Doc-only (no `apps/` code, no `platform/contracts/` change, no migrations, no seed). **Lifecycle close (ready → done) follows merge + 3-dim verification.** § 3.3 execution roadmap UNPAUSED — next = **M1** (consumer registration born-unified, P1/P2), `TASK-BE-*` in iam-platform (avoid BE-380 held by the concurrent ecommerce session).

# Owner

architecture

# Task Tags

- docs
- adr
- security

---

# Dependency Markers

- **child of**: ADR-MONO-032 (ACCEPTED — unified-identity model). ADR-036 realizes **D6-A** ("one account = one identity") **for new records going forward**; it does NOT re-decide ADR-032 D1-D6.
- **sibling of**: ADR-MONO-034 (ACCEPTED — account/credential unification) — ADR-036 **generalizes U4** (`resolveOrCreate` at creation, today only on the operator-creation path) to every creation path, and **reaffirms U3 § 1.3** (no-silent-merge: born-unified is same-origin issuance keyed on (tenant, email)/`account_id`, NOT email auto-merge; the opt-in link is retained as the legacy-reconciliation tool).
- **sibling of**: ADR-MONO-035 (ACCEPTED — operator auth unification) — ADR-036 **completes O3** by giving `credentials.identity_id` a production writer (P3), and picks up the "born-unified provisioning" + "`account_roles` re-key to `identity_id`" deferred follow-ups from ADR-035 § 3.3.
- **triggered by**: 2026-06-15 live verification (operator↔identity link demo, `tests/federation-hardening-e2e/fixtures/demo-operator-identity-link.sh`) — empty `identities` registry behind seed-injected `accounts.identity_id`, NULL `credentials.identity_id`, born-linked only for new operators → new records still born split on two of three stores. The mint point + its availability stance + the legacy strategy are HARDSTOP-09 architecture decisions + user-explicit steer 2026-06-15 ("처음부터 안 나눠지는 방식 + 데이터 날리고 새로 + 왜 실무는 못 날리는지 ADR 기록").
- **keeps disjoint**: no authorization / role-namespace change — this is identity *correlation*, not authz (ADR-033/034/035 disjointness untouched).
- **defers (follow-ups)**: production cross-DB reconciliation backfill (designed in P4, built only if a real deployment target appears); `account_roles` re-key to `identity_id`; dedicated identity-service; async `IdentityAssigned` writer (P3-C); OIDC-side step-up (ADR-032 D4-B).

# Goal

Publish ADR-MONO-036 PROPOSED so D6-A can be realized for new records against a recorded decision — (P1) mint the central identity at record birth via the existing `resolveOrCreate` primitive on every creation path, (P2) fail-SOFT so registration never blocks on identity-infra availability (ADR-034 stance preserved), (P3) wire the `credentials.identity_id` writer via in-band propagation from the registration orchestration, and (P4) seed-rewrite the demo legacy while documenting (not building) the production cross-DB reconciliation backfill — rather than an implicit one, with ACCEPTED and execution gated as separate user-explicit-intent tasks.

# Scope

- `docs/adr/ADR-MONO-036-born-unified-identity-provisioning.md` (NEW, Status PROPOSED) — P1 mint-at-birth via `resolveOrCreate` (reject lazy/as-is) + P2 fail-SOFT mint (reject fail-closed) + P3 `credentials.identity_id` writer via registration in-band propagation (reject sync auth→account call; async event deferred) + P4 demo seed-rewrite + designed-not-built production cross-DB backfill (account_id-keyed credentials, opt-in link for operators, no email auto-merge; why prod can't wipe) + P5 staged M1→M2→M3 additive net-zero + P6 safety invariants.
- `docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md` § 3 audit table — append row #41 (Meta-policy: ADR-036 PROPOSED publish; same one-off pre-author category as rows #33/#35/#37/#39). Row #42 (ACCEPTED) appended at the gated ACCEPTED transition.
- Doc-only. NO contract/schema/code/seed change (HARDSTOP-09 remediation: record the decision, PAUSE until ACCEPTED).

# Acceptance Criteria

- **AC-1** ADR-MONO-036 exists with Status PROPOSED, P1-P6 CHOSEN-PROPOSED, the live-finding evidence (empty `identities` registry, NULL `credentials.identity_id`, born-linked only for new operators), and the § 3.3 execution roadmap (M1/M2/M3 + designed-deferred backfill).
- **AC-2** The decision driver names the concrete gap (new records born split on two of three stores; ADR-035 O3 writer-less; only operator-creation mints at birth) + the mint-availability HARDSTOP-09 (fail-soft vs fail-closed) + the user-explicit steer.
- **AC-3** P1-B (lazy mint), P2-B (fail-closed mint), P3-B (sync auth→account call), P4-B (build backfill for demo data), P4-C (undocumented wipe), and "email auto-merge to fully automate" are recorded as rejected with reasons (P2-B names the sign-up availability regression; the auto-merge one names the ADR-034 § 1.3 email-collision vector).
- **AC-4** ADR-036 positions itself as a **child of ADR-032** (realizes D6-A for new records; does NOT re-decide D1-D6), a **sibling of ADR-034** (generalizes U4, reaffirms U3 § 1.3), and a **sibling of ADR-035** (completes O3's writer).
- **AC-5** ADR-003a § 3 audit row #41 (PROPOSED) appended (append-only; rows #1-#40 byte-unchanged).
- **AC-6** Doc-only diff (no `apps/` code, no `platform/contracts/` change, no migrations, no seed change).
- **AC-7 (gated)** ADR-036 lands as PROPOSED first; the ACCEPTED transition (Status ACCEPTED + § 6 ACCEPTED row + § 3.3 UNPAUSED + ADR-003a row #42) is a **separate step gated on user-explicit intent** — NOT a same-PR self-flip. **Self-ACCEPT prohibited.**

# Related Specs

- `docs/adr/ADR-MONO-032-unified-identity-roles-model.md` (§ D6-A — the end-state this ADR realizes for new records)
- `docs/adr/ADR-MONO-034-account-credential-unification-model.md` (§ U3/§ 1.3 no-silent-merge + § U4 the `resolveOrCreate` primitive this ADR generalizes + § U6 the three-store correlation)
- `docs/adr/ADR-MONO-035-operator-auth-unification-model.md` (§ O3 `credentials.identity_id` shadow column — this ADR wires its writer + § 3.3 deferred follow-ups)
- `projects/iam-platform/specs/services/account-service/architecture.md` (the `identities` registry + the account-registration path P1 wires)
- `projects/iam-platform/specs/services/auth-service/architecture.md` (the credential store + creation path P3 wires)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/internal/auth-internal.md` (the registration/provisioning seam the in-band `identity_id` propagation rides on) — **not amended in this PROPOSED ADR**.
- No `platform/contracts/` change (identity correlation is additive, net-zero; no wire-shape change in step scope).

# Edge Cases

- ADR-003a audit table is append-only — verify rows #1-#40 byte-unchanged.
- ADR-036 is PROPOSED only — no ACCEPTED self-flip, no code/migration/seed (M1/M2/M3 are post-ACCEPTED execution tasks per § 3.3).
- P1 must converge consumer-side and operator-side provisioning on the SAME identity by the (tenant, email) key (`uk_identities_tenant_email`) — same-origin issuance, NOT a later merge.
- P2 must keep the mint fail-SOFT — a fail-closed mint on registration would let identity-infra downtime block sign-ups (the ADR-034 availability stance this preserves).
- P4 must keep the operator-side legacy reconciliation on the ADR-034 opt-in link (email-match necessary-not-sufficient), NEVER email auto-merge — else it reopens the § 1.3 cross-tenant email-collision privilege-escalation vector.

# Failure Scenarios

- If the ADR is authored AND code/migration/seed is implemented in the same task → violates HARDSTOP-09 remediation (decision must precede + be ACCEPTED). This task is doc-only.
- If ADR-036 is **self**-ACCEPTED (AI-decided, without user-explicit intent) → violates the explicit gate. The ACCEPTED transition is a separate user-explicit-intent step.
- If the design makes the mint fail-CLOSED on registration → couples sign-up availability to the identity infra; ADR-036 keeps it fail-soft (P2-A), mirroring ADR-034 U4.
- If the design replaces the opt-in operator link with email auto-merge → reopens the ADR-034 § 1.3 email-collision vector; ADR-036 retains the opt-in link as the legacy tool (P4/P6).
- If the legacy demo data is wiped with no documented production approach → signals a production anti-pattern; ADR-036 records the designed cross-DB backfill + why production cannot wipe (P4-A).
