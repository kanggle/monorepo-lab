# Task ID

TASK-MONO-720

# Title

🔴 클라이언트 컴포넌트가 **배럴을 건너** 서버 전용 모듈에 닿아도 CI 는 초록이다 — console-web 은 `next build` 를 PR 마다 안 돌린다

# Status

in-progress (2026-09-22 UTC — AC-0 **형제 가드** 확정)

# Owner

미지정

# Task Tags

- ci
- platform-console
- guard

---

> **분석 모델:** Opus 5 / **구현 권장:** Sonnet — 기존 가드(`check-client-graph-backend-origins.mjs`)가
> **이미 같은 그래프를 걷는다**. 잎(금지 대상)을 하나 더 다는 일이고, 판정 설계는 이미 서 있다.

---

# 배경 — 이것은 추론이 아니라 **2026-09-22 에 실제로 밟은 것**이다

`TASK-MONO-719` 구현 중 `next build` 가 이렇게 죽었다:

```
Error: You're importing a component that needs "next/headers". That only works
       in a Server Component ...
  import trace:
    session.ts → DomainTenantGate.tsx → index.ts(배럴) → OrdersScreen.tsx
```

`OrdersScreen` 은 `'use client'` 이고, 위젯 **배럴**에서 `OtherTenantHint` 를 불렀다.
그 배럴은 **서버 전용** `DomainTenantGate`(→ `next/headers`)도 내보낸다 ⇒ 서버 코드가
클라이언트 번들로 딸려 왔다.

## 🔴🔴 그때 초록이었던 것들 — 전부

| 게이트 | 결과 |
|---|---|
| `vitest run` (console-web 전체) | 🟢 **316 files · 3552 tests** rc=0 |
| `tsc --noEmit` | 🟢 rc=0 |
| `next lint` | 🟢 경고 0 |
| **`next build`** | 🔴 **rc=1** ← 이것만 잡았다 |

🔵 vitest 는 **번들 경계를 세우지 않는다**. `tsc` 도 `eslint` 도 «이 모듈이 클라이언트
번들에 들어가는가» 를 묻지 않는다. 그 질문을 하는 것은 **번들러뿐**이다.

## 🔴 그리고 CI 는 console-web 의 `next build` 를 **돌리지 않는다** (실측)

`.github/workflows/ci.yml` 의 `Frontend unit tests (ecommerce + fan-platform + console-web, vitest)`
잡은 console-web 에 대해 **unit tests · typecheck · lint** 세 단계만 돈다. `build` 단계가 없다.
`Frontend lint & build` 라는 이름의 잡은 **ecommerce + fan-platform** 전용이다.

⇒ **내가 로컬에서 잡지 않았으면 이 결함은 초록으로 머지됐다.** 증상은 배포 시점에 나온다
(Vercel 빌드 실패) 또는 더 나쁘게는 빌드가 통과하되 **서버 전용 코드가 브라우저로 나간다**.

🔵 그리고 CI 가 안 돌리는 것은 **게으름이 아니다** — 같은 파일이 이미 이렇게 적고 있다:

> *"권위는 산출물 스캐너(`scan-client-bundle-origins.mjs`)다. 그러나 그것은 빌드가 필요하고
> (console-web 은 **수 분**) PR 마다 못 돈다. 이 잡은 소스만 읽어 **같은 축을 싸게** 잰다."*

---

# 🔵 그래서 고칠 자리가 이미 있다 — `check-client-graph-backend-origins.mjs`

그 가드(`TASK-MONO-585` / `ADR-MONO-067` D1)는 **`'use client'` 파일을 뿌리로 잡고 임포트를
따라가** 금지된 잎에 닿는지 본다. 지금의 잎은 «백엔드 주소 리터럴»이다.

🔴 **이 티켓이 더하려는 잎은 «서버 전용 모듈»이고, 그래프·해석기·self-test 하네스는 그대로
재사용된다.** 새 가드를 처음부터 쓰는 일이 아니다.

그 파일 스스로가 이 축의 근거를 적어 뒀다:

> *"**선언 경계 ≠ 번들 경계.** … 중간 세 파일 어디에도 `"use client"` 는 없다."*

