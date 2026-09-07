// DEMO-PUBLIC-DATA: **백엔드가 실제로 돌려주는 모양** 의 픽스처.
//
// =============================================================================
// 🔴🔴 이 파일의 값들은 «가짜 데이터» 가 아니라 **모집단의 대표**여야 한다
// =============================================================================
// 이 저장소가 이름 붙인 함정: *"픽스처가 현실을 안 담으면 초록도 공허하다 — 실물보다
// 관대한 스텁."* 그래서 여기 있는 행은 **실제 데모 시드에서 그대로 가져왔다**:
//
//   아티스트 3행  ← `infra/demo/seed/seed-fan.sh:110-127` (id·활동명·본명·소속·데뷔일·bio)
//   상품 8행      ← `projects/ecommerce-microservices-platform/apps/product-service/
//                     src/main/resources/db/migration/V8__seed_sample_data.sql`
//   옵션 28행     ← 같은 파일 (재고 수량 포함)
//   썸네일        ← 같은 디렉터리 `V9__add_product_thumbnail_url.sql` + `V11__…`
//   카테고리 7행  ← 같은 디렉터리 `V1`/`V8` (표시명 포함)
//
// 🔴🔴 **그리고 여기에는 공개되면 안 되는 필드가 일부러 들어 있다**:
//   · `realName` (아티스트 본명 — 시드의 실제 값)
//   · `accountId` / `tenantId`
//   · `stock` (옵션별 재고 — 시드의 실제 수량)
//   · `bodyPreview` + 회원 전용 글의 `body`
//
// 그것이 이 픽스처의 **존재 이유**다. 변환기가 이 필드들을 지우는지 시험하려면 픽스처가
// 그것을 **갖고 있어야** 한다. 지우지 마라 — 지우면 그 시험이 «아무것도 안 하면서 초록»
// 이 된다(모집단이 빈 채로 통과하는 그 모양).
//
// 🔵 이 파일은 저장소 안에만 있고 어떤 배포에도 안 들어간다(`snapshots/` 만 배포된다).
// =============================================================================

/** 팬 게이트웨이 `GET /api/v1/artists` 가 돌려주는 모양. */
export const RAW_ARTISTS = [
  {
    id: '0199de80-0000-7000-8000-00000000a001',
    tenantId: 'fan-platform',
    accountId: '0199de80-0000-7000-8000-00000000a001',
    artistType: 'SOLO',
    status: 'PUBLISHED',
    stageName: '루미',
    realName: '김하늘',
    debutDate: '2021-03-14',
    agency: 'Aurora Entertainment',
    bio: '2021년 데뷔한 솔로 아티스트입니다. 어쿠스틱 기반의 자작곡을 주로 발표합니다.',
    profileImageRef: null,
    createdAt: '2026-01-05T09:00:00Z',
    updatedAt: '2026-01-05T09:00:00Z',
  },
  {
    id: '0199de80-0000-7000-8000-00000000a002',
    tenantId: 'fan-platform',
    accountId: '0199de80-0000-7000-8000-00000000a002',
    artistType: 'SOLO',
    status: 'PUBLISHED',
    stageName: '노아',
    realName: '박서준',
    debutDate: '2019-08-01',
    agency: 'Aurora Entertainment',
    bio: '프로듀서 겸 솔로 아티스트.',
    profileImageRef: null,
    createdAt: '2026-01-05T09:00:00Z',
    updatedAt: '2026-01-05T09:00:00Z',
  },
  {
    id: '0199de80-0000-7000-8000-00000000a003',
    tenantId: 'fan-platform',
    accountId: '0199de80-0000-7000-8000-00000000a003',
    artistType: 'GROUP_MEMBER',
    status: 'PUBLISHED',
    stageName: '세아',
    realName: '이세아',
    debutDate: '2022-05-20',
    agency: 'Aurora Entertainment',
    bio: '그룹 STELLAR 의 리더.',
    profileImageRef: null,
    createdAt: '2026-01-05T09:00:00Z',
    updatedAt: '2026-01-05T09:00:00Z',
  },
  {
    // 🔴 **음성 대조군** — 공개 상태가 아닌 아티스트. 변환기가 이 행을 걸러야 한다.
    //    이 행이 없으면 「거른다」는 성질은 **한 번도 시험되지 않는다.**
    id: '0199de80-0000-7000-8000-00000000a09f',
    tenantId: 'fan-platform',
    accountId: '0199de80-0000-7000-8000-00000000a09f',
    artistType: 'SOLO',
    status: 'DRAFT',
    stageName: '비공개아티스트',
    realName: '최비공',
    debutDate: null,
    agency: null,
    bio: '아직 공개되지 않은 프로필입니다.',
    profileImageRef: null,
    createdAt: '2026-02-01T09:00:00Z',
    updatedAt: '2026-02-01T09:00:00Z',
  },
];

