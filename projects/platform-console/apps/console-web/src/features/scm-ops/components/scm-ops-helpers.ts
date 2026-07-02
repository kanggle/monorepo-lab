import type { SnapshotResponse, SnapshotRow } from '../api/types';
import type { StatusTone } from '@/shared/ui/StatusBadge';

/**
 * Pure helpers for the scm ops screen (TASK-PC-FE-144 split). The PO filter
 * shape + its empty value, the tolerant known-PO-status list, and the
 * snapshot row-shape normaliser. No hooks, no JSX — shared by the container
 * and the presentational regions.
 */

export interface PoFilterState {
  status: string;
  supplierId: string;
}

export const EMPTY_PO_FILTERS: PoFilterState = { status: '', supplierId: '' };

/** Tolerant: an unknown / future PoStatus renders generically (no throw —
 *  the closed set is the producer's; the console never gates on it). */
export const KNOWN_PO_STATUSES = [
  'DRAFT',
  'SUBMITTED',
  'ACKNOWLEDGED',
  'CONFIRMED',
  'PARTIALLY_RECEIVED',
  'RECEIVED',
  'SETTLED',
  'CLOSED',
  'CANCELED',
];

/**
 * PO status → shared semantic {@link StatusTone} (rendered via the shared
 * `<StatusBadge>` — TASK-PC-FE-159). The lifecycle runs DRAFT (neutral) →
 * SUBMITTED (warning, awaiting ack) → ACKNOWLEDGED/CONFIRMED/PARTIALLY_RECEIVED
 * (progress) → RECEIVED/SETTLED (success). CLOSED is terminal-inactive
 * (neutral); CANCELED is terminal-bad (danger). `status` is a TOLERANT free
 * string — an unknown/future value → `neutral` (never a throw).
 */
const PO_STATUS_TONE: Record<string, StatusTone> = {
  DRAFT: 'neutral',
  SUBMITTED: 'warning',
  ACKNOWLEDGED: 'progress',
  CONFIRMED: 'progress',
  PARTIALLY_RECEIVED: 'progress',
  RECEIVED: 'success',
  SETTLED: 'success',
  CLOSED: 'neutral',
  CANCELED: 'danger',
};

export function poStatusTone(status: string | undefined): StatusTone {
  return status ? (PO_STATUS_TONE[status] ?? 'neutral') : 'neutral';
}

/**
 * Snapshot staleness → shared semantic {@link StatusTone} (TASK-PC-FE-159).
 * FRESH is healthy (success); STALE is aging data needing attention (warning);
 * UNREACHABLE is a probe failure (danger). Any other value (incl. UNKNOWN) →
 * `neutral` (TOLERANT — this is a free string).
 */
const STALENESS_TONE: Record<string, StatusTone> = {
  FRESH: 'success',
  STALE: 'warning',
  UNREACHABLE: 'danger',
};

export function stalenessTone(status: string | undefined): StatusTone {
  return status ? (STALENESS_TONE[status] ?? 'neutral') : 'neutral';
}

export function snapshotRows(snap: SnapshotResponse): SnapshotRow[] {
  // The snapshot data is the paginated cross-node form OR the single-node
  // array form — both render as a flat row list (tolerant).
  return Array.isArray(snap.data) ? snap.data : snap.data.content;
}
