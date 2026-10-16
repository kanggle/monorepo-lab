# Task ID

TASK-PC-FE-341

# Title

nightly 콘솔 e2e `sample-visitor-transition.spec.ts:19` 가 `TASK-MONO-773` S6 머지와 함께 처음 flaky — 개요가 «일시적으로 불러올 수 없습니다» 로 저하(문서 10 s)

# Status

ready

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

- [ ] **AC-0** — 착수 시 재측정(결정 아님): 이 티켓 기안 뒤 **연속 nightly 최소 5 런**(또는 착수 시점까지 쌓인 전부)에서 콘솔 잡의 `N passed` · `flaky` 수와 flaky 칸 이름을 표로 적는다. 각 flaky 에 대해 ① 실패 단계(`:26` 익명 / `:35` 로그인 뒤) ② 같은 시각에 다른 워커가 돌던 spec(Playwright 보고서의 시작 · 종료 시각) ③ 문서 응답 시간 ④ console-web 서버 로그의 개요 저하 사유(어느 다리). 0 런 재발이면 그 사실과 런 목록을 적고 «재발 없음 — 관찰 종료» 를 소유자에게 제안한다.
- [ ] **AC-1** — AC-0 의 표로 원인을 하나로 좁힌다: (가) 동시 실행 상관(이 spec 실패가 773 spec 과 겹친 런에만) / (나) 겹침과 무관한 개요 SSR 지연 / (다) 익명 단계 저하(샘플 경로가 BFF 를 부름). 근거 없이 고르지 않는다 — 좁혀지지 않으면 «미확정» 으로 적고 추가 계측(개요 다리별 시간 로그)을 제안한다.
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
