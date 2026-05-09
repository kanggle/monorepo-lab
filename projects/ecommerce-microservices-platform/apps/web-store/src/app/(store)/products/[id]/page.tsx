export const revalidate = 60;

import { Suspense, cache } from 'react';
import dynamic from 'next/dynamic';
import { getProduct } from '@/entities/product';
import { ProductDetailWithCart } from '@/widgets/product-detail-with-cart';
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
      <ProductDetailWithCart product={product} />
      <Suspense fallback={<ReviewListSkeleton count={3} />}>
        <ReviewList productId={product.id} />
      </Suspense>
    </div>
  );
}
