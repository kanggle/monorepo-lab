# Task ID

TASK-MONO-735

# Status

review

# Title

계정 잠금 호출이 **계정의 테넌트를 싣지 않는다** — `fan-platform` 밖의 계정은 자동 잠금 · SUPER_ADMIN 잠금 · 셀러 정지 어느 것으로도 잠기지 않는다

# Owner

monorepo (iam-platform · ecommerce-microservices-platform — 호출처가 두 프로젝트에 걸친다)

# Task Tags

- security-service
- admin-service
- account-service
- product-service
- security
- multi-tenant

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus — 고치는 자리가 «호출자가 테넌트를 넘긴다» 와 «account-service 가 계정에서 테넌트를 푼다» 로 갈리는 설계 결정이고, 세 호출처 · 내부 계약 · 감사 경로가 얽힌다.

---

# Goal

2026-09-26 16차 AMI 데모 창(`TASK-MONO-672` § 2026-09-26 16차 창)에서 **실측**:

| 경로 | 대상 계정 | 결과 |
|---|---|---|
| security-service 자동 잠금 (`TokenReuseRule` AUTO_LOCK) | `fan-platform` 일회용 | **LOCKED (~2s)** 🟢 |
| 같은 경로 | `ecommerce` 일회용(`419f99a1-…`, 스토어 소셜 가입) | **`Auto-lock non-retryable 4xx: status=404`** → ACTIVE 🔴 |
| 콘솔 운영자 잠금 (`demo@demo.com` = SUPER_ADMIN, 테넌트 `ecommerce` 화면) | 같은 계정 | account-service **404** → 재시도가 같은 멱등 키로 감사 행 중복 → **500** 「하위 서비스 호출에 실패했습니다」 🔴 |

원인(코드):
- security-service `infrastructure/client/AccountServiceClient.lock` — `POST /internal/accounts/{id}/lock` 에 **`X-Tenant-Id` 없음**.
- admin-service `infrastructure/client/AccountServiceClient.lock` — 운영자의 활성 테넌트를 넣되 `"*"`/null 이면 넣지 않는다(주석: «account-service FAN default (net-zero)») ⇒ SUPER_ADMIN 은 항상 헤더 없음.
- ⚪ **미측정 · 코드 판독**: ecommerce product-service `AccountServiceSellerProvisioner.lockAccount`(셀러 정지 → 계정 잠금, `TASK-MONO-672` 항목 14 ②)도 헤더 없음 — 주석은 «경로에 테넌트가 없으니 테넌트 규칙이 적용되지 않는다» 고 적지만, 라이브의 account-service 는 헤더 없음을 `fan-platform` 으로 읽었다. 셀러는 `ecommerce` 계정이다 ⇒ 같은 모양으로 실패할 것으로 **추정**(시드 셀러를 정지시켜야 재므로 창에서 재지 않았다).

⇒ 스토어 고객 · 셀러 · 그 밖의 비-fan 테넌트 계정은 **반복된 토큰 재사용에도, 운영자가 잠가도 잠기지 않는다.** 자동 잠금 판정들(`TASK-MONO-727` ② · `TASK-BE-600` · `TASK-BE-601` AC-3)은 전부 `fan-platform` 계정으로 재서 이것을 못 봤다.

# Scope

## In Scope

- **AC-0 (🔴 소유자 결정)**: 고치는 자리.
  - (a) 호출자가 계정의 테넌트를 넘긴다 — security-service 는 이벤트의 `tenantId` 를 이미 들고 있다. admin-service 는 SUPER_ADMIN 일 때 **대상 계정의** 테넌트가 필요하다(운영자 테넌트가 아니다). product-service 는 `TenantContext.currentTenant()` 를 이미 받는다.
  - (b) account-service `/lock`(`/unlock` · `/delete` 형제 포함)이 헤더 없을 때 계정에서 테넌트를 푼다 — `TASK-BE-602` 의 `findByIdResolvingTenant`(«tenant 없는 findById 금지» 의 문서화된 예외)와 같은 모양. 헤더가 있으면 지금처럼 그 테넌트로 한정(교차 테넌트 404 유지).
  - (c) 둘 다.
- 결정대로 구현 + 내부 계약(`specs/contracts/http/internal/…` 의 lock 절) 먼저 갱신.
- admin-service 재시도가 **같은 멱등 키로 감사 행을 다시 쓰려다** 중복 키로 500 이 되는 것 — 404(비재시도 대상)가 500 으로 둔갑한다. 같은 티켓에서 고치거나 이름을 남겨 분리.

## Out of Scope

