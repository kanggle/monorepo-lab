import { describe, it, expect, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import OperatorGroupsPage from '@/app/(console)/operator-groups/page';
import PermissionsPage from '@/app/(console)/permissions/page';
import PermissionSetsPage from '@/app/(console)/permission-sets/page';

/**
 * TASK-PC-FE-225 — IAM nav 정석 재편성: the 4 new stub routes it introduces
 * (`/tenants`, `/operator-groups`, `/permissions`, `/permission-sets`). Each
 * WAS a plain static server component (no data fetch / no auth gate — the
 * `(console)` layout supplies the auth guard) that renders a placeholder
 * notice. Real functionality was out of scope here (TASK-PC-FE-226/227/228,
 * ADR-MONO-046) — this only proved the route exists and renders (never a
 * 404), per the task's Failure Scenarios.
 *
 * `/tenants` graduated to the real tenant-management screen in
 * TASK-PC-FE-226 (`src/app/(console)/tenants/page.tsx` is now an async SSR
 * gate + `TenantsScreen`, not a static stub) — its coverage moved to
 * `tests/unit/tenants-page.test.tsx` / `tests/unit/features/tenants/*`. The
 * remaining 3 routes (`/operator-groups` / `/permissions` / `/permission-sets`)
 * are still stubs pending FE-227/228/ADR-MONO-046.
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

  it('/permissions renders the 권한 placeholder', () => {
    render(<PermissionsPage />);
    expect(
      screen.getByRole('heading', { name: '권한' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('permissions-stub')).toBeInTheDocument();
  });

  it('/permission-sets renders the 권한 세트 placeholder', () => {
    render(<PermissionSetsPage />);
    expect(
      screen.getByRole('heading', { name: '권한 세트' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('permission-sets-stub')).toBeInTheDocument();
  });
});
