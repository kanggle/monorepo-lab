import type { RegistryProduct } from '@/shared/api/registry-types';
import {
  SUBSCRIBABLE_DOMAINS,
  type SubscribableDomainKey,
} from '../api/domains';
import type { DerivedSubscriptionState } from '../api/types';

export interface DomainSubscriptionRow {
  key: SubscribableDomainKey;
  /** Prefer the registry displayName (when the domain is in the catalog);
   *  else the static fallback label. */
  label: string;
  state: DerivedSubscriptionState;
}

/**
 * Derive the active tenant's per-domain subscription state from the catalog
 * (TASK-PC-FE-183 / ADR-MONO-023). A domain is ACTIVE ⟺ an `available`
 * registry product for that key lists the active tenant in `tenants` (SUSPENDED
 * /CANCELLED subscriptions drop out of the registry, so catalog ≈ ACTIVE-only).
 * Every subscribable domain is returned (subscribed or not) in the canonical
 * order so the screen can render the full enable/manage surface.
 */
export function deriveDomainSubscriptions(
  products: RegistryProduct[],
  activeTenant: string,
): DomainSubscriptionRow[] {
  const byKey = new Map(products.map((p) => [p.productKey, p]));
  return SUBSCRIBABLE_DOMAINS.map((d) => {
    const product = byKey.get(d.key);
    const active =
      product !== undefined &&
      product.available &&
      product.tenants.includes(activeTenant);
    return {
      key: d.key,
      label: product?.displayName ?? d.label,
      state: active ? 'ACTIVE' : 'NOT_SUBSCRIBED',
    };
  });
}
