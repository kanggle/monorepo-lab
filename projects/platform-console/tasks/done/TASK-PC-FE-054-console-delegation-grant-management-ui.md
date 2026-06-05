# Task ID

TASK-PC-FE-054

# Title

**콘솔 위임(대결) grant 관리 UI — approval v2.1 delegation 의 사용자-가시 표면 완성 (PC-FE-053 후속).** TASK-ERP-BE-013 이 라이브화한 위임 grant 관리(`/api/erp/approval/delegations` 3 endpoint)를 콘솔이 소비: 위임 **생성**(대결자 delegateId + 유효기간 validFrom/validTo? + 사유) · **회수**(reason 필수) · **목록**("내가 위임한"[as delegator] / "나에게 위임된"[as delegate], status ACTIVE|REVOKED). PC-FE-053 은 다단계 + 대결 승인 read-only **표시**만 했고, 이 task 가 위임 grant **관리**(create/revoke)를 추가 → `위임(백엔드) → 콘솔(grant 관리)` 루프 완결로 erp 결재 도메인 사용자-가시 표면 완성.

# Status

done

# Owner

frontend-engineer (platform-console console-web; TASK-ERP-BE-013 `/delegations` 라이브 — 이 task 는 콘솔 UI + proxy)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- test

---

# Dependency Markers

