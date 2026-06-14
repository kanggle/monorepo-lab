# Task ID

TASK-PC-FE-084

# Title

platform-console **ecommerce-ops users absorption** — ADR-MONO-031 **Phase 2b** (console absorption of the standalone admin-dashboard user-management area). Add a **read-only** users list/detail slice to `features/ecommerce-ops` (console-web Route Handler → ecommerce gateway `/api/admin/users`), mirroring the orders slice (TASK-PC-FE-083). The backend tenant isolation prerequisite landed in **TASK-BE-367** (Phase 2a), so the operator-plane read is tenant-scoped.

# Status

done

# Owner

frontend

# Task Tags

- code
- frontend
- console-web
- ecommerce-ops
- adr-031
- absorption

---

# Dependency Markers

- **선행 (prerequisite)**: **TASK-BE-367** (Phase 2a — user-service row-level `tenant_id` + operator-plane `/api/admin/users` tenant isolation; the read this slice consumes is now tenant-scoped) + **TASK-MONO-252** (Phase 0 — ADR-031 ACCEPTED + `console-integration-contract.md` § 2.4.10 ecommerce operations surface) + **TASK-PC-FE-083** (orders slice — the exact replica template).
- **mirror / reference**: **TASK-PC-FE-083** orders slice (`features/ecommerce-ops` orders-api/order-types/orders-state/use-ecommerce-orders/OrdersScreen/OrderDetail + `app/api/ecommerce/orders/**` route handlers + `app/(console)/ecommerce/orders/**` pages + `ConsoleSidebarNav` `주문` leaf). users = the **read-only** subset (no status-transition/mutation).
- **blocks / 후속**: ADR-031 **Phase 3~5** (promotions → shippings → notifications, each gated on its own backend `tenant_id` migration) and **Phase 6** (admin-dashboard deletion, after all 6 areas reach console parity — D7 gate).

# Goal

콘솔 `features/ecommerce-ops` 에 **users 읽기 전용 슬라이스**(목록 + 상세) 추가 — orders 슬라이스 미러, mutation 없음. admin-dashboard `user-management`(목록: email/name/nickname/status/createdAt + email·status 필터; 상세: + phone/profileImageUrl/updatedAt)를 콘솔로 흡수. Route Handler → ecommerce gateway `/api/admin/users` 직접(BFF write leg 없음, ADR-017 D2.A), 도메인-facing OIDC 토큰(`getDomainFacingToken()`, never `getOperatorToken()`), `tenant_id` 는 JWT claim(no `X-Tenant-Id` 헤더). **Source-of-Truth = `console-integration-contract.md` § 2.4.10.1(본 task 가 추가) + ecommerce `user-api.md` § GET /api/admin/users.**

# Scope

## In Scope

### A. console-web `features/ecommerce-ops` users slice (orders 미러)
- `api/users-api.ts` — server-side 단일 call site: `listUsers(params)` + `getUser(userId)`. orders-api 패턴(`getDomainFacingToken`, `ECOMMERCE_TIMEOUT_MS` AbortController, flat 에러봉투 `{code,message,timestamp}` 파싱, 401→ApiError, 403→ApiError, 503/timeout→EcommerceUnavailableError, no `X-Tenant-Id`, no `Idempotency-Key`). **mutation 없음**(`changeOrderStatus` 미복제).
- `api/user-types.ts` — Zod 스키마(`.passthrough()` 관용): `UserSummarySchema`(userId/email/name/nickname/status/createdAt), `UserListSchema`(content[]/page/size/totalElements), `UserDetailSchema`(+phone/profileImageUrl/updatedAt). status enum = `ACTIVE|SUSPENDED|WITHDRAWN`(표시용; **상태머신 없음**).
- `api/users-state.ts` — `getUsersSectionState(eligible, params)` + `getUserDetailSectionState(eligible, userId)` (orders-state 미러: 401→redirect('/login'), 403→forbidden, 404→notFound, 503/network→degraded).
- `hooks/use-ecommerce-users.ts` — `useUsers(params, initial)` + `useUser(userId, initial)` (same-origin `/api/ecommerce/users/**` proxy, seeding + staleTime). query key `['ecommerce-users','list'|'detail',…]`. **mutation hook 없음**.
- `components/UsersScreen.tsx` — 목록 테이블(email/name/nickname/status badge/createdAt) + email 검색 + status 필터 + 페이지네이션 + row→상세. 리질리언스(403 inline / 503·timeout degraded).
- `components/UserDetail.tsx` — 상세(status/email/name/nickname/phone/profileImageUrl/createdAt/updatedAt). **상태 전이 영역 없음**.
- `index.ts` — users export 추가.

### B. Route Handlers (proxy, GET only)
- `app/api/ecommerce/users/route.ts` — `GET` 목록 프록시(query: status/email/page/size) → `listUsers`. orders 의 `_proxy`(`mapEcommerceError`/`newRequestId`) 재사용.
- `app/api/ecommerce/users/[id]/route.ts` — `GET` 단건 프록시 → `getUser`. **POST/PATCH/DELETE 없음**.

### C. Pages (server component, 미러)
- `app/(console)/ecommerce/users/page.tsx` — 목록(`resolveEcommerceEligibility` → `getUsersSectionState` seed → `<UsersScreen>`). waterfall: registryDegraded→notEligible→forbidden→degraded→happy.
- `app/(console)/ecommerce/users/[id]/page.tsx` — 상세(`getUserDetailSectionState` → `<UserDetail>`). +notFound(404 USER_PROFILE_NOT_FOUND).

