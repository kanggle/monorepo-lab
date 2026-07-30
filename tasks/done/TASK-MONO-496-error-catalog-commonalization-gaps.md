# TASK-MONO-496 — Error catalog commonalization gaps: PERMISSION_DENIED promotion + Content-Heavy trait duplicate in ecommerce Product

**Status:** done

**Type:** TASK-MONO
**Analysis model:** Sonnet 5 / **Recommended impl model:** Sonnet 5 (documentation reconciliation; no new architectural decision beyond a naming ruling already implied by existing document convention)

> Root-level because the fix touches `platform/error-handling.md` (shared platform file, § Task Rules). Filed from a 2026-07-30
> ad-hoc commonalization review of the error registry (origin/main, no divergence at fetch time).

---

## Goal

Two commonalization gaps found in `platform/error-handling.md`'s own registry should be closed so the document consistently
follows its own stated rules (§ Rules: "Codes introduced by a trait... belong in the matching Platform-Common subsection...
Only truly domain-specific semantics go under `[domain:X]`"; § Change Rule: new codes must be registered before use).

**Gap 1 — `PERMISSION_DENIED` never promoted to Platform-Common despite identical semantics to `FORBIDDEN`.**
Platform-Common § Authorization registers only `FORBIDDEN` (403) and `ACCESS_DENIED` (403, back-compat alias). `PERMISSION_DENIED`
(403, "caller lacks required role/permission") is independently registered twice instead:
- `Admin [domain: saas]` — emitted by `admin-service`; its own note says IAM `community-service`, `membership-service`, and scm
  `procurement-service` / `demand-planning-service` / `inventory-visibility-service` emit it too, "with identical semantics."
- `Authorization [domain: erp]` — emitted by `masterdata-service`; noted as "same string as IAM admin-service
  `PermissionDeniedException` — erp-local emission."

Three domains, 6+ services, one semantic the document itself already asserts never diverged — exactly the case Platform-Common
promotion exists for, and it hasn't happened.

**Gap 2 — ecommerce Product section re-registers Content-Heavy Trait codes without a cross-reference.**
Platform-Common § Content-Heavy Trait already registers `STORAGE_UNAVAILABLE` (503), `MEDIA_NOT_FOUND` (404),
`MEDIA_VALIDATION_FAILED` (400). `Product [domain: ecommerce]` re-lists all three with identical codes/statuses, attributed to
`product-service`'s own exception classes (`MediaNotFoundException`, `MediaValidationException`, `StorageUnavailableException`)
— but unlike every other reused code in this document (`WEBHOOK_SIGNATURE_INVALID`, `TOKEN_REVOKED`, `CIRCUIT_OPEN`, ...), with
no "Same string as Platform-Common — service-local emission" annotation. Per the document's own Rules, trait-shape codes with
no domain-specific semantics don't belong under `[domain: ecommerce]` as standalone rows at all.

---

## Scope

### In Scope
- Re-verify both gaps against current `main` (AC-0) before touching anything.
- Gap 1 ruling: add `PERMISSION_DENIED` to Platform-Common § Authorization as its own registered row (do **not** merge it into
  `FORBIDDEN`/`ACCESS_DENIED` — three distinct strings already ship in live code); replace the two domain-section table rows
  with footnote-style cross-references in the style the document already uses elsewhere.
- Gap 2 ruling: remove the three standalone rows from `Product [domain: ecommerce]` and replace with a footnote pointing to
  Platform-Common § Content-Heavy Trait — unless AC-0 turns up a real semantic difference, in which case document that
  difference explicitly instead of silently duplicating.
- Grep `rules/domains/{saas,erp,ecommerce}.md` for independent restatements of these four codes and align their cross-references
  if any exist.

### Out of Scope
- Any other commonalization gap in the ~980-line registry not named above (this task doesn't relitigate the whole document).
- Merging `FORBIDDEN` / `ACCESS_DENIED` / `PERMISSION_DENIED` into one string — that's a breaking API change to shipped
  services, not a documentation fix.
- Any change to `*Exception.java` / exception-handler code in `admin-service`, `masterdata-service`, `product-service`, or any
  other emitter — this task touches only the registry doc and, if needed, `rules/domains/*.md` cross-references.

---

## Acceptance Criteria

- [ ] **AC-0 (re-measure gate)** — Before editing, grep current `main` for every occurrence of `PERMISSION_DENIED`,
      `MEDIA_NOT_FOUND`, `MEDIA_VALIDATION_FAILED`, and `STORAGE_UNAVAILABLE` across `platform/error-handling.md`,
      `rules/domains/*.md`, and the actual exception/handler source in `admin-service`, erp `masterdata-service`, and
      `product-service`, to confirm HTTP status and semantics still match what the doc currently claims.
- [ ] **AC-1** — `PERMISSION_DENIED` (403) appears exactly once as a registered row, under Platform-Common § Authorization,
      with a description that accounts for its multi-service usage.
- [ ] **AC-2** — `Admin [domain: saas]` and `Authorization [domain: erp]` no longer carry `PERMISSION_DENIED` as an
      independent table row; each instead carries a one-line cross-reference footnote to the Platform-Common row, matching
      the document's existing footnote convention.
- [ ] **AC-3** — `Product [domain: ecommerce]` no longer independently registers `STORAGE_UNAVAILABLE` / `MEDIA_NOT_FOUND` /
      `MEDIA_VALIDATION_FAILED` as new rows; it carries a cross-reference footnote to Platform-Common § Content-Heavy Trait,
      or (only if AC-0 finds real divergence) documents that divergence explicitly instead.
- [ ] **AC-4** — `git grep` for each of the four codes across the repo shows no remaining undocumented duplicate row — only
      the Platform-Common canonical row plus footnote-style references.
- [ ] **AC-5** — No `*.java` file is touched; the diff is confined to `platform/error-handling.md` and, if needed,
      `rules/domains/{saas,erp,ecommerce}.md`.

---

## Related Specs
- `platform/error-handling.md` § Rules, § Change Rule, § Authorization (Platform-Common), § Content-Heavy Trait
  (Platform-Common), § Admin `[domain: saas]`, § Authorization `[domain: erp]`, § Product `[domain: ecommerce]`
- `rules/domains/saas.md`, `rules/domains/erp.md`, `rules/domains/ecommerce.md` (cross-reference sections only, if present)
- `CLAUDE.md` § Task Rules (root-level task rationale), § Source of Truth Priority

## Related Contracts
- None directly changed. If a service's `specs/contracts/http/*-api.md` independently restates one of these four codes with
  its own description, AC-0's grep should surface it — align the contract's cross-reference wording too, don't leave it
  pointing at a row that no longer exists standalone.

---

## Edge Cases
- If AC-0 finds an actual semantic or status divergence between a "duplicate" row and its claimed Platform-Common counterpart
  (e.g. `product-service`'s `MediaValidationException` validates something the generic Platform-Common description doesn't
  cover), do not force-merge — document the divergence and treat promotion/cross-referencing as correctly declined for that
  one code (see F1).
- `Admin [domain: saas]`'s closing note already lists several codes "reused from Platform-Common" in prose — verify removing
  the standalone `PERMISSION_DENIED` row doesn't leave that prose note pointing at nothing or duplicating the new footnote.

## Failure Scenarios
- **F1 — force-unifying because duplication "looks bad."** A duplicate registry entry is only a defect if it hasn't diverged.
  AC-0 exists specifically to re-confirm none of the three services' actual exception classes have quietly diverged from the
  claimed shared semantic before any row is deleted — recount, don't inherit the prior review's conclusion as fact.
- **F2 — deleting a domain-section row without leaving a cross-reference footnote.** Every other reused code in this document
  keeps a footnote at its point of use so a reader scanning `Admin [domain: saas]` or `Authorization [domain: erp]` doesn't
  need to already know the code lives elsewhere. AC-2/AC-3 require the footnote, not a silent deletion.
