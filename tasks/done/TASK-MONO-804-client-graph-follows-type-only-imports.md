# Task ID

TASK-MONO-804

# Title

클라이언트 그래프 가드 둘이 타입 전용 임포트(`import type … from` · `export type … from`)를 번들 연결로 따라가 오탐한다 — 그리고 서버 전용 가드는 같은 파일의 진짜 임포트를 타입 줄 때문에 놓친다

# Status

done

# Owner

devops

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (공유 순회기 한 곳 + 가드 한 줄 · 자체 시험 칸 추가)
> 기안 2026-10-21 UTC — `TASK-PC-FE-358` 머지 전 PR #4451 에서 실제로 밟았다. 소유자 «진행» (2026-10-22 KST).

# Dependency Markers

- 출처: `projects/platform-console/tasks/review/TASK-PC-FE-358-wms-daily-throughput-chart.md` § 소유자 승인 · 머지 전 확인.
- 선행 없음.

# Goal

`scripts/client-graph.mjs`(두 가드가 공유하는 순회기)는 `'use client'` 뿌리에서 임포트를 따라가 «브라우저 번들에 들어가는 모듈» 을 모은다. 그런데 **문장 전체가 타입 전용인 임포트도 연결로 따라간다**(`client-graph.mjs:216-221` — `IMPORT_RE` 가 `import type { … } from` 을 일반 임포트와 같이 잡는다). 타입 전용 문장은 TypeScript · SWC 가 지우므로 번들에 안 들어간다.

