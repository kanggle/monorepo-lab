'use client';

import { useState } from 'react';
import { PageLayout, StatusBadge, DescriptionList, Section } from '@/shared/ui';
import { ErrorMessage } from '@repo/ui';
import { useProduct } from '../hooks/use-product';
import { StockAdjustmentForm } from './StockAdjustmentForm';
import { StockAdjustmentButtons } from './StockAdjustmentButtons';
import { VariantManagement } from './VariantManagement';
import type { ProductVariant, ProductImageSummary } from '@repo/types';

interface Props {
  productId: string;
}

const layoutStyle: React.CSSProperties = {
  display: 'flex',
  gap: 24,
  alignItems: 'flex-start',
};

const imageColumnStyle: React.CSSProperties = {
  flexShrink: 0,
  width: 280,
};

const infoColumnStyle: React.CSSProperties = {
  flex: 1,
  minWidth: 0,
};

const mainImageStyle: React.CSSProperties = {
  width: '100%',
  aspectRatio: '1',
  objectFit: 'cover',
  borderRadius: 8,
  border: '1px solid #e5e7eb',
  display: 'block',
};

const thumbnailRowStyle: React.CSSProperties = {
  display: 'flex',
  gap: 8,
  marginTop: 8,
  flexWrap: 'wrap',
};

const thumbStyle: React.CSSProperties = {
  width: 52,
  height: 52,
  objectFit: 'cover',
  borderRadius: 6,
  border: '1px solid #e5e7eb',
  cursor: 'pointer',
};

const thumbActiveStyle: React.CSSProperties = {
  ...thumbStyle,
  border: '2px solid #1A1A2E',
};

const noImageStyle: React.CSSProperties = {
  width: '100%',
  aspectRatio: '1',
  borderRadius: 8,
  border: '1px solid #e5e7eb',
  backgroundColor: '#f9fafb',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  color: '#9ca3af',
  fontSize: 14,
};

export function ProductDetail({ productId }: Props) {
  const { data: product, isLoading, isError, refetch } = useProduct(productId);
  const [selectedVariant, setSelectedVariant] = useState<ProductVariant | null>(null);
  const [selectedImageIndex, setSelectedImageIndex] = useState(0);

  if (isLoading || !product) {
    return <PageLayout.Skeleton />;
  }

  if (isError) {
    return <ErrorMessage message="상품 정보를 불러오는데 실패했습니다." onRetry={() => refetch()} />;
  }

  const images: ProductImageSummary[] = product.images ?? [];
  const selectedImage = images[selectedImageIndex] ?? images[0];

  return (
    <PageLayout
      title={product.name}
      actions={[
        { label: '← 상품 관리', href: '/products', variant: 'secondary' as const },
        { label: '수정', href: `/products/${productId}/edit` },
      ]}
    >
      <Section title="기본 정보">
        <div style={layoutStyle}>
          <div style={imageColumnStyle}>
            {images.length > 0 && selectedImage ? (
              <>
                <img
                  src={selectedImage.url}
                  alt={product.name}
                  style={mainImageStyle}
                />
                {images.length > 1 && (
                  <div style={thumbnailRowStyle}>
                    {images.map((img, idx) => (
                      <img
                        key={img.imageId}
                        src={img.url}
                        alt={`${product.name} ${idx + 1}`}
                        style={idx === selectedImageIndex ? thumbActiveStyle : thumbStyle}
                        onClick={() => setSelectedImageIndex(idx)}
                      />
                    ))}
                  </div>
                )}
              </>
            ) : product.thumbnailUrl ? (
              <img
                src={product.thumbnailUrl}
                alt={product.name}
                style={mainImageStyle}
              />
            ) : (
              <div style={noImageStyle}>이미지 없음</div>
            )}
          </div>

          <div style={infoColumnStyle}>
            <DescriptionList
              items={[
                { label: '상태', value: <StatusBadge status={product.status} /> },
                { label: '가격', value: `${product.price.toLocaleString()}원` },
                { label: '카테고리', value: product.categoryId },
                { label: '설명', value: product.description },
              ]}
            />
          </div>
        </div>
      </Section>

      <Section title="옵션 / 재고">
        <VariantManagement
          productId={productId}
          variants={product.variants}
          onChanged={() => refetch()}
        />
        <StockAdjustmentButtons
          variants={product.variants}
          onSelect={setSelectedVariant}
        />
      </Section>

      {selectedVariant && (
        <StockAdjustmentForm
          productId={productId}
          variant={selectedVariant}
          onClose={() => setSelectedVariant(null)}
        />
      )}
    </PageLayout>
  );
}
