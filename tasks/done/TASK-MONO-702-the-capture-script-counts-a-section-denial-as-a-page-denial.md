# Task ID

TASK-MONO-702

# Title

🔴 촬영 스크립트의 거부 판정이 **섹션 하나의 거부**를 **페이지 전체 거부**로 센다 — 쓸 수 있는 화면의 사진을 안 남긴다

# Status

done (2026-09-17 UTC — 창 판정 2026-09-17T16:44~16:52Z)

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

- [x] **AC-0 — 재측정.** `judgeDenialInPage()` 와 콘솔 `features/**` 의 섹션 단위 거부 마커를 그날의 코드에서 다시 센다(위 목록은 2026-09-17 표본). 🔴 `/wms/operations` 의 두 섹션이 지금도 그 모양인지 컴포넌트에서 확인한다.
- [x] **AC-1 — 술어를 고른다.** 이름 규칙(`-card-` 같은 목록 늘리기)과 구조 규칙(렌더 트리) 중 무엇으로 가를지 근거와 함께 적는다. 🔴 «목록 하나 더» 는 다음 모양이 또 샌다 — 고르면 이유를 적어라. 스크립트만으로 못 가르면 콘솔 마커 변경이 필요하다는 사실과 선택지를 소유자에게 묻는다.
- [x] **AC-2 — self-test bite.** 픽스처 ① 섹션 하나만 거부 + 나머지 본문 → `denied=false` · `partial≥1` ② 페이지 전체 거부 → `denied=true` ③ 기존 6칸 유지. 새 술어를 옛 규칙(`-card-` 만)으로 되돌리면 ① 이 빨개진다.
- [x] **AC-3 — 실전 판정.** 다음 창에서 `/wms/operations`(섹션 거부)가 사진으로 남고 `partialDenied` 로 표시되며, `/tenants` · `/ecommerce/products/[id]`(페이지 거부)는 여전히 `denied` 인지 본다. 창이 없으면 ⚪ + 갈 곳(`TASK-MONO-672`).

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

---

# 구현 기록 — 2026-09-17 UTC (분석·구현=Opus 5)

## AC-0 — 재측정 (그날의 코드)

- `judgeDenialInPage()` 는 여전히 `/-card-/` 만 부분으로 뺐다(수정 전 `scripts/capture-portfolio.mjs:319-335`).
- `console-web/src`(테스트 제외)에서 세 접미사로 끝나는 `data-testid` 문자열: **115곳 · 79파일**(Grep count). 🔵 페이지 전체 거부는 한 모양이다 — `<section>` 안에 `<h1>` + 거부 `div`(+ 안쪽이나 뒤에 «목록으로»/«카탈로그로 이동» 링크)뿐. 열어서 확인: `ecommerce/products/[id]/page.tsx:28-42` · `wms/inventory/page.tsx:100-118` · `accounts` · `audit` · `tenants` · `operators` · `ledger` · `finance/accounts` · `ecommerce/sellers` 의 `page.tsx`. 섹션 거부는 `features/**` 컴포넌트 안에서 **형제 섹션 옆에** 선다(`WmsOperationsScreen.tsx` · `AccrualsSection.tsx` · `ledger-ops/*Panel.tsx` 등).
- 🔴 `/wms/operations` 는 지금도 그 모양이다 — `WmsOperationsScreen.tsx:77-160` «운영 설정»(`wms-operations-settings-{forbidden,degraded,empty,table}`) 과 `:162-` «프로젝션 상태»(`wms-operations-projection-{forbidden,degraded,empty,table}`) 가 **각자** 상태를 갖는다. 2026-09-17 창의 «위 degraded · 아래 forbidden» 은 두 칸의 한 조합이다.
- 콘솔 셸: `(console)/layout.tsx:264-273` — 사이드바는 `<aside>`, 화면 본문은 `<main>` 안. 배너·헤더는 `<main>` 밖.

