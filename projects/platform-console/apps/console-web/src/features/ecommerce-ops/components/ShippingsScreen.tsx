'use client';

import { Button } from '@/shared/ui/Button';
import { messageForCode } from '@/shared/api/errors';
import { type ShippingList } from '../api/shipping-types';
import { ConfirmDialog } from './ConfirmDialog';
import { ShipFormDialog } from './ShipFormDialog';
import { ShippingsTable } from './ShippingsTable';
import { STATUS_FILTER_OPTIONS, statusLabel } from './shipping-labels';
import { useShippingsScreen } from '../hooks/use-shippings-screen';

/**
 * ecommerce shipping operations list section (TASK-PC-FE-088 — § 2.4.10.3).
 * The console equivalent of the `admin-dashboard` shipping list screen.
 *
 * Server-rendered initial page is passed in; client re-query handles
 * status-filter / pagination. Per-row actions:
 *   - Status transition (confirm-gated; PREPARING→SHIPPED opens ShipFormDialog
 *     for carrier+trackingNumber). Only the one allowed forward transition per
 *     linear state machine is offered.
 *   - Refresh tracking (operator-triggered carrier sync, best-effort).
 *
 * Shared pending guard (`isAnyPending`) prevents double-submit across both
 * mutations (mirrors admin-dashboard `isAnyPending` pattern).
 *
 * Resilience (§ 2.5): 401 handled by the server route (whole-session re-login);
 * 403 → inline actionable; 503/timeout → this section degrades only.
 *
 * TASK-PC-FE-140: query/state/mutations live in {@link useShippingsScreen}, the
 * list table + pagination in {@link ShippingsTable}, display labels in
 * `shipping-labels`. This container wires them to the heading/filter/branch +
 * the two dialogs (behavior-preserving split).
 */

export interface ShippingsScreenProps {
  shippings: ShippingList;
}

export function ShippingsScreen({ shippings }: ShippingsScreenProps) {
  const {
    statusFid,
    statusFilter,
    setStatusFilter,
    submitFilter,
    forbidden,
    degraded,
    loading,
    rows,
    isAnyPending,
    openTransition,
    triggerRefresh,
    pagination,
    pendingTransition,
    transitionError,
    confirmTransition,
    cancelTransition,
    transitionConfirmLabel,
    transitionDescription,
    updateStatusPending,
    shipDialog,
    shipError,
    confirmShip,
    cancelShip,
  } = useShippingsScreen(shippings);

  return (
    <section aria-labelledby="ecommerce-shippings-heading">
      <div className="mb-2 flex items-center justify-between">
        <h1
          id="ecommerce-shippings-heading"
          className="text-2xl font-semibold"
        >
          E-Commerce 배송
        </h1>
      </div>
      <p className="mb-6 text-sm text-muted-foreground">
        배송 목록 · 상태 전이(PREPARING→SHIPPED→IN_TRANSIT→DELIVERED) · 운송장 추적 동기화.
      </p>

      <form
        onSubmit={submitFilter}
        className="mb-4 flex flex-wrap items-end gap-3"
        role="search"
        aria-label="배송 필터"
      >
        <div>
          <label
            htmlFor={statusFid}
            className="block text-sm font-medium text-foreground"
          >
            상태
          </label>
          <select
            id={statusFid}
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            data-testid="shipping-status-filter"
            className="mt-1 rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            {STATUS_FILTER_OPTIONS.map((s) => (
              <option key={s || 'all'} value={s}>
                {s ? statusLabel(s) : '전체'}
              </option>
            ))}
          </select>
        </div>
        <Button type="submit" data-testid="shipping-filter-submit">
          조회
        </Button>
      </form>

      {forbidden ? (
        <div
          role="status"
          data-testid="shipping-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {messageForCode('FORBIDDEN')}
        </div>
      ) : degraded ? (
        <div
          role="status"
          data-testid="shipping-degraded"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          ecommerce 배송 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른
          기능은 계속 사용할 수 있습니다.
        </div>
      ) : loading ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="shipping-loading"
        >
          조회 중…
        </p>
      ) : rows.length === 0 ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="shipping-empty"
        >
          표시할 배송이 없습니다.
        </p>
      ) : (
        <ShippingsTable
          rows={rows}
          isAnyPending={isAnyPending}
          openTransition={openTransition}
          triggerRefresh={triggerRefresh}
          pagination={pagination}
        />
      )}

      {/* Confirm dialog for non-SHIPPED transitions */}
      <ConfirmDialog
        open={pendingTransition !== null}
        title="배송 상태를 변경할까요?"
        description={transitionDescription}
        confirmLabel={transitionConfirmLabel}
        tone="default"
        pending={updateStatusPending}
        errorMessage={transitionError}
        onConfirm={confirmTransition}
        onCancel={cancelTransition}
      />

      {/* ShipFormDialog for PREPARING → SHIPPED (needs carrier + trackingNumber;
          WMS-deduct toggle shown only on wmsRouted rows — ADR-MONO-022 D4 v2(c)) */}
      <ShipFormDialog
        open={shipDialog !== null}
        shippingId={shipDialog?.id ?? ''}
        wmsRouted={shipDialog?.wmsRouted ?? false}
        pending={updateStatusPending}
        errorMessage={shipError}
        onConfirm={confirmShip}
        onCancel={cancelShip}
      />
    </section>
  );
}
