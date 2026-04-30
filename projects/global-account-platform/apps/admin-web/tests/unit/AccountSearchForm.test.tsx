import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

vi.mock('next/link', () => ({ default: ({ children, href }: { children: React.ReactNode; href: string }) => <a href={href}>{children}</a> }));

import { AccountSearchForm } from '@/features/accounts/components/AccountSearchForm';

function wrapper(ui: React.ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={qc}>{ui}</QueryClientProvider>;
}

describe('AccountSearchForm', () => {
  it('shows validation error for invalid email', async () => {
    const user = userEvent.setup();
    render(wrapper(<AccountSearchForm />));
    await user.type(screen.getByLabelText('이메일'), 'not-an-email');
    await user.click(screen.getByRole('button', { name: '검색' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });

  it('renders results table on successful search', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          content: [
            { id: 'acc-1', email: 'u@x.com', status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z' },
          ],
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    const user = userEvent.setup();
    render(wrapper(<AccountSearchForm />));
    await user.type(screen.getByLabelText('이메일'), 'u@x.com');
    await user.click(screen.getByRole('button', { name: '검색' }));

    await waitFor(() => expect(screen.getByText('u@x.com')).toBeInTheDocument());
    expect(screen.getByText('ACTIVE')).toBeInTheDocument();
  });
});
