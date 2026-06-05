# Task ID

TASK-PC-FE-055

# Title

**콘솔 위임 현황 read 카드 — ERP-BE-015 delegation-fact read-model 의 콘솔 가시화 (parity slice).** TASK-ERP-BE-015 가 라이브화한 read-model `GET /api/erp/read-model/delegations`(+ `/{grantId}`)를 콘솔이 소비: org_scope 범위(delegator 부서 subtree) 내 위임 현황(ACTIVE/REVOKED, 위임자/대행자, 유효기간, 회수시각) read-only 조회 카드 + 필터(delegatorId/delegateId/status/activeAt). PC-FE-049(통합조회 org-view 카드) 청사진 동형. **PC-FE-054(위임 grant 관리, approval-service write)와는 다른 표면** — 이건 read-model 기반 org-scoped 현황 보고(eventually-consistent). ERP-BE-015 backend→콘솔 가시화 완성(ADR-013 §D3.1 parity).

# Status

done

# Owner

frontend-engineer (platform-console console-web; TASK-ERP-BE-015 read-model `/delegations` 라이브 — 이 task 는 콘솔 read 카드 + GET-only proxy)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- test

---

# Dependency Markers

- **선행 (cross-project, erp)**: TASK-ERP-BE-015 (delegation-fact read model — `GET /api/erp/read-model/delegations` list + `/{grantId}` detail 라이브, main 머지됨 `8fc218d9`; ACTIVE/REVOKED, org_scope=delegator 부서 subtree, activeAt 필터). 콘솔이 런타임 호출.
- **builds on**: TASK-PC-FE-049 (read-model org-view 카드 — `EmployeeOrgViewCard` + read-model proxy + eventually-consistent `meta.warning` 배너 + 미해소 배지 + server-only `getDomainFacingToken` 패턴; 이 task 의 1:1 청사진) + TASK-PC-FE-054 (위임 grant **관리** UI — 같은 도메인 다른 표면[write vs read-model 현황]).
- **realises**: ERP-BE-015 read-model delegation 조회의 콘솔 소비자. read-model-api.md § Delegation facts(2 GET endpoint) 소비. ADR-013 §D3.1 parity discipline(backend read 표면→콘솔 parity slice).
- **decision (user, 2026-06-06)**: 다음 작업 = 콘솔 위임 현황 read 카드.

# Goal

운영자가 콘솔에서 자신의 org_scope 범위 내 위임 현황(누가 누구를 언제까지 대행 가능, ACTIVE/REVOKED)을 read-only 로 조회·필터할 수 있다. ERP-BE-015 의 delegation-fact read model 이 콘솔 운영 화면에서 end-to-end demonstrable — read-model delegation 가시화 완성.

# Scope

## In Scope

- **신규 read 카드** `DelegationFactCard` (`features/erp-ops/components/`, `EmployeeOrgViewCard` 동형): org_scope 범위 위임-fact 목록 테이블(grantId, status badge[ACTIVE/REVOKED], delegatorId, delegateId, validFrom, validTo[ABSENT→"무기한"], reason[present 시], revokedAt[REVOKED 시]) + 필터(delegatorId/delegateId/status[ACTIVE|REVOKED]/activeAt[ISO instant]) + **eventually-consistent `meta.warning` 배너** + 로컬 페이지네이션. **read-only**(생성/회수 없음 — 그건 PC-FE-054 관리 UI).
- **server-only api client** `listDelegationFacts(params)` / `getDelegationFact(grantId)` (`features/erp-ops/api/erp-api.ts`): `callErp` 재사용 — `getDomainFacingToken()` 주입, **`X-Tenant-Id` 미전송**(erp JWT tenant_id 해석), **`X-Operator-Reason` 미전송**(read-only), `ERP_TIMEOUT_MS`, `DelegationFact*Schema.parse`(zod `.passthrough()` tolerant — NON_NULL absent 관용).
- **types** (`features/erp-ops/api/types.ts`): `DelegationFactSchema`(grantId/status/delegatorId/delegateId/validFrom?/validTo?/reason?/revokedAt?, passthrough) + `DelegationFactListResponseSchema`(data[] + read-model meta).
- **proxy** (신규 route handlers): `app/api/erp/read-model/delegations/route.ts`(GET list, 필터 스레딩 + `mapErpError`) + `app/api/erp/read-model/delegations/[grantId]/route.ts`(GET detail). **GET-only**(POST/PATCH/DELETE 핸들러 부재 — 테스트 단언). PC-FE-049 read-model employees proxy 패턴 동형.
- **hooks** (`hooks/use-erp-ops.ts`): `useDelegationFacts(params, initial)` / `useDelegationFact(grantId)` (React Query, proxy 호출, `retry:false`).
- **query keys** (`erp-keys.ts`): `delegationFactsListKey(...)` / `delegationFactDetailKey(grantId)`.
- **state 시딩** (`erp-state.ts` + `app/(console)/erp/page.tsx`): `getErpSectionState()` fan-out 에 `delegationFacts: listDelegationFacts(무필터 초기)` 추가 + 카드에 `initial` 프롭 전달.
- **nav** (`ErpOpsScreen.tsx`): in-section nav 에 "위임 현황"(`#delegation-facts-heading`) 추가 + 카드 렌더(PC-FE-054 "위임(관리)" 와 구분).
- **index.ts** export 추가.
- **tests** (vitest): `DelegationFactCard.test.tsx`(warning 배너 / ABSENT 필드 graceful no-crash / status badge / 필터 / 페이지네이션) + proxy GET-only 단언 + api-client read-model route 회귀. MONO-166 4-gate(vitest + `tsc --noEmit` + lint + build).

