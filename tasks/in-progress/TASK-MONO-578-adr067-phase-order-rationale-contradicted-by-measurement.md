# Task ID

TASK-MONO-578

# Title

`ADR-MONO-067` 안에 **재지 않고 쓴 근거 두 곳**이 살아 있다. 실측으로 교체한다 — 그중 하나는 fan 을 맨 뒤로 미룬 근거 **전부**다.

# Status

in-progress

# Owner

monorepo

# Task Tags

- adr
- documentation
- correction

---

# Goal

`ADR-MONO-067` 은 ACCEPTED 이고 **결정 자체는 건드리지 않는다.** 문제는 그 결정 **옆에 적힌 근거**다.
두 주장이 *실측이 아니라 추정*으로 쓰였고, 2026-08-25 소스 실측이 둘 다 뒤집었다.

🔴 **왜 지금 고쳐야 하나** — 이 저장소가 이미 두 번 데인 모양이다:
*한 사실이 두 절에 있으면 한쪽만 고쳐지고, **살아남은 거짓이 더 자주 읽히는 쪽**이 된다.*
지금 거짓은 **§ 결정 표**(가장 자주 읽히는 절)에 있고, 정정은 **아무 데도 없다**.

---

# Context — 무엇이 어긋났는가

## ① fan 은 "프록시 층 자체가 없다" — **거짓**

ADR 두 자리에 같은 주장이 있다:

| 자리 | 문구 |
|---|---|
| § 단계 순서 (L113) | *"fan 은 **프록시 층 자체가 없다**(`route.ts` 2개) — 경계를 새로 만들어야 한다"* |
| § 결정 표 (L192) | `4 \| fan \| route.ts 2개 \| **프록시 층을 새로 만들어야 함 — 신규 작업 최다**` |

**실측 (2026-08-25, `projects/fan-platform/web/fan-platform-web/src`)**:

| 잰 것 | 값 | 어떻게 |
|---|---:|---|
| `'use server'` 파일 | **5** | `features/{follow,post,membership,notification}/api/*` |
| 비테스트 `fetch(` 호출 지점 **전수** | **3** | `demo-payment.ts` · `client.ts` · `auth-callbacks.ts` |
| 그중 **클라이언트**에서 나가는 것 | **1** | `demo-payment.ts:20` |
| 그 1건의 대상 | **상대경로** `/api/payment-config` | 백엔드 오리진 아님 |
| 백엔드 주소를 **만드는** 지점 | **1** | `shared/api/client.ts:42` (`env.gatewayInternalUrl`) |

`client.ts` 는 자기 docblock 이 이렇게 말한다:

> *"Browser-side fetches are intentionally **not implemented** in this module — all read paths go
> through Server Components (RSC fetch) and write paths through Server Actions (`'use server'`).
> This keeps the access_token on the server and out of the client bundle."*

⇒ **경계는 이미 그어져 있다.** `route.ts` 개수는 프록시의 **유무**가 아니라 **모양**(route handler
BFF 냐 Server Action 이냐)의 차이였다. 🔴 **`route.ts` 를 세는 술어가 재려던 것을 재지 못했다** —
Server Action 은 그 술어에 안 걸리는데 하는 일은 같다.

## ② "브라우저가 `iam.local` 에 못 박힌다" — **지지되지 않음**

ADR § *"이것은 「문자열이 있다」보다 나쁘다"* 는 fan 클라이언트 번들의 `oidcIssuerUrl` 기본값
`http://iam.local` 이 **항상 쓰이게 된다**고 적었다. 같은 절이 스스로 유보를 달아 뒀다:

> *"단, 이 측정이 잰 것은 **존재**이지 **사용**이 아니다. 그 필드를 클라이언트 코드가 실제로
> 읽는지는 따로 확인해야 한다."*

**그 "따로 확인"을 했다 (2026-08-25)**:

| 잰 것 | 값 |
|---|---:|
| `@/shared/config/env` 비테스트 임포터 | 6 |
| 그중 `'use client'` | **2** (`portone-billing-key.ts` · `portone-checkout.ts`) |
| 그 2개가 읽는 필드 | `portoneStoreId` · `portoneChannelKey` **뿐** |
| `oidcIssuerUrl` / `gatewayUrl` 을 읽는 클라 지점 | **0** |
| `oidcIssuerUrl` 를 읽는 모듈 3개의 임포터 (1홉 전수) | **전부 서버** (`auth.ts`·`session.ts`·`middleware.ts`·`Header.tsx`·`[...nextauth]/route.ts`·`login/page.tsx`) |

⇒ 문자열이 번들에 **있는** 이유는 두 portone 클라 모듈이 **env 모듈 전체**를 클라이언트 그래프로
끌어오기 때문이고, **그 값을 읽는 클라 코드는 없다.**