- 잠금 해제 경로 설계(`TASK-BE-608` AC-3 소유자 결정 대기).

# Acceptance Criteria

- [x] **AC-0** — 위 (a)/(b)/(c) 소유자 결정 + 세 호출처 각각이 결정 뒤 어떤 테넌트를 싣는지 표. → § AC-0 소유자 결정 (c).
- [x] **AC-1** — 구현 + 단위/IT: 비-fan 테넌트 계정의 자동 잠금 · SUPER_ADMIN 잠금 · 셀러 정지 → account-service 200. 대조군: 다른 테넌트로 한정한 호출은 여전히 404(교차 테넌트 격리 유지). → § 구현 기록. 🔴 Testcontainers IT 셀은 로컬 Docker 가 꺼져 있어 **CI 가 판정**.
- [x] **AC-2** — admin-service: 비재시도 4xx 가 감사 중복 키 500 으로 바뀌지 않는다(테스트로 핀). → § 구현 기록 ③.
- [ ] **AC-3** — 🔴 **결과로 판정**(창): `ecommerce` 일회용 계정 — 합성 재사용 2건 → `accounts.status=LOCKED` · 콘솔 잠금 200 → LOCKED. 이어서 `TASK-BE-602` AC-3 런북(스토어 소셜 계정 잠금 → 소셜 로그인 `account_unavailable`)을 같은 창에서 닫는다. 셀러 정지는 일회용 셀러로(시드 셀러를 건드리지 마라).

# Related Specs

- `projects/iam-platform/specs/features/multi-tenancy.md` § 격리 회귀 방지 · `TASK-BE-602` 의 문서화된 예외
- `projects/iam-platform/specs/contracts/http/internal/` — account-service lock/unlock
- `TASK-BE-606`(1시간 2회 = AUTO_LOCK) · `TASK-BE-608` AC-3(해제 경로)

# Related Contracts

- account-service `POST /internal/accounts/{id}/lock|unlock|delete` 의 `X-Tenant-Id` 해석(additive 로만).

# Edge Cases

| 상황 | 기대 |
|---|---|
| 같은 이메일이 테넌트마다 다른 계정(`demo@demo.com` 셋) | 잠금은 **그 계정 id 하나**만 — 다른 테넌트 계정은 영향 없음 |
| 헤더가 있고 계정이 다른 테넌트 | 404 유지(격리) |

# Failure Scenarios

1. **fan-platform 계정으로만 재고 닫는다** → 이 결함이 그렇게 숨었다. AC-3 은 반드시 비-fan 계정.
2. **admin-service 에 운영자 테넌트를 넣는다** → SUPER_ADMIN(`"*"`)은 여전히 못 잠근다. 대상 계정의 테넌트다.
3. **(b) 를 헤더 유무와 무관하게 적용** → 교차 테넌트 격리가 풀린다.

---

# AC-0 소유자 결정 (2026-09-26 UTC) = (c) 둘 다

**(b)** account-service `/internal/accounts/{id}/lock` · `/unlock` · `/delete` 는 `X-Tenant-Id` 가 **없거나 공백이거나 `*`** 이면
계정 행에서 테넌트를 푼다(`AccountRepository.findByIdResolvingTenant` — `TASK-BE-602` 의 문서화된 예외의 둘째 사용). 헤더가
**구체 테넌트**면 오늘과 똑같이 그 테넌트로 한정한다(교차 테넌트 → 404). **(a)** 테넌트를 이미 아는 호출자는 그것을 명시적으로 싣는다
(심층 방어 — 틀린 id 가 다른 테넌트 계정을 가리키면 잠그지 말고 404).

🔵 `*` 를 «헤더 없음» 과 같이 푼 이유: 오늘 `TenantId.fromHeaderOrDefault` 가 `*` 를 부재와 **같은 값**(`fan-platform`)으로 읽고, admin-service
는 SUPER_ADMIN 일 때 헤더를 **생략하지 않고 `*` 를 싣는다**(`QueryTenantScopeGate` 가 `*` 를 그대로 돌려주고 `callPost` 가 non-null 이면
스탬프한다). «부재만» 을 풀면 SUPER_ADMIN 잠금은 고쳐지지 않는다.

