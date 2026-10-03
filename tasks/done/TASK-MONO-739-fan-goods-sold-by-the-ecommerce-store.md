# Task ID

TASK-MONO-739

# Title

팬 굿즈를 이커머스 스토어에 시드하고 팬 웹에서 링크한다 (`ADR-MONO-077`)

# Status

done

# Owner

monorepo

# Task Tags

- code
- frontend
- data

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 (시드 세 곳 + 생성기 + 서버 컴포넌트 링크 — 결정은 ADR 이 다 했다)
>
> ⏳ **DO NOT START — AC-0 이 참이 되기 전에는 착수하지 않는다.** AC-0 은 verify-then-act 게이트다.
>
> 🔵 2026-09-29 — AC-0 **참**(`ADR-MONO-077 ACCEPTED — D`). 🔴 다만 소유자가 **로그인 이음새를 먼저** 하라고 지시했다 — 그 작업 뒤에 착수한다.

---

# Dependency Markers

- **선행 (prerequisite)**: `ADR-MONO-077` ACCEPTED(정확형 `ADR-MONO-077 ACCEPTED — <A|B|C|D>`). 갈래가 팬 쪽 링크 모양과 스토어 카테고리 수를 정한다.

# Goal

`ADR-MONO-077` 대로, 아티스트 6명의 실물 굿즈를 이커머스 상품으로 시드하고(공개 저장본 포함), 팬 웹 헤더와(갈래 A·B 면) 아티스트 페이지에서 스토어의 굿즈 목록으로 링크한다.

# Scope

## In Scope

- postgres `V20__seed_artist_goods.sql` · h2 `V13__seed_artist_goods.sql` (카테고리 + 상품 + 변형, postgres 는 `tenant_id` 를 변형 행까지)
- `infra/demo/public-data/fixtures/raw-backend-responses.mjs` 에 같은 행 + 카테고리 이름 → `bin/build-bundled-snapshots.mjs` 로 `snapshots/store.json` 재생성
- 팬 웹: `NEXT_PUBLIC_STORE_URL`(기본 `https://store.hubwang.com`) 설정 모듈 + 헤더 「굿즈샵 ↗」 + (갈래 A·B) 아티스트 프로필 「공식 굿즈 ↗」 / (갈래 D) 아티스트 프로필에 굿즈 **카드** → 스토어 상품 상세, 「전체 보기」 → 그 아티스트 굿즈 목록
- 팬 웹 `VERCEL.md` 환경변수 원장에 한 줄

## Out of Scope

- 스토어·팬의 주문·결제·로그인 코드(`ADR-MONO-077` § 건드리지 않는 것)
- 데모 서버 재굽기(소유자 작업 — § Failure Scenarios 2)
- `ADR-MONO-072` 로그인 이음새

# Acceptance Criteria

- [x] **AC-0 (게이트)** — `docs/adr/ADR-MONO-077-*.md` 의 Status 가 `ACCEPTED` 이고 갈래 letter 가 적혀 있다. 아니면 착수하지 않는다.
- [x] **AC-1** — 세 시드의 상품 id 집합이 같다: `scripts/check-seed-catalogue-parity.sh` rc=0, `build-bundled-snapshots.mjs --check` rc=0.
- [x] **AC-2** — 🔴 링크가 **0건 목록으로 떨어지지 않는다**: 팬 웹이 만드는 스토어 URL 을 아티스트 6명 전부(갈래 C 는 헤더 1개)에 대해 공개 저장본 질의(`queryProducts`)로 풀어 **굿즈만, 1건 이상**임을 단위 시험으로 단언. 시험은 팬 쪽 URL 생성 함수의 출력과 스토어 저장본을 **같이** 읽는다(한쪽만 읽으면 두 프로젝트가 따로 바뀔 때 못 잡는다).
- [x] **AC-3** — `categories[].productCount` 가 실제 공개 상품 수와 일치(`TASK-MONO-638` AC 와 같은 대조).
- [x] **AC-4** — 팬 웹 헤더·아티스트 프로필이 서버 컴포넌트로 남고(`'use client'` 추가 0) 익명 렌더에서 fetch 0회.
- [x] **AC-5** — `check-public-domains.sh` · `check-client-graph-backend-origins.mjs` rc=0.
- [x] **AC-6** — 브라우저(`next start`): 팬 헤더 1280·400px 줄바꿈 이상 없음, 스토어 목록에 굿즈 카드 이미지 렌더.
- [x] **AC-7** — 팬·스토어 유닛·lint·build, `node --test infra/demo/public-data/tests/public-data.test.mjs` rc=0.
- [ ] ⚪ **AC-8** — postgres `V21`(749 로 밀림) 이 **기존 볼륨**(V20 까지 적용된 DB)에 적용된다 — Testcontainers 로 V20 까지 올린 뒤 V21 을 적용하거나, 불가하면 ⚪ 로 «못 쟀다, 이유» 를 적는다(`TASK-MONO-638` 의 V19 결함 부류).
- [x] **AC-9 (라이더, 구현자 기본값)** — `ADR-MONO-077` § 라이더 대조가 승격: 굿즈 아티스트당 3개·총 18개(R1) · 이미지 `placehold.co`(R2) · 링크는 같은 탭(R3). 소유자가 한 줄로 뒤집을 수 있다 — 구현 기록에 채택한 값을 적는다.

