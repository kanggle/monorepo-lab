# Task ID

TASK-BE-474

# Title

셀프서비스 B2B 테넌트 온보딩 — admin-service 오케스트레이션 (ADR-MONO-044 §3.4 실행 슬라이스)

# Status

done

# Owner

backend

# Task Tags

- code
- api
- security

---

# Goal

[ADR-MONO-044](../../../../docs/adr/ADR-MONO-044-self-service-tenant-onboarding.md) (ACCEPTED) §3.4 실행 로드맵의 백엔드 수직 슬라이스를 구현한다.

**인증된 사용자(일반 IAM user JWT, operator 토큰 아님)** 가 호출하는 **공개(operator-gate 아님) 셀프서비스 온보딩 엔드포인트**를 admin-service에 추가한다. 한 번의 원자적 호출로:
1. 새 `tenants` row 생성 (status ACTIVE)
2. 호출자의 중앙 신원 + backing operator 계정 resolve-or-create (born-unified, ADR-036 재사용)
3. 그 operator에게 **`TENANT_ADMIN` + `TENANT_BILLING_ADMIN`** 롤을 **새 테넌트에 스코프**하여 부여 (D6)
4. 새 테넌트에 대한 `operator_tenant_assignment` 생성

완료 후 참: 신규 방문자가 새 워크스페이스(테넌트)를 스스로 만들고 그 첫 운영자(TENANT_ADMIN+BILLING_ADMIN)가 되어, 그 테넌트 스코프로 콘솔에 로그인할 수 있다 — 플랫폼 SUPER_ADMIN 없이.

---

# Scope

## In scope (ADR-044 D1/D2/D3/D5/D7)
- admin-service 공개(인증됨, operator-gate 아님) 엔드포인트 1개 — 호출자 identity JWT로 principal 식별.
- 오케스트레이션 use-case: tenant 생성 → born-unified operator resolve/create → `TENANT_ADMIN`+`TENANT_BILLING_ADMIN` 그랜트(새 tenant_id 스코프) → self-assignment.
- **D2 confinement (핵심)**: self-grant는 **방금 생성한 tenant_id에만** 기록 — `'*'`·기존 테넌트 절대 불가. 구조적으로 불가능하게 구현.
- **D3 fail-closed + 보상**: 어느 단계 실패 시 생성된 tenant 롤백/보상(orphan admin-less 테넌트 금지).
- 계약: `specs/contracts/http/` 온보딩 엔드포인트 문서 (source-of-truth-first).
- 테스트: **cross-tenant-origination-leak IT**(self-grant가 새 테넌트만 겨냥, `'*'`/기존 불가 증명) + 성공 경로 IT + 보상 IT + 단위 테스트.

## Out of scope (ADR-044 deferred)
- D4 trust-gate의 이메일 인증 강제 — 슬라이스는 인증+rate-limit만(이메일 인증 필수화는 후속). approval-queue(D4-C) 후속.
- UI("조직 생성" 페이지) — 후속 태스크(§3.4 step 3).
- 도메인 구독 auto-subscribe(D6-B), org 프로필 관리, billing, multi-org switch.
- SUPER_ADMIN·ADR-024 role 정의·기존 confinement 변경(net-zero 유지).

---

# Acceptance Criteria

- [ ] **AC-1**: 공개(인증됨, operator-gate 아님) 엔드포인트가 유효 user JWT + org명으로 새 tenant + operator + 롤 + assignment를 원자적으로 생성, 201 반환.
- [ ] **AC-2 (D2, 보안 핵심)**: self-grant된 `admin_operator_roles` row의 `tenant_id` = 방금 생성한 tenant. `'*'`·기존 tenant 절대 불가. **cross-tenant-origination-leak IT**로 증명.
- [ ] **AC-3 (D3)**: 중간 단계 실패 시 tenant 롤백/보상 → orphan admin-less 테넌트 없음. 보상 IT.
- [ ] **AC-4 (D5)**: 기존 소비자 이메일로 온보딩 시 동일 중앙 신원에 operator facet만 추가(중복 신원 생성 안 함, born-unified `resolveOrCreate` 재사용).
- [ ] **AC-5 (D6)**: 첫 admin이 `TENANT_ADMIN` + `TENANT_BILLING_ADMIN` 둘 다 보유(새 tenant 스코프). 단, 새 tenant는 `tenant_domain_subscription` 0으로 출생(auto-subscribe 없음).
- [ ] **AC-6**: SUPER_ADMIN·기존 operator/tenant 데이터 net-zero. `./gradlew :projects:iam-platform:apps:admin-service:test` GREEN (Docker-free `:check` 통과 + Testcontainers IT는 CI Linux 권위).
- [ ] **AC-7**: 계약 문서(`specs/contracts/http/`) + rbac/onboarding 스펙 반영. 라이브 검증(fed-e2e): 온보딩 → 새 tenant 스코프 콘솔 로그인.

---

# Related Specs / Contracts

- [ADR-MONO-044](../../../../docs/adr/ADR-MONO-044-self-service-tenant-onboarding.md) (ACCEPTED — D1..D7), [ADR-MONO-024](../../../../docs/adr/ADR-MONO-024-tenant-admin-delegation.md) (TENANT_ADMIN/BILLING_ADMIN·confinement 재사용), ADR-019/020/023/032/036/042
- 기존 프리미티브: admin-service tenant-management(BE-250), CreateOperatorUseCase, born-unified resolveOrCreate(BE-402), operator_tenant_assignment(ADR-020/024), rbac.md

# Edge Cases / Failure Scenarios

- 절반-프로비저닝(tenant 생성됐으나 이후 실패) → D3 보상 롤백.
- 기존 소비자 이메일 → born-unified 동일 신원 공유(D5), operator 롤만 추가.
- self-grant가 `'*'`/기존 tenant 겨냥 시도(공격) → 구조적 불가 + IT로 회귀 방어(AC-2).
- account-service/tenant 생성 실패 → fail-closed 에러, orphan 없음.
- 인증 없는 호출 → 401(공개지만 인증은 필수 — principal 필요).