| 호출처 | 결정 뒤 싣는 테넌트 | account-service 가 찾는 곳 |
|---|---|---|
| security-service `AccountServiceClient.lock` (자동 잠금) | `X-Tenant-Id` = `SuspiciousEvent.getTenantId()` (탐지 이벤트의 테넌트 — 생성자에서 non-blank 강제) | 그 테넌트 한정 |
| admin-service `AccountServiceClient.lock`/`unlock` — TENANT_ADMIN 등 일반 운영자 | 운영자의 해소된 활성 테넌트(변경 없음) | 그 테넌트 한정(교차 → 404 유지) |
| admin-service 같은 메서드 — SUPER_ADMIN (`*`) | `X-Tenant-Id: *` (변경 없음) | **계정 행의 테넌트** (b) |
| ecommerce product-service `AccountServiceSellerProvisioner.lockAccount(tenantId, accountId)` (셀러 정지) | `X-Tenant-Id` = 받은 `tenantId` | 그 테넌트 한정 |
| 헤더를 안 싣는 그 밖의 호출자 | — | **계정 행의 테넌트** (b) — `fan-platform` 계정은 결과가 같다(net-zero) |

---

# 구현 기록 (2026-09-26 UTC)

## ① 계약 먼저 (커밋 `9a4a97e04`, additive)

- `iam-platform/specs/contracts/http/internal/admin-to-account.md` § Tenant Confinement — 헤더 없음/공백/`*` 이면 `/lock` · `/unlock` · `/delete` 는 계정 행의 테넌트. `/gdpr-delete` · `/export` 는 **변경 없음**(`fan-platform` 기본값).
- `security-to-account.md` — `X-Tenant-Id: {suspicious_event.tenant_id}` 헤더 + 404 응답 절.
- `ecommerce-microservices-platform/specs/contracts/http/internal/product-to-account.md` § 3 — `X-Tenant-Id: {tenantId}` (bearer 는 기본 자격 그대로 — 경로가 테넌트를 말하지 않으므로 ADR-MONO-076 교환 대상 아님).
- `admin-api.md` — SUPER_ADMIN `*` 해석 · lock/unlock 에 `409 IDEMPOTENCY_KEY_CONFLICT` 행 · 하위 404/409 매핑 명시.
- `multi-tenancy.md` § 격리 회귀 방지 · `auth-to-account.md` — `findByIdResolvingTenant` 의 **두 번째 등록 소비처**(새 예외가 아니다). `/status` 의 헤더 없음 = `fan-platform` 고정은 **바꾸지 않았다**.

## ② 구현 (커밋 `71acb7ecd`)

| 서비스 | 파일 | 변경 |
|---|---|---|
| account-service | `presentation/internal/AccountLockController.java` | `namesTenant(header)` — 구체 테넌트면 `changeStatus(cmd, TenantId)` / `deleteAccount(..., TenantId)`(오늘 그대로), 아니면(없음·공백·`*`) `changeStatusResolvingTenant` / `deleteAccountResolvingTenant` |
| account-service | `application/service/AccountStatusUseCase.java` | 위 두 메서드 추가(`findByIdResolvingTenant`), 본문은 `applyStatusChange` / `applyDelete` 로 공유. 헤더 없는 **배치/스케줄러** 오버로드 `changeStatus(cmd)` · 소비자 탈퇴 `deleteAccount(4-arg)` 는 `fan-platform` 그대로 |
| account-service | `domain/repository/AccountRepository.java` | 문서화된 예외 javadoc — 등록 소비처 2개 |
| security-service | `infrastructure/client/AccountServiceClient.java` | `X-Tenant-Id: event.getTenantId()` |
| product-service | `infrastructure/client/AccountServiceSellerProvisioner.java` | `lockAccount` 에 `X-Tenant-Id: tenantId` · 오해를 부르던 주석 정정 |
| admin-service | `infrastructure/client/AccountServiceClient.java` | javadoc 만(`*`/null → 계정 행의 테넌트; 운영자 테넌트를 넣어 «고치지» 말 것) — 스탬프 로직 **변경 없음** |
| admin-service | `application/AccountAdminUseCase.java` · `AdminActionAuditor.java` · `AdminActionAuditWriter.java` · `exception/TargetAccountNotFoundException.java` · `presentation/advice/AdminExceptionHandler.java` | ③ |

## ③ admin-service 500 의 기전 — «재시도» 는 @Retry 가 아니었다

