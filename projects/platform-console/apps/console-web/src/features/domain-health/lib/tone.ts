import type { Card } from '../api/types';

/**
 * 3-tone classification of a domain-health card, shared by the domain-health
 * summary band and the catalog product-header status dot (TASK-PC-FE-064).
 *
 * `ok`+`UP` → 정상(green); `ok`+non-`UP` (DOWN/OUT_OF_SERVICE/UNKNOWN, producer
 * self-reported) → 주의(red); `degraded` (leg unreachable) → 점검 불가(gray).
 */
export type HealthTone = 'healthy' | 'attention' | 'unknown';

export function healthTone(card: Card): HealthTone {
  if (card.status === 'degraded') return 'unknown';
  return card.data.status === 'UP' ? 'healthy' : 'attention';
}
