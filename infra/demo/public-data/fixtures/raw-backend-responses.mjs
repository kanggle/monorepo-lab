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
    profileImageRef: 'https://images.unsplash.com/photo-1618673747378-7e0d3561371a?w=400&h=400&q=80&auto=format&fit=crop&crop=faces',
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
    profileImageRef: 'https://images.unsplash.com/photo-1675859427928-fe41277572b4?w=400&h=400&q=80&auto=format&fit=crop&crop=faces',
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
    profileImageRef: 'https://images.unsplash.com/photo-1659150140178-d672b4763fd0?w=400&h=400&q=80&auto=format&fit=crop&crop=faces',
    createdAt: '2026-01-05T09:00:00Z',
    updatedAt: '2026-01-05T09:00:00Z',
  },
  // ── TASK-MONO-638 — 아티스트를 셋에서 여섯으로 늘린다 ────────────────────────
  // 🔴 셋으로는 「아티스트 목록」이 목록으로 안 보인다. 그리고 잠긴 글이 한 아티스트에게만
  //    있어서 «로그인하면 더 있다» 가 화면에서 거의 참이 아니었다 — 아래에서 여섯 명 모두
  //    공개 1 + 잠금 1 을 갖게 한다.
  // 🔵 프로필 사진은 **소유자 결정(2026-09-08)**: 라이선스 인물사진(Unsplash). 스토어 상품
  //    이미지와 같은 CDN·같은 계약이라 새 의존이 안 생긴다.
  // 🔴🔴 **실존 인물을 참고하지 않았다.** 이 아티스트들은 가공 인물이고, 실존 연예인을 닮게
  //    만들면 실존 인물의 초상이 포트폴리오에 들어간다. 필요한 것은 «그럴듯한 아티스트
  //    사진» 이지 «누군가를 닮은 사진» 이 아니다.
  // 🔴 사진 주소는 지어내지 않았다 — 후보를 전부 찔러 200 인 것만 남겼다(56개 중 43개).
  {
    id: '0199de80-0000-7000-8000-00000000a004',
    tenantId: 'fan-platform',
    accountId: '0199de80-0000-7000-8000-00000000a004',
    artistType: 'SOLO',
    status: 'PUBLISHED',
    stageName: '하린',
    realName: '정하린',
    debutDate: '2023-09-08',
    agency: 'Aurora Entertainment',
    bio: '신스팝 기반의 솔로 아티스트입니다. 직접 편곡한 무대를 자주 올립니다.',
    profileImageRef: 'https://images.unsplash.com/photo-1620653616528-7da9a2005478?w=400&h=400&q=80&auto=format&fit=crop&crop=faces',
    createdAt: '2026-01-05T09:00:00Z',
    updatedAt: '2026-01-05T09:00:00Z',
  },
  {
    id: '0199de80-0000-7000-8000-00000000a005',
    tenantId: 'fan-platform',
    accountId: '0199de80-0000-7000-8000-00000000a005',
    artistType: 'GROUP_MEMBER',
    status: 'PUBLISHED',
    stageName: '리오',
    realName: '강리오',
    debutDate: '2022-05-20',
    agency: 'Aurora Entertainment',
    bio: '그룹 STELLAR 의 메인 보컬. 커버 무대와 라이브 클립을 자주 올립니다.',
    profileImageRef: 'https://images.unsplash.com/photo-1619361368198-53f950a51dfa?w=400&h=400&q=80&auto=format&fit=crop&crop=faces',
    createdAt: '2026-01-05T09:00:00Z',
    updatedAt: '2026-01-05T09:00:00Z',
  },
  {
    id: '0199de80-0000-7000-8000-00000000a006',
    tenantId: 'fan-platform',
    accountId: '0199de80-0000-7000-8000-00000000a006',
    artistType: 'SOLO',
    status: 'PUBLISHED',
    stageName: '유노',
    realName: '오유노',
    debutDate: '2020-11-02',
    agency: 'Nova Sound',
    bio: '재즈와 알앤비를 오가는 싱어송라이터입니다.',
    profileImageRef: 'https://images.unsplash.com/photo-1619361369140-33c01702f9cc?w=400&h=400&q=80&auto=format&fit=crop&crop=faces',
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
  // ── TASK-MONO-638 — 여섯 아티스트가 각각 «공개 1 + 잠금 1» 을 갖는다 ─────────────
  // 🔴 잠긴 글이 있어야 «로그인하면 더 있다» 가 화면에서 참이 된다. 예전에는 잠긴 글이
  //    한 아티스트에게만 있어서, 나머지 카드는 그 사실을 보여 줄 방법이 없었다.
  // 🔴🔴 잠긴 글은 변환기가 `body` 와 `bodyPreview` 를 **둘 다** 지운다(ADR-MONO-070).
  //    그러므로 아래 잠긴 글들의 본문은 **누출 대조군이기도 하다** — 늘어난 만큼 그
  //    성질을 시험하는 표본도 늘어난다.
  {
    id: '0199de80-0000-7000-8000-00000000b005',
    postId: '0199de80-0000-7000-8000-00000000b005',
    tenantId: 'fan-platform',
    artistId: '0199de80-0000-7000-8000-00000000a002',
    authorAccountId: '0199de80-0000-7000-8000-00000000a002',
    postType: 'TEXT',
    status: 'PUBLISHED',
    visibility: 'MEMBERS_ONLY',
    title: '[멤버십] 다음 EP 의 트랙 리스트 초안',
    body: 'MEMBERS-ONLY-BODY-MUST-NOT-LEAK-005',
    bodyPreview: 'MEMBERS-ONLY-PREVIEW-MUST-NOT-LEAK-005',
    locked: true,
    likeCount: 54,
    commentCount: 7,
    publishedAt: '2026-02-11T09:00:00Z',
    createdAt: '2026-02-11T09:00:00Z',
  },
  {
    id: '0199de80-0000-7000-8000-00000000b006',
    postId: '0199de80-0000-7000-8000-00000000b006',
    tenantId: 'fan-platform',
    artistId: '0199de80-0000-7000-8000-00000000a003',
    authorAccountId: '0199de80-0000-7000-8000-00000000a003',
    postType: 'TEXT',
    status: 'PUBLISHED',
    visibility: 'MEMBERS_ONLY',
    title: '[멤버십] 안무 연습실 비하인드',
    body: 'MEMBERS-ONLY-BODY-MUST-NOT-LEAK-006',
    bodyPreview: 'MEMBERS-ONLY-PREVIEW-MUST-NOT-LEAK-006',
    locked: true,
    likeCount: 91,
    commentCount: 12,
    publishedAt: '2026-02-12T09:00:00Z',
    createdAt: '2026-02-12T09:00:00Z',
  },
  {
    id: '0199de80-0000-7000-8000-00000000b007',
    postId: '0199de80-0000-7000-8000-00000000b007',
    tenantId: 'fan-platform',
    artistId: '0199de80-0000-7000-8000-00000000a004',
    authorAccountId: '0199de80-0000-7000-8000-00000000a004',
    postType: 'TEXT',
    status: 'PUBLISHED',
    visibility: 'PUBLIC',
    title: '첫 단독 공연 준비 일지',
    body: '다음 달 첫 단독 공연을 준비하고 있습니다.\n\n세트리스트를 짜면서 데뷔곡을 어디에 둘지 한참 고민했어요. 결국 마지막 앙코르로 옮겼습니다.',
    bodyPreview: '다음 달 첫 단독 공연을 준비하고 있습니다…',
    locked: false,
    likeCount: 143,
    commentCount: 21,
    publishedAt: '2026-02-13T09:00:00Z',
    createdAt: '2026-02-13T09:00:00Z',
  },
  {
    id: '0199de80-0000-7000-8000-00000000b008',
    postId: '0199de80-0000-7000-8000-00000000b008',
    tenantId: 'fan-platform',
    artistId: '0199de80-0000-7000-8000-00000000a004',
    authorAccountId: '0199de80-0000-7000-8000-00000000a004',
    postType: 'TEXT',
    status: 'PUBLISHED',
    visibility: 'MEMBERS_ONLY',
    title: '[멤버십] 리허설 현장 사진',
    body: 'MEMBERS-ONLY-BODY-MUST-NOT-LEAK-008',
    bodyPreview: 'MEMBERS-ONLY-PREVIEW-MUST-NOT-LEAK-008',
    locked: true,
    likeCount: 67,
    commentCount: 8,
    publishedAt: '2026-02-14T09:00:00Z',
    createdAt: '2026-02-14T09:00:00Z',
  },
  {
    id: '0199de80-0000-7000-8000-00000000b009',
    postId: '0199de80-0000-7000-8000-00000000b009',
    tenantId: 'fan-platform',
    artistId: '0199de80-0000-7000-8000-00000000a005',
    authorAccountId: '0199de80-0000-7000-8000-00000000a005',
    postType: 'TEXT',
    status: 'PUBLISHED',
    visibility: 'PUBLIC',
    title: '커버 무대 영상 올렸습니다',
    body: '요청 많았던 곡으로 커버 무대를 준비했습니다.\n\n원곡의 키를 두 음 내려서 불렀는데, 오히려 제 목소리와는 더 맞는 것 같아요.',
    bodyPreview: '요청 많았던 곡으로 커버 무대를 준비했습니다…',
    locked: false,
    likeCount: 208,
    commentCount: 33,
    publishedAt: '2026-02-15T09:00:00Z',
    createdAt: '2026-02-15T09:00:00Z',
  },
  {
    id: '0199de80-0000-7000-8000-00000000b010',
    postId: '0199de80-0000-7000-8000-00000000b010',
    tenantId: 'fan-platform',
    artistId: '0199de80-0000-7000-8000-00000000a005',
    authorAccountId: '0199de80-0000-7000-8000-00000000a005',
    postType: 'TEXT',
    status: 'PUBLISHED',
    visibility: 'MEMBERS_ONLY',
    title: '[멤버십] 연습실 라이브 풀버전',
    body: 'MEMBERS-ONLY-BODY-MUST-NOT-LEAK-010',
    bodyPreview: 'MEMBERS-ONLY-PREVIEW-MUST-NOT-LEAK-010',
    locked: true,
    likeCount: 88,
    commentCount: 15,
    publishedAt: '2026-02-16T09:00:00Z',
    createdAt: '2026-02-16T09:00:00Z',
  },
  {
    id: '0199de80-0000-7000-8000-00000000b011',
    postId: '0199de80-0000-7000-8000-00000000b011',
    tenantId: 'fan-platform',
    artistId: '0199de80-0000-7000-8000-00000000a006',
    authorAccountId: '0199de80-0000-7000-8000-00000000a006',
    postType: 'TEXT',
    status: 'PUBLISHED',
    visibility: 'PUBLIC',
    title: '재즈 편곡 작업 노트',
    body: '이번 곡은 4비트 스윙으로 시작했다가 결국 보사노바로 바꿨습니다.\n\n리듬을 바꾸니 가사의 호흡이 완전히 달라져서, 멜로디도 절반을 다시 썼어요.',
    bodyPreview: '이번 곡은 4비트 스윙으로 시작했다가 결국 보사노바로…',
    locked: false,
    likeCount: 97,
    commentCount: 11,
    publishedAt: '2026-02-17T09:00:00Z',
    createdAt: '2026-02-17T09:00:00Z',
  },
  {
    id: '0199de80-0000-7000-8000-00000000b012',
    postId: '0199de80-0000-7000-8000-00000000b012',
    tenantId: 'fan-platform',
    artistId: '0199de80-0000-7000-8000-00000000a006',
    authorAccountId: '0199de80-0000-7000-8000-00000000a006',
    postType: 'TEXT',
    status: 'PUBLISHED',
    visibility: 'MEMBERS_ONLY',
    title: '[멤버십] 미공개 세션 녹음',
    body: 'MEMBERS-ONLY-BODY-MUST-NOT-LEAK-012',
    bodyPreview: 'MEMBERS-ONLY-PREVIEW-MUST-NOT-LEAK-012',
    locked: true,
    likeCount: 45,
    commentCount: 6,
    publishedAt: '2026-02-18T09:00:00Z',
    createdAt: '2026-02-18T09:00:00Z',
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
  // ===========================================================================
  // TASK-MONO-638 — 카테고리별로 «둘러볼 만한» 양까지 채운다
  // ===========================================================================
  // 🔴 여섯 리프 카테고리가 각각 1~2개뿐이었다. 그 화면은 «카탈로그» 가 아니라
  //    «샘플» 로 읽히고, 방문자는 제품이 그 정도인 줄 안다.
  // 🔴 이미지 주소는 **지어내지 않았다.** Unsplash 검색이 돌려준 실제 id 를 받아
  //    56개 후보를 전부 찔러 200 인 43개만 남기고 그중에서 골랐다(13개는 죽어 있었다).
  //    검증 없이 넣었으면 깨진 이미지가 카탈로그에 박혔을 것이다.
  // 🔵 실제 시드(V19 / h2 V12)에 같은 행이 들어간다 — 두 벌이 갈라지면 방문자가
  //    «로그인했더니 카탈로그가 줄었다» 를 겪는다. 가드 (z36)가 그 축을 대조한다.

  // ── 상의 (a…04) ─────────────────────────────────────────────────────────────
  {
    id: `${P}09`, name: '케이블 니트 스웨터', description: '도톰한 케이블 조직의 라운드넥 니트 스웨터입니다.',
    price: 79000, status: 'ON_SALE', categoryId: `${CAT}04`, createdAt: '2026-01-11T00:00:00Z',
    thumbnailUrl: U('1574201635302-388dd92a4c3f'), images: [],
    variants: [
      { id: `${C}29`, optionName: 'M 아이보리', stock: 40, additionalPrice: 0 },
      { id: `${C}30`, optionName: 'L 아이보리', stock: 35, additionalPrice: 0 },
      { id: `${C}31`, optionName: 'M 차콜', stock: 30, additionalPrice: 0 },
    ],
  },
  {
    id: `${P}10`, name: '릴랙스 스웨트셔츠', description: '기모 안감의 루즈핏 스웨트셔츠입니다.',
    price: 52000, status: 'ON_SALE', categoryId: `${CAT}04`, createdAt: '2026-01-12T00:00:00Z',
    thumbnailUrl: U('1620799140188-3b2a02fd9a77'), images: [],
    variants: [
      { id: `${C}32`, optionName: 'M 그레이', stock: 55, additionalPrice: 0 },
      { id: `${C}33`, optionName: 'L 그레이', stock: 45, additionalPrice: 0 },
      { id: `${C}34`, optionName: 'L 네이비', stock: 25, additionalPrice: 0 },
    ],
  },

  // ── 하의 (a…05) ─────────────────────────────────────────────────────────────
  {
    id: `${P}11`, name: '코튼 치노 팬츠', description: '사계절용 코튼 트윌 원단의 스트레이트 치노입니다.',
    price: 62000, status: 'ON_SALE', categoryId: `${CAT}05`, createdAt: '2026-01-13T00:00:00Z',
    thumbnailUrl: U('1473966968600-fa801b869a1a'), images: [],
    variants: [
      { id: `${C}35`, optionName: '30 베이지', stock: 40, additionalPrice: 0 },
      { id: `${C}36`, optionName: '32 베이지', stock: 45, additionalPrice: 0 },
      { id: `${C}37`, optionName: '32 블랙', stock: 35, additionalPrice: 0 },
    ],
  },
  {
    id: `${P}12`, name: '와이드 슬랙스', description: '드레이프감이 좋은 와이드 핏 슬랙스입니다.',
    price: 68000, status: 'ON_SALE', categoryId: `${CAT}05`, createdAt: '2026-01-14T00:00:00Z',
    thumbnailUrl: U('1584865288642-42078afe6942'), images: [],
    variants: [
      { id: `${C}38`, optionName: 'S 차콜', stock: 30, additionalPrice: 0 },
      { id: `${C}39`, optionName: 'M 차콜', stock: 40, additionalPrice: 0 },
      { id: `${C}40`, optionName: 'M 블랙', stock: 38, additionalPrice: 0 },
    ],
  },

  // ── 전자기기 (a…02) ─────────────────────────────────────────────────────────
  {
    id: `${P}13`, name: '노이즈 캔슬링 무선 이어버드', description: '액티브 노이즈 캔슬링과 30시간 재생을 지원하는 무선 이어버드입니다.',
    price: 189000, status: 'ON_SALE', categoryId: `${CAT}02`, createdAt: '2026-01-15T00:00:00Z',
    thumbnailUrl: U('1572569511254-d8f925fe2cbb'), images: [],
    variants: [
      { id: `${C}41`, optionName: '블랙', stock: 60, additionalPrice: 0 },
      { id: `${C}42`, optionName: '화이트', stock: 45, additionalPrice: 0 },
    ],
  },
  {
    id: `${P}14`, name: '포터블 블루투스 스피커', description: 'IPX7 방수 등급의 휴대용 블루투스 스피커입니다.',
    price: 129000, status: 'ON_SALE', categoryId: `${CAT}02`, createdAt: '2026-01-16T00:00:00Z',
    thumbnailUrl: U('1608043152269-423dbba4e7e1'), images: [],
    variants: [
      { id: `${C}43`, optionName: '차콜', stock: 35, additionalPrice: 0 },
      { id: `${C}44`, optionName: '샌드', stock: 25, additionalPrice: 0 },
    ],
  },
  {
    id: `${P}15`, name: '스마트워치 7', description: '심박·수면·운동 추적을 지원하는 스마트워치입니다.',
    price: 349000, status: 'ON_SALE', categoryId: `${CAT}02`, createdAt: '2026-01-17T00:00:00Z',
    thumbnailUrl: U('1579586337278-3befd40fd17a'), images: [],
    variants: [
      { id: `${C}45`, optionName: '41mm 실버', stock: 30, additionalPrice: 0 },
      { id: `${C}46`, optionName: '45mm 실버', stock: 22, additionalPrice: 40000 },
      { id: `${C}47`, optionName: '45mm 블랙', stock: 18, additionalPrice: 40000 },
    ],
  },

  // ── 식품 (a…03) ─────────────────────────────────────────────────────────────
  {
    id: `${P}16`, name: '스페셜티 원두 500g', description: '에티오피아 싱글 오리진, 미디엄 로스팅 원두입니다.',
    price: 24000, status: 'ON_SALE', categoryId: `${CAT}03`, createdAt: '2026-01-18T00:00:00Z',
    thumbnailUrl: U('1447933601403-0c6688de566e'), images: [],
    variants: [
      { id: `${C}48`, optionName: '홀빈', stock: 80, additionalPrice: 0 },
      { id: `${C}49`, optionName: '분쇄', stock: 60, additionalPrice: 0 },
    ],
  },
  {
    id: `${P}17`, name: '엑스트라 버진 올리브오일 500ml', description: '콜드 프레스 방식으로 착유한 엑스트라 버진 올리브오일입니다.',
    price: 32000, status: 'ON_SALE', categoryId: `${CAT}03`, createdAt: '2026-01-19T00:00:00Z',
    thumbnailUrl: U('1474979266404-7eaacbcd87c5'), images: [],
    variants: [
      { id: `${C}50`, optionName: '500ml', stock: 50, additionalPrice: 0 },
      { id: `${C}51`, optionName: '750ml', stock: 30, additionalPrice: 12000 },
    ],
  },
  {
    id: `${P}18`, name: '유기농 벌꿀 600g', description: '국내 산지에서 채밀한 유기농 아카시아 벌꿀입니다.',
    price: 28000, status: 'ON_SALE', categoryId: `${CAT}03`, createdAt: '2026-01-20T00:00:00Z',
    thumbnailUrl: U('1587049352851-8d4e89133924'), images: [],
    variants: [
      { id: `${C}52`, optionName: '600g', stock: 45, additionalPrice: 0 },
      { id: `${C}53`, optionName: '1.2kg', stock: 20, additionalPrice: 22000 },
    ],
  },

  // ── 스마트폰 (a…06) ─────────────────────────────────────────────────────────
  {
    id: `${P}19`, name: '아이폰 17 프로', description: '티타늄 프레임과 개선된 카메라 시스템을 갖춘 프리미엄 스마트폰입니다.',
    price: 1690000, status: 'ON_SALE', categoryId: `${CAT}06`, createdAt: '2026-01-21T00:00:00Z',
    thumbnailUrl: U('1592890288564-76628a30a657'), images: [],
    variants: [
      { id: `${C}54`, optionName: '256GB 티타늄', stock: 25, additionalPrice: 0 },
      { id: `${C}55`, optionName: '512GB 티타늄', stock: 15, additionalPrice: 250000 },
    ],
  },
  {
    id: `${P}20`, name: '픽셀 10 프로', description: 'AI 사진 보정에 강점을 둔 안드로이드 플래그십입니다.',
    price: 1290000, status: 'ON_SALE', categoryId: `${CAT}06`, createdAt: '2026-01-22T00:00:00Z',
    thumbnailUrl: U('1511707171634-5f897ff02aa9'), images: [],
    variants: [
      { id: `${C}56`, optionName: '128GB', stock: 30, additionalPrice: 0 },
      { id: `${C}57`, optionName: '256GB', stock: 20, additionalPrice: 150000 },
    ],
  },
  {
    id: `${P}21`, name: '폴더블 스마트폰 Z6', description: '펼치면 7.6인치가 되는 폴더블 스마트폰입니다.',
    price: 2090000, status: 'ON_SALE', categoryId: `${CAT}06`, createdAt: '2026-01-23T00:00:00Z',
    thumbnailUrl: U('1598327105666-5b89351aff97'), images: [],
    variants: [
      { id: `${C}58`, optionName: '256GB 그라파이트', stock: 12, additionalPrice: 0 },
      { id: `${C}59`, optionName: '512GB 그라파이트', stock: 8, additionalPrice: 280000 },
    ],
  },

  // ── 노트북 (a…07) ───────────────────────────────────────────────────────────
  {
    id: `${P}22`, name: '울트라북 14인치', description: '1.1kg 무게의 휴대성 중심 14인치 울트라북입니다.',
    price: 1890000, status: 'ON_SALE', categoryId: `${CAT}07`, createdAt: '2026-01-24T00:00:00Z',
    thumbnailUrl: U('1525547719571-a2d4ac8945e2'), images: [],
    variants: [
      { id: `${C}60`, optionName: '16GB/512GB', stock: 20, additionalPrice: 0 },
      { id: `${C}61`, optionName: '32GB/1TB', stock: 10, additionalPrice: 450000 },
    ],
  },
  {
    id: `${P}23`, name: '게이밍 노트북 16인치', description: '고주사율 디스플레이와 외장 그래픽을 갖춘 게이밍 노트북입니다.',
    price: 2590000, status: 'ON_SALE', categoryId: `${CAT}07`, createdAt: '2026-01-25T00:00:00Z',
    thumbnailUrl: U('1496181133206-80ce9b88a853'), images: [],
    variants: [
      { id: `${C}62`, optionName: '16GB/1TB', stock: 14, additionalPrice: 0 },
      { id: `${C}63`, optionName: '32GB/1TB', stock: 9, additionalPrice: 380000 },
    ],
  },
  {
    id: `${P}24`, name: '2-in-1 컨버터블 노트북', description: '360도 힌지와 펜 입력을 지원하는 컨버터블 노트북입니다.',
    price: 1490000, status: 'ON_SALE', categoryId: `${CAT}07`, createdAt: '2026-01-26T00:00:00Z',
    thumbnailUrl: U('1486312338219-ce68d2c6f44d'), images: [],
    variants: [
      { id: `${C}64`, optionName: '16GB/512GB', stock: 18, additionalPrice: 0 },
      { id: `${C}65`, optionName: '16GB/1TB', stock: 11, additionalPrice: 200000 },
    ],
  },
  {
    // 🔴 **음성 대조군 — 숨김 상품.** 변환기가 걸러야 한다.
    id: `${P}9f`, name: 'HIDDEN-PRODUCT-MUST-NOT-LEAK', description: 'HIDDEN-DESC-MUST-NOT-LEAK',
    price: 1000, status: 'HIDDEN', categoryId: `${CAT}01`, createdAt: '2026-01-10T00:00:00Z',
    thumbnailUrl: null, images: [], variants: [{ id: `${C}9f`, optionName: '기본', stock: 5, additionalPrice: 0 }],
  },
];
