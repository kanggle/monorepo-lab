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
import { type PromotionDetail as PromotionDetailType } from '../api/types';
import { ConfirmDialog } from './ConfirmDialog';
import { CouponIssueDialog } from './CouponIssueDialog';
import { DetailHeader } from './DetailHeader';
import { PromotionDetailFields } from './PromotionDetailFields';

/**
 * ecommerce promotion detail section (TASK-PC-FE-086 — ADR-031 Phase 3b).
 *
 * Shows all fields. Action buttons:
 *   - "수정" (only if status !== ENDED) → /[id]/edit
 *   - "삭제" (ConfirmDialog → deletePromotion; 422 PROMOTION_HAS_ISSUED_COUPONS inline)
 *   - "쿠폰 발급" (only if status === ACTIVE) → opens CouponIssueDialog
 *
 * TASK-PC-FE-200: the `<dl>` field grid is extracted into
 * {@link PromotionDetailFields} (presentational); this container keeps the
 * query, delete confirm-gate, and coupon-issue orchestration.
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

      <PromotionDetailFields data={data} />

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
