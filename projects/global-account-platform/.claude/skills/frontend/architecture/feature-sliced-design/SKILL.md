---
name: feature-sliced-design
description: Implement Feature-Sliced Design app (Next.js)
category: frontend
---

# Skill: Feature-Sliced Design Implementation

Next.js (App Router) implementation patterns for applications using Feature-Sliced Design.

Prerequisite: read `specs/services/<app>/architecture.md` before using this skill.

> `@repo/api-client`, `@repo/types` are monorepo shared libraries defined in `libs/`. See `platform/shared-library-policy.md` for ownership rules.

---

## Directory Structure

```
src/
├── app/                          # Next.js App Router
│   ├── layout.tsx
│   ├── page.tsx
│   ├── (store)/                  # Route group: storefront pages
│   │   ├── products/
│   │   │   ├── page.tsx          # Product listing (SSR)
│   │   │   └── [id]/
│   │   │       └── page.tsx      # Product detail (SSR)
│   │   ├── cart/
│   │   │   └── page.tsx          # Cart (CSR)
│   │   └── checkout/
│   │       └── page.tsx          # Checkout (CSR)
│   └── (auth)/
│       ├── login/page.tsx
│       └── signup/page.tsx
├── widgets/                      # Composite blocks (feature combinations)
├── features/                     # Self-contained feature modules
│   ├── product/
│   ├── cart/
│   ├── checkout/
│   ├── search/
│   └── auth/
├── entities/                     # Shared domain types and base components
│   ├── product/
│   ├── user/
│   └── order/
└── shared/                       # Framework-agnostic utilities and UI primitives
    ├── ui/
    ├── lib/
    ├── config/
    └── hooks/
```

---

## Feature Module Pattern

Each feature is a self-contained module with four segments.

```
features/cart/
├── ui/
│   ├── CartItem.tsx
│   ├── CartSummary.tsx
│   └── AddToCartButton.tsx
├── model/
│   ├── cart-store.ts             # Client state (Zustand or context)
│   ├── types.ts                  # Feature-specific types
│   └── use-cart.ts               # Combined hook exposing cart behavior
├── api/
│   ├── add-to-cart.ts
│   ├── remove-from-cart.ts
│   └── get-cart.ts
├── lib/
│   ├── calculate-total.ts        # Pure business logic
│   └── validate-quantity.ts
└── index.ts                      # Public API — only export what other layers need
```

### Public API (index.ts)

Each feature exposes a controlled public interface. Other layers import only from the index.

```typescript
// features/cart/index.ts
export { AddToCartButton } from './ui/AddToCartButton';
export { CartSummary } from './ui/CartSummary';
export { useCart } from './model/use-cart';
export type { CartItem } from './model/types';
```

Rules:
- Internal files (`lib/`, individual `api/` calls) are NOT exported
- Other layers import via `@/features/cart`, never `@/features/cart/lib/calculate-total`

---

## Entity Pattern

Entities provide shared domain types and base UI that features build upon.

```
entities/product/
├── ui/
│   ├── ProductCard.tsx           # Base card (no cart button — that's a feature concern)
│   └── ProductImage.tsx
├── model/
│   └── types.ts                  # Product, ProductVariant, Price types
├── api/
│   └── get-product.ts            # Base product fetch hook
└── index.ts
```

```typescript
// entities/product/model/types.ts
export interface Product {
  id: string;
  name: string;
  slug: string;
  description: string;
  price: Price;
  variants: ProductVariant[];
  status: ProductStatus;
}

export interface Price {
  amount: number;
  currency: string;
}
```

Rules:
- Entity types represent the core domain — import from `@repo/types` when shared with backend
- Entity UI is "pure" — no feature-specific behavior (e.g., ProductCard shows product info but has no "Add to Cart" button)
- Features compose entity UI with feature behavior

---

## Widget Pattern

Widgets combine multiple features into composite UI blocks.

```typescript
// widgets/ProductCardWithCart.tsx
'use client';

import { ProductCard } from '@/entities/product';
import { AddToCartButton } from '@/features/cart';
import type { Product } from '@/entities/product';

interface Props {
  product: Product;
}

export function ProductCardWithCart({ product }: Props) {
  return (
    <ProductCard product={product}>
      <AddToCartButton productId={product.id} price={product.price} />
    </ProductCard>
  );
}
```

