# admin-service — RBAC Model

## Purpose

admin-service가 하드코딩된 `SUPER_ADMIN` 전제를 대체하여 **역할 기반 권한 평가(RBAC)**를 적용하기 위한 스펙. 운영자(`admin_operators`)는 하나 이상의 역할(`admin_roles`)을 부여받고, 역할은 권한 키(`admin_role_permissions`)의 집합을 보유한다. HTTP 엔드포인트는 필요한 권한 키를 선언하며, 요청 처리 전에 operator의 권한 합집합과 대조하여 통과/거부를 결정한다.

이 문서는 [specs/services/admin-service/architecture.md](./architecture.md)의 하위 컴포넌트이며, 테이블 DDL은 [specs/services/admin-service/data-model.md](./data-model.md)에서 선언된다. 본 문서는 **행동 규칙**(매트릭스, 평가 알고리즘, seed 데이터, 기록 정책, JWT claim 규약)만 다룬다.

구현은 TASK-BE-028에서 수행한다. 본 태스크(TASK-BE-027)는 specs-only.

---

## Design Decisions

### D1. Permission은 키 문자열, Role은 키의 집합

권한 키는 UPPER dotted 형식(`account.lock`, `audit.read`). role은 해당 키들의 **명시적 집합**(bitmask/계층 없음). 복수 role은 **합집합**으로 평가한다 (task Edge Cases 명시).

### D2. Missing-annotation은 deny default

컨트롤러 메서드에 `@RequiresPermission`이 선언되지 않은 경우 — **deny** (fail-closed). 실수로 권한 없이 배포된 신규 endpoint를 자동 차단하기 위한 guardrail. 의도적 public endpoint는 `@RequiresPermission(PUBLIC)` 같은 명시적 annotation(TASK-BE-028 구현 세부)으로 예외 처리한다.

`admin_actions.permission_used`에는 sentinel `"<missing>"`을 기록하여 배포 후 즉시 감지 가능하도록 한다.

### D3. DENIED도 감사 row 1건으로 기록

권한 거부는 `admin_actions`에 `outcome = DENIED` row로 기록한다. 공격자가 권한 탐색(probe)을 수행해도 모두 감사 로그에 남는다 ([rules/traits/audit-heavy.md](../../../rules/traits/audit-heavy.md) A1·A5).

**기록 규칙 — 1 request = 1 row (no dedup)**:
- 동일 operator가 같은 endpoint를 짧은 시간에 반복 호출(spray)해도 **요청 단위로 N row 기록**한다.
- 중복 제거(dedup)·샘플링·집계 기록을 수행하지 않는다. 감사 완전성 우선.
- rate limiting은 별도 레이어(Redis 기반 operator quota, [architecture.md](./architecture.md) Integration Rules)가 담당하며, rate limit으로 차단된 요청도 차단 시점 기준 1건씩 DENIED 기록을 남긴다.

### D4. Operator 식별자는 JWT `sub` 클레임

admin-service의 operator JWT는 일반 사용자 JWT와 **분리된 발급 경로/서명 키**를 사용한다. 운영자 식별자는 JWT **`sub` 클레임**에 `admin_operators.operator_id` (UUID v7)를 실어 전달한다.

| 클레임 | 값 | 비고 |
|---|---|---|
| `sub` | `admin_operators.operator_id` (UUID v7) | 운영자 식별자 (canonical) |
| `token_type` | `"admin"` | 일반 user token과 구분. 다른 값은 admin-service에서 전부 401 |
| `iss` | `"admin-service"` | admin-service 자체 IdP가 발급한 토큰임을 식별. 다른 issuer 값은 401 ([architecture.md](./architecture.md) Admin IdP Boundary 참조). |
| `exp` / `iat` / `jti` | 표준 | `jti`는 `admin.action.performed` outbox envelope의 `actor.sessionId` 값으로 사용된다 ([data-model.md](./data-model.md) `admin.action.performed` Event Envelope 절). admin-service는 자체 세션 저장소를 두지 않으므로 `jti`가 session 식별 용도로 재활용된다. |

**선택 이유 (vs. 별도 커스텀 클레임)**:
- `sub`가 표준 JWT 클레임이며 Spring Security `Authentication.getName()`이 기본으로 참조하여 코드가 단순.
- `token_type = "admin"` 분리로 user token과의 혼용은 이미 차단되므로 `sub`를 재사용해도 충돌 없음.
- `roles` / `permissions`는 JWT에 싣지 **않는다**. role 변경 즉시 반영을 위해 매 요청마다 DB 조회(또는 짧은 TTL 캐시)로 해석한다 (D5).

