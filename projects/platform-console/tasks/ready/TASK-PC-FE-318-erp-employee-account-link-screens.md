# Task ID

TASK-PC-FE-318

# Title

erp 직원 ↔ 계정 연결 화면 다섯 — 직원 «연결된 계정» · 연결 제안 · 내 앞 제안 수락/거절 · 결재선 선택기 «연결된 계정 없음» · 결재함 빈 이유 + 콘솔 계약 보정 + `TASK-PC-FE-311` 보정 걷기 (`TASK-MONO-774` S4)

# Status

ready

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

- [ ] **AC-0** — 재측정(위 file:line 전부) + 판정 셋: ① 제안의 «계정 고르기» 원천(테넌트 운영자 목록 → 계정 UUID; 그 목록 API 와 소유 경계) ② 수락 표면 위치 ③ `TASK-PC-FE-311` 의 상태(`review/` → `done/` 됐는가)와 보정 코드가 실제로 불필요해졌는지(직원 id 승인자로 이름이 나오는가).
- [ ] **AC-1** — 직원 목록/상세가 연결 계정 / «연결된 계정 없음» 을 보인다(단위 테스트, 두 상태).
- [ ] **AC-2** — 제안 → (다른 계정으로) 수락 흐름이 BFF 를 거쳐 동작한다; 같은 계정 수락은 `EMPLOYEE_LINK_SELF_ACCEPT` 문구로 끝난다(단위 · BFF 라우트 테스트).
- [ ] **AC-3** — 결재함: `meta.actorEmployeeId` ABSENT ↔ 있음·0건 이 **다른 문구**를 낸다(단위).
- [ ] **AC-4** — 결재선 선택기가 미연결 직원을 «연결된 계정 없음» 으로 표시하고, 그 직원으로 상신 시 `APPROVAL_APPROVER_UNLINKED` 를 사람 말로 보인다. 🔴 `data-testid="approval-create-approver-${idx}"` 를 바꾸면 e2e/단위 스펙을 grep 해 함께 고친다(`tests/e2e` · `tests/unit` · nightly e2e 스펙 디렉터리).
- [ ] **AC-5** — `TASK-PC-FE-311` 보정 코드 제거 뒤에도 승인자 이름이 보인다(단위). 🔵 라이브(재굽기된 데모에서 결재함 · 이름) ⚪ 재굽기 창.
- [ ] **AC-6** — 콘솔 계약 § 2.4.8 행 추가 + `iam-integration.md:120` 정정이 같은 PR 에 있다.
- [ ] **AC-7** — `npx tsc --noEmit` · `npm run lint` · `npx vitest run`(전체) 각각 rc 기록.

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
