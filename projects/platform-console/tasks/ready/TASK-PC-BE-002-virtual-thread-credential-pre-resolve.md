# Task ID

TASK-PC-BE-002

# Title

console-bff — Operator Overview fan-out 의 virtual thread × `@RequestScope` `ScopeNotActiveException` 회귀 fix (TASK-PC-FE-011 머지 후 main RED — 0순위 회귀 회복)

# Status

ready

# Owner

backend

# Task Tags

- code
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **fixes (main 회귀)**: TASK-PC-FE-011 머지 (`6d0f6e97`) 이후 main `Integration (platform-console console-bff, ...)` job 가 RED. b378b201 (#673 close chore docs-only) / 71974cf6 (#674 spec docs-only) 두 후속 main push 의 IT 도 같은 실패.
- **blocks**: `TASK-PC-FE-012` (PR #675) — frontend test 1줄 fix 가 console-bff IT 회귀 mirror 때문에 머지 불가. 본 fix 가 main GREEN 회복하면 #675 rebase 후 머지 unblock.
- **honest record (BE-303 ledger)**: TASK-PC-FE-011 INDEX entry 는 *"self-CI 20/20 GREEN ... cycle 4 GREEN"* 이라 기록됐지만 머지 직전 PR #672 의 마지막 PR-time check 는 `Integration (platform-console console-bff): fail 55s` 였음 — 즉 BE-303 객관 머지 검증의 **CI 결과 차원이 누락**된 채 머지된 것. 회고는 memory 에 별도 저장 (별 작업), 본 task 는 회귀 회복만.
- **honest-fix chain**: 본 PR 의 IT 단언은 STRENGTHEN-ONLY. 새 회귀-차단 단언 추가 OK; 기존 단언 약화/skip 절대 불가.

# Goal

`OperatorOverviewCompositionUseCase.fanOut()` 가 Java 21 virtual-thread executor 로 5 leg 를 병렬 실행하는데, 각 leg 의 `bearerFor(domain)` 가 `CredentialSelectionAdapter.selectFor(domain)` 를 호출하고 그 안에서 `@RequestScope` `OperatorCredentialContext` proxy 를 dereference 한다. Spring `RequestContextHolder` 는 default 에서 thread-local (inheritable=false) → child virtual thread context 에 active request attribute 가 없어 `ScopeNotActiveException: Scope 'request' is not active for the current thread` throw → time() catch 의 generic `RuntimeException` arm 이 `DOWNSTREAM_ERROR` degrade 로 mapping. 결과: 모든 happy-path / 401 cross-leg / per-leg-forbidden / per-leg-degraded IT 가 5 leg `DOWNSTREAM_ERROR` 로 collapse, 단언 불일치 → IT FAIL.

본 task 는 **fan-out 시작 *전* main servlet thread 에서 5 domain 의 `OutboundCredential` 을 한 번에 pre-resolve** 한다. 결과는 `Map<DomainTarget, OutboundCredential>` (또는 missing 표현 sentinel 포함) 으로 fan-out 의 각 virtual thread 에 plain value 로 전달. 각 virtual thread 는 더 이상 `@RequestScope` bean 을 dereference 하지 않으므로 회귀가 사라진다. ADR-MONO-017 D4 (sealed switch, no fallback, single-truth) 는 *완전 보존* — pre-resolve 단계가 동일 sealed switch 를 동일하게 호출, fan-out 단계는 pre-resolved record 의 `token()` 만 읽음.

# Scope

## In Scope

### Backend (`apps/console-bff/src/main/java/.../application/usecase/OperatorOverviewCompositionUseCase.java`)

- `compose(tenantId)` 시작점 (main servlet thread, request scope active) 에서 5 domain credential pre-resolve:
  - `Map<DomainTarget, OutboundCredential>` 결과 + 별 `Set<DomainTarget> missingDomains` (또는 동등한 sentinel union type) — `selectFor()` 가 `MissingCredentialException` 을 throw 한 domain 은 missing 으로 표시.
  - missing 인 GAP (operator token 부재) 등은 D4 fail-closed 원칙 그대로 — 해당 leg 의 outcome 을 `forbidden / MISSING_PREREQUISITE` 로 미리 결정 (outbound HTTP 절대 미발사). finance MVP option (b) 와 동일 처리.
- `fanOut(tenantId)` 시그니처 변경: `fanOut(tenantId, preResolved)` — 각 `call*(tenantId, cred)` 에 resolved credential 직접 전달.
- `bearerFor(OutboundCredential cred)` 로 변경 (또는 `bearerFor(DomainTarget)` 폐기). switch 의 sealed shape 보존:
  ```java
  return switch (cred) {
      case OperatorToken t -> t.token();
      case GapOidcAccessToken t -> t.token();
  };
  ```
- 각 `callX` 가 더 이상 `credentialSelection.selectFor(...)` 호출하지 않음. 적용 가능한 경우 pre-resolved credential 을 받아 `bearerFor(cred)` 만 호출.
- D4 HARD INVARIANT 의 Javadoc / 단위 테스트는 변경 없음 — sealed switch 가 5-row exhaustive, default arm 부재 그대로 보존.

### Tests (impl PR 단계)

- `OperatorOverviewIntegrationTest` 10 IT 모두 GREEN. STRENGTHEN-ONLY.
- 단위 테스트: `OperatorOverviewCompositionUseCaseTest` (있다면) — pre-resolve 호출 path 가 main thread 에서 일어남을 단언 (e.g. `selectFor()` mock invocation count = 5 회, virtual thread 안이 아닌 caller thread 에서 호출).
- 회귀 차단 단언 추가: `OperatorOverviewIntegrationTest happy_path_5_cards_all_ok` 가 5 leg 모두 `ok` outcome 인지 (이전과 같음) + ScopeNotActiveException 가 server log 에 emit 되지 않음 (있다면 logback appender capture).

### Documentation

- `OperatorOverviewCompositionUseCase` Javadoc 의 invariant section 에 1-2 줄 추가 — "credential pre-resolve happens in the servlet thread before fan-out; virtual threads never touch the request-scoped bean".
- 본 task md + INDEX update.

## Out of Scope

- `console-integration-contract.md` § 2.4.9 / § 2.4.9.1 spec 변경 — D4 / D5 / D6 / 모든 hard invariant byte-unchanged (sealed switch, 5-row, no fallback, all-down 200 envelope, fixed 5-card order, tenant pass-through, 401 cross-leg, finance MVP option (b)). 본 fix 는 *implementation* 차원에서만 thread-context-safe 하게 재배치.
- `OperatorCredentialContext` 의 `@RequestScope` 자체는 보존 (proxy 가 main servlet thread 에서 dereference 되는 한 정상 동작). request-scope → inheritable / Scope-aware decorator 도입 등 더 큰 refactor 는 별 task.
- ADR-MONO-017 D1-D8 변경 부재 (HARDSTOP-04 discipline).
- TASK-PC-FE-012 의 frontend test fix — 별 PR (#675) 가 main GREEN 회복 후 rebase 머지.
- TASK-PC-BE-003 (PrometheusScrapeEndpoint deprecation upgrade) — 별 task, 이 fix 후 진행.

# Acceptance Criteria

1. `OperatorOverviewIntegrationTest` 10 IT 모두 GREEN — 회귀 해소.
2. `ScopeNotActiveException` 가 server log 에 emit 되지 않음 (production code path 에서 100% 부재) — IT log assertion 또는 단위 테스트로 단언.
3. ADR-MONO-017 D1-D8 hard invariants byte-unchanged: sealed switch 5-row exhaustive, no default arm, no fallback, 5-card fixed order, all-down 200 envelope, tenant pass-through, 401 cross-leg, finance MVP option (b).
4. `console-integration-contract.md` § 2.4.9 / § 2.4.9.1 attestation count § 3 = 16 byte-unchanged.
5. 단위 테스트 0 regression. Unit / Slice / IT 모두 통과.
6. CI self-job `Integration (platform-console console-bff, Testcontainers + WireMock JWKS)` GREEN.
7. 다른 producer (5 producer apps) / console-web / specs / ADR 모두 byte-unchanged.
8. self-CI 전체 ALL GREEN (회귀 0).

# Related Specs

- `projects/platform-console/specs/services/console-bff/architecture.md` — Hexagonal 분리, virtual-thread fan-out, per-domain credential dispatch.
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9 (BFF surface frame) + § 2.4.9.1 (MVP Operator Overview composition surface).

# Related Contracts

- ADR-MONO-017 § D4 (per-domain credential dispatch, HARD INVARIANT) — sealed switch shape preserved.

# Edge Cases

- **GAP operator token 부재** (X-Operator-Token header missing): pre-resolve 단계에서 `selectFor(GAP)` 가 `MissingCredentialException` throw → GAP leg outcome = `forbidden / MISSING_PREREQUISITE`, outbound HTTP 미발사. fan-out 의 다른 4 leg 는 정상 실행.
- **GAP OIDC access token 부재** (defensive — Spring Security 가 미리 401 처리): 가능성 낮지만 동일 mechanism (해당 leg forbidden + outbound 미발사). main thread 에서 한 번 catch.
- **`@RequestScope` proxy 가 main thread 에서도 fail**: 가능성 낮음 (controller 가 servlet thread). 만약 발생하면 inbound handler 가 catch → controller-level 401.
- **5 leg 동시 pre-resolve 실패** (operator token + GAP OIDC 둘 다 부재): GAP=forbidden + 4 도메인 모두 forbidden — composition 은 D5.A 에 따라 200 envelope + 5 카드 모두 forbidden 으로 emit (cross-leg 401 가 아니므로 401 collapse 미발생).

# Failure Scenarios

| 조건 | 본 PR 의 반응 |
|---|---|
| pre-resolve 가 main thread 에서도 ScopeNotActiveException | inbound handler 가 catch → controller-level 401 TOKEN_INVALID (Spring Security 가 미리 처리해야 정상). |
| virtual thread 안에서 plain value access 로 변경 후에도 IT FAIL | 다른 회귀가 존재 → 진단 surface (logback appender capture, AssertJ `.as(body)`) 강화. STOP 후 별 fix-task. |
| ADR-MONO-017 D4 sealed switch 가 amend 필요한 케이스 발견 | 본 PR scope 아님. ADR-MONO-018 PROPOSED 별 task. |
| TASK-PC-FE-012 (#675) rebase 시 conflict | conflict 부재 보장: 본 PR 은 console-bff Java 만, #675 는 console-web TypeScript test 1줄 만. 두 PR scope disjoint. |

---

# Implementation Notes (impl PR 단계 reference)

권장 변경 골자:

```java
public List<CompositionLeg> compose(String tenantId) {
    // (0) Pre-resolve 5 credentials in the servlet thread (request scope active).
    Map<DomainTarget, OutboundCredential> preResolved = new EnumMap<>(DomainTarget.class);
    Map<DomainTarget, CompositionLeg> earlyDecided = new EnumMap<>(DomainTarget.class);
    for (DomainTarget domain : CARD_ORDER) {
        try {
            preResolved.put(domain, credentialSelection.selectFor(domain));
        } catch (MissingCredentialException mce) {
            emitErrorCounter(domain, "missing_prerequisite");
            earlyDecided.put(domain,
                    CompositionLeg.outcomeOnly(LegOutcome.forbidden(domain, "MISSING_PREREQUISITE")));
        }
    }

    Map<DomainTarget, CompositionLeg> results = fanOut(tenantId, preResolved, earlyDecided);
    // ... 이후 cross-leg 401 / per-leg counter / fixed-order assembly 는 그대로.
}

private CompositionLeg callGap(String tenantId, OutboundCredential cred) {
    return time(DomainTarget.GAP, () -> {
        String bearer = bearerFromCred(cred);
        Map<String, Object> data = gapPort.read(tenantId, bearer);
        return CompositionLeg.ok(LegOutcome.ok(DomainTarget.GAP), data);
    });
}

private static String bearerFromCred(OutboundCredential cred) {
    return switch (cred) {
        case OutboundCredential.OperatorToken t -> t.token();
        case OutboundCredential.GapOidcAccessToken t -> t.token();
    };
}
```

`fanOut(tenantId, preResolved, earlyDecided)` 가 `earlyDecided` 에 있는 domain 은 그 leg 그대로 사용; 아니면 pre-resolved credential 로 fan-out.

추가 단언 (IT logback appender capture):

```java
// IT @AfterEach 또는 happy_path 끝에:
assertThat(captured).noneSatisfy(evt ->
    assertThat(evt.getMessage()).contains("ScopeNotActiveException"));
```

---

# Approval

- 분석 = Opus 4.7
- 구현 권장 = Opus 4.7 (concurrency / Spring scope / sealed switch 보존 까다로움)
- 리뷰 = Opus 4.7 (dispatcher 독립 재검증 + acceptance criteria 8/8 단언)
