import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProductDetailWithCart } from '@/widgets/product-detail-with-cart';
import { VariantSelector } from '@/widgets/product-detail-with-cart/VariantSelector';
import {
  SELECTABLE_VARIANT_OPTION,
  VARIANT_OPTION_TESTID,
} from '@/widgets/product-detail-with-cart/variant-option-testid';
import type { ProductDetail } from '@repo/types';
import type { ProductDetailView } from '@/entities/product';

const mockPush = vi.fn();
const mockAddItem = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
}));

vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

vi.mock('next/image', () => ({
  default: ({ src, alt, ...props }: { src: string; alt: string; [key: string]: unknown }) => (
    // eslint-disable-next-line @next/next/no-img-element
    <img src={src} alt={alt} {...props} />
  ),
}));

vi.mock('@/entities/product', () => ({
  ProductImage: ({ alt }: { alt: string }) => <div data-testid="product-image" data-alt={alt} />,
}));

vi.mock('@/features/cart', () => ({
  useCart: () => ({ addItem: mockAddItem, items: [] }),
}));

vi.mock('@/shared/ui', () => ({
  Toast: ({ message }: { message: string }) => <div data-testid="toast">{message}</div>,
}));

vi.mock('@/shared/hooks/use-click-outside', () => ({
  useClickOutside: vi.fn(),
}));

vi.mock('@/features/auth', () => ({
  useAuth: () => ({
    isAuthenticated: false,
    isLoading: false,
    user: null,
    login: vi.fn(),
    signup: vi.fn(),
    logout: vi.fn(),
  }),
}));

vi.mock('@/features/wishlist', () => ({
  WishlistButton: () => <button aria-label="wishlist-stub" />,
}));

const product: ProductDetail = {
  id: 'prod-1',
  name: '테스트 상품',
  description: '상품 설명입니다.',
  status: 'ON_SALE',
  price: 10000,
  categoryId: 'cat-1',
  images: [{ imageId: 'img-1', url: '/img/1.jpg', sortOrder: 0, isPrimary: true }],
  variants: [
    { id: 'v1', optionName: '빨강', stock: 10, additionalPrice: 1000 },
    { id: 'v2', optionName: '파랑', stock: 5, additionalPrice: 0 },
    { id: 'v3', optionName: '녹색', stock: 0, additionalPrice: 500 },
  ],
};

/** 드롭다운 트리거 버튼 (placeholder 텍스트를 가진 첫 번째 버튼) */
function getDropdownTrigger() {
  const buttons = screen.getAllByRole('button', { name: /옵션을 선택하세요/ });
  // dropdown trigger has the arrow ▾ inside, cart button does not
  return buttons.find(btn => btn.querySelector('span'))!;
}

