// DEMO-PUBLIC-DATA: 데이터셋별 **공개 필드 허용 목록**. 이 파일이 "무엇이 공개인가" 의 정본이다.
//
// =============================================================================
// 공개 DTO — 허용 목록이지 제외 목록이 아니다 (ADR-MONO-070 § D2)
// =============================================================================
// 🔴🔴 **백엔드 응답을 그대로 저장하지 않는다.** 제외 목록으로 짜면 백엔드에 필드가 하나
//    늘어나는 날 그것이 **자동으로 공개된다** — 그 순간 아무 가드도 안 문다. 그래서 방향을
//    뒤집는다: 여기 적힌 것만 나가고, 나머지는 **적지 않아서** 안 나간다.
//
// 아래 타입들이 곧 그 목록이다. 발행자(`extract.ts`)는 이 타입으로 **다시 조립**하며,
// 원본 객체를 spread 하지 않는다(`...raw` 는 이 파일 전체를 무효로 만든다 — 가드가 문다).
//
// -----------------------------------------------------------------------------
// 명시적으로 **오지 않는 것** — 그리고 각각 왜
// -----------------------------------------------------------------------------
//   토큰·세션·인증 정보     발행자는 공개 엔드포인트만 읽고, 봉투에 헤더를 안 싣는다.
//   accountId / tenantId    테넌트 축 전체. § 다중 테넌트 참조.
//   realName (아티스트)     활동명(stageName)은 공개지만 본명은 개인정보다.
//   이메일·주소·연락처       어떤 데이터셋에도 자리가 없다.
//   주문·결제·알림 내역      개인 데이터. 공개 화면이 그것을 그릴 이유가 없다.
//   비공개·삭제·미승인 글    발행자가 `status`/`visibility` 로 **미리 거른다**.
//   회원 전용 **본문**       § 회원 전용 콘텐츠 참조 — 미리보기조차 싣지 않는다.
//   재고 수량               § 재고 참조 — 저장된 수를 실시간처럼 보이게 하지 않는다.
//   내부 운영·권한 데이터    콘솔 샘플은 **합성**이다(백엔드에서 뽑지 않는다).
//
// -----------------------------------------------------------------------------
// 🔴🔴 다중 테넌트 — 공개 범위를 **파라미터가 아니라 발행 시점**에 정한다
// -----------------------------------------------------------------------------
// 요구: *"요청자가 임의의 tenant ID 를 넣어 다른 테넌트 데이터를 추출할 수 없게 한다."*
//
// 그 성질을 «검사» 로 얻지 않는다. 검사는 빠뜨릴 수 있고, 빠뜨린 검사는 조용하다. 대신
// **읽는 경로에 테넌트 파라미터를 만들지 않는다**:
//
//   · 발행자는 `--tenant <slug>` 하나를 받아 **그 테넌트만** 뽑는다(운영자 자격 필요).
//   · 봉투에는 `tenantId` 필드가 **없다** — 아래 타입 어디에도 자리가 없다.
//   · 판독자는 데이터셋 이름만 받는다. 테넌트를 받는 인자가 **존재하지 않는다.**
//
// ⇒ "다른 테넌트를 요청한다" 는 표현 자체가 불가능하다. 이것이 검사보다 강한 이유는,
//   새 화면을 추가하는 사람이 검사를 **잊을 수는 있어도** 없는 인자를 넘길 수는 없기
//   때문이다.
// =============================================================================

// ---------------------------------------------------------------------------
// fan — 팬 플랫폼
// ---------------------------------------------------------------------------

/**
 * 공개 아티스트.
 *
 * 🔴 `realName` 이 없다. `projects/fan-platform/web/fan-platform-web/src/entities/artist/types.ts`
 *    의 `Artist` 에는 있고, 이 저장소의 공개 데이터에는 **없다.** 두 타입이 다른 것이
 *    의도다 — 같으면 이 파일은 아무 일도 안 한다.
 * 🔴 `accountId` / `tenantId` 도 없다(§ 다중 테넌트).
 */
