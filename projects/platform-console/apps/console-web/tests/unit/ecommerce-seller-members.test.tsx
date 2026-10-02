import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  render,
  screen,
  fireEvent,
  waitFor,
  cleanup,
} from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { SellerMembers } from '@/features/ecommerce-ops/components/SellerMembers';
import {
  InviteSellerMemberBodySchema,
  invitationStateLabel,
} from '@/features/ecommerce-ops/api/seller-types';

/**
 * TASK-MONO-752 — the seller detail screen's members section (ADR-MONO-079 D5):
 * member list + invitations + invite-by-email, the one-time token shown once.
 */

const MEMBERS = {
  members: [
    {
      sellerId: 'acme-corp',
      accountId: 'acct-0001',
      role: 'MEMBER',
      status: 'ACTIVE',
      joinedAt: '2026-10-01T00:00:00Z',
    },
    {
      sellerId: 'acme-corp',
      accountId: 'acct-0002',
      role: 'MEMBER',
      status: 'REVOKED',
      joinedAt: '2026-09-01T00:00:00Z',
    },
  ],
  invitations: [
    {
      invitationId: 'inv-1',
      email: 'pending@example.com',
      status: 'PENDING',
      expired: false,
      expiresAt: '2026-10-10T00:00:00Z',
      createdAt: '2026-10-03T00:00:00Z',
      acceptedAt: null,
    },
    {
      invitationId: 'inv-2',
      email: 'late@example.com',
      status: 'PENDING',
      expired: true,
      expiresAt: '2026-09-10T00:00:00Z',
      createdAt: '2026-09-03T00:00:00Z',
      acceptedAt: null,
    },
  ],
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function stubFetch(postResponse: () => Response) {
  const fetchMock = vi.fn(async (input: unknown, init?: RequestInit) => {
    const method = (init?.method ?? 'GET').toUpperCase();
    if (method === 'POST') return postResponse();
    expect(String(input)).toContain('/api/ecommerce/sellers/acme-corp/members');
    return jsonResponse(MEMBERS);
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function renderMembers(status = 'ACTIVE') {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
  return render(<SellerMembers sellerId="acme-corp" sellerStatus={status} />, {
    wrapper,
  });
}

beforeEach(() => vi.unstubAllGlobals());
afterEach(() => cleanup());

describe('SellerMembers — list', () => {
  it('renders members with their status and invitations with 대기 / 만료', async () => {
    stubFetch(() => new Response(null, { status: 500 }));
    renderMembers();

    await waitFor(() =>
      expect(screen.getAllByTestId('seller-member-row')).toHaveLength(2),
    );
    const statuses = screen
      .getAllByTestId('seller-member-status')
      .map((n) => n.textContent);
    expect(statuses).toEqual(['활성', '회수됨']);
    const states = screen
      .getAllByTestId('seller-invitation-state')
      .map((n) => n.textContent);
    expect(states).toEqual(['대기', '만료']);
    // the list never shows a token
    expect(screen.queryByTestId('seller-invite-token')).toBeNull();
  });

  it('a non-ACTIVE seller offers no invite form', async () => {
    stubFetch(() => new Response(null, { status: 500 }));
    renderMembers('SUSPENDED');
    expect(screen.queryByTestId('seller-invite-form')).toBeNull();
    expect(screen.getByTestId('seller-invite-unavailable')).toBeInTheDocument();
  });
});

describe('SellerMembers — invite', () => {
  it('POSTs { email } to the invitations proxy and shows the one-time token', async () => {
    const fetchMock = stubFetch(() =>
      jsonResponse(
        {
          invitationId: 'inv-9',
          email: 'new@example.com',
          expiresAt: '2026-10-10T00:00:00Z',
          token: 'ONE-TIME-TOKEN',
        },
        201,
      ),
    );
    renderMembers();

    fireEvent.change(screen.getByTestId('seller-invite-email'), {
      target: { value: ' New@Example.com ' },
    });
    fireEvent.click(screen.getByTestId('seller-invite-submit'));

    await waitFor(() =>
      expect(screen.getByTestId('seller-invite-token').textContent).toBe(
        'ONE-TIME-TOKEN',
      ),
    );
    const post = fetchMock.mock.calls.find(
      ([, init]) => (init?.method ?? 'GET').toUpperCase() === 'POST',
    );
    expect(post).toBeDefined();
    expect(String(post![0])).toContain(
      '/api/ecommerce/sellers/acme-corp/invitations',
    );
    expect(JSON.parse(String(post![1]!.body))).toEqual({
      email: 'New@Example.com',
    });
  });

  it('409 SELLER_NOT_ACTIVE → inline message, no token', async () => {
    stubFetch(() =>
      jsonResponse({ code: 'SELLER_NOT_ACTIVE', message: 'x' }, 409),
    );
    renderMembers();

    fireEvent.change(screen.getByTestId('seller-invite-email'), {
      target: { value: 'a@b.co' },
    });
    fireEvent.click(screen.getByTestId('seller-invite-submit'));

    await waitFor(() =>
      expect(screen.getByTestId('seller-invite-error')).toBeInTheDocument(),
    );
    expect(screen.queryByTestId('seller-invite-token')).toBeNull();
  });

  it('an invalid email is refused client-side (no POST)', async () => {
    const fetchMock = stubFetch(() => new Response(null, { status: 500 }));
    renderMembers();

    fireEvent.change(screen.getByTestId('seller-invite-email'), {
      target: { value: 'not-an-email' },
    });
    fireEvent.submit(screen.getByTestId('seller-invite-form'));

    await waitFor(() =>
      expect(screen.getByTestId('seller-invite-error')).toBeInTheDocument(),
    );
    expect(
      fetchMock.mock.calls.some(
        ([, init]) => (init?.method ?? 'GET').toUpperCase() === 'POST',
      ),
    ).toBe(false);
  });
});

describe('seller-types — members helpers', () => {
  it('InviteSellerMemberBodySchema trims and validates', () => {
    expect(InviteSellerMemberBodySchema.parse({ email: ' a@b.co ' })).toEqual({
      email: 'a@b.co',
    });
    expect(InviteSellerMemberBodySchema.safeParse({ email: '' }).success).toBe(
      false,
    );
  });

  it('invitationStateLabel', () => {
    const base = MEMBERS.invitations[0];
    expect(invitationStateLabel({ ...base, status: 'ACCEPTED' })).toBe('수락됨');
    expect(invitationStateLabel({ ...base, expired: true })).toBe('만료');
    expect(invitationStateLabel(base)).toBe('대기');
  });
});
