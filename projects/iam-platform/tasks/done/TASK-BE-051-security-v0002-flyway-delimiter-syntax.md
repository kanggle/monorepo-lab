# Task ID

TASK-BE-051-security-v0002-flyway-delimiter-syntax

# Title

security-service V0002 — Flyway `--flyway:delimiter=//` 지시문 구문 불일치 조사·수정

# Status

ready

# Owner

backend

# Task Tags

- db
- fix

# depends_on

- (없음)

---

# Goal

E2E 런타임에서 security-service V0002__create_login_history_triggers.sql이 MySQL에서:

```
SQL State : 42000
Error Code : 1064
Message    : ... near '--flyway:delimiter=//\n\nCREATE TRIGGER ...' at line 3
```

로 실패한다. CRLF 문제는 아님(이미 LF 확인). Flyway가 `--flyway:delimiter=//` 지시문을 인식하지 못하고 MySQL에 그대로 전달. `:tests:e2e:test`가 아닌 단위/Testcontainers 테스트는 통과하는 이유 포함 조사 필요.

가설:
- Flyway Community(= OSS)는 `-- flyway:delimiter=X` 문법(공백 필수, 콜론 대신 `=`)을 요구. 현재 파일은 `--flyway:delimiter=//` (공백 없음 + 콜론 포함) → Community 미지원
- Flyway Teams/Enterprise만 지원하는 지시문

---

# Scope

## In Scope

1. 현재 빌드된 Flyway 버전 및 지원 문법 확인 (docs/릴리즈노트)
2. `--flyway:delimiter=//` → `-- flyway:delimiter=//` 또는 `DELIMITER //`(MySQL 스타일) 로 변환 검토. Flyway Community 호환 방식 선정
3. 단위/Testcontainers 테스트가 통과하는 이유 조사 — 실행 경로 차이(같은 파일을 실제로 실행하는지)
4. V0002 구문 교정
5. 로컬 compose 기동에서 Flyway V0002 정상 적용 확인

## Out of Scope

- 다른 trigger 마이그레이션(account V0004, admin V0010 등) 동일 구문 사용 여부 점검 시 발견되면 포함

---

# Acceptance Criteria

- [ ] 근본 원인 문서화
- [ ] V0002 구문 수정
- [ ] `./gradlew :apps:security-service:test` 통과
- [ ] docker compose up -d 후 security-service healthy

---

# Related Specs

- 없음

---

# Target Service

- `apps/security-service`

---

# Edge Cases

- admin/account trigger 마이그레이션이 동일 패턴 쓰는지 확인

---

# Failure Scenarios

- Flyway Community가 in-script delimiter 변경 자체를 미지원 시, trigger를 단일 statement로 재작성 필요

---

# Test Requirements

- security-service 테스트

---

# Definition of Done

- [ ] 분석 + 수정 + compose healthy
- [ ] Ready for review
