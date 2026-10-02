# Task ID

TASK-MONO-739

# Title

팬 굿즈를 이커머스 스토어에 시드하고 팬 웹에서 링크한다 (`ADR-MONO-077`)

# Status

ready

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

- [ ] **AC-0 (게이트)** — `docs/adr/ADR-MONO-077-*.md` 의 Status 가 `ACCEPTED` 이고 갈래 letter 가 적혀 있다. 아니면 착수하지 않는다.
- [ ] **AC-1** — 세 시드의 상품 id 집합이 같다: `scripts/check-seed-catalogue-parity.sh` rc=0, `build-bundled-snapshots.mjs --check` rc=0.
- [ ] **AC-2** — 🔴 링크가 **0건 목록으로 떨어지지 않는다**: 팬 웹이 만드는 스토어 URL 을 아티스트 6명 전부(갈래 C 는 헤더 1개)에 대해 공개 저장본 질의(`queryProducts`)로 풀어 **굿즈만, 1건 이상**임을 단위 시험으로 단언. 시험은 팬 쪽 URL 생성 함수의 출력과 스토어 저장본을 **같이** 읽는다(한쪽만 읽으면 두 프로젝트가 따로 바뀔 때 못 잡는다).
- [ ] **AC-3** — `categories[].productCount` 가 실제 공개 상품 수와 일치(`TASK-MONO-638` AC 와 같은 대조).
- [ ] **AC-4** — 팬 웹 헤더·아티스트 프로필이 서버 컴포넌트로 남고(`'use client'` 추가 0) 익명 렌더에서 fetch 0회.
- [ ] **AC-5** — `check-public-domains.sh` · `check-client-graph-backend-origins.mjs` rc=0.
- [ ] **AC-6** — 브라우저(`next start`): 팬 헤더 1280·400px 줄바꿈 이상 없음, 스토어 목록에 굿즈 카드 이미지 렌더.
- [ ] **AC-7** — 팬·스토어 유닛·lint·build, `node --test infra/demo/public-data/tests/public-data.test.mjs` rc=0.
- [ ] **AC-8** — postgres `V20` 이 **기존 볼륨**(V19 까지 적용된 DB)에 적용된다 — Testcontainers 로 V19 까지 올린 뒤 V20 을 적용하거나, 불가하면 ⚪ 로 «못 쟀다, 이유» 를 적는다(`TASK-MONO-638` 의 V19 결함 부류).
- [ ] **AC-9 (라이더, 구현자 기본값)** — `ADR-MONO-077` § 라이더 대조가 승격: 굿즈 아티스트당 3개·총 18개(R1) · 이미지 `placehold.co`(R2) · 링크는 같은 탭(R3). 소유자가 한 줄로 뒤집을 수 있다 — 구현 기록에 채택한 값을 적는다.

# Related Specs

- `docs/adr/ADR-MONO-077-fan-goods-sold-by-the-ecommerce-store.md`
- `infra/demo/public-data/README.md` § 시드는 생성한다
- `scripts/check-seed-catalogue-parity.sh`

# Related Contracts

- 없음. 스토어 목록 URL 의 쿼리 이름·의미는 `web-store/src/app/(store)/products/page.tsx` 가 고정을 선언한 그대로 쓴다.

# Edge Cases

- 갈래 B 의 예명 검색: 굿즈 이름이 `<예명> …` 으로 시작해야 한다(`ADR-MONO-077` R4). 예명 두 글자가 **다른 굿즈 이름 안에** 들어 있으면 그 아티스트 링크에 섞인다 — AC-2 가 «그 아티스트 굿즈만» 으로 단언한다.
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
