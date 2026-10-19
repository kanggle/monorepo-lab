# Task ID

TASK-PC-FE-349

# Title

`ADR-MONO-083` P6 콘솔 후속 (3) — 결재 세트만 가진 운영자의 «위임 생성» 대결자 칸이 «직원 목록을 불러오지 못해» 로 떨어진다 (권한 거절을 장애로 말한다)

# Status

in-progress (2026-10-19 UTC — AC-0 코드 확인 · 소유자 결정 (가) + (나) 기안 · 문안 승인)

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (콘솔 문구 · 분기 한 곳) — 🔵 선택지 (나) 를 고르면 서버 인가 변경이라 Opus · 별도 티켓.
> 기안 2026-10-18 UTC — `TASK-PC-FE-347` 구현 보고의 «열린 의문 1» 을 코드로 확인한 것. **라이브 미확인**.

# Dependency Markers

- 출처: `TASK-PC-FE-347`(ERP 쓰기 버튼 — `/erp/delegation` 의 «위임 생성» 은 `ERP_APPROVAL_WRITE` 로 보인다).
- 서버 판정: `TASK-MONO-793`(라이브). 선행 없음.
- 라이브 측정 칸: `TASK-MONO-672` § Order «Still open» 11 ④.

# Goal

`ERP_APPROVAL_WRITE` 만 가진 운영자(결재 세트만)는 `/erp/delegation` 에서 «위임 생성» 버튼을 본다(347 — 옳다: 위임 생성 · 회수는 approval `canWriteErp()` 다). 그런데 그 다이얼로그의 대결자 칸은 기준정보 **직원 목록**(`useEmployees` → masterdata `listEmployees`)으로 고르고, 그 목록은 `RequiredScope.READ` = 기준정보 조회 역할만 연다. `ERP_APPROVAL_WRITE` 가 여는 것은 직원 **단건** 조회(`SUBJECT_READ`)뿐이다. 그래서 그 운영자는 목록 `403` → `employeesQ.isError` → 수동 id 입력으로 떨어지고, 화면은 «직원 목록을 **불러오지 못해** 대결자 직원 ID 를 직접 입력합니다» 라고 말한다. **권한 거절을 일시 장애처럼 읽게 한다.** 운영자는 재시도를 기다리거나 장애로 신고한다.

## 증거 (코드, 2026-10-18 UTC · `b74e05ab7`)

| 항목 | 위치 |
|---|---|
| 다이얼로그가 직원 목록을 부른다 | `apps/console-web/src/features/erp-ops/hooks/use-delegation-screen.ts:70` `useEmployees({ page: 0, size: ERP_MAX_PAGE_SIZE })` |
| 실패 시 문구 | `apps/console-web/src/features/erp-ops/components/DelegationScreen.tsx:191-198` (`delegation-create-delegateId-fallback`) |
| 목록 = 기준정보 조회만 | `projects/erp-platform/apps/masterdata-service/.../MasterdataApplicationService.java:351-353` `listEmployees` → `authorize(actor, RequiredScope.READ, null)` |
| 결재 역할이 여는 것 = 단건 | 같은 파일 `:343-345` `getEmployee` → `SUBJECT_READ` · `RoleScopeAuthorizationAdapter.java:102` `case SUBJECT_READ -> masterdataRead \|\| actor.hasRole(ROLE_APPROVAL_WRITE)` |

⚪ **라이브로 보지 않았다.** 위 경로가 실제로 `403` 을 내고 BFF 가 그것을 `isError` 로 넘기는지는 데모 창에서 잰다(«Still open» 11 ④). 같은 모양의 직원 목록 폴백이 `DelegationFactCard.tsx:185`(`/erp/orgview` 위임 사실 카드)에도 있다 — 그 카드를 여는 운영자 집합이 다르므로 AC-0 에서 함께 본다.

# Scope

## In Scope

- **AC-0 에서 소유자 결정을 받는다** — 선택지:
  - (가) **콘솔만**: `403` 일 때와 그 밖의 실패를 나눈다. `403` = «기준정보 조회 권한이 없어 직원 목록을 볼 수 없습니다 — 대결자 직원 ID 를 직접 입력하세요» 처럼 사실대로 말하고 수동 입력을 유지한다. 서버 무변경. 🔵 추천(가장 작고, 거짓 문구만 고친다).
  - (나) **서버 인가 변경**: 결재 역할에 위임 대상 고르기용 직원 목록(또는 축약 조회)을 연다 — `ADR-MONO-083` P6 Q 결정의 확장이라 ADR/793 영역, **별도 티켓**.
  - (다) 둔다(결재 세트만 가진 운영자는 기준정보 조회 세트를 함께 받는 것이 운영 관례라고 가이드에 적는다).
