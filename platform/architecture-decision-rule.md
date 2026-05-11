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
- If the declared architecture is missing, emit the Hard Stop stanza below (per [`lint-remediation-message-standard.md`](lint-remediation-message-standard.md)) and halt implementation tool calls until a remediation option is chosen:

```
[VIOLATION] ARCH-RULE-01: Service architecture is not declared in `<project>/specs/services/<service>/architecture.md`.
[WHY] Architecture chosen during implementation cannot be defended against future "why was this chosen" review questions and shapes every downstream task on the service; the rule library forbids implicit architecture decisions.
[REMEDIATION] Choose one:
  1. Author the architecture spec under `<project>/specs/services/<service>/architecture.md` declaring the chosen style (Hexagonal / Layered / Clean / …) with rejected alternatives + reason; land the spec change before any code commit.
  2. If the decision is cross-service or irreversible, record it in `<project>/docs/adr/ADR-<scope>-XXX-<slug>.md` and PAUSE until ACCEPTED.
  3. If the decision is reversible and local, file a `tasks/ready/` follow-up task to backfill the architecture.md and reference its task ID in an inline code comment.
[REFERENCE] platform/architecture-decision-rule.md § Mandatory Rule + CLAUDE.md § HARDSTOP-09
```

---

# Selection Guidelines

For guidance on when to use each architecture style, consult the matching skill under `.claude/skills/backend/architecture/<style>/SKILL.md` (e.g. `hexagonal/`, `layered/`, `clean/`).

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