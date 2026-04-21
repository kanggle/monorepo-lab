---
name: ci-cd
description: GitHub Actions CI/CD pipelines
category: infra
---

# Skill: CI/CD

Patterns for GitHub Actions CI/CD pipelines in this repository.

Prerequisite: read `specs/platform/deployment-policy.md` before using this skill.

---

## Pipeline Structure

```
.github/workflows/
├── backend-ci.yml      # Backend services (Gradle build + test)
├── frontend-ci.yml     # Frontend apps (pnpm build + test)
└── deploy.yml          # Deployment (on merge to main)
```

---

## Backend CI Pipeline

### Change Detection

Only build/test services that have changes:

```yaml
jobs:
  detect-changes:
    runs-on: ubuntu-latest
    outputs:
      services: ${{ steps.changes.outputs.services }}
    steps:
      - uses: actions/checkout@v4
      - id: changed-files
        uses: tj-actions/changed-files@v44
        with:
          files_yaml: |
            libs:
              - 'libs/**'
            auth-service:
              - 'apps/auth-service/**'
            order-service:
              - 'apps/order-service/**'
```

### Build & Test Matrix

```yaml
  build-and-test:
    needs: detect-changes
    if: needs.detect-changes.outputs.services != '[]'
    strategy:
      matrix:
        service: ${{ fromJson(needs.detect-changes.outputs.services) }}
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - uses: gradle/actions/setup-gradle@v3
      - run: ./gradlew :apps:${{ matrix.service }}:build
      - run: ./gradlew :apps:${{ matrix.service }}:test
```

---

## Frontend CI Pipeline

```yaml
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: pnpm/action-setup@v2
      - uses: actions/setup-node@v4
        with:
          node-version: 20
          cache: pnpm
      - run: pnpm install --frozen-lockfile
      - run: pnpm --filter {app} build
      - run: pnpm --filter {app} test
```

---

## Key Patterns

### Trigger on PR to Protected Branches

```yaml
on:
  pull_request:
    branches: [main, develop]
    paths:
      - 'apps/**'
      - 'libs/**'
```

### Caching

```yaml
# Gradle
- uses: gradle/actions/setup-gradle@v3  # built-in caching

# pnpm
- uses: actions/setup-node@v4
  with:
    cache: pnpm
```

### Testcontainers in CI

GitHub Actions runners have Docker pre-installed. Testcontainers work out of the box.

```yaml
services:
  # No need for explicit Docker service — Testcontainers manages containers
```

### libs Change → Rebuild All

If `libs/` changes, all services that depend on it must be rebuilt:

```yaml
- id: set-matrix
  run: |
    if [ "${{ steps.changed-files.outputs.libs_any_changed }}" == "true" ]; then
      echo "services=[\"auth-service\",\"order-service\",\"product-service\"]" >> $GITHUB_OUTPUT
    else
      # build only changed services
    fi
```

---

## Rules

- PR checks must pass before merge.
- Only build/test affected services (change detection).
- Use `--frozen-lockfile` for deterministic installs.
- Testcontainers-based integration tests run in CI.
- No deployment on PR — deploy only on merge to `main`.

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Building all services on every PR | Use change detection matrix |
| No cache for Gradle/pnpm | Use setup-gradle and setup-node with cache |
| Tests fail due to Docker not available | GitHub Actions runners have Docker — check service health |
| Deploying from feature branch | Only deploy from `main` branch |
| Missing `libs` dependency check | Rebuild all dependent services when `libs/` changes |
