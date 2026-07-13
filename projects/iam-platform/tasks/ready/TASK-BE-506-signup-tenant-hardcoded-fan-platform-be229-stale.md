# Task ID

TASK-BE-506

# Title

두 가입 경로가 모든 소비자를 `FAN_PLATFORM` tenant 로 하드코딩한다 — 주석은 *"until BE-229"* 라는데 **BE-229 는 이미 done**. ecommerce 소비자까지 오배정되는지 **반경을 실측**하라 (조사 우선)

# Status

ready

# Owner

iam-platform

# Task Tags

- investigate
- iam
- tenant

---

# ⚠️ 이 티켓은 먼저 **조사**다 — 반경을 실측한 뒤 수정 범위를 정한다

수정부터 하지 말 것. 아래 **AC-0(반경 실측)** 이 이 티켓의 본체다. 오배정이 실재하는지, 어디까지 번지는지를 먼저 확정하고, 그 결과에 따라 수정을 이 티켓에서 할지 후속으로 뺄지 정한다.

---

# Dependency Markers

- **발굴 경위**: `TASK-MONO-381` 구현 실측 중(2026-07-13) — web-store role 가드가 vacuous 한 원인을 파다 소비자 tenant 배정이 신뢰 불가임을 발견.
- **선행 (done, 그러나 배선 미완 의심)**: `TASK-BE-228`(account tenant schema) · `TASK-BE-229`(auth jwt tenant claim) · `TASK-BE-230`(gateway tenant propagation). **주석이 가리키는 BE-229 가 done 인데 가입 경로가 갱신되지 않았다.**
- **연관**: `TASK-MONO-381`(이 조사 결과에 park 되어 의존) · `TASK-BE-313`(credential tenant_id pass-through — 하류에서 이 값을 그대로 전달).

---

# Goal — 실측한 것 (2026-07-13)

`account-service` 의 **두 가입 use case 모두** tenant 를 상수로 박는다:

```java
// SignupUseCase.java:35  (공개 폼 가입)
// SocialSignupUseCase.java:33  (소셜 가입)
// TASK-BE-228: tenant context is fixed to FAN_PLATFORM until TASK-BE-229
// introduces dynamic tenant injection from the JWT claim / X-Tenant-Id header.
TenantId tenantId = TenantId.FAN_PLATFORM;
```

- **`TASK-BE-229` 는 `projects/iam-platform/tasks/done/` 에 있다.** 주석이 약속한 "dynamic tenant injection" 이 이 두 경로에는 도착하지 않았다 — **선언(주석)↔진실(코드)** 드리프트.
- **함의(미확정)**: post-`MONO-027` 로 ecommerce 는 GAP/IAM OIDC 소비자다. web-store 가입은 `signIn('iam')` 으로 IAM 가입 흐름에 리다이렉트된다(`apps/web-store/src/app/(auth)/signup/page.tsx`). **그 흐름이 이 두 use case 중 하나를 타면, ecommerce 소비자도 `tenant=fan-platform` 으로 생성된다.** 그러면 `credentials.tenant_id`(BE-313 pass-through)와 토큰 `tenant_id` 도 `fan-platform` 이 되고 — 이는 **web-store 게이트웨이가 `acceptAnyWellFormedTenant` 를 켠 이유**(소비자가 `tenant=ecommerce` 를 안 가짐)와 정확히 맞물린다.

**하지만 이게 실제 오배정인지, 아니면 어딘가에서 override 되는지, 혹은 fan 외 소비자는 다른 경로를 타는지 — 확인하지 않았다.** 그래서 이 티켓은 조사다.

---

# Scope

## In Scope

- **AC-0 (반경 실측)** 이 본체. 결과에 따라 수정 범위 결정.
- 수정이 자명하고 국소적이면(예: JWT claim / `X-Tenant-Id` 에서 tenant 를 읽도록 두 use case 배선 — BE-229 가 원래 하려던 것) 이 티켓에서 처리.
- 배선이 넓거나(하류 마이그레이션, 기존 `fan-platform` 계정 재배정) 설계 결정을 부르면 **후속으로 분리**하고 이 티켓은 조사+선까지.

## Out of Scope

- `TASK-MONO-381`(web-store role 가드) 자체 — 이 조사 결과에 park 되어 있다. **이 티켓이 tenant 배정을 신뢰 가능하게 만들면 381 의 (a)/(b) 결정이 가능해진다.**
- 기존 프로덕션 `fan-platform` 계정의 소급 재배정 — 실측 후 별도 판단(프로덕션 데이터라 wipe 불가, ADR-036 P4 와 같은 성격).

