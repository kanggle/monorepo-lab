# Task ID

TASK-BE-353

# Title

ADR-MONO-029 step 3 — iam admin `RESOURCE_TAG` enforcement (deny-if-present on `protected` operators), composed AND-only at the single decision site. Add a minimal `tags` model to `admin_operators` (V0034) + an `OperatorResourceTagResolver` (path-aware, reads the trusted `tags` column) + wire `RESOURCE_TAG` into `RequiresPermissionAspect`'s 4th gate (compose AND-only with `SOURCE_IP`/`TIME_WINDOW`): an RBAC-granted role/status/profile mutation on an operator carrying a forbidden tag (e.g. `protected`) is denied 403 `ACCESS_CONDITION_UNMET`. Mutation-only, fail-safe, net-zero, producer-untouched (D3-B). The aspect remains the single authorization decision site (D2-A — the resolver is consulted FROM the aspect).

# Status

ready

# Owner

backend

# Task Tags

- access-conditions
- conditional-policy
- iam
- security
- rbac

---

# Dependency Markers

- **executes**: ADR-MONO-029 § 3.3 step 3 (the iam pilot's `RESOURCE_TAG` enforcement) on the ACCEPTED base (TASK-MONO-226).
- **depends on**: TASK-MONO-227 (the shared `ResourceTagCondition` evaluator, #1322) + TASK-BE-351/BE-352 (the `RequiresPermissionAspect` 4th-gate seam + the AND-only composition this extends).
- **blocks**: the deterministic federation-e2e composition proof (TASK-MONO-2xx).

# Goal

Make the `RESOURCE_TAG` access condition enforceable on the iam admin mutation surface, composed AND-only with `SOURCE_IP`/`TIME_WINDOW`, by resolving the target operator's tags at the single decision site (the aspect) via a `ResourceTagResolver` — so a `protected` operator's role/status/profile mutation is blocked, while keeping every unconfigured / untagged path byte-identical (net-zero).

# Scope

- NEW `…/resources/db/migration/V0034__add_tags_to_admin_operators.sql` — a comma-separated `tags VARCHAR(512) NULL` column on `admin_operators` (NULL/empty = untagged).
- `…/persistence/rbac/AdminOperatorJpaRepository.java` — a native projection `findTagsByOperatorId` (no entity field added; NULL/absent → `Optional.empty()`).
- NEW `…/presentation/aspect/ResourceTagResolver.java` (interface) + NEW `…/infrastructure/access/OperatorResourceTagResolver.java` (impl) — path-applicability (operator role/status/profile mutations; `/me/…` excluded) + the trusted `tags` column → tag set (anti-spoof, § D2-C).
- `…/config/AdminAccessConditionProperties.java` — nested `ResourceTag` (`forbidden` list, empty default → net-zero).
- `…/config/AccessConditionConfig.java` — the `ResourceTagCondition.forbidden(...)` bean.
- `…/presentation/aspect/RequiresPermissionAspect.java` — `ObjectProvider<ResourceTagCondition>` + `ObjectProvider<ResourceTagResolver>`; extend `anyConditionUnmet` to evaluate `RESOURCE_TAG` (skip when the resolver reports no resolvable resource).
- `…/resources/application.yml` — `admin.access.resource-tag.forbidden` + `ADMIN_ACCESS_RESOURCE_TAG_FORBIDDEN` env hook (empty default → net-zero).
- NEW `…/test/.../aspect/AdminResourceTagConditionEnforcementTest.java` (slice, mocked resolver: tagged → 403 / untagged → 200 / not-applicable → 200) + NEW `…/test/.../access/OperatorResourceTagResolverTest.java` (resolver path-matching + tag split).

**Out of scope**: the `RESOURCE_TAG` on tenants/accounts; the require-tag variant; the federation-e2e proof (separate task). No producer change (D3-B).

# Acceptance Criteria

- **AC-1 (deny-if-present)** With `forbidden=[protected]` configured, a role/status/profile mutation on an operator the resolver reports as carrying `protected` → 403 `ACCESS_CONDITION_UNMET` and the mutation is not executed.
- **AC-2 (allow untagged)** The same mutation on an untagged operator (resolver returns an empty set) → 2xx.
- **AC-3 (net-zero / not applicable)** A request the resolver reports as targeting no resolvable resource (e.g. a non-operator mutation, or operator CREATE/LIST, or `/me/…`) → the `RESOURCE_TAG` condition is skipped (2xx); an unconfigured forbidden list → no gate at all.
- **AC-4 (resolver)** `OperatorResourceTagResolver` is applicable only for `/api/admin/operators/{id}/{roles|status|profile}` (not `/me/…`, not collection paths, not non-operator paths); it splits the comma-separated `tags` column (trimmed, blanks dropped); an untagged/absent operator → an empty set; tags come from the DB, never the request.
- **AC-5 (composition + single site)** The condition composes AND-only with `SOURCE_IP`/`TIME_WINDOW` inside `RequiresPermissionAspect` (the single decision site is preserved — the resolver is consulted FROM the aspect).
- **AC-6** `:projects:iam-platform:apps:admin-service:test` GREEN + the monorepo Build & Test + Integration (iam) jobs GREEN (the V0034 migration applies + the beans wire).

# Related Specs

- `docs/adr/ADR-MONO-029-resource-tag-access-condition.md` § D2-A (the aspect-resolver seam) + § D3 (operators / `protected` / deny-if-present)
- `projects/iam-platform/specs/services/admin-service/rbac.md` (the 4th-gate enforcement seam)

# Related Contracts

- `platform/access-conditions.md` (§ 1 `RESOURCE_TAG` implemented + the input-source / resolver-seam note)

# Edge Cases

- **Applicability = path, tags = DB.** "Applicable" is decided by the request path (an operator mutation), not the DB; the tags come from `admin_operators.tags`. A NULL `tags` column and an absent row both project to an empty set (allowed at the gate; an absent operator 404s downstream regardless).
- **Anti-spoof (§ D2-C):** tags are read from the trusted column, never from the request body — a client cannot assert a benign tag to dodge the gate.
- **Single decision site:** the resolver is consulted from inside the aspect (D2-A), NOT a second authz site (the contract § 4 invariant holds).
- **Net-zero:** the `ResourceTagCondition` bean is always present (empty forbidden list → `isConfigured()` false → skipped); the resolver returning empty for non-operator requests skips the condition — existing behaviour is byte-identical until a forbidden tag is configured AND an operator is tagged.
- The new `ObjectProvider` fields resolve to empty providers in slices without a resolver/condition bean (existing slice tests unaffected — net-zero).

# Failure Scenarios

- If the resolver read tags from the request, a deny-if-present gate would be trivially bypassed — AC-4 sources tags from the trusted column.
- If a non-operator request's empty resolver result were treated as fail-safe deny, every non-operator admin mutation would 403 — the aspect SKIPS the condition on `Optional.empty()` (not applicable), and only evaluates on `Optional.of(tags)`. AC-3 guards it.
- If the gate evaluated OR, a `protected` operator's mutation could slip through when another condition passed — AC-5 pins AND-only.
- If the migration or beans failed to wire, the context would not boot — AC-6 (Integration (iam) GREEN) guards it.
