import type { PublicProduct } from '@demo/public-data';
import { storeArtistGoodsHref, storeProductHref } from '@/shared/config/store-links';

/**
 * 아티스트 공식 굿즈 카드 — `ADR-MONO-077` 갈래 D · `TASK-MONO-739`.
 *
 * 🔴🔴 **서버 컴포넌트다**(`'use client'` 없음, D4). 카드와 「전체 보기」는 평범한 `<a>` 이고 상태도
 *    이벤트 핸들러도 없다 — 그래서 이 조각이 클라이언트 경계를 새로 만들지 않는다.
 * 🔴 굿즈는 **스토어의** 상품이다(D1). 팬은 카드를 그리고 스토어로 보낼 뿐 — 가격·재고·주문은 스토어가 말한다.
 *    가격은 저장본 시점의 값이고, 재고는 공개 저장본에 아예 없다(변환기가 안 싣는다).
 * 🔵 외부 사이트로 나간다는 것을 **글자로** 말한다(「↗」, D4). 같은 탭으로 연다(R3 — `target` 없음).
 * 🔵 굿즈가 0개면 아무것도 안 그린다 — 「굿즈가 없습니다」 문구는 «스토어에 그 아티스트 굿즈가 없다» 를
 *    단정하게 되는데, 팬이 아는 것은 «번들 저장본에 없다» 뿐이다.
 * 🔵 이미지는 `<img>` 다 — 이 앱은 `images.unoptimized` 이고 `next/image` 를 쓰지 않는다(`PublicArtistAvatar`).
 */
export function PublicArtistGoods({
  stageName,
  goods,
}: {
  stageName: string;
  goods: readonly PublicProduct[];
}) {
  if (goods.length === 0) return null;
  return (
    <section data-testid="artist-goods">
      <div className="mb-4 flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <h2 className="text-lg font-semibold text-ink-900">공식 굿즈</h2>
        <a
          href={storeArtistGoodsHref(stageName)}
          data-testid="artist-goods-all"
          className="whitespace-nowrap text-sm text-brand-600 hover:text-brand-700"
        >
          굿즈샵에서 전체 보기 ↗
        </a>
      </div>
      <ul className="grid grid-cols-2 gap-4 sm:grid-cols-3">
        {goods.map((p) => (
          <li key={p.id}>
            <a
              href={storeProductHref(p.id)}
              data-testid="artist-goods-card"
              className="group flex h-full flex-col overflow-hidden rounded-xl border border-ink-200 bg-white shadow-sm transition-shadow hover:shadow-md dark:bg-ink-900 dark:border-ink-800"
            >
              {p.thumbnailUrl ? (
                // eslint-disable-next-line @next/next/no-img-element -- 이 앱은 next/image 를 쓰지 않는다(images.unoptimized)
                <img
                  src={p.thumbnailUrl}
                  alt={p.name}
                  loading="lazy"
                  className="aspect-square w-full object-cover"
                />
              ) : (
                <div className="aspect-square w-full bg-ink-100" aria-hidden="true" />
              )}
              <div className="flex flex-1 flex-col gap-1 p-3">
                <p className="text-sm font-medium text-ink-900 group-hover:text-brand-600 dark:text-ink-100">
                  {p.name}
                </p>
                <p className="text-sm text-ink-700 dark:text-ink-300">
                  {p.price.toLocaleString('ko-KR')}원
                  {p.status === 'SOLD_OUT' ? <span className="ml-2 text-xs text-ink-500">품절</span> : null}
                </p>
                <p className="mt-auto text-xs text-ink-500">굿즈샵에서 보기 ↗</p>
              </div>
            </a>
          </li>
        ))}
      </ul>
    </section>
  );
}