- (가) 이면: 단위 시험 — 직원 목록 `403` → 권한 문구 + 수동 입력 · 그 밖의 실패 → 지금 문구 그대로(대조군).

## Out of Scope

- 위임 생성 · 회수 버튼 자체의 게이트(`TASK-PC-FE-347` — 완료).
- 서버 인가 변경((나) — 별도 티켓).

# Acceptance Criteria

- [x] **AC-0** — 라이브 또는 BFF 단위로 «결재 세트만 → 직원 목록 `403` → `isError`» 를 확인한다(못 재면 ⚪ 로 이유를 적는다). `DelegationFactCard` 폴백도 같은 경로인지 확인. 소유자에게 (가)/(나)/(다) 를 추천과 함께 묻고 결정을 적는다.
  - ✅ 2026-10-19 UTC — 코드로 확인했다(라이브 ⚪ — 데모 창 «Still open» 11 ④). 경로: masterdata `listEmployees` = `RequiredScope.READ`(기준정보 역할만) → ERP 프록시 `mapErpError` 가 403 을 그대로 넘김 → 브라우저 `apiClient` `ApiError(403)` → `useEmployees`(`retry: false`) `isError` → 폴백 문구 «불러오지 못해». 🔴 **티켓보다 넓다 — 같은 폴백이 4 곳**: 위임 생성 대결자(`DelegationScreen`) · 결재 상신 대상 직원 · 결재 상신 결재자(`ApprovalCreateDialog`) · 위임 사실 카드 필터(`DelegationFactCard`, `/erp/orgview`). 🔴 **기본 세트가 해당된다**: admin `V0057` 의 «결재 운영»(`erp-approval-operate`) = `ERP_APPROVAL_READ` + `ERP_APPROVAL_WRITE` 뿐(기준정보 조회 없음) — 맞춤 세트만의 일이 아니다. 결재 담당자는 상신할 때 결재자까지 직원 ID 를 손으로 친다(서버는 허용).
- [x] **AC-1** — (가) 이면: `403` 과 그 밖의 실패가 다른 문구를 낸다(빨강 먼저). 수동 입력은 두 경우 다 유지.
  - ✅ 2026-10-19 UTC 소유자 결정 «(가) 지금 + (나) 기안»: 콘솔 4 곳의 문구를 403 / 그 밖의 실패로 나눈다(이 티켓). 결재 권한에 선택용 축약 직원 목록을 여는 서버 변경 (나) 는 별도 티켓으로 기안만 한다(`TASK-ERP-…` — 아래 § 후속).
- [x] **AC-2** — 사용자 노출 문구라 소유자 문안 확인 후 머지.
  - ✅ 2026-10-19 UTC 소유자 문안 승인(«이대로 진행»): 403 = «기준정보 조회 권한이 없어 직원 목록을 볼 수 없습니다. {대상 · 결재자 · 대결자 · 위임자/대행자} 직원 ID 를 직접 입력하세요.» · 그 밖의 실패 = 옛 문구 «직원 목록을 불러오지 못해 … 직원 ID 를 직접 입력합니다.» 그대로. 401 은 권한 문구가 아니다(세션 재로그인 경로).
- [ ] **AC-3** — 배포 상태(Vercel · 라이브 화면)를 따로 적는다.

# Related Specs

- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md` P6
- `projects/erp-platform/specs/integration/iam-integration.md` § 업무 역할

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.3.4 (쓰기 버튼 — 변경 없음 예상)

# Edge Cases

- 데모 운영자(legacy-compat)는 기준정보 조회를 가지므로 목록이 뜬다 — 데모에서는 안 보이는 결함이다.
- 기준정보 세트만 가진 운영자는 «위임 생성» 버튼 자체가 없다(347) — 이 칸에 닿지 않는다.

# Failure Scenarios

1. `403` 을 장애 문구로 덮은 채 둔다 — 운영자가 재시도 · 장애 신고를 한다.
2. 콘솔이 목록 대신 단건 조회를 여러 번 불러 우회한다 — 서버가 열지 않은 목록을 사실상 재구성한다(인가 의도 우회). (나)는 서버에서 결정한다.
3. 수동 입력까지 숨긴다 — 결재 세트만 가진 운영자가 위임을 아예 못 만든다(서버는 허용).
