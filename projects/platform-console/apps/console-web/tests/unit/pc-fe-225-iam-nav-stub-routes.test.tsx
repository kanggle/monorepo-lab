import { describe, it, expect, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import OperatorGroupsPage from '@/app/(console)/operator-groups/page';

/**
 * TASK-PC-FE-225 — IAM nav 정석 재편성: the 4 new stub routes it introduces
 * (`/tenants`, `/operator-groups`, `/permissions`, `/permission-sets`). Each
 * WAS a plain static server component (no data fetch / no auth gate — the
 * `(console)` layout supplies the auth guard) that rendered a placeholder
 * notice, proving only that the route exists and renders (never a 404), per
 * the task's Failure Scenarios.
 *
 * Three of the four have since graduated to real screens:
 *   - `/tenants` → TASK-PC-FE-226 (async SSR gate + `TenantsScreen`);
 *     coverage moved to `tenants-page.test.tsx` / `features/tenants/*`.
 *   - `/permissions` + `/permission-sets` → TASK-PC-FE-227 / -228 (async
 *     server components over `getRbacCatalogState()` + operator-token/tenant
 *     gating); coverage moved to `PermissionsScreen.test.tsx` /
 *     `PermissionSetsScreen.test.tsx` + `rbac-catalog-api.test.ts`.
 * Only `/operator-groups` remains a plain static stub (pending ADR-MONO-046).
 */
afterEach(() => cleanup());

describe('TASK-PC-FE-225 stub routes', () => {
  it('/operator-groups renders the 운영자 그룹 placeholder', () => {
    render(<OperatorGroupsPage />);
    expect(
      screen.getByRole('heading', { name: '운영자 그룹' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('operator-groups-stub')).toBeInTheDocument();
  });
});
