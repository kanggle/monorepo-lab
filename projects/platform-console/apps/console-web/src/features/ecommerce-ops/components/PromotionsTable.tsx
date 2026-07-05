'use client';

import Link from 'next/link';
import { Button } from '@/shared/ui/Button';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { formatPromotionDay } from './promotion-format';
import { promotionStatusTone, type PromotionList } from '../api/types';

interface PromotionsTableProps {
  rows: PromotionList['content'];
  onDelete: (promotion: { id: string; name: string }) => void;
  pagination: {
    prevDisabled: boolean;
    nextDisabled: boolean;
    pageInfo: string;
    onPrev: () => void;
    onNext: () => void;
  };
}

function formatDiscount(type: string, value: number): string {
  if (type === 'FIXED') return `₩${value.toLocaleString('ko-KR')}`;
  if (type === 'PERCENTAGE') return `${value}%`;
  return String(value);
}

/**
 * Promotion list table + pagination (TASK-PC-FE-200 — extracted from
 * {@link PromotionsScreen}, presentational only). Per-row actions: 상세(drill) /
 * 삭제(delegates to the container's confirm-gate via `onDelete`). Query/filter/
 * delete state stays owned by `PromotionsScreen`; all `data-testid`s are
 * unchanged.
 */
export function PromotionsTable({
  rows,
  onDelete,
  pagination,
}: PromotionsTableProps) {
  return (
    <>
      <table
        className="mb-3 data-table"
        data-testid="promotion-table"
      >
        <caption className="sr-only">프로모션 목록</caption>
        <thead>
          <tr className="border-b border-border text-left">
            <th scope="col" className="p-2">
              이름
            </th>
            <th scope="col" className="p-2">
              할인 유형
            </th>
            <th scope="col" className="p-2">
              할인값
            </th>
            <th scope="col" className="p-2">
              발급 / 한도
            </th>
            <th scope="col" className="p-2">
              시작일
            </th>
            <th scope="col" className="p-2">
              종료일
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
          {rows.map((p, i) => (
            <tr
              key={p.promotionId}
              data-testid={`promotion-row-${i}`}
              className="border-b border-border"
            >
              <td className="p-2">{p.name}</td>
              <td className="p-2">{p.discountType}</td>
              <td className="p-2">
                {formatDiscount(p.discountType, p.discountValue)}
              </td>
              <td className="p-2">
                {p.issuedCount} / {p.maxIssuanceCount}
              </td>
              <td className="p-2">{formatPromotionDay(p.startDate)}</td>
              <td className="p-2">{formatPromotionDay(p.endDate)}</td>
              <td
                className="p-2"
                data-testid={`promotion-row-status-${i}`}
              >
                <StatusBadge tone={promotionStatusTone(p.status)}>
                  {p.status}
                </StatusBadge>
              </td>
              <td className="p-2">
                <div className="flex gap-2">
                  <Link
                    href={`/ecommerce/promotions/${p.promotionId}`}
                  >
                    <Button
                      variant="secondary"
                      size="sm"
                      data-testid={`promotion-detail-${i}`}
                    >
                      상세
                    </Button>
                  </Link>
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() =>
                      onDelete({ id: p.promotionId, name: p.name })
                    }
                    data-testid={`promotion-delete-${i}`}
                  >
                    삭제
                  </Button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <nav
        className="flex items-center justify-between"
        aria-label="프로모션 페이지 이동"
      >
        <Button
          variant="secondary"
          disabled={pagination.prevDisabled}
          onClick={pagination.onPrev}
          data-testid="promotion-prev"
        >
          이전
        </Button>
        <span
          className="text-sm text-muted-foreground"
          data-testid="promotion-pageinfo"
        >
          {pagination.pageInfo}
        </span>
        <Button
          variant="secondary"
          disabled={pagination.nextDisabled}
          onClick={pagination.onNext}
          data-testid="promotion-next"
        >
          다음
        </Button>
      </nav>
    </>
  );
}
