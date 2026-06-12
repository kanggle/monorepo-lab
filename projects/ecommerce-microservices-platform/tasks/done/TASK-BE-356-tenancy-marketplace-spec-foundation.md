# Task ID

TASK-BE-356

# Title

ecommerce Multi-Tenancy & Marketplace **spec foundation** (ADR-MONO-030 **Step 1**, Source-of-Truth-first). Author the slice's tenancy+marketplace model spec — the two orthogonal axes (outer `tenant_id` reusing ADR-019 entitlement-trust; inner `seller_id` net-new) + the `account_type` plane (reused) — as the source of truth that Step 2 (outer-axis code) and Step 3 (inner-axis code) implement against. **Spec-only; no code, no migration, no `PROJECT.md` edit.**

# Status

done

> **DONE (2026-06-12)**: Step-1 spec foundation authored. NEW `specs/features/multi-tenancy-and-marketplace.md` (slice SoT: 0 요약 + 1 현재상태갭 + 2 바깥축 M1-M7 + entitlement-trust 진화 + 3 안쪽축 seller + 4 account_type 평면 + 5 계약영향 + 6 슬라이스검증 + 7 보류) + `product-service`/`order-service` architecture.md 「Multi-Tenancy & Marketplace」 섹션. Authored on the **corrected** ADR-030 basis (D4 = one IdP + account_type planes; gate = ecommerce 고정슬러그 → entitlement-trust; scm/erp = row-level `tenant_id` 참조). 분석=Opus 4.8 / 구현=Opus 직접. Next = Step 2 (outer-axis code + PROJECT.md trait) = TASK-BE-357.

# Owner

backend

# Task Tags

- spec
- multi-tenant
- marketplace
- tenancy
- ecommerce
- adr-030

---

# Dependency Markers

- **선행 (prerequisite)**: ADR-MONO-030 ACCEPTED (TASK-MONO-231) + 정정 (TASK-MONO-232) — D4 = account_type 평면, §1.1 = scm/erp 참조.
- **reuses**: ADR-MONO-019 D5 (entitlement-trust 게이트 진화) + ADR-MONO-025 (`org_scope` ABAC, 셀러-스코프 read 형태) + 기존 [iam-integration.md](../../specs/integration/iam-integration.md) (고정슬러그 게이트·`account_type` 평면).
- **blocks / 후속**: TASK-BE-357 (Step 2 = 바깥 축 코드: `tenant_id` 컬럼 + entitlement-trust 게이트 진화 + default-tenant 시드 + M6 IT + **PROJECT.md `multi-tenant` trait**) → TASK-BE-358 (Step 3 = 안쪽 축: `seller` 애그리거트 + `seller_id` 소유/귀속 + ABAC 셀러-스코프 read). 본 스펙 기준.

# Goal

product+order 슬라이스의 멀티테넌트·마켓플레이스 모델을 스펙으로 확정 — Step 2/3 코드가 재유도 없이 본 문서를 기준으로 구현하고, 두 축(격리/귀속)·평면(account_type)·degradation(default seed)·회귀(M6)·계약영향·보류범위가 명확하도록.

# Scope

## In Scope (spec only)

- NEW `specs/features/multi-tenancy-and-marketplace.md` — 슬라이스 SoT (두 축 + 평면 + M1-M7 채택 + entitlement-trust 진화 + 셀러 애그리거트/귀속 + ABAC 셀러-스코프 + 계약 영향 + 슬라이스 검증 + 보류).
- `specs/services/product-service/architecture.md` + `specs/services/order-service/architecture.md` — 「Multi-Tenancy & Marketplace (ADR-MONO-030)」 섹션 추가 (서비스별 적용분: product 소유권 `(tenant_id, seller_id)`; order **order-line 단위 `seller_id` 귀속**).
- 본 태스크 파일 + ecommerce `tasks/INDEX.md`.

## Out of Scope (Step 2/3/4)

- `tenant_id`/`seller_id` 컬럼·마이그레이션·시드, 게이트 코드(entitlement-trust 진화), 셀러 애그리거트 구현, M6 IT — **Step 2/3 코드**.
- `PROJECT.md` `multi-tenant` trait 추가 — **Step 2** (ADR-030 §D7 타이밍: 슬라이스 코드와 함께).
- `tenant_domain_subscription` `domain_key='ecommerce'` 시드 · 콘솔 통합 · 나머지 11 서비스 · 정산/수수료 · ADR-022 이행 이벤트 `tenant_id` 스레딩 — **Step 4**.

