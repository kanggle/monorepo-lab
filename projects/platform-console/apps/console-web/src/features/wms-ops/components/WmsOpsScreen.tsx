'use client';

import { useId, useMemo, useState } from 'react';
import { ApiError, messageForCode } from '@/shared/api/errors';
import {
  useWmsAlerts,
  useWmsShipments,
  useAcknowledgeAlert,
} from '../hooks/use-wms-ops';
import {
  WMS_DEFAULT_PAGE_SIZE,
  type AlertPage,
  type AlertRow,
  type ShipmentPage,
  type ShipmentQueryParams,
} from '../api/types';
import { AcknowledgeAlertDialog } from './AcknowledgeAlertDialog';
import { WmsShipmentsTable } from './WmsShipmentsTable';
import { WmsAlertsTable } from './WmsAlertsTable';
import {
  type ShipFilterState,
  EMPTY_SHIP_FILTERS,
  alertLabel,
} from './wms-ops-helpers';

/**
 * wms operations section (TASK-PC-FE-007 — ADR-MONO-013 Phase 4 slice 1).
 * The first NON-IAM federated domain screen.
 *
 * Server-rendered initial shipments + alerts pages are passed in; client
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
 *
 * ── MODULE SPLIT (TASK-PC-FE-103) ── this container owns ALL state, the
 * mutation, and the lag banner; the read regions (shipments, alerts) are
 * rendered by the prop-driven `WmsShipmentsTable` / `WmsAlertsTable`
 * presentational children, and the filter shapes + alert label formatter
 * live in `wms-ops-helpers.ts`.
 *
 * ── INVENTORY SPLIT (TASK-PC-FE-173) ── the inventory query table (filters
 * + pagination — unfit for a glance-overview, PC-FE-170 same principle) has
 * moved OFF this screen to the dedicated `/wms/inventory` route
 * (`WmsInventoryScreen` + `WmsInventoryTable`). This screen keeps only the
 * count-tile inventory snapshot (`WmsOverview`, passed in via `overview`).
 */

export interface WmsOpsScreenProps {
  alerts: AlertPage;
  shipments: ShipmentPage;
  /** NON-blocking eventual-consistency hint (seconds), or null. */
  lagSeconds: number | null;
  /** Optional operator overview-snapshot slot rendered above the tables
   *  (TASK-PC-FE-166 — the server page computes `getWmsOverviewState` and
   *  passes a `<WmsOverview>` node; a server component slotted into this
   *  client screen, the RSC-idiomatic way). Absent ⇒ no snapshot band. */
  overview?: React.ReactNode;
}

export function WmsOpsScreen({
  alerts,
  shipments,
  lagSeconds,
  overview,
}: WmsOpsScreenProps) {
  const shipWhFid = useId();
  const shipCarrierFid = useId();

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

  const lagBanner = useMemo(() => {
    if (lagSeconds === null || lagSeconds <= 0) return null;
    return `데이터가 약 ${Math.round(lagSeconds)}초 지연될 수 있습니다 (읽기 모델은 최종 일관성 — 표시값이 잠시 과거일 수 있습니다).`;
  }, [lagSeconds]);

  return (
    <section aria-labelledby="wms-heading">
      <h1 id="wms-heading" className="mb-2 text-2xl font-semibold">
        WMS 개요
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        배송 · 알림 (읽기 + 알림 확인).
      </p>

      {/* Operator overview snapshot band (TASK-PC-FE-166) — per-area counts,
          alert-ack distribution, recent shipments; server-rendered slot. */}
      {overview}

      {lagBanner && (
        <div
          role="status"
          data-testid="wms-lag-hint"
          className="mb-6 rounded-md border border-amber-300/50 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-700/40 dark:bg-amber-950/40 dark:text-amber-200"
        >
          {lagBanner}
        </div>
      )}

      <WmsShipmentsTable
        shipWhFid={shipWhFid}
        shipCarrierFid={shipCarrierFid}
        filters={shipFilters}
        onFiltersChange={setShipFilters}
        onSubmit={submitShipFilters}
        forbidden={shipForbidden}
        degraded={shipDegraded}
        data={shipData}
        query={shipQuery}
        onPrevPage={() =>
          setShipQuery((q) => ({ ...q, page: Math.max(0, (q.page ?? 0) - 1) }))
        }
        onNextPage={() =>
          setShipQuery((q) => ({ ...q, page: (q.page ?? 0) + 1 }))
        }
      />

      <WmsAlertsTable rows={alertsData.content} onAck={openAck} />

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
