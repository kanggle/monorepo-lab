import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CheckoutForm } from '@/features/checkout/ui/CheckoutForm';
import type { ApiErrorResponse } from '@repo/types';
import type { CheckoutCartItem } from '@/features/checkout/model/types';
import { TestQueryProvider } from './test-utils';

const mockPush = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, replace: vi.fn() }),
}));

vi.mock('@/entities/order', () => ({
  placeOrder: vi.fn(),
}));

// 🔴 주문 직전의 라이브 재검증(`verifyOrderLines`)은 이 스위트에서 **기본 통과**로 둔다 —
//    여기서 재는 것은 폼의 동작이고, 게이트 자체의 표는 `verify-order-lines.test.ts` 에 있다.
//    다만 «게이트가 배선돼 있는가» 는 여기서만 잴 수 있어서(폼이 안 부르면 양쪽 다 초록인
//    채 화면에서 백엔드 없이 주문이 나간다) 아래 두 칸을 둔다.
const mockVerifyOrderLines = vi.hoisted(() => vi.fn());
vi.mock('@/features/checkout/model/verify-order-lines', async () => {
  const actual = await vi.importActual<typeof import('@/features/checkout/model/verify-order-lines')>(
    '@/features/checkout/model/verify-order-lines',
  );
  return { ...actual, verifyOrderLines: mockVerifyOrderLines };
});

vi.mock('@/entities/user/api/use-addresses', () => ({
  useAddresses: () => ({ data: { addresses: [] }, isLoading: false, invalidate: vi.fn() }),
}));

const mockRequestPayment = vi.fn();
vi.mock('@/features/checkout/model/use-toss-payment', () => ({
  useTossPayment: () => ({ isReady: true, requestPayment: mockRequestPayment }),
}));

vi.mock('@/shared/ui/AddressSearch', () => ({
  AddressSearch: ({ onSelect }: { onSelect: (data: { zipCode: string; address1: string }) => void }) => (
    <button type="button" onClick={() => onSelect({ zipCode: '12345', address1: '서울시 강남구' })}>
      주소 검색
    </button>
  ),
}));

vi.mock('@/shared/ui/Skeleton', () => ({
  Skeleton: () => null,
}));

import { placeOrder } from '@/entities/order';
const mockSubmitOrder = vi.mocked(placeOrder);

const CART_ITEMS: CheckoutCartItem[] = [
  {
    productId: 'p1',
    variantId: 'v1',
    productName: '노트북',
    optionName: '실버',
    price: 1500000,
    quantity: 1,
  },
];

const mockOnOrderComplete = vi.fn();

function renderCheckoutForm(items = CART_ITEMS, totalAmount = 1500000) {
  return render(
    <TestQueryProvider>
      <CheckoutForm
        items={items}
        totalAmount={totalAmount}
        onOrderComplete={mockOnOrderComplete}
      />
    </TestQueryProvider>,
  );
}

async function fillRequiredFields(user: ReturnType<typeof userEvent.setup>) {
  await waitFor(() => {
    expect(screen.getByLabelText('수령인')).toBeInTheDocument();
  });
  await user.type(screen.getByLabelText('수령인'), '홍길동');
  await user.type(screen.getByLabelText('전화번호'), '010-1234-5678');
  await user.click(screen.getByRole('button', { name: '주소 검색' }));
}

