# Task ID

TASK-BE-588

# Title

`order-api.md` 대로 주문을 넣으면 **400 이 난다** — 계약은 `recipientName`, 서버는 `recipient` (같은 문서 안에서도 2:1 로 갈렸다)

# Status

done

# Owner

backend

# Task Tags

- api
- code

---

# 배경 — 계약서를 읽고 구현했더니 거절당했다

2026-08-15 통합 데모 라이브 검증에서 `POST /api/orders` 를 **계약서 § POST /api/orders 의
요청 예시 그대로** 만들어 보냈다:

```
HTTP 400 {"code":"VALIDATION_ERROR","message":"recipient is required"}
```

계약서는 `shippingAddress.recipientName` 을 준다. 서버는 `recipient` 를 요구한다.

## 무엇이 낡았는지는 갈리지 않는다 — 구현 쪽이 일관되다

| 위치 | 필드명 |
|---|---|
| `PlaceOrderRequest.ShippingAddressRequest` (`@NotNull(message = "recipient is required")`) | `recipient` |
| `PlaceOrderCommand` · `OrderDetail` · `OrderDetailResponse` · `OrderPlacedEvent` | `recipient` |
| 도메인 `ShippingAddress` · `ShippingAddressEmbeddable` | `recipient` |
| DB `orders` 테이블 컬럼 (실측) | `recipient` |
| web-store 체크아웃 (`use-shipping-address-state.ts`) | `recipientName` → **`recipient` 로 매핑해서 전송** |

⇒ **코드·DB·프런트가 전부 `recipient` 로 일치**하고, **계약서 한 곳만 어긋나 있다.**

🔵 web-store 가 화면 상태로는 `recipientName` 을 쓰는 것이 이 혼동의 출처로 보인다 —
**배송지 API**(`/api/users/me/addresses`)는 실제로 `recipientName` 이 맞다. 두 API 가 서로 다른
이름을 쓰고, 체크아웃이 그 사이에서 변환한다. 계약서가 그 변환 지점을 놓쳤다.

## 같은 문서가 자기 자신과도 어긋난다

`specs/contracts/http/order-api.md` 안에서 `recipient` 계열은 **세 번** 나온다:

| 행 | 위치 | 필드명 | 코드와 일치? |
|---|---|---|---|
| 41 | § POST /api/orders — **요청** | `recipientName` | ❌ |
| 130 | § GET /api/orders/{orderId} — **응답** | `recipientName` | ❌ (`OrderDetailResponse` 는 `recipient`) |
| 336 | § GET /api/admin/orders/{orderId} — 응답 | `recipient` | ✅ |

**3분의 2가 틀렸고, 나머지 하나가 옳다.** 즉 "문서 전체가 옛 이름을 쓴다"가 아니라
**부분적으로만 갱신된 흔적**이다.

🔴 이 저장소는 *"Specifications are the source of truth"* 를 규약으로 걸고 있다
(`CLAUDE.md` § Core Principles). 그 규약대로 계약서를 읽고 구현하는 다음 사람 — 새 클라이언트,
e2e, 또는 AI 에이전트 — 은 **반드시 400 을 맞는다.** 이번에 실제로 그렇게 됐다.

---

# Goal

`order-api.md` 의 `POST /api/orders` 요청 스키마와 `GET /api/orders/{orderId}` 응답 스키마가
구현과 일치한다. 계약서만 보고 만든 요청이 **성공한다.**

---

# Scope

## In Scope

- `projects/ecommerce-microservices-platform/specs/contracts/http/order-api.md` 41행 · 130행의
  `recipientName` → `recipient` 정정
- 🔴 **`recipient` 하나만 보고 끝내지 말 것** — 같은 `shippingAddress` 블록의 나머지 필드
  (`phone` · `zipCode` · `address1` · `address2`)와 `items[]` 의 전 필드를 **DTO 와 전수 대조**한다.
  한 필드가 갈렸다면 이웃도 의심 대상이다(이번 발견 자체가 "하나 틀린 김에 세어 보니 2/3" 였다).
- 대조 결과를 PR 본문에 표로 남긴다(무엇을 확인했고 무엇이 이미 맞았는지).

## Out of Scope

- **코드/DTO 를 계약서에 맞추는 방향** — 그쪽이 파괴적이다. `recipient` 는 DTO·도메인·JPA
  임베더블·이벤트 페이로드·DB 컬럼까지 관통하고 web-store 도 그것을 보낸다. 필드명을 바꾸면
  마이그레이션 + 이벤트 계약 + 프런트가 함께 움직여야 하고, **얻는 것은 이름 취향뿐**이다.
