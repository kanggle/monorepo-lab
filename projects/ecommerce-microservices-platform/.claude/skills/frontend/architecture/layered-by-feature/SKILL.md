---
name: layered-by-feature
description: Implement Layered by Feature app (Next.js)
category: frontend
---

# Skill: Layered by Feature Implementation

Next.js (App Router) implementation patterns for applications using Layered by Feature architecture.

Prerequisite: read `specs/services/<app>/architecture.md` before using this skill.

> `@repo/api-client`, `@repo/types` are monorepo shared libraries defined in `libs/`. See `specs/platform/shared-library-policy.md` for ownership rules.

---

## Directory Structure

```
src/
├── app/                          # Next.js App Router
│   ├── layout.tsx                # Root layout (auth guard, sidebar)
│   ├── page.tsx                  # Dashboard redirect
│   ├── dashboard/
│   │   └── page.tsx
│   ├── products/
│   │   ├── page.tsx              # Product list
│   │   ├── new/page.tsx          # Create product
│   │   └── [id]/
│   │       ├── page.tsx          # Product detail
│   │       └── edit/page.tsx     # Edit product
│   ├── orders/
│   │   ├── page.tsx
│   │   └── [id]/page.tsx
│   └── users/
│       ├── page.tsx
│       └── [id]/page.tsx
├── features/                     # Feature modules
│   ├── product-management/
│   ├── order-management/
│   ├── user-management/
│   └── dashboard/
└── shared/                       # Common components, hooks, utilities
    ├── ui/
    ├── hooks/
    ├── lib/
    └── config/
```

---

## Feature Module Pattern

Each feature handles one management domain with a consistent CRUD structure.

```
features/product-management/
├── components/
│   ├── ProductList.tsx
│   ├── ProductDetail.tsx
│   ├── ProductForm.tsx           # Create and edit (shared form)
│   └── ProductStatusBadge.tsx
├── hooks/
│   ├── use-products.ts           # List query hook
│   ├── use-product.ts            # Detail query hook
│   ├── use-create-product.ts     # Create mutation
│   ├── use-update-product.ts     # Update mutation
│   └── use-delete-product.ts     # Delete mutation
├── api/
│   └── product-api.ts            # API call functions
├── types/
│   └── index.ts                  # Feature-specific types (if not in @repo/types)
└── index.ts                      # Public API
```

### Public API (index.ts)

```typescript
// features/product-management/index.ts
export { ProductList } from './components/ProductList';
export { ProductDetail } from './components/ProductDetail';
export { ProductForm } from './components/ProductForm';
export { useProduct } from './hooks/use-product';
export { useProducts } from './hooks/use-products';
```

---

## CRUD Component Patterns

### List Component

```typescript
// features/product-management/components/ProductList.tsx
'use client';

import { DataTable } from '@/shared/ui/DataTable';
import { FilterBar } from '@/shared/ui/FilterBar';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { useProducts } from '../hooks/use-products';
import type { ColumnDef } from '@/shared/ui/DataTable';
import type { Product } from '@repo/types';

const columns: ColumnDef<Product>[] = [
  { key: 'name', header: '상품명', sortable: true },
  { key: 'price', header: '가격', sortable: true,
    render: (product) => `${product.price.toLocaleString()}원` },
  { key: 'status', header: '상태',
    render: (product) => <StatusBadge status={product.status} /> },
];

export function ProductList() {
  const { data, pagination, filters, isLoading } = useProducts();

  return (
    <div>
      <FilterBar filters={filters} />
      <DataTable
        columns={columns}
        data={data?.items ?? []}
        pagination={pagination}
        isLoading={isLoading}
      />
    </div>
  );
}
```

### Detail Component

```typescript
// features/product-management/components/ProductDetail.tsx
'use client';

import { PageLayout } from '@/shared/ui/PageLayout';
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog';
import { useProduct } from '../hooks/use-product';
import { useDeleteProduct } from '../hooks/use-delete-product';
import { useRouter } from 'next/navigation';
import { useState } from 'react';

interface Props {
  productId: string;
}

export function ProductDetail({ productId }: Props) {
  const { data: product, isLoading } = useProduct(productId);
  const deleteProduct = useDeleteProduct();
  const router = useRouter();
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);

  if (isLoading || !product) return <PageLayout.Skeleton />;

  const handleDelete = async () => {
    await deleteProduct.mutateAsync(productId);
    router.push('/products');
  };

  return (
    <PageLayout
      title={product.name}
      actions={[
        { label: '수정', href: `/products/${productId}/edit` },
        { label: '삭제', variant: 'danger', onClick: () => setShowDeleteConfirm(true) },
      ]}
    >
      {/* Product detail content */}
      <ConfirmDialog
        open={showDeleteConfirm}
        title="상품 삭제"
        message="이 상품을 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다."
        onConfirm={handleDelete}
        onCancel={() => setShowDeleteConfirm(false)}
      />
    </PageLayout>
  );
}
```

