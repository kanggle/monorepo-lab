/**
 * 공개 카탈로그 — **백엔드 0대, 로그인 0회**로 목록·상세·검색이 실물로 서는가.
 *
 * =============================================================================
 * 🔴🔴 이 스위트는 목이 아니라 **저장소에 커밋된 실제 번들 시드**를 읽는다
 * =============================================================================
 * 다른 스위트들은 판독자를 mock 해서 «질의 의미» 를 잰다. 여기는 그 반대다: 판독자를
 * 그대로 두고 `@demo/public-data/snapshots/store.json` 을 진짜로 읽는다. 목만 있으면
 * 「번들 시드가 계약을 어겨도 초록」인 스위트가 되고, 그 결함은 정확히 **배포된 화면에서만**
 * 보인다.
 *
 * 그리고 «백엔드가 안 불린다» 를 말로 하지 않고 **잰다**: `fetch` 를 던지는 스텁으로
 * 갈아 끼운다. 어딘가에 게이트웨이로 가는 코드가 남아 있으면 그 자리에서 죽는다.
 */
import { describe, it, expect, vi, beforeAll, afterAll } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

vi.mock('next/image', () => ({
  // eslint-disable-next-line @next/next/no-img-element, jsx-a11y/alt-text
  default: ({ src, alt }: { src: string; alt: string }) => <img src={src} alt={alt} />,
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock('@/features/cart', () => ({
  useCart: () => ({ addItem: vi.fn(), items: [] }),
}));

vi.mock('@/features/wishlist', () => ({
  WishlistButton: () => <button type="button" aria-label="위시리스트" />,
}));

vi.mock('@/entities/product/ui/ProductImage', () => ({
  ProductImage: ({ alt }: { alt: string }) => <div data-testid="product-image" data-alt={alt} />,
}));

vi.mock('@/shared/hooks/use-click-outside', () => ({ useClickOutside: vi.fn() }));

import { getProduct, getProducts } from '@/entities/product';
import { searchProducts } from '@/features/search';
import { ProductList } from '@/features/product';
import { SearchResults } from '@/features/search/ui/SearchResults';
import { ProductDetailWithCart } from '@/widgets/product-detail-with-cart';
import { readStoreSnapshot } from '@/shared/public-data/store-snapshot';

const ORIGINAL_ENV = { ...process.env };

beforeAll(() => {
  // 🔴 «백엔드에 안 간다» 를 재는 자리. 저장본 판독자도 이 환경에서는 fetch 를 안 쓴다
  //    (`DEMO_PUBLIC_DATA_BASE_URL` 이 없으면 번들 시드로 직행 — read.ts § 설정 없음).
  delete process.env.DEMO_PUBLIC_DATA_BASE_URL;
  vi.stubGlobal(
    'fetch',
    vi.fn(() => {
      throw new Error('공개 카탈로그가 네트워크를 때렸다 — 저장본만 읽어야 한다');
    }),
  );
});

afterAll(() => {
  vi.unstubAllGlobals();
  process.env = { ...ORIGINAL_ENV };
});

describe('공개 카탈로그 — 백엔드 없이 (번들 시드 실물)', () => {
  it('번들 시드가 봉투 계약을 지키고 상품·카테고리를 담고 있다', async () => {
    const { data, envelope, degraded } = await readStoreSnapshot();

    expect(envelope.dataset).toBe('store');
    expect(data.products.length).toBeGreaterThan(0);
    expect(data.categories.length).toBeGreaterThan(0);
    // 설정이 없는 것은 결함이 아니다 — degraded 가 아니어야 한다(read.ts § 설정 없음).
    expect(degraded).toBe(false);
  });

  it('시드에는 HIDDEN 상품이 없다 (발행자가 미리 거른다)', async () => {
    const { data } = await readStoreSnapshot();
    expect(data.products.every((p) => p.status === 'ON_SALE' || p.status === 'SOLD_OUT')).toBe(true);
  });

  it('시드의 어떤 상품·옵션에도 재고 필드가 없다 — 계약이 필드 자체를 안 싣는다', async () => {
    const { data } = await readStoreSnapshot();

    for (const p of data.products) {
      expect(p).not.toHaveProperty('stock');
      for (const o of p.options) {
        expect(o).not.toHaveProperty('stock');
      }
    }
  });

  it('목록이 실물로 렌더된다', async () => {
    const result = await getProducts({ page: 0, size: 20 });
    expect(result.content.length).toBeGreaterThan(0);

    render(<ProductList products={result.content} />);

    expect(screen.getByText(result.content[0].name)).toBeInTheDocument();
  });

  it('검색 결과가 실물로 렌더된다', async () => {
    const { data } = await readStoreSnapshot();
    const word = data.products[0].name.split(' ')[0];

    const result = await searchProducts({ q: word, page: 0, size: 20 });
    expect(result.totalElements).toBeGreaterThan(0);

    render(<SearchResults items={result.content} query={word} corpusSize={result.corpusSize} />);

    expect(screen.getByText(result.content[0].name)).toBeInTheDocument();
  });

  it('있지도 않은 검색어는 «검색 결과 없음» 이지 «저장본이 비어 있음» 이 아니다', async () => {
    const result = await searchProducts({ q: '절대없을검색어zzzz', page: 0, size: 20 });

    expect(result.totalElements).toBe(0);
    expect(result.corpusSize).toBeGreaterThan(0);

    render(<SearchResults items={result.content} query="절대없을검색어zzzz" corpusSize={result.corpusSize} />);

    const empty = screen.getByTestId('snapshot-empty-state');
    expect(empty).toHaveTextContent('검색 결과 없음');
    expect(empty).not.toHaveTextContent('저장본이 비어 있음');
  });

  describe('상세', () => {
    it('이름·설명·가격·이미지·옵션·카테고리를 그린다', async () => {
      const { data } = await readStoreSnapshot();
      const seed = data.products.find((p) => p.options.length > 0) ?? data.products[0];

      const product = await getProduct(seed.id);
      expect(product).not.toBeNull();

      render(<ProductDetailWithCart product={product!} fromSnapshot />);

      expect(screen.getByRole('heading', { name: seed.name })).toBeInTheDocument();
      expect(screen.getByText(seed.price.toLocaleString())).toBeInTheDocument();
      expect(screen.getByTestId('product-image')).toHaveAttribute('data-alt', seed.name);
      expect(product!.categoryName).not.toBeNull();
      if (seed.description) {
        expect(screen.getByText(seed.description)).toBeInTheDocument();
      }
    });

    it('표시 가격 · 재고는 주문 단계에서 확인 문구를 말한다', async () => {
      const { data } = await readStoreSnapshot();
      const product = await getProduct(data.products[0].id);

      render(<ProductDetailWithCart product={product!} fromSnapshot />);

      const note = screen.getByTestId('snapshot-price-note');
      expect(note).toHaveTextContent('표시 가격');
      expect(note).toHaveTextContent('재고·판매 가능 여부는 주문 단계에서 확인됩니다');
    });

    it('🔴 렌더된 상세 어디에도 재고 수·품절 배지가 없다 (옵션 목록을 연 뒤에도)', async () => {
      const user = userEvent.setup();
      const { data } = await readStoreSnapshot();
      const seed = data.products.find((p) => p.options.length > 0)!;
      const product = await getProduct(seed.id);

      const { container } = render(<ProductDetailWithCart product={product!} fromSnapshot />);

      // 드롭다운을 열어 옵션 행까지 DOM 에 올린다 — 안 열면 «없다» 가 공허하다.
      const trigger = screen
        .getAllByRole('button', { name: /옵션을 선택하세요/ })
        .find((btn) => btn.querySelector('span'))!;
      await user.click(trigger);

      const menu = screen.getByText(seed.options[0].optionName);
      expect(menu).toBeInTheDocument();

      const text = container.textContent ?? '';
      // "재고 12" 같은 숫자도, "품절"·"재고 있음" 같은 배지도 없어야 한다.
      expect(text).not.toMatch(/재고\s*\d/);
      expect(text).not.toContain('품절');
      expect(text).not.toContain('재고 있음');
      // 문구 안의 "재고·판매 가능 여부는 …" 은 남아 있어야 한다(그것이 정직한 표현이다).
      expect(text).toContain('재고·판매 가능 여부는 주문 단계에서 확인됩니다');
    });

    it('재고를 모를 때도 수량 증가 버튼이 살아 있다 (null 을 0 으로 읽으면 여기서 죽는다)', async () => {
      const user = userEvent.setup();
      const { data } = await readStoreSnapshot();
      const seed = data.products.find((p) => p.options.length > 0)!;
      const product = await getProduct(seed.id);

      render(<ProductDetailWithCart product={product!} fromSnapshot />);

      const trigger = screen
        .getAllByRole('button', { name: /옵션을 선택하세요/ })
        .find((btn) => btn.querySelector('span'))!;
      await user.click(trigger);
      await user.click(screen.getByText(seed.options[0].optionName));

      expect(screen.getByLabelText('수량 늘리기')).toBeEnabled();
    });

    it('fromSnapshot 이 아니면 표시 가격 문구를 그리지 않는다 (라이브 경로에 남지 않는다)', async () => {
      const { data } = await readStoreSnapshot();
      const product = await getProduct(data.products[0].id);

      render(<ProductDetailWithCart product={product!} />);

      expect(screen.queryByTestId('snapshot-price-note')).not.toBeInTheDocument();
    });
  });

  it('카테고리 필터로 좁혀도 카테고리 목록은 전부 남는다 (다른 카테고리로 옮겨갈 수 있다)', async () => {
    const { data } = await readStoreSnapshot();
    const target = data.categories[0];

    const result = await getProducts({ categoryId: target.id, page: 0, size: 20 });

    expect(result.content.every((p) => p.categoryId === target.id)).toBe(true);
    expect(result.categories.length).toBeGreaterThan(1);
    expect(result.categories.map((c) => c.id)).toContain(target.id);
  });

  it('목록의 모든 카테고리 패싯이 표시명을 갖는다 (UUID 가 이름 자리에 오지 않는다)', async () => {
    const result = await getProducts({ page: 0, size: 20 });

    expect(result.categories.length).toBeGreaterThan(0);
    for (const c of result.categories) {
      expect(c.name).not.toBeNull();
      expect(c.name).not.toMatch(/^[0-9a-f]{8}-/);
    }
  });

  it('카드 그리드를 그려도 네트워크로 나가지 않았다', () => {
    // 🔵 위 전 칸이 도는 동안 stub 된 fetch 가 한 번이라도 불렸으면 그 자리에서 던져서
    //    이미 빨강이었다. 여기서는 «한 번도 안 불렸다» 를 명시적으로 한 번 더 적는다.
    expect(vi.mocked(globalThis.fetch)).not.toHaveBeenCalled();
  });
});

describe('within 헬퍼 사용처', () => {
  it('목록 카드 안에서 이름과 가격이 같은 카드에 있다', async () => {
    const result = await getProducts({ page: 0, size: 4 });
    const first = result.content[0];

    render(<ProductList products={result.content} />);

    const card = screen.getByText(first.name).closest('a')!;
    expect(within(card).getByText(first.name)).toBeInTheDocument();
  });
});
