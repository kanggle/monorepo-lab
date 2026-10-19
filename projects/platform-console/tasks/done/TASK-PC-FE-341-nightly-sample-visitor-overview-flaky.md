# Task ID

TASK-PC-FE-341

# Title

nightly 콘솔 e2e `sample-visitor-transition.spec.ts:19` 가 `TASK-MONO-773` S6 머지와 함께 처음 flaky — 개요가 «일시적으로 불러올 수 없습니다» 로 저하(문서 10 s)

# Status

done

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
- [x] **AC-1** (2026-10-19 UTC — **판정: e2e 설정 누락 — 아래 § 판정 (조작 실험)**. 2026-10-18 의 «미확정» 기록은 § AC-1 판정에 그대로 둔다) — AC-0 의 표로 원인을 하나로 좁힌다: (가) 동시 실행 상관(이 spec 실패가 773 spec 과 겹친 런에만) / (나) 겹침과 무관한 개요 SSR 지연 / (다) 익명 단계 저하(샘플 경로가 BFF 를 부름). 근거 없이 고르지 않는다 — 좁혀지지 않으면 «미확정» 으로 적고 추가 계측(개요 다리별 시간 로그)을 제안한다.
- [x] **AC-2** — 판정에 맞는 수선 + 대조군: 수선 뒤 연속 nightly 5 런 flaky 0, 그리고 773 spec 3 칸 · 이 spec 이 모두 첫 시도 통과. (다)면 단위 시험으로 «익명 샘플 렌더의 BFF fetch 0» 을 먼저 빨갛게 재현한다.
  - 🔵 수선 = `docker-compose.e2e.yml` console-web 에 `ECOMMERCE_ADMIN_BASE_URL` · `ECOMMERCE_PUBLIC_BASE_URL` = `127.0.0.1:9`(다른 도메인과 같은 non-routable) + 가드 시험 `e2e-compose-composition-bases.test.ts`. 대조군 = 실험 3 런(아래 표). ⏳ 남은 것 = 머지 뒤 **main** nightly 연속 5 런 flaky 0 · 773 spec · 이 spec 첫 시도 통과 — 그때 체크.
  - ✅ 2026-10-19 UTC — 수선을 실은 nightly **연속 6 런**(14:51Z 이후 nightly 전부, 수선 없는 런 끼어들지 않음): 39137849403 · 39138716016 · 39139604078(실험 브랜치) · 39141931002(수선 PR 브랜치) · 39142846794(main push `b1c87eaa6`) · 39163307647(main schedule). 6/6 모두 `e2e-results.json` 에서 12 expected · flaky 0 · unexpected 0 · 재시도 0, 773 spec 4 칸 + 이 spec 1 칸이 첫 시도 통과, `*_bff_unavailable` 0. 런 결론 failure 는 6 개 모두 «AMI generation» 잡 하나(재굽기 대기 — 콘솔 잡 아님). 🔵 위 줄의 «main nightly 연속 5 런» 은 AC 원문(«nightly 5 런»)보다 엄격하게 적은 내 해석이었다 — 소유자 결정(2026-10-19 UTC «지금 닫는다»)으로 원문대로 닫는다. main 은 6 중 2 런이다.

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

# 재측정 — 중간 기록 (2026-10-18 UTC, 판정 아님)

🔴 **표본이 모자란다.** 계측 머지(`b74e05ab7`) 뒤 끝난 main nightly 는 **1 런**(39130456238)이다. AC-2 기준은 5 런이므로, 아래는 판정이 아니라 다음 측정이 무엇을 잴지 정하는 기록이다. 브랜치 런 39128709878 은 계측 동작 확인용이라 표본에서 뺐지만, 같은 사건이 나왔으므로 «사건 목록» 에는 함께 적는다. 분석은 아티팩트 `platform-console-playwright-report-nightly` 의 `test-results/e2e-results.json` + `compose-logs.txt` 두 파일로 했다. 스크립트는 세션 scratchpad 에만 있다(임시 — 사라질 수 있다). 재현: console-web JSON 줄을 `msg` 로 고르고, `console_ssr_phase` 는 `startedAt` 으로, `console_composition_leg` 는 `requestId` 로 묶는다.

## 기준선 (main 39130456238 — 12 expected · **0 flaky**)

