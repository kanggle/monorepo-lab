---
name: api-client
description: Frontend API client setup and usage
category: frontend
---

# Skill: API Client

Patterns for API communication in Next.js frontend applications.

Prerequisite: read `platform/coding-rules.md` (TypeScript Rules section) before using this skill.

---

## Shared API Client

The `@repo/api-client` package provides a centralized Axios-based client.

```typescript
// shared/config/api.ts
import { createApiClient } from '@repo/api-client';

export const apiClient = createApiClient({
  baseURL: process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080',
  loginPath: '/login',
});
```

---

## Feature API Module

Each feature creates a thin wrapper around the shared API client.

```typescript
// features/product-management/api/product-api.ts
import { apiClient } from '@/shared/config/api';
import { createProductApi } from '@repo/api-client';

const productApi = createProductApi(apiClient);

export async function getProducts(params?: ProductListParams) {
  return productApi.getProducts(params);
}

export async function getProduct(id: string) {
  return productApi.getProduct(id);
}

export async function createProduct(data: CreateProductRequest) {
  return productApi.createProduct(data);
}
```

---

## Type Safety

API types are defined in `@repo/types` and shared between apps.

```typescript
// Import from shared types package
import type { ProductSummary, ProductDetail, CreateProductRequest } from '@repo/types';
import type { PaginatedResponse } from '@repo/types';
```

---

## Error Handling

Use `getErrorMessage` from `@repo/types/guards` for user-facing error messages.

```typescript
import { getErrorMessage } from '@repo/types/guards';

try {
  await createProduct(data);
} catch (error) {
  const message = getErrorMessage(error, 'Failed to create product');
  // show to user
}
```

---

## Server-Side API Calls

For server components, call APIs directly without the client-side interceptors.

```typescript
// app/(admin)/products/page.tsx (server component)
async function ProductsPage() {
  const products = await getProducts({ page: 0 });
  return <ProductList initialData={products} />;
}
```

---

## Rules

- One API module per feature in `features/{name}/api/`.
- Use `@repo/api-client` factories — do not call `axios` directly.
- Types come from `@repo/types` — do not duplicate.
- Error messages use `getErrorMessage()` for consistency.
- API base URL comes from `NEXT_PUBLIC_API_URL` environment variable.

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Calling `axios.get()` directly | Use the shared `apiClient` or API factory |
| Duplicating response types locally | Import from `@repo/types` |
| Missing error handling on API calls | Always catch and show user-friendly messages |
| Hardcoding API URLs | Use `NEXT_PUBLIC_API_URL` env var |