🔵 **이건 철회가 아니라 ADR 이 스스로 열어 둔 항목에 대한 답이다.** 원래 문장이 정직하게
유보를 달아 뒀으므로, 고칠 것은 "틀렸다"가 아니라 **"물음이 닫혔다"** 다.

🔴 **한계를 함께 적어라**: 이건 **소스 1홉** 측정이지 다홉 도달성 증명이 아니고, 산출물 측정도
아니다(산출물은 `TASK-MONO-565` 가 했고 그것이 잰 것은 **존재**다). 술어를 넘어서 주장하지 않는다.

## ③ 두 주장 다 **다른 곳에는 복제되지 않았다**

```
grep -rn "프록시 층\|route.ts 2" tasks/ready/ tasks/in-progress/ tasks/review/   → 0건
grep -rn "iam.local"            tasks/ready/574,576,577 tasks/in-progress/575    → 0건
```
⇒ 정정 범위는 **ADR 파일 하나**로 닫힌다. (AC-4 에서 다시 센다 — 그 사이에 늘 수 있다.)

---

# Scope

**하는 것**: `docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md` **한 파일**의 근거 문구 교체 + History 1항목.

**안 하는 것** (명시):

- 🔴 **단계 순서를 바꾸지 않는다.** `단계 1~4` 는 소유자 **정확형 지정**으로 ACCEPT 된 결정의
  일부다(`platform/architecture-decision-rule.md` § The ACCEPTED Gate). 근거가 무너졌다고
  결정을 내가 다시 쓰는 것은 **self-ACCEPT** 다.
- `Status: ACCEPTED` 를 건드리지 않는다.
- 코드·티켓·`docs/adr/INDEX.md` 를 건드리지 않는다(새 ADR 이 아니다).

---

# Acceptance Criteria

## AC-0 — 전제 재측정 (착수 시점에 다시 재라)

착수 시점에 아래를 **다시** 돌리고, § Context 의 숫자와 어긋나면 **STOP** 하고 티켓을 고친다.
🔴 값을 여기서 베끼지 말고 **돌려서** 얻어라 — 이 저장소는 "이전 측정을 상속"해서 여러 번 틀렸다.

```bash
cd projects/fan-platform/web/fan-platform-web/src
git grep -l "^'use server'" -- . ':!**/__tests__/**'                    # 기대 5
git grep -n 'fetch(' -- . ':!**/__tests__/**' ':!**/*.test.ts*'         # 기대 3 지점
git grep -l "from '@/shared/config/env'" -- . ':!**/__tests__/**'       # 기대 6
```

**대조군**: `demo-payment.ts` 첫 줄이 `'use client';` 인지 확인한다 — 클라 판별자에 **눈이 있는지**
먼저 증명하고 나서 "클라 임포터 2개" 를 믿는다. (판별자가 0건을 내면 "없음"이 아니라 **"못 봤음"** 이다.)

## AC-1 — § 단계 순서 절의 근거를 교체한다

L113 불릿(*"fan 은 프록시 층 자체가 없다"*)을 실측으로 바꾼다. 최소한 이것을 담아야 한다:

- 경계는 **이미 있다** — 다만 Server Action 형태라 `route.ts` 를 세는 술어에 안 걸렸다.
- 백엔드 주소를 만드는 지점은 **1곳**(`client.ts:42`).
- 🔴 **fan 에 남은 진짜 비용은 프록시 신설이 아니라 D4(OIDC/쿠키)** 다 — fan 은 NextAuth 를 쓰고
  issuer 가 부팅마다 움직인다(`TASK-MONO-576` AC-1.5). console 도 같은 축에 걸린다.

## AC-2 — § 결정 표의 같은 행을 **반드시 함께** 고친다

L192 의 `4 | fan | … | 프록시 층을 새로 만들어야 함 — 신규 작업 최다` 행.

🔴 **AC-1 만 하고 AC-2 를 빼면 이 티켓은 실패다.** 한 사실이 두 절에 있고, 살아남는 쪽이
**더 자주 읽히는 절**이다. 두 자리를 **같은 커밋**에 넣고, 서로를 가리키게 한다.

## AC-3 — § "문자열이 있다보다 나쁘다" 절에 **답**을 적는다

"못 박힌다"는 결론을 **측정 결과로 대체**한다. 형태:

- 클라 임포터 2개가 읽는 필드는 portone 둘뿐 → `oidcIssuerUrl`·`gatewayUrl` 클라 독자 **0**.
- 🔵 그래서 **D1 은 fan 에서도 런타임상 이미 만족**이고, 남은 것은 D2(주소 조회)다.
- 🔴 그러나 **`sslip.io` 문자열이 클라 번들에 남는 것 자체는 여전히 D1 가드의 대상**이다 —
  "안 읽힌다"는 "번들에 없다"가 아니다. 가드는 **존재**를 잡아야 한다(그게 잡기 쉬운 축이고,
  안 읽히던 값이 읽히게 되는 변경은 조용하다).