### Form Component (Create + Edit)

```typescript
// features/product-management/components/ProductForm.tsx
'use client';

import { FormField, FormSection } from '@/shared/ui/FormField';
import { useCreateProduct } from '../hooks/use-create-product';
import { useUpdateProduct } from '../hooks/use-update-product';
import { useRouter } from 'next/navigation';
import { useForm } from 'react-hook-form';
import type { Product } from '@repo/types';

interface ProductFormData {
  name: string;
  description: string;
  price: number;
  status: string;
}

interface Props {
  product?: Product;  // undefined = create, defined = edit
}

export function ProductForm({ product }: Props) {
  const isEdit = !!product;
  const router = useRouter();
  const createProduct = useCreateProduct();
  const updateProduct = useUpdateProduct();

  const form = useForm<ProductFormData>({
    defaultValues: product
      ? { name: product.name, description: product.description,
          price: product.price, status: product.status }
      : { name: '', description: '', price: 0, status: 'DRAFT' },
  });

  const onSubmit = async (data: ProductFormData) => {
    if (isEdit) {
      await updateProduct.mutateAsync({ id: product.id, ...data });
      router.push(`/products/${product.id}`);
    } else {
      const created = await createProduct.mutateAsync(data);
      router.push(`/products/${created.id}`);
    }
  };

  return (
    <form onSubmit={form.handleSubmit(onSubmit)}>
      <FormSection title="기본 정보">
        <FormField label="상품명" error={form.formState.errors.name?.message}>
          <input {...form.register('name', { required: '상품명을 입력하세요' })} />
        </FormField>
        <FormField label="가격" error={form.formState.errors.price?.message}>
          <input type="number" {...form.register('price', {
            required: '가격을 입력하세요', min: { value: 0, message: '0 이상이어야 합니다' }
          })} />
        </FormField>
      </FormSection>
      <button type="submit" disabled={form.formState.isSubmitting}>
        {isEdit ? '수정' : '등록'}
      </button>
    </form>
  );
}
```

---

## Hook Patterns

### List Query Hook (with pagination and filters)

```typescript
// features/product-management/hooks/use-products.ts
import { useQuery } from '@tanstack/react-query';
import { useSearchParams, useRouter } from 'next/navigation';
import { productApi } from '../api/product-api';

export function useProducts() {
  const searchParams = useSearchParams();
  const router = useRouter();

  const page = Number(searchParams.get('page') ?? '1');
  const status = searchParams.get('status') ?? undefined;
  const search = searchParams.get('q') ?? undefined;

  const query = useQuery({
    queryKey: ['products', { page, status, search }],
    queryFn: () => productApi.getProducts({ page, status, search }),
  });

  const setFilter = (key: string, value: string | undefined) => {
    const params = new URLSearchParams(searchParams.toString());
    if (value) params.set(key, value);
    else params.delete(key);
    params.set('page', '1');
    router.push(`?${params.toString()}`);
  };

  return {
    ...query,
    pagination: { page, total: query.data?.totalPages ?? 0 },
    filters: { status, search, setFilter },
  };
}
```

### Mutation Hook

```typescript
// features/product-management/hooks/use-create-product.ts
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { productApi } from '../api/product-api';

export function useCreateProduct() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: productApi.createProduct,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['products'] });
    },
  });
}
```

---

## API Layer

```typescript
// features/product-management/api/product-api.ts
import { apiClient } from '@repo/api-client';
import type { Product, PaginatedResponse } from '@repo/types';

interface GetProductsParams {
  page?: number;
  status?: string;
  search?: string;
}

export const productApi = {
  getProducts: (params: GetProductsParams) =>
    apiClient.get<PaginatedResponse<Product>>('/admin/products', { params }),

  getProduct: (id: string) =>
    apiClient.get<Product>(`/admin/products/${id}`),

  createProduct: (data: Omit<Product, 'id'>) =>
    apiClient.post<Product>('/admin/products', data),

  updateProduct: (data: { id: string } & Partial<Product>) =>
    apiClient.put<Product>(`/admin/products/${data.id}`, data),

  deleteProduct: (id: string) =>
    apiClient.delete(`/admin/products/${id}`),
};
```

