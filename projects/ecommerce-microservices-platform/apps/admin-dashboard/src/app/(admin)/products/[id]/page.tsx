'use client';

import { use } from 'react';
import { ProductDetail } from '@/features/product-management';

interface Props {
  params: Promise<{ id: string }>;
}

export default function ProductDetailPage({ params }: Props) {
  const { id } = use(params);
  return <ProductDetail productId={id} />;
}
