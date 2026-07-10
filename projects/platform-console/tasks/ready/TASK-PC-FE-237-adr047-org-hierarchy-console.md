# Task ID

TASK-PC-FE-237

# Title

ADR-047 § 4 step 3 — 콘솔 조직 계층 화면: org-node 트리 CRUD + entitlement ceiling 편집기 + `ORG_ADMIN` 배정 + 테넌트 스위처 org-node 그룹핑

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

---

# Dependency Markers

- **선행 (prerequisite)**: `TASK-BE-492` — `/api/admin/org-nodes` (CRUD + `/{id}/ceiling` + `/{id}/admins` + `/{id}/tenants`, gate `org.manage`) must be merged and live. This task consumes it; it does not define it.
- **관련**: `TASK-BE-490` (계약 = `specs/contracts/http/admin-api.md` org-node 절), `TASK-BE-491` (ceiling 의미론).

---

# Goal

ADR-047의 **회사 → 서비스 → 도메인** 3축 구조를 콘솔에서 조작 가능하게 만든다.

완료 후:

1. `/org-hierarchy` (IAM 메뉴) 에서 org-node 트리를 **조회·생성·이름변경·재부모화·삭제** 할 수 있다.
2. 노드의 **entitlement ceiling** 을 편집할 수 있고, UI 는 ceiling 이 **좁히기만 한다**(deny-only)는 의미를 오해 없이 드러낸다 — "권한 부여"가 아니라 "상한". 부모보다 넓은 ceiling 은 저장 전에 막힌다.
3. 노드에 **`ORG_ADMIN`** 을 배정/해제할 수 있고, no-escalation(내가 가진 것 이하 · 노드 ceiling 이하 · `SUPER_ADMIN` 불가)이 UI 에서도 게이팅된다(기존 grantable-roles 컨벤션 재사용).
4. **테넌트 스위처가 org-node 로 그룹핑**된다 (AWS IdC account-picker parity) — 한 회사의 여러 서비스-테넌트가 회사 이름 아래 묶여 보인다. 그룹이 없는(ungrouped) 테넌트는 평면 항목으로 그대로 노출된다.

---

# Scope

## In Scope

- `src/features/org-hierarchy/` 신설: 트리 뷰(중첩 `<ul>` + `aria-expanded`, native `<details>` drill-down 컨벤션), 노드 상세, ceiling 편집기, `ORG_ADMIN` 배정 패널.
- `shared/api/org-nodes.ts` — BFF 프록시 클라이언트 (`GET/POST /api/admin/org-nodes`, `GET/PATCH/DELETE /{id}`, `PUT /{id}/ceiling`, `GET /{id}/tenants`, `GET/POST/DELETE /{id}/admins`).
- **BFF 프록시 라우트 추가** (누락 시 프런트만 404 — `ecommerce-ops` 6대 버그 클래스 ①).
- 테넌트 스위처: `GET /api/admin/org-nodes` + 기존 테넌트 목록을 합쳐 회사별 그룹 렌더. **ungrouped(`org_node_id = null`) 테넌트는 반드시 계속 보여야 한다** (D7 lazy-migration 합법 상태).
- SSR 회복탄력성: org-node 조회 실패 시 degrade 렌더(스위처는 평면 폴백), 전체 화면 500 금지.
- 상세/폼 UI 컨벤션 준수: 공유 `DetailHeader` ghost, `dl` 순서 = 명칭 → 상태 → 식별자 → 날짜. 날짜는 공유 `shared/lib/datetime.ts` (`formatDateTime` 24h·KST), `toLocale*` 직접 호출 금지.
- 테스트: vitest 유닛(트리 렌더·ceiling subset 클라이언트 검증·no-escalation 게이팅·ungrouped 폴백·degrade), axe 접근성.

## Out of Scope

- 백엔드 로직 일체 (BE-491/492 소유). 클라이언트 검증은 **UX 보조**일 뿐 — 권위는 서버 422/403 이다.
- ceiling 을 role 단위로 편집 (D3-B, 후속 ADR).
- 노드에서의 권한 **부여**(grant-at-node, D2-C — 기각/보류).
- 테넌트 CRUD 자체(기존 화면 유지).

---

# Acceptance Criteria

- [ ] **AC-1**: `/org-hierarchy` 에서 트리 조회 + 노드 생성/이름변경/재부모화/삭제가 동작하고, 서버 422(cycle / depth / ceiling-not-subset / 자식-보유 삭제)가 각각 구분 가능한 한국어 메시지로 표면화된다.
- [ ] **AC-2**: ceiling 편집기가 **상한** 의미를 명시하고(부여 아님), 부모 ceiling 초과 선택을 저장 전에 차단하며, 서버 422 도 동일하게 처리한다. **비어있는 ceiling `{}` 선택 시 "이 회사의 모든 서비스가 어떤 도메인도 사용할 수 없게 됩니다" 경고**를 띄운다(unbounded 와 혼동 금지).
- [ ] **AC-3**: `ORG_ADMIN` 배정 UI 가 grantable-roles 컨벤션으로 게이팅된다 — 내가 못 가진 롤·노드 ceiling 밖 도메인·`SUPER_ADMIN` 은 선택 불가. 서버가 403/422 로 거절하면 그대로 표면화한다.
- [ ] **AC-4**: 테넌트 스위처가 org-node 로 그룹핑되고, **ungrouped 테넌트가 사라지지 않는다**(전용 테스트). org-node 조회 실패 시 평면 목록으로 degrade 하고 스위처는 계속 동작한다.
- [ ] **AC-5**: BFF 프록시 라우트가 모든 신규 엔드포인트에 대해 존재한다(누락 시 프런트 404). 204/205/304 응답에 본문을 싣지 않는다(`Response` TypeError → Next 500 함정).
- [ ] **AC-6**: `org.manage` 없는 운영자는 메뉴/화면에 접근할 수 없고 403 을 정상 렌더한다(빈 화면·크래시 금지).
- [ ] **AC-7**: `pnpm lint` 0 + `tsc` 0 + 전체 `vitest run` GREEN(기존 스위트 무회귀). axe 위반 0.

