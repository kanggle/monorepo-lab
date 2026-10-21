import type { GenericRow, Throughput } from '../api/types';

/**
 * Pure helpers for the `/wms` 개요 daily throughput card (TASK-PC-FE-358,
 * console-integration-contract § 2.4.5.2 «Daily throughput card»). No I/O, no
 * React, no server-only imports — shared by the SSR state function, the
 * same-origin `/api/wms/throughput` proxy (validation) and the client card
 * (zero-fill after a warehouse switch).
 *
 * ── WHY UTC, NOT KST ──
 * wms admin-service buckets every throughput event with
 * `effectiveAt.atZone(ZoneOffset.UTC).toLocalDate()` (`InboundProjectionService`
 * :246, `OutboundProjectionService` :228). A § 1.2 `from`/`to` therefore names
 * **UTC calendar days**. Using `kstPeriodBounds()` here would, between 00:00
 * and 09:00 KST, ask for a "today" the producer has not started yet. The card
 * labels its window «UTC 기준» instead of pretending the day is KST.
 *
 * ── WHY ZERO-FILL ──
 * § 1.2 lists only days that HAVE a row (`ThroughputDashboardController` :69-92
 * iterates a `TreeSet` of dates present in either table). A chart that drew
 * the response as-is would close up quiet days and draw a false trend, so
 * every date `from..to` the response omits is filled with zeros.
 */

/** § 1.2 — "max 90-day range" (inclusive span, `ChronoUnit.DAYS.between + 1`). */
export const MAX_THROUGHPUT_RANGE_DAYS = 90;

/** Owner decision (b), 2026-10-22: the last 14 UTC days ending today. */
export const DEFAULT_THROUGHPUT_WINDOW_DAYS = 14;

const DAY_MS = 24 * 60 * 60 * 1000;
const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;
const UUID =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** UTC midnight epoch-ms of an ISO `YYYY-MM-DD`, or `null` when it is not a real date. */
export function isoDateToUtcMs(s: string): number | null {
  const m = ISO_DATE.exec(s);
  if (!m) return null;
  const y = Number(m[1]);
  const mo = Number(m[2]);
  const d = Number(m[3]);
  const ms = Date.UTC(y, mo - 1, d);
  const back = new Date(ms);
  // `Date.UTC` normalises 2026-02-30 → 03-02; a round-trip rejects it.
  if (
    back.getUTCFullYear() !== y ||
    back.getUTCMonth() !== mo - 1 ||
    back.getUTCDate() !== d
  ) {
    return null;
  }
  return ms;
}

export function utcMsToIsoDate(ms: number): string {
  return new Date(ms).toISOString().slice(0, 10);
}

export function isIsoLocalDate(s: string): boolean {
  return isoDateToUtcMs(s) !== null;
}

export function isUuid(s: string): boolean {
  return UUID.test(s);
}

/**
 * The `days`-long window ending at the **UTC** calendar date of `now`
 * (inclusive both ends). `now` is injectable for tests.
 */
export function utcThroughputWindow(
  now: Date = new Date(),
  days: number = DEFAULT_THROUGHPUT_WINDOW_DAYS,
): { from: string; to: string } {
  const toMs = Date.UTC(
    now.getUTCFullYear(),
    now.getUTCMonth(),
    now.getUTCDate(),
  );
  const span = Math.max(1, Math.floor(days));
  return {
    from: utcMsToIsoDate(toMs - (span - 1) * DAY_MS),
    to: utcMsToIsoDate(toMs),
  };
}

/** Every ISO date `from..to` inclusive (empty when invalid or reversed). */
export function enumerateIsoDates(from: string, to: string): string[] {
  const a = isoDateToUtcMs(from);
  const b = isoDateToUtcMs(to);
  if (a === null || b === null || b < a) return [];
  const out: string[] = [];
  for (let ms = a; ms <= b; ms += DAY_MS) out.push(utcMsToIsoDate(ms));
  return out;
}

/**
 * Validate a § 1.2 query BEFORE it leaves the console (the proxy answers
 * `400 VALIDATION_ERROR` with this message; the producer would reject the
 * same inputs). Returns `null` when valid.
 */
