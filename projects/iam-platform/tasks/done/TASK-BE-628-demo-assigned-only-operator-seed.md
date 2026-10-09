# Task ID

TASK-BE-628

# Status

done

# Title

데모 시드에 «로그인할 수 없는 · 배정만 된 운영자» 1명(HOME `ecommerce`, `demo-corp` 에만 배정)을 넣어, 콘솔 그룹 «멤버 추가» 의 «○○ 소속 · 배정만 됨» 회색 행을 데모 창에서 볼 수 있게 한다

# Owner

iam-platform

# Task Tags

- admin-service
- demo-seed

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 — dev 시드 SQL 한 파일 + 시드 IT 단언. (실제 구현: Opus 5.5)
>
> 🔵 **소유자 결정(2026-10-08 UTC):** `TASK-PC-FE-319` 라이브 확인 방법을 묻는 대화에서 «데모 시드에 대상이 없다» → 선택지 «1 — 로그인 불가 운영자 시드 + 다음 굽기» → «1». 이어 «지금 열린 창에 SSM 으로 한 번 넣기» → «지금 넣기»(그 1회 삽입은 다른 세션이 창에서 실행 — 아래 Implementation Record).

---

# Dependency Markers

- 선행: iam `TASK-BE-626`(done — 목록 `homeTenantId`).
- 후속: platform-console `TASK-PC-FE-319` AC-6(라이브) — 이 시드로 확인한다.
- 🟡 다음 데모 이미지 굽기에 실려야 시드로 존재한다(굽기 시점 = 소유자 결정). 그 전까지는 2026-10-08 창에 SSM 으로 넣은 1회분만 있다(창 교체 시 사라짐).

# Background (기안 시 측정, `origin/main` `d186f8a48`)

| 사실 | 근거 |
|---|---|
| `demo@demo.com` 의 SUPER_ADMIN grant 행은 HOME `demo-corp` 에 묶인다 → 그룹 생성·변경은 `demo-corp` 만 가능(`ecommerce` 그룹 생성 = `TENANT_SCOPE_DENIED`, 소유자 실측) | `apps/admin-service/src/main/resources/db/migration-dev/R__seed_demo_operator.sql:77-81` · `application/GroupAdminUseCase.java:85,319` |
| `demo-corp` 목록(HOME ∪ 배정)에 «배정만 된» 운영자가 시드에 없다 — demo · requester = HOME demo-corp, viewer = 배정 0 | 같은 디렉터리의 `R__seed_demo_operator.sql` · `R__seed_demo_viewer_operator.sql` |
| `password_hash` · `oidc_subject` 는 NULL 허용, 둘 다 NULL = 로그인 경로 없음(fail-closed) | `specs/services/admin-service/data-model.md:28,33` |
| auth-service `DemoSecondOperatorSeedTest` 는 `R__seed_demo_operator.sql` 에 demo-corp 운영자가 **정확히 둘**임을 단언 — 그 파일에 넣으면 빨강 | `R__seed_demo_viewer_operator.sql:15-22` 주석 |
| 데모 검증기(`infra/demo/verify-demo-wrapper.sh`)는 운영자 행 수를 세지 않는다(뷰어 · 플랫폼 자격 파일만 읽음) | `verify-demo-wrapper.sh:2257,2291` |

# Goal

데모의 `demo-corp` 그룹 «멤버 추가» 에 HOME 이 다른 운영자가 하나 보이고, 그 행이 «ecommerce 소속 · 배정만 됨» 으로 비활성이다. 그 운영자로는 누구도 로그인할 수 없다.

# Scope

## In Scope

- 새 파일 `apps/admin-service/src/main/resources/db/migration-dev/R__seed_demo_assigned_only_operator.sql` — `demo-assigned-only` · HOME `ecommerce` · `assigned-only@demo.com` · `password_hash` NULL · `oidc_subject` NULL · 역할 없음 · `operator_tenant_assignment` = `demo-corp` 하나.
- `DemoOperatorSeedIntegrationTest` 에 단언 셋: 행 모양(로그인 칸 둘 다 NULL) · 역할 0 · 배정 = 정확히 {demo-corp} · demo-operator 가 `demo-corp` 목록에서 이 운영자를 `homeTenantId=ecommerce` 로 본다(대조군: demo-operator 자신은 `demo-corp`).

## Out of Scope

