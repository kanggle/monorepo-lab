import { redirect } from 'next/navigation';

/**
 * `/` resolves to the authenticated console landing — the GAP composed
 * operator overview (`(console)/dashboards`, TASK-PC-FE-005 / ADR-MONO-013
 * Phase 2 slice 4). The `(console)` layout enforces the GAP OIDC session
 * guard; an unauthenticated visitor is redirected from there to `/login`.
 *
 * The data-driven service catalog stays at `/console` (an in-console nav
 * destination); the catalog `gap.baseRoute` is unchanged (still
 * `/accounts`, FE-002).
 */
export default function RootIndex() {
  redirect('/dashboards');
}
