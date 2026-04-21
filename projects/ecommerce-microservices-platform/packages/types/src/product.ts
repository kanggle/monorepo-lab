// Product domain types based on specs/contracts/http/product-api.md

export type ProductStatus = 'ON_SALE' | 'SOLD_OUT' | 'HIDDEN';

export interface ProductSummary {
  id: string;
  name: string;
  status: ProductStatus;
  price: number;
  thumbnailUrl: string;
  categoryId: string;
}

export interface ProductVariant {
  id: string;
  optionName: string;
  stock: number;
  additionalPrice: number;
}

export interface ProductImageSummary {
  imageId: string;
  url: string;
  sortOrder: number;
  isPrimary: boolean;
}

export interface ProductDetail {
  id: string;
  name: string;
  description: string;
  status: ProductStatus;
  price: number;
  categoryId: string;
  thumbnailUrl?: string;
  images?: ProductImageSummary[];
  variants: ProductVariant[];
}

export interface ProductListParams {
  name?: string;
  categoryId?: string;
  status?: ProductStatus;
  page?: number;
  size?: number;
}

export interface CreateProductRequest {
  name: string;
  description: string;
  price: number;
  categoryId: string;
  variants: Omit<ProductVariant, 'id'>[];
}

export interface UpdateProductRequest {
  name?: string;
  description?: string;
  price?: number;
  status?: ProductStatus;
}

export interface StockAdjustmentRequest {
  variantId: string;
  quantity: number;
  reason: string;
}

export interface StockAdjustmentResponse {
  variantId: string;
  currentStock: number;
}

export interface CreateProductResponse {
  id: string;
}

export interface UpdateProductResponse {
  id: string;
}