### D5. Role·Permission 해석은 request-time DB 조회

JWT에 role/permission을 embed하면 role revoke 즉시성이 깨진다. 매 요청마다 `operator_id → admin_operator_roles → admin_role_permissions`를 조인하여 permission 집합을 계산한다. 성능 필요 시 Redis에 **10초 TTL** 단기 캐시를 두되, role 변경 API는 해당 캐시 키를 명시적 invalidate해야 한다 (TASK-BE-028 구현 결정).

---

## Permission Keys

본 태스크에서 정의되는 권한 키 catalog. 다른 키는 본 문서 업데이트 없이 도입 금지.

| 키 | 설명 | 대상 endpoint |
|---|---|---|
| `account.read` | 전체 계정 목록 페이지네이션 조회 | `GET /api/admin/accounts` (email 파라미터 없음) |
| `account.lock` | 계정 강제 잠금 | `POST /api/admin/accounts/{accountId}/lock` |
| `account.unlock` | 계정 잠금 해제 | `POST /api/admin/accounts/{accountId}/unlock` |
| `account.force_logout` | 특정 계정의 모든 세션 강제 종료 | `POST /api/admin/sessions/{accountId}/revoke` |
| `audit.read` | 통합 감사 로그 조회 | `GET /api/admin/audit` |
| `security.event.read` | security-service의 로그인 이력·의심 이벤트 조회 | `GET /api/admin/audit` (source=login_history|suspicious 필터 사용 시 추가 요구) |
| `operator.manage` | 운영자 계정 생성·역할 부여·상태 변경 | `GET /api/admin/operators`, `POST /api/admin/operators`, `PATCH /api/admin/operators/{operatorId}/roles`, `PATCH /api/admin/operators/{operatorId}/status` |

**Annotation**: 각 endpoint에는 `@RequiresPermission("account.lock")` 형식으로 단일 키를 선언한다. 복수 키가 필요한 경우(예: `GET /api/admin/audit`가 source에 따라 `audit.read` 또는 `security.event.read`를 요구)는 컨트롤러 레벨에서 최소 공통 권한을 선언하고, 내부에서 파라미터별 추가 권한을 재검사한다 (TASK-BE-028 구현).

Permission 키 오탈자 방지를 위해 문자열 상수 클래스를 TASK-BE-028에서 도입한다 (task Failure Scenarios).

---

## Seed Roles

마이그레이션 시점에 투입되는 기본 역할. role 이름은 `admin_roles.name`의 UNIQUE 값.

| Role | 보유 권한 | 의도 |
|---|---|---|
| `SUPER_ADMIN` | `account.read`, `account.lock`, `account.unlock`, `account.force_logout`, `audit.read`, `security.event.read`, `operator.manage` | 전체 권한 |
| `SUPPORT_READONLY` | `account.read`, `audit.read`, `security.event.read` | CS L1. 조회 전용 |
| `SUPPORT_LOCK` | `account.lock`, `account.unlock`, `account.force_logout`, `audit.read` | CS L2. 계정 제어 + 감사 조회. security 이벤트는 열람 불가 |
| `SECURITY_ANALYST` | `audit.read`, `security.event.read`, `account.force_logout` | 보안팀. 의심 세션 긴급 종료 가능, 계정 lock/unlock은 CS 경유 |

### Seed Matrix (role × permission)

| permission \ role | SUPER_ADMIN | SUPPORT_READONLY | SUPPORT_LOCK | SECURITY_ANALYST |
|---|:-:|:-:|:-:|:-:|
| `account.read` | ✅ | ✅ | ❌ | ❌ |
| `account.lock` | ✅ | ❌ | ✅ | ❌ |
| `account.unlock` | ✅ | ❌ | ✅ | ❌ |
| `account.force_logout` | ✅ | ❌ | ✅ | ✅ |
| `audit.read` | ✅ | ✅ | ✅ | ✅ |
| `security.event.read` | ✅ | ✅ | ❌ | ✅ |
| `operator.manage` | ✅ | ❌ | ❌ | ❌ |

이 매트릭스가 `admin_role_permissions` seed 데이터의 canonical source이다. TASK-BE-028의 Flyway seed script는 본 표를 기계적으로 반영해야 한다.

### Seeding Strategy

- `V00NN+2__seed_admin_roles_and_permissions.sql`에서 `INSERT ... ON CONFLICT DO NOTHING`(MySQL은 `INSERT IGNORE`)으로 idempotent 투입.
- operator seed는 환경별로 분기: dev/staging은 고정 seed operator 1건, production은 수동 provisioning (seed script는 role/permission만 투입).
- role 이름 변경은 금지. 신규 role 추가는 본 rbac.md 업데이트 + 신규 Flyway migration 쌍으로만.

