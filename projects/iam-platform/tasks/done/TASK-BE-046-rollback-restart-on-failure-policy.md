# Task ID

TASK-BE-046-rollback-restart-on-failure-policy

# Title

compose `restart: on-failure:5` 롤백 — half-migration/seed 누적 방지, DNS TTL 설정은 유지

# Status

ready

# Owner

backend

# Task Tags

- deploy
- fix

# depends_on

- (없음)

---

# Goal

TASK-BE-044에서 DNS 간헐 실패 대비로 추가한 `restart: on-failure:5`가 다른 경로로 해악을 유발함이 런타임 검증에서 확인됐다:

- security-service: V0002 trigger 마이그레이션 첫 시도가 MySQL 연결 지연으로 실패 → `restart` 재시도 시 "Detected failed migration to version 0002" validate 차단
- admin-service: dev seed V0014 일부가 커밋된 상태에서 `restart` 재시도 시 `admin_roles.uk_admin_roles_name` 중복 키 오류 영구 failure

즉 restart 정책이 half-committed state를 복구 불가 상태로 악화시킨다. DNS TTL=0 설정은 유효하므로 유지하고, restart 정책만 되돌린다.

---

# Scope

## In Scope

1. `docker-compose.e2e.yml` — `x-app-restart: &app-restart` anchor 및 4개 서비스의 `<<: *app-restart` 참조 제거
2. `JAVA_TOOL_OPTIONS` 설정(BE-044 유지분)은 그대로 둔다
3. `tests/e2e/README.md` Known Limitations 섹션 갱신 — DNS 이슈 발생 시 `docker compose down -v && up -d` 권장, restart 정책은 의도적으로 미사용

## Out of Scope

- BE-045의 MySQL 커넥션 burst 튜닝 (독립 태스크)
- 서비스 코드 변경

---

# Acceptance Criteria

- [ ] compose 파일에 restart 정책 참조 없음
- [ ] JAVA_TOOL_OPTIONS 는 유지 (BE-044 유효)
- [ ] BE-045 적용과 무관하게 단일 `up -d` 사이클에서 failure 시 Exited 로 멈추고 재시도 루프로 인한 half-state 확산 없음을 로그로 확인

---

# Related Specs

- 없음

---

# Target Service

- 루트 `docker-compose.e2e.yml`
- `tests/e2e/README.md`

---

# Edge Cases

- BE-045 적용 후 DNS 이슈가 재등장하면 JAVA_TOOL_OPTIONS + 서비스 depends_on 체인으로 충분한지 재평가

---

# Failure Scenarios

- 없음 — 되돌림 성격

---

# Test Requirements

- 수동 검증: restart 없이 down -v / up -d 5회에서 Exited-then-no-retry 확인

---

# Definition of Done

- [ ] 롤백 적용 + README 갱신
- [ ] Ready for review
