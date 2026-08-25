# ADR-MONO-068 — 데모 백엔드 주소 해석기를 어디에 둘 것인가

**Status:** ACCEPTED
**Date:** 2026-08-26
**주관 티켓:** [`TASK-MONO-577`](../../tasks/done/TASK-MONO-577-adr-where-the-runtime-backend-resolver-lives-proposed.md)
**선행:** [`ADR-MONO-067`](ADR-MONO-067-demo-surfaces-served-from-vercel.md) D2 (*"백엔드 주소는 런타임 조회 결과다"*)
**출처:** 소유자 정확형 지정 (2026-08-26)

## History

- 2026-08-26 — **ACCEPTED (소유자 정확형 지정).** 지정 문구 그대로:

  > `577 = A + 승격 트리거(2개째에서 RED), D 는 067 로 상신`

  🔴 **이 ADR 은 PROPOSED 단계를 거치지 않았다** — 소유자가 초안보다 **먼저** 지정했다.
  `TASK-MONO-577` AC-4 는 *"PROPOSED 로 남기고 `## Decision` 은 소유자 지정 대기"* 를
  요구했는데, 그 요구의 목적(**self-ACCEPT 0**)은 여기서도 충족된다: 아래 § Decision 은
  **내 추천이 아니라 소유자가 적은 문자열**이고, 그 문자열은 A 만이 아니라 **rider 두 개
  (승격 트리거 · D 상신)까지 명시**한다. plain `A` 였다면 rider 는 미결로 남았을 것이다.
  [`platform/architecture-decision-rule.md`](../../platform/architecture-decision-rule.md) § The ACCEPTED Gate

---

## Context — 실측 (2026-08-26, 착수 시점 재측정)

`TASK-MONO-577` AC-0 이 *"착수 시점에 다시 재라, 어긋나면 STOP"* 을 요구했다. 다시 쟀고, 전제는 그대로다.

### 세 앱은 서로 다른 JS 세계에 산다

| 앱 | 워크스페이스 | 공용 패키지 |
|---|---|---|
| web-store | `projects/ecommerce-microservices-platform/pnpm-workspace.yaml` (`apps/*` + `packages/*`) | `@repo/api-client`·`eslint-config`·`tsconfig`·`types`·`ui`·`utils` |
| fan | `projects/fan-platform/pnpm-workspace.yaml` (`web/*`) | **없음** |
| console | **워크스페이스 없음** — 자기 `pnpm-lock.yaml` 단독 | **없음** |

`packages/*/package.json` 은 **6건**이고 **전부 ecommerce 프로젝트 안**이다 — 즉 프로젝트를
가로지르는 JS 코드의 자리가 아니다. 그리고 루트 `package.json` 이 스스로 못 박는다:

> `"description": "Monorepo root — thin shortcut scripts that delegate runtimes to the owning project. **No workspace / no dependencies at this level.**"`

### 🔴 그런데 결정을 가른 것은 위 표가 아니다 — **소비자가 하나**라는 사실이다

`ADR-MONO-067` 의 단계 순서는 2(web-store) → 3(console) → 4(fan) 이고, **3·4 는 D4(OIDC·쿠키
축)가 안 풀려서 못 간다.** 즉 이 해석기를 실제로 필요로 하는 앱은 지금 **web-store 하나**다.

그리고 이 저장소의 규칙이 그 경우를 이미 다룬다 — [`CLAUDE.md`](../../CLAUDE.md) § Project-scoped shared modules:

> **When**: **두 개 이상**의 서비스가 같은 도메인 개념을 선언하고…

⇒ **소비자가 하나인 코드의 공유 구조를 정하는 것은 결정이 아니라 추측이다.**
그리고 A 의 실패 모드(*"한 벌만 고쳐진다"*)는 **사본이 둘 이상일 때만** 발동한다.

### 공유 대상은 작다 — 그리고 D 가 채택되면 더 작아진다

`GET {DEMO_API_BASE}/status` → `{state, ip, used_minutes, budget_minutes}` 를 부르고 TTL 캐시하는
~30줄. 🔵 조회 소스는 **이미 있다**(론처가 쓰는 그 엔드포인트) — 새 인프라 0.

