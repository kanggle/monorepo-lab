# Task ID

TASK-FE-110

# Title

웹스토어 배송 추적이 «취소됨»(`CANCELLED`) 배송을 모른다 — 켜지는 단계 없이 빈 추적기가 보인다 (ADR-MONO-022 D4 v2(d))

# Status

done

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (상태 하나 추가 · 문구)
> 기안 2026-10-23 UTC — `TASK-BE-649` 구현 중 곁 발견. 배송에 끝 상태 `CANCELLED` 가 생겼는데(출고 전에 주문이 취소된 배송) 구매자 화면에 담당 티켓이 없다. 콘솔 짝 = `projects/platform-console` `TASK-PC-FE-367`.

# Dependency Markers

- 선행: `TASK-BE-649` (배송 `CANCELLED` · 계약 `shipping-api.md`).

# Goal

구매자가 취소한 주문의 배송을 열면, 지금은 `getStepIndex` 가 -1 이라 켜진 단계가 없는 추적기가 보인다. 화면이 깨지지는 않는다. 이것을 «배송이 취소되었습니다» 류의 상태로 보이게 한다.

# Scope

## In Scope

- **AC-0**: 웹스토어가 배송 상태를 받는 곳을 모두 찾는다(file:line). `packages/types/src/shipping.ts:3`, `shipping-steps.ts:3-12`, `ShippingTracker.tsx:51` 이 출발점이고, 주문 상세 · 목록의 배송 표시도 확인한다.
- 타입에 `CANCELLED` 를 넣는다. 추적기는 단계 대신 취소 표시를 그린다.
- **사용자에게 보이는 문구는 머지 전에 소유자 승인**을 받는다.

## Out of Scope

- 콘솔(`TASK-PC-FE-367`) · 서버.

# Acceptance Criteria

- [ ] **AC-0** — 위 측정. **측정함 (2026-10-23 UTC)** — 웹스토어가 `ShippingStatus` 를 받는 곳:
  `packages/types/src/shipping.ts:3`(유니온 · `ShippingStatusHistoryEntry:6` · `ShippingResponse:13` 이 씀) ·
  `apps/web-store/src/features/order/lib/shipping-steps.ts:3-12`(`SHIPPING_STEPS` · `getStepIndex` → 모르는 값은 -1) ·
  `features/order/ui/ShippingTracker.tsx:51`(`currentIndex=-1` → `ShippingStepIndicator` 가 켜진 단계 없이 그림) ·
  `ShippingStepIndicator.tsx:19-21`. 소비 지점은 `OrderDetailView.tsx:97` 하나뿐 — 주문 목록에는 배송 표시가 없다.
  `OrderStatusBadge.tsx:8` 은 *주문* 상태(`CANCELLED` 는 이미 있음)라 무관. 메시지 카탈로그(i18n) 없음 — 한국어 문자열 직접 기재라 로케일은 하나.
  `UpdateShippingStatusRequest.status`(`shipping.ts:40`)는 운영자 대상이라 `CANCELLED` 제외(계약 `shipping-api.md:203`).
- [x] **AC-1** — 문구 소유자 승인. ✅ 2026-10-23 UTC 소유자 선택창 «이대로 승인» — «주문이 취소되어 배송이 취소되었습니다.»
- [x] **AC-2** — 구현 + 단위 시험 · CI 초록 · 브랜치 nightly(web-store full-stack) 확인. ✅ 2026-10-23 UTC (메인 세션) — PR #4491 체크 pending 0 · fail 0. 로컬에서 vitest 가 돌지 않았으므로 CI 로그에서 새 시험이 돈 것을 확인했다: `shipping-tracker.test.tsx` 15건(기준 14) · `shipping-steps.test.ts` 10건(기준 6), 모두 ✓. 브랜치 nightly `39929126854` success(web-store full-stack 포함).
- [ ] **AC-3** — 굽기 뒤 라이브: 출고 전에 취소한 주문의 배송이 취소로 보인다.

# Related Specs

- `docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md` § D4 v2(d)

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/http/shipping-api.md` (`CANCELLED`)

# Edge Cases

- 배송 이력(`statusHistory`)의 마지막 항목이 `CANCELLED` 다. 그 앞 단계는 `PREPARING` 하나뿐이다.

# Failure Scenarios

1. 취소된 배송이 «준비중» 처럼 읽혀 구매자가 물건을 기다린다 → AC-0 · 문구.

## CORRECTION (2026-10-23 UTC)

- **AC-0 의 체크 칸 `[ ]` 은 표시 누락이다.** 그 줄 본문이 «**측정함 (2026-10-23 UTC)**» 과 측정 내용(file:line 다섯 곳 · 소비 지점 · i18n 없음 · 운영자 요청 타입 제외)을 담고 있어, AC-0 의 동사(«위 측정») 는 그 줄 자체로 닫혀 있다. 동결 규칙으로 칸은 고치지 않는다.

## 라이브 확인 (2026-10-23 UTC · 33차 창)

- 배포 상태: 화면은 #4491(`e5fb1e715`) 머지로 Vercel 에 이미 있었고, `CANCELLED` 배송을 만드는 서버 쪽(`TASK-BE-649`)이 굽기 `283fd39cf` → AMI `ami-02b3ba00a3b2ff622`(51분 36초 · 태그 RepoCommit 일치) · 핀 PR #4498(`c5e3ddad3`) · 인스턴스 `i-0f39aec49ddbaa899` 로 실렸다.
- **AC-3 ✅** — 데모 구매자로 웹스토어 `/my/orders/49eae2f6…`(이 창에서 콘솔로 취소한 `CONFIRMED` 주문 — `TASK-BE-646` · `TASK-BE-649` 와 같은 주문) → «배송 추적» 아래 «주문이 취소되어 배송이 취소되었습니다.» (빨간 글씨) · 단계/택배사/운송장 없음 (스크린샷 확인).

### 닫기 — 4차원 확인 (2026-10-23 UTC)

- (a) PR #4491 `state=MERGED` · `mergedAt` 2026-10-23T09:52:54Z.
- (b) 머지 커밋 `e5fb1e715` = `origin/main` 조상(`git merge-base --is-ancestor`), 굽기 커밋 `283fd39cf` 의 조상이기도 하다.
- (c) 머지된 PR 의 `statusCheckRollup` 69 칸 중 실패 0 (SUCCESS 19 · SKIPPED 50).
- (d) `# Acceptance Criteria` 를 열어 읽음 — AC-0 ✅(위 CORRECTION) · AC-1 · AC-2 ✅(기존) · AC-3 ✅(위). 체크 칸은 `review/` 동결 규칙으로 고치지 않았다 — 닫힘은 이 절이 기록한다.
- ⇒ `review/ → done/`.
