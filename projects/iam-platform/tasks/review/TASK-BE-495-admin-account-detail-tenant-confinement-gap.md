# Task ID

TASK-BE-495

# Title

`GET /api/admin/accounts/{accountId}` — 미문서화·무소비자 공개 detail 표면의 tenant-confinement 결여 해소 (제거, 대안: confine). GAP-ACCOUNT-01(BE-467/468) 이 닫지 못한 **읽기** 축의 잔여 갭

# Status

review

# Owner

backend

# Task Tags

- code
- security
- api
- test

---

# Dependency Markers

- **선행 (prerequisite)**: 없음. `TASK-BE-467`(계정 변이 tenant-confinement) / `TASK-BE-468`(session-revoke) 가 이미 머지되어 형제 표면은 전부 confined — 본 task 는 그 웨이브가 남긴 **읽기 detail** 축을 닫는다.
- **관련 (related, 비차단)**: `TASK-BE-357`(read-surface `QueryTenantScopeGate` 도입 — list/search 축을 닫은 task). 본 task 는 동일 게이트를 detail 축에 적용하거나, 표면 자체를 제거한다.
- **후속 (blocks)**: 없음.

---

# Goal

admin-service 의 공개 표면 `GET /api/admin/accounts/{accountId}` 는 **테넌트 스코프 게이트를 통과하지 않는다**. 같은 컨트롤러의 형제 엔드포인트(list/search/lock/unlock/bulk-lock/gdpr-delete/export)는 전부 `QueryTenantScopeGate` 로 confined 되어 있으므로 이는 **대칭성 결여(asymmetry)** 이며, `account.read` 를 보유한 **테넌트 한정 운영자**가 `accountId` 만 알면 타 테넌트 계정의 이메일·프로필(표시명·전화번호)을 읽을 수 있는 **IDOR 클래스 결함**이다.

동시에 이 엔드포인트는 **계약에 존재하지 않고**(`admin-api.md` 에 헤딩 없음), **소비자가 없으며**(콘솔은 detail 을 search/list 항목에서 파생), **컨트롤러 테스트도 없다**. 즉 코드가 계약을 초과(contract drift)한 상태다.

**채택 결정 — 옵션 A(제거).** Source of Truth 우선순위상 `specs/contracts/` 가 `code` 보다 상위이므로, 계약에 없고 소비자도 없는 공개 표면은 게이트를 덧대는 것이 아니라 **삭제하여 계약 정합을 복원**한다. 공격면이 0 이 되고 이후 confinement 회귀 자체가 불가능해진다.

**대안 옵션 B(유지 + confine)** 는 "향후 콘솔이 detail 을 소비할 예정" 이 확인될 때만 선택한다. 그 경우 `admin-api.md` 에 § 를 신설하고 `QueryTenantScopeGate` 를 적용해야 하며, 계약 신설이 선행(spec-first)이다. 구현자는 A 를 기본으로 진행하되, 착수 시점에 소비자 재확인(grep) 후 B 로 전환할 근거가 발견되면 **Hard Stop 하고 보고**한다.

---

# Scope

## In scope

1. **REMOVE** `AccountAdminController.detail(...)` — `@GetMapping("/{accountId}")` + `@RequiresPermission(Permission.ACCOUNT_READ)` 핸들러 (`admin-service`).
   - 이 핸들러가 유일하게 의존하던 `NonRetryableDownstreamException` 404 → `ResponseEntity.notFound()` 매핑 분기도 함께 제거.
2. **KEEP (삭제 금지)** `AccountServiceClient.getDetail(String accountId)` — **살아있는 소비자가 있다**:
   - `OnboardingController` (운영자 온보딩 시 계정 조회)
   - `LinkOperatorIdentityUseCase` (ADR-MONO-034 identity 링크)
   두 호출자는 admin-service **내부** 사용이며 공개 표면이 아니다. 클라이언트 메서드·`AccountDetailResponse` 레코드·`AccountServiceClientUnitTest` 의 `getDetail` 케이스는 모두 유지한다.
