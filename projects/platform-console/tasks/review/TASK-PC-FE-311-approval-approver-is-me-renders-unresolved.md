# Task ID

TASK-PC-FE-311

# Title

결재 화면의 결재자 칸이 **바로 나 자신**인데도 `이름 확인 불가` 로 그린다 — 직원 조회가 비면 «현재 로그인한 운영자인지» 를 한 번 더 본다

# Status

review

# Owner

platform-console

# Task Tags

- frontend
- erp
- demo

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 콘솔 화면 한 칸의 표시 보정. 서버가 이미 아는 «내 sub» 를 클라이언트 칸까지 내려 주는 배선이 본체.

---

# Dependency Markers

- 출처: `TASK-PC-FE-309`(review) 의 «🔴🔴 그런데 라이브 데모 시드는 계약을 어긴다» 절 — 결재함 2건의 결재자 칸이 `이름 확인 불가`.
- 관련(이 티켓이 정하지 않는 것): `TASK-MONO-746`(ADR-MONO-080 후보, «직원도 풀 계정») — 직원 마스터 ↔ IAM 계정 연결은 그 ADR 의 몫이다.
- 소유자 결정(2026-10-07 UTC): 시드에 «직원 id = 계정 id» 행을 넣는 안이 아니라 **화면 보정**으로 간다(시드가 아직 결정 안 된 모델을 몰래 정하지 않게).

# 배경 (2026-10-07 UTC, 코드 실측)

- 결재함 술어는 `approver_id = JWT sub`(erp approval-service `findInbox`). 그래서 데모 시드(`infra/demo/seed/seed-erp.sh` §6)는 결재자 자리에 **면접관 로그인 계정 `demo@demo.com` 의 토큰 `sub`**(`APPROVER_SUB="$(jwt_sub "$SEED_TOKEN")"`)를 넣는다 — 이것이 결재함을 채우는 유일한 방법이다.
- 콘솔 `features/erp-ops/components/approval-refs.tsx` 의 `ApprovalEmployeeRef` 는 그 id 를 `useEmployee`(→ `GET /api/erp/masterdata/employees/{id}`)로 푼다. 직원 마스터에는 그 id 가 없으므로 `masterRefLabel` 이 정직하게 `이름 확인 불가` 를 그린다 — 계약상 옳다.
- 그러나 **그 칸을 보는 사람이 바로 그 결재자**다(결재함은 정의상 «내가 결재자인 건»). 콘솔 서버는 운영자 세션 토큰을 갖고 있으므로 «내 sub» 를 안다 — 클라이언트 칸이 모를 뿐이다.

# Goal

직원 조회가 실패한 결재 참조 칸의 id 가 **현재 로그인한 운영자 자신의 sub** 와 같으면, `이름 확인 불가` 대신 «나(현재 운영자)» 처럼 사람이 읽을 수 있는 표기를 그린다. 그 외에는 지금과 같다.

# Scope

## In Scope

- 서버(세션 토큰을 읽는 곳)에서 «현재 운영자 sub» 를 얻어 결재 화면 클라이언트 칸까지 전달 — 기존 배선(레이아웃이 `AccountMenu` 에 넘기는 표시 신원, `shared/lib/jwt.ts`, `operator-context-types.ts` 등)을 먼저 찾아 재사용. 토큰 자체를 브라우저로 보내지 않는다(sub 문자열만).
- 🔴 AC-0 에서 **어느 토큰의 sub 가 시드의 `APPROVER_SUB` 와 같은 값인지** 확정(base access · operator · assumed 중) — `seed-erp.sh` 의 `SEED_TOKEN` 이 무엇인지, `ADR-MONO-060` 의 assume 토큰 `sub` 규칙을 읽고. 다른 토큰의 sub 와 비교하면 칸이 영원히 안 맞는다.
- `ApprovalEmployeeRef`(결재자 · 기안자 · 이력 처리자 · 대결 대상 — 같은 컴포넌트)에서: 직원 조회 결과 없음 **그리고** id === 내 sub → 보정 표기. 직원 조회가 성공하면 그쪽이 우선(보정은 폴백일 뿐).
- 표기 문구는 기존 화면 어휘에 맞춘다(예: `나 (현재 운영자)`). `title` 속성에는 원래 id 유지.
- 단위 테스트: ① 직원 있음 → 직원 이름 ② 직원 없음 + id=내 sub → 보정 표기 ③ 직원 없음 + 다른 id → `이름 확인 불가`(회귀) ④ 내 sub 를 모름(샘플 방문자 등) → `이름 확인 불가`.

