# Task ID

TASK-MONO-381

# Title

`account-type-guard.spec.ts` 는 **헬퍼가 아니라 스택 때문에** 통과할 수 없다 — lean IAM 은 `CUSTOMER` 없는 토큰을 **구조적으로 발급할 수 없다**. 실제 `account-service` 가 필요하다

# Status

ready

# Owner

monorepo

# Task Tags

- ci
- test

---

# Dependency Markers

- **선행 (done)**: `TASK-MONO-373` — 결제 경로 e2e 배선. 4개 중 **3개**(`golden-flow` · `cart-management` · `wishlist`)를 실제로 실행시켰고, **이 4번째는 실행 불가임을 실측**해 여기로 분리했다.
- **원 출처**: `helpers/auth.ts` 의 `completeGapSignIn` Javadoc — *"an FE-074 follow-up that needs a seeded operator identity lacking the CUSTOMER role — **stack-gated**"*. 그 "stack-gated" 가 무슨 뜻인지 MONO-373 이 실측했다(아래).

---

# Goal

## MONO-373 의 실측 (2026-07-13)

`account-type-guard.spec.ts` 는 **OPERATOR 로 web-store 에 로그인하면 `roles ∌ CUSTOMER` 이라 거부된다**를 단언한다. MONO-373 은 이 스펙을 배선하려다 **스택이 그 전제를 만들 수 없음**을 확인했다:

1. **lean IAM 스택엔 실제 `account-service` 가 없다** — `docker-compose.iam-e2e.yml` 은 `account-mock`(nginx)을 쓰고, 이 스텁은 `GET /internal/tenants/{tid}` 만 답한다. **roles 조회(`/internal/**`)는 404** 로 떨어진다.
2. **roles 404 → fail-soft → 로컬 seed** — `TenantClaimTokenCustomizer.populateRoles` 가 account 조회 실패 시 `RoleSeedPolicy` 로 폴백한다.
3. **`RoleSeedPolicy.seed()` 는 사용자가 아니라 *등록된 클라이언트의 platform tenant_id* 로 키잉된다** (BE-369, ADR-MONO-033 S3):
   ```java
   case "ecommerce" -> List.of("CUSTOMER");
   ```
   web-store 클라이언트(`ecommerce-web-store-client`)는 platform=`ecommerce` 로 등록돼 있다.

**⇒ lean 스택에서 `ecommerce-web-store-client` 로 로그인하는 *모든* 자격증명은 `CUSTOMER` 를 받는다.** 어떤 행을 어떻게 시드하든 "CUSTOMER 가 없는 operator" 토큰은 나오지 않는다. **가드가 물 대상 자체를 만들 수 없다.**

**⇒ 헬퍼를 고치는 문제가 아니다.** (헬퍼도 고쳐야 하지만 — 아래 — 그건 2차 문제다.)

## 그래서 지금 상태

MONO-373 이 이 스펙을 **`test.fixme()` + 본 티켓 참조**로 표시했다. `SKIP_GAP_E2E=0` 인 새 레인에서 나머지 3개는 실행되고 이 1개만 명시적으로 보류된다. **유예를 주석이 아니라 티켓으로 들고 있다** (MONO-369 가 만들어진 이유 — 티켓 없는 유예는 썩는다).

---

# Scope

## In Scope

1. **실제 `account-service` 를 세우는 IAM 레인** — operator 의 roles 를 진짜로 응답하는 스택. 후보:
   - `docker-compose.iam-e2e.yml` 을 확장해 real `account-service`(+ `account_db`) 를 넣는다.
   - 또는 `iam-platform` 실제 compose 를 ecommerce 백엔드와 함께 띄운다(무겁다 — 러너 RAM 예산 실측 필요; MONO-373 이 lean 을 고른 이유가 이것).
2. **OPERATOR 자격증명 + assignment 시드** — `admin_operators` / `operator_tenant_assignment` 를 포함해, roles 에 `CUSTOMER` 가 **없는** 토큰이 실제로 발급되는지 **토큰을 디코드해 확인**한다(가정 금지).
3. **`loginAsOperatorAndExpectMismatch` 재작성** — 현재는 `completeGapSignIn` 을 부르는데, 그 헬퍼는 GAP 이 **렌더하지 않는** "가입-또는-로그인" 페이지를 가정하고 `uniqueUser('operator')` 로 **존재하지 않는 이메일**을 넣는다(GAP 엔 inline signup 이 없다 — `auth.ts:86`). 시드된 operator + `fillGapCredentialForm` 으로 바꾼다.
4. `test.fixme()` 해제.

## Out of Scope