---

## Permission Evaluation Algorithm

요청 흐름에서 권한 검사는 `OperatorAuthenticationFilter` 이후, `AdminActionAuditor.begin(...)` 이전에 수행된다.

```
function evaluate(request, annotationPermission):
    # 1. operator 해석
    jwt = extractBearerToken(request)
    if jwt is null or invalid:
        respond 401 TOKEN_INVALID
        return  # audit row 없음 — 인증 실패는 upstream 필터가 처리

    operatorExternalId = jwt.claim("sub")
    tokenType = jwt.claim("token_type")
    if tokenType != "admin":
        respond 401 TOKEN_INVALID

    operator = admin_operators.findByOperatorId(operatorExternalId)
    if operator is null or operator.status != ACTIVE:
        respond 401 TOKEN_INVALID
        return

    # 2. 권한 집합 계산 (union across roles)
    roles  = admin_operator_roles.findByOperatorPk(operator.id)          # List<role_id>
    perms  = admin_role_permissions.findByRoleIds(roles).map(p -> p.permission_key)
    permissionSet = perms.toSet()   # union

    # 3. annotation 확인
    if annotationPermission is null:
        # D2: missing-annotation = deny
        recordDenied(operator, request, permissionUsed="<missing>")
        respond 403 PERMISSION_DENIED
        return

    # 4. 대조
    if annotationPermission not in permissionSet:
        recordDenied(operator, request, permissionUsed=annotationPermission)
        respond 403 PERMISSION_DENIED
        return

    # 5. 통과 — downstream command 실행
    proceed(operator, annotationPermission)
```

`recordDenied(...)`는 `admin_actions`에 다음 값을 가진 row 1건을 INSERT한다 (D3):

| 컬럼 | 값 |
|---|---|
| `action_code` | endpoint 매핑 (`ACCOUNT_LOCK`, `AUDIT_QUERY` 등). 미매핑 시 `UNKNOWN` |
| `operator_id` | `admin_operators.id` (내부 BIGINT PK) |
| `permission_used` | annotation 값 또는 `"<missing>"` |
| `target_type` / `target_id` | path variable에서 추출 가능하면 기록 (예: ACCOUNT / `{accountId}`) |
| `reason` | `X-Operator-Reason` 헤더. 없으면 공백이 아니라 `"<not_provided>"` |
| `request_id` | 요청 상관관계 ID (header 또는 생성) |
| `outcome` | `DENIED` |
| `detail` | 거부 사유 코드 (예: `MISSING_ANNOTATION`, `PERMISSION_NOT_GRANTED`) |
| `started_at` / `completed_at` | NOW() (DENIED row는 start=end 동일 값) |

DENIED row 기록과 403 응답은 **단일 트랜잭션**이어야 한다 ([rules/traits/audit-heavy.md](../../../rules/traits/audit-heavy.md) A10 fail-closed). 감사 기록 실패 시 응답도 실패(500)하여 로그 누락을 방지한다.

### Conditional Cross-Permission: `GET /api/admin/audit`

이 엔드포인트는 `source` query parameter 값에 따라 요구 권한이 달라진다. 컨트롤러 annotation은 최소 공통 권한(`audit.read`)만 선언하고, 내부에서 아래 알고리즘으로 추가 권한을 재검사한다.

```
function evaluateAuditRead(operator, permissionSet, request):
    source = request.queryParam("source")                # "admin_actions" | "login_history" | "suspicious" | null

    # 기본 요구 권한
    required = {"audit.read"}

    # source에 따라 security.event.read 추가 요구 (교집합이 아닌 조건부 확장 = AND)
    if source == "login_history" or source == "suspicious":
        required.add("security.event.read")
    # source == "admin_actions" 또는 null(전체 통합 조회이되 admin_actions 기본 소스로 제한):
    #   → audit.read만 요구

    # 평가
    missing = required - permissionSet
    if missing is empty:
        proceed(operator, action="AUDIT_QUERY",
                permissionUsed = joinKeys(required))     # "audit.read" 또는 "audit.read+security.event.read"
        return

    # DENIED
    if required.size == 2:
        permissionUsed = "audit.read+security.event.read"   # 복합 키 — 어떤 권한이 부족했는지가 아니라 요구된 전체 집합을 기록
    else:
        permissionUsed = "audit.read"
    recordDenied(operator, request,
                 actionCode = "AUDIT_QUERY",
                 permissionUsed = permissionUsed,
                 detail = "MISSING_PERMISSIONS=" + join(missing, ","))
    respond 403 PERMISSION_DENIED
```

