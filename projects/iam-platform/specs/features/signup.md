# Feature: Signup (회원가입)

## Purpose

새 사용자가 플랫폼에 계정을 등록하는 흐름. 계정 생성, 프로필 초기화, credential 저장, 이벤트 발행까지의 end-to-end.

## Related Services

| Service | Role |
|---|---|
| account-service | 계정·프로필 생성 소유. 이메일 중복 검사, 상태 ACTIVE 초기화 |
| auth-service | credential(패스워드 해시) 저장. 가입 후 즉시 로그인 가능 |
| gateway-service | `POST /api/accounts/signup` 라우팅, rate limit |

## User Flow

1. 사용자가 `POST /api/accounts/signup` 에 이메일·패스워드·(선택)프로필 정보 전송
2. gateway가 가입 rate limit 검사 (IP당 5회/분)
3. account-service가 이메일 중복 검사 (`accounts.email` unique index)
4. 중복 시 409 `ACCOUNT_ALREADY_EXISTS`
5. 패스워드 복잡도 검증 (PasswordPolicy: 최소 8자, 대소문자+숫자+특수문자 중 3종 이상)
6. `accounts` row 생성 (status=ACTIVE) + `profiles` row 생성
7. auth-service에 credential 생성 요청: 패스워드를 argon2id 해시 후 `credentials` 저장
8. `account.created` 이벤트 발행 (outbox)
9. 응답 201: `{ accountId, email, status, createdAt }`
10. (선택, 미래) 이메일 검증 코드 발송 → Redis `signup:email-verify:{token}` TTL 24h

**credential 생성의 서비스 간 조율**:
- 방법 A (동기): account-service가 내부 HTTP로 auth-service에 credential 생성 요청. 실패 시 계정 생성도 롤백.
- 방법 B (Saga): account.created 이벤트 발행 → auth-service가 소비하여 credential 생성. 실패 시 보상 트랜잭션.
- **초기 구현: 방법 A** (단순, 골든패스 우선). Saga는 백로그.

## Business Rules

- 이메일: RFC 5322 형식, 대소문자 무시 (저장 시 lowercase 정규화)
- 패스워드: 최소 8자, 3종 이상 조합. auth-service의 `PasswordPolicy` 도메인 객체가 검증
- 중복 가입 방어: `signup:dedup:{email_hash}` Redis 5분 TTL (리로드 공격) + DB unique constraint (최종 방어)
- 가입 직후 상태: `ACTIVE`
- 이메일 검증: 초기 스코프에서 선택사항 (검증 없이 가입 완료 가능). 검증 필수화는 백로그

## 브라우저 회원가입 화면의 제시 조건 (TASK-BE-581)

SAS 브라우저 화면(`/login`, `/signup`)은 **모든 OIDC 클라이언트가 공유**한다. 계정이 태어날
테넌트는 그 흐름을 시작한 클라이언트의 `oauth_clients.tenant_id` 로 결정된다
(`SavedRequestTenantResolver`, TASK-BE-507). 따라서 화면 하나가 **가입이 가능한 테넌트와
불가능한 테넌트 양쪽에** 동시에 노출된다.

- **제시 조건**: 해석된 테넌트에 대해 account-service 의 `ActiveTenantGuard` 가 통과할 때에만
  회원가입을 제시한다 — 즉 `tenants` 행이 **존재**하고 **`status=ACTIVE`** 일 때.
  - 조건 불충족 → `/login` 의 회원가입 링크를 렌더하지 않고, `/signup` 은 폼 없이 **영구적**
    사유를 표시하며 account-service 를 호출하지 않는다.
  - 판정 근거는 예약어 목록이 아니라 **테넌트 레코드 자체**다. 목록은 "우리가 생각해 둔 값인가"
    라는 다른 질문에 답하며, 정지(`SUSPENDED`) 테넌트 칸을 통째로 놓친다.