## Out of Scope

- 직원 ↔ 계정 연결 모델(ADR-MONO-080 후보).
- 데모 시드 변경 · 백엔드 변경 · 기안자 칸(상신자 `requester@demo.com` 는 «나» 가 아니므로 그대로 `이름 확인 불가`).
- 다른 운영자의 sub 를 이름으로 푸는 디렉터리 조회.

# Acceptance Criteria

- [x] **AC-0** — 시드 `APPROVER_SUB` 와 같은 값을 내는 토큰을 코드로 확정해 표로 적는다(토큰 · sub 의 출처 · 근거 file:line). 샘플 방문자 모드에서 그 값이 없음을 확인.
- [x] **AC-1** — 위 Scope 의 보정 구현. 토큰은 브라우저로 안 나간다(전달되는 것은 sub 문자열뿐) — 근거를 적는다.
- [x] **AC-2** — 단위 테스트 4종(위) 초록 + 기존 `approval-refs`/`ApprovalDetail`/결재 목록 테스트 무회귀. tsc · lint · vitest rc=0.
- [x] **AC-3** — e2e/유닛에서 `이름 확인 불가` 를 리터럴로 기대하던 단언이 이 변경으로 의미가 바뀌는지 grep(`approval`) 하고, 바뀌면 그 이유를 적고 갱신.
- [ ] **AC-4** — (라이브, 다음 데모 창) `demo@demo.com` 로 결재함 2건의 결재자 칸이 보정 표기 — 머지 시점에는 ⚪.

---

# 🟢 착수 기록 (2026-10-07 UTC)

## AC-0 — 어느 토큰의 `sub` 가 시드 `APPROVER_SUB` 와 같은가

`infra/demo/seed/seed-erp.sh`:150 `APPROVER_SUB="$(jwt_sub "$SEED_TOKEN")"`,
`SEED_TOKEN="$(operator_token demo-corp)"`(:128) — `operator_token`
(`infra/demo/seed/lib.sh`:217~234)은 **base 로그인 토큰 → RFC 8693
assume-tenant(audience=demo-corp) 교환**의 결과(assumed 토큰)다.

| 토큰(콘솔 쪽 이름) | `sub` 의 출처 | 시드 `APPROVER_SUB` 와 같은가 | 근거 |
|---|---|---|---|
| **base 접근 토큰** — `getAccessToken()` (`shared/lib/session.ts`:164-167, 쿠키 `console_access_token`) | IAM 로그인(auth-service) 발급, `sub` = 계정 UUID | ✅ **같다** | `TenantClaimTokenCustomizer.java`:566-569 `alignSubToAccountId`(OVERRIDE `sub` = account UUID) + `AssumeTenantExchangeIntegrationTest.java`:471-474(`basePayload.get("sub")` ≡ `account`) |
| **assume/도메인향 토큰** — `getAssumedToken()`/`getDomainFacingToken()` (`session.ts`:214-239, 쿠키 `console_assumed_token`) | assume-tenant RFC 8693 교환, `ADR-MONO-060 A`(ACCEPTED) 가 `sub` 를 계정 UUID 로 정렬 | ✅ **같다**(base 와 동일 계정 UUID) | `TenantClaimTokenCustomizer.java`:609-617 `alignSubToSubjectAccount` + `AssumeTenantExchangeIntegrationTest.java`:228-233("MONO-515 (ADR-MONO-060 A): and the same sub") 및 :474-478(동일 테스트, 다른 account, 같은 단언) |
| **operator 토큰** — `getOperatorToken()` (admin-service 교환, 쿠키 `console_operator_token`) | 별도 RFC 8693 교환(`/api/admin/**` 전용) | **평가 대상 밖** — `/api/admin/**` 호출 전용이고 이 화면이 읽는 도메인향 호출과 무관(§ 2.6 경계, out of scope). 추측하지 않는다. | — |

⇒ **base 접근 토큰과 assume 토큰 둘 다** 시드의 `APPROVER_SUB` 와 같은
계정 UUID 를 낸다(`ADR-MONO-060 A` 가 둘을 일치시켰다 — Edge Case
"테넌트 전환 직후에도 내 sub 는 테넌트와 무관해야 한다" 가 코드로 성립).
**base 접근 토큰을 쓴다** — `(console)/layout.tsx` 의 `accountDisplayLabel`
(계정 메뉴 라벨)이 이미 쓰는 바로 그 토큰이라 "기존 배선 재사용"이고,
`isAuthenticated()` 게이트를 통과한 모든 요청에 **항상 존재**한다(assume
토큰은 운영자가 테넌트를 전환하기 전까지 부재 — `getDomainFacingToken()`
의 폴백 설계가 그 비대칭을 문서화한다).

