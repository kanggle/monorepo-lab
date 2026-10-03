# ADR-MONO-079 — 소속사 · 셀러 · 콘솔의 팬 관리: **팬플랫폼의 아티스트와 스토어의 굿즈를 콘솔에서**

**Status:** ACCEPTED
**Date:** 2026-10-02
**주관 티켓:** `TASK-MONO-747`
**선행 결정:** [`ADR-MONO-077`](ADR-MONO-077-fan-goods-sold-by-the-ecommerce-store.md) (D — 굿즈는 스토어 상품, 팬은 카드·링크만) · [`ADR-MONO-078`](ADR-MONO-078-one-consumer-login-across-fan-and-store.md) (A — 전역 소비자 계정 풀 · CORRECTION 2026-10-02: 셀러 계정은 기계 계정, 사람 계정↔셀러 연결은 이 ADR 로) · [`ADR-MONO-059`](ADR-MONO-059-fan-authoring-identity-plane.md) (A — 아티스트가 진짜 계정으로 쓴다 · **binding: 운영자가 `B2C_CONSUMER` 테넌트를 assume 하는 새 조합은 열지 않는다**) · [`ADR-MONO-030`](ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md) (테넌트 = 스토어 운영사, 셀러 = 테넌트 안의 참여자) · [`ADR-MONO-042`](ADR-MONO-042-ecommerce-seller-onboarding-iam-provisioning.md) (셀러 기계 계정 · D4 정지 = 계정 잠금)

> 🟢 **ACCEPTED — 갈래 A** (2026-10-02 UTC, 소유자 정확형 `ADR-MONO-079 ACCEPTED — A`). 이름 · `ACCEPTED` · **갈래 letter** 세 요건이 모두 도착했다.
> 🔴 **라이더는 공급되지 않았다** — R1~R4 는 구현자 기본값 그대로다(§ 라이더 대조). 구현 티켓은 이 ACCEPT PR 안에서 기안했다(§ ACCEPT 가 만든 새 의무).

---

## Context

### 무엇을 원하나 (소유자, 2026-10-01 UTC)

> *«팬플랫폼은 여러 소속사의 여러 아티스트와 굿즈가 들어갈 수 있고, 이커머스 스토어에는 여러 판매자의 여러 물품이 판매될 수 있고,
> 콘솔에서 팬플랫폼의 아티스트와 굿즈가 관리되고 스토어의 물품을 관리하고 싶어.»*

소유자가 **이미 고른** 갈래(AskUserQuestion, 2026-10-01) — 이 ADR 은 다시 묻지 않고 결정으로 적는다:

| 질문 | 소유자 선택 |
|---|---|
| 소속사는 무엇인가 | **관리 대상**(팬플랫폼 테넌트 안의 엔티티) **+ 스토어 셀러와 연결** |
| 굿즈는 어디 사는가 | **스토어 상품 그대로**(`ADR-MONO-077` D1) |
| 아티스트 ↔ 굿즈 연결 | **상품의 «컬렉션» 속성** |

그리고 `TASK-MONO-745` 에서 흡수한 것(소유자 «079로 합치기», 2026-10-02): **사람의 풀 계정을 셀러에 연결해 스토어 사이트 역할 `SELLER` 를 준다** + 셀러 정지의 의미.

### 지금 어떻게 생겼나 (실측 — 코드, 2026-10-02)

