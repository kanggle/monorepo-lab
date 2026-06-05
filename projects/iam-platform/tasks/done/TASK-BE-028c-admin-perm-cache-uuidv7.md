# Task ID

TASK-BE-028c

# Title

admin-service — 권한 평가 Redis 캐시 + UUID v7 generator + 무효화

# Status

backlog

# Owner

backend

# Task Tags

- code
- deploy

# depends_on

- TASK-BE-028a

---

# Goal

admin-service 권한 평가 경로에 Redis 기반 10초 TTL 캐시를 부착하고, operator/event ID에 UUID v7을 도입해 시간순 정렬·인덱스 효율을 확보한다.

---

# Scope

## In Scope

- Redis 의존성(`spring-boot-starter-data-redis`)·config 클래스 추가. 기존 auth-service의 Redis 구성을 reference.
- `PermissionEvaluatorImpl`를 CacheAside 패턴으로 래핑: 캐시 키 `admin:operator:perm:{operator_id}`, TTL 10초, 값은 Set<String> 직렬화.
- 캐시 무효화 훅: operator의 role 변경/operator 비활성화 시점에 `invalidate(operatorId)` 호출 (관리 API 추가가 아니라 내부 서비스 메서드만 제공; 실제 관리 UI는 별도 스프린트).
- UUIDv7 generator: 공용 라이브러리(`com.github.f4b6a3:uuid-creator` 또는 동등) 또는 auth-service의 `UuidV7` 내부 구현을 libs로 승격해 재사용.
- `admin_operators.id`, `admin_actions` envelope의 `eventId`를 UUID v7으로 생성.
- 다중 인스턴스 전파 방식 결정: 이 스코프에서는 TTL 단독 의존 (10초 내 revocation 가능성 수용). Pub/Sub 기반 push invalidation은 TASK-BE-030 또는 별도 태스크.

## Out of Scope

- Admin 조직 관리 UI
- 캐시 hit/miss 메트릭 대시보드 (Phase C ops maturity)
- 외부 IAM 연동

---

# Acceptance Criteria

- [ ] Redis 컨테이너 활용하는 `PermissionEvaluatorCacheTest`에서 2회차 이상 호출 시 DB 쿼리 없음 확인
- [ ] 10초 TTL 경과 후 재조회 시 DB 쿼리 1회
- [ ] 명시 invalidation 호출 시 즉시 DB 재조회
- [ ] `admin_operators.id`, `eventId`가 UUID v7 형식(앞 48비트 ms timestamp)
- [ ] 전체 테스트 통과

---

# Related Specs

- `specs/services/admin-service/rbac.md` (§Caching 정책)

---

# Target Service

- `apps/admin-service`

---

# Edge Cases

- Redis 다운 시: evaluator가 원본 DB 경로로 graceful degrade (추가 latency 수용)
- Clock skew로 UUIDv7 monotonicity 깨짐 — RFC 9562상 허용

---

# Failure Scenarios

- 캐시 stale으로 revoke된 operator가 최대 10초 동안 계속 권한 보유 — 문서화된 제약으로 수용 (추후 push invalidation 도입 시 해소)

---

# Test Requirements

- Integration: Redis Testcontainer + cache miss/hit/TTL 검증 (Docker-gated)
- Unit: UUIDv7 생성기 RFC 9562 준수

---

# Definition of Done

- [ ] 구현·테스트 완료
- [ ] rbac.md의 "Open Issues" 섹션에서 해당 항목 제거
- [ ] Ready for review
