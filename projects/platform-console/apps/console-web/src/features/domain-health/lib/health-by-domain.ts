import type { DomainHealthState } from '../api/domain-health-state';
import { healthTone, type HealthTone } from './tone';

/**
 * TASK-PC-FE-310 — single-copy extraction of the "health state → per-product
 * tone map + why the dots are (not) there" shape that `(console)/console/page.tsx`
 * (the pre-fold catalog home) used to build inline. Both the catalog grid
 * NOW embedded in the operator overview (`dashboards/overview/page.tsx`) and
 * any future consumer read from here — TASK-MONO-711 ③'s rule ("an absence
 * must always carry an explanation") lives in ONE place, not two copies that
 * can drift apart (Failure Scenario 5).
 *
 * `K` is left generic (not hard-coded to `ProductKey`) so this module — which
 * lives in `features/domain-health` — does NOT need to import
 * `features/catalog`'s types (Allowed Dependencies — architecture.md §
 * Forbidden Dependencies forbids a cross-feature import the other way; this
 * keeps the dependency edge pointing catalog → domain-health, never back).
 */
export interface HealthByDomainResult<K extends string> {
  healthByDomain: Partial<Record<K, HealthTone>>;
  /**
   * Why the per-domain dots are (not) there — same 3-state judgement
   * `ServiceCatalog.healthState` renders (TASK-MONO-711 ③). See that prop's
   * doc comment for what each value means.
   */
  healthNotice: 'ok' | 'no-tenant' | 'unavailable';
}

export function deriveHealthByDomain<K extends string>(
  healthState: DomainHealthState,
): HealthByDomainResult<K> {
  const healthByDomain: Partial<Record<K, HealthTone>> = {};
  if (healthState.health) {
    for (const card of healthState.health.cards) {
      healthByDomain[card.domain as K] = healthTone(card);
    }
  }

  // 🔴 TASK-MONO-711 ③ copied verbatim from the original `console/page.tsx` —
  //    see that history for why a silent `{}` used to be indistinguishable
  //    from "every domain healthy".
  const healthNotice: 'ok' | 'no-tenant' | 'unavailable' = healthState.health
    ? 'ok'
    : healthState.noTenant
      ? 'no-tenant'
      : 'unavailable';

  return { healthByDomain, healthNotice };
}
