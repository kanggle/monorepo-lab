# Task ID

TASK-FAN-BE-028

# Title

Fix artist directory 500 when no search term (untyped null `:q` → `lower(bytea)`)

# Status

review

# Owner

backend

# Task Tags

- code
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

`GET /api/v1/artists` (public artist directory) with **no `q` search term** currently returns HTTP 500. The default "browse all artists" landing page issues exactly this call, so the directory is broken for every visitor until they type a search term.

Root cause: the directory JPQL guards the optional search term with a **bare, untyped** parameter:

```jpql
AND (:q IS NULL OR LOWER(a.stageName) LIKE LOWER(CONCAT('%', :q, '%')))
```
in `apps/artist-service/src/main/java/com/example/fanplatform/artist/adapter/out/persistence/ArtistJpaRepository.java` (`searchPublished`). `ArtistDirectoryService.search` passes `null` for `:q` when the normalized query is empty. PostgreSQL does not guarantee it short-circuits the `:q IS NULL` branch away before planning the `LOWER(:q ...)` subtree, so an untyped-null argument to `lower(...)` is resolved to the non-existent `lower(bytea)` overload → `42883 function lower(bytea) does not exist` → 500.

After this task: a directory request with no search term returns 200 with the full PUBLISHED page (same-tenant), and the search-term path continues to behave identically.

---

# Scope

## In Scope

- Type the null guard in `ArtistJpaRepository.searchPublished` so PostgreSQL can plan the predicate, e.g. `CAST(:q AS string) IS NULL OR LOWER(a.stageName) LIKE LOWER(CONCAT('%', :q, '%'))` (the established sibling-project idiom — see wms-platform optional-filter repositories such as `apps/admin-service/.../readmodel/alert/AlertLogRepository.java`).
- Preserve exact behavior of the non-null search path (case-insensitive substring match on `stageName`, plus the existing `artist_type` filter and ordering).
- Add regression coverage proving the no-`q` path returns 200 (not 500) and the `q` path still filters.

## Out of Scope

- Any change to the artist directory contract, response shape, pagination, or sort order.
- Frontend changes (`web/fan-platform-web`) — the error-state fallback stays as-is.
- Refactoring the directory search into a Specification/Criteria query.
- **Note (adjacent, separate ticket)**: the same un-cast `:q IS NULL OR LOWER(...)` pattern exists as a latent twin in `wms-platform/apps/admin-service/.../AdminUserJpaRepository.java` (survives only because its callers never pass null). Do NOT fix it here — cross-project change belongs in a wms task; recorded so it is not lost.

---

# Acceptance Criteria

- [ ] `GET /api/v1/artists?page=0&size=20` with **no `q`** returns 200 and lists PUBLISHED artists for the caller's tenant (previously 500).
- [ ] `GET /api/v1/artists?q=<term>` returns the same case-insensitive `stageName` substring matches as before (behavior-preserving on the non-null path).
- [ ] `GET /api/v1/artists?type=SOLO` (and combined with `q`) continues to filter by `artist_type`.
- [ ] The generated SQL binds the search parameter with a determinate type (no `lower(bytea)`); verified by a repository/integration test that exercises the null-`q` path against PostgreSQL (Testcontainers) and asserts 200 / correct rows.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `platform/entrypoint.md`
- `specs/services/artist-service/architecture.md`

# Related Skills

- `.claude/skills/backend/` (see `.claude/skills/INDEX.md`)

---

# Related Contracts

- `specs/contracts/http/artist-api.md` (artist directory `GET /api/v1/artists` — unchanged by this fix)

---

# Target Service

- `artist-service`

---

# Architecture

Follow:

- `specs/services/artist-service/architecture.md`

---

# Implementation Notes

- The fix is a one-line JPQL change in `ArtistJpaRepository.searchPublished`; the `ArtistDirectoryService.search` null-passing behavior is correct and should stay (an empty query legitimately means "no filter").
- Confirm the cast renders `cast(? as varchar)` (or equivalent) so the `:q IS NULL` branch and the `LIKE` branch both plan on a typed parameter. Keep the identical `LOWER(...)`/`CONCAT('%', :q, '%')` shape for the non-null path.
- H2 (Docker-free `:check` slice) may mask the defect because it plans nulls differently — the authoritative guard is the PostgreSQL Testcontainers integration lane. Ensure the regression runs there.

---

# Edge Cases

- No `q` param at all (undefined) vs empty/whitespace `q=` — both normalize to null and must return the unfiltered page.
- `q` present but matching zero artists → 200 with empty content (not 500).
- `type` filter present with null `q` → the `type` predicate still applies; only the search predicate is bypassed.
- Tenant isolation preserved: results limited to the caller's `tenant_id` and `status='PUBLISHED'`.

---

# Failure Scenarios

- Regression: a future optional-filter query added without a type cast reintroduces `lower(bytea)` — the integration test on the null path is the guard.
- Over-fix: switching to `nativeQuery` or a Specification that changes ordering/paging semantics — rejected, behavior must be preserved.
- Casting to the wrong JPQL type (e.g. numeric) — must be `string`/text so `LOWER`/`LIKE` remain valid.

---

# Test Requirements

- Repository/integration test (artist-service, PostgreSQL Testcontainers) covering: null-`q` → 200 + full PUBLISHED page; non-null `q` → filtered subset; `type` filter with null `q`.
- Keep existing `ArtistDirectoryService` slice/unit coverage green (behavior-preserving).

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed (none expected)
- [ ] Specs updated first if required (none expected)
- [ ] Ready for review
