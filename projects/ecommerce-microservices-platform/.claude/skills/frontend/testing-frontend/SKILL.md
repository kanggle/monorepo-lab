---
name: testing-frontend
description: Frontend test writing (Vitest + Testing Library)
category: frontend
---

# Skill: Frontend Testing

Patterns for testing Next.js applications with Vitest and Testing Library.

Prerequisite: read `specs/platform/testing-strategy.md` before using this skill.

---

## Test Setup

Tests use Vitest + `@testing-library/react` + `@testing-library/user-event`.

```typescript
// __tests__/setup.ts
import '@testing-library/jest-dom/vitest';
```

---

## Hook Tests

Test React Query hooks by mocking the API layer.

```typescript
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useProducts } from '@/features/product-management/hooks/use-products';
import * as productApi from '@/features/product-management/api/product-api';

vi.mock('@/features/product-management/api/product-api');

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

describe('useProducts', () => {
  it('returns product list on success', async () => {
    vi.mocked(productApi.getProducts).mockResolvedValue(mockProductsResponse);

    const { result } = renderHook(() => useProducts(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.content).toHaveLength(2);
  });
});
```

---

## Component Tests

Test components with user interactions and rendered output.

```typescript
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProductForm } from '@/features/product-management/components/ProductForm';

describe('ProductForm', () => {
  it('disables submit when name is empty', () => {
    render(<ProductForm onSubmit={vi.fn()} />);

    const submitButton = screen.getByRole('button', { name: /save/i });
    expect(submitButton).toBeDisabled();
  });

  it('calls onSubmit with form data', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();
    render(<ProductForm onSubmit={onSubmit} />);

    await user.type(screen.getByLabelText(/name/i), 'Test Product');
    await user.clear(screen.getByLabelText(/price/i));
    await user.type(screen.getByLabelText(/price/i), '10000');
    await user.click(screen.getByRole('button', { name: /save/i }));

    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'Test Product', price: 10000 })
    );
  });
});
```

---

## Mocking Patterns

### Mock API Module

```typescript
vi.mock('@/features/product-management/api/product-api');
vi.mocked(productApi.getProducts).mockResolvedValue(mockData);
```

### Mock Next.js Router

```typescript
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/products',
}));
```

### Mock Auth

```typescript
vi.mock('@/shared/hooks/use-auth', () => ({
  useAuth: () => ({ user: mockUser, isAuthenticated: true }),
}));
```

---

## Test File Structure

```
__tests__/
├── setup.ts
├── app/
│   └── orders/
│       └── page.test.tsx
├── features/
│   └── product-management/
│       ├── components/
│       │   ├── ProductList.test.tsx
│       │   └── ProductForm.test.tsx
│       └── hooks/
│           ├── use-products.test.ts
│           └── use-create-product.test.ts
└── shared/
    └── hooks/
        └── AuthGuard.test.tsx
```

---

## Test Naming

Use descriptive test names that explain the behavior:

```typescript
it('shows loading skeleton while fetching', () => { ... });
it('displays error message when API fails', () => { ... });
it('navigates to detail page on row click', () => { ... });
```

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Missing `QueryClientProvider` wrapper | Always wrap hooks in a provider with `retry: false` |
| Not awaiting async state changes | Use `waitFor` for React Query state updates |
| Testing implementation details | Test user-visible behavior, not internal state |
| Not mocking `next/navigation` | Mock router, searchParams, pathname |
| Shared query client across tests | Create a new `QueryClient` per test |