| 영역 | 현황 | 출처 |
|---|---|---|
| 소속사 | **엔티티가 없다** — `artists.agency` · `artist_groups.agency` 가 각각 자유 텍스트 `VARCHAR(120)`(정규화 · FK 없음, 표시용) | artist-service `V1__init.sql` |
| 아티스트 디렉터리 쓰기 | `POST/PATCH /api/artists` 는 `ADMIN`·`OPERATOR`·`SUPER_ADMIN`·`FAN_OPERATOR` 만 — 🔴 **이 역할들은 팬 테넌트에서 발급될 길이 없다**(ADR-059 binding · `fan` 도메인 구독 0/18 · `trustEntitledDomains()` 꺼짐 · `FanTenantGatePolicyTest`). 데모 디렉터리는 `seed-fan.sh` 가 **DB 에 직접** 쓴다 | artist-service `SecurityConfig` 주석(059/061/063 인용) |
| `artists.account_id` | 계약상 아티스트의 IAM 계정. V3 가 `account_id = id`(아티스트 행 id)로 채웠다 — 데모의 6명은 같은 id 로 IAM 계정을 시드해 로그인된다(`R__06`) | `V3__artists_account_id.sql:43` |
| 상품 분류 | `categories`(공유 트리) · `products.category_id` 뿐 — **태그·브랜드·컬렉션·속성 없음** | product-service 마이그레이션 · `Product.java` |
| 팬 → 굿즈 | 아직 없음. `TASK-MONO-739`(ADR-077 D)의 계획 = 카테고리 «아티스트 굿즈» 하나 + **상품명이 예명으로 시작**(라이더 R4) + 공개 스냅숏에서 거르기. 079 뒤로 미뤄져 있다 | `tasks/ready/TASK-MONO-739-*` |
| 셀러 | `sellers(tenant_id, seller_id, display_name, status, account_id, identity_id)` · 셀러 계정은 **기계 계정**(`seller+<tenant>+<id>@marketplace.local`, 무작위 비밀번호) · 정지/폐점 → 그 계정 `LOCKED` | product-service `V14`·`V15` · `AccountServiceSellerProvisioner` |
| 셀러로 일하는 사람 | **경로 없음** — `X-Seller-Scope` 는 소비 쪽만 있고 운영자 토큰 클레임에서 주입되지 않는다(`ADR-MONO-030` Step 4) | ecommerce gateway `GatewayIdentityConfig` 주석 |
| 콘솔 | 이커머스만 화면이 있다 — `ecommerce/products/**` · `ecommerce/sellers/**`(등록·프로비저닝·정지·폐점). **팬 화면·BFF 라우트 0** | console-web `app/(console)/ecommerce/**` |
| 테넌트 | 팬 = 단일 테넌트 `fan-platform`(`B2C_CONSUMER`) · 이커머스 = `ecommerce` 테넌트 안에 셀러 여럿(ADR-030 바깥=테넌트, 안=셀러) | account-service `V0009` · ADR-030 |

### 🔴 충돌 — 콘솔에서 팬을 관리하려면 ADR-059 의 binding 을 건드린다

콘솔은 «운영자가 도메인 테넌트를 assume 해서» 화면을 연다(이커머스가 그 모양). 팬에 같은 길을 열면 **운영자가 `B2C_CONSUMER` 테넌트를 assume** 하게 되고,
ADR-059 는 그것을 **binding 으로 닫았다**. 다만 059 가 닫은 **행위**는 «운영자가 아티스트 **이름으로 글을 쓰는 것**»(갈래 B, 대리 저작)이었고, 059 스스로
«콘솔에 `fan` 제품이 없으니 그 화면이 없다 — 그런 화면이 생기면 결정의 근거가 사라지므로 다시 정해야 한다» 고 적었다(artist-service `SecurityConfig`
주석). 이 ADR 이 그 «다시 정함» 이다.

---

## Decision

### D1 — 소속사는 **artist-service 안의 엔티티**다 (소유자 선택의 구체화)

- 새 테이블 `agencies(id, tenant_id, name, status, created_at, updated_at, version)` — `UNIQUE(tenant_id, name)`. 아티스트·그룹은 `agency_id`(NULL 허용 FK)로 소속.
- 기존 자유 텍스트 `agency` 컬럼은 **이전 기간에만** 남긴다 — 값이 같은 것끼리 소속사 행으로 묶어 `agency_id` 를 채운 뒤 표시는 소속사 이름에서 읽는다(컬럼 제거는 별도 단계).
- 왜 artist-service 인가: 소속사는 아티스트·그룹과 같은 디렉터리의 일부이고 같은 테넌트·같은 쓰기 권한을 쓴다. 새 서비스는 이 크기에서 운영 비용만 늘린다.

### D2 — 소속사 ↔ 셀러: **0..1**, 연결은 **팬 쪽이 갖는다**

