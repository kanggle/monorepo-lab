# Task ID

TASK-MONO-202

# Title

cross-project: IAM admin accounts 목록 권한 없음 시 빈 목록 200 → 403 PERMISSION_DENIED 신호 + 콘솔이 "권한 없음" vs "등록된 계정 없음" 구분

# Status

done

# Owner

cross-project (Opus 4.8 analysis / Opus impl — authz contract change). iam-platform producer(admin-service) + console consumer. **Atomic PR** (iam 단독 머지 시 no-permission 운영자가 콘솔 초기 로드 403→redirect 루프).

# Task Tags

- code
- test
- contract

---

# Dependency Markers

- **user request (2026-06-09)** — 계정 운영에서 권한이 없으면 "권한이 없습니다", 계정이 0건이면 "등록된 계정이 없습니다"로 **명확히 구분**.
- **AskUserQuestion 결정 (2026-06-09)** — "백엔드 403 신호 추가 (정확)": producer가 `account.read` 없을 때 **빈 목록 200 대신 403 PERMISSION_DENIED** 반환하도록 변경(현재 계약 §2.4.1 = 의도적 empty-200 → 뒤집음). 콘솔이 데이터 계층에서 정확히 구분.
- **현재 동작 (Explore 검증)**: `AccountAdminController.search` list 분기 = `!hasPermission(ACCOUNT_READ)` → `return ResponseEntity.ok(EMPTY_PAGE)`. email 단건검색은 account.read 불필요(계약, 유지). `PermissionDeniedException` → `AdminExceptionHandler.handlePermission` → 403 `{code:PERMISSION_DENIED}`. `AuditController`가 동일 "수동 권한체크 → `auditor.recordDenied` + throw" 선례.
- **atomic 근거**: 콘솔 `getAccountsListState`는 현재 `401||403 → redirect('/login')`. iam만 먼저 403화하면 no-permission 운영자 초기 로드가 redirect 루프. 따라서 콘솔의 403=forbidden(비-redirect) 처리와 **반드시 동시 머지**.

# Goal

`account.read` 없는 list 조회를 **403 PERMISSION_DENIED**로(감사 DENIED 기록 포함, `AuditController` 패턴). email 단건검색은 무권한 유지. 콘솔은 403→"조회 권한이 없습니다.", 빈 목록(200)→"등록된 계정이 없습니다.", 검색+빈→"검색 결과가 없습니다."로 구분(union 제거). 콘솔 401만 재로그인, 403은 forbidden 상태.

# Scope

## In Scope (iam-platform — producer, authoritative)

- **`apps/admin-service/.../presentation/AccountAdminController.java`** — list 분기 `return ResponseEntity.ok(EMPTY_PAGE)` → `auditor.recordDenied(null, Permission.ACCOUNT_READ, request.getRequestURI(), request.getMethod(), null)` + `throw new PermissionDeniedException("Operator lacks required permission: " + Permission.ACCOUNT_READ)`. `AdminActionAuditor auditor` final 필드 주입 + `HttpServletRequest request` 파라미터 추가. `EMPTY_PAGE` 상수 제거(미사용화). email 단건검색 분기 불변(무권한).
- **`apps/admin-service/.../test/.../AccountAdminControllerTest.java`** — `search_noEmail_noAccountReadPermission_returns_empty` → `..._returns_403`: `status().isForbidden()` + `jsonPath("$.code").value("PERMISSION_DENIED")`. (테스트엔 이미 `@MockitoBean AdminActionAuditor auditor` 존재 — 생성자 의존성 추가 무비용.)
- **`specs/contracts/http/admin-api.md`** — `GET /api/admin/accounts` 절: "`account.read` 권한 미보유 → 빈 목록 반환 (403 아님)" → "→ **403 PERMISSION_DENIED**" + 응답 예시에서 무권한 empty-200 케이스를 403으로 교체. email 단건검색 무권한 유지 명시.

## In Scope (platform-console — consumer, follows §5 Change Rule)

