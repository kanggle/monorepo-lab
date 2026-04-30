# account-service — Overview

## Purpose

계정 생명주기 및 프로필 데이터 전담 서비스. 회원가입, 프로필 CRUD, 그리고 계정 상태(`ACTIVE` / `LOCKED` / `DORMANT` / `DELETED`)의 **상태 기계 소유자**. Global Account Platform에서 "이 사용자가 현재 어떤 상태인가?"에 대한 유일한 진실 소스.

[rules/domains/saas.md](../../../rules/domains/saas.md) S1에 따라 credentials(auth-service)과 물리적으로 분리된다. profile 데이터에는 비밀·토큰·해시가 포함되지 않는다.

## Callers

### 공개 경로 (gateway 경유)
- **외부 사용자** — `POST /api/accounts/signup`, `GET/PATCH /api/accounts/me`, `GET /api/accounts/me/status`

### 내부 경로 (gateway 우회)
- **auth-service** — credential lookup, 로그인 시 계정 상태 조회
- **security-service** — 자동 잠금 명령 (비정상 로그인 탐지 결과)
- **admin-service** — 운영자 lock/unlock/delete 명령

## Callees

| 대상 | 목적 | 경로 |
|---|---|---|
| MySQL (`account_db`) | accounts, profiles, account_status_history, outbox_events | 직접 JPA |
| Kafka | outbox relay → `account.*` 이벤트 발행 | producer |
| Redis | 이메일 검증 코드 TTL, 가입 중복 요청 dedup | 직접 접속 |

다른 서비스를 직접 호출하지 않음 — 이벤트로만 다운스트림 통지 (auth가 세션 무효화, security가 이력 갱신 등은 이벤트 구독으로 수행).

## Owned State

### MySQL
- `accounts` — `id`, `email` (unique), `created_at`, `version`, 도메인 식별자만 포함
- `profiles` — `account_id`, `display_name`, `phone_number`, `birth_date`, `locale`, `timezone`, `preferences` (JSON). PII 마스킹 대상 필드를 명시적으로 라벨링 ([rules/traits/regulated.md](../../../rules/traits/regulated.md) R1·R4)
- `account_status_history` — **append-only**. `account_id`, `from_status`, `to_status`, `reason_code`, `actor_type`, `actor_id`, `occurred_at`, `details`
- `outbox_events` — `account.*` 이벤트 스테이징

### Redis
- `signup:email-verify:{token}` — 이메일 검증 코드, TTL 24시간
- `signup:dedup:{email_hash}` — 짧은 중복 가입 요청 차단 (리로드 공격), TTL 5분

## Change Drivers

1. **새 프로필 필드 추가** — display name 규칙, locale, preferences 구조
2. **새 계정 상태 전이 규칙** — 신규 상태 추가 (예: `SUSPENDED`, `PENDING_VERIFICATION`), 허용 전이 조정
3. **규제 요구 변화** — GDPR/PIPA의 삭제 유예 기간 변경, PII 보존 규칙, 익명화 경로 강화
4. **이메일/전화 검증 프로세스** — 검증 방식 변경, 재발송 정책
5. **휴면 정책** — `DORMANT` 전이 기준 (미접속 일수), 복구 경로
6. **데이터 이식성 (R8)** — 사용자 데이터 export 엔드포인트 추가

## Not This Service

- ❌ **credentials 관리** — auth-service의 책임
- ❌ **로그인 이력** — security-service의 `login_history`
- ❌ **세션 관리** — auth-service의 `refresh_tokens`
- ❌ **권한/역할** — 권한 모델이 추가되면 별도 authz 경계 필요 (현재 스코프 외)
- ❌ **관리자 감사 조회** — admin-service + security-service의 query
- ❌ **비정상 탐지** — security-service의 책임

## Related Specs

- Architecture: [architecture.md](architecture.md)
- Data model: [data-model.md](data-model.md)
- Dependencies: [dependencies.md](dependencies.md)
- Observability: [observability.md](observability.md)
- HTTP contracts: [../../contracts/http/account-api.md](../../contracts/http/) (외부) + [../../contracts/http/internal/](../../contracts/http/internal/) 아래 auth-to-account, security-to-account, admin-to-account
- Event contract: [../../contracts/events/account-events.md](../../contracts/events/)
- Feature specs: [../../features/signup.md](../../features/), [../../features/account-lifecycle.md](../../features/)
