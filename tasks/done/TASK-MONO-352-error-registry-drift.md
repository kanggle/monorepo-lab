# Task ID

TASK-MONO-352

# Title

에러코드 레지스트리 드리프트 해소 — 라이브 코드 37개 미등록 + 유령 행 1개(`PO_STATE_` 오타), 그리고 **재발을 막는 CI 가드** 신설

# Status

done

# Owner

monorepo

# Task Tags

- contracts
- ci
- chore

---

# Goal

`platform/error-handling.md` 는 스스로를 이렇게 규정한다:

> **Change protocol**
> - New platform-common error codes → add to this file only.
> - New domain-specific error codes → add to this file **and** cross-reference from the matching `rules/domains/<domain>.md`.
> - Do not duplicate the error table inside domain rule files. **This document is the single authoritative registry.**

**그 프로토콜이 37번 지켜지지 않았다.** 라이브 서비스가 HTTP 에러 봉투의 `code` 필드에 실어 보내는 코드 37개가 이 파일에 없다. 게다가 반대 방향의 결함도 하나 있다 — **아무도 발행하지 않는 유령 행**.

## 왜 이게 실제 문제인가

레지스트리는 CLAUDE.md § Source of Truth Priority 의 **5층**(`platform/`)이고 프로젝트 계약(6층)보다 **위**다. 즉 "이 코드가 존재하는가 / 어떤 상태인가" 의 권위다. 그런데:

- `ORG_NODE_*`(ADR-047), `PARTNERSHIP_*`/`PARTICIPANT_*`(ADR-045), `SUBSCRIPTION_*`, `ACCESS_CONDITION_UNMET`(ADR-026/028) 등 **최근 ADR 들이 추가한 코드가 통째로 빠져 있다.** 새 프로젝트를 부트스트랩하거나 표준화 감사를 돌리는 사람은 이 코드들의 존재를 **원리적으로 알 수 없다**.
- 프런트/BFF 가 `code` 로 분기할 때 참조할 단일 카탈로그가 **불완전**하다.

**계약(6층)은 무사하다** — 37개 중 HTTP 계약에 기재된 것은 **전부 코드와 상태가 일치**한다(불일치 0건). 즉 이건 계약 위반이 아니라 **레지스트리만의 결손**이며, 그래서 수정 방향이 유일하다(계약·코드가 이미 합의한 값을 레지스트리에 옮겨 적으면 된다).

## 유령 행 — `PO_STATE_TRANSITION_INVALID` (오타)

| | 값 |
|---|---|
| 레지스트리 `:420` | `PO_STATE_TRANSITION_INVALID` (422) |
| **발행 코드** | **0건** (`grep` 전수) |
| 실제 발행 | `PO_STATUS_TRANSITION_INVALID` (scm `GlobalExceptionHandler:66`, 422) |
| 계약 `procurement-api.md` | `PO_STATUS_TRANSITION_INVALID` (`:193 :211 :231 :274 :345 :378`) |

**STATE vs STATUS.** 코드와 계약은 일치하고 **레지스트리만 오타**다. 유령 행 하나와 미등록 코드 하나가 사실 **같은 것**이었다.

## 재발 방지 — 프로토콜에 강제력이 없다

Change protocol 은 **산문 규칙**이고 아무것도 이를 검사하지 않는다. 37개를 등록만 하고 끝내면 다음 ADR 이 38번째를 만든다. 그래서 본 task 의 절반은 **CI 가드**(`scripts/check-error-code-registry.sh`)다.

### 가드는 sound 하되 complete 하지 않다 (의도된 설계)

가드는 **명백히 HTTP 에러코드인 것만** 수집한다 — 에러 봉투 팩토리(`ErrorResponse.of("CODE"` / `ApiErrorBody.of("CODE"`)에 리터럴로 넘어가거나 도메인 예외의 `super("CODE", …)` 로 실려가는 것. 둘 다 **구성상** HTTP 응답의 `code` 필드에 도달한다.

**모든 SCREAMING_SNAKE 리터럴을 긁지 않는다.** 그렇게 하면 오탐이 생기고, **오탐은 누락보다 나쁘다** — HTTP 에러코드가 아닌 것을 등록하도록 사람을 압박하기 때문이다. 실제로 조사 중 걸러낸 함정들:

| 리터럴 | 정체 | 등록하면 |
|---|---|---|
| `CREDENTIALS_INVALID` | `LoginFailed` **이벤트의 `failureReason` enum**. 같은 경로의 HTTP 코드는 `INVALID_CREDENTIALS` (`AuthExceptionHandler:19-22` 가 "HTTP 코드와 이벤트 enum 은 별개 계약" 이라 명시) | 존재하지 않는 HTTP 코드를 문서화 |
| `EMAIL_DUPLICATE` · `ALREADY_LOCKED` | **bulk 응답 본문의 per-item 코드**(`FailedItem` / `OUTCOME_*`). 2xx 안에 담기며 에러 봉투에 오지 않는다 | 에러 카탈로그를 bulk 결과 코드로 오염 |
| `ACCOUNT_DEACTIVATED` | ecommerce 로그인의 **감사/이벤트 사유**. 그 경로는 곧바로 `InvalidCredentialsException` 을 던진다 — **계정 비활성 사실을 노출하지 않기 위한 의도적 설계** | **API 가 일부러 절대 반환하지 않는 코드**를 문서화 = 보안 설계 배신 |
| `WEIGHTED_AVERAGE` | FX 원가법 enum | 명백한 오염 |

