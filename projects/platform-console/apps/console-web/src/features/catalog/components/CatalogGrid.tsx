'use client';

import { useState } from 'react';
import { useTenantSwitch } from '@/features/tenant';
import type { RegistryProduct, ProductKey } from '@/shared/api/registry-types';
import { ServiceTile, type TileTone } from './ServiceTile';

/**
 * TASK-PC-FE-064 — the interactive catalog grid.
 *
 *  - Each product header shows a domain-health status dot (`healthByDomain`,
 *    keyed by `productKey`; absent ⇒ no dot — health degrades gracefully).
 *  - Clicking a tenant in a tile (a) sets it as the ACTIVE tenant via the
 *    existing assume-tenant flow (`useTenantSwitch` → `/api/tenant` +
 *    `router.refresh`), and (b) FILTERS the grid to products that include that
 *    tenant (`product.tenants.includes(filterTenant)`). "전체 보기" clears the
 *    view filter. The filter initialises from the current active tenant so the
 *    catalog reflects the operator's current tenant scope.
 */
export interface CatalogGridProps {
  products: RegistryProduct[];
  healthByDomain?: Partial<Record<ProductKey, TileTone>>;
  activeTenant?: string | null;
}

export function CatalogGrid({
  products,
  healthByDomain,
  activeTenant,
}: CatalogGridProps) {
  const [filterTenant, setFilterTenant] = useState<string | null>(
    activeTenant ?? null,
  );
  const switchTenant = useTenantSwitch();

  const onSelectTenant = (tenant: string) => {
    setFilterTenant(tenant);
    switchTenant.mutate(tenant);
  };

  const shown = filterTenant
    ? products.filter((p) => p.tenants.includes(filterTenant))
    : products;

  return (
    <div>
      {filterTenant && (
        <div
          data-testid="catalog-tenant-filter"
          className="mb-4 flex items-center justify-between gap-2 rounded-md border border-border bg-muted px-3 py-2 text-sm"
        >
          <span className="text-muted-foreground">
            테넌트{' '}
            <strong className="text-foreground">{filterTenant}</strong>의 도메인
          </span>
          <button
            type="button"
            data-testid="catalog-filter-clear"
            onClick={() => setFilterTenant(null)}
            className="shrink-0 rounded px-1.5 py-0.5 text-muted-foreground underline-offset-4 hover:text-foreground hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            전체 보기
          </button>
        </div>
      )}

      {shown.length === 0 ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="catalog-filter-empty"
        >
          이 테넌트에 이용 가능한 도메인이 없습니다.
        </p>
      ) : (
        <ul
          className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3"
          data-testid="catalog-grid"
        >
          {shown.map((product) => (
            <ServiceTile
              key={product.productKey}
              product={product}
              tone={healthByDomain?.[product.productKey]}
              onSelectTenant={onSelectTenant}
            />
          ))}
        </ul>
      )}
    </div>
  );
}
