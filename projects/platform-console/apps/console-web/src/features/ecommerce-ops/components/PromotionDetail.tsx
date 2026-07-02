'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import {
  usePromotion,
  useDeletePromotion,
} from '../hooks/use-ecommerce-promotions';
import type { PromotionDetail as PromotionDetailType } from '../api/types';
import { ConfirmDialog } from './ConfirmDialog';
import { CouponIssueDialog } from './CouponIssueDialog';
import { DetailHeader } from './DetailHeader';

/**
 * ecommerce promotion detail section (TASK-PC-FE-086 — ADR-031 Phase 3b).
 *
 * Shows all fields. Action buttons:
 *   - "수정" (only if status !== ENDED) → /[id]/edit
 *   - "삭제" (ConfirmDialog → deletePromotion; 422 PROMOTION_HAS_ISSUED_COUPONS inline)
 *   - "쿠폰 발급" (only if status === ACTIVE) → opens CouponIssueDialog
 */
export interface PromotionDetailProps {
  promotion: PromotionDetailType;
}

export function PromotionDetail({ promotion }: PromotionDetailProps) {
  const router = useRouter();
  const detailQ = usePromotion(promotion.promotionId, promotion);
  const data = detailQ.data ?? promotion;

  const del = useDeletePromotion();
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [delError, setDelError] = useState<string | null>(null);
  const [couponOpen, setCouponOpen] = useState(false);

  const canEdit = data.status !== 'ENDED';
  const canIssue = data.status === 'ACTIVE';

  function doDelete() {
    setDelError(null);
    del.mutate(data.promotionId, {
      onSuccess: () => {
        setConfirmDelete(false);
        router.push('/ecommerce/promotions');
        router.refresh();
      },
      onError: (e) => {
        const code = e instanceof ApiError ? e.code : 'SERVICE_UNAVAILABLE';
        setDelError(messageForCode(code, '프로모션을 삭제하지 못했습니다.'));
      },
    });
  }

  return (
    <section
      aria-labelledby="promotion-detail-heading"
      data-testid="promotion-detail"
    >
      <DetailHeader
        headingId="promotion-detail-heading"
        title="프로모션 상세"
        backHref="/ecommerce/promotions"
        backTestId="promotion-detail-back"
        actions={
          <>
            {canEdit && (
              <Link href={`/ecommerce/promotions/${data.promotionId}/edit`}>
                <Button
                  variant="secondary"
                  data-testid="promotion-detail-edit"
                >
                  수정
                </Button>
              </Link>
            )}
            <Button
              variant="secondary"
              onClick={() => {
                setDelError(null);
                setConfirmDelete(true);
              }}
              data-testid="promotion-detail-delete"
            >
              삭제
            </Button>
            {canIssue && (
              <Button
                onClick={() => setCouponOpen(true)}
                data-testid="promotion-detail-issue"
              >
                쿠폰 발급
              </Button>
            )}
          </>
        }
      />

      <dl className="mb-8 grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
        <div className="col-span-2 sm:col-span-4">
          <dt className="text-muted-foreground">프로모션명</dt>
          <dd data-testid="promotion-detail-name" className="font-medium">
            {data.name}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">상태</dt>
          <dd data-testid="promotion-detail-status">{data.status}</dd>
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
          <dd data-testid="promotion-detail-start">{data.startDate}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">종료일</dt>
          <dd data-testid="promotion-detail-end">{data.endDate}</dd>
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
            <dd className="text-xs">{data.createdAt}</dd>
          </div>
        )}
        {data.updatedAt && (
          <div>
            <dt className="text-muted-foreground">수정일</dt>
            <dd className="text-xs">{data.updatedAt}</dd>
          </div>
        )}
      </dl>

      <CouponIssueDialog
        open={couponOpen}
        promotionId={data.promotionId}
        onClose={() => setCouponOpen(false)}
        onIssued={() => setCouponOpen(false)}
      />

      <ConfirmDialog
        open={confirmDelete}
        title="프로모션을 삭제할까요?"
        description={`"${data.name}" 프로모션을 삭제합니다. 이 작업은 되돌릴 수 없습니다.`}
        confirmLabel="삭제"
        tone="destructive"
        pending={del.isPending}
        errorMessage={delError}
        onConfirm={doDelete}
        onCancel={() => {
          setConfirmDelete(false);
          setDelError(null);
        }}
      />
    </section>
  );
}
