# Task ID

TASK-MONO-334

# Title

운영자 등록 시 이메일이 **해당 테넌트에 가입된 계정**일 때만 등록 허용 (프런트 pre-gate + 백엔드 422 거부). ADR-MONO-035 O6 advisory → hard precondition 반전.

# Status

ready

# Owner

monorepo

# Task Tags

- iam
- platform-console
- adr
- security

---

# Goal

콘솔 "운영자 등록" 폼은 지금까지 이메일이 가입 계정이 아니어도(심지어 break-glass 비밀번호 없이도) 운영자를 생성할 수 있었다. `TASK-PC-FE-179`의 계정-존재 사전 확인은 **경고만** 하고 등록을 막지 않는 **fail-soft advisory**(ADR-MONO-035 O6)였고, 백엔드 `CreateOperatorUseCase`는 계정 존재를 검사하지 않았다.

사용자 지시(2026-07-07): **가입된 이메일에만 운영자를 등록**하도록, **프런트 제출 차단 + 백엔드 거부** 양쪽에서 강제하고, break-glass 비밀번호로 계정-없는 운영자를 만드는 경로를 **완전히 금지**한다.

이는 dangling-operator escape hatch를 닫아 ADR-032/034/036 통합 identity 방향(모든 운영자가 실제 Global Account에 매핑)으로 조인다.

# Scope

**cross-project 원자적 변경 (1 PR)**:

**iam-platform / admin-service**
- `OperatorAccountNotFoundException` 신규 → `AdminExceptionHandler`에서 **422 `OPERATOR_ACCOUNT_NOT_FOUND`** (기존 IDENTITY_LINK 422 family 미러).
- `CreateOperatorUseCase.createOperator`: 이메일-충돌 체크 직후, 비-`*` 테넌트에 대해 기존 `AccountServiceClient.search(tenantId, normalizedEmail)`로 계정 존재 확인 → `totalElements == 0`이면 throw. 계정 검사는 role 해석·INSERT·audit **이전**에 수행(fail-fast).
- 단위 테스트(`CreateOperatorUseCaseTest`): 계정 존재 기본 stub + 4개 신규(absent→422, break-glass여도 absent면 차단, `*` 면제, downstream 실패→fail-closed 전파). 컨트롤러 테스트(422 매핑) + 통합 테스트(`AccountServiceClient` @MockitoBean + 422 e2e).

**platform-console / console-web**
- `useCreateOperatorForm`: probe 결과가 `canSubmit`을 **게이트**(비-`*`는 `acctState==='exists'` 필수; `*`는 면제). advisory 3-boolean을 차단형(`showBlockingAbsent`/`showUnavailable`/`showChecking`/`showExistsOk`)으로 재작성.
- `CreateOperatorAccountAdvisory`: absent=차단 에러, unavailable=차단(확인 불가), break-glass note 제거.
- `CreateOperatorForm.test.tsx`: pre-gate 차단 시맨틱으로 테스트 재작성.

**shared docs**
- `admin-api.md § POST /api/admin/operators`: 선행 조건 note + 422/503 error 행.
- `console-integration-contract.md § 2.4.3`: create pre-gate consumer obligation.
- `ADR-MONO-035 § 8 Amendment`: O6 advisory→hard precondition 반전 기록(O6 break-glass 불변식은 유지, 생성 선행조건만 강화).

# Acceptance Criteria

- AC-1 (백엔드): 비-`*` 테넌트에서 가입 계정 없는 이메일로 `POST /api/admin/operators` → **422 `OPERATOR_ACCOUNT_NOT_FOUND`**, 운영자·audit 미생성.
- AC-2 (백엔드): break-glass 비밀번호가 있어도 계정 없으면 422 (계정-없는 운영자 완전 금지).
- AC-3 (백엔드): `tenantId='*'`는 계정 검사 면제(SUPER_ADMIN 부트스트랩) — `search` 미호출, 생성 정상.
- AC-4 (백엔드): account-service 불가용 시 **fail-closed** — `DownstreamFailureException` 전파(503), 미생성.
- AC-5 (프런트): 계정 존재(`exists`)일 때만 "운영자 등록" 버튼 활성. absent/unavailable/checking은 비활성 + 상응 안내. `*`는 면제(활성).
- AC-6: `FirstAdminProvisioner` self-service 온보딩 경로는 `CreateOperatorUseCase` 미경유 → 무영향(회귀 없음).
- AC-7: admin-service 단위 테스트 GREEN, console-web operators 테스트/lint/tsc GREEN. 통합 테스트는 CI(Linux) 권위.

# Related Specs

- `docs/adr/ADR-MONO-035-operator-auth-unification-model.md` (§ 8 Amendment — 이 task가 추가)
- `docs/adr/ADR-MONO-032` / `ADR-MONO-034` / `ADR-MONO-036` (통합 identity 방향)
- `projects/iam-platform/specs/services/admin-service/security.md` (Operator Credential Convergence)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md § POST /api/admin/operators`
- `projects/iam-platform/specs/contracts/http/internal/admin-to-account.md` (`GET /internal/accounts`)
- `projects/platform-console/specs/contracts/console-integration-contract.md § 2.4.3`

# Edge Cases

- `tenantId='*'`: account_db에 `*` tenant 행 없음 → 검사 대상 없음 → **면제**(프런트·백엔드 동일).
- 동일 이메일이 다른 테넌트에 계정 보유: 대상 테넌트에 없으면 422 (테넌트별 확인).
- probe 'checking'(디바운스 중): 프런트 제출 비활성(fail-closed) — 아직 exists 아님.
- 이메일 정규화: 백엔드는 `trim().toLowerCase()`한 이메일로 검색(충돌 체크·identity와 동일 키).
- OIDC-only(비밀번호 없음) 운영자: 계정만 존재하면 정상 생성(변화 없음) — 계정의 통합 IAM credential이 PRIMARY 로그인.

# Failure Scenarios

- account-service 다운/타임아웃/서킷오픈 → `DownstreamFailureException`/`CallNotPermittedException` → 503, 운영자 미생성(fail-closed). 프런트는 'unavailable' 차단 안내.
- account-service가 부분 매칭을 반환(예: 접두 매칭)해도 `totalElements>0`를 존재로 판정 — 프런트 advisory와 동일 시맨틱(정확 매칭 보강은 account-service 계약 범위).
- `*` 부트스트랩에서 실수로 검사가 도는 회귀 → 부트스트랩 폭사. 테스트 AC-3가 `search` 미호출을 verify로 가드.

---

> 구현은 이 브랜치(`mono-334-operator-create-require-account`)에 task와 함께 동봉(1 PR). 머지 후 close-chore로 `ready/ → done/`.
>
> 분석=Opus 4.8 / 구현 권장=Opus (auth/privilege-escalation surface + cross-project 원자 변경).
