# Task ID

TASK-PC-FE-382

# Title

익명 샘플에 «업무 권한 세트» · «테넌트 진입 정책» 이 없다 — 샘플 원장의 `pending` 두 표면(`domain_permission_sets` · `tenant_entry_policy`)을 `ready` 로

# Status

review

# Owner

frontend

# Task Tags

- code
- console
- sample

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (iam 샘플 픽스처 · 원장 · 스키마 시험 — 기존 `ready` 표면과 같은 모양)
> Drafted 2026-10-28 UTC. 출처: 소유자 질문 «샘플데이터는 더 추가할 필요 없어?» → 샘플 원장 실측(표면 37 = `ready` 34 · `pending` 3) → 소유자 «진행». 셋째 `pending`(`flat:fan`)은 의도된 것이라 범위 밖(아래).

# Dependency Markers

- 관련: `ADR-MONO-074`(익명 방문자 = 실제 콘솔 + 합성 샘플, A4 «데이터는 합성» · A9 «샘플 없는 GET 은 섹션 degrade + 원장»), `TASK-PC-FE-282`(샘플 원장), `TASK-PC-FE-338`(done — 업무 권한 세트 화면; 원장 주석 «P2 생산자가 막 머지돼 아직 픽스처 없음» 으로 `pending` 을 남겼다), `TASK-MONO-771` S5(테넌트 진입 정책 칸; 같은 이유로 `pending`).

# Goal

로그인하지 않은 방문자가 `/domain-permission-sets` 와 테넌트 상세(`/tenants/[tenantId]`)의 진입 정책 칸, 그리고 같은 표면을 읽는 다른 화면에서 «이 화면의 샘플 데이터는 준비 중입니다» 대신 합성 샘플을 본다.

# Scope

## In Scope

- **AC-0 사실 (기안 중 실측 — 2026-10-28 UTC; 🔴 착수 시 file:line 재확인):**
  - 원장 `projects/platform-console/apps/console-web/src/shared/sample/coverage.ts`: `{ core: 'iam', surface: 'tenant_entry_policy', status: 'pending', owner: 'TASK-MONO-771' }`(`:77`) · `{ core: 'iam', surface: 'domain_permission_sets', status: 'pending', owner: 'TASK-PC-FE-338' }`(`:81`) — 각 주석(`:74-76` · `:78-80`)이 «아직 픽스처 없음 → 503 `SAMPLE_NOT_READY` → 화면이 자기 degrade 문구» 라고 적는다. `:237` 근처에 tenant 상세 쪽 `pending` 언급이 하나 더 있다(같은 표면).
  - `domain_permission_sets` 클라이언트(`features/domain-permission-sets/api/domain-permission-sets-client.ts:34-35,60`)의 GET: `GET /api/admin/domain-roles` · `GET /api/admin/domain-permission-sets` · `GET /api/admin/domain-permission-sets/{setId}`(`domain-permission-sets-api.ts:39,48,60-61`). 같은 표면을 읽는 화면: `app/(console)/domain-permission-sets` · `operators` · `permission-sets` · `security-settings`(grep — 각 화면이 어느 GET 을 부르는지 착수 시 확정).
  - `tenant_entry_policy` 클라이언트(`features/tenant-entry-policy/api/entry-policy-client.ts:29,32`)의 GET: `GET /api/admin/tenants/{tenantId}/entry-policy` · `…/entry-policy/enrolment-summary`(`entry-policy-api.ts:18,46`). 화면: `app/(console)/tenants/[tenantId]`.
  - 생산자 계약(모양의 정본): `projects/iam-platform/specs/contracts/http/admin-api.md` § `GET /api/admin/domain-roles`(`:3985`) · § `GET /api/admin/domain-permission-sets`(`:4005`) · § `…/{setId}`(`:4029`) · 카탈로그 § 시스템 프리셋 · § `GET /api/admin/tenants/{tenantId}/entry-policy`(`:2685`) · § `…/enrolment-summary`(`:2741`).
  - 샘플 라우터 키는 `<core>:<surface>`(`shared/sample/fixtures/index.ts`), iam 픽스처는 `shared/sample/fixtures/iam.ts`(경로 상수 + 행 배열 + 핸들러). 원장 시험 `tests/unit/sample-coverage-ledger.test.ts` 가 원장 ↔ 소스 인벤토리를 양방향 대조한다.
