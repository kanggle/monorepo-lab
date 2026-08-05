# Task ID

TASK-BE-575

# Title

IAM 로그인 사용자에게 user-service 프로필이 프로비저닝되지 않는다 — 마이페이지 세 화면이 도달 불가능하고, 배송지 생성은 404 대신 500 을 낸다

# Status

ready

# Owner

backend

# Task Tags

- code
- test
- contract

---

# 배경 — `TASK-MONO-506` 시드 중 발견했다

데모 계정으로 스토어프런트의 마이페이지를 채우려다 나왔다. **결함 자체는 데모와 무관하다** —
IAM(SAS)으로 로그인한 **모든** 소비자에게 해당한다.

`ADR-MONO-040` 이후 회원가입/신원은 IAM 이 소유한다. 그런데 user-service 는 여전히 자기
`user_profiles` 행을 전제하고, **그 행을 만드는 경로가 어디에도 없다.**

## 실측 (데모 계정, 유효한 소비자 토큰 · 게이트웨이 경유)

```
GET   /api/users/me            404  USER_PROFILE_NOT_FOUND
PATCH /api/users/me            404  USER_PROFILE_NOT_FOUND
POST  /api/wishlists           404  USER_PROFILE_NOT_FOUND
POST  /api/users/me/addresses  500  (FK fk_user_addresses_user_id 위반)
GET   /api/users/me/addresses  200  {"addresses":[]}     ← 읽기만 통과
```

user-service 의 컨트롤러는 4개뿐이다 — `AddressController` · `AdminUserController` ·
`UserController` · `WishlistController`. **전수 확인 결과 프로필 생성 엔드포인트가 존재하지
않는다.** `POST /api/users` 도, 내부용 프로비저닝 엔드포인트도 없다. `GET/PATCH /api/users/me`
는 둘 다 기존 행을 전제한다.

## 결과 — 세 화면이 IAM 사용자에게 애초에 열리지 않았다

`/my/profile` · `/my/wishlist` · `/my/addresses`. 그리고 체크아웃의 `address1`/`zipCode` 는
주소검색 위젯 전용 `readOnly` 필드라 **저장된 배송지 없이는 결제 화면을 통과할 수 없다** —
즉 이 결함은 구매 경로까지 막는다.

## 두 번째 결함 — 500 과 거짓 성공 로그

`POST /api/users/me/addresses` 는 프로필 부재를 **검사하지 않고** INSERT 를 시도해 FK 위반으로
500 을 낸다. 형제 엔드포인트들이 같은 상황에서 404 `USER_PROFILE_NOT_FOUND` 를 내는 것과
불일치한다(500 은 "서버가 고장났다" 이지 "선행 조건이 없다" 가 아니다).

그리고 롤백 **직전에** 성공을 로그한다:

```
INFO  Address created: addressId=be404146-…, userId=0199de70-…    ← 커밋 전
ERROR insert or update on table "user_addresses" violates foreign key constraint
```

로그만 보는 운영자는 주소가 생성됐다고 믿는다.

---

# Goal

IAM 신원으로 로그인한 소비자가 **추가 조작 없이** 마이페이지 세 화면을 쓸 수 있다.
그리고 프로필이 없는 상태에서 배송지를 만들면 500 이 아니라 형제들과 같은 404 가 나온다.

---

# Scope

## In Scope

- user-service 에 IAM 신원 → 프로필 프로비저닝 경로 추가
- `AddressService.createAddress` 의 프로필 부재 처리(404 로 정렬)
- "Address created" 로그를 커밋 이후로 옮기거나 문구를 시도로 바꾼다
- `specs/contracts/http/` 갱신(엔드포인트가 늘면)
- 테스트: 프로필 없는 사용자의 4개 엔드포인트 응답 코드 고정

## Out of Scope

- IAM 쪽 변경 — 신원은 이미 정상이다. 빠진 것은 **도메인 프로필**이다
- `infra/demo/seed/seed-ecommerce.sh` 의 직접-DB 블록 제거 — 이 티켓이 닫히면
  그 블록은 `PATCH /api/users/me` 한 줄로 대체된다(별도 정리)

