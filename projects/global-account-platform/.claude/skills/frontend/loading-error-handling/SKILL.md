---
name: loading-error-handling
description: Frontend loading and error state handling
category: frontend
---

# Skill: Loading & Error Handling

Patterns for handling loading states and errors in Next.js applications.

Prerequisite: read `platform/coding-rules.md` (TypeScript Rules section) before using this skill.

---

## Query Loading States

React Query provides `isLoading`, `isError`, `error` out of the box.

```typescript
export function ProductList() {
  const { data, isLoading, isError, error } = useProducts();

  if (isLoading) return <LoadingSkeleton />;
  if (isError) return <ErrorMessage error={error} />;
  if (!data?.content.length) return <EmptyState message="No products found" />;

  return (
    <ul>
      {data.content.map((product) => (
        <ProductCard key={product.id} product={product} />
      ))}
    </ul>
  );
}
```

---

## Loading Patterns

### Skeleton Loading

```typescript
function LoadingSkeleton() {
  return (
    <div className="space-y-4">
      {Array.from({ length: 5 }).map((_, i) => (
        <div key={i} className="h-16 bg-gray-200 animate-pulse rounded" />
      ))}
    </div>
  );
}
```

### Inline Loading (for mutations)

```typescript
<button disabled={mutation.isPending}>
  {mutation.isPending ? 'Processing...' : 'Submit'}
</button>
```

---

## Error Patterns

### Error Message Component

```typescript
function ErrorMessage({ error }: { error: unknown }) {
  const message = getErrorMessage(error, 'An error occurred');
  return (
    <div className="p-4 bg-red-50 text-red-700 rounded">
      <p>{message}</p>
    </div>
  );
}
```

### Next.js Error Boundary

```typescript
// app/error.tsx
'use client';

export default function ErrorPage({ error, reset }: { error: Error; reset: () => void }) {
  return (
    <div>
      <h2>Something went wrong</h2>
      <p>{error.message}</p>
      <button onClick={reset}>Try again</button>
    </div>
  );
}
```

---

## Empty State

Always handle the case when data is loaded but empty.

```typescript
if (!data?.content.length) {
  return <EmptyState message="No orders found" />;
}
```

---

## Mutation Error Handling

Show errors inline or via alert. Do not silently swallow errors.

```typescript
const mutation = useMutation({
  mutationFn: cancelOrder,
  onError: (error) => {
    window.alert(getErrorMessage(error, 'Failed to cancel order'));
  },
});
```

---

## Page-Level Pattern

Standard order for conditional rendering in a page component:

```
1. Loading → show skeleton
2. Error → show error message
3. Empty → show empty state
4. Data → show content
```

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| No loading state — blank screen while fetching | Always handle `isLoading` |
| Error swallowed silently | Show error to user with `getErrorMessage()` |
| Missing empty state | Check `data.content.length === 0` |
| Using `try/catch` around `useQuery` | React Query handles errors internally — use `isError` |
| Full-page spinner for small updates | Use inline loading or skeleton for partial updates |
