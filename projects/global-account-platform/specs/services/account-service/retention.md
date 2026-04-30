# account-service — Retention Policy

## Purpose

`account-service`가 소유한 계정·프로필 데이터의 **보존 기간**과 **자동 전이/익명화 절차**를 정의한다. 본 문서는 두 배치 스케줄러의 동작 기준이다:

1. **휴면 전환 스케줄러** (`AccountDormantScheduler`) — ACTIVE 계정의 365일 미접속 시 DORMANT 자동 전환
2. **PII 익명화 스케줄러** (`AccountAnonymizationScheduler`) — DELETED 계정의 30일 유예 만료 후 PII 자동 익명화

규제 근거: [rules/traits/regulated.md](../../../rules/traits/regulated.md) R6 (Retention), R7 (Right to Erasure).
관련 상태 기계: [specs/features/account-lifecycle.md](../../features/account-lifecycle.md).
관련 데이터 모델: [data-model.md](./data-model.md).

---

## 1. Dormant Activation

### 1.1 정책

ACTIVE 상태 계정에서 **마지막 로그인 성공 후 365일**이 경과하면 시스템이 자동으로 DORMANT 상태로 전환한다 ([account-lifecycle.md](../../features/account-lifecycle.md) `Automatic Transitions`).

| 항목 | 값 |
|---|---|
| 트리거 컬럼 | `accounts.last_login_succeeded_at` |
| 임계 기간 | **365일** |
| 대상 상태 | `status = ACTIVE` |
| 전이 후 상태 | `DORMANT` |
| 전이 사유 코드 | `DORMANT_365D` |
| 전이 주체 | `actor_type = system`, `actor_id = NULL` |

복귀 경로: 사용자가 다음 로그인 시도에 성공하면 auth-service가 즉시 `DORMANT → ACTIVE` 전이를 호출하므로, 본 배치는 휴면 진입만 담당한다.

### 1.2 배치 주기

| 항목 | 값 |
|---|---|
| 빈도 | **일 1회** |
| 권장 시각 | **UTC 02:00** (`@Scheduled(cron = "0 0 2 * * *", zone = "UTC")`) |
| 이유 | 트래픽이 가장 낮은 시간대에 lock contention 최소화 |

### 1.3 대상 쿼리

```sql
SELECT *
  FROM accounts
 WHERE status = 'ACTIVE'
   AND last_login_succeeded_at IS NOT NULL
   AND last_login_succeeded_at < (NOW() - INTERVAL 365 DAY);
```

JPA 메서드 시그니처 (`AccountJpaRepository`):

```java
List<AccountJpaEntity> findActiveDormantCandidates(Instant threshold);
```

`threshold` 인자는 `Instant.now().minus(365, DAYS)`로 호출자가 계산하여 전달한다. 이 방식은 단위 테스트에서 시간 고정을 가능하게 한다.

### 1.4 `last_login_succeeded_at IS NULL` 처리

가입 직후 한 번도 로그인하지 않은 계정에 대해서는 다음 규칙을 적용한다:

| 규칙 | 동작 |
|---|---|
| **대체 기준** | `last_login_succeeded_at IS NULL`인 경우 `created_at`을 휴면 임계 계산의 기준으로 사용한다 |
| 적용 쿼리 | `WHERE status = 'ACTIVE' AND COALESCE(last_login_succeeded_at, created_at) < (NOW() - INTERVAL 365 DAY)` |
| 정당화 | 365일간 로그인하지 않은 신규 가입자도 정책상 휴면 대상. `last_login_succeeded_at`을 `created_at`으로 초기화하는 대안은 데이터 모델 의미를 흐리므로 채택하지 않는다 |

`last_login_succeeded_at` 컬럼은 미존재 시 dormant 스케줄러 구현 단계(TASK-BE-092)에서 마이그레이션으로 추가하며, 기존 row는 `NULL` 그대로 두고 본 절의 `COALESCE` 정책으로 처리한다.

### 1.5 전이 절차

각 후보 계정에 대해 동일 트랜잭션 내에서 다음을 수행한다 ([rules/traits/transactional.md](../../../rules/traits/transactional.md) T3):

1. `AccountStatusMachine.transition(ACTIVE, DORMANT, DORMANT_365D)` 호출
2. `accounts.status = 'DORMANT'`, `updated_at = now()` 저장 (낙관적 락 `version` 증분)
3. `account_status_history` append 1 row (`from_status=ACTIVE`, `to_status=DORMANT`, `reason_code=DORMANT_365D`, `actor_type=system`, `actor_id=NULL`)
4. outbox에 `account.status.changed` 이벤트 적재 ([account-events.md](../../contracts/events/account-events.md))

