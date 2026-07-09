# TASK-PC-FE-236 — guide presentation 원자 shared 통합 (5개 GuideScreen)

**Status:** done
**Area:** platform-console / console-web · **Target:** `src/features/{finance,erp,ecommerce,wms,scm}-guide/components/*GuideScreen.tsx` → 공용 `src/shared/ui/guide-primitives.tsx`
**Analysis model:** Opus 4.8 · **Impl model:** Sonnet (refactoring-engineer 위임) — 기계적 dedup, 행동/DOM/testid/aria 불변 · **검증:** Opus 재검증 + 도메인별 GuideScreen 테스트(axe 포함) GREEN.
**Source:** finance/erp 리팩토링 진단(2026-07-09) **P2b**. 진단이 finance/erp 넘어 5개 가이드 도메인 전체를 가로지르는 중복으로 판정.

---

## Goal

5개 도메인 가이드 화면(`FinanceGuideScreen` · `ErpGuideScreen` · `EcommerceGuideScreen` · `WmsGuideScreen` · `ScmGuideScreen`)이 각자 **byte-동일한 presentation 원자**(`Mono` · `NoteCard` · `StateTh` · `AttentionCell` · `TerminalCell`)를 파일마다 재정의하고 있었다. 도메인당 2~5개씩, 총 ~18개 중복 정의.

이 원자들을 공용 `shared/ui/guide-primitives.tsx` 하나로 통합하고, 각 GuideScreen은 import 로 대체한다. **순수 구조 변경 — 렌더 출력·DOM·testid·aria-label 불변**(기존 GuideScreen 테스트 무수정 GREEN 이 가드).

## Scope

1. **신규 `src/shared/ui/guide-primitives.tsx`** — `Mono` · `NoteCard`(`Card` 사용) · `StateTh` · `AttentionCell` · `TerminalCell` export. server-component 안전(무상태·무페치). feature(GuideScreen)에서만 import(app 레이어 직접 import 금지 규약 유지 — shared 는 하위 레이어라 무관).
2. **5개 GuideScreen 재배선** — 로컬 정의 삭제 + `@/shared/ui/guide-primitives` import. 각 파일이 실제 사용하던 원자만 import.
3. **`TerminalCell` aria-label 변형 보존** — 진행 중 케이스 aria-label 이 도메인별로 달랐다(erp=`"진행 중"`, ecommerce·scm=`"진행"`). 공용 `TerminalCell` 은 `inProgressLabel?: string`(기본 `'진행 중'`)을 받아, ecommerce·scm 호출부는 `inProgressLabel="진행"` 을 명시해 **기존 DOM 을 그대로 보존**. erp 는 기본값과 일치 → prop 불필요.
4. **미사용 import 정리** — 로컬 `NoteCard` 가 유일 소비처였던 파일(finance·erp·scm)은 `Card` import 제거(no-unused-vars → lint RED 방지, `[[env_console_web_local_verify_needs_lint]]`). ecommerce·wms 는 `Card` 를 본문에서 계속 사용 → 유지.

## Acceptance Criteria

- **AC-1** `shared/ui/guide-primitives.tsx` 신설, 5개 원자 export. 5개 GuideScreen 로컬 중복 정의 전부 제거.
- **AC-2** 렌더 출력·DOM·testid·aria-label 불변 — 기존 GuideScreen 테스트(`tests/unit/*GuideScreen.test.tsx`, axe 포함) 무수정 GREEN.
- **AC-3** `TerminalCell` 진행-중 aria-label 이 도메인별 기존 표기(erp="진행 중" / ecommerce·scm="진행")대로 렌더.
- **AC-4** `pnpm lint` + `tsc --noEmit` + `vitest`(전체) GREEN.

## Out of Scope

- WMS 가이드의 인라인 "진행" JSX(공용 `TerminalCell` 아님) — 원자가 아니었으므로 미변경.
- 가이드 본문 카피·표 구조 변경 — 이동/통합만, 개선 금지.
- IAM 가이드 — 해당 원자를 사용하지 않아 대상 외.

## Failure Scenarios

- 공용 `TerminalCell` 로 통합하며 aria-label 을 단일값으로 뭉갬 → 접근성 회귀. `inProgressLabel` prop + 도메인별 GuideScreen axe 테스트가 가드.
- `Card` import 을 무조건 제거 → ecommerce·wms 본문 `Card` 참조 깨짐(tsc RED). 파일별 잔여 사용 확인 후 선택적 제거로 방지.
- 로컬 정의 삭제 시 부착 JSDoc 잔여 → 파싱/lint 노이즈. 함수 본문 + 부착 주석만 삭제, 화면 최상단 설명 주석은 유지.

## Result

- 5개 GuideScreen net **-98줄**(+107/-205), 공용 파일 +77줄. 중복 원자 정의 ~18개 → 5개 canonical.
- `inProgressLabel="진행"` 4곳(ecommerce 2 + scm 2). `Card` import 3곳 제거(finance·erp·scm).
- lint clean · tsc clean · vitest **2757/2757 GREEN**(가이드 5종 axe 포함).

## Related

- 진단: finance/erp 리팩토링 스캔(2026-07-09) P2b(cross-domain guide atoms).
- 선행 리팩토링: `TASK-PC-FE-233`(ledger types 분할) · `TASK-PC-FE-234`(tolerant-label shared 추출) · `TASK-PC-FE-235`(ApprovalDetail hook-split).
