'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { PageLayout, StatusBadge, DescriptionList, Section, ConfirmDialog } from '@/shared/ui';
import { ErrorMessage } from '@repo/ui';
import { usePromotion } from '../hooks/use-promotion';
import { useDeletePromotion } from '../hooks/use-delete-promotion';
import { CouponIssueForm } from './CouponIssueForm';

interface Props {
  promotionId: string;
}

export function PromotionDetail({ promotionId }: Props) {
  const router = useRouter();
  const { data: promotion, isLoading, isError, refetch } = usePromotion(promotionId);
  const deletePromotion = useDeletePromotion();
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);

  if (isError) {
    return <ErrorMessage message="프로모션 정보를 불러오는데 실패했습니다." onRetry={() => refetch()} />;
  }

  if (isLoading || !promotion) {
    return <PageLayout.Skeleton />;
  }

  const handleDelete = async () => {
    await deletePromotion.mutateAsync(promotionId);
    router.push('/promotions');
  };

  const isEnded = promotion.status === 'ENDED';

  return (
    <PageLayout
      title={promotion.name}
      actions={[
        { label: '← 프로모션 관리', href: '/promotions', variant: 'secondary' as const },
        ...(!isEnded ? [{ label: '수정', href: `/promotions/${promotionId}/edit` }] : []),
        { label: '삭제', variant: 'danger' as const, onClick: () => setShowDeleteConfirm(true) },
      ]}
    >
      <Section title="기본 정보">
        <DescriptionList
          items={[
            { label: '상태', value: <StatusBadge status={promotion.status} /> },
            { label: '설명', value: promotion.description || '-' },
            { label: '할인 유형', value: promotion.discountType === 'FIXED' ? '정액' : '정률' },
            {
              label: '할인값',
              value: promotion.discountType === 'FIXED'
                ? `${promotion.discountValue.toLocaleString()}원`
                : `${promotion.discountValue}%`,
            },
            { label: '최대 할인금액', value: `${promotion.maxDiscountAmount.toLocaleString()}원` },
            { label: '발급 현황', value: `${promotion.issuedCount} / ${promotion.maxIssuanceCount}` },
            { label: '시작일', value: new Date(promotion.startDate).toLocaleString('ko-KR') },
            { label: '종료일', value: new Date(promotion.endDate).toLocaleString('ko-KR') },
            { label: '생성일', value: new Date(promotion.createdAt).toLocaleString('ko-KR') },
            { label: '수정일', value: new Date(promotion.updatedAt).toLocaleString('ko-KR') },
          ]}
        />
      </Section>

      {promotion.status === 'ACTIVE' && (
        <Section title="쿠폰 발급">
          <CouponIssueForm promotionId={promotionId} />
        </Section>
      )}

      <ConfirmDialog
        open={showDeleteConfirm}
        title="프로모션 삭제"
        message="이 프로모션을 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다."
        confirmLabel="삭제"
        onConfirm={handleDelete}
        onCancel={() => setShowDeleteConfirm(false)}
        confirmDisabled={deletePromotion.isPending}
      />
    </PageLayout>
  );
}