**샘플 방문자 모드** — `isSampleVisitor()`(`session.ts`:297-303)는
`getAccessToken() === null` 를 요구한다. 그 상태에서 `getAccessToken()`
은 `null` 을 반환하므로 `getMyOperatorSub()`(`erp-state.ts`, 이 구현이
추가)도 `null` — "내 sub" 가 없다. 코드로 확인(별도 라이브 실측 불필요
— `isSampleVisitor` 의 정의 자체가 그 전제).

## AC-1 — 구현 + 토큰 비노출 근거

- `erp-state.ts` `getMyOperatorSub()`(신설, 작은 헬퍼) — `getAccessToken()`
  으로 얻은 **토큰**을 `decodeJwtPayload()`(`shared/lib/jwt.ts`, 기존
  — verification-free, "DISPLAY ONLY, never an authorization input" 로
  이미 문서화된 바로 그 함수) 로 디코드해 `sub` **문자열만** 추출해
  반환한다. 토큰 자체는 이 함수의 지역 변수에서 끝난다 — 반환값도,
  `ErpApprovalState.mySub`(string | null)도 전부 문자열이다.
- 흐름: `getErpApprovalState()`(서버, `erp-state.ts`) → `ErpApprovalState.mySub`
  → `ErpApprovalPage`(서버, `app/(console)/erp/approval/page.tsx`) →
  `<ErpApprovalScreen mySub={state.mySub} .../>` → `<ApprovalScreen mySub=.../>`
  → `<ApprovalDetail mySub=.../>` → 5곳의 `<ApprovalEmployeeRef mySub=.../>`
  (기안자 · 결재자(legacy) · 결재선 단계별 · 이력 처리자 · 대결 대상).
  전부 **클라이언트 컴포넌트 props** 로만 내려간다 — `fetch`/헤더/쿠키로
  브라우저에 보내는 경로가 없다(React 서버→클라이언트 props 직렬화는
  문자열 1개를 복사하는 것이고, 이 프로젝트의 다른 서버→클라이언트
  display-only 식별자 전달(`accountDisplayLabel` → `<AccountMenu
  accountLabel=.../>`)과 동일한 패턴).
- `ApprovalEmployeeRef`(`approval-refs.tsx`): 직원 조회(`useEmployee`)가
  **성공하면 항상 그 결과가 이긴다**(`!resolved` 가드) — 실제 직원으로
  등록된 결재자를 "나" 로 덮어쓰지 않는다(Failure Scenario 2 방지).
  로딩 중(`empQ.isLoading`)에는 판정을 보류한다(Edge Case — 결과 확정
  전에 보정을 먼저 그리지 않는다). 그 외 "직원 없음 **그리고**
  `employeeId === mySub`" 일 때만 `나 (현재 운영자)`
  (`APPROVAL_SELF_LABEL`, export 됨) — 그 외(`mySub` 없음 · 다른 id)는
  기존 그대로 `masterRefLabel` → `이름 확인 불가`(id 로 되돌아가지
  않는다는 기존 계약 보존).

## AC-2 — 단위 테스트 4종 + 회귀

`tests/unit/erp-master-ref-names.test.tsx` 에 새 describe
`"ERP 결재 — 결재자 칸이 «나 자신» 인 경우 (TASK-PC-FE-311)"` 4개를
추가(새 가드 파일을 짓지 않음, 기존 `renderApprovalDetail` 헬퍼에 `mySub`
파라미터만 추가):

1. ① 직원 있음(mySub 가 같아도) → 직원 이름이 이긴다.
2. ② 직원 없음 + id === mySub → `나 (현재 운영자)`.
3. ③ 직원 없음 + 다른 id(mySub 도 다름) → `이름 확인 불가`(회귀).
4. ④ mySub 없음(샘플 방문자 등) → `이름 확인 불가`.

4종 전부 통과 확인(verbose 리포터로 개별 이름 대조). 기존
`ApprovalScreen.test.tsx`/이 파일의 TASK-PC-FE-309 describe 들은 `mySub`
를 안 넘기므로(undefined) 보정 로직이 전혀 발화하지 않아 **그대로
회귀 없음** — `Boolean(undefined)` 가 `false` 라 `isMe` 가 항상
`false`.

