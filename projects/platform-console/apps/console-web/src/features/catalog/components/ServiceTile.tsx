import Link from 'next/link';
import { cn } from '@/shared/lib/cn';
import type { RegistryProduct } from '@/shared/api/registry-types';

/**
 * One catalog tile. Rendered STRICTLY from the registry item — there is no
 * hardcoded product list (data-driven; flipping `available` in the registry
 * flips this tile with zero code change — task Acceptance).
 *
 * `available:false` (erp/finance pre-bootstrap, present OR absent) →
 * non-interactive "coming soon" tile, identical treatment (task Edge Case).
 */
export function ServiceTile({ product }: { product: RegistryProduct }) {
  const { productKey, displayName, available, tenants, baseRoute } = product;

  if (!available) {
    return (
      <li>
        {/* aria-disabled lives on the inner group, not the <li>: role
            listitem does not support aria-disabled (jsx-a11y). The
            non-interactive "coming soon" state stays programmatically
            exposed + visible. data-testid co-located with aria-disabled. */}
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
      <Link
        href={baseRoute}
        className={cn(
          'block rounded-lg border border-border bg-background p-5 transition-colors',
          'hover:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2',
        )}
      >
        <h2 className="text-base font-medium text-foreground">{displayName}</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          {tenants.length > 0
            ? `${tenants.length}개 테넌트 이용 가능`
            : '이용 가능'}
        </p>
      </Link>
    </li>
  );
}
