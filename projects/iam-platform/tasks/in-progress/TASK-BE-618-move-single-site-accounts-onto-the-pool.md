# Task ID

TASK-BE-618

# Status

in-progress

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
   → 🔴 **착수 시 정정: 방향이 반대다.** 아래 «착수 시 정정 ①».
2. 셀러를 같이 옮겨 product-service 가 셀러를 놓친다.

---

# 착수 시 정정 (2026-10-02 UTC, 구현자)

> 티켓 본문(Goal · AC)은 기안 때의 글이다. 아래는 코드를 열어 잰 뒤의 정정이고, 구현은 이 절을 따른다.

**① `refresh_tokens` 는 옮기지 않는다 — 옮기면 그것이 결함이다.** refresh 미러 행의 테넌트는 «계정의 테넌트» 가 아니라
«세션 테넌트 = 그 토큰의 `tenant_id`» 다(`AuthorizationSessionTenant` 클래스 주석 · `TASK-BE-604`). 풀 principal 의 세션 테넌트는
**요청한 사이트**다(`TASK-BE-615`, `multi-tenancy.md` § 4 «refresh» 줄). 그러니 사이트 계정의 기존 refresh 행(`tenant_id` = 그 사이트)은
**이미 목표 모양**이다. `consumer-pool` 로 옮기면 `RefreshTokenUseCase` 가 제출된 토큰의 사이트와 DB 행의 `consumer-pool` 을 비교해
`TOKEN_TENANT_MISMATCH` 를 낸다 — Failure Scenario 1 이 막으려던 바로 그 실패를, 옮겨서 만든다. 블랙리스트 키도 행 테넌트다.
⇒ AC-4 의 «일곱 종류» 는 아래 ③의 표로 바뀐다. 시험은 «옮기지 **않았고**, 이동 뒤 refresh 가 된다» 를 고정한다.

**② 소셜 신원이 있는 계정은 이 단계에서 옮기지 않는다 (`TASK-BE-617` 로 인계).** 지금 소셜 로그인은 `(사이트 테넌트, provider, provider_user_id)`
로 신원을 찾는다. 신원 행을 `consumer-pool` 로 옮기면 그 조회가 비어 «새 소셜 가입» 으로 가고, 그 가입은 풀로 가서 같은 이메일의 (방금 옮긴) 풀 계정에
`409` 로 막힌다 — 그 사람의 소셜 로그인이 끊긴다. 옮기지 않고 계정만 옮겨도 신원 행 테넌트(사이트)와 계정 테넌트(풀)가 갈린다.
617 의 AC-3 이 «기존 테넌트별 소셜 신원은 그대로 동작한다» 를 이미 요구하므로, 소셜 신원이 있는 계정은 건너뛰고(`SOCIAL_LINKED`) 617 이 소셜 조회를
풀에 맞춘 뒤 같은 이동기로 옮긴다.

**③ 옮기는 행 (AC-4 의 대상, 정정본)**

| DB | 행 | 이동 | 근거 |
|---|---|---|---|
| account_db | `accounts` | `tenant_id` → `consumer-pool` | § 1 |
| account_db | `profiles` | 〃 | 읽기는 `account_id` 로만 하지만 계정과 같은 값을 둔다 |
| account_db | `account_status_history` | 〃 | 〃 |
| account_db | `identities` (그 계정의 `identity_id` 행) | 〃 | 풀 가입은 `(consumer-pool, email)` 로 신원을 만든다 — 같은 모양. 같은 `identity_id` 라 `credentials.identity_id` 는 무변경 |
| account_db | `account_roles` (그 사이트) | → `consumer_site_roles(account, site, role)` 후 원래 행 삭제 | § 1 · 복합 FK 때문에 계정 테넌트 변경 **전에** 지워야 한다 |
| account_db | `consumer_site_memberships` | **신설** `(account, site, ACTIVE, consented_at = 계정 생성 시각)` | 가입 = 그 사이트 동의(§ 2) |
| auth_db | `credentials` | `tenant_id` → `consumer-pool` | § 1 · 폼 로그인의 풀-먼저 |
| auth_db | `refresh_tokens` | **옮기지 않는다** | ① |
| auth_db | `social_identities` | **옮기지 않는다** — 있으면 계정째 건너뜀 | ② |
| auth_db | `oauth2_authorization`(SAS 세션) | 옮기지 않는다 | 이동 전 세션의 principal 은 사이트 principal 로 남는다 → ⑤ |