🔴 그런데 세 앱의 fetch 스택이 다르다: web-store 는 axios(`@repo/api-client`), fan 은 네이티브
`gatewayFetch`, console 은 `route.ts` 159개. **공유 단위를 넓힐수록 그 차이에 걸린다.**

---

## 선택지

| # | 안 | 대표 실패 모드 |
|---|---|---|
| **A** | **앱별 구현** | 🔴 사본이 둘 이상 생기면 **한 벌만 고쳐진다** — `CLAUDE.md` 가 project-scoped libs 를 도입한 이유가 정확히 이 실패다 |
| B | 새 repo-root JS 패키지 | 루트 `package.json` 의 자기 선언을 뒤집는 **구조 변경**. **console 은 워크스페이스 자체가 없어** 새로 만들어야 하고, **세 Vercel 프로젝트의 install 단계**를 전부 건드려야 한다(fan 의 `installCommand` 가 존재하는 이유가 이미 *"Root Directory 가 lockfile 보다 깊다"* 였고, 그 영역은 **261자 하나로 배포를 0초에 죽인** 자리다 — `TASK-MONO-562`) |
| C | 복사 + **세 사본 동일성 가드** | 드리프트를 막지 못하고 **잡는다**. 🔴 그런데 사본이 하나뿐인 동안 이 가드는 **잴 것이 없다** — `TASK-MONO-579` 가 방금 같은 모양을 만났다(사본이 둘이면 가드보다 **줄이는 것**이 쌌다) |
| D | **주소를 안 바뀌게 만든다** | § D 상신 참조 — **이 ADR 이 채택할 수 없다** |

---

## Decision — `A + 승격 트리거(2개째에서 RED)`

**해석기는 앱별로 구현한다. 공유 구조를 만들지 않는다.** 단, **두 번째 구현이 생기는 순간
CI 가 RED** 가 되어 A/B/C 를 다시 결정하게 한다.

### D1 — 구현은 앱 안에 산다

각 앱이 자기 스택에 맞게 구현한다(web-store 는 axios, console 은 route handler, fan 은
`gatewayFetch`). **다른 앱에서 임포트하지 않는다.**

### D2 — 🔴 구현은 **기계 판독 마커**를 단다

```ts
// DEMO-RESOLVER: web-store   (ADR-MONO-068 — 두 번째가 생기면 CI 가 RED)
```

마커가 없으면 승격 트리거가 그 구현을 **못 센다.** 그래서 마커는 선택이 아니라 **요구**이고,
가드가 *"내용은 걸렸는데 마커가 없다"* 를 별도로 RED 로 잡는다.

### D3 — 승격 트리거의 숫자는 **2** 다

[`scripts/check-demo-resolver-copies.sh`](../../scripts/check-demo-resolver-copies.sh) 가 CI 에서
돌고, **해석기를 가진 앱이 2개 이상이면 실패**한다.

🔴 **비용이 0인 "나중에 승격" 은 오지 않는다.** 트리거가 자기 숫자를 명시하고 **물지 않으면**
A 는 결정이 아니라 미룸이다. 그래서 그 가드는 `--self-test` 로 물기를 증명한다(7칸).

🔴 **트리거가 발화하면 가드를 완화하지 마라.** 그 RED 는 결함이 아니라 **이 ADR 의 전제가
소진됐다는 신호**이고, 그때 할 일은 `TASK-MONO-577` 의 A/B/C 를 실제로 고르는 것이다.
결정하면 가드는 그 결정에 맞게 **교체**된다 — 삭제가 아니라.

### D4 — 🔵 "공유하지 않는다" 는 되돌리기가 싸다

A 를 골랐다가 B/C 로 가는 비용은 **30줄을 옮기는 것**이다. 반대 방향(B 를 만들었다가 A 로)
은 루트 워크스페이스와 세 Vercel install 설정을 되돌리는 것이다. **비대칭이 A 쪽을 가리킨다.**

