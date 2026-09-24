# Task ID

TASK-BE-597

# Title

데모 계정에 부족한 읽기 권한 보강 + 무권한 테스트 계정 신설 (AC-0 = SUPER_ADMIN partnership.manage 제외 사유부터 확인)

# Status

review

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

---

## 소유자 결정 (2026-09-24 UTC, 부모 세션 경유) — 범위·AC 개정

1. **`/partnerships` 는 demo-operator 에게 403 으로 둔다 — 선택지 ① 현행 유지.** SUPER_ADMIN 정의 · `rbac.md` 의 partnership 의미 · ADR-MONO-045 는 바꾸지 않는다.
   - 🔴 **이 티켓의 전제가 틀렸다는 것을 기록한다**: In Scope 첫 항목의 "`partnership.manage` 자체에는 … 명시적 문장이 없다"는 사실이 아니다. 근거 = `rbac.md:72`(§ Permission Keys) · `rbac.md:120`(§ Seed Matrix 주석, "의도적") · ADR-MONO-045 **D2-C**(:68, SUPER_ADMIN 중개 기각) · **D3-A**(:74, "SUPER_ADMIN stays net-zero"). 스펙 탄생 커밋 `cbdf91e3a`(#2222) 부터 있었다.
   - 소유자는 이 경계를 **시연 가능한 403** 으로 남기기로 했다.
2. **무권한 계정 진행.** 로그인·셸 진입은 되고 게이트된 화면에서 권한 부족 403 을 받는 최소 역할을 고르고 그 선택을 기록한다.

### 개정된 AC (원 AC 를 대체한다 — 원문은 위에 그대로 둔다)

- **AC-1′** — 콘솔의 모든 nav 라우트에서 demo-operator 토큰의 GET 이 200 이다. **단 `/partnerships` 는 제외 — ADR-MONO-045 에 따라 403 이 정답이다.**
- **AC-3′** — 역할 카탈로그를 바꾸지 않는다(소유자 ①). 따라서 `rbac.md` 변경 없음이 정답이다.
- AC-2 · AC-4 · AC-5 · AC-6 — 원문 그대로.

---

## Implementation Record (2026-09-24 UTC)

### 무권한 계정 설계 — 세 가지 선택과 그 근거

| 선택 | 값 | 근거 (실측·코드) |
|---|---|---|
| 역할 | **없음** (`admin_operator_roles` 0행) | 로그인에 역할이 필요 없다: `TokenExchangeService.java:81-102` 는 oidc_subject 매칭 + `status='ACTIVE'` 만 본다. 콘솔 셸의 레지스트리(`ConsoleRegistryController.java:26-31,42`)에도 `@RequiresPermission` 이 없다. `SUPPORT_READONLY` 는 기각 — `account.read`·`audit.read`·`security.event.read` 로 `/accounts`(소비자 계정 데이터)·`/audit`(감사 추적)를 **연다**. 역할이 없으면 권한 합집합이 공집합이라 모든 `@RequiresPermission` 엔드포인트가 403 `PERMISSION_DENIED`(rbac.md § Permission Evaluation Algorithm step 4) — 더 많은 화면에서 403 을 보이면서 데이터는 0. |
| 홈 테넌트 | **`demo-viewer`** (account-service 에 **미등록**) | 홈 테넌트는 assume 가능하다: `OperatorAssignmentCheckUseCase.java:127-136`(effective scope = assignment ∪ home). 홈을 `demo-corp` 로 두면 assume 시 5개 도메인 OPERATOR 롤(쓰기 포함)이 파생된다 — 최소가 아니다. 미등록 슬러그는 `ConsoleRegistryUseCase.java:83-85,158-198` 의 ACTIVE 테넌트 교집합에서 빠져 모든 제품의 `tenants` 가 `[]` → 스위처 미렌더(`TenantSwitcher.tsx:58`) → 도메인 섹션은 «테넌트를 먼저 선택하세요»(`DomainTenantGate.tsx:163-186`). |
| 테넌트 배정 | **없음** (`operator_tenant_assignment` 0행) | 위와 같은 이유. |

- **403 을 받는 화면(코드로 판정)**: `/iam`(카드 3종 전부), `/operators`, `/operator-groups`, `/org-hierarchy`, `/tenants`, `/permissions`, `/permission-sets`, `/audit`, `/accounts`, `/subscriptions`, `/partnerships` — `permission-map.ts` 의 `admin`/`admin-per-card` 행 전부. 셸·`/console`·`/dashboards/overview`·가이드는 열린다(`public`/`operator` 게이트).
- 🔴 **알려진 한계(수정 안 함, 기록)**: 공개 데모에서 이 계정의 제한은 변조 불가능하지 않다. ① `demo@demo.com`(SUPER_ADMIN, 공개 자격증명)이 이 운영자에게 아무 역할이나 부여할 수 있다. ② 셀프 온보딩 DTO(`OnboardOrganizationRequest.java:23`)는 패턴만 검사하고 예약어 목록은 검사하지 않으므로, 방문자가 슬러그 `demo-viewer` 로 조직을 만들면 그 TENANT_ADMIN 이 된다. R__ 재적용은 둘 다 되돌리지 못한다(체크섬이 바뀔 때만 재실행, INSERT 는 삭제하지 않음). ①이 이미 모든 데모 행에 존재하는 경로라 ②를 막아도 얻는 것이 작다고 판단 — 막으려면 account-service 에 `demo-viewer` 를 `SUSPENDED` 로 등록하는 시드 한 줄이면 된다(슬러그 선점 + 레지스트리 제외 유지).

### 변경 파일

- `projects/iam-platform/apps/admin-service/src/main/resources/db/migration-dev/R__seed_demo_viewer_operator.sql` (신규) — `admin_operators` 1행만. **별도 파일인 이유**: `DemoSecondOperatorSeedTest` 가 `R__seed_demo_operator.sql` 의 운영자를 **정확히 2명(demo-corp)** 으로 단언한다(ERP 직무분리 쌍, TASK-MONO-519) — 그 명제는 여전히 참이어야 한다.
- `projects/iam-platform/apps/auth-service/src/main/resources/db/migration-dev/R__seed_demo_viewer_operator_credential.sql` (신규) — `iam` 테넌트 자격증명 1행(`viewer@demo.com` / `Demo1234!` / account_id `…ad05`).
- `projects/iam-platform/apps/auth-service/src/test/java/com/example/auth/demoseed/DemoViewerOperatorSeedTest.java` (신규, 4케이스) — `iam` 단일행·이메일/계정ID 비충돌(대조군 = 형제 4행을 읽는지) · 해시 검증 · 링크 키 일치 · 역할/배정 없음 + 홈≠demo-corp(대조군 = 주석 제거 전 원문엔 두 테이블명이 **있다**).
- `projects/iam-platform/apps/auth-service/build.gradle` — admin-service 뷰어 시드를 `test` 입력으로 선언(형제와 같은 UP-TO-DATE 맹점).
- `projects/iam-platform/apps/admin-service/src/test/java/com/example/admin/integration/DemoOperatorSeedIntegrationTest.java` — 5케이스 추가: 뷰어 행 · 역할/배정 0 · `GET /api/admin/me` 200 + `roles=[]` · `GET /api/admin/{operators,audit,roles,tenants}` 403 `PERMISSION_DENIED` · **demo-operator 의 `GET /api/admin/partnerships` 403**(소유자 ① 을 핀으로).
- **안 바꾼 것**: `rbac.md`(카탈로그 불변, AC-3′), SUPER_ADMIN 시드, `R__seed_demo_operator.sql`, 론처 `index.html`, z11, `DemoLoginCredentials.tsx`, `permission-map.ts`(아래).

### `permission-map.ts` 를 안 바꾼 이유

그 표의 «테스트 계정 접근» 열은 `DEMO_TEST_ACCOUNT`(= demo-operator) 하나로 계산되고, demo-operator 의 역할·배정은 이번에 바뀌지 않았다. `/partnerships` 행의 `mismatch`(:390-391) 는 이미 «SUPER_ADMIN 도 이 키가 없다 … 데모 계정은 이 화면에서 403» 이라고 말해 소유자 ① 과 일치한다. 뷰어를 두 번째 열로 넣는 것은 화면 기능 추가라 이 티켓 범위 밖. → 콘솔 파일 무변경, 드리프트 테스트 실행 불요.

### AC-4 — 론처·z11 에 뷰어를 **아직** 넣지 않았다 (의도적 보류)

- 측정 근거: `infra/demo/aws/README.md:93-97` — 론처 `index.html` 은 **머지 = 배포**(Vercel, 분 단위)이고, 앱 소스(마이그레이션 SQL 포함)는 **AMI 에 구워져 있어 재굽기 전까지 데모에 없다.** 지금 론처에 `viewer@demo.com` 을 적으면, 재굽기 전까지 방문자가 그 계정으로 로그인하면 실패한다(자격증명 행이 없음 → 로그인 실패, 또는 operator 행이 없음 → `operator_exchange_unavailable`). 같은 README § 「이 계약을 바꾸는 PR 이 함께 할 일」(:132-158)이 말하는 두 속도 배포 문제 그 자체다.
- z11 의 짝 구조: z11(`verify-demo-wrapper.sh:2159-2229`)은 **단일 계정**을 세 사본(론처 `id="c-email"` · `seed/lib.sh` `user_token()` 기본값 · `DemoLoginCredentials.tsx` `DEMO_LOGIN_EMAIL`)으로 대조하며, 권위는 «부팅마다 실제로 로그인에 쓰이는» `lib.sh` 다. 뷰어는 시드 스크립트가 쓰지 않으므로 그 살아 있는 출처가 없다 — 넣는다면 권위를 auth-service 자격증명 시드의 이메일 **컬럼 값**(주석이 아닌 실제 값)으로 잡는 새 대조가 필요하다.
- 기존 demo@demo.com 짝은 건드리지 않았다(z11 무변경).
- ⇒ **후속(부모가 기안)**: 재굽기 후 론처에 뷰어 행(`id="c-viewer-email"`) + z11 에 «론처 뷰어 이메일 ↔ `R__seed_demo_viewer_operator_credential.sql` 이메일 컬럼» 대조 + 대조군. 루트 `tasks/` 티켓(경로 = `infra/demo/**`). ID 는 동시 세션 충돌을 피하려고 여기서 잡지 않았다.

### AC-6 — 재굽기 판정: ✅ **필요** (측정)

- 두 시드는 `src/main/resources/db/migration-dev/` 아래 = 앱 소스. `infra/demo/aws/README.md:87-97`: packer 가 bake 때 `git clone --depth 1 --branch main`, `demo-boot.sh` 는 부팅 시 `git pull` 을 **하지 않는다** → «앱 소스(Java/TS) … AMI 에 구워져 있다 → ✅ 재굽기 필요».
- 현재 AMI: `infra/demo/aws/deployed-ami.env` → `REPO_COMMIT=f1da21800`(2026-09-24T04:29:55Z bake, provenance `ami-tag`) — 이 브랜치의 변경을 담을 수 없다.
- 재굽기 뒤 반영 경로: 두 파일 모두 **새 R__** 라 기존 EBS 볼륨에서도 다음 부팅의 Flyway 가 적용한다(repeatable 은 히스토리 행이 없으면 적용; 버전 순서 무관). admin-service 는 `db/migration-dev` 를 기본 프로필에서 로드(`R__seed_demo_operator.sql:1-3,17-21` — 같은 디렉터리의 demo-operator 가 데모에서 실제로 로그인된다), auth-service 는 `e2e` 프로필 오버레이로 로드(형제 `R__seed_demo_second_operator_credential.sql:1-7` 와 동일 경로).

### AC 상태

- AC-0 — ✅ (위 판정 + 소유자 결정 ①).
- AC-1′ — ⚪ **라이브 미측정** (유료 데모 미기동 지시 + 이 호스트 Docker 미기동). 코드 판정: demo-operator(SUPER_ADMIN)는 `permission-map.ts` 의 모든 `admin` 행 키를 보유(`/partnerships` 제외), 도메인 행은 demo-corp 5개 구독으로 열림 — 이번 변경은 demo-operator 를 건드리지 않으므로 기존 상태와 동일. `/partnerships` 403 은 IT 로 핀. **다음 데모 창 절차**: 재굽기된 AMI 로 기동 → 콘솔에 `demo@demo.com` 로그인 → 테넌트 `demo-corp` 선택 → nav 의 모든 항목을 차례로 열어 HTTP 상태를 브라우저 DevTools(또는 콘솔 BFF 로그)로 기록 — 기대값 = `/partnerships` 403, 나머지 200(이커머스 목록은 `ecommerce` 테넌트에서 비지 않음).
- AC-2 — 🟡 **IT 작성·컴파일 완료, 실행 ⚪**(Docker 없음): `DemoOperatorSeedIntegrationTest#viewerIsDeniedOnGatedReads` 가 뷰어 토큰으로 `GET /api/admin/{operators,audit,roles,tenants}` → 403 `PERMISSION_DENIED` 를 단언. 재현 스텝(라이브): 재굽기 후 콘솔에 `viewer@demo.com` / `Demo1234!` 로그인 → `/operators` 열기 → 권한 부족(403) 확인 · `/dashboards/overview` 는 열림.
- AC-3′ — ✅ 카탈로그 불변, `rbac.md` 무변경.
- AC-4 — ⚪ **의도적 보류**(위) — 론처·z11 무변경이므로 기존 z11 은 그대로 통과해야 한다(아래 명령).
- AC-5 — 🟡 유닛 ✅ `DemoViewerOperatorSeedTest` 4/4(+ bite: 링크 키 `…ad05→…ad06` 변조 시 1건 실패, 원복 후 통과 — admin 파일만 바꿨는데 재실행됨 = gradle 입력 배선도 확인), IT ⚪(Docker).
- AC-6 — ✅ 재굽기 필요(측정 근거 위).