/** 팬 게이트웨이 `GET /api/v1/community/feed` 의 항목 모양(`FeedItem`). */
export const RAW_POSTS = [
  {
    id: '0199de80-0000-7000-8000-00000000b001',
    postId: '0199de80-0000-7000-8000-00000000b001',
    tenantId: 'fan-platform',
    artistId: '0199de80-0000-7000-8000-00000000a001',
    authorAccountId: '0199de80-0000-7000-8000-00000000a001',
    postType: 'TEXT',
    status: 'PUBLISHED',
    visibility: 'PUBLIC',
    title: '첫 정규 앨범 작업을 시작했습니다',
    body: '오랜만에 인사드립니다. 지난 겨울부터 준비해 온 첫 정규 앨범 작업을 시작했어요.\n\n어쿠스틱 기타로 시작한 데모를 밴드 편성으로 옮기는 중인데, 생각보다 시간이 걸리네요. 그래도 한 곡 한 곡 완성될 때마다 들려드리고 싶은 마음이 커집니다.',
    bodyPreview: '오랜만에 인사드립니다. 지난 겨울부터 준비해 온 첫 정규…',
    locked: false,
    likeCount: 128,
    commentCount: 14,
    publishedAt: '2026-02-10T08:00:00Z',
    createdAt: '2026-02-10T08:00:00Z',
  },
  {
    id: '0199de80-0000-7000-8000-00000000b002',
    postId: '0199de80-0000-7000-8000-00000000b002',
    tenantId: 'fan-platform',
    artistId: '0199de80-0000-7000-8000-00000000a002',
    authorAccountId: '0199de80-0000-7000-8000-00000000a002',
    postType: 'TEXT',
    status: 'PUBLISHED',
    visibility: 'PUBLIC',
    title: '프로듀싱 노트 — 드럼 사운드 잡기',
    body: '이번 트랙에서 가장 오래 붙잡고 있던 건 드럼이었습니다.\n\n킥과 베이스가 같은 대역에서 부딪히는 문제를 사이드체인으로 해결했는데, 결과적으로 곡 전체의 그루브가 달라졌어요.',
    bodyPreview: '이번 트랙에서 가장 오래 붙잡고 있던 건 드럼이었습니다…',
    locked: false,
    likeCount: 76,
    commentCount: 9,
    publishedAt: '2026-02-08T11:30:00Z',
    createdAt: '2026-02-08T11:30:00Z',
  },
  {
    id: '0199de80-0000-7000-8000-00000000b003',
    postId: '0199de80-0000-7000-8000-00000000b003',
    tenantId: 'fan-platform',
    artistId: '0199de80-0000-7000-8000-00000000a003',
    authorAccountId: '0199de80-0000-7000-8000-00000000a003',
    postType: 'TEXT',
    status: 'PUBLISHED',
    visibility: 'PUBLIC',
    title: 'STELLAR 컴백 준비 현장',
    body: '연습실에서 인사드려요! 컴백 안무 연습이 한창입니다.\n\n이번에는 멤버 전원이 안무 창작에 참여했어요. 무대에서 보여드릴 날이 기다려집니다.',
    bodyPreview: '연습실에서 인사드려요! 컴백 안무 연습이 한창입니다…',
    locked: false,
    likeCount: 342,
    commentCount: 51,
    publishedAt: '2026-02-11T02:15:00Z',
    createdAt: '2026-02-11T02:15:00Z',
  },
  {
    // 🔴🔴 **음성 대조군 — 회원 전용 글.** 변환기가 `body` 와 `bodyPreview` 를 **둘 다**
    //    지워야 한다. 이 행이 없으면 「회원 전용 본문은 공개 JSON 에 없다」는 명제가
    //    **한 번도 시험되지 않는다.** 본문에 알아보기 쉬운 표지를 넣어 둔 이유는, 시험이
    //    그 문자열을 산출물 전체에서 grep 으로 찾아 «없음» 을 확인하기 때문이다.
    id: '0199de80-0000-7000-8000-00000000b004',
    postId: '0199de80-0000-7000-8000-00000000b004',
    tenantId: 'fan-platform',
    artistId: '0199de80-0000-7000-8000-00000000a001',
    authorAccountId: '0199de80-0000-7000-8000-00000000a001',
    postType: 'TEXT',
    status: 'PUBLISHED',
    visibility: 'MEMBERS_ONLY',
    title: '[멤버십] 미공개 데모 트랙 이야기',
    body: 'MEMBERS-ONLY-BODY-MUST-NOT-LEAK 멤버십 회원분들께만 공개하는 미공개 데모 이야기입니다.',
    bodyPreview: 'MEMBERS-ONLY-PREVIEW-MUST-NOT-LEAK 멤버십 회원분들께만…',
    locked: true,
    likeCount: 61,
    commentCount: 7,
    publishedAt: '2026-02-09T05:00:00Z',
    createdAt: '2026-02-09T05:00:00Z',
    imageRefs: ['http://minio.demo.invalid/fan/members-only-1.jpg'],
  },
  {
    // 🔴 **음성 대조군 — 삭제된 글.**
    id: '0199de80-0000-7000-8000-00000000b09f',
    postId: '0199de80-0000-7000-8000-00000000b09f',
    tenantId: 'fan-platform',
    artistId: '0199de80-0000-7000-8000-00000000a002',
    authorAccountId: '0199de80-0000-7000-8000-00000000a002',
    postType: 'TEXT',
    status: 'DELETED',
    visibility: 'PUBLIC',
    title: 'DELETED-POST-MUST-NOT-LEAK',
    body: 'DELETED-POST-BODY-MUST-NOT-LEAK',
    bodyPreview: 'DELETED-POST-BODY-MUST-NOT-LEAK',
    locked: false,
    likeCount: 0,
    commentCount: 0,
    publishedAt: '2026-01-20T00:00:00Z',
    createdAt: '2026-01-20T00:00:00Z',
  },
  {
    // 🔴 **음성 대조군 — 걸러진 아티스트(DRAFT)의 글.** 아티스트가 안 나가면 그 글도 안
    //    나가야 한다. 안 그러면 화면이 «알 수 없는 아티스트» 의 글을 그린다.
    id: '0199de80-0000-7000-8000-00000000b0a0',
    postId: '0199de80-0000-7000-8000-00000000b0a0',
    tenantId: 'fan-platform',
    artistId: '0199de80-0000-7000-8000-00000000a09f',
    authorAccountId: '0199de80-0000-7000-8000-00000000a09f',
    postType: 'TEXT',
    status: 'PUBLISHED',
    visibility: 'PUBLIC',
    title: 'ORPHAN-POST-MUST-NOT-LEAK',
    body: 'ORPHAN-POST-BODY-MUST-NOT-LEAK',
    bodyPreview: 'ORPHAN-POST-BODY-MUST-NOT-LEAK',
    locked: false,
    likeCount: 0,
    commentCount: 0,
    publishedAt: '2026-02-02T00:00:00Z',
    createdAt: '2026-02-02T00:00:00Z',
  },
];

