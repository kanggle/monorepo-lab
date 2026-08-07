# 면접 데모 워크스루

> **읽는 사람**: 이 저장소를 처음 여는 사람(면접관 · 신규 합류자).
> **목적**: 계정 하나로 세 표면(스토어프런트 · 팬 · 콘솔)을 눌러 보고, **각 화면이
> 무엇을 증명하는지** 알게 하는 것.
>
> 이 문서는 사람용 참조다(`docs/guides/` 규약 — AI 에이전트의 소스오브트루스가 아니다).

---

## 0. 계정

| | |
|---|---|
| 이메일 | `demo@demo.com` |
| 비밀번호 | `Demo1234!` |

**하나의 자격증명으로 세 표면 전부**에 로그인한다. 같은 이메일/비밀번호가 표면마다
다른 신원으로 해석되는 것이 아니라, IAM(Spring Authorization Server)이 **어느
클라이언트로 왔는지**에 따라 다른 테넌트·역할의 토큰을 발급한다:

- 스토어프런트 → `tenant_id=ecommerce`, `roles=[CUSTOMER]`
- 팬 → `tenant_id=fan-platform`, `roles=[CUSTOMER]`
- 콘솔 → `tenant_id=iam` 로 로그인한 뒤, **`demo-corp` 를 assume** 하면
  `roles=[ECOMMERCE_OPERATOR, ERP_OPERATOR, FINANCE_OPERATOR, SCM_OPERATOR, WMS_OPERATOR, …]`

마지막 줄이 이 데모의 설계 요점이다. 운영자 권한은 계정에 붙어 있지 않고
**테넌트를 assume 하는 순간 그 테넌트의 도메인 구독에서 파생된다**
(`OperatorRoleDerivation.fromEntitledDomains`). `demo-corp` 하나가 5개 도메인을
구독하고 있으므로, 테넌트 스위처를 만지지 않고도 콘솔의 도메인 운영 5개 섹션이
전부 열린다.

---

## 1. 기동

```bash
# 전체
bash infra/demo/demo-up.sh full

# 슬라이스 (하드 의존은 자동 포함 — console 만 줘도 iam 이 함께 뜬다)
bash infra/demo/demo-up.sh iam ecommerce console

# 팬 표면 (§3). 로컬 메모리로는 스토어와 **동시에** 못 띄운다 — 먼저 내린다
bash infra/demo/demo-down.sh ecommerce
bash infra/demo/demo-up.sh iam fan console

# 백오피스 도메인은 **한 번에 하나씩**. 실측: wms 슬라이스 10컨 ≈ 1.9 GiB,
# erp 슬라이스 8컨 ≈ 3.9 GiB — 둘을 동시에 올리면 iam+console 과 합쳐 한계에 닿는다
bash infra/demo/demo-down.sh wms          # 볼륨은 보존된다(`-v` 아님)
bash infra/demo/demo-up.sh iam erp console
```

기동 마지막에 **도메인 데이터 시드**가 자동으로 돈다(`infra/demo/seed/`). 끄려면
`DEMO_SEED=0`. 시드가 실패해도 스택은 유지되며, 실패한 도메인 이름이 로그 마지막에
다시 나열된다.

진입 URL (로컬 기준 — `DEMO_DOMAIN` 이 `local` 이 아니면 그 도메인으로 치환):

| 표면 | URL |
|---|---|
| 콘솔 | `http://console.local` |
| 스토어프런트 | `http://web.ecommerce.local` |
| 팬 | `http://web.fan-platform.local` |

`*.local` 은 hosts 파일에 `127.0.0.1` 로 등록돼 있어야 한다(TEMPLATE.md § Local
Network Convention). EC2 데모는 `<ip>.sslip.io` 를 쓰므로 hosts 등록이 필요 없다.

---

## 2. 스토어프런트 — 구매를 끝까지 완주한다

`http://web.ecommerce.local` → 우상단 **로그인** → IAM 로그인 폼 → 스토어로 복귀.

### 클릭 경로

1. **홈 / 상품** — 카탈로그. 상품 8 · 카테고리 7 · 변형 28 (product-service 의
   `V8__seed_sample_data.sql`).
2. **상품 상세** — 옵션(변형)을 고르면 총액이 계산된다.
3. **장바구니** — 항목을 **체크박스로 선택**해야 주문 진입점이 활성화된다.
4. **주문/결제** — 저장된 배송지(시드가 넣은 「집」/「회사」)를 고른다.
5. **결제** — 데모는 `demo-pg` 프로파일이라 **결제 위젯 없이 즉시 승인**된다.
6. **주문 완료** → **마이페이지 → 주문내역**에 확정된 주문이 보인다.

### 이 경로가 증명하는 것

**결제만 가짜고 그 뒤는 전부 진짜다.** `demo-pg` 는 PG 승인만 대체하고
`PaymentEventPublisher` 는 실물 그대로다(TASK-BE-572). 그래서 한 번의 구매가
아래를 연쇄로 만든다 — 화면이 아니라 **DB 로** 확인할 수 있다:

