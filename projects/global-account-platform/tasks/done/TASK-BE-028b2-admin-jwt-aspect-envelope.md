# Task ID

TASK-BE-028b2

# Title

admin-service — JWT claim 정리, @PreAuthorize 제거, aspect 단일화, outbox envelope reshape

# Status

backlog

# Owner

backend

# Task Tags

- code
- event
- test

# depends_on

- TASK-BE-028b1

---

# Goal

028b1이 스키마를 정리한 뒤 이어지는 애플리케이션 레이어 작업. JWT 계약을 스펙에 맞추고, 권한 체크를 annotation 기반 단일 경로로 수렴하며, outbox 이벤트를 canonical envelope으로 재구성한다.

---

# Scope

## In Scope

### JWT claim 정리
- operator JWT 발급/검증에서 `scope == "admin"` → `token_type == "admin"` 전환
- `roles` claim 제거 (이미 evaluator는 DB 사용)
- `OperatorContext`에서 `Set<OperatorRole> roles` 필드 제거, `String jti` 필드 추가 (JWT jti 캡처)
- `OperatorRole` enum 삭제 및 모든 참조 제거
- `OperatorJwtTestFixture`: `sub` + `jti` + `token_type=admin`만 발급

### @PreAuthorize 제거 + aspect 단일화
- 컨트롤러에서 `@PreAuthorize` 어노테이션 전수 삭제
- use-case 내부 `requireRole(...)` 호출 제거
- `RequiresPermissionAspect`:
  - `@Order(200)` 명시 (Spring Security `MethodSecurityInterceptor` 기본값보다 낮은 우선순위임을 주석으로 인용)
  - deny-by-default 가드레일 추가: mutation controller method(@PostMapping/@PutMapping/@PatchMapping/@DeleteMapping)에 `@RequiresPermission`이 없으면 aspect가 `permission_used="<missing>"`로 DENIED 기록 + `PermissionDeniedException` throw

### AuditController cross-permission DENIED
- 현재 method body에서 `PermissionDeniedException`만 throw → `auditor.recordDenied(AUDIT_QUERY, "audit.read+security.event.read", endpoint, method, null)` 호출 후 throw

### Audit envelope reshape
- 신규 `apps/admin-service/src/main/java/com/example/admin/application/util/AdminPiiMaskingUtils.java` — security-service PiiMaskingUtils 패턴 참조. email/accountId/phone 마스킹. 단위 테스트 포함.
- `AdminEventPublisher.publishAdminActionPerformed` envelope을 canonical shape로 재구성:
  ```
  {
    "eventId": uuid,
    "occurredAt": iso8601,
    "actor": {"type":"operator","id": operator_uuid, "sessionId": jti},
    "action": {"permission": permission_key, "endpoint": uri, "method": http_method},
    "target": {"type": ACCOUNT|SESSION|AUDIT_QUERY|TARGET, "id": string, "displayHint": masked_or_null},
    "outcome": SUCCESS|FAILURE|DENIED,
    "reason": nullable
  }
  ```
- 엔드포인트/메서드 캡처: aspect에서 `HttpServletRequest` 주입 → `AdminActionAuditor`로 전달하는 context(StartRecord/DeniedRecord) 확장
- `target.displayHint`: target_type=ACCOUNT일 때 `AdminPiiMaskingUtils.maskEmail/maskAccountId` 호출
- `AdminActionAuditor.recordDenied` target_type 파생: actionCode → ACCOUNT/SESSION/AUDIT_QUERY 매핑 테이블 도입

### 테스트 스윕
- OperatorJwtTestFixture 업데이트 후 영향 테스트 일괄 수정
- AdminIntegrationTest: admin_operators + admin_operator_roles 직접 seed (기존에 roles claim 의존했다면 제거)
- 신규 테스트:
  - `AdminPiiMaskingUtilsTest` (unit)
  - `AdminEventPublisherCanonicalEnvelopeTest` (slice — 새 envelope shape 검증)
  - `AuditDeniedCrossPermissionTest` (slice — `admin_actions` DENIED row + 합성 permission_used 확인)

### 028a TODO 제거
- `rg "TODO\(TASK-BE-028b\)" apps/admin-service/` 결과 0건 되도록 모두 처리

