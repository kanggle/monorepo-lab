---
id: TASK-FE-016
title: "Fix issues found in TASK-FE-011 (dashboard multi-tab)"
status: ready
area: frontend
service: admin-web
---

## Goal

Fix issues found in TASK-FE-011: remove unused `useSearchParams` import and replace direct `clsx` usage with the project-wide `cn` utility from `@/shared/lib/cn` in `DashboardTabs.tsx`.

## Scope

### In

1. **`apps/admin-web/src/app/(console)/dashboards/_components/DashboardTabs.tsx`**
   - Remove unused `useSearchParams` import (violates `platform/coding-rules.md` "No dead code" rule)
   - Replace `import clsx from 'clsx'` with `import { cn } from '@/shared/lib/cn'` to follow the project convention shared by all other components in the codebase
   - Update `clsx(...)` call to `cn(...)` accordingly

### Out

- Logic changes to `page.tsx`
- Test changes (existing tests already pass and no behavior changes)
- Any other files

## Acceptance Criteria

- [ ] `useSearchParams` is not imported in `DashboardTabs.tsx`
- [ ] `clsx` is not imported directly in `DashboardTabs.tsx`
- [ ] `cn` from `@/shared/lib/cn` is used for class name merging
- [ ] All existing unit tests (`tests/unit/DashboardTabs.test.tsx`) continue to pass
- [ ] No `any` types introduced

## Related Specs

- `specs/services/admin-web/architecture.md`

## Related Contracts

없음 (백엔드 API 변경 없음)

## Edge Cases

- N/A (pure import/utility swap; no behavior change)

## Failure Scenarios

- N/A (no runtime logic changes)

## Test Requirements

- Run `npx vitest run tests/unit/DashboardTabs.test.tsx` — all 6 tests must pass
