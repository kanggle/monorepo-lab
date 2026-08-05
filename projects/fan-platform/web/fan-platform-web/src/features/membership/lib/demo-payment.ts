'use client';

/**
 * Is this deployment running the demo (mock) payment gateway? (TASK-FAN-FE-015)
 *
 * Answered at RUNTIME via `/api/payment-config`, never from a `NEXT_PUBLIC_*` value. Next inlines
 * those at build time, so the image would carry whichever answer was true when it was built —
 * which is precisely why the demo could not switch the PortOne window off by env alone.
 *
 * **Fails to `false`.** An unreachable or malformed config means "not a demo", so the caller falls
 * through to the real PortOne path exactly as it did before this change. The other default would
 * let a transient fetch failure silently turn a real storefront into one that skips the payment
 * window and reports every checkout as paid.
 *
 * Not cached: the flag is read once per checkout attempt, which is rare enough that a stale cache
 * would only ever cost correctness.
 */
export async function isDemoPayment(): Promise<boolean> {
  try {
    const res = await fetch('/api/payment-config', { cache: 'no-store' });
    if (!res.ok) return false;
    const body = (await res.json()) as { demoPayment?: boolean };
    return body.demoPayment === true;
  } catch {
    return false;
  }
}