Rules:
- Widgets import from `features/` and `entities/` — never contain business logic
- Use composition (children, slots) to combine features
- One widget per file, named after the combination it creates

---

## Page Composition (App Router)

Pages are thin — they fetch data and compose widgets/features.

### Server Component Page (SSR)

```typescript
// app/(store)/products/[id]/page.tsx
import { getProduct } from '@/entities/product';
import { ProductDetail } from '@/features/product';
import { AddToCartButton } from '@/features/cart';
import { notFound } from 'next/navigation';

interface Props {
  params: Promise<{ id: string }>;
}

export default async function ProductDetailPage({ params }: Props) {
  const { id } = await params;
  const product = await getProduct(id);

  if (!product) {
    notFound();
  }

  return (
    <div>
      <ProductDetail product={product} />
      <AddToCartButton productId={product.id} price={product.price} />
    </div>
  );
}
```

### Client Component Page (CSR)

```typescript
// app/(store)/cart/page.tsx
'use client';

import { CartSummary, useCart } from '@/features/cart';
import { CheckoutButton } from '@/features/checkout';

export default function CartPage() {
  const { items, totalAmount } = useCart();

  return (
    <div>
      <CartSummary items={items} total={totalAmount} />
      <CheckoutButton disabled={items.length === 0} />
    </div>
  );
}
```

Rules:
- Pages do NOT contain business logic
- Server Components by default — add `'use client'` only when client interactivity is needed
- Data fetching in Server Components uses `api/` functions directly (not hooks)
- Data fetching in Client Components uses hooks from feature's `api/`

---

## API Layer Pattern

### Server-side data fetching (Server Components)

```typescript
// entities/product/api/get-product.ts
import { apiClient } from '@repo/api-client';
import type { Product } from '../model/types';

export async function getProduct(id: string): Promise<Product | null> {
  try {
    return await apiClient.get<Product>(`/products/${id}`);
  } catch {
    return null;
  }
}
```

### Client-side data fetching (Client Components)

```typescript
// features/cart/api/get-cart.ts
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@repo/api-client';
import type { Cart } from '../model/types';

export function useCartQuery() {
  return useQuery({
    queryKey: ['cart'],
    queryFn: () => apiClient.get<Cart>('/cart'),
  });
}
```

### Mutation

```typescript
// features/cart/api/add-to-cart.ts
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@repo/api-client';

interface AddToCartParams {
  productId: string;
  quantity: number;
}

export function useAddToCart() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (params: AddToCartParams) =>
      apiClient.post('/cart/items', params),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cart'] });
    },
  });
}
```

---

## Cross-Feature Communication

Features must NOT import from each other. Use these patterns instead:

### Via Entity (shared type)

```typescript
// entities/product/model/types.ts — both features import this
export interface Product { id: string; name: string; price: Price; }

// features/product uses Product to display
// features/cart uses Product to reference in cart items
```

### Via Widget Composition

```typescript
// widgets/ProductCardWithCart.tsx — composes product + cart features
// Neither feature knows about the other
```

### Via URL/Router (navigation)

```typescript
// features/cart/ui/CheckoutLink.tsx
import Link from 'next/link';

export function CheckoutLink() {
  return <Link href="/checkout">Proceed to Checkout</Link>;
}
// Cart feature navigates to checkout page without importing checkout feature
```

---

## Common Mistakes

| Mistake | Fix |
|---|---|
| Feature imports from another feature | Extract shared type to `entities/`, or compose in `widgets/` |
| Business logic in `app/` page component | Move to feature's `model/` or `lib/` |
| Entity component has feature-specific behavior | Keep entity UI pure; add behavior via widget composition |
| `'use client'` on a page that only fetches and renders | Use Server Component; add `'use client'` only for interactivity |
| API call directly in component body | Use hook (`useQuery`) in Client Components, async function in Server Components |
| Importing feature internals (`@/features/cart/lib/...`) | Import only from feature's `index.ts` public API |
| Global state for server data | Use TanStack Query/SWR — server state is not client state |
| Shared component in a feature folder | Move to `shared/ui/` if used by two or more features |
