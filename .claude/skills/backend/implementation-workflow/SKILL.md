---
name: implementation-workflow
description: Full backend implementation workflow
category: backend
---

# Skill: Backend Implementation Workflow

Full workflow for implementing a backend task in this repository.

Prerequisite: read `specs/platform/coding-rules.md` and follow `specs/platform/entrypoint.md` before using this skill.

---

## Steps 1–6: Spec Reading

Follow the `CLAUDE.md` Required Workflow (steps 1–6) to read all required specs, contracts, and skills before implementation.

---

## Step 7: Implement

Follow the layer structure declared in `specs/services/<service>/architecture.md`.

**Layer violations to avoid:**
- application importing from `infrastructure.*` utilities directly
- domain importing from framework or web layer classes
- controller calling repositories directly

---

## Step 8: Write tests

Follow `specs/platform/testing-strategy.md` (Required Tests Per Task) and the `testing-backend` skill for implementation patterns.

---

## Step 9: Self-review checklist

Before finishing:
- [ ] No forbidden layer dependencies introduced
- [ ] All field names match contracts exactly
- [ ] All tests pass and cover acceptance criteria
- [ ] No infrastructure utilities imported in application or domain layer
- [ ] Contracts updated if API or event shape changed
