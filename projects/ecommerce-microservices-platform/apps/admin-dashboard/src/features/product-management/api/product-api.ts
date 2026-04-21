import { apiClient } from '@/shared/config/api';
import { createProductApi } from '@repo/api-client';
import { isMock, mockGetProducts, mockGetProduct } from '@/shared/lib/mock-data';
import type {
  PaginatedResponse,
  ProductSummary,
  ProductDetail,
  ProductVariant,
  ProductListParams,
  CreateProductRequest,
  CreateProductResponse,
  UpdateProductRequest,
  UpdateProductResponse,
  StockAdjustmentRequest,
  StockAdjustmentResponse,
} from '@repo/types';

const productApi = createProductApi(apiClient);

export async function getProducts(
  params?: ProductListParams,
): Promise<PaginatedResponse<ProductSummary>> {
  if (isMock()) return mockGetProducts(params);
  return productApi.getProducts(params);
}

export async function getProduct(productId: string): Promise<ProductDetail> {
  if (isMock()) return mockGetProduct(productId);
  return productApi.getProduct(productId);
}

export async function createProduct(
  data: CreateProductRequest,
): Promise<CreateProductResponse> {
  return productApi.createProduct(data);
}

export async function updateProduct(
  productId: string,
  data: UpdateProductRequest,
): Promise<UpdateProductResponse> {
  return productApi.updateProduct(productId, data);
}

export async function adjustStock(
  productId: string,
  data: StockAdjustmentRequest,
): Promise<StockAdjustmentResponse> {
  return productApi.adjustStock(productId, data);
}

export async function addVariant(
  productId: string,
  data: { optionName: string; stock: number; additionalPrice: number },
): Promise<ProductVariant> {
  return productApi.addVariant(productId, data);
}

export async function updateVariant(
  productId: string,
  variantId: string,
  data: { optionName: string; additionalPrice: number },
): Promise<ProductVariant> {
  return productApi.updateVariant(productId, variantId, data);
}

export async function deleteVariant(productId: string, variantId: string): Promise<void> {
  return productApi.deleteVariant(productId, variantId);
}
