export const revalidate = 60;

import { cache } from 'react';
import dynamic from 'next/dynamic';
import { getProduct } from '@/entities/product';
import { ProductDetailWithCart } from '@/widgets/product-detail-with-cart';
import { DataProvenanceNotice } from '@/widgets/data-provenance';
import { ReviewListSkeleton } from '@/features/review/ui/ReviewListSkeleton';
import { ErrorMessage } from '@repo/ui';
import { notFound } from 'next/navigation';
import type { Metadata } from 'next';

const ReviewList = dynamic(
  () => import('@/features/review/ui/ReviewList').then((m) => ({ default: m.ReviewList })),
  { loading: () => <ReviewListSkeleton count={3} /> },
);

const getCachedProduct = cache(async (id: string) => {
  try {
    return await getProduct(id);
  } catch {
    return undefined;
  }
});

interface Props {
  params: Promise<{ id: string }>;
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { id } = await params;
  const product = await getCachedProduct(id);

  if (product === undefined) {
    return { title: '상품을 불러올 수 없습니다 | Web Store' };
  }

  if (!product) {
    return { title: '상품을 찾을 수 없습니다' };
  }

  return {
    title: `${product.name} | Web Store`,
    description: product.description,
  };
}

export default async function ProductDetailPage({ params }: Props) {
  const { id } = await params;
  const product = await getCachedProduct(id);

  if (product === undefined) {
    return (
      <div className="container" style={{ paddingTop: 'var(--space-8)', paddingBottom: 'var(--space-16)' }}>
        <ErrorMessage message="상품 정보를 불러오는 데 실패했습니다." />
      </div>
    );
  }

  if (!product) {
    notFound();
  }

  return (
    <div className="container" style={{ paddingTop: 'var(--space-8)', paddingBottom: 'var(--space-16)' }}>
      <div style={{ marginBottom: 'var(--space-4)' }}>
        <DataProvenanceNotice />
      </div>
      {/* 🔴 `fromSnapshot` 은 위젯이 «표시 가격» 문구와 재고 자리를 어떻게 그릴지 정하는
          판정 값이다. 라이브 백엔드 상세로 돌아가는 날 그쪽은 이 필드를 안 붙이고,
          그러면 문구도 자동으로 사라진다 — 지워야 할 문구가 남는 실패를 막는다. */}
      <ProductDetailWithCart product={product} fromSnapshot={product.fromSnapshot} />
      {/* ReviewList is dynamically imported with its own `loading` skeleton (a
          single lazy/Suspense boundary). A second explicit <Suspense> around it
          was redundant and re-parented React 19.2 async-info on cleanup
          ("cleaning up async info that was not on the parent Suspense
          boundary"). One boundary only. (TASK-FE-082) */}
      <ReviewList productId={product.id} />
    </div>
  );
}
