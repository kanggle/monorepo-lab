# TASK-MONO-277 — Clarify multi-secondary Service Type declarations in service-types/INDEX.md

Status: done

## Goal

Make `platform/service-types/INDEX.md` Selection Rule 1 consistent with services that legitimately declare more than one secondary `Service Type` (e.g. `demand-planning-service` = `event-consumer + batch-job + rest-api`). The rule currently shows only the `<primary> + <secondary>` (single-secondary) form, so a triple declaration reads as a rule violation even though the "read exactly one (primary) spec file" invariant is fully satisfied.

## Scope

- `platform/service-types/INDEX.md` Selection Rule 1 only — generalize `<primary> + <secondary>` to `<primary> + <secondary>[ + <secondary> …]`, with an explicit example and a restatement that secondaries never add spec reads (primary alone selects the one `service-types/<type>.md` file per `platform/entrypoint.md`).
- Doc-only. No code, no contract, no architecture-decision change (the read invariant is unchanged; this clarifies the existing intent).

## Acceptance Criteria

- INDEX.md Rule 1 admits one-or-more secondaries and gives a concrete 3-type example.
- The "exactly one file read (primary only)" invariant is restated so secondaries are unambiguously read-neutral.
- `entrypoint.md` "Read exactly one file matching the declared Service Type" remains accurate (primary = the one file) — no edit needed there.
- No existing service declaration becomes non-conforming; `demand-planning-service` (3-type), `read-model-service` / `inventory-visibility-service` (dual) all conform.

## Related Specs

- `platform/service-types/INDEX.md` (the file edited)
- `platform/entrypoint.md` § Service-Type-Specific (the "exactly one file" read rule this preserves)

## Related Contracts

- None (doc-only; no API/event contract touched).

## Edge Cases

- A service with a single `Service Type` (no `+`) — unaffected, still the common case.
- A future 4+-type service — the `[ + <secondary> …]` form already covers it without further edits.

## Failure Scenarios

- Over-broadening into "read all matching spec files" — explicitly avoided; the edit reaffirms primary-only reads so the `entrypoint.md` invariant and HARDSTOP-10 detection are unchanged.

## Notes

Surfaced by `/validate-rules` (2026-06-16). Companion false-positive dropped in the same audit: `abac-data-scope.md` / `access-conditions.md` DO have a load path via `platform/security-rules.md` (rules/common.md canonical #10).