**④ 운영자 측면 판정.** 셀러 = 그 사이트에 저장된 `SELLER` 역할(account-service 가 직접 안다). 셀프 온보딩 운영자 = `admin_operators.oidc_subject`
가 이 계정 id — admin_db 에 있다. 거기에 더해 **운영자 신원 연결**(`LinkOperatorIdentityUseCase`, ADR-MONO-034 U3 — `admin_operators.identity_id` 가 이
계정의 신원)도 운영자 측면이다: 신원 행을 풀로 옮기면 그 운영자의 신원이 풀 신원이 된다. account-service 는 admin-service 를 부르지 않는다(반대 방향
의존이 이미 있어 순환). auth-service 는 이미 admin-service 를 부른다(`auth-to-admin.md`) — 그래서 판정은 auth-service 의 이동 엔드포인트가 admin-service
에 묻는다(**fail-closed**: 못 물으면 옮기지 않는다).

**⑤ 이동 전 세션(이미 로그인해 있던 사람).** 그 세션의 principal 은 사이트 principal 이라 refresh 때 `populateRoles(사이트, id)` 가
`GET /internal/tenants/{site}/accounts/{id}/roles` 를 부른다. 이동 뒤 그 사이트의 `account_roles` 는 비었으므로 시드만 실려 `ARTIST` 가 사라진다.
⇒ 그 조회를 § 5(사이트 조회는 그 사이트의 ACTIVE 풀 멤버를 포함)에 맞춰 넓힌다: 사이트 테넌트로 물었는데 그 계정이 그 사이트의 ACTIVE 풀 멤버면
`consumer_site_roles(account, site)` 를 답한다. 이동 전과 같은 역할 집합이 같은 «저장 역할만» 규칙으로 실린다.

# 결정 — 이동 시점: **일괄**(재실행 가능한 내부 유지보수 엔드포인트)

| 후보 | 장점 | 단점 |
|---|---|---|
| **일괄** ✅ | 결정적 · 시험 가능 · 로그인 경로 무변경 · 휴면 계정도 옮겨져 § 2 의 «사이트 계정 이메일 거절» 이 빨리 줄어든다 | 실행 주체가 필요(데모는 `TASK-MONO-744`) |
| 다음 로그인 때 | 활동 계정만 | 로그인 한 번에 세 서비스 쓰기가 끼어든다(지연 · 실패가 로그인 실패) · 휴면 계정은 영영 사이트 계정 · 운영자 판정이 로그인 경로에 들어간다 |

- 🔴 **재실행 가능해야 한다.** 내부 프로비저닝은 080 전까지 사이트 테넌트에 계정을 만든다(§ 3 마지막 문단) — 한 번 돌려도 새 사이트 계정이 계속 생긴다.
- 실행 주체: `POST /internal/consumer-pool/legacy-moves`(account-service). 데모 실행은 `TASK-MONO-744` 의 몫이다(재굽기와 함께).

# 결정 — 순서와 멱등성 (AC-5)

한 계정 = account-service 트랜잭션 하나 안에서: 계정 행 잠금 → 자격 검사 → 역할 이동·멤버십 생성 → 테넌트 값 변경 → **auth-service 이동 호출**
→ 커밋. auth 호출이 거절·실패하면 예외로 account 트랜잭션이 되돌아가 **아무것도 옮겨지지 않는다**(가입 `SignupUseCase` 와 같은 모양 — 원격 쓰기가
트랜잭션 안의 마지막 단계). 남는 창은 하나 — auth 커밋 뒤 account 커밋이 실패한 경우(자격만 풀). 그 계정은 여전히 사이트 계정이라 다음 실행의
후보로 다시 잡히고, auth 이동은 «이미 풀이면 성공» 이라 그대로 완결된다. 그 창 동안 폼 로그인은 풀 자격 → 풀 principal → 멤버십 없음 → 동의 화면이며,
동의는 풀 계정이 아니라 쓰지 않고 «멤버 아님» 으로 답한다(`ConsentToConsumerSiteUseCase`) — 그 사람은 재실행 전까지 들어가지 못한다(토큰 없음,
남의 계정에 붙는 일 없음). 이 창은 «auth 커밋 성공 + account 커밋 실패» 라는 드문 경우에만 열리고, 실행 보고서의 `failed` 로 드러난다.