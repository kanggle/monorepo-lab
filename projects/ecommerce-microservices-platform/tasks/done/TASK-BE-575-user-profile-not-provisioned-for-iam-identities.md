# Task ID

TASK-BE-575

# Title

IAM 로그인 사용자에게 user-service 프로필이 프로비저닝되지 않는다 — 마이페이지 세 화면이 도달 불가능하고, 배송지 생성은 404 대신 500 을 낸다

# Status

done

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

- [x] 구현 + 테스트
- [x] 계약 문서 갱신
- [x] 브라우저 검증 증거
- [x] Ready for review

---

# 결과 (2026-08-05)

## 🔴 컨슈머는 멀쩡했다. 받는 것이 없었을 뿐이다

착수 직후 `AccountCreatedConsumer` · `AccountCreatedEvent` · `AccountCreatedHandler` 가
**이미 존재한다**는 것을 발견했다. 안 B(이벤트 구독)는 고를 선택지가 아니라 이미 지어져
있었고, 코드는 옳았다. 티켓이 "먼저 확인할 것" 이라 적어 둔 그 확인이 답이었다.

브라우저로 실제 가입을 한 번 시켜 갈랐다:

```
가입 전  iam-kafka       account.created  0:0 1:0 2:0
        ecommerce-kafka account.created  0:0
가입 후  iam-kafka       account.created  0:0 1:0 2:1   ← IAM 은 발행했다
        ecommerce-kafka account.created  0:0           ← 여기는 그대로
        user_profiles: 새 행 없음
```

**IAM 은 자기 클러스터에 발행하고, ecommerce 컨슈머는 다른 클러스터를 구독한다.**
ecommerce 쪽 `account.created` 토픽은 컨슈머가 붙으며 auto-create 된 빈 토픽이라,
있다는 사실 자체가 배선된 것처럼 보이게 만든다.

**죽은 컨슈머는 하나가 아니다** — 전수로 세니 5개다: `user-service:account.created`,
`user-service:account.deleted`(**TASK-BE-258 GDPR 익명화**), `order-service:account.deleted`,
`product-service:account.status.changed`, `notification-service:account.created`.
→ **TASK-MONO-511** 로 분리했다.

## 무엇을 골랐는가

IAM 의 `consumer-integration-guide.md` 는 소비자가 IAM 과 **같은 클러스터**에 있다고 전제하고
(§590/607), 이벤트 스트림이 불가할 때의 대안으로 **pull-through(직접 조회) fallback** 을
명시적으로 인정한다(§742). 그래서:

- **이벤트 경로는 그대로 둔다** — 토폴로지가 고쳐지면 저절로 살아난다(MONO-511).
- **요청 시점 pull-through 를 더한다** — 게이트웨이가 검증한 신원으로 프로젝션을 만든다.
  IAM 변경이 필요 없다(이 티켓의 Out of Scope 를 지킨다).
- **두 경로가 같은 생성자로 수렴한다**(`UserProfileProvisioner`). 어느 쪽이 먼저 오든
  나머지는 no-op 이고, 만들어지는 행은 동일하다.

## 🔴 왜 호출부 4곳이 아니라 경계인가

프로필을 전제하는 엔드포인트는 지금 4개다. 호출부에 흩어 놓으면 **다섯 번째 엔드포인트가
그것 없이 추가될 수 있는 성질**이 된다 — 이 저장소가 반복해서 값을 치른 드리프트가 정확히
그 모양이다. 경계에 두면 이미 쓰인 모든 요청과 앞으로 쓰일 모든 요청에 대해 성립한다.

`/api/admin/**` 은 **제외했다.** 운영자의 `X-User-Id` 도 IAM subject 이므로, 제외하지 않으면
운영자가 admin API 를 건드릴 때마다 **자기가 조회하는 사용자 목록에 소비자 프로필로
등장한다.**

## 🔴 두 번의 읽기는 중복이 아니다 (실측으로 갈린 함정)

