# Task ID

TASK-PC-FE-284

# Title

E-Commerce 화면이 샘플로 선다 — 상품·주문·프로모션·판매자·정산·배송·회원·알림 템플릿 (`ADR-MONO-074` 실행 3/8)

# Status

ready

# Owner

platform-console

# Task Tags

- code
- test
- demo

---

# Goal

`TASK-PC-FE-282` 샘플 모드 위에서 **ecommerce 도메인 GET 을 전부 `ready`** 로 만든다.

⏳ **`TASK-PC-FE-282` 머지 전 착수 금지.** 🔴 283~288 은 등록부·원장을 공유 ⇒ **직렬 머지**.

화면: `/ecommerce` · `/ecommerce/guide`(정적) · `/ecommerce/products` · `/products/new` · `/products/[id]` · `/products/[id]/edit` ·
`/ecommerce/orders` · `/orders/[id]` · `/ecommerce/promotions` · `/promotions/new` · `/promotions/[id]` · `/promotions/[id]/edit` ·
`/ecommerce/sellers` · `/sellers/new` · `/sellers/[id]` · `/ecommerce/settlements` · `/settlements/periods/[id]` ·
`/ecommerce/shippings` · `/ecommerce/users` · `/users/[id]` · `/ecommerce/notifications/templates` · `/templates/new` · `/templates/[id]/edit`

코어: `callEcommerceGateway`. ADR 인벤토리: GET **19** · 쓰기 **28**.

---

# Scope

## In Scope

- 위 화면의 ecommerce GET 픽스처 + 원장 `ready`
- 편집 화면(`…/edit`)의 **초기값 로드**(GET)까지 — 폼이 채워져 보여야 «무엇을 하는 화면인가» 가 보인다

## Out of Scope

- 저장·삭제·상태 전이 — `SAMPLE_READ_ONLY`
- 상품 이미지 **바이너리** 프록시(`/api/ecommerce/products/[id]/images/[imageId]`) — AC-7 참조

---

# Acceptance Criteria

- [ ] **AC-0** 282 AC-0 표·원장에서 ecommerce `pending` GET 목록을 이 파일에 적는다.
- [ ] **AC-1** 전부 `ready` + 픽스처별 실제 파서 통과 테스트.
- [ ] **AC-2** «(샘플)» 규칙 테스트 초록(상품명·판매자명·회원명·템플릿 제목 끝). 🔴 금액·수량·주문번호·상태 enum 에는 없음.
- [ ] **AC-3** 목록 id ↔ 상세 일관(상품·주문·프로모션·판매자·정산 기간·회원). 주문 상세의 상품·회원 참조가 **같은 픽스처 세계** 안의 것.
- [ ] **AC-4** 노출된 필터·검색·페이지가 픽스처 위에서 적용되고 총계가 실제 행 수와 같다.
- [ ] **AC-5** 대표 쓰기 1개(예: 환불 승인) → «샘플 화면에서는 실행되지 않습니다».
- [ ] **AC-6** `e2e-smoke` 익명 `/ecommerce/orders` 렌더 1칸 + 주문 1건 상세 진입 1칸.
- [ ] **AC-7** 상품 이미지: 🔴 MinIO 주소를 픽스처에 넣지 않는다(EC2 가 꺼지면 깨진 이미지 = 고장으로 보인다). 선택지 — 이미지 없음 표시 / 이미 공개인 CDN 주소 — 를 **골라서 이유와 함께** 적는다.

# Related Specs

- `docs/adr/ADR-MONO-074-anonymous-visitors-see-the-real-console-with-sample-data.md`
- `TASK-PC-FE-282` (기반)
- `projects/platform-console/specs/services/console-web/architecture.md`

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.10 (ecommerce)

# Edge Cases

- 편집 화면이 초기 GET 후 저장 → `SAMPLE_READ_ONLY`, 폼 값은 유지.
- 정산 기간 상세의 합계 = 행 합 (합성이어도 산술은 맞아야 한다 — 틀리면 «고장» 으로 읽힌다).

# Failure Scenarios

- 주문 상세가 없는 상품 id 를 참조 → 링크 404 ⇒ AC-3.
- 이미지 주소가 꺼진 오리진 → 깨진 이미지 ⇒ AC-7.

# Test Requirements

- `pnpm lint` · `npx tsc --noEmit` · `pnpm test` · `pnpm test:e2e:smoke`(각각 독립 + `rc=$?`)

# Definition of Done

- [ ] 원장의 ecommerce `pending` 0
- [ ] 로그인 운영자 경로 무수정 초록

분석=Opus 5 / 구현 권장=Sonnet 5.