### 1.6 발행 이벤트

| Topic | Payload 핵심 필드 |
|---|---|
| `account.status.changed` | `previousStatus=ACTIVE`, `currentStatus=DORMANT`, `reasonCode=DORMANT_365D`, `actorType=system`, `actorId=null` |

`account.locked` 등 특화 이벤트는 발행하지 않는다 (DORMANT는 LOCKED와 별개의 상태).

### 1.7 실패 처리

| 시나리오 | 동작 |
|---|---|
| 단일 계정 처리 중 예외 (낙관적 락 충돌, 상태 기계 거부, DB 오류 등) | 해당 계정만 **skip + WARN 로그**, 다음 후보로 진행. 전체 배치는 계속 |
| 배치 시작 자체 실패 (DB 다운 등) | 다음날 동일 시각 재실행. 365일 + 1일 허용 SLA |
| 휴면 전환 도중 사용자가 동시 로그인 | 낙관적 락 충돌로 본 배치가 실패 → skip. 사용자 로그인 경로가 우선 (DORMANT 진입을 막음) |

**중요**: 한 계정의 실패가 다른 계정 처리를 막지 않도록 트랜잭션을 **계정 단위로 분리**한다.

### 1.8 지표

| 메트릭 | 타입 | 설명 |
|---|---|---|
| `scheduler.dormant.processed` | counter | 정상 전환된 계정 수 (배치 단위) |
| `scheduler.dormant.failed` | counter | skip된 계정 수 |
| `scheduler.dormant.duration_ms` | histogram | 배치 1회 실행 소요 시간 |

태그: `service=account-service`, `scheduler=dormant`. ([observability.md](./observability.md) 참조)

---

## 2. PII Anonymization

### 2.1 정책

DELETED 상태로 전이된 계정은 **30일 유예 기간**이 지나면 PII를 복구 불가능하게 익명화한다 ([rules/traits/regulated.md](../../../rules/traits/regulated.md) R7). 유예 기간 중에는 운영자가 admin-only 경로로 ACTIVE로 복구할 수 있다.

