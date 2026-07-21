# TASK-FIN-BE-060 — Document the per-tenant FX-rate-override endpoint in ledger-api.md

**Status:** done

**Type:** TASK-FIN-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (add one numbered contract section from existing code; no behavior change)

> Filed from the 2026-07-21 reconciliation audit round-2 re-measurement (origin/main `aa535c22b`). The audit's specific
> claims were mostly wrong and are **REFUTED**: there is no `§14.2` and no `§15` in the file (the audit fabricated the section
> numbers), and the ShedLock lock name is `ledger-fx-rate-poll` everywhere (not "OUT"). One real gap survives: an implemented
> endpoint has no numbered contract section.

---

## Goal

`GET`/`PUT /api/finance/ledger/settlements/fx-rate-override/{foreignCurrency}` (per-tenant FX contract-rate override,
TASK-FIN-BE-042) ships in code but `ledger-api.md` only name-drops it once in prose (`:678`) — unlike its sibling
per-account cost-flow override (`§13.1–13.3`), which gets a full numbered section. Add the missing section.

## Scope

**In scope (doc-only):** Add a new numbered section (a new `## 15. Per-tenant FX rate override`, since the file currently ends
its numbered sections at `## 14. FX rates (read)` / `### 14.1`) documenting:
- `GET /api/finance/ledger/settlements/fx-rate-override/{foreignCurrency}` and
  `PUT .../{foreignCurrency}` — code: `apps/ledger-service/.../presentation/controller/SettlementController.java:201-228`.
- Request: `{ "rate": "<decimal-string>" }` (`FxRateOverrideRequest.java:16`).
- Response: `{ baseCurrency, foreignCurrency, present, rate, updatedBy, updatedAt }`
  (`FxRateOverrideResponse.java:22-24`).
- Error: `400 VALIDATION_ERROR` for non-positive/unparseable rate (`LedgerErrors.java:265-268`).
Mirror the `§13.1–13.3` format.

**Out of scope:** any `§14.2`/`§15` "fix" as literally described by the audit (they don't exist); the ShedLock lock name
(already consistent at `ledger-fx-rate-poll` in `FxRateFeedPoller.java:42-46`, `architecture.md:1874`, `ADR-002:158`); any code change.

## Acceptance Criteria

- **AC-0 (gate — re-measure; code wins)** — Re-confirm the endpoint still ships and is still undocumented as a numbered section
  at current `main`; read the actual request/response DTOs (line numbers may shift) rather than trusting this task's quotes.
- **AC-1** — A numbered section documents both verbs, the request and response shapes (fields exactly as in the DTOs), and the
  400 error, in the same style as `§13`.
- **AC-2** — The forward-declaration prose at `ledger-api.md:678` (`V15 — TASK-FIN-BE-042`) is reconciled with (or points to)
  the new section rather than being the only mention.
- **AC-3** — No behavior change; only `ledger-api.md` touched. Confirm no other implemented ledger endpoint is similarly
  undocumented (grep `@GetMapping`/`@PutMapping`/`@PostMapping` in `SettlementController` and cross-check each against the spec).

## Related Specs
- `projects/finance-platform/specs/contracts/http/ledger-api.md` (§13 override pattern to mirror, §14 FX rates read)
- `projects/finance-platform/specs/services/ledger-service/architecture.md`

## Related Contracts
- `ledger-api.md` is itself the contract being completed.

## Edge Cases
- Section numbering: the file jumps `§14 → ## Error codes`; adding `## 15` before `## Error codes` keeps ordering. Confirm no
  internal cross-reference assumes `§15` means something else first.
- `present` in the response is a boolean-style presence flag (override set vs. absent) — document its meaning, not just its type.

## Failure Scenarios
- **F1 — "fixing" a nonexistent §14.2/§15 per the audit's wording.** Guarded by AC-0/Scope: the real action is *adding* a
  section, not editing a phantom one.
- **F2 — inventing response fields.** Guarded by AC-1 (copy from `FxRateOverrideResponse`).
