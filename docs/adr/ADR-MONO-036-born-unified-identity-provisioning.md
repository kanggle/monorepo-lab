# ADR-MONO-036 — Born-unified identity provisioning (ADR-MONO-032 D6-A realization for new records): mint the central identity at record birth; seed-rewrite the demo legacy; design — not build — the production cross-DB backfill

**Status:** ACCEPTED

**History:** PROPOSED 2026-06-15 (TASK-MONO-266 — records the **born-unified identity provisioning model**: every new account-creating / operator-creating / credential-creating path mints (or reuses) the central `identities` row **at record birth**, so the record's `identity_id` is populated in its home store from the start and never requires an after-the-fact link. ADR-MONO-032 **D6-A** ("one account = one credential / one identity") is the aspirational end-state; ADR-MONO-034 built the central `identities` registry + the **opt-in operator↔identity link** (the *reconciliation* tool for already-split records); ADR-MONO-035 **O3** added `credentials.identity_id` as an additive shadow column **with no production writer** and listed "`account_roles` re-key to `identity_id` / born-unified provisioning" as deferred follow-ups. A live verification on 2026-06-15 (the operator↔identity link demo) surfaced the concrete gap this ADR closes: in the running stack the consumer `accounts.identity_id` values were **seed-injected with an EMPTY `identities` registry** (so no real registration flow populates the registry), and `credentials.identity_id` was **NULL for every credential** (the O3 column has no writer). So "born unified" is true today **only for new operators** (ADR-034 U4 `CreateOperatorUseCase` → `resolveOrCreate`) and **only on the `admin_operators` store** — new consumers and the `credentials` store are still born split. This ADR decides (P1) where/when the identity is minted for new records, (P2) the availability stance of that mint (the trade-off: a synchronous fail-closed mint would let identity-infra downtime block sign-ups — HARDSTOP-09), (P3) the `credentials.identity_id` writer that ADR-035 O3 left open, (P4) the legacy-data strategy (seed-rewrite for the demo; a **designed-not-built** production cross-DB reconciliation backfill — the honest boundary), and (P5/P6) the staging + safety invariants. **Doc-only; ACCEPTED + implementation are separate user-explicit-intent-gated tasks (staged-child pattern, ADR-019/020/021/023/024/032/033/034/035). Self-ACCEPT prohibited.**) · ACCEPTED 2026-06-15 (TASK-MONO-266 — user-explicit *"진행"* after the PROPOSED decisions (P1–P6) were presented for the explicitly-required ACCEPT gate; the gate was honored — the PROPOSED record was presented and review awaited before any flip, **NOT a self-ACCEPT**. P1–P6 CHOSEN-PROPOSED direction **finalised byte-unchanged** — ACCEPTED *finalises*, does not re-decide; § 1 Context + § 2 Decision tables + § 3 Consequences + § 4 Alternatives + § 5 Relationship + § 7 Provenance byte-identical to the PROPOSED draft; flip = Status + this clause + § 6 ACCEPTED row + the § 3.3 execution roadmap PAUSED→UNPAUSED. Delivered in the same PR as the PROPOSED record (the user ACCEPTED after reviewing the presented decisions but before the PROPOSED record independently merged — the staged-child governance trail is preserved *within* the PR: both § 6 rows + ADR-003a audit rows #41 PROPOSED / #42 ACCEPTED, mirroring ADR-033/034/035). ADR-032 D1–D6, ADR-034 U1–U7, ADR-035 O1–O6 not re-litigated.)

**Parent:** [ADR-MONO-032](ADR-MONO-032-unified-identity-roles-model.md) (ACCEPTED 2026-06-14) — the unified-identity model. **D6-A** ("one account = one credential = one identity") is the end-state this ADR realizes **for new records going forward**. ADR-032 D5 steps 0–5 made `roles` the sole authorization axis and removed `account_type`; the identity *correlation* across the three physical stores (`accounts` / `admin_operators` / `credentials`) was built (ADR-034 registry + 3a `accounts.identity_id`, ADR-034 3c `admin_operators.identity_id`, ADR-035 O3 `credentials.identity_id`) but is **populated only opportunistically** (opt-in link + a single operator-creation path). ADR-036 makes the correlation **born-at-creation** for new records and decides how the demo legacy is reconciled. It does **not** re-decide D1–D6.

**Siblings:** [ADR-MONO-034](ADR-MONO-034-account-credential-unification-model.md) (ACCEPTED — central `identities` registry + the **opt-in, audited, reversible, email-match-necessary-but-not-sufficient** operator↔identity link; U3 § 1.3 **no-silent-merge**) and [ADR-MONO-035](ADR-MONO-035-operator-auth-unification-model.md) (ACCEPTED — operator auth unification; **O3** added the `credentials.identity_id` shadow column and **deferred its writer + the cross-DB backfill** as "the source value lives in `account_db.accounts.identity_id`"). ADR-036 **completes ADR-035 O3** (gives the column a production writer, P3) and **reaffirms ADR-034 U3 § 1.3** (P4/P6 — born-unified is *same-origin issuance*, NOT email auto-merge; the opt-in link is retained as the legacy-reconciliation tool, never replaced by auto-merge). It does not re-decide ADR-034 U1–U7 or ADR-035 O1–O6.

**Decision driver:** ADR-032 D6-A is the stated end-state but no ADR decided **how a new record acquires its central identity at birth**, and ADR-035 O3 explicitly left `credentials.identity_id` writer-less. The 2026-06-15 live finding (empty `identities` registry behind seed-injected `accounts.identity_id`; NULL `credentials.identity_id`; born-linked only for new operators on one store) shows new records are still born split on two of three stores. Choosing the mint point, its availability stance, and the legacy strategy in code would silently bake the provisioning model — and the availability stance in particular is a genuine trade-off (fail-soft "registration never blocks" vs fail-closed "every record born-linked") that ADR-034 already took a position on for the link path. That makes the mint-availability decision a HARDSTOP-09 architecture decision. This ADR is that record.

**Related:** [ADR-MONO-032](ADR-MONO-032-unified-identity-roles-model.md) D6-A (the end-state) + D5 (the completed `account_type`→`roles` migration this builds on); [ADR-MONO-034](ADR-MONO-034-account-credential-unification-model.md) U3/§ 1.3 (no-silent-merge — reaffirmed) + U4 (the `resolveOrCreate` provisioning primitive new-operator creation already calls — the model P1 generalizes) + U6 (the three-store `identity_id` correlation); [ADR-MONO-035](ADR-MONO-035-operator-auth-unification-model.md) O3 (`credentials.identity_id` shadow column — P3 wires its writer) + § 3.3 deferred follow-ups (born-unified provisioning + `account_roles` re-key); `projects/iam-platform/apps/account-service/.../application/service/ResolveOrCreateIdentityUseCase.java` (the mint primitive P1 reuses) + the consumer account-registration path (which P1 wires to the mint) + `.../infrastructure/persistence/IdentityJpaEntity.java` (the registry the live finding showed empty); `projects/iam-platform/apps/auth-service/.../domain/credentials/Credential.java` + `db/migration/V0026__add_identity_id_to_credentials.sql` (the ADR-035 O3 column P3 gives a writer); `tests/federation-hardening-e2e/fixtures/seed.sql` + `projects/iam-platform/.../db/migration` dev seeds (the demo legacy P4 rewrites born-unified).

---

## 1. Context

### 1.1 What "born unified" means, and where ADR-032 D6-A actually stands

ADR-032 D6-A is "one account = one credential = one identity." The *correlation* substrate exists: a central `identities` registry (ADR-034, `account_db`, V0023) and an `identity_id` column on all three physical stores — `accounts.identity_id` (ADR-034 3a), `admin_operators.identity_id` (ADR-034 3c), `credentials.identity_id` (ADR-035 O3, V0026). But the substrate is **populated only opportunistically**:

- **`accounts.identity_id`** — set only where a flow writes it. The 2026-06-15 live stack showed consumer accounts carrying `identity_id` values while the `identities` registry table was **EMPTY** → those values were **seed-injected**, not produced by a registration flow that also creates the registry row. No consumer-registration path calls the mint primitive.
- **`admin_operators.identity_id`** — born-linked for new operators: `CreateOperatorUseCase` calls `AccountServiceClient.resolveOrCreateIdentity` (ADR-034 U4, fail-soft) so operators created after step 3 are linked at birth. This is the **one** path that already realizes the born-unified model.
- **`credentials.identity_id`** — **NULL for every credential** in the live stack. ADR-035 O3 added the column as an additive shadow with the writer **explicitly deferred** ("the source value lives in `account_db.accounts.identity_id`"). No production code writes it.

So today: new **operators** are born unified on their home store; new **consumers** and **all credentials** are not. The end-state D6-A is realized for one of three stores on one of the creation paths.

### 1.2 The two halves of "make it born unified"

```
problem = [ legacy records already split ]  +  [ new records keep being born split ]
                     ↑ P4                                ↑ P1 + P3
              seed-rewrite (demo)              wire the mint at every creation path
              / backfill (prod, designed)      (consumer registration + credentials writer)
```

The legacy half is *data*; the forward half is *code*. **Wiping/reseeding the legacy data does NOT fix the forward half** — without P1/P3 a new account created after a reseed is born split again. Both halves are required for D6-A to actually hold.

### 1.3 The mint-availability trade-off (the crux — HARDSTOP-09)

"Born unified" implies the central identity is created **as part of** record creation. That couples record creation to the identity infrastructure's availability. ADR-034 already took a position for the *link* path (U4 `resolveOrCreate` is **fail-SOFT** — operator creation must not hard-fail on identity-infra unavailability; the operator is created unlinked and reconciled later). The same trade-off now applies to the *consumer registration* path, where the stakes are higher (sign-up is a user-facing, availability-critical path):

- **fail-CLOSED mint** — the registration transaction creates the identity first and aborts if identity infra is down → a hard born-unified guarantee, but **identity-infra downtime blocks all sign-ups**.
- **fail-SOFT mint** — registration mints the identity best-effort; if infra is down the account is born **without** `identity_id` and reconciled by a sweep → registration never blocks, but "born unified" is a happy-path default, not a transactional invariant.

Choosing this in code silently bakes the availability posture of the whole identity model. It is a HARDSTOP-09 decision (P2).

### 1.4 The underspecified points this ADR must resolve (HARDSTOP-09)

- **(a)** Where/when is the central identity **minted** for a new record, given only one creation path does it today? — **P1**.
- **(b)** What is the **availability stance** of that mint (fail-soft vs fail-closed), given ADR-034 chose fail-soft for the link path and registration is availability-critical? — **P2**.
- **(c)** Who **writes** `credentials.identity_id` (the ADR-035 O3 column left writer-less), and how without a synchronous cross-service call on the login-critical path? — **P3**.
- **(d)** How is the **legacy** (already-split) data reconciled — and what is the honest production story given a portfolio demo can wipe-and-reseed but a real system cannot delete users? — **P4**.
- **(e)** How does the whole change stage **additively, net-zero**, and what safety invariants hold? — **P5 / P6**.

Each would bake the provisioning model if chosen in code. This ADR records the decision (HARDSTOP-09 remediation: decide first, PAUSE until ACCEPTED); implementation is post-ACCEPTED.

---

## 2. Decision

> Direction is **CHOSEN-PROPOSED**; to be finalised (byte-unchanged) at ACCEPTED per the staged-child pattern. **No code / schema / seed change in this ADR.** Grounded in the 2026-06-15 live finding (empty `identities` registry behind seed-injected `accounts.identity_id`; NULL `credentials.identity_id`; born-linked only for new operators).

### P1 — mint the central identity at record birth, on every creation path, via the existing `resolveOrCreate` primitive

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Generalize the ADR-034 U4 pattern: every record-creating path (consumer account registration, operator creation [already done], and any future principal-creating path) calls `ResolveOrCreateIdentityUseCase` at creation and writes the returned `identity_id` into its home store. Consumer registration additionally guarantees the `identities` registry row exists (the live finding showed it empty). The mint is keyed on (tenant, normalized-email) so the same person registering consumer-side and being provisioned operator-side converges on the **same** identity by construction — same-origin issuance, no merge step.** | New records are born with `identity_id` populated in their home store, pointing at a real `identities` row. Reuses the existing, tested mint primitive (no new mechanism). The (tenant, email) unique key makes convergence structural, not a later reconciliation. | **CHOSEN** — smallest change that makes D6-A hold for new records; reuses U4's proven primitive; convergence is by-construction via the existing `uk_identities_tenant_email`. |
| B. Lazy mint — create the identity on first *need* (first link / first cross-store read), not at birth. | Defers the write. | **Rejected** — "first need" is exactly the after-the-fact link this ADR exists to remove; leaves a window where the record has no identity; reintroduces the opportunistic-population problem. |
| C. Leave creation as-is; rely on the opt-in link + a periodic sweep to populate everything. | No creation-path change. | **Rejected as the forward model** — keeps new records born split; the sweep then perpetually chases new unlinked records. (A sweep is still the right tool for *legacy* records — P4 — but not for *new* ones.) |

### P2 — availability stance of the mint: best-effort born-unified, **fail-SOFT** (preserve ADR-034's registration-never-blocks)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. The mint is fail-SOFT on every creation path (consumer registration included): record creation proceeds even if the identity infra is unavailable; on failure the record is born with `identity_id = NULL` and a `log.warn`, reconciled later by the legacy sweep (P4). "Born unified" is the happy-path default, NOT a hard transactional invariant. Mirrors ADR-034 U4 exactly (operator creation is already fail-soft).** | Registration / credential creation never blocks on identity-infra downtime (the availability-critical invariant). The unlinked-at-birth case is rare (infra down) and self-heals via the same reconciliation path used for legacy. Consistent with the one path that already does this. | **CHOSEN** — preserves ADR-034's deliberate availability stance; avoids making the IdP a hard dependency of every sign-up; the trade-off (a transient unlinked record) is bounded and reconcilable. |
| B. fail-CLOSED mint on consumer registration (block sign-up if identity infra is down). | Hard born-unified guarantee. | **Rejected** — lets identity-infra downtime block all sign-ups; contradicts ADR-034's fail-soft choice for the same primitive; couples the most availability-critical path to the identity infra. The marginal benefit (no transient unlinked records) is not worth the availability regression. |

> **P2 invariant.** "Born unified" is therefore a **strong default, not a guarantee**. The system's correctness never *depends* on `identity_id` being non-null at any instant (every consumer of the correlation tolerates NULL — the ADR-035 O3 / ADR-034 3a columns are additive and net-zero by construction). This is what lets the mint be fail-soft without breaking anything.

### P3 — `credentials.identity_id` writer (complete ADR-035 O3): propagate from the registration orchestration, no synchronous cross-service call on the login path

Wire a **writer** for the ADR-035 O3 column. The credential is created by auth-service; the identity is minted by account-service (different service + physical DB). The writer must not put a synchronous account-service call on the login-critical credential-creation path.

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Propagate the minted `identity_id` through the registration orchestration: the flow that creates the account (mint → `accounts.identity_id`) hands the same `identity_id` to credential creation, which writes `credentials.identity_id` in the same logical registration. Map `identity_id` on `CredentialJpaEntity` (it is currently deliberately unmapped, ADR-035 O3) so the create path persists it. Fail-soft (P2): a NULL propagated value leaves the credential born-unlinked, reconciled later.** | One logical registration sets all of `accounts` / `credentials` `identity_id` from the single minted value; no extra synchronous cross-service hop on the login path; the value is already in hand from the account mint. | **CHOSEN** — reuses the value the account mint already produced; keeps the login path free of a new synchronous dependency; symmetric with `accounts.identity_id`. |
| B. Synchronous auth-service → account-service `resolveIdentity` call at credential creation. | Self-contained in auth-service. | **Rejected** — adds a synchronous cross-service call (and a new failure mode) to the login-critical credential-creation path; duplicates the mint the account path already did. |
| C. Asynchronous event (account emits `IdentityAssigned`; auth-service consumes → updates `credentials`). | Fully decoupled. | **Deferred refinement** — clean but heavier (a new event + consumer for a value already available in-band at registration); A is sufficient and simpler. Reconsider if registration is ever split across services such that in-band propagation is impossible. |

### P4 — legacy (already-split) data: **seed-rewrite** the demo; **design — not build** the production cross-DB reconciliation backfill

| Option | Mechanics | Verdict |
|---|---|---|
| **A. For THIS portfolio: rewrite the dev/federation seeds (`seed.sql` + the IAM dev-seed migrations) so every seeded principal is born unified — a real `identities` row per (tenant, email) and matching `identity_id` across `accounts` / `admin_operators` / `credentials` from the start. Do NOT build a runtime backfill job; the demo data is reseedable. SEPARATELY, DOCUMENT (in this ADR, § P4 design) the production reconciliation backfill that a real deployment would need — an idempotent, resumable, no-downtime application-level job that, per `account_id`, reads `accounts.identity_id` and writes it to `credentials.identity_id` (same-person CERTAIN — shared `account_id`) and resolves `admin_operators.identity_id` via the ADR-034 opt-in link semantics (NOT email auto-merge). Explicitly record WHY production cannot take the wipe path (real users cannot be deleted).** | The demo reaches a fully-born-unified state cheaply (no throwaway migration for fake data). The production approach is captured as a design so the model is complete and the portfolio narrative demonstrates awareness of the real cross-DB migration problem (not hidden by the demo shortcut). | **CHOSEN** — right-sized for a reseedable demo; preserves the resume-worthy "I know production can't wipe and here's the no-downtime backfill" story without over-engineering a migration for seed data. |
| B. Build the full production backfill job now and run it against the demo data. | Most "real." | **Rejected for this project** — over-engineering: a resumable cross-DB reconciliation job for fake, reseedable seed data. The *design* (in A) captures the engineering value; the *implementation* against demo data adds cost without demo value. (Promotable later if a real deployment target appears.) |
| C. Truncate the three stores and reseed, with no design doc. | Simplest. | **Rejected** — the wipe alone, undocumented, signals "delete all users to migrate," which is a production anti-pattern; the honest boundary (why prod differs) is the point. |

> **P4 — production backfill design (documented, NOT implemented).** Per-`account_id` reconciliation: (1) `accounts.identity_id` is the source of truth (account-service owns the registry). (2) `credentials.identity_id := accounts.identity_id` keyed on the shared `account_id` — same-person CERTAIN, no matching heuristic, safe to batch. (3) `admin_operators.identity_id` is reconciled **only** via the ADR-034 U3 opt-in link semantics (`oidc_subject`/email is necessary-but-not-sufficient; the explicit, audited link is what authorizes) — the backfill **never** email-auto-merges an operator into a consumer identity (§ 1.3 no-silent-merge, reaffirmed in P6). Properties: idempotent (re-running is a no-op once set; never overwrites a non-NULL differing value — surfaces a conflict instead), resumable (cursor on `account_id`), no-downtime (additive column writes, no locks on the hot path), observable (counts: reconciled / already-set / conflict / skipped). This design is the deferred-to-production artifact; it is not built here.

### P5 — staged migration (each sub-step additive, net-zero, main-GREEN)

| Sub-step | Change | Net effect |
|---|---|---|
| **M1 — consumer registration born-unified (P1/P2)** | Consumer account-registration path calls `resolveOrCreate` (fail-soft) + ensures the `identities` row exists + writes `accounts.identity_id` at creation. | Additive: new consumer accounts born linked; infra-down → NULL (reconcilable). Net-zero for existing data. |
| **M2 — `credentials.identity_id` writer (P3)** | Map `identity_id` on `CredentialJpaEntity`; registration orchestration propagates the minted value to credential creation (fail-soft). | Additive: new credentials born linked. No login-path synchronous dependency. Net-zero. |
| **M3 — demo seed-rewrite (P4-A)** | Rewrite `seed.sql` + IAM dev seeds so all seeded principals are born unified (real `identities` rows + matching `identity_id` across all three stores). | Demo reaches fully-born-unified state. CI/e2e seeds updated atomically. |
| **(designed, deferred)** | Production cross-DB reconciliation backfill (P4 design). | NOT built (demo is reseedable). Captured as design only. |
| **(deferred follow-ups)** | `account_roles` re-key to `identity_id` (ADR-035 § 3.3); a dedicated identity-service; `IdentityAssigned` async writer (P3-C). | Out of scope. |

### P6 — safety invariants

- **Registration availability preserved** — the mint is fail-SOFT on every creation path (P2); identity-infra downtime never blocks sign-up / credential creation. Mirrors ADR-034 U4.
- **Born-unified ≠ email auto-merge** (ADR-034 U3 § 1.3 reaffirmed) — convergence is *same-origin issuance* keyed on (tenant, email) at the moment of creation; the legacy reconciliation uses the shared `account_id` (certain) for `accounts`↔`credentials` and the **opt-in, audited** operator link for `admin_operators`. No path email-auto-merges two pre-existing records.
- **The opt-in operator↔identity link is RETAINED, not replaced** — it remains the legacy-reconciliation tool (and the only way to link a pre-existing operator). Born-unified provisioning is *additive forward*, not a removal of the link surface.
- **Correctness never depends on non-NULL `identity_id`** (P2 invariant) — every consumer of the correlation tolerates NULL (additive, net-zero columns); this is what permits the fail-soft mint.
- **Role namespaces untouched** — this is identity *correlation*, not authorization. No change to `roles` / `account_roles` / `admin_operator_roles` (ADR-033/034/035 disjointness preserved). Identity unification ≠ authorization merge.
- **IAM is the sole issuance authority** (ADR-001) — account-service owns the `identities` registry; all stores reference it; no other service mints identities.

---

## 3. Consequences

### 3.1 Hard invariants this ADR carries

- **New records are born unified** (best-effort, fail-soft) across all three stores — D6-A holds going forward, not just for new operators on one store.
- **ADR-035 O3 gets a production writer** — `credentials.identity_id` is populated at credential creation, no longer a writer-less shadow column.
- **The demo reaches a fully-born-unified state** via seed-rewrite, with the production backfill captured as a design (the honest boundary: demo can wipe, production cannot).
- **No availability regression** — the mint is fail-soft; sign-up never blocks on identity infra (ADR-034 stance preserved).
- **No authorization change, no email auto-merge** — identity correlation only; ADR-034 § 1.3 no-silent-merge reaffirmed.

### 3.2 What this ADR does NOT do (deferred to ACCEPTED + post-ACCEPTED execution)

- No implementation: no registration-path change, no entity mapping, no seed rewrite, no backfill — all post-ACCEPTED tasks (§ 3.3).
- No production backfill **implementation** (design only — P4); the demo is reseeded instead.
- No `account_roles` re-key to `identity_id`; no dedicated identity-service; no async `IdentityAssigned` writer (P3-C deferred).
- No change to the opt-in operator↔identity link (retained as-is); no authorization / role-namespace change.
- Does not re-decide ADR-032 D1–D6, ADR-034 U1–U7, or ADR-035 O1–O6 — it realizes D6-A for new records and completes ADR-035 O3's writer.

### 3.3 Future-self (post-ACCEPTED execution roadmap — sketch, to be finalised at ACCEPTED; PAUSED until ACCEPTED)

1. **TASK-?-1 (M1, P1/P2)** — wire consumer account-registration → `resolveOrCreate` (fail-soft) + ensure `identities` row + write `accounts.identity_id` at creation. Model = **Opus** (identity provisioning on the registration path). *Closes the consumer half of "born unified."*
2. **TASK-?-2 (M2, P3)** — map `identity_id` on `CredentialJpaEntity`; propagate the minted `identity_id` from registration to credential creation (fail-soft). Model = **Opus** (login-path schema + orchestration). *Gives ADR-035 O3 its writer.*
3. **TASK-?-3 (M3, P4-A)** — rewrite `seed.sql` + IAM dev seeds born-unified (real `identities` rows + matching `identity_id` across all three stores); update CI/e2e seeds atomically. Model = **Sonnet** (seed data + verification). *Reconciles the demo legacy.*
- **Optional follow-ups:** production cross-DB reconciliation backfill (P4 design → build, only if a real deployment target appears); `account_roles` re-key to `identity_id`; dedicated identity-service; async `IdentityAssigned` writer (P3-C); OIDC-side step-up (ADR-032 D4-B).

> **UNPAUSED — ACCEPTED 2026-06-15 (TASK-MONO-266).** The § 3.3 roadmap proceeds dependency-correct from this ACCEPTED main. Task IDs are assigned at execution (root `TASK-MONO-*` for the seed/cross-cutting steps; `TASK-BE-*` in iam-platform for the service-internal registration/credential wiring), checked against the live `ready/` + `review/` queues and the concurrent worktrees (`be-380`, `pc-fe-092`) to avoid collision. Each remains a separate task; P1–P6 are finalised and not re-litigated at execution. Begin with **M1** (consumer registration born-unified, P1/P2 — the unblocker that makes new consumer accounts born-linked).

---

## 4. Alternatives Considered

- **Lazy mint on first need (P1-B).** Rejected — that *is* the after-the-fact link this ADR removes for new records.
- **fail-CLOSED mint on registration (P2-B).** Rejected — lets identity-infra downtime block all sign-ups; contradicts ADR-034's fail-soft stance for the same primitive.
- **Synchronous auth→account call for `credentials.identity_id` (P3-B).** Rejected — new synchronous dependency on the login-critical path; duplicates the account-side mint.
- **Build the production backfill against demo data (P4-B).** Rejected for this project — over-engineering a resumable cross-DB job for reseedable fake data; the *design* captures the value.
- **Wipe + reseed with no design doc (P4-C).** Rejected — undocumented wipe signals a production anti-pattern; the honest "why prod differs" boundary is the deliverable.
- **Replace the opt-in operator link with email auto-merge to "fully automate" convergence.** Rejected — reintroduces the ADR-034 § 1.3 cross-tenant email-collision privilege-escalation vector; born-unified is same-origin issuance, not merge.

## 5. Relationship to ADR-032 / ADR-034 / ADR-035

| | ADR-MONO-032 | ADR-MONO-034 | ADR-MONO-035 |
|---|---|---|---|
| Relationship | **Child** — realizes **D6-A** ("one account = one identity") for new records; does not re-decide D1–D6 | **Sibling** — generalizes **U4** (`resolveOrCreate` at creation) to all creation paths; **reaffirms U3 § 1.3** (no-silent-merge); retains the opt-in link as the legacy tool | **Sibling** — **completes O3** (gives `credentials.identity_id` a production writer); picks up the "born-unified provisioning" deferred follow-up from § 3.3 |

## 6. Status Transition History

| Date | Transition | Decision summary | Trigger | PR |
|---|---|---|---|---|
| 2026-06-15 | created PROPOSED | P1 = mint the central identity at record birth on every creation path via the existing `resolveOrCreate` primitive (generalize ADR-034 U4); reject lazy-mint + as-is. P2 = fail-SOFT mint (preserve ADR-034 registration-never-blocks); reject fail-closed. P3 = `credentials.identity_id` writer via in-band propagation from the registration orchestration (map the O3 column); reject sync auth→account call; async event deferred. P4 = seed-rewrite the demo legacy + DESIGN (not build) the production cross-DB reconciliation backfill (idempotent/resumable/no-downtime, `account_id`-keyed for `credentials`, opt-in link for operators, no email auto-merge); document why production cannot wipe. P5 = staged M1→M2→M3 (each additive net-zero), backfill designed-deferred. P6 = safety invariants (registration availability fail-soft; born-unified ≠ email auto-merge; opt-in link retained; correctness never depends on non-NULL identity_id; role namespaces untouched; IAM sole issuance authority). **Child of ADR-032 (realizes D6-A for new records); sibling of ADR-034 (generalizes U4, reaffirms U3 § 1.3) + ADR-035 (completes O3).** | 2026-06-15 live verification (operator↔identity link demo) surfaced the gap: empty `identities` registry behind seed-injected `accounts.identity_id`, NULL `credentials.identity_id`, born-linked only for new operators on one store → new records still born split on two of three stores; the mint point + its availability stance (fail-soft vs fail-closed) + the legacy strategy are HARDSTOP-09 architecture decisions. User-explicit steer 2026-06-15 ("처음부터 안 나눠지는 방식으로 + 데이터 날리고 새로 + 왜 실무는 못 날리는지 ADR에 기록"). | #<this> |
| 2026-06-15 | PROPOSED → ACCEPTED | P1–P6 CHOSEN-PROPOSED direction **finalised byte-unchanged** (ACCEPTED *finalises*, does not re-decide); § 1–5/7 byte-identical to the PROPOSED draft; flip = Status + History ACCEPTED clause + this row + § 3.3 PAUSED→UNPAUSED. Child-of-ADR-032 (realizes D6-A for new records) / sibling-of-ADR-034 (generalizes U4, reaffirms U3 § 1.3) + ADR-035 (completes O3) scope unchanged (D1–D6 / U1–U7 / O1–O6 not re-litigated). Delivered in the same PR as PROPOSED — the user ACCEPTED after the presented-decisions gate but before the PROPOSED record independently merged; governance trail preserved in-PR (both § 6 rows + ADR-003a audit rows #41/#42), mirroring ADR-033/034/035. The ACCEPT gate was honored (PROPOSED decisions presented + review awaited, NOT a self-ACCEPT). | "진행" (TASK-MONO-266 — user-explicit intent after the PROPOSED P1–P6 decisions were presented for the explicitly-required ACCEPT gate; sibling ADR-035/MONO-260 same-session PROPOSED→ACCEPTED) | #<this> |

> **ACCEPTED 2026-06-15 (TASK-MONO-266).** The § 3.3 execution roadmap is now **UNPAUSED**; the execution steps proceed dependency-correct from this ACCEPTED main, beginning with **M1** (consumer registration born-unified, P1/P2). Each remains a separate task; P1–P6 are finalised and not re-litigated at execution. **ADR-032 D1–D6, ADR-034 U1–U7, ADR-035 O1–O6 are not re-litigated here** — ADR-036 realizes D6-A for new records and completes ADR-035 O3's writer. The mint stays fail-SOFT (registration never blocks on identity infra — the ADR-034 availability stance) and born-unified is same-origin issuance, never email auto-merge (the ADR-034 § 1.3 no-silent-merge invariant).

## 7. Provenance

- 2026-06-15 live verification on the federation-hardening-e2e stack (operator↔identity link demo, `tests/federation-hardening-e2e/fixtures/demo-operator-identity-link.sh`):
  - **`identities` registry EMPTY** while consumer `accounts.identity_id` carried values → the values are seed-injected; no registration flow creates the registry row + links the account.
  - **`credentials.identity_id` NULL for every credential** → ADR-035 O3's column has no production writer (confirmed against `CredentialJpaRepositoryTest`: "new credential carries NULL identity_id (no creation path wired)").
  - **Born-linked only for new operators** — `CreateOperatorUseCase` → `AccountServiceClient.resolveOrCreateIdentity` (ADR-034 U4, fail-soft) is the one creation path that mints at birth; it links only `admin_operators`.
  - **The opt-in link works** — `PATCH /api/admin/operators/{op}/identity:link` set `admin_operators.identity_id` to the matching consumer account's central identity (200, audited `OPERATOR_IDENTITY_LINK`), proving the *reconciliation* tool; the 3-store match required a manual `credentials.identity_id` backfill (no writer) = the gap this ADR closes.
- ADR-MONO-032 D6-A ("one account = one credential = one identity") — the end-state realized here for new records.
- ADR-MONO-034 U3 § 1.3 (no-silent-merge; email-match necessary-but-not-sufficient) + U4 (`resolveOrCreate` fail-soft at operator creation — the primitive P1 generalizes) + U6 (the three-store `identity_id` correlation).
- ADR-MONO-035 O3 (`credentials.identity_id` additive shadow column, writer deferred) + § 3.3 ("born-unified provisioning" + "`account_roles` re-key to `identity_id`" deferred follow-ups).
- `projects/iam-platform/apps/account-service/.../application/service/ResolveOrCreateIdentityUseCase.java` (the mint primitive; (tenant, email) keyed; `uk_identities_tenant_email` race-safe) — the existing component P1/P2/P3 reuse.
- Google Cloud IAM (one identity from creation; the "born unified, no later merge" reference) — ADR-032's chosen model; ADR-036 closes the gap between that aspiration and the as-built opportunistic population, while keeping the safe opt-in link for the legacy that a real deployment cannot wipe.

분석=Opus 4.8 / 구현=Opus 4.8 (born-unified identity provisioning under HARDSTOP-09; child of ADR-032 realizing D6-A for new records; generalizes ADR-034 U4 + reaffirms U3 § 1.3 no-silent-merge; completes ADR-035 O3's writer; the mint-availability stance is the crux fail-soft decision; staged-child ADR pattern per ADR-019/020/021/023/024/032/033/034/035; seed-rewrite for the reseedable demo + a designed-not-built production cross-DB backfill as the honest boundary).
