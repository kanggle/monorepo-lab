# TASK-PC-FE-187 — partner-console 표면 (cross-org 파트너십 관리 UI: host invite/list/terminate + partner accept/participant)

**Status:** done
**Area:** platform-console / console-web · **Scope:** `(console)/partnerships` 화면 + partnership 프록시 라우트 + operator-gated 변이 클라이언트 + 사이드바
**Type:** admin-plane operator-gated CRUD UI 슬라이스 (백엔드가 confinement 전량 강제 — UI 는 표면만; PC-FE-183 구독 UI + operators 분할 패턴 미러)
**Depends on:** TASK-BE-476/477/478 (ADR-MONO-045 §3.4 step 1/2a/2b — `POST/GET/:accept/:suspend/:reactivate/:terminate /api/admin/partnerships`, `POST|DELETE .../participants/{operatorId}`, `partnership.manage` 게이트, cross-org confinement + assume-tenant cap — **전부 main 머지 완료**), ADR-MONO-023 (IAM↔entitlement 평면 분리)
**Analysis model:** Opus 4.8 · **Impl model:** Opus (7 엔드포인트 · two-sided role-gated 렌더 · scope-set 입력 · 비균일 헤더 매트릭스 — ADR-045 §3.4 는 Sonnet 명시했으나 관측된 범위상 Opus 로 상향)

## Goal

ADR-MONO-045 §3.4 로드맵의 **step 3(마지막)** — cross-org 파트너십의 **operator-facing 관리 표면**을 console-web 에 얹어 end-to-end 를 증명한다: **A(host) 가 invite → B(partner) 가 accept → B-operator 가 slice 내에서 A 를 assume(step 2b) → offboard/terminate → 접근 소멸**. 백엔드 계약(BE-476/477/478)은 완비돼 있으므로(신규 BE 0) 이 task 는 그 위에 얇은 UI 를 얹는다. 파트너십은 **관계 상태**만 다루고 파생 도메인-운영 권한은 assume-tenant 발급 시 캡된다 — 이 UI 는 admin 권한을 조직 경계 너머로 확장하지 **않는다**(백엔드가 강제; UI 는 표시·호출만).

## 설계 판정 — 미러 소스 = operators 분할 패턴 (subscriptions 아님)

파트너십은 (a) **실제 list GET** 이 있고(구독처럼 카탈로그 파생 불필요 → `getPartnershipsListState` SSR 게이트 = operators 미러), (b) **비균일 헤더 매트릭스**(invite=Idempotency-Key+body·reason / accept·suspend·reactivate·terminate=path-verb+reason / participant add=path+optional body+reason / participant remove=path+reason·204 / list=헤더 최소·reason 無)라서 operators `CallOptions` 매트릭스를 미러한다(subscriptions 의 단일-shape 코어보다 operators 코어에 가깝다). colon-verb(`:accept`)는 이 앱에 선례가 없다 → **REST 세그먼트로 프록시**(`/api/partnerships/[id]/accept` 등), 프록시가 producer 의 colon-verb(`{id}:accept`)로 매핑.

## Scope

**IN:**

