import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ArtistCreateForm } from '@/features/fan-directory/components/ArtistCreateForm';

/**
 * TASK-PC-FE-334 AC-4 — the 계정 ID (accountId) input stays; the new
 * email-lookup ASSIST (shared `<AccountEmailLookup>`) fills it, and the
 * existing submit path still forwards that id unchanged.
 */

const get = vi.fn();
const post = vi.fn();
vi.mock('@/shared/api/client', () => ({
  apiClient: {
    get: (...a: unknown[]) => get(...(a as [string])),
    post: (...a: unknown[]) => post(...a),
  },
}));
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), refresh: vi.fn() }),
}));

function agencyOptionsPage() {
  return { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 };
}

function renderForm() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <ArtistCreateForm />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  cleanup();
  get.mockReset();
  post.mockReset();
  get.mockImplementation((path: string) => {
    if (path.includes('/api/fan/agencies')) return Promise.resolve(agencyOptionsPage());
    if (path.includes('/api/accounts')) {
      return Promise.resolve({
        content: [{ id: 'acc-9', email: 'a@x.co', status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z' }],
        totalElements: 1,
        page: 0,
        size: 20,
        totalPages: 1,
      });
    }
    return Promise.reject(new Error(`unexpected GET ${path}`));
  });
  post.mockResolvedValue({
    id: 'artist-1',
    tenantId: null,
    accountId: 'acc-9',
    artistType: 'SOLO',
    status: 'DRAFT',
    stageName: 'Nova',
    realName: null,
    debutDate: null,
    agency: null,
    agencyId: null,
    bio: null,
    profileImageRef: null,
    createdAt: null,
    updatedAt: null,
    publishedAt: null,
    archivedAt: null,
  });
});

describe('ArtistCreateForm — email lookup fills accountId (AC-4)', () => {
  it('picking a found account fills the existing 계정 ID input, and submit forwards that id unchanged', async () => {
    const user = userEvent.setup();
    renderForm();

    await user.type(
      screen.getByTestId('fan-artist-create-account-lookup-email-input'),
      'a@x.co',
    );
    await user.click(screen.getByTestId('fan-artist-create-account-lookup-search'));
    await user.click(await screen.findByTestId('fan-artist-create-account-lookup-result-acc-9'));

    expect(screen.getByTestId('fan-artist-create-account')).toHaveValue('acc-9');

    await user.type(screen.getByTestId('fan-artist-create-stage'), 'Nova');
    await user.click(screen.getByTestId('fan-artist-create-submit'));

    await screen.findByTestId('fan-artist-create-form'); // still mounted (no crash)
    expect(post).toHaveBeenCalledWith(
      '/api/fan/artists',
      expect.objectContaining({ accountId: 'acc-9', stageName: 'Nova' }),
    );
  });
});