### 028b1 리뷰에서 이관된 항목
- **`trg_admin_actions_finalize_only` 트리거 guard 확장**: 현재 트리거는 V0001 컬럼만 보호하고 V0005가 추가한 `operator_id`, `permission_used`를 guard하지 않음. 두 컬럼도 mutation 시 예외 발생하도록 트리거 본문 확장 (신규 마이그레이션 V00XX으로 CREATE OR REPLACE).
- **`data-model.md` 컬럼명 수렴**: spec은 `occurred_at`, 실제 테이블은 `started_at`/`completed_at`. 실제를 canonical로 받아들이고 data-model.md 전반(인덱스 선언·envelope 매핑·분류 요약)을 `started_at` 기준으로 수정. 또는 컬럼 rename이 더 적절하면 마이그레이션으로 rename — 판단은 envelope 매핑 시점에 결정.
- **`admin_operators.version` 타입 정렬**: spec은 `INT`, 실제 DDL은 `BIGINT`. JPA entity 필드 타입(Long)과 spec(INT) 중 하나로 수렴. 기존 데이터 영향이 없으므로 `INT`로 ALTER 권장(spec 준수).

## Out of Scope

- Redis 10s 권한 캐시 (028c)
- UUIDv7 generator (028c)

---

# Acceptance Criteria

- [ ] `rg "@PreAuthorize" apps/admin-service/src/main/` → 0 hits
- [ ] `rg "OperatorRole" apps/admin-service/src/main/` → 0 hits
- [ ] `rg "TODO\(TASK-BE-028b\)" apps/admin-service/` → 0 hits
- [ ] `rg "scope.*admin" apps/admin-service/src/main/` → 0 (token_type 전환 확인)
- [ ] Aspect가 `@RequiresPermission` 누락 mutation 엔드포인트에서 런타임 403 + DENIED row 생성
- [ ] AuditController cross-permission DENIED에서 `admin_actions` row 생성
- [ ] Outbox payload가 canonical envelope 준수 (actor/action/target/outcome/reason + eventId + occurredAt)
- [ ] `AdminPiiMaskingUtils`의 displayHint 적용으로 target.id가 이메일 원문 노출 없음
- [ ] `trg_admin_actions_finalize_only`가 `operator_id`, `permission_used` mutation을 차단
- [ ] `data-model.md` 컬럼명(`started_at`/`completed_at`)이 실제 테이블과 일치
- [ ] `admin_operators.version`이 spec과 타입 정렬
- [ ] `./gradlew :apps:admin-service:test` 통과

---

# Related Specs

- `specs/services/admin-service/rbac.md`
- `specs/services/admin-service/data-model.md` (envelope 매핑 테이블)
- `rules/traits/regulated.md` R4 (마스킹)
- `rules/traits/audit-heavy.md` A2 (envelope)

# Related Contracts

- `specs/contracts/http/admin-api.md`

---

# Target Service

- `apps/admin-service`

---

# Edge Cases

- `target.id`가 UUID 등 PII가 아닌 경우 — 마스킹하지 않고 그대로 `displayHint=null` 또는 원문
- JWT jti가 없는 과거 토큰 — OperatorContext.jti nullable로 두고 envelope에서 `actor.sessionId=null` 허용
- `@RequiresPermission` 누락 GET 엔드포인트는 deny-by-default 대상 아님 (mutation만 가드)

# Failure Scenarios

- `HttpServletRequest` 주입이 aspect에서 실패 — request-scoped bean 구성 확인, ServletWebRequest로 fallback

---

# Test Requirements

- 위 Acceptance Criteria의 grep 4건 자동화 또는 수동 검증
- 신규 3개 테스트 클래스
- 기존 테스트 regression 없음

---

# Definition of Done

- [ ] 구현·테스트 완료
- [ ] 모든 grep 기준 통과
- [ ] Ready for review

---

## Review Outcome

**Reviewer**: code-reviewer agent
**Review date**: 2026-04-14
**Verdict**: FIX REQUIRED

Two issues promoted to a fix task `TASK-BE-028b2-fix-operator-id-fk-and-phone-mask.md`:

### Critical
- `apps/admin-service/src/main/java/com/example/admin/application/AdminActionAuditor.java:73` — `admin_actions.operator_id` BIGINT FK is always written as `null` in all three audit write paths (`recordStart`, `recordDenied`, `record`). `data-model.md` declares this column `NOT NULL`. DB migration V0008 also created it nullable, contradicting the spec. Fix task: resolve external UUID → internal BIGINT via `AdminOperatorJpaRepository.findByOperatorId` before each INSERT; add V0011 migration to enforce `NOT NULL` at DB level.

### Warning
- `apps/admin-service/src/main/java/com/example/admin/application/util/AdminPiiMaskingUtils.java:57-62` + `src/test/.../AdminPiiMaskingUtilsTest.java:47-51` — `maskPhone` retains only the last 2 digits but `rules/traits/regulated.md` R4 canonical format is `010-****-1234` (last 4 digits). Fix: change tail length to 4 and update tests.

All other acceptance criteria (grep gates, envelope shape, cross-permission DENIED path, deny-by-default aspect, V0010 trigger guard, version INT migration, PiiMaskingUtils placement, OperatorRole deletion, @PreAuthorize removal, TODO sweep) are satisfied. Implementation is structurally sound.
