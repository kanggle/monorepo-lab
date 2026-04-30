---
id: TASK-BE-091
title: "spec: account-service retention.md 작성 — 휴면 전환·익명화 보존 정책 정의"
status: ready
area: backend
service: account-service
---

## Goal

`specs/services/account-service/architecture.md` line 154에서 참조하는 `specs/services/account-service/retention.md` 파일이 없다. 이 파일을 신규 작성하여 다음 두 배치 스케줄러의 동작 기준이 되는 보존 정책을 명문화한다:

1. **휴면 전환 정책** — ACTIVE 계정을 365일 미접속 시 DORMANT로 자동 전환하는 기준
2. **PII 익명화 정책** — DELETED 계정의 30일 유예 기간 만료 후 PII 삭제(익명화) 실행 기준

## Scope

### In

- `specs/services/account-service/retention.md` 신규 작성
  - ## 1. Dormant Activation 섹션
    - 전환 조건: `last_login_succeeded_at + 365d ≤ now`, `status = ACTIVE`
    - 배치 주기: 일 1회 (UTC 02:00 권장)
    - 대상 쿼리: `WHERE status = 'ACTIVE' AND last_login_succeeded_at < now() - INTERVAL 365 DAY`
    - 전환 시 발행 이벤트: `account.status.changed` (source: `system`, reason: `DORMANT_365D`)
    - 실패 처리: 개별 오류 시 skip + 경고 로그, 전체 배치는 계속 진행
    - 지표: `scheduler.dormant.processed`, `scheduler.dormant.failed`, `scheduler.dormant.duration_ms`
  - ## 2. PII Anonymization 섹션
    - 조건: `deleted_at + 30d ≤ now`, `status = DELETED`, `masked_at IS NULL`
    - 배치 주기: 일 1회 (UTC 03:00 권장)
    - 대상 쿼리: `WHERE status = 'DELETED' AND masked_at IS NULL AND deleted_at < now() - INTERVAL 30 DAY`
    - PII 마스킹 필드: `email_hash` 보존, `display_name`, `phone`, `profile_image_url` → anonymized 형태
    - 마스킹 완료 후: `masked_at = now()` 업데이트
    - 발행 이벤트: 없음 (internal side-effect; `account.deleted` 이벤트는 이미 DELETED 전환 시 발행됨)
    - grace period 내 복구(DELETED → ACTIVE) 계정은 대상 제외 (status != DELETED 또는 masked_at IS NULL 아님)
    - 실패 처리: 개별 오류 skip + 경고 로그, SLA: 30일 + 1일 허용 (배치 실패 1회 용인)
    - 지표: `scheduler.anonymize.processed`, `scheduler.anonymize.failed`, `scheduler.anonymize.duration_ms`
  - ## 3. 보존 기간 테이블 섹션
    - PII 데이터 종류별 보존 기간 표
    - 규제 근거 (rules/traits/regulated.md R7)

### Out

- 배치 스케줄러 코드 구현 (TASK-BE-092, TASK-BE-093)
- DB 마이그레이션 (masked_at 컬럼은 TASK-BE-088에서 이미 추가됨)
- 타 서비스 변경

## Acceptance Criteria

- [ ] `specs/services/account-service/retention.md` 파일 존재
- [ ] Dormant Activation 섹션: 365일 기준, 배치 주기, 대상 쿼리 형태, 이벤트, 실패 처리, 지표 정의
- [ ] PII Anonymization 섹션: 30일 기준, 대상 쿼리 형태, 마스킹 필드 목록, 실패 처리, 지표 정의
- [ ] 보존 기간 테이블 포함 (PII 종류별)
- [ ] `architecture.md` line 154의 링크가 신규 파일을 올바르게 참조 (파일명 일치 확인)

## Related Specs

- specs/features/account-lifecycle.md — 상태 전이 규칙 (ACTIVE → DORMANT 365일, DELETED → 익명화 30일)
- specs/features/data-rights.md — GDPR 삭제 동작 정의
- specs/services/account-service/architecture.md — retention.md 참조 위치 (line 154)
- rules/traits/regulated.md — R7 PII 보존 규정

## Related Contracts

- specs/contracts/events/account-events.md — `account.status.changed` 이벤트 스키마

## Edge Cases

- `last_login_succeeded_at IS NULL` 계정 처리: 가입일(`created_at`) 기준으로 대체하거나 스킵 — 스펙에 명시
- grace period 복구된 계정의 `masked_at` 처리: DELETED → ACTIVE 전환 시 masked_at이 이미 설정된 경우는 없음 (grace period < 30일)
- `masked_at` 컬럼이 이미 존재하는지 확인 후 스펙 작성 (TASK-BE-088에서 추가됨)

## Failure Scenarios

- 스펙이 구현과 불일치하면 TASK-BE-092/093 구현 시 Hard Stop 발생 — 스펙 작성 시 기존 코드(`PiiAnonymizer`, `AccountStatusService`)와 정합성 확인
