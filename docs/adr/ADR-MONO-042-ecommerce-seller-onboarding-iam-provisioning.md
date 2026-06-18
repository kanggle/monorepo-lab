# ADR-MONO-042 — Ecommerce seller onboarding mints a real IAM seller-operator account: replace the "trusted token claim, no real account" seller with born-unified IAM provisioning (ADR-036 reuse) + seller-lifecycle deactivation, fail-soft

**Status:** ACCEPTED

**History:** PROPOSED 2026-06-18 (TASK-BE-402 — records the **ecommerce seller-onboarding IAM-provisioning model**: how a marketplace seller, today a bare `Seller` row in product-service [`SellerStatus = ACTIVE` only] plus a TRUSTED operator-token `seller_id` / seller-scope claim [TASK-BE-363 / BE-375], acquires a **real IAM seller-operator account** at onboarding [reusing the ADR-036 born-unified `resolveOrCreate` provisioning primitive] and is **deactivated** [the backing account locked] on operator suspend/close. This realizes ADR-MONO-030 §3.4 Step 4 facet f ["셀러 온보딩 흐름 + 실 IAM provisioning" — the last named Step-4 marketplace facet], which the parent ADR deferred as "보류 [토큰 claim 신뢰]". The **crux finding** [2026-06-18 code investigation]: a seller is provisioned NOWHERE in IAM — `RegisterSellerService.register()` writes a single `sellers` row [`status = ACTIVE`] and the seller's authority comes ENTIRELY from a trusted operator-token claim [`X-Seller-Scope`] the gateway forwards; there is no account, no credential, no identity behind the seller, and no lifecycle beyond ACTIVE. So a "seller" is an attribution label, not a principal. This ADR decides [D1] where the lifecycle + linkage live [extend the `Seller` aggregate vs a new seller-service], [D2] the provisioning mechanism [reuse the admin-service `AccountServiceClient` + ADR-036 `resolveOrCreate` pattern], [D3 — the CRUX, HARDSTOP-09] the AVAILABILITY stance of the onboarding mint [fail-soft "onboarding never blocks on IAM infra, seller stays PENDING_PROVISIONING" vs fail-closed "no seller without a provisioned account"], [D4] seller deactivation [lock/deactivate the stored account], [D5] identity convergence [reuse `resolveOrCreate` so a consumer + seller-operator on the same email share one central identity — ADR-036 born-unified], and [D6] authz net-zero [the runtime seller-scope path is UNCHANGED; only the trusted claim is now backed by a real account]. **Doc-only PROPOSED record; ACCEPTED + implementation are user-explicit-intent-gated [staged-child pattern, ADR-019/020/021/023/024/032/033/034/035/036/037]. Self-ACCEPT prohibited.**) · ACCEPTED 2026-06-18 (TASK-BE-402 — user-explicit ACCEPT after the PROPOSED decisions [D1–D6] were presented for the explicitly-required ACCEPT gate, **the user explicitly choosing D3 fail-soft** [the HARDSTOP-09 availability crux] over the rejected fail-closed alternative; the gate was honored — the PROPOSED record was presented and the decision awaited before any flip, **NOT a self-ACCEPT**. D1–D6 CHOSEN-PROPOSED direction **finalised byte-unchanged** — ACCEPTED *finalises*, does not re-decide; § 1 Context + § 2 Decision tables + § 3 Consequences + § 4 Alternatives + § 5 Relationship + § 7 Provenance byte-identical to the PROPOSED draft; flip = Status + this clause + § 6 ACCEPTED row + the § 3.3 execution roadmap PAUSED→UNPAUSED + the TASK-BE-402 HARDSTOP-09 banner lift. Delivered in the same PR/branch as the PROPOSED record — the user ACCEPTED after reviewing the presented decisions but before the PROPOSED record independently merged; the staged-child governance trail is preserved within the branch [both § 6 rows]. ADR-030 D1–D8, ADR-036 P1–P6, ADR-032 D1–D6, ADR-025 axis-2 not re-litigated.)

**Parent:** [ADR-MONO-030](ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md) (ACCEPTED 2026-06-12) — the ecommerce multi-vendor marketplace SaaS model. **D3** placed the seller as an aggregate inside a tenant (`(tenant_id, seller_id)`, D3-C nested participant, D3-B isolation-boundary rejected) and recorded "the aggregate records the lifecycle (onboarding) so a later step is additive, not a redesign" (D3 option C verdict); **§3.4 Step 4 facet f** ("셀러 온보딩 흐름 + 실 IAM provisioning") is the deferred follow-up this ADR realizes. ADR-042 does **not** re-decide D1–D8 — it executes the named Step-4 facet f on top of the D3 aggregate.

**Siblings:** [ADR-MONO-036](ADR-MONO-036-born-unified-identity-provisioning.md) (ACCEPTED 2026-06-15) — the **born-unified identity provisioning** model whose `resolveOrCreate` mint primitive (fail-soft, keyed on (tenant, normalized-email)) ADR-042 **reuses verbatim** for the seller-operator account; the seller-operator and a same-email consumer converge on one central identity *by construction* (P1 same-origin issuance, D5 here). [ADR-MONO-037](ADR-MONO-037-ecommerce-account-lifecycle-projection.md) (ACCEPTED 2026-06-15) — the ecommerce account-lifecycle *projection* (IAM `account.*` → ecommerce reaction) whose **fail-soft availability stance** (P2/P5) ADR-042's D3 mirrors. ADR-042 is the **seller-principal half** of the marketplace: 036 mints consumer/operator identity at birth; 037 projects account lifecycle into ecommerce domain state; 042 makes the *seller* a real provisioned operator principal (consuming 036's primitive) and wires its deactivation. It does **not** re-decide ADR-036 P1–P6 or ADR-037 P1–P6.

