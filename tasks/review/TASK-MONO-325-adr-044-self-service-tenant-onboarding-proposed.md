# Task ID

TASK-MONO-325

# Title

ADR-MONO-044 PROPOSED — self-service B2B tenant onboarding (가입자가 새 테넌트 생성 + 첫 TENANT_ADMIN 자동 임명; AWS 계정 생성 / GCP 프로젝트 생성 패리티)

# Status

review

# Owner

architecture

# Task Tags

- docs
- adr

---

# Goal

셀프서비스 **B2B 테넌트 온보딩**의 아키텍처 결정을 **PROPOSED ADR-MONO-044** 로 기록한다 (ADR-019/020/023/024/030/042 staged-child 패턴).

**해결하려는 갭**: [ADR-MONO-024](../../docs/adr/ADR-MONO-024-tenant-admin-delegation.md)가 이미 `TENANT_ADMIN`(테넌트-스코프 `operator.manage`, GCP project-IAM-admin 패리티) + `TENANT_BILLING_ADMIN`(`subscription.manage`) + in-tenant sub-delegation을 확립해, "테넌트 자기 관리자가 자기 운영자·구독을 관리"하는 다운스트림은 존재한다. 그러나 ADR-024는 **첫 `TENANT_ADMIN`을 "플랫폼 `SUPER_ADMIN`이 부여"** 한다고 명시 — 즉 신규 방문자가 **새 테넌트를 만들고 그 첫 `TENANT_ADMIN`이 되는 셀프서비스 부트스트랩이 없다**. 오늘날 테넌트 생성·첫 운영자 임명은 전부 operator-token(`token_type=admin`) 게이트(admin-api 전 엔드포인트) = 플랫폼 운영자만 가능.

이 ADR은 그 "AWS 계정 생성 / GCP 프로젝트 생성" 순간 — **인증된 사용자가 새 테넌트를 생성하고 오직 그 새 테넌트에 한해 첫 `TENANT_ADMIN`으로 자기-승격**하는 셀프서비스 front door + first-admin 부트스트랩 — 을 결정한다. 다운스트림(운영자 관리·구독·sub-delegation)은 ADR-024 재사용.

**이 태스크는 문서 전용 PROPOSED 기록.** ACCEPTED 전환 + 구현은 별도 user-explicit-intent-gated 태스크다. **Self-ACCEPT 금지.**

---

# Scope

## In scope
- `docs/adr/ADR-MONO-044-self-service-tenant-onboarding.md` PROPOSED 작성 — 결정 D1..D7 + CHOSEN/rejected + 보안 불변식(no-escalation·tenant confinement·ADR-024 D2/D3·BE-467/468) + zero-regression 로드맵 + 선행 ADR 관계.

## Out of scope
- 어떤 코드·스펙·계약·시드 변경도 없음 (PROPOSED = 결정 기록만).
- ACCEPTED 전환 (별도 태스크, user-gated).
- 구현(엔드포인트/오케스트레이션/UI).
- `SUPER_ADMIN`·기존 confinement·ADR-024 role 정의 변경 (net-zero 유지).

---

# Acceptance Criteria

- [ ] **AC-1**: `docs/adr/ADR-MONO-044-...md` 생성, `Status: PROPOSED`, HARDSTOP-09 PAUSED 배너 포함.
- [ ] **AC-2**: 결정 D1..D7 각각 CHOSEN(PROPOSED) + rejected 대안 표로 기록.
- [ ] **AC-3**: 보안 불변식 명시 — 셀프-승격은 **새로 생성된 테넌트에만** 국한, 기존 테넌트 무접근, `SUPER_ADMIN` net-zero, no-escalation(ADR-024 D2/D3)·tenant confinement(BE-467/468) 미약화.
- [ ] **AC-4**: 선행 ADR 관계 명시 — 024(첫 admin 부트스트랩 = 이 ADR / 다운스트림 = 024 재사용), 019/020(테넌트·assignment), 023(plane), 032/036(통합·born-unified 신원), 030/042(마켓·셀러 온보딩 선례).
- [ ] **AC-5**: `§ Status Transition History`에 PROPOSED 행만(ACCEPTED 행 없음). Self-ACCEPT 금지 문구.
- [ ] **AC-6**: doc lint/링크 정합 — 참조하는 ADR/스펙 경로 존재 확인.

---

# Related Specs / Contracts

- [ADR-MONO-024](../../docs/adr/ADR-MONO-024-tenant-admin-delegation.md) (TENANT_ADMIN/BILLING_ADMIN·confinement — 다운스트림 재사용), [ADR-MONO-019](../../docs/adr/ADR-MONO-019-platform-console-customer-tenant-model.md), [ADR-MONO-020](../../docs/adr/ADR-MONO-020-operator-multitenant-assignment.md), [ADR-MONO-023](../../docs/adr/ADR-MONO-023-entitlement-iam-plane-separation.md), [ADR-MONO-032](../../docs/adr/ADR-MONO-032-unified-identity-roles-model.md), [ADR-MONO-036](../../docs/adr/ADR-MONO-036-born-unified-identity-provisioning.md), [ADR-MONO-042](../../docs/adr/ADR-MONO-042-ecommerce-seller-onboarding-iam-provisioning.md)
- 기존 프리미티브: 테넌트 온보딩 API 계약(TASK-BE-256), tenant management(BE-250), account provisioning(BE-231), born-unified mint(BE-381/402)

# Edge Cases / Failure Scenarios

- 절반-프로비저닝(테넌트 생성됐으나 첫 admin 임명 실패) → orphan 테넌트 방지(D3 원자성/보상). 
- 남용(테넌트 스팸) → D4 신뢰 경계(이메일 인증/rate limit/pending 가드).
- 기존 이메일이 이미 소비자 → born-unified `resolveOrCreate`로 동일 중앙 신원 공유(D5), 새 operator 롤만 추가.
- self-ACCEPT 시도 = 위반. ACCEPTED는 사용자 명시 intent gate.