export function validateThroughputQuery(q: {
  warehouseId: string | null;
  from: string | null;
  to: string | null;
}): string | null {
  if (!q.warehouseId || !isUuid(q.warehouseId)) {
    return 'warehouseId must be a UUID';
  }
  if (!q.from || !q.to) return 'from and to are required';
  const a = isoDateToUtcMs(q.from);
  const b = isoDateToUtcMs(q.to);
  if (a === null || b === null) {
    return 'from/to must be ISO-8601 LocalDate (YYYY-MM-DD)';
  }
  if (b < a) return 'to must be on or after from';
  const span = Math.round((b - a) / DAY_MS) + 1;
  if (span > MAX_THROUGHPUT_RANGE_DAYS) {
    return `range exceeds ${MAX_THROUGHPUT_RANGE_DAYS} days`;
  }
  return null;
}

/** One zero-filled day of the chart. */
export interface ThroughputDay {
  date: string;
  putawayCount: number;
  shipmentCount: number;
  qtyReceived: number;
  qtyShipped: number;
}

export interface ThroughputTotals {
  putawayCount: number;
  shipmentCount: number;
  qtyReceived: number;
  qtyShipped: number;
}

/**
 * Fill every date `from..to` with the response's row for that date, or zeros
 * when the response omits it. A response day outside the window is ignored
 * (it would otherwise widen the axis the operator asked for).
 */
export function zeroFillThroughput(
  data: Pick<Throughput, 'days'>,
  from: string,
  to: string,
): ThroughputDay[] {
  const byDate = new Map<string, Throughput['days'][number]>();
  for (const d of data.days) byDate.set(d.date, d);
  return enumerateIsoDates(from, to).map((date) => {
    const row = byDate.get(date);
    return {
      date,
      putawayCount: row?.inbound?.putawayCount ?? 0,
      shipmentCount: row?.outbound?.shipmentCount ?? 0,
      qtyReceived: row?.inbound?.qtyReceived ?? 0,
      qtyShipped: row?.outbound?.qtyShipped ?? 0,
    };
  });
}

/** Totals of the drawn (zero-filled, in-window) days — always agree with the bars. */
export function sumThroughput(days: readonly ThroughputDay[]): ThroughputTotals {
  return days.reduce<ThroughputTotals>(
    (acc, d) => ({
      putawayCount: acc.putawayCount + d.putawayCount,
      shipmentCount: acc.shipmentCount + d.shipmentCount,
      qtyReceived: acc.qtyReceived + d.qtyReceived,
      qtyShipped: acc.qtyShipped + d.qtyShipped,
    }),
    { putawayCount: 0, shipmentCount: 0, qtyReceived: 0, qtyShipped: 0 },
  );
}

/** A selectable warehouse. */
export interface WarehouseOption {
  id: string;
  code: string;
  name: string | null;
}

function str(v: unknown): string | null {
  return typeof v === 'string' && v.length > 0 ? v : null;
}

/**
 * Owner decision (a): keep `ACTIVE` warehouses (a row without `status` is
 * kept — tolerant), sorted by warehouse code ascending. The producer's
 * `MasterRefResponse` names the code `code`; `warehouseCode` (the sample
 * fixture's name) and the id are tolerant fallbacks.
 */
export function activeWarehouseOptions(
  rows: readonly GenericRow[],
): WarehouseOption[] {
  const out: WarehouseOption[] = [];
  for (const r of rows) {
    const id = str(r.id);
    if (!id) continue;
    const status = str(r.status);
    if (status !== null && status !== 'ACTIVE') continue;
    out.push({
      id,
      code: str(r.code) ?? str(r.warehouseCode) ?? id,
      name: str(r.name),
    });
  }
  return out.sort((a, b) =>
    a.code < b.code ? -1 : a.code > b.code ? 1 : a.id < b.id ? -1 : 1,
  );
}

/** Chart scaling: % of the window max, 2% sliver for a non-zero value, 0 when max is 0. */
export function barPercent(value: number, max: number): number {
  if (!(max > 0) || !(value > 0)) return 0;
  return Math.max(2, Math.min(100, (value / max) * 100));
}