## Out of Scope

- 위임 **생성/회수**(write) — PC-FE-054 위임 관리 UI 가 이미 처리(approval-service `/api/erp/approval/delegations`). 이 카드는 read-model 현황만.
- read-model delegation 의 백엔드 변경(ERP-BE-015 라이브 — 콘솔 소비만).
- revoke 알림(notification leg) — 별 후속.
- 위임 fact 의 display-name 해소(read-model 이 ids-only; 이름 enrichment 는 read-model v2).

# Acceptance Criteria

- [ ] **AC-1** `DelegationFactCard` 가 `GET /api/erp/read-model/delegations` 소비 → org_scope 범위 위임-fact 목록 렌더(status badge ACTIVE/REVOKED, delegator/delegate, validFrom, validTo[ABSENT→"무기한"], reason/revokedAt present 시). 필터 delegatorId/delegateId/status/activeAt.
- [ ] **AC-2** **eventually-consistent**: `meta.warning` 배너 표시. ABSENT 필드(validFrom/validTo/reason/revokedAt) graceful(숨김 또는 "—", no crash).
- [ ] **AC-3** proxy **GET-only**(2 route: list + detail; POST/PATCH/DELETE 핸들러 부재 — 테스트 단언). server-only api: `getDomainFacingToken` + X-Tenant-Id/X-Operator-Reason 미전송 + `ERP_TIMEOUT_MS`.
- [ ] **AC-4** **PC-FE-054 관리 UI 와 표면 분리**: 이 카드는 read-only(생성/회수 버튼 0), read-model 엔드포인트(`/read-model/delegations`), "위임 현황" nav. PC-FE-054 의 "위임(관리)"(approval-service write) 무변경.
- [ ] **AC-5** 403(비-erp 운영자)/503/timeout/404 graceful degrade(shell 안 깨고 inert/배너); read-only.
- [ ] **AC-6** MONO-166 4-gate GREEN: `pnpm test`(vitest) + `tsc --noEmit` + lint + build. console-web 외 변경 0.

# Related Specs

- consume: `projects/erp-platform/specs/contracts/http/read-model-api.md` § Delegation facts(`GET /api/erp/read-model/delegations` + `/{grantId}`, DelegationFact 스키마, eventually-consistent meta). ADR-MONO-013 §D3.1 parity discipline. ADR-MONO-015/017(콘솔 구성).

# Related Contracts

- consume: read-model-api.md § Delegation facts(2 GET). 신규 콘솔 proxy 2 route(GET-only). PC-FE-049 read-model org-view proxy 패턴 동형.

# Edge Cases

- validTo ABSENT(무기한 위임): "무기한" 표기.
- validFrom ABSENT(out-of-order revoke-before-grant → read-model 이 grant-only 필드 미보유): graceful "—".
- reason/revokedAt ABSENT: 숨김.
- org_scope 범위 밖/미설정 운영자: read-model 이 net-zero(`["*"]`/미설정=무필터) 또는 빈 목록 → 카드 빈 상태 안내.
- activeAt 필터: read-model 이 status=ACTIVE ∧ 유효기간 포함 계산(콘솔은 파라미터 전달만).
- 403/503/timeout: 배너 degrade, shell 보존.

# Failure Scenarios

- read-model 미가용(503/timeout): `meta.warning` 또는 에러 배너, 카드 inert, 다른 카드 무영향.
- proxy 에 write 메서드 추가 유혹: AC-3 위반(GET-only 단언 게이트).
- zod parse 실패(스키마 drift): `.passthrough()` + optional 로 tolerant, 미상 필드 graceful.

# Test Requirements

- `DelegationFactCard.test.tsx`: warning 배너 / ABSENT 필드 no-crash / status badge / 필터 동작 / 페이지네이션. proxy GET-only 단언(POST/PATCH/DELETE undefined). api-client read-model route 회귀.
- MONO-166 4-gate(vitest + tsc --noEmit + lint + build) GREEN. console-web only.

# Definition of Done

- [ ] `DelegationFactCard`(read-only) + server-only api(listDelegationFacts/getDelegationFact) + types + 2 GET-only proxy route + hooks + keys + state 시딩 + nav("위임 현황") + index export.
- [ ] PC-FE-054 위임 관리 UI(write) 무변경, 표면 분리.
- [ ] eventually-consistent 배너 + ABSENT graceful + 403/503 degrade.
- [ ] vitest + 4-gate GREEN. console-web only.
- [ ] Task md + INDEX 갱신.
- [ ] Reviewed + merged (3-dim).

---

분석=Opus 4.8 / 구현 권장=Sonnet 4.6 (프론트 read 카드 — PC-FE-049 청사진 1:1 답습, write 없음, proxy GET-only, zod tolerant). 사용자 "콘솔 위임 현황 read 카드" 선택. 메타: ① **backend read 표면(ERP-BE-015 read-model)→콘솔 parity slice**(ADR-013 §D3.1) — read-model 조회 카드는 PC-FE-049 org-view 청사진 동형(eventually-consistent 배너 + ABSENT graceful + server-only getDomainFacingToken + GET-only proxy). ② **표면 분리**: PC-FE-054(위임 grant 관리=approval-service write) vs PC-FE-055(위임 현황=read-model org-scoped read) — 같은 도메인 2 표면, 혼동 금지. ③ read-only 라 X-Operator-Reason/Idempotency-Key 불요(PC-FE-049 read 선례). [[project_platform_console_adr_013]] [[project_monorepo_template_strategy]]
