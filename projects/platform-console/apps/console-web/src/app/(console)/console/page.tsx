import { redirect } from 'next/navigation';
import { getCatalog, ServiceCatalog } from '@/features/catalog';
import { ApiError } from '@/shared/api/errors';

export const dynamic = 'force-dynamic';

/**
 * Authenticated catalog home (replaces the Phase-1 static placeholder).
 *
 * Server component — the data-driven catalog is fetched server-side from the
 * IAM registry with the HttpOnly operator token. Tiles render strictly from
 * the registry response (no hardcoded list); a registry timeout / 5xx yields
 * a degraded-but-usable catalog (task Acceptance). An auth failure forces a
 * clean re-login.
 */
export default async function ConsoleHomePage() {
  let catalog;
  try {
    catalog = await getCatalog();
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) redirect('/login');
    throw err;
  }
  return <ServiceCatalog catalog={catalog} />;
}
