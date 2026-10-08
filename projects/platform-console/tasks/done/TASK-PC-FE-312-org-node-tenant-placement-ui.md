# Task ID

TASK-PC-FE-312

# Title

조직 계층 노드 상세에서 **테넌트 추가 · 옮기기 · 빼기** + 잃는 도메인 확인 화면 · `/tenants` 생성 폼의 «소속 노드 (선택)» (`ADR-MONO-047` 개정 2026-10-07)

# Status

done

# Owner

platform-console

# Task Tags

- console-web
- frontend

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 백엔드 판정(`TASK-BE-625`) 위의 화면 · 확인 창 · 폼 칸.

---

# Dependency Markers

- **선행**: `projects/iam-platform` `TASK-BE-625` 머지(API · 계약).
- 근거 ADR: `docs/adr/ADR-MONO-047-org-node-tenant-hierarchy.md` § 개정 (2026-10-07 UTC) — 결정 «양쪽을 다 관리하는 사람만» · P1 · P2.

# Goal

지금 조직 계층 화면은 노드의 «소속 테넌트 (하위 노드 포함)» 를 **보여 주기만** 한다(`apps/console-web/src/features/org-hierarchy/components/OrgNodeDetail.tsx:234-260`). BE-625 의 쓰기를 화면에 연결한다:

- 노드 상세 «소속 테넌트» 에 **[테넌트 추가]**(내 관리 범위 안의 테넌트에서 고름) · 각 테넌트 옆 **[다른 노드로 옮기기]** · **[빼기]**.
- 확인 창(P2): 목적지 노드의 유효 상한 때문에 **잃게 되는 도메인**을 이름으로 보여 준다(«이동하면 이 도메인들이 꺼집니다: …»). 거절이 아니라 확인이다.
- `/tenants` 생성 폼에 «소속 노드 (선택)» — 비워 두면 지금처럼 무소속.
- 서버가 404(범위 밖)를 주면 «이 노드 또는 테넌트를 관리할 권한이 없습니다» 로 — 존재 여부를 화면이 추측해 말하지 않는다.

# Scope

## In Scope

- `features/org-hierarchy`: 추가 · 옮기기 · 빼기 UI + 사유 입력(기존 `OrgReasonDialog` 재사용) + 확인 창
- `app/api/…` 동일 출처 프록시 라우트(BE-625 엔드포인트) — 기존 org-node 프록시 모양 그대로
- `features/tenants`: 생성 폼 칸 + 프록시 본문 스키마(`app/api/tenants/_proxy.ts:22-30`)에 선택 `orgNodeId`
- 단위 시험 + bite

## Out of Scope

- 권한 판정(서버가 권위 — 화면은 버튼 노출만 조절)
- 노드 생성 권한 변경

# Acceptance Criteria

- [x] **AC-0** — 착수 시 재측정: `OrgNodeDetail.tsx` 소속 테넌트 절 · org-node 프록시 라우트 · 테넌트 생성 프록시 스키마 file:line · BE-625 가 «잃는 도메인» 을 응답으로 주는지(아니면 콘솔이 상한 ∩ 구독으로 계산). → 아래 § AC-0 재측정. **API 가 준다**(미리보기 `lostDomains`) — 콘솔 계산 없음.
- [x] **AC-1** — 추가 · 옮기기 · 빼기가 렌더 DOM 에서 동작하고, 성공 뒤 소속 테넌트 목록이 갱신된다. → `TenantPlacement.test.tsx` § AC-1 (add · move · detach 각 1칸 + 미리보기 전 확정 불가 + 후보 0 개 · 비-SUPER_ADMIN 후보).
- [x] **AC-2** — 상한이 걸린 노드로 옮길 때 확인 창이 잃는 도메인을 이름으로 보여 준다 · 잃는 것이 없으면 그 문단이 없다(대조군). → `TenantPlacement.test.tsx` § AC-2 (양성 1 · 대조군 1).
- [x] **AC-3** — 서버 404 → 권한 문구(존재 추측 없음). → `TenantPlacement.test.tsx` § AC-3 (미리보기 404 `TENANT_NOT_FOUND` · 쓰기 404 `ORG_NODE_NOT_FOUND` · 409 재시도 문구 + 목록 재조회).
- [x] **AC-4** — 생성 폼: 노드 고르면 본문에 `orgNodeId` · 비우면 본문에 칸 없음(회귀). → `TenantsScreen.test.tsx` (고름 · 비움 · 골랐다 되돌림 · 노드 목록 실패) + `tenants-proxy.test.ts` (전달 · `null` → 없음) + `tenants-api-create-org-node.test.ts` (생산자 본문 3칸 그대로) + `tenants-page.test.tsx` (SSR 이 선택지를 넘김 · 실패해도 화면은 선다).
- [x] **AC-5** — bite: 확인 창의 «잃는 도메인» 계산을 지우면 AC-2 칸만 빨강. → 렌더 블록을 `false &&` 로 끄고 관련 12 파일 실행: **1 실패 / 135 통과** — 실패는 AC-2 양성 칸 하나뿐(대조군은 통과가 맞다). 되돌린 뒤 전부 통과.
- [x] **AC-6** — `tsc` · `lint` · `vitest` 전체 rc=0 · 콘솔 e2e 디렉터리 grep(`org-node`, `org-hierarchy`) 후 영향 확인 · 머지 뒤 첫 nightly 콘솔 잡 확인. → tsc rc=0 · lint rc=0 · vitest 전체 rc=0 (344 파일 · 3900/3900) · e2e grep 0건(아래). ⚪ **머지 뒤 첫 nightly 콘솔 잡 확인만 남음** — 그래서 체크하지 않는다.