- 한계 3줄(1홉 · 소스 · 산출물 아님)을 **같이** 적는다.

## AC-4 — 복제본 재확인

§ Context ③ 의 두 grep 을 **다시** 돌려 0건임을 확인한다. 0 이 아니면 그 자리도 같은 PR 에서 고친다.
🔴 0건 출력이 "없음"의 증거가 되려면 **패턴에 눈이 있어야 한다** — ADR 파일 자신을 포함시켜
`> 0` 이 나오는 것을 양성 대조군으로 확인한 뒤 티켓/큐 경로에서 0 을 읽는다.

## AC-5 — History 에 1항목 + **열린 질문을 소유자에게 올린다**

`## History` 에 append:

- 2026-08-25 — 근거 두 곳을 실측으로 교체. **결정·순서·Status 무변경.**
- 🔴 **열린 질문**: 3↔4 순서(console 먼저, fan 나중)의 근거는 이제 **비어 있다.** 남은 두 축
  (D2 플러그 지점 수 / D4 노출)에서 fan 이 더 싸 보인다 — 그러나 **순서 변경은 소유자 정확형
  지정 사안**이므로 이 티켓은 **묻기만 한다.**
- 🔵 **단계 2(web-store 파일럿)는 영향 없다** — web-store 는 D4 축에 아예 안 걸리고, 그것이
  파일럿으로 뽑힌 이유였다. 이 정정은 **2를 흔들지 않는다.**

## AC-6 — 검증 (문서 전용이므로 무엇이 검증인지 미리 못 박는다)

| 무엇 | 어떻게 |
|---|---|
| ADR 인덱스 드리프트 | `bash scripts/check-adr-index-drift.sh` (🔵 **약 88초 걸린다 — 멈춘 게 아니다**) |
| 큐 드리프트 | `bash scripts/check-index-queue-drift.sh` — 🔴 **`git add` **뒤에** 돌려라**(`git ls-files` 를 읽어서 스테이지 전에는 새 파일이 양쪽에서 안 보여 **가짜 통과**한다. 이 저장소 4회 재발) |
| 결정 문구 불변 | `git diff` 에서 `Status:` · § 결정 제목 · 단계 표의 **순서 열**이 안 바뀌었음을 눈으로 확인 |

---

# Related Specs

- `docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md` — 대상
- `platform/architecture-decision-rule.md` § The ACCEPTED Gate — 왜 순서를 못 바꾸는가
- `tasks/ready/TASK-MONO-576-*.md` AC-1.5 — 움직이는 `issuer`(fan 의 진짜 잔여 비용)
- `tasks/ready/TASK-MONO-577-*.md` — D2 해석기 자리(플러그 지점 수가 여기 근거가 된다)

# Related Contracts

없음 — 문서 정정이고 서비스 간 계약을 바꾸지 않는다.

---

# Edge Cases

| 케이스 | 처리 |
|---|---|
| AC-0 재측정이 § Context 와 어긋난다 | **STOP.** 그 사이 fan 이 바뀐 것이므로 티켓부터 고친다 |
| 정정하다 보니 순서를 바꾸고 싶어진다 | 🔴 **하지 마라.** AC-5 의 "열린 질문"까지가 이 티켓의 몫이다 |
| 다홉 도달로 클라가 `oidcIssuerUrl` 을 읽는 경로가 있다 | AC-3 의 한계 3줄이 이미 그렇게 적는다. 발견하면 **AC-3 을 그 사실로 쓴다**(티켓 실패가 아니다) |
| ADR 이 그 사이 다른 PR 로 수정됐다 | 리베이스 후 AC-0 부터 다시. 이 파일은 지금 열린 PR 이 없다(확인함) |

---

# Failure Scenarios

| 실패 | 징후 | 대응 |
|---|---|---|
| 두 자리 중 한 곳만 고친다 | § 결정 표에 옛 문구 잔존 | **AC-2 가 이것을 막는다.** 머지 전 `grep "프록시 층"` = 0 |
| 근거만 지우고 대체를 안 넣는다 | 왜 그 순서인지 아무 데도 안 남음 | 근거를 **교체**하는 것이지 삭제가 아니다 |
| 순서를 슬쩍 바꾼다 | 표의 단계 열이 바뀜 | AC-6 3행이 눈 검증으로 잡는다. self-ACCEPT 는 이 저장소의 Hard Stop 급 규율이다 |
| 가드를 스테이지 전에 돌려 가짜 초록 | 로컬 초록 / CI 빨강 | AC-6 2행. `git add` → `git ls-files` 눈확인 → 가드 |
| 실측 없이 "아마 그럴 것" 으로 고친다 | 새 문구에 숫자가 없음 | 이 티켓이 고치려는 결함과 **같은 결함**이다. 숫자 없는 문장은 안 넣는다 |
