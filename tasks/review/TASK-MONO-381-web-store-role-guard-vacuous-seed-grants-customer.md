# Task ID

TASK-MONO-381

# Title

web-store 의 role 기반 로그인 가드가 **구조적으로 절대 발화하지 못한다** — `RoleSeedPolicy` 가 ecommerce 클라이언트로 로그인하는 *모든* principal 에게 `CUSTOMER` 를 무조건 부여하기 때문. 이게 결함인지 의도된 aud-default 인지는 **ADR-MONO-035 개정 결정**이다 (HARDSTOP-09)

# Status

review

> **▶️ UNPARKED + 구현 완료 (2026-07-13).** park 사유(*"tenant 로 seed 를 좁히려 했으나 소비자도 `ecommerce` 가 아니다"*)는 `TASK-BE-507` 이 해소했다. **그러나 재개해 실측하니 방향 (A) 의 전제가 무너졌다 — 이 가드는 *운영자 가드*가 될 수 없다.** 사용자가 **A′(cross-tenant 가드로 재정의)** 를 택했고(2026-07-13), 그대로 구현했다. 아래 § "재개 실측" 참조.

# Owner

monorepo

# Task Tags

- security
- iam
- architecture-decision

---

# ⚠️ 이 task 는 착수 전 아키텍처 결정이 필요하다 (HARDSTOP-09)

**발급(issuance) 모델을 건드리므로 코드부터 짜면 안 된다.** `ADR-MONO-035` (ACCEPTED) 가 정의한 operator-auth 모델을 개정하는 결정이고, `shared-library-policy.md` / `architecture-decision-rule.md` 기준 **ADR 게이트**다. 아래는 결정에 필요한 실측을 모아둔 것이지, 구현 지시가 아니다. **self-ACCEPT 금지** — 방향은 사람이 정한다.

---

# Dependency Markers

- **선행 (done)**: `TASK-MONO-373` — full-stack e2e 배선. `account-type-guard.spec.ts` 만 배선으로 못 살려 `test.fixme()` + 본 티켓 참조로 남겼다. **그 스펙을 살리려다 이 발급-측 결함을 실측했다.**
- **원 ADR**: `ADR-MONO-035` (ACCEPTED 2026-06-14) — operator-auth 통합 + operator 도메인-role 발급 모델. 특히 **§ 4b** (ADR-032 D5 step 4: `account_type` 제거).
- **원인 커밋**: `TASK-MONO-263` (`9177b63c2`, ADR-035 4b-2b) — `RoleSeedPolicy` 에서 `account_type` 판별자를 제거해 시그니처를 `seed(platformTenantId, accountType)` → `seed(platformTenantId)` 로 축소.

---

# Goal — 실측한 것 (2026-07-13)

## 결함의 정확한 형태

`account-type-guard.spec.ts` 는 *"`CUSTOMER` 롤 없는 operator 가 web-store 에 로그인하면 거부된다"* 를 단언한다(ADR-MONO-035 4b-iii 가 정의한 role 기반 가드). **그 가드는 lean e2e 스택뿐 아니라 프로덕션에서도 발화하지 못한다.** 체인:

1. **로그인 자체는 된다.** `CredentialAuthenticationProvider.authenticate` 는 `credentialRepository.findAllByEmail(email)` 로 **크로스-테넌트** 조회를 한다(클라이언트 tenant 로 스코프하지 않음). operator 는 `MONO-334`(ADR-035 O2) 이후 **실제 가입 계정을 갖도록 강제**되므로 — *"that account's unified IAM (OIDC) credential is the operator's primary login"* — `credentials` 행이 존재하고, 이메일이 한 tenant 에만 있으면 로그인 성공(`matches.size()>1` 만 fail-closed).

2. **토큰에 `CUSTOMER` 가 붙는다.** `TenantClaimTokenCustomizer.populateRoles(claimTenantId, platformTenantId)`:
   ```java
   List<String> roles = (stored != null && !stored.isEmpty())
           ? stored
           : RoleSeedPolicy.seed(platformTenantId);   // platform = 클라이언트의 tenant = "ecommerce"
   ```
   operator 의 `account_roles` 가 비어 있으면(대개 그렇다 — ADR-035 §32: *"No `account_roles` rows exist for any operator"*) **fail-soft seed** 로 떨어지고, `RoleSeedPolicy.seed("ecommerce") = ["CUSTOMER"]`. **seed 는 사람(principal)이 아니라 등록된 클라이언트의 platform 으로만 키잉된다** — `MONO-263` 이 `account_type` 판별자를 제거한 결과.

