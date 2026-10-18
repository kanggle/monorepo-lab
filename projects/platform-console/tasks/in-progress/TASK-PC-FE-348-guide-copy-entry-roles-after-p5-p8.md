# Task ID

TASK-PC-FE-348

# Title

가이드 문안이 `ADR-MONO-083` P5~P8 뒤 틀렸다 — «도메인 권한 = `*_OPERATOR` 하나 · 테넌트를 고르면 자동 부여» 문장을 세분 업무 역할 · 진입 역할 파생으로 고친다 (SCM · E-commerce · IAM 가이드)

# Status

in-progress

# Owner

frontend

# Task Tags

- docs
- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (정적 문안 · 표 데이터 — 🔵 사용자에게 보이는 문장이라 소유자 검토 후 머지)
> 기안 2026-10-16 UTC — `TASK-PC-FE-342` ~ `345` 가 각 티켓에 «가이드 문안이 지금 틀림» 으로 적어 둔 것을 하나로 모은 후속(코디네이터 지시, `TASK-PC-FE-344` PR 에서 기안). 서버 · 신호 무변경.

# Dependency Markers

- 근거(이미 라이브, 28차 창 2026-10-16): `TASK-MONO-792`(SCM) · `793`(ERP) · `794`(Finance) · `795`(E-commerce) — 각 도메인 `iam-integration.md` § 업무 역할(정본).
- 형제(신호 · 버튼): `TASK-PC-FE-342` · `343` · `344` · `345` · `346` · `347`. 이 티켓은 **문안만** 고친다.

# Goal

P5~P8 뒤 SCM · ERP · Finance · E-commerce 의 운영 권한은 «테넌트를 고르면 자동으로 주어지는 `*_OPERATOR` 하나» 가 아니다. 업무 권한 세트로 **배정된** 영역별 조회/변경 역할이고, `*_OPERATOR` 는 그중 하나라도 있으면 auth 가 덧붙이는 **진입 역할**(배정 불가 · 단독으로 업무 API 를 열지 않음)이다. 정적 가이드가 옛 모델을 말하고 있어 방문자 · 운영자가 «세트를 줘도 안 되는» 이유를 가이드에서 찾을 수 없다.

# Scope

## In Scope (AC-0 에서 전수 재측정 — 아래는 2026-10-16 grep 결과)

| 파일 | 줄 | 지금 말하는 것 | 바뀌어야 할 것 |
|---|---|---|---|
| `apps/console-web/src/features/scm-guide/data.ts` | `:27` · `:363-369` | «단일 SCM_OPERATOR · 이 권한 하나뿐 · 보충 · 설정은 테넌트만 맞으면 열린다» | SCM 8 업무 역할(발주 · 수요계획 · 물류 · 재고 가시성 × 조회/변경) · `SCM_OPERATOR` = 파생 진입 역할 · 보충/설정 = 수요계획 역할 · 출고 «발송 재시도» = `SCM_LOGISTICS_WRITE` |
| `apps/console-web/src/features/ecommerce-guide/data.ts` | `:26` · `:553-558` · `:632` | «ECOMMERCE_OPERATOR 하나로만 열린다 · 테넌트를 고르면 자동 부여 · 7 화면이 똑같이» | 13 영역 역할(주문 · 상품(+판매자) · 회원(조회만) · 정산 · 프로모션 · 배송 · 알림) · 진입 역할 파생 · 화면마다 다른 역할 |
| `apps/console-web/src/features/iam-guide/data.ts` | `:139` · `:682-685` | 도메인 역할 목록 = `*_OPERATOR` 7 개 / 도메인별 `roles: '<X>_OPERATOR'` | 세분 4 도메인은 업무 역할(카탈로그) + «진입 역할 `*_OPERATOR` 는 파생» 문장, WMS · MES · fan 은 지금 카탈로그대로 |
| `apps/console-web/src/features/wms-guide/data.ts` | `:396` | «테넌트를 선택하면 WMS 재고 · 출고 권한이 자동으로 주어진다» | P3(`TASK-BE-632`) 뒤 = 배정된 세트 ∩ 구독 — «자동» 이 아니다 (WMS 는 P5~P8 밖이지만 같은 문장 모양) |
| (코드 주석 — 사용자에게 안 보임, 같은 PR 에서 정정 권장) | `ecommerce-ops/api/products-api.ts:150` · `ecommerce-ops/api/settlement-types.ts:12` | «role `ECOMMERCE_OPERATOR`» | 게이트웨이는 진입 역할, 서비스는 영역 역할 |

- ERP · Finance 가이드(`erp-guide` · `finance-guide`)는 역할 문장이 없다(grep 0) — AC-0 에서 «권한» 절이 필요한지만 판단(만들지 않으면 그 이유를 적는다).
- 가이드 시험(`*GuideScreen.test.tsx`)의 해당 단언 갱신 · 문안이 `permission-map.ts` 데이터와 어긋나지 않는지 대조.

## Out of Scope

- 신호 · 버튼(`permission-map.ts` `serverRequires` / `writeRequires`) · 서버 판정 — 형제 티켓.
- `R__seed_demo_cs_operator.sql` 머리 주석(체크섬 — `TASK-MONO-795` 가 일부러 남김).

# Acceptance Criteria

- [ ] **AC-0** — `features/**` 의 `(SCM|ERP|FINANCE|ECOMMERCE)_OPERATOR` · «자동으로 (부여|주어)» · «하나뿐» · «테넌트만 맞으면» 전수 grep(🔴 한글 패턴은 그룹으로 감싼다) · 위 표 갱신.
- [ ] **AC-1** — 바뀐 문장은 각 도메인 `iam-integration.md` § 업무 역할의 말과 같다(역할 이름 · 무엇을 여는지). 진입 역할을 «배정하는 권한» 으로 적은 문장 0.
- [ ] **AC-2** — 가이드 시험 갱신 + 옛 문장이 돌아오면 빨개지는 단언 1 개 이상(예: SCM 가이드 본문에 «하나뿐» 없음).
- [ ] **AC-3** — 소유자 문안 검토(사용자에게 보이는 문장) — PR 본문에 바뀐 문장 전후를 표로.
- [ ] **AC-4** — 배포 상태(Vercel)를 따로 적는다.

# Related Specs

- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md` P5 ~ P8
- `projects/{scm,erp,finance,ecommerce-microservices}-platform/specs/integration/iam-integration.md` § 업무 역할

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.3.4 (진입 역할은 선언하지 않는다)

# Edge Cases

- 데모 운영자는 legacy-compat 세트로 모든 역할을 받는다 — «데모에서는 다 열린다» 와 «역할은 배정된다» 를 함께 적어야 거짓이 아니다.
- 샘플 방문자(비로그인)에게도 보이는 정적 문안이다 — 내부 파일 경로 · 서비스 클래스 이름을 본문에 넣지 않는다.

# Failure Scenarios

1. 문안을 위임받은 쪽이 역할 이름 · 항목 수를 바꾼다(«쉽게 다시 쓰기» 가 사실을 흘린다) — AC-1 대조.
2. 한 가이드만 고치고 IAM 가이드의 도메인 역할 표는 그대로 — 두 곳이 서로 다른 말을 한다.
