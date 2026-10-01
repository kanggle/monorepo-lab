# Task ID

TASK-MONO-742

# Title

전역 소비자 계정 1단계 — **계약·스펙 먼저** (`ADR-MONO-078` A)

# Status

done (2026-10-01 UTC — AC-1~AC-6 닫힘 · PR #4086 squash `b08b25d21`)

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

- [x] **AC-1** — `jwt-standard-claims.md` 가 소비자 클라이언트의 `sub`·`tenant_id` 의미를 명시하고, **«같은 `sub` 가 client 별로 다른 `tenant_id`»** 가 허용되는 범위를 소비자 둘로 한정한다.
- [x] **AC-2** — `multi-tenancy.md` 의 소비자 규칙이 새 모델을 말하고, 옛 규칙(테넌트마다 한 계정)이 **어디서부터** 바뀌는지(새 가입 · 묶기 전 기존 계정)를 적는다.
- [x] **AC-3** — 🔴 **이벤트 결정**: 풀 계정의 `account.created` 가 ① 사이트 테넌트별로 한 번씩(첫 방문 동의 때) 나가는가 ② 풀 단위로 한 번 나가고 사이트는 pull-through(이커머스 `UserProfileProvisioningFilter`)에 맡기는가 — 하나를 고르고 근거를 적는다. 이커머스 user-service `AccountCreatedConsumer` 가 그 이벤트로 프로필을 만든다는 사실(`AccountCreatedConsumer.java:48-63`)과의 정합을 명시한다.
- [x] **AC-4** — 저장 모양: 풀 계정을 **예약 테넌트 값**으로 둘지 **테넌트 없는 행**으로 둘지 — 하나를 고르고, `accounts`·`credentials`·`account_roles`·`identities` 의 UNIQUE 제약이 어떻게 바뀌는지 표로 적는다.
- [x] **AC-5** — 역할 발급: 사이트별 역할(FAN·CUSTOMER)이 **요청한 client 의 사이트 역할만** 토큰에 실린다(계약 § Role Strategy «not flattened»). 지금 코드는 저장된 역할을 그대로 싣는다(`TenantClaimTokenCustomizer.java:872-874`) — 바꿔야 할 곳으로 적는다.
- [x] **AC-6** — 계약 문장마다 그것을 지킬 **시험의 자리**(어느 단계 티켓 · 어느 시험)를 적는다 — 산문에 게이트를 단다.

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

---

# 구현 기록 (2026-10-01 UTC · 분석=Opus 5.5 · 구현=Opus 5.5)

계약·스펙만 바꿨다. 코드·마이그레이션 0.

| 파일 | 무엇 |
|---|---|
| `platform/contracts/jwt-standard-claims.md` | § Identity Model 소비자 풀 항목 · `sub` 행(소비자 사이트끼리 같음) · `tenant_id` 행(요청 client 의 사이트, 풀 값은 토큰에 없음) · § Role Strategy(사이트 역할만, 미가입 사이트엔 토큰 없음) · § SSO Scope(소비자 사이트 사이 성립 조건과 사전 계정 예외, 콘솔 불변) · § Migration Compatibility «Account unify — done» 정정 · Change log |
| `projects/iam-platform/specs/features/multi-tenancy.md` | 신설 § 소비자 계정 풀(저장 · 가입 · 기존 계정 · 로그인/토큰 · 사이트로 찾는 표면 · 이벤트 · 시험 자리) + 적용 범위·소셜 절에 안내 |
| `projects/iam-platform/specs/contracts/events/account-events.md` | `account.created` 사이트별 1회 · `account.status.changed` 풀 1회 |
| `projects/iam-platform/specs/services/account-service/data-model.md` | 신설 `consumer_site_memberships` · `consumer_site_roles` |
| `projects/iam-platform/specs/services/auth-service/data-model.md` | 풀 자격 행 · 로그인 선택 순서 · refresh 미러 테넌트 |

## 결정 (AC 별)

- **AC-1** ✅ 소비자 `sub` = 풀 계정 id(사이트 불문), `tenant_id` = 요청 client 의 사이트. «같은 `sub` · client 별 다른 `tenant_id`» 는 소비자 사이트 사이로 한정(콘솔 · 소비자 아닌 테넌트 불변).
- **AC-2** ✅ 옛 규칙은 그대로 «지금 동작» 으로 남기고, 바뀌는 지점(새 가입 · 한 사이트 기존 계정의 같은 id 이동 · 두 사이트 기존 계정은 묶을 때까지 옛 규칙)을 적었다.
- **AC-3** ✅ 🔴 **이벤트 = 사이트별 1회**(풀 가입 시 가입 사이트, 첫 방문 동의 시 그 사이트). 풀 단위 1회 안은 기각 — `tenantId=consumer-pool` 이 이커머스 프로필을 엉뚱한 테넌트에 만든다. 상태 이벤트는 계정 하나의 일이라 `consumer-pool` 로 1회.
- **AC-4** ✅ 🔴 **저장 = 예약 테넌트 `consumer-pool` + 신설 멤버십·사이트 역할 테이블.** 기존 UNIQUE·FK 무변경. «테넌트 없는 행(NULL)» 은 NOT NULL 컬럼이고 MySQL UNIQUE 가 NULL 을 중복 검사하지 않아 기각. `account_roles` 에 사이트 역할을 두는 안은 복합 FK `(tenant_id, account_id) → accounts(tenant_id, id)` 가 깨져 기각.
- **AC-5** ✅ 토큰 역할 = 사이트 시드 ∪ `consumer_site_roles(account, 사이트)`. 지금 발급 경로가 저장 역할을 그대로 싣는 자리(`TenantClaimTokenCustomizer.java:872-874`)를 `TASK-BE-615` 의 일로 적었다.
- **AC-6** ✅ `multi-tenancy.md` § 소비자 계정 풀 § 7 — 계약 문장 9개와 각 시험 자리.

## 기안 중에 드러난 것 — 단계 티켓에 반영

- 🔴 **같은 이메일 공존 금지**: 사이트별 계정이 있는 이메일로 풀 가입을 받으면, 남이 그 이메일로 풀 계정을 만들 때 원래 주인이 자기 계정에 못 들어간다(로그인 폼은 한 자격만 검사). ⇒ 풀 가입 거절 + «로그인 후 전환» (§ 2). 시험 자리 `TASK-BE-614`.
- 🔴 **한 사이트 기존 계정은 같은 id 로 이동** — 데이터 이전이 필요 없다. id 이전은 두 사이트에 계정이 있어 묶는 사람(`TASK-MONO-743`)에게만 남는다. ADR-MONO-078 이 A 의 대가로 적은 «id 이전» 의 모집단이 그만큼 줄었다.
- 🔴 **운영자 측면 계정은 이동 제외** — 셀러(ADR-MONO-042)·셀프 온보딩 운영자(ADR-MONO-044 D5)를 옮기면 `(ecommerce, 계정)` 셀러 조회와 상태 이벤트 소비가 놓친다. 그런 이메일의 풀 가입 처리는 ⚪ 미결 → `TASK-BE-614` AC-6 · 이동 제외 시험 → AC-7.
- 🔴 **사이트로 계정을 찾는 표면**(내부 목록 · 콘솔 계정 운영)은 그 사이트 멤버 풀 계정을 포함해야 한다 — 안 그러면 운영자가 `ecommerce` 로 전환했을 때 새 쇼핑객이 사라진다. `TASK-BE-614`.
- 계약이 **처음부터 A 모양**이었다: 예시 1(스토어)·2(팬) 토큰이 같은 `sub` 를 썼다. 반면 § Migration Compatibility 는 «Account unify — done» 이라 적어 소비자 사이트 사이에서 거짓이었다 — 정정했다.

## 게이트

| 게이트 | rc |
|---|---|
| `check-jwt-claims-registry.sh` | 0 — «all 6 claims … registered» (새 클레임 없음) |
| `check-task-id-collision.sh` · `check-walkthrough-ledger-drift.sh` · `check-adr-index-drift.sh` | 0 |
| `check-index-queue-drift.sh` | 0 — INDEX 를 review 로 옮긴 뒤(옮기기 전 1 은 in-progress ↔ ready 행 불일치, 예상된 값) |
