# Architecture Decision Rule

This document defines how service architecture must be selected and documented.

---

# Purpose

Different services may use different internal architectures.

Architecture must not be chosen arbitrarily during implementation.

The architecture for each service must be declared in:

`specs/services/<service>/architecture.md`

---

# Mandatory Rule

- Every service must declare its architecture explicitly.
- AI agents and developers must follow the declared architecture.
- Do not change service architecture implicitly during implementation.
- If the declared architecture is missing, stop and report the issue.

---

# Selection Guidelines

For guidance on when to use each architecture style, see `knowledge/architecture/architecture-selection-guide.md`.

---

# Prohibited Decisions

Do not choose architecture based on:

- personal preference
- familiarity only
- temporary convenience
- copying another service without spec support

---

# Change Rule

If service architecture must change:

1. update `specs/services/<service>/architecture.md`
2. record the reason in ADR if the impact is significant
3. update related task/spec documents first
4. only then implement code changes

---

# Implementation Rule

Service implementation must follow the architecture declared in the service spec, even if another architecture would also be valid in theory.