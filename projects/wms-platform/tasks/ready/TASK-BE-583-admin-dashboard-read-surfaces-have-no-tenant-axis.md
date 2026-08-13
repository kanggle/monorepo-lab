# Task ID

TASK-BE-583

# Title

`admin-service` 대시보드 읽기 표면 6개에 **테넌트 축이 없다** — 원시 API 의 격리를 형제가 우회한다

# Status

ready

# Owner

wms-platform

# Task Tags

- security
- read-model

---

# 배경 — `ADR-MONO-064` § M5 가 발굴, § D4 가 여기로 분리

`TASK-BE-581` 을 재던 중, `TASK-MONO-304` 가 세운 테넌트 격리가 **정확히 한 표면**
(outbound 원시 API)에만 있고 **같은 데이터를 내주는 형제 표면에는 전혀 없다**는 것이
실측됐다. ADR 은 이 사실을 B 를 고르는 **근거**로 썼고(격리의 비용을 전부 치르면서
편익이 0 이었다), 고치는 것은 **§ D4 로 이 티켓에 분리**했다.

## 실측 (2026-08-13, 손대지 않은 데모 볼륨)

```java
// admin-service — OrderDashboardController
@RequestMapping("/api/v1/admin/dashboard/orders")
@PreAuthorize("hasRole('WMS_VIEWER')")     // ← 이게 전부. CallerScope 도 테넌트 필터도 없음
```

```
admin_db  information_schema 전수  22개 테이블  ⇒  tenant 컬럼 0개 (구조적으로 테넌트를 모른다)

같은 운영자 토큰(tenant_id=demo-corp):
  GET /api/v1/admin/dashboard/orders   →  totalElements = 1     ← 보인다
  GET /api/v1/outbound/orders          →  totalElements = 0     ← 안 보인다 (BE-581)
```

⇒ 관문 있는 표면은 굶고, **관문 없는 형제가 같은 행을 무필터로 내준다.**

🔵 erp 가 같은 모양을 먼저 겪었다 —
[`projects/erp-platform/docs/adr/ADR-001`](../../../erp-platform/docs/adr/ADR-001-erp-event-plane-tenant-axis.md) § 3:
*"불변식은 관문 없는 형제 5개가 16행으로 매일 깨고 있었고, 관문이 있는 하나만 굶었다."*

## 🔴 이 티켓은 "격리를 넣는다" 가 아니다 — **먼저 무엇이 맞는지 정해야 한다**

두 진술이 저장소 안에서 충돌한다:

| 출처 | 진술 |
|---|---|
| `PROJECT.md` § Out of Scope | **multi-tenant 는 의도적 제외** — *"단일 물류 센터 가정. 멀티 테넌트 확장 시 trait 재분류"* |
| `TASK-MONO-304` / `ADR-MONO-022` § D9 | outbound 주문은 테넌트로 격리된다 |

`ADR-MONO-064` 는 outbound 축에 대해서만 후자를 택했다. admin 프로젝션이 **의도적으로
전역 뷰**(wms 네이티브 운영자용)라면 그것을 명문화하는 것이 답이고, 아니라면 스키마
마이그레이션이 필요하다. **어느 쪽인지가 AC-1 이다.**

---

# Goal

`admin-service` 의 읽기 표면이 테넌트에 대해 **무엇을 약속하는지**가 결정되고, 그 약속이
코드와 계약 문서에서 일치한다. 전역 뷰라면 그렇게 명문화되고, 아니라면 격리가 들어간다.

---

# Scope

## In Scope

- **AC-0 실측**: dashboard 컨트롤러 전수(6개로 추정 — Order / Shipment / Throughput /
  Asn / Inventory / Alert)와 각각의 인가 술어. `admin_db` 테넌트 컬럼 재확인.
  **어떤 신원이 `WMS_VIEWER` 를 얻는지**도 함께(`OperatorRoleDerivation`) — 지금 실제로
  누가 이 표면에 닿는가가 위험의 크기다
- **AC-1 결정**: (A) 전역 뷰로 명문화 / (B) 테넌트 축 신설(스키마 + 프로젝션 + 필터).
  B 는 `ADR-MONO-064` 를 확장하는 결정이므로 **ADR 선행**
- 결정에 따른 구현 + 계약 문서(`specs/contracts/http/admin-service-api.md`) 갱신

## Out of Scope

- outbound 원시 API — `ADR-MONO-064` 가 이미 닫았다
- `admin-service` 의 쓰기 표면(User / Role / Setting) — 읽기 축만 본다

---

# Acceptance Criteria

- [ ] **AC-0 (실측)** — dashboard 컨트롤러 전수 + 인가 술어 표 · `admin_db` 테넌트 컬럼
      재확인 · `WMS_VIEWER` 를 얻는 신원 전수. 🔴 0건이면 "0건" 이라 적고 **대조군**을 남긴다
- [ ] **AC-1 (결정)** — A/B 중 하나 + 근거. **B 면 ADR ACCEPTED 선행**
- [ ] **AC-2 (구현)** — 결정대로. A 면 컨트롤러 javadoc + 계약 문서에 전역 뷰임을 명문화
      (주석만이 아니라 **계약**에), B 면 마이그레이션 + 프로젝션 + 필터 + 회귀 테스트
- [ ] **AC-3 (형제 전수)** — 같은 "관문 없는 읽기 표면" 이 wms 의 다른 서비스에도 있는지.
      `admin-service` 만이라는 보장이 없다

---

# Related Specs

- [`docs/adr/ADR-MONO-064`](../../../../docs/adr/ADR-MONO-064-wms-outbound-tenant-visibility-plane.md) § M5 · § D4 — 이 티켓의 출처
- `projects/wms-platform/PROJECT.md` § Out of Scope (`multi-tenant`)
- `projects/wms-platform/specs/contracts/http/admin-service-api.md` § 1.3
- `TASK-MONO-304` — outbound 격리 규칙의 출처

# Related Contracts

- `projects/wms-platform/specs/contracts/http/admin-service-api.md`

# Edge Cases

- `admin_order_summary` 는 프로젝션이라 **원본보다 뒤쳐질 수 있다** — 실측 시점에
  원본 `outbound_order.status = PICKING` 인데 프로젝션은 `RECEIVED` 였다. 격리를 넣든
  안 넣든 이 지연은 별개 사안이며, 여기서 고치지 않는다
- B 를 고르면 **기존 프로젝션 행에 테넌트가 없다** — 소급 채움은 원본 조인이 필요하고,
  원본 자체가 `tenant_id IS NULL` 인 행이 있다(`ADR-MONO-064` 가 소급 stamp 를 금지했다).
  ⇒ 마이그레이션은 코드 문제 이전에 **데이터 문제**다

# Failure Scenarios

- **컨트롤러에 필터만 넣고 스키마를 안 바꾼다** → 필터할 컬럼이 없어 상수 비교가 되고,
  가드는 영원히 초록이면서 아무것도 안 지킨다
- **"전역 뷰다" 를 주석에만 적고 계약 문서를 안 고친다** → 다음 사람이 계약을 읽고
  격리가 있다고 착각한다. `ADR-MONO-064` 가 정정해야 했던 것이 정확히 그런 문장이었다
- **AC-0 없이 착수** → dashboard 컨트롤러가 6개라는 것도 추정이다. 세지 않고 시작하면
  낙오가 남는다

# Definition of Done

- [ ] AC-0 실측 기록
- [ ] A/B 결정 + 근거 (B 면 ADR ACCEPTED 선행)
- [ ] 구현 + 계약 문서 일치
- [ ] Ready for review