- `agencies.store_seller_id`(NULL 허용) — 그 소속사의 굿즈를 파는 스토어 셀러(`ecommerce` 테넌트의 `seller_id`). 굿즈를 팔지 않는 소속사는 NULL.
- 팬 쪽이 갖는 이유: 그 연결을 **읽는 쪽이 팬**이다(소속사 페이지 «이 소속사의 굿즈»). 스토어는 소속사를 몰라도 된다 — 셀러는 소속사가 아니어도 되고(일반 판매자), 스토어 도메인에 팬 개념을 넣지 않는다(ADR-077 D1 의 «팬은 링크만» 과 같은 방향).
- DB 가 다르므로 FK 는 없다 — 값 검증은 쓰기 때 스토어의 셀러 조회로(없거나 `CLOSED` 면 거절).

### D3 — 아티스트 ↔ 굿즈: 상품의 **`collection_ref`** (소유자 선택의 구체화)

- `products.collection_ref VARCHAR(64) NULL` + 인덱스 `(tenant_id, collection_ref)`. 값 = **팬 아티스트 id**(`artists.id`, UUID) — 두 프로젝트가 공유하는 유일한 식별자다.
- 팬 아티스트 페이지는 지금처럼 **스토어 공개 스냅숏**(`@demo/public-data`)에서 `collection_ref = <그 아티스트 id>` 인 상품을 골라 카드로 보인다 — 익명 방문 «백엔드 호출 0» 불변식 유지(ADR-077).
- 이것이 `TASK-MONO-739` 의 «상품명이 예명으로 시작» 규칙(R4)을 **대체**한다 — 예명이 바뀌거나 겹치면 깨지는 문자열 규칙 대신 id. 카테고리 «아티스트 굿즈» 는 «전체 굿즈» 목록용으로 남는다.
- 자유 문자열이라 스토어에 팬 전용 테이블이 생기지 않는다. 값의 의미(«팬 아티스트 id»)는 계약 문서에 적는다.

### D4 — 🔴 콘솔의 팬 관리 — **갈래가 갈리는 곳**

| 갈래 | 무엇 | 059 와의 관계 |
|---|---|---|
| **① 관리 표면만 재개방** | 운영자가 `fan-platform` 을 assume 할 수 있게 하되 **디렉터리 관리(소속사·아티스트·그룹·팬덤 CRUD)만** 연다. `fan` 도메인 구독 + `FAN_OPERATOR` 파생 + artist-service **관리 경로에만** 엔타이틀먼트 신뢰. 커뮤니티(글·댓글·반응)·멤버십·알림은 계속 닫는다 | 059 의 binding 을 **부분 개정**: «대리 저작(`ARTIST_POST`)은 계속 금지» 는 유지하고 «디렉터리 관리는 운영자 몫» 을 새로 적는다 |
| **② 소속사 계정이 팬 웹에서 직접** | 소속사 담당자의 **풀 계정**에 팬 사이트 역할 `AGENCY_MANAGER`(`consumer_site_roles`)를 주고, 팬 웹의 관리 화면에서 **자기 소속사**의 아티스트만 관리. 콘솔은 팬에 손대지 않는다 | 059 무변경(운영자 assume 없음) — 대신 소비자 사이트에 관리 권한이 생긴다(풀 계정 + 사이트 역할) |
| ③ 콘솔 BFF → 내부 관리 API | 콘솔이 워크로드 자격으로 artist-service 내부 API 를 부르고, 권한 판정은 콘솔/admin-service 가 한다 | 테넌트 평면을 우회 — 🔴 이 저장소의 «테넌트 = 경계» 모양을 깬다. 추천하지 않음 |

### D5 — 사람 계정 ↔ 셀러: **셀러 구성원**, 정지 = **역할 회수** (`TASK-MONO-745` 흡수)

