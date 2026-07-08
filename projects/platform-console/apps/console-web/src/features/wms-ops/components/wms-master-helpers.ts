import type { StatusTone } from '@/shared/ui/StatusBadge';
import type { GenericRow, RefType } from '../api/types';
import { REF_TYPES } from '../api/types';

/**
 * Pure helpers for the wms **마스터**(master reference data) screen
 * (TASK-PC-FE-223, mirrors `wms-ops-helpers.ts`'s ASN section split). The
 * ref-type tab labels, the `q`/`status` filter shape (+ its empty default),
 * and generic-row field extraction (the producer's `RefPage` row is a
 * tolerant `GenericRow` — `id`/`code`/`name`/`status`/`lastEventAt`, per-type
 * "code" field name varies, per `wms-platform` `admin-service`
 * `domain-model.md` § 5 MasterRef tables). No hooks, no JSX.
 */

/** `{type}` ∈ warehouses|zones|locations|skus|lots|partners — the read-model
 *  supported set confirmed in `admin-service-api.md` § 1.7. Every entry is a
 *  tab; the console never invents a type the producer doesn't document. */
export const REF_TYPE_LABELS: Record<RefType, string> = {
  warehouses: '창고',
  zones: '구역',
  locations: '로케이션',
  skus: 'SKU',
  lots: 'Lot',
  partners: '거래처',
};

export const REF_TYPE_TABS: RefType[] = [...REF_TYPES];

export interface MasterFilterState {
  q: string;
  status: string;
}

export const EMPTY_MASTER_FILTERS: MasterFilterState = {
  q: '',
  status: '',
};

/**
 * Per-type "code" field name on the denormalised `*Ref` projection
 * (`domain-model.md` § 5: `warehouse_code` / `zone_code` / `location_code` /
 * `sku_code` / `lot_no` / `partner_code` — camelCased on the wire).
 */
const CODE_FIELD: Record<RefType, string> = {
  warehouses: 'warehouseCode',
  zones: 'zoneCode',
  locations: 'locationCode',
  skus: 'skuCode',
  lots: 'lotNo',
  partners: 'partnerCode',
};

function asString(v: unknown): string | null {
  return typeof v === 'string' && v.length > 0 ? v : null;
}

/** The row's display "코드" — the type-specific code field, falling back to
 *  a generic `code`/`id` (TOLERANT — never throws on an unexpected shape). */
export function refCode(type: RefType, row: GenericRow): string {
  return (
    asString(row[CODE_FIELD[type]]) ??
    asString(row.code) ??
    asString(row.id) ??
    '—'
  );
}

/** The row's display "명칭" — `name` (absent on `LotRef`, which has no
 *  `name` field — § 5). `null` renders as "—" at the call site. */
export function refName(row: GenericRow): string | null {
  return asString(row.name);
}

export function refStatus(row: GenericRow): string | undefined {
  return asString(row.status) ?? undefined;
}

export function refLastEventAt(row: GenericRow): string | undefined {
  return asString(row.lastEventAt) ?? undefined;
}

/** The row's stable React key — `id` first (PK per § 5), else the code. */
export function refRowKey(type: RefType, row: GenericRow, index: number): string {
  return asString(row.id) ?? refCode(type, row) ?? String(index);
}

/**
 * Maps a ref row's `status` to a shared semantic {@link StatusTone}. The
 * per-type status vocabulary is NOT enumerated by `domain-model.md` § 5
 * (`enum`, unspecified values) — this is a generic, TOLERANT heuristic
 * (ACTIVE-ish → success, INACTIVE/RETIRED/CLOSED-ish → neutral, everything
 * else → neutral), never a per-type hardcoded map (unlike `asnStatusTone`,
 * whose producer vocabulary IS enumerated). Unknown / absent / future status
 * → `neutral` — the console never crashes on an enum it doesn't know.
 */
export function refStatusTone(status: string | undefined): StatusTone {
  if (!status) return 'neutral';
  if (status === 'ACTIVE') return 'success';
  if (['INACTIVE', 'RETIRED', 'CLOSED', 'ARCHIVED'].includes(status)) {
    return 'neutral';
  }
  return 'neutral';
}