- **선행 (cross-project, erp)**: TASK-ERP-BE-013 (대결/위임 — `/api/erp/approval/delegations` create/revoke/list 라이브, main 머지됨 `650ae89c`). 콘솔이 런타임 호출.
- **builds on**: TASK-PC-FE-053 (다단계 결재 UI + 대결 승인 read-only 표시; 이 task 가 위임 grant 관리 추가) + TASK-PC-FE-050 (`OrgScopeDialog` reason-gated 관리 다이얼로그 + GET/PUT-only proxy 선례) + TASK-PC-FE-051 (결재함 proxy/에러 graceful 선례).
- **realises**: BE-013 위임 grant 관리의 콘솔 소비자. approval-api.md §v2.1 amendment(delegation 3 endpoint: POST create / POST {id}/revoke / GET list) 소비.
- **decision (user, 2026-06-05)**: 다음 작업 = 콘솔 위임 grant 관리 UI(#1, PC-FE-053 이 sequencing한 후속).

# Goal

운영자가 콘솔에서 자신의 위임(대결)을 생성(대결자·기간·사유)하고 회수하며, "내가 위임한"/"나에게 위임된" 목록을 조회할 수 있다. approval-service 의 위임 모델이 콘솔 운영 화면에서 end-to-end demonstrable — erp 결재 도메인(단계/대결/위임)의 사용자-가시 표면 완성.

# Scope

## In Scope

- **proxy routes**(server-only, erp 도메인-facing 토큰; 기존 `app/api/erp/_proxy.ts` 에러매핑 재사용; approval/masterdata write proxy 패턴):
  - `app/api/erp/approval/delegations/route.ts` — **GET**(list, query `?role=DELEGATOR|DELEGATE`) + **POST**(create grant, Idempotency-Key). 다른 메서드 미노출.
  - `app/api/erp/approval/delegations/[id]/revoke/route.ts` — **POST**(revoke, reason 필수, Idempotency-Key). POST-only.
- **erp-ops feature 확장**(`features/erp-ops/`):
  - types(zod): `DelegationGrant`(id/delegatorId/delegateId/validFrom/validTo?/reason?/status/createdAt/createdBy/revokedAt?/revokedBy? — **NON_NULL absent → optional**; status 자유문자 tolerant: ACTIVE|REVOKED) + list response + `CreateDelegationInput`(delegateId/validFrom/validTo?/reason?) + body 스키마(create: delegateId non-blank·validFrom required·validTo? · reason? + IdempotencyKey; revoke: reason non-blank + IdempotencyKey).
  - api client(server-only): `listDelegations(role?)` / `createDelegation(input, idemKey)` / `revokeDelegation(id, reason, idemKey)`. erp 도메인-facing 토큰(`getDomainFacingToken`), `X-Tenant-Id` 미전송. 에러 → `ApiError`/`ErpUnavailableError`(§2.5). create/revoke 멱등키.
  - keys + hooks(client): `useDelegations(role?)` query + `useCreateDelegation`/`useRevokeDelegation` mutation; 성공 시 delegation 목록 invalidate.
  - components: `DelegationScreen`(또는 ErpOpsScreen "위임" 섹션) — 탭/구분 "내가 위임한"(as DELEGATOR) + "나에게 위임된"(as DELEGATE) 목록(delegateId/delegatorId·기간·status badge·활성 여부) + **위임 생성 다이얼로그**(delegateId 입력 + validFrom/validTo date 입력 + reason; Idempotency-Key 클라 생성) + **회수 다이얼로그**(reason 필수, OrgScopeDialog/결재 reason 패턴; as delegator 인 ACTIVE grant 만 회수 액션). status badge(ACTIVE=활성/REVOKED=회수됨), 만료(validTo<now) 시각 표시.
  - **에러 graceful**: 422 `DELEGATION_INVALID`(자기위임/잘못된 기간) / 404 `DELEGATION_NOT_FOUND`(없는 grant revoke) / 403 `PERMISSION_DENIED` / IDEMPOTENCY_* → inline 안내(크래시 0). 자기위임(delegateId==본인) 클라 사전 경고.
  - ErpOpsScreen nav 에 "위임" 진입 + DelegationScreen 렌더 + index.ts export.
- **tests**: 컴포넌트(목록 as delegator/delegate·생성 다이얼로그·회수 reason-gated·status badge·자기위임 경고·에러 inline) + api client 단위(list role 쿼리·create/revoke payload+Idempotency-Key·absent 필드 파싱) + proxy route 단위(GET/POST 메서드 노출·revoke POST-only·에러매핑·server-only token). console-web **vitest + tsc --noEmit + lint + build** GREEN(MONO-166).

## Out of Scope

- approval-service backend — TASK-ERP-BE-013(소비만).
- delegation v2.2(per-request/per-route 위임·자동 부재 감지·transitive) — backend 미구현이라 콘솔 범위 밖.
- 대결 승인 표시 — PC-FE-053(이미 history `actingForApproverId` read-only 표시). 이 task 는 grant **관리**.
- delegate/delegator employee picker 고도화 — 기본 id 입력 + degrade(masterdata 직원 목록 재사용 가능 시).

# Acceptance Criteria

- [ ] **AC-1** "위임" 섹션에 "내가 위임한"(as DELEGATOR) + "나에게 위임된"(as DELEGATE) 목록 — delegateId/delegatorId·유효기간·status badge(ACTIVE/REVOKED)·활성 여부.
- [ ] **AC-2** 위임 생성 다이얼로그: delegateId + validFrom + validTo(선택, open-ended 허용) + reason → 생성(Idempotency-Key); 성공 시 목록 갱신. 자기위임(delegateId==본인) 클라 경고 + producer 422 graceful.
- [ ] **AC-3** 회수: as delegator 인 ACTIVE grant 에 회수 액션(reason 필수 다이얼로그) → REVOKED; 성공 시 목록 갱신. 없는 grant → 404 graceful.
- [ ] **AC-4** 에러 graceful: 422 `DELEGATION_INVALID`(자기위임/기간) / 404 `DELEGATION_NOT_FOUND` / 403 / IDEMPOTENCY_* → inline(크래시 0). absent 필드(validTo/reason/revokedAt) 정상 렌더(NON_NULL).
- [ ] **AC-5** proxy: delegations=GET+POST, {id}/revoke=POST(다른 메서드 미노출); 토큰 server-only(도메인-facing erp 토큰)·`X-Tenant-Id` 미전송·Idempotency-Key 전달; 에러매핑 기존 erp proxy 일치.
- [ ] **AC-6** console-web `vitest` + `tsc --noEmit` + `lint` + `build` GREEN(MONO-166). 기존 erp-ops/approval/operators 회귀 0.

# Related Specs

- consume: `projects/erp-platform/specs/contracts/http/approval-api.md` §v2.1 amendment(ERP-BE-013 — delegation 3 endpoint + grant shape). console-integration-contract.md §2.4.8(approval surface — delegation grant 관리 note 추가).

# Related Contracts

- consume: approval-api.md(delegation: POST /delegations create / POST /delegations/{id}/revoke / GET /delegations ?role). grant shape `{id, delegatorId, delegateId, validFrom, validTo?, reason?, status, createdAt, createdBy, revokedAt?, revokedBy?}`. console-web 자체 proxy(same-origin; delegations GET+POST / {id}/revoke POST).

# Edge Cases

- 자기위임(delegateId==본인): 클라 사전 경고 + producer 422 `DELEGATION_INVALID` graceful inline.
- 잘못된 기간(validTo<validFrom): 클라 검증 + producer 422 graceful.
- as delegate 목록의 grant: 회수 액션 미노출(회수는 delegator 권한; producer 가 권한 검사).
- REVOKED/만료 grant: status badge·만료 표시; 회수 액션 미노출(이미 비활성).
- absent validTo(open-ended): "무기한" 표시; absent reason/revokedAt: 숨김.
- 멱등: create/revoke 버튼 더블클릭 → 같은 키 replay(중복 0); 클라가 작업당 키 1회 생성.

# Failure Scenarios

- approval-service 불가(503/timeout): 섹션 degrade(기존 erp proxy degrade 패턴).
- reason 누락 revoke: 클라 차단(reason 필수) + producer 400 `VALIDATION_ERROR`.
- 잘못된 메서드 proxy: 405/미노출.
- write proxy 토큰 클라 노출: server-only(`getServerEnv`/도메인-facing 토큰) 가드.

# Test Requirements

- 컴포넌트: `DelegationScreen`(목록 delegator/delegate·생성 다이얼로그·회수 reason-gated·status badge·자기위임 경고·에러 inline), api client 단위(list role 쿼리·create/revoke payload+Idempotency-Key·absent 파싱·server-only), proxy route 단위(메서드 노출·revoke POST-only·에러매핑).
- console-web `vitest` + `tsc --noEmit` + `lint` + `build` GREEN(MONO-166). 기존 회귀 0.
- Local(선택): erp 재배포 후 라이브 위임 생성→다단계 결재 대결 승인→회수 루프 확인.

# Definition of Done

- [ ] proxy(delegations GET+POST / {id}/revoke POST) + erp-ops feature(delegation types/api/keys/hooks/DelegationScreen + create/revoke dialog) + ErpOpsScreen nav + 에러 graceful.
- [ ] console-web vitest + tsc + lint + build GREEN(MONO-166); 회귀 0.
- [ ] Task md + INDEX 갱신.
- [ ] Reviewed + merged (3-dim).

---

분석=Opus 4.8 / 구현 권장=Sonnet 4.6 (UI slice — PC-FE-050 OrgScopeDialog(reason-gated 관리 다이얼로그 + 메서드-제한 proxy) + PC-FE-051 결재함 proxy/에러 graceful 선례 재사용; 신규 도메인 로직 0, 백엔드 소비만; create/revoke 멱등 + reason-gated + 자기위임 경고 + status badge). 사용자 "콘솔 위임 grant 관리 UI" 선택 — PC-FE-053 이 sequencing한 후속. 메타: 위임 grant **관리**(create/revoke) — PC-FE-053 의 대결 승인 read-only **표시**와 짝; `위임(백엔드 BE-013) → 콘솔 grant 관리` 루프 완결 = erp 결재 도메인(단계/대결/위임) 사용자-가시 표면 완성. v2.2(per-request/자동부재) backend 미구현이라 콘솔 범위 밖. [[project_platform_console_adr_013]]
