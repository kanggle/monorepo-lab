# Task ID

TASK-PC-FE-355

# Title

ERP 결재 화면의 콘솔 오류 둘 — ① 콘솔에서 결재 요청을 상신하면 «요청을 처리하지 못했습니다»(같은 DRAFT 를 API 로 상신하면 200) ② 위임 «내가 위임한» 칸이 «결재 서비스를 일시적으로 사용할 수 없습니다» 와 «위임한 grant 가 없습니다» 를 함께 그린다(API 는 200 `[]`)

# Status

done

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet(원인이 콘솔 판정 · 스키마 한 곳이면) · Opus(BFF ↔ 게이트웨이 계약 차이면)
> 기안 2026-10-20 UTC — 32차 데모 창(`TASK-MONO-672` § 32nd window — harvest)의 발견. 측정 먼저(AC-0).

# Dependency Markers

- 출처: `TASK-MONO-672` 32차 창 · `TASK-PC-FE-308` · `TASK-PC-FE-347` 의 32차 기록.
- 선행 없음.

# Goal

1. **상신**: `requester@demo.com`(`demo-corp`, `<domain>-full` 다섯)이 콘솔 «결재 요청 작성» 으로 «308 반려 확인»(대상 `DEPT-HQ`, 결재자 `EMP-0001` 김본부)을 만들자 **DRAFT 는 생겼고**(18:14:02Z) 상신 단계에서 «요청을 처리하지 못했습니다» 가 떴다. 같은 DRAFT 를 같은 주체 토큰으로 `POST /api/erp/approval/requests/{id}/submit` 하면 **200 `SUBMITTED`**(18:16Z, 에이전트). erp approval · gateway 로그에 그 시각 오류 줄 없음(OTel 수출 실패 잡음 제외) ⇒ 요청이 서버에 안 닿았거나, 닿아 2xx 인데 콘솔이 실패로 읽었다.
2. **위임 «내가 위임한»**: `erp-read` 단독 · `erp-approval-operate` 단독 두 세트 모두, 새로고침 뒤에도 같은 칸에 오류 문구와 빈 목록 문구가 **함께** 나온다. 같은 주체 토큰으로 `GET /api/erp/approval/delegations?role=DELEGATOR` → **200 `{"data":[],…}`**, `?role=DELEGATE` → 200(1 건). ⇒ 콘솔이 성공 응답의 어딘가(빈 목록 · `meta.size=0` · 다른 호출)를 실패로 판정한다.

둘 다 콘솔이 성공을 실패로 말하는 모양이라 한 티켓에서 잰다. 원인이 다르면 AC-0 뒤에 나눈다.

# Scope

## In Scope

- **AC-0 측정**:
  - ① 콘솔 «결재 요청 작성» 의 호출 사슬(`ApprovalCreateDialog.tsx` → 훅 → BFF `app/api/erp/approval/requests/**` → `flat-envelope-gateway.ts`) — 만들기와 상신이 한 번에 도는가, 상신에 무엇을 싣는가(`Idempotency-Key` · `X-Operator-Reason` · 본문). 응답 스키마(zod)가 실제 200 본문(`data.route` 등)을 받아 주는가. Vercel 런타임 로그(18:14Z 근처)에 그 BFF 의 결과가 있으면 그것을 읽는다(소유자에게 요청).
  - ② «내가 위임한» 칸이 부르는 것 전부(목록 · 이름 해석 · 그 밖) 와 각 판정 — 빈 `data` · `meta.size=0` 을 어떻게 읽는가. 같은 응답의 «나에게 위임된» 은 정상으로 그린다(차이를 찾는다).
- 수선: 성공을 성공으로 그린다. 진짜 실패는 지금처럼 말한다.
- 시험: 실측 응답 모양을 고정값으로 — 상신 200 본문 → 성공 처리 · 빈 위임 목록 → 빈 목록 문구만(오류 없음) · 진짜 503 → 오류 문구.

## Out of Scope

