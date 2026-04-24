'use client';

import { PageLayout } from '@/shared/ui';
import { ProductForm } from '@/features/product-management';

export default function NewProductPage() {
  return (
    <PageLayout title="상품 등록" actions={[{ label: '← 상품 관리', href: '/products', variant: 'secondary' as const }]}>
      <ProductForm />
    </PageLayout>
  );
}