- **`/api/users/me/addresses` 의 `recipientName`** — 그쪽은 계약과 구현이 일치한다. 두 API 가
  다른 이름을 쓰는 것 자체를 통일하는 일은 별건(계약 변경 = 파괴적)이며, 이 태스크는
  **문서를 사실에 맞추는 것**이다.
- 다른 계약 문서 전수 감사 — 같은 종류의 드리프트가 더 있을 개연성은 높지만, 술어 없는
  전수 스윕은 이 티켓의 범위를 넘는다(§ Follow-up 참조).

---

# Acceptance Criteria

- [ ] **AC-1** `order-api.md` § POST /api/orders 요청 예시의 필드명이 `PlaceOrderRequest`
      (`presentation/dto/PlaceOrderRequest.java`)의 레코드 컴포넌트명과 **1:1 일치**한다.
- [ ] **AC-2** § GET /api/orders/{orderId} 응답 예시가 `OrderDetailResponse` 와 **1:1 일치**한다.
- [ ] **AC-3** 🔴 **계약서만 보고 만든 요청이 실제로 통과한다** — 수정된 계약서의 요청 예시를
      그대로 페이로드로 삼아 라이브(또는 IT) `POST /api/orders` 가 **201** 을 낸다.
      🔴 "필드명이 같아 보인다" 로 판정하지 말 것. 이 티켓의 발단이 **눈으로는 그럴듯했던**
      계약서다.
- [ ] **AC-4** `shippingAddress` · `items[]` 전 필드 대조표가 PR 본문에 있고, **일치하지 않는
      항목이 0** 이다(이미 맞았던 항목도 "확인함" 으로 표시 — 무엇을 안 봤는지가 드러나야 한다).
- [ ] **AC-5** 🔴 **네거티브 확인**: 수정 **전** 계약서의 예시(`recipientName`)로 보내면
      여전히 **400** 이다. 이것이 없으면 AC-3 의 201 이 "원래도 통과했을" 가능성을 못 배제한다.

---

# Related Specs

> **Before reading Related Specs**: `projects/ecommerce-microservices-platform/PROJECT.md` 의
> `domain`/`traits` 로 `rules/` 레이어를 먼저 해소할 것 (`CLAUDE.md` § Project Classification).

