# Task ID

TASK-BE-335

# Title

Fix **운영자 정지/활성화·비밀번호·프로파일 변경이 `200`을 반환하지만 DB 에 영속되지 않는 silent-write-loss 버그**. `JpaAdminOperatorAdapter` 의 load-mutate-save 메서드(`changeStatus` / `changePasswordHash` / `changeFinanceDefaultAccountId`)가 dirty-checking flush 에 의존하는데, 해당 요청의 메인 트랜잭션이 (OSIV/flush-mode 정황으로) commit 시 auto-flush 를 수행하지 않아 dirty UPDATE 가 조용히 사라진다. `save` → `saveAndFlush` (명시 flush)로 해소.

# Status

review

# Owner

backend-engineer (admin-service)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- test

---

# Dependency Markers

- **root cause (런타임 진단, 재현됨)**: `PATCH /api/admin/operators/{id}/status` 가 `previousStatus:ACTIVE→currentStatus:SUSPENDED` 로 **200** 을 반환하고 `admin_actions` 감사행도 기록되는데, `admin_operators.status` 는 ACTIVE 그대로 + `version=0`(UPDATE 한 번도 안 일어남). 직접 재현(운영자 로그인 토큰 + curl PATCH, 콘솔/브라우저 무관)으로 확정. **메커니즘**: ⑴ 생성(`createOperator`)은 `@GeneratedValue(IDENTITY)` 라 INSERT 가 **즉시** 실행 → 영속(버그 가림). ⑵ 감사(`AdminActionAuditor.record`)는 `@Transactional(REQUIRES_NEW)` → 별도 writable tx 로 commit → 영속. ⑶ 상태/비번/프로파일 변경은 managed-entity dirty UPDATE → commit-time **auto-flush 필요** → 메인 tx 가 flush 를 안 해(readOnly flush-mode/OSIV 정황) dirty UPDATE 유실. 메인 tx 자체는 commit 됨(생성 INSERT 가 같은 패턴으로 영속되는 것이 증거)이라, **명시 flush** 면 UPDATE 가 영속된다.
- **scope**: `JpaAdminOperatorAdapter.changeStatus` / `changePasswordHash` / `changeFinanceDefaultAccountId` — 3개 load-mutate-save 메서드 동일 결함 클래스(역할 grant/revoke 는 INSERT/DELETE 라 무관, 생성은 IDENTITY INSERT 라 무관).

# Goal

운영자 정지/활성화·자기 비밀번호 변경·프로파일(defaultAccountId) 변경이 실제로 `admin_operators` 에 영속된다(정지 → `status=SUSPENDED`, `version` 증가). `PATCH /status` 의 200 응답이 실제 상태와 일치한다.

# Scope

## In Scope

- `JpaAdminOperatorAdapter.changeStatus` / `changePasswordHash` / `changeFinanceDefaultAccountId` — `operatorRepository.save(entity)` → `operatorRepository.saveAndFlush(entity)` (managed-entity dirty UPDATE 를 메인 tx 내에서 즉시 flush; FlushMode.MANUAL 에서도 명시 flush 는 수행됨, MySQL readOnly 힌트는 write 비차단).
- Testcontainers IT: 정지 후 재조회로 `status=SUSPENDED` + `version` 증가를 단언(dirty-update flush 회귀 게이트).

## Out of Scope

- OSIV(`spring.jpa.open-in-view`) 전역 비활성화 등 더 넓은 트랜잭션 리아키텍처 — 본 task 는 표면화한 3 메서드의 결정적 최소 수정. (readOnly flush-mode 의 정확한 출처 추적/제거는 별도 후속.)
- 콘솔/contract 변경 없음.

# Acceptance Criteria

- [ ] **AC-1** `changeStatus`/`changePasswordHash`/`changeFinanceDefaultAccountId` 가 `saveAndFlush` 로 dirty UPDATE 를 메인 tx 내에서 flush.
- [ ] **AC-2** Testcontainers IT: 운영자 정지 → 재조회 `status=SUSPENDED` + `version` 증가(영속 회귀 게이트).
- [ ] **AC-3** 데모 라이브 재현 해소: globex 운영자 정지 → DB `status=SUSPENDED`, `version=1`. [live]
- [ ] **AC-4** admin-service `:test` GREEN; admin-service healthy; GAP Testcontainers IT GREEN.

# Related Specs

- `admin-api.md` § `PATCH /api/admin/operators/{id}/status`. `rbac.md` (operator lifecycle). `data-model.md` (admin_operators).

# Edge Cases

- 정지 대상이 자기 자신 → `SELF_SUSPEND_FORBIDDEN`(불변, 영속 이전 단계).
- 이미 SUSPENDED → `STATE_TRANSITION_INVALID`(불변).
- saveAndFlush 가 낙관적 락 충돌 시 즉시 표면화(기존 at-commit 보다 빠른 실패 — 동작 개선).

# Failure Scenarios

- flush 가 readOnly 로 막히면 IT 가 RED(현재 데모 재현과 동일) → 회귀 게이트로 방지.

# Test Requirements

- admin-service Testcontainers IT(정지 영속 + version 증가). `./gradlew :projects:global-account-platform:apps:admin-service:test`.
- Local: admin-service(host bootJar→image) 재빌드+재기동; 직접 재현(로그인 토큰 + PATCH /status)으로 `status=SUSPENDED`/`version=1` 확인.

# Definition of Done

- [ ] 어댑터 3 메서드 saveAndFlush + IT.
- [ ] admin-service `:test` GREEN; healthy; GAP IT GREEN.
- [ ] Local 재빌드+재기동; 정지 영속 확인.
- [ ] Task md + `projects/global-account-platform/tasks/INDEX.md` 갱신.
- [ ] Reviewed + merged.

---

분석=Opus 4.8 / 구현=Opus(직접). 사용자 "정지 했는데 status active"(2026-06-04) 진단 → managed-entity dirty UPDATE 가 commit-time auto-flush 누락으로 유실(200+audit 가 가림). 메타: ① 200 응답·감사행이 있어도 실제 영속 확인(version/updated_at) 없이는 성공 단정 금지. ② IDENTITY INSERT(생성)는 즉시 실행돼 같은 tx 의 flush 결함을 가린다 — UPDATE 경로가 진짜 신호. ③ 명시 `saveAndFlush` 가 flush-mode 무관 최소 수정.
