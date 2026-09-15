'use client';

import { useState } from 'react';
import { EmptyState } from '@repo/ui';
import { useAuth } from '@/shared/lib/auth-context';
import { useCreateReview } from '../model/use-create-review';
import { ReviewForm } from './ReviewForm';
import { RatingSummary, type RatingSummaryData } from './RatingSummary';
import { ReviewCard } from './ReviewCard';
import { Pagination } from './Pagination';
import styles from './ReviewList.module.css';

/**
 * 상품 상세의 리뷰 영역 (ADR-MONO-074).
 *
 * 🔴🔴 **이 컴포넌트는 리뷰를 읽으러 백엔드에 가지 않는다.** 목록과 요약은 서버가 저장본에서 읽어
 *    props 로 넘긴다(D3). 예전 판은 브라우저가 BFF → 게이트웨이 → review-service 로 읽어서, 데모가
 *    꺼진 동안(= 대부분의 시간) 상품은 보이는데 리뷰만 «불러오는데 실패» 였다.
 *    🔴 «백엔드 먼저 → 실패하면 저장본» 으로 되돌리지 마라 — `ADR-MONO-070` § 출처가 원문으로 금지한 구조다.
 *
 * 🔵 **쓰기는 여전히 백엔드**다(R2) — 로그인한 사용자의 작성 폼은 남는다. 방금 쓴 리뷰는 저장본을 다시
 *    발행해야 목록에 나오므로, 성공하면 그 사실을 말한다(말하지 않으면 «등록이 안 됐다» 로 읽힌다).
 * 🔴 **수정·삭제 버튼이 없다**(R3) — 저장본에 작성자가 없어 «본인 리뷰» 를 판정할 수 없다. 로그인 시
 *    백엔드에 따로 물어 본인 표시를 붙이면 «목록은 저장본, 본인 표시는 백엔드» 라는 반쪽 구현이 된다.
 *    관리는 `/my/reviews` 에서 한다.
 */

/** 화면이 그리는 리뷰 한 줄 — 저장본 `PublicReview` 가 이 모양을 만족한다. 🔴 작성자가 없다(D2). */
export interface ReviewListItem {
  id: string;
  rating: number;
  title: string;
  content: string;
  createdAt: string;
}

interface ReviewListProps {
  productId: string;
  /** 최신순. 서버가 저장본에서 이 상품의 것만 골라 넘긴다. */
  reviews: ReviewListItem[];
  summary: RatingSummaryData;
  /** 🔴 `true` 면 「샘플 리뷰」 를 표시한다(D4). 판정은 서버가 저장본 `source` 로 한다. */
  isSample: boolean;
}

const PAGE_SIZE = 10;

// 🔵 공개 목록의 카드는 편집 상태가 될 수 없다(R3) — 아무것도 안 하는 핸들러를 한 벌만 둔다.
const noop = () => undefined;
const NO_ACTIONS = {
  onEdit: noop,
  onDelete: noop,
  onUpdate: async () => undefined,
  onCancelEdit: noop,
};

export function ReviewList({ productId, reviews, summary, isSample }: ReviewListProps) {
  const [page, setPage] = useState(0);
  const [showForm, setShowForm] = useState(false);
  const [submitted, setSubmitted] = useState(false);

  const createReview = useCreateReview();
  const { isAuthenticated } = useAuth();

  const totalPages = Math.max(1, Math.ceil(reviews.length / PAGE_SIZE));
  const visible = reviews.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  async function handleCreate(formData: { rating: number; title: string; content: string }) {
    await createReview.mutateAsync({
      productId,
      rating: formData.rating,
      title: formData.title,
      content: formData.content,
    });
    setShowForm(false);
    setSubmitted(true);
  }

  function handlePageChange(newPage: number) {
    if (newPage >= 0 && newPage < totalPages) {
      setPage(newPage);
    }
  }

  return (
    <div className={styles.wrapper}>
      <h2
        style={{
          fontSize: 'var(--font-size-lg, 1.125rem)',
          fontWeight: 'var(--font-weight-semibold)',
          marginBottom: 'var(--space-4)',
        }}
      >
        상품 리뷰
      </h2>

      {isSample && (
        <p
          data-testid="sample-review-notice"
          style={{
            margin: '0 0 var(--space-3)',
            color: 'var(--color-text-muted)',
            fontSize: 'var(--font-size-xs)',
          }}
        >
          샘플 리뷰 — 실제 구매자가 쓴 리뷰가 아닙니다.
        </p>
      )}

      <RatingSummary summary={summary} />

      <div style={{ marginTop: 'var(--space-6)' }}>
        {submitted && (
          <p
            role="status"
            data-testid="review-submitted-notice"
            style={{
              marginBottom: 'var(--space-4)',
              fontSize: 'var(--font-size-sm)',
              color: 'var(--color-text-secondary)',
            }}
          >
            리뷰가 등록되었습니다. 공개 목록에는 다음 발행 때 반영됩니다.
          </p>
        )}

        {isAuthenticated && !showForm && (
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => setShowForm(true)}
            style={{ marginBottom: 'var(--space-4)' }}
          >
            리뷰 작성
          </button>
        )}

        {showForm && (
          <div
            style={{
              padding: 'var(--space-4)',
              border: '1px solid var(--color-border-light)',
              borderRadius: 'var(--radius-md)',
              marginBottom: 'var(--space-4)',
            }}
          >
            <h3
              style={{
                fontSize: 'var(--font-size-md, 1rem)',
                fontWeight: 'var(--font-weight-semibold)',
                marginBottom: 'var(--space-3)',
              }}
            >
              리뷰 작성
            </h3>
            <ReviewForm
              onSubmit={handleCreate}
              onCancel={() => setShowForm(false)}
              submitLabel="리뷰 작성"
              isPending={createReview.isPending}
            />
          </div>
        )}

        {reviews.length === 0 && <EmptyState message="아직 리뷰가 없습니다." />}

        {visible.length > 0 && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
            {visible.map((review) => (
              <ReviewCard
                key={review.id}
                review={review}
                isEditing={false}
                showActions={false}
                isUpdatePending={false}
                actions={NO_ACTIONS}
              />
            ))}
          </div>
        )}

        {reviews.length > PAGE_SIZE && (
          <Pagination
            page={page}
            totalPages={totalPages}
            onPageChange={handlePageChange}
            ariaLabel="리뷰 페이지네이션"
            style={{ marginTop: 'var(--space-6)' }}
          />
        )}
      </div>
    </div>
  );
}
