import { redirect } from 'next/navigation';

/**
 * `/` resolves to the authenticated console landing — the **5-domain
 * cross-domain Operator Overview** (`(console)/dashboards/overview`,
 * TASK-PC-FE-011 / ADR-MONO-017 § D8; promoted to the console
 * landing/home by TASK-PC-FE-034). One nav entry "개요" points here.
 * The `(console)` layout enforces the GAP OIDC session guard; an
 * unauthenticated visitor is redirected from there to `/login`.
 *
 * The GAP platform-operations detail (accounts · audit · operators
 * 3-leg composed overview, TASK-PC-FE-005) is no longer the landing —
 * it is reached one click deeper via the **GAP card** on this overview
 * (a drill-down to `/dashboards`; ADR-MONO-015 § D1-B re-position).
 *
 * The data-driven service catalog stays at `/console` (an in-console nav
 * destination); the catalog `gap.baseRoute` is unchanged (still
 * `/accounts`, FE-002).
 */
export default function RootIndex() {
  redirect('/dashboards/overview');
}