- 코드로 확인한 것: 감사 IN_PROGRESS 행은 `AccountAdminUseCase.executeAccountAction` 이 **한 번** INSERT 하고, `@Retry(accountService)` 는 그 **아래** `AccountServiceClient.lock` 에만 걸려 있어 `recordStart` 를 다시 부르지 않는다. 게다가 `application.yml` 의 `accountService` retry 는 `NonRetryableDownstreamException`(4xx)을 이미 `ignore-exceptions` 로 둔다 ⇒ **프로세스 안 재시도는 404 를 다시 보내지도, 감사 행을 다시 쓰지도 않는다.**
- 중복 키를 만든 것은 **두 번째 HTTP 요청**이다: `AdminExceptionHandler` 가 `NonRetryableDownstreamException`(=`DownstreamFailureException` 하위형)을 **`503 DOWNSTREAM_ERROR`** 로 냈고(admin-api.md 는 404 `ACCOUNT_NOT_FOUND` 라고 적는데), 콘솔 확인 대화상자는 **확정한 동작마다 키 하나**를 유지하므로(`use-accounts.ts` «Stable per the confirmed action») 운영자의 재클릭이 **같은 키**로 다시 온다 → `recordStart` INSERT 가 `idx_admin_actions_idemp (actor_id, action_code, idempotency_key)` 에 걸림 → `AuditFailureException` → `500 AUDIT_FAILURE` «fail-closed: audit write failed». ⚪ 라이브 로그로 «두 번째 요청이 있었다» 를 재지는 않았다 — 유니크 키 충돌이 **같은 (actor, action, key) 로 두 번째 INSERT** 가 있었다는 것 자체를 뜻한다는 추론이다.
- 고친 것 둘:
  1. 하위 `404` → **`404 ACCOUNT_NOT_FOUND`**(`TargetAccountNotFoundException` — `AccountBusinessException` 하위형이라 retry/CB 가 무시), `409` → **`400 STATE_TRANSITION_INVALID`**(admin-api.md 의 기존 표 그대로). 그 밖의 4xx·5xx 는 여전히 `503`. FAILURE 감사 행은 그대로 쓴다.
  2. `recordStart` **전에** `(operator, action, key)` 행 존재를 읽어, 있으면 **`409 IDEMPOTENCY_KEY_CONFLICT`** — 새 감사 행 없음 · 하위 호출 없음.
- 🔴 **남긴 것 (의도적, 기록)**: ⓐ 진짜 동시에 온 첫 요청 둘은 이 읽기를 함께 통과할 수 있고 진 쪽은 예전처럼 유니크 키 500 이다(경합 창 = 읽기~INSERT). ⓑ 같은 키 재전송은 **일시 장애(503) 뒤의 정당한 재시도**도 409 로 막는다 — 운영자는 대화상자를 다시 열어 새 키를 받아야 한다. 이전엔 같은 경우가 500 이었으므로 후퇴는 아니다. 감사 행이 `IN_PROGRESS` 만 UPDATE 를 허용하는(V0010 트리거) 이상 «같은 키로 재실행» 은 새 설계가 필요하다.

## ④ 테스트 (로컬, 2026-09-26 UTC · `rc` 는 `$?` 를 직접 읽음 · 파이프 없음)

| 모듈 | 명령 | rc | 결과(`build/test-results/test/*.xml` 합) |
|---|---|---|---|
| account-service | `:projects:iam-platform:apps:account-service:test` | **0** | 522 tests · 0 fail · 47 skipped |
| security-service | `…:security-service:test` | **0** | 249 · 0 · 13 |
| admin-service | `…:admin-service:test` | **0** | 859 · 0 · 58 |
| product-service | `:projects:ecommerce-microservices-platform:apps:product-service:test` | **0** | 383 · 0 · 0 |

- 새/바뀐 셀: account `AccountStatusUseCaseTest` +5(15) · `InternalControllerSliceTest` +6 · 헤더 없는 기존 5셀 스텁을 resolving 변형으로(23) · security `AccountServiceClientUnitTest` +2(9, 404 는 maxAttempts=3 에서 **1회만** 전송) · product `AccountServiceSellerProvisionerTest` +1(14) · admin `AccountAdminUseCaseTest` +5(11).
- 🔴 **Testcontainers IT 는 로컬에서 안 돌았다**(Docker 꺼짐 — `docker info` rc=1). CI 의 `integrationTest` 가 판정한다: `AccountMutationTenantConfinementIntegrationTest`(헤더 없음/`*` → `ecommerce` 계정 lock 200 + LOCKED · `X-Tenant-Id=fan-platform` + `ecommerce` 계정 → 404 + 미변경(대조군) · `X-Tenant-Id=ecommerce` → 200 · 없는 id → 404 · 헤더 없음 unlock 200 / delete 202) — BE-467 셀 「헤더 없음 → wms 계정 404」 는 **결함을 핀하던 셀**이라 교체했다. `AdminIntegrationTest.lockDownstream404_thenReplaySameKey_isNot500`(라이브 실패 재현: SUPER_ADMIN → 하위 404 → 404 `ACCOUNT_NOT_FOUND` + FAILURE 행 1 · 같은 키 재전송 → 409 · 행 여전히 1 · 하위 호출 정확히 1회 · `X-Tenant-Id: *`).

