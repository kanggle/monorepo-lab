# Task ID

TASK-BE-032

# Title

security-service — abnormal login rule 임계값 externalization (@ConfigurationProperties)

# Status

backlog

# Owner

backend

# Task Tags

- code
- adr

# depends_on

- (없음)

---

# Goal

`VelocityRule`, `GeoAnomalyRule` 등의 임계값이 현재 코드에 하드코딩되어 있는지 검증하고, `application.yml`로 이관한다. 운영 중 무배포 튜닝 가능성 확보.

---

# Scope

## In Scope

- `apps/security-service/src/main/java/com/example/security/domain/detection/VelocityRule.java`, `GeoAnomalyRule.java`, 기타 rule 파일 검토 후 하드코딩된 상수를 `DetectionRuleProperties`(`@ConfigurationProperties("security.detection")`)로 이관
- 예시 프로퍼티:
  - `security.detection.velocity.failures-per-hour-threshold`
  - `security.detection.geo.max-km-per-hour`
  - `security.detection.device.alert-on-new`
- `application.yml` 기본값 정의, env var overridable
- rule 생성자에 Properties 주입

## Out of Scope

- hot-reload (재시작 전제)
- 외부 config 서비스 (Consul/Spring Cloud Config)

---

# Acceptance Criteria

- [ ] rule 클래스에서 magic number 제거
- [ ] `application.yml`에 기본값 명시
- [ ] `application-test.yml`에서 override → 테스트 영향 확인
- [ ] 기존 rule 단위 테스트 통과 (properties 직접 주입 방식으로 수정)

---

# Related Specs

- `specs/services/security-service/architecture.md`

# Related Contracts

- (없음)

---

# Target Service

- `apps/security-service`

---

# Edge Cases

- properties 누락 시 시작 실패 (fail-fast) — `@Validated` 사용

---

# Failure Scenarios

- 잘못된 임계값(음수 등) — `@Min`/`@Max` 검증 실패 시 기동 차단

---

# Test Requirements

- Unit: rule 별 파라미터화 테스트

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added and passing
- [ ] Ready for review
