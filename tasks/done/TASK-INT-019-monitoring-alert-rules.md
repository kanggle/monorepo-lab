# Task ID

TASK-INT-019

# Title

모니터링 알림 규칙 세분화 — Prometheus AlertManager 알림 정책 구성

# Status

done

# Owner

integration

# Task Tags

- deploy
- code

---

# Goal

Prometheus + Grafana 모니터링 스택에 AlertManager 알림 규칙을 추가하여 운영 이상 상황을 자동 감지한다.

서비스별 핵심 메트릭에 대한 알림 임계값과 알림 채널을 정의한다.

---

# Scope

## In Scope

- AlertManager 설정 파일 작성
- 서비스별 알림 규칙 정의
  - 높은 에러율 (5xx 비율)
  - 느린 응답 시간 (P95 임계값 초과)
  - 서비스 다운 (health check 실패)
  - Kafka 소비 지연 (consumer lag)
  - DB 커넥션 풀 고갈
- docker-compose에 AlertManager 추가
- Grafana 알림 대시보드 패널 추가

## Out of Scope

- 외부 알림 채널 연동 (Slack, PagerDuty 등 — 설정 포인트만 제공)
- 클라우드 환경 알림 구성
- 알림 에스컬레이션 정책

---

# Acceptance Criteria

- [x] AlertManager 설정 파일 존재
- [x] 최소 5개 카테고리의 알림 규칙 정의
- [x] docker-compose에서 AlertManager 구동 가능
- [x] Grafana에서 알림 상태 확인 가능
- [x] 알림 규칙 문서화

---

# Related Specs

- `specs/platform/observability.md`
- `specs/services/*/observability.md`
- `specs/platform/deployment-policy.md`

# Related Skills

- N/A

---

# Related Contracts

- N/A

---

# Participating Components

- Prometheus
- AlertManager
- Grafana
- 전체 백엔드 서비스

# Trigger

모니터링 인프라는 구축되었으나 알림 규칙이 미설정 상태.

# Expected Flow

1. AlertManager 설정 파일 작성
2. 서비스별 알림 규칙 정의 (prometheus alert rules)
3. docker-compose에 AlertManager 서비스 추가
4. Grafana 알림 패널 구성
5. 테스트 알림 트리거 확인

# Edge Cases

- 알림 노이즈 방지를 위한 적절한 임계값 설정
- 서비스 재시작 시 일시적 알림 발생 억제

# Failure Scenarios

- AlertManager 설정 오류로 알림 미발송
- 임계값 설정 오류로 과도한 알림 발생

# Test Requirements

- AlertManager 설정 유효성 검증
- 알림 규칙 트리거 테스트

# Definition of Done

- [x] AlertManager 구성 완료
- [x] 알림 규칙 정의 및 동작 확인
- [x] docker-compose 통합
- [x] 문서화 완료
- [x] Ready for review
