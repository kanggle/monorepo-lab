# Task ID

TASK-PC-FE-343

# Title

`ADR-MONO-083` P6 콘솔 후속 — ERP 메뉴 «업무 권한 없음» · 쓰기 버튼을 ERP 세분 역할로 (`permission-map.ts` `serverRequires` / `writeRequires` · 계약 § 2.4.3.4) · 알림 종이 ERP 역할 없는 사용자에게 ERP 갈래를 부르지 않기

# Status

done

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (데이터 행 · 계약 표 · 알림 집계의 갈래 선택 하나 — 규칙 신설 없음)
> ⏳ **머지 게이트 — «`TASK-MONO-793` 을 실은 AMI 굽기가 라이브 확인된 뒤에만 머지»** (소유자 결정 2026-10-15 UTC, `TASK-MONO-793` § 소유자 결정 Q5 · 순서). 콘솔은 머지 즉시 Vercel 로 나가고 백엔드는 굽기에서만 나간다. 굽기 전에 머지하면 라이브 `GET /api/admin/me/domain-access` 가 아직 `ERP_OPERATOR` 만 내므로 **모든 운영자의 ERP 메뉴에 거짓 «업무 권한 없음» + 등록 · 수정 · 폐기 · 상신 · 승인 버튼 숨김 + 알림 종의 ERP 갈래 사라짐** — 서버는 열어 주는데 화면이 닫는다. 반대(굽기 뒤 · 이 PR 전)는 조회 세트 운영자에게 쓰기 버튼이 보이고 누르면 서버 403(화면 안 «권한 없음»), ERP 역할 없는 사용자의 알림 종이 `degradedDomains ∋ erp` — 안전 쪽 실패.

# Dependency Markers

- **선행**: `TASK-MONO-793`(머지 + 그 커밋을 담은 굽기 + 라이브 확인 — `TASK-MONO-672` § Next window — TASK-MONO-793). 이 티켓의 AC-0 이 그 라이브 확인을 다시 잰다.
- 형제: `TASK-PC-FE-342`(P5 SCM — 같은 모양) · `TASK-PC-FE-338`(c-2 — 메뉴 단위 `serverRequires` · `writeRequires` 규칙을 만든 티켓, 이 티켓은 **데이터만** 더한다 — 계약 § 2.4.3.4 «Scales with P5–P8»).

# Goal

`TASK-MONO-793` 이 ERP 4 서비스의 업무 인가를 세분했다(erp `iam-integration.md` § 업무 역할 — 정본). 콘솔의 ERP 메뉴 · 버튼 신호와 알림 종의 ERP 갈래를 서버 판정과 같게 만든다.

# Scope

## In Scope