- **`specs/contracts/console-integration-contract.md`** §2.4.1/§2.5 — "`account.read` absent ⇒ producer returns an empty list (not 403) ⇒ … empty/insufficient-permission state" → "**403 PERMISSION_DENIED** ⇒ console renders a distinct 권한 없음 state; an empty 200 list now unambiguously means 0 accounts."
- **`apps/console-web/src/features/accounts/api/accounts-state.ts`** — `AccountsListState`에 `forbidden: boolean` 추가. catch: `401 → redirect('/login')`; **403(또는 code PERMISSION_DENIED) → `{page:null, degraded:false, noTenant:false, forbidden:true, query}` (redirect 안 함)**; 나머지 분기 `forbidden:false`.
- **`apps/console-web/src/app/(console)/accounts/page.tsx`** — `state.forbidden` 분기 추가(degraded/!page 분기 **이전**): 계정 운영 헤더 + `data-testid="accounts-forbidden"` "조회 권한이 없습니다." notice.
- **`apps/console-web/src/features/accounts/lib/classify-empty.ts`** — unfiltered-empty: `'forbidden-or-empty'` union 제거 → `reason:'empty'`, `'등록된 계정이 없습니다.'`. `'forbidden'`(클라 403, mid-session 권한회수 등)·`'no-results'`·`'load-error'` 유지. doc 갱신(empty-200=권한없음 더 이상 아님).
- **`apps/console-web/src/features/accounts/components/AccountsScreen.tsx`** — `accounts-degraded` 배너를 search.error가 403/PERMISSION_DENIED일 때 **억제**(forbidden empty-state만 표시). 빈 분류 wiring 유지.
- **Tests**: `accounts-empty-state.test.ts`(unfiltered-empty→'empty'/"등록된 계정이 없습니다", 메시지/이유 갱신), 필요 시 accounts page/state forbidden 테스트.

## Out of Scope

- account.read 외 권한 모델·다른 admin 엔드포인트.
- email 단건검색 권한 정책 변경(무권한 유지).
- 도메인 health/사이드바(별 task PC-FE-068).

# Acceptance Criteria

- [ ] iam: `account.read` 없는 list 조회 → 403 `{code:PERMISSION_DENIED}` + DENIED 감사 1행; email 단건검색은 무권한 200 유지; superadmin list 200 유지.
- [ ] iam 계약 `admin-api.md` + 콘솔 계약 §2.4.1/§2.5 = 403 신호로 정합.
- [ ] 콘솔: 403→"조회 권한이 없습니다."(redirect 안 함), 빈200(무필터)→"등록된 계정이 없습니다.", 검색+빈→"검색 결과가 없습니다.", 401→재로그인.
- [ ] iam `./gradlew :apps:admin-service:test` green(no-permission 403 테스트 포함), 콘솔 `vitest`+`tsc --noEmit` green.
- [ ] **Atomic PR** (iam+console 동시).

# Related Specs

- `projects/iam-platform/specs/contracts/http/admin-api.md` (producer, authoritative — spec-first 변경).
- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.4.1/§2.5 (consumer, follows).

# Related Contracts

- 위 두 계약 변경.

# Target Service

- `iam-platform/apps/admin-service` (producer) + `platform-console/apps/console-web` (consumer).

# Architecture

- 단일 권한 결정 site = producer(admin-service RBAC). 콘솔은 표시 전용 — producer 403을 그대로 신뢰(클라가 권한 재평가 안 함). email 단건검색의 무권한 허용은 SUPPORT_LOCK류가 단건 잠금 위해 조회하는 계약 기능이라 유지.

# Edge Cases

- SUPPORT_LOCK(account.lock 보유, account.read 미보유) → 초기 무필터 로드 403 → 콘솔 forbidden notice(페이지 레벨). email 단건검색은 producer 200(무권한 허용)이지만 페이지 레벨 forbidden이 화면을 대체 → 단건검색 표면 상실은 데모 비대상 role의 minor 제약(수용; 필요 시 후속).
- mid-session 권한 회수 → 클라 재쿼리 403 → classify 'forbidden' + degraded 배너 억제.
- 빈 200(권한 보유, 0계정) → "등록된 계정이 없습니다."

# Failure Scenarios

- 콘솔 403에서 여전히 redirect('/login') → no-permission 운영자 무한 루프. AC가 "403=forbidden, 비-redirect" 단언.
- iam만 머지(staggered) → 위 루프가 main에. atomic PR로 방지.
- email 단건검색에 account.read 강제(메서드 전체 @RequiresPermission) → 계약 위반. list 분기에서만 throw로 회피.

# Definition of Done

- [ ] iam producer 403 + 감사 + 계약, 콘솔 forbidden 구분 + 계약, 양측 테스트
- [ ] iam test + 콘솔 vitest/tsc green
- [ ] Atomic cross-project PR
- [ ] Acceptance Criteria 충족
- [ ] Ready for review