describe('CheckoutForm', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockVerifyOrderLines.mockResolvedValue({ ok: true });
  });

  it('주문 상품 정보를 표시한다', async () => {
    renderCheckoutForm();

    await waitFor(() => {
      expect(screen.getByText(/노트북/)).toBeInTheDocument();
    });
    const priceElements = screen.getAllByText(/1,500,000/);
    expect(priceElements.length).toBeGreaterThanOrEqual(1);
  });

  it('배송지 입력 필드를 표시한다', async () => {
    renderCheckoutForm();

    await waitFor(() => {
      expect(screen.getByLabelText('수령인')).toBeInTheDocument();
    });
    expect(screen.getByLabelText('전화번호')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('주소 검색을 눌러주세요')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('우편번호')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('상세주소 입력')).toBeInTheDocument();
  });

  it('필수 필드가 비어있으면 결제 버튼이 비활성화된다', () => {
    renderCheckoutForm();

    const button = screen.getByRole('button', { name: /결제하기/ });
    expect(button).toBeDisabled();
  });

  it('필수 필드를 모두 채우면 결제 버튼이 활성화된다', async () => {
    const user = userEvent.setup();
    renderCheckoutForm();

    await fillRequiredFields(user);

    const button = screen.getByRole('button', { name: /결제하기/ });
    expect(button).toBeEnabled();
  });

  it('주문 성공 시 결제를 요청한다', async () => {
    mockSubmitOrder.mockResolvedValueOnce({ orderId: 'order-1' });

    const user = userEvent.setup();
    renderCheckoutForm();

    await fillRequiredFields(user);
    await user.click(screen.getByRole('button', { name: /결제하기/ }));

    await waitFor(() => {
      expect(mockRequestPayment).toHaveBeenCalledWith(
        expect.objectContaining({ orderId: 'order-1', amount: 1500000 }),
      );
    });
  });

  it('주문 성공 시 onOrderComplete를 호출한다', async () => {
    mockSubmitOrder.mockResolvedValueOnce({ orderId: 'order-1' });

    const user = userEvent.setup();
    renderCheckoutForm();

    await fillRequiredFields(user);
    await user.click(screen.getByRole('button', { name: /결제하기/ }));

    await waitFor(() => {
      expect(mockOnOrderComplete).toHaveBeenCalledTimes(1);
    });
  });

  it('주문 성공 시 올바른 데이터를 전송한다', async () => {
    mockSubmitOrder.mockResolvedValueOnce({ orderId: 'order-1' });

    const user = userEvent.setup();
    renderCheckoutForm();

    await fillRequiredFields(user);
    await user.type(screen.getByPlaceholderText('상세주소 입력'), '101호');
    await user.click(screen.getByRole('button', { name: /결제하기/ }));

    await waitFor(() => {
      // Second arg is the generated idempotency key (TASK-BE-430).
      expect(mockSubmitOrder).toHaveBeenCalledWith(
        {
          items: [{ productId: 'p1', variantId: 'v1', productName: '노트북', optionName: '실버', quantity: 1, unitPrice: 1500000 }],
          shippingAddress: {
            recipient: '홍길동',
            phone: '010-1234-5678',
            zipCode: '12345',
            address1: '서울시 강남구',
            address2: '101호',
          },
        },
        expect.any(String),
      );
    });
  });

  it('주문 실패 시 API 에러 메시지를 표시한다', async () => {
    const apiError: ApiErrorResponse = {
      code: 'INSUFFICIENT_STOCK',
      message: 'Not enough stock',
      timestamp: new Date().toISOString(),
    };
    mockSubmitOrder.mockRejectedValueOnce(apiError);

    const user = userEvent.setup();
    renderCheckoutForm();

    await fillRequiredFields(user);
    await user.click(screen.getByRole('button', { name: /결제하기/ }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('재고가 부족한 상품이 있습니다.');
    });
  });

  it('알 수 없는 에러 시 기본 메시지를 표시한다', async () => {
    mockSubmitOrder.mockRejectedValueOnce(new Error('unknown'));

    const user = userEvent.setup();
    renderCheckoutForm();

    await fillRequiredFields(user);
    await user.click(screen.getByRole('button', { name: /결제하기/ }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('주문에 실패했습니다.');
    });
  });

  it('주문 처리 중 중복 클릭을 방지한다', async () => {
    let resolveOrder: (value: { orderId: string }) => void;
    mockSubmitOrder.mockImplementationOnce(
      () => new Promise((resolve) => { resolveOrder = resolve; }),
    );

    const user = userEvent.setup();
    renderCheckoutForm();

    await fillRequiredFields(user);

    const button = screen.getByRole('button', { name: /결제하기/ });
    await user.click(button);

    expect(screen.getByRole('button', { name: /주문 처리 중/ })).toBeDisabled();
    expect(mockSubmitOrder).toHaveBeenCalledTimes(1);

    resolveOrder!({ orderId: 'order-1' });

    await waitFor(() => {
      expect(mockRequestPayment).toHaveBeenCalledWith(
        expect.objectContaining({ orderId: 'order-1' }),
      );
    });
  });

  it('상품이 없으면 결제 버튼이 비활성화된다', () => {
    renderCheckoutForm([], 0);

    const button = screen.getByRole('button', { name: /결제하기/ });
    expect(button).toBeDisabled();
  });

  describe('주문 직전 라이브 재검증 (공개 카탈로그가 저장본을 읽게 되면서 생긴 요구)', () => {
    it('주문을 만들기 전에 라이브로 각 줄을 확인한다', async () => {
      mockSubmitOrder.mockResolvedValueOnce({ orderId: 'order-1' });

      const user = userEvent.setup();
      renderCheckoutForm();

      await fillRequiredFields(user);
      await user.click(screen.getByRole('button', { name: /결제하기/ }));

      await waitFor(() => {
        expect(mockVerifyOrderLines).toHaveBeenCalledWith(CART_ITEMS);
      });
    });

    it('🔴 백엔드에 닿지 못하면 주문을 만들지 않는다 — 성공을 흉내내지 않는다', async () => {
      mockVerifyOrderLines.mockResolvedValue({ ok: false, reason: 'backend_unreachable' });

      const user = userEvent.setup();
      renderCheckoutForm();

      await fillRequiredFields(user);
      await user.click(screen.getByRole('button', { name: /결제하기/ }));

      await waitFor(() => {
        expect(screen.getByRole('alert')).toHaveTextContent('주문 서버에 연결할 수 없어');
      });
      expect(mockSubmitOrder).not.toHaveBeenCalled();
      expect(mockRequestPayment).not.toHaveBeenCalled();
      expect(mockOnOrderComplete).not.toHaveBeenCalled();
      // 거절 뒤에는 다시 시도할 수 있어야 한다.
      expect(screen.getByRole('button', { name: /결제하기/ })).toBeEnabled();
    });

    it('가격이 바뀌었으면 현재 가격을 말하고 주문하지 않는다', async () => {
      mockVerifyOrderLines.mockResolvedValue({
        ok: false,
        reason: 'price_changed',
        productName: '노트북',
        currentPrice: 1600000,
      });

      const user = userEvent.setup();
      renderCheckoutForm();

      await fillRequiredFields(user);
      await user.click(screen.getByRole('button', { name: /결제하기/ }));

      await waitFor(() => {
        expect(screen.getByRole('alert')).toHaveTextContent('1,600,000');
      });
      expect(mockSubmitOrder).not.toHaveBeenCalled();
    });
  });
});