3. **KEEP (삭제 금지)** account-service `GET /internal/accounts/{accountId}` (`AccountSearchController.detail`) — 위 두 소비자의 백엔드이며 `InternalApiFilter` 로 외부 비노출.
4. **회귀 가드 테스트 추가** — 제거된 공개 표면이 재도입되어도 무방비로 열리지 않도록:
   - `AccountAdminControllerTest`: `GET /api/admin/accounts/{accountId}` → **404 Not Found**(핸들러 부재) 단언. (표면이 사라졌음을 코드로 고정.)
5. **문서 동기화** — `admin-api.md` 는 변경 없음(원래 이 § 가 없었다). `rbac.md` 의 `account.read` 행에 detail 엔드포인트가 나열돼 있다면 제거. `console-integration-contract.md` § 2.4.1 의 "detail = derived from the search/list item" 문장은 이미 정확하므로 **무변경**.

## Out of scope

- **account-service `/internal/accounts/{accountId}` 의 tenant 스코프화** — 내부 EP 이고 소비자(온보딩/identity 링크)가 tenantId 를 갖지 않는 문맥에서 호출하므로, 시그니처에 `tenantId` 를 강제하면 두 유스케이스가 깨진다. 별도 판단 필요 → **Edge Cases** 참고.
- `GET /internal/accounts/{accountId}/status` (`AccountStatusQueryController`) — 동일하게 tenant 무스코프이나 상태 문자열만 반환하고 PII 가 없다. 별도 티켓 후보(아래 Edge Cases).
- seed role 재설계(예: `SUPPORT_LOCK` 이 `account.read` 없이 계정 목록을 못 여는 문제) — 별개 관심사.
- 콘솔(`platform-console`) 변경 — 이 엔드포인트를 소비하지 않으므로 FE diff 없음.

---

# Acceptance Criteria

- [ ] AC-1 — `AccountAdminController` 에 `@GetMapping("/{accountId}")` 핸들러가 존재하지 않는다.
- [ ] AC-2 — `AccountServiceClient.getDetail` 및 `AccountDetailResponse` 는 그대로 존재하고, `OnboardingController` / `LinkOperatorIdentityUseCase` 는 **무수정**으로 컴파일·통과한다.
- [ ] AC-3 — `AccountAdminControllerTest` 에 `GET /api/admin/accounts/{accountId}` → 404 회귀 가드 케이스가 추가되고 통과한다.
- [ ] AC-4 — `admin-api.md` 에 detail § 가 신설되지 **않는다**(옵션 A 는 계약 무변경). `rbac.md` 의 `account.read` 행에 detail 엔드포인트 잔여 참조가 없다.
- [ ] AC-5 — `./gradlew :projects:iam-platform:apps:admin-service:test` + `:account-service:test` GREEN. account-service 는 **무변경**이므로 기존 `AccountSearchControllerTest` / `AccountSearchQueryServiceTest` 도 무수정 통과.
- [ ] AC-6 — 콘솔 `parity-verification.test.ts` / `parity-matrix.ts` 는 detail 을 producer 행으로 갖고 있지 않으므로 **무수정** GREEN (제거가 parity 매트릭스를 깨지 않음을 확인).
- [ ] AC-7 — fed-e2e / `GoldenPathE2ETest` / `CrossServiceBulkLockE2ETest` 무영향(이들은 bulk-lock 만 호출).

---

# Related Specs