---

# 설계 선택지 (착수 시 ADR 필요 여부 판단할 것)

| 안 | 방식 | 유의점 |
|---|---|---|
| A | **지연 생성(lazy)** — `GET/PATCH /api/users/me` 가 없으면 토큰 클레임(`sub`/`email`/`tenant_id`)으로 만든다 | 가장 작다. 읽기 요청이 쓰기를 하는 것이 받아들여지는지 확인 필요 |
| B | **이벤트 구독** — IAM 의 계정 생성 이벤트를 소비해 프로필을 만든다 | 정석. IAM 이 그 이벤트를 실제로 발행하는지 **먼저 확인**할 것 |
| C | **내부 프로비저닝 엔드포인트** — 게이트웨이 뒤 S2S 로 호출 | 호출자가 누구인지 정해야 한다 |

A 를 고르더라도 `X-User-Email` 이 게이트웨이에서 실제로 주입되는지 확인해야 한다
(`GatewayIdentityConfig` 는 `skipIfNull("X-User-Email", JwtClaims::email)` 이다 — **null 이면
헤더가 아예 없다**).

---

# Acceptance Criteria

- [ ] **AC-0 (재측정)** — 착수 시 위 4개 엔드포인트의 응답을 **다시 측정한다.** 이 티켓의
      표는 2026-08-05 실측이지 현재 상태의 보증이 아니다. 그리고 `user_profiles` 를 전제하는
      **다른** 표면이 더 있는지 전수로 센다(이 티켓은 4개만 확인했다)
- [ ] **AC-1** — 프로필이 없는 IAM 사용자가 `/my/profile` · `/my/wishlist` · `/my/addresses`
      를 **브라우저로** 열어 정상 동작한다. 직접 토큰 스모크로 대체하지 않는다
- [ ] **AC-2** — 프로필 부재 시 `POST /api/users/me/addresses` 가 500 이 아니다
      (형제 엔드포인트와 같은 코드/코드명)
- [ ] **AC-3** — 성공 로그가 커밋 이후에만 남는다(롤백된 트랜잭션이 "created" 를 남기지 않는다)
- [ ] **AC-4** — 통합 테스트: 프로필 없는 사용자의 4개 엔드포인트 응답 코드가 고정된다.
      픽스처가 **프로덕션에 존재할 수 있는 상태**여야 한다(프로필 없이 유효한 토큰을 가진 사용자)
- [ ] **AC-5** — 계약 문서와 실제 응답이 일치한다

---

# Related Specs

- `projects/ecommerce-microservices-platform/specs/services/user-service/`
- `docs/adr/ADR-MONO-040-*` — 신원이 IAM 으로 이동한 결정
- `infra/demo/seed/seed-ecommerce.sh` — 현재의 우회(직접-DB)와 그 사유

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/http/user-api.md`

---

# Edge Cases

- 같은 이메일이 여러 테넌트에 존재한다 — 프로필은 `(user_id, tenant_id)` 로 갈린다
- 동시 요청 두 건이 동시에 지연 생성한다(안 A) — 유니크 제약 + 경합 처리
- 토큰에 `email` 이 없다(선택 클레임) — 프로필의 email 을 무엇으로 채울 것인가
- 이미 프로필이 있는 레거시 사용자 — 덮어쓰지 않아야 한다

# Failure Scenarios

- **지연 생성이 읽기 경로에 쓰기를 넣어** 캐시/복제 지연과 충돌한다
- **이벤트 구독(안 B)을 골랐는데 IAM 이 그 이벤트를 발행하지 않는다** — 착수 전 확인
- 404 로만 바꾸고 프로비저닝을 안 붙이면 **증상만 예뻐지고 화면은 그대로 안 열린다**

# Test Requirements

- 프로필 없는 사용자 시나리오 통합 테스트(위 4개 엔드포인트)
- 브라우저 실주행(AC-1)

# Definition of Done

- [ ] 구현 + 테스트
- [ ] 계약 문서 갱신
- [ ] 브라우저 검증 증거
- [ ] Ready for review