**Decision driver:** ecommerce sellers are an attribution label backed by a trusted token claim with no real IAM account, no credential, no identity, and no lifecycle past ACTIVE (the 2026-06-18 code finding). Making seller onboarding mint a real account in code would silently bake the availability posture of the whole seller-onboarding path — the trade-off (fail-soft "onboarding never blocks on IAM, the seller simply cannot operate until ACTIVE" vs fail-closed "no seller row exists without a provisioned account") is a genuine HARDSTOP-09 architecture decision, exactly the trade-off ADR-036 P2 took for the consumer/operator mint and ADR-037 P5 took for the lifecycle consumers. Choosing it (and the aggregate-vs-new-service placement, and the deactivation mechanism) in code without a record would misrepresent the seller-onboarding availability and lifecycle model. This ADR is that record; TASK-BE-402 is the implement-ready child gated on its ACCEPT.

**Related:** [ADR-MONO-030 §3.4 Step 4 facet f](ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md) (the deferred "셀러 온보딩 흐름 + 실 IAM provisioning" facet); ADR-036 P1/P2 (the `resolveOrCreate` fail-soft mint primitive D5 reuses); `projects/iam-platform/apps/admin-service/.../infrastructure/client/AccountServiceClient.java` + `IamClientCredentialsTokenProvider.java` (the client_credentials-JWT internal-call pattern D2 mirrors); account-service `POST /internal/tenants/{tenantId}/accounts` (`TenantProvisioningController` / `ProvisionAccountUseCase` — the seller-operator account mint) + `POST /internal/tenants/{tenantId}/identities:resolveOrCreate` (`ResolveOrCreateIdentityController`) + `POST /internal/accounts/{accountId}/lock` (`AccountLockController`) / `PATCH /internal/tenants/{t}/accounts/{accountId}/status` (`TenantProvisioningController#changeStatus`) — the internal EPs reused (verified present); ecommerce `Seller` / `SellerStatus` / `RegisterSellerService` / `AdminSellerController` (the D1 extension target); ADR-MONO-025 axis-2 + `SellerScopeContextFilter` `X-Seller-Scope` (the runtime seller-scope authz path D6 leaves UNCHANGED); [multi-tenancy-and-marketplace.md §3](../../projects/ecommerce-microservices-platform/specs/features/multi-tenancy-and-marketplace.md) (the seller-lifecycle spec section M4 updates from "보류" to "real onboarding").

---

## 1. Context

### 1.1 What a "seller" is today (as-built, 2026-06-18)

```
 SELLER ONBOARDING (today)
 AdminSellerController POST /api/admin/sellers
        │  (X-User-Role == ADMIN)
        ▼
 RegisterSellerService.register(cmd)
        │
        ▼
 sellers row: (tenant_id, seller_id, display_name, status=ACTIVE)   ◄── the ENTIRE provisioning

 SELLER AUTHORITY (today)
 operator token  ──►  gateway forwards  X-Seller-Scope: <seller_id>  ──►  SellerScopeContextFilter
                                                                            (ADR-025 axis-2 seller-scoped read)
        (a TRUSTED claim — no account/credential/identity backs it)
```

Three facts, each code-verified:

- **Seller = one `sellers` row, ACTIVE only.** `Seller.register()` produces a `(tenant_id, seller_id)` aggregate with `SellerStatus = ACTIVE`; `SellerStatus` has exactly one constant (`ACTIVE`). There is no PENDING / SUSPENDED / CLOSED — no lifecycle.
- **Authority is a trusted token claim.** The seller's operating authority is the gateway-forwarded `X-Seller-Scope` claim (the operator token's seller-scope, TASK-BE-363/375), read by `SellerScopeContextFilter` into `SellerScopeContext` and consumed by the ADR-025 axis-2 seller-scoped read. **No IAM account, credential, or identity exists behind the seller** — the claim is simply trusted.
- **No provisioning, no deactivation.** `RegisterSellerService` calls no IAM service; there is no path to suspend, close, or deactivate a seller, and nothing to deactivate (no backing account).

So a "seller" is an **attribution label** (a `seller_id` stamped on products / order-lines) plus a **trusted scope claim**, not a real principal. ADR-030 D3 anticipated this: it recorded the seller aggregate as the production form precisely so onboarding/lifecycle could be added additively ("a later step is additive, not a redesign").