- prod 마이그레이션(`db/migration`) — dev/demo 전용.
- 데모 이미지 굽기 — 소유자 지목 시.

# Acceptance Criteria

- [x] **AC-1** — 시드 파일이 위 모양이고 멱등(INSERT … ON DUPLICATE KEY UPDATE / INSERT IGNORE).
- [x] **AC-2** — 로그인 불가: `password_hash` · `oidc_subject` 둘 다 NULL, 역할 행 0 — IT 단언.
- [x] **AC-3** — 배정은 정확히 `{demo-corp}` — IT 단언(넓어지는 쪽도 빨강).
- [x] **AC-4** — `GET /api/admin/operators?tenantId=demo-corp` 가 이 운영자를 `homeTenantId=ecommerce` 로 돌려준다 — IT 단언.
- [x] **AC-5** — CI iam 통합 레인(`Integration (iam A/B)`) 초록. impl PR **#4246** `statusCheckRollup` 확인: `Integration (iam A, Testcontainers) / integration` SUCCESS · `Integration (iam B, Testcontainers) / integration` SUCCESS(둘 다).
- [x] **AC-6** — 다음 데모 굽기 뒤 창에서 그 행이 회색으로 보인다(= `TASK-PC-FE-319` AC-6 과 같은 관찰). ⚪→✅ 25차 데모 창(2026-10-09 UTC, AMI `ami-03cc7efda4b0a7809` 재굽기) 라이브: 소유자가 demo-corp 에 그룹을 만들고 «멤버 추가» 에서 `Store Staff (demo-corp assignment only)`(`assigned-only@demo.com`, HOME ecommerce) 행이 «ecommerce 소속 · 배정만 됨» 으로 비활성 표시됨을 확인했다.

# Related Specs

- `projects/iam-platform/specs/services/admin-service/data-model.md` (`admin_operators` · `operator_tenant_assignment`)
- `docs/adr/ADR-MONO-046-operator-group-model.md` § D3 (그룹 = HOME 운영자)

# Related Contracts

- 없음 — 시드 데이터. `GET /api/admin/operators` 의 `homeTenantId` 는 `TASK-BE-626` 계약.

# Edge Cases

- 데모 SUPER_ADMIN(공개 자격)이 이 운영자에게 역할을 줄 수 있다 → 그래도 로그인 칸이 없어 쓸 수 없다. 재적용으로 되돌려지지 않는다(뷰어 시드와 같은 한계, 파일 주석에 기록).
- `ecommerce` 테넌트의 운영자 목록에도 이 운영자가 HOME 으로 나온다 → 의도된 부산물(쇼핑몰 테넌트 소속 직원이라는 설정).

# Failure Scenarios

- 로그인 칸 하나라도 채워짐 → 공개 데모에 계정이 생김 → AC-2 IT 가 막는다.
- 배정이 늘어남 → 아무도 검토 안 한 테넌트로 도달 → AC-3 IT(정확히 하나) 가 막는다.

# Implementation Record (2026-10-08 UTC)

- 변경: 새 `R__seed_demo_assigned_only_operator.sql` · `DemoOperatorSeedIntegrationTest.java`(+3).
- 라이브 1회분: 같은 SQL 을 2026-10-08 창(i-0445d76661ef0013d)의 iam DB 에 SSM 으로 넣기를 소유자가 승인 → 그 창을 운영 중인 다른 세션에 실행 요청. 결과는 PR · `TASK-PC-FE-319` 쪽에 기록.
- IT 는 Testcontainers 라 CI iam 통합 레인에서 판정(AC-5).

# 25차 데모 창 측정 기록 (2026-10-09 UTC, AMI `ami-03cc7efda4b0a7809`) — AC-5·6 닫음

- **AC-5** — impl PR #4246 머지 시점 `statusCheckRollup`: `Integration (iam A, Testcontainers) / integration` = SUCCESS, `Integration (iam B, Testcontainers) / integration` = SUCCESS. 둘 다 초록.
- **AC-6** — 재굽기(AMI 핀 갱신, 25차)로 이 시드가 데모 이미지에 실제로 올라간 뒤, 소유자가 demo-corp 에 그룹을 만들어 «멤버 추가» 화면에서 `assigned-only@demo.com`(HOME ecommerce) 행이 «ecommerce 소속 · 배정만 됨» 으로 비활성 표시됨을 확인했다.
- 이 티켓의 전체 AC(1~6) 가 닫혔다.
