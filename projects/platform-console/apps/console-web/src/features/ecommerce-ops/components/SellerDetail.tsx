'use client';

import { useSeller } from '../hooks/use-ecommerce-sellers';
import type { SellerDetail as SellerDetailType } from '../api/seller-types';
import { DetailHeader } from './DetailHeader';

/**
 * ecommerce seller detail section (TASK-PC-FE-090 — § 2.4.10.5 sellers).
 * The console seller detail screen: read-only display.
 *
 * Server-seeded detail is passed in; the client query keeps it fresh on
 * window focus. READ-ONLY — no status-transition actions, no mutation area
 * (v1: no update/deactivate). notFound → rendered by the page waterfall.
 */

export interface SellerDetailProps {
  seller: SellerDetailType;
}

export function SellerDetail({ seller }: SellerDetailProps) {
  const detailQ = useSeller(seller.sellerId, seller);
  const data = detailQ.data ?? seller;

  return (
    <section
      aria-labelledby="seller-detail-heading"
      data-testid="seller-detail"
    >
      <DetailHeader
        headingId="seller-detail-heading"
        title="셀러 상세"
        backHref="/ecommerce/sellers"
        backTestId="seller-detail-back"
      />

      <dl className="mb-6 grid grid-cols-2 gap-3 text-sm sm:grid-cols-3">
        <div>
          <dt className="text-muted-foreground">셀러 ID</dt>
          <dd className="font-mono text-xs" data-testid="seller-detail-id">
            {data.sellerId}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">셀러 이름</dt>
          <dd data-testid="seller-detail-display-name">{data.displayName}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">상태</dt>
          <dd data-testid="seller-detail-status">
            <span className="inline-block rounded bg-green-100 px-2 py-0.5 text-xs font-medium text-green-800">
              {data.status}
            </span>
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">등록일</dt>
          <dd className="text-xs" data-testid="seller-detail-created-at">
            {new Date(data.createdAt).toLocaleString('ko-KR')}
          </dd>
        </div>
        {data.updatedAt != null && data.updatedAt !== '' && (
          <div>
            <dt className="text-muted-foreground">수정일</dt>
            <dd className="text-xs" data-testid="seller-detail-updated-at">
              {new Date(data.updatedAt).toLocaleString('ko-KR')}
            </dd>
          </div>
        )}
      </dl>
    </section>
  );
}
