# Task ID

TASK-MONO-681

# Title

상품은 서버 없이 보이는데 **리뷰만 서버를 요구한다** — 리뷰를 store 저장본에 싣는다 (`ADR-MONO-075`)

# Status

done

# Owner

monorepo

# Task Tags

- ecommerce
- demo
- public-data
- contract

---

# 🟢 게이트 닫힘 — `ADR-MONO-075 ACCEPTED` (2026-09-15 UTC, 재확인 완료)

소유자 정확형 **`ADR-MONO-075 ACCEPTED`** 를 새 이름으로 받았다 — 이름 · `ACCEPTED` 둘 다 있고, 갈래 letter 없음,
**뒤집힌 라이더 0** ⇒ 아래 AC 는 기안 그대로다. 아래는 재확인을 받게 된 경위(기록으로 남긴다).

🔴🔴 **번호 충돌.** 이 ADR 은 처음 `ADR-MONO-074` 로 발행됐고, 소유자는 이 대화에서 원문 그대로
**`ADR-MONO-074 ACCEPTED`** 로 답했다(라이더 뒤집기 없음). 그런데 그 사이 다른 세션의 콘솔 ADR
(`ADR-MONO-074-anonymous-visitors-see-the-real-console-with-sample-data.md`, PROPOSED, `TASK-MONO-682`)이 **먼저 main 에
머지**됐다(#3815). `tasks/INDEX.md` § Task ID Allocation 규칙 4(먼저 머지된 쪽이 번호를 갖는다)에 따라 이 ADR 은
**`ADR-MONO-075` 로 재번호**했다.

⇒ 그 ACCEPT 줄의 **이름이 이제 다른 결정을 가리킨다.** 게이트의 목적이 «이름으로 귀속» 이므로 그 줄을 075 로 옮겨
적지 않고 **재확인을 받는다.** 구현은 이 PR 에 들어 있으나 **재확인 전에는 머지하지 않는다.** 그리고 🔴 그 `074 ACCEPTED`
줄을 콘솔 ADR 의 수락으로 읽지 마라 — 그 결정은 소유자가 본 적이 없다.

🔴 **AC-0 (게이트)**: `ADR-MONO-075` 의 Status 가 `ACCEPTED` 이고, 그 ACCEPT 가 소유자의 정확형
(`ADR-MONO-075 ACCEPTED` [— 뒤집을 라이더])으로 기록돼 있는가. **아니면 STOP.** 방향 선택(갈래 고르기)은 ACCEPT 가
아니다(`platform/architecture-decision-rule.md` § The ACCEPTED Gate). 뒤집힌 라이더가 있으면 **아래 AC 를 그에 맞게
먼저 고친다.**

---

# Goal

데모 백엔드가 꺼져 있어도 공개 상품 상세에서 **리뷰 목록과 평점 요약**이 보이게 한다. 출처는 폴백이 아니라
**store 저장본**이다(`ADR-MONO-070` D3 · `ADR-MONO-075` D3).

---

# Context — 실측 (2026-09-15 UTC, `main` = `30cdff1a2`)

- 라이브 `https://store.hubwang.com/products/b0000000-0000-0000-0000-000000000022`: 페이지 200 · 리뷰 목록/요약 API
  **502** `BFF_UPSTREAM_ERROR` · `/api/demo/backend-state` = `unavailable` · 저장본 `data-source="bundled"`.
- 선행 `TASK-FE-100`(done)은 **다른 결함**(낡은 세션 토큰의 `/login` 튕김)을 고쳤다. 사용자가 본 증상은 이것이었다 —
  그 티켓 착수 전에 라이브를 안 열어 본 것이 그 원인이다.
- 상세 경위·결정·라이더: `docs/adr/ADR-MONO-075-product-reviews-ride-in-the-store-snapshot.md`.

---

# Scope

## In Scope

- `infra/demo/public-data/`: `datasets.ts`(`PublicReview`) · `contract.mjs`(검증) · `transform.mjs`(`toPublicReview`) ·
  `query.ts`(상품별 리뷰 + 요약) · `index.ts` · `fixtures/`(샘플 리뷰 + 음성 대조군) · `bin/build-bundled-snapshots.mjs` ·
  `bin/publish-public-data.mjs`(`extractStore` 수집) · `tests/public-data.test.mjs` · `snapshots/store.json` 재생성 · `README.md`
- `projects/ecommerce-microservices-platform/apps/web-store`: 상품 상세가 저장본 리뷰를 서버에서 읽어 넘기고,
  `ReviewList`·`RatingSummary` 가 props 로 그린다. 샘플 표시. 작성 성공 문구. 상세의 수정·삭제 제거(R3). 테스트.
- `docs/adr/ADR-MONO-070` § 후속에 `ADR-MONO-075` 포인터

## Out of Scope

- review-service 데모 시드 리뷰 추가(R5)
- `/my/reviews`(백엔드 경로 그대로)
- 리뷰 작성 화면이 공개 범위를 사용자에게 알리는가(ADR § Consequences 의 후속 질문)
- Blob 발행 실행(토큰 없음 — `ADR-MONO-070` D5)

---

# Acceptance Criteria

- [x] **AC-0** — 위 게이트. ✅ 2026-09-15 소유자 정확형 `ADR-MONO-075 ACCEPTED`(재번호 뒤 재확인 — 처음 받은 원문 `ADR-MONO-074 ACCEPTED` 는 이름 충돌로 옮겨 적지 않았다).
- [x] **AC-1** — `validateDatasetData('store', …)` 가 `reviews` 부재 · 작성자 식별 키(`userId`/`accountId`/`tenantId`/
      `email`/`userName`/`nickname`) · 1~5 밖의 별점 · 저장본에 없는 `productId` 를 **각각** 거부한다(대조군: 정상본 통과).
      ✅ `tests/public-data.test.mjs` «내용물: 리뷰 계약이 부재·작성자·별점·고아를 각각 거부한다» — 거부 6칸(부재 · `userId` ·
      `nickname` · 별점 0 · 별점 3.5 · 고아) + 대조군 2칸(정상 · 0개). 계약 목록은 `authorName` 까지 7키.
- [x] **AC-2** — `toPublicReview` 산출물에 작성자 필드가 없다. 🔴 **양성 대조군**: 픽스처 원본에는 `userId` 가 실제로 있다.
      ✅ «리뷰: 작성자가 공개 DTO 에 없다» — 키 집합이 정확히 6개 + 원본 전 항목에 `userId` 실재 단언.
- [x] **AC-3** — 숨김 상품에 달린 리뷰(음성 대조군)가 번들 시드에 **문자열로도** 없다(`assertNoLeak` + 시험 둘 다).
      ✅ 생성기 누출 대조군 store **68건**(이전 상품만일 때보다 늘었다) + 시험 «리뷰의 음성 대조군이 문자열로도 없다»
      (MUST-NOT-LEAK 4건 이상 비공허성 단언 포함).
- [x] **AC-4** — `build-bundled-snapshots.mjs --check` 드리프트 0 · 번들 시드 `coverage.reviews > 0` · 공개 상품
      **전부**가 리뷰 ≥ 1 (상품 상세에 빈 리뷰가 없다).
      ✅ `--check` rc=0 · `reviews=60`(24상품 × 2~3) · 시험 «공개 상품 전부가 리뷰를 갖고…» + web-store 오프라인 스위트(실제 시드) 두 곳.
- [x] **AC-5** — 발행자 `extractStore` 가 상품 하나의 리뷰 수집만 실패해도 `collectionStatus.reviews = 'failed'` 를 낸다.
      ✅ 판정은 `collectReviews`(`src/transform.mjs`, 네트워크 주입)가 들고 있고 시험 «한 상품이라도 실패하면 fetched=false
      이고 봉투에서 failed 가 된다» 가 문다. `extractStore` 는 `detailsOk && collected.fetched` 를 그대로 싣는다.
      ⚪ `extractStore` 의 HTTP 배관 자체는 **시험하지 않았다** — 이 패키지가 원래 안 시험하는 축(README § 시험: 게이트웨이 없음).
- [x] **AC-6** — 공개 상품 상세가 **백엔드를 부르지 않고** 리뷰·요약을 그린다 — 상세 경로에서 `useProductReviews`·
      `useReviewSummary` 호출 **0건**, 테스트가 그것을 문다.
      ✅ 상세 경로(`page.tsx` → `getSnapshotProductReviews` → `ReviewList`/`RatingSummary` props)에 두 훅 호출 0 ·
      `review-list.test.tsx` 가 리뷰 API 조회 함수 **미호출**을 단언(페이지 넘김 뒤에도) · `rating-summary.test.tsx` 도 같은 단언.
      CI `Frontend unit tests` 로그: `review-list.test.tsx (11 tests)` · `rating-summary.test.tsx (6 tests)` · `get-snapshot-reviews.test.ts (7 tests)` 통과(PR #3814, run 34947107750).
- [x] **AC-7** — `source !== 'backend'` 일 때 「샘플 리뷰」 표시가 보이고, `source === 'backend'` 면 안 보인다(두 칸 다 시험).
      ✅ `get-snapshot-reviews.test.ts` 가 `bundled`/`authored` → `isSample=true`, `backend` → `false` 세 칸 · `review-list.test.tsx` 가
      표시 있음/없음 두 칸(CI 통과, 위와 같은 런).
- [x] **AC-8** — 라이브 판정: 배포 뒤 익명으로 `/products/b0000000-0000-0000-0000-000000000022` 의 **렌더된 HTML** 에 리뷰
      제목과 「샘플 리뷰」가 있다(서버 렌더이므로 HTML 로 판정 가능 — 클라이언트 fetch 가 아니다). 🔴 판정 전에
      `build-info`/배포 커밋이 머지 커밋인지부터 확인한다.
      ✅ **배포 커밋 먼저**: `build-info.json` 은 이 앱에서 307(리다이렉트)이라 쓸 수 없어 GitHub deployments 로 확인 —
      `Production – kanggle-store` 배포 `8d07c1454`(= PR #3814 squash) **success** 2026-09-15T10:39:35Z.
      🔴 그 «success» 는 판정이 아니다(빌드 훅 호출만 뜻한 적이 있다) ⇒ **렌더된 HTML 로 판정**했다. 쿠키 없는 curl(익명):
      | 상품 | 「샘플 리뷰」 문구 | 평점 요약 | 저장본 리뷰 제목 | 개수·평균 |
      |---|---|---|---|---|
      | 22 (울트라북) | **1** | **1** | **2/2** (`업무용으로 추천` · `충전기가 커요`) | `(2개 리뷰)` · `4.0` (5·3점) |
      | 01 (티셔츠) | **1** | **1** | **3/3** | `(3개 리뷰)` · `4.0` (5·3·4점) |
      🔵 **대조군** — 배포 전 같은 상품 22 페이지: 문구 **0** · 평점 요약 **0**(리뷰 영역 제목만 1). 차이가 이 변경의 효과다.
      🔵 개수 문구를 처음 `(2개 리뷰)` 로 grep 해서 **0건**이 나왔다 — React SSR 이 `(<!-- -->2<!-- -->개 리뷰)` 로 주석을 끼운다.
      판정기 문제였고 렌더는 정상이다(HTML 을 열어 확인).
- [x] **AC-9** — 게이트: `node --test` public-data · web-store `tsc`·lint(CI)·vitest(CI) · `check-seed-catalogue-parity.sh` ·
      필수 가드 3종.
      ✅ PR #3814 머지 전 CI(head `5e8940d3c`): **FAILURE 0** (SUCCESS 19 · SKIPPED 44) · 필수 4종 SUCCESS ·
      `Demo wrapper smoke` 안의 두 스텝 «Public-data bundled seed is regenerable and not drifted» · «Public-data package tests» **둘 다
      success**(잡이 초록이어도 스텝이 skip 일 수 있어 스텝 단위로 확인) · `Public catalogue does not split between bundled and real seed`
      SUCCESS · `Frontend lint & build` SUCCESS(`next build` 가 `/products/[id]` 컴파일) · `Frontend unit tests` SUCCESS · `ADR index drift` SUCCESS.
      로컬(스테이지 후): `node --test` 39/39 · `tsc` rc=0 · ESLint rc=0.

---

# Related Specs

- `docs/adr/ADR-MONO-075-product-reviews-ride-in-the-store-snapshot.md` (이 티켓의 결정)
- `docs/adr/ADR-MONO-070-public-browsing-served-from-a-versioned-vercel-snapshot.md` (D1·D2·D3·D4)
- `infra/demo/public-data/README.md`

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/http/review-api.md` — 발행자가 읽는 입력(변경 없음)
- `infra/demo/public-data/src/contract.mjs` — 공개 봉투 계약(변경 대상)

---

# Edge Cases

- 리뷰 0개인 상품(발행본) — `empty` 가 아니라 상품별 0개는 정상 · 화면은 「아직 리뷰가 없습니다」
- 리뷰가 페이지 크기(10)를 넘는 상품 — 클라이언트 페이지네이션이 저장본 배열 안에서 돈다
- 로그인 사용자가 방금 쓴 리뷰 — 공개 목록에 없음 · 작성 성공 문구가 그 사실을 말한다(R2)
- 저장본을 못 읽어 번들로 떨어진 경우(`degraded`) — 샘플 표시 그대로

# Failure Scenarios

- **리뷰를 백엔드 우선 → 실패 시 저장본으로 그린다** → `ADR-MONO-070` § 출처 위반. AC-6 이 문다
- **transform 에서 원본을 spread** → 작성자 필드가 조용히 공개된다. AC-1(계약)·AC-2(변환) 두 겹이 문다
- **샘플 표시 없이 합성 리뷰를 그린다** → 없는 구매자 평가를 사실로 주장. AC-7 이 문다
- **시드를 손으로 고친다** → `--check` 드리프트로 빨개진다(AC-4)
- **ADR ACCEPT 전에 구현을 머지** → AC-0

# Test Requirements

- public-data: 계약 거부 4종 + 대조군 · 변환 허용 목록 + 양성 대조군 · 시드 누출 · 요약 계산(평균·분포·0개)
- web-store: 저장본 리뷰 props 렌더 · 샘플 표시 두 칸 · 페이지네이션 · 비로그인 폼 없음 · 로그인 폼 + 작성 성공 문구 · 수정/삭제 부재

# Definition of Done

- [x] ADR-MONO-075 ACCEPTED
- [x] 구현 + 테스트
- [x] 게이트 통과 + 라이브 판정(AC-8)

분석=Opus 5 / 구현 권장=Opus (계약·보안 허용목록·교차 패키지)
