# TASK-PC-FE-247 — 콘솔 리팩토링 sizing 조사 2건

**Status:** done
**Area:** platform-console / console-web
**Type:** `TASK-PC-FE` (investigation — sizing/judgment)
**Lifecycle:** backlog(2026-07-18, PC-FE-246 에서 분리) → 조사 수행 → **DECLINE (양쪽 무액션)** → done

> **결론 (2026-07-18)**: 두 조사 모두 **코드 변경 불필요(decline)**. 조사1=benign 응집(분할 시 오히려 파편화), 조사2=의도적 설계(§3 드리프트 아님). 조사2의 유일한 산출 = 정경 §3 문서 명문화(재-flag 방지, 사용자 승인).

---

## 조사 1 — `features/*/api/types.ts` 스키마 + UI 상태머신 게이트 혼재 → **DECLINE (benign)**

**전수 재측정 결과** (티켓 초기 추정 "30–50줄 tail"·"166줄" 은 승계하지 않고 실측): 게이팅 로직은 정확히 5개 파일에만 존재하며 대부분 소형이고 스키마와 **인터리브**(tail 아님):

| 파일 | 게이팅 | 형태 |
|---|---|---|
| `ecommerce-ops/order-types.ts` | `TRANSITIONS` 맵 + `allowedTransitions` (L87-89, **3줄**) | 인터리브(뒤로 스키마 계속) |
| `ecommerce-ops/shipping-types.ts` | `allowedNextStatus` (L71) 소형 | 인터리브 |
| `erp-ops/approval-types.ts` | `allowedTransitionsFor`+`transitionRequiresReason` (**11줄**) | tail |
| `scm-replenishment/types.ts` | `canApprove`+`canDismiss` (**10줄**) | tail |
| `wms-outbound-ops/types.ts` | `canPick`/`canPack`/`canShip`/`canCancel`/`cancelNeedsAdmin`/`canRetryTms` (**45줄**) | tail |

**판정 = 분할하지 않는다**:
- 게이팅은 **같은 파일이 정의한 status enum 의 전이 규칙**(예: `OrderStatus` 의 허용 전이)이라 **응집**. 컴포넌트/훅이 소비하지만 이미 export 돼 격리 테스트 가능.
- 갈라짐·버그·변경-누락 위험 **0** — "schema + state-machine 혼재" 는 스타일 관찰이지 결함이 아님(발굴 에이전트 자신도 "단독 티켓 감 아님").
- 분할 시 응집된 status 로직을 오히려 파편화하고, order/shipping 은 게이팅이 3줄이라 분할 대상 자체가 미달.
- **재개 조건**: 어느 한 파일의 게이팅 로직이 독립적으로 커지고(수십→수백 줄) 스키마와 분리해도 응집이 깨지지 않을 때.

## 조사 2 — `DomainHealthCard` pill vs `DomainHealthSummaryCard` dot → **DECLINE (의도적 설계)**

- 두 컴포넌트는 **같은 신호의 다른 해상도**: `DomainHealthCard` = raw 4-value `HealthStatus`(UP/DOWN/OUT_OF_SERVICE/UNKNOWN)를 glyph(OK/X/!/?)+색+라벨로 렌더하는 pill; `DomainHealthSummaryCard` = 그걸 3-tone 으로 collapse(`healthTone()`)한 glance dot. "같은 신호 두 색 시스템" 이 **아니라** 상세(4값) vs 요약(3톤).
- `domain-health/api/types.ts:82-92` 주석이 **"one visual per value" 를 명시 문서화** — 의도적. `StatusBadge` 는 glyph 도 이 4톤도 지원 안 해 강제 시 affordance 손실.
- §3 "Service-health **dots**" 제외의 **취지**가 이 health 지표를 덮음(문자만 "dot").

**판정 = 코드 no-op + 정경 §3 명문화**(사용자 승인, 2026-07-18): `frontend-ui.md` §3 "Deliberately NOT status chips" 에 **"Service-health status pills"** 항목 추가 — 다음 스윕이 `DomainHealthCard` 를 residue 로 재-flag 하지 않도록(PC-FE-241/242 "잔여가 아닌 것" 명문화 계열).

## Reference

- 발굴: 2026-07-18 콘솔 리팩토링 스윕. 상위 묶음 = PC-FE-243/244/245/246 + PC-BE-012(#2649 머지).
