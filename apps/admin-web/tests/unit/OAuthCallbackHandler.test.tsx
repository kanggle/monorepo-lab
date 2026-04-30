import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, waitFor } from '@testing-library/react';

const replaceMock = vi.fn();
const refreshMock = vi.fn();
let searchString = '';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: replaceMock, refresh: refreshMock }),
  useSearchParams: () => new URLSearchParams(searchString),
}));

import { OAuthCallbackHandler } from '@/features/auth/components/OAuthCallbackHandler';

describe('OAuthCallbackHandler', () => {
  beforeEach(() => {
    replaceMock.mockReset();
    refreshMock.mockReset();
    searchString = '';
  });

  it('POSTs to /api/auth/oauth/callback and redirects to /accounts on success', async () => {
    searchString = 'provider=google&code=abc123&state=xyz789';
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ ok: true, isNewAccount: false }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    render(<OAuthCallbackHandler />);

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/auth/oauth/callback');
    expect((init as RequestInit).method).toBe('POST');
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body).toMatchObject({
      provider: 'google',
      code: 'abc123',
      state: 'xyz789',
    });
    expect(body.redirectUri).toContain('/oauth/callback');

    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith('/accounts'));
  });

  it('treats isNewAccount=true the same as a normal login (still redirects to /accounts)', async () => {
    searchString = 'provider=kakao&code=abc&state=xyz';
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ ok: true, isNewAccount: true }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    render(<OAuthCallbackHandler />);

    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith('/accounts'));
  });

  it('redirects to /login?error=INVALID_STATE on 401 INVALID_STATE', async () => {
    searchString = 'provider=google&code=abc&state=xyz';
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ code: 'INVALID_STATE', message: 'expired' }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    render(<OAuthCallbackHandler />);

    await waitFor(() =>
      expect(replaceMock).toHaveBeenCalledWith('/login?error=INVALID_STATE'),
    );
  });

  it('redirects to /login?error=EMAIL_REQUIRED on 422 EMAIL_REQUIRED', async () => {
    searchString = 'provider=kakao&code=abc&state=xyz';
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ code: 'EMAIL_REQUIRED', message: 'no email' }), {
        status: 422,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    render(<OAuthCallbackHandler />);

    await waitFor(() =>
      expect(replaceMock).toHaveBeenCalledWith('/login?error=EMAIL_REQUIRED'),
    );
  });

  it('redirects to /login?error=ACCOUNT_LOCKED on 403 ACCOUNT_LOCKED', async () => {
    searchString = 'provider=google&code=abc&state=xyz';
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ code: 'ACCOUNT_LOCKED', message: 'locked' }), {
        status: 403,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    render(<OAuthCallbackHandler />);

    await waitFor(() =>
      expect(replaceMock).toHaveBeenCalledWith('/login?error=ACCOUNT_LOCKED'),
    );
  });

  it('redirects to /login?error=ACCOUNT_DORMANT on 403 ACCOUNT_DORMANT', async () => {
    searchString = 'provider=google&code=abc&state=xyz';
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ code: 'ACCOUNT_DORMANT', message: 'dormant' }), {
        status: 403,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    render(<OAuthCallbackHandler />);

    await waitFor(() =>
      expect(replaceMock).toHaveBeenCalledWith('/login?error=ACCOUNT_DORMANT'),
    );
  });

  it('redirects to /login?error=ACCOUNT_DELETED on 403 ACCOUNT_DELETED', async () => {
    searchString = 'provider=kakao&code=abc&state=xyz';
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ code: 'ACCOUNT_DELETED', message: 'deleted' }), {
        status: 403,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    render(<OAuthCallbackHandler />);

    await waitFor(() =>
      expect(replaceMock).toHaveBeenCalledWith('/login?error=ACCOUNT_DELETED'),
    );
  });

  it('redirects to /login?error=INVALID_STATE when query params are missing', async () => {
    searchString = 'provider=google';
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    render(<OAuthCallbackHandler />);

    await waitFor(() =>
      expect(replaceMock).toHaveBeenCalledWith('/login?error=INVALID_STATE'),
    );
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('forwards provider-side error param (user denied consent)', async () => {
    searchString = 'error=access_denied';
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    render(<OAuthCallbackHandler />);

    await waitFor(() =>
      expect(replaceMock).toHaveBeenCalledWith('/login?error=ACCESS_DENIED'),
    );
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('redirects to /login?error=INVALID_STATE on unsupported provider', async () => {
    searchString = 'provider=apple&code=abc&state=xyz';
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    render(<OAuthCallbackHandler />);

    await waitFor(() =>
      expect(replaceMock).toHaveBeenCalledWith('/login?error=INVALID_STATE'),
    );
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