# Acceptance Criteria

- **AC-1** `specs/features/multi-tenancy-and-marketplace.md` 가 두 직교 축(바깥 `tenant_id`=ADR-019 D5 재사용/안쪽 `seller_id`=net-new)·평면(`account_type` 재사용)·복합신원(`tenant_id`×`account_type`×`seller_id`)을 확정.
- **AC-2** 현재상태 갭이 정확: 게이트는 고정슬러그(`tenant_id=='ecommerce'`) 존재→entitlement-trust 진화 필요; product/order `tenant_id` 부재; `account_type` 평면 이미 존재; scm/erp = row-level 참조.
- **AC-3** product/order architecture.md 에 테넌시 섹션 — product 소유권=`(tenant_id, seller_id)`, order=**order-line 단위 `seller_id` 귀속**(다중 셀러 주문 가능), 둘 다 M1-M7 + degradation(default seed) + M6 회귀.
- **AC-4** degradation(D8) 명시: default-tenant + default-seller 시드 → 단일 스토어/셀러 동작 byte-identical; standalone `tenant_id` 부재 → default tenant.
- **AC-5** 보류 범위(Step 4)가 명시 — 정산/콘솔/11서비스/ADR-022 스레딩/M7 quota/셀러 온보딩.
- **AC-6** Spec-only — 코드·마이그레이션·`PROJECT.md`·계약 필드 편집 없음(계약 영향은 §5 에 기술, 편집은 Step 2/3).

# Related Specs

- `specs/integration/iam-integration.md` (기존 IAM 통합 — 고정슬러그 게이트·`account_type` 평면·auth-service 제거 로드맵)
- `specs/services/product-service/architecture.md` · `specs/services/order-service/architecture.md`
- `rules/traits/multi-tenant.md` M1-M7

# Related Contracts

- 없음 (필드 단위 계약 편집은 Step 2/3; 본 태스크는 §5 에서 **영향 범위**만 기술)

# Edge Cases

- **order 다중 셀러**: 한 주문이 여러 셀러 상품을 포함 가능 → `seller_id` 는 order 헤더가 아니라 **order-line(항목)** 단위. order 헤더는 `tenant_id`.
- **소비자는 셀러 아님**: `CONSUMER` 평면은 `seller_id` 권위 없음 — `seller_id` 는 상품/항목 귀속 속성이지 소비자 신원이 아님.
- **net-zero**: default-tenant/default-seller 시드 + entitlement-trust dual-accept + ABAC 빈 스코프 무필터 → 기존 단일 스토어 동작 불변(슬라이스가 오늘 동작을 안 깸).
- **`tenant_id` = claim 파생**, 요청 본문 필드 아님 — API 표면에 노출 안 함(`X-Tenant-Id` 헤더 컨텍스트).

# Failure Scenarios

- 스펙이 `seller_id` 를 order 헤더에 두면 다중 셀러 주문(공유 카탈로그)을 표현 불가 → §3.2/order 섹션에서 order-line 단위로 고정.
- 스펙이 default seed/dual-accept 를 누락하면 Step 2 가 기존 단일 스토어 동작을 깰 위험 → §2.5/§3.3 에서 net-zero degradation 고정.
- 스펙이 `PROJECT.md` trait 를 Step 1 에 넣으면 미마이그 서비스 M1 미스분류(ADR §D7) → out-of-scope 로 Step 2 에 배치.
- 스펙이 ADR-022 이행 이벤트 `tenant_id` 스레딩을 슬라이스에 포함하면 cross-project 로 비대 → §2.3/order 섹션에서 Step 4 보류로 고정.

# Notes

- 분석=Opus 4.8 / 구현 권장=Opus (테넌시·격리·마켓플레이스 모델 설계). spec-only.
- Authored on the **corrected** ADR-030 (TASK-MONO-232): D4 = one IdP + `account_type` 평면 (별도 auth-service 아님), 게이트 = 고정슬러그→entitlement-trust 진화.