3. **web-store 가드가 admit 한다.** `signInCallback` 은 `roles` 의 `CUSTOMER` **만** 본다(`auth-callbacks.ts:218`, tenant_id 는 안 봄). 2번이 `CUSTOMER` 를 넣었으므로 통과.

4. **게이트웨이도 통과.** ecommerce 게이트웨이는 `TenantClaimValidator.acceptAnyWellFormedTenant()`(마켓플레이스 edge, ADR-MONO-030 §2.4) 라 `tenant_id` 가 well-formed 이기만 하면 통과 — SUPER_ADMIN 의 `'*'` 포함.

**⇒ ecommerce 클라이언트로 로그인하는 *모든* 인증된 principal 이 `CUSTOMER` 를 받는다. "`CUSTOMER` 없는 토큰" 이 이 경로에서 발급 불가능하므로, ADR-035 가 의미 있게 만들려던 role 기반 가드는 절대 발화하지 못한다(vacuous).**

## 왜 아무도 못 봤나

이 유일한 code-level 가드를 검증하는 테스트가 `account-type-guard.spec.ts` 하나인데, **그 스펙은 지금껏 어느 CI 잡에서도 돈 적이 없다**(MONO-373 이 밝힌 공백). 게다가 그 스펙은 자체로도 깨져 있다 — `completeGapSignIn` 은 GAP 이 렌더하지 않는 페이지를 가정하고, `uniqueUser('operator')` 로 **존재하지 않는 이메일**을 넣는다(GAP 엔 inline signup 없음). **가드가 vacuous 하다는 사실을, vacuous 한 테스트가 가리고 있었다.**

## 결정해야 할 것 — 이게 결함인가, 의도된 aud-default 인가

**ADR-MONO-035 §4b-iii 는 "no-cross-type-SSO 규칙을 폐기(lift)" 하고 web-store 가드를 role 기반으로 바꿨다.** 그리고 §4b-iv 는 *"decouple `RoleSeedPolicy` from `account_type` — **the SAS path authenticates only consumers**"* 라 적었다. **바로 이 전제가 강제되지 않는다** — `findAllByEmail` 은 크로스-테넌트라 operator 자격증명도 찾는다. 두 해석이 가능하다:

- **(A) 결함이다** — role 기반 가드를 의미 있게 만들려면 `CUSTOMER` 가 실제 consumer 에게만 붙어야 한다. seed 가 principal 종류를 구분하거나(예: operator 신원이면 seed 억제), web-store 가드가 role 외에 tenant_id/entitled_domains 도 봐야 한다. 후보 수정 지점: `RoleSeedPolicy` / `populateRoles` fail-soft 분기 / `signInCallback`.
- **(B) 의도된 aud-default 다** — *"web-store 에 로그인하는 사람은 곧 고객"* 이고, 실제 경계는 "인증되는가(credentials 보유)" + 영속층 `WHERE tenant_id` 다. 이 경우 `account-type-guard.spec.ts` 가 stale 이고 삭제 대상이며, 가드가 vacuous 한 것은 설계다. **단, 그렇다면 ADR-035 4b-iii 의 "role 기반 가드" 는 실질적 방어가 아니라 형식임을 ADR 에 명시**해야 한다(선언↔진실 정합).

**두 경우 모두 ADR-035 의 문서 상태를 바꾼다** — (A) 는 발급 모델 개정, (B) 는 가드의 실효성에 대한 정정. 그래서 이건 code fix 가 아니라 **ADR 결정**이다.

---

# Scope

## In Scope (결정 이후)

- 방향 (A)/(B) 중 하나를 `ADR-MONO-035` 개정(또는 새 ADR)으로 기록하고 ACCEPT(사람).
- (A) 라면: seed/가드 수정 + `account-type-guard.spec.ts` 를 **실제로 무는** 형태로 배선(mutation: operator 에게 CUSTOMER 강제 → RED). **착수 전 토큰을 디코드해** operator 토큰에 `CUSTOMER` 가 없는지 먼저 확인(§ 착수 전 실측).
- (B) 라면: `account-type-guard.spec.ts` 삭제 + ADR-035 에 가드 실효성 정정.

## Out of Scope

