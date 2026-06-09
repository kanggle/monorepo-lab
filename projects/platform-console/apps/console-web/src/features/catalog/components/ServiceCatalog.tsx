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
 */
export interface ServiceCatalogProps {
  catalog: CatalogState;
  healthByDomain?: Partial<Record<ProductKey, TileTone>>;
  activeTenant?: string | null;
}

export function ServiceCatalog({
  catalog,
  healthByDomain,
  activeTenant,
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

      {catalog.products.length === 0 && !catalog.degraded ? (
        <p className="text-sm text-muted-foreground" data-testid="catalog-empty">
          이용 가능한 서비스가 없습니다.
        </p>
      ) : (
        <CatalogGrid
          products={catalog.products}
          healthByDomain={healthByDomain}
          activeTenant={activeTenant}
        />
      )}
    </section>
  );
}