```
payments        COMPLETED               ← 결제
payment_outbox  PaymentCompleted, published_at 채워짐   ← 트랜잭셔널 아웃박스
orders          CONFIRMED               ← 주문 사가
shippings       PREPARING               ← 배송 생성
commission_accrual  1행 (ACCRUAL)       ← 정산 적립
```

기존 `standalone` 프로파일이었다면 **첫 줄만 남고 나머지는 전부 비어 있다** —
화면은 "결제됨" 인데 시스템의 나머지가 조용히 동의하지 않는 상태다.

### 마이페이지 화면들

| 화면 | 증명하는 것 |
|---|---|
| 프로필 | IAM 신원이 도메인 프로필로 연결돼 있다 — **직접 가입해 확인해도 된다**: 처음 여는 그 요청이 프로필을 만든다(`TASK-BE-575`) |
| 주문내역 · 주문 상세 | 주문 사가의 결과 |
| 위시리스트 | 사용자 소유 데이터의 CRUD |
| 배송지 관리 | 체크아웃이 참조하는 마스터 데이터 |
| 쿠폰 | 프로모션에서 **발급된** 쿠폰(쿠폰을 직접 만들지 않는다) |
| 내 리뷰 | **배송 완료된 주문에만** 쓸 수 있다(구매자 리뷰 규칙) |
| 알림 · 알림 설정 | 알림 구독/수신 설정 |

---

## 3. 팬 플랫폼

`http://web.fan-platform.local` → 로그인(같은 계정).

아티스트 디렉터리 · 아티스트 상세 · 게시물 · 멤버십 · 알림 · 내 정보.
`PUBLIC` / `MEMBERS_ONLY` / `PREMIUM` 세 가시성이 **멤버십 등급에 따라** 다르게
보이는 것이 이 표면의 핵심이다(권한이 UI 가 아니라 서비스에서 강제된다).

데모 계정은 **`MEMBERS_ONLY` 를 보유**한다. 일부러 `PREMIUM` 이 아니다 —
프리미엄이면 세 가시성이 전부 열려 게이팅을 보여줄 수 없다.

| 화면 | 무엇을 보나 |
|---|---|
| 홈(피드) | 팔로우한 아티스트의 글. **`PREMIUM` 글은 제목·본문이 비고 잠금 표시**가 뜬다 |
| 아티스트 디렉터리 | 솔로 2 · 그룹 멤버 1 (`PUBLISHED` 만 노출된다) |
| 아티스트 상세 | 프로필 + 팔로우 버튼 |
| 게시물 상세 | `PUBLIC` 열림 / `MEMBERS_ONLY` **멤버십으로 열림** / `PREMIUM` "멤버십이 필요합니다" |
| 멤버십 · 이력 | 현재 구독 + 가입·해지 이력 |
| 알림 | 멤버십 시작·해지 이벤트로 채워진다(직접 넣지 않는다) |
| 내 정보 | 토큰 클레임(`tenant_id=fan-platform`, `roles=[FAN]`) |

> 🔴 **여기서 눌리지 않는 것 두 가지** — 시드의 한계가 아니라 제품의 공백이다.
> 멤버십 **구독 버튼**은 PortOne 키가 없으면 "결제 모듈이 설정되지 않았습니다" 로
> 막힌다(화면 문구는 "모의 PG" 라고 약속하지만 그 스위치가 없다 — `TASK-FAN-FE-015`).
> **팬 게시물 작성 화면은 아예 없다**(API 는 되고 시드가 그 경로로 글을 하나 넣어
> 둔다 — `TASK-FAN-FE-016`).

---

## 4. 콘솔 — 운영자 시점

`http://console.local` → 로그인 → **테넌트 스위처에서 테넌트를 고른다.**

> 🔴 테넌트를 고르기 전에는 도메인 운영 섹션이 열리지 않는다. 운영자 역할이
> 테넌트 assume 에서 파생되기 때문이다(§0).

### 어느 테넌트를 고를 것인가 — 둘은 서로 다른 것을 준다

| 고르면 | 얻는 것 | 보이는 것 |
|---|---|---|
| **`demo-corp`** | **권한** — 5개 도메인 구독에서 파생된 `*_OPERATOR` 역할 전부 | 도메인 운영 5개 섹션이 모두 열린다 |
| **`ecommerce`** | **가시성** — 스토어프런트가 쓰는 행이 실제로 사는 테넌트 | E-Commerce 10개 탭이 실데이터로 찬다(+ WMS 섹션) |

**스토어프런트에서 방금 산 주문을 콘솔에서 보려면 `ecommerce` 를 고른다.** 그것이 그
데이터가 사는 곳이기 때문이다 — 카탈로그부터가 `tenant_id='ecommerce'` 이고, 게이트웨이가
소비자 토큰의 테넌트를 그 값으로 못 박는다. `demo-corp` 는 "이 회사가 다섯 도메인을
쓴다" 는 **권한의 폭**을 보여주는 자리고, `ecommerce` 는 "그중 이 도메인을 실제로
운영한다" 는 자리다.