### D. Sidebar nav
- `ConsoleSidebarNav.tsx` ecommerce children 에 `{ href:'/ecommerce/users', label:'사용자', testid:'nav-ecommerce-users' }` 추가(주문 leaf 옆).

### E. 계약 + 테스트
- `console-integration-contract.md` **§ 2.4.10.1** users sub-binding 추가(§2.4.10 cross-cutting 상속: 자격증명/tenant claim/eligibility/resilience; producer = user-service `AdminUserController` 2 read EP). **본 task 가 계약 먼저 갱신 후 구현.**
- vitest: users-api(자격증명 핀·resilience·proxy), user-types(스키마 tolerance), hooks·state 커버. orders 테스트 패턴 미러.

## Out of Scope

- **user mutation**(status 변경/정지/GDPR 삭제 등) — 백엔드 admin write EP 부재 + Phase 2b 는 read 흡수만. 후속.
- promotions/shippings/notifications 슬라이스 — Phase 3~5.
- admin-dashboard `user-management` 삭제 — Phase 6(전 영역 parity 후 D7 gate).
- OIDC scope 추가(non-IAM 도메인은 `tenant_id`/productKey eligibility 게이팅 — PC-FE-081 결정 답습).
- `seller_id` 표시 — user 도메인 무관.

# Acceptance Criteria

- **AC-1** `features/ecommerce-ops` 에 users 슬라이스(api/types/state/hooks/UsersScreen/UserDetail) — orders 미러, **mutation 0**.
- **AC-2** Route Handler `GET /api/ecommerce/users`(목록) + `GET /api/ecommerce/users/[id]`(단건) — ecommerce gateway `/api/admin/users` 직접, `getDomainFacingToken()`(getOperatorToken 미사용), no `X-Tenant-Id`/`Idempotency-Key`. **POST/PATCH/DELETE 0**.
- **AC-3** Pages(목록/상세) — eligibility waterfall(registryDegraded→notEligible→forbidden→degraded→happy) + 상세 notFound(404). 사이드바 `사용자` leaf.
- **AC-4** 목록 = email/name/nickname/status badge/createdAt + email·status 필터 + 페이지네이션; 상세 = +phone/profileImageUrl/updatedAt.
- **AC-5** 계약 § 2.4.10.1 추가(§2.4.10 상속, producer 2 read EP 명시).
- **AC-6** 검증 GREEN: **tsc + pnpm lint + vitest** 3종(메모리 `env_console_web_local_verify_needs_lint` — CI 두 프런트 잡이 no-unused-vars 로 RED 나므로 push 전 lint 필수). products/orders 슬라이스·BFF·백엔드 0-change.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.10 + **§ 2.4.10.1**(본 task)
- `projects/ecommerce-microservices-platform/specs/contracts/http/user-api.md` § GET /api/admin/users(목록·단건 — producer 계약)
- `projects/platform-console/apps/console-web/architecture.md` ecommerce-ops phase 노트

# Related Contracts

- producer = ecommerce `user-api.md` GET `/api/admin/users`(목록: status/email/page/size → content[userId,email,name,nickname,status,createdAt]/page/size/totalElements) + GET `/api/admin/users/{userId}`(+phone/profileImageUrl/updatedAt). 에러 flat `{code,message,timestamp}`: 401/403/404 USER_PROFILE_NOT_FOUND. **consumer 전용, read-only.**

# Edge Cases

- **eligibility**: registry `productKey=ecommerce` 미가용 → notEligible 빈상태(신규 enum 불요 — PC-FE-081 답습). 403 → inline "권한 없음", 503/timeout → 섹션만 degrade(전역 아님).
- **404 = 빈 상태**: 단건 USER_PROFILE_NOT_FOUND → notFound 빈상태(SCM 메모리 "404=빈상태" 패턴).
- **status 필터**: user-service 는 status 쿼리 지원(ACTIVE/SUSPENDED/WITHDRAWN) → 드롭다운. 없으면 전체.
- **사이드바 rebase 충돌**: ConsoleSidebarNav ecommerce children 편집 — 동시 다른 leaf 추가와 충돌 가능(SCM 메모리). 단일 라인 추가로 최소화.
- **PII**: email/phone/profileImageUrl 절대 로깅 금지(§2.4.10 cross-cutting).

# Failure Scenarios

- BFF 에 write leg 추가하면 ADR-017 D2.A 위반 → Route Handler 직접만(GET).
- `getOperatorToken()` 사용하면 IAM-도메인 스코프 토큰으로 ecommerce 호출 → 401/403. 반드시 `getDomainFacingToken()`.
- pnpm lint 생략하고 push 하면 no-unused-vars 로 CI 두 프런트 잡 RED(tsc+vitest 미적발) → push 전 3종 필수.
- 상세에 mutation/상태전이 영역 복제하면 범위 초과 + 백엔드 write EP 부재로 런타임 404 → read-only 유지.

# Notes

- 분석=Opus 4.8 / **구현 권장=Sonnet** (orders 슬라이스의 read-only mechanical replica — mutation 제거가 주된 차이). worktree=`monorepo-lab-pcfe084`, console-web node_modules = main junction(메모리 패턴). 검증 3종 = `pnpm -C projects/platform-console/apps/console-web run typecheck && pnpm -C … run lint && pnpm -C … run test`(실제 script 명은 package.json 확인).
- ADR-031 Phase 2 = (a) BE-367 백엔드 tenant_id[DONE/PR#1521] + (b) 본 task 콘솔 흡수. 본 task 가 (b).
