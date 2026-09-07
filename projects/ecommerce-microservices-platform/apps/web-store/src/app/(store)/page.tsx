import { getProducts } from '@/entities/product';
import { ProductListWithWishlist } from '@/widgets/product-list-with-wishlist';
import { DataProvenanceNotice } from '@/widgets/data-provenance';
import { HeroBanner } from '@/widgets/hero';
import { SnapshotEmptyState } from '@/shared/ui';
import Link from 'next/link';
import type { ProductSummary } from '@repo/types';

export const revalidate = 60;

export default async function HomePage() {
  // 🔵 `.catch(() => [])` 를 유지한다 — 판독자는 백엔드로 안 가지만 번들 시드 파싱 같은
  //    **프로그래밍 오류**까지 이 페이지가 삼키면 안 되는 것도 아니다(홈은 다른 섹션이라도
  //    떠야 한다). 다만 그 catch 는 이제 «백엔드가 죽었다» 를 뜻하지 않는다: 저장본 읽기
  //    실패는 던지지 않고 `degraded` 로 내려오며, 그 사실은 아래 출처 표시가 말한다.
  const result = await getProducts({ page: 0, size: 8 }).catch(() => null);
  const products: ProductSummary[] = result?.content ?? [];

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
          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-4)' }}>
            <DataProvenanceNotice />
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
        </div>
        {products.length === 0 ? (
          <SnapshotEmptyState corpusSize={result?.corpusSize ?? 0} />
        ) : (
          <ProductListWithWishlist products={products} />
        )}
      </section>
    </div>
  );
}