검증: `npx tsc --noEmit` rc=0 · `pnpm lint`("No ESLint warnings or
errors") rc=0 · `npx vitest run --minWorkers 2 --maxWorkers 2` **338
files / 3818 tests 전부 초록, 0 회귀**(이 티켓이 `erp-master-ref-names.test.tsx`
에 더한 것은 정확히 새 describe 의 4개 `it` 뿐 — 파일 수는 이 티켓
착수 시점의 베이스(`origin/main`, 다른 머지된 작업 포함) 그대로다;
TASK-PC-FE-309 가 자신의 종료 시점에 적은 337/3812 는 그 이후 머지된
다른 티켓들이 올린 수치라 이 비교의 기준이 아니다). `erp-state.ts` 의
`getMyOperatorSub()` 는 기존 `erp-state.test.ts` 의 쿠키 모의
(`ACCESS_COOKIE='GAP-ACCESS'`, 점 없는 문자열 → `decodeJwtPayload` 가
`null`)와 충돌하지 않는다(그 테스트들은 `mySub` 필드를 단언하지 않는다).

## AC-3 — `이름 확인 불가` 리터럴 단언 grep(`approval`)

`tests/unit/`·`tests/e2e/` 전체에서 `이름 확인 불가`/`MASTER_REF_UNRESOLVED`
를 그렙한 결과, approval 관련 단언은 `erp-master-ref-names.test.tsx`
(이 티켓이 직접 갱신한 파일)와 `features/erp-ops/ApprovalScreen.test.tsx`
뿐이었다. 후자의 모든 `<ApprovalDetail>`/`<ApprovalScreen>` 렌더 호출은
`mySub` 를 넘기지 않는다(그 파일은 상태기계/전이를 재는 스위트이고 이
티켓의 범위 밖) — `mySub` 가 `undefined` 이면 보정이 전혀 발화하지
않으므로 **의미가 바뀌지 않는다**. e2e(`tests/e2e/overview-consolidation.spec.ts`)
는 `approval` 을 nav href 비교에만 쓸 뿐 `이름 확인 불가` 단언이 없다
— 영향 없음. ⇒ 갱신이 필요한 기존 단언은 **0건**.

## AC-4 — ⚪ 라이브 확인(다음 데모 창)

`demo@demo.com` 로 로그인해 결재함 2건의 결재자 칸이 실제로
`나 (현재 운영자)` 로 그려지는지는 이 세션에서 콘솔을 띄워 측정하지
않았다. AC-0~AC-3 는 코드(세션/토큰/JWT 커스터마이저/통합테스트
단언) + 신규 단위 테스트로 닫았다 — 오케스트레이터가 다음 라이브
데모 창에서 측정.

## 파일 변경 (요약)

- `features/erp-ops/api/erp-state.ts` — `getMyOperatorSub()` 신설,
  `ErpApprovalState.mySub` 추가.
- `app/(console)/erp/approval/page.tsx` — `state.mySub` → `ErpApprovalScreen`.
- `features/erp-ops/components/ErpApprovalScreen.tsx` /
  `ApprovalScreen.tsx` / `ApprovalDetail.tsx` — `mySub` prop 중계(5곳).
- `features/erp-ops/components/approval-refs.tsx` — `ApprovalEmployeeRef`
  보정 로직 + `APPROVAL_SELF_LABEL` export.
- `tests/unit/erp-master-ref-names.test.tsx` — 단위 테스트 4종 추가.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` (서버/클라이언트 경계 · 토큰 비노출)
- `TASK-PC-FE-276` · `277` · `309` (`masterRefLabel` 계약 — id 로 되돌아가지 않는다)

# Related Contracts

- `projects/erp-platform/specs/contracts/http/approval-api.md` — 읽기만(approverId 는 계약상 employee id; 데모 시드가 sub 를 넣는 편법은 그대로).

# Edge Cases

- 테넌트 전환 직후: 내 sub 는 테넌트와 무관해야 한다 — AC-0 에서 assume 토큰과 base 토큰의 sub 가 같은지 확인.
- 직원 조회가 아직 로딩 중일 때 보정 표기를 먼저 그리지 않는다(로딩 → 결과 확정 뒤 판정).

# Failure Scenarios

1. **잘못된 토큰의 sub 와 비교** — 보정이 영원히 안 걸려 «고쳤는데 그대로» (AC-0 이 막는다).
2. **보정이 직원 조회보다 앞선다** — 실제 직원으로 등록된 결재자가 «나» 로 덮인다(테스트 ①).
3. **토큰 자체를 클라이언트로 내린다** — 보안 회귀(AC-1).