- **예약 슬러그는 구조적으로 가입 불가**다. `iam` 은 콘솔 자신의 운영 슬러그이며
  ([multi-tenancy.md](multi-tenancy.md) 예약어, `V0024`), `tenants` 행이 없는 것이 **정상**이고
  앞으로도 생기지 않는다. 콘솔 경로의 회원가입은 일시적 실패가 아니라 **구조적 불가능**이었다.
- **콘솔 운영자는 셀프 가입 대상이 아니다.** 운영자는 `admin_operators` 에 살고 SUPER_ADMIN 이
  `POST /api/admin/operators` 로 생성한다([operator-management.md](operator-management.md)).
  OIDC 로그인 후 admin-service 토큰 교환이 `sub`(account_id) → `admin_operators` 를 해석하며
  미매핑이면 `401 TOKEN_INVALID` 로 fail-closed 한다
  ([admin-service security.md](../services/admin-service/security.md)). 그러므로 `iam` 테넌트를
  실재시켜 가입을 201 로 만들어도 **콘솔에 로그인할 수는 없다** — 실패 지점이 가입에서 로그인
  루프로 옮겨갈 뿐이다.
- **가용성 정책**: account-service 가 응답하지 못하면 **제시한다**(fail-open). 이 판정은 UX
  게이트이며 권한 경계가 아니다 — 권위는 `ActiveTenantGuard` 다. 닫는 쪽으로 실패시키면
  account-service 장애 동안 **모든 소비자 화면**에서 회원가입이 사라져 TASK-BE-470 을 조용히
  되돌린다.

### 실패의 종류를 구별해 보고한다 (TASK-BE-580)

브라우저 가입 프록시(`auth-service`)는 account-service 의 4xx 를 **재시도가 의미 있는 것**과
**재시도가 무의미한 것**으로 가른다. 🔴 **판별자는 상태 코드가 아니라 본문 `code` 다** — 같은
`409` 가 정반대 두 가지를 뜻하기 때문이다.

| account-service | `code` | 사용자에게 |
|---|---|---|
| `409` | `ACCOUNT_ALREADY_EXISTS` | 이미 가입됨 — 로그인 안내 |
| **`409`** | **`TENANT_SUSPENDED`** | **재시도 권하지 않음** — 관리자 문의 |
| **`404`** | **`TENANT_NOT_FOUND`** | **재시도 권하지 않음** — 관리자 문의 |
| `400` / `422` | — | 입력값 안내 (BE-472) |
| `429` | `RATE_LIMITED` | *"잠시 후 다시"* — 진짜 일시적이다 |
| 5xx / 연결 실패 / 타임아웃 | — | *"잠시 후 다시"* |

- 🔴 **분류 불가는 영구가 아니다.** 본문이 비었거나 JSON 이 아니거나 처음 보는 `code` 면 판정할
  수 없고, 판정 불가의 안전한 쪽은 **기존 동작(일시적)** 이다. 영구라고 잘못 말하면 될 일을
  포기하라고 안내하게 된다. 그래서 영구 목록은 **상태 규칙이 아니라 `code` allowlist** 다.
- 🔴 `429` 를 영구로 넘기지 마라 — rate limit 에 걸린 사용자에게 *"다시 시도하지 마세요"* 가 된다.
- 로그에는 본문의 `code` 만 남긴다(전체 본문 금지 — 지금은 PII 가 없지만 계약이 넓어지면 샌다).

## Edge Cases

- 동시 중복 가입 → 두 번째 요청이 DB unique constraint에 걸림 → 409
- 패스워드가 이메일과 동일 → 422 VALIDATION_ERROR (PasswordPolicy에서 거부)
- 한글 이메일 도메인 (IDN) → 초기 스코프 미지원. ASCII만

## Related Contracts

- HTTP: [account-api.md](../contracts/http/account-api.md) `POST /api/accounts/signup`
- Events: [account-events.md](../contracts/events/account-events.md) `account.created`
