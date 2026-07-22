# Trait: multi-tenant

> **Activated when**: `PROJECT.md` includes `multi-tenant` in `traits:`.

---

## Scope

하나의 코드베이스/배포 단위가 여러 고객 조직(tenant)을 동시에 서비스하며, 각 tenant 의 데이터·메타·설정이 **논리적 또는 물리적으로 격리**되어야 한다. SaaS·내부 IdP·B2B 통합 플랫폼 등에서 활성.

"한 tenant 의 사고가 다른 tenant 에 새지 않는다" 가 설계의 1번 원칙. 격리는 데이터 ROW 레벨에서 시작해 cache 키, 메트릭 차원, 메시지 envelope 까지 종방향으로 관통한다.

적용 범위:
- **필수 적용**: 모든 데이터 영구화 경로 (DB / cache / 검색 인덱스 / 객체 스토리지 키)
- **필수 적용**: 모든 외부 노출 경로 (REST / 이벤트 / 웹훅 페이로드)
- **조건부**: cross-tenant 운영 도구 (admin / SUPER_ADMIN tenant context)
- **제외**: tenant 개념이 의미 없는 시스템 메타 (예: monitoring 자체, build artifact)

전형적으로 `regulated` / `audit-heavy` 와 함께 선언되며, 본 trait 의 격리 보장이 그 두 trait 의 컴플라이언스 요건을 떠받친다.

---

## Mandatory Rules

### M1. 모든 영속 데이터 row 에 `tenant_id` (NOT NULL)

데이터 분리 전략은 다음 중 하나를 선택:

- **shared-database / shared-schema (가장 일반적)**: 모든 테이블에 `tenant_id` 컬럼 + WHERE 절 강제
- **shared-database / per-tenant-schema**: tenant 별 schema 분리 (`tenant_a.users`, `tenant_b.users`)
- **per-tenant-database**: tenant 별 DB 인스턴스 (대형 enterprise 한정)

본 monorepo 의 기본은 **shared-database / shared-schema + tenant_id NOT NULL**. 어떤 전략이든 다음은 공통 강제:

- 신규 테이블 = `tenant_id VARCHAR(32) NOT NULL` + `INDEX(tenant_id, ...)` 필수
- 기존 테이블 마이그레이션 시 backfill → ALTER → SET NOT NULL 의 3-단계 절차 (downtime 0)
- 객체 스토리지 키 prefix = `<tenant_id>/...`
- Redis key prefix = `<tenant_id>:...`
- 검색 인덱스 = tenant 별 별도 인덱스 또는 모든 문서에 tenant_id 필드 + 쿼리 시 강제 필터

### M2. **3-layer tenant isolation** (방어 깊이)

요청 경로에 3 단계 가드:

1. **Gateway / API edge**: JWT `tenant_id` claim 검증 + non-allowed tenant → 403 `TENANT_FORBIDDEN`
2. **Service-level Spring Security filter**: `TenantClaimEnforcer` 같은 final guard — 게이트웨이를 우회한 internal 호출에도 적용
3. **Persistence layer**: 모든 query 에 `WHERE tenant_id = :ctx.tenantId` 자동 주입 (Hibernate `@Filter` 또는 repository derived methods)

3 layer 중 하나만 누락되어도 cross-tenant leak 가능 — 3 layer 모두 통과해야 데이터 노출.

### M3. **404 over 403** for cross-tenant reads (existence leak 방지)

다른 tenant 의 리소스 조회는:

- **Cross-tenant read** (다른 tenant 의 ID 로 조회) → `Optional.empty()` → **404 NOT_FOUND** (NOT 403)
- 이유: 403 은 "리소스가 존재하지만 권한 없음" 신호 — 다른 tenant 의 ID enumeration 가능성 노출
- 단 **cross-tenant write** 는 명시적으로 403 — write 의도 자체가 boundary 위반 신호

예외: SUPER_ADMIN role 또는 platform-internal context 는 cross-tenant 조회 허용 (별도 audit-log 강제).

### M4. Cross-tenant **enumeration** 방지

- 검색 / 리스트 endpoint 는 tenant 컨텍스트 없이 호출 시 빈 결과 또는 401 (default deny)
- pagination cursor 에 tenant_id 인코딩 필수 (디코딩 시 mismatch → 400 invalid cursor)
- `GET /resources?id=<other-tenant-id>` 같은 ID query parameter 우회 차단 (path variable 도 동일 적용)

### M5. Tenant context **propagation across async boundaries**

- Kafka envelope / 메시지 / 이벤트 = tenant_id 필드 NOT NULL (envelope schema 에 명시)
- 비동기 워커 / scheduler / 백그라운드 잡 = SecurityContext 부재 환경에서 tenant_id 를 명시적으로 메서드 인자 또는 ThreadLocal 로 전달
- outbox row = tenant_id 컬럼 + Kafka header 동시 carry (consumer side 에서 검증)

### M6. Cross-tenant leak 회귀 테스트 **필수**

다음 시나리오에 대한 통합 테스트가 모든 multi-tenant service 에서 강제:

- `MultiTenantIsolationTest` — tenant A 의 토큰으로 tenant B 의 ID 조회 → 404 (READ 격리)
- `MultiTenantIsolationTest` — tenant A 의 토큰으로 tenant B 의 리소스 변경 시도 → 403 (WRITE 격리)
- `CrossTenantEventConsumptionTest` (event consumer 인 경우) — tenant_id 가 일치하지 않는 envelope 은 silent drop + 메트릭만 increment
- 이 테스트가 1개라도 빠진 service 의 PR 은 `qa-engineer` 가 review block

### M7. Per-tenant **rate limit / quota / 백압**

- API rate limit 은 (tenant_id, route_id) tuple 키 — 한 tenant 의 burst 가 다른 tenant 의 latency 영향 X
- DB connection pool / Kafka producer 가 한 tenant 의 폭주에 cross-tenant 으로 노출되는 경로 점검
- 무한 query (예: `LIMIT` 없는 list endpoint) 금지 — 단일 tenant 가 cross-tenant DBMS 자원 고갈 가능

---

## Overrides

해당 없음. common rule 과 충돌 없음. 단 `transactional` T1 의 idempotency 키는 tenant_id 와 함께 namespace 형성 (`<tenant>:<endpoint>:<idem-key>`) — 본 trait 와 자연 부합.

---

## Forbidden Patterns

- 단일 admin 콘솔이 cross-tenant 데이터 화면 노출하면서 audit log 미적용 — leak 사고 시 책임 추적 불가
- "tenant_id 컬럼만 추가" 후 query 에 `WHERE tenant_id = ?` 누락 — DB 레벨 격리 실패
- JWT 의 tenant_id claim 만 검증하고 path/body 의 tenant_id 와 cross-check 안 함 — claim 변조로 우회
- 대시보드 집계 쿼리가 tenant context 없이 전체 row 집계 — 집계 결과로 다른 tenant 존재/규모 추정 가능
- Redis 키에 tenant prefix 없이 단일 keyspace 사용 — eviction 정책으로 한 tenant 가 다른 tenant 의 캐시 무효화
- 다른 tenant 의 ID enumeration 후 403 응답 받아 존재 확인 — 404 over 403 룰 위반
- async worker / batch job 이 SecurityContext 없는 상태에서 tenant context 추론 누락 — 첫 row 의 tenant 로 모든 row 처리
- multi-tenant 가드 부재 service 가 outbox 통해 cross-tenant 이벤트 publish — consumer 측에서 tenant filter 우회
