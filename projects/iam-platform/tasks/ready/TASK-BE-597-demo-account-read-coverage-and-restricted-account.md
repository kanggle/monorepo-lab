# Task ID

TASK-BE-597

# Title

데모 계정에 부족한 읽기 권한 보강 + 무권한 테스트 계정 신설 (AC-0 = SUPER_ADMIN partnership.manage 제외 사유부터 확인)

# Status

ready

# Owner

backend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus 5.5 — 역할·권한 카탈로그 변경은 이 저장소의 auto-mode 분류기가 게이트하는 축(권한 매트릭스 변경)이라 AC-0의 판단과 배선을 한 사람이 이어 가야 한다.

---

# Goal

소유자 결정(2026-09-24): 데모 테스트 계정(`demo@demo.com`)이 콘솔의 모든 메뉴를 렌더할 수 있도록 **부족한 읽기 권한을 보강**하고, 권한 부족 상태(403)를 시연할 수 있는 **최소/무권한 신규 계정**을 하나 추가한다. 실제 운영 데이터 접근은 허용하지 않는다.

# Scope

## In Scope

- **AC-0 먼저** — `demo-operator` 는 이미 `SUPER_ADMIN`(`R__seed_demo_operator.sql:77-81`)을 보유하고, `rbac.md` Seed Matrix(2026-09-24 UTC 실측 확인, :100-114)는 `SUPER_ADMIN` 이 `partnership.manage` 를 **보유하지 않는다**(`❌`, :112)는 것을 보여준다. 같은 표 바로 아래(:118)에는 `tenant.admin.delegate` 가 SUPER_ADMIN 에 ❌ 인 이유가 명시돼 있다("SUPER_ADMIN 은 플랫폼-unconstrained 권한으로 위임하며 이 키를 거치지 않는다 — 이 키는 in-tenant sub-delegation 을 게이트") — **그러나 `partnership.manage` 자체에는 이 파일에 같은 형태의 명시적 문장이 없다.** 이 티켓은 `partnership.manage`가 SUPER_ADMIN에서 빠진 것이 (a) `tenant.admin.delegate`와 같은 구조적 이유(플랫폼 스코프가 이미 우회한다)인지, (b) 분리된 보안 결정(separation of duties)인지 **rbac.md 전체 + 관련 ADR을 다시 읽어 판단**한다.
- **판단 이후 갈래**:
  - **(a) 구조적 이유로 결론 나면** — `demo-operator` 에 `partnership.manage` 를 직접 부여하지 말고, **별도 데모 전용 역할/권한 세트**를 통해 부여하는 방법을 우선 검토한다(SUPER_ADMIN 정의 자체를 건드리지 않는다).
  - **(b) 분리된 보안 결정으로 판단되면** — **STOP, 소유자에게 묻는다**(SUPER_ADMIN에 권한을 추가하는 것은 spec 변경이자 보안 경계 변경이다).
  - 역할 카탈로그가 바뀌면 `rbac.md` 를 **먼저** 갱신한다(Spec-먼저 규율, `CLAUDE.md` § Layer Rules).
- **읽기 권한 전수 보강**: `demo-operator` 가 콘솔의 모든 nav 항목을 200으로 렌더할 수 있도록, 지금 403이 나는 화면을 나열하고(`TASK-PC-FE-298` 의 권한 매핑 표가 이미 있으면 그것을 근거로 사용, 없으면 이 티켓에서 직접 컨트롤러 `@RequiresPermission` 을 순회) 부족한 **읽기** 권한만 추가한다. 쓰기/삭제 권한은 추가하지 않는다(소유자 결정은 "읽기 권한"으로 한정).
- **무권한 테스트 계정 신설**: `viewer@demo.com`(또는 동등한 이름) — 최소/무권한 역할. 로그인은 가능하되 게이트된 화면에서 403을 보여준다. `R__seed_demo_operator.sql` 과 같은 패턴(admin_operators + admin_operator_roles + operator_tenant_assignment)으로 시딩한다.
- **론처 자격 정보 동기화**: `infra/demo/aws/site/index.html`(:315,319 부근 — 로그인 자격 정보 표시)과 가드 (z11 — `seed/lib.sh` `user_token()` 과 대조)가 새 계정 쌍을 추가해도 계속 일치하도록 함께 갱신한다.
- **재굽기 여부 기록**: 데모 시드는 AMI 클론 위에서 돈다 — 이 변경이 `R__` repeatable 마이그레이션(다음 부팅 시 자동 반영)으로 충분한지, AMI 재굽기가 필요한지 판단해 기록한다(추정: compose/시드만의 변경이면 재굽기 불필요 — `TASK-MONO-672`/`727` 의 선례와 같은 패턴이지만, **직접 확인**하고 추정으로 남기지 않는다).

## Out of Scope

