'use client';

import { use } from 'react';
import { EditProduct } from '@/features/product-management';

interface Props {
  params: Promise<{ id: string }>;
}

export default function EditProductPage({ params }: Props) {
  const { id } = use(params);
  return <EditProduct productId={id} />;
}