| 단계 | n | p50 | p90 | max |
|---|---|---|---|---|
| `/dashboards/overview` `page_total` | 14 | 4063 | 7423 | **10099** |
| `overview_state` | 14 | 4063 | 7422 | 10099 |
| `domain_health` | 14 | 4014 | 4025 | 7370 |
| `(console)/layout` `layout_total` | 26 | 37 | 487 | 705 |
| layout `me_roles` · `me_domain_access` · `registry` · `org_nodes` | — | 28 · 35 · 54 · 244 | — | 207 · 112 · 130 · 642 |

- 레이아웃은 1 s 미만이다. 개요 지연은 전부 `overview_state` · `domain_health` 쪽에 있다.
- 조합 다리: E-Commerce 는 `domain-health` 11/11 · `operator-overview` 9/9 가 `TIMEOUT` 이고 p50 4000 ms 다. WMS · SCM · ERP 는 즉시 `DOWNSTREAM_ERROR`(≤8 ms)다. ⇒ 인증 개요의 바닥 4 s 를 다시 확인했다.
- 🔴 **flaky 0 인 초록 런에서도 개요 렌더가 7.4 s · 10.1 s 까지 간다.** 10 s 렌더는 flaky 런에만 있는 사건이 아니다. 시험이 그 렌더를 기다렸느냐에 따라 flaky 가 될 뿐이다.

## 사건: «~10 s 뒤 내부 fetch 가 `transport:TypeError`, 합성 다리 0»

| 런 | 렌더 | `overview_state` | 끝난 로그 | 그 렌더의 `operator-overview` 다리 | 그때 돌던 칸 |
|---|---|---|---|---|---|
| 39130456238 (main) | 13:19:40.108 → 50.207 | **10099** | `operator_overview_bff_unavailable cause=transport:TypeError` (50.207) | **0** — 그 구간에 시작한 묶음은 `ed74f7f3` 하나이고, 45.392 에 시작한 다른 렌더의 것이다(requestId 대조) | w0 `overview-consolidation.spec.ts:55` back-link(FE-068, 13:19:27.7–46.4, 이 런에서 가장 긴 18.7 s) · w1 `:133` 끝(→40.3) 다음 `sample-visitor-transition.spec.ts:19`(40.3–45.7) |
| 39128709878 (브랜치, 계측 확인용) | → 12:56:38.610 | **10329** | 같음 | (같은 방법으로 미대조) | w0 `:55`(12:56:14.4–38.7, 24.3 s) · w1 `sample-visitor:19`(24.9–31.6) → `:133` 등 |
| 38777797324 (AC-0, 잡 실패 덤프) | — | — | `operator_overview_bff_unavailable` + `domain_health_bff_unavailable` 둘 다 `transport:TypeError` (17:38:51.42) | — | — |

- 이 개요 내부 fetch(`getOperatorOverviewState` → `selfOrigin()` = e2e 에서 `http://localhost:3000`, 자기 자신)에는 **타임아웃이 없다.** 그런데도 ~10 s 에 `TypeError` 로 끝났다. undici 의 기본 connect timeout 이 10 s 라 숫자가 맞는다. 🔴 이것은 **가설이다** — `TypeError` 만으로는 connect timeout 인지 reset 인지 가를 수 없다.
- 원래 사례(run 38763381565: 문서 10063 ms + «통합 개요를 일시적으로 불러올 수 없습니다»)와 **숫자 · 문구 모양이 같다.** `bffUnavailable` 이면 개요가 그 문구로 저하된다.
- AC-0 의 IAM 카드 flaky 3 건은 첫 시도가 모두 back-link(`:55`)와 겹쳤다. 그때는 «단독 재시도도 10~14 s» 라서 (가)를 기각했다. 오늘 두 런은 같은 칸 근처에서 10 s 사건을 보인다. 🔴 그런데 두 런 모두 **다른 워커에서 sample-visitor:19 도 함께 돌고 있었다** — 사건을 한 칸에 귀속시킬 근거는 아직 없다. 겹침이 **필요조건**인지, 어느 칸이 **유발**하는지는 모른다(표본 2).
- (다)(익명 단계) 재검토: 두 런에서 익명 렌더(`overview_state` 11~50 ms)는 모두 빨랐다. 10 s 사건은 인증된 렌더였다.

## 이 기록과 함께 넣은 계측 1 개