# Related Specs

- `docs/adr/ADR-MONO-077-fan-goods-sold-by-the-ecommerce-store.md`
- `infra/demo/public-data/README.md` § 시드는 생성한다
- `scripts/check-seed-catalogue-parity.sh`

# Related Contracts

- 없음. 스토어 목록 URL 의 쿼리 이름·의미는 `web-store/src/app/(store)/products/page.tsx` 가 고정을 선언한 그대로 쓴다.

# Edge Cases

- ~~갈래 B 의 예명 검색: 굿즈 이름이 `<예명> …` 으로 시작해야 한다(`ADR-MONO-077` R4).~~ → 🔴 ADR-079 D3 으로 대체: 카드 선택은 `collection_ref = <팬 아티스트 id>`. 「전체 보기」 링크(D5 «B 와 같은 목록» = `categoryId=<굿즈>&q=<예명>`)만 여전히 예명 검색이다 — AC-2 가 그 링크의 결과를 **`collectionRef === 아티스트 id` 집합과 같다**로 단언한다(예명이 남의 굿즈에 섞이거나 이름에서 빠지면 빨강).
- 그룹 STELLAR 멤버(세아·리오): 그룹 굿즈를 어느 쪽 링크에 보일지 — 이 티켓은 멤버 개인 굿즈만 둔다.

# Failure Scenarios

1. `store.json` 을 손으로 고친다 → 변환기를 안 지난 공개 JSON 이 된다(README § 시드는 생성한다).
2. 🔴 공개 목록엔 굿즈가 있는데 로그인 후 장바구니에서 «없는 상품» — 실 백엔드 DB 는 재굽기 전까지 옛 이미지다(`ADR-MONO-077` § 새로 생기는 위험). 구현 기록에 재굽기 필요를 적는다.
3. 적용된 `V8`/`V19` 를 수정한다 → 기존 볼륨이 Flyway 체크섬으로 기동 실패.

---

# ADR-MONO-079 ACCEPTED — A 반영 (2026-10-02 UTC)

- **선행 추가**: `TASK-MONO-749`(상품 `collection_ref`). 굿즈 시드는 그 필드 위에서 한다.
- 🔴 **라이더 R4(«굿즈 상품명은 예명으로 시작» · 팬은 카테고리 + 예명 접두어로 거른다)는 대체됐다** — ADR-079 D3: 팬 아티스트 페이지는 `collection_ref = <팬 아티스트 id>` 로 굿즈를 고른다. 예명이 바뀌거나 겹쳐도 깨지지 않는다. 카테고리 «아티스트 굿즈» 는 «전체 굿즈» 목록용으로 남는다. 착수 시 AC 중 예명 접두어를 단언하는 줄을 `collection_ref` 로 고친다.
- 🔴 **마이그레이션 번호가 한 칸씩 밀렸다 (`TASK-MONO-749`, 2026-10-02 UTC)** — 749 가 선행으로 `V20__add_product_collection_ref.sql`(postgres) · `V13__add_product_collection_ref.sql`(h2)를 차지했다. 이 티켓 § Scope 와 `ADR-MONO-077` D2 가 적은 `V20__seed_artist_goods.sql` · `V13__seed_artist_goods.sql` 은 **`V21` · `V14`** 로 쓴다(같은 서비스 안 같은 버전 = `check-flyway-version-collision.sh` 빨강). 굿즈 행은 `collection_ref = <팬 아티스트 id>` 를 채우고, 픽스처(`raw-backend-responses.mjs`)에도 `collectionRef` 를 같은 값으로 넣는다 — 생성기(`toPublicProduct`)가 그 키를 공개 저장본에 싣는다. 팬 쪽 선택은 `features/public-browse` 의 **`artistGoods(fan, products, artistId)`** 를 쓴다(새로 만들지 마라).
- 소속사(`TASK-MONO-748`)가 생기면 굿즈를 파는 셀러를 소속사의 연결 셀러로 둘 수 있다 — 이 티켓의 필수는 아니다(시드 셀러는 지금처럼 `default` 여도 된다).