---

## 🔴🔴 D 상신 — `ADR-MONO-067` 로 올린다 (이 ADR 은 채택할 수 없다)

`TASK-MONO-577` AC-1.5 가 열거하지 않은 축을 찾았다: **주소를 안 바뀌게 만들면 해석기 자체가
필요 없다.** 그런데 그것은 `ADR-MONO-067` 의 선택지 공간(D2 = *"주소는 런타임 조회 결과다"*)을
건드리므로 **이 ADR 이 단독으로 채택할 수 없다.** 관측과 대가를 적어 067 로 올린다.

### 대가 — **계정에서 실측했다** (2026-08-26)

`TASK-MONO-577` AC-1.5 는 *"금액을 티켓에 적지 말고 계정에서 확인하라"* 고 못 박았다. 확인했다:

| Cost Explorer `APN2-PublicIPv4:InUseAddress` | 값 |
|---|---|
| 2026-08 (26일까지) 사용량 · 비용 | **11.41 시간 · $0.057** |
| 단가 (두 값의 나눗셈) | **$0.005 / 시간** |
| 고정 IP 로 24/7 유지 시 (730h) | **≈ $3.65 / 월** |
| **차액** | **≈ +$3.6 / 월** — 같은 항목이 **약 64배** |

🔵 맥락: 8월 총액 ≈ **$12**, 그중 `EC2 - Other` **$4.42 는 100GB EBS** 로 데모가 꺼져 있는
98.6% 동안에도 나간다. **이미 같은 종류의 비용을 받아들이고 있다.**
🔵 계정에 **EIP 는 0개**다(`describe-addresses` 실측) — 아직 아무것도 시작되지 않았다.
🔴 Route53 은 이 IAM 사용자에게 **권한이 없어** 호스티드존 유무를 못 쟀다. **못 쟀다고 적는다.**

### D 가 채택되면 사라지거나 줄어드는 것 (577 AC-1.5 의 열거)

① Traefik 라벨 드리프트(`553`) · ② `demo-boot.sh` 의 `DEMO_DOMAIN` 파생 · ③ 론처의 동적 링크 ·
④ `CONSOLE_PUBLIC_ORIGIN`(`358`) · ⑤ **이 ADR 의 해석기 자체** · ⑥ `TASK-MONO-576` AC-1.5 의
**움직이는 `issuer`**

⚠️ **D 는 D4(OIDC·쿠키)를 풀지 않는다.** 이름이 고정돼도 여전히 **평문 HTTP** 라 로그인 축은
남는다. 다만 TLS 를 붙일 **전제**는 된다(그것은 `ADR-MONO-067` 의 선택지 A 이고 이미 기각된
방향이므로, 다시 여는 것은 **별개의 결정**이다).

🔵 **D 가 채택되면 A 는 더 좋아진다** — 주소가 고정되면 앱이 공유할 것은 *"주소"* 가 아니라
*"데모가 켜져 있는가"* 한 줄로 줄고, 그 크기는 공유할 가치가 없다.

---

## Consequences

- ➕ **구조 변경 0.** 루트 워크스페이스도, 새 패키지도, Vercel install 설정 변경도 없다.
- ➕ 각 앱이 **자기 스택에 맞는** 구현을 갖는다(axios / route handler / 네이티브 fetch).
- ➖ 🔴 **두 번째 구현이 생기면 중복이 실재한다.** 그것을 감수하는 대가로 트리거를 둔다 —
  중복이 **생기는 것**을 막지 못하고, **조용히 생기는 것**을 막는다.
- ➖ 트리거가 발화하는 시점은 **단계 3(console) 착수 시**로 예상된다. 즉 이 결정은
  *"지금 정하지 않는다"* 를 **명시적으로, 만료일과 함께** 고른 것이다.
- 🔵 오늘 가드의 실제 대상은 **0개**다. 그래서 라이브 통과는 아무것도 증명하지 않으며,
  가드는 **탐지기 생존 대조군**과 **선언 앱 커버리지**로 자기가 눈이 있음을 따로 증명한다.
