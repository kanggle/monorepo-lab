import type { ProductKey } from '@/shared/api/registry-types';

/**
 * The subscribable business domains (TASK-PC-FE-183 / ADR-MONO-023).
 *
 * The registry `ProductKey` enum includes `iam` — but IAM is the identity
 * plane itself, never a *subscription* (entitlement plane, ADR-023 D2). A
 * tenant's IAM access is intrinsic; only the 5 business domains are
 * subscribe-able. There is no shared 5-domain constant in the codebase, so
 * this is the canonical list for the subscription surface. Order mirrors the
 * sidebar "도메인 운영" group for visual consistency.
 *
 * `label` is a display fallback — the screen prefers the registry product's
 * `displayName` when the domain appears in the catalog, and uses this label
 * for domains the tenant is NOT subscribed to (absent from the catalog).
 */
export type SubscribableDomainKey = Exclude<ProductKey, 'iam'>;

export interface SubscribableDomain {
  key: SubscribableDomainKey;
  label: string;
}

export const SUBSCRIBABLE_DOMAINS: readonly SubscribableDomain[] = [
  { key: 'wms', label: 'WMS (창고관리)' },
  { key: 'scm', label: 'SCM (공급망)' },
  { key: 'finance', label: 'Finance (재무)' },
  { key: 'erp', label: 'ERP (전사자원)' },
  { key: 'ecommerce', label: 'E-Commerce (이커머스)' },
] as const;