> 🔵 이 두 칸짜리 스위처 자체가 콘솔의 멀티테넌트 운영을 보여주는 화면이다
> (`acme-corp` ↔ `globex-corp` 쌍도 같은 기능의 다른 예다).

### 화면 모집단

`console-nav-config.ts` 의 nav 리프는 **47개**이고, nav 에 없지만 개요에서
도달 가능한 `/dashboards/health` 를 더하면 **48개**다.

| 그룹 | 리프 수 | 내용 |
|---|---|---|
| (최상단) | 2 | 개요 · 카탈로그 |
| 관리 → IAM | 9 | 개요 · 가이드 · 운영자 · 운영자 그룹 · 조직 계층 · 테넌트 · 권한 · 권한 세트 · 감사 |
| 고객 신원 | 1 | 계정 운영 |
| 조직 설정 | 2 | 도메인 구독 · 파트너십 |
| 도메인 운영 → WMS | 7 | 개요 · 가이드 · 입고 · 재고 · 출고 · 마스터 · 운영설정 |
| 도메인 운영 → SCM | 6 | 개요 · 가이드 · 조달 · 재고 · 보충 계획 · 보충 계획 설정 |
| 도메인 운영 → Finance | 4 | 개요 · 가이드 · 계좌 · 원장 |
| 도메인 운영 → ERP | 6 | 개요 · 가이드 · 마스터 · 통합 조회 · 결재함 · 위임 |
| 도메인 운영 → E-Commerce | 10 | 개요 · 가이드 · 상품 · 주문 · 배송 · 프로모션 · 사용자 · 셀러 · 정산 · 알림 |

각 도메인 섹션은 **그 도메인 스택이 떠 있을 때만** 데이터를 보여준다. `demo-up.sh`
에 그 도메인을 주지 않았다면 해당 섹션은 "일시적으로 불러올 수 없습니다" 로
degrade 한다 — 이것은 결함이 아니라 부분 기동의 정상 동작이다.

### 🔵 왜 테넌트를 골라야 하는가 — 한때 여기가 비어 있었다 (TASK-BE-576)

이 절은 원래 **"지금 열리지 않는 것"** 이었다. 스토어프런트에서 구매를 완주해도 콘솔의
「주문」 탭은 비어 있었고, 「상품」 「사용자」 「배송」 「정산」 도 마찬가지였다.

원인은 **데이터의 테넌트와 운영자의 테넌트가 달랐다**는 것이다. 스토어프런트가 쓰는 행은
전부 `tenant_id=ecommerce` 인데(게이트웨이가 소비자 토큰에 그 값을 강제한다), 데모
운영자는 `demo-corp` **하나에만** assign 돼 있었다.

그런데 게이트웨이는 그 토큰을 **정상적으로 받아들였다** — `demo-corp` 가 다섯 도메인을
구독하므로 entitlement 검사를 통과한다. 그래서 엣지는 초록, 헬스 카드도 초록, 목록만 비어
있었다:

```
GET /api/admin/orders   200  totalElements 0      (DB 에는 4건)
```

**읽기만의 문제도 아니었다.** 구매자 토큰으로 방금 조회한 배송 건을 운영자는 찾지 못했고
(`PUT /api/shippings/{id}/status` → 404 `SHIPPING_NOT_FOUND`), 그래서 배송을 완료시킬 수
없었고, 배송 완료가 전제인 **리뷰도 쓸 수 없었다.**

**수정**: 데모 운영자에게 `ecommerce` 테넌트 assignment 를 하나 추가했다(dev 전용 시드
`R__seed_demo_operator.sql`). 코드 변경은 없다 — 스위처는 이 assignment 에서 목록을
만들기 때문에 저절로 두 칸이 됐다.

**왜 반대로 스토어프런트를 `demo-corp` 로 옮기지 않았나**(그게 더 단순해 보인다):
**카탈로그 자체가 `ecommerce` 에 살기 때문이다.** `product-service` 의 V8 시드는 tenant
컬럼을 적지 않아 기본값을 타고, 그 값이 `ecommerce` 다(상품 8/8 · 카테고리 7/7 실측).
`demo-corp` 로 뜨는 스토어프런트는 **텅 빈 상점**이 된다. 데모를 위해 프로덕션
마이그레이션을 재-테넌트하는 것은 선택지가 아니다.

> 🔵 남는 교훈: **"게이트웨이가 토큰을 받았다" 와 "그 토큰이 데이터를 본다" 는 다른
> 명제다.** 후자는 목록의 **원소 수**로만 확인된다 — 200 으로도, degraded 마커 부재로도,
> 헬스 카드로도 확인되지 않는다.

---

## 5. 시드는 무엇을 하는가

`infra/demo/seed/` — 자세한 설계는 그 디렉터리의 `README.md`.