/**
 * 카테고리 표시명.
 *
 * 🔴 **백엔드에 카테고리 조회 API 가 없다**(실측: product-service 에 `CategoryJpaEntity` 는
 *    있으나 컨트롤러 매핑이 없고, `packages/api-client` 에도 category 서비스가 없다).
 *    그래서 표시명은 조회할 수 없고, 이 목록은 `V8__seed_sample_data.sql` 의 값을 저장소가
 *    들고 있는 것이다. 발행자는 백엔드에서 못 얻으면 id 를 사람이 읽게 다듬는다
 *    (`humanizeCategoryId`) — **없는 것을 있는 척하지 않는다.**
 */
export const CATEGORY_NAMES = {
  'a0000000-0000-0000-0000-000000000001': '의류',
  'a0000000-0000-0000-0000-000000000002': '전자기기',
  'a0000000-0000-0000-0000-000000000003': '식품',
  'a0000000-0000-0000-0000-000000000004': '상의',
  'a0000000-0000-0000-0000-000000000005': '하의',
  'a0000000-0000-0000-0000-000000000006': '스마트폰',
  'a0000000-0000-0000-0000-000000000007': '노트북',
};

const P = 'b0000000-0000-0000-0000-0000000000';
const C = 'c0000000-0000-0000-0000-0000000000';
const CAT = 'a0000000-0000-0000-0000-0000000000';
const U = (id) => `https://images.unsplash.com/photo-${id}?w=600&q=80&auto=format&fit=crop`;