---

# 구현 기록 (2026-10-02 UTC)

> 분석·구현 = Opus 5.5. 갈래 **D**(ADR-077) + `collection_ref`(ADR-079 D3). 결정은 두 ADR 이 다 했고, 이 기록은 무엇을 어디에 했는지와 무엇을 못 쟀는지를 적는다.

## 무엇을 바꿨나

| 프로젝트 | 파일 | 내용 |
|---|---|---|
| ecommerce | `product-service/.../db/migration/V21__seed_artist_goods.sql` | 카테고리 `a0000000-…-000000000008` «아티스트 굿즈»(루트) + 상품 18(`b…25`~`b…42`) + 옵션 21(`c…66`~`c…86`). `tenant_id` 를 **카테고리·상품·옵션 셋 다**, `seller_id='default'`, `collection_ref` = 팬 아티스트 id |
| ecommerce | `…/db/migration-h2/V14__seed_artist_goods.sql` | 같은 행(h2 트리 컬럼 집합) |
| ecommerce | `CollectionRefMigrationTwinTest` | 749 의 «시드 상품 전부 NULL» 대조를 «굿즈가 아닌 상품은 NULL» 로 좁힘 + h2 굿즈 시드 칸 신설 |
| ecommerce | `ArtistGoodsSeedOnExistingVolumeIntegrationTest`(신규, `@Tag("integration")`) | AC-8 — Flyway `target=20` + 운영자 등록 행 → V21 만 적용 · 18/6×3 · 옵션 21 전부 `tenant_id=ecommerce` · 기존 행 불변 · 재실행 no-op |
| infra | `public-data/fixtures/raw-backend-responses.mjs` | 같은 18행(`collectionRef` 는 `RAW_ARTISTS` 에서 **읽는다**) · `CATEGORY_NAMES` 에 «아티스트 굿즈» · 리뷰 문구 풀(굿즈 카테고리 — 없으면 픽스처가 throw) |
| infra | `public-data/snapshots/store.json` | **생성기로 재생성**(products 24→42 · categories 6→7 · reviews 60→105). 기존 상품·리뷰 바이트 불변(diff = 추가 + coverage 3줄) |
| infra | `public-data/tests/public-data.test.mjs` | AC-3 — 카테고리**마다** `productCount` = 실제 수 · 굿즈 모양(18 · 아티스트당 3 · placehold.co · 굿즈 밖 `collectionRef=null`) |
| fan | `shared/config/store-links.ts`(신규) | `NEXT_PUBLIC_STORE_URL`(기본 `https://store.hubwang.com`) · 굿즈 카테고리 id **1개** · URL 생성 3개 |
| fan | `features/public-browse/api/read-store.ts`(신규, `server-only`) | 스토어 **번들** 저장본을 `bundledEnvelope` 로 직접 읽는다 — env·fetch 0 |
| fan | `features/public-browse/ui/PublicArtistGoods.tsx`(신규) | 서버 컴포넌트 카드(사진·이름·가격) → `/products/<id>`, 「굿즈샵에서 전체 보기 ↗」 |
| fan | `app/(main)/artists/[id]/page.tsx` · `widgets/header/Header.tsx` | `artistGoods(fan, readStoreProducts(), id)` 로 카드 · 헤더 「굿즈샵 ↗」(평범한 `<a>`) |
| fan | 시험 | `store-links.test.ts`(AC-2, 17칸) · `header-goods-link.test.tsx`(AC-4, 7칸) · `public-pages.test.tsx`(카드 2칸 + 스토어 판독자 mock) · `post-detail-back-link.test.tsx`(mock) · `artist-goods.test.ts`(실제 굿즈가 생긴 저장본에 맞춰 합성 바탕을 `collectionRef=null` 상품으로) |
| fan | `VERCEL.md` | env 원장에 `NEXT_PUBLIC_STORE_URL` 절 |

굿즈 ↔ 아티스트(`collection_ref`):