- product-service 새 테이블 `seller_members(tenant_id, seller_id, account_id, role, status, joined_at)` — 한 셀러에 사람 **여럿**.
- 연결 = **초대 → 로그인한 본인이 수락**: 운영자(`ECOMMERCE_OPERATOR`, 콘솔 셀러 화면)가 이메일로 초대하고, 그 이메일의 사람이 **스토어에 로그인한 상태로** 수락해야 붙는다 — 이메일 일치만으로는 붙지 않는다(`ADR-MONO-034` § 1.3 · ADR-078 D2).
- 수락하면 IAM 이 그 풀 계정에 `consumer_site_roles(account, ecommerce, SELLER)` 를 쓴다 → 스토어 토큰 `["CUSTOMER","SELLER"]`(ADR-078 § 4: 시드 ∪ 사이트 역할).
- 🔴 **셀러 정지 · 폐점 = 구성원의 `SELLER` 사이트 역할 회수**다. 사람 계정을 **잠그지 않는다** — 잠그면 그 사람의 쇼핑·팬 이용까지 막힌다. 셀러 기계 계정은 지금처럼 잠근다(ADR-042 D4 유지).
- 역방향(계정 잠금 → 셀러 정지, `AccountStatusChangedSellerConsumer`)은 **기계 계정에만** 적용한다 — 구성원 한 명이 잠겨도 셀러는 정지되지 않는다. 그 소비자가 이벤트 `tenantId` 를 셀러 테넌트로 읽는 가정(풀 계정이면 `consumer-pool`)은 기계 계정이 풀로 가지 않으므로 그대로 맞다(ADR-078 CORRECTION).
- **범위 밖**: 셀러가 **일하는 화면**(셀러 센터 · 운영자 토큰의 셀러 범위 클레임 주입, ADR-030 Step 4). D5 는 «누가 그 셀러인가» 만 정한다 — 일하는 표면은 이 ADR 뒤의 별도 결정이다.

---

## 갈래 — ACCEPT 단위

D1 · D2 · D3 · D5 는 갈래와 무관하게 같다. 갈래는 **D4 와 범위**에서 갈린다.

| 갈래 | D4 | 범위 |
|---|---|---|
| **A** | ① 관리 표면만 재개방 — **콘솔에서** 소속사·아티스트 관리 | D1~D5 전부 |
| **B** | ② 소속사 계정이 **팬 웹에서** 직접 | D1~D5 전부 |
| **C** | 정하지 않음(팬 관리 표면 없음 — 지금처럼 시드로만) | D1 · D2 · D3 만(소속사 엔티티 · 셀러 연결 · 컬렉션). D5 · 관리 표면은 나중 |

## 추천 — 🔴 **구현자의 선호**다. 소유자 결정이 아니다

**A.** 소유자가 말한 목표 상태가 «**콘솔에서** 팬의 아티스트와 굿즈를 관리» 이고, 콘솔에는 이커머스 상품·셀러 화면이라는 같은 모양의 선례가 있다.
059 가 막은 것은 «운영자가 아티스트 이름으로 **쓰는**» 위조 표면이었다 — 디렉터리 관리는 그 행위가 아니고, 059 자신이 «관리 화면이 생기면 다시 정하라» 고
남겼다. 부분 개정으로 대리 저작 금지는 그대로 지킨다.

B 는 059 를 건드리지 않는 장점이 있지만, 관리 권한이 **소비자 사이트**에 생기고(풀 계정 + 관리 역할), 소속사가 콘솔이 아닌 팬 웹에서 일하게 되어 목표 상태와
다르다. C 는 가장 작지만 «콘솔에서 관리» 를 미룬다 — `TASK-MONO-739`(굿즈)를 먼저 풀고 싶을 때의 선택이다.

## 라이더 — 🔴 **내 선택**이지 소유자 결정이 아니다

| 번호 | 기본값 | 뒤집기 |
|---|---|---|
| R1 | `collection_ref` 값은 **아티스트 id**(그룹·소속사 단위 컬렉션은 나중) | «그룹 굿즈도 컬렉션으로» |
| R2 | 소속사 ↔ 셀러는 **0..1**(소속사 하나에 셀러 하나) | «소속사가 셀러 여럿» |
| R3 | (A 일 때) 운영자의 팬 관리 권한은 **플랫폼 운영자만**(고객사 운영자 assume 불가) | «고객사 운영자도» |
| R4 | 셀러 구성원 역할은 하나(`MEMBER`) — 구성원 안의 등급은 나중 | «소유자/직원 구분» |

---

## Alternatives Considered

