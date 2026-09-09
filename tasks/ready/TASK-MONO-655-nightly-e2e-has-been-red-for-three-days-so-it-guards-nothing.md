# Task ID

TASK-MONO-655

# Title

🔴🔴 **nightly e2e 가 3일째 빨갛다 — 즉 지금 아무것도 지키지 않는다** (그리고 내가 찾은 기전은 시작 날짜를 설명하지 못한다)

# Status

ready

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

- [ ] 🔴 **첫 초록을 찾아라.** 09-07 이전으로 `gh run list --workflow nightly-e2e.yml` 을
      더 거슬러 본다. 「3일」은 내가 **40런만 본** 값이고 하한이다 — 더 길 수 있다.
- [ ] 🔴 **두 잡의 실패 사유가 같은지 다른지 확인하라.** 나는 web-store 만 읽었다.
      콘솔이 다른 이유로 죽는다면 **결함이 둘**이고 한 티켓으로 묶으면 하나만 고쳐진다.
- [ ] 위 40런 표를 다시 세라. 그 사이 초록이 하나라도 있으면 「영원한 빨강」이 아니다.

## AC-1 — 원인을 지목한다

- [ ] 🔴🔴 **지목한 원인이 «시작 날짜» 를 설명하는가**를 반드시 확인하라. 내가 찾은
      `stock === null` 갈래는 그러지 못했다(변경 09-08 · 첫 실패 09-07).
- [ ] 🔵 원인이 둘 이상일 수 있다. `stock === null` 이 **두 번째** 원인일 가능성은 남아
      있다 — 첫 원인을 고쳐도 이 갈래가 여전히 헬퍼를 굶길 수 있다.
- [ ] 🔴 **`ADR-MONO-070` 이 잘못했다고 적지 마라.** 그 결정(모르는 것을 그리지 않는다)은
      옳다. 낡은 것은 **그 결정에 맞춰 안 고쳐진 e2e 헬퍼**다.

## AC-2 — 고친다

- [ ] 헬퍼가 «재고 숫자» 가 아니라 **«선택 가능한 옵션»** 을 고르게 한다(그 화면이 재고를
      모를 수 있다는 것이 이제 설계다). 🔴 술어를 넓히기만 하면 **품절 옵션도 잡는다** —
      대조군을 둬라.
- [ ] 🔴 **`data-testid` 를 새로 만들 거면 그 이유를 적어라.** 텍스트로 고르는 것이 깨진
      원인이 「텍스트가 설계상 사라졌다」이므로, 여기서는 testid 가 옳은 답일 수 있다.

## AC-3 — 다시 꺼지지 않게 한다

- [ ] 🔴🔴 **이 티켓의 진짜 결함은 «3일간 아무도 몰랐다» 이다.** 빨강 자체보다 그것이 크다.
      nightly 가 빨개졌을 때 **누군가에게 도착하는 경로**를 만들거나, 못 만들면
      **그 사실을 ⚪ 로 적어라**(「알림을 못 붙였고 그래서 다음에도 3일 갈 수 있다」).
- [ ] 🔵 `CLAUDE.md` 는 이미 *"check the next nightly run on `main` once after"* 라고
      적어 두었다. **산문에는 게이트가 없다** — 이 저장소가 여러 번 데인 문장이다.
      그래서 산문을 고치는 것으로 닫지 마라.

---

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
