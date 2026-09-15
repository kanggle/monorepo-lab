# ADR-MONO-074 — 상품 리뷰는 store 저장본에 실린다 (그리고 작성자는 싣지 않는다)

**Status:** ACCEPTED
**Date:** 2026-09-15
**주관 티켓:** `TASK-MONO-681`
**개정 대상:** [`ADR-MONO-070`](ADR-MONO-070-public-browsing-served-from-a-versioned-vercel-snapshot.md) § D2(공개 필드 허용 목록) —
store 데이터셋에 `reviews` 컬렉션을 더한다. § D3(백엔드로 가는 판독자 금지)·§ 출처의 금지 조항은 **그대로 둔다.**

**출처 — 소유자 결정 (2026-09-15), 갈래 넷 중:**

> **「저장본에 리뷰 포함」** — *"ADR-MONO-070을 개정해 store 저장본에 리뷰를 싣고, 공개 상품 페이지는 서버 상태와
> 무관하게 항상 저장본에서 리뷰를 읽습니다. 공개 필드는 별점·제목·본문·작성일만 두고 작성자 식별정보는 뺍니다.
> 지금은 시드 리뷰가 없어 합성 리뷰가 되므로 화면에 '샘플 리뷰'라고 표시합니다."*

탈락한 갈래: 안내 문구만 정직하게 · 폴백 금지 조항 뒤집기 · 코드 변경 없이 서버 켜기.

---

## History

- 2026-09-15 — **ACCEPTED (라이더 R1~R6 그대로).** 소유자 정확형: **`ADR-MONO-074 ACCEPTED`**. 이름 · `ACCEPTED` 둘 다
  있고, 이 ADR 은 갈래 letter 를 두지 않았으므로(방향은 이미 소유자가 골랐다) 세 번째 요건은 해당이 없다. 뒤집힌 라이더 **0**.
  🔴 결정 본문(§ Decision · § 라이더 · § Consequences)은 **한 바이트도 안 바꿨다** — ACCEPT 는 *finalise* 이지 *re-decide* 가 아니다.
  🔵 실행 `TASK-MONO-681` 은 PROPOSED 와 **같은 PR 에서 기안**돼 있었고, 그 AC-0(게이트)이 이 줄로 닫힌다.
- 2026-09-15 — **PROPOSED.** 🔴 위 인용은 **방향**을 정했다. 그러나 그 문장 안의 세부(필드 목록·샘플 표시)는 내가
  선택지 설명에 **써 넣은 것**이고, 그 밖의 결정(아래 § 라이더)은 소유자가 본 적이 없다. ⇒
  `platform/architecture-decision-rule.md` § The ACCEPTED Gate 에 따라 이 ADR 은 **PROPOSED** 이고,
  **`TASK-MONO-681` 의 구현은 ACCEPT 전까지 멈춘다.** 방향을 골랐다는 사실을 ACCEPT 로 세탁하지 않는다.
