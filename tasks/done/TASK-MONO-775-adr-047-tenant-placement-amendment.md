# Task ID

TASK-MONO-775

# Title

`ADR-MONO-047` 개정 — 테넌트를 org-node 에 **두는** 일의 권한(«양쪽을 다 관리하는 사람만») + 단계 티켓 `TASK-BE-625` · `TASK-PC-FE-312` 기안

# Status

done

# Owner

monorepo

# Task Tags

- adr
- iam
- console

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (문서만 — 권한 경계 결정 기록)

---

# Dependency Markers

- 선행: 없음. 소유자 결정(2026-10-07 UTC, 선택창 *«① 양쪽 관리자 (추천)»*).
- 후속: `TASK-BE-625`(iam-platform) → `TASK-PC-FE-312`(platform-console).

# Goal

조직 계층에서 노드를 만들어도 테넌트를 넣을 방법이 없다(API 0 · 화면 0 · `assignOrgNode` 호출자 0). 이 쓰기는 관리 범위와 상한을 옮기므로 ADR-047 D5 가 정하지 않은 권한 결정이 필요했다. 소유자 결정을 ADR 개정으로 기록하고 구현 티켓을 기안한다.

# Scope

## In Scope

- `docs/adr/ADR-MONO-047-org-node-tenant-hierarchy.md` 끝에 § 개정 (2026-10-07) — 실측 · 결정 · 라이더 P1/P2 · 인가하는 티켓 · 건드리지 않는 것
- `projects/iam-platform/tasks/ready/TASK-BE-625-*` · `projects/platform-console/tasks/ready/TASK-PC-FE-312-*` + 두 프로젝트 INDEX

## Out of Scope

- 구현 · D1~D7 본문 변경 · 노드 생성 권한

# Acceptance Criteria

- [x] **AC-1** — 개정 절이 «로드맵에 없던 한 칸» 을 file:line 실측으로 적는다(API 0 · 생성 입력 · `assignOrgNode` 호출자 0 · V0028 백필뿐).
- [x] **AC-2** — 결정(출발 쪽 · 도착 쪽 · 404 · 감사)과 그 결과(목적지만 관리 = 막힘 · 주인 혼자 빼기 = 막힘)가 적혀 있고, 소유자 선택 원문이 인용돼 있다.
- [x] **AC-3** — 구현자 기본값은 라이더(P1 · P2)로 분리돼 «뒤집기» 문구가 있다.
- [x] **AC-4** — D1~D7 본문 무변경(덧붙이기만) — `git diff` 로 확인.
- [x] **AC-5** — 단계 티켓 둘이 필수 섹션을 갖추고, 기존 판정기(`TenantScopeGuard` · `OrgNodeScopeGuard`) 재사용 · 격리 대조군을 AC 로 갖는다.

# Related Specs

- `docs/adr/ADR-MONO-047-org-node-tenant-hierarchy.md`
- `projects/iam-platform/specs/contracts/http/admin-api.md` § Org Hierarchy

# Related Contracts

- 없음(문서만). 구현 시 `admin-api.md` · `internal/admin-to-account.md`.

# Edge Cases

- 개정이 ACCEPTED ADR 의 결정 범위를 넓히는가 — 아니다. D1 이 정한 «묶는다» 의 **쓰기 권한**만 채운다(새 개념 없음).

# Failure Scenarios

1. 개정에서 D5 문장을 고친다 — ACCEPTED 본문을 바꾸는 일이다. 덧붙이기만 한다.
2. 라이더를 결정처럼 적는다 — 소유자가 고른 것은 «① 양쪽 관리자» 하나이고, P1 · P2 는 구현자 기본값이다.

---

# 작업 기록 (2026-10-07 UTC)

> 분석 · 작성 = Opus 5.5.

## 실측 (코드 읽기 — 라이브 아님)

| 사실 | 근거 |
|---|---|
| org-node API 에 테넌트를 붙이는 엔드포인트 0 | `projects/iam-platform/specs/contracts/http/admin-api.md` § Org Hierarchy `:2198`~`:2478` |
| 테넌트 생성 입력에 노드 칸 없음 | `projects/platform-console/apps/console-web/src/app/api/tenants/_proxy.ts:22-30` |
| `Tenant.assignOrgNode()` 호출자 0 | account-service `domain/tenant/Tenant.java:144` · `git grep "assignOrgNode("` (정의 제외 0) |
| 소속 = V0028 백필뿐, 이후 테넌트는 무소속 | `V0028__backfill_org_node_per_tenant.sql` 머리 주석 |
| 판정기 둘이 이미 있다 | admin-service `TenantScopeGuard.java:49-62` · `OrgNodeScopeGuard.java` · `AdminGrantScopeEvaluator`(subtree) |

## 라이더를 둔 이유

- **P1**: «양쪽 관리자» 의 «지금 위치» 가 **무소속**일 때 그 테넌트의 관리자는 누구인가 — `SUPER_ADMIN` 만 두면 고객사가 자기 테넌트를 넣을 길이 없고, `TENANT_ADMIN` 을 넣되 «노드 아래 T» 에는 안 넣어야 상한 탈출을 막는다. 소유자 선택지에 이 세부가 없어 기본값으로 두었다.
- **P2**: 상한이 deny-only(D2)라 옮겨도 구독 행은 남는다 — 거절보다 확인 화면이 덜 놀랍다.

## 검증

- AC-4: `git diff --numstat origin/main -- docs/adr/ADR-MONO-047-org-node-tenant-hierarchy.md` = **+51 / −0** (덧붙이기만).
- AC-5: 두 티켓 모두 Goal · Scope · AC · Related Specs · Related Contracts · Edge Cases · Failure Scenarios 를 갖고, BE-625 AC-1 이 격리 대조군(실패 쪽 먼저)이다.
- 가드: 스테이지 뒤 필수 3종 + `check-adr-index-drift.sh`.

---

## 닫기 기록 (2026-10-07 UTC) — 4차원

| 차원 | 결과 |
|---|---|
| (a) `gh pr view 4211` | `state=MERGED` · mergeCommit `44fd37dbc` |
| (b) origin/main 조상 | 참 |
| (c) 머지 시점 실패 체크 | 66 중 **FAILURE 0** |
| (d) `# Acceptance Criteria` | AC-1 ~ AC-5 `[x]` — 동사(«적는다» · «무변경» · «갖춘다») = 개정 문서 + 티켓 기안으로 닫힘 |

- 후속: iam `TASK-BE-625`(ready) → console `TASK-PC-FE-312`(ready, BE-625 선행).