### 1.2 The two halves of "make the seller a real principal"

```
problem = [ no real IAM account behind the seller ]  +  [ no lifecycle / no deactivation ]
                       ↑ D2 + D5                                    ↑ D1 + D4
              mint a seller-operator account               extend the Seller aggregate
              (reuse ADR-036 resolveOrCreate)              (PENDING_PROVISIONING/ACTIVE/SUSPENDED/CLOSED)
              + converge identity by email                 + lock the backing account on suspend/close
```

The provisioning half is *integration* (call account-service to mint the account + identity, store the ids). The lifecycle half is *domain* (the state machine + the deactivation reaction). Both are required for the seller to stop being a trusted-claim shim and become a backed principal.

### 1.3 The onboarding-availability trade-off (the crux — HARDSTOP-09)

Making the seller "real" means the seller-operator account is created **as part of** onboarding. That couples onboarding to the IAM (account-service / identity-infra) availability. ADR-036 P2 already took a position for the consumer/operator mint (**fail-SOFT** — registration must not hard-fail on identity-infra unavailability), and ADR-037 P5 took the same stance for the lifecycle consumers. The same trade-off now applies to seller onboarding:

- **fail-CLOSED onboarding** — the onboarding transaction provisions the account first and aborts if account-service / identity infra is down → a hard "every seller is backed" guarantee, but **IAM downtime blocks all seller onboarding** (and a half-provisioned seller would need a compensating rollback).
- **fail-SOFT onboarding** — the seller row is created IMMEDIATELY as `PENDING_PROVISIONING`; provisioning is attempted and is RETRYABLE; on success the seller transitions to `ACTIVE`; on IAM unavailability the seller STAYS `PENDING_PROVISIONING` (onboarding never blocks; the seller simply cannot operate until ACTIVE). Mirrors ADR-036 P2 / ADR-037 P5 exactly.

Choosing this in code silently bakes the seller-onboarding availability posture. It is a HARDSTOP-09 decision (D3). **The user explicitly chose fail-soft.**

### 1.4 The underspecified points this ADR must resolve (HARDSTOP-09)

- **(a)** Where do the seller lifecycle + the account/identity linkage live — the existing `Seller` aggregate, or a new seller-service? — **D1**.
- **(b)** How is the seller-operator account minted, and authenticated to account-service? — **D2**.
- **(c)** What is the **availability stance** of the onboarding mint (fail-soft vs fail-closed), given ADR-036/037 chose fail-soft and onboarding is operator-facing? — **D3 (CRUX)**.
- **(d)** How is a seller **deactivated** (suspend/close) — what happens to the backing account? — **D4**.
- **(e)** How does the seller-operator identity **converge** with a same-email consumer (born-unified)? — **D5**.
- **(f)** What is the **authz** impact — does the runtime seller-scope path change? — **D6**.

Each would bake the seller-onboarding model if chosen in code. This ADR records the decisions (HARDSTOP-09 remediation: decide first, PAUSE until ACCEPTED); implementation is post-ACCEPTED (TASK-BE-402).

---

## 2. Decision

> Direction is **CHOSEN-PROPOSED**; finalised (byte-unchanged) at ACCEPTED per the staged-child pattern. **No code / schema change in this ADR.** Grounded in the 2026-06-18 code investigation (seller = one ACTIVE `sellers` row + a trusted `X-Seller-Scope` claim; no backing account/credential/identity; no lifecycle; `RegisterSellerService` calls no IAM service). **D3 fail-soft is the user-chosen availability crux.**

### D1 — placement: extend the existing `Seller` aggregate in product-service; NO new seller-service (additive, ADR-030 D3-C)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Extend the existing `Seller` aggregate in product-service: add lifecycle states (`PENDING_PROVISIONING` / `ACTIVE` / `SUSPENDED` / `CLOSED`) + nullable `account_id` + `identity_id` columns (additive Flyway, backfill existing sellers → `ACTIVE` with null account/identity = behavior-unchanged). Onboarding + provisioning + deactivation live in the existing `RegisterSellerService` / `AdminSellerController` surface.** | Smallest change consistent with ADR-030 D3-C (seller is a participant *inside* a tenant, nested under `(tenant_id, seller_id)` — already in product-service). The aggregate D3 reserved for exactly this lifecycle addition. No new deployable, no new DB, no new gateway route. | **CHOSEN** — ADR-030 D3-C placed the seller here precisely so onboarding/lifecycle is additive; a new service would re-litigate D3-B (seller as isolation boundary) which D3 rejected. |
| B. New `seller-service` owning seller accounts + lifecycle. | A dedicated marketplace seller deployable. | **Rejected** — re-opens ADR-030 D3-B (seller-as-boundary, rejected); the seller is a *participant in a tenant*, not a tenant; a new service adds a deployable, a DB, gateway wiring, and cross-service reads for a lifecycle that is 4 states + 2 nullable columns on an existing aggregate. |
| C. Keep seller column-only; bolt provisioning onto a side table. | Avoid touching the aggregate. | **Rejected** — ADR-030 D3 already chose the aggregate over column-only ("the production form so a later step is additive"); a side table fragments the seller's own lifecycle off its aggregate. |

