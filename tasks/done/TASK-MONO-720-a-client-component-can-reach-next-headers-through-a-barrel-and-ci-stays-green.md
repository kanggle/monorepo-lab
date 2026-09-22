# Task ID

TASK-MONO-720

# Title

🔴 클라이언트 컴포넌트가 **배럴을 건너** 서버 전용 모듈에 닿아도 CI 는 초록이다 — console-web 은 `next build` 를 PR 마다 안 돌린다

# Status

done (2026-09-22 UTC — 4차원 검증 · impl PR #3955 squash `9dadd7a98`)

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

---

# 구현 기록 (2026-09-22 UTC · AC-0 = 형제 가드)

## 바뀐 것

| 파일 | 무엇 |
|---|---|
| `scripts/client-graph.mjs` | **신규(추출)** — `'use client'` 뿌리에서의 전이 닫힘. **판정을 갖지 않는다** |
| `scripts/check-client-graph-server-only.mjs` | **신규** — 서버 전용 잎 판정 + self-test 7칸 |
| `scripts/check-client-graph-backend-origins.mjs` | 순회기를 공유 모듈로 **갈아 끼움**. 판정은 그대로 |
| `.github/workflows/ci.yml` | 새 잡 + outputs + 경로 필터(🔴 **공유 순회기도 트리거**) |
| `CLAUDE.md` · `platform/git-workflow-policy.md` | 「N of the M」 수치 **22/60 → 24/62** |

## 🟢 추출이 **무손실**임을 수치로 고정했다

형제 가드를 추출 전후로 같은 트리에 돌렸다:

```
before  앱 4 · 클라이언트 뿌리 377 · 도달 661 · hits 0 · self-test 7/7
after   앱 4 · 클라이언트 뿌리 377 · 도달 661 · hits 0 · self-test 7/7
```

🔵 이것이 «복사 대신 추출» 을 고를 수 있었던 이유다 — 그 가드의 self-test 가 **추출이
무엇을 깨뜨렸는지 즉시 말해 준다**. 실제로 두 번 말해 줬다(아래 § 추출이 드러낸 것).

---

# AC 판정

## AC-0 — ✅ **형제 가드** (구현이 셋째 선택지를 강제했고, 그것도 기록했다)

기존 가드는 아무것도 `export` 하지 않고 최상위에서 바로 실행된다(import 하면 즉시 돈다).
⇒ 형제를 세우려면 순회기를 **복사**하거나 **추출**해야 했고, 복사는 이 저장소가 이름 붙여
감시하는 실패다(`check-demo-resolver-copies.sh` — *"두 번째 것은 결정이지 사본이 아니다"*).

## AC-1 — ✅ 금지 잎을 **코퍼스에서 셌다**

2026-09-22 실측 (`projects/*/apps/*/src`, `from '<X>'` 기준):

| 잎 | 파일 수 |
|---|---|
| `next/headers` | **12** |
| `server-only` | 0 |
| `next/cache` | 0 |
| `client-only` | 0 |

⇒ **오늘 이 저장소가 실제로 쓰는 서버 전용 잎은 `next/headers` 하나뿐이다.**
🔵 그래도 목록에 `server-only`·`next/cache` 를 둔다 — 이것은 «세는 모집단» 이 아니라
**금지 목록**이라 0건이어도 공허하지 않다. Failure Scenario 1 이 지목한 모양(다음 사건이
다른 잎으로 오는 것)을 self-test 칸 (f) 가 고정한다.
🔴 `client-only` 는 **안 넣는다** — 축이 반대다(클라이언트 전용이 서버로 가는 것).

## AC-2 — ✅ bite: **되돌리면 빨개진다**, 그리고 **진짜 사건으로** 확인했다

self-test 7칸(합성 트리):

```
(a) 'use client' → **배럴** → 서버 전용            문다   ← 이 가드를 만든 사건의 모양
(b) 서버 컴포넌트가 같은 모듈을 부른다              안 문다 (대조군 ①)
(c) 'use client' 가 안전한 모듈만 부른다            안 문다 (대조군 ②)
(d) 중간이 `'use server'`                           안 문다 (Server Action 은 그래프의 끝)
(e) `import type` 만 한다                           안 문다 (번들에 안 들어간다)
(f) 잎이 `server-only` 다                           문다   ← 잎 하나만 박으면 놓친다
(g) 클라이언트 뿌리 0개                             판정 불가 (통과로 안 읽는다)
```

🔴🔴 **그리고 합성 트리로 끝내지 않았다** — `TASK-MONO-719` 가 고친 그 한 줄을 실제로
되돌려 봤다:

```
배럴로 되돌림 → rc=1
  ✗ projects/platform-console/apps/console-web/src/shared/lib/session.ts  →  next/headers
  console-web  client roots=276  reached=404 → **429**   (배럴이 25개를 더 끌어온다)
복원          → rc=0
```

⇒ **9월 22일에 이 가드가 있었으면 CI 가 그 커밋에서 잡았다.**

## AC-3 — ✅ `main` 에서 초록이고, 그 초록이 공허하지 않다

```
ok — 앱 4개 · 클라이언트 뿌리 377개에서 도달하는 661개 모듈에 서버 전용 임포트 0건
```

하한(앱 3 · 뿌리 20 · 도달 60)을 넘겨야 이 문장을 인쇄한다 ⇒ «뿌리를 못 찾아서 초록» 이
아님을 **출력 자신이** 말한다. 🔵 하한값은 형제와 **같은 값**을 쓴다 — 같은 그래프이므로
갈라지면 한쪽이 조용히 약해진다.

## AC-4 — ✅ `scripts/` 파일 수 변화를 쓸었다

`22/60` → **`24/62`**. 산문 집 두 곳(`CLAUDE.md` · `platform/git-workflow-policy.md`)을
고쳤다 — 가드가 인쇄한 처방 그대로(*"이 스크립트가 아니라 산문의 문장을 고쳐라"*).

---

# 🔴🔴 추출이 드러낸 것 — **원래부터 있던 결함 하나**

새 가드의 self-test 칸 (f)(`import 'server-only';`)가 처음에 **안 물었다.** 원인은 공유
`IMPORT_RE` 가 **부수효과 임포트**(`from` 절 없는 `import 'x';`)를 아예 안 보는 것이었다.

🔵 그 정규식은 **추출 전 판에서 그대로 가져온 것**이다 ⇒ **형제 가드도 같은 구멍을 갖고
있었다.** 부수효과 임포트도 모듈을 **실행**하므로 번들에 들어가고, 그 안의 백엔드 오리진
리터럴은 브라우저로 간다 — 즉 저쪽 축에서도 놓칠 수 있는 경로였다.

⇒ 공유 모듈에서 고쳤고(세 모양 전부: `from` · 동적 `import()` · 부수효과), **형제의
self-test 7칸과 실 트리 수치가 변하지 않음**을 확인했다(661 도달, hits 0 그대로).
🔴 이것은 추출이 **만든** 결함이 아니라 추출이 **드러낸** 결함이다 — 구별해서 적는다.

## 🔵 그리고 «참인 멤버가 더 좁은 술어에 떨어지는» 것을 한 번 막았다

추출 직후 `check-ls-files-guard-count.sh` 가 `23 of 62` 를 냈다. 분자가 +1 밖에 안 오른
이유는 형제 가드가 `listFiles` 를 넘기면서 **`ls-files` 라는 문자열을 잃었기** 때문이다 —
그 가드는 **여전히 그 인덱스를 읽는데** 텍스트 술어에서 빠진 것이고, 그 파일이 스스로
경고하는 바로 그 실패 모드다(*"a true member lost to a tighter predicate"*).

⇒ 형제의 헤더에 **모집단이 무엇인지** 다시 적었다(사실이므로 정직하고, 동시에 계수가
맞는다). `24 of 62`.

---

# 🔴 이 PR 이 **안 한 것** (의도적으로)

- **console-web 의 `next build` 를 PR 마다 켜기** — 티켓 § 제외. 수 분이 든다는 실측이
  이미 저장소에 적혀 있고 이 티켓은 그 비용 판단을 뒤집지 않는다.
- **동적 `import()` 를 별도로 세기** — Edge Cases 가 «어떻게 셀지 정하고 적어라» 라고
  했는데, 지금 판정은 **정적과 똑같이** 센다(청크가 갈려도 브라우저로 가는 것은 같다).
  🔵 이 결정을 여기 적는 것으로 그 요구를 만족시킨다.
- **서버 전용 모듈의 재배치** — 이 티켓은 **무는 것**을 만든다. 배치는 각 PR 이 고친다.

---

# 게이트 기록 (AC-4 의 «가드 전수»)

| | |
|---|---|
| `scripts/check-*.mjs` **6개** | 🟢 전부 rc=0 (새 가드 · 형제 · 나머지 4) |
| `scripts/check-*.sh` 전수 | 🟢 **2건 제외 전부 rc=0** |
| 필수 3종(index drift · id collision · ledger drift) | 🟢 rc=0 |
| `check-ls-files-guard-count.sh` | 🟢 rc=0 (`24/62`) + self-test rc=0 |
| `ci.yml` YAML 파싱 | 🟢 OK |

🔴 **실패한 2건은 「내 변경과 무관」이라고 가정하지 않고 실측했다** — `main`(내 변경이 없는
트리)에서 같은 둘을 돌려 **똑같이 rc=1** 임을 확인했다:

```
scripts/check-erp-single-tenant-ratchet.sh     rc=1   ← erp-platform-mysql 컨테이너 필요
scripts/check-prerendered-demo-verdict.sh      rc=1   ← DEMO_API_BASE 필요
```

🔵 둘 다 «SKIP 이 아니라 아무것도 재지 못함» 을 스스로 말하는 가드다(래칫이 계측기 부재를
초록으로 읽지 않는 설계) ⇒ 환경 의존이고 이 PR 의 책임이 아니다.

---

## CORRECTION — `# Acceptance Criteria` 의 체크박스가 **안 찍혔다** (2026-09-22 UTC, close chore 의 4차원 (d) 가 잡음)

🔴 위 `# Acceptance Criteria` 절의 상자는 **전부 `[ ]` 인 채로 `review/` 에 들어왔다.**
판정은 아래 § AC 판정에 **전부 적혀 있지만**, 그 절만 열어 본 사람은 «아무것도 안 했다» 로
읽는다. 이 저장소가 이름 붙인 **«한 사실이 두 절에 있으면 한쪽만 고쳐진다»** 이고,
이 세션에서 `TASK-MONO-710` 이 같은 자리에서 같은 이유로 걸렸다.

🔵 `review/` 는 frozen 이므로(tasks/INDEX.md:108 — *"Editing one rewrites the record of
what was reviewed"*) **상자를 고쳐 찍지 않는다.** 대신 상자↔판정의 대응을 여기 명시한다:

| AC | 상자 | 어디서 닫혔나 | 한 줄 근거 |
|---|---|---|---|
| **AC-0** | 2칸 | § AC 판정 · AC-0 | **형제 가드** 확정. 구현이 셋째 선택지(추출 vs 복사)를 강제했고 «복사 금지» 로 추출을 골랐다 |
| **AC-1** | 2칸 | § AC 판정 · AC-1 | 코퍼스 실측 — `next/headers` **12파일**, `server-only`·`next/cache`·`client-only` **0** |
| **AC-2** | 3칸 | § AC 판정 · AC-2 | self-test **7칸**(배럴 경유 · 대조군 2 · `'use server'` · `import type` · `server-only` 잎 · **뿌리 0개=판정 불가**) + **실제 사건 되돌리기** rc=1 |
| **AC-3** | 2칸 | § AC 판정 · AC-3 | `main` 초록이고 «앱 4 · 뿌리 377 · 도달 661» 을 **출력 자신이** 인쇄한다 |
| **AC-4** | 1칸 | § AC 판정 · AC-4 + § 게이트 기록 | 가드 전수 · `22/60 → 24/62` 로 산문 집 둘 갱신 |

## 🔵 다음 사람에게 — 이 실수의 **재발 방지는 절차이지 가드가 아니다**

⇒ **`ready → review` 를 옮기는 그 커밋에서 상자를 함께 찍어라.** 옮긴 뒤에는 frozen 이라
이 정정문 말고는 길이 없다.

🔴 그리고 이것을 무는 가드를 만들지 마라. `CLAUDE.md` 가 이미 그 이유를 적었다 —
«✅ 를 grep 하는 검사기는 `TASK-MONO-605` 를 통과시켰을 것이다». 상자가 찍혔는지는
기계가 세지만 **그 상자가 참인지**는 못 센다. 그래서 이 판정은 close chore 를 도는
사람 몫이고, **이번엔 그 사람이 잡았다**(그것이 4차원 (d) 가 있는 이유다).