/** 스토어 게이트웨이 `GET /api/products/{id}` 의 모양(`ProductDetail`). 재고 포함. */
export const RAW_PRODUCTS = [
  {
    id: `${P}01`, name: '베이직 코튼 티셔츠', description: '부드러운 코튼 100% 소재의 베이직 티셔츠입니다.',
    price: 29000, status: 'ON_SALE', categoryId: `${CAT}04`, createdAt: '2026-01-02T00:00:00Z',
    // V11 이 V9 의 값을 덮어썼다 — 최신 마이그레이션의 값을 쓴다.
    thumbnailUrl: U('1618354691373-d851c5c3a990'), images: [],
    variants: [
      { id: `${C}01`, optionName: 'S', stock: 50, additionalPrice: 0 },
      { id: `${C}02`, optionName: 'M', stock: 100, additionalPrice: 0 },
      { id: `${C}03`, optionName: 'L', stock: 80, additionalPrice: 0 },
      { id: `${C}04`, optionName: 'XL', stock: 30, additionalPrice: 2000 },
    ],
  },
  {
    id: `${P}02`, name: '슬림핏 데님 청바지', description: '스트레치 소재로 편안한 착용감의 슬림핏 청바지입니다.',
    price: 59000, status: 'ON_SALE', categoryId: `${CAT}05`, createdAt: '2026-01-03T00:00:00Z',
    thumbnailUrl: U('1542272604-787c3835535d'), images: [],
    variants: [
      { id: `${C}05`, optionName: '28', stock: 40, additionalPrice: 0 },
      { id: `${C}06`, optionName: '30', stock: 60, additionalPrice: 0 },
      { id: `${C}07`, optionName: '32', stock: 50, additionalPrice: 0 },
      { id: `${C}08`, optionName: '34', stock: 20, additionalPrice: 0 },
    ],
  },
  {
    id: `${P}03`, name: '갤럭시 S25 울트라', description: '최신 AI 기능이 탑재된 프리미엄 스마트폰입니다.',
    price: 1590000, status: 'ON_SALE', categoryId: `${CAT}06`, createdAt: '2026-01-04T00:00:00Z',
    thumbnailUrl: U('1610945415295-d9bbf067e59c'), images: [],
    variants: [
      { id: `${C}09`, optionName: '256GB 블랙', stock: 30, additionalPrice: 0 },
      { id: `${C}10`, optionName: '512GB 블랙', stock: 20, additionalPrice: 200000 },
      { id: `${C}11`, optionName: '256GB 화이트', stock: 25, additionalPrice: 0 },
      { id: `${C}12`, optionName: '512GB 화이트', stock: 15, additionalPrice: 200000 },
    ],
  },
  {
    id: `${P}04`, name: '맥북 프로 16인치 M4', description: 'M4 칩셋 탑재 프로페셔널 노트북입니다.',
    price: 3490000, status: 'ON_SALE', categoryId: `${CAT}07`, createdAt: '2026-01-05T00:00:00Z',
    thumbnailUrl: U('1541807084-5c52b6b3adef'), images: [],
    variants: [
      { id: `${C}13`, optionName: '36GB/512GB', stock: 15, additionalPrice: 0 },
      { id: `${C}14`, optionName: '36GB/1TB', stock: 10, additionalPrice: 300000 },
      { id: `${C}15`, optionName: '48GB/1TB', stock: 8, additionalPrice: 600000 },
    ],
  },
  {
    id: `${P}05`, name: '오버핏 후드 집업', description: '유니섹스 오버핏 디자인의 후드 집업입니다.',
    price: 45000, status: 'ON_SALE', categoryId: `${CAT}04`, createdAt: '2026-01-06T00:00:00Z',
    thumbnailUrl: U('1556821840-3a63f95609a7'), images: [],
    variants: [
      { id: `${C}16`, optionName: 'M 블랙', stock: 70, additionalPrice: 0 },
      { id: `${C}17`, optionName: 'L 블랙', stock: 60, additionalPrice: 0 },
      { id: `${C}18`, optionName: 'M 그레이', stock: 50, additionalPrice: 0 },
      { id: `${C}19`, optionName: 'L 그레이', stock: 40, additionalPrice: 0 },
    ],
  },
  {
    id: `${P}06`, name: '프리미엄 견과류 선물세트', description: '아몬드, 캐슈넛, 호두, 마카다미아 프리미엄 견과류 세트입니다.',
    price: 35000, status: 'ON_SALE', categoryId: `${CAT}03`, createdAt: '2026-01-07T00:00:00Z',
    thumbnailUrl: U('1599599810769-bcde5a160d32'), images: [],
    variants: [
      { id: `${C}20`, optionName: '500g', stock: 100, additionalPrice: 0 },
      { id: `${C}21`, optionName: '1kg', stock: 50, additionalPrice: 30000 },
    ],
  },
  {
    id: `${P}07`, name: '프로 무선 이어폰', description: '노이즈캔슬링 기능이 탑재된 무선 이어폰입니다.',
    price: 320000, status: 'ON_SALE', categoryId: `${CAT}02`, createdAt: '2026-01-08T00:00:00Z',
    thumbnailUrl: U('1590658268037-6bf12165a8df'), images: [],
    variants: [
      { id: `${C}22`, optionName: '화이트', stock: 45, additionalPrice: 0 },
      { id: `${C}23`, optionName: '블랙', stock: 55, additionalPrice: 0 },
    ],
  },
  {
    id: `${P}08`, name: '와이드핏 슬랙스', description: '구김이 적은 폴리 소재의 와이드핏 슬랙스입니다.',
    price: 39000, status: 'ON_SALE', categoryId: `${CAT}05`, createdAt: '2026-01-09T00:00:00Z',
    thumbnailUrl: U('1624378439575-d8705ad7ae80'), images: [],
    variants: [
      { id: `${C}24`, optionName: 'S 블랙', stock: 35, additionalPrice: 0 },
      { id: `${C}25`, optionName: 'M 블랙', stock: 50, additionalPrice: 0 },
      { id: `${C}26`, optionName: 'L 블랙', stock: 40, additionalPrice: 0 },
      { id: `${C}27`, optionName: 'S 베이지', stock: 30, additionalPrice: 0 },
      { id: `${C}28`, optionName: 'M 베이지', stock: 45, additionalPrice: 0 },
    ],
  },
  {
    // 🔴 **음성 대조군 — 숨김 상품.** 변환기가 걸러야 한다.
    id: `${P}9f`, name: 'HIDDEN-PRODUCT-MUST-NOT-LEAK', description: 'HIDDEN-DESC-MUST-NOT-LEAK',
    price: 1000, status: 'HIDDEN', categoryId: `${CAT}01`, createdAt: '2026-01-10T00:00:00Z',
    thumbnailUrl: null, images: [], variants: [{ id: `${C}9f`, optionName: '기본', stock: 5, additionalPrice: 0 }],
  },
];
