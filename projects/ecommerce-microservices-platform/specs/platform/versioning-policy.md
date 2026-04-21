# Versioning Policy

Defines versioning rules for APIs, events, and libraries.

---

# HTTP API Versioning

## Strategy

- URL path versioning: `/api/v{n}/{resource}` (e.g. `/api/v1/products`).
- Current default version: `v1`. All existing endpoints are implicitly `v1`.
- Version prefix is omitted in current contracts but must be added when a breaking change is introduced.

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

- Event type format: `{EventName}V{n}` when a breaking change is needed (e.g. `OrderPlacedV2`).
- Old and new versions must be produced simultaneously during the migration window.
- Consumers must migrate to new versions before old versions are retired.

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
