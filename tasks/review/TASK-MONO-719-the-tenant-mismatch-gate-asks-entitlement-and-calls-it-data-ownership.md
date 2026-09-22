# Task ID

TASK-MONO-719

# Title

🔴 테넌트 안내 게이트가 **자격을 묻고 그것을 데이터 소유라고 부른다** — 라이브 레지스트리에서 영영 안 뜬다 (`TASK-MONO-718` ⓑ 의 실측 FAIL)

# Status

review (2026-09-22 UTC — 소유자 결정 **ⓑ** 구현 · 🔴 AC-3(창 판정)은 `TASK-MONO-672` 항목 11)

# Owner

미지정

# Task Tags

- platform-console
- tenancy
- demo
- verification

---

> **분석 모델:** Opus 5 / **구현 권장:** Opus 5 — 고칠 곳은 한 함수지만 **무엇을 물어야 하는가**가
> 이 티켓의 전부다(자격 ≠ 데이터 소유). 갈래 셋 중 둘은 소유자 결정이고 하나는 ADR 경계에 닿는다.

---

# 배경 — 이것은 추론이 아니라 **데모 창 실측**이다 (2026-09-22, 15차 창)

`TASK-MONO-718` 소유자 결정 ⓑ 는 *"안 맞는 테넌트로 들어온 운영자에게 콘솔이 안내를 보여 준다"* 를
`DomainTenantGate` 에 넣었고, 단위 칸 10개가 초록으로 머지됐다(PR #3949 → `498f260d4`).
그 판정을 `TASK-MONO-672` 항목 8 이 창으로 받았고, **창에서 FAIL 했다.**

```
콘솔 로그인 → 테넌트 demo-corp «적용» 확인 → GET /ecommerce/orders
  data-testid="domain-tenant-mismatch"  →  없음
  data-testid="order-empty"             →  있음  («표시할 주문이 없습니다»)
  표 행                                  →  0
대조군: 테넌트 ecommerce 로 전환 → 같은 URL → 표 행 5 · 안내 없음  (게이트 자체는 산다)
```

## 🔴🔴 원인 — 게이트의 **증거원**을 그대로 읽었다

`tenantMismatch(productKey)` 는 `getCatalog().products[…].tenants` 에 활성 테넌트가 **없을 때만**
안내를 띄운다. 라이브 레지스트리를 콘솔 자신의 `/console` 타일에서 전수로 꺼냈다:

| product | tenants (라이브) |
|---|---|
| iam | `demo-corp`, `ecommerce` |
| wms | `demo-corp`, `ecommerce` |
| scm | `demo-corp` |
| erp | `demo-corp` |
| finance | `demo-corp` |
| **ecommerce** | **`demo-corp`, `ecommerce`** |

⇒ `product.tenants.includes('demo-corp') === true` ⇒ `tenantMismatch` 가 `null` 을 돌려주고
**안내는 구조적으로 뜰 수 없다.**

🔵 **그리고 그것은 레지스트리가 틀린 것이 아니다.** `demo-corp` 는 ecommerce 제품에 **자격이 있다** —
운영자 역할을 드는 테넌트가 그쪽이다(`TASK-BE-576`). 행이 `tenant_id=ecommerce` 에 사는 것은 **별개의
사실**이고, 레지스트리는 그 질문에 답하도록 만들어진 표가 아니다.

⇒ **게이트는 «자격이 있는가» 를 물어 놓고 그 답을 «데이터를 소유하는가» 의 답으로 썼다.**
두 명제는 이 시스템에서 실제로 갈라지고, 갈라지는 바로 그 지점이 718 이 닫으려던 실패다.

## 🔴 단위 테스트가 왜 못 막았나 — **픽스처가 불가능한 입력이었다**

`domain-tenant-gate.test.tsx` 의 718 블록은 `products: [{ productKey:'ecommerce', tenants:['ecommerce'] }]`
를 쓴다. 라이브가 내는 값은 `['demo-corp','ecommerce']` 다 ⇒ **이 시스템이 만들지 않는 입력**으로
10칸이 초록이었다. 저장소가 이미 이름 붙인 함정이다(«픽스처가 현실을 안 담으면 초록도 공허»).

🔴 그러므로 **칸을 더 넣는 것으로는 못 고친다** — 픽스처의 모양이 라이브를 따라가야 하고,
그 뒤엔 지금의 술어가 **정의상 통과**한다. 술어를 바꾸는 것이 이 티켓이다.

---

# Goal

운영자가 «화면은 열리는데 목록이 빈» 상태에 있을 때, 콘솔이 그 사실을 **말하게 한다** —
그리고 그 판정을 **레지스트리의 자격 표가 아닌 근거**로 내린다.

---

# Scope

## 포함

- `projects/platform-console/apps/console-web/src/widgets/domain-tenant-gate/DomainTenantGate.tsx`
  의 `tenantMismatch()` 술어.
- 그 술어를 재는 단위 칸(픽스처를 **라이브가 내는 모양**으로).
- 계약서 `console-integration-contract.md` 의 해당 절(718 이 쓴 문장은 이 실측으로 **틀렸다**).

## 제외

- 🔴 **레지스트리(`tenants` 목록)를 고치지 마라.** `demo-corp` 가 ecommerce 에 자격이 있다는 것은
  참이고 `TASK-BE-576` 이 그렇게 정했다. 거기서 `demo-corp` 를 빼면 **운영자 권한이 사라진다.**
- 🔴 «시드를 `demo-corp` 로 옮긴다» — `TASK-MONO-718` AC-1 에서 소유자가 **ⓑ 를 골랐고** ⓐ 는
  그때 이미 반증됐다(카탈로그 `tenant_id` 는 producer 측 기본값이라 못 옮긴다).
- 🔴 활성 테넌트를 자동으로 바꾸는 것 — 718 이 세 가지 권한 경계를 들어 거절했고 그 거절은 유효하다.

---

# Acceptance Criteria

## AC-0 — 착수 게이트: **술어의 근거부터 고른다** (소유자 결정) — ✅ **ⓑ 확정 (2026-09-22 UTC)**

> 🔵 **소유자 결정: ⓑ — 「빈 목록 + 다른 선택지 → 힌트」.** 추천대로 확정됐다.
> 🔴 아래 표와 추천은 **결정 당시의 입력**이므로 고쳐 쓰지 않고 그대로 둔다.

- [ ] 🔴 아래 갈래 중 **어느 근거로 판정할지**를 먼저 정한다. 코드를 먼저 쓰면 또 «있는 표» 로
      답하게 된다(이 티켓이 생긴 이유가 정확히 그것이다).

| 갈래 | 판정 근거 | 대가 |
|---|---|---|
| **ⓐ 도메인에 묻는다** | 선택 가능한 테넌트마다 그 도메인의 count 를 1건씩 읽어 «행을 가진 테넌트» 를 찾는다 | 테넌트당 토큰 교환 1회 + 왕복 N회. 화면 진입마다면 비싸다 ⇒ 캐시/타이밍이 설계 항목이 된다 |
| **ⓑ 빈 목록을 신호로 쓴다** | 목록이 0건 **이고** 다른 테넌트가 선택 가능하면 «다른 테넌트에 있을 수 있습니다» 를 **힌트**로 (단언 아님) | 진짜로 0건인 경우에도 뜬다 ⇒ 문구가 «없다» 가 아니라 «여기엔 없다» 여야 한다 |
| **ⓒ 레지스트리에 축을 하나 더 만든다** | 제품마다 «데이터가 사는 테넌트» 를 별도 필드로 싣는다 | 🔴 계약 변경 + 그 값을 **누가 참으로 유지하는가** 가 열린다 ⇒ **ADR** |

- [ ] 🔵 추천은 **ⓑ** 다 — 지금 콘솔이 이미 가진 정보만 쓰고(목록 길이 + 선택 가능한 테넌트),
      틀릴 때 **일이 늘어나는 쪽**으로 틀린다(«확인해 보라» 는 힌트는 오탐이어도 해롭지 않다).
      🔴 그러나 이것은 **내 추천이지 소유자 선택이 아니다.**

## AC-1 — 술어를 바꾸고, **라이브가 내는 모양**으로 잰다

- [ ] 픽스처의 `tenants` 를 `['demo-corp','ecommerce']` (실측값)로 바꾼다.
- [ ] 🔴 **그 픽스처에서 지금 코드가 빨개지는 것을 먼저 본다**(bite). 안 빨개지면 픽스처가 아직
      라이브를 안 담은 것이다.
- [ ] 대조군: 진짜로 데이터가 그 테넌트에 있는 경우 안내가 **안 뜬다**.

## AC-2 — 「못 확인함」은 여전히 **통과**여야 한다

- [ ] 레지스트리 degraded · 제품 부재 · 목록 조회 실패 → **화면을 막지 않는다**(718 이 세운 규칙이고
      유효하다). 이 티켓은 그 규칙을 바꾸지 않는다.

## AC-3 — 창 판정을 다시 받는다

- [ ] 🔴 단위 칸 초록으로 닫지 마라 — 718 이 그렇게 닫혔고 창에서 FAIL 했다.
      판정은 **`TASK-MONO-672`** 에 항목으로 넘긴다: «`demo-corp` 로 `/ecommerce/orders` 를 열면
      안내가 뜨는가 + `ecommerce` 로 전환하면 사라지고 5행이 차는가».

---

# Related Specs / Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9.1
- `projects/platform-console/apps/console-web/src/shared/lib/active-tenant-default.ts`
  (`selectableTenants()` — **같은 레지스트리를 읽는다**; 이 티켓의 술어가 그것과 갈라져야 하는 이유)
- `tasks/done/TASK-MONO-718-…md` § CORRECTION
- `tasks/done/TASK-BE-576-…` (행이 `ecommerce` 에 사는 이유 — 바꾸지 않는다)

---

# Edge Cases

- 선택 가능한 테넌트가 **하나뿐**인 배포 — 「다른 테넌트로 가라」가 말이 안 된다 ⇒ 안내를 띄우지 않는다.
- 샘플 방문자(ADR-MONO-074) — 토큰이 없고 샘플 라우터가 답한다 ⇒ 모집단 밖.
- 목록이 0건인데 **정말로** 아무 테넌트에도 없는 경우 — ⓑ 를 고르면 이 경우에도 힌트가 뜬다.
  그것이 허용 가능한지가 ⓑ 의 선택 조건이다.

---

# Failure Scenarios

1. **픽스처만 바꾸고 술어를 안 바꾼다** → 칸이 빨개진 채 남거나, 술어를 픽스처에 맞춰
   느슨하게 고쳐 «항상 안내» 가 된다. 🔴 대조군 칸이 이것을 문다.
2. **레지스트리에서 `demo-corp` 를 뺀다** → 안내는 뜨지만 **운영자 권한이 같이 사라진다.**
   증상은 「안내가 뜬다」가 아니라 「5개 도메인이 전부 죽는다」다.
3. **ⓐ 를 고르고 캐시를 안 둔다** → 도메인 화면 진입마다 테넌트 수만큼 토큰 교환 ⇒ 로그인 직후가 느려진다.

---

# 구현 기록 (2026-09-22 UTC · 소유자 결정 ⓑ)

## 🔴 ⓑ 는 `DomainTenantGate` 안에 살 수 없다 — 기안 당시 § 포함이 틀렸다

기안문의 § 포함은 *"`DomainTenantGate.tsx` 의 `tenantMismatch()` 술어"* 를 지목했다.
구현하려고 열어 보니 그 자리가 **구조적으로 불가능**하다:

- 게이트는 **섹션 레이아웃**에서 돈다 ⇒ `children` 보다 **먼저** 렌더된다.
- ⓑ 의 조건은 *"목록이 0건이고"* 인데, 목록이 0건인지는 **그 children 이** 안다.

⇒ 힌트는 **빈 목록이 그려지는 자리**에 살아야 한다. 게이트는 그 자리를 못 본다.

🔵 그래서 § 포함을 «게이트의 술어» 에서 «빈 목록 옆» 으로 **옮겨 읽었다**. 기안문은
고쳐 쓰지 않는다 — 기안 시점의 판단이 그랬다는 것이 기록이다.

## 🔵 새 컨텍스트를 만들지 않았다

빈 목록은 클라이언트가 알고(`OrdersScreen` 은 `'use client'`), 테넌트 집합은 서버가 안다.
React Context 로 이으려다 **이 앱에 `createContext` 가 한 곳도 없다**는 것을 확인하고
그만뒀다. 대신 **서버 페이지가 `getTenantScope()` 를 불러 prop 으로 내려보낸다** — 그
페이지는 이미 서버 컴포넌트이고 `getCatalog()` 는 그 요청에서 이미 불린다.

## 바뀐 것

| 파일 | 무엇 |
|---|---|
| `widgets/domain-tenant-gate/OtherTenantHint.tsx` | **신규.** 빈 목록 **옆**에 붙는 힌트. 활성 테넌트가 없거나 전환할 다른 테넌트가 없으면 **아무것도 안 그린다** |
| `widgets/domain-tenant-gate/tenant-scope.ts` | **신규.** 서버에서 `{ activeTenant, otherTenants }`. 🔴 모든 불확실(degraded·throw·제품 없음)은 **«힌트 없음»** 으로 떨어진다 |
| `widgets/domain-tenant-gate/DomainTenantGate.tsx` | 주석 **정정** — «이 실패를 닫는다» 는 문단을 «닫지 못했다 + 왜» 로 바꾸고, 이 함수가 **여전히 정직하게 닫는 것**(제품이 정말 서비스하지 않는 테넌트)을 남겼다 |
| `features/ecommerce-ops/components/OrdersScreen.tsx` | `activeTenant`·`otherTenants` **선택 prop**. 🔴 `order-empty` 를 **지우지 않고** 그 아래 한 줄을 붙인다 |
| `app/(console)/ecommerce/orders/page.tsx` | `getTenantScope()` 를 불러 내려보낸다 |

## 🔴 문구가 «데이터가 없습니다» 가 아닌 이유

진짜로 어느 테넌트에도 0건일 수 있고 **그때도 이 힌트는 뜬다**(ⓑ 를 고른 대가이고,
AC-0 표가 미리 적은 그 대가다). 그래서 단언하지 않는다 — «현재 테넌트 `X` **에는**
없습니다 … `Y` 테넌트에 **있을 수 있습니다**». 틀릴 때 **운영자가 한 번 더 확인하는**
쪽으로 틀린다.

## 🔴 opt-in 범위 — 측정된 화면 하나

2026-09-22 창이 판정한 것은 `/ecommerce/orders` 다. 나머지 ecommerce 화면(그리고 68개
`-empty` 마커 전체)으로 넓히는 것은 **아무도 재지 않은 주장**이므로 하지 않았다 —
`TASK-MONO-718` § 제외가 `productKey` 에 대해 세운 것과 같은 규율이다.
🔵 그 경계를 **칸이 지킨다**: `other-tenant-hint.test.tsx` 의 마지막 칸이
`getTenantScope` 를 부르는 ecommerce 라우트가 **정확히 `['orders']`** 임을 단언한다
⇒ 넓히는 PR 은 그 기대값을 **함께** 고쳐야 하고, 조용히 번질 수 없다.

---

# AC 판정

## AC-0 — ✅ 소유자 결정 **ⓑ** 확정 (2026-09-22 UTC)

## AC-1 — ✅ 술어를 바꾸고 **라이브가 내는 모양**으로 쟀다

- [x] 픽스처의 `tenants` 를 **실측값** `['demo-corp','ecommerce']` 로 바꿨다.
- [x] 🔴 **그 픽스처에서 기존 코드가 빨개지는 것을 먼저 봤다** (AC-1 이 요구한 순서):

```
× TASK-MONO-718 > 🔴 assumed into a tenant the product does NOT serve → the mismatch notice
  AssertionError: expected '<p data-testid="section-body">body</p>' to contain 'domain-tenant-mismatch'
```

  ⇒ 창에서 본 화면과 **글자 그대로 같은 실패**다. 픽스처가 문제였다는 증명.
- [x] 그 칸을 **라이브가 만들 수 있는 입력**으로 고쳤다 — 제품 둘(`ecommerce`·`scm`)을 두고
  `scm` 에만 있는 `other-corp` 로 들어가면 안내가 뜬다. `selectableTenants()` 가 **제품
  건너 합집합**이므로 도달 가능한 경로다.
- [x] 대조군: 데이터가 그 테넌트에 있는 경우 힌트가 **안 뜬다**(`otherTenants: []` → 빈 렌더).

### 🔴 새 층의 bite — 양방향, 그리고 **되돌려서** 봤다

```
BITE A  힌트가 항상 null 을 돌려주게 함        →  2 failed | 12 passed   (rc=1)
BITE B  서버가 otherTenants 를 항상 [] 로 냄   →  2 failed | 12 passed   (rc=1)
복원 후                                        →  14 passed             (rc=0)
```

🔵 두 bite 가 **서로 다른 칸**을 문다 — A 는 렌더 층, B 는 서버 층. 한 방향만 봤으면
«배선이 없어도 초록» 을 못 걸렀다.

## AC-2 — ✅ 「못 확인함」은 여전히 통과다

- [x] degraded 레지스트리 · 던지는 레지스트리 · 제품 없음 → **힌트만 침묵**하고 화면은 그대로.
      718 이 세운 규칙을 바꾸지 않았다. (칸 3개, `it.each`)
- [x] 🔵 방향이 다르다는 점을 적어 둔다: 718 의 규칙은 «막지 마라» 였고, 719 의 것은
      «말하지 마라» 다. 둘 다 «못 확인했을 때 해로운 쪽으로 틀리지 않는다» 의 같은 얼굴이다.

## AC-3 — ⏳ 창 판정은 `TASK-MONO-672` 로 넘긴다

- [x] 🔴 단위 칸 초록으로 닫지 않는다 — **718 이 그렇게 닫혔고 창에서 FAIL 했다.**
- [x] `TASK-MONO-672` 에 **항목 11** 로 넘겼다.

---

# 🔴 이 PR 이 **안 한 것** (의도적으로)

- **다른 ecommerce 화면**(`products`·`sellers`·`users`·`shippings`·정산 …)에 배선하지 않았다.
  창이 «14장 중 9장이 빈 목록» 을 봤으므로 넓힐 근거는 있으나, **어느 9장인지 내가 기록으로
  갖고 있지 않다** ⇒ 목록을 지어내는 대신 측정된 한 장만 배선했다.
- **68개 `-empty` 마커 전체**로의 일반화 — 이것은 디자인 결정이고 이 티켓의 권한 밖이다.
- `DomainTenantGate.tenantMismatch()` **삭제** — 도달 가능하고 정직하게 닫는 케이스가 있다.
  지우면 그 케이스가 조용히 사라진다.

---

# 🔴🔴 게이트 하나가 잡은 것 — **단위 칸 3552개가 전부 초록인 채 빌드가 깨졌다**

`next build` 가 이렇게 죽었다:

```
Error: You're importing a component that needs "next/headers". That only works
       in a Server Component ...
  import trace:
    session.ts → DomainTenantGate.tsx → index.ts(배럴) → OrdersScreen.tsx
```

`OrdersScreen` 은 `'use client'` 인데 **위젯 배럴**로 `OtherTenantHint` 를 불렀고, 그 배럴은
**서버 전용** `DomainTenantGate` 도 내보낸다 ⇒ 서버 코드가 클라이언트 번들로 딸려 왔다.

🔴 **vitest 는 이것을 못 잡는다** — 번들 경계를 세우지 않으므로 단위 칸은 **전부 초록**이었다
(316파일 · **3552칸** 통과, 타입체크 rc=0, 린트 경고 0). 이 저장소가 이름 붙인 «가드가 **없는**
한 지점이 결함 자리» 그대로다.

고침은 **직접 임포트**(`@/widgets/domain-tenant-gate/OtherTenantHint`)이고, 회귀는
`other-tenant-hint.test.tsx` 의 칸이 **소스 문자열로** 문다 — 동작으로 물 수 있는 종류가 아니다.
🔵 bite 확인: 배럴로 되돌리면 그 칸만 **1 failed | 14 passed**.

---

# 게이트 기록 (전부 이 트리에서 실행)

| 게이트 | 결과 |
|---|---|
| `vitest run` (console-web 전체) | 🟢 **316 files · 3552 tests passed** (rc=0, 1034s) |
| `tsc --noEmit` | 🟢 rc=0 |
| `next lint` | 🟢 **No ESLint warnings or errors** |
| `next build` | 🔴 **rc=1 → 고침 → 🟢 rc=0** (66/66 static pages) |
| bite A (힌트가 null) | 🟢 2 failed — 문다 |
| bite B (서버가 `[]`) | 🟢 2 failed — 문다 |
| bite C (배럴로 되돌림) | 🟢 1 failed — 문다 |

🔴 **로컬 초록은 CI 초록이 아니다.** 여기서 안 돌린 것: 통합(Testcontainers) · e2e ·
`nightly-e2e`. 이 변경은 라우트·testid 를 **추가만** 하므로 프런트 e2e 가 볼 수 있다
⇒ 머지 뒤 다음 nightly 를 한 번 확인한다(`platform/git-workflow-policy.md` § post-merge nightly).