- **구현:** iam 픽스처에 두 표면의 GET 응답을 생산자 계약 모양 그대로 합성(업무 역할 카탈로그 · 시스템 프리셋 + 샘플 테넌트 세트 1~2개 · 세트 상세 · 샘플 테넌트(들)의 진입 정책 + 등록 요약). 쓰기(POST · PATCH · DELETE · PUT)는 ADR-074 R1 의 기존 샘플 쓰기 처리 그대로(새 규칙 없음). 원장 두 행 `pending` → `ready`(주석 갱신, owner 는 기존 `ready` 행의 관례를 따른다). 없는 `setId` · `tenantId` 는 실제 백엔드와 같은 404 모양(`FixtureNotFound`).
- **값:** 합성이고 «(샘플)» 표기 관례(`SAMPLE_LABEL_SUFFIX`)를 따른다. 시스템 프리셋 키 · 업무 역할 키는 생산자 카탈로그의 실제 키를 쓴다(화면이 키로 라벨을 찾으므로) — 다른 iam 샘플(운영자 · 그룹 배정)이 세트를 참조하면 그 id 와 맞춘다.

## Out of Scope

- `flat:fan`(`coverage.ts:132`) — 플랫폼 운영자 전용 섹션이고 샘플 레지스트리에 `fan` 테넌트가 없어 샘플 방문자에게 메뉴가 안 보인다(주석 `:129-131`). 의도된 `pending`.
- 그래프용 샘플 밀도(이미 충분 — 월간 6~10/15일, `TASK-PC-FE-374`·`378`·`380`).
- 백엔드 · 계약 변경, 로그인 화면 동작 변경.

# Acceptance Criteria

- [x] **AC-0** 위 사실을 착수 시점 코드로 재확인하고, 두 표면을 읽는 화면 · GET 전수를 표로 남긴다(어긋나면 정정 기록 후 진행).
- [x] **AC-1** 원장의 두 행이 `ready` 이고 `sample-coverage-ledger.test.ts` 가 초록. 수정 전에는 두 표면에 픽스처가 없어 원장을 `ready` 로만 바꾸면 그 시험이 빨강임을 확인(바꾸는 순서의 증거).
- [x] **AC-2** 새 픽스처 응답이 콘솔의 zod 스키마(`features/domain-permission-sets/api/types.ts` · `features/tenant-entry-policy/api/types.ts`)를 통과하는 시험 — 기존 `sample-fixtures-schema-*` 시험 관례를 따른다. 없는 id → 404 모양 시험.
- [x] **AC-3** 쿠키 없는 렌더(단위 또는 로컬 `next dev` 샘플 방문)에서 `/domain-permission-sets` 와 `/tenants/<샘플 테넌트>` 진입 정책 칸에 «준비 중» 문구가 없고 샘플 행이 보인다. 같은 표면을 읽는 다른 화면(`operators` · `permission-sets` · `security-settings`)도 «준비 중» 이 사라졌는지 확인.
- [x] **AC-4** console-web `lint` · `tsc` · vitest(`--maxWorkers=2 --minWorkers=1`) 전체 초록. 샘플 격리 가드(샘플 모듈이 네트워크 · 세션 · env 를 임포트하지 않음) 초록.
- [ ] **AC-5 (라이브, 배포 뒤)** Vercel Production 에 배포된 뒤 로그아웃 상태로 `console.hubwang.com/domain-permission-sets` 와 샘플 테넌트 상세를 열어 «준비 중» 이 없음을 확인(데모 창 불필요).

# Related Specs

