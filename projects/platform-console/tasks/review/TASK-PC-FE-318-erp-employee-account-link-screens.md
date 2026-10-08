# Task ID

TASK-PC-FE-318

# Title

erp 직원 ↔ 계정 연결 화면 다섯 — 직원 «연결된 계정» · 연결 제안 · 내 앞 제안 수락/거절 · 결재선 선택기 «연결된 계정 없음» · 결재함 빈 이유 + 콘솔 계약 보정 + `TASK-PC-FE-311` 보정 걷기 (`TASK-MONO-774` S4)

# Status

review

# Owner

platform-console

# Task Tags

- console-web
- frontend
- erp

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 백엔드 판정(`TASK-ERP-BE-044` · `TASK-MONO-776`) 위의 화면 · BFF 프록시 · 계약 행. 🔴 결재선 입력을 선택기로 바꾸는 부분만 조심(아래 AC-4).

---

# Dependency Markers

- **선행**: 루트 `TASK-MONO-776` **`done/`** (approval v2.4 · inbox `meta.actorEmployeeId` · 데모 시드 연결). 그 선행인 `projects/erp-platform` `TASK-ERP-BE-044`(masterdata 연결 표면)도 따라서 `done/`.
  - 🔵 **2026-10-07 UTC 갱신 (소유자 결정, AC-0)** — S4 가 실제로 필요로 하는 선행은 **머지된 코드**다: `TASK-MONO-776` PR #4234 squash `64d6070e6` · `TASK-ERP-BE-044` PR #4231 squash `836a122d9`. 776 은 `review/` 에 있고 남은 항목은 라이브 데모 시드 확인(AC-8)뿐이며, 그 `done/` 은 재굽기 창을 기다린다. 그래서 이 티켓은 776 의 `done/` 을 기다리지 않고 착수한다.
- 관련: `TASK-PC-FE-311`(«나 (현재 운영자)» 보정 — 이 티켓이 걷는다; 착수 시 그 티켓의 상태를 본다 — 지금 `review/`) · `TASK-PC-FE-309`(«이름 확인 불가») · `TASK-PC-FE-047`(FK 는 원시 UUID 입력이 아니라 선택기).
- 상위: 루트 `TASK-MONO-774`(우산 · `ADR-MONO-080` D7 = E1).
- 🔴 동시 작업 경계: `features/operator-groups/**` · `shared/api/iam-operators-types.ts` 는 다른 세션 소유였다(2026-10-08) — 연결 제안의 «계정 고르기» 가 운영자 목록을 재사용한다면 착수 시 그 파일들의 현재 주인을 확인한다.

# Goal

소유자 결정(2026-10-08 UTC) — **인사 제안 + 본인 수락** · **미연결 승인자 = 상신 거절, 선택기가 «연결된 계정 없음» 을 표시** — 을 콘솔에서 쓸 수 있게 하고, 계정 `sub` 를 승인자에 넣던 시절의 보정(`TASK-PC-FE-311`)을 걷는다.

# Scope

## In Scope

