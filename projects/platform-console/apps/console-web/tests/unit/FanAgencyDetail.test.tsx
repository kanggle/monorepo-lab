import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AgencyDetail } from '@/features/fan-directory/components/AgencyDetail';
import { ApiError } from '@/shared/api/errors';

/**
 * TASK-MONO-751 — the agency detail's store-seller link (AC-2) and its honest «unwired»
 * state. artist-service refuses every link with `503 STORE_SELLER_LOOKUP_UNAVAILABLE` until
 * TASK-MONO-759 wires the store lookup; the field must stay visible and a refused attempt
 * must say nothing was saved. A definite store answer (`422 STORE_SELLER_NOT_FOUND`) is the
 * validation error AC-2 asks to show.
 */
const patch = vi.fn();
vi.mock('@/shared/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    patch: (...a: unknown[]) => patch(...a),
  },
}));
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn(), back: vi.fn() }),
  usePathname: () => '/fan/agencies/ag-1',
}));

const AGENCY = {
  id: 'ag-1',
  tenantId: 'fan-platform',
  name: 'Aurora Entertainment',
  status: 'ACTIVE',
  storeSellerId: null as string | null,
  createdAt: '2026-10-03T00:00:00Z',
  updatedAt: '2026-10-03T00:00:00Z',
};

function renderDetail(agency = AGENCY) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnMount: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <AgencyDetail agency={agency} />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  cleanup();
  patch.mockReset();
});

describe('AgencyDetail — store-seller link', () => {
  it('shows the field AND says up front that new links are refused for now (not hidden)', () => {
    renderDetail();
    expect(screen.getByTestId('fan-agency-seller-input')).toBeInTheDocument();
    expect(screen.getByTestId('fan-agency-seller-unwired-note')).toHaveTextContent('TASK-MONO-759');
    expect(screen.getByTestId('fan-agency-seller-current')).toHaveTextContent('연결 없음');
  });

  it('503 STORE_SELLER_LOOKUP_UNAVAILABLE → its own «nothing was saved» state, not a generic error', async () => {
    patch.mockRejectedValue(new ApiError(503, 'STORE_SELLER_LOOKUP_UNAVAILABLE', 'fan unavailable'));
    renderDetail();
    fireEvent.change(screen.getByTestId('fan-agency-seller-input'), { target: { value: 'acme-goods' } });
    fireEvent.click(screen.getByTestId('fan-agency-seller-link'));
    const state = await screen.findByTestId('fan-agency-seller-lookup-unavailable');
    expect(state).toHaveTextContent('아무것도 저장되지 않았습니다');
    expect(screen.queryByTestId('fan-agency-seller-error')).toBeNull();
    expect(patch).toHaveBeenCalledWith('/api/fan/agencies/ag-1/store-seller', { storeSellerId: 'acme-goods' });
  });

  it('422 STORE_SELLER_NOT_FOUND → the validation message (AC-2)', async () => {
    patch.mockRejectedValue(new ApiError(422, 'STORE_SELLER_NOT_FOUND', 'no seller'));
    renderDetail();
    fireEvent.change(screen.getByTestId('fan-agency-seller-input'), { target: { value: 'nope' } });
    fireEvent.click(screen.getByTestId('fan-agency-seller-link'));
    expect(await screen.findByTestId('fan-agency-seller-error')).toHaveTextContent('셀러가 없습니다');
    expect(screen.queryByTestId('fan-agency-seller-lookup-unavailable')).toBeNull();
  });

  it('a linked agency offers «연결 해제», which sends storeSellerId:null', async () => {
    patch.mockResolvedValue({ ...AGENCY, storeSellerId: null });
    renderDetail({ ...AGENCY, storeSellerId: 'acme-goods' });
    fireEvent.click(screen.getByTestId('fan-agency-seller-clear'));
    await waitFor(() =>
      expect(patch).toHaveBeenCalledWith('/api/fan/agencies/ag-1/store-seller', { storeSellerId: null }),
    );
  });

  it('an ARCHIVED agency shows the link read-only and no rename / archive controls', () => {
    renderDetail({ ...AGENCY, status: 'ARCHIVED' });
    expect(screen.queryByTestId('fan-agency-seller-input')).toBeNull();
    expect(screen.queryByTestId('fan-agency-rename-input')).toBeNull();
    expect(screen.queryByTestId('fan-agency-archive')).toBeNull();
    expect(screen.getByTestId('fan-agency-archived-note')).toBeInTheDocument();
  });

  it('409 AGENCY_NAME_CONFLICT on rename → the agency-specific message (not the IAM group one)', async () => {
    patch.mockRejectedValue(new ApiError(409, 'AGENCY_NAME_CONFLICT', 'dup'));
    renderDetail();
    fireEvent.change(screen.getByTestId('fan-agency-rename-input'), { target: { value: 'Other' } });
    fireEvent.click(screen.getByTestId('fan-agency-rename-submit'));
    expect(await screen.findByTestId('fan-agency-rename-error')).toHaveTextContent('같은 이름의 소속사');
  });
});