- 운영 데이터(실 고객/거래 데이터) 접근 — 계정 둘 다 데모/시드 데이터에만 닿는다.
- 쓰기·삭제 권한 확장 — 소유자 결정은 읽기 권한만.
- `TASK-PC-FE-298`(권한 매핑 표)의 콘텐츠 작성 — 이 티켓은 그 표가 있으면 참고하고, 없으면 직접 컨트롤러를 순회해 판단한다. 두 티켓은 서로 독립적으로 진행 가능하다.

# Acceptance Criteria

- [ ] **AC-0** — `partnership.manage`가 SUPER_ADMIN에서 빠진 이유를 rbac.md + 관련 ADR에서 재확인하고 판정(구조적 vs 보안 결정)을 구현 기록에 근거와 함께 남긴다. (b)로 판단되면 STOP하고 소유자 확인을 받은 뒤에만 진행한다.
- [ ] **AC-1** — 콘솔의 모든 nav 라우트를 나열하고, `demo-operator` 토큰으로 각각 GET(읽기 경로)을 호출해 200을 확인한다(라우트 목록 + 결과를 구현 기록에 남긴다).
- [ ] **AC-2** — `viewer@demo.com`(또는 동등 계정)이 게이트된 화면 중 최소 1개에서 실제로 403을 받는다(재현 스텝).
- [ ] **AC-3** — 역할 카탈로그가 바뀌었다면 `rbac.md` 가 코드보다 먼저(또는 같은 커밋에서) 갱신됐다.
- [ ] **AC-4** — 론처 자격 정보 표시와 (z11) 가드가 새 계정과 일치한다(가드 rc=0).
- [ ] **AC-5** — admin-service 유닛/통합 테스트로 시드/권한 카탈로그 변경을 커버한다(신규 또는 갱신).
- [ ] **AC-6** — 재굽기 필요 여부가 추정이 아니라 실제로 확인된 근거와 함께 기록된다.

# Related Specs

- `projects/iam-platform/apps/admin-service/src/main/resources/db/migration-dev/R__seed_demo_operator.sql`
- `projects/iam-platform/specs/services/admin-service/rbac.md`(§ Default Roles, § Seed Matrix — :62-119 부근)
- `infra/demo/aws/site/index.html`(:315,319 — 로그인 자격 정보 안내)
- `infra/demo/verify-demo-wrapper.sh`(z11 — 자격 정보 대조 가드)
- `infra/demo/seed/lib.sh`(`user_token()`)
- `projects/iam-platform/tasks/done/TASK-BE-221-security-issue-autolock-usecase.md`(참고 — 이 저장소의 계정 잠금 관련 선례)
- 2026-09-24 UTC 포트폴리오 UX 전수조사(소유자 승인)

# Related Skills

- `.claude/skills/backend/` — INDEX 참조.

---

# Related Contracts

- `projects/iam-platform/specs/services/admin-service/rbac.md` — 역할 카탈로그가 소스 오브 트루스, 코드가 이를 반영.

---

# Target Service

- `admin-service`

---

# Architecture

- `projects/iam-platform/specs/services/admin-service/architecture.md`

---

# Implementation Notes

- **AC-0을 건너뛰고 바로 SUPER_ADMIN에 `partnership.manage`를 추가하지 않는다** — 그것이 이 티켓의 핵심 위험이다. 의도적으로 빠진 권한을 "데모 편의"로 되돌리면 `rbac.md`가 문서화한 권한 경계가 조용히 깨질 수 있다.
- 권한 카탈로그/역할 매트릭스를 바꾸는 편집은 이 호스트의 auto-mode 분류기가 `.claude/` 밖에서도 게이트할 수 있는 축이다(`CLAUDE.md` § Classifier gating) — 한 번 시도하고, 실제로 막히면 정확한 patch를 소유자에게 전달한다(우회 금지).

---

# Edge Cases

- 새 무권한 계정이 콘솔 로그인 자체(OIDC 토큰 교환)는 통과해야 한다(전면 차단이 아니라 "메뉴 접근"만 403) — `status='ACTIVE'` 를 보장한다(`TokenExchangeService`가 이 조건만 확인).
- `demo-requester`(기존 두 번째 계정, ERP 결재용)의 권한은 이 티켓의 대상이 아니다 — 건드리지 않는다.
- 데모 볼륨이 stop/start 를 건너 사는 경우(신선 볼륨이 아닌 경우) 시드 변경이 기존 인스턴스에 반영되려면 `git pull` + 컨테이너 재생성이 필요할 수 있다 — AC-6이 이를 판단한다.

# Failure Scenarios

- `partnership.manage`를 SUPER_ADMIN에 직접 추가해 의도된 권한 경계가 깨진다(AC-0 미준수).
- 읽기 권한을 보강하다가 실수로 쓰기/삭제 권한까지 함께 부여한다.
- 론처 자격 정보 표시를 안 바꿔서 방문자가 새 계정으로 로그인할 방법을 모른다.
- (z11) 가드를 안 돌려서 자격 정보 불일치가 조용히 머지된다.