- 계약 먼저: `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.3.4 — «SCM · ERP · Finance leaves | — | … `authenticated()` only» 줄에서 ERP 행을 떼어 아래 대조표로 · «Scales with P5–P8» 에 P6 완료 표시 · 알림 종(§ 2.4.8 알림 집계) 의 ERP 갈래 선택 규칙 한 줄.
- `apps/console-web/src/shared/guide/permission-map.ts` ERP 행(`/erp` · `/erp/masters` · `/erp/orgview` · `/erp/approval` · `/erp/delegation`, `/erp/guide` 제외):
  - `gate.roles` = ERP 카탈로그 역할(상한) — `ERP_OPERATOR` 단독이 아니라 4개 업무 역할.
  - `serverRequires`(기본 조회) · `writeRequires`(변경 버튼) — 아래 대조표 대로.
- 알림 종(`shared/composition/notification-inbox.ts`): `/me/domain-access` 가 ERP 업무 역할 0 을 말하면 ERP 갈래를 **부르지 않는다**(지금은 부르고 403 → `degradedDomains ∋ erp`). `/me/domain-access` 실패 · 시간 초과면 지금처럼 부른다.
- 시험: `permission-map-drift` · 메뉴 신호 · 버튼 숨김 · 알림 갈래 선택 단위 시험.

## Out of Scope

- 서버 판정 변경(793 에서 끝났다) · Finance · E-commerce(P7 ~ P8 의 콘솔 후속).
- IAM 운영자 화면의 `org_scope` 부서 선택기 — 기준정보 조회 역할 없는 관리자의 403 → 수동 id 입력 저하는 **소유자가 수용**(793 Q5). 바꾸지 않는다.
- 새 신호 규칙(계약 § 2.4.3.4 의 규칙은 그대로 — 데이터만).

# 서버 판정 대조표 (`TASK-MONO-793` 기준 — AC-0 에서 다시 잰다)

| 화면 | 기본 조회가 부르는 것 | `serverRequires` | 변경 버튼 | `writeRequires` |
|---|---|---|---|---|
| `/erp` 개요 | masterdata 목록 5 · approval 결재함 · 위임 | 둘 중 하나라도 — `ERP_MASTERDATA_READ/WRITE` · `ERP_APPROVAL_READ/WRITE` (타일별 저하는 지금 규칙) | — | — |
| `/erp/masters` | masterdata 목록 | `ERP_MASTERDATA_READ` · `ERP_MASTERDATA_WRITE` | 등록 · 수정 · 폐기 · 부서 이동 · 계정 연결 제안/철회 | `ERP_MASTERDATA_WRITE` |
| `/erp/orgview` | read-model `employees` (+ 위임 사실 카드) | `ERP_MASTERDATA_READ` · `_WRITE` — 🔵 위임 사실 카드는 `ERP_APPROVAL_READ/WRITE` 라 기준정보 세트만이면 그 카드만 «권한 없음» | (READ-ONLY) | — |
| `/erp/approval` | approval `requests` · `inbox` | `ERP_APPROVAL_READ` · `_WRITE` | 상신 · 승인 · 반려 · 회수 | `ERP_APPROVAL_WRITE` |
| `/erp/delegation` | approval `delegations` | `ERP_APPROVAL_READ` · `_WRITE` | 위임 등록 · 회수 | `ERP_APPROVAL_WRITE` |
| 알림 종 | notification inbox | ERP 업무 역할 하나라도(개인 엔드포인트) | (읽음 표시 = 개인) | — |
| 계정 연결 수락 · 거절(내 제안) | masterdata `/account-link-proposals/mine` | ERP 업무 역할 하나라도(개인 엔드포인트) | 수락 · 거절 | 같은 조건 |

# Acceptance Criteria

- [x] **AC-0** — 🔴 머지 게이트 실측: `TASK-MONO-793` 을 담은 AMI 가 라이브인가(굽기 기록 · `GET /api/admin/domain-roles` 가 `ERP_MASTERDATA_READ` 를 내는가 · 라이브 `/me/domain-access` 가 데모 운영자에게 ERP 4 역할을 내는가). 아니면 **이 티켓은 시작하지 않는다**. 위 대조표를 서버 코드(masterdata `RoleScopeAuthorizationAdapter` · approval `ActorContext` · read-model / notification `ReadAuthorizationGate`)와 다시 대조.
  - ✅ (2026-10-21 UTC 정리 · 소유자 결정 «근거를 적고 체크 → done») 게이트 = **충족**: TASK-MONO-793 를 실은 굽기 `ami-00ef8cc1057cca59b` @ `8905d2c29` 가 28차 창(2026-10-16)에서 라이브 확인된 뒤 머지(§ Merge gate — 28th window · `TASK-MONO-672` § 28th window — harvest: `demo@` assume `demo-corp` 토큰 roles 에 ERP 4 역할 · V0056–V0059 행 수). 서버 코드 대조 = § 구현 기록 «AC-0 — 오늘 거짓 배지 · 숨김이 있는가 · 서버 대조»(`RoleScopeAuthorizationAdapter:55-58` · `ActorContext:59,69` · `ReadAuthorizationGate` 둘). 🔵 원문의 두 하위 항목 중 `GET /api/admin/domain-roles` 는 **문자 그대로 잰 기록이 없다** — 카탈로그 코드 대조로 갈음. 라이브 `/me/domain-access` 는 32차 창 화면(데모 운영자 배지 없음 · 세트 0 배지 있음 — 판별력 있는 대조)으로 **간접** 확인.
- [x] **AC-1** — 계약 § 2.4.3.4 · § 2.4.8 개정이 코드보다 먼저. → red 커밋 `88d89e571`.
- [x] **AC-2** — 🔴 대조군: 조회 세트(`erp-read`) → ERP 메뉴 배지 없음 · 쓰기 버튼 없음 / 결재 세트만 → `/erp/masters` · `/erp/orgview` 배지, `/erp/approval` 버튼 보임 / 기준정보 세트만 → `/erp/approval` · `/erp/delegation` 배지 / ERP 세트 0 → ERP 메뉴 전부 배지 · 알림 종이 ERP 갈래를 부르지 않음(`degradedDomains` 에 erp 없음). → **메뉴 · 알림 종 몫 닫힘**(`console-nav-business-permission.test.ts` § TASK-PC-FE-343 · `sidebar-business-permission.test.tsx` · `notification-routes.test.ts` § TASK-PC-FE-343). **버튼 몫(«쓰기 버튼 없음» · «`/erp/approval` 버튼 보임») 은 `TASK-PC-FE-347` 로** — § 범위 결정.
  - ✅ (2026-10-21 UTC 정리) 메뉴 절반 = 이 티켓(단위 시험 + 32차 창 라이브), 버튼 절반 = `TASK-PC-FE-347` 로 이관 · 그 티켓 **done**(32차 창 라이브 포함). 두 절반이 모두 닫혀 AC-2 를 닫는다.
- [x] **AC-3** — 실패 경로: `/me/domain-access` 실패 · 시간 초과 → 배지 없음 · 버튼 보임 · 알림 종은 ERP 갈래를 부른다(지금 규칙 그대로). → row 12 `null`(실패 · 시간 초과 — `getMyDomainRolesOrNull` 이 모든 실패를 `null` 로) → 배지 없음 · 갈래 호출, 테넌트 없음 · `'*'` 도 호출.
- [x] **AC-4** — 배포 상태(Vercel · 라이브 화면)를 따로 적는다. → § 32차 창 라이브.
  - 🔵 Vercel 절반 ✅ (2026-10-18 UTC 측정): 머지 커밋 `82d857680` → `Production – kanggle-console` 배포 7129951620 state=success (2026-10-16T21:30:42Z). 🔴 라이브 화면 절반(로그인 운영자 · 세트별 배지/버튼)은 백엔드가 필요 — 다음 데모 창(`TASK-MONO-672` § Order «Still open» 10). 이 줄만으로 AC-4 를 닫지 않는다.

# Related Specs

- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md` D9 · P6
- `projects/erp-platform/specs/integration/iam-integration.md` § 업무 역할 (서버 판정 정본)

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.3.4 · § 2.4.8
- `projects/iam-platform/specs/contracts/http/admin-api.md` § 카탈로그 (`/me/domain-access`)

