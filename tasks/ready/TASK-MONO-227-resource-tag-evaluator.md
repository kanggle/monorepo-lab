# Task ID

TASK-MONO-227

# Title

ADR-MONO-029 step 2 — `ResourceTagCondition` shared evaluator + contract flip. Add `com.example.security.access.ResourceTagCondition` to `libs/java-security` (sibling to `SourceIpCondition` / `TimeWindowCondition`: framework-agnostic, fail-safe, net-zero; `forbidden(tags)` deny-if-present + `required(tags)` modes; `isSatisfiedBy(Set<String> resourceTags)`) with unit tests, and flip `platform/access-conditions.md` § 1 `RESOURCE_TAG` reserved → implemented + add the input-source / aspect-resolver seam note (per-resource → deterministically federation-provable). Completes the closed condition enum. No enforcement yet — that is the follow-up iam BE task.

# Status

ready

# Owner

backend

# Task Tags

- access-conditions
- conditional-policy
- shared-library
- security

---

# Dependency Markers

- **executes**: ADR-MONO-029 § 3.3 step 2 on the ACCEPTED base (TASK-MONO-226, #1320).
- **mirrors**: TASK-MONO-218 (`SourceIpCondition`) + TASK-MONO-224 (`TimeWindowCondition`) — same blueprint, 3rd / final condition type.
- **blocks**: the iam pilot enforcement (tags model on `admin_operators` + `ResourceTagResolver` + aspect wiring) + the deterministic federation-e2e proof.

# Goal

Provide the canonical `RESOURCE_TAG` evaluator (deny-if-present + require modes) with the same three invariants (restriction-only / fail-safe / net-zero), and record in the contract that the input is a resource attribute (resolved by the domain, composed at the single decision site via a `ResourceTagResolver`) — completing the closed condition enum at the library + contract layer.

# Scope

- NEW `libs/java-security/src/main/java/com/example/security/access/ResourceTagCondition.java` — `forbidden(Collection)` (deny-if-present) / `required(Collection)` (require-all) factories; `isConfigured()` (any tag declared); `isSatisfiedBy(Set<String>)` (case-insensitive + trimmed matching; `null` tags → fail-safe deny; empty → known-untagged; unconfigured → net-zero).
- NEW `libs/java-security/src/test/java/com/example/security/access/ResourceTagConditionTest.java` — net-zero / deny-if-present (present/absent/empty/case-insensitive/multiple) / require (all-present / missing) / fail-safe (null) / empty-vs-null.
- `platform/access-conditions.md` — § 1 `RESOURCE_TAG` reserved → implemented; the input-source (request-context vs resource-attribute) + aspect-resolver seam note (single decision site preserved via `ResourceTagResolver`; deterministically federation-provable); References add ADR-029 + `ResourceTagCondition`.
- This task file.

**Out of scope** (the follow-up iam BE task): the `admin_operators` tags model + migration, the `ResourceTagResolver`, the `RequiresPermissionAspect` wiring, config, IT; (the fed-e2e task): the proof. No producer change (D3-B).

# Acceptance Criteria

- **AC-1** `forbidden(...)` / `required(...)` return a net-zero condition (`isConfigured()` false, `isSatisfiedBy` true for any input including `null`) when no tag is declared.
- **AC-2 (deny-if-present)** `forbidden(["protected"])` denies a resource carrying `protected` (case-insensitively, among other tags) and allows one without it (incl. a known-empty tag set).
- **AC-3 (require)** `required(["approved"])` allows only a resource carrying all required tags; a missing/empty set denies.
- **AC-4 (fail-safe)** A configured condition with `null` resourceTags denies (both modes); a known-empty set is distinguished from `null` (empty allows under deny-if-present).
- **AC-5** `platform/access-conditions.md` § 1 marks `RESOURCE_TAG` implemented and documents the resource-attribute input + the aspect-resolver seam (single decision site, deterministic federation-provability).
- **AC-6** `:libs:java-security:test` GREEN + the monorepo Build & Test job GREEN.

# Related Specs

- `docs/adr/ADR-MONO-029-resource-tag-access-condition.md` § 3.3 step 2 + § D2 (aspect-resolver seam) + § D3 (deny-if-present)
- `docs/adr/ADR-MONO-026-role-grant-access-conditions.md` (the framework + the sibling evaluators)

# Related Contracts

- `platform/access-conditions.md` (§ 1 flip + the input-source / resolver-seam note)

# Edge Cases

- **Net-zero**: an undeclared tag set (`isConfigured()` false) returns `true` for every input — the gate never bites until configured.
- **Fail-safe vs empty**: `null` resourceTags (resolver could not determine the tags) → deny; an *empty* set (resource known to carry no tags) → allow under deny-if-present, deny under require. The resolver MUST return empty (not null) for an existing-but-untagged resource.
- **Case-insensitive**: tags trimmed + lower-cased on both sides so `PROTECTED` matches `protected`.
- Each evaluator stays **input-specific** (`Set<String>` for `RESOURCE_TAG`); the aspect composes AND-only with the others — no unifying interface.

# Failure Scenarios

- If an undeclared/empty tag set were fail-closed, every existing path would regress — AC-1 pins net-zero.
- If `null` resourceTags fell open, an unresolved-resource mutation could bypass a deny-if-present gate — AC-4 pins fail-safe.
- If matching were case-sensitive, `PROTECTED` would dodge a `protected` gate — AC-2 pins case-insensitive.
- If the contract were not flipped, a future adopter would re-derive `RESOURCE_TAG` — AC-5 flips § 1 and records the resolver-seam pattern.
