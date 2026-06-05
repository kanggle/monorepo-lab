# Task ID

TASK-BE-043-security-v0002-trigger-crlf

# Title

security-service V0002 trigger 마이그레이션 CRLF → LF 정규화 + .gitattributes 강화

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

E2E 런타임 중 security-service V0002__create_login_history_triggers.sql 이 Flyway delimiter 지시문 파싱에서 실패함:

```
Error Code : 1064
Message    : ... syntax ... near '--flyway:delimiter=//\r\n\r\nCREATE TRIGGER ...'
```

원인: Windows에서 CRLF로 저장된 파일을 Flyway가 delimiter 지시문 줄에서 `\r`을 그대로 넣어 다음 문장과 붙여 파싱한다. 모든 `*.sql` 은 LF로 고정돼야 한다.

---

# Scope

## In Scope

1. `apps/security-service/src/main/resources/db/migration/V0002__create_login_history_triggers.sql` 라인 엔딩 LF로 재저장
2. 저장소 전체 `*.sql` 라인 엔딩 전수 확인 — CRLF로 저장된 파일이 있으면 LF로 정규화
3. `.gitattributes` 에 `*.sql text eol=lf` 명시 (없으면 신설, 있으면 강화)
4. Flyway delimiter 지시문을 쓰는 trigger/function 마이그레이션 전수 재검증 (admin, account도 유사 패턴 있는지)

## Out of Scope

- 트리거/함수 로직 변경
- 다른 파일 타입 eol 규칙

---

# Acceptance Criteria

- [ ] security-service V0002 마이그레이션 LF 종료
- [ ] `.gitattributes`에 `*.sql text eol=lf` 항목 존재
- [ ] e2e 프로파일에서 security-service 기동 시 V0002 마이그레이션 통과
- [ ] 기타 trigger/function 사용 마이그레이션 동일 패턴 확인

---

# Related Specs

- `platform/testing-strategy.md` (Testcontainers MySQL이 같은 파일을 읽으므로 CI에도 영향)

---

# Target Service

- `apps/security-service` (+ `.gitattributes` 저장소 루트)

---

# Edge Cases

- 이미 적용된 로컬 환경이 있는 경우 `down -v`로 깨끗이 재기동 필요

---

# Failure Scenarios

- git autocrlf 가 Windows에서 다시 CRLF로 체크아웃되는 경우 → .gitattributes가 해당 파일을 LF로 강제

---

# Test Requirements

- compose up 후 security-service healthy

---

# Definition of Done

- [ ] 파일 정규화 + .gitattributes 반영
- [ ] compose 기동 확인
- [ ] Ready for review
