// DEMO-PUBLIC-DATA-CONSUMER: web-store
//
// 공개 저장본의 DTO → 이 앱이 이미 쓰는 화면 타입으로의 번역. **여기 한 곳뿐이다** —
// 목록·상세·검색이 각자 번역하면 같은 상품이 세 화면에서 다른 모양이 된다.
//
// 🔴 번역은 **필드 단위로 손으로** 쓴다. `...p` 로 퍼뜨리지 않는다: 저장본에 필드가 하나
//    늘어나는 날 그것이 자동으로 화면 타입에 실리고, 그 순간 이 파일은 아무 일도 안 한다
//    (발행자 쪽 `datasets.ts` 가 같은 이유로 같은 규율을 쓴다).

import type { PublicCategory, PublicProduct } from '@demo/public-data';
import type { CategoryFacet, ProductSummary } from '@repo/types';
import type { ProductDetailView, ProductVariantView } from '../model/types';
import { fallbackImages, fallbackThumbnail } from './fallback-images';

/**
 * 저장본 상세 — `ProductDetailView` 에 저장본만 아는 두 가지를 더한 것.
 *
 * 🔴 `fromSnapshot` 은 «장식» 이 아니라 화면의 표시가격 문구가 걸리는 **판정 값**이다.
 *    라이브 백엔드 경로가 되살아나면 그쪽은 이 필드를 안 붙이고, 그러면 문구도 안 뜬다.
 */
export interface StoreProductDetail extends ProductDetailView {
  /** 저장본의 카테고리 표시명. 🔵 백엔드 상세 응답에는 없다(id 만 온다). */
  categoryName: string | null;
  fromSnapshot: true;
}

export function toProductSummary(p: PublicProduct): ProductSummary {
  return {
    id: p.id,
    name: p.name,
    // `ProductSummary.thumbnailUrl` 은 필수 string 인데 저장본은 null 일 수 있다.
    // 폴백 이미지는 목 **데이터**가 아니라 placeholder **이미지 URL** 이다(fallback-images.ts).
    thumbnailUrl: p.thumbnailUrl || fallbackThumbnail(p.name),
    price: p.price,
    status: p.status,
    categoryId: p.categoryId,
  };
}

export function toProductDetail(
  p: PublicProduct,
  categories: PublicCategory[],
): StoreProductDetail {
  const images = p.images.length > 0
    ? p.images
        .slice()
        .sort((a, b) => a.sortOrder - b.sortOrder)
        .map((img) => ({
          imageId: `${p.id}-${img.sortOrder}`,
          url: img.url,
          sortOrder: img.sortOrder,
          isPrimary: img.isPrimary,
        }))
    : (p.thumbnailUrl ? [p.thumbnailUrl] : fallbackImages(p.name)).map((url, i) => ({
        imageId: `fallback-${i}`,
        url,
        sortOrder: i,
        isPrimary: i === 0,
      }));

  const variants: ProductVariantView[] = p.options.map((o) => ({
    id: o.id,
    optionName: o.optionName,
    additionalPrice: o.additionalPrice,
    // 🔴🔴 여기에 숫자를 넣지 마라. 저장본에는 재고가 **없고**, 없는 것을 0 이나 임의의
    //    양수로 채우면 화면이 «품절» 또는 «재고 있음» 이라는 **없는 사실**을 말한다.
    stock: null,
  }));

  return {
    id: p.id,
    name: p.name,
    // `ProductDetail.description` 은 필수 string, 저장본은 null 가능.
    description: p.description ?? '',
    status: p.status,
    price: p.price,
    categoryId: p.categoryId,
    thumbnailUrl: p.thumbnailUrl ?? undefined,
    images,
    variants,
    categoryName: categories.find((c) => c.id === p.categoryId)?.name ?? null,
    fromSnapshot: true,
  };
}

/**
 * 패싯(id + 건수)에 저장본의 카테고리 **표시명**을 붙인다.
 *
 * 🔴 이름을 못 찾으면 `null` 을 그대로 둔다 — `CategoryFacet.name` 이 nullable 인 이유가
 *    바로 그것이고(백엔드 검색 인덱스에도 이름이 없다), 화면이 이미 `?? '기타'` 로 받는다.
 *    여기서 id 를 이름 자리에 밀어 넣으면 UUID 가 카테고리 이름으로 보인다.
 */
export function toCategoryFacets(
  facets: Array<{ id: string; count: number }>,
  categories: PublicCategory[],
): CategoryFacet[] {
  return facets.map((f) => ({
    id: f.id,
    name: categories.find((c) => c.id === f.id)?.name ?? null,
    count: f.count,
  }));
}
