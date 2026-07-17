# TASK-PC-FE-246 — 상대시간 포매터 dedup + 두 건의 sizing 조사

**Status:** backlog — candidate (2026-07-18 리팩토링 스윕 발굴)
**Area:** platform-console / console-web
**Type:** `TASK-PC-FE` (dedup 1건 = 실행 가능, 2건 = 착수 전 sizing 조사 필요)
**Confidence:** dedup=MEDIUM · 조사 2건=LOW (범위 확정 전 실행 금지)

---

## 파트 A — 상대시간 포매터 중복 → `shared/lib/datetime.ts formatRelative()` 추출 (MEDIUM, 실행 가능)

동일한 "상대 age 버킷" 로직이 두 곳에 독립 재구현:

- `features/notifications/components/NotificationBell.tsx:52` — 로컬 `formatShortDate(iso)`: "방금"/"N분 전"/"N일 전", 30일+ fallback 은 `toLocaleDateString('ko-KR', {month:'short', day:'numeric'})` 를 **`timeZone`/`hourCycle` pin 없이** 직접 호출(정경 §1 은 이 pin 을 "load-bearing, not style"=hydration 안전이라 규정).
- `features/ledger-ops/components/FxRatesTable.tsx:49-61` — `humanizeAge(ageSeconds)`: `<60s`→방금, `<3600s`→N분전, `<86400s`→N시간전, else→N일전 (절대 날짜로 롤 안 함 = 미세 행동 차이).

두 미조정 중복 포매터. `shared/lib/datetime.ts` 의 `formatDate`/`formatDateTime` 는 절대시간용이라 상대시간을 안 덮으므로, **`formatRelative()` 를 신설**해 두 소비자를 통합(각자의 행동 차이 — 절대날짜 롤 유무 — 를 옵션으로 보존할지 착수 시 결정).

> ⚠️ 순수 dedup 이 아님: `NotificationBell` 의 30일+ 절대 fallback 에 timeZone pin 을 추가하면 hydration 관점의 관찰가능 변경. 이는 정경 §1 준수 방향이므로 의도적 개선으로 AC 에 명시.

## backlog → ready 게이트 (파트 A)

- [ ] `formatRelative()` 시그니처(절대 fallback 옵션·pin) 확정, 정경 §1 datetime 절 갱신 여부.
- [ ] AC: 두 소비자 통합, timeZone/hourCycle pin, vitest 회귀 0(NotificationBell·FxRatesTable 라벨 보존).

---

## 파트 B — 조사 1: `features/*/api/types.ts` 의 스키마 + UI 상태머신 게이트 혼재 (LOW, sizing 필요)

`api/types.ts` 파일들이 Zod wire 스키마와 **UI측 라이프사이클 상태머신 게이트**를 함께 보유:

- `features/wms-outbound-ops/api/types.ts` (352줄) — L1–300 스키마 + `normaliseOutboundPage`, L302–352 `canPick`/`canPack`/`canShip`/`canCancel`/`cancelNeedsAdmin`/`canRetryTms` (producer 주문 라이프사이클의 UI 미러 = UX 로직, wire-shape 아님).
- 같은 shape 재발: `ecommerce-ops` 의 `order-types.ts`(`allowedTransitions`), `shipping-types.ts`(`allowedNextStatus`).

**착수 전 sizing 필수**: 개별 파일은 대부분 300–380줄이고 게이팅 로직은 tail 30–50줄뿐이라 **단독으로는 god-file 임계 미달**. `*-ops` feature 전반을 전수 스캔해 실제 범위를 확정한 뒤에야 분할 계획(예: `action-gates.ts`/`state-machine.ts` 분리)이 성립. 기계적 신뢰 금지 — 전수 재측정 후 판단.

## 파트 C — 조사 2: `DomainHealthCard` pill vs `DomainHealthSummaryCard` dot (LOW, 사람 판단 필요)

`features/domain-health/components/DomainHealthCard.tsx:55-76` 이 `HEALTH_VISUAL` 색맵을 손으로 들고 full **pill**(L86)로 렌더. 정경 §3: *"Never re-implement the pill markup or a colour map at a call site."* §3 의 제외 목록은 "Service-health **dots**"만 카브아웃하는데 이건 dot 이 아니라 pill.

- **한 feature 안 두 색 시스템**: 같은 신호를 `DomainHealthSummaryCard.tsx:29-33` 는 공용 `healthTone()`(`features/domain-health/lib/tone.ts`)로 **dot** 렌더(§3 dot 제외 적합)하는데, `DomainHealthCard` 는 그 헬퍼를 재사용 않고 별도 팔레트 손구현.
- **⚠️ 사람 판단 필요**: `api/types.ts:84-92` 주석이 이를 의도적 설계("one visual per value: glyph + colour")라 프레이밍 → 우발 드리프트가 아닐 수 있음. **실행 전 정경 §3 이 pill 형 health 지표를 허용/금지하는지 소유자 판단**(정경 문서에 명문화 여부 결정 포함).

## backlog → ready 게이트 (파트 B·C)

- [ ] 파트 B: `*-ops`/`*-guide` 전반 `api/types.ts` 전수 스캔으로 게이팅-로직 총량·분할 경계 확정 후에만 승격.
- [ ] 파트 C: 정경 §3 의 pill-형 health 지표 정책을 소유자와 확정(허용 시 no-op + 문서 명문화 / 금지 시 `healthTone()` 재사용). 코드가 규칙보다 우선.

## Reference

- 발굴: 2026-07-18 콘솔 리팩토링 발굴 스윕(god-file · 컨벤션 스캔). 정경 `docs/conventions/frontend-ui.md` §1·§3.
