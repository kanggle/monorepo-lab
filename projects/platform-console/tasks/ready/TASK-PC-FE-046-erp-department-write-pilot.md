# Task ID

TASK-PC-FE-046

# Title

**erp 부서(department) 마스터 WRITE 파일럿** — 콘솔 erp-ops 의 부서 마스터에 create / update / retire / move-parent 쓰기 affordance 를 추가한다. 운영자 관리(`/operators`)의 reason+confirm+`Idempotency-Key` 패턴을 이식하되, erp 고유 제약(GAP OIDC 도메인 토큰 · 멱등키 4개 전부 필수 · `reason` 은 producer slot 이 있는 retire/move-parent 에만 · effective-dating · 참조무결성/사이클)을 따른다. 나머지 4개 마스터(직원/직급/비용센터/거래처)는 read-only 유지(후속 task). producer `masterdata-service` write 엔드포인트는 **이미 라이브** — 신규 백엔드 없음.

# Status

ready

# Owner

frontend-engineer (console-web only — project-internal; spec = console-integration-contract §2.4.8 + ADR-MONO-016 §D3.1 additive amendment)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- test
- adr

---

# Dependency Markers

- **follows**: TASK-PC-FE-010 (erp-ops read-only 5-master surface + §2.4.8 binding), TASK-PC-FE-004 (operators write 슬라이스 — reason+confirm+Idempotency-Key 패턴의 원본), TASK-ERP-BE-002 (erp `gap-integration.md` console operator read consumer — 동일 credential 재사용 기반).
- **producer (불변)**: erp `masterdata-api.md` § Department — 4 mutation (POST create / PATCH update / POST retire / POST move-parent) **이미 구현·라이브**. 이 task 는 그 엔드포인트를 콘솔에서 소비할 뿐, producer 계약/코드 변경 없음.
- **spec-first**: console-integration-contract §2.4.8 가 원래 erp 전체 read-only(test 로 mutation 부재 단언) → 이 task 가 부서만 write carve-out 으로 amend + ADR-MONO-016 §D3.1 additive amendment(read-only parity slice → 부서 write pilot). **코드 전에 spec 먼저 랜딩.**
- **decision (user, 2026-06-04)**: "1 — 부서 파일럿부터" (콘솔에서 erp 마스터 write; AskUserQuestion 범위 선택).

# Goal

운영자가 콘솔 `ERP 운영 → 부서` 에서 부서를 **생성·수정·폐기·상위이동** 할 수 있다. 각 작업은 명시적 confirm 뒤에 실행되고, 파괴적 작업(폐기/이동)은 사유 입력을 요구하며, 모든 작업은 `Idempotency-Key` 를 동반한다. 자격증명·테넌트 모델은 read 와 동일(GAP OIDC 도메인 토큰, `X-Tenant-Id` 미전송). 나머지 4개 마스터에는 write affordance 가 없다(여전히 read-only).

# Scope

## In Scope

- **spec**: console-integration-contract §2.4.8 *Department write binding (PILOT)* 추가 + read-only bullet 4개 마스터로 rescope. ADR-MONO-016 §D3.1 additive amendment.
- **api**: `features/erp-ops/api/erp-api.ts` — `callErp` 를 method/body/idempotencyKey 받도록 일반화(read 경로 불변) + `createDepartment`/`updateDepartment`/`retireDepartment`/`moveDepartmentParent`. `api/types.ts` — write input + result 스키마.
- **hooks**: `hooks/use-erp-ops.ts` — 4 mutation 훅 + `invalidateDepartments`(`['erp-ops','departments']` prefix).
- **proxy**: same-origin POST route 4개 — `departments/route.ts`(GET+POST create), `departments/[id]/route.ts`(GET+POST update→upstream PATCH), `departments/[id]/retire/route.ts`(POST), `departments/[id]/move-parent/route.ts`(POST). `app/api/erp/_proxy.ts` — body 스키마(zod).
- **ui**: 신규 `DepartmentWriteDialog.tsx`(create/update/retire/move-parent, confirm + 조건부 reason + 멱등키 생성) + `DepartmentList.tsx` 에 "부서 추가" 버튼 + per-row 수정/이동/폐기 action(`writable` prop gate). `ErpOpsScreen.tsx`/`erp/page.tsx` 에 부서 writable 배선 + read-only 카피 수정. `index.ts` export.
- **test**: `erp-api.test.ts` 2개 단언 rescope(부서 write fn/route 허용, 나머지 4개 read-only 유지) + write-call 단언. `erp-proxy.test.ts` 부서 write route 테스트. 신규 `erp-department-write.test.tsx`. e2e `erp-department-write.spec.ts`(@e2e, nightly).