describe('ProductDetailWithCart', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('상품 이름과 가격을 표시한다', () => {
    render(<ProductDetailWithCart product={product} />);

    expect(screen.getByRole('heading', { name: '테스트 상품' })).toBeInTheDocument();
    expect(screen.getByText('10,000')).toBeInTheDocument();
  });

  it('상품 설명을 표시한다', () => {
    render(<ProductDetailWithCart product={product} />);

    expect(screen.getByText('상품 설명입니다.')).toBeInTheDocument();
  });

  it('브레드크럼을 표시한다', () => {
    render(<ProductDetailWithCart product={product} />);

    expect(screen.getByText('홈')).toBeInTheDocument();
    expect(screen.getByText('상품')).toBeInTheDocument();
  });

  it('옵션 선택 드롭다운과 장바구니 버튼이 있다', () => {
    render(<ProductDetailWithCart product={product} />);

    const buttons = screen.getAllByRole('button', { name: /옵션을 선택하세요/ });
    expect(buttons).toHaveLength(2); // dropdown trigger + cart button
  });

  it('옵션 미선택 시 즉시 주문 버튼이 비활성화된다', () => {
    render(<ProductDetailWithCart product={product} />);

    expect(screen.getByRole('button', { name: '즉시 주문' })).toBeDisabled();
  });

  it('드롭다운을 열어 옵션 목록을 표시한다', async () => {
    const user = userEvent.setup();
    render(<ProductDetailWithCart product={product} />);

    await user.click(getDropdownTrigger());

    expect(screen.getByText('빨강')).toBeInTheDocument();
    expect(screen.getByText('파랑')).toBeInTheDocument();
    expect(screen.getByText('녹색')).toBeInTheDocument();
  });

  it('품절 옵션은 비활성화된다', async () => {
    const user = userEvent.setup();
    render(<ProductDetailWithCart product={product} />);

    await user.click(getDropdownTrigger());

    const greenBtn = screen.getByText('녹색').closest('button');
    expect(greenBtn).toBeDisabled();
  });

  it('옵션 선택 후 장바구니 담기 버튼이 활성화된다', async () => {
    const user = userEvent.setup();
    render(<ProductDetailWithCart product={product} />);

    await user.click(getDropdownTrigger());
    await user.click(screen.getByText('빨강'));

    expect(screen.getByText('장바구니 담기')).toBeEnabled();
  });

  it('장바구니 담기 클릭 시 addItem을 호출하고 토스트를 표시한다', async () => {
    const user = userEvent.setup();
    render(<ProductDetailWithCart product={product} />);

    await user.click(getDropdownTrigger());
    await user.click(screen.getByText('빨강'));
    await user.click(screen.getByText('장바구니 담기'));

    expect(mockAddItem).toHaveBeenCalledWith(
      expect.objectContaining({
        productId: 'prod-1',
        variantId: 'v1',
        productName: '테스트 상품',
        optionName: '빨강',
        price: 11000,
      }),
      1,
    );
    expect(screen.getByTestId('toast')).toHaveTextContent('장바구니에 추가되었습니다.');
  });

  it('즉시 주문 클릭 시 checkout 페이지로 이동한다', async () => {
    const user = userEvent.setup();
    render(<ProductDetailWithCart product={product} />);

    await user.click(getDropdownTrigger());
    await user.click(screen.getByText('빨강'));
    await user.click(screen.getByText('즉시 주문'));

    expect(mockPush).toHaveBeenCalledWith(
      expect.stringContaining('/checkout?items='),
    );
  });

  it('선택된 옵션을 삭제할 수 있다', async () => {
    const user = userEvent.setup();
    render(<ProductDetailWithCart product={product} />);

    await user.click(getDropdownTrigger());
    await user.click(screen.getByText('빨강'));

    expect(screen.getByText('장바구니 담기')).toBeEnabled();

    await user.click(screen.getByLabelText('빨강 삭제'));

    // 삭제 후 즉시 주문 버튼이 다시 비활성화
    expect(screen.getByRole('button', { name: '즉시 주문' })).toBeDisabled();
  });
});

// ---------------------------------------------------------------------------
// TASK-MONO-655 원인 ② — e2e 헬퍼의 술어에 대한 대조군
//
// 🔴 위의 기존 스위트는 `stock: 10 / 5 / 0` 만 쓴다. 그것은 **라이브 백엔드가 말한 수**
//    이고, 공개 상세는 2026-09-07 이후 **저장본**에서 와서 `stock` 이 항상 `null` 이다
//    (`snapshot-mappers.ts`). 그래서 이 스위트는 초록인 채로 남았고 e2e 만 3일 빨갰다 —
//    픽스처가 현실을 안 담으면 초록도 공허하다. 아래가 그 구멍을 메운다.
//
// 재는 것은 **헬퍼가 실제로 쓰는 그 문자열**이다(`SELECTABLE_VARIANT_OPTION` 을 양쪽이
// 같은 모듈에서 import 한다). 여기서 직접 셀렉터를 다시 타이핑하면 이 테스트는 헬퍼가
// 아니라 자기가 베낀 사본을 재게 된다.
// ---------------------------------------------------------------------------