---

# Related Specs

> `platform/entrypoint.md` Step 0 먼저. `projects/platform-console/PROJECT.md` 의 domain/traits 로 rule layer 로드.

- `docs/adr/ADR-MONO-047-org-node-tenant-hierarchy.md` D2/D3/D5 (**권위** — ceiling 은 좁히기만, `ORG_ADMIN` 은 subtree)
- `docs/adr/ADR-MONO-024-tenant-admin-delegation.md` § D2/D3 (no-escalation)
- `docs/adr/ADR-MONO-013-platform-console-foundation.md`, `ADR-MONO-017-platform-console-bff-architecture.md`
- `projects/iam-platform/specs/services/admin-service/rbac.md` (`org.manage`, `ORG_ADMIN`)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` (org-node 절 — BE-490 이 확정)

---

# Target Service

- `platform-console` (`console-web` + `console-bff` 프록시)

---

# Implementation Notes

- **ceiling 은 부여가 아니다.** UI 문구가 "허용 도메인 선택"처럼 읽히면 D2-A 의 의미가 뒤집힌다. "이 회사에서 사용할 수 있는 도메인의 **상한**" 으로 표기.
- **unbounded vs `{}`**: 상한 미설정(unbounded, 제한 없음)과 빈 집합(아무것도 불가)은 정반대다. 토글/체크박스로 뭉뚱그리면 운영자가 회사 전체를 잠근다. 별도 상태로 렌더.
- 클라이언트 subset 검증은 UX 보조 — 서버가 권위. 클라이언트가 통과시켜도 서버 422 를 반드시 표면화.
- 스위처 그룹핑은 **표시 전용**. 토큰은 여전히 단일 `tenant_id` 를 싣는다(M1) — 그룹을 "선택"할 수 있는 것처럼 보이면 안 된다. 선택 가능한 것은 언제나 leaf 테넌트다.
- 로컬 검증 전 `pnpm lint` 필수(`no-unused-vars` 로 CI 프런트 RED — tsc/vitest 가 못 잡음).

---

# Edge Cases

- ungrouped 테넌트(‎`org_node_id = null`) — D7 lazy 상태에서 합법. 스위처에서 절대 숨기지 말 것.
- 깊이 5 트리 — 들여쓰기가 뭉개지지 않아야 하고, 재부모화 드롭 타깃이 자기 자손이면 UI 에서 막는다(서버도 422).
- 노드 삭제 시 자식/테넌트 보유 → 서버 422. UI 는 사전 경고 후 서버 응답을 그대로 표면화.
- ceiling 을 좁힌 직후 해당 도메인을 이미 구독 중인 자식 테넌트 — 구독 행은 남지만 **해석된** `entitled_domains` 에서 빠진다. UI 는 "구독 중이나 상한으로 차단됨" 상태를 구분해 보여준다(구독 관리 화면은 행을 계속 보여줌 — BE-491 이 관리 read 를 좁히지 않음).
- 비-ASCII 노드 이름이 경로 세그먼트로 들어갈 때 400(`[id]` 함정) — id 는 UUID/숫자, 이름은 쿼리/본문으로만.
- org-node API 다운 → 트리 화면은 degrade, 스위처는 평면 폴백. 로그인/기존 화면 무영향.

---

# Failure Scenarios

- ceiling 편집기가 "부여" 로 읽히는 문구 → 운영자가 상한을 넓혀 권한을 준다고 착각. 실제로는 아무 것도 부여되지 않아 "버그" 리포트가 온다. Guard: AC-2.
- unbounded 와 `{}` 를 한 컨트롤로 표현 → 운영자가 회사 전체를 잠근다(fail-closed 이므로 조용히 전 서비스 403). Guard: AC-2 경고 + 별도 상태.
- 스위처 그룹핑 후 ungrouped 테넌트 누락 → lazy-migration 상태의 고객이 콘솔에서 사라진다. Guard: AC-4 전용 테스트.
- BFF 프록시 라우트 누락 → 백엔드는 200 인데 프런트만 404 (알려진 6대 버그 클래스). Guard: AC-5.
- 그룹 자체를 선택 가능한 것처럼 렌더 → M1(토큰 단일 `tenant_id`) 오해를 UI 가 조장. Guard: leaf 만 선택 가능.
- `pnpm lint` 생략 → `no-unused-vars` 로 CI 프런트 RED. Guard: AC-7.