| 팬 아티스트 id | 예명 | 상품 |
|---|---|---|
| `0199de80-0000-7000-8000-00000000a001` | 루미 | `b…25` 공식 응원봉 · `b…26` 포토카드 세트 · `b…27` 「밤의 끝」 후드티 |
| `…a002` | 노아 | `b…28` 응원봉 · `b…29` 포토카드 · `b…30` 프로듀싱 노트 포스터 |
| `…a003` | 세아 | `b…31` 응원봉 · `b…32` 포토카드 · `b…33` 시그니처 티셔츠 |
| `…a004` | 하린 | `b…34` 응원봉 · `b…35` 포토카드 · `b…36` 단독 공연 포스터 |
| `…a005` | 리오 | `b…37` 응원봉 · `b…38` 포토카드 · `b…39` 시그니처 티셔츠 |
| `…a006` | 유노 | `b…40` 응원봉 · `b…41` 포토카드 · `b…42` 재즈 세션 포스터 |

### 판단을 적어 둔다

- 🔴 **「전체 보기」는 여전히 예명 검색이다**(`categoryId=<굿즈>&q=<예명>`). D5 갈래 D 가 «B 와 같은 목록» 으로 정했고, 스토어 목록에는 컬렉션 필터가 없다(쿼리 이름 고정 — Related Contracts). ADR-079 D3 이 대체한 것은 **카드 선택**(R4)이다. 그래서 굿즈 이름은 `<예명> ` 으로 시작하게 시드했고, AC-2 가 그 링크의 결과 = `collectionRef` 로 고른 카드 집합임을 **양방향**으로 단언한다. 스토어에 `collectionRef` 쿼리를 더하는 것은 스토어 계약 변경이라 이 티켓 밖이다.
- 팬은 스토어 저장본을 판독자(`createPublicDataReader`)가 아니라 **번들로 직접** 읽는다 — D1 «공개 번들 저장본을 읽기만», 익명 요청 0 이 구조로 보장된다. 대가(스토어가 Blob 으로 전환하면 세대가 갈릴 수 있다)는 ADR-077 § D5 D 가 이미 적은 그대로다.
- 그룹 STELLAR 멤버(세아·리오)는 개인 굿즈만 뒀다(Edge Case). 굿즈 셀러는 `default`(소속사 연결 셀러는 `TASK-MONO-748` 몫).

### AC-9 — 채택한 라이더 값

R1 아티스트당 3개 · 총 18개 / R2 `placehold.co` 자리표시(아티스트 색 + 영문 상품명, 기존 `fallback-images.ts` 와 같은 URL 모양) / R3 같은 탭(`target` 없음). 소유자가 한 줄로 뒤집을 수 있다.

## 테스트 · 로컬 판정

| AC | 명령 / 근거 | 결과 |
|---|---|---|
| AC-0 | `ADR-MONO-077` Status `ACCEPTED — D` | ✅ |
| AC-1 | `bash scripts/check-seed-catalogue-parity.sh` · `node infra/demo/public-data/bin/build-bundled-snapshots.mjs --check` | rc=0 «번들 42 · postgres 42 · h2 42» · rc=0 드리프트 없음 |
| AC-2 | `store-links.test.ts` — 팬 URL 함수 출력을 `new URL` 로 풀어 스토어 페이지처럼 `queryProducts`(store.json) 에 넣는다. 헤더 = 굿즈 18 전부 · 6명 각자 카드 ≥1(상세가 `ON_SALE`/`SOLD_OUT`·굿즈·그 아티스트) · 「전체 보기」 = 굿즈만 · 그 아티스트만 · 카드 집합과 같다 | 17/17 ✅ |
| AC-2 bite | 픽스처에서 `b…33`(세아 티셔츠)의 `collectionRef` 를 리오로 → 생성기 재생성 → 시험 | **rc=1, 2칸 빨강**(세아·리오 「전체 보기」 양방향) → 백업에서 복원 → `--check` rc=0 · 시험 17/17 |
| AC-3 | `node --test infra/demo/public-data/tests/public-data.test.mjs` (카테고리마다 대조) | 47/47 rc=0 |
| AC-4 | `header-goods-link.test.tsx` — 익명 `Header` fetch 0 · 회원 조각 미생성 · `'use client'` 0(헤더·프로필 페이지·카드·설정) / `public-pages.test.tsx` 프로필 페이지 fetch 0 | ✅ |
| AC-5 | `bash scripts/check-public-domains.sh` · `node scripts/check-client-graph-backend-origins.mjs` | rc=0 · rc=0(백엔드 오리진 0건) |
| AC-6 | `next build` + `next start`(fan :3612 · store :3613, 사전 리슨 없음 확인) + Playwright chromium | fan 1280px: 메뉴 4개 한 줄(높이 20 = line-height) · 400px: 둘째 줄에 4개 한 줄, `scrollWidth=400`(가로 넘침 0) · 카드 3장 `target` 없음 / store `/products?categoryId=<굿즈>`: placehold.co 이미지 18장 전부 로드(`naturalWidth>0`), 드롭다운 «아티스트 굿즈 (18)» · `q=루미` 3건 |
| AC-7 | fan `npx vitest run` · `npx tsc --noEmit` · `next lint` · `pnpm build` / store `tsc --noEmit` · `next build` / product-service `./gradlew :projects:ecommerce-microservices-platform:apps:product-service:test` | fan 40 파일 357/357 · tsc 0 · lint 0 · build rc=0 / store tsc rc=0 · 컴파일 성공(아래 ⚪) / product-service 396 tests 0 fail · `BUILD SUCCESSFUL` |
| AC-8 | `ArtistGoodsSeedOnExistingVolumeIntegrationTest` | ⚪ 아래 |
| AC-9 | 위 § | ✅ |