## Out of Scope

- 나머지 4개 마스터(직원/직급/비용센터/거래처) write — 후속 task. (이 PR 에서 read-only 유지가 회귀-게이트.)
- erp v2 `approval-service` / `read-model-service` / `admin-service` — ADR-MONO-016 §D3 v2-deferred 유지.
- producer `masterdata-service` 계약/코드 — 불변(consume only).

# Acceptance Criteria

- [ ] **AC-1 (spec-first)** §2.4.8 *Department write binding (PILOT)* + ADR-MONO-016 §D3.1 amendment 가 코드보다 먼저 랜딩(같은 PR 내 선행 커밋). read-only bullet 은 4개 마스터로 rescope.
- [ ] **AC-2 (create/update/retire/move-parent)** 콘솔 부서 화면에서 4개 작업 모두 동작 — 각각 올바른 same-origin POST → 올바른 upstream method(create POST / update PATCH / retire POST / move-parent POST) + `Idempotency-Key` 동반.
- [ ] **AC-3 (credential 불변)** 4개 write 전부 GAP OIDC 도메인 토큰(`getDomainFacingToken`) 사용, `getOperatorToken` 절대 미사용, `X-Tenant-Id` 미전송 (read 와 동일 — test 단언).
- [ ] **AC-4 (reason 정직성)** retire 는 reason 필수(≤256, body), move-parent reason 옵션(≤256, body), create/update 는 reason 필드 없음 + `X-Operator-Reason` 헤더 미생성(producer slot 없음).
- [ ] **AC-5 (나머지 4개 read-only 회귀-게이트)** employees/job-grades/cost-centers/business-partners 는 write affordance/route/fn 부재 — test 로 단언(기존 read-only 단언을 4개 마스터로 rescope, 부서만 예외).
- [ ] **AC-6 (에러 정직)** 409(DUPLICATE_KEY/REFERENCE_VIOLATION/PARENT_CYCLE/IDEMPOTENCY_KEY_CONFLICT/CONCURRENT_MODIFICATION)/422(EFFECTIVE_PERIOD_INVALID)/400(IDEMPOTENCY_KEY_REQUIRED)/403(PERMISSION_DENIED) 가 inline-actionable 로 표면화(크래시 없음). 403 은 pre-judge 없이 producer 권위로.
- [ ] **AC-7 (CI)** console `pnpm test` GREEN(rescope+신규), `tsc`/`lint`/`build` GREEN.

# Related Specs

- `console-integration-contract.md` §2.4.8 *Department write binding (PILOT)* (이 task 가 추가). ADR-MONO-016 §D3.1 (amended). ADR-MONO-013 §3.3 (console = only UI — write affordance 콘솔, write logic 백엔드).
- producer (불변): erp `masterdata-api.md` § Department.

# Related Contracts

- erp `masterdata-api.md` § Department — POST/PATCH/retire/move-parent (request/response/error 표 canonical, 불변 소비).

# Edge Cases

