import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-PC-FE-310 — `/console` is no longer the catalog home. The product ×
 * tenant grid + its concurrent `getCatalog()`/`getDomainHealthState()` fetch
 * (TASK-PC-FE-117, this file's ORIGINAL subject) moved into
 * `/dashboards/overview` — see `overview-page-parallel.test.tsx` (the
 * concurrency proof, now covering all three fetchers) and
 * `overview-catalog-fold.test.tsx` (the fold-specific DOM assertions).
 *
 * This file's only remaining subject is that `/console` survives as a bare
 * redirect (AC-7) — kept, not deleted, so bookmarks/the already-logged-in
 * `/login` shortcut/the root capture script keep landing somewhere real
 * (task Failure Scenario 2).
 */

const redirect = vi.fn((path: string) => {
  throw new Error(`NEXT_REDIRECT:${path}`);
});
vi.mock('next/navigation', () => ({ redirect: (p: string) => redirect(p) }));

import ConsoleHomePage from '@/app/(console)/console/page';

beforeEach(() => {
  redirect.mockClear();
});

describe('ConsoleHomePage — redirect-only (TASK-PC-FE-310)', () => {
  it('redirects to /dashboards/overview unconditionally', async () => {
    await expect(ConsoleHomePage()).rejects.toThrow(
      'NEXT_REDIRECT:/dashboards/overview',
    );
    expect(redirect).toHaveBeenCalledWith('/dashboards/overview');
    expect(redirect).toHaveBeenCalledTimes(1);
  });
});
