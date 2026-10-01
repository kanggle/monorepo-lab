# Task ID

TASK-MONO-745

# Title

전역 소비자 계정 — **셀러를 풀로** 옮긴다: product-service 셀러 조회·상태 이벤트를 계정 id 기준으로, 그 뒤 셀러 계정 이동 (`ADR-MONO-078` 후속 결정)

# Status

ready

# Owner

monorepo

# Task Tags

- identity
- ecommerce
- data-migration

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (두 프로젝트에 걸친 계정 이동 — 셀러를 놓치면 셀러 정지가 조용히 안 먹는다)

---

# Dependency Markers

- **선행**: `TASK-MONO-742`(계약) ✅ · `TASK-BE-614`(풀 모델) · `TASK-BE-615`(인증 흐름 · 역할 합치기 · AC-7 «고치기 전» 대조군)
- **관계**: `ADR-MONO-079`(소속사·셀러를 조직에 연결)와 **독립**이다 — 이 티켓은 셀러 계정의 id 를 바꾸지 않으므로 `Seller.accountId` 의 모양을 건드리지 않는다. 079 가 셀러 모델을 바꾸면 그 위에서 그대로 동작해야 한다.

# Goal

소유자 결정(2026-10-01 UTC, «셀러를 풀에 포함»)을 구현한다. 셀러 계정을 소비자 계정 풀로 **같은 id 로** 옮겨, 셀러가 같은 계정으로 쇼핑하고(스토어 토큰 `["CUSTOMER","SELLER"]`) 팬도 쓰게 한다. `ADR-MONO-042` D5 가 의도한 «쇼핑객이면서 셀러인 사람 = 한 신원» 을 실제로 성립시킨다.

# Scope

## In Scope

- product-service: 셀러 조회와 `AccountStatusChangedSellerConsumer` 가 **계정 테넌트가 `ecommerce` 라는 가정 없이** 셀러를 찾게 한다(셀러의 테넌트는 셀러 행이, 계정의 테넌트는 IAM 이 갖는다 — 둘을 같은 값으로 읽지 않는다)
- 셀러 정지 경로(`PATCH /internal/tenants/{t}/accounts/{accountId}/status`)가 풀 계정에 대해 동작
- 셀러 계정 이동: `tenant_id` → `consumer-pool`, `account_roles(ecommerce, acct, SELLER)` → `consumer_site_roles(acct, ecommerce, SELLER)`, 스토어 멤버십 생성
- 셀러 생성 경로(`AccountServiceSellerProvisioner`)의 새 셀러: 그 이메일의 풀 계정이 있으면 거기에 `SELLER` 사이트 역할을, 없으면 풀 계정을 만들어서

## Out of Scope

- 셀러를 조직(회사)에 연결 · 셀러 한 곳에 계정 여럿 — `ADR-MONO-079`
- 셀프 온보딩 운영자 — `TASK-MONO-746`(ADR-MONO-080 후보)

# Acceptance Criteria

- [ ] **AC-1** — 🔴 **순서**: product-service 수정이 **먼저** 머지되고, 셀러 계정 이동은 그 뒤다. 거꾸로 하면 이동한 셀러의 정지 이벤트(`tenantId=consumer-pool`)를 셀러 소비자가 놓친다.
- [ ] **AC-2** — 이동한 셀러의 스토어 토큰 역할이 `["CUSTOMER","SELLER"]` 이고 web-store 로그인이 통과한다. `TASK-BE-615` AC-7 의 «고치기 전» 결과(`["SELLER"]` → `account_type_mismatch`)와 **같은 시험 모양**으로 대조한다.
- [ ] **AC-3** — 🔴 **대조군**: 이동한 셀러를 정지하면 셀러가 정지되고(셀러 상태 · 상품 노출), 정지하지 않은 다른 셀러는 영향이 없다.
- [ ] **AC-4** — 셀러 범위(`X-Seller-Scope`)가 이동 전과 같다 — 셀러는 여전히 자기 상품·주문만 본다.
- [ ] **AC-5** — 이동한 셀러가 같은 계정으로 팬 첫 방문 동의 → 팬 토큰에 `SELLER` 가 **없다**(역할 평탄화 금지).
- [ ] **AC-6** — `TASK-BE-614` AC-6 의 임시 거절(셀러 이메일의 풀 가입)이 셀러 이동 뒤에는 필요 없어진다 — 이동 완료 셀러 이메일로의 풀 가입은 «이미 계정 있음» 으로 정상 처리된다.
- [ ] **AC-7** — 이동은 **기존 볼륨**에서 시험된다(Testcontainers 로 이동 전 상태를 만든 뒤 이동). 불가하면 ⚪ «못 쟀다, 이유».

# Related Specs

- `projects/iam-platform/specs/features/multi-tenancy.md` § 소비자 계정 풀 § 3 · § 4
- `docs/adr/ADR-MONO-042-*.md` D5 · D6
- `docs/adr/ADR-MONO-078-one-consumer-login-across-fan-and-store.md` § 소유자 후속 결정

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` § Role Strategy(사이트 역할 합치기)
- `projects/iam-platform/specs/contracts/events/account-events.md` § account.status.changed

# Edge Cases

- 셀러이면서 이미 같은 이메일의 풀 계정(쇼핑객)이 있는 사람 — 두 계정을 **본인 확인으로** 묶는 `TASK-MONO-743` 경로를 탄다(이메일만으로 합치지 않는다).
- 셀러 생성이 실패해 `PENDING_PROVISIONING` 으로 남은 셀러(`ADR-MONO-042` D3 fail-soft) — 재시도가 풀 규칙으로 동작한다.

# Failure Scenarios

1. 셀러 계정을 먼저 옮기고 product-service 를 나중에 고친다 — 그 사이 셀러 정지가 조용히 안 먹는다(«성공» 응답, 셀러는 그대로 판매).
2. 셀러 사이트 역할을 `account_roles` 에 그대로 두어 풀 계정의 복합 FK 가 깨진다.