- 🔴 **이 ADR 이 나오게 된 경위를 적는다** — 처음에 나는 *"백엔드가 꺼져 있으면 샘플 리뷰로 폴백"* 을 **권장**했다.
  그것은 `ADR-MONO-070` § 출처가 원문으로 금지한 구조(*"먼저 백엔드를 호출했다가 실패하면 저장본을 보여주는 구조를
  기본으로 만들지 않는다"*)였고, 나는 ADR 을 읽기 **전에** 권장했다. 착수 직전에 읽고 멈췄다. 이 ADR 이 폴백이
  아니라 **저장본 1차 출처**로 설계된 이유가 그것이다.

---

## Context — 재고 있는 사실만 (2026-09-15 UTC)

| 사실 | 근거 |
|---|---|
| 공개 상품 상세는 저장본에서 **상품**을 읽는다 | `web-store/src/entities/product/api/get-product.ts` |
| 같은 화면의 **리뷰**는 브라우저가 BFF → 게이트웨이 → review-service 로 읽는다 | `features/review/ui/ReviewList.tsx` → `useProductReviews` |
| 데모가 꺼져 있으면 리뷰 요청은 **502** | 라이브 실측: `/api/bff/api/reviews/products/b0…22` → `502 BFF_UPSTREAM_ERROR`, `/api/demo/backend-state` → `unavailable` |
| 라이브 store 는 **번들 시드**를 서빙한다(Blob 발행본 없음) | 라이브 HTML `data-testid="store-data-provenance" data-source="bundled"` |
| store 저장본에는 `products`·`categories` 뿐이다 | `infra/demo/public-data/src/datasets.ts` `StorePublicData` |
| review-service 에 **데모 시드 리뷰가 없다** | `INSERT INTO reviews` 는 통합테스트 1곳뿐 |
| 백엔드 리뷰 항목은 **작성자 `userId`** 를 싣는다 | `packages/types/src/review.ts` `ReviewItem` |

⇒ 한 화면 안에서 **상품은 D3 를 따르고 리뷰는 안 따른다.** `ADR-MONO-070` 은 댓글을 «공개 정책 미정의» 로 뺐고(D7)
리뷰는 언급하지 않았다 — 그래서 리뷰는 **결정된 적 없이** 백엔드에 남아 있었다.

---

## Decision

### D1 — store 저장본에 `reviews` 컬렉션을 더한다

`StorePublicData = { products, categories, reviews }`. `reviews` 는 **필수**다 — `coverage`·`collectionStatus` 키
집합에도 들어간다(`ADR-MONO-070` D1: «정상적인 0건» 과 «수집 실패» 를 가르는 유일한 수단).

### D2 — 공개 리뷰 필드는 **허용 목록**이고 작성자는 없다

```ts
interface PublicReview {
  id: string;
  productId: string;
  rating: 1 | 2 | 3 | 4 | 5;
  title: string;
  content: string;
  createdAt: string;
}
```

명시적으로 나가지 않는 것: `userId` · 계정/테넌트 id · 이메일 · 닉네임·표시명 · `updatedAt`.
🔴 계약 검증기(`contract.mjs`)가 작성자 식별 키가 **실려 오면 봉투를 거부**한다 — 판독자와 발행자 **둘 다**.

### D3 — 공개 상품 페이지의 리뷰 목록·평점 요약은 **저장본에서만** 온다

로그인 여부와 무관하다(`ADR-MONO-070` D3.1 — 목록과 상세가 섞이지 않는다를 리뷰까지 넓힌다). 요약(평균·개수·분포)도
백엔드 summary API 가 아니라 **저장본 안에서** 계산한다.

### D4 — 번들 시드의 리뷰는 **저장소가 소유한 샘플**이고, 화면이 그렇게 말한다

백엔드에 시드 리뷰가 없으므로 번들 시드 리뷰는 합성이다. 봉투 `source !== 'backend'` 일 때 리뷰 영역은
**「샘플 리뷰 — 실제 구매자가 쓴 리뷰가 아닙니다」** 를 표시한다. 🔴 표시 없이 합성 리뷰를 그리면 **존재하지 않는
구매자 평가를 사실처럼 주장**하는 화면이 된다.

### D5 — 발행자는 상품마다 리뷰 전 페이지를 수집하고, 하나라도 실패하면 발행을 거부한다

`GET /api/reviews/products/{id}` 를 끝까지 따라간다. 실패 하나 = 컬렉션 `failed` = 포인터 안 움직임
(`ADR-MONO-070` D4). 「일부 상품만 리뷰가 0개」 는 화면에서 «리뷰가 없는 상품» 으로 읽히고 되돌릴 수 없다.

---

## 라이더 — 소유자가 안 고른 것, 내가 고른 것

🔴 아래는 **내 선택**이다. ACCEPT 할 때 한 줄로 뒤집을 수 있어야 하고, 뒤집히지 않으면 이대로 구현된다.

| # | 내가 고른 것 | 대안 | 뒤집으려면 |
|---|---|---|---|
| R1 | 작성자 표시를 **아예 없앤다**(익명 «구매자» 도 안 쓴다) | 마스킹된 닉네임 표시 | 백엔드에 공개 표시명이 생기고 그 공개 정책을 정한 뒤 D2 개정 |
| R2 | 상품 상세에서 **리뷰 작성 폼은 유지**(로그인 시, 백엔드 쓰기). 작성 성공 시 *"공개 목록에는 다음 발행 때 반영됩니다"* 를 알린다 | 상품 상세에서 작성 폼 제거 | 폼 제거 — 작성 동선이 사라진다 |
| R3 | 상품 상세에서 **본인 리뷰 수정·삭제 버튼을 뺀다** — 저장본에 작성자가 없어 «본인» 을 판정할 수 없다. 관리는 `/my/reviews` 에서 | 로그인 시 백엔드를 추가로 물어 본인 리뷰를 표시 | 🔴 D3 를 깨는 반쪽 구현(목록은 저장본, 본인 표시는 백엔드)이 된다 |
| R4 | 샘플 리뷰는 **상품마다 2~3개**, 별점이 고르게 섞이게 | 일부 상품만 / 전부 5점 | 픽스처 조정 |
| R5 | review-service 에 **데모 시드 리뷰를 넣지 않는다** — 번들 샘플과 DB 가 다른 리뷰를 갖는다. 데모를 켜고 발행하면 저장본 리뷰가 **실제 DB 리뷰(현재 0개)로 바뀐다** | 두 벌을 같게 시드(`check-seed-catalogue-parity` 처럼 대조 가드 추가) | 백엔드 시드 + 대조 가드 추가 |
| R6 | `schemaVersion` 을 **1 로 유지**한다 — `reviews` 필수화는 호환이 깨지지만 **Blob 발행본이 아직 없다**(라이브 `data-source=bundled` 실측) | 판을 2 로 올린다 | 발행본이 생긴 뒤였다면 판을 올려야 한다 |

🔴 **되돌릴 수 없는 축은 R1 이다** — 한 번 공개된 작성자 식별은 회수해도 이미 나갔다. 그래서 R1 만 가장 안전한 쪽으로 기울였다.
🔴 **R5 를 ACCEPT 한다는 것의 뜻**: 데모를 켠 뒤 `--from` 으로 발행하면 공개 화면의 샘플 리뷰가 **사라진다**
(DB 리뷰 0개 ⇒ `collectionStatus: empty`). 그것이 싫으면 R5 를 뒤집어야 한다.

---

## Consequences

- 데모가 꺼져 있어도 공개 상품 상세에 리뷰와 평점이 보인다.
- 🔴 로그인한 사용자가 방금 쓴 리뷰가 **공개 목록에 바로 안 보인다** — 발행이 있어야 보인다(R2 가 그 사실을 알린다).
- 🔴 실제 리뷰가 발행되는 날, **사용자가 쓴 본문이 공개 JSON 에 실린다.** 이것이 소유자가 고른 공개 정책이고(작성자 제외),
  리뷰 작성 화면이 그 공개 범위를 사용자에게 말하는가는 이 ADR 이 다루지 않는다 — 후속 질문으로 남긴다.
- `web-store` 의 `useProductReviews`·`useReviewSummary` 는 상품 상세에서 더 이상 쓰이지 않는다.

---

## ACCEPT 하는 법

한 줄로: **`ADR-MONO-074 ACCEPTED`** (라이더 그대로) — 또는 뒤집을 라이더를 붙여서, 예: `ADR-MONO-074 ACCEPTED — R3 뒤집기`.
