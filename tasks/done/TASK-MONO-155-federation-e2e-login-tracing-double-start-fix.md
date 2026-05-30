# Task ID

TASK-MONO-155

# Title

federation-hardening-e2e 하니스 fix — `driveOidcPkceLogin` 의 수동 `tracing.start()` 가 테스트 내부 로그인(`loginAsAcmeOperator`, MONO-154)에서 Playwright-managed 트레이싱과 이중 시작되어 throw 하는 회귀 수정.

# Status

done

> **완료 (2026-05-31)**: impl PR #975 (squash `83f52d97`). MONO-154 회귀 fix-forward — `driveOidcPkceLogin` 에 `manageTracing=true` 파라미터 추가, 수동 트레이싱을 `CI && manageTracing` 게이트. `loginAsSuperAdmin`(global-setup)=true(무변경) / `loginAsAcmeOperator`(in-test)=false(테스트 러너 자체 트레이싱 위임). login.ts only. **검증**: federation-e2e 재실행 run 26697320370 = **9 passed (37.2s) SUCCESS**(이중 tracing.start throw 해소 → entitlement 단언 실행 GREEN). 3차원(MERGED `83f52d97` / tip 일치 / pre-merge 0). **메타**: MONO-154 워크플로 검증이 tsc 로는 못 잡는 런타임 이중-tracing 회귀를 포착 — workflow_dispatch 실검증의 가치 사례. (부수: #975 squash 에 MONO-154 task 의 ready→done 이동이 leak — 본 close 에서 MONO-154 Status 정정.)

# Owner

backend

# Task Tags

- code
- e2e
- ci

---

# Dependency Markers

- **fix-forward of**: TASK-MONO-154(#974 `ef26d530`) — federation-e2e workflow_dispatch(run 26696990585) 에서 신규 `entitlement-trust-crossdomain.spec.ts` 가 `Error: tracing.start: Tracing has been already started`(login.ts:77)로 실패. entitlement 단언 도달 전 로그인 fixture 에서 throw.
- **model**: 분석=Opus 4.8 / **구현 권장=Opus**(2-line 하니스 fix; 직접).

---

# Goal

`driveOidcPkceLogin` 의 수동 `context.tracing.start/stop` 은 **global-setup 컨텍스트 전용**(거기엔 Playwright per-test 트레이싱이 없음). MONO-154 의 `loginAsAcmeOperator` 는 **테스트 내부**에서 호출되는데, 그 테스트 컨텍스트는 이미 `playwright.config use.trace` 로 트레이싱 중 → 두 번째 `tracing.start()` 가 "Tracing has been already started" throw. 기존 spec 들은 global-setup 의 storageState 를 써서(로그인이 테스트 밖) 안 걸렸음.

수정: `driveOidcPkceLogin` 에 `manageTracing: boolean = true` 파라미터 추가, 수동 트레이싱을 `CI && manageTracing` 으로 게이트. `loginAsSuperAdmin`(global-setup) = true(무변경). `loginAsAcmeOperator`(in-test) = false(테스트 러너 자체 트레이싱에 위임).

# Scope

## In scope

- `tests/federation-hardening-e2e/fixtures/login.ts`: `driveOidcPkceLogin` 5th param `manageTracing=true` + tracing 게이트 `!!process.env.CI && manageTracing`; `loginAsAcmeOperator` 가 false 전달. 주석 추가.

## Out of scope

- 신규 spec 단언 로직/seed 변경(MONO-154 머지본; 본 fix 는 로그인 fixture 만).
- 기존 `loginAsSuperAdmin`/spec 동작 변경.
- production code.

# Acceptance Criteria

- **AC-1**: `loginAsSuperAdmin`(global-setup) 트레이싱 동작 무변경(`manageTracing` 기본 true).
- **AC-2**: `loginAsAcmeOperator`(in-test) 는 수동 트레이싱 미시작 → 이중-start throw 해소.
- **AC-3 (검증)**: 머지 후 `gh workflow run federation-hardening-e2e.yml` → `entitlement-trust-crossdomain.spec.ts` 가 로그인 통과 후 entitlement 단언(scm/erp forbidden, finance/wms not-forbidden) 실행 + 8 spec GREEN.
- **AC-4 (scope-lock)**: 변경 = login.ts 만.

# Related Specs

- `docs/adr/ADR-MONO-018-...md`(federation hardening 하니스). MONO-154 task.

# Related Code

- `tests/federation-hardening-e2e/fixtures/login.ts`.

# Edge Cases

- global-setup 는 tracing 미시작 상태 → start 정상; in-test 는 이미 시작 → skip.
- tracing.stop 도 manageTracing 게이트 하에만(이미 finally 의 try/catch 보호).

# Failure Scenarios

- manageTracing 기본을 false 로 하면 global-setup 트레이스 artifact 소실 → 기본 true 유지.

---

# Implementation Design Notes

- 2-line 게이트 + 파라미터. CI 검증 = workflow_dispatch(nightly 채널).
- 구현 = Opus(직접).

---

# Notes

- MONO-154 의 워크플로 검증이 잡은 하니스 회귀. tsc 로는 못 잡는 런타임 이중-tracing — workflow_dispatch 실검증의 가치 사례. 후속: 본 fix 머지 + 재실행으로 MONO-154 AC-5(entitlement 단언) 최종 확인.
