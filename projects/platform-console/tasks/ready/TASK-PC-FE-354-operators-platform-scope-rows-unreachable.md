# Task ID

TASK-PC-FE-354

# Title

`/operators` 의 `'*'` 두 공백 — `*` 운영자의 «모든 테넌트» 업무 권한 행을 콘솔에서 주거나 회수할 수 없다(회수 = 404) · «플랫폼 운영자 등록» 양식이 한 번도 그려지지 않았다

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus(권한 쓰기의 테넌트 귀속 — 잘못 보내면 다른 테넌트에 행이 생긴다)
> 기안 2026-10-20 UTC — `TASK-PC-FE-353` AC-0 ② 의 발견(소유자 결정 «(가) 문구만 + /operators 별도 티켓»). 스위처에 `*` 를 넣지 않는 **좁은 수선**이다.

# Dependency Markers

- 출처: `TASK-PC-FE-353` § AC-0 결과 ② «옆 화면 `/operators` 에 진짜 공백 둘».
- 선행 없음. 353 과 같은 파일을 건드리지 않는다(353 = 여섯 화면 문구 · 주석).

# Goal

`'*'` 범위를 가진 운영자(예 `demo-star-owner`)가 콘솔 `/operators` 에서:

1. `*` 운영자의 «모든 테넌트» 업무 권한 세트 행을 **부여 · 회수**할 수 있다. 지금은 콘솔이 늘 활성 테넌트를 `X-Tenant-Id` 로 보내므로, 부여는 그 테넌트(`demo-corp`) 행을 만들고 기존 `'*'` 행의 회수는 `demo-corp` 아래에서 0 건을 지워 **404** 가 된다(`OperatorDomainAccessUseCase.java:166-216` · 행은 `X-Tenant-Id` 가 `*` 이거나 없을 때만 `'*'`). 계약 `console-integration-contract.md:267` 은 이것이 된다고 적는다.
2. «플랫폼 운영자 등록» 양식(`OperatorsScreen.tsx:689-696`)과 «모든 회사에 적용» 정지 문구(`:419`)를 본다. 지금은 `app/(console)/operators/page.tsx:133` `isPlatformOperator = selectableTenants(...).includes('*')` 가 늘 false 다(`*` 는 스위처 목록에 없다 — 353 AC-0 ①).

# Scope

## In Scope

- **AC-0 측정**:
  - ① «이 운영자가 `'*'` 범위를 가졌는가» 의 출처 — `GET /api/admin/me` 의 `homeTenantId`(`iam-operators-types.ts:86`, `new-tenant/page.tsx:68` 이 이미 씀) 인가, grant 범위인가(`admin-api.md:88` 은 «grant 범위에 `*`» 로 정의). 둘이 갈라지는 운영자가 있는가.
  - ② 활성 테넌트가 실제 테넌트(`demo-corp`)일 때 `*` 대상 운영자가 `/operators` 목록에 나오는가(353 AC-0 의 추론 · 미측정 — `:267` 은 `*` 활성일 때만이라고 읽힐 수 있다). 안 나오면 1 의 수선은 그 행에 닿지도 못한다.
  - ③ 부여 · 회수 BFF 경로(`/api/admin/operators/{id}/domain-permission-sets` 계열)가 `X-Tenant-Id` 를 어디서 정하는가 — 호출 단위로 `'*'` 를 보낼 자리가 있는가(`shared/api/iam-gateway.ts:300-311`).
  - ④ 계약 `:267` 의 문장이 «활성 테넌트 `*`» 를 전제하는가 — 그렇다면 그 문장을 «대상이 `*` 운영자이면 `'*'` 를 보낸다» 로 먼저 고친다(계약 우선).
- 수선(AC-0 이 확정):
  - 대상이 `*` 운영자이고 호출자가 `'*'` 범위일 때만 그 부여 · 회수 호출이 `X-Tenant-Id: *` 를 보낸다. 그 밖의 모든 호출은 지금처럼 활성 테넌트.
  - `isPlatformOperator` 를 ① 의 출처로.
  - 시험 고정값 정정: `tests/unit/operators-page-parallel.test.tsx:91` `['wms','*']` 처럼 실제로 안 나오는 레지스트리 모양을 실제 모양으로(그 고정값이 `isPlatformOperator=true` 를 만들어 «양식이 보인다» 시험을 거짓으로 초록으로 만든다).

## Out of Scope

- 스위처에 `*` 항목 넣기(353 에서 기각).
- 서버(admin-service) 변경 — 서버는 이미 `X-Tenant-Id: *` 를 받으면 `'*'` 행을 쓴다. AC-0 이 서버도 바꿔야 한다고 보이면 IAM 티켓 먼저.

# Acceptance Criteria

- [ ] **AC-0** — 위 ①②③④ 를 file:line 으로. ② 가 «안 나온다» 면 소유자에게 묻는다(목록까지 넓힐지).
- [ ] **AC-1** — `*` 대상의 부여 · 회수가 `'*'` 행에 닿는다. 일반 대상 · 일반 호출자는 그대로(활성 테넌트).
- [ ] **AC-2** — `'*'` 범위 운영자에게 «플랫폼 운영자 등록» 양식이 보이고, 아닌 운영자에게는 안 보인다.
- [ ] **AC-3** — 시험: 헤더 선택(`*` 대상 + `'*'` 호출자 → `*` · 일반 대상 → 활성 테넌트 · `'*'` 없는 호출자 → 활성 테넌트) · 양식 표시 양방향 · 거짓 고정값 정정 후에도 초록. 문구가 바뀌면 소유자 문안 승인.
- [ ] **AC-4** — 배포(Vercel) · 라이브(`star-owner@demo.com` 으로 `*` 운영자의 «모든 테넌트» 행 회수 → 200, 다음 데모 창).

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md`
- `projects/iam-platform/specs/contracts/http/admin-api.md` (§ 테넌트 결정 `:88` · `:102-117`)

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` `:267`
- `projects/iam-platform/specs/contracts/http/admin-api.md` — 업무 권한 세트 부여 · 회수

# Edge Cases

- `'*'` 범위 없는 호출자가 `*` 대상을 연다 — 서버가 거절한다(`TenantScopeDeniedException`, `:203-207`). 콘솔은 그 호출에 `*` 를 보내지 않는다(보내도 거절이지만 감사에 tenant-denied 가 남는다).
- 일반 대상에게 `X-Tenant-Id: *` 를 보내면 서버가 400(`:209-212`) — 헤더 선택이 대상 기준이어야 하는 이유.
- 데모 데이터: 29–31차 창이 남긴 DIRECT 행 · TERMINATED 파트너십 등은 다음 굽기가 지운다 — 라이브 시험은 그 위에서 돈다.

# Failure Scenarios

1. 모든 호출에 `*` 를 보낸다 — 일반 대상의 부여가 400, 또는 플랫폼 행이 엉뚱하게 생긴다.
2. 거짓 고정값(`['wms','*']`)을 둔 채 양식 시험을 쓴다 — 초록이지만 라이브는 여전히 양식이 없다.
3. ② 를 재지 않고 1 만 고친다 — `demo-corp` 활성에서 `*` 대상이 목록에 없으면 고친 버튼에 닿을 길이 없다.
