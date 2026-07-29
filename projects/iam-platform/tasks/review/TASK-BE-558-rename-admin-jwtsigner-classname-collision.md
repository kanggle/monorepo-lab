# Task ID

TASK-BE-558

# Title

Rename admin-service's local `JwtSigner` to `OperatorJwtSigner` — resolve classname collision with `libs/java-security`'s `JwtSigner` interface

# Status

review

# Owner

backend

# Task Tags

- code

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

`apps/admin-service/src/main/java/com/example/admin/infrastructure/security/JwtSigner.java`
declares `public final class JwtSigner` with method `String sign(Map<String,Object>)`.
`libs/java-security` separately declares `com.example.security.jwt.JwtSigner` — an
**interface** with an identical simple name and an identical-shaped method.
`admin-service` depends on `libs:java-security`, so both types are on the same
classpath; admin's class wraps `Rs256JwtSigner` (the lib's implementation of the
lib's `JwtSigner` interface) internally but does **not** implement that interface
— it is an unrelated class that happens to share a name. This is a same-file
juxtaposition hazard: `admin/infrastructure/config/JwtConfig.java` imports
admin's local `JwtSigner` alongside the lib's `JwtVerifier` side by side.
Separately, `auth-service` (same project) uses the lib's `JwtSigner` directly —
so the identifier `JwtSigner` means two different things in two services of the
same project.

After this task, admin-service's local class is renamed to `OperatorJwtSigner`
(matching its existing bean name `operatorJwtSigner` in `JwtConfig` and its
documented role as the admin-service self-issuing **operator** IdP signer — see
`specs/services/admin-service/architecture.md` § Admin IdP Boundary /
§ JwtSigner Bean Requirements). All usages within admin-service are updated to
the new name, and the class's own stale javadoc reference to a nonexistent
`com.gap.security.jwt.JwtSigner` package (verified 0 hits via
`git ls-files "**/com/gap/**"`) is corrected to compare against the actual
`libs/java-security` `com.example.security.jwt.JwtSigner` interface (or removed
if the rename alone makes the two names unambiguous).

This is a pure rename + one javadoc correction. No behavior change.

---

# Scope

## In Scope

- Rename `com.example.admin.infrastructure.security.JwtSigner` (class) to
  `OperatorJwtSigner` (rename the file too:
  `JwtSigner.java` → `OperatorJwtSigner.java`).
- Update every reference to the old simple name within `admin-service`
  (main + test sources): imports, field/variable declarations, constructor
  calls, javadoc `{@link}` references, and the `JwtSignerTest.java` test
  class (rename to `OperatorJwtSignerTest.java` to match its subject).
- Correct the stale javadoc disambiguation comment in the renamed class
  (currently references a nonexistent `com.gap.security.jwt.JwtSigner`).
- Update `specs/services/admin-service/architecture.md` only if it names the
  class directly by its old simple name in a way the rename breaks (check
  before editing — the spec section already refers to it as "JwtSigner Bean"
  generically; only touch if a literal type reference would go stale).

## Out of Scope

- `libs/java-security`'s `com.example.security.jwt.JwtSigner` interface —
  unchanged.
- `auth-service`'s usage of the lib's `JwtSigner` — unchanged, not touched.
- Any behavior change to signing logic, kid selection, issuer injection, or
  the `signRefresh` convenience method — byte-identical logic, name-only
  change.
- Spring bean method name (`operatorJwtSigner` in `JwtConfig`) — already
  matches the new class name; no change needed there beyond the return/param
  type.

---

# Acceptance Criteria

- [ ] `admin-service` no longer declares any class named `JwtSigner` — the
      only `JwtSigner` reachable from admin-service's classpath is
      `libs/java-security`'s interface.
- [ ] New class `com.example.admin.infrastructure.security.OperatorJwtSigner`
      exists with identical field/method bodies as the old `JwtSigner` (rename
      only).
- [ ] All internal call sites (`BootstrapTokenService`, `AdminRefreshTokenIssuer`,
      `AdminRefreshTokenService`, `OperatorAccessTokenIssuer`, `JwtConfig`,
      `TotpConfig`, and their tests) compile against the new name.