- `projects/iam-platform/specs/services/admin-service/rbac.md` — § seed matrix (`account.read` 보유 role), § effective admin-grant scope (`'*'` short-circuit → subtree → tenant).
- `projects/iam-platform/specs/services/account-service/architecture.md` — internal EP 경계 / `InternalApiFilter`.
- `platform/hardstop-rules.md` — HARDSTOP-06(스펙 부재/충돌) 판단 근거: **코드가 계약을 초과**하는 경우의 해소는 코드 축소가 기본.

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` — **권위 계약**. 현재 accounts 표면 헤딩은 정확히 6 개: `GET /api/admin/accounts`, `POST .../{accountId}/lock`, `POST .../bulk-lock`, `POST .../{accountId}/unlock`, `POST .../{accountId}/gdpr-delete`, `GET .../{accountId}/export`. **detail 헤딩은 없다** — 본 task 의 근거.
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.1 — 소비자 계약: `| 2 | detail | derived from the search/list item + (3–8) per-account ops | read |` (전용 엔드포인트를 소비하지 않음).

---

# Evidence (착수 전 검증 완료 — 2026-07-10)

미티켓 백로그 후보의 REAL-GAP 판정 규율(구현상태 검증 필수)에 따라 아래를 확인함:

| # | 확인 항목 | 결과 |
|---|---|---|
| 1 | `AccountAdminController.detail` 의 게이트 | `@RequiresPermission(ACCOUNT_READ)` **만**. `queryTenantScopeGate.resolve(...)` 호출 **없음** |
| 2 | 형제 표면의 게이트 | search/list(BE-357), lock/unlock/bulk-lock(BE-467), gdpr-delete, export(BE-467) — **전부** `queryTenantScopeGate.resolve(...)` 경유 |
| 3 | 다운스트림 스코프 | `AccountServiceClient.getDetail` → `callGet("/internal/accounts/" + accountId, null, null, ...)` — operatorId·tenantId **둘 다 null** → `X-Tenant-Id` 헤더 미전송 |
| 4 | 저장소 쿼리 | `AccountQueryPortImpl.findDetailById(accountId)` — `tenantId` 파라미터 **없음**(전역 `findById`). 형제 `findAll`/`findByEmail` 은 tenantId 로 분기 |
| 5 | 계약 존재 여부 | `admin-api.md` 에 `## GET /api/admin/accounts/{accountId}` 헤딩 **부재**(export 만 존재) → 미문서화 |
| 6 | 소비자 존재 여부 | 콘솔 `features/accounts` 는 lock/unlock/bulk-lock/gdpr-delete/export/revoke 만 호출. detail 미호출. e2e 도 bulk-lock 만 |
| 7 | 테스트 커버리지 | `AccountAdminControllerTest` 에 detail 케이스 **없음** |
| 8 | 악용 가능성(핵심) | `QueryTenantScopeGate.isPlatformScope` 는 **operator row 의 `tenant_id == '*'`** 로 판정(role 의 `scope` 필드가 아님). `SUPPORT_READONLY`(= `account.read` 보유)는 **테넌트 한정 grant 로 부여 가능**하며 `tests/federation-hardening-e2e/fixtures/seed.sql` 에 `SUPPORT_READONLY @ umbrella-corp` 가 **실재**한다 → 이론적 갭이 아니라 **구성 가능한 실재 경로** |
| 9 | `getDetail` 의 생존 소비자 | `OnboardingController:61`, `LinkOperatorIdentityUseCase:104` — **dead code 아님**. 클라이언트/내부 EP 는 유지해야 함 |

**요지**: 노출 조건은 "테넌트 한정 운영자 + `account.read` + 대상 `accountId` 인지". 목록은 자기 테넌트만 돌려주므로 타 테넌트 `accountId` 를 목록으로는 얻을 수 없으나, `accountId` 는 비밀값이 아니다(감사 로그, 지원 티켓, 이벤트 페이로드, 사용자 제보 경유 유입). 기밀성 방어를 ID 추측 난이도에 의존시키는 것은 본 저장소의 tenant-confinement 불변식과 배치된다.

---

# Edge Cases