필드에 담긴 코드(`private static final String CODE = "…"` → `super(CODE, …)`)는 가드를 통과할 수 있다. **이건 인정된 갭이다** — 헛되이 터지지 않는 가드라야 사람들이 유지하고, 지금까지 드리프트한 코드들은 전부 가드가 잡는 형태였다.

---

# Scope

## IN

**공유 (root)**
- `platform/error-handling.md`
  - **유령 행 정정**: `PO_STATE_TRANSITION_INVALID` → `PO_STATUS_TRANSITION_INVALID` (`:420`)
  - **37개 등록** — 각 코드의 HTTP 상태는 **발행 핸들러에서 추적**했고 계약과 대조했다(불일치 0건). 배치:
    - `Tenant [saas]` — `SUBSCRIPTION_*`(3) · `ORG_NODE_*`(7, `ORG_NODE_INVARIANT_VIOLATION` 포함) · `ORG_ADMIN_GRANT_OUT_OF_CEILING` · `PARTNERSHIP_*`(5) · `PARTICIPANT_*`(3) · `TENANT_SCOPE_MISMATCH`
    - `Admin [saas]` — `ACCESS_CONDITION_UNMET` · `ACCOUNT_IDENTITY_UNRESOLVABLE` · `ASSIGNMENT_ALREADY_EXISTS` · `IDENTITY_LINK_EMAIL_MISMATCH` · `OPERATOR_ACCOUNT_NOT_FOUND` · `OPERATOR_ALREADY_LINKED` · `OPTIMISTIC_LOCK_CONFLICT`(등록된 별칭 — `CONCURRENT_MODIFICATION` 선례) · `ROLE_GRANT_FORBIDDEN`
    - `Validation`(Platform-Common) — `INVALID_REQUEST`(ecommerce order + iam admin 양쪽 발행 → 도메인 섹션 중복 대신 공통에 1행)
    - `Order [ecommerce]` — `DUPLICATE_ORDER_REQUEST`
    - `Settlement [ecommerce]` — `PERIOD_ALREADY_CLOSED` · `PERIOD_NOT_CLOSED` · `PERIOD_WINDOW_INVALID`
    - `Notification [ecommerce]` — `PUSH_NOT_CONFIGURED`
    - `Product [ecommerce]` — `SELLER_NOT_FOUND`
    - `Procurement [scm]` — `REQUEST_ERROR`(**상태 pass-through** — 아래 Edge Cases)
    - `Reconciliation / Ledger [fintech]` — `SETTLEMENT_AMOUNT_INVALID`
- `rules/domains/{saas,scm,fintech}.md` — Change protocol 이 요구하는 **교차참조**(불릿 추가). `rules/domains/ecommerce.md` 는 코드를 나열하지 않고 **섹션 매핑만** 하므로 수정 불필요.
- `scripts/check-error-code-registry.sh` — **신규 가드**
- `.github/workflows/` — 가드를 CI 에 배선

## OUT (의도적 제외 — 근거 포함)

- **`CREDENTIALS_INVALID` / `EMAIL_DUPLICATE` / `ALREADY_LOCKED` / `ACCOUNT_DEACTIVATED` / `WEIGHTED_AVERAGE` 등록 금지.** HTTP 에러코드가 아니다(위 표). 특히 `ACCOUNT_DEACTIVATED` 등록은 **API 가 의도적으로 숨기는 사실을 문서화**하는 것이다.
- **코드 무수정.** 37개 전부 코드가 옳다(계약과 일치). 고칠 대상은 레지스트리다.
- **`INVALID_STATE` / `INVALID_CREDENTIALS` / `INVALID_CODE` 상태 모순** → `TASK-MONO-350`(AC-0 사람-결정 게이트). 방향이 유일하지 않다.
- **`ACCESS_CONDITION_UNMET` · `SUBSCRIPTION_DOMAIN_OUT_OF_CEILING` 의 HTTP 계약 부재** — 이 둘은 `specs/contracts/http/` 에도 없고 서비스 스펙에만 있다(각각 `rbac.md:195` 403 / `data-model.md:186` 422, 코드와 일치). **레지스트리 등록은 본 task 에서 하되, 계약 표면 추가는 iam 프로젝트 task 로 분리**(본 task 는 root 이고 프로젝트 계약을 건드리면 범위가 섞인다).
- 가드의 **completeness 확장**(필드-담김 코드 탐지) — 오탐 위험이 커진다. 인정된 갭.

---

# Acceptance Criteria