`findByUserId` 는 테넌트 스코프인데 `uq_user_profiles_user_id` 와 `existsByUserId` 는
**user_id 단독 전역**이다. 티켓의 Edge Case 는 "프로필은 `(user_id, tenant_id)` 로 갈린다"
고 적었는데 **실측 결과 틀렸다.** `existsByUserId` 만으로 판단하면 다른 테넌트에 행이 있을 때
조용히 건너뛰어 호출자가 영구히 404 가 되고, `findByUserId` 만으로 판단하면 unique 위반이
난다. 그래서 테넌트 읽기 먼저(정상 경로 1회 조회), 빗나갈 때만 전역을 묻고 **그 경우를
로그로 말한다.**

## 🔴 필터를 `@Component` 로 두면 슬라이스 4개가 죽는다

`@WebMvcTest` 는 `Filter` 빈을 전부 슬라이스에 넣는다. 스캔되는 필터로 두면 영속 계층이 없는
컨트롤러 슬라이스가 `UserProfileProvisioner` → JPA 리포지토리를 끌어와 컨텍스트 로딩에
실패한다 — **실측 58건 실패**(슬라이스 3종 + 계약 테스트). `FilterRegistrationBean` 으로
등록하니 240/240 초록. 순서는 `TenantContextFilter` 다음이어야 한다(테넌트가 묶이기 전에
프로비저닝하면 전부 기본 테넌트로 들어간다).

## AC 별 결과

| AC | 결과 |
|---|---|
| AC-0 재측정 | ✅ 표 그대로 재현. 표면은 4개가 아니라 **10개**(Wishlist 4 · Address 4 · User 2)로 다시 셌다 |
| AC-1 브라우저 | ✅ **가입부터** 브라우저만으로 8/8 PASS |
| AC-2 500→404 | ✅ 실측 `500 → 201`. 서비스 계층 가드는 404 |
| AC-3 로그 | ✅ `afterCommit` 으로 이동 |
| AC-4 IT | ✅ 통합 6건 + 단위 9건 추가 |
| AC-5 계약 정합 | ✅ — 그리고 **문서가 틀린 곳을 3군데 찾았다**(아래) |

### AC-0 재측정 (프로필 없는 IAM 신원, tenant=ecommerce)

```
                               수정 전                      수정 후
GET   /api/users/me            404 USER_PROFILE_NOT_FOUND  →  200
PATCH /api/users/me            404 USER_PROFILE_NOT_FOUND  →  200
POST  /api/wishlists           404 USER_PROFILE_NOT_FOUND  →  201
GET   /api/users/me/addresses  200 {"addresses":[]}        →  200
POST  /api/users/me/addresses  500 (FK 위반)                →  201
```

픽스처는 **실제 브라우저 가입**으로 만들었다(authorize → IAM `/signup`). 그래야 계정이
`ecommerce` 테넌트에 태어난다 — `POST /api/accounts/signup` 에 `X-Tenant-Id` 를 직접 넣는
방식은 게이트웨이가 클라이언트 신원 헤더를 strip 하므로 무시되고 `fan-platform` 이 된다
(실측).

### AC-1 브라우저 실주행 (신규 계정, 가입부터)

```
PASS  스토어 로그인 화면의 "회원가입" 으로 계정을 만들 수 있다   iam.local/login?registered
PASS  방금 만든 계정으로 스토어에 로그인된다
PASS  /my/profile   — 프로비저닝 없이 정상 동작
PASS  /my/wishlist  — 프로비저닝 없이 정상 동작
PASS  /my/addresses — 프로비저닝 없이 정상 동작
PASS  주소 검색 위젯이 readOnly 필드를 채운다  {"zipCode":"06236","address1":"서울 강남구 테헤란로 152 …"}
PASS  배송지를 화면에서 만들면 목록에 나타난다 (예전: 500 FK 위반)
PASS  마이페이지 전 과정에서 4xx/5xx API 응답이 없다   none
ALL 8 CHECKS PASS
```

