# Task ID

TASK-MONO-269

# Title

Author **ADR-MONO-037 PROPOSED** — Project the IAM account lifecycle into ecommerce. Records the decision to **re-point ecommerce onboarding off the decommissioned `auth.user.signed-up` to IAM `account.created`** and to **wire the `account.deleted` two-phase (grace-withdraw → post-grace GDPR-anonymize) reaction that the existing IAM consumer contract already mandates** (`account-events.md` TASK-BE-258 + `consumer-integration-guide.md` § GDPR downstream). Post-IAM (TASK-BE-132, ecommerce `auth-service` decommissioned) the account-lifecycle bridge is **orphaned**: a dead `auth.user.signed-up` producer, two live consumers (`user-service` `UserSignedUpConsumer` + `notification-service` `UserSignedUpEventConsumer`) still listening to it, and IAM `account.created`/`account.deleted` consumed by no ecommerce service. The **crux finding** (2026-06-15 code investigation): the GDPR reaction is **not an open compliance question** — IAM already *mandates* the consumer obligation — ecommerce simply never wired it. So this ADR **records ecommerce's alignment to the existing IAM consumer contract** and decides the three genuinely-ecommerce-local items that would bake compliance/availability posture if chosen in code: (P1) onboarding-PII sourcing (`account.created` is **emailHash-only** — the old event carried raw `email`+`name`), (P2/P3) the `account.deleted` two-phase reaction + the **order-PII cascade scope boundary** (user-service profile vs order-service-held PII — compliance-sensitive), (P4/P5) the id-mapping + fail-soft/idempotency stance, (P6) staging + safety invariants. Doc-only; **ACCEPTED + implementation (TASK-BE-388) are separate user-explicit-intent-gated tasks** (staged-child pattern, ADR-019/020/021/023/024/032/033/034/035/036). **Self-ACCEPT prohibited.**

# Status

done

