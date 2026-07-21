import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { ProductForm } from '@/features/ecommerce-ops/components/ProductForm';
import { StockAdjustDialog } from '@/features/ecommerce-ops/components/StockAdjustDialog';
import { CouponIssueDialog } from '@/features/ecommerce-ops/components/CouponIssueDialog';

/**
 * TASK-PC-FE-252 — the Idempotency-Key must be a function of the user's INTENT,
 * not of the individual network call. These tests drive two submits through each
 * dialog and compare the key actually sent on the wire — the bar the task set
 * (F2: "assert the two requests' keys, not merely that a header exists"):
 *
 *   AC-2  same intent resubmit (retry after a failure, no edit)  → SAME key
 *   AC-3  different intent (values edited between submits)        → DIFFERENT key
 *   AC-4  a legitimately repeated operation (+10 twice / re-issue)→ DIFFERENT key
 *         (a value-hash key would collapse these forever — the wrong fix)
 *
 * The client posts `{ ...body, idempotencyKey }`; `fetch` is stubbed, so the
 * captured request body carries the key the dialog decided to send.
 */

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), refresh: vi.fn() }),
}));

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

function ok(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function unavailable() {
  return new Response(
    JSON.stringify({ code: 'SERVICE_UNAVAILABLE', message: 'e', timestamp: 't' }),
    { status: 503, headers: { 'Content-Type': 'application/json' } },
  );
}

/** The idempotencyKey on the Nth request whose method matches. */
function sentKeys(fetchMock: ReturnType<typeof vi.fn>, method: string): string[] {
  return fetchMock.mock.calls
    .filter((c) => (c[1] as RequestInit | undefined)?.method === method)
    .map((c) => JSON.parse((c[1] as RequestInit).body as string).idempotencyKey);
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

// ===========================================================================
// registerProduct — key held in the confirm-gated form (use-product-form)
// ===========================================================================
describe('registerProduct idempotency-key lifetime (TASK-PC-FE-252)', () => {
  async function fillAndSubmit(user: ReturnType<typeof userEvent.setup>) {
    await user.type(screen.getByTestId('product-form-name'), 'Tee');
    await user.type(screen.getByTestId('product-form-price'), '12000');
    await user.type(screen.getByTestId('product-form-variant-name-0'), 'M');
    await user.click(screen.getByTestId('product-form-submit'));
  }

  it('AC-2: retrying the SAME confirmed create (after a failure, no edit) reuses the key', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(unavailable())
      .mockResolvedValue(ok({ id: 'p-9' }, 201));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<ProductForm />, { wrapper: wrapper() });

    await fillAndSubmit(user);
    await user.click(screen.getByTestId('ecommerce-confirm-confirm')); // fails
    await waitFor(() => expect(sentKeys(fetchMock, 'POST')).toHaveLength(1));
    await user.click(screen.getByTestId('ecommerce-confirm-confirm')); // retry
    await waitFor(() => expect(sentKeys(fetchMock, 'POST')).toHaveLength(2));

    const [k1, k2] = sentKeys(fetchMock, 'POST');
    expect(k1).toBeTruthy();
    expect(k2).toBe(k1);
  });

  it('AC-3: editing a field then resubmitting sends a NEW key (a different create)', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(unavailable())
      .mockResolvedValue(ok({ id: 'p-9' }, 201));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<ProductForm />, { wrapper: wrapper() });

    await fillAndSubmit(user);
    await user.click(screen.getByTestId('ecommerce-confirm-confirm')); // fails
    await waitFor(() => expect(sentKeys(fetchMock, 'POST')).toHaveLength(1));

    // Back out of the confirm, edit the name → a genuinely different product.
    await user.click(screen.getByTestId('ecommerce-confirm-cancel'));
    await user.type(screen.getByTestId('product-form-name'), 'X');
    await user.click(screen.getByTestId('product-form-submit'));
    await user.click(screen.getByTestId('ecommerce-confirm-confirm'));
    await waitFor(() => expect(sentKeys(fetchMock, 'POST')).toHaveLength(2));

    const [k1, k2] = sentKeys(fetchMock, 'POST');
    expect(k2).not.toBe(k1);
  });
});