- `src/shared/config/env.ts` — `PARTNERSHIPS_TIMEOUT_MS`(z.coerce.number int positive default 5000) — **schema + `getServerEnv()` 반환 양쪽**. base URL 은 기존 `IAM_ADMIN_API_BASE` 재사용(동일 admin-service).
- `src/shared/api/errors.ts` — `PartnershipsUnavailableError`(`SubscriptionsUnavailableError` verbatim 복제·rename, `reason: 'timeout'|'circuit_open'|'downstream'` + `code`) + MESSAGES 항목: `PARTNERSHIP_SCOPE_DENIED`/`PARTNERSHIP_SCOPE_INVALID`/`PARTICIPANT_NOT_OWN_OPERATOR`/`PARTICIPANT_SCOPE_EXCEEDS_DELEGATION`/`PARTNERSHIP_NOT_FOUND`/`PARTICIPANT_NOT_FOUND`/`PARTNERSHIP_ALREADY_EXISTS`/`PARTNERSHIP_TRANSITION_INVALID`(+공용 `NO_ACTIVE_TENANT`/`PERMISSION_DENIED`/`REASON_REQUIRED`/`TENANT_NOT_FOUND`/`OPERATOR_NOT_FOUND` 는 기존 재사용).
- `src/features/partnerships/api/partnerships-client.ts` (신규) — hardened operator-token 코어(operators-client 미러, feature-격리 복제): operator token + `X-Tenant-Id`(서버 activeTenant) + `X-Operator-Reason`(percent-encode, 비-Latin1) + **per-endpoint `CallOptions` 매트릭스**(`reason?`·`idempotencyKey?`·`expectNoContent?`) + taxonomy(401→ApiError·503→PartnershipsUnavailableError·403/404/409/400/422→ApiError·AbortError→timeout). 토큰 미로깅. `PARTNERSHIPS_PREFIX='/api/admin/partnerships'`.
- `src/features/partnerships/api/types.ts` — zod: `PartnershipStatusSchema`(PENDING/ACTIVE/SUSPENDED/TERMINATED), `ScopeSetSchema`({domains:string[], roles:string[]}), `PartnershipSchema`(partnershipId/hostTenantId/partnerTenantId/status/delegatedScope/myRole('host'|'partner')/invitedAt/acceptedAt?/participantCount), `PartnershipListSchema`(items/page/size/totalElements/totalPages), 요청 DTO(`InvitePartnershipInput`/`ParticipantAddInput`).
- `src/features/partnerships/api/partnerships-api.ts` — 서버-only fns: `listPartnerships(filter?)`(GET) / `invitePartnership(partnerTenantId, delegatedScope, reason)`(POST, idempotencyKey 생성) / `acceptPartnership(id, reason)` / `suspend|reactivate|terminatePartnership(id, reason)` / `addParticipant(id, operatorId, participantScope|null, reason)` / `removeParticipant(id, operatorId, reason)`. **tenantId 는 항상 서버 activeTenant**(클라 미제공).
- `src/features/partnerships/api/partnerships-state.ts` — `getPartnershipsListState()` → `{ page, noTenant, degraded, permissionError }`(operators-state 미러: 401→호출측 redirect 신호, NO_ACTIVE_TENANT→noTenant, 403→permissionError, Unavailable→degraded).
- `src/app/api/partnerships/route.ts` — `POST`(invite: body zod `.strict()` {partnerTenantId, delegatedScope, reason}) + `GET`(list: query role/status/page/size passthrough). `runtime='nodejs'`.
- `src/app/api/partnerships/[id]/accept/route.ts`·`suspend/`·`reactivate/`·`terminate/route.ts` — 각 `POST`(body {reason}), 프록시가 producer colon-verb 로 매핑.
- `src/app/api/partnerships/[id]/participants/[operatorId]/route.ts` — `POST`(body {participantScope?, reason}) + `DELETE`(body {reason}, 204).
- `src/app/api/partnerships/_proxy.ts` — zod body/param 스키마 + `mapError`(401→401·NO_ACTIVE_TENANT→400·ApiError passthrough·Unavailable→503·unknown→503) + `badRequest()`(422 VALIDATION_ERROR) + `newRequestId` 재노출.
- `src/app/(console)/partnerships/page.tsx` (신규) — 서버 컴포넌트, `dynamic='force-dynamic'`, `getPartnershipsListState()` 4-way 분기(noTenant `data-testid=partnerships-no-tenant` / permissionError `partnerships-permission-denied` / degraded 배너 `partnerships-degraded` / success), 401→`redirect('/login')`.
- `src/features/partnerships/components/PartnershipsScreen.tsx` (신규, `'use client'`) — host-side + partner-side 섹션 분리(myRole). host: **invite 폼**(partnerTenantId + delegatedScope domains/roles 입력) + host 행에 suspend/reactivate/terminate. partner: PENDING 행에 **accept**, ACTIVE 행에 **participant 관리**(add: operatorId + optional participantScope ⊆ delegatedScope / remove) + suspend/reactivate/terminate. 상태 전이 매트릭스(계약 §Status)대로 버튼 gating. 모든 변이 → `PartnershipConfirmDialog` 경유 → `apiClient` → `router.refresh()`. `data-testid={`partnership-row-${partnershipId}`}`.
- `src/features/partnerships/components/PartnershipConfirmDialog.tsx` (신규, `'use client'`) — reason-capture(빈 사유 submit 비활성 — producer `X-Operator-Reason` 필수), `role="dialog" aria-modal`, textarea auto-focus/Escape, 파괴적(terminate) vs primary 스타일, `data-testid=partnership-confirm-reason`/`partnership-confirm-submit`.
- `src/features/partnerships/index.ts` — 배럴(app/ 은 이것만 import).
- `src/shared/ui/ConsoleSidebarNav.tsx` — **'조직 설정' 그룹**에 leaf `{ href:'/partnerships', label:'파트너십', testid:'nav-partnerships' }` 추가(entitlement/admin 평면 org-settings; nav 회귀 0).
- 테스트: `tests/unit/partnerships-client.test.ts`(코어 taxonomy + 헤더 매트릭스: token·X-Tenant-Id·percent-encoded reason·idempotencyKey on invite·raw 한글 미노출·토큰 미로깅·guards 401/400/reason) · `tests/unit/partnerships-proxy.test.ts`(POST invite 201·GET list 200·accept/terminate/participant add·remove 204·422 unknown/malformed·403/409/422 passthrough).

