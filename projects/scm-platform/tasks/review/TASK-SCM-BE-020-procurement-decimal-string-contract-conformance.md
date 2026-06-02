# Task ID

TASK-SCM-BE-020

# Title

`procurement-service` PO response **contract conformance** — serialise the monetary / quantity `BigDecimal` fields as JSON **strings** (`"125000.00"`) per `procurement-api.md`, not JSON numbers. Jackson's default `BigDecimal`→number serialisation violates the contract and makes the platform-console SCM 운영 PO list fail to parse.

# Status

review

# Owner

backend-engineer (procurement-service presentation DTO — response serialisation only; no domain/persistence/auth change)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- api
- code
- test

---

# Dependency Markers

- **surfaced by**: the TASK-MONO-170 platform-console per-domain ops live demo (2026-06-02/03). With the SCM gateway + console wiring working, the SCM 운영 PO list received `200` from `/api/v1/procurement/po` but the console parse failed (`scm_ok` immediately followed by `scm_error`). Root cause isolated to the response **type**: `PurchaseOrderResponse.totalAmount` (+ line `quantity`/`unitPrice`/`receivedQuantity`) are `BigDecimal`, which Jackson serialises as JSON **numbers**; the contract + the console consumer require **strings**.
- **fixes a producer↔contract drift**, NOT the console: `procurement-api.md` shows `"totalAmount": "125000.00"`, `"quantity": "10.0000"`, `"unitPrice": "12500.00"` — all quoted **strings** (decimal-as-string preserves scale/precision). The console (`platform-console` `features/scm-ops/api/types.ts`, PC-FE-008) parses these as `z.string()`. The producer emits numbers — non-conformant.
- **why undetected**: the console SCM client had only mocked unit tests, and the procurement slice test asserted only `$.data...status`/`id` (never the decimal field types). No test exercised the real producer→console JSON for the money/quantity fields.
- **no dependency on**: any domain / persistence / auth / contract / ADR change (the contract already specifies strings; the producer is brought into conformance).

---

# Goal

Make `procurement-service` serialise the PO money/quantity decimals as the strings `procurement-api.md` specifies, so the platform-console SCM 운영 PO list parses + renders. Removes a latent contract violation that would break any strict consumer.

# Scope

## In Scope

`projects/scm-platform/apps/procurement-service` only — presentation DTO:

1. **`PurchaseOrderResponse`** — `@JsonFormat(shape = JsonFormat.Shape.STRING)` on `totalAmount` and on `LineResponse.{quantity, unitPrice, receivedQuantity}` (BigDecimal → quoted decimal string preserving DB scale).
2. **Test** — extend `PurchaseOrderControllerSliceTest.searchWithStatusFilter` (the list path the console uses) with a regression assertion that `$.data.content[0].totalAmount` + line `quantity`/`unitPrice` are JSON **strings** (`instanceOf(String.class)`).

## Out of Scope

- Any domain / persistence / auth change.
- The **inventory-visibility-service `422 VALIDATION_ERROR`** the same MONO-170 demo surfaced on `/snapshot` + `/staleness` (globex-corp) — a DISTINCT runtime `IllegalArgumentException` (the `GlobalExceptionHandler` maps it to 422 but does NOT log it; it returns 200 to the BFF leg but 422 to the console ops/gateway path). NOT root-caused from code/logs alone — needs a token-based reproduction (or adding diagnostic logging to the handler). **Separate task** (`inventory-visibility-service`). The SCM 운영 page renders fully only after BOTH this and that are fixed.
- The platform-console consumer (already correct per the contract).
- Mutation endpoints' request shapes (unchanged).

# Acceptance Criteria

- [x] **AC-1** `PurchaseOrderResponse.totalAmount` + line `quantity`/`unitPrice`/`receivedQuantity` serialise as JSON strings (quoted, scale-preserving) — verified by the slice-test `instanceOf(String.class)` assertion through real Jackson/MockMvc.
- [x] **AC-2** `procurement-service` `./gradlew :check` green.
- [x] **AC-3** Diff confined to `procurement-service/.../dto/PurchaseOrderResponse.java` + the slice test (+ task lifecycle). No domain/persistence/auth/contract/ADR change.
- [ ] **AC-4** (live) Rebuilt procurement-service + scm-gateway in the MONO-170 demo stack → console SCM 운영 PO list section parses (no `scm_error` on `/api/v1/procurement/po`). NOTE: the SCM 운영 page as a whole still degrades until the inventory-visibility 422 (separate task) is fixed — this AC verifies only the PO-list leg parse.

# Related Specs

- [`specs/contracts/http/procurement-api.md`](../../specs/contracts/http/procurement-api.md) — `totalAmount`/`quantity`/`unitPrice` as quoted decimal strings. **Unchanged** (producer is the non-conformant party).
- Consumer reference: `platform-console` `apps/console-web/src/features/scm-ops/api/types.ts` (`PurchaseOrderSchema` / `PoLineSchema` — `z.string()` decimals).

# Related Contracts

- [`procurement-api.md`](../../specs/contracts/http/procurement-api.md) — byte-unchanged; this task makes the implementation match it.

# Edge Cases

- `BigDecimal` scale preserved (`decimal(18,2)` → `"125000.00"`, `decimal(18,4)` → `"10.0000"`) — `@JsonFormat` STRING uses `BigDecimal.toString()`.
- Empty `lines` (PO with no lines) → no line decimals to serialise; `totalAmount` still applies.

# Failure Scenarios

- Missing a field (e.g. leaving `receivedQuantity` a number) → that field still breaks a strict consumer. Mitigation: AC-1 covers all four decimal fields.

# Test Requirements

- Slice-test regression assertion (string types).
- `./gradlew :projects:scm-platform:apps:procurement-service:check` green.
- Live AC-4 = rebuild in the MONO-170 demo stack.

# Definition of Done

- [x] `@JsonFormat` STRING on the 4 decimal fields + slice-test assertion.
- [x] `:check` green.
- [ ] AC-4 live (PO-list leg parses in the demo stack).
- [x] Diff scope confined; contract/ADR untouched.
- [ ] Task md + `INDEX.md` updated.
- [ ] Ready for review.

---

분석=Opus 4.8 / 구현=Opus(직접). 같은 MONO-170 데모가 노출한 inventory-visibility 422 는 별도 런타임 건(재현 필요)으로 분리 — green-wash 금지: 이 task 는 PO-list leg 의 계약-준수만 고치며 SCM 운영 전체 렌더는 inventory-visibility 건과 함께여야 완성.
