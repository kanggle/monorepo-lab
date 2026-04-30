# Workflow: Service Bootstrap

Workflow for creating a new service from scratch.

## Principle

> New services must follow the architecture declared in `specs/services/<service>/architecture.md`. (CLAUDE.md Architecture Rule)

## Steps

### 1. Architecture Decision

- Select an architecture pattern based on the service's domain complexity
- Follow `platform/architecture-decision-rule.md`
- Document the rationale as an ADR (`.claude/templates/adr-template.md`)

### 2. Write Service Spec

- Create `specs/services/<service>/architecture.md`
  - Selected architecture pattern
  - Layer structure and dependency directions
  - Tech stack
- Refer to `.claude/templates/service-architecture-template.md`

### 3. Define Contracts

- API contracts: `specs/contracts/` (if applicable)
- Event contracts: `specs/contracts/` (if applicable)

### 4. Create Project Structure

- Generate package/directory structure matching the selected architecture
- Build configuration (Gradle module / package.json)
- Configuration files (application.yml / .env)

### 5. Configure Infrastructure

- Write Dockerfile
- Kubernetes manifests (if applicable)
- Add to CI/CD pipeline

### 6. Base Implementation

- Health check endpoint
- Common configuration (security, error handling, logging)
- Database connection and initial migration

## Related Agents

| Role | Agent |
|---|---|
| Architecture selection | `architect` |
| API contracts | `api-designer` |
| DB schema | `database-designer` |
| Backend implementation | `backend-engineer` |
| Frontend implementation | `frontend-engineer` |
| Infrastructure setup | `devops-engineer` |
