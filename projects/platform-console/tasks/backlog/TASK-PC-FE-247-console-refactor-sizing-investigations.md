# TASK-PC-FE-247 — 콘솔 리팩토링 sizing 조사 2건 (착수 전 범위확정 필요)

**Status:** backlog — investigation (2026-07-18 리팩토링 스윕 잔여; PC-FE-246 에서 분리)
**Area:** platform-console / console-web
**Type:** `TASK-PC-FE` (investigation — 실행 전 sizing/judgment 필수)
**Confidence:** LOW (범위 확정·소유자 판단 전 실행 금지)

> PC-FE-246(상대시간 포매터 dedup)의 실행 가능한 part A 는 별도로 완료(review). 남은 두 항목은 **기계적 신뢰 금지 — 전수 재측정/판단 후에만 승격**.

---

## 조사 1 — `features/*/api/types.ts` 의 스키마 + UI 상태머신 게이트 혼재

`api/types.ts` 파일들이 Zod wire 스키마와 **UI측 라이프사이클 상태머신 게이트**를 함께 보유:

- `features/wms-outbound-ops/api/types.ts` (352줄) — L1–300 스키마 + `normaliseOutboundPage`, L302–352 `canPick`/`canPack`/`canShip`/`canCancel`/`cancelNeedsAdmin`/`canRetryTms`.
- 재발: `ecommerce-ops` 의 `order-types.ts`(`allowedTransitions`), `shipping-types.ts`(`allowedNextStatus`).

**착수 전 sizing 필수**: 개별 파일 대부분 300–380줄, 게이팅 로직은 tail 30–50줄뿐이라 단독 god-file 임계 미달. `*-ops` feature 전반 전수 스캔으로 실제 게이팅-로직 총량·분할 경계를 확정한 뒤에야 분할 계획(`action-gates.ts`/`state-machine.ts` 분리)이 성립.

## 조사 2 — `DomainHealthCard` pill vs `DomainHealthSummaryCard` dot (정책 사람판단)

`features/domain-health/components/DomainHealthCard.tsx:55-76` 이 `HEALTH_VISUAL` 색맵을 손으로 들고 full **pill**(L86)로 렌더. 정경 `frontend-ui.md` §3: *"Never re-implement the pill markup or a colour map at a call site."* §3 제외 목록은 "Service-health **dots**"만 카브아웃 — 이건 dot 아닌 pill.

- 같은 feature 안 두 색 시스템: `DomainHealthSummaryCard.tsx:29-33` 은 공용 `healthTone()`(`domain-health/lib/tone.ts`)로 **dot** 렌더(§3 dot 제외 적합), `DomainHealthCard` 는 그 헬퍼를 재사용 않고 별도 팔레트 손구현.
- **⚠️ 사람 판단 필요**: `api/types.ts:84-92` 주석이 이를 의도적 설계("one visual per value: glyph + colour")라 프레이밍 → 우발 드리프트 아닐 수 있음. **정경 §3 이 pill-형 health 지표를 허용/금지하는지 소유자 판단**(정경 명문화 여부 포함) 후에만 실행.

## backlog → ready 게이트

- [ ] 조사 1: `*-ops`/`*-guide` 전반 `api/types.ts` 전수 스캔으로 게이팅-로직 총량·분할 경계 확정 후 승격.
- [ ] 조사 2: 정경 §3 의 pill-형 health 지표 정책을 소유자와 확정(허용=no-op+문서 명문화 / 금지=`healthTone()` 재사용). 코드가 규칙보다 우선.

## Reference

- 발굴: 2026-07-18 콘솔 리팩토링 스윕. PC-FE-246 에서 실행 항목(part A)과 분리.
