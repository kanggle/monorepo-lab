import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';

const {
  mockLoginAction,
  mockLogoutAction,
  mockGetUserFromToken,
  mockSaveTokens,
  mockClearTokens,
  mockGetStoredRefreshToken,
} = vi.hoisted(() => ({
  mockLoginAction: vi.fn(),
  mockLogoutAction: vi.fn(),
  mockGetUserFromToken: vi.fn(),
  mockSaveTokens: vi.fn(),
  mockClearTokens: vi.fn(),
  mockGetStoredRefreshToken: vi.fn(),
}));

vi.mock('@/features/auth/api/auth-actions', () => ({
  login: mockLoginAction,
  logout: mockLogoutAction,
  signup: vi.fn(),
}));

vi.mock('@repo/api-client', () => ({
  getUserFromToken: () => mockGetUserFromToken(),
  saveTokens: (...args: unknown[]) => mockSaveTokens(...args),
  clearTokens: () => mockClearTokens(),
  getStoredRefreshToken: () => mockGetStoredRefreshToken(),
}));

import { AuthProvider, useAuth } from '@/features/auth/model/auth-context';
import { CartProvider, useCart } from '@/features/cart/model/cart-context';

const ITEM_A = {
  productId: 'p1',
  variantId: 'v1',
  productName: '상품A',
  optionName: '옵션1',
  price: 10000,
};

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

describe('로그아웃 카트 통합 테스트', () => {
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

    mockLoginAction.mockResolvedValue({
      accessToken: 'fake.access.token',
      refreshToken: 'fake-refresh-token',
    });
    mockLogoutAction.mockResolvedValue(undefined);
    mockSaveTokens.mockImplementation(() => {});
    mockClearTokens.mockImplementation(() => {});
    mockGetStoredRefreshToken.mockReturnValue('fake-refresh-token');

    // 초기 마운트 시: 로그인 상태
    mockGetUserFromToken.mockReturnValue({
      userId: 'u1',
      email: 'test@example.com',
      name: '테스터',
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('시나리오 A: logout 호출 시 itemCount=0 이고 localStorage cart가 삭제된다', async () => {
    const user = userEvent.setup();

    render(
      <Providers>
        <TestApp />
      </Providers>,
    );

    // AuthProvider 마운트 완료 대기 (isLoading false → isAuthenticated true)
    await waitFor(() => {
      expect(screen.getByTestId('count').textContent).toBe('0');
    });

    // 상품 추가
    await user.click(screen.getByText('addA'));

    await waitFor(() => {
      expect(screen.getByTestId('count').textContent).toBe('1');
    });

    // localStorage에 cart 저장 확인
    expect(storage['cart']).toBeDefined();

    // 로그아웃: clearTokens 호출 후 getUserFromToken은 null 반환
    mockGetUserFromToken.mockReturnValue(null);

    await act(async () => {
      await user.click(screen.getByText('logout'));
    });

    // itemCount=0 확인
    await waitFor(() => {
      expect(screen.getByTestId('count').textContent).toBe('0');
    });

    // localStorage cart=null(undefined) 확인
    expect(storage['cart']).toBeUndefined();
  });

  it('시나리오 B: 로그아웃 후 provider 재마운트 시에도 items=[] & localStorage cart=null 유지', async () => {
    const user = userEvent.setup();

    const { unmount } = render(
      <Providers>
        <TestApp />
      </Providers>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('count').textContent).toBe('0');
    });

    // 상품 추가
    await user.click(screen.getByText('addA'));
    await waitFor(() => {
      expect(screen.getByTestId('count').textContent).toBe('1');
    });

    // 로그아웃
    mockGetUserFromToken.mockReturnValue(null);

    await act(async () => {
      await user.click(screen.getByText('logout'));
    });

    await waitFor(() => {
      expect(screen.getByTestId('count').textContent).toBe('0');
    });
    expect(storage['cart']).toBeUndefined();

    // 언마운트 후 재마운트 (새로고침 시뮬레이션)
    unmount();

    // 재마운트 시에도 getUserFromToken은 null (비로그인)
    render(
      <Providers>
        <TestApp />
      </Providers>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('count').textContent).toBe('0');
    });

    // localStorage.cart=null 유지
    expect(storage['cart']).toBeUndefined();
  });
});