**OUT (의도적 비범위):**

- 신규 BE/contract 변경(전부 BE-476/477/478 소비만).
- N-way consortia · SUPER_ADMIN broker gate · partner billing · ABAC per-resource cross-org data scope · invite rate-limit · partner discovery(전부 ADR-045 §3.4-4 deferred follow-up).
- assume-tenant 실제 발급 UI(별개 경로; 이 표면은 관계 상태만).
- 파트너 테넌트/operator 자동완성·검색(수동 id 입력으로 충분 — proving surface).

## Acceptance Criteria

- [ ] **AC-1** `/partnerships` 가 host-side + partner-side 파트너십을 `GET /api/admin/partnerships` 로 렌더(myRole 로 섹션 분리, status·delegatedScope·participantCount 표시). 테넌트 미선택 → `partnerships-no-tenant` 게이트, 403 → `partnerships-permission-denied`, list unreachable → `partnerships-degraded` 배너(shell 유지).
- [ ] **AC-2** host: invite 폼(partnerTenantId + delegatedScope {domains,roles}) → `POST /api/partnerships {partnerTenantId, delegatedScope, reason}`; Idempotency-Key 클라 생성; 성공 201 → `router.refresh()`. tenantId 는 서버 activeTenant 주입(클라 미제공).
- [ ] **AC-3** 상태 전이 버튼이 계약 매트릭스대로 gating: PENDING+partner→accept, PENDING+either→terminate, ACTIVE+either→suspend/terminate, SUSPENDED+either→reactivate/terminate, TERMINATED→없음. accept 는 partner-side 행에만.
- [ ] **AC-4** partner: ACTIVE 파트너십 행에서 participant add(operatorId + optional participantScope) → `POST .../participants/{operatorId}` / remove → `DELETE .../participants/{operatorId}`(204). participantScope 생략 시 body 에서 omit(net-zero = delegatedScope 전체).
- [ ] **AC-5** 모든 변이(invite/accept/suspend/reactivate/terminate/participant add·remove)는 reason-capture 다이얼로그 경유(빈 사유 submit 비활성). GET 은 reason 無.
- [ ] **AC-6** 클라 코어 taxonomy: 401→ApiError(re-login)·403 PARTNERSHIP_SCOPE_DENIED/PERMISSION_DENIED→ApiError inline·422 PARTNERSHIP_SCOPE_INVALID/PARTICIPANT_*→ApiError inline·503/timeout→PartnershipsUnavailableError. operator 토큰+사유 인코딩(비-Latin1=encodeURIComponent), invite 만 Idempotency-Key. 토큰 미로깅.
- [ ] **AC-7** 프록시: 미지/malformed body·param → 422 VALIDATION_ERROR, activeTenant 없음 → 400 NO_ACTIVE_TENANT, producer 에러(403/404/409/422/503) passthrough. tenantId 클라 미수용(서버 activeTenant 만).
- [ ] **AC-8** 사이드바 '조직 설정' 그룹에 `nav-partnerships`; 기존 nav 테스트 회귀 0.
- [ ] **AC-9** `pnpm lint` + `pnpm exec tsc --noEmit` + `pnpm vitest`(신규 스위트 partnerships-client + partnerships-proxy) green, 회귀 0.