- 위임 화면이 직원 id(`01a157fa-…`)를 이름 대신 그리는 것 — 따로 본다(AC-0 에서 원인만 적는다).
- 서버(approval-service) 변경 — AC-0 이 서버 문제를 보이면 erp 티켓 먼저.

# Acceptance Criteria

- [x] **AC-0** — ①② 의 원인을 file:line 으로(실측 응답 · 콘솔 판정 지점). 원인이 둘이면 나눌지 적는다. → § AC-0 측정 (2026-10-21 UTC). 원인 둘 · 티켓은 나누지 않는다(소유자 결정).
- [x] **AC-1** — 수선(문구가 바뀌면 소유자 문안 승인). → 문구 변경 없음. § 구현 (2026-10-21 UTC).
- [x] **AC-2** — 시험: 위 세 고정값 + 기존 비회귀(결재 화면 · 위임 화면 스위트). → § 구현.
- [ ] **AC-3** — 배포(Vercel) · 라이브(콘솔에서 상신 → `SUBMITTED` · «내가 위임한» 에 오류 문구 없음, 다음 데모 창).

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md`

# Related Contracts

- `projects/erp-platform/specs/contracts/http/approval-api.md`(있는 이름으로 — AC-0 에서 확인) · `projects/platform-console/specs/contracts/console-integration-contract.md` § ERP

# Edge Cases

- 상신이 실제로 두 번 나가 두 번째가 409 · 422 로 실패할 수 있다(만들기 + 상신 사이) — AC-0 이 호출 수를 센다.
- 데모 데이터: 이 창이 만든 «308 반려 확인» 은 반려됨 상태로 남는다(다음 굽기가 지운다).

# Failure Scenarios

1. 오류 문구만 지우고 진짜 실패도 조용해진다 — 시험의 503 고정값이 막는다.
2. 고정값을 손으로 만든 모양으로 쓴다 — 실측 응답(이 창의 200 본문)으로 쓴다.

---

## AC-0 측정 (2026-10-21 UTC · 창 없음 · 분석=Opus 5.5)

실측 = 티켓에 적힌 라이브 API 응답 + console-web 의 zod 로 스키마 사본을 node 에서 판정(빈 위임 목록 `meta.size:0` → **false** · `size:1` → true · 감싸지 않은 본문 → `parseApprovalDetail` **false** · `{data}` → true). 나머지는 코드 추적. Vercel 로그는 안 읽었다.

**① 실패한 것은 «상신» 이 아니라 «작성»(만들기)이다.**
- BFF 가 감싸지 않는다: `apps/console-web/src/app/api/erp/approval/requests/route.ts:74` `NextResponse.json(result, { status: 201 })`. 형제 `requests/[id]/route.ts:23` · `[id]/[transition]/route.ts:105` 는 `{ data: result }`.
- 클라이언트는 `{data}` 를 기대: `features/erp-ops/hooks/use-erp-approval.ts:118-121` `parseApprovalDetail` = `ApprovalRequestSchema.parse(env.data)` → `useCreateApproval`(`:157`) 에서 ZodError(브라우저).
- 화면: ZodError 는 `ApiError` 가 아니라 `approval-error.ts:105` 대체 문구 «요청을 처리하지 못했습니다.» → `ApprovalCreateDialog.tsx:442-449` 표시 · `:163` `onSuccess` 미호출 → 대화상자 열린 채. 서버엔 DRAFT 가 이미 있다(18:14:02Z) — 그래서 에이전트의 API 상신이 409 가 아니라 200 이었다. 다시 누르면 새 Idempotency-Key 로 DRAFT 가 하나 더 생긴다. 버튼 testid `approval-create-submit` 의 라벨이 «작성» 이라 «상신» 으로 읽혔다.
- 나이: `TASK-PC-FE-051`(#1108 `9f04b977e`) 부터. 시험이 못 잡은 이유: `tests/unit/features/erp-ops/ApprovalScreen.test.tsx:426` 이 BFF 를 손으로 만든 `{ data: DRAFT }` 로 흉내 · `tests/unit/approval-proxy.test.ts:179-205` 는 만들기 응답 본문 모양을 안 본다(Failure Scenario 2 그대로).
- ⚪ 상세 화면의 «상신»(`ApprovalDetail.tsx:307-316` → `[transition]/route.ts`) 은 코드상 정상 · 라이브 미관찰.

**② 빈 위임 목록의 `meta.size=0` 을 콘솔 스키마가 거절한다.**
- 서버: `erp-platform/apps/approval-service/.../DelegationController.java:100` `ApiEnvelope.ofList(data, 0, data.size(), data.size())` — 페이지 없는 목록이라 size = 건수(`approval-api.md:211-213`).
- 콘솔: `features/erp-ops/api/delegation-types.ts:60` `size: z.number().int().positive().optional()` 가 0 거절 → `delegation-api.ts:122` 파싱이 `shared/api/flat-envelope-gateway.ts:366` try 안 → `:383-388` catch → `ErpUnavailableError('NETWORK_ERROR')` → `shared/api/proxy-factory.ts:103-114` 503 → `approval-error.ts:63-64` «결재 서비스를 일시적으로 사용할 수 없습니다». 클라이언트 재파싱(`use-erp-delegation.ts:52`)도 같은 이유로 실패.
- «나에게 위임된» 이 정상인 이유: 1건 → `size=1` 통과. 두 문구가 함께 나오는 이유: `DelegationGrantList.tsx:98-111` 이 `isError` 면 오류 문구, `grants=[]` 라 빈 목록 문구도.
- 시험이 못 잡은 이유: `tests/unit/delegation-api.test.ts:130` 고정값 `size:20`. `ofList(…, data.size(), …)` 는 이 컨트롤러 하나뿐 — `approval-types.ts:103` · `types/common.ts:148` 의 `positive()` 는 지금은 무해.

**형제 결함(코드 추적 · 라이브 미확인)**: 위임 **생성 · 회수** — `delegation-api.ts:150-153` · `:172-174` 가 응답을 감싸지 않은 grant 로 `DelegationGrantSchema.parse(json)`(주석 «201 body = grant»), 서버는 `ApiEnvelope.of(v)`(`DelegationController.java:59,74`) → grant 는 생기는데 콘솔은 503 문구일 것. 고정값 `delegation-api.test.ts:277,298,327` 이 감싸지 않은 grant.

범위 밖(원인만): 위임 화면의 직원 id 표시 = `DelegationGrantList.tsx:120` 이 `{g[idField]}` 원문을 그린다(이름 풀이 없음).

### 소유자 결정 (2026-10-21 UTC)

- 나누지 않는다. ① = BFF 만들기 라우트를 `{ data: result }` 로(형제와 같게, 클라이언트 그대로) · ② = `delegation-types.ts:60` `nonnegative()` · 오류일 때 빈 목록 문구 숨김(`DelegationGrantList.tsx:107-111` 에 `!isError`) · **위임 생성 · 회수 결함도 이 티켓에 포함**(`.data` 를 꺼낸다). 서버 변경 없음.
- 문구 변경 없음(기존 문구만 쓴다) — AC-1 문안 승인 대상 없음.
- 시험: 고정값을 실제 서버 모양으로(만들기 BFF 응답 `{data}` 단언 · 빈 위임 목록 `meta:{page:0,size:0,totalElements:0,timestamp}` · 진짜 503 고정값은 오류 문구만 · 생성 · 회수 응답 `{data: grant}`).
- 구현 = Sonnet.

## 구현 (2026-10-21 UTC · Sonnet)

수선 (모두 `apps/console-web/src/` 기준, 사용자에게 보이는 문구 변경 없음):
- `app/api/erp/approval/requests/route.ts:74-76` — 만들기 응답 `NextResponse.json({ data: result }, { status: 201 })` (형제 라우트와 동일, 클라이언트 그대로).
- `features/erp-ops/api/delegation-types.ts:60-62` — `meta.size` `positive()` → `nonnegative()`.
- `features/erp-ops/components/DelegationGrantList.tsx:109-114` — `query.isError` 이면 빈 목록 문구를 그리지 않음(오류 문구만).
- `features/erp-ops/api/delegation-api.ts` — `parseGrantEnvelope`(`.data` 를 꺼내 `DelegationGrantSchema` 파싱) 추가, `createDelegation` · `revokeDelegation` 이 사용. 서버 `DelegationController` 는 `ApiEnvelope.of(v)`. 위임 BFF 라우트는 여전히 풀린 grant 를 클라이언트에 돌려주므로 `use-erp-delegation.ts` 변경 없음.

시험 (red 커밋 `8b05b9667` → green): `approval-proxy.test.ts`(만들기 응답 `data.id` 단언) · `delegation-api.test.ts`(빈 목록 `meta:{page:0,size:0,totalElements:0,timestamp}` · 생성/회수 `{data: grant}`) · `delegation-proxy.test.ts`(업스트림 `{data: grant}`, BFF 는 풀린 grant) · `DelegationScreen.test.tsx`(EMPTY_LIST 실제 모양 · 빈 목록에서 오류 없음 · 진짜 503 은 오류 문구만). `ApprovalScreen.test.tsx:426` 은 이미 `{data: DRAFT}` 라 변경 불필요.

로컬: `npx vitest run --maxWorkers=4 --minWorkers=2` rc=0 — 412 files · 4962 tests 통과. (기본 병렬도에서는 무관한 6 파일이 타임아웃으로 흔들렸고 — 알려진 부하 flake — 낮춘 병렬도에서 전부 통과.) `tsc --noEmit` rc=0 · `next lint` rc=0(기존 경고만).

열림: AC-3(Vercel 배포 · 라이브 — 콘솔에서 만들기 → 대화상자 닫힘/DRAFT 1건 · «내가 위임한» 오류 문구 없음 · 위임 생성/회수 성공, 다음 데모 창).

## 라이브 확인 (2026-10-23 UTC · 33차 창)

- 백엔드: 굽기 `283fd39cf` → AMI `ami-02b3ba00a3b2ff622`(51분 36초 · 태그 RepoCommit 일치) · 핀 PR #4498(`c5e3ddad3`) · 인스턴스 `i-0f39aec49ddbaa899`.
- **AC-3 🟡 절반** — `/erp/delegation` «내가 위임한» 에 위임 1 건, 오류 문구 0.
- 미측정: 상신(→ `SUBMITTED`) — `requester@demo.com` 작성 대화상자가 필요해 이번 창에서 하지 않았다.
- ⇒ 이 티켓은 `review` 에 남는다(체크 칸은 동결 규칙으로 고치지 않음).

---

## CORRECTION — 34차 창 라이브 결과 (2026-10-24 UTC)

- **AC-3 — 닫힘.** 34차 창(2026-10-24 UTC): 소유자가 콘솔에서 «308 반려 확인»(DRAFT 17:07:53Z, erp `approval_request`)을 만들고 상신을 눌렀다. `SUBMITTED` 17:09:39Z, 단계 1/1 EMP-0002 진행중, 이력 행이 생겼다(o14 + 소유자 화면). 「내가 위임한」 칸은 활성 grant 하나와 회수 버튼을 보이고 오류 문구가 없다.
- 🔵 작은 발견(기록만): 위임 행이 직원 이름 대신 원시 id(`01a16eaa-980d-…`)를 그린다. 이 티켓 Out of Scope 로 이미 분리된 항목이다.
- (원문 체크박스는 동결 규칙(HARDSTOP-05)상 바꾸지 않았다 — 이 절이 그 AC 의 닫힘 기록이다. 위 «이 티켓은 `review` 에 남는다» 문장은 33차 창 시점의 기록이며 지금은 `done` 이다.)