## ⑤ bite (커밋 뒤 한 파일씩 되돌려 빨강 확인 → `git checkout --` 로 복구 → 대상 클래스 재실행 rc=0)

| 되돌린 것 | 실행 | 결과 |
|---|---|---|
| account 컨트롤러 `namesTenant` → 항상 `true`(= 헤더 없음도 `fan-platform` 한정) | `InternalControllerSliceTest` | **rc=1 · 23 중 9 실패** |
| account 유스케이스 resolving 변형 → `findById(FAN_PLATFORM, …)` | `AccountStatusUseCaseTest` | **rc=1 · 15 중 6 실패**(MONO-735 4셀 전부 + 같은 치환이 BE-602 `getStatusResolvingTenant` 2셀도 건드림) |
| security `X-Tenant-Id` 헤더 제거 | `AccountServiceClientUnitTest` | **rc=1 · 9 중 1 실패**(헤더 셀) |
| product `lockAccount` 의 `X-Tenant-Id` 한 줄만 제거 | `AccountServiceSellerProvisionerTest` | **rc=1 · 14 중 1 실패** |
| admin 재전송 검사 `if (false)` | `AccountAdminUseCaseTest` | **rc=1 · 11 중 2 실패**(lock/unlock 재전송 셀) |
| admin 4xx 매핑 제거(`throw ex`) | `AccountAdminUseCaseTest` | **rc=1 · 11 중 2 실패**(404 · 409 셀) |
| 복구 뒤 위 5개 클래스 재실행 | 한 gradle 호출 | **rc=0** |

## ⑥ 남은 것 · 범위 밖 (이름을 남김)

- 🔴 **AC-3 은 열려 있다** — 아래 런북.
- `/gdpr-delete` · `/export` 는 SUPER_ADMIN(`*`)에서 여전히 `fan-platform` 기본값 ⇒ 비-fan 계정은 404. 소유자 결정 범위(lock/unlock/delete) 밖이라 **고치지 않았다** — 같은 모양이므로 후속 티켓 후보.
- security-service 가 싣는 테넌트는 **이벤트의** 테넌트다. 이벤트 테넌트 ≠ 계정 테넌트인 세션(`multi-tenancy.md` § 소셜 로그인 — `ecommerce` 신원으로 팬 client 에 소셜 로그인)이면 명시 헤더 때문에 **404(안 잠김)** 가 된다. (b) 만 썼다면 잠겼을 경우다. 소유자 결정 (c) 의 심층 방어가 치르는 대가이고, 로그로는 `Auto-lock non-retryable 4xx: status=404` 로 보인다. `TASK-BE-605` 결정 (iii)(신원 조회를 client 테넌트로 한정)이 들어가면 이 불일치 모집단 자체가 줄어든다.
- admin-service 재전송 경합(③ ⓐ) · 정당한 재시도의 409(③ ⓑ).

## ⑦ AC-3 런북 (창 — 🔴 반드시 비-fan 계정)

1. **자동 잠금**: 스토어(`ecommerce`)에서 일회용 계정을 만든다(소셜 또는 폼). `accounts` 에서 `id`·`tenant_id=ecommerce`·`ACTIVE` 확인. 그 계정의 `auth.token.reuse.detected` 합성 이벤트 2건(`tenantId=ecommerce`, 1시간 안) → security-service 로그에 `Auto-lock non-retryable 4xx` 가 **없고** → `accounts.status=LOCKED` (결과 상태로 판정). 대조군: 같은 절차의 `fan-platform` 계정도 LOCKED(회귀 없음).
2. **콘솔 잠금 (SUPER_ADMIN)**: `demo@demo.com` 으로 두 번째 `ecommerce` 일회용 계정을 잠금 → **200** · `accounts.status=LOCKED` · `admin_actions` 에 그 키로 행 1개 `SUCCESS`. 같은 대화상자에서 다시 확인을 눌러 **409 `IDEMPOTENCY_KEY_CONFLICT`**(500 아님)도 본다. 없는 id 잠금 → **404 `ACCOUNT_NOT_FOUND`**(503 아님).
3. **`TASK-BE-602` AC-3**: 1 의 LOCKED 스토어 소셜 계정으로 소셜 로그인 → `/login?error=account_unavailable` · `login_history` FAILURE.
4. **셀러 정지**: **일회용 셀러**를 온보딩(계정 발급 확인 — `sellers.account_id` · `accounts.tenant_id=ecommerce`) → 정지 → `accounts.status=LOCKED`. 🔴 시드 셀러를 건드리지 마라.