| 항목 | 값 |
|---|---|
| 트리거 컬럼 | `accounts.deleted_at`, `profiles.masked_at` |
| 유예 기간 | **30일** (환경 변수로 조정 가능, 기본값 30일 — [data-model.md](./data-model.md#anonymization)) |
| 대상 상태 | `status = DELETED` |
| 익명화 완료 표식 | `profiles.masked_at IS NOT NULL` |

### 2.2 GDPR 즉시 마스킹과의 관계

[data-rights.md](../../features/data-rights.md) Right to Erasure 경로(GDPR/PIPA 즉시 삭제 요청, `POST /internal/accounts/{accountId}/gdpr-delete`)는 **유예 없이 즉시 마스킹**을 수행한다. 본 절(2.x)의 배치는 **유예가 있는 일반 탈퇴 경로**(`USER_REQUEST`, `ADMIN_DELETE`)만을 대상으로 한다. 두 경로는 다음 조건으로 자연 분리된다:

- GDPR 경로: 즉시 `masked_at`이 설정됨 → 본 배치 쿼리에서 자동 제외
- 일반 탈퇴: `masked_at IS NULL` 상태로 30일 대기 → 본 배치가 처리

### 2.3 배치 주기

| 항목 | 값 |
|---|---|
| 빈도 | **일 1회** |
| 권장 시각 | **UTC 03:00** (`@Scheduled(cron = "0 0 3 * * *", zone = "UTC")`) |
| 이유 | 휴면 배치(02:00) 이후 충돌 회피, 새벽 트래픽 최소 시간대 |

### 2.4 대상 쿼리

```sql
SELECT a.*
  FROM accounts a
  LEFT JOIN profiles p ON p.account_id = a.id
 WHERE a.status = 'DELETED'
   AND a.deleted_at < (NOW() - INTERVAL 30 DAY)
   AND (p.masked_at IS NULL OR p.account_id IS NULL);
```

> 프로필이 없는 계정도 익명화 대상에 포함한다(emails 마스킹은 accounts 테이블 단독 처리).

JPA 메서드 시그니처 (`AccountJpaRepository`):

```java
List<AccountJpaEntity> findAnonymizationCandidates(Instant threshold);
```

`threshold = Instant.now().minus(30, DAYS)`.

### 2.5 마스킹 대상 필드

본 절은 [data-model.md](./data-model.md#anonymization) "Anonymization" 표를 권위 원본으로 따른다.

| 테이블 | 필드 | 처리 후 값 | 분류 등급 |
|---|---|---|---|
| `accounts` | `email` | `anon_{SHA256(email)[:12]}@deleted.local` | confidential → 마스킹 |
| `accounts` | `email_hash` | **보존** (재가입 차단·중복 검사용 64자 SHA-256 hex) | internal |
| `profiles` | `display_name` | `'탈퇴한 사용자'` (고정 문자열) | confidential → 마스킹 |
| `profiles` | `phone_number` | `NULL` | confidential → 제거 |
| `profiles` | `birth_date` | `NULL` | confidential → 제거 |
| `profiles` | `profile_image_url` | `NULL` | confidential → 제거 |
| `profiles` | `preferences` | `NULL` | internal → 제거 (식별 가능 설정 제거) |
| `accounts` | 그 외 (`status`, `created_at`, `deleted_at`, `version`) | **보존** | 감사 추적 유지 |
| `account_status_history` | 모든 row | **보존** (append-only, [audit-heavy.md](../../../rules/traits/audit-heavy.md) A3) | — |

> **`profiles.profile_image_url` 컬럼 도입 노트**: 본 컬럼은 [data-model.md](./data-model.md)의 `profiles` 테이블에 아직 정의되지 않았다. 프로필 이미지 기능을 도입하는 PR에서 `data-model.md`의 `profiles` 표에 `profile_image_url VARCHAR(500) NULL, confidential` 행을 추가하고, 본 표의 `profile_image_url → NULL` 처리 정책을 그대로 활성화한다. 본 retention 정책은 향후 컬럼 추가 시 누락을 방지하기 위해 마스킹 대상에 선제적으로 명시한다 (TASK-BE-091 AC `profile_image_url → anonymized 형태` 요건 충족). 다른 추가 PII 필드를 도입할 때도 본 표와 [data-model.md](./data-model.md)를 같은 PR에서 동시에 갱신해야 한다.

원복 불가능성 보장: SHA-256 hex의 일방향성 + `display_name` 고정 문자열 + 나머지 필드 NULL 처리. `email_hash`는 의도적으로 보존하지만 단방향 해시이므로 PII로 환원되지 않는다.

### 2.6 익명화 완료 처리

각 후보 계정에 대해 동일 트랜잭션 내에서 다음을 수행한다:

1. `accounts.email` → `anon_{SHA256(email)[:12]}@deleted.local`
2. `accounts.email_hash` 보존 (이미 GDPR 경로 외에는 비어있을 수 있으므로, NULL일 경우 동일하게 SHA-256 full hex로 채움)
3. `profiles.display_name`, `phone_number`, `birth_date`, `preferences` 마스킹
4. `profiles.profile_image_url` → `NULL` (컬럼이 도입된 이후 활성화. 컬럼 부재 단계에서는 이 단계를 무시하되, 컬럼 추가 PR에서 본 단계가 자동 활성화되도록 안전하게 작성한다 — 예: 컬럼 존재 여부 가드)
5. `profiles.masked_at = now()` 설정
6. `accounts.updated_at = now()` 갱신 (`version` 증분)

`PiiAnonymizer.anonymize(account, profile)` 인프라 클래스가 위 단계를 담당하며, [architecture.md](./architecture.md) `infrastructure/anonymizer/` 위치에 둔다.

### 2.7 발행 이벤트

| Topic | 발행 여부 | 비고 |
|---|---|---|
| `account.deleted` (anonymized=false) | 본 배치에서 **발행하지 않음** | DELETED 전이 시 이미 발행됨 |
| `account.deleted` (anonymized=true) | 본 배치에서 **재발행** | [account-events.md](../../contracts/events/account-events.md) "유예 종료 후 PII 익명화 완료 시 동일 토픽에 `anonymized: true`로 재발행" |

재발행 payload는 `account.deleted` 스키마와 동일하되 다음 필드를 채운다 ([account-events.md](../../contracts/events/account-events.md) 계약과 일치):

| 필드 | 값 | 비고 |
|---|---|---|
| `accountId` | 대상 계정 ID | 변경 없음 |
| `reasonCode`, `actorType`, `actorId` | 원래 DELETED 전이 시 발행한 값 그대로 | 재발행이므로 원래 사유·주체 보존 |
| `deletedAt` | 원래 `accounts.deleted_at` 시각 | 변경 없음. 익명화 시각이 아님 |
| `gracePeriodEndsAt` | **원래 유예 종료 시각** (`deleted_at + 30d`) | [account-events.md](../../contracts/events/account-events.md) `account.deleted` 스키마 정의(유예 종료 시각)와 동일 의미. **익명화 완료 시각이 아님** |
| `anonymized` | `true` | 본 재발행에서만 `true` |

> **이전 문서 표기 정정**: 본 절은 과거에 `gracePeriodEndsAt`을 "익명화 시각"으로 채운다고 기술했으나, 이는 [account-events.md](../../contracts/events/account-events.md)의 필드 의미(유예 종료 시각)와 충돌하여 다운스트림 consumer 해석 오류를 유발한다. 본 개정에서 해당 표기를 제거하고 계약 정의와 일치시킨다.
>
> **익명화 완료 시각이 필요한 다운스트림 처리**: consumer는 `anonymized=true` 이벤트의 `occurredAt`(envelope) 또는 별도 처리 시각으로 자체 마스킹 시점을 확보한다. 만약 `account.deleted` payload에 `anonymizedAt` 필드를 신설할 필요가 발생하면 [account-events.md](../../contracts/events/account-events.md) 계약 변경을 선행하는 별도 태스크로 다룬다 (Contract Rule).

다운스트림 consumer는 `anonymized=true` 이벤트를 수신하면 자체 보관 PII (예: security-service의 로그인 이력 actor email)도 마스킹한다.

### 2.8 grace period 복구된 계정 처리

유예 중 운영자가 `DELETED → ACTIVE` 복구를 수행한 계정은 `status != 'DELETED'`이므로 쿼리에서 자연 제외된다.

복구 후 동일 계정이 다시 삭제되더라도 새로운 `deleted_at`이 기록되며 30일 카운트다운이 재시작된다 — `masked_at`은 grace period 내에는 절대 설정되지 않으므로 (grace period < 30일 < masked_at 설정 시점), 충돌은 발생하지 않는다.

### 2.9 실패 처리 및 SLA

| 시나리오 | 동작 |
|---|---|
| 단일 계정 처리 중 예외 (DB 오류, 동시성 충돌 등) | 해당 계정 **skip + WARN 로그**, 다음 후보로 진행 |
| 배치 1회 실패 | **30일 + 1일 허용 SLA**. 다음날 재실행 시 처리됨 |
| 배치 2회 연속 실패 | 알림 발송 (CRITICAL). 30일 + 2일 초과 시 GDPR/PIPA 잠재 위반 |
| 동시성: 배치 실행 중 grace period 복구 호출 | 낙관적 락(`version`) + `status='DELETED'` 재확인. 충돌 시 배치 측 skip |

**중요**: 익명화는 멱등성을 갖지 않는다 (재실행 시 이미 마스킹된 email을 다시 해시하면 emailHash가 깨짐). 따라서 `masked_at IS NULL` 가드가 필수이며, 트랜잭션 단위는 계정 1건이다.

### 2.10 지표

| 메트릭 | 타입 | 설명 |
|---|---|---|
| `scheduler.anonymize.processed` | counter | 익명화 완료 계정 수 |
| `scheduler.anonymize.failed` | counter | skip된 계정 수 |
| `scheduler.anonymize.duration_ms` | histogram | 배치 1회 실행 소요 시간 |
| `scheduler.anonymize.overdue` | gauge | `deleted_at + 31d`를 초과한 미처리 계정 수 (SLA 위반 감지) |

태그: `service=account-service`, `scheduler=anonymize`.

---

## 3. 보존 기간 테이블 (Retention Schedule)

[rules/traits/regulated.md](../../../rules/traits/regulated.md) R6 요구: 모든 PII·민감 데이터는 보존 기간을 명시한다.

| 데이터 | 분류 등급 | 보존 기간 | 종료 시 처리 | 규제 근거 |
|---|---|---|---|---|
| `accounts.email` (ACTIVE/LOCKED/DORMANT) | confidential | 계정 활성 기간 + 유예 30일 | 익명화(SHA-256 해시 기반 anon 이메일) | GDPR Art.17, PIPA §21 |
| `accounts.email_hash` | internal | **무기한** (재가입 차단·중복 검사 목적) | 보존 — 단방향 해시이므로 PII 아님 | [data-rights.md](../../features/data-rights.md) Data Retention Policy |
| `profiles.display_name` | confidential | 계정 활성 기간 + 유예 30일 | 고정 문자열 `'탈퇴한 사용자'`로 마스킹 | GDPR Art.17, PIPA §21 |
| `profiles.phone_number` | confidential | 계정 활성 기간 + 유예 30일 | NULL | GDPR Art.17, PIPA §21 |
| `profiles.birth_date` | confidential | 계정 활성 기간 + 유예 30일 | NULL | GDPR Art.17, PIPA §21 |
| `profiles.profile_image_url` *(향후 컬럼 도입 시 적용)* | confidential | 계정 활성 기간 + 유예 30일 | NULL (익명화) | GDPR Art.17, PIPA §21 |
| `profiles.preferences` | internal | 계정 활성 기간 + 유예 30일 | NULL | regulated R6 (불필요 데이터 미보유) |
| `account_status_history` | internal | **5년** (감사 보존) | 보존 — append-only, PII 미포함 (`actor_id`는 운영자 ID 또는 system) | [audit-heavy.md](../../../rules/traits/audit-heavy.md) A3, PIPA §21② |
| `outbox_events` | internal | 발행 후 7일 (publishedAt 기준) | 별도 정리 배치(범위 외) | regulated R6 |
| `accounts` row 자체 | internal | **무기한** (감사 추적) | 보존 — PII 마스킹 후 row는 유지 | [data-rights.md](../../features/data-rights.md) Data Retention Policy |

### 3.1 휴면 전환 기준

| 데이터 | 휴면 진입 임계 |
|---|---|
| `accounts` (ACTIVE → DORMANT) | `last_login_succeeded_at` 또는 `created_at` 기준 365일 미접속 |

### 3.2 무기한 보존 항목 정당화

- `email_hash`: 동일 이메일의 재가입 검증과 중복 방지용. 일방향 해시이므로 [regulated.md](../../../rules/traits/regulated.md) R7의 "복구 불가능 익명화" 요건을 충족한다. PII 환원 불가.
- `accounts` row (status, created_at, deleted_at): 감사 추적과 서비스 분쟁 대응에 필수. PII는 모두 마스킹된 상태로만 유지.
- `account_status_history`: [audit-heavy.md](../../../rules/traits/audit-heavy.md) A3의 append-only 요구. 5년 후 압축/외부 저장으로 이전하는 정책은 본 문서 범위 외 (별도 archival 정책 필요).

---

## 4. Operational Runbook (요약)

| 상황 | 확인 항목 | 조치 |
|---|---|---|
| `scheduler.dormant.failed` 급증 | 로그의 skip 원인 (낙관적 락 vs 상태 기계 거부 vs DB 오류) | 락 충돌이면 정상, DB 오류면 인프라 점검 |
| `scheduler.anonymize.overdue > 0` | 며칠째 SLA 초과인지 | 즉시 수동 트리거 또는 배치 재시작. 30일+2일 초과 시 컴플라이언스 알림 |
| 휴면 진입 후 즉시 로그인 클레임 | 정상 — DORMANT → ACTIVE 자동 복귀 경로 ([account-lifecycle.md](../../features/account-lifecycle.md)) | 사용자 안내만 |
| 익명화 후 복구 요청 | 복구 불가 (R7 — 복구 불가능 익명화) | 운영자에게 거부 응답 |

---

## Related Specs

- [specs/features/account-lifecycle.md](../../features/account-lifecycle.md) — 상태 전이 규칙
- [specs/features/data-rights.md](../../features/data-rights.md) — GDPR 즉시 삭제 경로
- [specs/services/account-service/architecture.md](./architecture.md) — `infrastructure/anonymizer/`, `infrastructure/scheduler/` 레이어
- [specs/services/account-service/data-model.md](./data-model.md) — 컬럼·분류 등급·anonymization 표
- [specs/contracts/events/account-events.md](../../contracts/events/account-events.md) — `account.status.changed`, `account.deleted` 스키마
- [rules/traits/regulated.md](../../../rules/traits/regulated.md) — R6/R7
- [rules/traits/audit-heavy.md](../../../rules/traits/audit-heavy.md) — A3 (history 불변성)

## Change Rule

본 문서는 다음 변경 시 같은 PR에서 갱신해야 한다:

1. 휴면 임계 기간(365일) 또는 PII 유예 기간(30일) 변경
2. PII 마스킹 대상 필드 추가/제거 (data-model.md와 동시 갱신)
3. 배치 cron 주기 변경 (운영 영향 검토 필요)
4. 발행 이벤트 변경 (account-events.md와 동시 갱신)