실제 사건 (PR #4451, head `c04b575e5`):
- `WmsThroughputCard.tsx`(`'use client'`) 가 서버 전용 `api/throughput-state.ts` 를 **여러 줄** `import type { ThroughputCardStatus, WmsThroughputState } from '../api/throughput-state'` 로만 가리켰다.
- 두 가드가 빨강: `Client graph server-only imports` (`shared/lib/session.ts → next/headers`) · `Client graph backend origins` (`shared/config/env.ts` · `demo-backend.ts` 의 백엔드 주소).
- 같은 커밋의 브랜치 nightly(run `39636295024`)는 console-web `next build` 를 포함해 **success** — 번들러는 그 연결을 따라가지 않았다 ⇒ **오탐**.
- 우회: 타입을 `lib/` 로 옮겨 머지(#4451 `940b86ec8`). 가드 자체는 그대로다 — 다음 사람이 같은 모양으로 또 막힌다.

곁 결함 (코드 읽기 — 재현은 AC-0):
- `check-client-graph-server-only.mjs:69-70, 88` 의 `TYPE_ONLY_RE` 는 **파일 본문 전체**에서 «그 스펙을 가리키는 `import type` 줄이 있는가» 를 본다. 한 파일에 `import type { X } from 'next/headers'` 와 `import { cookies } from 'next/headers'` 가 **둘 다** 있으면 타입 줄이 맞아 진짜 임포트까지 걸러진다 ⇒ **놓침**(false negative). 이쪽이 더 위험하다 — 빨강이어야 할 것이 초록이다.

# Scope

## In Scope

- **AC-0 재현** (자체 시험 합성 트리, 고치기 전 빨강을 먼저 본다):
  - ① 클라이언트 → 여러 줄 `import type { A, B } from './s'` → `s.ts` 가 `next/headers` 임포트 ⇒ 지금 `bad=1`(오탐), 기대 0.
  - ② 같은 모양을 backend-origins 축에서(`s.ts` 가 `http://x.local` 리터럴) ⇒ 지금 1, 기대 0.
  - ③ 한 파일에 `import type { X } from 'next/headers'` + `import { cookies } from 'next/headers'` ⇒ 지금 0(놓침), 기대 1.
- **수선**:
  - 순회기: 문장 전체가 타입 전용인 `import type … from` · `export type … from` 은 **따라가지 않고 `importsOf` 에도 넣지 않는다**. `import type from './x'`(기본 바인딩 이름이 `type`) 는 타입 전용이 **아니다** — 계속 따라간다.
  - 서버 전용 가드: 본문 단위 `TYPE_ONLY_RE` 필터를 없앤다(순회기가 이미 타입 전용 스펙을 빼므로 남은 스펙은 전부 진짜다).
  - 🔵 인라인 `import { type A } from 'x'` 는 **계속 따라간다**(보수적) — `verbatimModuleSyntax` 아래에서는 `import {} from 'x'` 부수효과 임포트로 남을 수 있다. 이 결정을 순회기 주석에 적는다.
- **자체 시험 칸 추가** (두 가드 모두): ①②③ + 대조군 `import type from './s'` → 따라간다 · `export type { A } from './s'` → 안 따라간다 · 여러 줄 **값** 임포트 → 문다.
- 실 트리: 두 가드 rc=0 · 하한(뿌리 ≥20 · 도달 ≥60) 유지 · `reached` 수 변화를 기록(줄어든 만큼이 타입 전용 연결이었다).

## Out of Scope

- 인라인 `type` 수식자 처리(위 🔵 — 따로 정할 일).
- 가드 판정 대상(잎 목록 · 백엔드 주소 리터럴) 변경.
- `scripts/` 에 파일을 더하거나 지우는 것(CLAUDE.md — 그러면 `check-ls-files-guard-count.sh` 분모가 움직인다). 이 티켓은 수정만.

# Acceptance Criteria

- [x] **AC-0** — ①②③ 이 고치기 전 기대와 다르게 나오는 것을 자체 시험 출력으로 기록. ③ 이 이미 기대대로(1)면 곁 결함은 «없음» 으로 적고 시험 칸만 남긴다.
- [x] **AC-1** — 수선 + 자체 시험 칸. `node scripts/check-client-graph-server-only.mjs --self-test` · `node scripts/check-client-graph-backend-origins.mjs --self-test` 전부 통과.
- [ ] **AC-2** — 실 트리 두 가드 rc=0, `reached` 변화 기록. CI 의 두 체크 초록.

# Related Specs

- `scripts/client-graph.mjs` 머리말(«선언 경계 ≠ 번들 경계» · `'use server'` 경계 규칙)
- `TASK-MONO-585` · `TASK-MONO-720` (두 가드의 출처 — `tasks/done/`)

# Related Contracts

- 없음 (저장소 가드).

# Edge Cases

- `import type X, { Y } from` 같은 문법 오류 모양 — TS 가 거부하므로 고려하지 않는다.
- 주석 안의 `import type` — `stripComments` 뒤에 판정하므로 영향 없음.
- `export type * from './x'` (TS 5) — 타입 전용 → 안 따라간다(시험 칸 하나).
- 타입 전용 연결을 빼면 `reached` 가 줄어 하한에 가까워질 수 있다 — AC-2 가 수를 기록한다.

# Failure Scenarios

1. `import type from './x'`(이름이 `type` 인 기본 임포트)를 타입 전용으로 잘못 읽어 진짜 연결을 끊는다 → 대조군 칸이 막는다.
2. 순회기만 고치고 `TYPE_ONLY_RE` 를 남긴다 → ③ 의 놓침이 그대로다.
3. 자체 시험 없이 실 트리 rc=0 만 본다 → 「무는가」 를 증명하지 않은 가드(`client-graph.mjs` 의 2026-09-05 교훈).

## AC-0 측정 (2026-10-21 UTC · 분석=Opus 5.5)

고치기 **전** 트리(`b1e5a9271`)에 자체 시험 칸을 먼저 넣고 돌렸다.

| 칸 | 가드 | 기대 | 고치기 전 |
|---|---|---|---|
| (h) 여러 줄 `import type { … }` → 서버 전용 모듈 | server-only | 0 | **1** (오탐) |
| (k) `export type { … } from` · `export type * from` 재수출만 | server-only | 0 | **1** (오탐) |
| (i) 한 파일에 `import type … 'next/headers'` + `import { headers } from 'next/headers'` | server-only | 1 | **0** (🔴 놓침 — 곁 결함 실재) |
| (j) `import type from './s'`(이름이 `type` 인 기본 임포트) — 대조군 | server-only | 1 | 1 |
| (f) 여러 줄 `import type { … }` → `http://iam.local` 리터럴 | backend-origins | 0 | **1** (오탐) |
| (f2) 같은 모양의 여러 줄 **값** 임포트 — 대조군 | backend-origins | 1 | 1 |

출력: `server-only --self-test` rc=1 «3건 실패»(h · i · k) · `backend-origins --self-test` rc=1 «1건 실패»(f).

원인 둘:
- `scripts/client-graph.mjs:216-221` — `IMPORT_RE` 가 잡은 모든 `… from 'x'` 를 연결로 따라갔다(타입 전용 문장 포함).
- `scripts/check-client-graph-server-only.mjs:69-70, 88` — `TYPE_ONLY_RE` 가 **파일 본문 전체**에서 «그 스펙을 가리키는 `import type` 줄이 있는가» 를 보고 스펙을 통째로 지웠다 ⇒ (i).

## 구현 (2026-10-21 UTC · Opus 5.5)

- `scripts/client-graph.mjs`: `TYPE_ONLY_STATEMENT_RE` · `isTypeOnlyStatement()` 추가 — `import type {…}` · `import type X` · `import type * as` · `export type {…}` · `export type *` 는 연결로 안 따라가고 `importsOf` 에도 안 넣는다. `import type from` 은 값 임포트(기본 바인딩 이름 `type`). 인라인 `import { type A }` 는 계속 따라간다(보수적 — 주석에 이유).
- `scripts/check-client-graph-server-only.mjs`: 본문 단위 `TYPE_ONLY_RE` 필터와 쓰이지 않게 된 `stripComments` 임포트 삭제 — 순회기가 문장 단위로 이미 뺀다. 자체 시험 칸 (h) (i) (j) (k).
- `scripts/check-client-graph-backend-origins.mjs`: 자체 시험 칸 (f) (f2).
- `scripts/` 파일 추가 · 삭제 없음(수정만 — `check-ls-files-guard-count.sh` 분모 불변).

검증 (스테이지 뒤):
- `server-only --self-test` rc=0 — **11/11** · `backend-origins --self-test` rc=0 — **9/9**.
- 실 트리 두 가드 rc=0 · hits=0. `reached` 변화(같은 그래프라 두 가드 동일): web-store **222 → 217** · fan-platform-web **41 → 35** · console-web **486 → 481** (빠진 16 = 타입 전용 연결). 뿌리 수 불변(83 · 21 · 326) · 하한(뿌리 ≥20 · 도달 ≥60) 여유.
- CI: `ci.yml:1902-1956` 의 두 잡이 `scripts/client-graph.mjs` 변경에 걸려 자체 시험 + 실 트리를 돈다 — 이 PR 의 체크로 AC-2 를 닫는다.

## CORRECTION (2026-10-21 UTC · close chore · 4차원 확인)

- **AC-2 닫힘** — 위 AC 목록의 AC-2 칸은 review 시점 기록이라 빈 채로 둔다. impl PR #4453(head `e9b560150`) 의 체크: `Client graph server-only imports (the browser must not pull next/headers)` **pass** · `Client graph backend origins (the browser must not know the address)` **pass**(두 잡 모두 자체 시험 + 실 트리, `ci.yml:1902-1956`) · 전체 67 · pending 0 · fail 0. 실 트리 rc=0 · `reached` 변화는 § 구현에 기록.
- 4차원: (a) `gh pr view 4453` `state=MERGED` · mergedAt 2026-10-21T19:13:59Z · (b) `origin/main` tip = 스쿼시 `c3a3509ac` · (c) 머지 전 head 체크 실패 0 · (d) AC-0 · AC-1 · AC-2 전부 그 AC 의 동사대로 닫힘(AC-2 는 이 CORRECTION).
