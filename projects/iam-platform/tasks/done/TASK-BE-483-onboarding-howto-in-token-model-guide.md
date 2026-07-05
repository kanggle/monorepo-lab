# Task ID

TASK-BE-483

# Title

operator-auth-token-model.md § 6 에 "② 만드는 법 — self-service 온보딩" 하위 절 추가 — 모자 ②(내가 운영하는 회사)를 어떻게 얻는지(회원가입→로그인→/onboarding 조직 생성→구독/운영) ADR-MONO-044 기반 how-to (human-reference, 기존 가이드 확장)

# Status

done

# Owner

backend (docs)

# Task Tags

- docs
- guide
- iam

---

# Dependency Markers

- **builds on**: TASK-BE-482(§ 6 "하나의 계정, 4개의 모자" — 4유형 개념). 본 절은 그 중 **모자 ②(내가 운영하는 회사)를 실제로 만드는 how-to**를 § 6 하위에 추가.
- **grounded in**: ADR-MONO-044(Self-Service B2B Tenant Onboarding, ACCEPTED) — D1 atomic 트랜잭션 / D2 new-tenant 한정 confinement / D3 fail-closed saga(orphan 방지) / D5 born-unified 운영자 facet / D6 `TENANT_ADMIN`+`TENANT_BILLING_ADMIN`·entitlement-empty. 실 구현: admin-service `SelfServiceOnboardingUseCase`/`FirstAdminProvisioner`, `POST /api/admin/onboarding/organizations`, 콘솔 `/onboarding` `CreateOrganizationForm`.
- **note (동기)**: § 6 은 모자 ②를 **개념**으로만 설명하고 "어떻게 얻나"가 없다. 세션 중 사용자 질문("회원가입하고 내가 소유한 회사를 운영하기 위한 콘솔 접근 계정 생성은 어떻게?")에 대한 정답을 가이드에 명문화.

# Goal

`operator-auth-token-model.md` § 6(4개의 모자) 하위에 **"### ② 만드는 법 — self-service 온보딩"** 절을 추가한다. AWS "회원가입→새 계정→root" / GCP "새 프로젝트→owner" parity 로, 플랫폼 관리자 개입 없이 스스로 모자 ②가 되는 단계:

1. 회원가입(`/signup`) → 통합 IAM 계정(모자 ①). email 인증=온보딩 진입 게이트(D4).
2. 콘솔 로그인 → 아직 운영자 아님(1축만); 워크스페이스 없으면 콜백이 `/onboarding` 으로.
3. `/onboarding` 조직 생성 → `POST /api/admin/onboarding/organizations`(내 OIDC 토큰=subject). atomic 트랜잭션(D1)이 {새 테넌트 + 운영자 facet(born-unified) + `TENANT_ADMIN`+`TENANT_BILLING_ADMIN` (새 테넌트 scope로만, D2) + `operator_tenant_assignment`} all-or-nothing(실패→롤백, orphan 방지 D3). → ①이 ②로 승격.
4. 콘솔 운영: `/subscriptions`(도메인 켜기 — 테넌트는 구독 0으로 태어남 D6)·`/operators`(직원 운영자 생성=모자 ③). 도메인 켠 뒤 assume-tenant(2축)로 도메인 운영.

핵심: self-grant 는 **자기가 만든 테넌트에만** 갇힘(D2, SUPER_ADMIN net-zero 불변). SoT=ADR-MONO-044.

# Scope

## In Scope

- `projects/iam-platform/docs/guides/operator-auth-token-model.md` § 6 하위에 `### ② 만드는 법 — self-service 온보딩` 절 추가(공통 blockquote 뒤, `---` 앞). 4단계 + confinement/entitlement-empty 핵심 + ADR-MONO-044 링크.

## Out of Scope

- 코드·스펙·계약·시드·테스트 변경 0 (순수 문서 확장).
- § 6 4-모자 표/구분 문단·다른 섹션(§ 1~5, § 7~9) 변경(추가만).
- 콘솔 `/onboarding` 화면 힌트 — 별건 platform-console task(PC-FE).
- ADR-044 세부(saga 보상·rate-limit 등) 재서술 — 링크만.

# Acceptance Criteria

- [ ] **AC-1** § 6 하위에 `### ② 만드는 법 — self-service 온보딩` 절 추가, 4단계(회원가입→로그인→/onboarding 생성→구독/운영) 포함.
- [ ] **AC-2** atomic 트랜잭션 내용(새 테넌트 + born-unified 운영자 facet + `TENANT_ADMIN`+`TENANT_BILLING_ADMIN` 새-테넌트-scope + assignment)과 confinement(D2 자기 테넌트만)·entitlement-empty(D6 구독 0으로 태어남) 명시.
- [ ] **AC-3** ADR-MONO-044 링크(실재 확인) + 모자 ①→②→③ 흐름 연결.
- [ ] **AC-4** 섹션 번호(§ 6~9)·기존 § 참조 무손상, 코드/스펙/계약 변경 0. CI markdown fast-lane.

# Related Specs

- `docs/adr/ADR-MONO-044-self-service-tenant-onboarding.md` (SoT — D1~D7).
- `projects/iam-platform/specs/services/admin-service/rbac.md` (`TENANT_ADMIN`/`TENANT_BILLING_ADMIN` role 모델).
- `projects/iam-platform/specs/contracts/http/onboarding-api.md` (`POST /api/admin/onboarding/organizations` 계약 — 링크만).

# Related Contracts

- 변경 없음(개념 가이드 확장).

# Edge Cases

- 링크 경로 정확: ADR-MONO-044 는 repo `docs/adr/`(가이드 기준 상대경로 `../../../../docs/adr/...`, § 7/§ 9 기존 ADR 링크와 동일 깊이) — AC-3 확인.
- how-to 가 ADR-044 세부와 어긋나지 않게: 단계는 개념 수준, 수치·보상 로직은 ADR 링크 위임.

# Failure Scenarios

- 스펙-only 문서라 런타임 실패 없음. CI = markdown lane. 링크 깨짐만 주의(실재 확인).
- 온보딩 단계가 실제 구현(SelfServiceOnboardingUseCase/onboarding-api.md)과 어긋날 위험 → ADR-044 D1/D2/D6 에 맞춰 서술(복제 아님, 링크).

# Definition of Done

- [ ] `### ② 만드는 법 — self-service 온보딩` 절 추가(4단계 + confinement/entitlement-empty + ADR-044 링크)
- [ ] 섹션 번호·기존 § 참조 무손상, 코드/스펙/계약 0
- [ ] Acceptance Criteria 충족
- [ ] Ready for review
