# Refactoring Policy

Platform-wide rules for safe code refactoring across all services.

---

# Purpose

Define when, how, and under what constraints refactoring is allowed — ensuring behavior preservation and architecture alignment.

---

# Definition

Refactoring is a code change that improves internal structure without altering externally observable behavior.

The following are NOT refactoring:
- Adding new features or endpoints
- Changing API or event contracts
- Modifying business rules
- Changing database schema

---

# Preconditions

Before any refactoring:

1. **Tests must exist.** If no tests cover the target code, write tests first.
2. **Tests must pass.** Run the full test suite for the target service and confirm green.
3. **Architecture must be declared.** The target service must have `specs/services/<service>/architecture.md`.

If any precondition is not met, resolve it before starting refactoring.

---

# Allowed Refactoring Categories

| Category | Description | Risk |
|---|---|---|
| **Extract Method** | Break a long method into smaller, named methods | Low |
| **Extract Class** | Move a responsibility into its own class | Low |
| **Inline** | Remove unnecessary indirection (unused wrapper, trivial delegate) | Low |
| **Rename** | Rename class, method, variable, or package to match naming conventions | Low |
| **Remove Dead Code** | Delete unused classes, methods, fields, or imports | Low |
| **Move to Correct Layer** | Relocate code to the layer it belongs in per architecture spec | Medium |
| **Replace Pattern** | Replace an anti-pattern with the declared architecture pattern | Medium |
| **Simplify Conditional** | Flatten nested conditionals, replace with guard clauses or polymorphism | Medium |
| **Reduce Duplication** | Extract shared logic from duplicated code blocks | Medium |
| **Restructure Package** | Reorganize package structure to match naming conventions | High |

---

# Rules

### Mandatory

1. **No behavior change.** Externally observable behavior must remain identical before and after.
2. **Tests pass before and after.** Run the full service test suite at both checkpoints.
3. **One category at a time.** Do not mix multiple refactoring types in a single change.
4. **Architecture direction only.** Refactoring must move code toward the declared architecture, never away from it.
5. **No contract changes.** If refactoring requires API or event contract changes, use the contract-change workflow instead.

### Prohibited

- Refactoring production code and test code in the same change (adjust tests separately after).
- Refactoring without a green test baseline.
- Combining refactoring with feature work in the same commit.
- Moving domain logic into `libs/` (see `shared-library-policy.md`).

---

# Prioritization

When multiple refactoring opportunities exist, address them in this order:

1. **Layer violations** — code in the wrong architectural layer
2. **Pattern mismatches** — code not following declared architecture patterns
3. **Dead code** — safe removal, reduces noise for subsequent work
4. **Duplication** — extract shared logic
5. **Long methods / complexity** — structural improvement
6. **Naming** — align with `naming-conventions.md`

---

# Verification

After refactoring:

1. All existing tests pass without modification (test logic unchanged)
2. No new compiler warnings introduced
3. No architecture rule violations introduced (check `dependency-rules.md`)
4. Code follows `coding-rules.md` and `naming-conventions.md`

---

# Cross-references

- Architecture per service: `specs/services/<service>/architecture.md`
- Dependency rules: `platform/dependency-rules.md`
- Naming: `platform/naming-conventions.md`
- Coding rules: `platform/coding-rules.md`
- Shared library policy: `platform/shared-library-policy.md`
- Testing strategy: `platform/testing-strategy.md`

---

# Change Rule

Changes to this policy require team agreement and must be updated here before applying.