원칙 한 줄: **넣을 수 있는 것은 실제 API 로 넣는다. 넣는 행위 자체가 그 기능의
검증이기 때문이다.** 직접 DB 는 `dbexec --why "<사유>"` 로만 가능하며, 사유가 함수의
**필수 인자**라 빠뜨릴 수 없다.

현재 직접-DB 항목은 **하나뿐**이다: ecommerce 소비자 프로필의 **이름·이메일**.

`TASK-BE-575` 이후 프로필 자체는 더 이상 시드가 만들지 않는다 — 게이트웨이가 검증한
신원으로 **첫 요청이 스스로 만든다**. 그래도 이 블록이 남는 이유는 그 프로필이
**이름과 이메일을 가질 방법이 없기 때문**이다: `account.created` 페이로드는 emailHash
뿐이고, 액세스 토큰에는 `email` 클레임이 없어 게이트웨이의 `X-User-Email` 이 아예 나가지
않으며, `PATCH /api/users/me` 는 nickname/phone/imageUrl 만 받는다. 그래서 데모 화면의
"데모 구매자 / demo@demo.com" 은 이 한 줄이 넣는다. → `TASK-BE-577` 이 닫히면 이 블록도
사라진다.

시드는 두 신원으로 일한다. 소비자 토큰(내 배송지·위시리스트·리뷰)과 운영자 토큰
(셀러·수수료율·정산기간·알림템플릿·프로모션·배송 진행)이다. 🔴 **운영자 토큰은
`ecommerce` 를 assume 한다** — `demo-corp` 로 넣으면 콘솔이 반쪽이 된다(셀러·프로모션은
보이는데 그 옆의 주문·배송은 다른 테넌트라 비어 있다). §4 가 그 이유다.

그리고 시드는 **도메인 규칙을 우회하지 않는다.** 리뷰는 배송 완료된 주문에만 쓸 수
있으므로, 시드는 리뷰 행을 직접 넣는 대신 **배송을 실제로 진행시켜**(PREPARING →
SHIPPED → IN_TRANSIT → DELIVERED) 자격을 만든다. 그 과정에서 콘솔 「배송」 탭도 함께 찬다.

---

## 6. 알려진 한계 (조용한 누락 없이)

