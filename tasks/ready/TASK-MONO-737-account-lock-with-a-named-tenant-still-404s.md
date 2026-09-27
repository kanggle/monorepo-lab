# Task ID

TASK-MONO-737

# Status

ready

# Title

계정 잠금 호출에 **구체 테넌트**를 실어도 여전히 404다 — account-service 에 실제로 도착하는 `X-Tenant-Id` 값부터 확인한다

# Owner

monorepo (iam-platform · ecommerce-microservices-platform — 호출처가 두 프로젝트에 걸친다)

# Task Tags

- admin-service
- account-service
- product-service
- multi-tenant
- security

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus 5.5 — 원인이 「호출자가 잘못된 값을 만든다」인지 「중간에서 값이 덮인다」인지가 먼저 갈려야 하고, 확인에는 실제 내부 워크로드 자격증명 경로 재현이 필요하다(설계 판단 + 배선 양쪽).

---

# Goal

`TASK-MONO-735`(#4048, 2026-09-26 UTC 병합)가 account-service `/lock`·`/unlock`·`/delete` 를 「헤더 없음/공백/`*` → 계정 행에서 테넌트를 푼다(b) · 구체 테넌트 헤더 → 그 테넌트로 한정(교차 테넌트 404 유지)」으로 고쳤다. 17차 AMI 창(2026-09-27 UTC, `a6f0ab791`, `TASK-MONO-672` § 2026-09-27 17차 창 수확)에서 그 AC-3 런북을 실행한 결과, **헤더를 안 싣는 호출은 성공하고 구체 테넌트를 싣는 호출은 여전히 404**다:

| 호출 경로 | 싣는 헤더(설계상) | 대상 계정 테넌트 | 결과 |
|---|---|---|---|
| security-service 자동 잠금(헤더 없음, (b) 경로) | 없음 | ecommerce · fan-platform | 🟢 **LOCKED(~2s)** — 자동 잠금 · 교차 테넌트 세션 재현 둘 다, 대조군(fan-platform)도 LOCKED |
| admin-service 콘솔 잠금(SUPER_ADMIN `demo@demo.com`, 콘솔 테넌트 전환 `ecommerce`, 대상 계정 `78740d21-…`) | `X-Tenant-Id: ecommerce`(구체) | ecommerce | 🔴 **404 「대상 계정을 찾을 수 없습니다」** ×3(07:02:15·07:02:39·07:05:12Z) · admin-service 로그 `account-service returned 404 NOT_FOUND on /internal/accounts/…/lock` · 계정 `ecommerce ACTIVE` 그대로 · `admin_actions` FAILURE |
| product-service 셀러 정지(`AccountServiceSellerProvisioner.lockAccount(tenantId, accountId)`) | `X-Tenant-Id: tenantId`(구체, TASK-MONO-735 § AC-0 표) | ecommerce | 🔴 셀러 `SUSPENDED` 로 전이했지만 계정은 **ACTIVE** 그대로 · product-service 로그 `seller account lock failed (fail-soft) tenant=ecommerce account=… : 404 Not Found` |

패턴은 뚜렷하다 — **헤더가 없는 호출(security-service)은 성공하고, 테넌트를 명시로 싣는 호출(admin-service · product-service, 둘 다 대상은 `ecommerce`)은 같은 모양으로 404**다. account-service 코드(`AccountLockController.namesTenant(header)` → 구체 테넌트면 `changeStatus(cmd, TenantId)` → `AccountRepositoryImpl.findByTenantIdAndId`)는 헤더가 `ecommerce`로 오면 `ecommerce` 테넌트에서 그 계정을 찾아야 하므로, **실제로 도착하는 헤더 값이 `ecommerce`가 아닐 가능성이 높다** — 어느 호출 경로의 인터셉터/테넌트 전파 계층이 값을 덮거나 중복 지정하고 있을 수 있다. 🔴 **단, 확인되지 않은 추정이다.** 라이브에서 확정하려면 실제 내부 워크로드 자격증명으로 admin-service→account-service · product-service→account-service 호출을 재현해야 하고, 이번 창에서는 하지 않았다(운영자 작업 도중의 관찰만).

`TASK-MONO-735`의 IT 스위트(`AccountMutationTenantConfinementIntegrationTest`)에는 **「올바른 구체 테넌트 헤더 → 200」 셀이 없다** — 있는 것은 헤더 없음/`*` → 200과 잘못된 테넌트 헤더 → 404(대조군)뿐이다. 그래서 CI가 이 결함을 잡지 못하고 병합됐다.

# Scope

## In Scope

- **AC-0** — 실제로 account-service 에 도착하는 `X-Tenant-Id` 값을 확인한다. 후보 방법: account-service `AccountLockController`에 요청-스코프 로그(수신 헤더 원문) 추가 · 또는 admin-service/product-service 의 실제 client 빈(테넌트 스탬프 로직 포함)으로 account-service 를 부르는 IT 작성(현재 IT 는 헤더를 직접 구성해 호출자의 스탬프 로직 자체를 통과하지 않는다 — `feedback_assert_injection_before_reading_bite` 축).
- **AC-1** — AC-0 이 찾은 원인을 고친다(호출자 쪽 값 생성 로직 또는 중간 전파 계층). 계약 변경이 필요하면 먼저.
- **AC-1 부속** — admin-service·product-service 각각에 「올바른 구체 테넌트 헤더 → 200」 IT 셀을 추가한다(TASK-MONO-735 가 빠뜨린 셀).

## Out of Scope

- 잠금 해제 경로 자체의 설계(`TASK-BE-608` AC-3 소유자 결정 대기).
- security-service 경로 재설계 — (b) 경로로 이미 라이브에서 동작한다(변경 없음).
- account-service 의 `findByIdResolvingTenant`/`namesTenant` 판정 로직 자체 — 그 로직은 대조군(교차 테넌트 404 유지, 자동 잠금 LOCKED)으로 라이브에서 옳다는 것이 확인됐다. 의심 지점은 **호출자가 만드는/전파되는 값**이다.

# Acceptance Criteria

- [ ] **AC-0** — 실제로 도착하는 `X-Tenant-Id` 값을 실측한다(로그 또는 실제 client 빈 재현 IT). 값이 `ecommerce`가 아니면 무엇으로 도착하는지, 그리고 그 값이 어디서 만들어지는지(호출자 코드 vs 공통 인터셉터 vs 테넌트 전파 필터)까지 표로 남긴다.
- [ ] **AC-1** — 원인을 고친다. admin-service·product-service 각각에 「올바른 구체 테넌트 헤더 → 200」 단위/IT 셀을 추가한다(양쪽 다 — 하나만 고치면 나머지가 남는다). bite로 되돌려 실패 확인.
- [ ] **AC-2** — 🔴 **결과로 판정**(다음 데모 창): 콘솔에서 `demo@demo.com`(SUPER_ADMIN, 테넌트 전환 `ecommerce`)로 `ecommerce` 계정 잠금 → **200** · `accounts.status=LOCKED`. 셀러 정지(일회용 셀러) → **`accounts.status=LOCKED`**. 이 결과로 `TASK-MONO-735` AC-3 잔여 스텝 2·4, `TASK-MONO-726` 항목 14 ②를 닫는다.

# Related Specs

- `projects/iam-platform/specs/contracts/http/internal/admin-to-account.md`
- `projects/ecommerce-microservices-platform/specs/contracts/http/internal/product-to-account.md`
- `projects/iam-platform/specs/features/multi-tenancy.md` § 격리 회귀 방지
- `tasks/review/TASK-MONO-735-account-lock-calls-drop-the-accounts-tenant.md`(원인 소스 · AC-3 런북)
- `tasks/review/TASK-MONO-726-two-internal-callers-717-left-without-a-home.md`(항목 14 ②)

# Related Contracts

- account-service `POST /internal/accounts/{id}/lock|unlock|delete` 의 `X-Tenant-Id` 해석 — `TASK-MONO-735`가 이미 additive 로 정의했다. 이 티켓은 계약을 바꾸지 않고 **배선 결함**만 고친다(원인이 계약 위반이면 재검토).

# Edge Cases

| 상황 | 기대 |
|---|---|
| 헤더가 실제로 다른 테넌트 값(예: 운영자 홈 테넌트 · 워크로드 client 의 기본 테넌트)으로 도착한다 | 그 값을 만드는 계층을 고친다 — account-service 판정 로직은 손대지 않는다 |
| 헤더가 아예 도착하지 않는다(빈 문자열이 `X-Tenant-Id: ecommerce`로 오인됐을 뿐) | `namesTenant` 판정 자체가 구체값으로 오판하고 있는지 재확인 — 이 경우 (b) 경로로 떨어져야 정상 |
| 같은 이메일이 테넌트마다 다른 계정으로 존재 | 잠금은 그 계정 id 하나만 — 원인 수정이 다른 테넌트 계정에 영향을 주지 않는지 확인 |

# Failure Scenarios

1. **단위 테스트만으로 닫는다** → `TASK-MONO-735`도 단위 테스트 전부 초록이었지만 라이브에서 404였다. 결과 상태(AC-2)로만 판정한다.
2. **헤더 부재 셀만 다시 추가하고 「올바른 구체 테넌트」 셀을 또 빼먹는다** → 같은 구멍이 재발한다(이번 결함이 바로 그 구멍이다).
3. **원인을 확인하지 않고 admin-service·product-service 양쪽에 「테넌트 헤더를 다시 보낸다」는 식으로 겹쳐 고친다** → 실제 원인(예: 공통 전파 계층)을 못 찾은 채 증상만 가린다.
