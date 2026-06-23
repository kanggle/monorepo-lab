import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { PromotionForm } from '@/features/ecommerce-ops/components/PromotionForm';

/**
 * TASK-PC-FE-127 — clicking ANYWHERE in the 시작일/종료일 `<input type="date">`
 * fields (not just the small calendar glyph) opens the native date picker, via
 * the shared `showPickerOnClick` handler.
 *
 * jsdom does not implement `HTMLInputElement.showPicker`, so the handler's
 * feature-detect skips it by default — the test installs a spy on the element to
 * assert the click path invokes it.
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

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('PromotionForm date fields open the picker on click (TASK-PC-FE-127)', () => {
  it('clicking the 시작일 / 종료일 input area calls showPicker', async () => {
    const user = userEvent.setup();
    render(<PromotionForm />, { wrapper: wrapper() });

    const start = screen.getByTestId('promotion-form-start-date') as HTMLInputElement;
    const end = screen.getByTestId('promotion-form-end-date') as HTMLInputElement;

    const startPicker = vi.fn();
    const endPicker = vi.fn();
    // jsdom has no showPicker — install spies the handler will feature-detect + call.
    (start as unknown as { showPicker: () => void }).showPicker = startPicker;
    (end as unknown as { showPicker: () => void }).showPicker = endPicker;

    await user.click(start);
    expect(startPicker).toHaveBeenCalledTimes(1);

    await user.click(end);
    expect(endPicker).toHaveBeenCalledTimes(1);
  });

  it('does not throw when showPicker is unsupported (feature-detect)', async () => {
    const user = userEvent.setup();
    render(<PromotionForm />, { wrapper: wrapper() });
    const start = screen.getByTestId('promotion-form-start-date');
    // No showPicker installed (jsdom default) → handler must be a no-op, not throw.
    await expect(user.click(start)).resolves.not.toThrow();
  });
});
