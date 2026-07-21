# TASK-BE-550 — outbound-service: PATCH packing-units contract over-promises "add-lines" (silent unconditional SEAL hazard)

Status: done

`(분석=Opus 4.8 / 구현 권장=Opus — 계약↔코드 정합 + 파괴적 무음-SEAL 해저드 판정)`

---

## Goal

Close a spec↔code drift found in the 2026-07-21 reconciliation audit and re-measured against `main` (`dd93fc420`): the outbound-service **PATCH packing-units** HTTP contract documents **two** operations ("add lines" + "seal"), but the code implements **only seal**. The "add-lines" half was never built — and because the request DTO carries only `version`, a client that follows the documented body silently gets an **irreversible unconditional SEAL**.

## Re-measured evidence (line numbers = hypotheses, re-verify at start)

**Contract (over-promises):** [`specs/contracts/http/outbound-service-api.md`](../../specs/contracts/http/outbound-service-api.md) § 3.2 (≈ line 654 `PATCH /api/v1/outbound/packing-units/{id} — Update Packing Unit (add lines / seal)`):
- ≈ lines 659–666: "Two operations are supported: 1. **Add lines** … 2. **Seal** … A request may seal the unit and add lines in the same call (add first, then seal)."
- ≈ lines 672–696: request body documents `seal` (default false) + `addLines[]`; validation: "if `addLines` absent/empty and `seal = false` → 400 (nothing to do)."

**Code (seal-only):**
- [`apps/outbound-service/.../adapter/in/web/controller/PackingController.java`](../../apps/outbound-service/src/main/java/com/wms/outbound/adapter/in/web/controller/PackingController.java) `sealUnit` (≈ lines 92–112): resolves the unit, builds a `SealPackingUnitCommand(orderId, packingUnitId, version, actor, roles)`, calls `sealPackingUnit.seal(...)`. **No add-lines branch.** The controller Javadoc (≈ lines 38–39) itself says only "PATCH … — seals the unit."
- `.../adapter/in/web/dto/request/SealPackingUnitRequest.java`: the request record has **only** `@Min(0) long version` — no `seal` field, no `addLines`.
- `grep addLines|AddLine|add-lines` across `outbound-service` → **0 files**. No use-case, command, or DTO exists.
- `PackingControllerTest` (≈ lines 149–219) exercises the PATCH strictly as "sealUnit"; no add-lines case.

**Behavioral hazard (not merely cosmetic):** because `SealPackingUnitRequest` binds only `version`, a contract-following client sending `{"seal": false, "addLines": [...], "version": 0}` has `seal`/`addLines` **silently dropped by Jackson** and the unit is **unconditionally sealed** — the exact opposite of `seal:false`, and a seal is irreversible. A caller intending "add lines, don't seal yet" destroys the unit's open state.

## Scope

**In:** reconcile PATCH packing-units so the contract and the code agree, AND remove the silent-seal footgun.
**Out:** the create path § 3.1 (packing-units are fully populated at create-time via `lines[]` — that is why seal-only is the implemented design); the seal domain logic itself (correct); other outbound endpoints.

## Acceptance Criteria

- **AC-0 (re-measure):** confirm on `main` that (a) § 3.2 still documents add-lines + a `seal` flag, (b) `SealPackingUnitRequest` binds only `version`, (c) no add-lines code path exists, and (d) a body with `seal:false` still results in a SEAL (write a RED test that POSTs `{seal:false, version:0}` and asserts the unit ends SEALED — proving the footgun). Re-measure the file:line refs — code wins.
- **AC-1 (direction — decide + record):**
  - **Option A — narrow the contract to seal-only (recommended if no consumer needs incremental add-lines).** Rewrite § 3.2 to describe seal-only (drop the `seal` flag + `addLines[]` from the documented body), and **harden the DTO to reject the removed fields** (`@JsonIgnoreProperties(ignoreUnknown = false)` or explicit rejection) so a stale client sending `seal:false`/`addLines` gets a **400, not a silent SEAL**. This closes the footgun without building unused functionality.
  - **Option B — implement add-lines.** Add `seal` + `addLines[]` to the DTO, an add-lines use-case/command, and honor `seal:false` (add lines, leave OPEN). Only if a real consumer needs to append lines to an OPEN unit — none is wired today.
  - **Recommendation: Option A** (design is seal-at-create; the add-lines clause is vestigial). Either way the `seal:false`-silently-seals behavior must be eliminated.
- **AC-2:** a test asserts the PATCH's actual behavior for a well-formed request AND that a request carrying the removed/absent `seal:false`/`addLines` fields no longer results in a silent SEAL (400 under Option A, or lines-added-not-sealed under Option B).
- **AC-3:** the contract § 3.2 example body is byte-consistent with what the DTO accepts.
- **AC-4:** `./gradlew :projects:wms-platform:apps:outbound-service:check` (+ Testcontainers integration where the seal path is IT-covered) green — CI Linux authority (local Windows Docker host-dependent).

## Related Specs

- `projects/wms-platform/specs/contracts/http/outbound-service-api.md` § 3.1 (create with lines) + § 3.2 (this PATCH).
- `projects/wms-platform/specs/services/outbound-service/architecture.md` — packing-unit lifecycle (OPEN → SEALED).

## Related Contracts

- `projects/wms-platform/specs/contracts/http/outbound-service-api.md` (the contract in dispute).

## Edge Cases

- A well-formed seal request (`{version: N}`) must keep working exactly as today — the fix must not change the happy path.
- Optimistic-lock version mismatch on seal must still surface its current status code (verify it is not swallowed by the DTO-strictness change).
- If Option A hardens the DTO, ensure no existing caller legitimately sends extra fields (grep callers / FE) before flipping `ignoreUnknown=false`.

## Failure Scenarios

- **Ship the doc-narrow but leave the DTO lenient:** the contract now says seal-only, but a client written against the old contract still silently seals with `seal:false` → the irreversible-seal footgun survives the "fix." AC-1/AC-2 require the DTO to reject the removed fields.
- **Implement add-lines nobody uses (Option B without a consumer):** builds and maintains dead functionality; prefer A unless a consumer is identified.
- **Doc-narrow without AC-0's RED footgun test:** the silent-seal hazard is asserted nowhere and can regress. AC-0 pins it.
