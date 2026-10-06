import { redirect } from 'next/navigation';

export const dynamic = 'force-dynamic';

/**
 * TASK-PC-FE-310 — the old catalog home. The service catalog grid moved
 * INTO the operator overview (`dashboards/overview/page.tsx`): a no-tenant
 * operator sees it directly as the landing content, and an active-tenant
 * operator finds it in the «제품·테넌트 전체» `<details>` section below the
 * overview cards. The sidebar no longer carries a «카탈로그» entry
 * (`console-nav-config.ts`).
 *
 * This route survives as a pure redirect — not deleted — so every existing
 * bookmark, the already-logged-in `/login` shortcut
 * (`(auth)/login/page.tsx`), and the shared root capture script
 * (`scripts/capture-portfolio.mjs`, out of this task's scope) keep landing
 * somewhere real instead of 404ing (task Failure Scenario 2).
 */
export default async function ConsoleHomePage() {
  redirect('/dashboards/overview');
}
