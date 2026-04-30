---
name: implementation-workflow
description: Full frontend implementation workflow
category: frontend
---

# Skill: Frontend Implementation Workflow

Step-by-step workflow for implementing frontend tasks in Next.js applications.

Prerequisite: read `platform/coding-rules.md` (TypeScript Rules section) and follow `platform/entrypoint.md` before using this skill.

---

## Workflow Steps

### 1. Read Specs

1. Read `CLAUDE.md`
2. Read the target task in `tasks/ready/`
3. Read `platform/entrypoint.md` → follow spec reading order
4. Read `.claude/skills/INDEX.md` → identify matched frontend skills
5. Read the app's architecture skill (`feature-sliced-design.md` or `layered-by-feature.md`)

### 2. Read Existing Code

1. Check the app's directory structure and existing patterns
2. Read related components, hooks, and API modules
3. Identify shared UI components in `shared/ui/`
4. Check API client configuration in `shared/config/`

### 3. Implement API Layer

1. Add API types to `@repo/types` if needed
2. Create or update feature API module (`features/{name}/api/`)
3. Follow existing API client patterns

### 4. Implement Hooks

1. Create query hooks with React Query (`use-{resource}.ts`)
2. Create mutation hooks with cache invalidation (`use-{action}.ts`)
3. Define query keys in `hooks/query-keys.ts`

### 5. Implement Components

1. Build feature components (`features/{name}/components/`)
2. Use shared UI components where possible
3. Follow existing component patterns (List, Detail, Form)

### 6. Implement Pages

1. Create page components in `app/` directory
2. Use server components for data fetching where appropriate
3. Wrap client components with `'use client'` directive

### 7. Write Tests

1. Test hooks with `@testing-library/react` + mocked API
2. Test components with `@testing-library/react`
3. Follow existing test patterns in `__tests__/`

### 8. Self-Review Checklist

- [ ] API response types match `specs/contracts/`
- [ ] Loading and error states handled
- [ ] Forms validate required fields
- [ ] Navigation and routing work correctly
- [ ] Tests cover happy path and error cases
- [ ] No TypeScript errors (`tsc --noEmit`)
- [ ] Follows the app's architecture pattern
