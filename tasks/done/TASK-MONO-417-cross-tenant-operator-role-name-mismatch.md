# Task ID

TASK-MONO-417

# Title

크로스-테넌트 operator 가 자기 표면에서 operator 로 인식되지 않는다 — iam 은 `SCM_OPERATOR`/`FAN_OPERATOR`/`FINANCE_OPERATOR` 를 민팅하는데 그 세 플랫폼의 서비스는 일반형 `OPERATOR`/`ADMIN`/`SUPER_ADMIN` 만 본다

# Status

done

# Owner

monorepo

# Task Tags

- code
- security
- decision

---

# Goal

iam 의 assume-tenant(token_exchange) 발급기가 선택 테넌트의 entitled 도메인에서 **도메인 접두 operator 역할**을 도출한다:

- `projects/iam-platform/apps/auth-service/.../oauth2/OperatorRoleDerivation.java:82-86` — `scm → SCM_OPERATOR` · `fan/fan-platform → FAN_OPERATOR` · `finance → FINANCE_OPERATOR` · `erp → ERP_OPERATOR`.

그런데 **scm·fan·finance 서비스의 `isOperator()` 는 접두형을 보지 않고 일반형만** 본다(재측정 시 확인 — 아래는 착수 baseline 가설):

- scm `procurement-service/.../application/ActorContext.java:19` → `hasRole("OPERATOR") || hasRole("ADMIN") || hasRole("SUPER_ADMIN")`
- fan `community-service/.../ActorContext.java:17` (+ `artist-service/.../ActorContext.java:18`) → 동일 일반형 트리플
- finance `account-service/.../ActorContext.java:18` → 동일 일반형 트리플
- **erp 만 정렬됨**: `masterdata-service/.../ActorContext.java` → `ERP_OPERATOR`/`ERP_ADMIN`/`SUPER_ADMIN` (iam 의 `ERP_OPERATOR` 와 일치)

⇒ **콘솔 operator 가 scm/fan/finance 테넌트를 assume 하면 `SCM_OPERATOR` 등을 들고 게이트웨이 인증·admission(TASK-MONO-416, presence)은 통과하지만, 그 플랫폼의 서비스는 그를 BUYER/비-operator 로 취급**한다 → operator 액션 불가. 즉 iam 이 그 토큰에 실어 보낸 operator 역할이 **소비 측에서 죽은 역할**이다. `SUPER_ADMIN`(incident response)은 양쪽이 다 보므로 별개 경로다.

이건 **결정 사안**이다: 접두형을 민팅하면서 소비 측이 안 보는 건 어느 한쪽이 틀렸다는 뜻인데, 어느 쪽을 맞출지는 제품 의도(크로스-테넌트 operator 가 이 플랫폼들에서 operator 로 동작해야 하는가)에 달렸다.

---

# 🔴 AC-0 (착수 = 재측정 — 코드가 이긴다)

위 파일:라인은 **TASK-MONO-416 구현 세션이 소스로 확인한 baseline 가설**이다. 착수 세션은 그대로 믿지 말고 전수 재측정한다([[feedback_recount_population_dont_inherit_scope]]):

- [ ] 각 플랫폼(scm·fan·finance·erp·wms·ecommerce)의 **모든** `isOperator()`/역할 체크 지점을 넓은 술어(`hasRole|hasAnyRole|hasAuthority|ActorType|_OPERATOR`)로 전수 → 접두형 vs 일반형 매핑을 실측. erp=정렬을 **아는 답**으로 자기검증.
- [ ] iam `OperatorRoleDerivation` 의 현재 switch 를 재확인(접두형 목록이 위와 같은지).
- [ ] **크로스-테넌트 operator 가 실제 존재하는 경로인지** 확인 — platform-console assume-tenant 흐름이 scm/fan/finance 를 대상으로 하는가(대상이 아니면 노출면이 다르다). `TASK-SCM-BE-029`(done)가 scm 을 *"의도적 미정렬, erp 의 `ERP_OPERATOR` 를 import 하지 말 것"* 으로 적어 둔 근거를 읽는다 — 그게 결정의 한 축이다.

# Scope

## In Scope

- **결정(AC-1, 블로킹·self-decision 금지)**: 다음 중 하나를 사람이 정한다 — (a) iam 이 이 플랫폼들에 **일반형** operator 역할을 민팅(소비 측을 진실로) / (b) scm·fan·finance 서비스가 **접두형**도 수용(iam 을 진실로, erp 와 대칭) / (c) 크로스-테넌트 operator 는 이 플랫폼들에서 operator 로 동작하지 않는 것이 의도임을 명문화하고 iam 의 접두형 민팅을 그에 맞게 정리(dead role 제거).
- **결정에 따른 구현** — (a)면 iam `OperatorRoleDerivation`, (b)면 3플랫폼 서비스의 역할 체크, (c)면 iam 민팅 + 문서.
- 결정을 뒷받침하는 계약/스펙 갱신(assume-tenant operator 역할 계약이 있으면).

