# Task ID

TASK-INT-018-fix

# Title

부하 테스트 docker-compose 네트워크/경로 오류 수정 및 RPS 기준 추가

# Status

done

# Owner

integration

# Task Tags

- test
- deploy

---

# Goal

TASK-INT-018 리뷰에서 발견된 이슈를 수정한다.

1. docker-compose.load-test.yml의 네트워크 설정 충돌 해결
2. Docker 볼륨 마운트와 k6 스크립트 import 경로 불일치 해결
3. SLA에 RPS 임계값 추가 (Acceptance Criteria 충족)
4. 미사용 변수 제거

---

# Scope

## In Scope

- docker-compose.load-test.yml의 `BASE_URL`을 `http://localhost:8080`으로 변경
- Docker 볼륨 마운트 구조를 k6 스크립트의 `../lib/` 상대 경로와 일치하도록 수정
- sla.md 및 k6 thresholds에 API별 최소 RPS 기준 추가
- search-load-test.js의 미사용 변수 `STATUSES` 제거

## Out of Scope

- 새 시나리오 추가
- SLA 수치 변경 (RPS 추가만 해당)

---

# Acceptance Criteria

- [ ] docker-compose.load-test.yml에서 k6 컨테이너가 gateway에 정상 접근 가능
- [ ] Docker 환경에서 k6 스크립트의 lib import가 정상 해석됨
- [ ] sla.md에 API별 RPS 기준이 정의됨
- [ ] k6 thresholds에 `http_reqs` rate 기준 추가됨
- [ ] 미사용 변수 제거됨

---

# Related Specs

- `specs/platform/testing-strategy.md`
- `specs/platform/deployment-policy.md`

# Related Skills

- N/A

# Related Contracts

- N/A

---

# Participating Components

- load-tests/

# Trigger

TASK-INT-018 리뷰에서 발견된 이슈.

# Expected Flow

1. docker-compose.load-test.yml 네트워크/URL 수정
2. 볼륨 마운트 또는 import 경로 수정
3. sla.md RPS 기준 추가
4. k6 thresholds에 RPS 추가
5. 미사용 변수 정리

# Edge Cases

- N/A

# Failure Scenarios

- N/A

# Test Requirements

- docker-compose.load-test.yml 설정의 논리적 정합성 확인

# Definition of Done

- [ ] 모든 수정 완료
- [ ] Ready for review