### D2 — provisioning: product-service → account-service internal EPs via client_credentials JWT, mirroring admin-service `AccountServiceClient` + ADR-036 `resolveOrCreate`

| Option | Mechanics | Verdict |
|---|---|---|
| **A. On seller onboarding, product-service calls account-service [1] `POST /internal/tenants/{tenantId}/accounts` (a seller-scoped role, e.g. `SELLER`) to mint the seller-operator account and [2] `POST /internal/tenants/{tenantId}/identities:resolveOrCreate` (born-unified identity, D5), authenticated with a GAP `client_credentials` Bearer JWT — MIRRORING admin-service `AccountServiceClient` + `IamClientCredentialsTokenProvider` (ADR-005 단계 3b) and ADR-036's `ProvisionAccountUseCase` / `resolveOrCreate` usage. The returned `accountId` / `identityId` are stored on the seller.** | Reuses the proven internal-call pattern (client_credentials JWT, `/internal/**` dual-allow JWT-or-`X-Internal-Token`) and the existing account-mint + identity-mint EPs verbatim — no new account-service code, no new mechanism. The seller-operator account carries the seller-scoped role that yields the existing ADR-025 seller-scope claim (D6 net-zero). | **CHOSEN** — smallest integration that makes the seller backed; reuses the admin client blueprint + ADR-036 primitive; account-service EPs verified present (no invention). |
| B. Emit a `seller.onboarded` event; an IAM consumer provisions asynchronously. | Fully decoupled. | **Deferred refinement** — clean but heavier (a new event + IAM-side consumer) for a value the synchronous (fail-soft) call delivers in-band; A is sufficient. Reconsider if onboarding throughput ever needs decoupling. |
| C. Have product-service write directly into account_db. | No cross-service hop. | **Rejected** — violates IAM-as-sole-issuance-authority (ADR-001); account-service owns the registry; cross-DB writes from a consuming domain are forbidden. |

### D3 — availability stance of the onboarding mint: **fail-SOFT** (seller born `PENDING_PROVISIONING`, provisioning retryable, onboarding never blocks) — **USER-CHOSEN**

| Option | Mechanics | Verdict |
|---|---|---|
| **A. The seller is created IMMEDIATELY as `PENDING_PROVISIONING`; IAM provisioning (D2) is then attempted and is RETRYABLE; on success the seller transitions to `ACTIVE` (account/identity ids stored); on IAM unavailability the seller STAYS `PENDING_PROVISIONING` (logged `warn`, re-provision path available). Onboarding NEVER blocks on IAM infra — the seller simply cannot operate until ACTIVE (a `PENDING_PROVISIONING` seller is not yet a usable principal). Mirrors ADR-036 P2 / ADR-037 P5 exactly.** | Seller onboarding never hard-fails on account-service / identity-infra downtime (the availability-critical invariant). The unprovisioned case is rare (infra down) and self-heals via the retryable re-provision path. Consistent with the sibling identity ADRs' deliberate fail-soft stance; no half-provisioned seller needing rollback (the row is the seller; the account is attached on success). | **CHOSEN — user-explicit** — preserves the ADR-036/037 fail-soft availability stance; avoids making IAM a hard dependency of seller onboarding; the trade-off (a transient `PENDING_PROVISIONING` seller) is bounded and retryable. |
| B. fail-CLOSED onboarding (no seller row unless the account provisions first; abort + roll back on IAM down). | Hard "every seller is backed" guarantee. | **Rejected — user-declined** — lets IAM downtime block all seller onboarding; contradicts ADR-036 P2 / ADR-037 P5 fail-soft for the same primitive; introduces a half-provisioned-rollback failure mode. The marginal benefit (no transient `PENDING_PROVISIONING`) is not worth the availability + complexity regression. |

> **D3 invariant.** "Backed seller" is a **strong default, not a hard transactional invariant**. The system never *depends* on `account_id` being non-null at any instant — a `PENDING_PROVISIONING` seller is simply not yet operable (it holds no seller-scope authority until ACTIVE). This is what lets the mint be fail-soft. Existing pre-ADR sellers backfill to `ACTIVE` with null account/identity (behavior-unchanged; the trusted-claim path still serves them — D6).

