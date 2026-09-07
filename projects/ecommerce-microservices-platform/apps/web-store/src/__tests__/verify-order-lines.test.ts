/**
 * `verifyOrderLines` — 주문 생성 **직전**의 라이브 재검증.
 *
 * 🔴🔴 이 스위트의 핵심은 **`backend_unreachable` 칸**이다. 공개 카탈로그가 저장본을 읽게
 *    되면서 «백엔드가 죽어 있어도 화면은 멀쩡히 도는» 상태가 생겼다. 그 상태에서 주문이
 *    통과하면 화면이 «주문 완료» 라는 없는 사실을 그린다. 판정 불가를 «괜찮다» 로 번역하지
 *    않는지를 여기서 잰다.
 * 🔴 그리고 «네트워크 실패» 와 «없는 상품(null)» 은 다른 칸이다 — 같은 문구로 뭉개면
 *    사용자가 할 일이 달라진다(기다리기 vs 장바구니에서 빼기).
 */
import { describe, it, expect, vi } from 'vitest';
import type { ProductDetail } from '@repo/types';
import type { CheckoutCartItem } from '@/features/checkout/model/types';
import {
  orderLineVerdictMessage,
  verifyOrderLines,
} from '@/features/checkout/model/verify-order-lines';

const ITEM: CheckoutCartItem = {
  productId: 'p1',
  variantId: 'v1',
  productName: '노트북',
  optionName: '실버',
  price: 1_500_000,
  quantity: 1,
};

function liveProduct(over: Partial<ProductDetail> = {}): ProductDetail {
  return {
    id: 'p1',
    name: '노트북',
    description: '',
    status: 'ON_SALE',
    price: 1_450_000,
    categoryId: 'c1',
    variants: [{ id: 'v1', optionName: '실버', stock: 5, additionalPrice: 50_000 }],
    ...over,
  };
}

describe('verifyOrderLines', () => {
  it('라이브 값이 장바구니와 일치하면 통과한다', async () => {
    const verdict = await verifyOrderLines([ITEM], { fetchProduct: async () => liveProduct() });
    expect(verdict).toEqual({ ok: true });
  });

  it('백엔드에 닿지 못하면 거절한다 — 성공을 흉내내지 않는다', async () => {
    const verdict = await verifyOrderLines([ITEM], {
      fetchProduct: async () => {
        throw new Error('ECONNREFUSED');
      },
    });

    expect(verdict).toEqual({ ok: false, reason: 'backend_unreachable' });
  });

  it('«닿지 못함» 과 «없는 상품» 은 다른 판정이다', async () => {
    const unreachable = await verifyOrderLines([ITEM], {
      fetchProduct: async () => {
        throw new Error('ECONNREFUSED');
      },
    });
    const gone = await verifyOrderLines([ITEM], { fetchProduct: async () => null });

    expect(unreachable.ok).toBe(false);
    expect(gone.ok).toBe(false);
    expect(orderLineVerdictMessage(unreachable)).not.toBe(orderLineVerdictMessage(gone));
  });

  it('판매 중이 아니면 거절한다', async () => {
    const verdict = await verifyOrderLines([ITEM], {
      fetchProduct: async () => liveProduct({ status: 'SOLD_OUT' }),
    });
    expect(verdict).toMatchObject({ ok: false, reason: 'unavailable' });
  });

  it('옵션이 사라졌으면 거절한다', async () => {
    const verdict = await verifyOrderLines([ITEM], {
      fetchProduct: async () => liveProduct({ variants: [] }),
    });
    expect(verdict).toMatchObject({ ok: false, reason: 'option_gone', optionName: '실버' });
  });

  it('라이브 재고가 수량보다 적으면 거절한다', async () => {
    const verdict = await verifyOrderLines([{ ...ITEM, quantity: 9 }], {
      fetchProduct: async () => liveProduct(),
    });
    expect(verdict).toMatchObject({ ok: false, reason: 'out_of_stock' });
  });

  it('가격이 바뀌었으면 거절하고 현재 가격을 말한다 — 저장본의 표시 가격으로 주문이 만들어지지 않는다', async () => {
    const verdict = await verifyOrderLines([ITEM], {
      fetchProduct: async () => liveProduct({ price: 1_600_000 }),
    });

    expect(verdict).toMatchObject({ ok: false, reason: 'price_changed', currentPrice: 1_650_000 });
    expect(orderLineVerdictMessage(verdict)).toContain('1,650,000');
  });

  it('같은 상품의 여러 옵션은 상품당 한 번만 묻는다', async () => {
    const fetchProduct = vi.fn(async () =>
      liveProduct({
        variants: [
          { id: 'v1', optionName: '실버', stock: 5, additionalPrice: 50_000 },
          { id: 'v2', optionName: '블랙', stock: 5, additionalPrice: 50_000 },
        ],
      }),
    );

    const verdict = await verifyOrderLines(
      [ITEM, { ...ITEM, variantId: 'v2', optionName: '블랙' }],
      { fetchProduct },
    );

    expect(verdict).toEqual({ ok: true });
    expect(fetchProduct).toHaveBeenCalledTimes(1);
  });

  it('빈 장바구니는 통과한다 (호출부가 이미 막지만 여기서 던지지 않는다)', async () => {
    const fetchProduct = vi.fn();
    expect(await verifyOrderLines([], { fetchProduct })).toEqual({ ok: true });
    expect(fetchProduct).not.toHaveBeenCalled();
  });
});
