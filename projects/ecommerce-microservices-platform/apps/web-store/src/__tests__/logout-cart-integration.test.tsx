import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';
import { useSession, signOut } from 'next-auth/react';
import { AuthProvider, useAuth } from '@/features/auth/model/auth-context';
import { CartProvider, useCart } from '@/features/cart/model/cart-context';

const mockUseSession = vi.mocked(useSession);
const mockSignOut = vi.mocked(signOut);

const ITEM_A = {
  productId: 'p1',
  variantId: 'v1',
  productName: '상품A',
  optionName: '옵션1',
  price: 10000,
};

function authenticatedSession() {
  return {
    data: {
      accountId: 'u1',
      tenantId: 'ecommerce',
      roles: ['CUSTOMER'],
      accessToken: 'fake.access.token',
      user: { email: 'test@example.com', name: '테스터' },
      expires: '2099-01-01T00:00:00Z',
    },
    status: 'authenticated' as const,
    update: vi.fn(),
  } as unknown as ReturnType<typeof useSession>;
}

function unauthenticatedSession() {
  return {
    data: null,
    status: 'unauthenticated' as const,
    update: vi.fn(),
  } as unknown as ReturnType<typeof useSession>;
}

function TestApp() {
  const { logout } = useAuth();
  const { itemCount, addItem } = useCart();
  return (
    <div>
      <span data-testid="count">{itemCount}</span>
      <button onClick={() => addItem(ITEM_A)}>addA</button>
      <button onClick={() => logout()}>logout</button>
    </div>
  );
}

function Providers({ children }: { children: React.ReactNode }) {
  return (
    <AuthProvider>
      <CartProvider>{children}</CartProvider>
    </AuthProvider>
  );
}

describe('로그아웃 카트 통합 (NextAuth)', () => {
  let storage: Record<string, string>;

  beforeEach(() => {
    storage = {};
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation((key) => storage[key] ?? null);
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation((key, value) => {
      storage[key] = String(value);
    });
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation((key) => {
      delete storage[key];
    });

    mockSignOut.mockResolvedValue({ url: '/' } as unknown as ReturnType<typeof signOut> extends Promise<infer R> ? R : never);
    mockUseSession.mockReturnValue(authenticatedSession());

    // RP-initiated logout: logout() fetches the end_session URL then navigates.
    // No id_token in this unit context → { url: null } → local-only fallback '/'.
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, json: async () => ({ url: null }) }),
    );
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { set href(_v: string) {} },
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('logout() 호출 시 itemCount=0 + localStorage cart 삭제 + signOut 호출', async () => {
    const user = userEvent.setup();

    render(
      <Providers>
        <TestApp />
      </Providers>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('count').textContent).toBe('0');
    });

    await user.click(screen.getByText('addA'));
    await waitFor(() => {
      expect(screen.getByTestId('count').textContent).toBe('1');
    });
    expect(storage['cart']).toBeDefined();

    // logout()은 signOut() 을 호출하기 전에 즉시 cart 를 정리한다. (F2: 토큰은
    // server-only 이므로 클라이언트 token-bridge 정리 단계는 더 이상 없다.)
    await act(async () => {
      await user.click(screen.getByText('logout'));
    });

    expect(mockSignOut).toHaveBeenCalledWith({ redirect: false });
    expect(storage['cart']).toBeUndefined();
  });

  it('useSession 이 unauthenticated 로 바뀌면 cart 가 비워진다', async () => {
    const user = userEvent.setup();

    const { rerender } = render(
      <Providers>
        <TestApp />
      </Providers>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('count').textContent).toBe('0');
    });

    await user.click(screen.getByText('addA'));
    await waitFor(() => {
      expect(screen.getByTestId('count').textContent).toBe('1');
    });

    // 세션이 만료되거나 signOut 결과가 반영된 시뮬레이션
    mockUseSession.mockReturnValue(unauthenticatedSession());
    rerender(
      <Providers>
        <TestApp />
      </Providers>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('count').textContent).toBe('0');
    });
    expect(storage['cart']).toBeUndefined();
  });
});
