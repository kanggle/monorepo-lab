# Task ID

TASK-PC-FE-341

# Title

nightly 콘솔 e2e `sample-visitor-transition.spec.ts:19` 가 `TASK-MONO-773` S6 머지와 함께 처음 flaky — 개요가 «일시적으로 불러올 수 없습니다» 로 저하(문서 10 s)

# Status

in-progress (2026-10-18 UTC — AC-0 측정 완료 · AC-1 «미확정» · 소유자 결정 «계측 먼저» · AC-2 열림)

# Owner

platform-console

# Task Tags

- platform-console
- e2e
- flaky

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet(측정 · 판정) — 원인이 SSR fetch 경합으로 확정되면 수선 설계는 Opus 검토.

---

# Dependency Markers

- 출처: `TASK-MONO-773` § 닫기 (2026-10-15 UTC) § 4 «열린 관측» — 773 S6(#4374, `712629cac`) 머지 뒤 첫 nightly.
- 관련: `TASK-MONO-773` S6 의 새 spec `tests/e2e/non-operator-tenant-creation.spec.ts`(3 칸) · `TASK-PC-FE-282` AC-14(이 spec 의 출처) · `ADR-MONO-074`(샘플 방문자).
- 선행 없음. 🔵 AC-0 은 **다음 nightly 런들이 쌓여야** 판정이 난다 — 착수는 언제든 가능하지만 재발 측정에 최소 런 수가 필요하다(AC-0).

# Goal

`nightly-e2e.yml` 콘솔 잡에서 `sample-visitor-transition.spec.ts:19` «anonymous sees samples; after login the same URL carries zero sample strings» 가 처음으로 flaky 가 됐다. 지금은 재시도가 덮어 잡이 초록이지만(`retries: 2` in CI), 재시도가 덮는 동안 **개요 화면이 실제로 저하된다**는 신호다 — 원인이 테스트 간 경합이면 테스트 배치 문제고, 원인이 SSR 개요 fetch 의 지연이면 제품 문제다. 어느 쪽인지 잰다.

## 증거 (2026-10-15 UTC 측정 — `TASK-MONO-773` 닫기 세션)

| 항목 | 값 |
|---|---|
| 런 | run **38763381565**(`push` of `712629cac`, 2026-10-15T15:47Z) — 결론 success |
| 잡 | **116520791861** «Platform Console E2E full-stack (Playwright + docker compose)» — `Running 11 tests` · `10 passed (59.1s)` · `1 flaky` |
| 첫 시도 실패 | `getByTestId('operator-overview-cards')` 5 s 안에 못 찾음 · 페이지 스냅샷 = «통합 개요를 일시적으로 불러올 수 없습니다»(개요 저하) · 문서 `/dashboards/overview` **10063 ms** |
| 재시도 | 통과 |
| 앞선 nightly 다섯 | 38745574967 · 38727544958 · 38704767456 · 38645649227 · 38642293093 — 모두 `8 passed` · flaky **0** |
| 바뀐 것 | 773 S6 이 spec 1 개 · 칸 3 개를 더했다(8 → 11 칸) · `fullyParallel: true`(`playwright.config.ts:26`) · 워커 2 |

🔴 **원인 미확정.** 가설(재지 않음): SSR 개요 fetch 가, 병렬 워커에서 같은 시각에 도는 비운영자 흐름(실제 OIDC 로그인 · 테넌트 생성 · 운영자 토큰 재교환 · assume)과 백엔드(admin-service · auth-service · console-bff)를 두고 경합해 10 s 를 넘겼다.

⚪ 위 증거는 **어느 `operator-overview-cards` 대기**(익명 단계 `:26` / 로그인 뒤 `:35`)에서 실패했는지를 적지 않았다. 둘은 뜻이 다르다 — 익명 샘플 경로는 합성 데이터라 BFF 를 부르지 않아야 하므로, 익명 단계에서 «불러올 수 없습니다» 가 떴다면 그것만으로 별개 결함 후보다(`sample-fetch-allowlist` 가 지키는 성질). AC-0 이 먼저 고정한다.

# Scope

## In Scope

- AC-0 측정: 재발 빈도 · 실패 단계(`:26` / `:35`) · 동시 실행 상관 · 개요 저하의 서버 쪽 원인(어느 BFF 다리가 늦었나 — console-web/console-bff 로그).
- 판정에 따른 수선 하나: (가) 테스트 배치(예: 이 spec 과 773 spec 을 같은 워커에 두지 않음 · 대기 시간 조정 — 단 «타임아웃만 늘리기» 는 원인이 테스트 경합으로 확정됐을 때만) 또는 (나) 제품 수선(개요 SSR fetch 의 타임아웃 · 저하 경로) — (나)면 범위가 커지므로 별도 티켓으로 떼는 것을 먼저 검토.

## Out of Scope

- 773 의 새 spec 자체를 바꾸는 것(그 3 칸은 첫 시도에 통과했다) — 상관이 확정되어 배치를 바꿀 때만 손댄다.
- `retries` 값 변경으로 flaky 를 숨기는 것.

# Acceptance Criteria

- [x] **AC-0** (2026-10-18 UTC — 아래 § AC-0 측정) — 착수 시 재측정(결정 아님): 이 티켓 기안 뒤 **연속 nightly 최소 5 런**(또는 착수 시점까지 쌓인 전부)에서 콘솔 잡의 `N passed` · `flaky` 수와 flaky 칸 이름을 표로 적는다. 각 flaky 에 대해 ① 실패 단계(`:26` 익명 / `:35` 로그인 뒤) ② 같은 시각에 다른 워커가 돌던 spec(Playwright 보고서의 시작 · 종료 시각) ③ 문서 응답 시간 ④ console-web 서버 로그의 개요 저하 사유(어느 다리). 0 런 재발이면 그 사실과 런 목록을 적고 «재발 없음 — 관찰 종료» 를 소유자에게 제안한다.
- [~] **AC-1** (2026-10-18 UTC — **«미확정»**, 근거 행 · 소유자 결정은 아래 § AC-1 판정) — AC-0 의 표로 원인을 하나로 좁힌다: (가) 동시 실행 상관(이 spec 실패가 773 spec 과 겹친 런에만) / (나) 겹침과 무관한 개요 SSR 지연 / (다) 익명 단계 저하(샘플 경로가 BFF 를 부름). 근거 없이 고르지 않는다 — 좁혀지지 않으면 «미확정» 으로 적고 추가 계측(개요 다리별 시간 로그)을 제안한다.
- [ ] **AC-2** — 판정에 맞는 수선 + 대조군: 수선 뒤 연속 nightly 5 런 flaky 0, 그리고 773 spec 3 칸 · 이 spec 이 모두 첫 시도 통과. (다)면 단위 시험으로 «익명 샘플 렌더의 BFF fetch 0» 을 먼저 빨갛게 재현한다.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` (셸 · 개요 SSR · 샘플 방문자)
- `docs/adr/ADR-MONO-074-anonymous-visitors-see-the-real-console-with-sample-data.md` (샘플 방문자 — A10)
- `.github/workflows/nightly-e2e.yml` 잡 `platform-console-e2e-fullstack`
- `projects/platform-console/apps/console-web/playwright.config.ts`

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.6(셸 가드 · 비운영자 상태) — 변경 없음 예상, 수선이 개요 저하 문구 · 경로를 바꾸면 해당 절 확인

# Edge Cases

- 재시도가 통과하므로 잡 결론은 success — 런 결론만 보면 영원히 안 보인다. flaky 수는 잡 로그의 `N flaky` 줄에서 센다(런 결론 · 체크 상태로 세지 않는다).
- 773 AC-2 칸은 생성 직후 운영자가 되므로 CI 재시도 때 다른 착지로 간다(spec 주석) — 그 재시도 경로가 개요(`/dashboards`)를 함께 두드려 경합을 키울 수 있다.
- 개요 저하는 설계상 있는 상태(데모에서도 BFF 공개 호스트가 없으면 저하) — «저하 문구가 떴다» 만으로 결함이 아니다. 문제는 **full-stack e2e 안에서** 10 s 가 걸렸다는 것.

# Failure Scenarios

1. 타임아웃을 늘려 초록으로 만든다 — 원인이 제품 쪽 지연이면 사용자가 겪는 10 s 를 숨긴다.
2. «flake = 인프라» 로 닫는다 — 앞선 다섯 런 0 → S6 직후 1 은 변화와 시점이 맞물린 신호다. 가설은 측정으로만 판정한다.
3. 한 런의 상관을 성질로 승격한다 — AC-0 의 최소 런 수 전에는 «겹쳤다» 를 원인으로 적지 않는다.

---

# 증거 추가 (2026-10-15 UTC — `TASK-MONO-773` 닫기 재개)

| 항목 | 값 |
|---|---|
| 런 | run **38782478516**(`push` of `5dd4aeb8e`, 773 AC-2 시험 PR #4376 머지 커밋) — 결론 success |
| 잡 | **116586647094** 콘솔 잡 — `Running 12 tests using 2 workers` · `12 passed (50.4s)` · flaky **0** |
| 비교 | 최초 관측 run 38763381565 는 `11 tests · 10 passed · 1 flaky`(flaky = `sample-visitor-transition.spec.ts:19`). 이 런은 칸이 12 개(773 spec 4 칸)로 늘었는데도 flaky 0 |

🔵 한 점일 뿐이다 — AC-0 의 «연속 최소 5 런» 표에 넣을 두 번째 데이터이며, 재발 없음의 근거로 승격하지 않는다. 이 티켓은 닫지 않는다.

## 증거 추가 (2026-10-16 UTC) — 같은 종류로 보이는 세 번째 사례

- `nightly-e2e` 브랜치 실행 run 38856950242 attempt 1(TASK-MONO-794 브랜치) · 콘솔 job 116830663748: `operators-profile.spec.ts:44` Finance 카드 `degraded`(기대 `ok`). 같은 순간 콘솔 로그 `console_composition_leg` 가 `operator-overview` finance `degraded reason=TIMEOUT latencyMs=4002` · `domain-health` finance `degraded TIMEOUT 4000` — 인가 거절(`forbidden PERMISSION_DENIED`)이 아니라 **4 s 조합 시간 초과**. attempt 2(job 116835225745) 12 passed.
- 🔵 run 38763381565 의 sample-visitor 사례(문서 응답 10 s · 개요 «일시적으로 불러올 수 없습니다»)와 같은 «개요 조합 다리 시간 초과» 가설 — 다른 spec · 다른 도메인에서 같은 증상. 원인 확정 아님(AC-0 이 잰다). 794 가 원인이 아니라는 것도 확정 아님 — 다음 main nightly 들에서 Finance `TIMEOUT` 이 반복되는지 함께 본다.

## 증거 추가 (2026-10-16 UTC) — 네 번째 사례: `overview-consolidation.spec.ts:133` 첫 시도 실패

- `nightly-e2e` 브랜치 실행 run **38965748396**(`TASK-PC-FE-343` 브랜치, 커밋 `a21b54c03`) · 콘솔 job **117192025297**: `11 passed · 1 flaky`. flaky = `overview-consolidation.spec.ts:133` «IAM card on the home overview drills down to the IAM detail» — 첫 시도 `getByTestId('operator-overview-card-iam')` **element(s) not found**(`toHaveAttribute('data-status','ok')`, 5 s) → 재시도 통과.
- 같은 커밋의 두 번째 run **38967164204** · job **117196733812**: `12 passed · 0 flaky`. 342 · 345 브랜치 nightly(각 12/0)에서는 첫 시도 통과.
- 🔵 사유 문구가 앞 사례와 **다르다** — 문서 10 s TIMEOUT 이 아니라 개요 IAM 카드 미렌더(5 s). 같은 «개요 첫 렌더 지연» 계열로 보이나 확정 아님.
- 🔴 가설(미확정): `TASK-PC-FE-343` 이 알림 종 route 에 row 12(`/me/domain-access`) 호출 1 회를 더했다 — 개요 SSR 과 겹칠 수 있다. 표본 3 개로는 확인도 배제도 못 했다. AC-0 의 연속 런 표에 `:133` 칸을 함께 둔다.

---

# AC-0 측정 (2026-10-18 UTC)

읽기 전용. 범위: `nightly-e2e.yml` 전 런 · 2026-10-15T00:00Z ~ 2026-10-18T10:22Z, `gh run list --limit 100` 로 모은 **56 런-시도**(구간 최초 런 38704767456) × 콘솔 잡 «Platform Console E2E full-stack (Playwright + docker compose)» 1 개씩. 로그 56/56 확보. **56 행 전체 표는 PR 설명에 있다**(여기엔 flaky · failed 행과 집계만).

도구 한계 둘: (a) CI 리포터가 `dot` 형식이라 잡 로그에 테스트별 시작/종료 줄이 없다 → ② 는 업로드된 trace.zip 의 시계로 복원(flaky 4 런만; 값은 «컨텍스트 열림~마지막 이벤트» 라 실제보다 약간 넓다). (b) `console_composition_leg` 는 **잡이 실패했을 때만** 덤프된다 → 초록(flaky 로 덮인) 잡엔 ④ 자료가 없다.

## flaky · failed 칸 (① 실패 단계 · ② 동시 워커 · ③ 문서 응답 · ④ 서버 로그)

| 런 | 칸 | ① | ② | ③ | ④ |
|---|---|---|---|---|---|
| **38763381565** main push `712629cac` (773 S6) · job 116520791861 | `sample-visitor-transition.spec.ts:19` | **`:26` 익명 단계**. `operator-overview-cards` 5 s 안에 못 찾음; 스냅샷 = 샘플 카드 5 장 **과 함께** «통합 개요를 일시적으로 불러올 수 없습니다» | `back-link` 와 첫 3.9 s 만 겹침, 이후 ~11 s 는 trace 상 다른 spec 없음 | 첫 시도 익명 `GET /dashboards/overview` **10063 ms**; 재시도 익명 76 ms · 로그인 뒤 63 ms | ⚪ 판정 불가(초록 잡, 덤프 없음) |
| **38776096504** main push `a01005367` · job 116564931532 | `overview-consolidation.spec.ts:128` IAM 카드 | `operator-overview-card-iam` `data-status="ok"` 5 s 실패(로그인 뒤) | 첫 시도만 `back-link`/`sample-visitor` 와 겹침; 재시도 단독 | 첫 시도 10172 ms · 재시도(단독) 13957 ms | ⚪ |
| **38892569380** 브랜치 be-638 `3c807a9a9` · job 116946011408 | `overview-consolidation.spec.ts:131` IAM 카드 | 동일; **재시도 1 도 실패, 2 에서 통과** | 첫 시도만 겹침; 재시도 1·2 단독 | 10165 · 10513(단독, 실패) · 4113(단독, 통과) ms | ⚪ |
| **38965748396** 브랜치 pc-fe-343 `a21b54c03` · job 117192025297 | `overview-consolidation.spec.ts:133` IAM 카드 | 동일, 재시도 1 통과 | 첫 시도만 겹침; 재시도 단독 | 10285 · 13202(단독) ms | ⚪ |
| **38965272467** main schedule `d64a6b8a7` · job 117190548510 (**잡 failure**) | `operators-profile.spec.ts:44` (:75 Finance 카드 `degraded`) | 3 시도 모두 실패 | ⚪ 미복원 | ⚪ 미측정 | **있음**: Finance `TIMEOUT latencyMs 4000/4002` 1 쌍(런 전체 Finance TIMEOUT 은 이것뿐) |
| **38856950242** 브랜치 mono-794 attempt 1 · job 116830663748 (**잡 failure**, attempt 2 = 12 passed) | 동일 | 동일 | ⚪ | ⚪ | **있음**: 같은 모양 Finance `TIMEOUT 4000/4002` 1 쌍 |
| 38777797324 브랜치 mono-773-ac2 `cda01f129` · job 116570399831 (**잡 failure**) | `non-operator-tenant-creation.spec.ts:137` | AC-2 roles 단언 — 개요와 무관한 결정적 실패(개발 중), 이 티켓 범위 밖 | — | — | — |

## 집계

- main 연속 런(push + schedule) **34 런**, 2026-10-15T08:18Z(`bf2d0236f`) → 2026-10-18T08:32Z(`4d96f4457`): 773 S6 전 3 런 flaky 0 · S6 첫 런 38763381565 flaky 1 (sample-visitor) · S6 이후 30 런 flaky 1 (38776096504, IAM 칸) + 잡 실패 1 (38965272467). main 합계 **flaky 런 2 · 잡 실패 런 1**.
- 브랜치 런(workflow_dispatch) 22 런-시도: flaky 2 (38892569380 · 38965748396, 둘 다 IAM 칸) · 잡 실패 2 런-시도 (38777797324 = 773 개발 중 결정적 실패, 38856950242 attempt 1 = Finance TIMEOUT). 나머지 18 은 12/0. 이 구간 칸 수: 8 → 11 (S6) → 12.
- `sample-visitor-transition.spec.ts:19` flaky: main **1 회**(34 런 중), 그 뒤 main 30 런 · 브랜치 22 런 **재발 0**(= 52 런-시도). IAM 카드 칸(`overview-consolidation` «IAM card … drills down») flaky: main 1 + 브랜치 2 = **3 회**.
- 연속 최소 5 런 요건 충족. 단 sample-visitor 사건 자체는 **1 건**.
- 구조(실패 3 잡의 compose 로그 집계): e2e 스택에서 E-Commerce 다리는 늘 `degraded TIMEOUT ≈4000 ms`, WMS · SCM · ERP 는 즉시 `DOWNSTREAM_ERROR` ⇒ 인증된 개요 문서는 구조적으로 ≥ 4.0 s (5 s 대기와 여유 < 1 s). 익명 개요 문서는 정상일 때 45~76 ms (8/8). 10 s 대 문서(IAM 3 건 + sample-visitor 1 건)에서 4 s 바닥 위의 ~6 s 가 무엇인지는 다리별 시간 로그가 없어 **판정 불가**.

# AC-1 판정 (2026-10-18 UTC)

**«미확정»** — 하나로 좁혀지지 않았다.

| 가설 | 판정 | 근거 행 |
|---|---|---|
| (가) 동시 실행 상관 | IAM 칸: **기각** · sample-visitor: 결정 불가 | IAM 3 건 모두 **재시도를 단독으로 돌려도** 10.5 · 13.2 · 14.0 s (38892569380 · 38965748396 · 38776096504). sample-visitor 는 10 s 중 겹침 3.9 s, 나머지 ~6 s 는 다른 spec 없음; 표본 1 |
| (나) 겹침과 무관한 개요 SSR 지연 | 부분 지지, **원인 다리 미확정** | 위 IAM 3 건 + Finance `TIMEOUT 4000` 이 `operators-profile:44` 시점에 1 쌍 (38965272467 · 38856950242). 6 s 이상 추가분의 출처는 로그에 없음 |
| (다) 익명 단계 저하 | 지지(**단일 사례, 성질 승격 불가**) | 38763381565 `:26`, 샘플 카드 + 저하 문구, 익명 문서 10063 ms (통과 시도는 45~76 ms). 재발 0 |

**소유자 결정 (2026-10-18 UTC): «계측 먼저».** 수선 없이 계측만 추가하고, 이 티켓은 연다. 계측이 쌓인 nightly **5 런 이상**으로 재측정한 뒤 AC-1 을 다시 판정한다. 계측 3 건 (같은 PR):

1. `console-web` 서버: `console_ssr_phase` 로그 — `/dashboards/overview` 렌더의 단계별 시간 (layout: registry · me_roles · me_domain_access · org_nodes · demo_stop_check · layout_total; page: overview_state · registry · domain_health · page_total). 필드 `route · phase · status · latencyMs · startedAt` (+ 오류 시 `httpStatus · errorName`), 토큰 · 계정 id · 본문 없음.
2. Playwright CI 리포터에 `json` 추가 → 아티팩트 `test-results/e2e-results.json` 에 시도별 `startTime · duration · workerIndex · retry` (② 를 로그가 아니라 파일에서).
3. `TASK-MONO-799` — nightly 콘솔 잡이 compose 로그를 **매 런** `test-results/compose-logs.txt` 로 업로드 (④ 를 초록 잡에서도).

# 소유자 제안 (AC-0 규정)

`sample-visitor-transition.spec.ts:19` 자체는 main 30 런 · 브랜치 22 런(52 런-시도)에서 **재발 0** — 이 칸에 한해서는 «재발 없음 — 관찰 종료» 를 제안할 근거가 있다. 그러나 같은 «개요 첫 렌더 지연» 계열이 IAM 카드 칸(3×)과 Finance 카드로 계속 나타나며 **지금의 주 재발은 IAM 카드 칸**이므로, 이 티켓 전체를 닫는 근거는 아니다. **티켓은 닫지 않는다.**

# AC-2 (열림)

계측이 쌓인 뒤 재측정 → 판정 → 판정에 맞는 수선 + 대조군. 지금은 시작하지 않는다.

## 계측 실동작 확인 (2026-10-18 UTC)

브랜치 nightly run **39128709878**(PR #4410) 콘솔 잡 초록(12 expected · 0 flaky). 아티팩트 `platform-console-playwright-report-nightly` 에서 확인한 것:

- `test-results/e2e-results.json`: 28 KB. 시도마다 `workerIndex` · `retry` · `startTime` · `duration` 이 남는다(② 동시 실행을 이 파일로 판정한다).
- `test-results/compose-logs.txt`: 812 KB. 매 줄에 docker 타임스탬프가 붙는다(④).
- `console_ssr_phase` 151 줄이 찍혔다. route 는 `(console)/layout` · `/dashboards/overview` 둘이다.

재측정은 이 PR 머지 뒤 nightly 부터 센다. 이 런은 계측 동작 확인용이고, 표본으로는 세지 않는다.