- MONO-373 이 배선한 3개 스펙(golden-flow/cart/wishlist) — 이미 돈다.
- 게이트웨이 `acceptAnyWellFormedTenant` 자체 — ecommerce 마켓플레이스 edge 설계로 정당(ADR-030 §2.4). 이 결함의 원인이 아니라 **네 번째 통과 지점**일 뿐.

---

# ⚠️ 착수 전 실측할 것 (F1)

**real account-service 를 붙여 operator 에게 `account_roles` 를 시드하면 stored 경로를 타 seed 를 우회할 수 있으나, roles 가 비어 돌아오면(`[]`) 다시 fail-soft seed 로 떨어져 `CUSTOMER` 가 붙는다.** 코드가 아니라 **발급된 토큰을 디코드**해 확인할 것. `populateRoles` 의 *"stored empty → seed"* 분기가 핵심이다.

---

# 재개 실측 (2026-07-13) — **이 가드는 운영자 가드가 될 수 없다**

BE-507 이 park 을 풀어줬으므로 원래 계획(seed 를 *"claim tenant == 클라이언트 platform"* 으로 좁히기)을 다시 검토했다. **좁히기는 유효하지만, 그것이 막는 대상은 티켓이 믿었던 대상이 아니다.**

**결정적 사실 (코드로 확인):**

1. `CreateOperatorUseCase:86-98` (**TASK-MONO-334**) — 운영자 생성은 **그 운영자의 홈 tenant 에 이미 가입 계정이 있어야** 가능하다 (`accountServiceClient.search(tenantId, normalizedEmail)`; 주석: *"that account's unified IAM (OIDC) credential is the operator's primary login"*).
2. **BE-507 이후** 가입 계정의 tenant = **가입한 OIDC 클라이언트의 tenant**.
3. ⇒ **`ecommerce` 운영자가 되려면 `tenant='ecommerce'` 계정이 있어야 하고, 그건 web-store 클라이언트로 가입했다는 뜻 — 즉 그는 이미 등록된 쇼핑객이다.**

**⇒ "CUSTOMER 없는 ecommerce 운영자 토큰" 은 seed 정책과 무관하게 *구성 불가능*하다.** 같은-tenant 운영자에 대해 이 가드가 vacuous 한 것은 **MONO-334 의 설계 귀결**이지 seed 의 사고가 아니다. 원 티켓의 (A)/(B) 프레이밍 — *"운영자를 막을 것인가"* — 은 **답할 수 없는 질문**이었다.

**부수 실측**: 소비자에게 `account_roles` 를 심는 경로가 **0건**이다(`SignupUseCase`/`SocialSignupUseCase` 는 `AccountRole` 을 참조조차 안 함; 모든 writer 는 provisioning/admin 경로). ⇒ **seed 는 fallback 이 아니라 `roles` 클레임의 유일한 출처**다. 이것이 "가드가 물 대상을 발급 단계에서 이미 잃었다" 의 정확한 기전이다.

## 결정 — **A′ (사용자, 2026-07-13): cross-tenant 가드로 재정의**

seed 는 **principal 자신의 tenant 가 클라이언트의 platform 일 때만** 발화한다. 이 가드가 실제로 막는 것:

| principal | claim tenant | seed | roles | web-store |
|---|---|---|---|---|
| 신규 ecommerce 소비자 | `ecommerce` | ✅ | `[CUSTOMER]` | 통과 (의도) |
| **ecommerce 운영자** | `ecommerce` | ✅ | `[CUSTOMER]` | 통과 — **정당**(그는 등록된 쇼핑객이다) |
| **wms/acme 운영자** | `wms` 등 | ❌ | 없음 | **차단** ← 새로 막힌다 |
| **SUPER_ADMIN** | `*` | ❌ | 없음 | **차단** ← 게이트웨이가 `acceptAnyWellFormedTenant` 로 `'*'` 를 통과시키므로 **이 가드가 유일한 방어** |
| 레거시 소비자(BE-507 이전) | `fan-platform` | ❌ | 없음 | **차단** ⚠️ 아래 |

## ⚠️ 의도된 대가 — 레거시 소비자, 그리고 이것이 `TASK-MONO-386` 의 forcing function 이다

BE-507 이전에 만들어진 소비자는 `tenant='fan-platform'` 이라 스토어프론트에서 seed 를 못 받는다 ⇒ **로그인은 되지만**(BE-507 의 cross-tenant credential 폴백) **스토어프론트는 거부**된다. 이 모집단은 **장수 데모 인스턴스에만 존재**한다(`TASK-MONO-386` 실측: 시드가 소비자 계정을 만들지 않고, 갓 부팅한 스택은 `credentials` 가 빈다). **A′ 는 MONO-386 의 D1(방치/폐기/부분재배정)을 강제한다** — 그 결정 전까지 장수 데모의 기존 쇼핑객은 재가입이 필요하다.