export interface PublicArtist {
  id: string;
  stageName: string;
  artistType: 'SOLO' | 'GROUP_MEMBER';
  agency: string | null;
  debutDate: string | null;
  bio: string | null;
  /** 영속 저장소로 **복사된** 주소. 🔴 EC2/MinIO 주소가 여기 오면 안 된다 — 가드가 문다. */
  profileImageUrl: string | null;
  /**
   * 브라우저 검색용 소문자 결합 문자열. 화면이 매번 조립하지 않게 발행 시점에 만든다.
   * 🔵 여기에 들어가는 것은 **위 필드들뿐**이다 — 검색용이라는 이유로 비공개 필드를 섞으면
   *    그것이 공개 JSON 에 실린다(이 파일이 막는 실패의 가장 흔한 모양).
   */
  searchText: string;
}

/**
 * 공개 게시물.
 *
 * 🔴🔴 **회원 전용 콘텐츠** — `visibility !== 'PUBLIC'` 인 글은 `body` 가 `null` 이고
 *    `bodyPreview` 도 **싣지 않는다.** 백엔드의 `FeedItem` 에는 `bodyPreview` 가 있지만,
 *    그것이 무엇을 담는지는 community-service 의 리댁션 규칙에 달려 있고 이 저장소의 공개
 *    저장본이 그 규칙에 **의존해서는 안 된다.** 확인되지 않은 리댁션을 신뢰하는 것과
 *    아무것도 안 싣는 것 중, 되돌릴 수 없는 쪽은 전자다.
 *    ⇒ 잠긴 글은 **제목과 잠김 표시만** 공개한다. 화면은 그것으로 "가입하면 볼 수 있다" 를
 *      말할 수 있고, 본문은 공개 JSON·HTML·RSC 응답 어디에도 없다.
 */
export interface PublicPost {
  id: string;
  artistId: string;
  artistStageName: string;
  title: string;
  /** `visibility === 'PUBLIC'` 일 때만 값이 있다. 그 외에는 항상 `null`. */
  body: string | null;
  visibility: 'PUBLIC' | 'MEMBERS_ONLY' | 'PREMIUM';
  /** `visibility !== 'PUBLIC'` 과 동치. 화면이 두 번 판정하지 않게 발행 시점에 굳힌다. */
  locked: boolean;
  publishedAt: string;
  /** 공개 글의 이미지만. 잠긴 글은 **항상 빈 배열**(경로도 본문이다). */
  imageUrls: string[];
}

/**
 * 멤버십 요금제 안내.
 *
 * 🔴🔴 **백엔드에서 뽑지 않는다.** `GET /api/v1/memberships` 는 *"내 구독"* 을 돌려주는
 *    **개인 데이터** 엔드포인트다(`Membership` DTO 에 accountId·결제 상태가 있다). 그것을
 *    공개 저장본의 출처로 삼는 것은 이 파일이 막으려는 바로 그 실패다.
 *    ⇒ 요금제 **안내**는 저장소가 소유한 `authored` 콘텐츠다. 실제 과금·가입은 로그인 뒤
 *      백엔드가 하고, 이 카드는 그 화면으로 보내는 안내판일 뿐이다.
 */
export interface PublicMembershipPlan {
  tier: 'MEMBERS_ONLY' | 'PREMIUM';
  name: string;
  priceKrw: number;
  period: 'MONTHLY';
  benefits: string[];
  /** 🔴 "실제 가입은 로그인 후" 를 화면이 반드시 말하게 하는 자리. */
  note: string;
}

export interface FanPublicData {
  artists: PublicArtist[];
  posts: PublicPost[];
  membershipPlans: PublicMembershipPlan[];
}

// ---------------------------------------------------------------------------
// store — 이커머스 스토어
// ---------------------------------------------------------------------------

export interface PublicProductImage {
  url: string;
  sortOrder: number;
  isPrimary: boolean;
}

/**
 * 공개 상품 옵션.
 *
 * 🔴🔴 **`stock` 이 없다.** 백엔드 `ProductVariant` 에는 있다. 요구사항이 명시한다:
 *    *"저장된 재고를 실시간 재고처럼 표시하지 마라."* 필드를 안 실으면 화면은 그것을
 *    **표시할 수 없고**, 표시할 수 없으면 오인시킬 수도 없다. 「표시하되 주의문구를 단다」
 *    보다 강한 이유가 그것이다 — 문구는 지워지고 필드는 안 지워진다.
 */
