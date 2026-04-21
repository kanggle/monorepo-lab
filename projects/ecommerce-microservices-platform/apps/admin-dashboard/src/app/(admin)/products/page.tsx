'use client';

import { Suspense } from 'react';
import { PageLayout } from '@/shared/ui';
import { LoadingSpinner } from '@repo/ui';
import { ProductList } from '@/features/product-management';

export default function ProductsPage() {
  return (
    <PageLayout title="상품 관리" actions={[{ label: '상품 등록', href: '/products/new' }]}>
      <Suspense fallback={<LoadingSpinner />}>
        <ProductList />
      </Suspense>
    </PageLayout>
  );
}