## AC-1 — 술어: **구조** (이름 목록이 아니라)

**고른 것**: 주 영역(`<main>`, 없으면 `body`)을 복제해 ⓐ 거부 요소 전부 ⓑ «본문이 아닌 것» — 제목(`h1`–`h6`) · 링크 · 버튼 · `nav` · 탭 · 폼 라벨/컨트롤 · 안 그려지는 것(`script` · `[hidden]` · `.sr-only` …) — 을 지우고 **남는 글자 수**(공백 제외)를 센다. 0 이면 페이지 거부(`denied`), 0 보다 크면 섹션 거부(`partial` — 사진을 남긴다). 대시보드 카드(`-card-`)는 옛 판정 그대로 항상 `partial`.

**이유**:
1. 이름으로는 못 가른다 — 페이지 거부(`product-forbidden`)와 섹션 거부(`settlements-accruals-forbidden`)가 **같은 접미사 · 같은 `role="status"` · 같은 클래스**다. `-projection-` 을 더하는 목록은 Failure Scenario 1 그대로 `settlements-*` · `ledger` 패널에서 또 샌다.
2. 페이지 거부 화면은 콘솔 전체에서 **«제목 + 거부 + 되돌아가는 링크»** 한 모양이라 그것을 지우면 정확히 0 이 남고, 섹션 거부 화면엔 형제 섹션의 표·안내 문구가 남는다 ⇒ 콘솔 마커를 **안 바꾸고** 스크립트만으로 가른다(Scope «제외» 충족 — 소유자 질문 불필요).
3. 틀리는 방향이 안전하다 — 거부 화면에 새 설명 문단이 붙으면 남는 글자가 생겨 `partial` 로 **사진을 남기고 사람이 연다**(일이 늘어나는 쪽). 반대(쓸 수 있는 화면을 버림)는 형제 섹션이 **제목·링크·폼뿐**일 때만 생기고, 그것은 Edge Case 1 이 «사실상 페이지 거부» 로 정한 모양이다.
- Edge Case 3(섹션 거부 + 나머지 «불러올 수 없음»): degraded 안내 문구는 본문으로 남으므로 `partial` — 사진이 남고, 캡처 뒤 본문 정규식이 `degraded` 도 단다(`DEGRADED_RE`). 둘 다 표시 = 큐레이션 후보 아님.
- 요약 출력 `[카드]` → `[일부]`(카드·섹션) 로 이름만 바꿨다.

## AC-2 — self-test bite

`node scripts/capture-portfolio.mjs --self-test` — **11/11 (거부 6 · 비거부 5), rc=0**. 기준선(수정 전 main)은 6/6. 새 칸:

| 칸 | 모양 | 기대 |
|---|---|---|
| ① `section-forbidden-beside-table` | 설정 표 + 프로젝션만 거부, `<main>` 밖 사이드바 | `denied=false · partial=1` |
| ② `section-forbidden-beside-degraded` | 2026-09-17 창의 실제 조합(위 degraded · 아래 forbidden) | `denied=false · partial=1` |
| ③ `every-section-forbidden` | 섹션 둘 다 거부 (Edge Case 1) | `denied=true` |
| ④ `page-forbidden-in-shell` | 헤더·사이드바 셸 안의 `product-forbidden` + «목록으로» | `denied=true` |
| ⑤ `filter-form-and-forbidden-only` | 필터 폼 + 거부뿐 | `denied=true` |

**bite — 두 방향** (복제본 `scripts/_bite702{a,b}.mjs` 를 만들어 돌리고 지웠다. 주입 문자열이 안 맞으면 복제 단계가 던지게 했다):
- 판정을 옛 규칙으로(`pageDenied = others.length > 0`) → **9/11, rc=1**, ✗ ① ② (둘 다 `denied=true`). = AC-2 «되돌리면 ① 이 빨개진다».
- «본문이 아닌 것» 에서 제목 · 링크/버튼/내비 제거를 뺌 → **8/11, rc=1**, ✗ ③ ④ ⑤ (전부 `partial` 로 샘). = 반대 방향 — 페이지 거부를 섹션 거부로 오판하면 빨개진다.
- 🔴 이 픽스처는 **술어**를 잰다. 실제 콘솔 트리가 그 모양인지는 AC-3.

