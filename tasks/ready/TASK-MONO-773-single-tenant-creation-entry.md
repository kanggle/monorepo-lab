# Task ID

TASK-MONO-773

# Title

`ADR-MONO-080` D9 = **T1** — «조직 만들기»(`/onboarding`) · «테넌트 등록»(`/tenants`)을 **«테넌트 생성» 하나**로: 콘솔 셸 안 · 첫 관리자는 들어온 계정이 정한다 · 두 번째 회사

# Status

ready

# Owner

monorepo

# Task Tags

- iam
- platform-console
- security

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (권한 원점 · 셸 가드 상태 · 새 테넌트 상태)

---

# Dependency Markers

- **선행**: `TASK-MONO-772` `done/` — `SUPER_ADMIN` 이 남을 첫 관리자로 지정하는 길은 D6 초대가 있어야 하고, 운영자 아닌 풀 계정의 콘솔 세션도 772 의 토큰 경로 위에 선다.

# Goal

같은 행위(테넌트 생성)가 두 화면으로 갈라져 결과가 다르다 — 온보딩은 «테넌트 + 관리자», `/tenants` 는 «테넌트만»(`console-web/src/app/api/tenants/_proxy.ts:22-30`). `/tenants` 로 만든 B2B 테넌트에는 `SUPER_ADMIN` 도 운영자를 못 앉힌다(면제는 대상 `'*'` 만, `CreateOperatorUseCase.java:100-107`). 하나로 합친다:

| 들어온 계정 | 셸 | 첫 관리자 |
|---|---|---|
| 운영자 아님 | 콘솔 셸 — 사이드바 «가이드 · 테넌트 생성» 만, 운영자 토큰 없음 | **본인** |
| 이미 운영자(다른 회사) | 지금 셸 | **본인** — 두 번째 회사 |
| `SUPER_ADMIN` | 지금 셸 | **지정한 이메일에 D6 초대** — 수락까지 테넌트 = «관리자 대기» |

# Scope

## In Scope

- 계약 먼저: `onboarding-api.md` 와 `admin-api.md` § tenants 를 «테넌트 + 첫 관리자» 하나의 원자 묶음으로(입구 둘의 인증 방식 — 비운영자 OIDC 토큰 / 운영자 토큰) · «관리자 대기» 상태와 그 만료·재초대·정리
- admin-service: 생성 원자 묶음 일반화(ADR-044 D1 · D2 — 셀프 경로의 권한은 **새 테넌트에만**) · `SUPER_ADMIN` 경로 = 테넌트 + 첫 관리자 초대(772 재사용) · 044 D4 트러스트 게이트(인증된 이메일 · 속도 제한 · 신원당 소유 상한)를 **모든 비-`SUPER_ADMIN` 경로**에
- console-web: `(console)` 셸 가드에 «로그인 · 운영자 아님» 상태 — 그 상태에서 레지스트리 등 운영자 토큰 호출을 건너뛴다(401 재로그인 고리 방지; 샘플 방문자 분기 `(console)/layout.tsx:148-160` 이 선례) · «테넌트 생성» 화면 하나 · 사이드바 진입점 · 빈 상태 안내(«회사에 관리자가 있다면 초대를 요청하세요» 포함) · 생성 직후 운영자 토큰 재교환
- `/onboarding` 은 새 화면으로 넘겨주는 주소로 남긴다(콜백 · 리프레시가 지금 그리로 보낸다 — `callback/route.ts:191` · `refresh/route.ts:205`)
- B2C 테넌트 생성은 `SUPER_ADMIN` 전용 유지, 첫 관리자는 같은 초대 규칙

## Out of Scope

- 조직 계층 노드에 테넌트 넣기(`ADR-MONO-047` 개정 — 별도)
- «한 신원 여러 조직» 의 조직 전환 화면 개선(지금 상단 스위처로 충분한지 AC-0 에서 확인만)

# Acceptance Criteria

- [ ] **AC-0** — 착수 시 재측정: 두 입구의 file:line · 온보딩 · `/tenants` 계약 · 셸 가드 · 콜백/리프레시의 `/onboarding` 보냄. «관리자 대기» 상태의 집(account-service `tenants.status` 확장 또는 별도 표)을 정하고 이유를 적는다.
- [ ] **AC-1** — 운영자 아닌 계정: 셸이 열리고 사이드바는 «가이드 · 테넌트 생성» 뿐 · 🔴 **운영자 토큰 없음** · 관리 API 는 거절(대조군).
- [ ] **AC-2** — 운영자 아닌 계정이 생성 → 테넌트 + 본인 `TENANT_ADMIN` · `TENANT_BILLING_ADMIN` + 배정 → 재교환 뒤 개요로.
- [ ] **AC-3** — 이미 운영자인 계정이 두 번째 회사를 생성 → 새 테넌트에만 관리자 · 🔴 기존 테넌트 권한 불변 · 신원당 상한을 넘으면 거절.
- [ ] **AC-4** — `SUPER_ADMIN` 이 생성 + 첫 관리자 이메일 → 테넌트 «관리자 대기» + 초대 · 수락 뒤 활성 · 만료 시 정리 규칙대로. 🔴 관리자 없는 활성 테넌트가 생기지 않는다(ADR-044 D3).
- [ ] **AC-5** — `/onboarding` 직접 접근 · 콜백의 «운영자 아님» 분기 → 새 화면으로.
- [ ] **AC-6** — 콘솔 e2e 디렉터리 grep(`/onboarding`, `onboarding-` testid) 후 수정 · 머지 뒤 첫 `nightly-e2e.yml` 콘솔 잡 확인.

# Related Specs

- `docs/adr/ADR-MONO-080-workforce-on-the-consumer-pool.md` D9 · D6 · § 새로 생기는 위험
- `docs/adr/ADR-MONO-044-self-service-tenant-onboarding.md` D1~D4 · D7
- `projects/platform-console/specs/services/console-web/architecture.md` (셸 · 인증 흐름)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/onboarding-api.md` · `admin-api.md` § tenants · `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.6

# Edge Cases

- 초대받은 첫 관리자가 수락하지 않고 만료 — 테넌트를 정리할지 «관리자 대기» 로 둘지(AC-0 결정).
- 운영자 아닌 계정이 직원으로 초대받은 상태(772) — 빈 상태 화면이 «받은 초대» 를 먼저 보여 준다.

# Failure Scenarios

1. 셸 가드에 상태를 더하고 레지스트리 호출을 안 건너뛴다 — 운영자 아닌 계정이 401 재로그인 고리에 빠진다.
2. 셀프 경로의 권한 행이 기존 테넌트를 가리킬 수 있다 — ADR-044 D2 위반(권한 상승).
