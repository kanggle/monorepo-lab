'use client';

import { PageLayout } from '@/shared/ui';
import { ErrorMessage } from '@repo/ui';
import { useProduct } from '../hooks/use-product';
import { ProductForm } from './ProductForm';

interface Props {
  productId: string;
}

export function EditProduct({ productId }: Props) {
  const { data: product, isLoading, isError, refetch } = useProduct(productId);

  if (isLoading || !product) {
    return <PageLayout.Skeleton />;
  }

  if (isError) {
    return <ErrorMessage message="상품 정보를 불러오는데 실패했습니다." onRetry={() => refetch()} />;
  }

  return (
    <PageLayout title={`${product.name} 수정`}>
      <ProductForm product={product} />
    </PageLayout>
  );
}