# Related Specs

- `docs/adr/ADR-MONO-047-org-node-tenant-hierarchy.md` § 개정 (2026-10-07)
- `specs/services/console-web/architecture.md`

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` § Org Hierarchy · § tenants (BE-625 가 추가)

# Edge Cases

- 내 관리 범위 안의 테넌트가 0 개 — [테넌트 추가] 를 숨기지 않고 «추가할 수 있는 테넌트가 없습니다» 로.
- 샘플 방문자(ADR-MONO-074) — 쓰기 버튼 없음(기존 샘플 규율).

# Failure Scenarios

1. 확인 창 없이 옮긴다 — 운영자가 모르는 사이 그 테넌트의 도메인 화면이 꺼진다.
2. 화면이 404 를 «없는 테넌트» 로 말한다 — 남의 테넌트 존재 여부를 추측해 흘린다.

---

# AC-0 재측정 (2026-10-08 UTC, base `origin/main` `4cd6c9c85`)

경로는 `projects/platform-console/apps/console-web/src/` 기준.

- **소속 테넌트 절** — `features/org-hierarchy/components/OrgNodeDetail.tsx:234-263`(티켓 인용 234-260 과 같은 절, 닫는 `</section>` 까지 263). 읽기 전용 `<ul data-testid="org-node-tenants-list">` 에 tenantId 문자열만(`:252-261`). 데이터 = `useOrgNodeTenants(node.orgNodeId)`(`hooks/use-org-nodes.ts:59-70`) → `GET /api/org-nodes/{id}/tenants` → 노드 + **모든 후손**의 tenantId. 🔵 그래서 이 목록의 테넌트가 이 노드에 «직접» 있는지는 화면이 모른다 — 옮기기/빼기의 출발 노드는 미리보기 응답의 `fromOrgNodeId` 로 보여 준다(추측 안 함).
- **org-node 프록시 모양** — 오류 매핑·본문 스키마 `app/api/org-nodes/_proxy.ts:20-76`(스키마) · `:78-112`(`mapError`: 401 → 401, `NO_ACTIVE_TENANT` → 400, 그 밖 `ApiError` → 코드·상태 그대로 통과, `OrgNodesUnavailableError` → 503) · `:114-119`(`badRequest` 422). 쓰기 라우트 원형 `app/api/org-nodes/[orgNodeId]/ceiling/route.ts:20-38`(PUT · zod parse → api 함수 → `mapError`) · 읽기 원형 `[orgNodeId]/tenants/route.ts:13-25`. api 함수는 `features/org-hierarchy/api/org-nodes-api.ts` 의 `callOrgNodes`(`api/org-nodes-client.ts:71-86`, 공유 `callAdminGateway` — 토큰·`X-Tenant-Id`·`X-Operator-Reason` 부착 · 샘플 게이트가 맨 앞).
- **테넌트 생성 프록시 스키마** — `app/api/tenants/_proxy.ts:22-30`(`.strict()` 라 지금은 `orgNodeId` 를 실으면 422). 라우트 `app/api/tenants/route.ts:58-80` 이 세 칸만 골라 `createTenant` 에 넘기고, `features/tenants/api/tenants-api.ts:66-85` 가 생산자 본문을 같은 세 칸으로 만든다 — 세 곳 모두 고쳐야 칸이 생산자까지 간다.
- **BE-625 가 «잃는 도메인» 을 주는가 — 준다.** `projects/iam-platform/specs/contracts/http/admin-api.md:2528-2554`(placement effect: `domainsBefore`·`domainsAfter`·`lostDomains`·`gainedDomains`) · `:2556-2580` `GET /api/admin/tenants/{tenantId}/org-node/preview?orgNodeId=`(생략 = 빼기, 쓰기와 같은 양쪽 판정) · `:2582-2622` `PUT …/org-node`(`{"orgNodeId": …|null}` 키 필수, `X-Operator-Reason` 필수, 응답 = 효과 + `changed`, 409 `TENANT_ORG_NODE_CONFLICT`) · 생성 `:1794`·`:1798`·`:1821`(선택 `orgNodeId`, 생략/`null` = 무소속). 구현 확인: `iam-platform/apps/admin-service/.../presentation/TenantOrgNodePlacementController.java:33-62`. → **콘솔은 상한 ∩ 구독을 계산하지 않는다**(`admin-api.md:2554` — `ORG_ADMIN` 은 구독을 읽을 권한이 없다). 확인 창은 미리보기의 `lostDomains` 를 그대로 그린다.
- **«추가할 테넌트» 후보의 읽기** — 새 엔드포인트 없이 콘솔이 이미 가진 두 읽기의 합: ① 내 도달 범위의 **최상위 노드들**(`GET /api/org-nodes` 평면 목록에서 부모가 목록에 없는 노드)의 `GET /api/org-nodes/{id}/tenants` — `ORG_ADMIN` 이 관리하는 «놓인» 테넌트 전부, ② `GET /api/tenants`(`SUPER_ADMIN` 전용 — 무소속 테넌트까지; 다른 actor 는 403 → 조용히 ① 만). 지금 노드 subtree 에 이미 있는 테넌트는 빼고 보인다(그건 목록의 «옮기기»). 🔴 TENANT_ADMIN 의 «무소속 자기 테넌트»(라이더 P1)는 이 두 읽기에 안 잡힌다 — 그 actor 는 `org.manage` 가 없으면 이 화면 자체에 못 들어온다.
- **샘플 방문자 규율(실측)** — 이 feature 에 샘플 분기는 0건(`features/org-hierarchy/**` 에 `sample` 0). 기존 규율은 `ADR-MONO-074` R1ⓐ: 쓰기 버튼은 그대로 두고 게이트웨이 코어의 `sampleGate`(`shared/api/sample-gate.ts:34-42`)가 모든 비-GET 을 `403 SAMPLE_READ_ONLY` 로 거절, 화면은 `messageForCode` 로 «샘플 화면에서는 실행되지 않습니다…» 를 보인다(`shared/api/errors.ts:828-829` · `shared/lib/sample-refusal.ts`). 새 쓰기도 `callOrgNodes` 를 타므로 같은 규율이 그대로 걸린다.

---

# Implementation Record (2026-10-08 UTC)

경로는 `projects/platform-console/apps/console-web/` 기준.

**화면 (`features/org-hierarchy`)**
- `components/TenantPlacementSection.tsx`(신규) — `OrgNodeDetail` 의 읽기 전용 절을 대체. 머리말 옆 **[테넌트 추가]**(`org-node-tenant-add`, 후보 0 개여도 숨기지 않고 패널에 «추가할 수 있는 테넌트가 없습니다») · 행마다 **[다른 노드로 옮기기]**(`org-node-tenant-move-<t>` → 노드 선택 → 다음) · **[빼기]**(`org-node-tenant-detach-<t>`). 기존 testid `org-node-tenants-list` · `org-node-tenants-empty` · 머리말 id 는 그대로.
- 확인 창 = 기존 `OrgReasonDialog` 재사용 + 두 선택 prop 추가(`children` 본문 · `confirmBlocked`). 창이 열리면 미리보기를 읽고 **지금 위치 → 목적지**(노드 이름, 못 찾으면 `이름 확인 불가`, `null` = `무소속`) · 같은 위치면 «바뀌는 것이 없습니다» · `lostDomains` 가 있을 때만 «이동하면 이 도메인들이 꺼집니다: …»(`org-placement-lost-domains`, 구독은 남는다는 한 줄 포함) · `gainedDomains` 가 있으면 «다시 켜집니다». **미리보기가 성공하기 전엔 확정 버튼이 잠긴다**(Failure Scenario 1 — 효과를 모른 채 쓰지 않는다).
- 오류 문구: **어떤 404 든** «이 노드 또는 테넌트를 관리할 권한이 없습니다.» — 코드(`TENANT_NOT_FOUND`/`ORG_NODE_NOT_FOUND`)로 갈라 말하지 않는다(Failure Scenario 2). 409 `TENANT_ORG_NODE_CONFLICT` → «확인하는 사이에 다른 요청이 … 먼저 바꿨습니다. 목록을 새로 불러왔으니 다시 시도하세요.» + org-nodes 쿼리 전부 무효화.
- `hooks/use-org-nodes.ts` — `usePlacementPreview`(`gcTime: 0` — 낡은 미리보기 재사용 금지) · `usePlaceTenant`(성공 시 org-nodes 쿼리 전체 무효화 — 출발·도착·조상 목록이 다 바뀔 수 있다) · `usePlacementCandidates`(아래).
- `api/org-nodes-api.ts` — `previewTenantPlacement`(목적지 `null` 이면 `orgNodeId` 파라미터 자체를 뺀다) · `placeTenant`(본문 `{ orgNodeId }` — 키 항상 존재, `null` = 빼기). 경로는 `/api/admin/tenants/…` 지만 `org.manage` 표면이라 org-nodes 프로필(`callOrgNodes`)을 탄다. `api/types.ts` — `PlacementEffectSchema` · `PlacementResultSchema`. 배럴에 export.

**프록시 (`app/api/tenants/[tenantId]/org-node/`)** — `route.ts`(PUT) · `preview/route.ts`(GET). org-node 프록시와 같은 모양(zod parse → api 함수 → `mapError`)이고 **org-nodes `_proxy` 의 `mapError` 를 쓴다** — 503 이 `OrgNodesUnavailableError` 로 오기 때문(테넌트 `_proxy` 는 그 클래스를 모른다). 본문 스키마 `PlaceTenantBodySchema`(`app/api/org-nodes/_proxy.ts`) — `orgNodeId` 는 `nullable()` 이지 `optional()` 이 아니다: **키가 없으면 422**(오타 난 키가 조용히 «빼기» 가 되지 않게 — 생산자의 400 과 같은 이유). 빈 문자열도 422.

**«추가할 테넌트» 후보 — 새 엔드포인트 없음.** 콘솔이 이미 가진 두 읽기의 합에서 지금 노드 subtree 에 있는 것을 뺀다: ① 도달 범위의 최상위 노드들(평면 목록에서 부모가 목록에 없는 노드)의 `GET /api/org-nodes/{id}/tenants` ② `GET /api/tenants`(최대 20 쪽 × 100, `SUPER_ADMIN` 전용 — 403 은 삼키고 ① 만, 그 밖의 실패는 오류로 보인다). 패널을 열 때만 읽는다. 🔴 한계: TENANT_ADMIN 라이더 P1 의 «무소속 자기 테넌트» 는 이 합에 안 잡힌다(그 actor 는 `org.manage` 없이는 이 화면에 못 온다). 고른 뒤에도 서버가 미리보기·쓰기에서 다시 판정한다.

**`/tenants` 생성 폼** — `TenantForm` 에 «소속 노드 (선택)»(`tenant-form-org-node`, 첫 항목 «소속 없음»). 비우면 draft 에 **키 자체가 없다**(`undefined` 값도 아님). 선택지는 `app/(console)/tenants/page.tsx` 가 서버에서 `listOrgNodes()` 로 읽어 `TenantsScreen` → `TenantForm` 으로 넘긴다(tenants feature 는 org-hierarchy 를 import 하지 않는다 — 자기 view-model `TenantOrgNodeOption`). 읽기 실패 → `null` → 칸은 «소속 없음» 만 + 안내문, 생성은 그대로 된다. 게이트 상태(무테넌트·권한·degrade)에선 읽지 않는다. 프록시 스키마 `orgNodeId` 선택(`null` 허용) · 라우트는 값이 있을 때만 넘김 · `tenants-api.ts` 는 값이 있을 때만 생산자 본문에 싣는다(없으면 3칸 그대로). 생성 404 `ORG_NODE_NOT_FOUND` → «선택한 소속 노드에 둘 수 없습니다 … 무소속으로 이미 만들어졌을 수 있으니 목록을 확인하고 …» — 계약의 경합 노트(`admin-api.md:1798`) 때문에 화면은 두 경우를 가르지 못하므로 둘 다 말한다.

**시험** — 신규 `tests/unit/features/org-hierarchy/TenantPlacement.test.tsx`(11, 실 `OrgNodeDetail` + React Query, `apiClient` 만 URL 라우팅 모의) · `tests/unit/tenant-placement-proxy.test.ts`(14) · `tests/unit/features/tenants/tenants-api-create-org-node.test.ts`(3). 보강 `TenantsScreen.test.tsx`(+5) · `tenants-proxy.test.ts`(+3) · `tenants-page.test.tsx`(+3, `@/features/org-hierarchy` 모의 추가) · `erp-master-ref-names.test.tsx`(그 파일의 `use-org-nodes` 부분 모의에 새 훅 4개 idle 스텁 — 없으면 `OrgNodeDetail` 렌더가 깨진다).

**게이트** — `npx tsc --noEmit` rc=0 · `npm run lint` rc=0 · `npx vitest run` rc=0(344 파일 · 3900/3900).

**AC-6 e2e grep** — `apps/console-web/e2e-smoke/**` · `apps/console-web/tests/e2e/**` · 루트 `tests/federation-hardening-e2e/**` 에서 `org-node|org-hierarchy|orgNode|소속 테넌트|tenant-form-|tenant-create|tenants-list|'/tenants|"/tenants|테넌트 등록|tenant-confirm` → **0 건**(같은 glob 의 `getByTestId|goto(` 는 28 파일 129 건 — glob 이 비어서 0 이 아님을 확인). src 를 import 해 라우트 목록을 도는 스펙도 0. ⚪ 머지 뒤 첫 nightly 콘솔 잡은 미확인.

**티켓과 다른 점**
- Edge Case «샘플 방문자 — 쓰기 버튼 없음(기존 샘플 규율)»: 실측한 기존 규율은 «버튼은 두고 서버가 `SAMPLE_READ_ONLY` 로 거절»이다(위 AC-0 마지막 항목 — 이 feature 의 생성·삭제·상한·관리자 버튼도 샘플에게 보인다). 새 버튼도 **그 규율을 따랐다**(버튼 표시 · 쓰기는 게이트웨이가 거절 · 셸 배너 + `messageForCode` 문구). 샘플에게만 버튼을 숨기려면 클라이언트가 샘플 여부를 알아야 하는데 지금 그 신호는 서버 레이아웃에만 있다 — 별도 결정이 필요하면 후속 티켓. 샘플의 미리보기는 픽스처가 없어 `503 SAMPLE_NOT_READY`(«이 화면의 샘플 데이터는 준비 중입니다») 로 확인 창이 잠긴다.
- 노드 이동 선택지는 도달 범위의 모든 노드(지금 노드 포함 — 하위 노드에 있는 테넌트를 이 노드로 직접 올리는 것도 «옮기기»). 같은 위치를 고르면 미리보기가 «바뀌는 것이 없습니다» 를 보인다(서버는 `changed=false` 로 멱등 처리).

# 닫기 — 4차원 검증 (2026-10-08 UTC, `date -u` 실측)

- (a) PR **#4227** `state=MERGED` · (b) `origin/main` 에 스쿼시 **`dab387e74`** · (c) 머지 시점 `statusCheckRollup` 실패 **0**.
- (d) AC-0~5 `[x]`. AC-6 의 마지막 칸(머지 뒤 nightly)을 닫는다: 머지 직후 nightly(`dab387e74`)는 빨강이었으나 실패는 **이 티켓과 무관한 1 건**(`overview-consolidation.spec.ts:82`, `TASK-PC-FE-314` 배지 — `TASK-PC-FE-320` 이 고침)이었고, 이 티켓의 표면을 포함한 첫 초록은 nightly `37740852093` (`cde64563b`) — «Platform Console E2E full-stack» **success**, 전체 run success.