`shared/api/unavailable-cause.ts` 의 `transport` 분류가 undici `err.cause.code` 를 싣는다(오류 코드 모양만 — `[A-Z][A-Z0-9_]{1,39}`, 메시지 · 주소는 싣지 않는다). 로그 형식은 `cause=transport:TypeError/UND_ERR_CONNECT_TIMEOUT` 이다. 다음 런부터 connect timeout · reset · socket 을 가를 수 있다. 시험 `tests/unit/shared/unavailable-cause.test.ts`(빨강 2 → 초록, 대조군 3).

## 다음 측정

- main nightly 를 5 런까지 모으고, 런마다 다음을 센다.
  - `*_bff_unavailable` 사건 수 · 그 `cause` 코드(새로 생긴 칸)
  - 사건 시각에 돌던 칸(`e2e-results.json`)
  - 사건 렌더의 `operator-overview` 다리 수(0 인가)
  - flaky 칸과 사건이 같은 렌더인가
- 판정 후보는 셋이다.
  - (나′) 자기 자신으로 가는 내부 fetch 의 연결이 간헐적으로 10 s 막힌다(제품 결함 후보).
  - (가′) back-link 칸의 탐색 취소 · 재요청이 그 막힘을 유발한다.
  - 둘 다 아니다.
- 🔴 원인이 확정되기 전에는 대기 시간 · 재시도 · 바닥(E-Commerce 4 s)을 바꾸지 않는다(Failure Scenario 1).

## 추가 (2026-10-18 UTC) — 새 칸의 첫 표본: `UND_ERR_CONNECT_TIMEOUT`

이 PR 의 브랜치 nightly **39134957346**(12 expected · 0 flaky)에서 사건 1 건이 나왔다.

- 로그: `operator_overview_bff_unavailable cause=transport:TypeError/**UND_ERR_CONNECT_TIMEOUT**` (14:19:50.587).
- 그 렌더: `overview_state` = `page_total` = **10316 ms**(14:19:40.271 시작). 이 런의 `page_total` 14 개 중 최댓값이고, 그다음은 7671 ms 다.
- 그때 돌던 칸: w0 `overview-consolidation.spec.ts:55` back-link(14:19:31.8, 18.9 s) · w1 `:133`(39.5, 4.5 s) → `sample-visitor:19`(44.1, 5.3 s).
- 렌더 구간에 시작한 `operator-overview` 다리 묶음은 1 개(`c725b48f`)다. 이 렌더의 것인지는 requestId 로 대조하지 않았다.

이 사실이 좁히는 것과 아직 모르는 것:

- ✅ 좁혀진 것: 이 한 건은 «응답이 늦다» 가 아니라 **TCP 연결을 맺지 못했다** 다. 개요 페이지 → `http://localhost:3000`(자기 자신)으로 가는 connect 가 undici 기본 10 s 를 다 썼다.
- ⚪ 모르는 것 (1): 앞 두 런(39130456238 · 39128709878)의 사건도 같은 코드였는지. 그 런들은 이 칸이 생기기 전이다.
- ⚪ 모르는 것 (2): **왜 자기 자신에게 연결이 안 되는가.** `localhost` 이름 해석(::1 / 127.0.0.1 — 콘솔은 `HOSTNAME=0.0.0.0` 으로 IPv4 에만 바인드된다), listen 백로그, 이벤트 루프 정체 등 후보만 있고 하나도 재지 않았다. 🔴 검증 가능한 기전이 원인은 아니다 — 다음 런들이 같은 코드를 내는지부터 센다.
- 사건 수: 계측 런 **3/3** 에서 런당 1 건이고, 3 건 모두 w0 의 `:55` 와 겹친다. 🔴 표본 3 은 성질이 아니다(Failure Scenario 3).

# 판정 (조작 실험, 2026-10-19 UTC)

**원인: e2e 스택에서 E-Commerce 주소만 설정이 빠져 코드 기본값 `http://ecommerce.local/...`(그 스택에서 풀리지 않음)로 갔다.** 다른 도메인(WMS · SCM · ERP)은 같은 블록 주석대로 «풀리지 않는 `*.local` 을 기다리지 않도록» `127.0.0.1:9` 로 막혀 있었다. E-Commerce 만 그 목록에서 빠져 있었다.

실험: 브랜치 `exp/pc-fe-341-ecommerce-nonroutable` 에서 **그 두 줄만** 넣고 nightly 를 차례로 3 회 돌렸다. 셈 술어는 사건이 있던 런 39134957346 에서 먼저 대조했다(사건 1 · 사유 `UND_ERR_CONNECT_TIMEOUT` · E-Commerce TIMEOUT 20/20).

