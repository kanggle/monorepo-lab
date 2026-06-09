'use client';

import { useRouter } from 'next/navigation';
import { useTenantSwitch } from '@/features/tenant';
import type { RegistryProduct, ProductKey } from '@/shared/api/registry-types';
import { ServiceTile, type TileTone } from './ServiceTile';

/**
 * TASK-PC-FE-064/065 — the interactive catalog grid.
 *
 *  - ALWAYS renders the full product list (no tenant filter — the catalog is a
 *    discovery surface; TASK-PC-FE-065 dropped the PC-FE-064 filter).
 *  - Each product header (and each tenant row) shows the product's domain-health
 *    status dot (`healthByDomain`, keyed by `productKey`; absent ⇒ no dot —
 *    domain health is per-domain/global, so a tenant's dot == its product's).
 *  - Clicking a tenant inside a product card SETS it as the active tenant via
 *    the assume-tenant flow (`useTenantSwitch` → `/api/tenant`) and, once the
 *    switch lands, navigates to that product's domain-operations screen
 *    (`resolveConsoleRoute(product)`, passed up as `productRoute`) scoped to the
 *    selected tenant. The `onSuccess` ordering guarantees the destination
 *    renders with the NEW tenant (no stale-tenant flash).
 */
export interface CatalogGridProps {
  products: RegistryProduct[];
  healthByDomain?: Partial<Record<ProductKey, TileTone>>;
}

export function CatalogGrid({ products, healthByDomain }: CatalogGridProps) {
  const router = useRouter();
  const switchTenant = useTenantSwitch();

  const onSelectTenant = (tenant: string, productRoute: string) => {
    switchTenant.mutate(tenant, {
      onSuccess: () => router.push(productRoute),
    });
  };

  return (
    <ul
      className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3"
      data-testid="catalog-grid"
    >
      {products.map((product) => (
        <ServiceTile
          key={product.productKey}
          product={product}
          tone={healthByDomain?.[product.productKey]}
          onSelectTenant={onSelectTenant}
        />
      ))}
    </ul>
  );
}
