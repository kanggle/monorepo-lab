import type { CheckoutCartItem } from './types';

/**
 * Checkout placement idempotency key (TASK-BE-430).
 *
 * The web-store creates an order BEFORE redirecting to the Toss payment widget;
 * a payment failure / back-navigation / double-submit used to re-run placement and
 * create a DUPLICATE order. The server now dedupes on a client `Idempotency-Key`,
 * and this module produces a key that is:
 *   - STABLE across retries of the SAME cart (so a retry reuses the original order), and
 *   - FRESH when the cart content changes (so a genuinely different order is created).
 *
 * The key is persisted in sessionStorage keyed by a cart-content hash, so it
 * survives the full-page Toss redirect round-trip within the same tab. It is
 * cleared on a completed payment ({@link clearCheckoutIdempotencyKey}, called from
 * the payment-complete page) so the next purchase starts a new key.
 */

const STORAGE_KEY = 'checkout_idem';

interface StoredKey {
  cartHash: string;
  key: string;
}

/** Order-independent, content-sensitive hash of the checkout line items. */
function hashItems(items: CheckoutCartItem[]): string {
  const canonical = items
    .map((i) => `${i.productId}:${i.variantId}:${i.quantity}:${i.price}`)
    .sort()
    .join('|');
  // Small, stable, non-cryptographic string hash (djb2). Collisions only weaken
  // dedup (two different carts sharing a key) which the server backstops by also
  // scoping the key to the user; for cart content it is more than sufficient.
  let h = 5381;
  for (let i = 0; i < canonical.length; i++) {
    h = ((h << 5) + h + canonical.charCodeAt(i)) | 0;
  }
  return (h >>> 0).toString(36);
}

function newKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
}

/**
 * Returns the idempotency key for placing an order with the given cart items:
 * the stored key when the cart is unchanged (retry of the same checkout), or a
 * freshly generated + persisted key when the cart differs / none is stored.
 */
export function getOrCreateIdempotencyKey(items: CheckoutCartItem[]): string {
  const cartHash = hashItems(items);
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as StoredKey;
      if (parsed && parsed.cartHash === cartHash && parsed.key) {
        return parsed.key;
      }
    }
  } catch {
    // sessionStorage unavailable / malformed → fall through to a fresh key.
  }
  const key = newKey();
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ cartHash, key } satisfies StoredKey));
  } catch {
    // Best-effort persistence; a non-persisted key still dedupes within this call.
  }
  return key;
}

/** Clears the stored key after a completed payment so the next checkout starts fresh. */
export function clearCheckoutIdempotencyKey(): void {
  try {
    sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // no-op
  }
}
