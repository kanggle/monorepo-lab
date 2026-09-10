# Task ID

TASK-MONO-655

# Title

🔴🔴 **nightly e2e 가 3일째 빨갛다 — 즉 지금 아무것도 지키지 않는다** (그리고 내가 찾은 기전은 시작 날짜를 설명하지 못한다)

# Status

done

# Owner

monorepo

# Task Tags

- ci
- e2e

---

# Goal

`nightly-e2e.yml` 을 다시 **판정하는 가드**로 되돌린다. 지금은 항상 빨개서 아무도 안 보고,
그래서 **콘솔·web-store 풀스택 e2e 가 실질적으로 꺼져 있다.**

---

# 🔴 실측 (2026-09-09 UTC)

## ① 40런 전부 실패

| 날짜 | 런 | 결론 |
|---|---|---|
| 2026-09-07 | 2 | 전부 failure |
| 2026-09-08 | 23 | 전부 failure |
| 2026-09-09 | 15 | 전부 failure |

🔴🔴 **영원한 빨강은 꺼진 가드다.** 이 저장소가 이미 그 문장을 다른 가드에 대해 쓴 적이
있다(`TASK-MONO-649` 헤더). 여기서는 **그 상태가 3일째 유지 중**이고, 그 사이 12건이
머지됐다 — 전부 이 잡을 빨간 채로 지나갔다.

🔵 **`ci.yml` 은 초록이다.** 이 잡은 `nightly-e2e.yml` 이고 required 도 아니다. 즉
`CLAUDE.md` 가 경고한 그 상태다 — *"the console and web-store full-stack e2e suites run only
in `nightly-e2e.yml`, not `ci.yml` — a route/nav/testid/heading change can merge green having
never been exercised. … check the next nightly run on `main` once after."*
**그 「한 번 확인」을 아무도 안 했다.**

## ② 실패하는 잡은 둘, 3일 내내 같다

```
🔴 Platform Console E2E full-stack (Playwright + docker compose)
🔴 Frontend E2E full-stack (web-store, Playwright + docker compose)
```

## ③ web-store 의 현재 지문 — **2 실패 / 10 통과**

```
[chromium] › e2e/cart-management.spec.ts:15:7  › 장바구니 조작 …
[chromium] › e2e/golden-flow.spec.ts:65:7      › 웹스토어 주문 골든 플로우 …
```

둘 다 같은 헬퍼에서 죽는다 — `e2e/helpers/product.ts:33`:

```ts
const firstOption = page
  .locator('button:not([disabled])')
  .filter({ hasText: /재고\s+\d+/ })     // ← 이 술어가 아무것도 못 잡는다
  .first();
await expect(firstOption).toBeVisible();   // Expect "toBeVisible" with timeout 10000ms
```

## ④ 🔵 그럴듯한 기전을 찾았고 — **그것이 시작을 설명하지 못한다**

`VariantSelector.tsx` 가 옵션 항목을 **세 갈래**로 그린다:

```tsx
{v.stock === null ? null : isSoldOut ? (…품절…) : (<span>재고 {v.stock}</span>)}
```

🔴 `stock === null` 이면 **아무것도 안 그린다** — 숫자도, "재고 있음" 배지도. 사유가 코드
주석에 있다: *"저장본은 재고를 싣지 않고(`ADR-MONO-070` § 재고), 모르는 것을 그리면 그 화면이
없는 사실을 주장한다."* 그러면 헬퍼의 `/재고\s+\d+/` 는 **정의상** 아무것도 못 잡는다.

🔴🔴 **그런데 그 갈래는 2026-09-08 에 들어왔고**(`9f0fcd2d6`, ADR-MONO-070/071)
**첫 실패는 2026-09-07 이다 — 하루 빠르다.** 그리고 09-07 의 두 런에서 **이미 콘솔과
web-store 가 둘 다** 실패했다.