- **소속사를 테넌트로**(팬 테넌트를 소속사마다) — 기각. 팬은 «여러 소속사의 아티스트가 한 플랫폼에» 이고(소유자 목표), 팔로우·피드가 테넌트를 넘나들게 된다. 소유자도 «관리 대상» 을 골랐다.
- **굿즈를 팬이 소유** — 기각(소유자 선택 · ADR-077 D1).
- **상품명 접두어 규칙 유지(739 R4)** — 기각. 예명 변경·중복에 깨지는 문자열 규칙이다(D3).
- **스토어에 `collections` 테이블** — 기각(지금 크기에서). 이름·설명을 가진 컬렉션이 필요해지면 그때. 자유 문자열 `collection_ref` 가 팬 개념을 스토어에 들이지 않는다.
- **셀러 정지 = 사람 계정 잠금** — 기각(D5). 그 사람의 다른 이용까지 막는다.
- **셀러 기계 계정을 풀로**(옛 `TASK-MONO-745`) — 기각(ADR-078 CORRECTION). 로그인하는 사람이 없다.

## Consequences

### ACCEPT 가 인가하는 것

- artist-service: `agencies` 테이블 · 아티스트/그룹 `agency_id` · 자유 텍스트 이전 · 소속사 CRUD API
- product-service: `products.collection_ref` · (A·B) `seller_members` + 초대·수락 API · 셀러 정지의 구성원 역할 회수
- IAM: 셀러 구성원 수락 시 `consumer_site_roles(ecommerce, SELLER)` 쓰기 · 회수 (내부 API)
- (A) `fan` 도메인 구독 · `FAN_OPERATOR` 파생 · artist-service 관리 경로 엔타이틀먼트 신뢰 · 콘솔 팬 화면(소속사·아티스트) · ADR-059 부분 개정 기록
- (B) 팬 사이트 역할 `AGENCY_MANAGER` · 팬 웹 관리 화면 · 소속사 범위 강제
- `TASK-MONO-739` 의 R4(예명 접두어)를 D3 으로 교체

### 건드리지 않는 것

- 팬의 커뮤니티 쓰기 권한(대리 저작 금지 — 059 유지) · 셀러 기계 계정 모델(ADR-042) · 소비자 풀(ADR-078) · 셀러가 일하는 화면(범위 밖)

### 새로 생기는 위험

- (A) 운영자가 팬 테넌트에 들어오는 **첫 길**이다 — 관리 경로 밖(커뮤니티·멤버십)으로 새지 않는다는 것을 시험으로 고정해야 한다(«열린 경로 + 닫힌 경로» 대조군).
- `collection_ref` 는 FK 가 없다 — 아티스트가 보관되면 그 상품 카드가 고아가 된다(팬은 보관된 아티스트를 안 보이므로 노출은 없다).
- 구성원 수락 흐름은 계정 탈취 경로가 될 수 있다 — «남의 이메일로 초대 → 그 이메일의 남이 수락» 은 정상이지만, «초대받지 않은 사람이 수락» 은 막혀야 한다(초대 토큰 + 로그인 본인 이메일 일치 + 1회).

## Verification (구현 단계에서)

- (A) 운영자 토큰으로 artist-service 관리 경로 200 · 커뮤니티 `ARTIST_POST` 403 · 멤버십 403 — 같은 시험 안에서.
- 팬 아티스트 페이지가 `collection_ref` 로 고른 굿즈만 보이고, 다른 아티스트 굿즈는 안 보인다.
- 셀러 정지 → 구성원 스토어 토큰에서 `SELLER` 가 빠지고 `CUSTOMER` 는 남는다 · 그 사람의 팬 로그인 무영향.

## History

| 날짜 (UTC) | 상태 | 내용 | 근거 |
|---|---|---|---|
| 2026-10-02 | PROPOSED | D1~D5 · 갈래 A/B/C · 추천 A · 라이더 R1~R4. 소유자가 고른 세 갈래(소속사=관리 대상+셀러 연결 · 굿즈=스토어 상품 · 컬렉션 속성)는 결정으로 적음. `TASK-MONO-745` 흡수분(D5) 포함 | `TASK-MONO-747` · AC-0 실측(위 Context 표) |
| 2026-10-02 | PROPOSED → **ACCEPTED (A)** | 소유자 정확형 `ADR-MONO-079 ACCEPTED — A`. D1~D5 본문은 **바이트 그대로** — ACCEPT 는 확정이지 재결정이 아니다. 라이더 미공급 → 기본값 | 소유자(이 대화) · § ACCEPT 게이트 기록 |

## ACCEPT 게이트 기록 (2026-10-02 UTC) — 🔴 **게이트가 실제로 물었다**

