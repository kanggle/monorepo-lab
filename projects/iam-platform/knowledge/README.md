# Knowledge

This directory contains design references, trade-off analyses, and best practices.

---

# Purpose

Use `knowledge/` for:
- Design judgment and decision rationale
- Technology trade-off comparisons
- Pattern references that inform (but do not override) specs
- Research and investigation notes

Do NOT use `knowledge/` as a substitute for `specs/`.
If a decision is official policy, it belongs in `platform/` or `specs/services/`.

---

# Structure

Organize files by topic:

```
knowledge/
├── architecture/     # Architectural patterns and trade-offs
├── database/         # DB design decisions and references
├── messaging/        # Event-driven design references
├── security/         # Security pattern references
└── README.md
```

---

# Priority

`knowledge/` is lower priority than all `specs/` documents.
If `knowledge/` content conflicts with `specs/`, follow `specs/`.