- `docs/adr/ADR-MONO-074-anonymous-visitors-see-the-real-console-with-sample-data.md` (A4 · A9 · R1)
- `projects/platform-console/specs/contracts/console-integration-contract.md` (샘플 모드 문단들)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` — 위 절들. **읽기만, 변경 없음.**

# Edge Cases

- 샘플 테넌트가 여럿이면 진입 정책 GET 이 테넌트마다 다른 값을 돌려도 되지만, 레지스트리에 없는 테넌트 id 는 404.
- 업무 권한 세트 목록에 시스템 프리셋(읽기 전용, `tenantId` 없음)과 테넌트 세트가 섞인다 — 화면이 둘을 구분해 그리므로 둘 다 넣는다.
- 다른 iam 샘플(운영자 · 그룹)의 배정 행이 `setId` 를 참조하면 그 id 가 이 목록에 있어야 한다(없으면 화면에 «알 수 없는 세트»).

# Failure Scenarios

1. 원장만 `ready` 로 바꾸고 픽스처를 안 넣는다 — 원장 시험이 빨강(AC-1 이 지키는 것).
2. 픽스처 모양이 생산자 계약과 다르다 — 샘플 화면은 초록인데 실제 백엔드와 갈라진다(35차 창 `TASK-PC-FE-373` 의 `alertId` ↔ `id` 사고와 같은 클래스). AC-2 는 콘솔 스키마만 본다 — 키 이름은 생산자 계약 절과 대조해 구현 보고에 적는다.
3. 세트 키 · 역할 키를 지어낸다 — 화면 라벨이 «unknown» 으로 나온다.

# Implementation Notes (2026-10-28 UTC)

## AC-0 (착수 시점 재확인 — 어긋남 없음, 보강 1건)

| 화면 | 읽는 표면 | 부르는 GET |
|---|---|---|
| `/domain-permission-sets` | `iam:domain_permission_sets` | `GET /api/admin/domain-roles` · `GET /api/admin/domain-permission-sets` (상세 `…/{setId}` 는 BFF 만 — 화면은 목록으로 그림) |
| `/tenants/[tenantId]` (진입 정책 칸) | `iam:tenant_entry_policy` | `GET …/{tenantId}/entry-policy` · 클라이언트에서 `…/enrolment-summary` |
| `/security-settings` | `iam:tenant_entry_policy` | 위와 같음 (활성 테넌트) |
| `/operators` (도메인 접근 대화상자) | `iam:domain_permission_sets` | BFF `/api/domain-permission-sets` (세트 이름 목록) |
| `/operator-groups` (그룹 세트 대화상자) | `iam:domain_permission_sets` | 같은 BFF |
| `/permission-sets` | 없음 | 링크만 (`/domain-permission-sets`) |

- 보강: 화면 원장 `SCREEN_COVERAGE` 의 `/domain-permission-sets` · `/security-settings` 도 `pending` 이었다 — «준비 중» 문구는 이 화면 행에서 나오므로 표면 행과 함께 `ready` 로 바꿨다.
- 범위 밖 발견: 운영자의 `GET /operators/{id}/domain-access` 와 그룹의 `GET /groups/{id}/domain-permission-sets` 는 각각 `operators` · `groups` 표면이고 픽스처가 없다(503) — 샘플의 배정 행이 `setId` 를 참조하지 않으므로 «알 수 없는 세트» 문제는 없다. 별도 티켓 후보.

## AC-1
원장 두 행만 `ready` 로 바꾸고 픽스처 없이 `sample-coverage-ledger.test.ts` 실행 → rc=1, 4건 빨강:
`iam:tenant_entry_policy at /: expected 503 to be 200` · `iam:domain_permission_sets at /: expected 503 to be 200` · `every fixture belongs to a ready row…` · `every ready row … has an explicit SURFACE_SAMPLE_PATH entry`. 픽스처 추가 뒤 초록.

## AC-2 / AC-3 / AC-4
- `tests/unit/sample-fixtures-schema-iam-domain-sets.test.tsx` — 스키마 통과 · 와이어 키 · 프리셋 systemKey 33 · 카탈로그 40 · 404 · 쓰기 403.
- `e2e-smoke/sample-visitor-iam-domain-sets.spec.ts` — 로컬 `next dev -p 3010` + smoke 더미 env, 쿠키 없는 방문 3건 통과.
- `pnpm lint` rc=0 · `npx tsc --noEmit` rc=0 · `npx vitest run --maxWorkers=2 --minWorkers=1` rc=0 (452 files · 5609 tests).

## Deviations
- 진입 정책 GET 이 레지스트리에 없는 테넌트에 404 `TENANT_NOT_FOUND` — 티켓 Edge Case 를 따랐다. 실제 백엔드 GET 은 404 가 아니라 `requireMfa=false` 를 낸다.
- 카탈로그 `label` · 프리셋 `name`/`description` 에 `(샘플)` 접미 — 라벨 가드(R2ⓐ) 때문.
- `label-rule.ts` 에 기계값 키 4개(`domainKey` · `role` · `access` · `systemKey`) 추가.
- 등록 요약은 `IAM_OPERATORS` 에서 파생, 모든 샘플 테넌트가 같은 수(운영자 풀이 테넌트 무관).

## 남은 것
AC-5 (배포 뒤 라이브 확인) — 미체크.

---

## CORRECTION (2026-10-28 UTC)
위 Deviations 첫 항목(«진입 정책 GET 이 레지스트리에 없는 테넌트에 404 `TENANT_NOT_FOUND`»)은 더 이상 사실이 아니다. 티켓 Edge Case «레지스트리에 없는 테넌트 id 는 404» 는 생산자 계약(`admin-api.md` § GET …/entry-policy: «행이 없으면 `{tenantId, requireMfa:false, updatedAt:null, updatedBy:null}` — 404 가 아니다»)과 어긋났고, 픽스처는 계약을 따르도록 고쳤다: 정책 행이 없는 tenantId → 200 + 위 기본 본문(tenantId = 경로값). `…/enrolment-summary` 는 계약이 미등록 테넌트를 말하지 않는다(400 `'*'`/정규식 · 503 만) — 정책 엔드포인트와 일관되게 200 + 0 건수(소속 운영자 없음)로 했다; 읽기는 테넌트 존재 확인을 하지 않는다는 계약 문구와도 맞다. 없는 `setId` 404 `DOMAIN_PERMISSION_SET_NOT_FOUND` 는 계약(§ GET …/{setId} 오류표)대로 유지.