1. **직원 목록/상세 «연결된 계정»** — `features/erp-ops/components/EmployeeList.tsx` · `EmployeeDetail.tsx` · `api/types/employee.ts`(`accountId?`). 연결 없음 = «연결된 계정 없음». 원시 UUID 를 그대로 찍지 않는다(`TASK-PC-FE-309` 규율 — 표시 이름 해소 방식은 AC-0).
2. **연결 제안**(`erp.write` 보유자) — 직원 상세에서 계정을 골라 제안 · 대기 제안 표시 · 철회 · 연결 해제. BFF 프록시: `app/api/erp/masterdata/employees/[id]/account-link-proposals/route.ts` 등(기존 `app/api/erp/_proxy.ts` 경로 · `Idempotency-Key` 규율).
3. **내 앞 제안 수락/거절** — `GET /account-link-proposals/mine` 을 보여주는 표면(위치는 AC-0: `/erp` 개요 카드 또는 `/erp/masters` 직원 탭 상단). 수락 · 거절 버튼, `EMPLOYEE_LINK_SELF_ACCEPT`(«제안한 사람이 수락할 수 없습니다») · `EMPLOYEE_LINK_NOT_ADDRESSEE` 오류 문구.
4. **결재선 선택기** — 지금 결재선은 **원시 id 텍스트 입력**이다(`ApprovalCreateDialog.tsx:167-178`, placeholder `emp-…`). 직원 선택기로 바꾸고 `accountId` 없는 직원을 «연결된 계정 없음» 으로 표시(선택은 막지 않는다 — 상신 시 `APPROVAL_APPROVER_UNLINKED` 를 화면이 사람 말로 보인다).
5. **결재함 빈 이유** — inbox `meta.actorEmployeeId` 가 ABSENT 이면 «내 계정이 직원과 연결되지 않아 결재함이 비어 있습니다» 를 «처리할 결재가 없습니다» 와 갈라 말한다. `APPROVAL_ACTOR_NOT_LINKED` 문구.
6. **`TASK-PC-FE-311` 보정 걷기** — `approval-refs.tsx:70-79`(`APPROVAL_SELF_LABEL`) · `ApprovalScreen.tsx:65` · `ApprovalDetail.tsx:34` · `ErpApprovalScreen.tsx:19` · `api/erp-state.ts:32, 243` · 테스트 `tests/unit/erp-master-ref-names.test.tsx`. 승인자가 이제 직원 id 이므로 직원 조회로 이름이 나온다 — 보정이 필요 없어졌는지 먼저 확인하고(AC-0) 걷는다.
7. **콘솔 계약 보정** — `specs/contracts/console-integration-contract.md` § 2.4.8: 마스터 쓰기 행렬(`:1960-1979`)에 **없는** 연결 다섯 동작(제안 · 수락 · 거절 · 철회 · 해제) 행 추가 — 🔴 수락 · 거절은 `erp.write` 가 아니라 «계정 주인» 이 하는 쓰기라는 점을 명시. 같은 PR 에서 **`projects/erp-platform/specs/integration/iam-integration.md:120`** 의 낡은 문장(«write/mutation 표면 … 콘솔이 소비하지 않는다») 을 고친다 — 콘솔은 이미 다섯 마스터를 쓴다(직원 BFF `app/api/erp/masterdata/employees/route.ts:30` · `[id]/route.ts:36` · `[id]/retire/route.ts:15`).

## Out of Scope

- 백엔드 — `TASK-ERP-BE-044` · `TASK-MONO-776`.
- 연결 상태의 실시간 갱신(폴링 · 푸시) — 화면 새로고침으로 충분.

# Acceptance Criteria

- [x] **AC-0** — 재측정(위 file:line 전부) + 판정 셋: ① 제안의 «계정 고르기» 원천(테넌트 운영자 목록 → 계정 UUID; 그 목록 API 와 소유 경계) ② 수락 표면 위치 ③ `TASK-PC-FE-311` 의 상태(`review/` → `done/` 됐는가)와 보정 코드가 실제로 불필요해졌는지(직원 id 승인자로 이름이 나오는가). → § AC-0 기록.
- [x] **AC-1** — 직원 목록/상세가 연결 계정 / «연결된 계정 없음» 을 보인다(단위 테스트, 두 상태). → § 구현 기록 AC 표.
- [x] **AC-2** — 제안 → (다른 계정으로) 수락 흐름이 BFF 를 거쳐 동작한다; 같은 계정 수락은 `EMPLOYEE_LINK_SELF_ACCEPT` 문구로 끝난다(단위 · BFF 라우트 테스트).
- [x] **AC-3** — 결재함: `meta.actorEmployeeId` ABSENT ↔ 있음·0건 이 **다른 문구**를 낸다(단위).
- [x] **AC-4** — 결재선 선택기가 미연결 직원을 «연결된 계정 없음» 으로 표시하고, 그 직원으로 상신 시 `APPROVAL_APPROVER_UNLINKED` 를 사람 말로 보인다. 🔴 `data-testid="approval-create-approver-${idx}"` 를 바꾸면 e2e/단위 스펙을 grep 해 함께 고친다(`tests/e2e` · `tests/unit` · nightly e2e 스펙 디렉터리). → testid 유지(요소만 `<input>` → `<select>`), e2e 0건 · 단위 스펙 갱신.
- [x] **AC-5** — `TASK-PC-FE-311` 보정 코드 제거 뒤에도 승인자 이름이 보인다(단위). 🔵 라이브(재굽기된 데모에서 결재함 · 이름) ⚪ 재굽기 창.
- [x] **AC-6** — 콘솔 계약 § 2.4.8 행 추가 + `iam-integration.md:120` 정정이 같은 PR 에 있다.
- [x] **AC-7** — `npx tsc --noEmit` · `npm run lint` · `npx vitest run`(전체) 각각 rc 기록. → § 구현 기록 «검증».

