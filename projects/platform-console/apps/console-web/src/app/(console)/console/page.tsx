import { redirect } from 'next/navigation';
import { getCatalog, ServiceCatalog } from '@/features/catalog';
import type { TileTone } from '@/features/catalog';
import { getDomainHealthState, healthTone } from '@/features/domain-health';
import type { ProductKey } from '@/shared/api/registry-types';
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
 *
 * TASK-PC-FE-064/065 — also composes the per-domain health (for the product
 * header + per-tenant status dots). Best-effort: a null/degraded health simply
 * yields no dots (the catalog never blanks). The grid always shows the full
 * product list (no tenant filter); selecting a tenant navigates to that
 * product's domain operations (TASK-PC-FE-065).
 */
export default async function ConsoleHomePage() {
  // Fire both independent server fetches concurrently to avoid an SSR
  // waterfall (TASK-PC-FE-117): the catalog (IAM registry) and the per-domain
  // health fan-out have no data dependency, so starting them together turns
  // the page latency from `catalog + health` into `max(catalog, health)`.
  // `getDomainHealthState()` never throws (it catches every error and returns
  // a discriminated state), so leaving its promise un-awaited on the catalog
  // 401-redirect path raises no unhandled rejection.
  const catalogPromise = getCatalog();
  const healthPromise = getDomainHealthState();

  let catalog;
  try {
    catalog = await catalogPromise;
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) redirect('/login?error=session_expired');
    throw err;
  }

  const healthState = await healthPromise;

  // ProductKey ↔ domain-health domain key is 1:1 (iam/wms/scm/finance/erp).
  const healthByDomain: Partial<Record<ProductKey, TileTone>> = {};
  if (healthState.health) {
    for (const card of healthState.health.cards) {
      healthByDomain[card.domain as ProductKey] = healthTone(card);
    }
  }

  // 🔴 TASK-MONO-711 ③ — 「점이 없다」는 사실을 화면이 **말하게** 한다.
  //    여기 이전에는 실패가 `healthByDomain = {}` 로만 표현됐다: 문구도 마커도
  //    없이 **원소가 사라지는 것**이 유일한 자국이었고, 그래서 2026-09-18 촬영은
  //    레그 하나가 죽은 이 화면을 «저하 아님» 으로 집계했다(마커 0개).
  //    🔵 `no-tenant` 를 «저하» 로 섞지 않는다 — 이 페이지에서 테넌트 미선택은
  //    정상 상태다(카탈로그 자신이 테넌트 선택기다). 다만 **조용하지도 않게** 한다:
  //    설명 없는 공백을 하나라도 남기면 이 결함이 그 문으로 되돌아온다.
  const healthNotice: 'ok' | 'no-tenant' | 'unavailable' = healthState.health
    ? 'ok'
    : healthState.noTenant
      ? 'no-tenant'
      : 'unavailable';

  return (
    <ServiceCatalog
      catalog={catalog}
      healthByDomain={healthByDomain}
      healthState={healthNotice}
    />
  );
}
