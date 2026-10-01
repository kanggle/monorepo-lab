# Task ID

TASK-MONO-742

# Title

전역 소비자 계정 1단계 — **계약·스펙 먼저** (`ADR-MONO-078` A)

# Status

ready

# Owner

monorepo

# Task Tags

- contract
- identity
- docs

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (계약 문장이 이후 여섯 단계의 술어가 된다)

---

# Dependency Markers

- **선행**: `ADR-MONO-078` ACCEPTED — A (2026-10-01) ✅
- **후속**: `TASK-BE-614` · `TASK-BE-615` · `TASK-BE-616` · `TASK-MONO-743` · `TASK-BE-617` · `TASK-MONO-744` — 전부 이 티켓의 계약 문장 위에서 구현한다.

# Goal

`ADR-MONO-078` A(소비자 계정 풀 하나 + 토큰 테넌트는 사이트 기준)를 **구현 전에** 계약과 스펙에 적는다. CLAUDE.md § Layer Rules — 계약 변경은 구현보다 먼저다.

# Scope

## In Scope

- `platform/contracts/jwt-standard-claims.md` — 소비자 클라이언트에서 `sub` = 풀 계정 id(사이트 불문 같음), `tenant_id` = **요청한 client 의 테넌트**. § SSO Scope 가 소비자 둘 사이에서 참이 되는 조건. 운영자(`iam`)는 변경 없음.
- `projects/iam-platform/specs/features/multi-tenancy.md` — «테넌트마다 한 계정»(L177-178 · L283 · L306-317 · L362-376) 과 § SSO(L325-360)를 소비자 둘에 대해 다시 쓴다. 다른 테넌트 규칙은 그대로.
- `projects/iam-platform/specs/contracts/events/account-events.md` — 풀 계정의 `account.created` 에서 `tenantId` 가 무엇인가(`ADR-MONO-078` § 라이더 대조의 미결 둘째).
- `projects/iam-platform/specs/services/auth-service/data-model.md` · account-service 데이터 모델 스펙 — 풀 계정·사이트 멤버십·사이트별 역할의 저장 모양.

## Out of Scope

- 코드, 마이그레이션(`TASK-BE-614` 이후)

# Acceptance Criteria

- [ ] **AC-1** — `jwt-standard-claims.md` 가 소비자 클라이언트의 `sub`·`tenant_id` 의미를 명시하고, **«같은 `sub` 가 client 별로 다른 `tenant_id`»** 가 허용되는 범위를 소비자 둘로 한정한다.
- [ ] **AC-2** — `multi-tenancy.md` 의 소비자 규칙이 새 모델을 말하고, 옛 규칙(테넌트마다 한 계정)이 **어디서부터** 바뀌는지(새 가입 · 묶기 전 기존 계정)를 적는다.
- [ ] **AC-3** — 🔴 **이벤트 결정**: 풀 계정의 `account.created` 가 ① 사이트 테넌트별로 한 번씩(첫 방문 동의 때) 나가는가 ② 풀 단위로 한 번 나가고 사이트는 pull-through(이커머스 `UserProfileProvisioningFilter`)에 맡기는가 — 하나를 고르고 근거를 적는다. 이커머스 user-service `AccountCreatedConsumer` 가 그 이벤트로 프로필을 만든다는 사실(`AccountCreatedConsumer.java:48-63`)과의 정합을 명시한다.
- [ ] **AC-4** — 저장 모양: 풀 계정을 **예약 테넌트 값**으로 둘지 **테넌트 없는 행**으로 둘지 — 하나를 고르고, `accounts`·`credentials`·`account_roles`·`identities` 의 UNIQUE 제약이 어떻게 바뀌는지 표로 적는다.
- [ ] **AC-5** — 역할 발급: 사이트별 역할(FAN·CUSTOMER)이 **요청한 client 의 사이트 역할만** 토큰에 실린다(계약 § Role Strategy «not flattened»). 지금 코드는 저장된 역할을 그대로 싣는다(`TenantClaimTokenCustomizer.java:872-874`) — 바꿔야 할 곳으로 적는다.
- [ ] **AC-6** — 계약 문장마다 그것을 지킬 **시험의 자리**(어느 단계 티켓 · 어느 시험)를 적는다 — 산문에 게이트를 단다.

# Related Specs

- `docs/adr/ADR-MONO-078-one-consumer-login-across-fan-and-store.md`
- `projects/iam-platform/specs/features/multi-tenancy.md`

# Related Contracts

- `platform/contracts/jwt-standard-claims.md`
- `projects/iam-platform/specs/contracts/events/account-events.md`

# Edge Cases

- 같은 사람이 아직 묶지 않은 기존 사이트 계정 둘을 가진 기간 — 계약이 그 상태(옛 규칙)를 **같이** 말해야 한다.
- `*` 와일드카드(SUPER_ADMIN) 토큰은 이 변경 밖이다.

# Failure Scenarios

1. 계약을 «`sub` 는 사람 id» 로만 고치고 `tenant_id` 를 안 적어, 구현이 `tenant_id` 를 풀 값으로 내보낸다 — 기각된 첫 판 A 로 되돌아간다(스토어 테넌트 = 운영 회사와 충돌).
2. 이벤트 결정(AC-3)을 미루고 구현 단계에서 각자 정한다 — 이커머스 프로필 생성이 조용히 끊긴다.