# Related Specs

- `docs/adr/ADR-MONO-080-workforce-on-the-consumer-pool.md` § D7
- 루트 `tasks/in-progress/TASK-MONO-774-erp-employee-account-link.md` § AC-0 (소유자 결정 원문 · 실측 6 «콘솔은 이미 마스터를 쓴다»)
- `projects/platform-console/specs/` 의 erp 화면 스펙(있으면)

# Related Contracts

- `projects/erp-platform/specs/contracts/http/masterdata-api.md` § Employee ↔ IAM account link
- `projects/erp-platform/specs/contracts/http/approval-api.md` § v2.4 (`APPROVAL_APPROVER_UNLINKED` · `APPROVAL_ACTOR_NOT_LINKED` · `meta.actorEmployeeId`)
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.8
- `projects/erp-platform/specs/integration/iam-integration.md` § platform-console Operator Read Consumer

# Edge Cases

- 결재 진행 중 승인자 직원의 연결이 해제됨 — 그 건은 누구 결재함에도 안 보인다(상신 시점 검사만). 상세 화면이 «현재 단계 승인자: 연결된 계정 없음» 을 보인다.
- 퇴사 직원과 그 연결 — 연결은 남고 선택기에서 비활성 직원은 기존 규칙대로 숨거나 표시(기존 FK 선택기 규율 따름).
- 내 앞 제안이 여럿(다른 테넌트는 토큰이 달라 안 보인다 — 한 테넌트 안에서도 직원 여럿 제안 가능, 하나만 수락 가능).

# Failure Scenarios

1. 311 보정을 걷기 전에 이름 해소가 직원 id 로 되는지 안 본다 — 이름이 «확인 불가» 로 퇴행한다(AC-0 ③ · AC-5).
2. 결재선 testid 를 바꾸고 nightly 전용 e2e 를 안 본다 — `ci.yml` 은 초록, 다음 nightly 가 빨강.
3. 결재함 빈 이유를 한 문구로 접는다 — «연결 안 됨» 이 «할 일 없음» 으로 읽혀 운영자가 원인을 못 찾는다.

---

# AC-0 기록 (2026-10-07 UTC · 기준 `origin/main` `64d6070e6`)

## 소유자 결정 — 착수 시점 (2026-10-07 UTC, 오케스트레이터 경유 — 원문 그대로)

