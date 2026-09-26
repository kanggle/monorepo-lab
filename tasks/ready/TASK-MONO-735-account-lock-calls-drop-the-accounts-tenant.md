# Task ID

TASK-MONO-735

# Status

ready

# Title

계정 잠금 호출이 **계정의 테넌트를 싣지 않는다** — `fan-platform` 밖의 계정은 자동 잠금 · SUPER_ADMIN 잠금 · 셀러 정지 어느 것으로도 잠기지 않는다

# Owner

monorepo (iam-platform · ecommerce-microservices-platform — 호출처가 두 프로젝트에 걸친다)

# Task Tags

- security-service
- admin-service
- account-service
- product-service
- security
- multi-tenant

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus — 고치는 자리가 «호출자가 테넌트를 넘긴다» 와 «account-service 가 계정에서 테넌트를 푼다» 로 갈리는 설계 결정이고, 세 호출처 · 내부 계약 · 감사 경로가 얽힌다.

---

# Goal

2026-09-26 16차 AMI 데모 창(`TASK-MONO-672` § 2026-09-26 16차 창)에서 **실측**:

| 경로 | 대상 계정 | 결과 |
|---|---|---|
| security-service 자동 잠금 (`TokenReuseRule` AUTO_LOCK) | `fan-platform` 일회용 | **LOCKED (~2s)** 🟢 |
| 같은 경로 | `ecommerce` 일회용(`419f99a1-…`, 스토어 소셜 가입) | **`Auto-lock non-retryable 4xx: status=404`** → ACTIVE 🔴 |
| 콘솔 운영자 잠금 (`demo@demo.com` = SUPER_ADMIN, 테넌트 `ecommerce` 화면) | 같은 계정 | account-service **404** → 재시도가 같은 멱등 키로 감사 행 중복 → **500** 「하위 서비스 호출에 실패했습니다」 🔴 |

원인(코드):
- security-service `infrastructure/client/AccountServiceClient.lock` — `POST /internal/accounts/{id}/lock` 에 **`X-Tenant-Id` 없음**.
- admin-service `infrastructure/client/AccountServiceClient.lock` — 운영자의 활성 테넌트를 넣되 `"*"`/null 이면 넣지 않는다(주석: «account-service FAN default (net-zero)») ⇒ SUPER_ADMIN 은 항상 헤더 없음.
- ⚪ **미측정 · 코드 판독**: ecommerce product-service `AccountServiceSellerProvisioner.lockAccount`(셀러 정지 → 계정 잠금, `TASK-MONO-672` 항목 14 ②)도 헤더 없음 — 주석은 «경로에 테넌트가 없으니 테넌트 규칙이 적용되지 않는다» 고 적지만, 라이브의 account-service 는 헤더 없음을 `fan-platform` 으로 읽었다. 셀러는 `ecommerce` 계정이다 ⇒ 같은 모양으로 실패할 것으로 **추정**(시드 셀러를 정지시켜야 재므로 창에서 재지 않았다).

⇒ 스토어 고객 · 셀러 · 그 밖의 비-fan 테넌트 계정은 **반복된 토큰 재사용에도, 운영자가 잠가도 잠기지 않는다.** 자동 잠금 판정들(`TASK-MONO-727` ② · `TASK-BE-600` · `TASK-BE-601` AC-3)은 전부 `fan-platform` 계정으로 재서 이것을 못 봤다.

# Scope

## In Scope

- **AC-0 (🔴 소유자 결정)**: 고치는 자리.
  - (a) 호출자가 계정의 테넌트를 넘긴다 — security-service 는 이벤트의 `tenantId` 를 이미 들고 있다. admin-service 는 SUPER_ADMIN 일 때 **대상 계정의** 테넌트가 필요하다(운영자 테넌트가 아니다). product-service 는 `TenantContext.currentTenant()` 를 이미 받는다.
  - (b) account-service `/lock`(`/unlock` · `/delete` 형제 포함)이 헤더 없을 때 계정에서 테넌트를 푼다 — `TASK-BE-602` 의 `findByIdResolvingTenant`(«tenant 없는 findById 금지» 의 문서화된 예외)와 같은 모양. 헤더가 있으면 지금처럼 그 테넌트로 한정(교차 테넌트 404 유지).
  - (c) 둘 다.
- 결정대로 구현 + 내부 계약(`specs/contracts/http/internal/…` 의 lock 절) 먼저 갱신.
- admin-service 재시도가 **같은 멱등 키로 감사 행을 다시 쓰려다** 중복 키로 500 이 되는 것 — 404(비재시도 대상)가 500 으로 둔갑한다. 같은 티켓에서 고치거나 이름을 남겨 분리.

## Out of Scope

- 잠금 해제 경로 설계(`TASK-BE-608` AC-3 소유자 결정 대기).

# Acceptance Criteria

- [ ] **AC-0** — 위 (a)/(b)/(c) 소유자 결정 + 세 호출처 각각이 결정 뒤 어떤 테넌트를 싣는지 표.
- [ ] **AC-1** — 구현 + 단위/IT: 비-fan 테넌트 계정의 자동 잠금 · SUPER_ADMIN 잠금 · 셀러 정지 → account-service 200. 대조군: 다른 테넌트로 한정한 호출은 여전히 404(교차 테넌트 격리 유지).
- [ ] **AC-2** — admin-service: 비재시도 4xx 가 감사 중복 키 500 으로 바뀌지 않는다(테스트로 핀).
- [ ] **AC-3** — 🔴 **결과로 판정**(창): `ecommerce` 일회용 계정 — 합성 재사용 2건 → `accounts.status=LOCKED` · 콘솔 잠금 200 → LOCKED. 이어서 `TASK-BE-602` AC-3 런북(스토어 소셜 계정 잠금 → 소셜 로그인 `account_unavailable`)을 같은 창에서 닫는다. 셀러 정지는 일회용 셀러로(시드 셀러를 건드리지 마라).

# Related Specs

- `projects/iam-platform/specs/features/multi-tenancy.md` § 격리 회귀 방지 · `TASK-BE-602` 의 문서화된 예외
- `projects/iam-platform/specs/contracts/http/internal/` — account-service lock/unlock
- `TASK-BE-606`(1시간 2회 = AUTO_LOCK) · `TASK-BE-608` AC-3(해제 경로)

# Related Contracts

- account-service `POST /internal/accounts/{id}/lock|unlock|delete` 의 `X-Tenant-Id` 해석(additive 로만).

# Edge Cases

| 상황 | 기대 |
|---|---|
| 같은 이메일이 테넌트마다 다른 계정(`demo@demo.com` 셋) | 잠금은 **그 계정 id 하나**만 — 다른 테넌트 계정은 영향 없음 |
| 헤더가 있고 계정이 다른 테넌트 | 404 유지(격리) |

# Failure Scenarios

1. **fan-platform 계정으로만 재고 닫는다** → 이 결함이 그렇게 숨었다. AC-3 은 반드시 비-fan 계정.
2. **admin-service 에 운영자 테넌트를 넣는다** → SUPER_ADMIN(`"*"`)은 여전히 못 잠근다. 대상 계정의 테넌트다.
3. **(b) 를 헤더 유무와 무관하게 적용** → 교차 테넌트 격리가 풀린다.
