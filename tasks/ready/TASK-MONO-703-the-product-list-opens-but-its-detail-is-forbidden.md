# Task ID

TASK-MONO-703

# Title

🔴 콘솔 E-Commerce 에서 상품 **목록은 열리는데 상세·편집만 403** 이다 — 같은 운영자 · 같은 테넌트(`ecommerce`)

# Status

ready

# Owner

monorepo

# Task Tags

- console
- ecommerce
- authorization
- cross-project

---

# Goal

2026-09-17 데모 창(AMI `b54296645`)에서 `scripts/capture-portfolio.mjs` 를 테넌트 `ecommerce` 로 돌렸을 때 관측했다(기록: `tasks/in-progress/TASK-MONO-648-*` § «창 실측 — 2026-09-17»).

| 경로 (운영자 `demo@demo.com`, 테넌트 `ecommerce`) | 결과 |
|---|---|
| `/ecommerce/products` (목록) | 🟢 열림 — 동적 경로 해석이 목록에서 링크를 찾았다 |
| `/ecommerce/products/b0000000-0000-0000-0000-000000000002` (상세) | 🔴 화면 `product-forbidden` · `GET /api/ecommerce/products/{id}` → **403 `{"code":"FORBIDDEN","message":"not permitted"}`** |
| `/ecommerce/products/{id}/edit` | 🔴 `denied`(같은 상품) |
| 대조군: `/ecommerce/promotions/[id]` · `/ecommerce/sellers/[id]` · `/ecommerce/users/[id]` | 🟢 전부 열림(같은 세션) |

⇒ **목록 권한과 상세 권한이 어긋난다.** 방문자(운영자)는 목록에서 상품을 눌러 «권한 없음» 을 만난다.

🔴 **어느 층이 거절했는지는 아직 모른다.** 콘솔 게이트웨이 코어가 403 메시지를 `'not permitted'` 로 **덮어쓴다**(`TASK-PC-FE-282` 기록 · `tests/unit/sample-read-only-copy.test.tsx`) — 그래서 응답 본문의 `FORBIDDEN` 은 **원래 코드가 아닐 수 있다**. 후보: ① 콘솔 route handler 의 적격 판정(`app/(console)/ecommerce/products/[id]/page.tsx` 의 `product-forbidden`) ② ecommerce 게이트웨이의 경로별 권한 ③ product-service 의 상세 엔드포인트 권한 ④ 그 상품 id 의 테넌트/판매자 소유 검사. 🔴 **추론으로 고르지 마라** — AC-0 이 층을 관측으로 지목한다.

---

# Scope

## 포함

- 403 을 낸 **층**을 관측으로 지목(원래 에러 코드까지).
- 그 층의 요구 권한 ↔ 데모 운영자가 `ecommerce` 테넌트에서 실제로 가진 권한 대조.
- 결함이면 고친다(계약/스펙 먼저). 의도라면 목록이 상세 링크를 **그리지 않게** 하거나 사유를 표시하는 쪽을 고른다(🔴 소유자 결정).

## 제외

- 촬영 스크립트의 섹션 거부 판정(`TASK-MONO-702`).
- 다른 ecommerce 동적 경로(주문 · 알림 템플릿 — 목록이 비어 해석 불가였다, 권한 문제가 아니다).

---

# Acceptance Criteria

- [ ] **AC-0 — 층을 지목한다.** 콘솔 `app/api/ecommerce/products/[id]/route.ts` → 게이트웨이 코어 → ecommerce 게이트웨이 → product-service 경로를 코드로 따라가고, **덮어쓰기 전의 원래 응답**(상태 · 코드)을 어디서 볼 수 있는지 찾는다. 로컬 재현(단위/슬라이스 테스트)으로 같은 403 을 만들 수 있으면 그것이 첫 판정이다. 🔴 창에서만 볼 수 있으면 그 사실과 술어를 적고 ⚪ + 갈 곳.
- [ ] **AC-1 — 요구 ↔ 보유 대조.** 상세가 요구하는 권한 키(또는 소유 검사)와, 목록이 요구하는 것을 **나란히** 적는다. 🔴 화면 문구는 키가 아니다(`TASK-MONO-676` 이 배운 것).
- [ ] **AC-2 — 결함/의도 판정 → (결함이면) 고친다, (의도면) 소유자에게 표시 방식을 묻는다.** bite: 고친 뒤 되돌리면 AC-0 의 재현 테스트가 403 으로 돌아간다.
- [ ] **AC-3 — 창 판정.** 테넌트 `ecommerce` 로 `/ecommerce/products` → 상품 클릭 → 상세·편집이 열리는지(또는 의도된 표시인지) 이미지로 본다. 🔴 백엔드 변경이면 AMI 재굽기 뒤 창이어야 한다. 창이 없으면 ⚪ + 갈 곳(`TASK-MONO-672`).

---

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` (ecommerce 프록시 · 403 처리)
- `projects/ecommerce-microservices-platform/specs/contracts/http/` — product 상세 엔드포인트 계약(AC-0 에서 파일을 특정한다)
- `tasks/done/TASK-PC-FE-282-*` — 게이트웨이 코어의 403 메시지 덮어쓰기

# Related Contracts

- ecommerce product 상세 API · 콘솔 ecommerce 프록시. 🔴 바꿔야 하면 계약 먼저.

---

# Edge Cases

| 상황 | 기대 |
|---|---|
| 테넌트 `demo-corp` 에서 같은 상세 | `demo-corp` 는 상품 목록이 비어 링크가 없었다 — 대조군으로 쓸 수 없음을 적는다 |
| 특정 상품(`b0…002`)만 거부 | 다른 상품 id 로도 재서 «상품 전반» 인지 «그 행» 인지 가른다 |
| 판매자 소유 검사(운영자가 판매자가 아님) | 의도일 수 있다 — AC-2 의 소유자 결정으로 |

# Failure Scenarios

1. **응답의 `FORBIDDEN` 을 원래 코드로 믿는다** → 코어가 덮어쓴 값일 수 있다(AC-0).
2. **콘솔 적격 판정만 풀어 화면을 연다** → 백엔드가 여전히 403 이면 화면은 «불러올 수 없음» 으로 바뀔 뿐이다.
3. **한 층을 고치고 창에서 안 본다** → 층이 둘 이상 겹쳐 있을 수 있다(AC-3).

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Opus 5** (층 지목 + 두 프로젝트 권한 대조. 원인이 콘솔 한 곳으로 확정되면 Sonnet 5)