- 나머지 3개 스펙 — MONO-373 에서 이미 돌고 있다.
- web-store 의 role 가드 로직 자체 — 정상이다. **테스트 스택이 그 입력을 못 만들 뿐이다.**

---

# ⚠️ 착수 전 실측할 것

**"operator 를 시드하면 CUSTOMER 가 빠지는가"를 코드가 아니라 토큰으로 확인하라.** real account-service 가 roles 를 응답하면 seed 폴백이 아예 안 타지만, **응답이 비어 있으면(`[]`) 다시 fail-soft seed 로 떨어져 `CUSTOMER` 가 붙을 수 있다** — 그 경우 이 스펙은 여전히 통과하지 못한다. `populateRoles` 의 "stored empty → seeds by principal account_id" 분기(`TenantClaimTokenCustomizer` 주변 주석)를 먼저 읽을 것.

---

# Acceptance Criteria

- [ ] **AC-1** — operator 토큰의 `roles` 에 `CUSTOMER` 가 **없음**을 디코드로 확인(스펙 통과 이전에).
- [ ] **AC-2** — `account-type-guard.spec.ts` 가 CI 잡에서 **실행**되고 통과한다(`test.fixme()` 제거).
- [ ] **AC-3 (헛된 초록 배제)** — 리포트에서 이 스펙의 실행 건수를 확인. skip 이 아니어야 한다.
- [ ] **AC-4 (가드가 무는가)** — operator 에게 `CUSTOMER` 를 강제로 부여한 mutation 에서 이 스펙이 **RED** 가 된다(= 가드가 role 을 실제로 본다).
- [ ] **AC-5** — MONO-373 이 세운 3개 스펙 + `auth-redirect` + `rp-initiated-logout` 무손실.

---

# Related Specs

- `projects/ecommerce-microservices-platform/apps/web-store/e2e/account-type-guard.spec.ts`
- `projects/ecommerce-microservices-platform/apps/web-store/e2e/helpers/auth.ts` — `completeGapSignIn`(사실상 dead) / `loginAsOperatorAndExpectMismatch`
- `projects/iam-platform/apps/auth-service/src/main/java/com/example/auth/infrastructure/oauth2/RoleSeedPolicy.java`
- `.../oauth2/TenantClaimTokenCustomizer.java` — `populateRoles` fail-soft 분기
- `tasks/done/TASK-MONO-373-*.md` — 배선 + 이 분리의 근거

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` — `roles` 클레임 (TASK-MONO-371 이 등록)

---

# Edge Cases

- **`account-mock` 의 404 는 의도된 fail-soft** 다 — 없애면 로그인이 fail-closed 로 깨진다(BE-407). real account-service 로 갈아끼울 때 **tenant_type 응답을 잃지 말 것**.
- **RAM** — real account-service + account_db 를 ecommerce 22 컨테이너 위에 얹는다. MONO-373 의 fullstack 레인이 이미 러너 예산 근처다. 별도 잡이 필요할 수 있다.

# Failure Scenarios

- **F1** — real account-service 를 넣었는데 roles 가 비어 돌아와 fail-soft seed 가 다시 `CUSTOMER` 를 붙인다 → § "착수 전 실측" 참조. 이 경우 스펙이 요구하는 상태는 **operator 를 web-store 클라이언트로 로그인시키는 것 자체가 성립하는가**로 재검토해야 한다.
- **F2** — 스택은 healthy 한데 operator 로그인이 안 된다 → MONO-358 이 통째로 겪은 자리.

---

# Test Requirements

- 실제 CI 런에서 `account-type-guard` 실행 + skipped=0.
- mutation: operator 에게 `CUSTOMER` 부여 → 스펙 RED (AC-4).

---

# Definition of Done

- [ ] AC-1 ~ AC-5.
- [ ] `tasks/INDEX.md` done entry.

---

# Provenance

발굴 2026-07-13 — `TASK-MONO-373` 구현 중 실측.

**MONO-373 의 티켓은 "스펙 4개는 멀쩡하다, 배선만 하라" 고 적었고 그 근거로 MONO-292 의 실증을 들었다. 그런데 그 실증은 `4/4` 가 아니라 `3/3` 이었다** — 숫자가 이미 답을 알고 있었는데 아무도 그 3 과 4 의 차이를 묻지 않았다. (`feedback_recount_population_dont_inherit_scope`: 선행 문서의 숫자는 출처가 아니라 **가설**이다.)

분석=Opus 4.8 / 구현 권장=**Opus**(IAM 스택 선택 + role 파생 경로 판단. 잘못 고르면 F1 로 되돌아온다).