**규칙 요약**:

- `source=login_history` 또는 `source=suspicious` → **`audit.read` AND `security.event.read`** 모두 필요
- `source=admin_actions` 또는 `source` 미지정 → **`audit.read`만** 필요
- DENIED 시 `admin_actions.permission_used`:
  - 단일 요구였던 경우: `"audit.read"`
  - 복수 요구였던 경우: 복합 키 `"audit.read+security.event.read"` (어떤 쪽이 부족했는지는 `detail` 필드에 `MISSING_PERMISSIONS=...`로 기록)
- 응답 본문에는 어느 permission이 누락되었는지 노출하지 않는다 (공격자에게 권한 구조 누설 방지, [admin-api.md](../../contracts/http/admin-api.md) 403 Response Shape 절과 일치).

---

## Caching & Invalidation (구현 가이드)

- Redis 키: `admin:operator:perm:{operator_id}` → permission key set, TTL 10초
- `admin_operator_roles` / `admin_role_permissions` 변경 API는 해당 key를 즉시 DELETE
- 캐시 miss 시 DB fallback, 동시 다발 miss는 mutex 불필요 (TTL 10초로 thundering herd 무시 가능)

세부 구현은 TASK-BE-028에서 확정.

---

## Testing Expectations (specs-level)

| 레이어 | 시나리오 |
|---|---|
| Unit | permission union across multiple roles, missing-annotation → deny, annotation mismatch → deny, operator.status != ACTIVE → 401 |
| Integration | seed role × endpoint 매트릭스를 table-driven으로 검증 (4 role × 6 endpoint ≈ 24 조합) |
| Audit | DENIED 시 `admin_actions` row 1건 기록, spray 10회 → 10 row (no dedup, D3) |
| Fail-closed | `admin_actions` INSERT 실패 주입 → 403이 아닌 500 반환, downstream 호출 없음 |

---

## Open Issues (to TASK-BE-028)

- `@RequiresPermission` annotation + `AspectJ` 또는 `HandlerInterceptor` 중 선택
- Permission 상수 클래스 배치 (application 레이어 vs infrastructure/security)

> TASK-BE-028c에서 확정됨: 다중 인스턴스 간 캐시 무효화 전파는 **TTL 10초 단독 의존**으로 결정. Pub/Sub 기반 push invalidation은 추후 backlog 항목(미지정 태스크)으로 이월. 프로세스-로컬 `invalidate(operatorId)`는 공유 Redis 인스턴스의 키를 즉시 DELETE하므로 같은 Redis를 바라보는 모든 admin-service 인스턴스에 반영된다. TTL 단독 의존의 한계(최대 10초 stale window)는 별도 backlog에서 다룬다.

---

## Reserved for TASK-BE-029 (2FA)

- `admin_operators.totp_secret_encrypted`, `admin_operators.totp_enrolled_at` 컬럼은 본 태스크에서 NULL 허용 placeholder로만 생성
- `admin_roles.require_2fa` 컬럼은 생성하되 권한 평가에서 참조하지 않음 (TASK-BE-029에서 `OperatorAuthenticationFilter`에 `totp_verified_at` 클레임 검사 추가 예정)
- 본 문서에서 2FA 정책은 정의하지 않는다 — Out of Scope

### Recovery Codes Hashing Policy (TASK-BE-029a 결정)

2FA enrollment 시 발급되는 **recovery codes**(10개, one-time-use)의 저장 정책:

- **Canonical hasher**: `libs/java-security.PasswordHasher` (Argon2id). 서비스 단위 우회 금지.
- **이유**: [rules/traits/regulated.md](../../../rules/traits/regulated.md) R2 — 전 플랫폼 canonical 비밀 값 hashing은 Argon2id로 통일되어 있으며, BCrypt/PBKDF2/sha 등 서비스별 변종은 금지된다. recovery code는 로그인 크리티컬 경로는 아니지만 **동일한 canonical 정책**을 적용한다.
- **Parameter**: `libs/java-security`에 선언된 기본 프로파일을 그대로 사용(별도 tuning 금지). 검증 시 `PasswordHasher.matches(code, hash)` 사용.
- **저장 컬럼**: `admin_operator_totp.recovery_codes_hashed` (JSON array of Argon2 hash strings) — 실제 스키마는 [data-model.md](./data-model.md) V0012 마이그레이션에서 확정.
- **사용 정책**: 1회 사용 후 해당 entry 삭제 또는 `used_at` 마킹(구현 선택, 029에서 결정). 재사용 시 401.
- TASK-BE-029의 "BCrypt" 류 문구는 본 섹션을 canonical source로 정정된다.
