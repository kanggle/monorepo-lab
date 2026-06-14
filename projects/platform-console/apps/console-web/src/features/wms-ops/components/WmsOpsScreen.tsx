'use client';

import { useId, useMemo, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import {
  useWmsInventory,
  useWmsAlerts,
  useWmsShipments,
  useAcknowledgeAlert,
} from '../hooks/use-wms-ops';
import {
  WMS_DEFAULT_PAGE_SIZE,
  type InventoryPage,
  type AlertPage,
  type AlertRow,
  type InventoryQueryParams,
  type ShipmentPage,
  type ShipmentQueryParams,
} from '../api/types';
import { AcknowledgeAlertDialog } from './AcknowledgeAlertDialog';

/**
 * wms operations section (TASK-PC-FE-007 — ADR-MONO-013 Phase 4 slice 1).
 * The first NON-IAM federated domain screen.
 *
 * Server-rendered initial inventory + alerts pages are passed in; client
 * re-query handles filter / pagination changes. The read-model is
 * eventually consistent — `lagSeconds` (when the producer surfaced
 * `X-Read-Model-Lag-Seconds`) is shown as a NON-blocking hint banner; the
 * section still renders (eventual-consistency honesty, § 2.4.5). There is
 * NO auto-refetch loop polling around the lag.
 *
 * The single mutation is the confirm-gated alert acknowledge — REASON-FREE
 * (wms does not define `X-Operator-Reason`; the confirm dialog is the
 * security gate). The adjustments audit and every other read carry NO
 * mutation affordance (append-only / read-only).
 *
 * Resilience (§ 2.5): 401 is handled by the server route (whole-session
 * re-login — not surfaced here as a per-section state); 403/404/422/409 →
 * inline actionable; 503/timeout → this section degrades only (the console
 * shell + IAM sections stay intact).
 */

export interface WmsOpsScreenProps {
  inventory: InventoryPage;
  alerts: AlertPage;
  shipments: ShipmentPage;
  /** NON-blocking eventual-consistency hint (seconds), or null. */
  lagSeconds: number | null;
}

interface InvFilterState {
  warehouseId: string;
  skuId: string;
  lowStockOnly: boolean;
}

const EMPTY_INV_FILTERS: InvFilterState = {
  warehouseId: '',
  skuId: '',
  lowStockOnly: false,
};

interface ShipFilterState {
  warehouseId: string;
  carrierCode: string;
}

const EMPTY_SHIP_FILTERS: ShipFilterState = {
  warehouseId: '',
  carrierCode: '',
};

function alertLabel(a: AlertRow): string {
  return a.alertType ? `${a.alertType} (${a.alertId})` : a.alertId;
}

export function WmsOpsScreen({
  inventory,
  alerts,
  shipments,
  lagSeconds,
}: WmsOpsScreenProps) {
  const whFid = useId();
  const skuFid = useId();
  const lowFid = useId();
  const shipWhFid = useId();
  const shipCarrierFid = useId();

  const [invFilters, setInvFilters] =
    useState<InvFilterState>(EMPTY_INV_FILTERS);
  const [invQuery, setInvQuery] = useState<InventoryQueryParams>({
    page: 0,
    size: inventory.page.size || WMS_DEFAULT_PAGE_SIZE,
  });

  const invSeeded =
    (invQuery.page ?? 0) === 0 &&
    !invQuery.warehouseId &&
    !invQuery.skuId &&
    !invQuery.lotId &&
    !invQuery.locationId &&
    !invQuery.lowStockOnly &&
    invQuery.minOnHand === undefined;

  const inv = useWmsInventory(invQuery, invSeeded ? inventory : undefined);
  const invData = inv.data ?? inventory;

  const invApiError =
    inv.error instanceof ApiError ? (inv.error as ApiError) : null;
  const invForbidden = invApiError?.status === 403;
  const invDegraded =
    inv.isError && (!invApiError || invApiError.status >= 500) && !invForbidden;

  // ── Shipments (택배/출고 read — carrier code / tracking no) ─────────────
  const [shipFilters, setShipFilters] =
    useState<ShipFilterState>(EMPTY_SHIP_FILTERS);
  const [shipQuery, setShipQuery] = useState<ShipmentQueryParams>({
    page: 0,
    size: shipments.page.size || WMS_DEFAULT_PAGE_SIZE,
  });

  const shipSeeded =
    (shipQuery.page ?? 0) === 0 &&
    !shipQuery.warehouseId &&
    !shipQuery.carrierCode;

  const ship = useWmsShipments(shipQuery, shipSeeded ? shipments : undefined);
  const shipData = ship.data ?? shipments;

  const shipApiError =
    ship.error instanceof ApiError ? (ship.error as ApiError) : null;
  const shipForbidden = shipApiError?.status === 403;
  const shipDegraded =
    ship.isError &&
    (!shipApiError || shipApiError.status >= 500) &&
    !shipForbidden;

  function submitShipFilters(e: React.FormEvent) {
    e.preventDefault();
    setShipQuery({
      warehouseId: shipFilters.warehouseId.trim() || undefined,
      carrierCode: shipFilters.carrierCode.trim() || undefined,
      page: 0,
      size: shipments.page.size || WMS_DEFAULT_PAGE_SIZE,
    });
  }

  const shipRows = shipData.content;
  const shipTotalPages = Math.max(1, shipData.page.totalPages);

  // The alerts list is not paginated in this slice's UI (only inventory
  // is). It is seeded page-0; the only re-fetch is the post-ack
  // invalidation (the acknowledged row then reflects state).
  const alertsQuery = useMemo(
    () => ({ page: 0, size: alerts.page.size || WMS_DEFAULT_PAGE_SIZE }),
    [alerts.page.size],
  );
  const alertsQ = useWmsAlerts(alertsQuery, alerts);
  const alertsData = alertsQ.data ?? alerts;

  const ack = useAcknowledgeAlert();
  // The Idempotency-Key is generated ONCE per a confirmed action and held
  // here while the dialog is open; a NEW confirmed attempt (a new dialog
  // open) generates a fresh key (§ 2.4.5 stable-per-action / fresh-per-
  // attempt).
  const [ackTarget, setAckTarget] = useState<{
    alert: AlertRow;
    idempotencyKey: string;
  } | null>(null);
  const [ackError, setAckError] = useState<string | null>(null);

  function openAck(alert: AlertRow) {
    setAckError(null);
    setAckTarget({ alert, idempotencyKey: crypto.randomUUID() });
  }

  function confirmAck() {
    if (!ackTarget) return;
    ack.mutate(
      {
        alertId: ackTarget.alert.alertId,
        idempotencyKey: ackTarget.idempotencyKey,
      },
      {
        onSuccess: () => {
          setAckTarget(null);
          setAckError(null);
        },
        onError: (e) => {
          // STATE_TRANSITION_INVALID (already acknowledged) / 409 / 404 →
          // inline actionable, no crash. 401 is a re-login signal handled
          // by the api client (not shown here).
          const code =
            e instanceof ApiError ? e.code : 'SERVICE_UNAVAILABLE';
          setAckError(
            code === 'STATE_TRANSITION_INVALID'
              ? messageForCode('ALERT_ALREADY_ACKNOWLEDGED')
              : messageForCode(code, '확인 처리에 실패했습니다.'),
          );
        },
      },
    );
  }

  function submitInvFilters(e: React.FormEvent) {
    e.preventDefault();
    setInvQuery({
      warehouseId: invFilters.warehouseId.trim() || undefined,
      skuId: invFilters.skuId.trim() || undefined,
      lowStockOnly: invFilters.lowStockOnly || undefined,
      page: 0,
      size: inventory.page.size || WMS_DEFAULT_PAGE_SIZE,
    });
  }

  const invRows = invData.content;
  const invTotalPages = Math.max(1, invData.page.totalPages);
  const alertRows = alertsData.content;

  const lagBanner = useMemo(() => {
    if (lagSeconds === null || lagSeconds <= 0) return null;
    return `데이터가 약 ${Math.round(lagSeconds)}초 지연될 수 있습니다 (읽기 모델은 최종 일관성 — 표시값이 잠시 과거일 수 있습니다).`;
  }, [lagSeconds]);

  return (
    <section aria-labelledby="wms-heading">
      <h1 id="wms-heading" className="mb-2 text-2xl font-semibold">
        WMS 운영
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        재고 스냅샷 · 알림 (읽기 + 알림 확인). wms 운영 표면을 콘솔 안에서
        조회합니다.
      </p>

      {lagBanner && (
        <div
          role="status"
          data-testid="wms-lag-hint"
          className="mb-6 rounded-md border border-amber-300/50 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-700/40 dark:bg-amber-950/40 dark:text-amber-200"
        >
          {lagBanner}
        </div>
      )}

      {/* ── Inventory snapshot ─────────────────────────────────────────── */}
      <h2 className="mb-3 text-lg font-medium text-foreground">
        재고 스냅샷
      </h2>
      <form
        onSubmit={submitInvFilters}
        className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4"
        role="search"
        aria-label="재고 스냅샷 필터"
      >
        <div>
          <label
            htmlFor={whFid}
            className="block text-sm font-medium text-foreground"
          >
            창고 ID
          </label>
          <input
            id={whFid}
            type="text"
            value={invFilters.warehouseId}
            onChange={(e) =>
              setInvFilters((f) => ({ ...f, warehouseId: e.target.value }))
            }
            data-testid="wms-inv-filter-warehouse"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          />
        </div>
        <div>
          <label
            htmlFor={skuFid}
            className="block text-sm font-medium text-foreground"
          >
            SKU ID
          </label>
          <input
            id={skuFid}
            type="text"
            value={invFilters.skuId}
            onChange={(e) =>
              setInvFilters((f) => ({ ...f, skuId: e.target.value }))
            }
            data-testid="wms-inv-filter-sku"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          />
        </div>
        <div className="flex items-end">
          <label
            htmlFor={lowFid}
            className="flex items-center gap-2 text-sm font-medium text-foreground"
          >
            <input
              id={lowFid}
              type="checkbox"
              checked={invFilters.lowStockOnly}
              onChange={(e) =>
                setInvFilters((f) => ({
                  ...f,
                  lowStockOnly: e.target.checked,
                }))
              }
              data-testid="wms-inv-filter-lowstock"
              className="h-4 w-4 rounded border-border"
            />
            저재고만
          </label>
        </div>
        <div className="flex items-end">
          <Button type="submit" data-testid="wms-inv-filter-submit">
            조회
          </Button>
        </div>
      </form>

      {invForbidden ? (
        <div
          role="status"
          data-testid="wms-inv-forbidden"
          className="mb-8 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {messageForCode('FORBIDDEN')}
        </div>
      ) : invDegraded ? (
        <div
          role="status"
          data-testid="wms-inv-degraded"
          className="mb-8 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          wms 재고 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은
          계속 사용할 수 있습니다.
        </div>
      ) : invRows.length === 0 ? (
        <p
          className="mb-8 text-sm text-muted-foreground"
          data-testid="wms-inv-empty"
        >
          표시할 재고 스냅샷이 없습니다.
        </p>
      ) : (
        <>
          <table
            className="mb-3 data-table"
            data-testid="wms-inv-table"
          >
            <caption className="sr-only">재고 스냅샷</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">
                  위치
                </th>
                <th scope="col" className="p-2">
                  SKU
                </th>
                <th scope="col" className="p-2">
                  로트
                </th>
                <th scope="col" className="p-2">
                  가용
                </th>
                <th scope="col" className="p-2">
                  예약
                </th>
                <th scope="col" className="p-2">
                  보유
                </th>
                <th scope="col" className="p-2">
                  저재고
                </th>
              </tr>
            </thead>
            <tbody>
              {invRows.map((r, i) => (
                <tr
                  key={`${r.locationId}-${r.skuId}-${r.lotId ?? 'nolot'}`}
                  data-testid={`wms-inv-row-${i}`}
                  className="border-b border-border"
                >
                  <td className="p-2">{r.locationCode ?? r.locationId}</td>
                  <td className="p-2">{r.skuCode ?? r.skuId}</td>
                  <td className="p-2">{r.lotNo ?? r.lotId ?? '—'}</td>
                  <td className="p-2">{r.availableQty ?? '—'}</td>
                  <td className="p-2">{r.reservedQty ?? '—'}</td>
                  <td className="p-2">{r.onHandQty ?? '—'}</td>
                  <td className="p-2">
                    {r.lowStockFlag ? (
                      <span
                        className="rounded bg-destructive/15 px-1.5 py-0.5 text-xs text-destructive"
                        data-testid={`wms-inv-low-${i}`}
                      >
                        저재고
                      </span>
                    ) : (
                      '—'
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <nav
            className="mb-8 flex items-center justify-between"
            aria-label="재고 페이지 이동"
          >
            <Button
              variant="secondary"
              disabled={(invQuery.page ?? 0) <= 0}
              onClick={() =>
                setInvQuery((q) => ({
                  ...q,
                  page: Math.max(0, (q.page ?? 0) - 1),
                }))
              }
              data-testid="wms-inv-prev"
            >
              이전
            </Button>
            <span
              className="text-sm text-muted-foreground"
              data-testid="wms-inv-pageinfo"
            >
              {`${invData.page.number + 1} / ${invTotalPages} 페이지 · 총 ${invData.page.totalElements}건`}
            </span>
            <Button
              variant="secondary"
              disabled={invData.page.number + 1 >= invData.page.totalPages}
              onClick={() =>
                setInvQuery((q) => ({ ...q, page: (q.page ?? 0) + 1 }))
              }
              data-testid="wms-inv-next"
            >
              다음
            </Button>
          </nav>
        </>
      )}

      {/* ── Shipments (택배/출고 read — carrier code / tracking no) ─────── */}
      <h2 className="mb-3 text-lg font-medium text-foreground">택배 / 출고</h2>
      <p className="mb-4 text-sm text-muted-foreground">
        출고 확정된 화물의 택배사 · 운송장번호 · 출고시각 (읽기 전용 — 출고
        확정은 출고 운영 화면에서 수행합니다).
      </p>
      <form
        onSubmit={submitShipFilters}
        className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3"
        role="search"
        aria-label="택배/출고 필터"
      >
        <div>
          <label
            htmlFor={shipWhFid}
            className="block text-sm font-medium text-foreground"
          >
            창고 ID
          </label>
          <input
            id={shipWhFid}
            type="text"
            value={shipFilters.warehouseId}
            onChange={(e) =>
              setShipFilters((f) => ({ ...f, warehouseId: e.target.value }))
            }
            data-testid="wms-ship-filter-warehouse"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          />
        </div>
        <div>
          <label
            htmlFor={shipCarrierFid}
            className="block text-sm font-medium text-foreground"
          >
            택배사 코드
          </label>
          <input
            id={shipCarrierFid}
            type="text"
            value={shipFilters.carrierCode}
            onChange={(e) =>
              setShipFilters((f) => ({ ...f, carrierCode: e.target.value }))
            }
            data-testid="wms-ship-filter-carrier"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          />
        </div>
        <div className="flex items-end">
          <Button type="submit" data-testid="wms-ship-filter-submit">
            조회
          </Button>
        </div>
      </form>

      {shipForbidden ? (
        <div
          role="status"
          data-testid="wms-ship-forbidden"
          className="mb-8 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {messageForCode('FORBIDDEN')}
        </div>
      ) : shipDegraded ? (
        <div
          role="status"
          data-testid="wms-ship-degraded"
          className="mb-8 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          wms 출고/택배 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른
          기능은 계속 사용할 수 있습니다.
        </div>
      ) : shipRows.length === 0 ? (
        <p
          className="mb-8 text-sm text-muted-foreground"
          data-testid="wms-ship-empty"
        >
          표시할 출고/택배 내역이 없습니다.
        </p>
      ) : (
        <>
          <table className="mb-3 data-table" data-testid="wms-ship-table">
            <caption className="sr-only">출고/택배 내역</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">
                  출고번호
                </th>
                <th scope="col" className="p-2">
                  주문번호
                </th>
                <th scope="col" className="p-2">
                  택배사
                </th>
                <th scope="col" className="p-2">
                  운송장번호
                </th>
                <th scope="col" className="p-2">
                  출고시각 (UTC)
                </th>
                <th scope="col" className="p-2">
                  수량
                </th>
              </tr>
            </thead>
            <tbody>
              {shipRows.map((s, i) => (
                <tr
                  key={s.shipmentId}
                  data-testid={`wms-ship-row-${i}`}
                  className="border-b border-border"
                >
                  <td className="p-2">{s.shipmentNo ?? '—'}</td>
                  <td className="p-2">{s.orderNo ?? '—'}</td>
                  <td className="p-2">
                    {s.carrierCode ? (
                      <span data-testid={`wms-ship-carrier-${i}`}>
                        {s.carrierCode}
                      </span>
                    ) : (
                      '—'
                    )}
                  </td>
                  <td className="p-2">{s.trackingNo ?? '—'}</td>
                  <td className="p-2">{s.shippedAt ?? '—'}</td>
                  <td className="p-2">{s.totalQty ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <nav
            className="mb-8 flex items-center justify-between"
            aria-label="출고/택배 페이지 이동"
          >
            <Button
              variant="secondary"
              disabled={(shipQuery.page ?? 0) <= 0}
              onClick={() =>
                setShipQuery((q) => ({
                  ...q,
                  page: Math.max(0, (q.page ?? 0) - 1),
                }))
              }
              data-testid="wms-ship-prev"
            >
              이전
            </Button>
            <span
              className="text-sm text-muted-foreground"
              data-testid="wms-ship-pageinfo"
            >
              {`${shipData.page.number + 1} / ${shipTotalPages} 페이지 · 총 ${shipData.page.totalElements}건`}
            </span>
            <Button
              variant="secondary"
              disabled={shipData.page.number + 1 >= shipData.page.totalPages}
              onClick={() =>
                setShipQuery((q) => ({ ...q, page: (q.page ?? 0) + 1 }))
              }
              data-testid="wms-ship-next"
            >
              다음
            </Button>
          </nav>
        </>
      )}

      {/* ── Alerts (confirm-gated acknowledge — the only mutation) ─────── */}
      <h2 className="mb-3 text-lg font-medium text-foreground">알림</h2>
      {alertRows.length === 0 ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="wms-alerts-empty"
        >
          표시할 알림이 없습니다.
        </p>
      ) : (
        <table
          className="data-table"
          data-testid="wms-alerts-table"
        >
          <caption className="sr-only">알림 목록</caption>
          <thead>
            <tr className="border-b border-border text-left">
              <th scope="col" className="p-2">
                유형
              </th>
              <th scope="col" className="p-2">
                메시지
              </th>
              <th scope="col" className="p-2">
                감지 시각 (UTC)
              </th>
              <th scope="col" className="p-2">
                상태
              </th>
              <th scope="col" className="p-2">
                작업
              </th>
            </tr>
          </thead>
          <tbody>
            {alertRows.map((a, i) => (
              <tr
                key={a.alertId}
                data-testid={`wms-alert-row-${i}`}
                className="border-b border-border"
              >
                <td className="p-2">{a.alertType ?? '—'}</td>
                <td className="p-2">{a.message ?? '—'}</td>
                <td className="p-2">{a.detectedAt ?? '—'}</td>
                <td className="p-2">
                  {a.acknowledged ? (
                    <span
                      className="rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground"
                      data-testid={`wms-alert-acked-${i}`}
                    >
                      확인됨
                    </span>
                  ) : (
                    <span className="rounded bg-amber-100 px-1.5 py-0.5 text-xs text-amber-900 dark:bg-amber-950/50 dark:text-amber-200">
                      미확인
                    </span>
                  )}
                </td>
                <td className="p-2">
                  {a.acknowledged ? (
                    <span className="text-xs text-muted-foreground">—</span>
                  ) : (
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => openAck(a)}
                      data-testid={`wms-alert-ack-${i}`}
                    >
                      확인 처리
                    </Button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <AcknowledgeAlertDialog
        open={ackTarget !== null}
        alertLabel={ackTarget ? alertLabel(ackTarget.alert) : ''}
        pending={ack.isPending}
        errorMessage={ackError}
        onConfirm={confirmAck}
        onCancel={() => {
          setAckTarget(null);
          setAckError(null);
        }}
      />
    </section>
  );
}