> OWNER DECISION: start now although TASK-MONO-776 is still in `review/` (its only open item is the live demo seed check AC-8). The dependency S4 actually needs is the merged code (PR #4234 squash `64d6070e6`, PR #4231 `836a122d9`); `done/` waits for the rebake window.

반영: § Dependency Markers 의 «선행» 줄 아래에 갱신 줄을 달았다.

## 재측정 — 티켓이 인용한 file:line (`64d6070e6`)

| 인용 | 지금 그 자리 | 판정 |
|---|---|---|
| `approval-refs.tsx:70-79` (`APPROVAL_SELF_LABEL`) | 상수 `:71`, 보정 컴포넌트 `ApprovalEmployeeRef` `:98-125`(`isMe` 판정 `:112-116`) | ✅ |
| `ApprovalScreen.tsx:65` | `mySub` prop 선언 `:65-67`, `ApprovalDetail` 로 넘김 `:241` | ✅ |
| `ApprovalDetail.tsx:34` | `mySub` prop `:34-36`, `ApprovalEmployeeRef` 4곳 `:97-101 · :164-168 · :191-195 · :227-231 · :245-249` | ✅ |
| `ErpApprovalScreen.tsx:19` | `mySub` prop `:18-23` | ✅ |
| `api/erp-state.ts:32, 243` | `getMyOperatorSub` `:57-61`(주석 `:31-56`), `ErpApprovalState.mySub` `:242-245` | ✅ |
| `tests/unit/erp-master-ref-names.test.tsx` | 311 보정 단언 4종 + 309 단언 | ✅ |
| `ApprovalCreateDialog.tsx:167-178` | 결재선 원시 텍스트 입력 `:172-178`, testid `approval-create-approver-${idx}` | ✅ |
| 콘솔 계약 `:1960-1979` | § 2.4.8 «Masterdata write binding» `:1954-2015`, 행렬 `:1974-1979` | ✅ (±6줄) |
| `iam-integration.md:120` | «Read-only (write/mutation 미소비)» 문장 그대로 | ✅ |
| 직원 BFF `employees/route.ts:30` · `[id]/route.ts:36` · `[id]/retire/route.ts:15` | POST 셋 그대로 | ✅ |

## 판정 ① — 제안의 «계정 고르기» 원천

- **테넌트 운영자 목록은 원천이 될 수 없다** — `GET /api/admin/operators` 의 행(`OperatorSummarySchema`, `shared/api/iam-operators-types.ts:50-72`)은 `operatorId` 를 싣고 **계정 UUID(JWT `sub`)를 싣지 않는다**(운영자 ≠ 계정 — 운영자는 `oidc_subject` 로 계정을 가리키지만 목록 응답에 그 칸이 없다). 그 목록으로 고르면 제안의 `accountId` 를 채울 수 없다.
- IAM 계정 단건 조회(`GET /api/admin/accounts?email=`, `shared/api/iam-accounts-read.ts:129-157`)는 계정 UUID 를 준다. 그러나 ⓐ 그 호출은 IAM `account.*` 권한을 요구하고 erp 인사 권한(`erp.write`)과 **다른 권한**이다 ⓑ 그 프로필은 `forbiddenMode: 'auth'`(`:99`) — 403 을 **재로그인**으로 다룬다. erp 인사 담당자가 IAM 계정 권한이 없으면 «계정 찾기» 한 번이 세션을 끊는다. 콘솔 계약에도 erp 화면이 IAM 계정 검색을 부르는 행이 없다.
- ⇒ **판정: 계정 ID 직접 입력**(형식만 — 비어 있지 않음 · 64자 이하, 계약 `masterdata-api.md:349-353` 이 제안 시점에 존재를 확인하지 않는다고 정한 그대로). 화면이 «존재 여부는 확인하지 않으며 계정 주인이 수락해야 연결된다» 를 함께 말한다(두 사람 규칙이 안전장치). 🔵 `TASK-PC-FE-047`(FK 는 선택기) 규율은 **erp 마스터 사이 FK** 에 대한 것이고, 계정은 erp 밖 컨텍스트라 erp 쪽에 고를 목록이 없다. 후속 후보: «이메일로 계정 찾기» 를 `erp.write` 보유자가 쓸 수 있는 조회가 생기면 선택기로 바꾼다.
- 표시(`TASK-PC-FE-309` 규율): 목록 · 상세는 원시 UUID 를 찍지 않는다 — **«연결됨» / «연결된 계정 없음»**, 계정 ID 는 `title` 속성으로만. 연결 관리 창에서만 «계정 ID» 를 식별자 칸(`<code>`)으로 보인다 — 인사 담당자가 «어느 계정에 제안했는지» 를 확인해야 하는 자리라서.
- 소유 경계: 이 판정으로 `features/operator-groups/**` · `shared/api/iam-operators-types.ts` 는 **건드리지 않는다**. 실측: `iam-operators-types.ts` 최근 커밋 `07328d826 2026-10-08 TASK-BE-626/TASK-PC-FE-319 (#4222)` · `8706986b8 2026-10-07 TASK-PC-FE-317 (#4218)` — 둘 다 머지됨, `gh pr list --state open` = **0건**(2026-10-07 UTC 측정).

## 판정 ② — 수락 표면 위치

- **`/erp` 개요 화면에 카드로**(개요 화면 컴포넌트 밖, 같은 페이지에 붙인다). 이유: `/erp/masters` 는 다섯 마스터 목록이 함께 성공해야 서는 화면이라(`erp-state.ts` `getErpMastersState` 의 `Promise.all` — 하나라도 403 이면 화면 전체 `forbidden`) 부서 scope 가 좁은 직원은 그 화면을 못 볼 수 있다. 수락자는 인사 권한자가 아니라 **계정 주인**(`erp.read` 이상, data scope 없음 — 계약 `:393-394`)이므로 칸마다 따로 무너지는 개요가 맞다. 카드는 자기 실패만 자기 자리에서 말한다.

## 판정 ③ — `TASK-PC-FE-311` 상태 · 보정이 불필요해졌는가

- 상태: `projects/platform-console/tasks/review/TASK-PC-FE-311-…md` — **`review/`**(AC-4 라이브 ⚪ 만 열림). 이 티켓이 그 보정 코드를 걷어도 311 의 닫힘 판정(코드가 머지됐다 · 라이브 ⚪)은 바뀌지 않는다 — 311 은 «그때의 데모 시드 편법 위의 화면 보정» 을 닫은 것이고, 그 편법(승인자 칸의 계정 `sub`)은 `TASK-MONO-776` 이 없앴다.
- 보정이 발화하는 조건은 «직원 조회 실패 **그리고** id === 내 `sub`» 하나다(`approval-refs.tsx:112-116`). v2.4 이후 사람 칸은 전부 **직원 id**(`approval-api.md` § v2.4 «직원 id 인 칸 (전부)»)이고, 직원 id 는 UUIDv7 로 새로 만들어진 값이라 어떤 계정 `sub` 와도 같지 않다 ⇒ **새 데이터에서 보정은 영원히 발화하지 않는다**(죽은 분기).
- 이름 해소: 승인자 칸은 `useEmployee(id)` → `GET /employees/{id}` 로 이미 풀린다(309 의 Method A). 승인자가 직원 id 이면 이 경로가 이름을 낸다 — AC-5 단위 테스트가 «보정 코드 없이 승인자 이름이 보인다» 를 고정한다.
- 🔵 남는 차이(적어 둔다): ⓐ 이 PR 이전에 만들어진 행(사람 칸에 계정 UUID)은 테넌트 전체 목록에서 `이름 확인 불가` 로 보인다 — 계약 § v2.4 «이전 데이터» 가 정한 결과이고 데모는 재시드된다. ⓑ `GET /employees/{id}` 는 부서 data scope 를 건다 — scope 가 좁은 운영자에게 상위 부서 승인자는 지금도(311 전후 무관) `이름 확인 불가` 다. 바꾸지 않는다.
- **대체**: «나» 표시는 `meta.actorEmployeeId`(결재함 응답)로 한다 — 직원 칸의 id 가 내 직원 id 와 같으면 이름 뒤에 «(나)». 토큰 디코드(`getMyOperatorSub`)는 더 필요 없다.

## e2e 스펙 grep (testid · 제목 변경 전)

- `approval-create-approver-` · `approval-inbox-empty` · `APPROVAL_SELF_LABEL` · `나 (현재 운영자)` · `mySub` — `apps/console-web/e2e-smoke/**` · `apps/console-web/tests/e2e/**` · 루트 `tests/federation-hardening-e2e/**` 에서 **0건**.
- 술어가 비어 있지 않다는 대조: 같은 디렉터리에서 `approval|erp-employee|nav-erp` = `e2e-smoke/sample-visitor-erp.spec.ts` 4건(`approval-screen` testid — 이 PR 이 바꾸지 않는다) · `tests/e2e/overview-consolidation.spec.ts` 11건(`nav-erp*` — 바꾸지 않는다). 루트 `tests/federation-hardening-e2e/**` 는 `HOST-PORTS.md` 1건(문서).

---

# 구현 기록 (2026-10-07 UTC)

## 한 PR 에 들어왔다 (분할 안 함)

다섯 표면 + 계약 + 311 걷기가 한 erp-ops 피처 안에서 끝났다(새 피처 · 다른 피처 import 0). 공유 파일 변경은 `details` 전달 한 줄씩(아래 «공유 변경») 뿐이다.

## 무엇을 바꿨나

- **계약**: `console-integration-contract.md` § 2.4.8 «Employee ↔ IAM account link binding» 신설 — 다섯 동작 행렬(누가 · 프록시 · 업스트림 · `Idempotency-Key` · `reason`), 🔴 수락 · 거절 = «계정 주인» 쓰기(`erp.write` 아님), 계정 고르기 판정, 오류 문구 의무, 샘플 방문자, approval v2.4 소비 의무(`meta.actorEmployeeId` · 새 코드 · 503 문구). `erp-platform/specs/integration/iam-integration.md:110, :120` 정정(«read-only / write 미소비» → 쓰기 소비 + 근거 경로).
- **BFF 프록시 4 파일**(`app/api/erp/masterdata/…`): `employees/[id]/account-link-proposals`(GET 이력 · POST 제안) · `employees/[id]/account-link/unlink`(POST) · `account-link-proposals/mine`(GET) · `account-link-proposals/[proposalId]/[action]`(POST, `accept|decline|revoke` 허용 목록 — 그 외 404, 상위 호출 없음). 모든 쓰기 `Idempotency-Key`(생산자 컨트롤러가 다섯 다 `@RequestHeader("Idempotency-Key")` 를 요구 — 계약 본문은 decline/revoke/unlink 에 헤더를 적지 않았지만 코드가 권위), 철회 · 해제는 사유 필수 사전 거절.
- **서버 api**: `api/masters/account-link-api.ts`(7 함수, `callErp`). **훅**: `hooks/masters/use-account-link.ts`(쓰기 성공 시 `[ERP_KEY, 'employees']` 무효화 — 목록 «연결된 계정» 칸도 함께 갱신).
- **화면**: ① `EmployeeList` «연결된 계정» 열 + (쓰기 가능 시) 행마다 «계정 연결» → `EmployeeAccountLinkDialog`(제안 · 대기 제안 철회 · 연결 해제 · 지난 제안) ② `EmployeeDetail` «연결된 계정» 줄 ③ `/erp` 개요 페이지에 `MyAccountLinkProposalsCard`(수락 · 거절) ④ `ApprovalCreateDialog` 결재선 = 직원 선택기(미연결 «연결된 계정 없음» 표시 · 선택은 막지 않음 · 고르면 행 경고 · 직원 목록 실패 시 같은 testid 의 직원 ID 입력으로 폴백) ⑤ `ApprovalScreen` 결재함 빈 이유 두 문구 + 결재함 오류를 «빈 결재함» 으로 접지 않는 오류 줄.
- **311 걷기**: `APPROVAL_SELF_LABEL` · `mySub` prop 사슬(`erp-state.ts getMyOperatorSub` → 페이지 → `ErpApprovalScreen` → `ApprovalScreen` → `ApprovalDetail` → `ApprovalEmployeeRef`) 제거. 대체 = `meta.actorEmployeeId` → `myEmployeeId` → 이름 뒤 «(나)». 결재자 칸(단계 · legacy)은 조회된 직원에 `accountId` 가 없으면 «· 연결된 계정 없음»(Edge Case «현재 단계 승인자: 연결된 계정 없음»).
- **오류 문구**: `approval-error.ts` — `APPROVAL_ACTOR_NOT_LINKED` · `APPROVAL_APPROVER_UNLINKED`(`details.stageIndex` → «N단계 결재자») · `APPROVAL_ROUTE_INVALID` cause 별(`self_approval` · `approver_unresolved` · `subject_unresolved` · `duplicate_stage_approver`) · `DELEGATION_INVALID delegate_unresolved` · 🔴 503 은 클라이언트에서 `ApiError(503)` 로 오므로(서버 `ErpUnavailableError` 가 아니다) 상태로도 잡아 «일시적으로 사용할 수 없습니다» — 이전에는 생산자 `message`("erp unavailable")가 그대로 나갔다. 새 `account-link-error.ts` — 링크 코드마다(+ `EMPLOYEE_LINK_CONFLICT` cause 다섯) 다른 문장, `EMPLOYEE_LINK_SELF_ACCEPT` 는 제안 쪽 · 수락 쪽 문구가 다르다.
- **샘플**: `fixtures/erp.ts` — 직원 셋에 `accountId`, `emp-sample-0004` 는 미연결(그 직원이 `appr-sample-0002` 의 현재 단계 승인자 → Edge Case 가 샘플에서 보인다), 결재함 `meta.actorEmployeeId = ME`, 링크 두 GET 은 빈 페이지(새 라벨 키 없음). 쓰기는 기존 라우터 규율 그대로 `403 SAMPLE_READ_ONLY`(버튼은 보인다).

## 공유 변경 (적어 둔다)

`EMPLOYEE_LINK_CONFLICT` 의 `details.cause` · `APPROVAL_APPROVER_UNLINKED` 의 `details.stageIndex` 가 화면까지 오려면 세 층이 `details` 를 버리지 않아야 했다 — 지금까지는 셋 다 버렸다:
- `shared/api/errors.ts` `ApiError` 에 선택 5번째 인자 `details` + 읽기 헬퍼 `errorDetail`.
- `shared/api/flat-envelope-gateway.ts` 비-ok 분기(400/404/409/422…)에서 `details` 를 실어 던진다(로그에는 안 싣는다). 401/403/503 분기는 그대로.
- `shared/api/proxy-factory.ts` 통과 분기: `details` 가 있을 때만 응답 본문에 붙인다 — 없으면 **본문이 바이트 단위로 이전과 같다**(시험 «details 없는 오류는 옛 모양»).
- `shared/api/client.ts` `parseError` 가 `details` 를 `ApiError` 에 싣는다.
소유 실측: 네 파일의 최근 커밋은 모두 머지된 PR(`errors.ts` #4130 · `flat-envelope-gateway.ts` #4173 · `proxy-factory.ts` #1151 · `client.ts` #3845), 열린 PR 0건.

## AC ↔ 닫는 시험

| AC | 시험 (파일 › 이름) |
|---|---|
| AC-1 | `tests/unit/features/erp-ops/AccountLink.test.tsx` › «list: linked row reads «연결됨», unlinked row reads «연결된 계정 없음»…» · «detail: both states» · «writable list offers «계정 연결»…» · «read-only list offers no «계정 연결» button»; `tests/unit/sample-fixtures-schema-erp.test.ts` › «the employee list has BOTH states…» |
| AC-2 | `tests/unit/erp-account-link-proxy.test.ts` › «propose with token A, accept with token B → 200 employee with accountId» · «the proposer accepting their own proposal → 403 EMPLOYEE_LINK_SELF_ACCEPT passes through» · «a non-addressee accepting → 403 EMPLOYEE_LINK_NOT_ADDRESSEE» · «409 … passes through WITH details.cause»; `AccountLink.test.tsx` › «propose → POST …» · «🔴 the proposer accepting → «제안한 사람이 수락할 수 없습니다»» · «not the addressee → its own copy» · «503 on accept → outage copy» · «accountLinkErrorMessage — … pairwise-distinct» |
| AC-3 | `tests/unit/features/erp-ops/ApprovalScreen.test.tsx` › «AC-3 — actorEmployeeId ABSENT → …» · «AC-3 — actorEmployeeId present + 0 rows → …»; `sample-fixtures-schema-erp.test.ts` › «the inbox carries meta.actorEmployeeId…» |
| AC-4 | `ApprovalScreen.test.tsx` › «🔴 AC-4 — an employee with no linked account is marked «연결된 계정 없음» in the selector» · «🔴 AC-4 — submit 422 APPROVAL_APPROVER_UNLINKED points at the stage…» · «APPROVAL_ACTOR_NOT_LINKED / ROUTE_INVALID causes / 503 — pairwise-distinct…» · «employee list unreadable → … falls back…»; `erp-account-link-proxy.test.ts` › «submit 422 APPROVAL_APPROVER_UNLINKED keeps details.stageIndex through the proxy» |
| AC-5 | `tests/unit/erp-master-ref-names.test.tsx` › «ERP 결재 — 311 보정 걷기 뒤 결재자 칸 (TASK-PC-FE-318 AC-5)» 6칸(① 이름이 보인다 ② «(나)» 덧붙음 ③ 옛 보정 경로 사라짐 · `APPROVAL_SELF_LABEL` export 없음 ④ «(나)» 없음 ⑤ Edge «연결된 계정 없음» ⑥ 조회 실패면 덧붙이지 않음). ⚪ 라이브 = 재굽기 창 |
| AC-6 | 이 PR 의 `console-integration-contract.md` § 2.4.8 · `iam-integration.md` diff |
| AC-7 | 아래 «검증» |

## bite (AC-4 — 티켓이 🔴 로 짚은 자리)

`ApprovalCreateDialog.tsx` `approverOptionLabel` 의 `if (!e.accountId) parts.push(ACCOUNT_UNLINKED_LABEL);` 를 주석으로 끄고 `tests/unit/features/erp-ops` + `erp-master-ref-names` + `erp-account-link-proxy` 를 돌렸다 → rc=1, **정확히 1 실패**: `ApprovalScreen.test.tsx` › «🔴 AC-4 — an employee with no linked account is marked «연결된 계정 없음» in the selector (selectable, not disabled)», 168 통과. Edit 로 되돌린 뒤 같은 묶음 + `erp-api` + `sample-fixtures-schema-erp` 10 파일 250/250 rc=0. 🔵 행 경고(`approval-create-approver-unlinked-0`)는 다른 코드라 이 bite 가 끄지 않는다 — 시험이 옵션 문구를 먼저 단언하므로 빨강은 옵션 표시 쪽에서 났다.

## 검증 (apps/console-web, 각각 따로 · rc 를 파일로)

- `npx tsc --noEmit` rc=0
- `npm run lint` rc=0 («No ESLint warnings or errors»)
- `npx vitest run`(전체) rc=0 — **346/346 파일 · 3948/3948 시험**. 첫 전체 실행은 rc=1(2 실패, 둘 다 `tests/unit/erp-api.test.ts` 의 인벤토리 핀 — export 목록과 erp 프록시 라우트 수 GET 18 · POST 20). 의도된 표면 추가라 핀을 갱신했다(GET 20 · POST 23, 7 함수) — 각 줄에 근거 주석.
- e2e grep(변경 뒤): `대기 중인 결재가 없습니다|approval-create|approval-inbox|emp-…|결재선|erp-employee|erp-employees-table` → console `e2e-smoke/**` · `tests/e2e/**`(nightly 콘솔 잡이 도는 디렉터리) · 루트 `tests/federation-hardening-e2e/**` 에서 **0건**. 대조 `approval-screen|nav-erp` = 11건(2 파일) — 술어는 비어 있지 않다. 고칠 스펙 없음.

## 티켓과 다른 점

- Scope 2 «직원 상세에서 계정을 골라 제안» — `EmployeeDetail` 은 어떤 라우트에도 마운트돼 있지 않다(`features/erp-ops/index.ts` export 뿐, 페이지 0곳). 그래서 제안 · 철회 · 해제는 `/erp/masters` 직원 목록 행의 «계정 연결» 창에 두고, `EmployeeDetail` 에는 «연결된 계정» 줄만 더했다(AC-1).
- «계정 고르기» 는 선택기가 아니라 계정 ID 입력이다 — AC-0 ① 판정.
- 결재함 «처리할 결재가 없습니다» 로 기존 «대기 중인 결재가 없습니다.» 문구를 바꿨다(티켓 Scope 5 의 대비 문구 그대로).

## 후속 후보

- «이메일로 계정 찾기» — `erp.write` 보유자가 부를 수 있는 계정 조회가 생기면 계정 ID 입력을 선택기로(AC-0 ①).
- 결재선 선택기는 직원 첫 100명만 싣는다(넘으면 화면이 그렇게 말한다) — 검색형 선택기는 직원 수가 늘면.
- `GET /employees/{id}` 는 부서 data scope 를 건다 — scope 가 좁은 운영자에게 상위 부서 승인자는 `이름 확인 불가`(311 전후 무관, 이 티켓이 바꾸지 않음). 이름 해소용 scope 없는 읽기가 필요한지는 별도 질문.
- ⚪ 라이브(재굽기 창): `demo@` 결재함이 «(나)» 없이도 이름으로 차는지 · `requester@` 로 개요 카드에 제안이 안 남는지(시드 §4b 가 이미 수락) · 미연결 직원(박재무) 승인자 초안 상신 시 «1단계 결재자에게 연결된 계정이 없어…».
