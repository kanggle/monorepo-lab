# Task ID

TASK-MONO-781

# Title

데모 CS 2선 운영자 `cs@demo.com` 시드 — 홈 `ecommerce` · `SUPPORT_LOCK`@`ecommerce` (사이트 운영자) · **도메인 역할 파생 트레이드오프 공개**(소유자 결정 A)

# Status

review — AC-0~3 닫힘(2026-10-09 UTC). ⏳ AC-4 = CI `Integration` 레인(이 호스트 Docker 없음) · AC-5 = 재굽기 뒤 라이브(`TASK-MONO-782` AC-1).

# Owner

monorepo

# Task Tags

- iam
- demo
- seed
- platform-console

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus 5.5 — 시드 자체는 형제 복사지만, «이 계정이 실제로 무엇을 받는가» 가 assume 토큰 파생(ADR-MONO-035)에 걸려 있어 AC-0 가 소유자 결정을 낳았다.

---

# Dependency Markers

- 출처: 2026-10-09 UTC 소유자 대화(호스트 KST 기준 2026-10-10 새벽 — 날짜 도장은 UTC).
- 관련: `TASK-PC-FE-326`(#4265 — SUPPORT_LOCK 의 이메일 검색 전용 «계정 운영», 마지막 AC 가 이 계정으로 재는 라이브 확인) · `TASK-BE-597`(`viewer@demo.com` 선례) · `TASK-MONO-751`(`platform@demo.com` · `confined_tenant_id`) · `TASK-BE-621`(사이트 운영자 잠금 = 사이트 멤버십).
- 후속: `TASK-MONO-782`(재굽기 뒤 론처 · (z11) · 콘솔 가이드 행 — ⏳ AC-0 게이트).

# Goal

`TASK-PC-FE-326` 의 라이브 확인(SUPPORT_LOCK 만 가진 운영자로 로그인 → 사이드바에 «계정 운영» → 검색 전용 안내 → `demo@demo.com` 검색 1행 → 잠금 · 해제 → 빈 검색이면 안내로 복귀)을 **지금 데모 데이터로는 UI 로 할 수 없다**:

- `demo@demo.com`(SUPER_ADMIN)의 역할 행은 `tenant_id = 'demo-corp'` 라 `ecommerce` 운영자를 만들거나 바꿀 수 없다(`TENANT_SCOPE_DENIED`, 오케스트레이터 라이브 실측).
- 데모 소비자는 `demo@demo.com`(소비자 풀) 하나이고 사이트 `ecommerce` · `fan-platform` 의 멤버다 — 콘솔 이메일 검색은 그 멤버인 **사이트 테넌트** 로만 풀 계정을 찾는다(`multi-tenancy.md` § 5).

⇒ CS 2선 전용 데모 운영자 하나를 시드한다: `cs@demo.com`, 홈 `ecommerce`, `SUPPORT_LOCK` 을 `tenant_id = 'ecommerce'` 로(사이트 잠금 — `'*'` 이면 계정 전체 잠금).

# Scope

## In

- admin-service `db/migration-dev/R__seed_demo_cs_operator.sql`(신규) — 운영자 행 + `SUPPORT_LOCK`@`ecommerce` + `confined_tenant_id = 'ecommerce'`.
- auth-service `db/migration-dev/R__seed_demo_cs_operator_credential.sql`(신규) — `iam` 테넌트 자격증명(같은 공개 데모 비밀번호).
- `projects/iam-platform/specs/features/multi-tenancy.md` — `confined_tenant_id` 절에 이 계정과 **공개된 트레이드오프** 한 단락(스펙 먼저).
- 테스트: auth-service `DemoCsOperatorSeedTest`(링크 키 · 해시 · 홈/권한/한정) · `DemoCsOperatorDerivedRolesTest`(트레이드오프 핀) · admin-service `DemoOperatorSeedIntegrationTest` 5칸 · auth-service `build.gradle` 의 `test` 입력 선언.
- 후속 `TASK-MONO-782` 기안(root `tasks/ready/`).

## Out

- 론처(`infra/demo/aws/site/index.html`) 계정 행 · (z11) 대조 · 콘솔 전역 가이드 «테스트 계정» 행 · `DemoLoginCredentials` — **재굽기 전에 공개하면 로그인이 실패하는 계정을 안내한다** → `TASK-MONO-782`(아래 § 공개 시점 판단).
- ADR-MONO-035 파생 규칙 변경(갈래 B) · 가짜 사이트 테넌트(갈래 C) — 기각.
- AMI 재굽기 자체(소유자 실행).

# Acceptance Criteria

- [x] **AC-0 — 측정 먼저, 결정 하나는 소유자에게 (2026-10-09 UTC)**
  1. **홈 `ecommerce` 의 부작용 — 🔴 피할 수 없는 도메인 쓰기 → 소유자 결정 A.** 근거 사슬:
     - 콘솔은 고른 테넌트를 **항상 assume** 한다: `console-web/src/shared/lib/active-tenant-default.ts:32-34,160-169`; 고를 수 있는 테넌트가 하나면 로그인 때 자동 선택(`:53-54`).
     - 고를 수 있는 테넌트 = 실효 범위(배정 ∪ 홈): `ConsoleRegistryUseCase.java:81-82,199-207` → 홈 `ecommerce` 하나 → 로그인 때 자동 assume.
     - assume 게이트가 홈을 들인다: `OperatorAssignmentCheckUseCase.java:184-192`.
     - 토큰 `roles` 는 RBAC 행이 아니라 **선택 테넌트의 ACTIVE 구독**에서 파생: `TenantClaimTokenCustomizer.java:863-893` → `OperatorRoleDerivation.fromEntitledDomains`(`:877` «never admin_operator_roles»).
     - `ecommerce` 의 구독 = `ecommerce`(account-service `V0022:13-16`) + `wms`(`V0025:20-23`) ⇒ `ECOMMERCE_OPERATOR` + `WMS_OPERATOR, OUTBOUND_READ/WRITE, INBOUND_READ/WRITE, INVENTORY_READ/WRITE, MASTER_READ`(`OperatorRoleDerivation.java:53-58,102-103`).
     - 좁히는 장치 없음: `operator_tenant_assignment.permission_set_id` 는 assume 경로 어디서도 읽히지 않는다(auth-service 0건 · `OperatorAssignmentCheckUseCase` 미사용) · `confined_tenant_id` 는 테넌트만 좁힌다(`:153-163`) · 역할 상한은 파트너십 `delegatedScope` 뿐(`:195-204`)인데 레지스트리가 파트너십 호스트를 나열하지 않아 선택 불가. 홈 대신 배정을 써도 같다(실효 범위 = 배정 ∪ 홈). 테넌트 없이는 «계정 운영» 자체가 안 열린다(`app/(console)/accounts/page.tsx:37-56`).
     - **소유자 결정 (2026-10-09 UTC) = A** «브리프대로 시드하고 트레이드오프를 공개한다». 기각: **B** = ADR-MONO-035 파생 규칙 변경(ADR 급, 범위 밖) · **C** = 구독 0 인 가짜 사이트 테넌트(잠금 시연이 실제 사이트가 아니게 됨).
  2. **2단계 인증 불필요 ✅.** `SUPPORT_LOCK` `require_2fa = FALSE`(`V0006__seed_admin_rbac.sql:7` · `V0009:9`); V0013 은 SUPER_ADMIN · SECURITY_ANALYST 만 TRUE(`V0013__operator_totp_and_require_2fa.sql:76-78`). `tenant_entry_policy`(V0047)는 어느 시드도 행을 넣지 않는다(V0047 DDL · V0048:7 · `migration-demo/R__demo_relax_super_admin_require_2fa.sql:28` 주석뿐) ⇒ `mfaRequired=false`(`OperatorAssignmentCheckUseCase.java:112`). 단, 공개 SUPER_ADMIN 으로 방문자가 정책을 켤 수는 있다(Edge Cases).
  3. **콘솔 흐름 (코드 판정) ✅.** 스위처는 `ecommerce` 하나를 보이고 자동 assume(위 1). `/accounts` 는 `getAccountsAccessTier()` 를 먼저 보고 `search-only` 면 검색 전용 화면(`page.tsx:35-64`); 사이드바 «계정 운영» 은 `admin-per-card`[`account.read`·`account.lock`·…] OR 게이트(`shared/guide/permission-map.ts:373-386`). 서버: 이메일 검색은 `account.read` 불필요 · 테넌트 스코프만(`AccountAdminController.java:88-95`), 무필터 목록은 403(`:105-111`), 잠금은 사이트 운영자 → 활성 테넌트로 내려가 풀 멤버의 그 사이트 멤버십만(`:174-176`, `multi-tenancy.md` § 5 표 `:527`). 🔵 `SUPPORT_LOCK` 은 `audit.read` 도 가진다(`V0006:36`) → «감사 · 보안» 화면도 보인다(`permission-map.ts:296`).
  4. **재굽기 필요 ✅(측정).** 시드 SQL 은 앱 소스 → AMI 에 구워진다: `infra/demo/aws/README.md:90-94`(bake 때 `git clone`, `demo-boot.sh` 는 `git pull` 안 함). 배포 AMI = 25차 `REPO_COMMIT=e2a0c7eb5`(`infra/demo/aws/deployed-ami.env:37-41`) — 이 브랜치를 담을 수 없다. 두 파일 모두 **새 R__** 라 재굽기 뒤 기존 볼륨에서도 다음 부팅 Flyway 가 적용한다(BE-597 AC-6 와 같은 판정).
- [x] **AC-1** — 이메일 · account_id 충돌 없음: `cs@demo.com` · `0199de70-0000-7000-8000-00000000ad08` · `demo-cs` 를 작업 트리 전체(Grep) + `git log --all -S` 로 확인 — 0건(ad01·ad02 도 미사용이지만 가장 최근 `ad07` 다음 번호를 택했다).
- [x] **AC-2** — 시드 두 파일 + 스펙 단락(위 Scope In).
- [x] **AC-3** — auth-service `DemoCsOperatorSeedTest` 5칸 + `DemoCsOperatorDerivedRolesTest` 3칸 green(아래 § 검증), bite 2종 red → 원복 → green.
- [ ] **AC-4** — admin-service `DemoOperatorSeedIntegrationTest` 신규 5칸 — 🟡 **작성 · 컴파일 rc=0, 실행 ⚪(이 호스트 Docker 없음)** → CI `Integration` 레인이 판정.
- [ ] **AC-5** — 재굽기 뒤 라이브: `TASK-PC-FE-326` 마지막 AC 를 `cs@demo.com` 으로 — ⏳ `TASK-MONO-782` AC-1 이 함께 잰다.

# Related Specs

- `projects/iam-platform/specs/features/multi-tenancy.md` § Platform Console(`confined_tenant_id` 절) · § 5 사이트 테넌트로 계정을 찾는 표면
- `projects/iam-platform/specs/services/admin-service/rbac.md` § 역할 표(`SUPPORT_LOCK`)
- `infra/demo/aws/README.md` § 배포 층

# Related Contracts

- 없음(계약 변경 없음). 참고: `projects/iam-platform/specs/contracts/http/admin-api.md:107`(이메일 검색은 `account.read` 불필요).

# Edge Cases

| 상황 | 기대 |
|---|---|
| 누가 `ecommerce` 에 구독을 하나 더 시드 | `DemoCsOperatorDerivedRolesTest` RED — 공개 문구(시드 머리 · 스펙 · 가이드 행)를 같은 변경에서 고친다 |
| 공개 데모에서 방문자가 런타임에 구독 · 진입 정책 · 역할을 바꿈 | 알려진 한계(BE-597 과 같음) — 시드 재적용은 지우지 않는다. 테스트는 시드 상태만 핀 |
| 방문자가 `demo@demo.com` 으로 `demo-cs` 에 `demo-corp` 배정 추가 | `confined_tenant_id = 'ecommerce'` 가 assume 과 레지스트리를 계속 `ecommerce` 하나로 묶는다 |
| `cs@demo.com` 으로 «GDPR 삭제»(=`account.lock`) | 사이트 운영자 → `demo@demo.com` 의 `ecommerce` 멤버십만 `LEFT`(`multi-tenancy.md` § 5 `:546`). `demo@demo.com`(demo-corp 홈, `ecommerce` 배정)도 이미 같은 일을 할 수 있다 — 새 노출 아님 |

# Failure Scenarios

1. `oidc_subject` ≠ 자격증명 `account_id` → 콘솔 401(`operator_exchange_unavailable`) — `DemoCsOperatorSeedTest#csLinkKeyMatches`.
2. `SUPPORT_LOCK` 을 `'*'` 로 부여 → 잠금이 모든 사이트에 걸리는 계정 전체 잠금 — 시드 테스트 + IT 가 둘 다 잡는다.
3. 재굽기 전에 론처 · 가이드에 계정 공개 → 방문자 로그인 실패 — 이 티켓은 공개하지 않는다(`TASK-MONO-782`).

# 공개 시점 판단 (론처 · 콘솔 가이드)

- 론처는 머지 즉시 Vercel 로 나간다 — BE-597 → `TASK-MONO-730` 선례대로 **재굽기 뒤** 후속에서 공개한다.
- 콘솔 전역 가이드(«권한 및 테스트 계정» 탭)와 `/login` 의 `DemoLoginCredentials` 도 **같은 Vercel 배포**다 — 가이드는 로그인 뒤 화면이지만 공개 데모 방문자 누구나 본다. 같은 논리로 미룬다. 반례 `TASK-MONO-751`(#4130)은 시드와 론처를 한 PR 에 실었지만 그 PR 은 19차 굽기 직전이었고, 이번 브리프는 BE-597 선례를 명시했다.
- ⇒ 공개 문구(소유자 지시 그대로)는 `TASK-MONO-782` 의 AC 에 고정해 두었다: «CS 2선(계정 잠금·해제) — 데모에서는 ecommerce 테넌트를 고르면 이커머스·WMS 운영 권한도 함께 받습니다» + 감사 화면도 보인다는 한 줄. 이 PR 에서 공개 문구가 실린 곳 = 시드 머리 주석 · `multi-tenancy.md` 단락 · 테스트 설명.

---

# 구현 기록 (2026-10-09 UTC)

## 변경 파일

- `projects/iam-platform/specs/features/multi-tenancy.md` — `confined_tenant_id` 절에 `cs@demo.com` 한정 + 공개 트레이드오프 단락.
- `projects/iam-platform/apps/admin-service/src/main/resources/db/migration-dev/R__seed_demo_cs_operator.sql` — 신규. `demo-cs` · 홈 `ecommerce` · `confined_tenant_id='ecommerce'` · `SUPPORT_LOCK`@`ecommerce` · 배정 행 없음. 머리 주석에 선택 4개 + 트레이드오프 + 기각 갈래.
- `projects/iam-platform/apps/auth-service/src/main/resources/db/migration-dev/R__seed_demo_cs_operator_credential.sql` — 신규. `iam` · `…ad08` · `cs@demo.com` · 형제와 같은 Argon2id.
- `projects/iam-platform/apps/auth-service/src/test/java/com/example/auth/demoseed/DemoCsOperatorSeedTest.java` — 신규 5칸.
- `projects/iam-platform/apps/auth-service/src/test/java/com/example/auth/infrastructure/oauth2/DemoCsOperatorDerivedRolesTest.java` — 신규 3칸. **트레이드오프 핀**: 입력 쪽은 하드코딩 없이 (1) admin 시드에서 홈 · 한정 테넌트를, (2) account-service `db/migration` + `db/migration-dev` **전 파일**에서 `tenant_domain_subscription` 쓰기를 파싱(모르는 모양이면 건너뛰지 않고 RED), (3) 운영 코드 `OperatorRoleDerivation.fromEntitledDomains` 로 역할을 재계산해 공개 문구가 말하는 9개 역할과 정확히 비교. 대조군 3개(VALUES=acme-corp · SELECT==fan-platform · SELECT IN=wms)로 각 파서 모양이 실제 행을 잡는지 확인. `OperatorRoleDerivation` 이 package-private 라 그 패키지에 둔다.
- `projects/iam-platform/apps/auth-service/build.gradle` — `test` 입력: admin `R__seed_demo_cs_operator.sql` + account-service 마이그레이션 두 디렉터리(형제 파일만 바뀌어도 재실행 — bite 2 가 실제로 재실행됨을 확인).
- `projects/iam-platform/apps/admin-service/src/test/java/com/example/admin/integration/DemoOperatorSeedIntegrationTest.java` — 5칸: 행(홈 · 한정 · 링크 키) · 역할 정확히 `SUPPORT_LOCK|ecommerce` + 배정 0 · `/api/admin/me` roles=[SUPPORT_LOCK] · 무필터 `/api/admin/accounts` 403 `PERMISSION_DENIED` · assume 게이트(`ecommerce` 허용 · `mfaRequired=false` · `delegatedScope=null` · `demo-corp`/`fan-platform` 거절).
- `tasks/ready/TASK-MONO-782-show-the-cs-account-on-the-launcher-and-guide-after-the-rebake.md` — 후속 기안.

## 검증

- `./gradlew :projects:iam-platform:apps:auth-service:test --tests '*Demo*'` → **rc=0** — 7 스위트 35칸 실패 0(신규 `DemoCsOperatorSeedTest` 5 · `DemoCsOperatorDerivedRolesTest` 3, 기존 Demo* 27 회귀 없음).
- `./gradlew :projects:iam-platform:apps:admin-service:compileTestJava` → **rc=0**.
- `./gradlew :projects:iam-platform:apps:admin-service:test --tests '*DemoOperatorSeed*'` → rc=1 «No tests found» — 그 클래스는 `@Tag("integration")` 이라 `test` 태스크가 제외한다. `integrationTest` 는 Testcontainers 가 필요한데 이 호스트에 Docker 가 없다(`docker info` rc=1) ⇒ **IT 는 실행하지 않았고 green 이라 주장하지 않는다.** CI 가 판정.
- `bash scripts/check-dev-seed-migration-band.sh` → rc=0(새 파일은 R__ 라 버전 대역 무관).
- **bite**(Edit 로 변조 → 실행 → Edit 로 원복, `git checkout` 안 씀):
  1. admin 시드의 역할 행 테넌트 `'ecommerce'` → `'*'` 
  2. account-service `V0025` 의 `'wms'` → `'scm'`(=`ecommerce` 구독이 바뀐 상황)
  
  → `--tests '*DemoCs*'` **rc=1, 8칸 중 3 실패**: `csOperatorIsSiteScopedSupportLock`(1) · `csTenantSubscriptionsAreExactlyTheDisclosedOnes` · `derivedRolesAreExactlyTheDisclosedOnes`(2). 2 는 auth-service 밖 파일만 바꿨는데 재실행됐다 — `build.gradle` 입력 선언이 문다. 원복 후 `git status` 에 V0025 변경 없음 확인 → 위 rc=0 실행.
- 콘솔 파일 변경 없음 ⇒ pnpm 미실행(해당 없음). `infra/demo/**` 변경 없음 ⇒ (z11) 미실행(해당 없음).

## 재굽기

**필요**(AC-0 4). 재굽기 전에는 데모에 이 계정이 없다 — 그래서 공개(론처 · 가이드)는 `TASK-MONO-782` 의 AC-0(배포 AMI 의 `REPO_COMMIT` 이 이 PR 스쿼시의 자손인가) 뒤로 미뤘다.
