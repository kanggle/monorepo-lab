---
name: state-management
description: Frontend state management with React Query
category: frontend
---

# Skill: State Management

Patterns for state management in Next.js applications using TanStack React Query.

Prerequisite: read `platform/coding-rules.md` (TypeScript Rules section) before using this skill.

---

## Query Hooks

Use React Query for all server state. Define hooks in `features/{name}/hooks/`.

```typescript
// features/product-management/hooks/use-products.ts
import { useQuery } from '@tanstack/react-query';
import { getProducts } from '../api/product-api';
import { productKeys } from './query-keys';

export function useProducts() {
  const { page, getParam, setFilter, buildPagination } = useListParams();
  const status = toValidStatus(getParam('status'), VALID_STATUSES);
  const name = getParam('name') || undefined;

  const query = useQuery({
    queryKey: productKeys.list({ page, status, name }),
    queryFn: () => getProducts({ page, status, name }),
  });

  return {
    ...query,
    pagination: buildPagination(query.data),
    filters: { status, name, setFilter },
  };
}
```

---

## Query Keys

Centralize query keys per feature for consistent cache management.

```typescript
// features/product-management/hooks/query-keys.ts
export const productKeys = {
  all: ['products'] as const,
  lists: () => [...productKeys.all, 'list'] as const,
  list: (params: Record<string, unknown>) => [...productKeys.lists(), params] as const,
  details: () => [...productKeys.all, 'detail'] as const,
  detail: (id: string) => [...productKeys.details(), id] as const,
};
```

---

## Mutation Hooks

Use `useMutation` with cache invalidation on success.

```typescript
// features/product-management/hooks/use-create-product.ts
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createProduct } from '../api/product-api';
import { productKeys } from './query-keys';

export function useCreateProduct() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: createProduct,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: productKeys.all });
    },
    onError: (error) => {
      window.alert(getErrorMessage(error, 'Failed to create product'));
    },
  });
}
```

---

## Cache Invalidation Strategy

| Action | Invalidation |
|---|---|
| Create | Invalidate all list queries (`keys.all`) |
| Update | Invalidate all list queries + specific detail (`keys.detail(id)`) |
| Delete | Invalidate all list queries |

---

## Client State

For UI-only state (modals, form inputs, toggles), use React `useState`. Do not use React Query for client-local state.

```typescript
const [isModalOpen, setIsModalOpen] = useState(false);
const [searchInput, setSearchInput] = useState('');
```

---

## URL State (Filters & Pagination)

Use URL search params for filter/pagination state via `useListParams` hook.

```typescript
const { page, getParam, setFilter, buildPagination } = useListParams();
```

This ensures filters are shareable via URL and survive page refresh.

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Storing server data in `useState` | Use React Query — it handles caching, refetching, staleness |
| Inconsistent query keys | Use centralized `query-keys.ts` per feature |
| Missing cache invalidation after mutation | Always invalidate related queries in `onSuccess` |
| Fetching in `useEffect` manually | Use `useQuery` — it handles loading, error, and caching |