# Edge Cases

- `ERP_OPERATOR` 는 `/me/domain-access` 에 **나오지 않는다**(카탈로그 밖 · auth 파생) — 신호 입력으로 쓰지 않는다.
- 테넌트를 고르지 않은 운영자(기본 토큰 — 역할 0)는 ERP 전 화면이 서버 403 이다(793 Q1, SCM 과 같음) — 신호도 «업무 권한 없음» 이 맞다.
- 결재 대상 확인(`GET /departments/{id}` · `/employees/{id}`)은 `ERP_APPROVAL_WRITE` 도 받는다 — 결재 화면의 대상 표시가 기준정보 세트 없이도 뜬다.

# Failure Scenarios

1. 굽기 전에 머지 — 위 머지 게이트의 거짓 배지 · 버튼 숨김 · 알림 갈래 소실.
2. `ERP_OPERATOR` 를 `serverRequires` 에 넣는다 — 진입 역할은 업무 API 를 열지 않으므로 신호가 서버보다 넓어진다.
3. 알림 종이 `/me/domain-access` 실패를 «역할 0» 으로 읽는다 — ERP 역할 보유자의 알림이 사라진다(실패는 지금처럼 부른다).

# Merge gate — 28th window

- 2026-10-16 UTC — gate met: the bake carrying 792/793/794/795 was live-verified in the 28th window (2026-10-16) — may now be implemented/merged. (AMI `ami-00ef8cc1057cca59b` @ `8905d2c29`; this ticket's source `TASK-MONO-793` → `done/`; results in `TASK-MONO-672` § 28th window — harvest.)

# 구현 기록 — 2026-10-16 UTC (branch `task/pc-fe-343-erp-nav`, base `d64a6b8a7`)

## AC-0 — 오늘 거짓 배지 · 숨김이 있는가 (코디네이터 질문) · 서버 대조

**판정: 거짓 «업무 권한 없음» · 거짓 버튼 숨김은 없다 — 반대 방향(덜 알림)만 있다.**
- c-2 는 ERP 5 잎에 `serverRequires` · `writeRequires` 를 **선언하지 않았다** → `leafLacksBusinessPermission` 은 선언 없음에서 `false`, `canWriteOn` 은 선언 없음에서 `true`. row 12 가 무엇을 내든(V0057 뒤: `ERP_OPERATOR` 삭제 · 4 역할 — `V0057:45-58` 이 V0059 와 같은 «넓힌 뒤 지움») ERP 에는 배지도 숨김도 생기지 않는다.
- 남는 것(안전 쪽): 793 뒤 «구독 합성 READ 는 없다» 라 ERP 역할 없는 운영자는 ERP 화면 전부 서버 `403` 인데 메뉴에 배지가 없다(화면 안 «권한 없음» 으로만 안다). 알림 종은 그 운영자에게 ERP 갈래를 부르고 `403` → `degradedDomains ∋ erp`.
- ⇒ 345 같은 라이브 퇴행은 아니다. 이 PR 은 «덜 알림» 을 서버와 같게 맞춘다.

서버 대조(base `d64a6b8a7`, erp `iam-integration.md` § 업무 역할 `:106-138` 정본): masterdata `RoleScopeAuthorizationAdapter:55-58` · approval `ActorContext:59,69` · read-model `ReadAuthorizationGate:45-48` · notification `ReadAuthorizationGate:57-80`(ERP 4 역할 ∨ 파생 `ERP_OPERATOR` ∨ `erp.read/write` ∨ `tenant_id='*'`). ⇒ 티켓 대조표 그대로:
- `/erp` 개요 = MD ∪ AP 의 READ/WRITE(타일별 저하) · `/erp/masters` · `/erp/orgview` = MD_READ/WRITE · `/erp/approval` · `/erp/delegation` = AP_READ/WRITE.
- 알림 종: 개인 엔드포인트 = ERP 업무 역할 하나라도 ∨ `tenant_id='*'`. 콘솔의 도메인 대면 토큰 = 활성 테넌트가 있으면 그 테넌트의 assume 토큰(`session.ts` `getDomainFacingToken`) — 그 `roles` = row 12 ∩ 구독(P3). ⇒ row 12 에 ERP 역할이 0 이면 그 토큰은 `403` 을 받는다 — 부르지 않아도 잃는 것이 없다. `'*'` 테넌트는 역할 없이 열리므로 예외.

## 범위 결정 — 쓰기 버튼은 분리 (`TASK-PC-FE-347`)

`writeRequires`(기준정보 MD_WRITE · 결재/위임 AP_WRITE)는 **선언하지 않았다** — `TASK-PC-FE-345` → `346` 과 같은 이유. 버튼은 `features/erp-ops/components` 의 17 컴포넌트(기준정보 5 목록 + 쓰기 대화상자 3 · 결재 화면/상신/상세 · 위임 화면/목록)에 흩어져 있고 어느 것도 `canWrite` 를 받지 않는다. 배선 없는 선언은 계약이 «숨는다» 고 거짓말하게 한다. 이 PR = 메뉴 신호 + 알림 종(티켓의 다른 두 몫), 버튼 = `TASK-PC-FE-347`(ready, 이 PR 에서 기안). 그 사이 조회 세트 운영자는 쓰기 버튼을 보고 누르면 서버 `403` → inline «권한 없음»(안전 쪽, 지금과 같음). ⇒ AC-2 의 «쓰기 버튼 없음» · «`/erp/approval` 버튼 보임» 은 347 로.

## e2e 핀 조정 (코디네이터 지시)

이 PR 뒤 ERP 상위 토글은 «업무 권한 없음» 을 달 수 있다(ERP 역할 0 · ERP 구독 테넌트). `tests/e2e/overview-consolidation.spec.ts` 의 `/^ERP(구독 필요)?$/` → **`/^ERP(구독 필요|업무 권한 없음)?$/`** — 라벨은 그대로 정확히, 배지는 많아야 하나(두 신호는 겹치지 않는다 — «구독 필요» 가 이긴다). 단위 시험(`sidebar-business-permission.test.tsx`)이 두 방향을 고정한다(역할 0 → `ERP업무 권한 없음` · 4 역할 → `ERP`). e2e · smoke · federation 의 다른 `nav-erp` 텍스트 핀 0(grep — 나머지는 `href` 속성 핀).

## Red — 커밋 `88d89e571` (계약 · 시험 · e2e 핀 · 티켓만, 제품 코드 0)

- 로컬 전체: **8 failed | 4595 passed (4603)** — `console-nav-business-permission.test.ts` 5 · `notification-routes.test.ts` 2 · `sidebar-business-permission.test.tsx` 1. 7 = `AssertionError`, 1 = jest-dom 매처 단언(`expect(element).toHaveTextContent()` — 기대 `ERP업무 권한 없음`, 실제 `ERP`). `tsc` rc=0.
- CI run `38964505930`, `Frontend unit tests (… console-web, vitest)` job **`117187593284`**: console-web **8 failed | 4595 passed (4603)**, 실패 파일 3 — 같은 8 건 · 같은 종류(로그에서 셈).
- 🔵 red 에서 이미 통과하는 대조군(설계상): legacy-compat 4 역할 → 배지 없음 · `erp-read` → 배지 없음 · 알림 갈래의 fail-open 4 건(row 12 실패 · 테넌트 없음 · `'*'` · ERP 역할 보유 → 부른다) — 지금 행동이 이미 그것이라 green 이 그것을 깨지 않는지를 잰다.

## Green

- `permission-map.ts`: `ERP_ROLES`(4) · `ERP_GET` · 5 행 `roles` = 4 · `serverRequires` = 대조표(개요 = MD ∪ AP).
- `shared/composition/notification-inbox.ts` `inboxDomainsWorthAsking(domains, activeTenant, myDomainRoles)` + 알림 route 가 활성 테넌트(≠`'*'`)일 때만 row 12 를 묻고 그 결과로 갈래를 고른다.
- 로컬: `npx vitest run` **399 files / 4603 passed** · `tsc` rc=0 · `next lint` rc=0(경고 1 = 기존 `received-invitations/route.ts:26`).
- CI 커밋 `a21b54c03` run `38965751601`: `Frontend unit tests (… console-web, vitest)` job **`117191759384`** — **399 files / 4603 passed**(로그에서 셈) · `Backend fetch resolution` pass(알림 route 의 새 row 12 호출) · 실행된 검사 14 전부 pass · 52 skipped.
- nightly(브랜치 dispatch) run **`38965748396`** = success(15 job) · 콘솔 job **`117192025297`**: **11 passed · 1 flaky · 0 failed**. flaky = `overview-consolidation.spec.ts:133` «IAM card on the home overview …» 첫 시도 `getByTestId('operator-overview-card-iam')` **element(s) not found**(5 s) → 재시도 통과. ERP 핀 시험(`:75`)은 첫 시도 통과. 🔵 이 변경이 닿지 않는 카드(IAM 개요 카드)이고 개요 SSR 지연 계열(`TASK-PC-FE-341` 은 같은 개요의 문서 10 s TIMEOUT)로 보이나, 사유 문구가 341 과 **같지 않다**(TIMEOUT 아님 — 카드 미렌더). 알림 종이 row 12 를 한 번 더 부르는 것이 개요 첫 렌더와 겹칠 수 있어 **원인 배제는 못 했다** — 표본을 늘려 재실행:
- 두 번째 nightly run **`38967164204`**(같은 커밋 `a21b54c03`) = success(15 job) · 콘솔 job **`117196733812`**: **12 passed · 0 flaky · 0 failed**. ⇒ 같은 코드 2 런 중 1 런 flaky 1. 342 · 345 브랜치 nightly 는 각 12/0. 표본 3 개로는 «이 변경이 개요 첫 렌더를 늦춘다» 를 확인도 배제도 못 한다 — ⚪, 머지 뒤 정기 nightly 에서 `:133` 첫 시도 실패율을 본다(341 의 관찰 칸과 같은 자리).
- ⚪ AC-4: `main` 미머지(DRAFT) · Vercel 미배포 · 라이브 화면 미확인. 백엔드 변경 없음(굽기 불필요) — 다음 창: ERP 역할 0 운영자의 ERP 메뉴 배지 · 종의 `degradedDomains` 에 erp 없음 · 데모 운영자는 배지 없음.

## 함께 적는 것

- 🔵 가이드 문안이 지금 틀림(범위 밖 — 코디네이터가 끝에 후속 기안): `features/scm-guide/data.ts:363-369` · `features/iam-guide/data.ts:139,683`(도메인 역할 목록에 `ERP_OPERATOR` 등 진입 역할).
- 🔵 알림 종 비용: 활성 테넌트가 있는 종 열기마다 row 12 호출 1 회가 늘었다(사이드바도 같은 호출을 레이아웃에서 한다 — 캐시 공유 없음). ERP 역할 0 운영자에게는 ERP 호출 1 회가 줄었다.

## 32차 창 라이브 (2026-10-20 UTC · `console` + 콘솔 묶음 다섯 · 소유자 브라우저 + 에이전트 세트 전환)

주체 `requester@demo.com`(`demo-corp`)의 세트를 한 조합씩 바꿨다(에이전트 `swap.sh` — 스냅샷 = `<domain>-full` 다섯 DIRECT, 끝에 복원 → assume 역할 42 개 스냅샷과 동일 · PASS 9 FAIL 0). 비교 기준 `demo@demo.com`(`legacy-compat`).
- 배포: Vercel `7129951620`(`82d857680`) success(10-18 기록).
- demo@: ERP 메뉴 배지 없음 ✅. ERP 세트 0(`scm-read` + `wms-full`): ERP 메뉴 배지 ✅ · **알림 종 «새 알림이 없습니다» — ERP 저하 보고 없음** ✅.
- `ecommerce-order-operate` · `erp-approval-operate` · `finance-ledger-operate`: ERP «마스터 · 통합 조회» 배지 · «결재함 · 위임» 배지 없음 ✅.
- 🔴 `in-progress` 유지 — AC-0 체크박스가 비어 있다.


## 닫기 (2026-10-21 UTC — 4차원 확인)

- (a) #4400 `state=MERGED` · (b) `82d857680` 가 `origin/main` 조상 · (c) 머지 시점 체크 SUCCESS 14 · 실패 0 · (d) AC-0~AC-4 전부 닫힘(AC-0 · AC-2 · 위 2026-10-21 줄, AC-4 = Vercel 7129951620 success + § 32차 창 라이브).
- 빈 체크칸은 «체크만 빠진 것»으로 판정했다 — 본문 기록은 이미 있었다. in-progress → done.