⇒ **이 기전은 원인이 아니거나, 원인의 전부가 아니다.** 「검증 가능한 기전 ≠ 원인」이고,
증상이 그 변경 **이전에도** 있었으면 그 변경이 원인이 아니다. 최소 하나가 더 앞에 있다.

---

# 🔴🔴 정정 (2026-09-09 UTC, 착수 직후) — **이 티켓의 ④ 가 틀렸다**

기안할 때 나는 *"이 기전은 원인이 아니거나 전부가 아니다"* 라고 적었다. **틀렸다.
`stock === null` 갈래를 만든 그 커밋이 원인이 맞다.**

## 무엇을 잘못 쟀나

AC-0 이 요구한 대로 **런을 100개까지 거슬러** 다시 세었더니 그림이 달라졌다:

| 날짜(UTC) | 초록 | 빨강 |
|---|---|---|
| 09-09 | 0 | 15 |
| **09-08** | **0** | **23** |
| 09-07 | **13** | 3 |
| 09-06 | 7 | 7 |
| 09-05 | 11 | 20 |
| 09-01~04 | 49 | 1 |

🔴 **기안의 「09-07 ×2 failure」는 40런만 본 값이었다.** 09-07 은 실은 **13번 초록**이었고,
시각순으로 보면 이렇다:

```
09-07 02:02Z ~ 09:47Z   success × 13      (마지막 초록 = ea2e91d3b)
09-07 19:17Z            🔴 failure  9f0fcd2d6   ← ADR-MONO-070/071 그 커밋 자신
09-07 19:22Z            🔴 failure  d82d5c9bf
09-07 21:24Z            🔴 failure  d82d5c9bf
… 이후 초록 0
```

**`9f0fcd2d6` 이 도착한 그 런에서 처음 빨개졌고 다시는 안 초록이 됐다.**

## 왜 놓쳤나 — **KST 커밋 날짜를 UTC 런 날짜와 비교했다**

`git log --date=short` 가 준 값은 `2026-09-08`(**KST**)이고 런 목록은 **UTC** 다. 실제로
`9f0fcd2d6` 의 커밋 시각은 `2026-09-08 04:17 +0900` = **`2026-09-07T19:17Z`** 다. 그래서
「변경은 09-08, 첫 실패는 09-07 이니 원인이 아니다」라는 **역전된 결론**이 나왔다.

🔴🔴 이 호스트의 문서화된 함정이다(`CLAUDE.md` § Date Stamps: *KST 저자 · UTC CI*).
**측정 규율을 다루는 티켓을 쓰면서 그 함정을 밟았다.** ⇒ 일반화: **시각을 비교할 때는 두
값의 타임존을 먼저 같게 만들어라.** 날짜만 보면 하루가 통째로 뒤집힌다.

🔵 그리고 두 번째 오류가 첫 번째를 가렸다 — 「40런 전부 실패」는 **정렬된 결과의 머리만**
본 것이다. 그 표본에서 09-07 은 실패만 보였고, 같은 날의 초록 13개는 **범위 밖**이었다.

---

# 🟢 원인 둘 — **같은 커밋, 다른 기전** (AC-0 ②가 옳았다)

두 잡의 사유는 **다르다.** 기안은 web-store 만 읽었고, 콘솔은 Playwright 에 **닿지도
못한다.**

## ① 콘솔 — `console-web` 이 **컴파일에 실패**한다

```
./src/features/demo-tour/api/read-console-sample.ts
Module not found: Can't resolve '@demo/public-data'
  ← ./src/features/demo-tour/index.ts ← ./src/app/(demo)/layout.tsx
```

`demo-tour` 는 `9f0fcd2d6` 이 만든 기능이고, `@demo/public-data` 는
`link:../../../../infra/demo/public-data` — **컨텍스트 밖**을 가리킨다.

🔴 **공급은 돼 있었다.** compose 넷 모두 `additional_contexts` 에
`demo-public-data` 를 준다. 빠진 것은 **Dockerfile 의 스테이지 하나**다:

| 스테이지 | `backend-resolver` | `public-data` |
|---|---|---|
| `deps`(install) | ✅ 58행 | ✅ 64행 |
| `builder`(build) | ✅ 79행 | 🔴 **없음** |

`builder` 는 `COPY --from=deps …/node_modules` 로 **심링크만** 물려받으므로 대상이 여기
없으면 `pnpm build` 가 죽는다. 🔴🔴 **그 파일 자신이 그 경고를 이미 적어 두었다** —
*"this stage inherits the dangling symlink, so the target must exist **HERE too**"* 이고
deps 스테이지 주석은 *"형제 앱들이 한 스테이지에서 install·build 를 다 하므로 그들의 한 번
복사는 **여기서 한 번 복사해도 된다는 선례가 아니다**"* 라고까지 적었다.
**경고를 써 놓고 다음 패키지에 적용하지 않았다** — 한 사실이 두 자리에 있고 한쪽만 고쳐진
그 부류다.

- [x] **고쳤다**: `builder` 스테이지에 `COPY --from=demo-public-data …` 한 줄.
- [x] 🟢 **머지 후 실측으로 닫혔다** (소유자가 「AC-4」로 부른 그 확인). `main` `3a0b8b99f`
      의 nightly 런 [`34375762085`](https://github.com/kanggle/monorepo-lab/actions/runs/34375762085):

      | 잡 | 결론 | 근거 |
      |---|---|---|
      | Platform Console E2E full-stack | 🟢 **success** | `Running 7 tests using 2 workers` → **`7 passed (22.0s)`** |
      | Frontend E2E full-stack (web-store) | 🔴 failure | 원인 ②, 아래에서 고친다 |

      🔴 **「초록」만으로 닫지 않았다** — 스펙이 0개 돌아도 러너는 rc=0 을 낼 수 있다. 그래서
      판정은 «실행된 테스트 수»로 했다: **7개가 실제로 돌았고 7개가 통과**했다. 그 전 런에서
      이 잡은 `pnpm build` 단계에서 죽어 Playwright 에 **닿지도 못했다** ⇒ 원인 ① 진단 확정.

## ② web-store — 헬퍼의 술어가 굶는다 (기안 ③ 그대로, 원인은 같은 커밋)

`VariantSelector` 가 `stock === null` 이면 아무것도 안 그리고(`ADR-MONO-070` § 재고),
헬퍼는 `/재고\s+\d+/` 로 옵션을 고른다 ⇒ 정의상 못 찾는다.

### 🔴 먼저 — 「기전이 옳은가」를 코드로 확정했다

기안은 `VariantSelector` 의 세 갈래만 읽었다. 그것만으로는 **e2e 환경에서 실제로 `null` 이
오는지** 알 수 없다(라이브 백엔드가 도는 풀스택 잡이므로 숫자가 올 수도 있었다). 두 파일이
그것을 닫는다:

```
get-product.ts:20      const { data } = await readStoreSnapshot();   ← 상세는 «항상» 저장본
snapshot-mappers.ts:67 stock: null,                                  ← 하드코딩
```

⇒ 공개 상세에서 `stock` 은 **상수 `null`** 이다. `/재고\s+\d+/` 는 정의상 0건이고, 로그의
지문도 `element(s) not found`(「보이지 않음」이나 「N개 중 0개」가 아니다)로 그것과 일치한다.

- [x] **고쳤다** — 셋 중 셋째 길을 골랐다.

  1. 🔴 텍스트를 되살린다 → 화면이 **모르는 사실을 주장**하게 된다. `ADR-MONO-070` 이 금지.
  2. 🔴 `button:not([disabled])` 로 **넓히기만** 한다 → 페이지 첫 활성 버튼(찜·트리거)이
     잡힌다. 실패가 아니라 **조용한 오작동**이 되므로 더 나쁘다.
  3. 🟢 원소의 **역할**에 이름을 붙인다 ⇒ `data-testid="variant-option"`.

  술어와 그 사유는 `variant-option-testid.ts` **한 파일에만** 있고, e2e 헬퍼와 단위 대조군이
  **같은 상수를 import** 한다 — 여기서 셀렉터를 다시 타이핑하면 테스트는 헬퍼가 아니라 자기
  사본을 재게 된다.

- [x] 🟢 **대조군 4칸** (`product-detail-with-cart.test.tsx`).
      🔴 기존 스위트는 `stock: 10 / 5 / 0` 만 썼다 — 즉 **라이브 모양뿐**이라 저장본 경로에
      구멍이 있었고, 그래서 **단위는 초록인 채로 e2e 만 3일 빨갰다.** 픽스처가 현실을 안
      담으면 초록도 공허하다. 네 칸이 각각 다른 축을 문다:

      | # | 축 | 무는 것 |
      |---|---|---|
      | 1 | `stock: null` 을 잡는가 | + 같은 화면에 `/재고\s+\d+/` 가 **0건**임을 단언 ⇒ 저장본 경로에 재고 문구를 되살리면 빨개진다 |
      | 2 | 🔴 **품절은 안 잡는가** | 품절 항목이 testid 를 **가진 채** 제외되는지 — 제외가 `:not([disabled])` 로 일어나는지 «원소가 없어서» 인지를 가른다 |
      | 3 | 이미 선택된 항목은 안 잡는가 | 같은 걸 또 고르면 e2e 가 헛돈다 |
      | 4 | 🔴🔴 **페이지 전체에서 옵션만 잡는가** | 활성 버튼이 옵션보다 많고 **그 첫째가 옵션이 아님**을 단언 ⇒ 길 (2) 가 왜 답이 아닌지를 산문이 아니라 **수치로** 남긴다 |

- [x] 🔴 **`data-testid` 를 새로 만든 사유**는 `variant-option-testid.ts` 머리 주석에 적었다.
      요지: 텍스트로 고르는 것이 깨진 원인이 **「텍스트가 설계상 사라졌다」**이므로, 여기서는
      testid 가 옳은 답이다. testid 는 재고에 대해 **아무것도 주장하지 않고** 「이것은 옵션
      항목이다」만 말한다. 품절 제외의 근거는 `disabled` **한 곳**으로 두었다(같은 사실을
      `data-sold-out` 으로 또 적으면 한쪽만 고쳐지는 자리가 생긴다) — 그 대신 그 전제를
      대조군 #2 가 문다.

- [x] 🟢 **web-store 잡의 최종 판정이 났다 — 아래 § 최종 판정.** (기록 유지) ⚪ 였던 사유: 로컬에서 이 스위트를 돌릴 수
      없다 — web-store 는 vitest 4 이고 이 호스트는 Node 24 라 **기동 자체가 안 된다**
      (`#module-evaluator`, 문서화된 한계). Node 20 을 임시로 끌어와도 pnpm 심링크 해석에서
      다시 막혔다. 로컬에서 실제로 통과시킨 것은 **`tsc --noEmit` rc=0 · `next lint` rc=0**
      뿐이고, 단위 스위트의 권위는 **CI(Node 20)의 `frontend-unit-tests`** 다.
      🔴 그리고 e2e 자체는 **nightly 에서만** 돈다 — 이 PR 이 초록이어도 헬퍼가 고쳐졌다는
      증거는 아니다. 그 증거는 머지 후 nightly 의 web-store 잡뿐이다.

---

# 🟢 가드 — `scripts/check-stage-inherits-its-contexts.mjs`

**불변식**: `node_modules` 를 다른 스테이지에서 복사해 오는 스테이지는, 그 원본이 놓은
외부 컨텍스트를 **하나도 빠짐없이** 자기도 놓아야 한다.

🔴🔴 **기존 `check-build-context-declarations.sh` 는 이 축을 못 본다.** 그것은
「Dockerfile 이 요구 ↔ compose 가 공급」을 보고, 이번 결함은 **공급도 됐고 복사도 했는데
스테이지 하나를 빠뜨린** 것이라 거기 **초록으로 통과했다.** 같은 계열이 이 저장소를 문 것은
이번이 **네 번째**다(`585` · `615` · `629` · `655`).

**bite 를 ①주입 ②구조 무사 ③물기로 나눠, 실제 저장소에서 증명했다:**

```
①  builder 의 public-data 복사 제거
②  FROM 3개 — Dockerfile 구조는 무사(빨강이 파싱 실패가 아니다)
③  rc=1 · 「스테이지 «builder» 이 «deps» 의 node_modules 를 물려받는데
           그 스테이지가 놓은 «demo-public-data» 을 자기는 안 놓는다」
```

self-test 5칸. **대조군 셋**: (3) 한 스테이지에서 install·build 를 다 하는 앱은 **대상이
아니다**(형제 앱들의 모양 — 여기서 빨개지면 그들이 이유 없이 죽는다) · (4) 상속 스테이지가
**더** 놓는 것은 결함이 아니다 · (5) 이미지에서 가져오면 **판정 불가이지 결함이 아니다**.
비-공허성 하한 = **비교한 스테이지 쌍의 수**(파서가 스테이지를 못 읽으면 0이 되어 빨개진다).

CI 에 필터 `stage-contexts` + 잡을 달았다. 🔴 `changes.outputs` 선언도 함께 — 그것이 없으면
`if:` 가 늘 거짓이라 가드가 **한 번도 안 돈다**(`TASK-MONO-646`).

---

# Scope

## 포함

- **09-07 이전으로 거슬러 첫 초록을 찾는다** — 언제부터 빨간지가 이 티켓의 첫 축이다
- 콘솔 e2e 의 실패 사유(아직 안 읽었다)
- 고치거나, 못 고치면 **왜 못 고치는지와 그때까지 무엇이 안 지켜지는지**를 적는다

## 제외

- 🔴 **`ci.yml` 로 옮기는 것** — 이 잡들은 docker compose 풀스택이라 PR 마다 돌리기엔
  비싸다. 그것이 nightly 인 이유다. 옮기려면 **비용을 재고 근거를 적어라.**
- 🔴 **테스트를 지우거나 skip 하는 것.** 빨강을 없애는 가장 빠른 길이고 **가장 나쁜 길**이다
  — 지금도 안 지키고 있는데 그러면 «안 지킨다는 사실»까지 사라진다.
- `TASK-MONO-654`(스토어 배너) · `TASK-PC-FE-276`(UUID) — 표면은 겹치지만 다른 결함이다.

---

# Acceptance Criteria

## AC-0 — 착수 전 재측정

- [x] 🔴 **첫 초록을 찾아라.** 09-07 이전으로 `gh run list --workflow nightly-e2e.yml` 을
      더 거슬러 본다. 「3일」은 내가 **40런만 본** 값이고 하한이다 — 더 길 수 있다.
- [x] 🔴 **두 잡의 실패 사유가 같은지 다른지 확인하라.** 나는 web-store 만 읽었다.
      콘솔이 다른 이유로 죽는다면 **결함이 둘**이고 한 티켓으로 묶으면 하나만 고쳐진다.
- [x] 위 40런 표를 다시 세라. 그 사이 초록이 하나라도 있으면 「영원한 빨강」이 아니다.

## AC-1 — 원인을 지목한다

- [x] 🔴🔴 **지목한 원인이 «시작 날짜» 를 설명하는가**를 반드시 확인하라. 내가 찾은
      `stock === null` 갈래는 그러지 못했다(변경 09-08 · 첫 실패 09-07).
- [x] 🔵 원인이 둘 이상일 수 있다. `stock === null` 이 **두 번째** 원인일 가능성은 남아
      있다 — 첫 원인을 고쳐도 이 갈래가 여전히 헬퍼를 굶길 수 있다.
- [x] 🔴 **`ADR-MONO-070` 이 잘못했다고 적지 마라.** 그 결정(모르는 것을 그리지 않는다)은
      옳다. 낡은 것은 **그 결정에 맞춰 안 고쳐진 e2e 헬퍼**다.

## AC-2 — 고친다

- [x] 헬퍼가 «재고 숫자» 가 아니라 **«선택 가능한 옵션»** 을 고르게 한다(그 화면이 재고를
      모를 수 있다는 것이 이제 설계다). 🔴 술어를 넓히기만 하면 **품절 옵션도 잡는다** —
      대조군을 둬라.
- [x] 🔴 **`data-testid` 를 새로 만들 거면 그 이유를 적어라.** 텍스트로 고르는 것이 깨진
      원인이 「텍스트가 설계상 사라졌다」이므로, 여기서는 testid 가 옳은 답일 수 있다.

## AC-3 — 다시 꺼지지 않게 한다

- [x] 🔴🔴 **이 티켓의 진짜 결함은 «3일간 아무도 몰랐다» 이다.** 빨강 자체보다 그것이 크다.
      nightly 가 빨개졌을 때 **누군가에게 도착하는 경로**를 만들거나, 못 만들면
      **그 사실을 ⚪ 로 적어라**(「알림을 못 붙였고 그래서 다음에도 3일 갈 수 있다」).
- [x] 🔵 `CLAUDE.md` 는 이미 *"check the next nightly run on `main` once after"* 라고
      적어 두었다. **산문에는 게이트가 없다** — 이 저장소가 여러 번 데인 문장이다.
      그래서 산문을 고치는 것으로 닫지 마라.

---

---

# 🟢 AC-3 실행 — 빨강이 «도착한다» (`nightly-red-arrives`)

`nightly-e2e.yml` 에 11번째 잡을 달았다. 다른 잡들과 달리 **무엇도 검사하지 않는다** — 이
워크플로의 판정이 저장소 **밖으로** 나가는 유일한 자리다.

- **빨강**: 고정 제목의 이슈를 연다(이미 있으면 본문 갱신). 🔵 코멘트는 **빨간 집합이 바뀐
  때만** — 매 런마다 달면 main 머지마다 쌓여서 그 이슈가 안 읽히게 된다.
- **초록**: 「다시 초록이다」를 코멘트하고 **닫는다.** ⇒ 이슈의 열림/닫힘이 nightly 의 상태를
  그대로 따라간다.

🔴 **v1 이 왜 실패했는지가 이 파일 머리에 처음부터 적혀 있었다** — *"Failure turns the main
branch badge red; visible in GitHub Actions UI"* + *"v2 would add issue auto-create … out of
scope"*. 도착 경로가 «누가 UI 를 보러 간다» 였고, 3일 동안 아무도 안 갔다. 그 머리 주석을
그 자리에서 갱신했다(v2 도착).

## 이 잡 자신이 눈이 머는 것을 막는다

🔴🔴 감시 모집단은 자기 `needs` 다 — 잡을 새로 만들고 `needs` 에 안 넣으면 이 알림은 그
잡에 대해 **조용히 눈이 먼다**. 그것이 정확히 655 가 고발하는 부류라서, 「모집단」 스텝이
워크플로에 **선언된** 잡 수와 감시 중인 수를 대조하고 어긋나면 **먼저 빨개진다.**

**실측** (이 브랜치): 선언 `15` · `needs` `14` · `15 == 14 + 1` ✅ · needs 에 빠진 잡 없음 ·
유령 잡 없음.

## 판정기 self-test — 4칸, 양방향

「항상 초록인 알림」은 655 가 고발한 바로 그 상태(안 무는 가드)의 재현이다. 그래서 판정을
고정 입력으로 **실제 `jq` 로** 돌려 증명했다:

| # | 입력 | 기대 | 실측 |
|---|---|---|---|
| 1 | 전부 `success` | 빨강 0 | `«»` ✅ |
| 2 | 하나 `failure` | 그 잡 이름 | `«y»` ✅ |
| 3 | `cancelled` | **빨강** (MONO-374: 취소된 런은 성공도 실패도 아니라 「안 일어난 것」이 된다) | `«x»` ✅ |
| 4 | 🔵 **대조군** `skipped` | 빨강 **아님** (watch 잡들은 push 런에서 정상 skip) | `«»` ✅ |

실제 모양(`frontend-e2e-fullstack: failure` + `platform-console-e2e-fullstack: success` +
`ami-generation-watch: skipped`)으로 한 번 더 → `frontend-e2e-fullstack` **하나만** 지목. ✅

## ⚪ 한계 — 정직하게

이 잡이 보장하는 것은 **이슈가 열리는 것까지**다. 그 이슈가 사람에게 «알림»으로 도달하는지는
소유자의 watch 설정에 달려 있고 **저장소 안에서 잴 수 없다.** 배지 대비 실질 개선인 지점은
하나뿐이고 그것으로 충분하다 — 배지는 보러 가야 보이지만 **이슈는 남는다.**
🔴 Slack webhook 은 여전히 없다(ADR-MONO-011 § 6.1 은 그 부분에서 계속 outstanding).

---

# 🟢 최종 판정 — nightly 가 다시 초록이다 (2026-09-09 UTC)

머지 커밋 `a1f763e3e` 의 nightly 런
[`34380542810`](https://github.com/kanggle/monorepo-lab/actions/runs/34380542810):

```
run conclusion = success          ← 2026-09-07T19:17Z 이후 처음
```

| 잡 | 결론 |
|---|---|
| Frontend E2E full-stack (web-store) | 🟢 **success** |
| Platform Console E2E full-stack | 🟢 **success** |
| 나머지 e2e·가드 9개 | 🟢 success |
| watch 3개 (AMI·fan surface·launcher freshness) | ⚪ skipped (push 런에서 정상) |
| **A red nightly arrives** | 🟢 success |

## 🔴 「초록」이 아니라 «무엇이 돌았나» 로 닫았다

빨강을 없애는 가장 나쁜 길이 skip 이라고 이 티켓이 스스로 적었으므로, 판정은 실행 수로 했다.
잡 자신의 「Assert the required specs actually ran」 스텝 출력:

```
spec                           ran  skipped
account-type-guard.spec.ts       1        0
auth-redirect.spec.ts            7        0
cart-management.spec.ts          1        0     ← 죽어 있던 둘 중 하나
golden-flow.spec.ts              1        0     ← 나머지 하나
rp-initiated-logout.spec.ts      1        0
wishlist.spec.ts                 1        0
OK — all 5 required specs ran (12 tests collected).
```

🔵 콘솔 쪽도 같은 축으로 확인했다 — `Running 7 tests using 2 workers` → `7 passed`.
단위 대조군도 실행됐다: `product-detail-with-cart.test.tsx` 가 **11 → 15 tests**(새 4칸).

## 🟢 AC-3 의 알림이 «초록 경로» 를 실제로 걸었다

`nightly-red-arrives` 가 처음으로 돌았고 자기 스텝이 실제 숫자를 냈다:

```
판정기 self-test 4칸 통과 — 초록/빨강 두 방향이 실제로 다르다.
선언된 잡 15 · 감시 중 14 (+ 이 잡 자신)
```

빨간 잡 0개 ⇒ 이슈를 안 만들었다. 실측: 저장소 이슈 **0건**(`gh issue list --state all`).

## ⚪ 그래도 안 잰 것 — 빨강 경로는 라이브에서 한 번도 안 돌았다

🔴 `gh issue create` / `gh issue edit` / `gh issue close` 자체는 **실행된 적이 없다.**
self-test 가 증명한 것은 **판정**(어느 잡이 빨간가)이지 **부작용**(이슈가 열리는가)이 아니다.
⇒ 다음에 nightly 가 실제로 빨개지는 날이 그 경로의 첫 실행이고, 그때 이슈가 안 열리면
**이 티켓이 만든 도착 경로는 없는 것**이다. 🔵 그 실패는 조용하지 않다 — 스텝이 죽으면
`nightly-red-arrives` 잡 자신이 빨개진다(그리고 그것은 배지에 남는다).

## 🔵 이 티켓이 남기는 일반화 두 개

1. **시각을 비교할 때는 두 값의 타임존을 먼저 같게 만들어라.** KST 커밋 날짜를 UTC 런
   날짜와 대조해서 「변경이 첫 실패보다 하루 늦다」는 **역전된 결론**을 냈고, 그 결론이
   원인을 **틀렸다고 판정**했다. 날짜만 보면 하루가 통째로 뒤집힌다.
2. **픽스처가 현실을 안 담으면 초록도 공허하다.** 단위 스위트는 `stock: 10/5/0` 만 써서
   3일 내내 초록이었고, 그 초록이 e2e 의 빨강을 **가리지는 않았지만 설명도 못 했다.**

# Related Specs / Contracts

- `.github/workflows/nightly-e2e.yml`
- `CLAUDE.md` § Git / branch / worktree discipline — *Post-merge nightly check*
- `ADR-MONO-070` § 재고 — 저장본은 재고를 싣지 않는다(**옳은 결정**)
- `projects/ecommerce-microservices-platform/apps/web-store/e2e/helpers/product.ts`
- `.../src/widgets/product-detail-with-cart/VariantSelector.tsx`
- `TASK-MONO-649` — 「영원한 빨강은 꺼진 가드다」를 이름 붙인 티켓
- `project_nightly_only_spec_merges_green_then_main_reds` · `project_nightly_e2e_service_addition_drift`

---

# Edge Cases

- **두 잡이 다른 이유로 죽는다** — 그러면 결함이 둘이다. AC-0 이 이것을 먼저 가른다.
- **flake 다** — 🔴 40런 연속은 flake 가 아니다. 그러나 *일부* 셀은 flake 일 수 있고,
  「전부 flake」로 접으면 진짜 결함이 그 안에 숨는다.
- **인프라(러너 디스크·docker compose)** — `ENOSPC` 는 단언 전 collection 실패로 위장한다.
  지문이 다르므로 로그에서 갈라라.
- **고쳤는데 다음 날 또 빨갛다** — 원인이 둘이었다는 뜻이다. AC-1 의 「시작 날짜를
  설명하는가」가 그것을 미리 막는다.

---

# Failure Scenarios

1. 🔴🔴 **`stock === null` 을 원인으로 적고 닫는다.** 그것은 첫 실패보다 하루 늦게 들어왔다.
   고쳐도 09-07 의 실패는 설명되지 않고, **증상이 남으면 원인이 아니었던 것**이다.
2. 🔴🔴 **테스트를 skip 해서 초록을 만든다.** 지금도 안 지키고 있는데 그러면 **안 지킨다는
   사실까지** 사라진다.
3. 🔴 **web-store 만 고치고 콘솔을 잊는다.** 잡이 둘이고 나는 하나만 읽었다.
4. 🔴 **`CLAUDE.md` 의 「한 번 확인해라」 문장을 강화하는 것으로 닫는다.** 산문에는 게이트가
   없다 — 그 문장은 **이미 있었고** 3일 동안 아무도 안 지켰다.
5. **`ADR-MONO-070` 을 되돌린다.** 그 결정은 옳다. 낡은 것은 헬퍼다.

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Opus** — 원인이 여럿일 가능성이 높고, 「그럴듯한 기전」이 이미 한
번 시작 날짜에 걸려 넘어졌다. 헬퍼 한 줄 고치는 일로 보이지만 **그 판단 자체가 이 티켓이
경계하는 것**이다.