### D4 — deactivation: operator SUSPEND/CLOSE → product-service locks/deactivates the stored `account_id` (idempotent, net-zero)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. An operator SUSPEND/CLOSE on the seller transitions the aggregate (`ACTIVE → SUSPENDED` / `→ CLOSED`) and, if an `account_id` is stored, product-service calls account-service to lock/deactivate it — `POST /internal/accounts/{accountId}/lock` (suspend) or `PATCH /internal/tenants/{t}/accounts/{accountId}/status` (deactivate/close), same client_credentials JWT. Idempotent (re-suspending an already-SUSPENDED seller / re-locking an already-locked account is a no-op log) and net-zero (a seller with null `account_id` — pre-ADR or PENDING — transitions state without an IAM call). The reverse `account.status.changed` subscription [IAM-initiated lock → seller SUSPENDED] is a NAMED follow-up, not built now.** | The seller's domain lifecycle drives the backing account's status; locking the account revokes the seller-operator's ability to authenticate, so deactivation is real, not just a label. Idempotent + null-safe = net-zero for legacy/PENDING sellers. | **CHOSEN** — completes the principal lifecycle (a real seller can be really deactivated); reuses existing lock/status EPs; the reverse projection is honestly deferred, not silently omitted. |
| B. Status label only (no account lock). | Simplest. | **Rejected** — a "SUSPENDED" seller whose account is still active can still authenticate + carry the seller-scope claim; the deactivation would be cosmetic. |
| C. Build the bidirectional `account.status.changed` projection now. | Most complete. | **Deferred (not rejected)** — the correct end-state, but the IAM→seller direction is a separate consumer with its own idempotency story; doing it now doubles the slice. Named as a tracked follow-up. |

### D5 — identity: reuse `resolveOrCreate` so a consumer + a seller-operator on the same email converge on ONE central identity (ADR-036 born-unified)

The seller-operator account's identity is minted via the SAME `POST /internal/tenants/{tenantId}/identities:resolveOrCreate` primitive ADR-036 P1 uses, keyed on (tenant, normalized-email). So a person who is both a consumer (shopper) and a seller-operator on the same email converges on the **same** central identity *by construction* (same-origin issuance, ADR-036 § P6 — NOT email auto-merge; the convergence is structural via `uk_identities_tenant_email`). Fail-soft (D3): a failed identity mint leaves `identity_id` null, reconciled on re-provision. **Implementation MUST verify** (TASK-BE-402 AC) the `reuseExisting` opt-in semantics produce the intended convergence (mirroring ADR-036's verify-don't-assume obligation for the mint).

### D6 — authz net-zero: the seller-operator account yields the EXISTING ADR-025 seller-scope claim; the RUNTIME authz path is UNCHANGED

| Option | Mechanics | Verdict |
|---|---|---|
| **A. The seller-operator account carries a seller-scoped role (D2) that, via the existing IAM assume-tenant role-derivation (ADR-032 D5 / ADR-035 4a) + gateway claim forwarding, yields the SAME `X-Seller-Scope` claim the runtime already consumes (`SellerScopeContextFilter` → ADR-025 axis-2). NO change to the identity/role MODEL, the gateway, the filter, or the seller-scoped read. The ONLY change is that the trusted claim is now BACKED by a real provisioned account instead of being asserted with no account behind it. Consumes ADR-036/032/025 contracts; adds none.** | The runtime authz path (the part that actually enforces seller-scope) is byte-unchanged — net-zero. The improvement is solely at the *provisioning* layer (the claim is now real). No new authz surface, no role-model change, no enforcement change. | **CHOSEN** — the whole point is to back the existing claim, not to invent a new authz axis; net-zero on the enforcement path is the safety property. |
| B. Introduce a new seller-authorization model / claim. | A dedicated seller authz axis. | **Rejected** — re-invents ADR-025 axis-2 (seller-scope) which already works; this ADR is about *provisioning* the principal, not *re-modeling* its authorization. |

**Safety invariants (D1–D6):**

- **Additive** — new lifecycle states + 2 nullable columns + a provisioning call; existing sellers backfill to `ACTIVE` (null account/identity), behavior-unchanged.
- **Idempotent** — re-onboarding is harmless (`resolveOrCreate` + the account mint are idempotent on (tenant, email); re-provisioning a PENDING seller converges); re-suspend / re-lock is a no-op.
- **No-overwrite** — a stored non-null `account_id` / `identity_id` is never silently replaced (re-provision only fills nulls).
- **Fail-soft (D3)** — onboarding never blocks on IAM infra; the seller stays `PENDING_PROVISIONING` and is retryable.
- **Authz net-zero (D6)** — the runtime seller-scope enforcement path is byte-unchanged; only the claim's backing changes.
- **No identity-model change** — consumes ADR-036/032/025 contracts; born-unified is same-origin issuance, never email auto-merge (ADR-034 §1.3 / ADR-036 P6 reaffirmed).
- **IAM is the sole issuance authority** (ADR-001) — account-service mints the seller-operator account + identity; product-service stores the returned ids; no cross-DB write.

---

## 3. Consequences

### 3.1 Hard invariants this ADR carries

- **A seller is a real provisioned principal** — onboarding mints a seller-operator IAM account + (born-unified) identity; the seller is no longer a trusted-claim shim.
- **The seller has a real lifecycle** — `PENDING_PROVISIONING → ACTIVE` (on provision) and `ACTIVE → SUSPENDED / CLOSED` (operator-driven), with the backing account locked/deactivated on suspend/close.
- **Onboarding never blocks on IAM infra** — fail-soft (D3); a seller stays `PENDING_PROVISIONING` and is retryable on IAM downtime.
- **Identity converges born-unified** — a same-email consumer + seller-operator share one central identity by construction (D5), no email auto-merge.
- **Authz is net-zero** — the runtime seller-scope enforcement path is unchanged; only the claim's backing is now real (D6).

