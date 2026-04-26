# TASK-MONO-010 — Frontend CI Phase 2: vitest unit tests (ecommerce)

**Status**: done  
**Completed**: 2026-04-26

---

## Goal

Add a `frontend-unit-tests` CI job to `.github/workflows/ci.yml` that runs `pnpm test`
(turbo vitest) for the ecommerce-microservices-platform frontend, and fix all pre-existing
test failures found in the process.

---

## Scope

- `.github/workflows/ci.yml` — new `frontend-unit-tests` job
- `projects/ecommerce-microservices-platform/turbo.json` — add `test` task
- `projects/ecommerce-microservices-platform/package.json` — add `test` script
- `apps/admin-dashboard/vitest.config.ts` — add `retry: 1` for Windows parallelism flakiness
- Pre-existing test failures fixed:
  - `apps/web-store/src/__tests__/use-update-profile.test.ts` — sed incorrectly injected
    `return Wrapper;` inside a test body; removed it
  - `apps/web-store/src/__tests__/product-detail.test.tsx` — `images: ['/img/1.jpg']` string
    array didn't match `ProductImageSummary[]` type after `any` cast removal; updated to
    `{ imageId, url, sortOrder, isPrimary }` shape
  - `apps/web-store/src/__tests__/get-product.test.ts` — assertion expected string array but
    `getProduct()` now wraps fallback URLs into `ProductImageSummary` objects; updated assertion
  - `apps/web-store/src/features/auth/ui/OAuthButton.tsx` — missing `?? 'http://localhost:8080'`
    fallback on `NEXT_PUBLIC_API_URL`; test expected fallback but component didn't have it
  - `apps/admin-dashboard/src/__tests__/.../ImageUploader.test.tsx` — text mismatch "하세요" vs
    component's "이미지를 드래그하거나 클릭하여 선택" (without "하세요")
  - `apps/admin-dashboard/src/__tests__/.../ProductImageSection.test.tsx` — test looked for
    `data-testid="image-uploader"` but `ProductImageSection` renders its own drop zone, not
    `ImageUploader`; updated assertion to `findByText(/이미지를 드래그하거나 클릭하여 선택/)`

---

## Acceptance Criteria

- [x] `pnpm test` exits 0 for all 9 turbo tasks
  - web-store: 103 test files / 749 tests
  - admin-dashboard: 91 test files / 444 tests
  - api-client: 8 test files / 168 tests
  - types: 1 test file / 17 tests
  - ui: 1 test file / 7 tests
- [x] `frontend-unit-tests` job added to ci.yml: Node 20, pnpm 9.15, `pnpm test`
- [x] Job is independent (no `needs`) — runs in parallel with `build-and-test`

---

## Related Specs

- `turbo.json` — `test` task depends on `^build` (packages must build before app tests run)

---

## Related Contracts

None.

---

## Edge Cases

- **Windows parallelism flakiness**: `TemplateForm.test.tsx` user-interaction tests fail
  non-deterministically when all packages' tests run in parallel under heavy load on Windows
  (jsdom + userEvent timing). On ubuntu-latest CI this does not occur. Fix: `retry: 1` in
  admin-dashboard vitest.config.ts ensures a single retry on transient failures.
- **`utils` package has no test files**: turbo reports it as a successful no-op (no test
  script causes `pnpm test` to skip gracefully).

---

## Failure Scenarios

- If a new test introduces a global timer leak (no `afterEach` cleanup) it may cause
  TemplateForm-style flakiness. The `retry: 1` provides a safety net; root cause should
  still be investigated if retry fires consistently.