| | 실험 전 (계측 런 3: 39128709878 · 39130456238 · 39134957346) | 실험 (39137849403 · 39138716016 · 39139604078) |
|---|---|---|
| `*_bff_unavailable` 사건 | 1 · 1 · 1 (확인된 사유 `UND_ERR_CONNECT_TIMEOUT`) | **0 · 0 · 0** |
| E-Commerce 조합 다리 | 전부 `TIMEOUT` ~4000 ms | 21/21 · 21/21 · 21/21 즉시 `DOWNSTREAM_ERROR` |
| 개요 `page_total` | p50 ~4.06 s · max 10.1~10.3 s | p50 0.18 · 0.20 · 0.18 s · max 0.50 · 0.63 · 0.42 s |
| back-link `:55` 칸 | 18.7 · 24.3 · 18.9 s | 2.2 · 2.4 · 2.0 s |
| 콘솔 e2e 전체 | ~40 s | 24.0 · 26.8 · 23.9 s |
| flaky | 0 · 0 · 0 | 0 · 0 · 0 |

- 바꾼 것이 하나뿐이고 사건이 1·1·1 → 0·0·0 이 됐다. 그래서 **이 설정 누락이 4 s 바닥과 10 s 사건 둘 다의 원인**이라고 판정한다. 런당 1 건 꼴이던 사건이 3 런 연속 0 일 확률은 우연으로는 약 5% 다.
- 🔴 **기전은 재지 않았다.** «취소할 수 없는 `getaddrinfo(ecommerce.local)` 가 libuv threadpool(기본 4)을 채워, 개요의 자기 자신 fetch 가 `localhost` 를 찾는 일이 줄을 서다 undici 10 s connect timeout 에 걸린다» 는 관측 전부와 맞는 **가장 그럴듯한 설명**일 뿐이다. 확인은 제품 쪽 티켓 `TASK-PC-FE-350` 의 AC-0 이다.
- 가설 정리: (가) 동시 실행 상관 — 사건이 back-link 칸과 겹친 것은 그 칸이 개요를 빠르게 여러 번 열어 E-Commerce 조회를 몰아서 냈기 때문으로 읽힌다(원인은 아니고 증폭기 후보). (나) 개요 SSR 지연 — 맞다. 다만 제품 코드가 아니라 e2e 설정이 만든 지연이다. (다) 익명 단계 — 기각. 계측 런의 익명 렌더는 모두 11~50 ms 였다.
- Failure Scenario 1(«타임아웃을 늘려 초록」) 대조: 이 수선은 대기 · 재시도 · 바닥을 손대지 않는다. 그 스택에 없는 도메인을 다른 셋과 같은 방식으로 막았을 뿐이다. 사용자에게 갈 수 있는 제품 쪽 위험(풀리지 않는 도메인 주소 하나가 콘솔 SSR 전체를 막을 수 있는가)은 `TASK-PC-FE-350` 으로 떼었다.

# 닫기 (2026-10-19 UTC) — in-progress → done

- (a) 구현 PR 셋 전부 `state=MERGED`: #4410 `b74e05ab7`(계측) · #4412 `79f74def4`(사유 코드 · 중간 기록) · #4413 `b1c87eaa6`(수선 · 가드).
- (b) 셋 다 `origin/main`(`b1c87eaa6`)의 조상.
- (c) 머지된 PR 의 `statusCheckRollup`(브랜치 nightly 제외) 실패 0: #4410 SUCCESS 42 · #4412 SUCCESS 12 · #4413 SUCCESS 10. 브랜치 nightly 의 «AMI generation» 실패는 PR 체크가 아니다.
- (d) AC 를 동사별로 확인했다.
  - AC-0 «재측정해 표로 적는다» ✅
  - AC-1 «하나로 좁힌다 · 근거 없이 고르지 않는다» ✅ — 조작 실험
  - AC-2 «수선 + 대조군 · 연속 5 런 flaky 0 · 첫 시도 통과» ✅ — 위 6 런
- 🔴 기전(threadpool 점유)은 이 티켓에서 재지 않았다 → `TASK-PC-FE-350`. 이 티켓이 확정한 것은 «e2e 의 E-Commerce 주소 누락이 원인» 까지다.
- `review/` 단계는 거치지 않고 바로 `done/`: 구현 PR 셋은 머지 시점에 검토됐다. 마지막 AC 는 머지 뒤 측정이었다.