- **AC-1** — `scripts/check-error-code-registry.sh` 가 **현재 main 에서 exit 1**(37개 드리프트 검출)이고, 본 task 완료 후 **exit 0**.
- **AC-2 (유령 행)** — `PO_STATE_TRANSITION_INVALID` 가 레지스트리에서 사라지고 `PO_STATUS_TRANSITION_INVALID`(422)가 등록된다. 코드·계약·레지스트리 **셋이 같은 문자열**을 말한다.
- **AC-3 (상태 정확성)** — 등록된 각 코드의 HTTP 상태가 **발행 핸들러의 실제 반환값**과 일치한다. 추측 금지 — 근거는 `file:line`.
- **AC-4 (오염 0)** — 등록된 코드 중 **HTTP 에러 봉투에 도달할 수 없는 것이 하나도 없다**. 위 4개 함정 리터럴은 등록되지 않는다.
- **AC-5 (Change protocol 준수)** — 도메인 코드가 `rules/domains/{saas,scm,fintech}.md` 에서 교차참조된다.
- **AC-6 (가드가 실제로 문다 — mutation-check)** — 레지스트리에서 임의의 한 행을 지웠을 때 가드가 **exit 1 + 그 코드를 지목**해야 한다. 통과하는 가드는 무는 가드가 아니다.
- **AC-7 (가드가 헛되이 물지 않는다)** — 현재 트리의 **비-에러코드 리터럴**(`CREDENTIALS_INVALID` 등)에 대해 가드가 **침묵**한다. 오탐 0 을 실측으로 확인한다.
- **AC-8** — CI 가 가드를 실행하고, 가드 실패가 PR 을 막는다.

---

# Related Specs

- `platform/error-handling.md` (§ Registry Structure — Change protocol)
- `rules/domains/{saas,scm,fintech,ecommerce}.md` § Standard Error Codes
- 선행 판례: `TASK-MONO-348`(한 코드 두 상태 = 결함) · `TASK-BE-500`(계약↔코드 드리프트) · `TASK-MONO-244`(의도적 이중등록/별칭 선례)

# Related Contracts

- 무수정. 대조 대상: `projects/*/specs/contracts/http/*.md` (37개 중 기재분 **전부 코드와 일치** — 불일치 0건)

---

# Edge Cases

- **`REQUEST_ERROR` 는 상태가 고정이 아니다.** scm `GlobalExceptionHandler#handleResponseStatus` 는 `ResponseStatusException` 의 상태를 **그대로 통과**시키고, 401→`UNAUTHORIZED` / 403→`PERMISSION_DENIED` / **그 외→`REQUEST_ERROR`** 로 코드만 고른다. scm main 코드에는 **`ResponseStatusException` 을 던지는 곳이 없지만**(테스트에만 존재), Spring 자신이 그 서브클래스를 던진다(예: `NoResourceFoundException`, 404). 따라서 도달 가능하며 **상태는 pass-through** 로 등록한다.
- **`OPTIMISTIC_LOCK_CONFLICT` 는 세 번째 409 별칭**이다(`CONFLICT` · `CONCURRENT_MODIFICATION` 에 이어). admin-service 가 부모 핸들러의 매핑을 **의도적으로 오버라이드**해 계약이 요구하는 canonical 코드를 낸다(`AdminExceptionHandler:488-499` 주석). `TASK-MONO-244` 의 별칭 선례를 따라 그 사실을 명시해 등록한다.
- **`ORG_NODE_INVARIANT_VIOLATION` 은 폴백 코드**다. admin-service 는 org-node 불변식을 재구현하지 않고 account-service(권위)의 코드를 **그대로 통과**시킨다; 권위가 코드를 비워 보낼 때만 이 폴백이 나간다(`OrgNodeInvariantViolationException:17`). 등록 설명에 그 성격을 적어 "왜 이 코드가 보이면 안 되는지" 를 남긴다.
- **`INVALID_REQUEST` 는 두 도메인이 발행**한다(ecommerce order 400 · iam admin 400). 도메인 섹션에 두 번 적으면 레지스트리가 **중복표**가 된다(Change protocol 이 금지하는 바로 그것). Platform-Common `Validation` 에 1행 + emitters 명시.

# Failure Scenarios

- **가드가 오탐을 낸다** → 사람들이 비-에러코드를 등록해 레지스트리를 오염시키거나, 가드를 꺼버린다. **오탐 0 이 completeness 보다 중요하다**(AC-7).
- **상태를 추측해서 적는다** → 레지스트리가 코드와 어긋난 채 "권위" 를 자처하게 된다. 이건 지금 고치려는 병을 다른 형태로 재생산하는 것이다. 모든 상태는 핸들러에서 추적한다(AC-3).
- **`PO_STATE_` 를 남겨둔 채 `PO_STATUS_` 만 추가** → 유령 행이 그대로 남아 "두 코드가 있다" 고 읽힌다. 정정(rename)이지 추가가 아니다.
- **`ACCOUNT_DEACTIVATED` 를 "빠진 코드" 로 보고 등록** → 로그인이 계정 상태를 노출하지 않으려 의도적으로 숨긴 사실을 공개 카탈로그에 적는 것. 스윕 결과를 기계적으로 신뢰하면 정확히 이 실수를 한다.
