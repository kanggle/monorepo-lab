# Task ID

TASK-BE-281

# Title

`auth-service/architecture.md` § application + § infrastructure 의 credential-lookup spec lag → tenant-info lookup contract 결정으로 align (refactor-spec all 2026-05-14 GAP audit critical #4)

# Status

done

# Owner

global-account-platform

# Task Tags

- gap
- auth-service
- spec
- refactor
- spec-lag

---

# Goal

`/refactor-spec all --dry-run` (2026-05-13~14) GAP audit critical #4 finding closure.

`projects/global-account-platform/specs/services/auth-service/architecture.md` § application (L157) + § infrastructure/ (L168) 의 표현이 stale:

- `AccountServiceClient.lookupCredential(email)` 호출 명시 — 그러나 **TASK-BE-063 (credential ownership)** closure 후 credential 자체는 auth-service 의 local `CredentialRepository` 가 소유하므로 cross-service lookup 대상이 아님.
- "프로토콜은 `(email, tenant_id?)` 입력으로 단일 row 응답을 강제하거나, 다중 매칭 시 `LOGIN_TENANT_AMBIGUOUS` 400 ... (상세는 ... 갱신 시 확정)" — 실제 contract `auth-to-account.md` 의 `GET /internal/accounts/tenant-info` (TASK-BE-229) 가 이미 결정됨: query = `email` + optional `tenantId`, response 200 = array `[{accountId, tenantId, tenantType}]`, length 0 → 미존재 / length 1 → 정상 / length ≥2 → presentation 에서 LOGIN_TENANT_AMBIGUOUS 변환.

즉 contract decision 은 이미 명확, architecture.md 의 "(갱신 시 확정)" 표현이 lag 상태. spec/contract sync 회복.

provenance: `/refactor-spec all --dry-run` 2026-05-13~14 GAP audit high #4 (architecture promises "갱신 시 확정" but contract already decided without updating architecture), TASK-BE-280 (PROJECT.md service_types align) 직접 sibling 패턴.

---

# Scope

## In Scope

### A. `auth-service/architecture.md` § application (L157) 정정

stale 표현:
> `LoginUseCase`는 (1) account-service `AccountServiceClient.lookupCredential(email)`을 호출하여 응답에서 `tenant_id`·`tenant_type`·`accountId`를 받음

실제:
- credential 자체는 auth-service local `CredentialRepository` (TASK-BE-063 closure 결과)
- account-service 호출은 `GET /internal/accounts/tenant-info` (TASK-BE-229) — tenant context 결정용
- response = array shape (0/1/N) + presentation 의 LOGIN_TENANT_AMBIGUOUS 변환

stale "(갱신 시 확정)" 제거 + contract 의 array response 분기 (0/1/N) 명시.

### B. `auth-service/architecture.md` § infrastructure/ (L168) 정정

stale 표현:
> `client/AccountServiceClient`는 account-service를 내부 HTTP로 호출 ... **credential lookup 응답에 `tenant_id` 포함**, 로그인 use-case가 이를 토큰 발급에 사용

실제:
- credential lookup 자체 없음 (TASK-BE-063)
- tenant-info lookup 응답에 `tenant_id`·`tenant_type`·`accountId` 포함

"credential lookup" → "tenant-info lookup" 명명 통일.

### C. cross-ref 검증

- L155 의 internal contract 링크 ([specs/contracts/http/internal/auth-to-account.md](../../contracts/http/internal/)) 가 본 task 의 source-of-truth 인지 verify (이미 그러함).
- L174 § Integration Rules HTTP 컨트랙트 (내부) 의 "credential lookup(응답에 `tenant_id`·`tenant_type` 포함)" 표현도 동일 align.

## Out of Scope

- contract 자체 변경 (auth-to-account.md 무변경 — source of truth 보존).
- LoginUseCase / RefreshTokenUseCase 의 production 코드 (architecture.md spec lag 만 align).
- TASK-BE-063 credential ownership 영역의 spec 정정 (이미 별 inline note 로 충분 — agent audit 의 CRIT-3 은 false positive, fix 미적용).
- 다른 service 의 architecture.md (account/security/admin 등) — 본 task scope 밖.

---

# Acceptance Criteria

### Impl PR

- [x] `auth-service/architecture.md` L157 의 `AccountServiceClient.lookupCredential(email)` → `lookupTenantInfo(email, tenantId?)` 표기 정정 (+ TASK-BE-229 cite).
- [x] L157 의 "(상세는 ... 갱신 시 확정)" 표현 제거 + contract 의 array response 분기 (0/1/N) + LOGIN_TENANT_AMBIGUOUS 변환 timing (presentation layer) 명시.
- [x] L157 의 단계 표기 정정 — (1) auth-service local CredentialRepository (TASK-BE-063), (2) account-service tenant-info lookup, (3) 토큰 발급, (4) 이벤트 발행.
- [x] L168 § infrastructure/ 의 "credential lookup 응답" → "tenant-info lookup 응답 (array shape)" 명명 통일.
- [x] L174 § Integration Rules 의 "credential lookup(응답에 ...)" → "tenant-info lookup (응답 array shape)" 명명 통일.
- [x] cross-ref 정확성 검증 (auth-to-account.md L11 TASK-BE-229 note 와 일관 — TASK-BE-229 endpoint + array shape 결정 사항 양쪽 일치).
- [x] HARDSTOP-03 hook PASS (project-specific content 잔존 0).
- [ ] CI self-CI PASS (path-filter gap project — markdown spec-only, 15 SKIP + 1 changes PASS 예상).
- [x] task lifecycle ready → review (in-progress 단계 우회, spec-only single-PR closure).
- [x] gap tasks/INDEX.md 동기 (root INDEX 무영향).

### Close chore PR

- [ ] task Status review → done.
- [ ] git mv tasks/review → tasks/done.
- [ ] gap tasks/INDEX.md ## review 제거, ## done append 1-line outcome.

---

# Related Specs

- `projects/global-account-platform/specs/services/auth-service/architecture.md` (수정 대상).
- `projects/global-account-platform/specs/contracts/http/internal/auth-to-account.md` (source of truth, 무변경).
- `projects/global-account-platform/tasks/done/TASK-BE-063-*` (credential ownership transfer history).
- `projects/global-account-platform/tasks/done/TASK-BE-229-*` (tenant-aware login endpoint introduction).

---

# Related Contracts

본 task = architecture.md 의 spec lag align only. contract (auth-to-account.md) 무변경.

HTTP/event payload 변경 0.

---

# Target Service

`projects/global-account-platform/apps/auth-service/` (spec 정정 대상).

---

# Architecture

GAP auth-service 의 application + infrastructure layer 의 spec lag align. production code 무관 — architecture.md 가 contract 결정과 byte-level 일치하도록 정정.

---

# Implementation Notes

## stale 영역 cite (architecture.md current)

L157 (§ application/):
```
**로그인 흐름의 tenant 컨텍스트 결정**: `LoginUseCase`는 (1) account-service `AccountServiceClient.lookupCredential(email)`을 호출하여 응답에서 `tenant_id`·`tenant_type`·`accountId`를 받음, (2) 비밀번호 검증 통과 시 ... (3) 발행 이벤트 ... 같은 이메일이 두 테넌트에 등록될 수 있으므로 lookup 응답이 다중 row가 가능 → 프로토콜은 `(email, tenant_id?)` 입력으로 단일 row 응답을 강제하거나, 다중 매칭 시 `LOGIN_TENANT_AMBIGUOUS` 400으로 명시적 tenant 선택을 요구한다(상세는 [specs/contracts/http/internal/auth-to-account.md](../../contracts/http/internal/) 갱신 시 확정)
```

L168 (§ infrastructure/):
```
`client/AccountServiceClient`는 account-service를 내부 HTTP로 호출 ... 응답은 내부 DTO로 번역 후 `domain`으로 전달. **credential lookup 응답에 `tenant_id` 포함**, 로그인 use-case가 이를 토큰 발급에 사용
```

L174 (§ Integration Rules):
```
**HTTP 컨트랙트 (내부)**: [specs/contracts/http/internal/auth-to-account.md] — credential lookup(응답에 `tenant_id`·`tenant_type` 포함), 계정 상태 조회
```

## fix target shape (proposed)

L157 새 표현 (예):
```
**로그인 흐름의 tenant 컨텍스트 결정**: `LoginUseCase`는 (1) auth-service local `CredentialRepository.findByEmail(email)` 로 credential 검증, (2) account-service `AccountServiceClient.lookupTenantInfo(email, tenantId?)` 호출 (GET /internal/accounts/tenant-info, TASK-BE-229) 로 array 응답 (length 0/1/N) 수신, (3) length=0 → ACCOUNT_NOT_FOUND, length=1 → 정상 토큰 발급, length≥2 → presentation 에서 LOGIN_TENANT_AMBIGUOUS 400 변환 + 클라이언트 재요청 시 tenantId 명시, (4) `tenant_id`·`tenant_type` 을 access token claim + `refresh_tokens.tenant_id` 컬럼에 영속, (5) 발행 이벤트 (auth.login.succeeded 등) payload 에 `tenant_id` 포함.
```

L168 새 표현:
```
`client/AccountServiceClient`는 account-service tenant-info 엔드포인트를 내부 HTTP 로 호출 ... 응답 (array `[{accountId, tenantId, tenantType}]`) 은 내부 DTO 로 번역. credential 자체는 local `CredentialRepository` (TASK-BE-063)
```

L174 새 표현:
```
**HTTP 컨트랙트 (내부)**: ... — tenant-info lookup (응답 array shape), 계정 상태 조회
```

---

# Edge Cases

- TASK-BE-063 inline note 가 auth-to-account.md L9 에 충분히 명시되어있어 별 deprecation header 불필요 (agent CRIT-3 은 false positive, fix 미적용).
- 본 file 외 다른 auth-service spec (overview.md, multi-tenancy feature spec 등) 에 "credential lookup" 잔재 grep — spot-check 필요. 발견 시 같은 PR 에서 align.
- LoginUseCase production code (LoginUseCase.java 등) 의 method 명 verify — `lookupCredential` vs `lookupTenantInfo`. 본 task 는 spec 정정만 — production code 영역은 별 task.

---

# Failure Scenarios

- architecture.md 새 표현이 contract decision (auth-to-account.md L24-40) 과 다시 어긋남 → audit fail. PR 시점 spot-check 필수.
- production code 가 method 이름 `lookupCredential` 유지하면 spec 과 어긋남 — 별 follow-up task 후보.

---

# Test Requirements

- HARDSTOP-03 hook PASS.
- CI self-CI PASS (markdown-only 가능 — Integration (GAP) job 영향에 따라 SKIP 가능).
- 본 file 의 linked-from cross-ref 정상 (auth-to-account.md ↔ architecture.md 양방향).
- Production code = 0.

---

# Definition of Done

### Impl PR

- [ ] AC 완료.
- [ ] task lifecycle ready → in-progress → review.

### Close chore PR

- [ ] review → done, INDEX 동기.

---

# Provenance

- `/refactor-spec all --dry-run` 2026-05-13~14 GAP audit critical #4 (spec lag — architecture.md "갱신 시 확정" 이지만 contract auth-to-account.md 가 이미 array shape 결정).
- Sibling 답습 패턴: TASK-BE-280 (PROJECT.md service_types sync, PR #449) + TASK-MONO-083 (platform jwt-standard-claims, PR #455) + TASK-SCM-BE-011 (SCM envelope align, PR #458) — 모두 same-day single-PR closure.
- 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (spec wording align, mechanical).