- `projects/ecommerce-microservices-platform/specs/services/order-service/architecture.md`
- `platform/entrypoint.md` · `platform/testing-strategy.md`

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/http/order-api.md` ← **수정 대상**
- `projects/ecommerce-microservices-platform/specs/contracts/http/user-api.md`
  (배송지 `recipientName` — 비교 대상, 무변경)

---

# Target Service

- `order-service`

---

# Implementation Notes

- 🔵 **계약이 구현을 따라가는 방향의 수정이다.** 이 저장소의 기본은 반대(계약 먼저)지만,
  여기서는 구현·DB·프런트·이벤트가 이미 한 이름으로 정착했고 계약서만 부분 갱신된 상태라
  **문서가 사실을 잘못 적은 경우**에 해당한다. 그 판단 근거를 PR 본문에 남길 것.
- 계약서 본문에 **왜 두 API 의 이름이 다른지** 한 줄 주석을 남기는 편이 낫다
  (배송지=`recipientName` / 주문=`recipient`, 체크아웃이 변환). 다음 사람이 같은 곳에서
  또 헷갈린다.

---

# Edge Cases

- **`address2` 는 nullable** — DTO 에 `@NotNull` 이 없다. 계약서가 required 로 읽히지 않게 할 것.
- **`items[].sellerId` 는 optional** — 부재 시 기본 셀러(`default`). 계약서 서술이 이미 맞으므로 깨지 말 것.
- **`Idempotency-Key` 헤더 서술은 이번 범위 밖** — 실측에서 정상 동작했다(멱등 201).

---

# Failure Scenarios

- 🔴 **`recipient` 만 고치고 이웃 필드를 안 센다** — 이 결함의 정체가 *"부분 갱신"* 이므로,
  또 부분 갱신하면 같은 함정을 남긴다. AC-4 가 이것을 막는다.
- 🔴 **AC-5(네거티브)를 건너뛴다** — 고친 뒤 201 만 보면 "원래도 됐던 것"과 구별되지 않는다.
- **코드를 계약서에 맞추려 든다** — 파괴적이고 Out of Scope 다. 그 방향이 필요하다는 판단이
  서면 ADR 로 올릴 것(`platform/architecture-decision-rule.md`).

---

# Test Requirements

- 🔵 **새 테스트를 만드는 것이 목적이 아니다** — 계약 문서 수정이다. 다만 AC-3/AC-5 의
  판정은 **실행된 요청**이어야 한다:
  - 기존 order-service IT 에 계약서 예시 페이로드를 그대로 쓰는 케이스가 이미 있으면 그것으로 판정
  - 없으면 라이브 스택(`demo-up.sh iam ecommerce`)에 소비자 토큰으로 실제 `POST /api/orders`
    2회(수정 전 예시 → 400 / 수정 후 예시 → 201)
- 기존 테스트 무변경 통과

---

# Follow-up (별건, 이 태스크에서 열지 않는다)

이 드리프트는 **한 번의 라이브 호출로** 드러났다. 같은 종류가 다른 계약 문서에 몇 개나 있는지는
아무도 모른다 — 계약서의 JSON 예시와 DTO 를 대조하는 **기계적 술어**가 없기 때문이다.
필요해지면 그 가드를 별도 티켓으로 세울 것(요청 예시 블록을 파싱해 대응 DTO 의 필드 집합과
대조). 🔴 다만 그 가드는 "예시 ↔ DTO" 매핑 규약이 먼저 서야 성립하므로, 지금 억지로 넣지 않는다.

---

# AC-4 전 필드 대조 결과 (2026-08-15) — 어긋난 곳은 **정확히 2건**, 나머지는 전부 일치

계약서의 JSON 예시를 `PlaceOrderRequest` / `OrderDetailResponse` 와 필드 단위로 맞췄다.
🔵 **이미 맞았던 항목도 적는다** — 무엇을 *안 봤는지*가 드러나야 한다.

## § POST /api/orders 요청 ↔ `PlaceOrderRequest`

| 블록 | 계약서 필드 | DTO | 판정 |
|---|---|---|---|
| `items[]` | `productId` · `variantId` · `productName` · `optionName` · `quantity` · `unitPrice` · `sellerId` | 동일 7개 | ✅ 7/7 확인함 |
| `shippingAddress` | **`recipientName`** | **`recipient`** (`@NotNull "recipient is required"`) | ❌ **정정** |
| `shippingAddress` | `phone` · `zipCode` · `address1` · `address2` | 동일 4개 | ✅ 확인함 |

## § GET /api/orders/{orderId} 응답 ↔ `OrderDetailResponse`

| 블록 | 계약서 필드 | DTO | 판정 |
|---|---|---|---|
| 최상위 | `orderId` · `status` · `totalPrice` · `items` · `shippingAddress` · `createdAt` · `updatedAt` | 동일 7개 | ✅ 확인함 |
| `items[]` | 위와 동일 7개 | 동일 | ✅ 확인함 |
| `shippingAddress` | **`recipientName`** | **`recipient`** | ❌ **정정** |
| `shippingAddress` | `phone` · `zipCode` · `address1` · `address2` | 동일 4개 | ✅ 확인함 |

## § GET /api/admin/orders/{orderId} — 손대지 않음

이미 `recipient` 로 옳았다(336행). **이 한 곳이 옳았다는 사실이 "옛 이름 관행" 가설을
배제한다** — 문서 전체가 낡은 게 아니라 **부분만 갱신된** 것이다.

⇒ `optionName` · `address2` 의 nullable 서술, `items[].sellerId` optional 서술,
`Idempotency-Key` 헤더 서술은 전부 구현과 일치해 **무변경**이다.

---

# AC-3 / AC-5 라이브 판정 (2026-08-15, `demo-up.sh iam ecommerce console` 스택)

🔴 **네거티브를 먼저 쟀다** — 고친 뒤 201 만 보면 *"원래도 통과했을"* 가능성을 못 배제한다.

```
수정 전 계약서 예시  {"shippingAddress":{"recipientName": ...}}
  → HTTP 400  {"code":"VALIDATION_ERROR","message":"recipient is required"}

수정 후 계약서 예시  {"shippingAddress":{"recipient": ...}}
  → HTTP 201  {"orderId":"246d75d5-78ee-403d-b389-79a2b3f8fbcd"}
```

응답부도 실물로 확인:

```
GET /api/orders/246d75d5-...  → 200
"shippingAddress":{"recipient":"데모 구매자","phone":"010-1234-5678",
                   "zipCode":"06232","address1":"서울 강남구 테헤란로 1","address2":"3층"}
응답에 "recipientName" 등장 횟수: 0
```

⇒ **계약서만 보고 만든 요청이 통과한다**(AC-3), **수정 전 예시는 여전히 거절된다**(AC-5).

🔵 새 테스트는 추가하지 않았다 — 이 태스크는 문서 수정이고, 판정은 **실행된 요청**으로 했다.
코드·DTO·DB·이벤트는 한 글자도 바뀌지 않았다.

---

# Definition of Done

- [x] AC-1 ~ AC-5 충족
- [x] 전 필드 대조표 + 수정 방향의 근거 기록(위)
- [x] `projects/ecommerce-microservices-platform/tasks/INDEX.md` 갱신
- [x] Ready for review
