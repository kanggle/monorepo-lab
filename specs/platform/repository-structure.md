# Repository Structure

Defines the top-level directory layout and ownership rules for the monorepo.

---

# Directory Layout

```
/
├── apps/                    # Deployable services (owner: service team)
├── libs/                    # Shared Java libraries (owner: platform team)
├── packages/                # Shared TypeScript packages (owner: frontend team)
├── specs/                   # Specifications — source of truth (owner: architecture team)
│   ├── platform/
│   ├── contracts/ (http/, events/, schemas/)
│   ├── services/
│   └── features/
├── tasks/                   # Task lifecycle (owner: all teams)
├── .claude/skills/          # AI implementation guidance (owner: platform team)
├── knowledge/               # Design references (owner: all teams)
├── docs/                    # Human-readable documentation (owner: all teams)
├── infra/                   # Infrastructure monitoring configuration (alertmanager, grafana, prometheus)
├── k8s/                     # Kubernetes manifests and deployment overlays
├── load-tests/              # Load testing scenarios and scripts
├── scripts/                 # Build, deploy, and utility scripts
├── CLAUDE.md
├── pnpm-workspace.yaml
└── turbo.json
```

For the list of services under `apps/` and shared libraries under `libs/` and `packages/`, see `architecture.md`.

---

# Rules

- Do not create top-level directories without updating this document.
- Service directories under `apps/` must correspond to services declared in `architecture.md`.
- Shared libraries must pass the shared-library policy before being added to `libs/` or `packages/`.
- Each service under `specs/services/` must have an `architecture.md`.

---

# Change Rule

Changes to the repository structure must be documented here before implementation.
