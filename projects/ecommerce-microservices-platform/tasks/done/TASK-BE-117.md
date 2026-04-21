# Task ID

TASK-BE-117

# Title

Alertmanager 외부 알림 채널 연동 — Slack Webhook 수신자 설정 및 notification-service 전용 알림 규칙 추가

# Status

done

# Owner

backend

# Task Tags

- code
- deploy

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

현재 Alertmanager의 receiver가 stub 상태(주석 처리)로, 알림 규칙이 발동해도 운영자에게 실제 알림이 전달되지 않는다.
이 태스크는 Alertmanager receiver에 Slack Webhook을 연동하고, notification-service 알림 발송 실패에 특화된 Prometheus 알림 규칙을 추가하여 운영자가 장애 상황을 즉시 인지할 수 있도록 한다.

---

# Scope

## In Scope

- Alertmanager receiver 3개(`default`, `critical`, `warning`)에 Slack Webhook 설정 활성화
- Slack Webhook URL을 환경 변수로 외부화 (`SLACK_WEBHOOK_URL`, `SLACK_CHANNEL_CRITICAL`, `SLACK_CHANNEL_WARNING`)
- notification-service 전용 Prometheus 알림 규칙 추가:
  - `NotificationDeliveryFailureRateHigh`: `notification_failed_total` 비율이 10% 초과 시 WARNING, 30% 초과 시 CRITICAL
  - `NotificationSenderDown`: notification-service가 다운되었을 때 CRITICAL
- docker-compose.yml의 alertmanager 서비스에 환경 변수 전달 설정

## Out of Scope

- PagerDuty, email 등 Slack 외 채널 연동
- Grafana 대시보드 생성
- notification-service 애플리케이션 코드 변경
- 알림 규칙의 임계값 튜닝 (운영 후 별도 태스크로 조정)

---

# Acceptance Criteria

- [ ] Alertmanager `default`, `critical`, `warning` receiver에 Slack Webhook이 설정되어 있다
- [ ] Slack Webhook URL과 채널명이 환경 변수로 외부화되어 있다 (하드코딩 없음)
- [ ] `notification_failed_total` 기반 알림 규칙이 `infra/prometheus/alert-rules.yml`에 추가되어 있다
- [ ] `docker compose up` 시 Alertmanager가 정상 기동되고 설정을 로드한다
- [ ] Alertmanager `/api/v2/status` API로 receiver 설정이 활성화된 것을 확인할 수 있다

---

# Related Specs

> **Before reading Related Specs**: Follow `specs/platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `specs/rules/common.md` plus any `specs/rules/domains/<domain>.md` and `specs/rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `specs/platform/observability.md` — 알림 규칙 baseline 정의
- `specs/services/notification-service/observability.md` — notification-service 비즈니스 메트릭 정의

# Related Skills

- `.claude/skills/common/implement-task.md`

---

# Related Contracts

- 해당 없음 (인프라 설정 변경, API/이벤트 계약 변경 없음)

---

# Target Service

- infra (Prometheus, Alertmanager 설정)

---

# Architecture

Follow:

- `infra/prometheus/alert-rules.yml` — 기존 알림 규칙 구조
- `infra/alertmanager/alertmanager.yml` — 기존 receiver 구조

---

# Implementation Notes

- `alertmanager.yml`에서 Slack Webhook URL을 직접 하드코딩하지 말고, 환경 변수 치환 또는 docker-compose의 env 전달 방식을 사용한다.
- 기존 alert-rules.yml의 5개 카테고리 구조를 유지하면서 카테고리 6으로 `notification_delivery` 그룹을 추가한다.
- `notification_failed_total` 메트릭은 `specs/services/notification-service/observability.md`에 정의되어 있으며, channel과 reason 레이블을 가진다.

---

# Edge Cases

- Slack Webhook URL 환경 변수가 설정되지 않은 경우 — Alertmanager 기동은 되지만 알림 발송 실패 (기존 stub 동작과 동일)
- notification-service가 아직 한 번도 메트릭을 발행하지 않은 경우 — `notification_failed_total` 시계열이 없으므로 알림 규칙이 발동하지 않음 (정상 동작)
- Slack API rate limit — Alertmanager의 `group_wait`, `group_interval` 설정으로 자연스럽게 제어됨

---

# Failure Scenarios

- Slack Webhook URL이 만료/변경된 경우 — Alertmanager 로그에 발송 실패 기록, 환경 변수 교체로 복구
- Prometheus → Alertmanager 연결 실패 — Prometheus 로그에 alertmanager send 실패 기록, 네트워크 확인 필요
- 알림 규칙 문법 오류 — Prometheus 기동 시 rule 파일 파싱 실패 로그 출력

---

# Test Requirements

- Alertmanager 설정 파일 문법 검증 (`amtool check-config`)
- Prometheus 알림 규칙 문법 검증 (`promtool check rules`)
- docker-compose up 후 Alertmanager `/api/v2/status` 정상 응답 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Alertmanager config 문법 검증 통과
- [ ] Prometheus alert rules 문법 검증 통과
- [ ] docker-compose 기동 정상 확인
- [ ] Specs updated first if required
- [ ] Ready for review