내 사례는 그 문장의 **또 다른 사례**다 — 다만 방향이 반대다(금지된 **값**이 아니라 금지된
**능력**이 새어 든다).

---

# Goal

`'use client'` 에서 출발해 임포트를 따라갔을 때 **서버 전용 모듈**에 닿으면 **CI 가 문다** —
`next build` 를 PR 마다 돌리지 않고도.

---

# Scope

## 포함

- `scripts/check-client-graph-backend-origins.mjs` 확장 **또는** 같은 그래프 유틸을 쓰는 형제 가드.
  🔴 **어느 쪽인지는 AC-0 이 정한다** — 한 가드에 축 둘을 넣으면 실패 메시지가 «무엇이
  잘못됐는가» 를 못 말한다(이 저장소의 «원인을 지목한 메시지엔 그 원인만 무는 술어»).
- `.github/workflows/ci.yml` 의 해당 잡(또는 새 잡) + 경로 필터.
- 🔴 `scripts/` 에 파일을 **더하면** `check-ls-files-guard-count.sh` 의 분모가 움직인다
  ⇒ `TASK-MONO-650` 이 밟은 그대로다. **가드 전수를 돌리고** 그 가드가 지목하는 산문 집 둘을 고쳐라.

## 제외

- 🔴 **console-web 의 `next build` 를 PR 마다 켜기.** 수 분이 든다는 실측이 이미 파일에 적혀
  있고, 그 비용 판단을 이 티켓이 뒤집지 않는다. 🔵 **nightly 에 얹는 것**은 별개로 고려 가능
  하지만 그것도 이 티켓의 범위 밖이다(다른 결정, 다른 비용).
- 서버 전용 모듈의 **재배치**(예: 배럴을 쪼개기). 이 티켓은 **무는 것**을 만들고,
  무는 것이 생기면 배치는 각 PR 이 알아서 고친다.

---

# Acceptance Criteria

## AC-0 — 착수 게이트: **한 가드인가 두 가드인가** (소유자 결정) — ✅ **형제 가드 확정 (2026-09-22 UTC)**

> 🔵 **결정: 형제 가드.** 추천대로 확정됐다. 🔴 아래 기준(실패 메시지)은 결정 당시의 입력이므로 그대로 둔다.
>
> 🔴🔴 **그리고 구현이 셋째 선택지를 강제했다 — 그래프를 «공유» 하려면 추출이 필요하다.**
> 기존 가드는 단일 파일이고 **아무것도 export 하지 않으며** 최상위에서 바로 실행된다
> (import 하면 즉시 돌아 버린다). 그래서 형제를 세우는 길은 둘뿐이었다:
>   ⓐ 그래프 순회기를 **복사** — 🔴 두 순회기가 갈라지면 한쪽이 조용히 틀린다.
>      이 저장소엔 그 실패를 감시하는 가드가 **이름부터** 있다(`check-demo-resolver-copies.sh`
>      — *"두 번째 것은 결정이지 사본이 아니다"*).
>   ⓑ 순회기를 **공유 모듈로 추출** — 기존 가드를 건드리지만, 그 가드의 **self-test 7칸**이
>      추출이 무손실인지 즉시 말해 준다.
> ⇒ **ⓑ.** 사본을 만들지 않는다.
>
> 🔴 **추출 위치는 `scripts/` 바로 아래다 — `scripts/lib/` 가 아니다.**
> `check-ls-files-guard-count.sh` 의 분모는 `find scripts -maxdepth 1 -type f`(하위 디렉터리 제외)인데
> 분자는 `grep -rl 'ls-files' scripts`(**재귀**)다. 하위 디렉터리에 두면 **분자만 늘어** 비율이
> 비틀리고, 그 수치는 두 산문 집이 들고 있다. 실측 기준선 **22 / 60**.

- [ ] 🔴 기존 가드를 확장할지 형제를 세울지 정한다. 판단 기준은 **실패 메시지**다 —
      «백엔드 주소가 브라우저에 샌다» 와 «서버 전용 능력이 브라우저에 샌다» 는 고치는
      방법이 다르므로, 한 메시지가 둘을 말하면 읽는 사람이 엉뚱한 곳을 고친다.
