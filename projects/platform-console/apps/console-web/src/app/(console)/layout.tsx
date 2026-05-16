import type { ReactNode } from 'react';
import Link from 'next/link';
import { redirect } from 'next/navigation';
import { isAuthenticated, getActiveTenant } from '@/shared/lib/session';
import { getCatalog } from '@/features/catalog';
import { selectableTenants, TenantSwitcher } from '@/features/tenant';
import { LogoutButton } from '@/features/auth';
import { ApiError } from '@/shared/api/errors';

export const dynamic = 'force-dynamic';

/**
 * Authenticated console shell layout.
 *
 * Server-side session guard: no GAP access-token cookie → redirect to
 * `/login` (no client-side token juggling — frontend-app.md
 * § Authentication). The tenant switcher options come from the operator's
 * own (GAP-scoped) registry response — multi-tenant isolation is enforced
 * producer-side; the switcher degrades to hidden / read-only for
 * zero / single-tenant operators (task Edge Case).
 *
 * Registry unavailable here does NOT blank the shell — the switcher simply
 * has no options; the catalog page renders its own degraded state
 * (integration-heavy resilience; console-integration-contract § 2.5).
 */
export default async function ConsoleLayout({
  children,
}: {
  children: ReactNode;
}) {
  if (!(await isAuthenticated())) redirect('/login');

  const activeTenant = await getActiveTenant();
  let tenants: string[] = [];
  try {
    const catalog = await getCatalog();
    tenants = selectableTenants(catalog.products);
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) redirect('/login');
    tenants = []; // degraded — switcher hidden, shell still usable
  }

  return (
    <div className="flex min-h-screen flex-col">
      <header className="border-b border-border bg-primary text-primary-foreground">
        <div className="mx-auto flex h-14 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
          <div className="flex items-center gap-6">
            <span className="text-lg font-semibold tracking-tight">
              Platform Console
            </span>
            <nav aria-label="콘솔 내비게이션">
              <ul className="flex items-center gap-4 text-sm">
                <li>
                  <Link
                    href="/console"
                    data-testid="nav-catalog"
                    className="rounded px-1 py-0.5 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-foreground"
                  >
                    카탈로그
                  </Link>
                </li>
                <li>
                  <Link
                    href="/audit"
                    data-testid="nav-audit"
                    className="rounded px-1 py-0.5 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-foreground"
                  >
                    감사 · 보안
                  </Link>
                </li>
                <li>
                  <Link
                    href="/operators"
                    data-testid="nav-operators"
                    className="rounded px-1 py-0.5 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-foreground"
                  >
                    운영자 관리
                  </Link>
                </li>
              </ul>
            </nav>
          </div>
          <div className="flex items-center gap-4">
            <TenantSwitcher tenants={tenants} activeTenant={activeTenant} />
            <LogoutButton />
          </div>
        </div>
      </header>
      <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-8 sm:px-6 lg:px-8">
        {children}
      </main>
    </div>
  );
}