### 3.2 What this ADR does NOT do (deferred to ACCEPTED + post-ACCEPTED execution)

- No implementation in this ADR: no aggregate change, no migration, no provisioning client, no deactivation wiring — all post-ACCEPTED (TASK-BE-402, § 3.3).
- No new seller-service (D1-B rejected); no direct cross-DB write to account_db (D2-C rejected); no async `seller.onboarded` provisioning event (D2-B deferred).
- No reverse `account.status.changed` → seller-SUSPENDED projection (D4-C deferred follow-up); no order-line / settlement reaction to seller closure.
- No new seller-authorization model (D6-B rejected); no change to the gateway, `SellerScopeContextFilter`, ADR-025 axis-2 read, or the identity/role model.
- Does **not** re-decide ADR-030 D1–D8, ADR-036 P1–P6, ADR-037 P1–P6, ADR-032 D1–D6, or ADR-025 axis-2 — it executes ADR-030 §3.4 Step 4 facet f by reusing those ACCEPTED contracts.

### 3.3 Future-self (post-ACCEPTED execution roadmap — sketch, finalised at ACCEPTED; PAUSED until ACCEPTED)

1. **TASK-BE-402 M1 (D1)** — extend the `Seller` aggregate: `SellerStatus += PENDING_PROVISIONING / SUSPENDED / CLOSED`; add nullable `account_id` / `identity_id` + Flyway (next free V-number, backfill existing → `ACTIVE` null/null); transitions register→PENDING_PROVISIONING, provision-success→ACTIVE, suspend/close. Model = **Opus** (lifecycle state machine + migration). *Makes the seller a stateful aggregate.*
2. **TASK-BE-402 M2 (D2/D3/D5)** — IAM provisioning client in product-service (mirror admin `AccountServiceClient` + `IamClientCredentialsTokenProvider`); wire onboarding to fail-soft (create PENDING → attempt provision → ACTIVE on success; store ids; failure leaves PENDING, retryable). Model = **Opus** (cross-service provisioning + fail-soft). *Mints the real seller-operator account + born-unified identity.*
3. **TASK-BE-402 M3 (D4)** — operator suspend/close → lock/deactivate the stored account (idempotent, null-safe). Model = **Opus** (lifecycle deactivation). *Makes deactivation real.*
4. **TASK-BE-402 M4 (contracts + spec)** — document the seller-provisioning internal-call usage under `specs/contracts/`; update `multi-tenancy-and-marketplace.md §3` seller-lifecycle (onboarding now real, not "보류"); update product-service `architecture.md`; mark ADR-030 §3.4 Step 4 facet f realized. Model = **Sonnet** (contract/doc). *Contract-first record.*
- **Optional follow-ups:** reverse `account.status.changed` → seller-SUSPENDED projection (D4-C); async `seller.onboarded` provisioning event (D2-B); order-line / settlement reaction to seller closure; seller self-service onboarding surface.

> **UNPAUSED — ACCEPTED 2026-06-18 (TASK-BE-402).** The § 3.3 roadmap proceeds dependency-correct from this ACCEPTED branch. **TASK-BE-402** (ecommerce `tasks/ready/`) is the implement-ready child; its HARDSTOP-09 ADR-prerequisite is **satisfied in this same branch** (ADR-042 ACCEPTED). It proceeds M1→M2→M3→M4; D1–D6 are finalised and not re-litigated at execution. Begin with **M1** (the lifecycle state machine + migration — the unblocker that makes the seller a stateful aggregate). The mint stays fail-SOFT (onboarding never blocks on IAM infra — the ADR-036/037 availability stance, user-chosen D3) and identity convergence is same-origin issuance, never email auto-merge (the ADR-034 §1.3 / ADR-036 P6 no-silent-merge invariant).

---

## 4. Alternatives Considered

- **New seller-service (D1-B).** Rejected — re-opens ADR-030 D3-B (seller-as-isolation-boundary, rejected); a seller is a participant in a tenant, not a tenant.
- **Direct cross-DB write to account_db (D2-C).** Rejected — violates IAM-as-sole-issuance-authority (ADR-001).
- **Async `seller.onboarded` provisioning event (D2-B).** Deferred — heavier than the in-band fail-soft call for the same value.
- **fail-CLOSED onboarding (D3-B).** Rejected — **user explicitly chose fail-soft**; fail-closed lets IAM downtime block all seller onboarding and adds a half-provisioned-rollback failure mode, contradicting ADR-036 P2 / ADR-037 P5.
- **Status-label-only deactivation (D4-B).** Rejected — a SUSPENDED seller whose account is still active can still authenticate; the deactivation would be cosmetic.
- **Bidirectional `account.status.changed` projection now (D4-C).** Deferred — the correct end-state, but a separate consumer with its own idempotency; doubles the slice. Named follow-up.
- **New seller-authorization model (D6-B).** Rejected — re-invents ADR-025 axis-2; this ADR provisions the principal, it does not re-model its authorization.