## Out of Scope

- erp·wms·ecommerce 의 역할 체크 변경(erp=이미 정렬, wms/ecommerce=별 어휘) — 재측정에서 갭이 나오면 별건.
- 게이트웨이 admission(MONO-416 에서 presence 로 배선 완료 — presence 는 접두/일반 양쪽을 통과시키므로 이 티켓과 독립).

---

# Acceptance Criteria

- [ ] **AC-0** 위 재측정. 접두형↔일반형 매핑을 전수 실측하고 PR 본문에 기록(뒤집히면 뒤집힌 대로). 크로스-테넌트 operator 경로 실재 여부 확인.
- [ ] **AC-1 (결정 — 블로킹, self-decision 금지)** (a)/(b)/(c) 중 사람 결정.
- [ ] **AC-2 (구현)** 결정대로 한쪽을 맞춘다. (b)를 택하면 3플랫폼이 접두형을 수용하되 erp 패턴(`ERP_OPERATOR` OR 일반형)과 일관되게.
- [ ] **AC-3 (음성/양성 테스트)** assume-tenant 로 도출된 operator 역할을 든 토큰이 그 플랫폼 서비스에서 operator 로 인식되는지(양성) + 비-operator 는 아닌지(음성)를 단언하는 테스트. 값이 프로덕션에 공존 가능한 픽스처로([[env_test_fixture_impossible_input_proves_nothing]]).
- [ ] **AC-4 (CI 3차원 GREEN)** 영향 서비스 잡 GREEN, 종료코드 금지 판정.

---

# Related Specs

- `platform/contracts/jwt-standard-claims.md` (roles = sole authorization axis)
- `docs/adr/ADR-MONO-035-operator-auth-unification-model.md` (operator 역할 도출 = O1/step 4a) · `ADR-MONO-032`
- `projects/scm-platform/tasks/done/TASK-SCM-BE-029-*.md` (scm 일반형 유지 근거 — 결정 입력)
- `projects/*/specs/integration/iam-integration.md` (assume-tenant 흐름)

# Related Contracts

- `platform/contracts/jwt-standard-claims.md`

# Target Service

`projects/iam-platform/apps/auth-service` (민팅) · `projects/{scm,fan,finance}-platform/apps/*` (소비) — 결정에 따라 한쪽.

---

# Edge Cases

- **`SUPER_ADMIN` 는 이미 양쪽이 본다** — incident response 경로는 이 갭과 무관(양성 회귀 방지).
- **base 로그인은 다르다** — scm/erp 는 backend-only 라 base 로그인이 `[]` 역할(또는 `account_roles` 직접 부여). 이 갭은 **assume-tenant(token_exchange) 경로 전용**이다. 재측정에서 base vs assume-tenant 를 섞지 말 것.
- **erp 가 이미 정렬됐다는 사실이 (b) 를 지지한다** — 접두형 수용이 이미 한 플랫폼에서 프로덕션 패턴이다.

# Failure Scenarios

- **재측정 없이 인계 파일:라인을 믿는다** → 값이 stale 이면 없는 갭에 처방하거나 표적을 놓친다. 완화 = AC-0.
- **self-decision 으로 한쪽을 맞춘다** → 계약/제품 의도 없이 역할 어휘를 바꿔 크로스-플랫폼 회귀. 완화 = AC-1.
- **양성 케이스만 테스트한다** → 매핑이 틀려도 초록(MONO-416 D5-8 거울상). 완화 = AC-3 음성 포함.

---

# Provenance

`TASK-MONO-416`(게이트웨이 role admission) 구현 중 AC-4(역할 발급 소스 재측정)에서 소스로 확인된 별건. 416 의 게이트웨이 admission 은 presence 라 이 불일치를 통과시킨다(장애 없음) — 갭은 **게이트웨이 아래, 서비스 인가 층**이다. `TASK-SCM-BE-029` 가 scm 을 "의도적 미정렬" 로 적었으므로 **결정이 필요**(그 의도가 맞으면 iam 민팅이 dead, 틀리면 서비스가 버그).

분석=Opus 4.8 / 구현 권장=**Opus** (계약 해석 + 크로스-서비스 보안 의미 + 결정 해소).

[[project_gateway_role_admission_gap_2026_07_15]] · [[feedback_recount_population_dont_inherit_scope]] · [[feedback_guard_predicate_wrong_verify_the_artifact]]
