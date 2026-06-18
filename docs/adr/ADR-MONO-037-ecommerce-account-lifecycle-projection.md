# ADR-MONO-037 — Project the IAM account lifecycle into ecommerce: re-point onboarding off the decommissioned `auth.user.signed-up` to IAM `account.created`, and wire the `account.deleted` two-phase (withdraw → GDPR-anonymize) reaction the existing IAM consumer contract already mandates

**Status:** ACCEPTED

**History:** PROPOSED 2026-06-15 (TASK-MONO-269 — records the **ecommerce account-lifecycle projection model**: how IAM's account-lifecycle events [`account.created` / `account.deleted`] project into ecommerce domain state [user-service profile onboarding + withdrawal + GDPR PII anonymization, and the notification-service welcome path]. Post-IAM [TASK-BE-132, ecommerce `auth-service` decommissioned], the ecommerce account-lifecycle event wiring is **orphaned**: the only producer of `auth.user.signed-up` is the excluded-from-build ecommerce `auth-service`, yet two live consumers [`user-service` `UserSignedUpConsumer` `groupId=user-service`, `notification-service` `UserSignedUpEventConsumer` `groupId=notification-service`] still `@KafkaListener` it, while IAM's `account.created` / `account.deleted` [`AccountOutboxPollingScheduler`] are **consumed by no ecommerce service**. So onboarding rides a topic no live service emits, and account deletion has no ecommerce reaction — even though `UserProfileService.withdrawProfile()` + `UserWithdrawn` [consumed by order-service] sit ready as the orphaned wiring target [TASK-BE-387 kept them]. The **crux finding** [2026-06-15 code investigation]: the GDPR reaction is **not an open compliance question** — IAM already *mandates* the consumer obligation [`account-events.md` TASK-BE-258 + `consumer-integration-guide.md` § GDPR downstream prescribe the exact two-phase `account.deleted` handling: `anonymized=false` grace-entry → logical/status delete; `anonymized=true` post-grace → PII mask]; ecommerce simply never wired it. This ADR therefore **records ecommerce's alignment to the existing IAM consumer contract** and decides the three ecommerce-specific items that *are* genuinely underspecified and would bake compliance/availability posture if chosen in code: [1] onboarding-PII sourcing [IAM `account.created` carries only `emailHash`, no raw email/name — the old `auth.user.signed-up` carried raw `email` + `name`], [2] the order-PII cascade scope boundary [user-service profile PII vs order-service-held shipping/recipient PII], [3] the id-mapping + availability/idempotency stance. **Doc-only; ACCEPTED + implementation are separate user-explicit-intent-gated tasks [staged-child pattern, ADR-019/020/021/023/024/032/033/034/035/036]. Self-ACCEPT prohibited.**) · ACCEPTED 2026-06-15 (TASK-MONO-269 — user-explicit *"ACCEPT (같은 PR에서 flip)"* after the PROPOSED decisions [P1–P6] were presented for the explicitly-required ACCEPT gate; the gate was honored — the PROPOSED record was presented and review awaited before any flip, **NOT a self-ACCEPT**. P1–P6 CHOSEN-PROPOSED direction **finalised byte-unchanged** — ACCEPTED *finalises*, does not re-decide; § 1 Context + § 2 Decision tables + § 3 Consequences + § 4 Alternatives + § 5 Relationship + § 7 Provenance byte-identical to the PROPOSED draft; flip = Status + this clause + § 6 ACCEPTED row + § 3.3 PAUSED→UNPAUSED + the TASK-BE-388 HARDSTOP-09 banner lift. Delivered in the same PR as the PROPOSED record [the user ACCEPTED after reviewing the presented decisions but before the PROPOSED record independently merged — the staged-child governance trail is preserved *within* the PR: both § 6 rows + ADR-003a audit rows #43 PROPOSED / #44 ACCEPTED, mirroring ADR-033/034/035/036]. ADR-032 D1–D6, ADR-034 U1–U7, ADR-035 O1–O6, ADR-036 P1–P6 not re-litigated.)

**Sibling:** [ADR-MONO-036](ADR-MONO-036-born-unified-identity-provisioning.md) (ACCEPTED 2026-06-15) — the **birth/provisioning** half of the identity-lifecycle pair: how a new record *acquires* its central identity at creation (mint-at-birth, fail-soft). ADR-037 is the **death/lifecycle-projection** half: how a *downstream domain* (ecommerce) *reacts* to the central identity's lifecycle (creation → onboard; deletion → withdraw + GDPR-anonymize). 036 is IAM-internal write-side provisioning; 037 is ecommerce-side read/projection of IAM-emitted lifecycle events. **No overlap** — 036 mints identity at birth (iam-scoped), 037 projects the lifecycle into a consuming domain (ecommerce-scoped). Edge case (TASK-BE-388 § Edge Cases) confirmed: "this is the account-lifecycle PROJECTION into ecommerce (death + onboarding), orthogonal to ADR-036 born-unified PROVISIONING."

**Parent / family:** [ADR-MONO-032](ADR-MONO-032-unified-identity-roles-model.md) (ACCEPTED 2026-06-14) — the unified-identity model under which IAM is the sole account authority and ecommerce `auth-service` was decommissioned (the root cause of the orphaned bridge). ADR-037 does **not** re-decide ADR-032 D1–D6, nor ADR-034 U1–U7 / ADR-035 O1–O6 / ADR-036 P1–P6 — it consumes the IAM account-events contract those ADRs left authoritative, and projects it into ecommerce.

**Decision driver:** The orphaned bridge (dead producer, two live consumers on the dead topic, unconsumed IAM lifecycle events) leaves ecommerce profile creation depending on a topic no live service emits and account deletion with no ecommerce reaction. Wiring the fix in code would silently bake three postures: (a) **how ecommerce GDPR-reacts to a deletion** (the order-PII cascade boundary — compliance-sensitive), (b) **where onboarding PII comes from** now that the lifecycle event no longer carries it (a real behavior change to profile creation + the welcome notification), and (c) **the availability/idempotency stance** of the new consumers. (a) in particular is compliance-sensitive — shipping a status-only reaction to a GDPR `account.deleted` without a documented PII-retention stance would misrepresent deletion handling. That makes this a HARDSTOP-09 cross-project integration decision. This ADR is that record; TASK-BE-388 is the implement-ready child gated on its ACCEPT.

**Related:** [account-events.md](../../projects/iam-platform/specs/contracts/events/account-events.md) (`account.created` schema v2 — `accountId`/`tenantId`/`emailHash`/`status`/`locale`/`createdAt`; `account.deleted` schema v2 — `accountId`/`tenantId`/`reasonCode`/`actorType`/`actorId`/`deletedAt`/`gracePeriodEndsAt`/`anonymized`; **§ Consumer Obligations TASK-BE-258** — the existing GDPR mandate this ADR aligns ecommerce to); [consumer-integration-guide.md § GDPR downstream](../../projects/iam-platform/specs/features/consumer-integration-guide.md) (the prescribed two-phase `account.deleted` consumer code + the `account.created` "minimal row insert / JIT pre-empty" onboarding obligation); ecommerce `UserSignedUpConsumer` / `UserSignedUpEventConsumer` (the two dead-topic consumers), `UserProfileService.withdrawProfile()` + `UserWithdrawnSpringEvent` (the orphaned wiring target — TASK-BE-387), `UserProfile.create(userId, email, name)` (the onboarding write that currently persists event-carried PII); TASK-BE-132 (auth-service decommission — root cause), TASK-BE-387 (removed the stale HTTP withdrawal endpoint — this ADR wires the event-driven reaction it pointed at).

---

## 1. Context

### 1.1 The orphaned bridge (as-built, 2026-06-15)

```
 PRODUCER (dead)                  TOPIC                       CONSUMERS (live, on a dead topic)
 ecommerce auth-service     ──►  auth.user.signed-up   ──►   user-service   UserSignedUpConsumer       (groupId=user-service)
 (DECOMMISSIONED, BE-132,                                     notification-service UserSignedUpEventConsumer (groupId=notification-service)
  excluded from Gradle build)

 IAM account-service        ──►  account.created        ──►   (NO ecommerce consumer)
 (AccountOutboxPollingScheduler)  account.deleted        ──►   (NO ecommerce consumer)
                                  account.status.changed
                                  account.locked / unlocked
                                  account.roles.changed
```

Three facts, each code-verified:

- **Producer is dead.** `auth.user.signed-up` is emitted only by the ecommerce `auth-service` (`UserSignupRepublishService` + `AuthEventKafkaBridge`), which TASK-BE-132 excluded from the build. No live service emits it.
- **Two live consumers still listen.** `user-service` `UserSignedUpConsumer` creates the profile (`UserProfile.create(userId, email, name)`); `notification-service` `UserSignedUpEventConsumer` sends the `WELCOME` notification (`name` + `email` template vars). Both `@KafkaListener(topics = "auth.user.signed-up")`.
- **IAM lifecycle events are unconsumed.** IAM publishes `account.created` / `account.deleted` (and locked/unlocked/status/roles), but no ecommerce service consumes any of them.

### 1.2 The crux that the backlog finding (TASK-BE-388) framed as open — but is largely pre-decided

TASK-BE-388 framed **D-a (GDPR reaction depth)** as "the crux decision, not a wiring detail." The 2026-06-15 investigation shows the GDPR *pattern* is **already decided on the IAM side** and is a standing **consumer obligation**, not an ecommerce judgment call:

- **`account-events.md` § Consumer Obligations (TASK-BE-258):** "`account.deleted(anonymized=true)` 이벤트를 수신한 모든 소비 서비스는 자체 보유 PII를 마스킹할 GDPR/PIPA 의무를 진다. 유예 진입(`anonymized=false`) 이벤트는 마스킹 대상이 아니다." External consumers (WMS/ERP/SCM **and by the same rule ecommerce**) must mask their domain-held PII within **24h** of `anonymized=true`.
- **`consumer-integration-guide.md` § GDPR downstream** prescribes the exact two-phase consumer, verbatim:
  ```java
  @KafkaListener(topics = "account.deleted", groupId = "...-account-sync")
  public void onAccountDeleted(...) {
      if (!"<tenant>".equals(p.tenantId())) return;
      if (Boolean.TRUE.equals(p.anonymized())) userPiiAnonymizer.anonymize(p.accountId()); // phase 2
      else                                     userCache.softDelete(p.accountId());        // phase 1
  }
  ```

So ecommerce is **out of conformance with a contract that already binds it** — not facing a novel compliance decision. This ADR's job is to **record that alignment** and decide the genuinely-ecommerce-local items below.

### 1.3 The three items that *are* underspecified (HARDSTOP-09)

- **(a) Onboarding-PII sourcing.** IAM `account.created` carries `accountId` / `tenantId` / **`emailHash` (SHA256[:10], PII-masked)** / `status` / `locale` / `createdAt` — **no raw email, no name.** The old `auth.user.signed-up` carried raw `email` + `name`, which `UserProfile.create(userId, email, name)` persisted and the `WELCOME` notification personalized. Re-pointing to `account.created` **removes the PII the onboarding write and the welcome template consumed.** Where does onboarding PII come from now? — this is a behavior change, not a topic swap.
- **(b) Order-PII cascade scope.** User-service holds profile PII (`email` / `nickname` / `phone` / `profileImageUrl` — admin-queryable by email via `listUsers`). Order-service holds order PII (shipping addresses, recipient names). The TASK-BE-258 obligation says "domain-held PII" — does the v1 `anonymized=true` reaction cover **only** user-service's profile, or **cascade** to order-service? (compliance-sensitive scope boundary.)
- **(c) id mapping + availability/idempotency.** Is `account.{created,deleted}.accountId` the same identifier as ecommerce `profile.userId`? And what is the consumers' availability/idempotency stance (user-service has **no** `processed_event` store today; `withdrawProfile()` **throws** `UserProfileNotFoundException` on a missing profile — not idempotent as written)?

Each would bake ecommerce's compliance/availability posture if chosen in code. This ADR records the decisions (HARDSTOP-09 remediation: decide first, PAUSE until ACCEPTED); implementation is post-ACCEPTED (TASK-BE-388).

---

## 2. Decision

> Direction is **CHOSEN-PROPOSED**; to be finalised (byte-unchanged) at ACCEPTED per the staged-child pattern. **No code / schema / contract change in this ADR.** Grounded in the 2026-06-15 code investigation (dead-topic consumers; IAM `account.created` emailHash-only; existing TASK-BE-258 / consumer-integration-guide GDPR obligation; `withdrawProfile` non-idempotent; `UserProfile` PII columns).

### P1 — re-point onboarding to IAM `account.created`, creating a **minimal** profile; onboarding PII is sourced from the OIDC token / profile-update, **not** the lifecycle event

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Both consumers switch `auth.user.signed-up` → `account.created`. `user-service` creates a MINIMAL profile keyed on `accountId` (status ACTIVE, tenant-scoped; `email`/`nickname` left empty/lazily-populated — `account.created` has no raw PII). The profile's email/name are sourced from the OIDC id_token (`profile`/`email` scopes) at first login or via the existing profile-update flow, NOT the lifecycle event. `notification-service` re-points to `account.created` and sends a non-PII-personalized `WELCOME` (or no welcome until the profile is enriched — implementation chooses; the template must not assume event-carried `name`/`email`). Matches the IAM consumer-guide obligation (line 433: `account.created` → "도메인 user 캐시에 신규 row insert (또는 JIT 미리 비워두기)").** | New profiles are born from the live event; onboarding no longer depends on a dead topic. PII flows through the OIDC token (its proper, consented channel), not a fan-out event — privacy-aligned with IAM's deliberate `emailHash`-only masking. The welcome path degrades gracefully. | **CHOSEN** — the only option consistent with IAM emitting no raw onboarding PII; aligns to the documented "minimal row insert" obligation; keeps onboarding off any synchronous IAM dependency. |
| B. On `account.created`, `user-service` synchronously calls an IAM account-profile read API to fetch email/name and persist a full profile. | Full profile at onboarding. | **Deferred refinement** — adds a synchronous cross-service dependency on the onboarding path (a new failure mode) for data the OIDC token already delivers at login; promotable only if a full profile is required *before* first login. Not v1. |
| C. Keep sourcing email/name from the event. | Status quo write shape. | **Rejected — impossible.** `account.created` does not carry raw email/name (emailHash only); there is no event field to read. |

### P2 — `account.deleted` reaction: the IAM-prescribed **two-phase** consumer, keyed on the event's own `anonymized` flag (align to the existing TASK-BE-258 obligation)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. `user-service` consumes `account.deleted` and branches on `anonymized`, exactly as the consumer-integration-guide prescribes: (phase 1) `anonymized=false` (grace entry) → resolve profile by `accountId` → `withdrawProfile()` → status WITHDRAWN + `UserWithdrawn` emitted (already consumed by order-service); logical delete, blocks new activity. (phase 2) `anonymized=true` (post-grace) → anonymize user-service-held profile PII (`email`/`nickname`/`phone`/`profileImageUrl` masked/NULL), preserving `userId` for FK/audit/order integrity. Tenant-filter on envelope `tenant_id` (M5 net-zero, as the current consumers do).** | Brings ecommerce into conformance with the binding TASK-BE-258 obligation it currently violates; reuses the orphaned `withdrawProfile()` + `UserWithdrawn` wiring target (TASK-BE-387); the two phases map 1:1 onto the IAM guide's reference code. | **CHOSEN** — direct alignment to the existing IAM consumer contract; no new compliance model invented; reuses ready wiring. |
| B. Single-phase: react only to grace-entry with status withdrawal; never anonymize. | Simpler. | **Rejected** — ships a *known* GDPR non-compliance (the TASK-BE-258 `anonymized=true` masking obligation goes unmet); F2. |
| C. React only to `anonymized=true` (skip the grace-entry withdrawal). | One handler. | **Rejected** — leaves the account live/searchable in ecommerce through the entire grace window; the guide mandates phase-1 logical delete at grace entry. |

### P3 — order-PII cascade **scope boundary**: v1 anonymizes **user-service profile PII**; the **order-service** cascade is a **documented deferred follow-up** (not silently omitted)

| Option | Mechanics | Verdict |
|---|---|---|
| **A. v1 covers user-service profile PII (the primary identity-bearing store). The order-service-held PII (shipping addresses, recipient names) cascade is captured as a DOCUMENTED known scope boundary + a follow-up task — a separate order-service consumer of `account.deleted(anonymized=true)` (or a reaction to `UserWithdrawn`). The ADR records the limitation explicitly (F2): "v1 anonymizes ecommerce profile PII; order-PII anonymization is a tracked follow-up, not yet wired."** | Right-sizes the v1 vertical slice to the profile store while keeping the compliance boundary HONEST and visible (mirrors ADR-036's "design-not-build" honesty: do the principal piece, document the deferred piece rather than hide it). order-service is a distinct consumer with its own PII model and no anonymizer today — coupling it into this bridge would expand scope materially. | **CHOSEN** — covers the principal PII store now; the order cascade is named, tracked, and non-silent (no misrepresentation of deletion handling). |
| B. Full cascade (user-service + order-service) in v1. | Most complete. | **Deferred (not rejected)** — the correct end-state, but order-service has no `PiiAnonymizer` and its own address/recipient model; building it doubles the slice. Promote when the order-PII anonymizer lands. |
| C. Status-only, no anonymization, no documented boundary. | Smallest. | **Rejected** — violates TASK-BE-258 AND hides it; the undocumented gap is the anti-pattern (F2). |

### P4 — id mapping: `account.{created,deleted}.accountId` **is** the ecommerce `profile.userId` (= OIDC `sub`); implementation carries a verification obligation

Post-IAM (ecommerce `auth-service` decommissioned, ADR-032), there is no separate ecommerce-minted user id — the IAM `accountId` **is** the canonical subject, and is the `sub` the OIDC token already carries for profile lookup. So `account.created.accountId` → new `profile.userId`, and `account.deleted.accountId` → profile resolution key, **directly**. **Implementation MUST verify** (TASK-BE-388 AC) that `accountId` equals the identifier `profile.userId` is keyed on elsewhere (the OIDC `sub`); if any translation layer exists, define the resolution before wiring. (Same verify-don't-assume obligation ADR-036 carried for the mint id.)

### P5 — availability / idempotency: **fail-SOFT** consumers; idempotency via the **naturally-idempotent status transition**, not a new dedup store

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Both new consumers are fail-SOFT (a malformed/unresolvable message logs + is skipped or DLQ'd per the standard `<topic>.dlq` rule; a transient downstream failure retries — never blocks the partition indefinitely on a poison message), mirroring ADR-036 P2 and the existing consumers' `log.warn`+skip on null fields. Idempotency is achieved by making the reaction naturally idempotent: `withdrawProfile()` and the anonymize step treat already-WITHDRAWN / not-found / already-anonymized as a no-op log (NOT a throw) — so re-delivery (Kafka at-least-once) and the two `account.deleted` emissions (false then true) are safe without a `processed_event` store. The IAM-prescribed `eventId` dedup remains the guidance; user-service relies on the idempotent transition.** | No new persistence; the status transition is monotonic (ACTIVE → WITHDRAWN → anonymized) so re-processing converges. Requires relaxing `withdrawProfile()`'s current `throw UserProfileNotFoundException` to a no-op on the missing/already-terminal case (a small, behavior-preserving idempotency hardening). | **CHOSEN** — smallest change that makes the consumers safe under at-least-once + two-phase re-emission; avoids over-engineering a dedup store for a monotonic transition. |
| B. Add a `processed_event` store to user-service (as settlement-service has). | Explicit dedup. | **Deferred** — justified only if a *non-idempotent* reaction is later added; for a monotonic status/anonymize transition it is over-engineering. |
| C. Fail-CLOSED consumers (block on downstream unavailability). | Strong delivery. | **Rejected** — a poison message or a downstream blip would stall the lifecycle partition; contradicts the fail-soft stance ADR-036 P2 took for the sibling identity path. |

### P6 — staged migration (each sub-step additive, net-zero, main-GREEN) + safety invariants

| Sub-step | Change | Net effect |
|---|---|---|
| **M1 — onboarding re-point (P1)** | Both consumers switch `auth.user.signed-up` → `account.created`; `user-service` writes a minimal profile; `notification-service` welcome de-PII'd; remove the dead-topic listeners. | New profiles born from the live event; dead topic retired. Existing profiles untouched (net-zero). |
| **M2 — delete reaction phase 1 (P2)** | `user-service` consumes `account.deleted(anonymized=false)` → `withdrawProfile()` (idempotent, fail-soft) → `UserWithdrawn`. | Grace-entry withdrawal wired; reuses the orphaned target. Additive. |
| **M3 — delete reaction phase 2 (P2/P3)** | `user-service` consumes `account.deleted(anonymized=true)` → anonymize profile PII (preserve `userId` FK). | TASK-BE-258 obligation met for the profile store. Additive. |
| **M4 — contracts + docs (P1/P3)** | Add ecommerce subscription contracts for `account.created` + `account.deleted`; retire the `auth.user.signed-up` producer assumption from `authentication.md` / event docs; document the onboarding behavior change + the order-PII cascade follow-up boundary. | Contract-first record; the deferred order cascade is named, not hidden. |
| **(deferred follow-ups)** | ~~order-service `account.deleted(anonymized=true)` PII cascade (P3-B)~~ — **DONE (TASK-BE-401):** order-service `AccountDeletedConsumer` (group `order-service-account-sync`) tombstones the shipping-address snapshot on every order for the subject (all statuses/tenants), preserving `order_id`/`user_id` FK + business data; both ecommerce PII stores now meet TASK-BE-258. synchronous IAM profile-fetch onboarding enrichment (P1-B); user-service `processed_event` store (P5-B). | P3-B closed; P1-B/P5-B out of v1 scope; tracked. |

**P6 safety invariants:**

- **Onboarding availability preserved** — `account.created` creates a minimal profile with **no synchronous IAM call** (P1-A); onboarding never blocks on IAM infra. Mirrors ADR-036's fail-soft stance.
- **GDPR obligation honored, boundary documented** — phase-2 anonymization meets TASK-BE-258 for the profile store; the order-PII cascade is an explicit, tracked deferral, never a silent omission (F2).
- **Idempotent + fail-soft** — the monotonic ACTIVE → WITHDRAWN → anonymized transition is re-delivery-safe; consumers never block the partition.
- **id mapping verified, not assumed** — `accountId = profile.userId = sub` carries an implementation verification obligation (P4).
- **No identity / authorization change** — this is lifecycle *projection* into a consuming domain, not an identity or role change. ADR-032/034/035/036 (D1–D6 / U1–U7 / O1–O6 / P1–P6) are not re-decided.
- **IAM is the sole lifecycle authority** — ecommerce only *reacts* to IAM-emitted `account.*` events; it never originates account creation/deletion (auth-service decommissioned).

---

## 3. Consequences

### 3.1 Hard invariants this ADR carries

- **Ecommerce onboarding rides the live `account.created`** — no live dependency on the decommissioned `auth.user.signed-up`.
- **`account.deleted` gets the IAM-prescribed two-phase reaction** — grace-entry withdrawal + post-grace profile anonymization; ecommerce moves from violating TASK-BE-258 to conforming (for the profile store).
- **Onboarding PII flows through the OIDC token, not a fan-out event** — aligned with IAM's deliberate `emailHash`-only masking.
- **The order-PII cascade is a documented, tracked boundary** — deletion handling is not misrepresented.
- **No identity/authorization change; no availability regression** — projection only; fail-soft consumers.

### 3.2 What this ADR does NOT do (deferred to ACCEPTED + post-ACCEPTED execution)

- No implementation: no consumer re-point, no entity change, no contract edit, no anonymizer — all post-ACCEPTED (TASK-BE-388, § 3.3).
- No order-service PII cascade (P3-B deferred); no synchronous IAM profile-fetch onboarding (P1-B deferred); no `processed_event` store (P5-B deferred).
- No change to `account.locked` / `account.unlocked` / `account.status.changed` / `account.roles.changed` reactions (out of scope — this ADR is the create/delete bridge only; locked/status projection is a possible later slice).
- Does not re-decide ADR-032 D1–D6, ADR-034 U1–U7, ADR-035 O1–O6, or ADR-036 P1–P6 — it consumes the IAM account-events contract they left authoritative.

### 3.3 Future-self (post-ACCEPTED execution roadmap — sketch, finalised at ACCEPTED; PAUSED until ACCEPTED)

1. **TASK-BE-388 M1 (P1)** — re-point both consumers `auth.user.signed-up` → `account.created`; minimal profile; de-PII welcome; remove dead-topic listeners. Model = **Opus** (cross-project event re-point + onboarding semantics). *Closes the dead-producer dependency.*
2. **TASK-BE-388 M2 (P2)** — `user-service` consumes `account.deleted(anonymized=false)` → idempotent `withdrawProfile()` (relax the throw) + `UserWithdrawn`. Model = **Opus** (GDPR-adjacent lifecycle wiring). *Wires the grace-entry withdrawal.*
3. **TASK-BE-388 M3 (P2/P3)** — `user-service` consumes `account.deleted(anonymized=true)` → profile PII anonymization. Model = **Opus** (compliance-sensitive PII handling). *Meets TASK-BE-258 for the profile store.*
4. **TASK-BE-388 M4 (contracts)** — ecommerce `account.created` / `account.deleted` subscription contracts; retire the `auth.user.signed-up` producer assumption; document the onboarding change + order-PII boundary. Model = **Sonnet** (contract/doc). *Contract-first record.*
- **Optional follow-ups:** ~~order-service PII cascade (P3-B)~~ **DONE (TASK-BE-401)** — order-service consumes `account.deleted(anonymized=true)` and anonymizes order-held PII; synchronous IAM profile-fetch onboarding enrichment (P1-B); `processed_event` dedup store (P5-B); `account.locked`/`status.changed` projection.

> **UNPAUSED — ACCEPTED 2026-06-15 (TASK-MONO-269).** The § 3.3 roadmap proceeds dependency-correct from this ACCEPTED main. **TASK-BE-388** (ecommerce `tasks/ready/`) is the implement-ready child; its HARDSTOP-09 ADR-PREREQUISITE banner is **lifted in this same PR** (the prerequisite is now satisfied — ADR-037 ACCEPTED). It proceeds M1→M2→M3→M4; P1–P6 are finalised and not re-litigated at execution. Begin with **M1** (onboarding `account.created` re-point + minimal profile — the unblocker that retires the dead-topic dependency).

---

## 4. Alternatives Considered

- **Treat the re-point as a pure topic swap (P1-C).** Rejected — impossible: `account.created` carries no raw email/name; onboarding PII sourcing genuinely changes.
- **Single-phase / status-only delete reaction (P2-B).** Rejected — ships a known, undocumented TASK-BE-258 GDPR non-compliance.
- **Full order-PII cascade in v1 (P3-B).** Deferred, not rejected — correct end-state, but order-service has no anonymizer; doubles the slice. Tracked as a documented boundary.
- **Synchronous IAM profile-fetch at onboarding (P1-B).** Deferred — adds a synchronous IAM dependency on the onboarding path for data the OIDC token already delivers.
- **`processed_event` dedup store now (P5-B).** Deferred — over-engineering for a monotonic, naturally-idempotent transition.
- **Fail-closed consumers (P5-C).** Rejected — a poison message would stall the lifecycle partition; contradicts ADR-036 P2's sibling fail-soft stance.

## 5. Relationship to ADR-032 / ADR-036

| | ADR-MONO-032 | ADR-MONO-036 |
|---|---|---|
| Relationship | **Family parent** — operates under the unified-identity model (IAM sole account authority, ecommerce `auth-service` decommissioned = the root cause); does not re-decide D1–D6 | **Sibling (the other half of the identity lifecycle)** — 036 = birth/provisioning (mint identity at record creation, iam-internal, fail-soft); 037 = death/projection (react to the identity's lifecycle in a consuming domain, ecommerce-scoped). Same fail-soft availability philosophy; no overlap in surface |

## 6. Status Transition History

| Date | Transition | Decision summary | Trigger | PR |
|---|---|---|---|---|
| 2026-06-15 | created PROPOSED | P1 = re-point onboarding to `account.created` with a MINIMAL profile (PII from OIDC token, not the event — `account.created` is emailHash-only); reject "keep email/name from event" (impossible) + sync IAM fetch (deferred). P2 = the IAM-prescribed two-phase `account.deleted` consumer (grace `anonymized=false` → withdraw; post-grace `anonymized=true` → profile PII anonymize) = align to the existing TASK-BE-258 / consumer-integration-guide obligation ecommerce currently violates; reject single-phase + anonymize-only. P3 = v1 covers user-service profile PII; order-service PII cascade is a DOCUMENTED deferred follow-up (not silent); reject full-cascade-now (deferred) + status-only-undocumented (F2). P4 = `account.{created,deleted}.accountId` = `profile.userId` = OIDC `sub` (verify-don't-assume obligation). P5 = fail-soft consumers + natural idempotency via the monotonic ACTIVE→WITHDRAWN→anonymized transition (relax `withdrawProfile`'s throw); reject new dedup store (deferred) + fail-closed. P6 = staged M1→M4 (each additive net-zero) + safety invariants. **Sibling of ADR-036 (death/projection half of the identity lifecycle); family-child of ADR-032 (unified-identity model).** Doc-only. | The 2026-06-15 code investigation of the orphaned account-lifecycle bridge (dead `auth.user.signed-up` producer; two live consumers on it; IAM `account.created`/`deleted` unconsumed) surfaced while removing the stale HTTP withdrawal endpoint (TASK-BE-387). The GDPR pattern is pre-decided IAM-side (TASK-BE-258 + consumer-integration-guide) — ecommerce is out of conformance — but the onboarding-PII sourcing (emailHash-only event), the order-PII cascade boundary (compliance-sensitive), and the consumer availability/idempotency stance are HARDSTOP-09 architecture decisions baked-if-coded. User-explicit steer 2026-06-15 ("권장 경로대로 진행" — author the ADR PROPOSED → user-gated ACCEPT). | #<this> |
| 2026-06-15 | PROPOSED → ACCEPTED | P1–P6 CHOSEN-PROPOSED direction **finalised byte-unchanged** (ACCEPTED *finalises*, does not re-decide); § 1–5/7 byte-identical to the PROPOSED draft; flip = Status + History ACCEPTED clause + this row + § 3.3 PAUSED→UNPAUSED + the TASK-BE-388 HARDSTOP-09 banner lift. Sibling-of-ADR-036 (death/projection half) / family-child-of-ADR-032 scope unchanged (D1–D6 / U1–U7 / O1–O6 / P1–P6 not re-litigated). Delivered in the same PR as PROPOSED — the user ACCEPTED after the presented-decisions gate but before the PROPOSED record independently merged; governance trail preserved in-PR (both § 6 rows + ADR-003a audit rows #43/#44), mirroring ADR-033/034/035/036. The ACCEPT gate was honored (PROPOSED decisions presented + review awaited, NOT a self-ACCEPT). | "ACCEPT (같은 PR에서 flip)" (TASK-MONO-269 — user-explicit intent after the PROPOSED P1–P6 decisions were presented for the explicitly-required ACCEPT gate; sibling ADR-036/MONO-266 same-session PROPOSED→ACCEPTED) | #<this> |

> **ACCEPTED 2026-06-15 (TASK-MONO-269).** The § 3.3 execution roadmap is now **UNPAUSED**; TASK-BE-388 proceeds dependency-correct (M1→M2→M3→M4) from this ACCEPTED main, its HARDSTOP-09 banner lifted in this same PR. P1–P6 are finalised and not re-litigated at execution. **ADR-032 D1–D6, ADR-034 U1–U7, ADR-035 O1–O6, ADR-036 P1–P6 are not re-litigated here** — ADR-037 projects the IAM account lifecycle into ecommerce and aligns it to the existing TASK-BE-258 consumer obligation. The consumers stay fail-SOFT (the lifecycle partition never blocks on a poison message — the ADR-036 availability stance) and the order-PII cascade is a documented, tracked boundary (never a silent omission).

## 7. Provenance

- 2026-06-15 code investigation (this ADR's grounding):
  - `auth.user.signed-up` producer = ecommerce `auth-service` `UserSignupRepublishService` + `AuthEventKafkaBridge`, **excluded from the Gradle build** (TASK-BE-132).
  - Live consumers on the dead topic: `user-service` `UserSignedUpConsumer` (`UserProfile.create(userId, email, name)` via `UserSignedUpHandler`) + `notification-service` `UserSignedUpEventConsumer` (`WELCOME` with `name`/`email` template vars).
  - IAM `account.created` payload = `accountId`/`tenantId`/**`emailHash`**/`status`/`locale`/`createdAt` (no raw email/name) — `account-events.md` schema v2.
  - IAM `account.deleted` payload = `accountId`/`tenantId`/`reasonCode`/`actorType`/`actorId`/`deletedAt`/`gracePeriodEndsAt`/`anonymized`; re-emitted with `anonymized=true` post-grace — `account-events.md` schema v2.
  - Existing GDPR obligation: `account-events.md` § Consumer Obligations (TASK-BE-258) + `consumer-integration-guide.md` § GDPR downstream (two-phase reference consumer + `account.created` "minimal row insert / JIT pre-empty" onboarding obligation).
  - Orphaned wiring target: `UserProfileService.withdrawProfile()` (currently **throws** `UserProfileNotFoundException` — non-idempotent) + `UserWithdrawnSpringEvent` (consumed by order-service); kept by TASK-BE-387.
  - `UserProfile` PII columns: `email` / `nickname` / `phone` / `profileImageUrl` (admin-queryable by email via `listUsers`).
- ADR-MONO-036 — the birth/provisioning sibling (mint-at-birth, fail-soft); 037 is the death/projection half, mirroring its fail-soft availability stance.
- ADR-MONO-032 — the unified-identity model under which ecommerce `auth-service` was decommissioned (the orphaned-bridge root cause).
- TASK-BE-132 (auth-service decommission), TASK-BE-387 (stale HTTP withdrawal endpoint removal — this ADR wires the event-driven reaction it pointed at), TASK-BE-388 (the implement-ready child gated on this ADR's ACCEPT).

분석=Opus 4.8 / 구현=Opus 4.8 (cross-project IAM↔ecommerce account-lifecycle projection under HARDSTOP-09; sibling of ADR-036 [death/projection half]; aligns ecommerce to the existing TASK-BE-258 / consumer-integration-guide GDPR consumer obligation; the onboarding-PII-sourcing change, order-PII cascade boundary, and fail-soft/idempotency stance are the genuinely-ecommerce-local decisions; staged-child ADR pattern per ADR-019/020/021/023/024/032/033/034/035/036; PROPOSED → user-gated ACCEPT, self-ACCEPT prohibited).
