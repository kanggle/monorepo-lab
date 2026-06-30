'use client';

import { Button } from '@/shared/ui/Button';
import { allowedNextStatus, type ShippingList } from '../api/shipping-types';
import { statusLabel, nextStatusLabel } from './shipping-labels';

interface ShippingsTableProps {
  rows: ShippingList['content'];
  isAnyPending: boolean;
  openTransition: (id: string, nextStatus: string, wmsRouted: boolean) => void;
  triggerRefresh: (id: string) => void;
  pagination: {
    prevDisabled: boolean;
    nextDisabled: boolean;
    pageInfo: string;
    onPrev: () => void;
    onNext: () => void;
  };
}

/**
 * Shipping list table + pagination (TASK-PC-FE-140 — extracted from
 * {@link ShippingsScreen}, presentational only). Per-row forward transition +
 * tracking-refresh actions are gated by the shared `isAnyPending`. State stays
 * owned by `useShippingsScreen`; all `data-testid`s are unchanged.
 */
export function ShippingsTable({
  rows,
  isAnyPending,
  openTransition,
  triggerRefresh,
  pagination,
}: ShippingsTableProps) {
  return (
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
                        onClick={() =>
                          openTransition(
                            s.shippingId,
                            next,
                            s.wmsRouted ?? false,
                          )
                        }
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
          disabled={pagination.prevDisabled}
          onClick={pagination.onPrev}
          data-testid="shipping-prev"
        >
          이전
        </Button>
        <span
          className="text-sm text-muted-foreground"
          data-testid="shipping-pageinfo"
        >
          {pagination.pageInfo}
        </span>
        <Button
          variant="secondary"
          disabled={pagination.nextDisabled}
          onClick={pagination.onNext}
          data-testid="shipping-next"
        >
          다음
        </Button>
      </nav>
    </>
  );
}