- [ ] 🔵 추천: **형제 가드**(그래프 유틸은 공유, 판정·메시지는 분리). 🔴 내 추천이지 결정이 아니다.

## AC-1 — 금지된 잎의 목록을 **실측으로** 세운다

- [ ] 🔴 `next/headers` 하나만 박지 마라. 최소한 `next/headers` · `server-only` · `next/cache`
      (`revalidatePath` 등)를 보고, **이 저장소가 실제로 쓰는 것**을 grep 으로 세라.
- [ ] 🔴 «내가 아는 목록» 이 아니라 «이 코퍼스의 목록» 이어야 한다 — 이 저장소가 이름 붙인
      «내 레코드의 이름은 그 코퍼스의 이름이 아니다».

## AC-2 — bite: **되돌리면 빨개지는가**

- [ ] 🔴 실제 사건을 재현하는 self-test 칸: `'use client'` → 배럴 → 서버 전용.
      **배럴을 거치는 경로**가 핵심이다(직접 임포트만 보면 이 사건을 놓친다).
- [ ] 대조군 둘: ① 서버 컴포넌트가 같은 모듈을 부르는 것은 **정상**이다(물면 안 된다)
      ② `'use client'` 가 안전한 모듈만 부르는 것도 정상이다.
- [ ] 🔴 «일을 하나도 안 하고 rc=0» 을 막는 비공허성 — 뿌리(`'use client'` 파일)를 **0개**
      찾았으면 그것은 통과가 아니라 **실패**다.

## AC-3 — 지금 트리에서 **초록이어야 한다** (그리고 그 초록이 공허하지 않아야 한다)

- [ ] `TASK-MONO-719` 가 이미 고쳤으므로 `main` 에서는 통과해야 한다.
- [ ] 🔴 그 통과가 «뿌리를 못 찾아서» 가 아님을 출력이 말해야 한다(뿌리 수 · 잎 수를 인쇄).

## AC-4 — 🔴 `scripts/` 파일 수 변화를 쓸어라

- [ ] 파일을 더하거나 지우면 **모든 가드**를 돌려라. `check-ls-files-guard-count.sh` 가
      분모로 물고, 그 가드가 **자기 처방**을 인쇄한다(*"산문 집의 문장을 고쳐라, 이 스크립트가 아니라"*).

---

# Related Specs / Contracts

- `scripts/check-client-graph-backend-origins.mjs` (같은 그래프, 다른 잎)
- `.github/workflows/ci.yml` § `client-graph-origins` · § `frontend-unit`
- `ADR-MONO-067` D1 (브라우저는 백엔드 주소를 몰라야 한다 — 형제 축)
- `tasks/done/TASK-MONO-585-…` (선언 경계 ≠ 번들 경계의 최초 실측)
- `tasks/review/TASK-MONO-719-…` § 「게이트 하나가 잡은 것」 (이 티켓을 만든 사건)

---

# Edge Cases

- **`'use server'` 파일**을 지나는 경로 — 기존 가드가 이미 그 마디를 다룬다(`actions.ts`).
  서버 액션 너머는 클라이언트 번들이 아니므로 **거기서 끊어야** 한다. 🔴 안 끊으면 오탐이 쏟아진다.
- **타입 전용 임포트**(`import type`) — 번들에 안 들어간다 ⇒ 물면 오탐이다.
- **동적 `import()`** — 번들에 들어가지만 청크가 갈린다. 🔴 어떻게 셀지 정하고 그 결정을 적어라.

---

# Failure Scenarios

1. **`next/headers` 리터럴 하나만 박는다** → 다음 사건은 `server-only` 나 `next/cache` 로 온다.
   AC-1 이 코퍼스를 세라고 하는 이유다.
2. **직접 임포트만 본다** → **이 티켓을 만든 바로 그 사건을 놓친다**(경로가 배럴을 지났다).
3. **뿌리를 0개 찾고 초록** → 가드가 «일을 하나도 안 하고 rc=0». AC-2 의 비공허성 칸이 문다.
4. **한 가드에 축 둘을 합친다** → 실패 메시지가 두 처방을 섞어 말하고, 읽는 사람이 엉뚱한
   파일을 고친다.