---

# Acceptance Criteria

- [ ] **AC-0 (반경 실측 — 본체)** — 다음을 코드/데이터로 확정한다: (1) web-store 소비자 가입이 실제로 이 두 use case 중 하나를 타는가, 타면 어느 것. (2) 결과 `accounts.tenant_id` / `credentials.tenant_id` / 토큰 `tenant_id` 가 무엇이 되는가(가정 말고 실측 — 가능하면 로컬 가입 후 DB/토큰 디코드). (3) 오배정이 ecommerce 외 다른 소비자(fan 정상? scm? 등)에도 번지는가. **"주석이 stale 하다" 만으로 결함이라 결론 내지 말 것 — BE-229 가 이 경로를 의도적으로 제외했을 수도 있다(그렇다면 주석만 정정).**
- [ ] **AC-1** — 실측 결과가 오배정이면: 두 use case 가 tenant 를 **동적으로**(JWT claim / `X-Tenant-Id`, BE-229 가 세운 메커니즘) 결정하도록 수정하거나, 그 수정이 큰 경우 후속 티켓으로 분리 + 이 티켓은 정확한 범위를 남긴다.
- [ ] **AC-2 (stale 주석 정정)** — 결과와 무관하게 `until TASK-BE-229` 주석은 거짓이다(BE-229 done). 지우거나 사실로 고친다.
- [ ] **AC-3** — 기존 커버리지 무손실: fan-platform 소비자 가입/로그인이 계속 동작.

---

# Related Specs

- `projects/iam-platform/apps/account-service/src/main/java/com/example/account/application/service/SignupUseCase.java:35`
- `.../account/application/service/SocialSignupUseCase.java:33`
- `projects/ecommerce-microservices-platform/apps/web-store/src/app/(auth)/signup/page.tsx` — `signIn('iam')` 리다이렉트
- `projects/iam-platform/tasks/done/TASK-BE-229-auth-jwt-tenant-claim.md` — 주석이 가리키는 "완료된" 선행
- `tasks/ready/TASK-MONO-381-web-store-role-guard-vacuous-seed-grants-customer.md` — 이 조사에 park 된 하류 결함

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` — `tenant_id` (TASK-MONO-371 등록; BE-229 가 발급하는 claim)

---

# Edge Cases

- **`fan-platform` 이 의도된 기본값일 수 있다** — account-service 는 fan-platform 태생이다. BE-229 가 fan 이외 tenant 를 동적 주입하도록 했는데 이 가입 경로만 누락됐는지, 아니면 가입은 원래 fan 고정이 맞는지 실측으로 가른다.
- **소셜 vs 폼 경로가 다를 수 있다** — 둘 다 하드코딩이지만 실제 트래픽 경로가 다르면 반경이 다르다.

# Failure Scenarios

- **F1 — "stale 주석" 을 결함으로 속단** → BE-229 가 이 경로를 의도적으로 제외했다면 수정이 아니라 주석 정정이 답. AC-0 이 이걸 가른다.
- **F2 — 동적 tenant 로 바꿨더니 기존 fan 소비자가 깨진다** → 소급 재배정은 Out of Scope. additive/net-zero 로.

---

# Test Requirements

- AC-0 의 실측(로컬 가입 → DB/토큰 확인)이 조사의 증거.
- 수정 시: 가입 tenant 배정 단위/통합 테스트 + fan 회귀 무손실.

---

# Definition of Done

- [ ] AC-0 실측 기록 + AC-1~AC-3(또는 후속 분리 명시).
- [ ] `projects/iam-platform/tasks/INDEX.md` done entry.

---

# Provenance

발굴 2026-07-13 — `TASK-MONO-381` 구현 실측 중.

**모양이 이 저장소가 반복해 배운 것과 같다: 선언↔진실 드리프트.** 주석은 *"until BE-229"* 라 **선언**했고, BE-229 가 done 이라는 **진실**과 어긋난다. 그리고 그 드리프트가 하류(web-store role 가드 vacuity, `MONO-381`)로 번졌는데 **아무 테스트도 소비자 tenant 배정을 단언하지 않아** 드러나지 않았다. **먼저 반경을 세라 — 주석의 "BE-229" 는 출처가 아니라 가설이다**(`feedback_recount_population_dont_inherit_scope`).

분석=Opus 4.8 / 구현 권장=Sonnet (조사 후 수정이 국소적이면; 넓으면 재평가).
