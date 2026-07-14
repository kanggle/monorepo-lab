# Versioning Policy

Defines versioning rules for APIs, events, and libraries.

---

# HTTP API Versioning

## Strategy

**This section is canonical for HTTP API path shape and versioning.** [`service-types/rest-api.md`](service-types/rest-api.md) § Versioning and [`naming-conventions.md`](naming-conventions.md) § API Endpoints point here; they do not define the rule. On any disagreement, this section wins.

- **The `/api/` prefix is mandatory** on every externally-routed endpoint — no exceptions. Service-to-service surfaces (`/internal/…`) and inbound webhook receivers are not client-facing APIs and are outside this rule.
- **URL path versioning**: the canonical full path is `/api/v{n}/{resource}`.
- **The `v{n}` segment is optional today, and whether an endpoint carries it is fixed by that endpoint's contract** in `specs/contracts/http/<service>-api.md` (mandatory per `service-types/rest-api.md` § Contract First). An endpoint whose contract declares no explicit segment **is** `v1` — implicitly, not accidentally. Both forms are live across the repository.
- **A breaking change requires an explicit version segment.** An endpoint that omits it introduces `/api/v2/{resource}` alongside the existing implicit-`v1` path; an endpoint that already carries one bumps `v{n}`. The contract is updated first (§ Change Rule).

> The third bullet formerly read *"Version prefix is omitted in current contracts but must be added when a breaking change is introduced."* Read as the `/api/` prefix that sentence was false (no endpoint omits it) — which is why `service-types/rest-api.md` came to assert the opposite while citing this file. Read as the `v{n}` segment it was true, but only for the majority of contracts, not all. The rule above separates the two so the sentence can no longer be read both ways (TASK-MONO-411).

## Breaking vs Non-Breaking Changes

### Non-Breaking (no version bump required)
- Adding new optional fields to response bodies.
- Adding new optional query parameters.
- Adding new endpoints.

### Breaking (requires new version)
- Removing or renaming fields.
- Changing field types.
- Changing HTTP status codes for existing scenarios.
- Removing endpoints.

## Deprecation

- Deprecated endpoints must include a `Deprecation` response header.
- Deprecated versions must be supported for at least 3 months after announcement.

---

# Event Versioning

**Canonical: [`event-driven-policy.md`](event-driven-policy.md) § Contract Rule + § Schema Versioning.** This section is a pointer, not a rule — the `event` task tag routes to `event-driven-policy.md` ([`entrypoint.md`](entrypoint.md) § Auxiliary), so that is the document an implementer actually opens.

The headline, so nobody has to guess before clicking: a breaking event change bumps `eventVersion` **and** publishes on a new topic version (`<topic>.v{n}`) with a coexistence period — **both, never either.**

> This section formerly specified an `{EventName}V{n}` event-type suffix, offered a topic suffix as an *"alternative — either approach"*, and never mentioned `eventVersion` at all. All three were wrong: `event-driven-policy.md` requires both mechanisms together, `eventVersion` is a mandatory envelope field, and no live event contract implements the event-type-suffix form. The rule text was removed rather than reconciled — a second copy is how the two files drifted apart (TASK-MONO-411).

---

# Library Versioning

- Libraries under `libs/` follow Semantic Versioning: `MAJOR.MINOR.PATCH`.
- `MAJOR`: breaking API change.
- `MINOR`: backward-compatible new functionality.
- `PATCH`: backward-compatible bug fix.
- All library versions are declared in `gradle.properties`.

---

# Database Schema Versioning

- Managed by Flyway with sequential integer versions: `V1`, `V2`, ...
- Each migration must be idempotent or clearly documented as not idempotent.
- Never modify an already-applied migration file.

---

# Change Rule

API version changes must update the related contract in `specs/contracts/` before implementation.
