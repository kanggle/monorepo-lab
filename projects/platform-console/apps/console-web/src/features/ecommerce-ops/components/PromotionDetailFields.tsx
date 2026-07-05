'use client';

import { StatusBadge } from '@/shared/ui/StatusBadge';
import { formatDateTime } from '@/shared/lib/datetime';
import {
  promotionStatusTone,
  type PromotionDetail as PromotionDetailType,
} from '../api/types';
import { formatPromotionDay } from './promotion-format';

interface PromotionDetailFieldsProps {
  data: PromotionDetailType;
}

/**
 * Promotion detail field grid (TASK-PC-FE-200 — extracted from
 * {@link PromotionDetail}, presentational only). Renders the `<dl>` in the
 * shared 명칭→상태→식별자→날짜 order; the query/delete/coupon orchestration
 * stays owned by `PromotionDetail`. All `data-testid`s are unchanged.
 */
export function PromotionDetailFields({ data }: PromotionDetailFieldsProps) {
  return (
    <dl className="mb-8 grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
      <div className="col-span-2 sm:col-span-4">
        <dt className="text-muted-foreground">프로모션명</dt>
        <dd data-testid="promotion-detail-name" className="font-medium">
          {data.name}
        </dd>
      </div>
      <div>
        <dt className="text-muted-foreground">상태</dt>
        <dd data-testid="promotion-detail-status">
          <StatusBadge tone={promotionStatusTone(data.status)}>
            {data.status}
          </StatusBadge>
        </dd>
      </div>
      <div>
        <dt className="text-muted-foreground">할인 유형</dt>
        <dd data-testid="promotion-detail-discount-type">
          {data.discountType}
        </dd>
      </div>
      <div>
        <dt className="text-muted-foreground">할인값</dt>
        <dd data-testid="promotion-detail-discount-value">
          {data.discountType === 'FIXED'
            ? `₩${data.discountValue.toLocaleString('ko-KR')}`
            : `${data.discountValue}%`}
        </dd>
      </div>
      <div>
        <dt className="text-muted-foreground">최대 할인 금액</dt>
        <dd data-testid="promotion-detail-max-discount">
          {data.maxDiscountAmount != null
            ? `₩${data.maxDiscountAmount.toLocaleString('ko-KR')}`
            : '—'}
        </dd>
      </div>
      <div>
        <dt className="text-muted-foreground">발급 / 한도</dt>
        <dd data-testid="promotion-detail-issue-count">
          {data.issuedCount} / {data.maxIssuanceCount}
        </dd>
      </div>
      <div>
        <dt className="text-muted-foreground">시작일</dt>
        <dd data-testid="promotion-detail-start">
          {formatPromotionDay(data.startDate)}
        </dd>
      </div>
      <div>
        <dt className="text-muted-foreground">종료일</dt>
        <dd data-testid="promotion-detail-end">
          {formatPromotionDay(data.endDate)}
        </dd>
      </div>
      <div>
        <dt className="text-muted-foreground">프로모션 ID</dt>
        <dd className="break-all text-xs">{data.promotionId}</dd>
      </div>
      {data.description && (
        <div className="col-span-2 sm:col-span-4">
          <dt className="text-muted-foreground">설명</dt>
          <dd data-testid="promotion-detail-description">
            {data.description}
          </dd>
        </div>
      )}
      {data.createdAt && (
        <div>
          <dt className="text-muted-foreground">생성일</dt>
          <dd className="text-xs">{formatDateTime(data.createdAt)}</dd>
        </div>
      )}
      {data.updatedAt && (
        <div>
          <dt className="text-muted-foreground">수정일</dt>
          <dd className="text-xs">{formatDateTime(data.updatedAt)}</dd>
        </div>
      )}
    </dl>
  );
}
