import type { ReactNode } from 'react';
import { redirect } from 'next/navigation';
import { isAuthenticated, getActiveTenant } from '@/shared/lib/session';
import { getCatalog } from '@/features/catalog';
import { selectableTenants, TenantSwitcher } from '@/features/tenant';
import { LogoutButton } from '@/features/auth';
import { ThemeToggle } from '@/shared/ui/ThemeToggle';
import { ConsoleSidebarNav } from '@/shared/ui/ConsoleSidebarNav';
import { ApiError } from '@/shared/api/errors';

export const dynamic = 'force-dynamic';

/**
 * Authenticated console shell layout (Vercel-style — TASK-PC-FE-039).
 *
 * Layout: a full-width sticky top bar holds only the brand + the **tenant
 * switcher** (+ theme toggle / logout account controls); the section
 * navigation lives in a **left sidebar** ({@link ConsoleSidebarNav}). The
 * sidebar is `hidden md:block` (desktop ops console; a mobile drawer is a
 * deferred follow-up — the top bar controls stay visible on all sizes).
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
      <header className="sticky top-0 z-40 border-b border-border bg-background/80 backdrop-blur supports-[backdrop-filter]:bg-background/60">
        <div className="flex h-14 items-center justify-between px-4 sm:px-6 lg:px-8">
          <span className="text-sm font-semibold tracking-tight text-foreground">
            Platform Console
          </span>
          <div className="flex items-center gap-3">
            <TenantSwitcher tenants={tenants} activeTenant={activeTenant} />
            <ThemeToggle />
            <LogoutButton />
          </div>
        </div>
      </header>
      <div className="flex flex-1">
        <aside className="hidden w-56 shrink-0 border-r border-border md:block">
          <ConsoleSidebarNav />
        </aside>
        <main className="min-w-0 flex-1 px-4 py-8 sm:px-6 lg:px-8">
          <div className="mx-auto max-w-6xl">{children}</div>
        </main>
      </div>
    </div>
  );
}
