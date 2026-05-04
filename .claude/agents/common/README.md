# Common Agents (Domain-Agnostic)

This directory holds agents whose responsibilities are **independent of the project's `domain`** declared in [`PROJECT.md`](../../../PROJECT.md). They are loaded for every project regardless of which domain / traits the project declares.

Domain-specific agents (when needed) live under [`../domain/`](../domain/) — see that directory's [README](../domain/README.md) for the rule.

---

## Catalog (13 agents)

| Agent | Role |
|---|---|
| [`architect.md`](architect.md) | High-level design, ADR drafting, cross-service patterns |
| [`api-designer.md`](api-designer.md) | HTTP / gRPC contract design, OpenAPI authoring |
| [`backend-engineer.md`](backend-engineer.md) | Server-side implementation, primary `Agent(subagent_type=...)` target for backend tasks |
| [`code-reviewer.md`](code-reviewer.md) | Independent review of pending changes (Critical / Warning / Suggestion classification) |
| [`coordinator.md`](coordinator.md) | Multi-agent orchestration, task decomposition |
| [`data-engineer.md`](data-engineer.md) | ETL, warehouse modeling, batch pipelines |
| [`database-designer.md`](database-designer.md) | Schema, migration, index strategy |
| [`devops-engineer.md`](devops-engineer.md) | Docker, k8s, CI/CD, Terraform |
| [`event-architect.md`](event-architect.md) | Domain events, Kafka topics, outbox pattern |
| [`frontend-engineer.md`](frontend-engineer.md) | Next.js / React implementation, FE state, API integration |
| [`ml-engineer.md`](ml-engineer.md) | Training pipelines, model registry, inference services |
| [`qa-engineer.md`](qa-engineer.md) | Test authoring, coverage verification |
| [`refactoring-engineer.md`](refactoring-engineer.md) | Behavior-preserving refactoring |

> Note: `data-engineer` / `ml-engineer` are PLACEHOLDERS — they activate when the first analytics-batch / ml-pipeline service exists in some project.

---

## Frontmatter convention

Every common agent file **must** declare:

```yaml
---
name: <agent-name>            # matches filename without .md
description: <one-line role>  # used by dispatcher scoring
model: opus | sonnet | haiku  # default model when invoked without override
tools: <comma-separated>      # tool allowlist (Read, Write, Edit, Glob, Grep, Bash, Agent, ...)
domains: [all]                # carries no project leak — matches every domain
service_types: [...]          # optional, used by dispatcher when service_type matches
triggers: [...]               # optional keyword triggers
---
```

`domains: [all]` is the standard for common agents — never list specific service names (e.g. `[web-store, admin-dashboard]`) because that leaks project-specific structure into the shared library and breaks the Library vs Project boundary (CLAUDE.md § Hard Stop Rules).

---

## Dispatch

Common agents activate via `Agent(subagent_type="<agent-name>", model="<override>", ...)` from any task context. The dispatcher routes based on:

1. `service_type` of the target service (read from `specs/services/<svc>/architecture.md`)
2. `domain` of the project (read from `PROJECT.md`)
3. Trigger keywords in the task body (matched against agent `triggers`)

Model selection rule (CLAUDE.md § Recommending Tasks): pass `model` explicitly per dispatch — do not rely on session inheritance. Complex domain work → Opus. CI / docs / chore → Sonnet or Haiku.

---

## See also

- [`../domain/README.md`](../domain/README.md) — when to add a domain-specific agent
- [`../../config/activation-rules.md`](../../config/activation-rules.md) — full routing table (trait/domain → skill bundle / agent set)
- [`../../../CLAUDE.md`](../../../CLAUDE.md) § Recommending Tasks and Dispatching Agents — model selection annotation rule
- [`../../../docs/guides/monorepo-workflow.md`](../../../docs/guides/monorepo-workflow.md) § Agent Dispatch — practical dispatch patterns