- **403 PERMISSION_DENIED**: 데모 운영자 토큰이 erp write E6 role 을 못 가질 수 있음 → inline "권한 없음" 표면화(크래시 없음). 콘솔은 write 권한을 pre-judge 하지 않음(producer 권위).
- **멱등 재시도**: 동일 `Idempotency-Key` 재전송 시 producer 가 409 IDEMPOTENCY_KEY_CONFLICT 또는 캐시 응답 — 콘솔은 inline 표면화. 키는 작업 시도마다 새로 생성.
- **참조무결성**: 폐기 대상 부서에 live 직원/비용센터/하위부서 존재 → 409 MASTERDATA_REFERENCE_VIOLATION(`details` 에 referer 종류) inline.
- **사이클**: move-parent 의 newParentId 가 자신의 하위 → 409 MASTERDATA_PARENT_CYCLE inline.
- **effective-dating**: create/update/move-parent 의 `effectiveFrom` 가 유효하지 않으면 422 MASTERDATA_EFFECTIVE_PERIOD_INVALID inline.

# Failure Scenarios

- read 경로 회귀: `callErp` 일반화가 read 호출에 body/Idempotency-Key/Content-Type 를 새지 않아야 함 → "every read is a pure GET" 단언으로 게이트.
- 나머지 4개 마스터에 write 누수: rescope 한 단언이 부서만 예외이고 4개는 GET-only/mutation-fn-부재 유지.
- spec 후행: §2.4.8/ADR amendment 커밋이 코드보다 앞서야 함(AC-1).

# Test Requirements

- `erp-api.test.ts`: (a) "every read is a pure GET" 보존; (b) export-name 단언 → 부서 write 4개 fn 허용 + 나머지 마스터 mutation-fn 부재; (c) proxy-walk → 부서 route POST/PATCH 허용 + 나머지 마스터 GET-only; (d) write-call 단언(create POST + Idempotency-Key + body, update→PATCH upstream, retire/move-parent reason body, getDomainFacingToken 사용/getOperatorToken 미사용).
- `erp-proxy.test.ts`: 부서 4개 write route — 올바른 upstream method/path + Idempotency-Key 전달 + reason body 전달 + 401/403/409/422 매핑.
- 신규 `erp-department-write.test.tsx`: 다이얼로그 — create/update confirm(reason 없음), retire/move-parent reason 필수 gate, 멱등키 생성, mutation POST 호출.
- e2e `erp-department-write.spec.ts`(@e2e): 부서 생성 → 목록 반영(nightly).
- `pnpm test` + `tsc` + `lint` + `build`.
- Local: console-web 재빌드+재기동; ERP 운영 → 부서에서 생성/수정/폐기/이동 라이브 스모크 + **DB 영속 확인**(머지+CI GREEN≠기능작동 — 인증·write 플로우는 라이브 엔드포인트 직접타격까지 실증).

# Definition of Done

- [ ] spec(§2.4.8 + ADR-016 §D3.1) 선행 커밋 + 코드 + 테스트.
- [ ] console `pnpm test`/`tsc`/`lint`/`build` GREEN.
- [ ] Local 재빌드+재기동; 부서 4개 작업 라이브 동작 + DB 영속 확인 + 나머지 4개 마스터 write affordance 부재 확인.
- [ ] Task md + `projects/platform-console/tasks/INDEX.md` 갱신.
- [ ] Reviewed + merged (3-dim verified).

---

분석=Opus 4.8 / 구현=Opus(직접). 사용자 "ERP 운영 ... 쓰는건 어디서 해?"(2026-06-04) → 콘솔 erp 가 의도적 read-only(§2.4.8 normative, ADR-016 §D3.1)였음을 확인 → "부서 파일럿부터" 선택. 메타: 콘솔 = federated read/observability 였던 posture 를 부서 마스터에 한해 write 로 여는 첫 사례 — producer write 는 이미 라이브였고(approval-service 등만 v2-deferred), 막혀 있던 건 콘솔 binding. write affordance=콘솔 / write logic(멱등·참조무결성·E6 authz·E8 audit)=백엔드 분리 유지(ADR-013 §3.3 위배 아님).
