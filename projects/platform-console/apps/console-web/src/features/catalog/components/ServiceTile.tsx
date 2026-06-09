'use client';

import Link from 'next/link';
import { cn } from '@/shared/lib/cn';
import type { RegistryProduct } from '@/shared/api/registry-types';
import { resolveConsoleRoute } from '../lib/console-route';

/**
 * One catalog tile. Rendered STRICTLY from the registry item — there is no
 * hardcoded product list (data-driven; flipping `available` in the registry
 * flips this tile with zero code change).
 *
 * TASK-PC-FE-064:
 *  - the product header shows a single domain-health status dot (`tone`),
 *    resolved by the catalog page from `getDomainHealthState()` (domain health
 *    is per-domain/global, so it lives on the product header — NOT per tenant);
 *  - tenants are BUTTONS (`onSelectTenant`) — clicking sets the active tenant +
 *    filters the catalog (handled by `CatalogGrid`). The tenant buttons live
 *    OUTSIDE the header `<Link>` (interactive content cannot nest in `<a>`).
 *
 * `available:false` → non-interactive "coming soon" tile (no dot / no tenants).
 */
export type TileTone = 'healthy' | 'attention' | 'unknown';

const TONE_DOT: Record<TileTone, string> = {
  healthy: 'bg-green-500',
  attention: 'bg-red-500',
  unknown: 'bg-muted-foreground/40',
};
const TONE_LABEL: Record<TileTone, string> = {
  healthy: '정상',
  attention: '주의',
  unknown: '점검 불가',
};

export interface ServiceTileProps {
  product: RegistryProduct;
  tone?: TileTone;
  /** Called with the tenant + this product's console route (TASK-PC-FE-065 —
   *  selecting a tenant activates it and navigates to the product's domain ops). */
  onSelectTenant?: (tenant: string, productRoute: string) => void;
}

export function ServiceTile({
  product,
  tone,
  onSelectTenant,
}: ServiceTileProps) {
  const { productKey, displayName, available, tenants } = product;
  const href = resolveConsoleRoute(product);

  if (!available) {
    return (
      <li>
        {/* aria-disabled lives on the inner group, not the <li>: role
            listitem does not support aria-disabled (jsx-a11y). */}
        <div
          role="group"
          aria-disabled="true"
          aria-label={`${displayName} — 준비 중`}
          data-testid={`tile-${productKey}`}
          className={cn(
            'rounded-lg border border-border bg-muted p-5 opacity-60',
          )}
        >
          <div className="flex items-start justify-between gap-2">
            <h2 className="text-base font-medium text-foreground">
              {displayName}
            </h2>
            <span className="shrink-0 rounded-full bg-muted-foreground/20 px-2 py-0.5 text-xs text-muted-foreground">
              Coming soon
            </span>
          </div>
          <p className="mt-1 text-sm text-muted-foreground">
            준비 중인 서비스입니다.
          </p>
        </div>
      </li>
    );
  }

  return (
    <li data-testid={`tile-${productKey}`}>
      <div className="rounded-lg border border-border bg-background p-5 transition-colors focus-within:border-primary hover:border-primary">
        <Link
          href={href}
          className="block rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          <h2 className="flex items-center gap-2 text-base font-medium text-foreground">
            {tone && (
              <span
                role="img"
                data-testid={`tile-${productKey}-status`}
                data-tone={tone}
                aria-label={`상태: ${TONE_LABEL[tone]}`}
                className={cn('h-2 w-2 shrink-0 rounded-full', TONE_DOT[tone])}
              />
            )}
            {displayName}
          </h2>
          <p className="mt-1 text-sm text-muted-foreground">
            {tenants.length > 0
              ? `${tenants.length}개 테넌트 이용 가능`
              : '이용 가능'}
          </p>
        </Link>
        {tenants.length > 0 && (
          <ul
            data-testid={`tile-${productKey}-tenants`}
            className="mt-2 space-y-0.5"
          >
            {tenants.map((tenant) => (
              <li key={tenant}>
                <button
                  type="button"
                  data-testid={`tile-${productKey}-tenant-${tenant}`}
                  onClick={() => onSelectTenant?.(tenant, href)}
                  className={cn(
                    'flex w-full items-center gap-1.5 rounded px-1.5 py-0.5 text-left text-xs text-muted-foreground transition-colors',
                    'hover:bg-accent hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                  )}
                >
                  {tone && (
                    <span
                      role="img"
                      data-testid={`tile-${productKey}-tenant-${tenant}-status`}
                      data-tone={tone}
                      aria-label={`상태: ${TONE_LABEL[tone]}`}
                      className={cn(
                        'h-1.5 w-1.5 shrink-0 rounded-full',
                        TONE_DOT[tone],
                      )}
                    />
                  )}
                  {tenant}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </li>
  );
}