---

# Acceptance Criteria

- [x] **AC-1** — **A′** 가 `ADR-MONO-035` **§ Amendments (2026-07-13, TASK-MONO-381)** 에 기록됐다: *"§4b-iii 의 가드는 cross-tenant 가드이지 operator 가드가 아니다"* + 그 근거(위 3단 논증) + 레거시 대가. **방향은 사용자가 정했다**(self-ACCEPT 아님).
- [x] **AC-2** — 발급 측: `TenantClaimTokenCustomizer.seedFor(claim, platform)` 로 좁힘. **토큰 디코드 대신 발급 로직 단위 테스트 4종**으로 고정(cross-tenant operator · SUPER_ADMIN `'*'` · 레거시 fan 소비자 · **fail-soft 가 seed 를 되살리지 못함**). e2e: `account-type-guard.spec.ts` **`test.fixme` 해제** — cross-tenant 자격증명(`tenant_id='*'`)을 시드해 실제로 돌게 했다. **`assert-specs-ran.mjs` 의 REQUIRED 목록에도 추가**(이 스펙이 "안 돌면서 초록"으로 돌아가지 못하게).
- [x] **AC-3 (가드가 무는가)** — **mutation-check**: 좁히기를 제거(=옛 blind seed 복원)하자 **새 가드 4개만 정확히 FAILED**, 나머지 44개 통과(**오탐 0**). 복구 확인.
- [x] **AC-4 (무손실)** — auth-service 단위 전량 그린. 기존 `authorizationCode_emptyStored_ecommerce_seedsCustomer`(claim==platform)·`fan-platform → FAN`·`rolesLookupThrows_failSoftToSeed` 모두 유지. web-store `tsc` exit=0 · `next lint` **경고/에러 0**.

## 검증

- **auth-service 단위**: 전량 그린(`TenantClaimTokenCustomizerTest` 48건 포함).
- **mutation-check**: 위 AC-3.
- **web-store**: `tsc --noEmit` exit=0, `next lint --max-warnings=0` clean (worktree 에 pnpm 미populate → 메인의 `node_modules` 를 junction 으로 붙여 검증 후 **junction 선제거**).
- **미실행**: Testcontainers IT(`SocialLoginSasBrowserIntegrationTest` — 소셜 브라우저 로그인은 claim==platform 이므로 계속 `CUSTOMER` 여야 한다) + **full-stack e2e**. 로컬 Docker 백엔드 행(`timed out dialing Hyper-V socket`) ⇒ **CI/nightly 가 권위**. **`account-type-guard.spec.ts` 는 이번에 처음 실행되는 스펙이므로 nightly 가 첫 실측이다.**

---

# Related Specs

