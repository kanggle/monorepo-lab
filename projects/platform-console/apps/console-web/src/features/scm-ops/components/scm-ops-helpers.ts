import type { SnapshotResponse, SnapshotRow } from '../api/types';

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

export function snapshotRows(snap: SnapshotResponse): SnapshotRow[] {
  // The snapshot data is the paginated cross-node form OR the single-node
  // array form — both render as a flat row list (tolerant).
  return Array.isArray(snap.data) ? snap.data : snap.data.content;
}
