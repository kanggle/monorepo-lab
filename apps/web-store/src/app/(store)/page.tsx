import { getProducts } from '@/entities/product';
import { ProductListWithWishlist } from '@/widgets/product-list-with-wishlist';
import { HeroBanner } from '@/widgets/hero';
import Link from 'next/link';
import type { ProductSummary } from '@repo/types';

export const revalidate = 60;

export default async function HomePage() {
  const products: ProductSummary[] = await getProducts({ page: 0, size: 8 })
    .then((result) => result.content)
    .catch(() => []);

  return (
    <div>
      <div style={{ paddingTop: 'var(--space-8)' }}>
        <HeroBanner />
      </div>

      {/* Popular Products */}
      <section className="container" style={{ padding: 'var(--space-12) var(--space-6)' }}>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginBottom: 'var(--space-6)',
          }}
        >
          <h2
            style={{
              fontSize: 'var(--font-size-2xl)',
              fontWeight: 'var(--font-weight-bold)',
              margin: 0,
            }}
          >
            인기 상품
          </h2>
          <Link
            href="/products"
            style={{
              color: 'var(--color-primary-hover)',
              fontWeight: 'var(--font-weight-semibold)',
              fontSize: 'var(--font-size-sm)',
            }}
          >
            전체보기 &rarr;
          </Link>
        </div>
        <ProductListWithWishlist products={products} />
      </section>
    </div>
  );
}