### ⚪ 못 쟀다 — 이유

- ⚪ **AC-8**: 이 호스트의 Docker 데몬이 꺼져 있다(`docker info` → `dockerDesktopLinuxEngine` 파이프 없음). IT 는 컴파일됐고(`testClasses`) CI 의 ecommerce integration 잡이 돌린다. h2 절반은 Docker 없이 `CollectionRefMigrationTwinTest#h2Tree_seedsArtistGoods` 로 쟀다(초록) — 🔴 h2 초록은 postgres 의 증거가 아니다(V19 가 바로 그 부류).
- ⚪ **web-store vitest**: Node 24 에서 vitest 4 가 `#module-evaluator` 로 기동 실패(이 호스트의 알려진 한계). web-store 코드는 이 티켓에서 바뀌지 않았고 바뀐 것은 `store.json` 뿐 — 그 파일에 수치를 얼린 web-store 시험은 grep 상 없다(`corpusSize` 단언은 전부 합성 봉투). CI 가 판정한다.
- ⚪ **web-store `next build`의 standalone 단계**: 컴파일은 성공, `output: 'standalone'` 의 traced-file 복사가 Windows 심볼릭 링크 권한으로 `EPERM`(fan `next.config.ts` 머리 주석이 적은 그 호스트 한계). CI(Linux)가 판정한다. web-store lint 는 코드 변경이 없어 돌리지 않았다.

## 🔴 재굽기 필요 (Failure Scenario 2)

공개 열람(번들 저장본)은 머지·배포만으로 굿즈가 보인다. 그러나 **로그인 후 장바구니·주문 경로는 `product-service` DB** 를 쓰고, V21 은 데모 서버 이미지를 **다시 구워야** 들어간다. 그 전에는 «공개 목록엔 있는데 담으면 없는 상품» 이 된다 — 재굽기는 소유자 작업이다(ADR-077 § Outstanding follow-ups).

---

## CORRECTION (2026-10-03 UTC) — 4차원 종결

- (a) #4119 `MERGED` · (b) 스쿼시 `2921d1b7e` 가 `origin/main` 에 포함 · (c) 머지 시점 rollup 실패 0/68.
- (d) AC-0~7·9 본문 근거로 닫힘. **AC-8 정정**: 본문의 ⚪(«로컬 미실행») 는 CI 에서 해소됐다 — `Integration (ecommerce C)` 잡 로그에 `ArtistGoodsSeedOnExistingVolumeIntegrationTest` 의 모양(13:18:05 `Migrating … version "20 - add product collection ref"` → `"21 - seed artist goods"` → `Schema "public" is up to date. No migration necessary`)이 찍혀 있다. 즉 V20 까지 올린 볼륨에 V21 만 적용되고 재실행은 no-op. ⇒ AC-8 이 요구한 «기존 볼륨 적용» 을 CI 가 쟀다.
- 🔵 그 IT 는 `TASK-MONO-752` 가 product-service 에 V22 를 더할 때 upgrade·재실행 target 을 `21` 로 고정했다(target 없는 재실행이 새 파일을 적용해 빨개지는 부류 — 750 의 iam IT 에서 실제로 났다).