DB 실측: `user_profiles` 에 `tenant_id=ecommerce` 행 생성, `user_addresses` 에 위젯이 채운
값 그대로 저장. **그동안 `ecommerce-kafka` 의 `account.created` 는 계속 0** — 이 결과는
이벤트 없이 얻은 것이고, 그게 이 수정의 요지다.

### 🔴 술어가 두 번 틀렸다 (기록)

1. 첫 브라우저 판정의 앵커가 `프로필`/`위시리스트`/`배송지` 였는데 그 단어는 전부
   **사이드바 nav 링크**에 있다. 로딩 셸(206자)만 렌더돼도 PASS 가 났다 →
   본문(`main`)으로 좁히고 그 화면에만 있는 문구로 바꿨다.
2. `/my/addresses` 앵커를 `최대 10개` 로 잡았는데 그건 **목록이 비어 있지 않을 때만** 나온다.
   신규 계정에선 `등록된 배송지가 없습니다` 다.

그리고 초기 측정 3회가 전 엔드포인트 401 을 냈는데, 시드 lib 의 `http()` 가
`ecommerce.local` 을 붙지 못한 **계측기 결함**이었다(raw curl 은 정상). 하마터면
"인증까지 깨졌다" 로 보고할 뻔했다.

## 🔴 계약 문서가 실제와 달랐다 (AC-5)

- `POST /api/users/me/addresses` 201 → 문서 `addressId` / **실제 `id`**
- `PATCH /api/users/me/addresses/{id}` 200 → 문서 `addressId` / **실제 `id`**
- `GET /api/users/me/addresses` 목록 원소 → 문서 `addressId` / **실제 `id`**

세 곳 모두 실측값으로 고쳤다. **주목할 점**: `UserApiContractTest` 는 이미 `id` 를 단언하고
있었다 — 테스트는 코드를 고정했고 마크다운만 따로 흘렀다. 테스트가 `SPEC_REF` 라는 이름의
상수로 스펙을 가리키지만 **그 파일을 읽지는 않는다.** (BE-576 의 "픽스처가 yml 손 복사본"과
같은 계열이다. 별도 티켓은 올리지 않았고 여기 기록해 둔다.)

## 함께 올린 티켓

- **TASK-MONO-511** — IAM 계정 이벤트가 소비자에 한 건도 도달하지 않는다(컨슈머 5개,
  `account.deleted` GDPR 포함). 이 티켓의 근본 원인이다.
- **TASK-FE-097** — 스토어 `/login` 의 "회원가입" 이 next-auth v4 URL 로 보내
  `?error=Configuration`. AC-1 검증을 IAM 로그인 화면의 링크로 우회한 이유다.
- **TASK-BE-577** — `email` scope 를 받은 액세스 토큰에 `email` 클레임이 없어
  `X-User-Email` 이 영영 안 나간다. 그래서 프로비저닝된 프로필의 이메일·이름이 빈다.

## 남은 것 / 알려진 한계

- **프로필의 이메일·이름은 여전히 빈칸이다.** ADR-MONO-037 P5/P6 설계대로 최소 프로필이고,
  채울 값의 출처가 없다(BE-577). 게이트웨이가 `X-User-Email` 을 주는 날 코드 변경 없이
  채워지도록 배선은 해 두었다.
- 그래서 **시드의 직접-DB 블록은 그대로 남는다.** 이 티켓의 Out of Scope 가 예상한
  "`PATCH /api/users/me` 한 줄로 대체" 는 성립하지 않는다 — `PATCH` 는
  nickname/phone/profileImageUrl 만 받고 email/name 을 받지 않는다. BE-577 이 닫히면 재검토.
- `AddressService` 의 프로필 가드는 HTTP 경로에서는 필터 때문에 도달하지 않는다.
  서비스 계층 테스트로 고정했고 이유를 코드에 적었다.