- [ ] The stale `com.gap.security.jwt.JwtSigner` javadoc reference is gone;
      any remaining disambiguation note (if kept) accurately names
      `com.example.security.jwt.JwtSigner` (the real lib interface).
- [ ] `./gradlew :projects:iam-platform:apps:admin-service:test` passes GREEN
      with the same test count as the pre-change baseline.
- [ ] No other admin-service file references the old simple name `JwtSigner`
      in a way that would now fail to compile (grep verification, including
      Spring bean names/wiring and test doubles).
- [ ] `auth-service` and `libs/java-security` are untouched (`git diff --stat`
      shows no files under those paths).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `specs/services/admin-service/architecture.md` § Admin IdP Boundary,
  § JwtSigner Bean Requirements

# Related Skills

- `.claude/skills/backend/` (implementation conventions)

---

# Related Contracts

- None — no API/event contract shape changes (internal class rename only).

---

# Target Service

- `admin-service`

---

# Architecture

Follow:

- `specs/services/admin-service/architecture.md`

---

# Implementation Notes

- Read the full body of the old `JwtSigner.java` before renaming — it is not
  a bare pass-through: it injects the configured issuer claim (`putIfAbsent`)
  and resolves the active kid's private key from `AdminJwtKeyStore` before
  delegating to `Rs256JwtSigner`, plus exposes an `activeKid()` accessor and a
  `signRefresh(...)` convenience method for the operator refresh-token flow
  (TASK-BE-040). None of this behavior changes — only the class/file name and
  the stale javadoc line.
- Grep for the bare identifier `JwtSigner` (not `Rs256JwtSigner`,
  `JwtSignerVerifierTest`, etc.) across all of `admin-service` before starting,
  and again after the rename, to confirm zero stragglers.
- `JwtSignerTest.java` asserts on the class directly (`new JwtSigner(...)`) —
  rename both the file and the class reference; do not leave a test named
  after a class that no longer exists.
- `OperatorJwtTestFixture.java` in test support already uses the lib's
  `Rs256JwtSigner` directly (not admin's wrapper) — no change needed there,
  but re-verify after the rename that it still doesn't collide.

---

# Edge Cases

- Javadoc `{@link JwtSigner}` references in *other* classes
  (`AdminJwtKeyStore`, `OperatorAccessTokenIssuer`, `AdminLoginResponse`,
  `JwtConfig`) must be updated to `{@link OperatorJwtSigner}` — a stale
  `{@link}` doesn't fail compilation under default javac settings but is a
  correctness regression this task should not introduce.
- Confirm no `@Bean` name or `@Qualifier` string literal anywhere hardcodes
  the old class's simple name as a string (e.g. bean name overrides) — checked
  during implementation via grep.

---

# Failure Scenarios

- If any admin-service source file outside the ones enumerated above turns
  out to reference the old simple name and is missed, the module fails to
  compile — caught by `./gradlew :projects:iam-platform:apps:admin-service:test`
  before this task can move to `review/`.
- If the javadoc correction accidentally changes the described relationship
  (e.g. wrongly claims the local class *implements* the lib interface, which
  it does not), that would be a documentation regression — the corrected
  comment must state the actual relationship (wraps `Rs256JwtSigner`, does
  not implement the lib's `JwtSigner` interface).

---

# Test Requirements

- Existing unit tests (`JwtSignerTest` → renamed `OperatorJwtSignerTest`,
  `BootstrapTokenServiceTest`, `OperatorAccessTokenIssuerTest`,
  `AdminRefreshTokenServiceTest`) must pass unchanged in behavior — only
  the referenced type name changes.
- No new test scenarios required — this is a rename, not new behavior.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added (renamed test class only — no new scenarios)
- [ ] Tests passing (`./gradlew :projects:iam-platform:apps:admin-service:test` GREEN, identical test count to baseline)
- [ ] Contracts updated if needed (N/A)
- [ ] Specs updated first if required (N/A — no architecture change)
- [ ] Ready for review