- **`SUPER_ADMIN` 무영향** — operator `tenant_id='*'` 는 애초에 크로스테넌트 조회가 허용된 주체다. 옵션 A 는 SUPER_ADMIN 의 detail 접근을 없애지만, 콘솔이 이 표면을 쓰지 않으므로 사용자-가시적 회귀는 없다(계정 상세는 list 항목 + export 로 구성).
- **`export` 와의 관계** — `GET .../{accountId}/export` 는 unmasked PII 를 반환하지만 `AUDIT_READ` 게이트 + `QueryTenantScopeGate`(BE-467) + meta-audit 이 걸려 있다. 즉 **더 민감한 표면이 더 강하게 보호**되고 있어, detail 만 무방비인 현 상태의 비일관성이 두드러진다.
- **account-service 내부 EP 의 잔여 무스코프** — `/internal/accounts/{accountId}` 는 본 task 이후에도 tenant 무스코프로 남는다. 이는 `InternalApiFilter` 로 외부 비노출이고 호출자가 admin-service 자신이므로 **현 위협모델상 수용**한다. 단, 향후 이 EP 를 게이트웨이에 노출하거나 제3 서비스가 소비하게 되면 즉시 confinement 가 필요하다 → 해당 시점의 선행 조건으로 명시.
- **`GET /internal/accounts/{accountId}/status` 별도 후보** — 동일 무스코프이나 PII 미반환(상태 문자열). 별도 백로그 티켓으로 분리 판단.
- **옵션 B 로 전환 시** — `admin-api.md` § 신설(spec-first) → `QueryTenantScopeGate.resolve(op, tenantId, ActionCode.ACCOUNT_SEARCH, Permission.ACCOUNT_READ)` 적용 → `getDetail(accountId, tenantId)` 시그니처 확장 → account-service `findDetailById(tenantId, accountId)` 확장. 이때 **크로스테넌트 대상은 404**(형제 표면의 enumeration-safe 규약과 동일; 403 은 존재를 누설).

---

# Failure Scenarios

| # | 시나리오 | 기대 동작 |
|---|---|---|
| 1 | 제거 후 누군가 `GET /api/admin/accounts/{id}` 호출 | Spring 이 핸들러를 찾지 못해 **404** (AC-3 가 고정) |
| 2 | 제거가 `OnboardingController` 를 깨뜨림 | **발생하면 안 됨** — 온보딩은 `AccountServiceClient.getDetail` 을 직접 호출하지 공개 컨트롤러를 거치지 않는다. AC-2 가 이를 보증. 깨진다면 Scope 판단 오류이므로 **Hard Stop 후 재설계** |
| 3 | 착수 시점에 콘솔이 detail 을 소비하기 시작했음이 발견됨 | 옵션 A 무효 → **Hard Stop 하고 보고**. 옵션 B(spec-first 계약 신설 + confine)로 전환 |
| 4 | `AccountDetailResponse` 를 함께 지우려는 유혹 | 금지 — `LinkOperatorIdentityUseCaseTest` 가 이 레코드를 조립한다. AC-2 위반 |
| 5 | 옵션 B 채택 시 크로스테넌트 대상에 403 반환 | 계약 위반 — 형제 표면(BE-467)은 **404 ACCOUNT_NOT_FOUND**(enumeration-safe). 403 은 타 테넌트 계정의 **존재**를 누설 |
| 6 | 회귀: 후속 task 가 detail 핸들러를 다시 추가 | AC-3 회귀 가드가 RED 로 잡는다 |

---

# Notes

- **작업 규모**: 핸들러 1 개 제거 + 테스트 1 케이스 추가 + `rbac.md` 잔여 참조 정리. FE diff 없음, 계약 diff 없음, 마이그레이션 없음.
- **진행 권장 (분석=Opus 4.8 / 구현 권장=Sonnet — 표면 제거 + 가드 테스트, 도메인 설계 없음)**. 단 착수 시 Evidence #6·#9 를 **재검증**(grep)한 뒤 진행할 것 — 소비자가 생겼다면 Failure Scenario #3.
- 본 task 는 `GAP-ACCOUNT-01`(→ BE-467/468, 계정 **변이** tenant-confinement) 웨이브의 **읽기 축 잔여물**이다. 그 웨이브의 교훈("착수 전 구현상태 검증 + 시그니처 변경 시 전체 `:test`")이 그대로 적용된다.