// ===========================================================================
// adjustStock — key held in StockAdjustDialog
// ===========================================================================
describe('adjustStock idempotency-key lifetime (TASK-PC-FE-252)', () => {
  const VARIANT = { id: 'v-1', optionName: 'M', stock: 5, additionalPrice: 0 };

  function renderDialog() {
    return render(
      <StockAdjustDialog
        open
        productId="p-1"
        variant={VARIANT}
        onClose={vi.fn()}
        onAdjusted={vi.fn()}
      />,
      { wrapper: wrapper() },
    );
  }

  async function fill(user: ReturnType<typeof userEvent.setup>, qty: string) {
    await user.clear(screen.getByTestId('stock-adjust-quantity'));
    await user.type(screen.getByTestId('stock-adjust-quantity'), qty);
    await user.clear(screen.getByTestId('stock-adjust-reason'));
    await user.type(screen.getByTestId('stock-adjust-reason'), 'restock');
  }

  it('AC-2: retrying the same delta after a failure reuses the key', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(unavailable())
      .mockResolvedValue(ok({ variantId: 'v-1', currentStock: 15 }));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderDialog();

    await fill(user, '10');
    await user.click(screen.getByTestId('ecommerce-confirm-confirm')); // fails
    await waitFor(() => expect(sentKeys(fetchMock, 'PATCH')).toHaveLength(1));
    await user.click(screen.getByTestId('ecommerce-confirm-confirm')); // retry
    await waitFor(() => expect(sentKeys(fetchMock, 'PATCH')).toHaveLength(2));

    const [k1, k2] = sentKeys(fetchMock, 'PATCH');
    expect(k1).toBeTruthy();
    expect(k2).toBe(k1);
  });

  it('AC-3: editing the delta before a retry sends a NEW key', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(unavailable())
      .mockResolvedValue(ok({ variantId: 'v-1', currentStock: 25 }));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderDialog();

    await fill(user, '10');
    await user.click(screen.getByTestId('ecommerce-confirm-confirm')); // fails
    await waitFor(() => expect(sentKeys(fetchMock, 'PATCH')).toHaveLength(1));

    await fill(user, '20'); // different delta = different intent
    await user.click(screen.getByTestId('ecommerce-confirm-confirm'));
    await waitFor(() => expect(sentKeys(fetchMock, 'PATCH')).toHaveLength(2));

    const [k1, k2] = sentKeys(fetchMock, 'PATCH');
    expect(k2).not.toBe(k1);
  });

  it('AC-4: a genuine second +10 (after success) gets a fresh key — not swallowed as a replay', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok({ variantId: 'v-1', currentStock: 15 }));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderDialog();

    await fill(user, '10');
    await user.click(screen.getByTestId('ecommerce-confirm-confirm')); // success → reset()
    await waitFor(() => expect(sentKeys(fetchMock, 'PATCH')).toHaveLength(1));

    await fill(user, '10'); // deliberately the same delta again
    await user.click(screen.getByTestId('ecommerce-confirm-confirm'));
    await waitFor(() => expect(sentKeys(fetchMock, 'PATCH')).toHaveLength(2));

    const [k1, k2] = sentKeys(fetchMock, 'PATCH');
    expect(k1).toBeTruthy();
    expect(k2).not.toBe(k1); // both +10s go through — the second is not deduped
  });
});

// ===========================================================================
// issueCoupons — key held in CouponIssueDialog
// ===========================================================================
describe('issueCoupons idempotency-key lifetime (TASK-PC-FE-252)', () => {
  function renderDialog() {
    return render(
      <CouponIssueDialog
        open
        promotionId="promo-1"
        onClose={vi.fn()}
        onIssued={vi.fn()}
      />,
      { wrapper: wrapper() },
    );
  }

  it('AC-2: retrying the same batch after a failure reuses the key', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(unavailable())
      .mockResolvedValue(ok({ issuedCount: 2 }, 201));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderDialog();

    await user.type(screen.getByTestId('coupon-issue-userids'), 'u-1\nu-2');
    await user.click(screen.getByTestId('ecommerce-confirm-confirm')); // fails
    await waitFor(() => expect(sentKeys(fetchMock, 'POST')).toHaveLength(1));
    await user.click(screen.getByTestId('ecommerce-confirm-confirm')); // retry
    await waitFor(() => expect(sentKeys(fetchMock, 'POST')).toHaveLength(2));

    const [k1, k2] = sentKeys(fetchMock, 'POST');
    expect(k1).toBeTruthy();
    expect(k2).toBe(k1);
  });

  it('AC-3: editing the userId list before a retry sends a NEW key', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(unavailable())
      .mockResolvedValue(ok({ issuedCount: 2 }, 201));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderDialog();

    await user.type(screen.getByTestId('coupon-issue-userids'), 'u-1');
    await user.click(screen.getByTestId('ecommerce-confirm-confirm')); // fails
    await waitFor(() => expect(sentKeys(fetchMock, 'POST')).toHaveLength(1));

    await user.type(screen.getByTestId('coupon-issue-userids'), '\nu-2'); // different batch
    await user.click(screen.getByTestId('ecommerce-confirm-confirm'));
    await waitFor(() => expect(sentKeys(fetchMock, 'POST')).toHaveLength(2));

    const [k1, k2] = sentKeys(fetchMock, 'POST');
    expect(k2).not.toBe(k1);
  });

  it('AC-4: re-issuing the SAME batch after success gets a fresh key (dialog stays open)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok({ issuedCount: 2 }, 201));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderDialog();

    await user.type(screen.getByTestId('coupon-issue-userids'), 'u-1\nu-2');
    await user.click(screen.getByTestId('ecommerce-confirm-confirm')); // success
    await waitFor(() => expect(sentKeys(fetchMock, 'POST')).toHaveLength(1));
    // The success notice is shown and the dialog stays open (no reset of the list).
    await waitFor(() => expect(screen.getByTestId('coupon-issue-success')).toBeInTheDocument());

    await user.click(screen.getByTestId('ecommerce-confirm-confirm')); // deliberate re-issue
    await waitFor(() => expect(sentKeys(fetchMock, 'POST')).toHaveLength(2));

    const [k1, k2] = sentKeys(fetchMock, 'POST');
    expect(k1).toBeTruthy();
    expect(k2).not.toBe(k1);
  });
});
