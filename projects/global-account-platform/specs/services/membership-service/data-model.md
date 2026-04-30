# membership-service — Data Model

## Design Decision

구독 상태는 `UPDATE subscriptions SET status = ?` 금지. 모든 전이는 `SubscriptionStatusMachine`을 통해 `subscription_status_history`에 append-only 기록 ([rules/traits/audit-heavy.md](../../../rules/traits/audit-heavy.md) A3).

계정당 플랜당 ACTIVE 구독은 1개만 허용 — `SubscriptionRepository.findByAccountIdAndPlanLevelAndStatus(ACTIVE)` 중복 체크.

## Tables

### membership_plans

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | VARCHAR(36) | PK (UUID) | 플랜 식별자 |
| plan_level | VARCHAR(20) | UNIQUE, NOT NULL | FREE / FAN_CLUB |
| name | VARCHAR(100) | NOT NULL | 표시명 (예: "팬 클럽") |
| price_krw | INT | NOT NULL, DEFAULT 0 | 월 구독료 (원화, FREE는 0) |
| duration_days | INT | NOT NULL | 구독 기간 (일, FREE는 0 = 영구) |
| description | TEXT | NULL | 혜택 설명 |
| is_active | BOOLEAN | NOT NULL, DEFAULT true | 플랜 활성화 여부 |

**기본 데이터** (Flyway 초기 데이터):
- FREE: price=0, duration=0 (영구), is_active=true
- FAN_CLUB: price=9900, duration=30, is_active=true

### subscriptions

| 컬럼 | 타입 | 제약 | PII 등급 | 설명 |
|---|---|---|---|---|
| id | VARCHAR(36) | PK (UUID) | internal | 구독 식별자 |
| account_id | VARCHAR(36) | NOT NULL, INDEX | internal | 구독자 계정 ID |
| plan_level | VARCHAR(20) | NOT NULL | internal | FREE / FAN_CLUB |
| status | VARCHAR(20) | NOT NULL | internal | ACTIVE / EXPIRED / CANCELLED |
| started_at | DATETIME(6) | NOT NULL | internal | 구독 시작 일시 |
| expires_at | DATETIME(6) | NULL | internal | 만료 예정 일시 (FREE는 NULL = 영구) |
| cancelled_at | DATETIME(6) | NULL | internal | 해지 일시 |
| created_at | DATETIME(6) | NOT NULL | internal | 레코드 생성 일시 |
| version | INT | NOT NULL, DEFAULT 0 | internal | 낙관적 락 (T5) |

**인덱스**: idx_subscriptions_account_status (account_id, status), idx_subscriptions_expires (expires_at, status)

**UNIQUE KEY**: (account_id, plan_level, status='ACTIVE') 논리 제약 — 중복 ACTIVE 구독은 애플리케이션 레이어에서 방지

### subscription_status_history (append-only)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK AUTO_INCREMENT | — |
| subscription_id | VARCHAR(36) | NOT NULL, INDEX | 구독 참조 |
| account_id | VARCHAR(36) | NOT NULL, INDEX | 계정 참조 |
| from_status | VARCHAR(20) | NOT NULL | 이전 상태 |
| to_status | VARCHAR(20) | NOT NULL | 후 상태 |
| reason | VARCHAR(50) | NOT NULL | USER_SUBSCRIBE / USER_CANCEL / SCHEDULED_EXPIRE |
| actor_type | VARCHAR(20) | NOT NULL | user / system |
| occurred_at | DATETIME(6) | NOT NULL | UTC |

**불변성**: DB 트리거로 UPDATE/DELETE 차단 (A3)

### content_access_policies

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | VARCHAR(36) | PK (UUID) | — |
| visibility_key | VARCHAR(50) | UNIQUE, NOT NULL | 콘텐츠 공개 범위 키 (e.g. MEMBERS_ONLY) |
| required_plan_level | VARCHAR(20) | NOT NULL | 접근에 필요한 최소 플랜 레벨 |
| description | VARCHAR(200) | NULL | 정책 설명 |

**기본 데이터**: `MEMBERS_ONLY` → `FAN_CLUB`

### outbox_events

표준 outbox 스키마 ([libs/java-messaging](../../../libs/java-messaging) `OutboxJpaEntity` 참조).

## Migration Strategy

| 버전 | 파일 | 내용 |
|---|---|---|
| V0001 | `V0001__create_membership_plans.sql` | membership_plans 테이블 + 초기 데이터 |
| V0002 | `V0002__create_subscriptions.sql` | subscriptions 테이블 |
| V0003 | `V0003__create_subscription_status_history.sql` | subscription_status_history + append-only 트리거 |
| V0004 | `V0004__create_content_access_policies.sql` | content_access_policies 테이블 + 초기 데이터 |
| V0005 | `V0005__create_outbox_events.sql` | outbox_events 테이블 |