| 항목 | 상태 | 추적 |
|---|---|---|
| 새로 가입한 계정의 프로필에 **이름·이메일이 비어 있다** | 최소 프로필 설계(ADR-MONO-037 P5/P6)인데 채울 값의 출처가 없다 — 토큰에 `email` 클레임이 없어 `X-User-Email` 이 나가지 않는다 | `TASK-BE-577` |
| 스토어 "회원가입" 이 **IAM 로그인 화면**에 내려놓는다 — 가입 폼까지 한 클릭 더 | 저장된 `/oauth2/authorize` 요청이 tenant 를 정하므로 IAM `/signup` 직링크는 계정을 `fan-platform` 에 만든다. 그 한 클릭을 없애려면 IAM 이 registration hint 를 받아야 한다 | `TASK-BE-578` |
| IAM 계정 이벤트가 도메인 서비스에 **도달하지 않는다**(클러스터가 다름) | 데모 동작에는 영향 없음 — 프로필은 요청 시점에 만들어진다. 그러나 `account.deleted` 익명화도 같이 멈춰 있다 | `TASK-MONO-511` |
| 팬 **멤버십 구독 버튼**이 눌리지 않는다 | `fan-platform-web` 에 데모 모의 PG 스위치가 없다. PortOne 키 미설정 → 클릭이 요청 전에 거절된다(백엔드 목 PG 는 정상). 게다가 페이지 문구는 "모의 PG 로 처리됩니다" 라고 약속한다 | `TASK-FAN-FE-015` |
| 팬 **게시물 작성 화면이 없다** | API 는 `FAN_POST` 를 받는다(201). 프런트에 작성 진입점이 0개라 시드가 넣어 둔 글만 보인다 | `TASK-FAN-FE-016` |
| 팬 **아티스트 글은 어떤 실제 호출자도 쓸 수 없다** | 피드는 `posts.author_account_id ⋈ follows.artist_account_id`(= 아티스트 엔티티 id)로 잇는데 `PublishPostUseCase` 는 저자를 호출자 sub 으로 고정한다. 시드가 직접-DB 를 쓰는 이유 | `TASK-FAN-BE-045` |
| 팬 도메인에 **발급 가능한 운영자 역할이 없다** | `FAN_OPERATOR` 를 받는 코드는 iam·artist·community 전부에 있는데, `fan` 도메인을 구독한 테넌트가 없어 그 역할이 발급될 수 없다 | `TASK-MONO-512` |
| 멤버십을 해지해도 **피드는 최대 5분간 열린 채**다 | 피드 캐시가 렌더된 `locked`/제목/본문 미리보기를 담고 TTL(5분)로만 만료된다. 상세 경로는 즉시 403 이다 | `TASK-FAN-BE-046` |
| **WMS 도메인 데이터 시드 있음** — 입고 흐름 한 벌 + 출고 주문 1건 | `seed-wms.sh` 가 ASN → 검수 → 적치 → **재고 반영**(`available_qty=95`, `system:putaway-consumer`) → 출고 주문(`PICKING`)을 **실제 API 로** 심는다. 2회 실행 수렴 확인(2회차 = 생성 0 · 기존 2 · 실패 0) | `TASK-MONO-510` |
| **ERP 도메인 데이터 시드 있음** — 마스터 5종 + 결재 3건 + 위임 1건 | `seed-erp.sh` 가 직급 3 · 부서 3(트리) · 코스트센터 3 · 사원 4 · 거래처 3 · 결재 3 · 위임 1 = **20건을 전부 실제 API 로** 심는다(직접-DB **0건**). 2회 실행 수렴 확인(1회차 생성 20 / 2회차 생성 0 · 기존 20 · 실패 0) | `TASK-MONO-510` |
| **SCM 도메인 데이터 시드 있음** — config 4건 + 발주 3건 | `seed-scm.sh` 가 재주문 정책 2 · SKU-공급사 매핑 2 · PO 3 을 **실제 API 로** 심는다. 공급사 1건만 직접-DB 이고 그 사유가 코드에 있다(아래 공급사 API 부재). 3회 실행 후 `purchase_orders` **3행** 수렴 | `TASK-MONO-510` |
| **Finance 도메인 데이터 시드 있음** — 계좌 2건(ACTIVE/FULL) | `seed-finance.sh` 가 계좌 개설 2 + KYC 승급 2 를 **전부 실제 API 로** 심는다(직접-DB **0건**). 🔵 원장은 **일부러 손으로 넣지 않았다** — 이체가 성립했다면 `/ledger` 는 *투영이 동작한다* 는 증거였겠지만, 손으로 넣으면 *시드가 넣었다* 는 증거밖에 안 된다 | `TASK-MONO-510` |
| 🔴 SCM **공급사를 등록할 API 가 없다** | `POST /po` 가 `SUPPLIER_NOT_FOUND` 인데 저장소 전 컨트롤러에 suppliers 생성 매핑이 **0건**이다(그 도메인의 e2e 도 DB 픽스처로 직접 넣는다). 공급사 관리 화면 자체가 없다 ⇒ 시드의 유일한 경로가 직접-DB 다 | `TASK-SCM-BE-059` |
| ✅ SCM **발주가 전 상태를 완주한다** (2026-08-07 고침) | 전에는 `DRAFT` 가 종착이었다 — `submit` 이 `http://supplier-mock:9090` 을 **실제로** 호출하는데 그 서비스가 **어느 compose 에도 없었다**. 이음매는 처음부터 외부화돼 있었고 **아무도 채우지 않은 배선 누락**이었다. 🔴 그 짝은 submit 에 200 을 내는 것만으로 부족하다 — `PoStatusMachine` 이 `SUBMITTED → ACKNOWLEDGED` 를 **SUPPLIER 전이**로 두고 `CONFIRMED` 를 `ACKNOWLEDGED` 에서만 허용하므로 **HMAC 서명된 ack 웹훅을 되돌려 불러야** 완주한다. 라이브(깨끗한 볼륨): `purchase_orders` 가 `DRAFT`·`ACKNOWLEDGED`·`CONFIRMED` 각 1건, `po_status_history` 에 `SUBMITTED→ACKNOWLEDGED` 가 **`actor_type=SUPPLIER`** 로 2건 | `TASK-SCM-BE-060` |
| 🟡 SCM ack 웹훅이 **제출이 알려주지 않는 `tenantId` 를 요구한다** | `SupplierAckWebhookRequest` 는 `tenantId` 필수인데 제출 페이로드는 poId/poNumber/supplierId/currency/totalAmount/lines 만 보낸다 — **실제 공급사라도 알 길이 없는** 구매자 쪽 파티션 키다. 그래서 mock 에 `ACK_TENANT_ID` 로 대역 밖 주입한다(데모=`demo-corp`). 🔴 데모 테넌트를 바꾸면 **이 값도 같이** 바꿔야 ack 가 남의 테넌트에서 PO 를 찾다 실패한다 | `TASK-SCM-BE-060` ④ |
| ✅ Finance **계좌에 돈이 들어간다** (2026-08-07 고침) | 이 행은 원래 *"돈을 넣을 수 없다"* 였다 — 입금 매핑 **0건**, `topUp()` 은 있으나 프로덕션 호출자 **0건**(테스트 6회만). 운영자 전용 입금 경로를 열었고 실측: 입금 `0→500000` ×2 → 이체 A `500000→400000` / B `500000→600000` → 시산표 **3계정, 차변합=대변합=1,100,000**. 🔴 **그 다음이 진짜 교훈이다** — 시산표가 60초간 0 이었는데 브로커를 보니 발행 3 · 전기 3 · DLT 0 으로 **지연도 발행부재도 아니었다**. 봉투에 `tenantId` 가 없어 원장이 리터럴 `finance` 로 폴백했고, `demo-corp` 계좌의 전기가 **주인이 못 읽는 테넌트**로 적히고 있었다. 막힌 것을 뚫자 그 아래가 드러난 형태 | `TASK-FIN-BE-068` |
| 🔵 콘솔 **SCM 6화면 중 4개가 찬다** | `/scm` · `/scm/guide` · `/scm/procurement`(**3**) · `/scm/config`(정책 1 + 매핑 1). `/scm/inventory` 는 창고 재고 이벤트가 있어야 차고(이 슬라이스 밖), `/scm/replenishment` 는 제안 생성에 재고 신호가 더 필요하다 | — |
| 🟡 콘솔 **`/finance/accounts` 에 목록 라우트가 없다** (유지 — 근거 기록됨) | 단건 조회뿐이라 운영자가 계좌 id 를 **직접 입력**해야 한다. 🔵 만들지 **않기로** 하고 숫자를 남겼다: 영향 화면 1 · 데모 계좌 2(시드가 id 출력) · 목록을 받쳐줄 repository 메서드 **0**. 진짜 장벽은 배선이 아니라 소유자 참조가 **암호화 저장**이라 "고객으로 찾기" 가 blind index 설계 결정을 요구한다는 것 | `TASK-FIN-BE-068` |
| 🟡 콘솔 `/ledger` **4피드 중 1개가 찬다 — 나머지 셋은 정상적으로 비어 있다** | 실측(2026-08-07, **BFF 원소 수**로 판정, **웜 상태**): 시산표 **3원소** ✅ · 기간 **빈 배열** · 불일치 **빈 배열** · 환율 **`{"feedEnabled":false,"rates":[]}`**. 환율 피드는 `enabled=false`+`mode=noop` 이 **문서화된 기본값**(외부 호출 0건)이고 화면도 *"피드 비활성 — 환율 폴백이 꺼져 있습니다"* 배지로 그렇게 말한다 ⇒ **결함 아님**. 🔴🔴 **선행조건이 둘이고, 하나만 알면 나머지에 속는다**: ① 갓 로그인한 세션은 테넌트가 `iam` 이라 **4피드 전부 403** ② **콜드스타트 1회차는 3.1초**라 BFF 레그가 끊어 **503** 을 낸다(웜 520~696ms). 둘 다 "빈 화면" 처럼 읽히지만 셋 다 다른 사건이다 | `TASK-PC-FE-273` (조사 종결) |
| 🟡 콘솔에서 **쓰기가 되는 도메인은 4개 중 2개** | 실측(2026-08-07): **ERP** 부서 생성(201, 목록 3→4)과 **SCM** 재주문 정책 upsert(200, `version 7→8`)는 브라우저에서 성립한다. **WMS** 는 세 겹으로 막히고(목록 0건 → pick 403 → 알림 0건), **Finance** 는 콘솔에 동작하는 쓰기 라우트가 없다. 🔴 환율 refresh 는 **200 을 내지만 no-op** 이다(`{"feedEnabled":false,"refreshed":0}`) — 상태코드만 보면 성공으로 오독한다. 🔵 **주의: 이 "2개" 는 *콘솔 화면에서* 라는 뜻이다.** Finance 는 같은 날 API 로는 입금·이체가 성립하게 됐지만(위 ✅ 행) 콘솔에 그 쓰기 화면이 없어 여기 숫자는 그대로다 — 도메인 능력과 콘솔 표면은 **다른 축**이고, 둘을 한 숫자로 합치면 어느 쪽이 막혔는지 알 수 없게 된다 | `TASK-MONO-510` |
| 🔴 창고 스택이 **재시작 루프**에 빠진다 (`Up 3초` 반복) | 그 프로젝트의 postgres 볼륨이 **프로젝트 `.env` 가 안 실린 상태로 초기화**되면 롤 비밀번호가 compose 기본값(`inbound`, `master`, …)으로 굳는데, 앱은 `.env` 의 `*-changeme-local` 로 접속한다. postgres init 은 **빈 데이터 디렉터리에서만** 돌므로 재기동으로는 절대 고쳐지지 않는다. 🔴 증상이 설정 오류가 아니라 **앱 크래시**로 나타난다 — 컨테이너 로그에 `FATAL: password authentication failed`. 비파괴 복구: `docker exec <pg 컨테이너> psql -U postgres -c "ALTER ROLE <롤> WITH PASSWORD '<.env 의 값>'"` (해당 롤 전부; 데이터 보존) | — |
| 🔵 콘솔 **ERP 6화면 중 5개가 찬다** | `/erp` · `/erp/guide` · `/erp/masters`(5탭, 라이브 원소 수 3·4·3·3·3) · `/erp/approval` 목록(3) · `/erp/delegation`(1) · **`/erp/orgview`(4)**. 앞 셋은 프로듀서 DB 직독이고, `/erp/orgview` 는 `TASK-ERP-BE-042` 로 프로젝션이 살아난 뒤 **0 → 4** 가 됐다 | — |
| 🔴 ERP **결재 상신이 항상 실패한다** — 결재는 `DRAFT` 로만 쌓인다 | 실재하는 `ACTIVE` 부서를 주체로 상신해도 매번 `422 subject_unresolved` 다. `MasterDataRestAdapter` 가 masterdata-service 를 **인증 헤더 없이** 부르고(컨테이너 안 실측 `401`), `onStatus(4xx)` 가 그 401 을 삼켜 **"마스터가 ACTIVE 아님"** 으로 번역한다 | `TASK-ERP-BE-041` |
| ✅ 콘솔 **`/erp/orgview` 가 찬다** (2026-08-07 고침) | erp 아웃박스 릴레이가 **한 번도 돌지 않았다** — `@Scheduled` 는 있는데 `@EnableScheduling` 이 없어 Spring 이 **조용히** 등록하지 않았다(erp 5앱 중 아웃박스가 **없는** notification 하나만 보유; wms 6/6·scm·finance 는 전부 보유 ⇒ 형제 파리티 낙오). 아웃박스 UNPUBLISHED 16+1, **kafka end-offset 전 토픽 0** 이 판정 근거였다(“프로젝션이 0” 은 지연과 발행 부재 양쪽에 부합하므로 **브로커에서** 봐야 갈린다). 고친 뒤 백로그가 **전량 발행**되고 `/api/erp/read-model/employees` 원소수 **0 → 4** | `TASK-ERP-BE-042` |
| 🔴 콘솔 **`/erp/delegation` 의 read-model 위임 뷰만 여전히 빈다** | 릴레이가 살아나자 **그 아래의 결함**이 드러났다: approval 봉투에 최상위 `aggregateId`·`tenantId` 가 없는데(masterdata 봉투는 둘 다 싣는다) 소비자가 그것을 **필수**로 요구해 첫 메시지가 곧바로 **DLT** 로 갔다. `erp.approval.*` **여섯 토픽이 같은 운명**이고, 지금 하나만 보이는 것은 나머지를 낳는 상신이 `TASK-ERP-BE-041` 로 막혀 있기 때문이다. 원본 위임 목록(`/api/erp/approval/delegations`)은 정상(1건) | `TASK-ERP-BE-043` |
| 🔴 콘솔 **ERP 결재함이 어떤 사용자에게도 비어 있다** | 결재함은 "호출자가 현재 단계 승인자인 건" 만 준다. ① 자기결재가 금지되고 ② assume 토큰의 `sub` 가 계정이 아니라 **`platform-console-web`**(acting 클라이언트 — iam IT 가 그렇게 단언한다)이며 ③ token-exchange grant 를 가진 클라이언트가 **하나뿐**이다 ⇒ `demo-corp` 안의 actorId 가 정확히 하나라 제출자≠승인자가 성립할 수 없다. 승인/반려 버튼도 도달 불가 | `TASK-MONO-515` |
| ✅ 콘솔 **`/wms/inbound` 목록이 찬다** (2026-08-06 고침) | `admin-service` 가 **아무도 발행하지 않는 토픽**(`wms.inbound.asn.v1`, 실제 발행은 `...asn.received.v1`)을 구독해 입고 프로젝션이 0행이던 것을 계약(`inbound-events.md § Topic Layout`)대로 **분리 토픽 전부 구독**으로 고쳤다. 백필은 불필요 — `auto-offset-reset: earliest` 라 재기동만으로 재생된다. 라이브: `/api/wms/inbound/asns` 원소수 **0 → 2** | `TASK-BE-582` |
| 🔴 콘솔 **`/wms/outbound` 목록은 여전히 빈다** | 이 화면의 상류는 admin 프로젝션이 **아니라** outbound-service 원시 API 다(런타임 로그로 확인). 데모 운영자(`tenant_id=demo-corp`)는 **restricted** 로 판정돼 `tenant_id` **AND `source=FULFILLMENT_ECOMMERCE`** 인 주문만 보는데, 시드가 만든 주문은 `tenant_id=NULL · source=MANUAL` 이다 ⇒ **만든 주체가 만든 것을 못 본다**. 프로젝션을 고친 뒤에도 원소수 0 으로 재확인 | `TASK-BE-581` |
| 🔵 콘솔 **`/wms/inventory` 는 실제로 찬다** | 시드의 적치 확정 → Kafka → `admin_inventory_snapshot` 투영까지 도달한다(라이브 확인) | — |
| `/wms/operations` 가 **403** | 운영자 토큰에 `projection-status` 권한이 없다 | `TASK-MONO-514` |
| WMS **출고는 주문까지**만 심는다 | 예약은 `INVENTORY_RESERVE` 를 요구하는데 운영자에게 파생되지 않고, 배송은 도달 불가 TMS 스텁에 의존한다 | `TASK-MONO-514` |
| WMS **마스터 쓰기는 API 로 불가** | `master-service` 는 `MASTER_WRITE`/`MASTER_ADMIN` 을 요구하는데 `OperatorRoleDerivation` 은 `MASTER_READ` 까지만 준다(형제 3개에는 READ+WRITE 를 주는 비대칭). 데모 마스터 데이터는 Flyway 시드로 들어간다 | `TASK-MONO-514` |
| ~~`inventory-service` · `outbound-service` 가 **HTTP 를 아예 안 받는 상태로 갇힌다**~~ — **원인 규명 + 수정** | 로그 한 줄이 스케줄러를 잠그고 있었다. `spring.threads.virtual.enabled: true` 라 Kafka 리스너가 가상 스레드에서 도는데, Kafka 클라이언트가 **자기 `synchronized` 안에서 로그를 찍는다** ⇒ JDK 21 에서 그 스레드는 언마운트 못 하고(핀), 동기 `ConsoleAppender` 락을 기다리며 **캐리어째 park** 한다. 스케줄러 parallelism = CPU 수 = **4** 이므로 네 개면 전부 소진되고, 락을 쥔 스레드마저 가상 스레드라 다시 마운트될 수 없어 **영구 교착**이다. 수정: 모든 root appender 를 `AsyncAppender(neverBlock=true)` 로 감쌌다 | `TASK-BE-579` |
| ~~`outbound-service` 의 마스터 read-model 이 영구히 0행~~ — **해소됨** | 시드 파일은 **있었는데 `db/dev/` 에 있어 한 번도 실행된 적이 없었다**(형제 셋은 `db/seed/` 를 쓰고, 저장소의 어떤 `spring.flyway.locations` 도 `db/dev` 를 부르지 않는다). `db/seed/` 로 옮기고 빠져 있던 `application-dev.yml` 을 더해 살렸다 ⇒ 이제 Flyway 가 채우고 시드의 `dbexec` 는 사라졌다 | `TASK-BE-580` (review) |
| 🔴 `docker ps` 의 `healthy` 를 믿지 마라 | 갇힌 outbound 를 실측하니 **19분 내내 `healthy`** 였다(`failingStreak=11`, `retries: 12` 미달). 게다가 healthcheck 의 `curl` 에 `-m` 이 없어 **컨테이너 안에 curl 이 11개 쌓여** 있었다 — 프로브가 장애의 일부였다. `-m 5` 를 붙였다. **판정은 직접 HTTP 로**: `docker exec -i wms-gateway-service sh -c 'wget -T 5 -qO- http://outbound-service:8084/actuator/health/liveness'` | `TASK-BE-579` |
| 🔵 데모 스택 재배포 시 **반드시 `demo.env` 를 source** | `set -a; source infra/demo/demo.env; set +a` 없이 compose 를 돌리면 서비스가 `OIDC_ISSUER_URL` 을 compose 기본값(`iam-gateway-service:8080`)으로 잡는데 토큰의 `iss` 는 `http://iam.local` 이라 **그 서비스의 전 경로가 401** 이 된다 | — |
| 장바구니는 시드할 수 없다 | `localStorage` 기반(클라이언트 상태) — 면접관이 담으면 즉시 찬다 | — |
| 상품 이미지가 MinIO 에 없다 | V8 시드의 원격 `thumbnailUrl` 로 표시된다(깨지지 않음) | — |
| 로컬 호스트에서 8프로젝트 동시 기동 불가 | 실측: `iam+console+ecommerce` 35컨테이너 = 9.2 GiB, `iam+fan+console` 26컨테이너 = **7.7 GiB** (팬 슬라이스 단독 9컨 = 2.4 GiB) / 도커 가용 11.7 GiB ⇒ 스토어와 팬은 **번갈아** 띄운다 | `TASK-MONO-399` AC-2 |

---

## 7. 문제가 생기면

| 증상 | 먼저 볼 것 |
|---|---|
| 로그인 후 되돌아오지 못한다 | `infra/demo/seed-demo-domain.sh` 로그 — redirect_uri 가 데모 도메인에 등록됐는지 |
| 콘솔 도메인 섹션이 전부 degrade | 그 도메인 스택이 떠 있는지 (`docker ps`), `demo.env` 의 `CONSOLE_BFF_OUTBOUND_*` |
| 백엔드가 401 "Authentication required" | 그 서비스가 `traefik-net` 에 붙어 JWKS 를 해소하는지 (`infra/demo/*-identity.override.yml`) |
| 화면은 200 인데 목록이 비었다 | **테넌트를 의심하라** — §4 의 사례가 정확히 그 모양이다 |
| 결제가 실패한다 | `ECOMMERCE_PAYMENT_PROFILES=demo-pg` 와 `DEMO_PAYMENT_MOCK=1` 이 **함께** 켜졌는지 |

전 항목 정적 검증: `bash infra/demo/verify-demo-wrapper.sh`