export interface PublicProductOption {
  id: string;
  optionName: string;
  additionalPrice: number;
}

/**
 * 공개 상품.
 *
 * 🔴 `price` 는 **표시 가격**이다. 주문·결제 직전에 백엔드가 최신 가격·재고·판매 가능
 *    여부를 다시 확인한다(그 경로는 로그인과 백엔드를 요구한다 — 이 저장본은 거기 안 낀다).
 * 🔴 `status: 'HIDDEN'` 은 발행자가 **거른다.** 여기 올 수 있는 값은 두 개뿐이다.
 */
export interface PublicProduct {
  id: string;
  name: string;
  description: string | null;
  status: 'ON_SALE' | 'SOLD_OUT';
  price: number;
  thumbnailUrl: string | null;
  images: PublicProductImage[];
  categoryId: string;
  options: PublicProductOption[];
  /** 검색·필터용 소문자 결합. 출처는 위 공개 필드뿐(§ PublicArtist.searchText 와 같은 규율). */
  searchText: string;
  /** 정렬 `newest` 의 축. */
  createdAt: string;
}

export interface PublicCategory {
  id: string;
  name: string;
  productCount: number;
}

export interface StorePublicData {
  products: PublicProduct[];
  categories: PublicCategory[];
}

// ---------------------------------------------------------------------------
// console-sample — 운영자 콘솔 둘러보기
// ---------------------------------------------------------------------------

/**
 * 🔴🔴 이 데이터셋은 **합성이다.** `source` 는 항상 `authored` 이고, 발행자에게 이것을
 *    백엔드에서 뽑는 경로가 **없다**(`extract.ts` 에 fan·store 추출기만 있다).
 *
 *    요구사항: *"실제 고객·주문·재무·계정 데이터를 공개 저장본으로 복사하지 않는다."*
 *    콘솔은 **정의상** 그런 데이터만 그리는 화면이므로, 「허용 목록을 잘 짜서 일부만 뽑는다」
 *    는 접근 자체가 위험하다 — 한 필드만 새면 그것이 실제 운영 데이터다.
 *    ⇒ 축을 바꾼다: 콘솔 샘플은 **백엔드에 원본이 없는 데이터**로 만든다.
 */
export interface ConsoleSampleMetric {
  label: string;
  value: string;
  hint: string;
}

export interface ConsoleSampleColumn {
  key: string;
  label: string;
}

export interface ConsoleSampleTable {
  key: string;
  title: string;
  /** 이 화면이 실제로 무슨 일을 하는지 — 요구사항의 "각 화면의 기능 설명". */
  description: string;
  columns: ConsoleSampleColumn[];
  rows: Array<Record<string, string | number>>;
}

export interface ConsoleSampleDomain {
  key: string;
  label: string;
  description: string;
  /** 실시간 콘솔에서 이 섹션의 경로. 둘러보기에서는 **링크가 아니라 안내**로 쓴다. */
  liveHref: string;
  metrics: ConsoleSampleMetric[];
  tables: ConsoleSampleTable[];
}

export interface ConsoleSampleData {
  domains: ConsoleSampleDomain[];
}

// ---------------------------------------------------------------------------
// 데이터셋 ↔ 타입 매핑
// ---------------------------------------------------------------------------
//
// 🔴 **런타임 검증은 여기 없다.** `validateDatasetData` 는 `contract.mjs` 에 있고, 그
//    이유는 소비자가 둘이기 때문이다 — 판독자(이 TS 트리)와 발행자(node CLI, 빌드 단계
//    없음). 두 벌로 쓰면 한쪽만 고쳐지고, 여기서 그것이 뜻하는 바는 특히 나쁘다:
//    발행자의 허용 목록과 판독자의 검증이 갈라지면 **발행자가 실은 개인정보를 실어
//    보내는데 판독자는 «계약대로» 라고 읽는다.** 이 파일은 타입만 소유한다.

export interface PublicDataByDataset {
  fan: FanPublicData;
  store: StorePublicData;
  'console-sample': ConsoleSampleData;
}

/** 어느 화면의 공개 데이터인가. 데이터셋 하나 = Blob 접두사 하나 = 포인터 하나. */
export type PublicDataset = keyof PublicDataByDataset;