## AC-3 — ⏳ 창 대기

다음 창에서 `/wms/operations`(테넌트 `demo-corp`)가 `ok` + `partialDenied` 로 사진이 남는지, `/tenants` · `/ecommerce/products/[id]`(테넌트 `ecommerce`)가 여전히 `denied` 인지 본다. 이 세션에 창이 없으면 ⚪ + `TASK-MONO-672`.

---

# 🔵 창 실측 — 2026-09-17 UTC 둘째 창(시작 2026-09-17T16:34:55Z · 종료 17:21:02Z · 46분) · AMI `ami-02613b0378621b124`(RepoCommit `af0018aa6`, 12차 — 구조된 굽기, provenance operator-record) · 인스턴스 `i-07ddb6b41233f2673` · 묶음 `console console-ecommerce console-wms console-scm store fan` · 소유자 승인 «af0018aa6, 상한 100분»

`node scripts/capture-portfolio.mjs --app console`(이 PR 이 들어간 판) — 테넌트 `demo-corp`: 계획 67 · 찍음 55 · 실패 12.

| 경로 | 결과 | 판정 |
|---|---|---|
| `/wms/operations` | 🟢 `ok` · `partialDenied: [wms-operations-projection-forbidden]` · `degraded: true` — 이미지를 열어 확인(위 «운영 설정» = 일시적으로 불러올 수 없음, 아래 «프로젝션 상태»만 «권한 없음») | 섹션 거부가 **사진으로 남았다** · Edge Case 3(거부+저하 둘 다 표시) 그대로 |
| `/tenants` | `denied 200` | 🟢 페이지 거부 유지 |
| `/partnerships` | `denied 200` | 🟢 페이지 거부 유지 |
| `/ecommerce/products/[id]` | 🔵 **더 이상 거부가 아니다** — `TASK-MONO-703` 이 같은 AMI 로 고쳤다(그 티켓 § 창 실측). 그래서 이 칸의 «페이지 거부 대조군» 역할은 `/tenants` · `/partnerships` 가 대신한다 |

- 🔵 곁관찰(설계대로): `/wms/operations` 는 제목 아래 **설명 문단**이 있어 두 섹션이 모두 거부돼도 남는 글자가 생긴다 ⇒ `partial`(사진 남김) 로 판정될 것이다 — 틀리는 방향이 «사람이 연다» 쪽이라 AC-1 이유 3 이 수용한 모양이다.
- 🔴 테넌트 `ecommerce` 로 같은 스크립트를 돌린 실행은 **사전 점검에서 앱 전체를 건너뛰었다**(`/dashboards/overview` 가 «테넌트를 선택» 을 그림) — 스크립트의 `selectTenant` 가 **편도 전환**이라 생긴 일이다(왕복 전환이 필요하다는 기존 함정). 이 티켓의 술어와 무관 — 648 에 기록.

---

# ✅ 닫음 — 2026-09-17 UTC (4차원 검증)

- (a) impl PR [#3900](https://github.com/kanggle/monorepo-lab/pull/3900) `state=MERGED` 2026-09-17T14:09:54Z
- (b) squash `36519302f` 가 `origin/main` 조상(rc=0)
- (c) 머지된 PR `statusCheckRollup` — SUCCESS 6 · SKIPPED 56 · **FAILURE 0** (main CI · Nightly E2E 도 success)
- (d) AC-0~AC-3 본문을 열어 읽음 — 전부 `[x]`. AC-3 «사진으로 남고 partialDenied 로 표시 · 페이지 거부는 여전히 denied» 는 위 표로 닫힘.
