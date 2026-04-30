# admin-service — Overview

## Purpose

운영자 전용 **관리 서비스**. 계정 lock/unlock, 강제 로그아웃(refresh token revoke), 감사 조회 프록시 등 운영자가 수행하는 특권 작업의 단일 진입점. 자체 도메인 상태를 거의 가지지 않는 **명령 오케스트레이션 레이어**로서, 실제 상태 변경은 downstream(auth, account, security)이 수행하고 admin-service는 다음만 담당한다:

1. **운영자 인증/권한** (일반 사용자 JWT와 분리된 경계)
2. **명령 파라미터 검증 및 사유 수집**
3. **downstream 내부 HTTP 호출**
4. **모든 행위의 불변 감사 기록** (`admin_actions` append-only 테이블 + `admin.action.performed` 이벤트)

[rules/domains/saas.md](../../../rules/domains/saas.md) S5와 [rules/traits/audit-heavy.md](../../../rules/traits/audit-heavy.md) A1의 교차 지점 — 운영자 작업은 모두 감사 대상이며, admin-service가 그 감사의 **issuer**이자 **gateway**.

## Callers

- **내부 운영자만** — admin dashboard(별도 프론트엔드), runbook 자동화 스크립트, SRE/CS 담당자
- **NOT 일반 사용자** — 공개 사용자 트래픽이 도달하지 못하도록 게이트웨이에서 `/api/admin/*` 경로에 별도 인증 필터 체인 적용
- **NOT 다른 서비스** — 다른 내부 서비스는 downstream을 직접 호출하지, admin-service를 경유하지 않음

## Callees

| 대상 | 목적 | 경로 |
|---|---|---|
| `auth-service` | 강제 로그아웃, refresh token revoke | 내부 HTTP [contracts/http/internal/admin-to-auth.md](../../contracts/http/internal/) |
| `account-service` | lock / unlock / delete (상태 기계 명령) | 내부 HTTP [contracts/http/internal/admin-to-account.md](../../contracts/http/internal/) |
| `security-service` | 감사 조회 (login_history, suspicious_events) | 내부 HTTP (read-only query) |
| MySQL (`admin_db`) | `admin_actions` append-only 감사 원장 + `outbox_events` | 직접 JPA |
| Kafka | outbox relay → `admin.action.performed` | producer |

downstream 호출 시 **반드시 Idempotency-Key 전달** ([rules/traits/transactional.md](../../../rules/traits/transactional.md) T1) + circuit breaker + 재시도 ([rules/traits/integration-heavy.md](../../../rules/traits/integration-heavy.md) I1-I3).

## Owned State

### MySQL
- `admin_actions` — **append-only 감사 원장**. 필드: `id`, `action_code` (ACCOUNT_LOCK / ACCOUNT_UNLOCK / SESSION_REVOKE / AUDIT_QUERY 등), `actor` (operator_id / role), `target_type`, `target_id`, `reason`, `ticket_id`, `outcome` (SUCCESS / FAILURE / IN_PROGRESS), `downstream_detail` (JSON), `started_at`, `completed_at`. DB 트리거로 UPDATE/DELETE 차단
- `outbox_events` — `admin.action.performed` 스테이징

### Redis (선택)
- operator 세션 nonce, rate limit 버킷 (사용 시)

**도메인 상태 없음**. 계정/세션/credential은 모두 downstream이 소유.

## Change Drivers

1. **새 운영 명령 추가** — 신규 runbook 요구 (예: 이메일 변경 강제, 2FA 리셋, bulk lock)
2. **권한 모델 확장** — 새 operator role (SUPER_ADMIN / ACCOUNT_ADMIN / AUDITOR 등)
3. **규제 감사 요구 강화** — 감사 필드 추가, 보존 기간 연장, 외부 SIEM 연동
4. **운영자 인증 강화** — 2FA 의무화, 하드웨어 토큰, IP allowlist
5. **downstream 내부 API 변경** — 호출 대상 서비스의 계약 변경에 따른 클라이언트 업데이트

## Not This Service

- ❌ **일반 사용자 기능** — 공개 게이트웨이와 같은 인증 경계를 **절대 공유하지 않음**
- ❌ **도메인 로직** — 계정 상태 기계는 account-service, 토큰 회전은 auth-service. admin-service에 로직을 이식하지 않음 (중복·드리프트의 원인)
- ❌ **감사 데이터의 원본 소유** — login_history와 suspicious_events는 security-service 소유. admin-service는 **조회 프록시** 역할만 (+ 자체 `admin_actions`는 "운영자가 수행한 행위"에 한정)
- ❌ **자동화된 의사 결정** — 자동 잠금은 security-service가 수행. admin-service는 **인간 운영자가 직접 호출**하는 엔드포인트만 제공
- ❌ **알림 발송** — 이메일/SMS 알림은 auth-service의 outbox 또는 별도 notification 경로

## Related Specs

- Architecture: [architecture.md](architecture.md)
- Dependencies: [dependencies.md](dependencies.md)
- Observability: [observability.md](observability.md)
- HTTP contract (외부, admin 전용): [../../contracts/http/admin-api.md](../../contracts/http/)
- HTTP contracts (out-going): [../../contracts/http/internal/admin-to-auth.md](../../contracts/http/internal/), [../../contracts/http/internal/admin-to-account.md](../../contracts/http/internal/)
- Event contract: [../../contracts/events/admin-events.md](../../contracts/events/)
- Feature specs: [../../features/admin-operations.md](../../features/), [../../features/audit-trail.md](../../features/)
