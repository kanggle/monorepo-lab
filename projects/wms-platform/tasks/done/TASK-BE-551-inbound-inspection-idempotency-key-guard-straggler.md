# TASK-BE-551 — inbound-service: inspection:start / record-inspection missing the Idempotency-Key required-guard (sibling-parity straggler)

Status: done

`(분석=Opus 4.8 / 구현 권장=Sonnet — 형제 대비 2줄/메서드 배선 + web-slice 테스트)`

---

## Goal

Close a spec↔code drift found in the 2026-07-21 reconciliation audit and re-measured against `main` (`dd93fc420`). The contract says the inbound **inspection** endpoints "Require `Idempotency-Key`", but `InspectionController.startInspection` and `recordInspection` are the **only two** inbound write endpoints that never enforce it — every sibling (ASN, putaway, and even `acknowledgeDiscrepancy` in the *same* controller) does. This is a **straggler sibling-parity gap** ([[project_enforcement_straggler_sibling_parity]]): N−1 endpoints wire the guard, 2 do not.

**Note — the audit's original hypothesis was wrong and is corrected here:** the audit suspected "no dedupe, DUPLICATE_REQUEST never returned." Re-measurement REFUTES that — dedupe + `DUPLICATE_REQUEST` are fully wired via a shared filter. The real (narrower) gap is the **missing 400-on-absent-key guard** on these two endpoints only.

## Re-measured evidence (line numbers = hypotheses, re-verify)

**Contract:** [`specs/contracts/http/inbound-service-api.md`](../../specs/contracts/http/inbound-service-api.md):
- § 2.1 `inspection:start` (≈ line 354): "Requires `Idempotency-Key`." (≈ 357); body-mismatch-on-retry → `DUPLICATE_REQUEST` (≈ 361–362); errors list includes `DUPLICATE_REQUEST` 409 (≈ 379).
- § 2.2 `inspection` / record (≈ line 381): "Requires `Idempotency-Key`." (≈ 384).

**Dedupe IS wired (audit hypothesis refuted):**
- [`apps/inbound-service/.../config/IdempotencyConfig.java`](../../apps/inbound-service/src/main/java/com/wms/inbound/config/IdempotencyConfig.java) (≈ lines 54–74): registers the shared `IdempotencyKeyFilter` for **all** `POST /api/v1/inbound/*` (webhooks excluded) — blanket-covers `inspection:start` + `inspection`.
- shared `libs/java-web-servlet/.../idempotency/IdempotencyKeyFilter.java` (≈ 133–143): a store hit with a differing body-hash calls `errorWriter.writeConflict(...)`.
- `.../adapter/in/web/filter/InboundIdempotencyErrorWriter.java` (≈ 28–33): `writeConflict` emits **409 `DUPLICATE_REQUEST`**. So when a key IS sent, replay + `DUPLICATE_REQUEST` both work.

**The real gap — the "required" 400-guard is absent on these two endpoints:**
- The shared filter deliberately **skips** a request with no key (≈ lines 88–93: "skip if no Idempotency-Key header (controller returns 400)") — it delegates the 400 to the controller.
- [`apps/inbound-service/.../adapter/in/web/controller/InspectionController.java`](../../apps/inbound-service/src/main/java/com/wms/inbound/adapter/in/web/controller/InspectionController.java): `startInspection` (≈ 48–56) and `recordInspection` (≈ 58–76) have **no** `@RequestHeader("Idempotency-Key")` and **never** call `RequestContext.requireIdempotencyKey(...)`.
- **Every sibling enforces it:** `AsnController` (≈ 63, 108, 124), `PutawayController` (≈ 70, 95, 112), and `acknowledgeDiscrepancy` **in this same InspectionController** (≈ 84, 87). `RequestContext.requireIdempotencyKey` (util ≈ 14–18) throws → 400 when the key is null/blank.
- **No test** pins it — there is no `InspectionControllerTest` web-slice at all (only the domain `InspectionTest`).

