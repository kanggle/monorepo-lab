---
name: frontend-app-setup
description: Set up a `frontend-app` service end-to-end
category: service-types
---

# Skill: Frontend App Service Setup

Implementation orchestration for a `frontend-app` service (Next.js).

Prerequisite: read `platform/service-types/frontend-app.md` before using this skill.

---

## Orchestration Order

1. **Architecture pattern** — choose `frontend/architecture/feature-sliced-design/SKILL.md` or `frontend/architecture/layered-by-feature/SKILL.md` and declare in `architecture.md`
2. **Tooling baseline** — Next.js App Router, TypeScript strict, ESLint, Prettier, Vitest, Testing Library
3. **Performance budget** — declare per-route JS budget in CI config
4. **API client** — typed client under `shared/api/`, derived from `specs/contracts/http/`
5. **Auth** — `frontend/auth-client/SKILL.md` (HttpOnly cookies + server refresh route)
6. **Component primitives** — `frontend/component-library/SKILL.md` (shadcn/ui style under `shared/ui/`)
7. **State management** — `frontend/state-management/SKILL.md` (React Query for server state)
8. **Forms** — `frontend/form-handling/SKILL.md` or `frontend/server-actions/SKILL.md`
9. **Loading / error UX** — `frontend/loading-error-handling/SKILL.md`
10. **Bundling** — `frontend/bundling-perf/SKILL.md`
11. **Observability** — web-vitals reporting, client error capture
12. **a11y** — axe-core in component tests, Lighthouse in CI
13. **Tests** — component, hook, E2E for critical journeys

---

## Repository Layout (Feature-Sliced Design example)

```
apps/web-store/
  app/                     # Next.js routes
  src/
    features/              # business features
      cart/
        api/
        components/
        hooks/
        model/
    shared/
      api/                 # typed API client
      ui/                  # design system primitives
      lib/                 # utilities
      config/
    entities/              # domain entities
    widgets/               # composite UI blocks
```

---

## Performance Budget in CI

`next.config.js`:

```js
module.exports = {
  experimental: {
    bundlePagesRouterDependencies: true,
  },
};
```

`.github/workflows/web-store.yml` includes:

```yaml
- name: Check bundle budget
  run: |
    pnpm --filter web-store build
    pnpm --filter web-store check-bundle-budget
```

Where `check-bundle-budget` is a script that fails if first-load JS exceeds the per-route budget declared in `architecture.md`.

---

## API Client Skeleton

```ts
// apps/web-store/src/shared/api/client.ts
import { z } from 'zod';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE!;

async function request<T>(input: string, init: RequestInit, schema: z.ZodType<T>): Promise<T> {
  let res = await fetch(`${API_BASE}${input}`, {
    credentials: 'include',
    ...init,
    headers: { 'Content-Type': 'application/json', ...init.headers },
  });
  if (res.status === 401) {
    const refreshed = await fetch('/api/auth/refresh', { method: 'POST', credentials: 'include' });
    if (refreshed.ok) {
      res = await fetch(`${API_BASE}${input}`, { credentials: 'include', ...init });
    }
  }
  if (!res.ok) throw new ApiError(res.status, await res.text());
  return schema.parse(await res.json());
}

export const apiClient = { request };
```

Every feature wraps this with typed methods and never calls `fetch` directly.

---

## a11y in CI

`.github/workflows/web-store.yml`:

```yaml
- name: Run Lighthouse a11y
  uses: treosh/lighthouse-ci-action@v11
  with:
    urls: |
      http://localhost:3000/
      http://localhost:3000/products
    assertMatrix: |
      [{"matchingUrlPattern": ".*", "assertions": {"categories:accessibility": ["error", {"minScore": 0.9}]}}]
```

---

## web-vitals Reporting

```tsx
// app/layout.tsx
'use client';
import { useReportWebVitals } from 'next/web-vitals';

useReportWebVitals(metric => {
  navigator.sendBeacon('/api/vitals', JSON.stringify(metric));
});
```

The `/api/vitals` route forwards to the observability backend.

---

## Self-Review Checklist

Verify against `platform/service-types/frontend-app.md` Acceptance section. Specifically:

- [ ] Architecture pattern declared and followed
- [ ] Typed API client wraps every backend call
- [ ] HttpOnly cookie auth with server refresh route
- [ ] Performance budget enforced in CI
- [ ] a11y check enforced in CI
- [ ] web-vitals reporting wired
- [ ] At least one E2E test for the critical user journey
- [ ] No JWT in localStorage (verified by manual check + lint rule)