- `projects/ecommerce-microservices-platform/apps/web-store/e2e/account-type-guard.spec.ts` (현재 `test.fixme()`, 본 티켓 참조)
- `projects/ecommerce-microservices-platform/apps/web-store/src/shared/auth/auth-callbacks.ts` — `signInCallback` (roles-only)
- `projects/iam-platform/apps/auth-service/.../oauth2/RoleSeedPolicy.java` — client-platform 키잉
- `projects/iam-platform/apps/auth-service/.../oauth2/TenantClaimTokenCustomizer.java` — `populateRoles` fail-soft
- `projects/iam-platform/apps/auth-service/.../security/CredentialAuthenticationProvider.java` — 크로스-테넌트 `findAllByEmail`
- `libs/java-gateway/.../security/TenantClaimValidator.java` — `acceptAnyWellFormedTenant`
- `docs/adr/ADR-MONO-035-operator-auth-unification-model.md` §4b (iii)(iv), §31-32

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` — `roles` (TASK-MONO-371 등록), `account_type` (제거됨, ADR-035 4b)

---

# Edge Cases

- **operator 가 두 tenant 에 이메일을 가지면** `matches.size()>1` → fail-closed(로그인 거부). 이건 다른 문제(tenant chooser 미구현)이고 이 결함을 **가린다** — 단일-tenant operator 만 재현된다.
- **`acceptAnyWellFormedTenant` 는 `'*'` 도 통과**시키므로 SUPER_ADMIN 도 web-store 에 CUSTOMER 로 진입 가능. 심각도 판단 시 고려.

# Failure Scenarios

- **F1** — (A) 수정 후에도 seed 가 CUSTOMER 를 붙인다 → § 착수 전 실측.
- **F2** — 스택 healthy 인데 operator 로그인 실패 → MONO-358 이 통째로 겪은 자리.

---

# Test Requirements

- (A): 실제 CI 런에서 `account-type-guard` 실행 + operator-CUSTOMER mutation RED.
- (B): 스펙 삭제 + ADR 정정 diff.

---

# Definition of Done

- [ ] AC-1 ~ AC-4.
- [ ] `tasks/INDEX.md` done entry.

---

# 구현 실측 (2026-07-13) — 방향 (A) 착수 시도 결과: 깔끔한 수정이 없다

사용자가 방향 **(A) 결함으로 고침**을 택한 뒤 최소 수정을 찾으려다 **막혔다.** 기록:

**시도한 깔끔한 수정 (성립 안 함).** `populateRoles` 에서 seed 를 *"claim `tenant_id` == platform `tenant_id`(`ecommerce`) 일 때만"* 으로 좁히면 operator(홈 tenant 또는 `'*'`)는 걸러지고 소비자만 CUSTOMER 를 받을 것 같았다. **그러나 소비자도 `tenant_id='ecommerce'` 가 아니다** — 아래 선행 때문에.

**선행 (blocker) — 소비자 tenant 배정이 미완성 stopgap.** `account-service` 의 **두 가입 경로 모두**가 tenant 를 하드코딩한다:
- `SignupUseCase.java:35` — `TenantId tenantId = TenantId.FAN_PLATFORM;`
- `SocialSignupUseCase.java:33` — 동일.
둘 다 주석이 *"TASK-BE-228: tenant context is fixed to FAN_PLATFORM **until TASK-BE-229** introduces dynamic tenant injection"* 라는데 — **`TASK-BE-229` 는 이미 `done`** 이고 이 경로들은 갱신되지 않았다(stale 주석 + 미완 배선). ⇒ 소비자의 `tenant_id` 를 seed 판정 기준으로 **쓸 수 없다**(정상 소비자까지 걸러질 것). **이 stopgap 이 web-store 게이트웨이가 `acceptAnyWellFormedTenant` 를 쓰는 이유와도 연결된다** — 소비자가 `tenant=ecommerce` 를 안 갖기 때문. ⇒ **별건 조사 `TASK-BE-506` 로 분리**(반경 미확정).

**남은 수정 대안 — 둘 다 설계 결정이다.**
- **(a) 가입 시 `account_roles` 에 CUSTOMER 프로비저닝** (ADR-033: account_roles 가 authoritative) + blind seed 축소/제거. → 기존 소비자 데이터 마이그레이션 수반.
- **(b) 소비자 로그인 hot-path 에 "이 계정이 operator 인가"(admin_operators 조회) 추가.** → 다운스트림 의존 + fail-soft 의미론을 소비자 로그인 경로에 얹음.

⇒ **원 티켓의 "ADR-035 개정 결정 필요" 판단이 실측으로 재확인됐다.** 게다가 (a)/(b) 어느 쪽도 `TASK-BE-506`(tenant 배정 정리)의 결론에 의존한다. **그래서 park.** tenant 모델이 정리되면 방향 (A) 하에 (a)/(b) 를 결정해 재개.

---

# Provenance

발굴 2026-07-13 — `TASK-MONO-373` 구현 중, `account-type-guard.spec.ts` 를 살리려다 발급-측에서 실측.

**이 결함의 모양은 이 저장소가 반복해 배운 것과 정확히 같다: 선언↔진실 드리프트.** ADR-MONO-035 §4b-iv 는 *"the SAS path authenticates only consumers"* 라 **선언**했고, `CredentialAuthenticationProvider.findAllByEmail` 의 **진실**은 크로스-테넌트다. 그 선언이 강제되지 않아 role 기반 가드가 vacuous 해졌고, **그 사실을 검증할 유일한 테스트가 한 번도 실행되지 않아**(MONO-373) 아무도 몰랐다. **가드가 있다고 믿었는데 그 가드는 물 대상을 발급 단계에서 이미 잃었다** — `project_guard_reachability_not_just_bite` 의 발급-측 변종.

분석=Opus 4.8 / 구현 권장=**Opus** (발급 모델 판단 + IAM 보안 경로. ACCEPT 는 사람).