## 5. Relationship to ADR-030 / ADR-036 / ADR-037 / ADR-025

| | ADR-MONO-030 | ADR-MONO-036 | ADR-MONO-037 | ADR-MONO-025 |
|---|---|---|---|---|
| Relationship | **Parent** — realizes **§3.4 Step 4 facet f** (seller onboarding + real IAM provisioning) on the D3-C seller aggregate; does not re-decide D1–D8 | **Sibling (reused)** — reuses the born-unified `resolveOrCreate` mint primitive (P1) for the seller-operator account + identity; mirrors P2 fail-soft (D3); no email auto-merge (P6) | **Sibling (stance)** — mirrors the ecommerce-side fail-soft availability stance (P5) for the onboarding mint; 037 = consumer/lifecycle projection, 042 = seller-principal provisioning | **Reused** — the seller-operator account yields the existing axis-2 seller-scope claim; the runtime authz path is byte-unchanged (D6 net-zero) |

ADR-042 is the **seller-principal half** of the ecommerce marketplace identity story: ADR-036 mints consumer/operator identity at birth, ADR-037 projects IAM account lifecycle into ecommerce, and ADR-042 makes the *seller* a real provisioned operator principal (consuming 036's primitive) with a real lifecycle — closing the last named ADR-030 Step-4 marketplace facet (f).

## 6. Status Transition History

| Date | Transition | Decision summary | Trigger | PR |
|---|---|---|---|---|
| 2026-06-18 | created PROPOSED | D1 = extend the existing `Seller` aggregate in product-service (lifecycle states + nullable `account_id`/`identity_id`), NO new seller-service (ADR-030 D3-C additive); reject seller-service (re-opens D3-B) + side-table. D2 = product-service → account-service `POST /internal/tenants/{t}/accounts` + `identities:resolveOrCreate` via client_credentials JWT, mirroring admin `AccountServiceClient` + ADR-036 `resolveOrCreate`; reject async event (deferred) + direct cross-DB write (ADR-001 violation). D3 (CRUX, HARDSTOP-09) = fail-SOFT onboarding (seller born `PENDING_PROVISIONING`, provisioning retryable, onboarding never blocks, → ACTIVE on success); reject fail-closed (IAM downtime blocks onboarding + rollback failure mode). D4 = operator suspend/close → lock/deactivate the stored `account_id` (idempotent, null-safe, net-zero); reject status-label-only (cosmetic) + bidirectional projection now (deferred). D5 = reuse `resolveOrCreate` so a same-email consumer + seller-operator converge on one central identity (ADR-036 born-unified, same-origin issuance, no auto-merge). D6 = the seller-operator account yields the existing ADR-025 axis-2 seller-scope claim; the RUNTIME authz path is UNCHANGED (net-zero); reject new seller-authz model. **Parent ADR-030 (realizes §3.4 Step 4 facet f on the D3-C aggregate); sibling of ADR-036 (reuses the born-unified mint, mirrors P2 fail-soft) + ADR-037 (mirrors the ecommerce fail-soft stance) + ADR-025 (reuses axis-2 seller-scope).** Doc-only PROPOSED record. | 2026-06-18 code investigation: a "seller" is one `sellers` row (`SellerStatus = ACTIVE` only) + a trusted `X-Seller-Scope` operator-token claim (TASK-BE-363/375) with NO backing IAM account/credential/identity and no lifecycle past ACTIVE; `RegisterSellerService` calls no IAM service → the seller is an attribution label, not a principal. Making onboarding mint a real account in code would bake the seller-onboarding availability posture (fail-soft vs fail-closed) — a HARDSTOP-09 architecture decision (plus the aggregate-vs-service placement + the deactivation mechanism). Realizes the last deferred ADR-030 §3.4 Step 4 marketplace facet (f). | #<this> |
| 2026-06-18 | PROPOSED → ACCEPTED | D1–D6 CHOSEN-PROPOSED direction **finalised byte-unchanged** (ACCEPTED *finalises*, does not re-decide); § 1–5/7 byte-identical to the PROPOSED draft; flip = Status + History ACCEPTED clause + this row + § 3.3 PAUSED→UNPAUSED + the TASK-BE-402 HARDSTOP-09 banner lift. **The user explicitly ACCEPTED, choosing D3 fail-soft** (the HARDSTOP-09 availability crux) over the rejected fail-closed alternative. Parent-of-ADR-030-§3.4-facet-f / sibling-of-ADR-036 (reuses born-unified mint, mirrors P2 fail-soft) + ADR-037 (mirrors fail-soft stance) + ADR-025 (reuses axis-2) scope unchanged (ADR-030 D1–D8 / ADR-036 P1–P6 / ADR-037 P1–P6 / ADR-032 D1–D6 / ADR-025 axis-2 not re-litigated). Delivered in the same branch as PROPOSED — the user ACCEPTED after the presented-decisions gate but before the PROPOSED record independently merged; the staged-child governance trail is preserved in-branch (both § 6 rows). The ACCEPT gate was honored (PROPOSED decisions presented + the fail-soft choice awaited, NOT a self-ACCEPT). | User-explicit ACCEPT (TASK-BE-402 — "D3 fail-soft 선택" after the PROPOSED D1–D6 decisions were presented for the explicitly-required ACCEPT gate; sibling staged-child ADR-036/037 precedent) | #<this> |

> **ACCEPTED 2026-06-18 (TASK-BE-402).** The § 3.3 execution roadmap is now **UNPAUSED**; TASK-BE-402 proceeds dependency-correct (M1→M2→M3→M4) from this ACCEPTED branch, its HARDSTOP-09 ADR-prerequisite satisfied here. D1–D6 are finalised and not re-litigated at execution. **ADR-030 D1–D8, ADR-036 P1–P6, ADR-037 P1–P6, ADR-032 D1–D6, ADR-025 axis-2 are not re-litigated here** — ADR-042 realizes ADR-030 §3.4 Step 4 facet f by reusing those ACCEPTED contracts. The onboarding mint stays fail-SOFT (onboarding never blocks on IAM infra — the user-chosen D3, the ADR-036/037 availability stance) and identity convergence is same-origin issuance, never email auto-merge (the ADR-034 §1.3 / ADR-036 P6 no-silent-merge invariant).

## 7. Provenance

- 2026-06-18 code investigation (this ADR's grounding):
  - `Seller` / `SellerStatus` — `SellerStatus` has exactly one constant (`ACTIVE`); `Seller.register()` → ACTIVE; no PENDING/SUSPENDED/CLOSED, no `account_id`/`identity_id` (no backing principal).
  - `RegisterSellerService.register()` — writes one `sellers` row; calls NO IAM service (no provisioning).
  - Seller authority = the gateway-forwarded `X-Seller-Scope` operator-token claim (TASK-BE-363/375), read by `SellerScopeContextFilter` into `SellerScopeContext`, consumed by the ADR-025 axis-2 seller-scoped read — a trusted claim with no account behind it.
  - account-service internal EPs verified present: `POST /internal/tenants/{tenantId}/accounts` (`TenantProvisioningController` → `ProvisionAccountUseCase`, `ProvisionAccountRequest{email,password,displayName,locale,timezone,roles,operatorId}` → `ProvisionAccountResponse{accountId,tenantId,email,status,roles,createdAt}`); `POST /internal/tenants/{tenantId}/identities:resolveOrCreate` (`ResolveOrCreateIdentityController`); `POST /internal/accounts/{accountId}/lock` (`AccountLockController`); `PATCH /internal/tenants/{tenantId}/accounts/{accountId}/status` (`TenantProvisioningController#changeStatus`). Role validation (`AccountRoleName`) accepts any `^[A-Z][A-Z0-9_]*$` ≤64 — a `SELLER` role is accepted (no allowlist block).
  - admin-service `AccountServiceClient` + `IamClientCredentialsTokenProvider` — the client_credentials-JWT internal-call blueprint (ADR-005 단계 3b) D2 mirrors; `resolveOrCreateIdentity(..., reuseExisting)` is the fail-soft provisioning call (returns null on downstream failure) ADR-036 P2 established.
- ADR-MONO-030 D3 (seller aggregate, D3-C nested participant, D3-B isolation-boundary rejected) + §3.4 Step 4 facet f (the deferred "셀러 온보딩 흐름 + 실 IAM provisioning" this ADR realizes).
- ADR-MONO-036 P1 (`resolveOrCreate` born-unified mint, (tenant,email)-keyed, `uk_identities_tenant_email` race-safe) + P2 (fail-soft mint — onboarding never blocks) + P6 (born-unified = same-origin issuance, not email auto-merge) — the primitive + availability stance + no-merge invariant D2/D3/D5 reuse.
- ADR-MONO-037 P5 (fail-soft ecommerce lifecycle consumers) — the sibling ecommerce-side availability stance D3 mirrors.
- ADR-MONO-025 axis-2 (`org_scope`/`seller_id` ABAC data-scope) + `SellerScopeContextFilter` `X-Seller-Scope` — the runtime seller-scope authz path D6 leaves byte-unchanged.

분석=Opus 4.8 / 구현=Opus 4.8 (ecommerce seller onboarding → real IAM seller-operator provisioning under HARDSTOP-09; realizes ADR-030 §3.4 Step 4 facet f on the D3-C seller aggregate; reuses the ADR-036 born-unified `resolveOrCreate` mint + the admin-service `AccountServiceClient` client_credentials-JWT internal-call blueprint; the onboarding-mint availability stance is the crux **user-chosen fail-soft** decision [D3]; deactivation locks the backing account [D4]; authz net-zero [D6]; staged-child ADR pattern per ADR-019/020/021/023/024/032/033/034/035/036/037; PROPOSED → user-gated ACCEPT, self-ACCEPT prohibited).
