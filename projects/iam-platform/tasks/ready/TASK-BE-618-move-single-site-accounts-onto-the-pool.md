# Task ID

TASK-BE-618

# Status

ready

# Title

전역 소비자 계정 — 기존 **한 사이트 계정**을 **같은 id 로** 풀로 옮긴다 (`ADR-MONO-078` A · 계약 § 3)

# Owner

iam-platform

# Task Tags

- account-service
- auth-service
- data-migration

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (두 DB 에 걸친 이동 — 한 행이라도 남으면 그 행의 조회가 404)

---

# Dependency Markers

- **선행**: `TASK-BE-614`(풀 모델) · `TASK-BE-615`(풀 자격 로그인 — 옮긴 계정이 로그인할 길)
- **분리 출처**: `TASK-BE-614` AC-7 (착수 시 정정 2026-10-01 — 614 의 Goal 과 모순)

# Goal

078 이전에 **한 소비자 사이트에만** 계정이 있는 사람의 계정을 같은 id 로 `consumer-pool` 로 옮기고 그 사이트 멤버십을 만든다. id 가 그대로라 팬·스토어 데이터는 무변경이다. 두 사이트에 계정이 있는 사람은 대상이 아니다(`TASK-MONO-743` 묶기).

# Scope

## In Scope

- 이동 시점 결정: 일괄(내부 배치) / 다음 로그인 때 지연 — 하나를 고르고 근거를 적는다
- 같이 옮길 IAM 행: `accounts` · `profiles` · `account_status_history` · `credentials` · `refresh_tokens` · `social_identities` · `identities`
- 팬 `ARTIST` 역할 → `consumer_site_roles(account, fan-platform, ARTIST)`

## Out of Scope

- 운영자 측면 계정: 셀러(`TASK-MONO-745`) · 셀프 온보딩 운영자(`TASK-MONO-746`)
- 두 사이트 계정(`TASK-MONO-743`)

# Acceptance Criteria

- [ ] **AC-1** — 운영자 측면이 붙은 계정(셀러 · 셀프 온보딩 운영자)은 **제외**된다 — 시험으로.
- [ ] **AC-2** — 팬 `ARTIST` 역할이 `consumer_site_roles` 로 옮겨지고 `artists.account_id` 는 무변경 — 시험으로.
- [ ] **AC-3** — 옮긴 계정이 같은 비밀번호로 원래 사이트에 로그인되고, 그 사이트 데이터(팔로우·주문)가 같은 `sub` 로 보인다.
- [ ] **AC-4** — 🔴 IAM 행 일곱 종류가 **전부** 옮겨진다 — 한 종류라도 남으면 실패하는 시험.
- [ ] **AC-5** — 이동 중 실패가 «반쯤 옮겨진» 계정을 남기지 않는다(재시도 가능 또는 되돌림). 두 DB(account_db · auth_db)라 단일 트랜잭션이 아니다 — 순서와 멱등성으로 보장한다.
- [ ] **AC-6** — 기존 볼륨 시험(Testcontainers). 불가하면 ⚪ «못 쟀다, 이유».

# Related Specs

- `projects/iam-platform/specs/features/multi-tenancy.md` § 소비자 계정 풀 § 3

# Related Contracts

- `projects/iam-platform/specs/contracts/events/account-events.md` (이동은 `account.created` 를 내지 않는다)

# Edge Cases

- 같은 이메일로 다른 사이트에 계정이 생기는 경합(이동 중 가입) — § 2 의 공존 금지로 막힌다.

# Failure Scenarios

1. `credentials` 만 옮기고 `refresh_tokens` 를 남겨 refresh 가 `TOKEN_TENANT_MISMATCH` 로 실패.
2. 셀러를 같이 옮겨 product-service 가 셀러를 놓친다.