---

## Page Composition (App Router)

Admin pages are all client-rendered. Pages are thin — compose features only.

```typescript
// app/products/page.tsx
'use client';

import { PageLayout } from '@/shared/ui/PageLayout';
import { ProductList } from '@/features/product-management';

export default function ProductsPage() {
  return (
    <PageLayout title="상품 관리" actions={[{ label: '상품 등록', href: '/products/new' }]}>
      <ProductList />
    </PageLayout>
  );
}
```

```typescript
// app/products/new/page.tsx
'use client';

import { PageLayout } from '@/shared/ui/PageLayout';
import { ProductForm } from '@/features/product-management';

export default function NewProductPage() {
  return (
    <PageLayout title="상품 등록">
      <ProductForm />
    </PageLayout>
  );
}
```

```typescript
// app/products/[id]/edit/page.tsx
'use client';

import { PageLayout } from '@/shared/ui/PageLayout';
import { ProductForm } from '@/features/product-management';
import { useProduct } from '@/features/product-management';
import { use } from 'react';

interface Props {
  params: Promise<{ id: string }>;
}

export default function EditProductPage({ params }: Props) {
  const { id } = use(params);
  const { data: product, isLoading } = useProduct(id);

  if (isLoading || !product) return <PageLayout.Skeleton />;

  return (
    <PageLayout title={`${product.name} 수정`}>
      <ProductForm product={product} />
    </PageLayout>
  );
}
```

---

## Shared UI Components

### DataTable

```typescript
// shared/ui/DataTable.tsx
'use client';

export interface ColumnDef<T> {
  key: string;
  header: string;
  sortable?: boolean;
  render?: (item: T) => React.ReactNode;
}

interface Props<T> {
  columns: ColumnDef<T>[];
  data: T[];
  pagination: { page: number; total: number };
  isLoading: boolean;
  onPageChange?: (page: number) => void;
}

export function DataTable<T>({ columns, data, pagination, isLoading }: Props<T>) {
  // Table rendering with sorting, pagination
}
```

### PageLayout

```typescript
// shared/ui/PageLayout.tsx
interface Action {
  label: string;
  href?: string;
  variant?: 'primary' | 'danger';
  onClick?: () => void;
}

interface Props {
  title: string;
  actions?: Action[];
  children: React.ReactNode;
}

export function PageLayout({ title, actions, children }: Props) {
  return (
    <div>
      <header>
        <h1>{title}</h1>
        <div>{actions?.map(/* render action buttons */)}</div>
      </header>
      <main>{children}</main>
    </div>
  );
}

PageLayout.Skeleton = function Skeleton() {
  return <div>{/* loading skeleton */}</div>;
};
```

---

## Auth Guard Pattern

```typescript
// app/layout.tsx
import { AuthGuard } from '@/shared/hooks/use-auth';
import { Sidebar } from '@/shared/ui/Sidebar';

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthGuard>
      <div className="admin-layout">
        <Sidebar />
        <main>{children}</main>
      </div>
    </AuthGuard>
  );
}
```

---

## Adding a New Feature (Checklist)

When adding a new management domain (e.g., `coupon-management`):

1. Create `features/coupon-management/` with `components/`, `hooks/`, `api/`, `types/`
2. Implement API functions in `api/coupon-api.ts`
3. Create query/mutation hooks in `hooks/`
4. Build List, Detail, Form components using shared UI (`DataTable`, `FormField`, `PageLayout`)
5. Add route pages in `app/coupons/`
6. Export public API from `index.ts`

---

## Common Mistakes

| Mistake | Fix |
|---|---|
| Feature imports from another feature | Extract shared type to `@repo/types` or shared component to `shared/` |
| Business logic in page component | Move to feature's `hooks/` |
| Duplicating DataTable/Form logic per feature | Use shared components; features only configure columns/fields |
| API call directly in component | Create hook in `hooks/`, API function in `api/` |
| Shared component depends on feature-specific type | Use generic types (`<T>`) in shared components |
| Feature-specific component in `shared/` | Keep in feature; only move to `shared/` when two or more features need it |
| Form state managed with useState | Use React Hook Form for consistent form handling |
| URL state not synced with filters | Use `useSearchParams` for filter/pagination state |