## Related Specs

- `docs/adr/ADR-MONO-045-cross-org-partner-delegation.md` §3.4 step 3(minimal partner-console surface) + D2(two-sided consent)/D4(partner self-governance)/D6(cascade offboarding).
- `projects/iam-platform/specs/services/admin-service/rbac.md` § Cross-Org Partner Delegation Confinement — 이 표면은 관계 상태만 다루고 파생 권한은 assume-tenant 캡(표시 참고).
- `docs/adr/ADR-MONO-023-entitlement-iam-plane-separation.md` — 브라우저 직접 admin-service 호출 금지(동일 출처 프록시 경유; operator 토큰 server-only).

## Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` § Partnership Management (BE-476, 소비만): `POST /api/admin/partnerships`(201) / `POST {id}:accept|:suspend|:reactivate|:terminate`(200) / `GET`(목록, role/status/page/size) / `POST|DELETE {id}/participants/{operatorId}`(201/204). 공통 에러 401/403/400/422/404/409. 헤더 `X-Operator-Reason`(변이 필수)·`X-Tenant-Id`(활성)·`Idempotency-Key`(invite).
- `projects/iam-platform/specs/contracts/events/partnership-events.md` — 참고(프런트 미소비).

## Edge Cases

- **PENDING host-side 행**: host 는 자기 invite 를 terminate(철회)만; accept 버튼 없음(partner 권리).
- **participantScope ⊄ delegatedScope**: producer 422 `PARTICIPANT_SCOPE_EXCEEDS_DELEGATION` → inline 표시(클라 사전검증은 best-effort, producer 가 SoT).
- **operator home ≠ partner tenant**: producer 422 `PARTICIPANT_NOT_OWN_OPERATOR` → inline.
- **중복 invite**: producer 409 `PARTNERSHIP_ALREADY_EXISTS` → inline.
- **TERMINATED 행**: 액션 버튼 없음(종단), 목록엔 이력으로 표시.
- **테넌트 미선택**: 게이트(operators/subscriptions 미러).
- **list degraded(admin-service unreachable)**: 배너 고지, shell 유지.

## Failure Scenarios

- **partnership 엔드포인트 5xx/timeout** → PartnershipsUnavailableError → 다이얼로그 inline "일시적으로 처리 불가" + 재시도(세션 유지).
- **사이드바 그룹 추가가 nav 회귀?** → operators-nav 테스트 precedent 로 확증(testid/href 불변 → 회귀 0).
- **클라가 다른 테넌트 타겟?** → 불가: tenantId 는 서버 activeTenant 만(POST body·path 모두 클라 미제공).
- **비-ASCII 사유로 fetch throw?** → 코어가 `encodeURIComponent` 로 헤더 ASCII 화(TASK-MONO-176), producer `OperatorReasonDecodingFilter` 디코드.