/** 저장본 모양 — 재고를 «모른다». 위 `product` 와 달리 이것이 지금의 공개 상세다. */
const snapshotProduct: ProductDetailView = {
  ...product,
  variants: [
    { id: 'v1', optionName: '빨강', stock: null, additionalPrice: 1000 },
    { id: 'v2', optionName: '파랑', stock: null, additionalPrice: 0 },
  ],
};

function selectable(root: ParentNode = document): HTMLElement[] {
  return Array.from(root.querySelectorAll<HTMLElement>(SELECTABLE_VARIANT_OPTION));
}

function renderOpenSelector(variants: ProductDetailView['variants'], selectedIds: string[] = []) {
  return render(
    <VariantSelector
      variants={variants}
      selectedItems={selectedIds.map((variantId) => ({ variantId, quantity: 1 }))}
      dropdownOpen
      onDropdownToggle={vi.fn()}
      onSelect={vi.fn()}
      onDropdownClose={vi.fn()}
    />,
  );
}

describe('e2e 헬퍼의 술어 — «선택 가능한 옵션»', () => {
  it('재고를 «모르는» 옵션(stock: null)을 잡는다 — 낡은 술어는 못 잡던 자리', () => {
    const { container } = renderOpenSelector(snapshotProduct.variants);

    expect(selectable(container).map((el) => el.textContent)).toEqual([
      expect.stringContaining('빨강'),
      expect.stringContaining('파랑'),
    ]);

    // 🔴 같은 화면에서 낡은 술어 `/재고\s+\d+/` 는 **0건**이다. 이것이 e2e 가 죽은 사유고,
    //    저장본 경로에 재고 문구를 되살리면 여기가 빨개진다(ADR-MONO-070 § 재고).
    expect(container.textContent).not.toMatch(/재고\s+\d+/);
  });

  it('🔴 대조군 — 품절 옵션은 testid 를 «가진 채» 이 술어에 안 잡힌다', () => {
    const { container } = renderOpenSelector([
      { id: 'v0', optionName: '녹색', stock: 0, additionalPrice: 0 },
      ...snapshotProduct.variants,
    ]);

    // 제외가 «원소가 없어서» 가 아니라 `:not([disabled])` 로 이뤄지는지 먼저 증명한다.
    expect(container.querySelectorAll(`[data-testid="${VARIANT_OPTION_TESTID}"]`)).toHaveLength(3);

    const names = selectable(container).map((el) => el.textContent);
    expect(names).toHaveLength(2);
    expect(names.join('|')).not.toContain('녹색');
  });

  it('🔴 대조군 — 이미 선택된 옵션도 안 잡힌다 (다시 고르면 e2e 가 헛돈다)', () => {
    const { container } = renderOpenSelector(snapshotProduct.variants, ['v1']);

    const names = selectable(container).map((el) => el.textContent);
    expect(names).toHaveLength(1);
    expect(names[0]).toContain('파랑');
  });

  it('🔴 대조군 — 페이지 전체에서 옵션 항목만 잡는다 (술어를 넓히기만 하면 엉뚱한 버튼을 누른다)', async () => {
    const user = userEvent.setup();
    render(<ProductDetailWithCart product={snapshotProduct} fromSnapshot />);
    await user.click(getDropdownTrigger());

    const matched = selectable();
    expect(matched).toHaveLength(2);
    expect(matched.every((el) => el.dataset.testid === VARIANT_OPTION_TESTID)).toBe(true);

    // 🔴🔴 넓히기만 한 술어(`button:not([disabled])`)가 왜 답이 아닌지를 **재서** 남긴다:
    //    페이지에는 활성 버튼이 옵션보다 많고, 그 중 **첫 번째가 옵션이 아니다** ⇒
    //    `.first()` 는 찜/트리거를 누른다. 실패가 아니라 조용한 오작동이 된다.
    const allEnabled = Array.from(document.querySelectorAll<HTMLElement>('button:not([disabled])'));
    expect(allEnabled.length).toBeGreaterThan(matched.length);
    expect(allEnabled[0].dataset.testid).not.toBe(VARIANT_OPTION_TESTID);
  });
});
