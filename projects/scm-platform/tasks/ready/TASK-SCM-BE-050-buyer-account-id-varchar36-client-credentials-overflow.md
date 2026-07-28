# TASK-SCM-BE-050 — `actor.accountId()` overflows every `VARCHAR(36)` actor column when the caller is a client-credentials client (PO drafting hard-fails)

**Status:** ready
**Type:** TASK-SCM-BE (defect)
**Depends on / 전제:** none.
**후속 / blocks:** none required — this is a standalone defect fix.

> **Found by:** a live functional smoke check of the scm 3PL feature chain (2026-07-28, local docker-compose, real client-credentials token against `scm.local`), not by CI. `TASK-SCM-BE-048`'s reorder→PO-draft flow was exercised end-to-end and `TASK-SCM-BE-049`'s honour-sink chain could not be verified live because the PO draft step itself 500s before BE-049's routing logic gets a persisted PO to act on — even though the routing decision fires and logs correctly.

---

## Why (the gap this closes)

`purchase_orders.buyer_account_id` is declared `VARCHAR(36) NOT NULL` ([`V1__init.sql:47`](../../apps/procurement-service/src/main/resources/db/migration/procurement/V1__init.sql#L47)), and the JPA entity mirrors it exactly: [`PurchaseOrder.java:62-63`](../../apps/procurement-service/src/main/java/com/example/scmplatform/procurement/domain/po/PurchaseOrder.java#L62-L63) `@Column(name = "buyer_account_id", length = 36, nullable = false)`. Every other `VARCHAR(36)` column in this schema (`id`, `supplier_id`, `source_suggestion_id`, `destination_warehouse_id`) is UUID-shaped by convention — 36 is exactly `UUID.toString()` length. `buyer_account_id` was sized the same way, on the implicit assumption that the caller identity (`actor.accountId()`) is always a UUID.

That assumption is false for one of scm's two documented caller types. [`ActorContextJwtAuthenticationConverter.java:29`](../../apps/procurement-service/src/main/java/com/example/scmplatform/procurement/infrastructure/security/ActorContextJwtAuthenticationConverter.java#L29) does `String accountId = jwt.getSubject();` — the raw JWT `sub` claim, with no length check or normalisation. Per [`specs/integration/iam-integration.md`](../../specs/integration/iam-integration.md) Edge Case E1 (line 72-76), the `scm-platform-internal-services-client` client-credentials token — scm's documented backend-to-backend caller (line 57) — carries `sub == client_id`, i.e. the literal string `scm-platform-internal-services-client` (**37 characters**, one over the limit). Verified directly against Postgres: an insert with a 37-char `buyer_account_id` fails with `ERROR: value too long for type character varying(36)`; the identical insert with a 36-char UUID succeeds.

The failure is not cosmetic. `PurchaseOrderApplicationService.draftFromSuggestion` (and the plain `draft` path — both call sites pass `actor.accountId()` into `PurchaseOrder.createDraft(...)`, e.g. [line ~104](../../apps/procurement-service/src/main/java/com/example/scmplatform/procurement/application/PurchaseOrderApplicationService.java#L104)) throws a Hibernate `DataException` on flush, surfaced as a plain `500 INTERNAL_ERROR` (the exact non-`23505` SQLSTATE path `DataIntegrityViolationIntegrationTest` already asserts maps to 500 — this ticket is a *live trigger* of that mapping, not a new error-handling gap). Any client-credentials-authenticated caller drafting a PO — via `demand-planning-service`'s `ApproveSuggestionUseCase` → `ProcurementDraftPoClient` (which forwards the caller's bearer unchanged), or a direct `POST /api/procurement/po` — hits this. It is **not** specific to `TASK-SCM-BE-048`/`049`'s 3PL path: the same `draftFromSuggestion` method is the single shared PO-creation path for both `WMS_WAREHOUSE` and `THIRD_PARTY_LOGISTICS` destinations. CI/E2E does not catch it because those suites authenticate as an operator (UUID or email `sub`, ≤36 chars), never as the 37-char internal service client.

Two sibling columns carry the identical trap, fed by the same `actor.accountId()`, just currently `NULL`-tolerant so they degrade instead of hard-failing: `po_status_history.actor_account_id VARCHAR(36)` ([`V1__init.sql:100`](../../apps/procurement-service/src/main/resources/db/migration/procurement/V1__init.sql#L100)) and `audit_log.actor_account_id VARCHAR(36)` ([`V1__init.sql:154`](../../apps/procurement-service/src/main/resources/db/migration/procurement/V1__init.sql#L154)). A write attempt to either with the 37-char client id would fail the same way if it were ever the *first* write in a transaction that reaches that far (in the observed failure, `buyer_account_id`'s `NOT NULL` fails first since it's on the same `INSERT`).

## Scope

**In scope:**

1. **Root-cause fix, not a truncation patch.** Truncating/hashing `accountId` at write time would make `buyer_account_id` silently lossy (two different-but-truncated-alike client ids could collide, and audit trails would show a mangled value) — that trades a loud 500 for a quiet correctness bug. Decide and implement one of:
   - Widen `buyer_account_id` (+ the two sibling `actor_account_id` columns) to a width that safely accommodates OAuth2 `client_id` strings, which have no standard length ceiling — pick a defensible bound (e.g. 100 or 255) and document why, rather than guessing a new too-small number. Flyway migration(s), additive.
   - Or: mint a stable, bounded actor identifier for machine callers at the JWT-conversion boundary (e.g. a deterministic UUID derived from `client_id`) so every downstream `VARCHAR(36)` actor column keeps its UUID-shaped invariant — trades a wider blast radius (every service with the same converter pattern) for not touching schema. **This is the harder, more architecturally consistent option; a genuine call, not a default.**
   - Whichever is chosen, apply it to all three columns (`purchase_orders.buyer_account_id`, `po_status_history.actor_account_id`, `audit_log.actor_account_id`) — fixing only the one that hard-fails and leaving the two `NULL`-tolerant ones latent would just relocate the same bug.
2. **Audit sibling services for the same pattern.** `demand-planning-service`, `inventory-visibility-service`, and `logistics-service` likely have their own `ActorContext`-equivalent JWT converters and actor-storing columns (grep first — do not assume identical file names/line numbers). If the same `VARCHAR(36)` + raw-`sub` pattern exists there, it has the same latent bug even if not yet observed; fix or explicitly scope out with a reason.
3. **Tests**: unit/slice — a client-credentials-shaped `ActorContext` (37+ char accountId) drafting a PO succeeds and the stored/returned value round-trips correctly (not truncated). Slice/IT — the same via the real JWT converter + a live Postgres write (Testcontainers), for at least `purchase_orders`; extend to the sibling columns per the chosen fix's blast radius.

**Out of scope:**

- Re-litigating whether `scm-platform-internal-services-client` should have a shorter client_id — it is already provisioned (`TASK-MONO-042`, V0013) and rotating it is a separate, higher-blast-radius change with no benefit here (a different machine client could just as easily be >36 chars again).
- Any change to the BE-048/049 3PL routing logic itself — it is confirmed correct (logs the honour decision correctly); this ticket only unblocks it reaching a persisted PO.
- `X-Token-Type` header-based user/machine branching (E1's suggested alternative) — that changes API contract surface for a narrower fix than actually needed (the same overflow risk exists for *any* long-`sub` machine caller, not just ones a header happens to flag).

## Acceptance Criteria

- [ ] A PO drafted (via `draftFromSuggestion` or plain `draft`) by an actor whose `accountId` is the 37-char `scm-platform-internal-services-client` subject succeeds (2xx), and the stored `buyer_account_id` is the full, correct value (not truncated/hashed away from being verifiable against the JWT `sub`, unless the chosen design explicitly documents a derived-id mapping and provides a way to reverse/look it up).
- [ ] `po_status_history.actor_account_id` and `audit_log.actor_account_id` no longer share the same overflow risk (fixed the same way, or explicitly scoped out with a written reason).
- [ ] The `TASK-SCM-BE-048`→`049` chain (reorder suggestion → approve → PO drafted with `destinationNodeType=THIRD_PARTY_LOGISTICS` → BE-049 honour-sink record → BE-047 observation reconciles it) completes live end-to-end when driven by the `scm-platform-internal-services-client` client-credentials token — this was the originally-blocked flow; re-run it (or an equivalent IT) as verification.
- [ ] No regression to human-operator callers (UUID/email `sub`, ≤36 chars) — existing tests for those paths stay green unchanged.
- [ ] Build & Test + scm Integration CI lanes GREEN.

## Related Specs

- `projects/scm-platform/specs/integration/iam-integration.md` — Edge Case E1 (`sub` of client_credentials tokens), the client registration table (line 57).
- `projects/scm-platform/specs/services/procurement-service/data-model.md` (if it documents column widths — verify/update after the fix).

## Related Contracts

- None directly (no API/event shape changes expected) — unless the chosen fix changes what `buyer_account_id`/`actor_account_id` *mean* (e.g. a derived id instead of the raw `sub`), in which case any contract documenting that field's semantics must be updated alongside the code (contract-first).

## Edge Cases

- **A future machine client with an even longer `client_id`.** Whatever bound is chosen must not just barely fit 37 — pick a value with real headroom, and document the reasoning so the next client-credentials registration doesn't silently reopen this.
- **`po_status_history`/`audit_log` are nullable today.** Confirm whether any existing code path already writes a truncated-by-accident value there for a different reason before assuming the fix is purely additive.
- **Sibling services.** Don't assume `demand-planning-service`/`inventory-visibility-service`/`logistics-service` are unaffected without checking — they may not use the identical `ActorContextJwtAuthenticationConverter` class (each service likely has its own copy) and may size their own actor columns differently.

## Failure Scenarios

- **A — Truncation instead of a real fix.** Silently truncating `accountId` to 36 chars before storage "fixes" the 500 but makes `buyer_account_id` lossy/ambiguous — a loud failure traded for a quiet correctness bug. Reject this approach (Scope §1 already rules it out; restated here because it is the easiest wrong fix to reach for).
- **B — Fixing only `purchase_orders.buyer_account_id`.** The two sibling `actor_account_id` columns keep the identical trap; a future write path that isn't `NULL`-tolerant (or a `NOT NULL` migration on either) reopens this exact bug under a different stack trace.
- **C — Assuming CI will catch a regression.** CI/E2E currently never authenticates as the 37-char client, so a regression here would ship silently again unless the new test explicitly uses that identity (AC's slice/IT requirement exists exactly to close this).

## Notes

- 분석=Opus 4.8 / 구현 권장=**Opus** — the real work is the schema-width-vs-derived-id design call (Scope §1), not the mechanical migration once decided; getting that call wrong reopens the same class of bug under different numbers.
- 발견 경위: TASK-SCM-BE-049 라이브 기능 점검 세션(2026-07-28) — BE-046/047/048/044/045 전부 PASS, BE-049 라우팅 로직 자체는 정상 fire했으나 PO INSERT 단계에서 이 결함으로 500 → suggestion이 `APPROVED`에 멈추고 sink 기록에 도달 못 함.
