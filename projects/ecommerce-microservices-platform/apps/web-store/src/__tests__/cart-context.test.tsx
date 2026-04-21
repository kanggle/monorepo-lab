import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

const mockAuthState = { isAuthenticated: true, isLoading: false };

vi.mock('@/shared/lib/auth-context', () => ({
  useAuth: () => mockAuthState,
}));

import { CartProvider, useCart } from '@/features/cart/model/cart-context';

const ITEM_A = {
  productId: 'p1',
  variantId: 'v1',
  productName: '상품A',
  optionName: '옵션1',
  price: 10000,
};

const ITEM_B = {
  productId: 'p2',
  variantId: 'v2',
  productName: '상품B',
  optionName: '옵션2',
  price: 20000,
};

function TestConsumer() {
  const { items, totalAmount, itemCount, addItem, removeItem, updateQuantity, clearCart } =
    useCart();
  return (
    <div>
      <span data-testid="count">{itemCount}</span>
      <span data-testid="total">{totalAmount}</span>
      <span data-testid="items">{JSON.stringify(items)}</span>
      <button onClick={() => addItem(ITEM_A)}>addA</button>
      <button onClick={() => addItem(ITEM_B, 2)}>addB2</button>
      <button onClick={() => removeItem('p1', 'v1')}>removeA</button>
      <button onClick={() => updateQuantity('p1', 'v1', 3)}>updateA3</button>
      <button onClick={() => updateQuantity('p1', 'v1', 0)}>updateA0</button>
      <button onClick={() => clearCart()}>clear</button>
    </div>
  );
}

describe('CartContext', () => {
  let storage: Record<string, string>;

  beforeEach(() => {
    storage = {};
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation((key) => storage[key] ?? null);
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation((key, value) => {
      storage[key] = value;
    });
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation((key) => {
      delete storage[key];
    });
    mockAuthState.isAuthenticated = true;
    mockAuthState.isLoading = false;
  });

  it('초기 상태에서 장바구니가 비어있다', async () => {
    render(
      <CartProvider>
        <TestConsumer />
      </CartProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('count').textContent).toBe('0');
    });
    expect(screen.getByTestId('total').textContent).toBe('0');
  });

  it('addItem으로 상품을 추가한다', async () => {
    const user = userEvent.setup();
    render(
      <CartProvider>
        <TestConsumer />
      </CartProvider>,
    );

    await user.click(screen.getByText('addA'));

    await waitFor(() => {
      expect(screen.getByTestId('count').textContent).toBe('1');
    });
    expect(screen.getByTestId('total').textContent).toBe('10000');
  });

  it('동일 상품 추가 시 수량이 증가한다', async () => {
    const user = userEvent.setup();
    render(
      <CartProvider>
        <TestConsumer />
      </CartProvider>,
    );

    await user.click(screen.getByText('addA'));
    await user.click(screen.getByText('addA'));

    await waitFor(() => {
      expect(screen.getByTestId('count').textContent).toBe('2');
    });
    expect(screen.getByTestId('total').textContent).toBe('20000');
  });

  it('수량을 지정하여 추가할 수 있다', async () => {
    const user = userEvent.setup();
    render(
      <CartProvider>
        <TestConsumer />
      </CartProvider>,
    );

    await user.click(screen.getByText('addB2'));

    await waitFor(() => {
      expect(screen.getByTestId('count').textContent).toBe('2');
    });
    expect(screen.getByTestId('total').textContent).toBe('40000');
  });

  it('removeItem으로 상품을 삭제한다', async () => {
    const user = userEvent.setup();
    render(
      <CartProvider>
        <TestConsumer />
      </CartProvider>,
    );

    await user.click(screen.getByText('addA'));
    await user.click(screen.getByText('removeA'));

    await waitFor(() => {
      expect(screen.getByTestId('count').textContent).toBe('0');
    });
  });

  it('updateQuantity로 수량을 변경한다', async () => {
    const user = userEvent.setup();
    render(
      <CartProvider>
        <TestConsumer />
      </CartProvider>,
    );

    await user.click(screen.getByText('addA'));
    await user.click(screen.getByText('updateA3'));

    await waitFor(() => {
      expect(screen.getByTestId('count').textContent).toBe('3');
    });
    expect(screen.getByTestId('total').textContent).toBe('30000');
  });

  it('수량 0 이하로 변경 시 삭제된다', async () => {
    const user = userEvent.setup();
    render(
      <CartProvider>
        <TestConsumer />
      </CartProvider>,
    );

    await user.click(screen.getByText('addA'));
    await user.click(screen.getByText('updateA0'));

    await waitFor(() => {
      expect(screen.getByTestId('count').textContent).toBe('0');
    });
  });

  it('clearCart으로 전체 삭제한다', async () => {
    const user = userEvent.setup();
    render(
      <CartProvider>
        <TestConsumer />
      </CartProvider>,
    );

    await user.click(screen.getByText('addA'));
    await user.click(screen.getByText('addB2'));
    await user.click(screen.getByText('clear'));

    await waitFor(() => {
      expect(screen.getByTestId('count').textContent).toBe('0');
    });
    expect(screen.getByTestId('total').textContent).toBe('0');
  });

  it('여러 상품의 합계가 정확하다', async () => {
    const user = userEvent.setup();
    render(
      <CartProvider>
        <TestConsumer />
      </CartProvider>,
    );

    await user.click(screen.getByText('addA')); // 10000 x 1
    await user.click(screen.getByText('addB2')); // 20000 x 2

    await waitFor(() => {
      expect(screen.getByTestId('count').textContent).toBe('3');
    });
    expect(screen.getByTestId('total').textContent).toBe('50000');
  });

  it('localStorage에서 장바구니를 복원한다', async () => {
    storage['cart'] = JSON.stringify([{ ...ITEM_A, quantity: 2 }]);

    render(
      <CartProvider>
        <TestConsumer />
      </CartProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('count').textContent).toBe('2');
    });
    expect(screen.getByTestId('total').textContent).toBe('20000');
  });

  it('잘못된 localStorage 데이터는 빈 장바구니로 초기화한다', async () => {
    storage['cart'] = 'invalid json{{{';

    render(
      <CartProvider>
        <TestConsumer />
      </CartProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('count').textContent).toBe('0');
    });
  });

  it('localStorage 로드 실패 시 console.warn 로그가 남는다', async () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    storage['cart'] = 'invalid json{{{';

    render(
      <CartProvider>
        <TestConsumer />
      </CartProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('count').textContent).toBe('0');
    });
    expect(warnSpy).toHaveBeenCalledWith('Cart load failed', expect.any(Error));
    warnSpy.mockRestore();
  });

  it('localStorage 저장 실패 시 console.warn 로그가 남는다', async () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('QuotaExceededError');
    });

    const user = userEvent.setup();
    render(
      <CartProvider>
        <TestConsumer />
      </CartProvider>,
    );

    await user.click(screen.getByText('addA'));

    await waitFor(() => {
      expect(warnSpy).toHaveBeenCalledWith('Cart save failed', expect.any(Error));
    });
    warnSpy.mockRestore();
  });

  it('useCart를 CartProvider 없이 사용하면 에러가 발생한다', () => {
    expect(() => {
      render(<TestConsumer />);
    }).toThrow('useCart must be used within a CartProvider');
  });
});