> **DONE (2026-06-15, 3-dim verified — impl PR #1647 squash `6461ac382`).** PROPOSED → ACCEPTED in the impl PR, user-explicit ACCEPT gate honored. ADR-MONO-037 authored Status PROPOSED on `task/mono-269-account-lifecycle-projection-adr` (worktree-isolated, main parked); the P1-P6 decisions were **presented for review first** (the explicitly-required gate, self-ACCEPT prohibited); the user then gave explicit intent *"ACCEPT (같은 PR에서 flip)"* → ADR-MONO-037 flipped PROPOSED → ACCEPTED in the same PR (Status + History ACCEPTED clause + § 6 ACCEPTED row + § 3.3 UNPAUSED + TASK-BE-388 HARDSTOP-09 banner lift), ADR-003a audit rows #43 (PROPOSED) + #44 (ACCEPTED) appended (sibling ADR-036/MONO-266 same-PR pattern). Doc-only (no `apps/` code, no `platform/contracts/` change, no migrations, no seed). **Lifecycle close (ready → done) follows merge + 3-dim verification.** § 3.3 execution roadmap UNPAUSED — next = **TASK-BE-388 M1** (onboarding `account.created` re-point + minimal profile, P1), now implement-ready (banner lifted).

# Owner

architecture

# Task Tags

- docs
- adr
- security

---

# Dependency Markers

- **sibling of**: ADR-MONO-036 (ACCEPTED 2026-06-15 — born-unified identity provisioning). ADR-036 and ADR-037 are the two halves of the identity lifecycle: 036 = **birth/provisioning** (mint the central identity at record creation, iam-internal, fail-soft); 037 = **death/projection** (a consuming domain [ecommerce] reacts to the central identity's lifecycle — creation → onboard; deletion → withdraw + GDPR-anonymize). No surface overlap; 037 mirrors 036's fail-soft availability stance. ADR-037 does NOT re-decide ADR-036 P1-P6.
- **family-child of**: ADR-MONO-032 (ACCEPTED — unified-identity model). The orphaned bridge exists because ADR-032 made IAM the sole account authority and decommissioned ecommerce `auth-service` (the `auth.user.signed-up` producer). ADR-037 consumes the IAM account-events contract ADR-032's family left authoritative; it does NOT re-decide D1-D6 (nor ADR-034 U1-U7 / ADR-035 O1-O6).
- **aligns to (existing obligation, NOT a new decision)**: `account-events.md` § Consumer Obligations (TASK-BE-258) + `consumer-integration-guide.md` § GDPR downstream — the two-phase `account.deleted` consumer (`anonymized=false` grace → logical/status delete; `anonymized=true` post-grace → PII mask) is already a standing consumer obligation ecommerce currently violates. The ADR records ecommerce's conformance.
- **triggered by**: 2026-06-15 code investigation of the orphaned account-lifecycle bridge, surfaced while removing the stale HTTP withdrawal endpoint (TASK-BE-387). Dead `auth.user.signed-up` producer (only ecommerce `auth-service`, excluded from build by BE-132); two live consumers on the dead topic; IAM `account.created`/`deleted` unconsumed. The onboarding-PII-sourcing change (emailHash-only event), the order-PII cascade boundary (compliance-sensitive), and the consumer availability/idempotency stance are HARDSTOP-09 architecture decisions + user-explicit steer 2026-06-15 ("권장 경로대로 진행" — author the ADR PROPOSED → user-gated ACCEPT).
- **unblocks (post-ACCEPT)**: TASK-BE-388 (ecommerce `tasks/ready/`) — the implement-ready child carrying the HARDSTOP-09 ADR-PREREQUISITE banner; MUST NOT be implemented until ADR-037 is ACCEPTED. On ACCEPT its banner lifts and it proceeds M1→M4.
- **keeps disjoint**: no identity/authorization/role-namespace change — this is lifecycle *projection* into a consuming domain, not an identity or authz change (ADR-032/034/035/036 untouched).
- **defers (follow-ups)**: order-service PII cascade (P3-B, documented boundary); synchronous IAM profile-fetch onboarding enrichment (P1-B); `processed_event` dedup store (P5-B); `account.locked`/`status.changed` projection.

# Goal

Publish ADR-MONO-037 PROPOSED so the ecommerce account-lifecycle projection can be wired against a recorded decision — (P1) re-point onboarding to `account.created` with a minimal profile (PII from the OIDC token, not the event), (P2) the IAM-prescribed two-phase `account.deleted` reaction (grace-withdraw → post-grace anonymize) aligning ecommerce to the existing TASK-BE-258 obligation, (P3) v1 = user-service profile PII with the order-service cascade as a documented deferred boundary, (P4) `accountId = profile.userId = sub` (verify-don't-assume), (P5) fail-soft consumers with natural idempotency via the monotonic withdraw/anonymize transition — rather than an implicit one, with ACCEPTED and execution (TASK-BE-388) gated as separate user-explicit-intent tasks.

# Scope

- `docs/adr/ADR-MONO-037-ecommerce-account-lifecycle-projection.md` (NEW, Status PROPOSED) — P1 onboarding `account.created` re-point + minimal profile (reject keep-PII-from-event [impossible] + sync IAM fetch [deferred]) + P2 two-phase `account.deleted` consumer aligned to TASK-BE-258 (reject single-phase + anonymize-only) + P3 v1 user-service profile PII with documented order-PII cascade follow-up (reject full-cascade-now [deferred] + status-only-undocumented [F2]) + P4 id mapping `accountId=userId=sub` (verify obligation) + P5 fail-soft + natural-idempotency (reject dedup-store-now [deferred] + fail-closed) + P6 staged M1→M4 additive net-zero + safety invariants.
- `docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md` § 3 audit table — append row #43 (Meta-policy: ADR-037 PROPOSED publish; same one-off pre-author category as rows #37/#39/#41). Row #44 (ACCEPTED) appended at the gated ACCEPTED transition.
- Doc-only. NO contract/schema/code change (HARDSTOP-09 remediation: record the decision, PAUSE until ACCEPTED).

# Acceptance Criteria

- **AC-1** ADR-MONO-037 exists with Status PROPOSED, P1-P6 CHOSEN-PROPOSED, the code-finding evidence (dead `auth.user.signed-up` producer; two live consumers on it; IAM `account.created` emailHash-only; existing TASK-BE-258 / consumer-integration-guide GDPR obligation; `withdrawProfile` non-idempotent; `UserProfile` PII columns), and the § 3.3 execution roadmap (M1-M4 + deferred follow-ups).
- **AC-2** The decision driver names the orphaned bridge + the three baked-if-coded postures (onboarding-PII sourcing from an emailHash-only event; the order-PII cascade boundary [compliance-sensitive]; the consumer availability/idempotency stance) + the crux that the GDPR pattern is pre-decided IAM-side (TASK-BE-258) so the ADR records *alignment*, not a novel compliance decision.
- **AC-3** P1-C (keep email/name from event — impossible), P2-B (single-phase), P2-C (anonymize-only), P3-C (status-only-undocumented), P5-C (fail-closed) are recorded as rejected with reasons; P1-B (sync IAM fetch), P3-B (full cascade now), P5-B (dedup store) are recorded as deferred (not rejected) with the promotion condition.
- **AC-4** ADR-037 positions itself as a **sibling of ADR-036** (death/projection half of the identity lifecycle; 036 = birth/provisioning) and a **family-child of ADR-032** (consumes the IAM account-events contract; does NOT re-decide D1-D6 / U1-U7 / O1-O6 / P1-P6).
- **AC-5** ADR-003a § 3 audit row #43 (PROPOSED) appended (append-only; rows #1-#42 byte-unchanged).
- **AC-6** Doc-only diff (no `apps/` code, no `platform/contracts/` change, no migrations, no seed change).
- **AC-7 (gated)** ADR-037 lands as PROPOSED first; the ACCEPTED transition (Status ACCEPTED + § 6 ACCEPTED row + § 3.3 UNPAUSED + TASK-BE-388 banner lift + ADR-003a row #44) is a **separate step gated on user-explicit intent** — NOT a same-PR self-flip without it. **Self-ACCEPT prohibited.**

# Related Specs

- `docs/adr/ADR-MONO-036-born-unified-identity-provisioning.md` (the birth/provisioning sibling — fail-soft stance mirrored)
- `docs/adr/ADR-MONO-032-unified-identity-roles-model.md` (the unified-identity model under which ecommerce auth-service was decommissioned)
- `projects/iam-platform/specs/contracts/events/account-events.md` (`account.created` / `account.deleted` schemas + § Consumer Obligations TASK-BE-258 — the obligation this ADR aligns ecommerce to)
- `projects/iam-platform/specs/features/consumer-integration-guide.md` (§ GDPR downstream — the prescribed two-phase consumer + the `account.created` minimal-row-insert onboarding obligation)
- `projects/ecommerce-microservices-platform/specs/services/user-service/architecture.md` + `.../notification-service/architecture.md` (the two consumers re-pointed)
- `projects/ecommerce-microservices-platform/tasks/ready/TASK-BE-388-iam-account-lifecycle-event-bridge-repoint.md` (the implement-ready child gated on this ADR)

# Related Contracts

- IAM `account-events.md` (`account.created` / `account.deleted` payloads — consumed, not changed by this PROPOSED ADR).
- ecommerce `specs/contracts/events/` subscription contracts for `account.created` + `account.deleted` — **authored in TASK-BE-388 M4**, not in this PROPOSED ADR.
- No `platform/contracts/` change (lifecycle projection is additive; no wire-shape change in ADR scope).

# Edge Cases

- ADR-003a audit table is append-only — verify rows #1-#42 byte-unchanged.
- ADR-037 is PROPOSED only — no ACCEPTED self-flip, no code/contract change (M1-M4 are post-ACCEPTED execution under TASK-BE-388 per § 3.3).
- P1 must NOT assume `account.created` carries raw email/name — it is emailHash-only; onboarding PII is sourced from the OIDC token / profile-update.
- P2 must branch on the event's own `anonymized` flag (grace-entry false → withdraw; post-grace true → anonymize), NOT a self-scheduled `gracePeriodEndsAt` timer — the producer re-emits at grace end.
- P3 must DOCUMENT the order-PII cascade as a tracked deferral (F2) — a status/profile-only reaction with no documented boundary would misrepresent GDPR deletion handling.
- P5 must keep `withdrawProfile()` idempotent (relax its `throw UserProfileNotFoundException` to a no-op on missing/already-terminal) so at-least-once re-delivery + the two `account.deleted` emissions are safe without a dedup store.

# Failure Scenarios

- If the ADR is authored AND code/contract is implemented in the same task → violates HARDSTOP-09 remediation (decision must precede + be ACCEPTED). This task is doc-only; implementation is TASK-BE-388.
- If ADR-037 is **self**-ACCEPTED (AI-decided, without user-explicit intent) → violates the explicit gate. The ACCEPTED transition is a separate user-explicit-intent step.
- If the re-point is treated as a pure topic swap (keeping email/name from the event) → impossible (P1-C): `account.created` carries no raw PII; onboarding sourcing genuinely changes.
- If the delete reaction ships single-phase / status-only with no documented order-PII boundary → ships a known, hidden TASK-BE-258 GDPR non-compliance (F2). ADR-037 mandates the two-phase reaction + the documented cascade boundary.
- If the consumers are fail-closed → a poison message stalls the lifecycle partition; ADR-037 keeps them fail-soft (P5-A), mirroring ADR-036 P2.
