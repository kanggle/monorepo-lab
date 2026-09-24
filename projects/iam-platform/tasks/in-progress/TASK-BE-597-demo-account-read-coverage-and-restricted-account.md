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

---

# Implementation Record

## AC-0 판정 (2026-09-24 UTC) — 🔴 STOP, 소유자 확인 대기. 권한·시드 변경 0건.

**판정: (b) 쪽 — 문서화된 의도적 설계 결정이다. 그리고 (a) 로 읽더라도 이 티켓의 «읽기 권한만» 제약과 충돌한다.** 코드·시드·rbac.md 는 한 줄도 바꾸지 않았다.

### 1. 티켓 전제 정정 — 「명시적 문장이 없다」는 틀렸다

In Scope 첫 항목은 "`partnership.manage` 자체에는 이 파일에 같은 형태의 명시적 문장이 없다"고 적었지만, `rbac.md` 에 **두 곳**이 있다(표 행 :112 만 보고 표 아래 주석을 한 개만 읽은 것으로 보인다):

- `rbac.md:72` (§ Permission Keys) — "SUPER_ADMIN 은 미보유 — 파트너십은 **두 실제 고객 테넌트** 사이 관계이며 플랫폼은 당사자가 아니다(D2-C broker gate 는 deferred, `tenant.admin.delegate` 가 SUPER_ADMIN 에 ❌ 인 것과 동형)".
- `rbac.md:120` (§ Seed Matrix 주석, ADR-MONO-045 step 1→2) — "`partnership.manage` 가 SUPER_ADMIN 에 ❌ 인 것은 **의도적** — 파트너십은 두 실제 고객 테넌트 사이 관계이며 플랫폼은 당사자가 아니다(D2-C SUPER_ADMIN broker gate 는 deferred …)".
- 출처: 두 문장 모두 스펙 탄생 커밋 `cbdf91e3a` (#2222, 2026-07-04, `docs(iam): cross-org partnership specs/contracts (ADR-MONO-045 step 1, BE-476)`) 에서 들어왔다 — `git log -S 'partnership.manage' -- rbac.md` 결과가 이 커밋 하나뿐. 나중에 붙은 사후 합리화가 아니다.

### 2. ADR 근거 — `docs/adr/ADR-MONO-045-cross-org-partner-delegation.md`

- **D2-C** (:68) — "Platform `SUPER_ADMIN` brokers each partnership … **Rejected as default** — re-introduces the operator-in-the-loop ADR-044 removed." 선택된 D2-A 는 host `TENANT_ADMIN` invite + partner `TENANT_ADMIN` accept 의 **양측 동의**다(:66).
- **D3-A** (:74) — "`SUPER_ADMIN` stays net-zero; … the partner is a *scoped guest*, never a co-admin."
- D8 (:109) / :136 / :144 — SUPER_ADMIN broker gate 는 **별도 후속 결정**으로 연기.

즉 이것은 「아직 아무도 안 넣었다」가 아니라 **ADR 이 선택지로 올려 놓고 기본값으로 기각한** 거버넌스 경계(양측 동의 · 플랫폼 비당사자)다. `tenant.admin.delegate` 와 「동형」이라는 문구가 있지만, 그 키는 SUPER_ADMIN 이 `operator.manage`(`'*'`)로 **이미 우회**하는 구조적 중복인 반면, `partnership.manage` 는 SUPER_ADMIN 에게 **어떤 우회 경로도 없다** — `/api/admin/partnerships/**` 는 이 키 하나로만 열린다. 따라서 (a) 「플랫폼 스코프가 이미 우회한다」 구조는 성립하지 않는다.

### 3. (a) 로 읽어도 이 티켓 범위로는 못 푼다 — 읽기 전용 키가 없다

`PartnershipAdminController.java` 는 GET 목록(:120)과 모든 변이(invite :51 · accept :70 · suspend :82 · reactivate :94 · terminate :106 · participant 배정 :141 / 해제 :159)를 **같은 `Permission.PARTNERSHIP_MANAGE` 하나**로 게이트한다. 그래서 `/partnerships` 를 200 으로 만들 수 있는 어떤 부여 방식도 — SUPER_ADMIN 정의 변경이든, 데모 전용 역할이든, `TENANT_ADMIN @ demo-corp` 추가 바인딩이든 — **invite/accept/terminate 쓰기까지 함께 연다.** 소유자 결정(2026-09-24)과 이 티켓 Out of Scope 는 "쓰기·삭제 권한 확장 금지 — 읽기 권한만"이다. `TENANT_ADMIN @ demo-corp` 는 추가로 `tenant.admin.delegate`(in-tenant 재위임)도 딸려 온다.

### 4. 보강 대상의 실제 크기 — 한 화면뿐

콘솔 매핑 표 `projects/platform-console/apps/console-web/src/shared/guide/permission-map.ts`(#3999, rbac.md Seed Matrix × 데모 시드로 계산)에서 `demo-operator`(SUPER_ADMIN) 가 `no` 인 nav 행은 **`/partnerships` 하나**다(:380-392, 그 행의 `mismatch` 가 이미 이 설계를 설명). 나머지 IAM 행의 키(`operator.manage` · `group.manage` · `org.manage` · `tenant.manage` · `audit.read` · `account.read` · `subscription.manage`)는 전부 SUPER_ADMIN ✅, 도메인 행은 demo-corp 의 5개 도메인 구독으로 열린다. 즉 「부족한 읽기 권한 전수 보강」은 실제로는 **partnership 한 키의 문제**이고, 그 한 키가 위 1–3 의 경계에 걸린다.

### 5. 소유자에게 묻는 선택지 (구현자 추천 ≠ 소유자 선택)

- **①  현상 유지 + 문서화** — `/partnerships` 403 을 «설계상 SUPER_ADMIN 은 파트너십 당사자가 아니다»로 두고, 그 화면을 오히려 권한 경계 시연 지점으로 쓴다. 매핑 표 `mismatch` 가 이미 그렇게 말한다. 변경 0.
- **②  읽기 전용 키 신설 `partnership.read`** — GET `/api/admin/partnerships` 만 새 키로 분리하고 SUPER_ADMIN(또는 데모 전용 역할)에 부여. **새 권한 키 = rbac.md § Permission Keys 카탈로그 변경 + 컨트롤러 애노테이션 변경 + 새 Flyway 마이그레이션**(rbac.md:58 "다른 키는 본 문서 업데이트 없이 도입 금지", :130). 그리고 ADR-MONO-045 D3 의 "SUPER_ADMIN stays net-zero" 를 **읽기 한정으로 완화**하는 것이라 ADR 보정(amendment) 판단이 필요하다. 이 티켓의 «기존 읽기 권한 보강» 범위를 넘는다 → 별도 티켓 권장.
- **③  `TENANT_ADMIN @ demo-corp` 를 demo-operator 에 추가 바인딩** — SUPER_ADMIN 정의 불변, 스코프는 demo-corp 로 confine. 그러나 쓰기(invite/accept/terminate) + `tenant.admin.delegate` 가 함께 열려 소유자의 «읽기만» 결정과 충돌. 소유자가 «데모 테넌트 한정이면 쓰기 허용» 으로 결정을 넓힐 때만 가능.
- **무권한 계정(`viewer@demo.com`) 부분은 AC-0 과 독립이다** — 파트너십 판정과 무관하게 진행 가능하지만, 새 operator 시드도 «권한 시드 변경»이라 이번 디스패치 지시(AC-0 STOP 시 권한 변경 금지)에 따라 손대지 않았다. 소유자가 이 부분만 먼저 진행하라고 하면 그대로 착수할 수 있다(패턴: `R__seed_demo_operator.sql` §5 와 같은 admin_operators + admin_operator_roles(역할 없음 또는 `SUPPORT_READONLY`) + operator_tenant_assignment, 그리고 auth-service 쪽 `iam`-테넌트 credential 시드 + 론처 `index.html` + z11 가드 + `permission-map.ts` 동기화).

### AC 상태

- AC-0 — ✅ 판정 기록(위). 결론 = STOP, 소유자 확인 대기.
- AC-1 ~ AC-6 — ⚪ 미착수(AC-0 STOP 게이트). 권한·시드·rbac.md·론처·매핑 표 변경 0건.
