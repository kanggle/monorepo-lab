# Task ID

TASK-BE-013

# Title

Fix TASK-BE-002: saltLength constructor parameter silently ignored in Argon2idPasswordHasher

# Status

review

# Owner

backend

# Task Tags

- code
- test

---

# Goal

Fix the `Argon2idPasswordHasher` in `libs/java-security` so that the `saltLength` constructor parameter is actually passed through to the underlying password4j `Argon2Function`, satisfying the spec requirement of 16-byte salt (`specs/features/password-management.md`). The current implementation accepts `saltLength` as a constructor argument but silently drops it — `Argon2Function.getInstance(memoryKb, iterations, parallelism, outputLength, Argon2.ID)` is called without the salt length, while password4j exposes a 6-argument overload `getInstance(int, int, int, int, Argon2, int)` that accepts a salt length.

This task was identified during the code review of TASK-BE-002.

---

# Scope

## In Scope

- Fix `Argon2idPasswordHasher(int, int, int, int, int)` to call `Argon2Function.getInstance(memoryKb, iterations, parallelism, outputLength, Argon2.ID, saltLength)` using the 6-argument overload
- Verify that the no-arg constructor `Argon2idPasswordHasher()` continues to use `DEFAULT_SALT_LENGTH = 16` (matching the spec: 16-byte salt)
- Update `PasswordHasherTest` to assert that the output hash encodes the expected salt length (if assertable via the PHC string format) or confirm that the correct 6-argument overload is exercised

## Out of Scope

- Changes to JWT utilities
- Changes to any service module
- Password policy logic (stays in auth-service domain layer)

---

# Acceptance Criteria

- [ ] `Argon2idPasswordHasher(int, int, int, int, int)` calls `Argon2Function.getInstance` with all 5 int parameters including saltLength
- [ ] The no-arg `Argon2idPasswordHasher()` uses saltLength=16 as spec requires
- [ ] Existing `PasswordHasherTest` roundtrip and failure tests continue to pass
- [ ] `./gradlew :libs:java-security:test` passes
- [ ] `./gradlew build` passes (all 6 libs + all apps)

---

# Related Specs

- `specs/features/password-management.md` — salt: 16 bytes (random), output: 32 bytes
- `platform/shared-library-policy.md` — no domain logic in libs; this is a pure utility fix

# Related Skills

- `.claude/skills/backend/jwt-auth/SKILL.md`
- `.claude/skills/review-checklist/SKILL.md`

---

# Related Contracts

없음 (라이브러리 태스크)

---

# Target Service

- `libs/java-security`

---

# Architecture

Shared library. Follow `platform/shared-library-policy.md`. Pure Java — no Spring dependencies.

---

# Implementation Notes

- password4j `Argon2Function` has two factory methods:
  - `getInstance(int memory, int iterations, int parallelism, int outputLength, Argon2 type)` — 5 args, salt length uses library default
  - `getInstance(int memory, int iterations, int parallelism, int outputLength, Argon2 type, int saltLength)` — 6 args, explicit salt length
- The fix is to call the 6-argument overload and pass `saltLength` through
- The `Argon2Function` instance is cached by password4j internally (flyweight pattern), so calling `getInstance` with same parameters is safe and returns the same instance

---

# Edge Cases

- `saltLength <= 0` passed to constructor: add defensive validation (throw `IllegalArgumentException`) matching the null-check pattern already used for `plainPassword`
- Calling the no-arg constructor must still produce spec-compliant 16-byte salt output

---

# Failure Scenarios

- password4j rejects an invalid saltLength at runtime: the constructor will fail fast, which is the desired behavior
- Test performance: test suite already uses low-parameter profile (memoryKb=4096, iterations=1), saltLength=16 has no meaningful perf impact

---

# Test Requirements

- `PasswordHasherTest`: all existing tests must pass without modification
- Add a test asserting that `Argon2idPasswordHasher(4096, 1, 1, 32, 16)` produces a hash and that the hash can be verified — confirming the 6-arg path executes correctly
- `./gradlew :libs:java-security:test` passes

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added and passing
- [ ] `./gradlew build` 전체 통과
- [ ] Ready for review
