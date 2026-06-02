# Task ID

TASK-SCM-BE-021

# Title

`scm-platform` (inventory-visibility + procurement) — reclassify **corrupt read-model data** from a misleading silent `422` to a logged `500`, and add diagnostic logging to the residual genuine-`422` `handleIllegalArgument` path. Closes the observability gap that TASK-MONO-171 surfaced but did not fix: a non-UUID persisted id flowed through `toDomain` reconstruction (`UUID.fromString(...)`), threw a bare `IllegalArgumentException`, and the web advice mapped it to `422 VALIDATION_ERROR` with **no log line** — so the operator console showed "degraded" with zero diagnostic trail and the (blameless) client got a "your request is invalid" error.

# Status

review

# Owner

backend-engineer (small, low-risk: a new exception + a persistence parse helper + 4 reconstruction sites rerouted + 2 handler edits + unit tests)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- test

---

# Dependency Markers

- **follows**: TASK-MONO-171 (inventory-visibility 422 seed fix) — that task fixed the *symptom* (a bad seed UUID); this task fixes the *observability + status-correctness root* so a future corrupt-data occurrence is diagnosable and correctly classified.
- **no dependency on**: any contract/ADR/spec change. Error-code contract (`rules/domains/scm.md` § Standard Error Codes) already defines `INTERNAL_ERROR` (500) and `VALIDATION_ERROR` (422); this only routes the corrupt-data case to the correct one.

---

# Goal

Server-side data-integrity faults (corrupt persisted read-model values) surface as `500 INTERNAL_ERROR` **and are logged**; genuine client-boundary validation stays `422 VALIDATION_ERROR` **and is logged**. No bare `IllegalArgumentException` from read-model reconstruction can silently masquerade as a client 422 again.

# Scope

## In Scope

- `inventory-visibility-service`:
  - **New** `domain/error/ReadModelCorruptException.java` — server data-integrity fault.
  - **New** `adapter/outbound/persistence/adapter/ReadModelIds.java` — `requireUuid(value, column)` parse helper that wraps a malformed/null persisted UUID as `ReadModelCorruptException` (naming the `table.column`).
  - Reroute the **7 read-model reconstruction `UUID.fromString` sites** in 4 repository adapters (`InventorySnapshotRepositoryImpl` ×3, `InventoryNodeRepositoryImpl` ×1, `NodeStalenessRepositoryImpl` ×2, `EventDedupeRepositoryImpl` ×1) through `ReadModelIds.requireUuid`.
  - `GlobalExceptionHandler`: add `@ExceptionHandler(ReadModelCorruptException.class)` → `500 INTERNAL_ERROR` + `log.error`; add `log.warn` to the existing `handleIllegalArgument` (422).
- `procurement-service`:
  - `GlobalExceptionHandler.handleIllegalArgument` — add `log.warn` for parity with its own (already-logged) `handleIllegalState`. procurement has **no** read-model `UUID.fromString` reconstruction path, so no 500-reclassification applies there — logging-only.
- Unit tests: `ReadModelIdsTest` (valid / corrupt / null), inventory-visibility `GlobalExceptionHandlerTest` (`ReadModelCorruptException`→500, `IllegalArgumentException`→422).

## Out of Scope

- **Controller-boundary parsing of client input** (e.g. a bad `nodeId` path variable parsed via `NodeId.of(String)`) is deliberately left as `IllegalArgumentException` → `422`: there the client genuinely sent a bad value. Only **persistence→domain reconstruction** is reclassified.
- The other 12 portfolio `GlobalExceptionHandler`s — a blind "log everything" sweep risks log-noise on legitimate-422 paths and needs per-service judgement; not in this task (the demo only surfaced the scm read-model path).

# Acceptance Criteria

- [x] **AC-1** A corrupt persisted UUID in any of the 4 inventory-visibility read-model reconstructions throws `ReadModelCorruptException` (verified by `ReadModelIdsTest`), which maps to `500 INTERNAL_ERROR` + `log.error` (verified by `GlobalExceptionHandlerTest`).
- [x] **AC-2** Genuine client-boundary `IllegalArgumentException` still maps to `422 VALIDATION_ERROR`, now `log.warn`-logged (both services).
- [x] **AC-3** `:inventory-visibility-service:check` + `:procurement-service:check` green. No contract/spec/ADR change. Diff confined to the two services' error handling + tests + task lifecycle.

# Related Specs

- `inventory-visibility-service` `architecture.md` § error handling; `rules/domains/scm.md` § Standard Error Codes (`INTERNAL_ERROR` 500 / `VALIDATION_ERROR` 422 already defined — no contract change).

# Related Contracts

- `specs/contracts/http/inventory-visibility-api.md` — error envelope unchanged (still `ApiErrorBody`); only which status a corrupt-data fault returns.

# Edge Cases

- Nullable `node_staleness.last_event_id`: the null-guard is preserved (null → null, not an exception); only a **non-null but malformed** value throws.
- A genuinely bad client path/query value → unchanged `422` (not reclassified).

# Failure Scenarios

- If corrupt data were instead a transient/expected condition, 500 would over-alert — but a non-UUID in a UUID column is a true data-integrity defect (bad write / bad seed / migration drift), correctly a 500.

# Test Requirements

- `ReadModelIdsTest`, inventory-visibility `GlobalExceptionHandlerTest` (new). `:inventory-visibility-service:check` + `:procurement-service:check` green.

# Definition of Done

- [x] `ReadModelCorruptException` + `ReadModelIds` added; 7 reconstruction sites rerouted; both handlers updated.
- [x] Unit tests green; both services' `:check` green.
- [x] Diff confined; no contract/spec/ADR change.
- [x] Task md + `INDEX.md` updated.
- [ ] Reviewed + merged (3-dim verified) — pending close chore.

---

분석=Opus 4.8 / 구현=Opus(직접). TASK-MONO-171 후속 — 증상(seed)이 아니라 관측성+상태정합 근본을 닫음. **메타: 같은 예외 타입(`IllegalArgumentException`)이 두 의미(클라이언트 입력 검증 ↔ 서버 데이터 무결성)를 가질 때, 발생 지점(controller boundary ↔ persistence reconstruction)에서 구분되는 전용 예외로 분리해야 status/logging 이 정직해진다. read-model toDomain 의 `UUID.fromString(persisted)` 는 항상 후자.**