**Blast radius:** a client omitting the key gets no 400 and no idempotency protection; a retry is deduped only if a key was sent. The domain state machine partially backstops duplicates (a second `inspection:start` from `INSPECTING` → `STATE_TRANSITION_INVALID`; a second `record` after status advances likewise) — consistent with the filter's "availability-over-correctness, domain-backstopped" posture. So it is a **minor real defect** (missing input-contract enforcement + parity break), not a hollow dedupe.

## Scope

**In:** restore sibling parity — enforce the `Idempotency-Key` required-guard on `InspectionController.startInspection` + `recordInspection`, and add the missing web-slice test coverage.
**Out:** the shared `IdempotencyKeyFilter` / `InboundIdempotencyErrorWriter` (correct — dedupe + `DUPLICATE_REQUEST` already work); ASN/putaway (already enforce); the inspection domain state machine.

## Acceptance Criteria

- **AC-0 (re-measure):** confirm on `main` that `startInspection`/`recordInspection` lack the `@RequestHeader`/`requireIdempotencyKey` guard while every sibling has it, and that a POST to these two endpoints **without** `Idempotency-Key` currently returns a non-400 (write a RED web-slice test). Re-verify the file:line refs — code wins.
- **AC-1:** add `@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey` + `RequestContext.requireIdempotencyKey(idempotencyKey)` to both methods, mirroring `acknowledgeDiscrepancy` directly below them (≈ 2 lines/method).
- **AC-2:** a new `InspectionControllerTest` web-slice asserts: (a) missing key → **400** on both endpoints; (b) a same-key same-body replay is idempotent; (c) a same-key different-body retry → **409 `DUPLICATE_REQUEST`** (confirming the shared filter reaches these endpoints).
- **AC-3:** no behavior change for a request that DOES carry a valid `Idempotency-Key` (the happy path is unchanged).
- **AC-4:** `./gradlew :projects:wms-platform:apps:inbound-service:check` green — CI Linux authority; the inbound IT lane is serialised (`--no-parallel`) per `platform/testing-strategy.md` § Integration lane serialisation, do not reintroduce parallelism.

## Related Specs

- `projects/wms-platform/specs/contracts/http/inbound-service-api.md` § 2.1 / § 2.2 (the "Requires Idempotency-Key" + `DUPLICATE_REQUEST` clauses).
- `projects/wms-platform/specs/services/inbound-service/architecture.md` — idempotency posture (availability-over-correctness, domain-backstopped).
- `platform/error-handling.md` — `DUPLICATE_REQUEST` (409) code.

## Related Contracts

- `projects/wms-platform/specs/contracts/http/inbound-service-api.md`.

## Edge Cases

- The domain state machine already rejects a second `inspection:start`/`record` (`STATE_TRANSITION_INVALID`) — the new guard fires **before** the domain, so a missing-key request 400s regardless of state; keep the two distinct (missing key = 400, valid key + wrong state = the existing domain error).
- `acknowledgeDiscrepancy` in the same controller is the reference implementation — copy its exact guard call to avoid a subtly different predicate.
- Confirm the shared filter's blanket `POST /api/v1/inbound/*` registration actually matches the inspection paths (it should — verify the path shape once).

## Failure Scenarios

- **Doc-fix instead of code-fix:** dropping "Requires Idempotency-Key" from the spec would contradict every other inbound write endpoint AND the spec's own `DUPLICATE_REQUEST` error listing — the code-fix (restore parity) is the correct direction.
- **Fix the guard, skip the test:** without `InspectionControllerTest`, the parity gap silently reopens (it shipped precisely because there was no web-slice test). AC-2 closes the coverage hole.
- **Enforce required=true at the binding:** using `@RequestHeader(required = true)` would yield a framework 400 with a non-contract body — mirror the sibling's `requireIdempotencyKey` so the error envelope matches the documented shape.
