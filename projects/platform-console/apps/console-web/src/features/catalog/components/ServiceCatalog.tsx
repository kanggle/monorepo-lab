import { CatalogGrid } from './CatalogGrid';
import type { TileTone } from './ServiceTile';
import type { CatalogState, ProductKey } from '@/shared/api/registry-types';

/**
 * Data-driven service catalog. Renders exactly the registry's products in
 * order. No hardcoded fallback list — `degraded` shows a non-blocking notice
 * while keeping the shell usable (integration-heavy resilience; task
 * Acceptance "app does not crash").
 *
 * The interactive grid (per-product health dot + tenant-filter / active-tenant
 * select — TASK-PC-FE-064) lives in the client {@link CatalogGrid}; this server
 * shell keeps the heading + degraded/empty notices.
 *
 * <p>🔴 TASK-MONO-711 ③ — the per-domain health leg used to fail **as an
 * absence**: `getDomainHealthState()` returned `health: null`, the page built
 * an empty `healthByDomain`, and the dots simply were not drawn. No copy, no
 * marker. The 2026-09-18 capture judged this screen «not degraded» (본문 436자,
 * 마커 0개) while one of its two legs was dead — a failure that is neither
 * worded nor marked is invisible to the degradation predicate (문구 ∪ 마커) by
 * construction, and it is invisible to the operator too. So the absence now
 * always carries an explanation; see {@link ServiceCatalogProps.healthState}.
 */
export interface ServiceCatalogProps {
  catalog: CatalogState;
  healthByDomain?: Partial<Record<ProductKey, TileTone>>;
  /**
   * Why the per-domain dots are (not) there. TASK-MONO-711 ③.
   *
   * - `'ok'` — dots rendered (or the registry simply has no matching domain).
   * - `'no-tenant'` — no active tenant yet. A **normal** state on this page:
   *   the catalog itself is the tenant picker, so the absence is expected and
   *   the notice is informational — deliberately NOT a degradation marker.
   * - `'unavailable'` — the health fan-out failed (BFF unreachable / 5xx /
   *   timeout / 401 on that leg). A real failure: marked so the degradation
   *   predicate can see it.
   *
   * 🔴 Both of the non-`'ok'` cases render something. Leaving the "normal" one
   * silent would re-open exactly the hole this prop closes — a bug that
   * manifests as `no-tenant` would once again be an unexplained blank.
   * 🔴 The *cause* (status code / transport) is NOT rendered — it goes to the
   * server log (`domain_health_bff_unavailable`, TASK-MONO-711 ②). Visitors do
   * not get internal state.
   */
  healthState?: 'ok' | 'no-tenant' | 'unavailable';
}

export function ServiceCatalog({
  catalog,
  healthByDomain,
  healthState = 'ok',
}: ServiceCatalogProps) {
  return (
    <section aria-labelledby="catalog-heading">
      <h1 id="catalog-heading" className="mb-6 text-2xl font-semibold">
        서비스
      </h1>

      {catalog.degraded && (
        <div
          role="status"
          className="mb-6 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
          data-testid="catalog-degraded"
        >
          서비스 카탈로그를 일시적으로 불러올 수 없습니다. 콘솔은 계속
          사용할 수 있으며 잠시 후 자동으로 다시 시도합니다.
        </div>
      )}

      {healthState === 'unavailable' && (
        <div
          role="status"
          className="mb-6 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
          data-testid="catalog-health-unavailable"
        >
          {/*
            🔴 «일시적» 이라고 쓰지 않는다. 방문자 배포(Vercel)에서 이 레그는
            **상시** 끊겨 있다(`console-bff` 에 엣지 라우터가 없다 —
            `infra/demo/console-vercel.override.yml` § 영구 열화). 상시 참인
            환경에서 «일시적» 은 거짓말이고, 거짓말하는 신호는 꺼진 신호와 같다.
          */}
          서비스별 상태를 불러올 수 없어 타일의 상태 표시를 비워 두었습니다.
          서비스 목록과 이동은 그대로 사용할 수 있습니다.
        </div>
      )}

      {healthState === 'no-tenant' && (
        <p
          className="mb-6 text-sm text-muted-foreground"
          data-testid="catalog-health-no-tenant"
        >
          {/*
            🔵 저하가 아니다 — 그래서 `-degraded`/`-unavailable` 접미사를 쓰지
            않는다(`scripts/capture-portfolio.mjs` DEGRADED_SUFFIXES). 그래도
            **말은 한다**: 설명 없는 공백이 이 티켓이 닫는 결함이다.
          */}
          테넌트를 선택하면 서비스별 상태가 함께 표시됩니다.
        </p>
      )}

      {catalog.products.length === 0 && !catalog.degraded ? (
        <p className="text-sm text-muted-foreground" data-testid="catalog-empty">
          이용 가능한 서비스가 없습니다.
        </p>
      ) : (
        <CatalogGrid
          products={catalog.products}
          healthByDomain={healthByDomain}
        />
      )}
    </section>
  );
}
