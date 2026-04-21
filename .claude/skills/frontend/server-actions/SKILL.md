---
name: server-actions
description: Next.js App Router server actions and revalidation
category: frontend
---

# Skill: Server Actions

Patterns for Next.js App Router server actions with revalidation and error handling.

Prerequisite: read `specs/platform/coding-rules.md` (TypeScript Rules) and `frontend/api-client/SKILL.md` before using this skill. For HTTP-based APIs prefer `frontend/api-client/SKILL.md` + React Query; use server actions only when the action is colocated with a server component or page.

---

## When to Use Server Actions

Use server actions when:
- Form submission lives in a server component page
- The action needs server-only secrets (API tokens) and proxies to a backend
- You want automatic revalidation of cached server data

Do **not** use server actions when:
- Multiple components on the same page consume the same mutation (use React Query mutation)
- The action is purely client-side state
- You need optimistic updates with rollback (React Query is better)

---

## Defining a Server Action

```tsx
// features/order/actions/place-order.ts
'use server';

import { revalidateTag } from 'next/cache';
import { z } from 'zod';
import { orderApi } from '@/shared/api/order';

const PlaceOrderSchema = z.object({
  productId: z.string().uuid(),
  quantity: z.coerce.number().int().min(1).max(99),
});

export type PlaceOrderState =
  | { status: 'idle' }
  | { status: 'success'; orderId: string }
  | { status: 'error'; message: string; fieldErrors?: Record<string, string> };

export async function placeOrderAction(
  _prev: PlaceOrderState,
  formData: FormData
): Promise<PlaceOrderState> {
  const parsed = PlaceOrderSchema.safeParse(Object.fromEntries(formData));
  if (!parsed.success) {
    return {
      status: 'error',
      message: 'Invalid input',
      fieldErrors: parsed.error.flatten().fieldErrors as Record<string, string>,
    };
  }
  try {
    const { orderId } = await orderApi.placeOrder(parsed.data);
    revalidateTag('orders');
    return { status: 'success', orderId };
  } catch (err) {
    return { status: 'error', message: (err as Error).message };
  }
}
```

Rules:
- Always validate input with zod (or equivalent) — never trust the client
- Return a discriminated union state, never throw across the boundary
- Call `revalidateTag` / `revalidatePath` on success to refresh cached data
- Never read `cookies()` / `headers()` and forward them to clients

---

## Consuming with `useActionState`

```tsx
'use client';
import { useActionState } from 'react';
import { placeOrderAction, type PlaceOrderState } from '../actions/place-order';

const initial: PlaceOrderState = { status: 'idle' };

export function PlaceOrderForm({ productId }: { productId: string }) {
  const [state, formAction, pending] = useActionState(placeOrderAction, initial);
  return (
    <form action={formAction}>
      <input type="hidden" name="productId" value={productId} />
      <input type="number" name="quantity" min={1} max={99} />
      <button type="submit" disabled={pending}>{pending ? 'Placing…' : 'Place Order'}</button>
      {state.status === 'error' && <p role="alert">{state.message}</p>}
      {state.status === 'success' && <p>Order placed: {state.orderId}</p>}
    </form>
  );
}
```

---

## Revalidation Patterns

| Function | Effect |
|---|---|
| `revalidateTag('orders')` | Invalidate all fetches with `next: { tags: ['orders'] }` |
| `revalidatePath('/orders')` | Invalidate a specific route segment |
| `redirect('/orders/123')` | Server-side redirect after success |

Tag your fetches consistently:

```tsx
const orders = await fetch(`${API}/orders`, { next: { tags: ['orders'], revalidate: 60 } });
```

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Throwing instead of returning state | Always return discriminated union |
| Forgetting to call `revalidateTag` | Stale UI after mutation |
| Validating only on the client | Server must re-validate |
| Logging full FormData | Mask sensitive fields |
| Server action in client component file | Must live in `'use server'` module |
| Mixing server action with React Query mutation for same data | Choose one path |

---

## Verification Checklist

- [ ] Server action validates input with zod
- [ ] Returns discriminated union state, never throws
- [ ] Calls `revalidateTag` or `revalidatePath` on success
- [ ] Form uses `useActionState` for pending and error UI
- [ ] No secrets or tokens leaked to client
- [ ] Test covers happy path, validation error, and server error
