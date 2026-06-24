'use client';

import { useId, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import {
  useShippings,
  useUpdateShippingStatus,
  useRefreshTracking,
} from '../hooks/use-ecommerce-shippings';
import {
  SHIPPING_DEFAULT_PAGE_SIZE,
  SHIPPING_STATUS_VALUES,
  allowedNextStatus,
  type ShippingList,
  type ShippingListParams,
} from '../api/shipping-types';
import { ConfirmDialog } from './ConfirmDialog';
import { ShipFormDialog } from './ShipFormDialog';

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
 */

export interface ShippingsScreenProps {
  shippings: ShippingList;
}

const STATUS_FILTER_OPTIONS = ['', ...SHIPPING_STATUS_VALUES] as const;

const STATUS_LABELS: Record<string, string> = {
  PREPARING: '준비중',
  SHIPPED: '발송',
  IN_TRANSIT: '배송중',
  DELIVERED: '배송완료',
};

function statusLabel(s: string): string {
  return STATUS_LABELS[s] ?? s;
}

const NEXT_STATUS_LABELS: Record<string, string> = {
  SHIPPED: '배송 시작',
  IN_TRANSIT: '배송중',
  DELIVERED: '배송완료',
};

function nextStatusLabel(s: string): string {
  return NEXT_STATUS_LABELS[s] ?? s;
}

export function ShippingsScreen({ shippings }: ShippingsScreenProps) {
  const statusFid = useId();

  const [statusFilter, setStatusFilter] = useState('');
  const [query, setQuery] = useState<ShippingListParams>({
    page: 0,
    size: shippings.size || SHIPPING_DEFAULT_PAGE_SIZE,
  });

  const seeded = (query.page ?? 0) === 0 && !query.status;
  const listQ = useShippings(query, seeded ? shippings : undefined);
  // Only the seeded (page 0, no filter) query may fall back to the server-rendered
  // `shippings` seed. For a filtered/paginated query, falling back to the seed would
  // flash the full unfiltered list while the new query is still in flight — instead
  // we render a loading placeholder until the real result lands.
  const data = seeded ? listQ.data ?? shippings : listQ.data;
  const loading = data === undefined;

  const apiError =
    listQ.error instanceof ApiError ? (listQ.error as ApiError) : null;
  const forbidden = apiError?.status === 403;
  const degraded =
    listQ.isError && (!apiError || apiError.status >= 500) && !forbidden;

  // --- status transition --------------------------------------------------
  const updateStatus = useUpdateShippingStatus();
  const refreshTracking = useRefreshTracking();

  // Shared pending guard — disables all row actions while any mutation is inflight.
  const isAnyPending = updateStatus.isPending || refreshTracking.isPending;

  // Confirm dialog (for non-SHIPPED transitions — just confirm-gated)
  const [pendingTransition, setPendingTransition] = useState<{
    id: string;
    nextStatus: string;
  } | null>(null);
  const [transitionError, setTransitionError] = useState<string | null>(null);

  // ShipFormDialog (for PREPARING → SHIPPED — needs carrier + trackingNumber)
  const [shipDialogId, setShipDialogId] = useState<string | null>(null);
  const [shipError, setShipError] = useState<string | null>(null);

  function openTransition(id: string, nextStatus: string) {
    if (nextStatus === 'SHIPPED') {
      setShipError(null);
      setShipDialogId(id);
    } else {
      setTransitionError(null);
      setPendingTransition({ id, nextStatus });
    }
  }

  function confirmTransition() {
    if (!pendingTransition) return;
    setTransitionError(null);
    updateStatus.mutate(
      { id: pendingTransition.id, body: { status: pendingTransition.nextStatus } },
      {
        onSuccess: () => setPendingTransition(null),
        onError: (e) => {
          const code = e instanceof ApiError ? e.code : 'SERVICE_UNAVAILABLE';
          setTransitionError(
            messageForCode(code, '상태를 변경하지 못했습니다.'),
          );
        },
      },
    );
  }

  function confirmShip(payload: { carrier: string; trackingNumber: string }) {
    if (!shipDialogId) return;
    setShipError(null);
    updateStatus.mutate(
      {
        id: shipDialogId,
        body: {
          status: 'SHIPPED',
          carrier: payload.carrier,
          trackingNumber: payload.trackingNumber,
        },
      },
      {
        onSuccess: () => setShipDialogId(null),
        onError: (e) => {
          const code = e instanceof ApiError ? e.code : 'SERVICE_UNAVAILABLE';
          setShipError(messageForCode(code, '배송 시작에 실패했습니다.'));
        },
      },
    );
  }

  function triggerRefresh(id: string) {
    refreshTracking.mutate(id);
  }

  function submitFilter(e: React.FormEvent) {
    e.preventDefault();
    setQuery({
      status: statusFilter || undefined,
      page: 0,
      size: shippings.size || SHIPPING_DEFAULT_PAGE_SIZE,
    });
  }

  const rows = data?.content ?? [];
  const totalPages = data
    ? Math.max(1, Math.ceil(data.totalElements / (data.size || 20)))
    : 1;

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
        <>
          <table className="mb-3 data-table" data-testid="shipping-table">
            <caption className="sr-only">배송 목록</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">
                  배송 ID
                </th>
                <th scope="col" className="p-2">
                  주문 ID
                </th>
                <th scope="col" className="p-2">
                  상태
                </th>
                <th scope="col" className="p-2">
                  택배사
                </th>
                <th scope="col" className="p-2">
                  운송장 번호
                </th>
                <th scope="col" className="p-2">
                  생성일
                </th>
                <th scope="col" className="p-2">
                  작업
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((s, i) => {
                const next = allowedNextStatus(s.status);
                return (
                  <tr
                    key={s.shippingId}
                    data-testid={`shipping-row-${i}`}
                    className="border-b border-border"
                  >
                    <td className="p-2 text-xs break-all">{s.shippingId}</td>
                    <td className="p-2 text-xs break-all">{s.orderId}</td>
                    <td
                      className="p-2"
                      data-testid={`shipping-row-status-${i}`}
                    >
                      {statusLabel(s.status)}
                    </td>
                    <td className="p-2 text-sm">
                      {s.carrier ?? '—'}
                    </td>
                    <td className="p-2 text-xs">
                      {s.trackingNumber ?? '—'}
                    </td>
                    <td className="p-2 text-sm text-muted-foreground">
                      {new Date(s.createdAt).toLocaleDateString('ko-KR')}
                    </td>
                    <td className="p-2">
                      <div className="flex gap-2">
                        {next !== null && (
                          <Button
                            variant="secondary"
                            size="sm"
                            disabled={isAnyPending}
                            onClick={() => openTransition(s.shippingId, next)}
                            data-testid={`shipping-transition-${i}`}
                          >
                            {nextStatusLabel(next)}
                          </Button>
                        )}
                        {s.status !== 'PREPARING' && s.status !== 'DELIVERED' && (
                          <Button
                            variant="secondary"
                            size="sm"
                            disabled={isAnyPending}
                            onClick={() => triggerRefresh(s.shippingId)}
                            data-testid={`shipping-refresh-${i}`}
                          >
                            추적 동기화
                          </Button>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <nav
            className="flex items-center justify-between"
            aria-label="배송 페이지 이동"
          >
            <Button
              variant="secondary"
              disabled={(query.page ?? 0) <= 0}
              onClick={() =>
                setQuery((q) => ({
                  ...q,
                  page: Math.max(0, (q.page ?? 0) - 1),
                }))
              }
              data-testid="shipping-prev"
            >
              이전
            </Button>
            <span
              className="text-sm text-muted-foreground"
              data-testid="shipping-pageinfo"
            >
              {`${(data?.page ?? 0) + 1} / ${totalPages} 페이지 · 총 ${data?.totalElements ?? 0}건`}
            </span>
            <Button
              variant="secondary"
              disabled={(data?.page ?? 0) + 1 >= totalPages}
              onClick={() =>
                setQuery((q) => ({ ...q, page: (q.page ?? 0) + 1 }))
              }
              data-testid="shipping-next"
            >
              다음
            </Button>
          </nav>
        </>
      )}

      {/* Confirm dialog for non-SHIPPED transitions */}
      <ConfirmDialog
        open={pendingTransition !== null}
        title="배송 상태를 변경할까요?"
        description={
          pendingTransition
            ? `배송 #${pendingTransition.id} 상태를 "${nextStatusLabel(pendingTransition.nextStatus)}"(으)로 변경합니다.`
            : ''
        }
        confirmLabel={
          pendingTransition
            ? nextStatusLabel(pendingTransition.nextStatus)
            : '확인'
        }
        tone="default"
        pending={updateStatus.isPending}
        errorMessage={transitionError}
        onConfirm={confirmTransition}
        onCancel={() => {
          setPendingTransition(null);
          setTransitionError(null);
        }}
      />

      {/* ShipFormDialog for PREPARING → SHIPPED (needs carrier + trackingNumber) */}
      <ShipFormDialog
        open={shipDialogId !== null}
        shippingId={shipDialogId ?? ''}
        pending={updateStatus.isPending}
        errorMessage={shipError}
        onConfirm={confirmShip}
        onCancel={() => {
          setShipDialogId(null);
          setShipError(null);
        }}
      />
    </section>
  );
}