소유자의 첫 메시지는 *«ADR-079 갈래 선택 A»* 였다 — 갈래 letter 는 있지만 정확형(`ADR-MONO-079 ACCEPTED — <letter>`)이 아니다.
이 ADR 의 추천도 A 였으므로, 그 문장을 수락으로 읽으면 «구현자 추천을 소유자 결정으로 읽는» 귀속 오류와 구별되지 않는다.
그래서 반영하지 않고 정확형을 요청했고, 다음 메시지로 *«ADR-MONO-079 ACCEPTED — A»* 가 도착했다. ACCEPT 는 그 두 번째 메시지에 귀속된다.

## 라이더 대조 (ACCEPT 시점) — 🔴 **반사가 아니라 대조로 했다**

ACCEPT 메시지에 라이더 언급이 없다. 아래 기본값이 **구현자의 선택**으로 남는다 — 소유자가 언제든 한 줄로 뒤집을 수 있고, 그때 해당 티켓의 AC 를 고친다.

| 번호 | 적용되는 기본값 | 걸리는 티켓 |
|---|---|---|
| R1 | `collection_ref` = **아티스트 id** 단위(그룹·소속사 컬렉션은 나중) | `TASK-MONO-749` |
| R2 | 소속사 ↔ 셀러 **0..1** | `TASK-MONO-748` |
| R3 | 운영자의 팬 관리 권한 = **플랫폼 운영자만**(고객사 운영자 assume 불가) | `TASK-MONO-750` |
| R4 | 셀러 구성원 역할 **하나**(`MEMBER`) | `TASK-MONO-752` |

## ACCEPT 가 만든 새 의무 — 티켓으로 기안했다

| 티켓 | 무엇 | 선행 |
|---|---|---|
| `TASK-MONO-748` | D1 · D2 — artist-service `agencies` · 아티스트/그룹 `agency_id` · 자유 텍스트 이전 · 소속사 ↔ 셀러 연결(0..1, 쓰기 때 셀러 검증) · 소속사 CRUD API | — |
| `TASK-MONO-749` | D3 — `products.collection_ref` · 공개 스냅숏에 싣기 · 팬 아티스트 페이지가 그것으로 굿즈 선택 | — (`TASK-MONO-739` 가 이것 위에서 시드) |
| `TASK-MONO-750` | D4-A — 운영자의 팬 디렉터리 관리 경로: `fan` 도메인 구독 · `FAN_OPERATOR` 파생 · artist-service **관리 경로에만** 엔타이틀먼트 신뢰 · 커뮤니티·멤버십은 계속 닫힘(대조군) · `ADR-MONO-059` 부분 개정 기록 | 748 |
| `TASK-MONO-751` | D4-A — 콘솔 팬 화면(소속사 · 아티스트 · 그룹) + BFF 라우트 | 748 · 750 |
| `TASK-MONO-752` | D5 — `seller_members` · 초대 → 로그인 본인 수락 · IAM `consumer_site_roles(ecommerce, SELLER)` 쓰기/회수 · 셀러 정지 = 역할 회수 · 콘솔 셀러 화면의 초대 | — |

- `TASK-MONO-739`(팬 굿즈)의 라이더 R4(예명 접두어)는 D3 으로 **대체**된다 — 739 에 그 정정과 선행(749)을 적었다.
- `ADR-MONO-059` 에 부분 개정 사실을 덧붙였다(그 ADR 의 binding 문장 자체는 바이트 그대로 — 개정 범위는 이 ADR D4-A).

## R3 보강 기록 (2026-10-03 UTC — 덧붙임만, 위 본문은 바이트 그대로)

- **소유자 결정 2026-10-03 (원문): «데모 운영자는 팬 전용으로»** — 데모 플랫폼 운영자 신원(`platform@demo.com`, `'*'`, `TASK-MONO-751`)은 **`fan-platform` 만** assume 할 수 있다. 구현: `admin_operators.confined_tenant_id`(V0046) — 비-NULL 이면 assignment-check 가 `'*'` 의 «모든 테넌트» 단계보다 **먼저** 다른 테넌트를 거절하고 레지스트리도 그 테넌트로 좁힌다(좁히기만 — R3 는 그대로, 일반 플랫폼 운영자는 NULL 이라 불변). 근거: `TASK-MONO-751` § CORRECTION (2026-10-03 UTC).