describe('CartContext × 인증 상태', () => {
  let storage: Record<string, string>;

  beforeEach(() => {
    storage = {};
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation((key) => storage[key] ?? null);
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation((key, value) => {
      storage[key] = value;
    });
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation((key) => {
      delete storage[key];
    });
    mockAuthState.isAuthenticated = true;
    mockAuthState.isLoading = false;
  });

  it('비로그인 상태에서는 localStorage 카트를 복원하지 않는다', async () => {
    storage['cart'] = JSON.stringify([{ ...ITEM_A, quantity: 5 }]);
    mockAuthState.isAuthenticated = false;

    render(
      <CartProvider>
        <TestConsumer />
      </CartProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('count').textContent).toBe('0');
    });
    expect(storage['cart']).toBeUndefined();
  });

  it('비로그인 상태에서 addItem은 카트에 아무것도 추가하지 않는다', async () => {
    mockAuthState.isAuthenticated = false;
    const user = userEvent.setup();

    render(
      <CartProvider>
        <TestConsumer />
      </CartProvider>,
    );

    await user.click(screen.getByText('addA'));

    expect(screen.getByTestId('count').textContent).toBe('0');
    expect(storage['cart']).toBeUndefined();
  });

  it('인증 로딩 중에는 카트를 로드하지 않고 대기한다', async () => {
    storage['cart'] = JSON.stringify([{ ...ITEM_A, quantity: 1 }]);
    mockAuthState.isLoading = true;
    mockAuthState.isAuthenticated = false;

    render(
      <CartProvider>
        <TestConsumer />
      </CartProvider>,
    );

    expect(screen.getByTestId('count').textContent).toBe('0');
  });

  it('로그아웃 시 localStorage의 cart 키가 삭제된다', async () => {
    // 선행 조건: 로그인 상태에서 카트가 localStorage에 존재
    storage['cart'] = JSON.stringify([{ ...ITEM_A, quantity: 2 }]);
    mockAuthState.isAuthenticated = true;
    mockAuthState.isLoading = false;

    const { rerender } = render(
      <CartProvider>
        <TestConsumer />
      </CartProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('count').textContent).toBe('2');
    });
    expect(storage['cart']).toBeDefined();

    // 로그아웃 전환: isAuthenticated true -> false
    mockAuthState.isAuthenticated = false;
    rerender(
      <CartProvider>
        <TestConsumer />
      </CartProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('count').textContent).toBe('0');
    });
    expect(storage['cart']).toBeUndefined();
  });
});
