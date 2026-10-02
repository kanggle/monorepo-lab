import 'server-only';
import { bundledEnvelope, type PublicProduct, type StorePublicData } from '@demo/public-data';
import storeJson from '@demo/public-data/snapshots/store.json';

/**
 * 스토어의 **공개 번들 저장본**에서 상품을 읽는다 — 굿즈 카드(`ADR-MONO-077` 갈래 D · `TASK-MONO-739`)용.
 *
 * 🔴🔴 판독자(`createPublicDataReader`)를 쓰지 않는다 — 번들을 **직접** 읽는다. 갈래 D 는 «스토어의 공개 번들
 *    저장본을 읽기만 한다»(D1)이고, 이 모듈에는 네트워크로 갈 길이 **아예 없다**: env 를 안 읽고, fetch 를
 *    안 하고, 게이트웨이 클라이언트를 임포트하지 않는다. 그래서 익명 방문의 «요청 0» 이 이 모듈 때문에
 *    깨질 수 없다(`read.ts` 의 «부를 수단을 안 준다» 와 같은 규율).
 * 🔴 대가(`ADR-MONO-077` § D5 D 의 남는 위험): 스토어가 Blob 발행본으로 전환하면(`DEMO_PUBLIC_DATA_BASE_URL`)
 *    스토어는 발행본을, 팬은 이 번들을 본다 — 두 사이트가 다른 세대를 볼 수 있다. 오늘은 미설정이고,
 *    두 사이트의 Vercel 빌드 규칙이 이 패키지를 **같이** 감시하므로 번들끼리는 같은 커밋으로 배포된다.
 * 🔴 `bundledEnvelope` 는 저장본이 계약을 어기면 **모듈 로드 때 throw** 한다 — 조용히 빈 굿즈로 가면
 *    «이 아티스트는 굿즈가 없다» 와 «저장본이 깨졌다» 가 구별되지 않는다.
 * 🔵 `server-only` — 상품 저장본 전체를 클라이언트 청크에 싣지 않는다. 페이지 테스트는 이 모듈 id 를
 *    `vi.mock` 으로 갈아끼우고 그 자리에서 같은 번들을 읽는다(`read.ts` 와 같은 방식).
 */
const storeData = bundledEnvelope('store', storeJson).data as StorePublicData;

export function readStoreProducts(): readonly PublicProduct[] {
  return storeData.products;
}
