# Task ID

TASK-MONO-702

# Title

🔴 촬영 스크립트의 거부 판정이 **섹션 하나의 거부**를 **페이지 전체 거부**로 센다 — 쓸 수 있는 화면의 사진을 안 남긴다

# Status

ready

# Owner

monorepo

# Task Tags

- portfolio
- capture
- discriminator

---

# Goal

`TASK-MONO-648` AC-1b(2026-09-16, PR #3857)가 `scripts/capture-portfolio.mjs` 의 거부 판정을 **본문 문구 → 거부 요소**(`data-testid` 접미사 `-permission-denied` · `-not-eligible` · `-forbidden`)로 옮겼고, 대시보드 **카드**(`-card-`)만 부분 거부로 뺐다. 2026-09-17 창의 실전 실행에서 AC-1b 의 닫는 조건(가이드 셋 거부 0 · `/tenants` 거부 1)은 통과했지만, **다른 모양의 부분 거부**를 페이지 거부로 셌다:

| 경로 | 실제 화면(이미지 확인) | 판정 | 결과 |
|---|---|---|---|
| `/wms/operations` | 위 «운영 설정» 섹션 = «일시적으로 불러올 수 없습니다», 아래 «프로젝션 상태» 섹션**만** «권한 없음»(`wms-operations-projection-forbidden`) | `denied` | 🔴 **사진 안 남김**(쓸 수 있는 절반이 있는 화면) |
| `/ecommerce/products/[id]` | 페이지 전체 거부(`product-forbidden`, API 403) | `denied` | 🟢 맞다(대조군) |

`judgeDenialInPage()` 는 `/-card-/` 만 부분으로 가른다(`capture-portfolio.mjs:319-325`). 콘솔의 섹션·패널 단위 마커는 **같은 접미사**를 쓴다 — 2026-09-17 `features/**` 전수에서 예: `wms-operations-projection-forbidden` · `wms-operations-settings-forbidden` · `settlements-{accruals,balance,payouts,periods,rate}-forbidden` · `ledger-{entry,fx-history,fx-rates,lots}-forbidden` · `wms-asn-inspection-forbidden` · `wms-inv-detail-forbidden` · `outbound-drill-forbidden`. 이름만으로는 «페이지를 덮는 거부» 와 «섹션 하나» 를 못 가른다.

기록: `tasks/in-progress/TASK-MONO-648-*` § «창 실측 — 2026-09-17».

---

# Scope

## 포함

- 페이지 거부 vs 섹션 거부를 가르는 **술어**(이름 목록 확장이 아니라 구조 — 예: 거부 요소 밖에 다른 본문 섹션이 렌더됐는가, 또는 거부 요소가 페이지 주 영역 전체인가).
- `--self-test` 픽스처에 섹션 거부 칸 추가(양성·음성 대조군 유지).

## 제외

- 콘솔 화면의 `data-testid` 이름 체계 변경(가능하면 스크립트 쪽으로 푼다 — 바꿔야 한다는 결론이면 AC-1 에서 멈추고 묻는다).
- `/ecommerce/products/[id]` 403 자체(`TASK-MONO-703`).

---

# Acceptance Criteria

- [ ] **AC-0 — 재측정.** `judgeDenialInPage()` 와 콘솔 `features/**` 의 섹션 단위 거부 마커를 그날의 코드에서 다시 센다(위 목록은 2026-09-17 표본). 🔴 `/wms/operations` 의 두 섹션이 지금도 그 모양인지 컴포넌트에서 확인한다.
- [ ] **AC-1 — 술어를 고른다.** 이름 규칙(`-card-` 같은 목록 늘리기)과 구조 규칙(렌더 트리) 중 무엇으로 가를지 근거와 함께 적는다. 🔴 «목록 하나 더» 는 다음 모양이 또 샌다 — 고르면 이유를 적어라. 스크립트만으로 못 가르면 콘솔 마커 변경이 필요하다는 사실과 선택지를 소유자에게 묻는다.
- [ ] **AC-2 — self-test bite.** 픽스처 ① 섹션 하나만 거부 + 나머지 본문 → `denied=false` · `partial≥1` ② 페이지 전체 거부 → `denied=true` ③ 기존 6칸 유지. 새 술어를 옛 규칙(`-card-` 만)으로 되돌리면 ① 이 빨개진다.
- [ ] **AC-3 — 실전 판정.** 다음 창에서 `/wms/operations`(섹션 거부)가 사진으로 남고 `partialDenied` 로 표시되며, `/tenants` · `/ecommerce/products/[id]`(페이지 거부)는 여전히 `denied` 인지 본다. 창이 없으면 ⚪ + 갈 곳(`TASK-MONO-672`).

---

# Related Specs

- `scripts/capture-portfolio.mjs` § 권한 거부 판정 · `--self-test`
- `tasks/in-progress/TASK-MONO-648-capture-every-page-for-the-application-portfolio.md` AC-1b · § 창 실측 2026-09-17

# Related Contracts

- 없음 — 촬영 스크립트(포트폴리오 자산).

---

# Edge Cases

| 상황 | 기대 |
|---|---|
| 섹션 거부가 화면의 유일한 본문 섹션 | 사실상 페이지 거부 — 구조 술어가 `denied` 로 가르는지 확인 |
| 대시보드 카드(`-card-`) | 지금처럼 `partialDenied` 유지 |
| 섹션 거부 + 나머지 섹션이 «불러올 수 없음» | 사진은 남기되 `degraded` + `partialDenied` 둘 다 표시 — 큐레이션 후보는 아님 |

# Failure Scenarios

1. **`-projection-` · `-settings-` 를 목록에 추가하고 닫는다** → `settlements-*` · `ledger-*` 모양이 그대로 샌다.
2. **self-test 만 고치고 실전 실행을 안 본다** → 픽스처는 술어를 재지 실제 콘솔의 트리를 재지 않는다(AC-3).

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Sonnet 5** (스크립트 한 함수 + 픽스처. 판단은 AC-1 의 술어)
