# Task ID

TASK-PC-FE-150

# Title

console-web erp-ops `DelegationScreen.tsx`(466줄) behavior-preserving 분할 — 화면 상태/뮤테이션을 커스텀 훅(`use-delegation-screen.ts`)으로, 반복되는 두 grant 리스트 블록을 presentational `DelegationGrantList`로 추출(container/presentational + hook 패턴)

# Status

review

# Owner

frontend (Opus 4.8 분석·구현 — behavior-preserving 모듈 분할; contract/spec/backend 무변경)

# Task Tags

- code
- refactor
- test

---

# Dependency Markers

- **builds on**: TASK-PC-FE-054(위임(대결) 관리 화면 도입 — § 2.4.8 delegation grant management). 본 task는 그 위에서 **렌더 트리·로직 동일**, 모듈 경계만 재편한다.
- **note (현 구조)**: `DelegationScreen.tsx`(466줄, `'use client'`)는 한 파일에 (a) `newIdemKey`/`fmt`/`periodText` 헬퍼, (b) `DelegationStatusBadge`, (c) DELEGATOR/DELEGATE 두 리스트 블록(헤딩·로딩/빈/에러·행 렌더가 거의 동일, primary id 와 revoke 액션 유무만 상이), (d) `DelegationScreen` container(2 query + 2 dialog open-state), (e) `DelegationCreateDialog`(폼 4필드 + create 뮤테이션), (f) `DelegationRevokeDialog`(reason-gated + revoke 뮤테이션)를 모두 보유한 god-file이다.

# Goal

`DelegationScreen.tsx`를 behavior-preserving 분할한다. 화면/다이얼로그의 상태·뮤테이션·핸들러·`newIdemKey`를 커스텀 훅으로, 반복되는 두 grant 리스트 표현덩어리를 presentational 컴포넌트로 추출해 god-file을 container/presentational + hook 구조로 재편한다. 렌더 출력·DOM·모든 `data-testid`·ARIA·class·label·wire body·뮤테이션 시그니처·검증 술어는 byte-identical.

# Scope

## In Scope

- **신규 `src/features/erp-ops/components/use-delegation-screen.ts`** (`'use client'`):
  - `newIdemKey()`(export) — 기존 헬퍼 verbatim 이동.
  - `useDelegationScreen()` — 2개 query(`useDelegations('DELEGATOR'|'DELEGATE')`) + create/revoke open-state + grant 배열 파생.
  - `useDelegationCreate(onClose)` — create 다이얼로그 폼 state(delegateId/validFrom/validTo/reason) + `canConfirm` 술어 + `onConfirm`(create 뮤테이션 + onSuccess close).
  - `useDelegationRevoke(grant, onClose)` — revoke reason state + `ok` 술어 + `onConfirm`(revoke 뮤테이션).
- **신규 `src/features/erp-ops/components/DelegationGrantList.tsx`** (`'use client'`):
  - `DelegationStatusBadge`(export) + `periodText`/`fmt` 헬퍼 이동.
  - presentational `DelegationGrantList` — 한 리스트(헤딩 + 로딩/빈/에러 + 행: status badge·period·조건부 revoke). DELEGATOR 는 `onRevoke` 전달(회수 버튼 노출), DELEGATE 는 미전달(회수 없음). primary id 는 `idField`(`delegateId`|`delegatorId`)로 분기.
- **`src/features/erp-ops/components/DelegationScreen.tsx`** — 훅 + presentational 리스트 소비로 재작성. 두 dialog 는 동일 파일에 유지(훅만 소비). 공개 export `DelegationScreen` named 유지(feature barrel `index.ts` 경로 안정).

## Out of Scope

- delegation read/write API·hook(`use-erp-ops.ts`)·types(`delegation-types.ts`) 변경.
- DELEGATOR-only revoke 정책·status badge 규칙·idem-key 생성 로직 변경.
- 다른 erp-ops 컴포넌트.

# Acceptance Criteria

- [x] 두 리스트(내가 위임한 / 나에게 위임된)·헤딩·status badge(활성/회수됨/만료)·period("무기한" 포함)·DELEGATOR-only 회수 버튼이 기존과 동일 렌더(behavior-preserving).
- [x] create 다이얼로그: 필수필드(delegateId+validFrom) gate, submit 시 trimmed body + `Idempotency-Key`로 POST, 성공 시 close — 기존 wire 불변.
- [x] revoke 다이얼로그: reason-required gate, submit 시 reason + key로 revoke — 기존 동작 불변.
- [x] delegation 에러코드 inline 매핑(`approvalErrorMessage`) 무변경, 모든 `data-testid`(`delegation-*`) 보존.
- [x] `npx tsc --noEmit` clean / `npx next lint` clean / `npx vitest run delegation` green(무회귀). scope = console-web only.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.8(delegation grant management) — read-only 소비, 변경 없음.

# Related Contracts

- 변경 없음. 동일 delegation read/write 계약·동일 클라이언트 훅·동일 호출.

# Target Service

- `platform-console` / `apps/console-web` — `src/features/erp-ops/components/{DelegationScreen.tsx, use-delegation-screen.ts(신규), DelegationGrantList.tsx(신규)}`. behavior-preserving 모듈 분할.

# Architecture

- Next.js App Router `'use client'` 컴포넌트의 container/presentational + 커스텀 훅 분할 패턴. 상태 소유권은 훅에 두고 presentational `DelegationGrantList`는 표현 전용(props로 query/grants/idField/onRevoke 수령). 두 리스트의 byte-identical DOM 차이(wrapper testid·heading·primary id·revoke 유무)만 prop 으로 매개변수화.

# Edge Cases

- DELEGATE 리스트는 `onRevoke` 미전달 → 행 우측 `<div className="ml-2">`(회수 미노출) 분기. DELEGATOR 는 `<div className="ml-2 flex items-center gap-2">` + ACTIVE 일 때만 `<Button>` 회수 — 원본과 동일 DOM.
- 회수 버튼은 `Button variant="secondary"` 컴포넌트 유지(plain `<button>` 아님) — class/렌더 동일.
- 빈/로딩/에러 상태의 문구·`role="status"`·`data-testid="delegation-error"` 모두 보존.

# Failure Scenarios

- presentational 분할 시 회수 버튼을 `<button>`으로 잘못 치환하면 class/DOM 회귀 → `Button` 컴포넌트 유지로 가드, vitest 회귀 테스트(회수 버튼 노출/클릭)로 검증.
- 훅으로 state 를 올리며 onSuccess close 누락 시 다이얼로그가 안 닫힘 → 기존 `{ onSuccess: () => onClose() }` verbatim 유지.
- lint(no-unused-vars: 추출 후 잔여 import) / tsc(prop 타입) RED → push 전 3종 게이트 필수.

# Definition of Done

- [x] `use-delegation-screen.ts` + `DelegationGrantList.tsx` 추출, `DelegationScreen.tsx` 훅/presentational 소비로 재작성
- [x] 렌더 출력·DOM·data-testid·ARIA·wire·검증 술어 behavior-preserving
- [x] vitest(delegation) + tsc + lint green, 무회귀; scope = console-web only
- [x] 공개 export 표면(`DelegationScreen` via feature barrel) 불변
- [x] Acceptance Criteria 충족
- [x] Ready for review
