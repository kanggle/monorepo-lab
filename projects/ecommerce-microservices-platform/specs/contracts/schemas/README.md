# Contract Schemas

> **Status: intentionally unused in v1 — envelopes are defined inline by deliberate choice.**
>
> This directory was originally reserved for a shared-schema model (one
> canonical HTTP error envelope + one event envelope, referenced by every
> contract). That model was **never adopted**, and the de-facto convention
> across the whole portfolio is **inline envelopes per contract**:
>
> - ecommerce: every `specs/contracts/http/*.md` repeats the
>   `{ code, message, timestamp }` error envelope; every
>   `specs/contracts/events/*.md` repeats the
>   `{ event_id, event_type, occurred_at, source, tenant_id, payload }` event
>   envelope.
> - The other portfolio projects do not maintain a populated
>   `contracts/schemas/` either (iam-platform keeps an empty
>   placeholder; wms / scm / fan-platform have no `schemas/` directory).
>
> Markdown contracts in this repo have no `$ref` / transclusion mechanism,
> so a "shared schema" would only be a manual cross-file pointer convention.
> Introducing that would be a **cross-cutting monorepo convention** and is
> therefore explicitly **out of scope for any single-project task** — it
> would require a portfolio ADR (per ADR-MONO governance), not a unilateral
> per-project change.
>
> The inline-per-contract envelope is the accepted convention. This file is
> retained (rather than deleted) to record that decision and prevent the
> unfulfilled shared-schema promise from being re-introduced. Reconciled by
> TASK-BE-292 (E11), WI-1 option B.

---

# If a shared-schema model is ever adopted (deferred — needs an ADR)

It would live here and follow these rules:

## Allowed

- reusable request/response schema fragments
- common error schema
- pagination schema
- metadata envelope schema
- shared event envelope schema

## Not Allowed

- service-internal DTO definitions
- unpublished internal payloads
- database-oriented models
- schemas not referenced by official contracts

## Rule

Anything placed in this directory must be referenced by at least one
document in `specs/contracts/http/` or `specs/contracts/events/`.
