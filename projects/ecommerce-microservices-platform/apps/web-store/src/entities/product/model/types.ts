import type { ProductSummary, ProductDetail, ProductVariant } from '@repo/types';

// Re-export backend types for use within the app
export type { ProductSummary, ProductDetail, ProductVariant };

export type ProductStatus = 'ON_SALE' | 'SOLD_OUT' | 'HIDDEN';
