# ADR-MONO-081 — 콘솔의 교차 도메인 합성을 **콘솔 서버**로: `console-bff` 은퇴

**Status:** ACCEPTED
**Date:** 2026-10-02
**주관 티켓:** `TASK-MONO-754`
**개정 대상:** [`ADR-MONO-017`](ADR-MONO-017-platform-console-bff-architecture.md) D1·D2(합성을 하는 **자리**) · [`ADR-MONO-043`](ADR-MONO-043-notification-architecture-unification.md) D2(알림 집계기의 **자리**) · [`ADR-MONO-013`](ADR-MONO-013-platform-console-foundation.md) § D5(`service_types` 에 `rest-api` 를 더한 근거)
**관련:** [`ADR-MONO-067`](ADR-MONO-067-demo-surfaces-served-from-vercel.md)(콘솔이 Vercel 로 감) · [`ADR-MONO-074`](ADR-MONO-074-anonymous-visitors-see-the-real-console-with-sample-data.md)(샘플 방문자) · [`ADR-MONO-079`](ADR-MONO-079-agencies-sellers-and-console-fan-management.md)(콘솔 팬 화면 — `TASK-MONO-751`)

> 🟢 **ACCEPTED — 갈래 A** (2026-10-02 UTC, 소유자 정확형 `ADR-MONO-081 ACCEPTED — A`). 이름 · `ACCEPTED` · **갈래 letter** 세 요건이 모두 도착했다.
> 🔴 **라이더는 공급되지 않았다** — R1~R3 은 구현자 기본값 그대로다(§ 라이더 대조). 단계 티켓은 이 ACCEPT PR 안에서 기안했다(§ ACCEPT 가 만든 새 의무).
---

## Context

### 무엇이 문제인가

콘솔 앱(`console-web`, Next.js)은 이미 **서버 컴포넌트와 서버 라우트**를 가진다. 그런데 교차 도메인 합성 세 가지만 별도 Spring 서비스 `console-bff` 를 한 번 더 거친다.
스토어(`web-store`)와 팬(`fan-platform-web`)에는 이런 별도 서비스가 없다 — 둘 다 Next 서버가 게이트웨이를 직접 부른다.

### 지금 어떻게 동작하나 (실측 — 코드, 2026-10-02)

| 무엇 | console-bff 를 지나나 | 근거 |
|---|---|---|
| 운영 개요 `GET /api/console/dashboards/operator-overview` | **지난다** | console-web `app/api/console/dashboards/operator-overview/route.ts` → `CONSOLE_BFF_URL` |
| 도메인 상태 `GET /api/console/dashboards/domain-health` | **지난다** | 같은 디렉터리 `domain-health/route.ts` |
| 알림 인박스 `GET /api/console/notifications/inbox` · 읽음 `POST …/{sourceDomain}/{id}/read` | **지난다** (쓰기 하나 포함) | `notifications/**/route.ts` · console-bff `NotificationAggregatorController` |
| 도메인 화면 6종(iam·wms·scm·finance·erp·ecommerce) | 안 지난다 — console-web → 도메인 게이트웨이 **직접** | `console-web/architecture.md` §§ 2.4.5–2.4.10 |
| 도메인 **안**의 개요 합성(IAM 개요, ecommerce 개요, wms/scm/finance/erp 개요) | 안 지난다 — **console-web 서버가 직접 fan-out** | 같은 문서 §303–309: «bff leg 신설은 무이득» |

- console-bff 규모: main Java 66 파일 · 테스트 22 파일 · 합 10,277 줄. 엔드포인트 **4개**.
- 알림 집계 도메인은 기본값 `["erp"]` **하나**다(`NotificationAggregatorProperties`).
- 합성은 **기존 조회 API 를 다시 묶을 뿐**이다(ADR-017 D3-A — 새 생산자 API 0).

### 🔴 Vercel 로 간 콘솔은 console-bff 에 **닿지 못한다**

`console-bff` 는 공개 호스트명이 없다 — `TASK-MONO-362` 가 그 Traefik 라우터를 일부러 없앴다(`platform/api-gateway-policy.md` L14: 백엔드 서비스는 엣지에 노출하지 않는다).
콘솔이 Vercel(`console.hubwang.com`)로 옮겨 간 뒤(ADR-067 단계 3) 위 세 화면은 데모에서 **항상** `bffUnavailable` / 502 상태다.
`infra/demo/console-vercel.override.yml` §«이 억제가 영구히 열화시키는 것» 이 이것을 적고, 고치는 길을 둘로 적었다 — *«BFF 에 공개 경로를 주거나(엣지 노출 금지에 정면으로 걸린다) 합성을 콘솔 서버로 옮겨야 하고 둘 다 아키텍처 결정이다.»* 이 ADR 이 그 결정이다.

### ADR-017 이 비교하지 않은 후보

ADR-017 D2 는 «브라우저에서 합성(B)» 만 기각했다 — 토큰이 브라우저로 간다는 이유였다. **«Next 서버에서 합성»** 은 후보에 오르지 않았다.
Next 서버에서 합성해도 토큰은 서버(HttpOnly 세션) 밖으로 나가지 않으므로 D2-B 의 기각 사유가 적용되지 않는다. 그리고 저장소는 이미 도메인 안 개요에서 그 방식을 쓰고 있다(위 표 마지막 행).

---

## Decision

D1~D4 는 갈래와 무관하게 고정이다. **D5 만 소유자가 고른다.**

### D1 — 합성 응답의 **선(wire) 모양은 그대로**다

`console-integration-contract.md` § 2.4.9 의 응답 봉투(카드별 `status`, 도메인별 열화, 오류 봉투 `NO_ACTIVE_TENANT`·`TOKEN_INVALID`·`BAD_GATEWAY`)를 바꾸지 않는다.
바뀌는 것은 **그 봉투를 만드는 자리**(console-bff → console-web 서버)뿐이다. 그래서 화면 컴포넌트와 샘플 방문자 데이터(ADR-074 — `sampleGate` 의 `core: 'console-bff'` 표면)는 손대지 않아도 된다.
🔴 단, 샘플 원장의 `core` 이름이 `console-bff` 인 것은 **이름일 뿐**이다 — 바꿀지는 단계 티켓이 정한다(바꾸면 원장 가드가 문다).

### D2 — ADR-017 의 **불변식은 자리를 옮겨도 그대로** 지킨다

| ADR-017 | 무엇 | console-web 에서 |
|---|---|---|
| D4 (HARD INVARIANT) | 도메인별 자격 — IAM 레그 = 운영자 토큰(RFC 8693 교환), 나머지 = 도메인용 토큰(`getDomainFacingToken()`) | 이미 console-web 에 있는 두 함수를 그대로 쓴다 — **새 자격·새 발급자 0** |
| D5 | 도메인별 실패 격리 — 한 도메인이 죽어도 나머지 카드는 그린다. 503 으로 전체를 비우지 않는다 | 레그마다 타임아웃 + `Promise.allSettled`. 회로 차단기는 D5 갈래가 정한다 |
| D6 | `tenant_id` 는 생산자가 판정한다. 합성자는 다시 판정하지 않는다 | 그대로 — 활성 테넌트 없음 = 400 `NO_ACTIVE_TENANT` 은 **호출 전에** 거절(지금 프록시와 같다) |
| ADR-043 D5 | 알림 — 한 도메인이 죽어도 벨은 산다 | 위 D5 와 같은 장치 |

401 규칙: 어느 레그든 401 이면 **전체 재로그인**(기존 IAM 개요와 같은 규칙). 401 을 «그 카드만 열화» 로 삼키지 않는다.

### D3 — 도메인 **하나**의 합성과 **여럿**의 합성을 같은 방식으로 한다

이 ADR 뒤로 콘솔의 모든 합성은 console-web 서버에서 한다. «도메인 안이면 console-web, 도메인 사이면 console-bff» 라는 두 규칙은 사라진다.
🔴 그러므로 `TASK-MONO-751`(콘솔 팬 화면)의 «BFF 라우트» 는 **console-web 의 같은 출처 라우트**(ecommerce `products/**` 모양)를 뜻한다. 그 티켓의 Related Contracts 줄 «console-bff ↔ fan gateway» 는 이 ADR 이 ACCEPT 되면 틀린 말이 된다 — 단계 티켓이 그 줄을 고치게 한다(751 은 다른 세션의 티켓이라 이 PR 은 손대지 않는다).

### D4 — 지우는 것과 남기는 것

- **지운다(마지막 단계)**: `apps/console-bff` 모듈 · `settings.gradle` include · compose(본체·e2e·데모) 서비스 · `ci.yml` 의 경로 필터와 잡 · `nightly-e2e.yml` 의 해당 단계 · 데모 AMI 의 서비스 수 · `CONSOLE_BFF_*` 환경변수 · `verify-demo-wrapper.sh` / `check-gateway-drift.sh` / `check-service-map-drift.sh` 의 console-bff 항목 · `console-vercel.override.yml` 의 «알려진 한계» 절.
- **고친다**: `PROJECT.md`(Service Map 에서 console-bff 행 · `service_types: [frontend-app, rest-api]` → `rest-api` 의 근거가 사라진다 — ADR-013 § D5 가 «BFF 가 오면 더한다» 로 넣었다) · `console-integration-contract.md` § 2.4.9 의 «생산자» · `notification-inbox-contract.md` 의 집계기 자리 · `console-bff/architecture.md`(삭제) · `jwt-standard-claims.md` 의 console-bff 대상(audience) 언급.
- **남긴다**: `libs/java-security` `AllowedAudiencesValidator` — console-bff 말고도 서비스 레벨 디코더가 쓴다. 다만 그 javadoc 의 console-bff 사례는 고친다.
- 🔴 **페더레이션 e2e**(`tests/federation-hardening-e2e`): `operator-overview-composition` · `domain-health-composition` · `observability-trace-tree`(console-bff 언급 40곳 — 트레이스 뿌리가 console-bff 다) 세 스펙이 console-bff 를 전제로 한다. 지우기 **전에** console-web 기준으로 다시 쓴다 — 지우고 나서 고치면 그 사이 nightly 가 빨갛다.

### D5 — 옮기는 **방식** — 🔴 **소유자 결정**

| | **A. 옮기고 지운다** | B. console-bff 를 엣지에 공개 | C. 둘 다 유지 | D. 지금대로 |
|---|---|---|---|---|
| 한 줄 | 세 합성을 console-web 서버로 옮기고, 옮긴 뒤 console-bff 를 지운다 | console-bff 에 Traefik 라우터(공개 호스트명)를 다시 준다 | Vercel 콘솔은 console-web 합성, 데모 호스트 콘솔은 console-bff | 데모에서 세 화면이 «BFF 사용 불가» 인 채 둔다 |
| Vercel 데모 | 세 화면이 **살아난다** | 살아난다 | 살아난다 | 계속 깨져 있다 |
| 규칙 | 지킨다 | 🔴 `api-gateway-policy.md` L14(백엔드 엣지 노출 금지)를 **깬다** — `TASK-MONO-362` 를 되돌린다 | 지킨다 | 지킨다 |
| 비용 | 합성 3개 이전 + 정리(D4) — 아래 단계 표 | 라우터 1 + 정책 예외 + 공개 경로의 인증·속도 제한을 게이트웨이 수준으로 다시 갖춤 | 🔴 같은 합성이 **두 집**(Java·TS) — 한쪽만 고쳐진다 | 0 |
| 남는 것 | 서비스 하나 줄어듦 · 데모 AMI 컨테이너 1 감소 | 서비스 유지 · 공개 표면 1 증가 | 서비스 유지 + 사본 | 그대로 |

---

## 추천 — 🔴 **구현자의 선호**다. 소유자 결정이 아니다

**A.** 세 화면은 기존 조회 API 를 다시 묶는 일이라 Next 서버로 충분하다 — 저장소가 도메인 안 개요에서 이미 그렇게 하고 있다. A 만이 «Vercel 데모의 세 화면» 과 «엣지 노출 금지» 를 **동시에** 지킨다. B 는 규칙을 깨서 고치고, C 는 같은 사실에 집을 둘 만든다.

---

## 라이더 — 🔴 **내 선택**이지 소유자 결정이 아니다

| # | 라이더 | 뒤집는 법 |
|---|---|---|
| R1 | 도메인별 지표(`bff_fanout_latency{domain}` · `bff_fanout_errors_total` · `bff_aggregation_degrade_count` · `bff_circuit_breaker_state`, ADR-017 D7)는 **구조화 로그**(레그마다 `domain`·`status`·`latencyMs` 한 줄)로 대체한다. Next 서버에 Prometheus 수집 경로가 없다 — 새로 만들지 않는다 | 「지표를 유지」 — 수집 경로가 새 티켓이 된다 |
| R2 | 회로 차단기는 **두지 않는다** — 레그마다 타임아웃만. Vercel 함수는 요청마다 상태가 없어 차단기 상태를 들고 있을 자리가 없다 | 「차단기 유지」 — 상태 저장소(예: KV)가 필요하다 |
| R3 | console-bff 삭제는 세 합성을 옮긴 PR 들이 **머지되고 다음 nightly 가 초록인 뒤** 한 PR 로 한다(옮기는 PR 과 섞지 않는다) | 「옮기면서 바로 지운다」 |

---

## Alternatives Considered

- **B. console-bff 에 공개 호스트명** — 🔴 기각 후보. 백엔드 서비스를 엣지에 내놓지 않는다는 규칙을 깬다(`TASK-MONO-362` 가 그 이유로 라우터를 지웠다). 공개 경로는 게이트웨이가 하던 인증·속도 제한·헤더 정리를 다시 갖춰야 하는데, 그것은 **게이트웨이를 하나 더 만드는 것**이다.
- **C. 둘 다 유지** — 🔴 기각 후보. 합성 규칙(카드 열화·401 처리·자격 선택)이 Java 와 TS 두 곳에 산다. «한 사실이 두 집을 가지면 한쪽만 갱신된다» 는 이 저장소가 여러 번 밟은 실패다(`ADR-MONO-067` D3 이 런처의 두 집을 하나로 만든 것과 같은 이유).
- **D. 지금대로** — 데모의 세 화면이 영구히 깨져 있다. 비용이 0 이라는 것 말고 장점이 없다.
- **콘솔을 Vercel 에서 데모 호스트로 되돌림** — ADR-067 을 되돌리는 별개 결정이고, `TASK-MONO-625` 가 «데모 호스트 콘솔이 더 온전하다» 는 근거를 과장으로 철회한 기록이 있다(`console-vercel.override.yml`).

---

## Consequences

### ACCEPT 가 인가하는 것

`TASK-MONO-754` — 갈래대로 **단계 티켓을 기안**하는 것까지. 구현은 단계 티켓마다 따로 간다(HARDSTOP-09).

### 단계(A 일 때 — 기안 예정 모양)

| 단계 | 무엇 | 선행 |
|---|---|---|
| 1 | 계약·스펙 — `console-integration-contract.md` § 2.4.9 생산자 이전 · `notification-inbox-contract.md` · `console-web/architecture.md` · `PROJECT.md`(Service Map·`service_types`) | — |
| 2 | 운영 개요 + 도메인 상태 합성을 console-web 서버로 (D1 선 모양 유지 · D2 불변식 · 대조군 시험) | 1 |
| 3 | 알림 인박스 + 읽음 처리를 console-web 서버로 | 1 |
| 4 | 페더레이션 e2e 세 스펙을 console-web 기준으로 다시 쓴다 | 2·3 |
| 5 | console-bff 삭제 + 정리(D4) — R3: 2~4 머지 · nightly 초록 뒤 | 4 |
| 6 | 데모 재굽기에 실려 Vercel 콘솔의 세 화면이 실제 데이터로 뜨는지 라이브 확인 | 5 |

### 건드리지 않는 것

- 도메인 화면 6종의 경로(이미 console-web 직접)
- 자격 모델(ADR-017 D4, ADR-020) · 테넌트 판정(D6)
- 스토어·팬

### 새로 생기는 위험

- 🔴 Vercel 함수 실행 시간 — 6개 레그를 동시에 부르므로 **가장 느린 레그가 응답 시간을 정한다**. 레그 타임아웃을 함수 한도보다 충분히 짧게 둔다(한도 값은 아직 재지 않았다 — `console-web` 에 `maxDuration` 설정 0건). 단계 2 의 AC.
- 🔴 «생산자 API 의 401» 과 «레그 하나 실패» 를 구별하지 못하면 운영자가 재로그인 루프에 빠지거나 반대로 만료 세션이 열화 카드로 숨는다 — 단계 2 의 대조군.
- 지표 상실(R1) — 운영 대시보드가 `bff_*` 지표를 읽고 있으면 빈다. 소비자 grep 이 단계 5 의 AC.

---

## Verification

- 🔴 **대조군 1**: 레그 하나(예: scm)를 죽인 상태에서 운영 개요가 200 이고 scm 카드만 열화, 나머지 카드는 실제 값이다.
- 🔴 **대조군 2**: 레그 하나가 401 이면 응답은 401(재로그인), 열화 카드 200 이 **아니다**.
- 활성 테넌트 없음 → 400 `NO_ACTIVE_TENANT`, **어떤 레그도 호출되지 않는다**(호출 수 0 을 단언).
- IAM 레그는 운영자 토큰, 나머지는 도메인용 토큰으로 나간다(헤더 단언 — D2 / ADR-017 D4).
- 응답 JSON 이 옮기기 전 console-bff 응답과 **같은 모양**이다(`specs/contracts/fixtures/operator-overview-leg-bodies.json` 재사용).
- 샘플 방문자(ADR-074)는 여전히 어떤 레그도 부르지 않는다.
- 라이브: Vercel 콘솔에서 세 화면이 `bffUnavailable` 없이 뜬다(단계 6).

---

## Outstanding follow-ups

- ⏸️ ACCEPT 뒤 `TASK-MONO-754` 가 단계 티켓을 기안한다 — 🔴 ACCEPT PR 안에서 그 자리 기안한다(산문에만 남기지 않는다).
- ⏸️ `TASK-MONO-751` 의 Related Contracts 줄(D3) — 단계 1 티켓이 그 티켓 소유자에게 고치게 하거나 그 자리에서 고친다.

---

## History

- 2026-10-02 — PROPOSED. 소유자가 대화에서 «콘솔도 서버 컴포넌트에서 하는 것으로 충분하다는 거지?» 에 대한 답(충분하다 — 근거: 도메인 안 합성의 선례 · Vercel 도달 불가 · ADR-017 의 미비교 후보)을 받고 «진행» 이라 했다. 갈래(D5)는 이 문서가 처음 제시한다.
  근거 조사: console-web 의 console-bff 호출 4 라우트 · console-bff 엔드포인트 4 · 저장소 전체 console-bff 언급(완료 티켓·ADR 제외 145 파일) · `console-vercel.override.yml` 의 알려진 한계.
- 2026-10-02 — **ACCEPTED — A.** 소유자 원문: `ADR-MONO-081 ACCEPTED — A`(PROPOSED PR #4109 머지 뒤). D1~D5 본문은 **바이트 그대로**다(finalise 이지 re-decide 아님).

---

## 라이더 대조 (ACCEPT 시점, 2026-10-02) — 🔴 **반사가 아니라 대조로 했다**

라이더는 공급되지 않았다. 판별: *«이 질문에 답하지 않고도 A 를 고를 수 있는가?»*

| # | 항목 | 대조 결과 | 처리 |
|---|---|---|---|
| R1 | 도메인별 지표 → 구조화 로그 | 라이더다 — A 는 «옮긴다» 만 정하고 관측 수단을 정하지 않는다 | `TASK-PC-FE-302` · `303` AC(로그 한 줄 모양) · `TASK-MONO-757` AC(`bff_*` 소비자 grep = 0) |
| R2 | 회로 차단기 없음, 레그 타임아웃만 | 라이더다 | `TASK-PC-FE-302` AC |
| R3 | 삭제는 이전 머지 + nightly 초록 뒤 별도 PR | 라이더다 | `TASK-MONO-757` AC-0 게이트 |
| — | 🔴 **레그 타임아웃 값** | 표에 **없던** 질문이다 — § 새로 생기는 위험이 «함수 한도보다 짧게» 만 적었고 한도를 재지 않았다(`maxDuration` 설정 0건). A 를 고르는 데 필요 없다 ⇒ 미결 | `TASK-PC-FE-302` AC — **재고 나서** 정한다(값을 지어내지 않는다) |
| — | 🔴 **샘플 원장의 `core: 'console-bff'` 이름** | D1 이 «이름일 뿐, 단계 티켓이 정한다» 로 남겼다 | `TASK-PC-FE-302` Edge Case — 기본은 **바꾸지 않는다**(바꾸면 원장 가드가 문다) |

### ACCEPT 가 인가하는 것 / 하지 않는 것

- 인가: 아래 단계 티켓의 착수(계약 단계 `TASK-MONO-755` 가 먼저).
- 인가하지 않음: 자격 모델 변경(ADR-017 D4) · 도메인 화면 6종의 경로 변경 · console-bff 를 엣지에 공개(B).

### ACCEPT 가 만든 새 의무 — 확인하고 적는다

`§ Outstanding follow-ups` 의 «ACCEPT 뒤 단계 티켓 기안» 을 **이 PR 에서** 했다(`TASK-MONO-754` → review):

| 단계 | 티켓 | 무엇 | 선행 |
|---|---|---|---|
| 1 | `TASK-MONO-755` | 계약·스펙 — § 2.4.9 생산자 · `notification-inbox-contract.md` · `console-web/architecture.md` · `PROJECT.md` · `TASK-MONO-751` Related Contracts 줄 | — |
| 2 | `TASK-PC-FE-302` | 운영 개요 + 도메인 상태 합성을 console-web 서버로 | 755 |
| 3 | `TASK-PC-FE-303` | 알림 인박스 + 읽음 처리를 console-web 서버로 | 755 |
| 4 | `TASK-MONO-756` | 페더레이션 e2e 세 스펙을 console-web 기준으로 | 302 · 303 |
| 5 | `TASK-MONO-757` | console-bff 삭제 + 정리(D4) | 756 머지 + 다음 nightly 초록(R3) |
| 6 | `TASK-MONO-758` | 재굽기 창에서 Vercel 콘솔 세 화면 라이브 확인 | 757 |

🔴 **다른 티켓에 생긴 의무** — 활성 큐에서 console-bff 를 전제로 적은 티켓이 셋 있다. 이 PR 은 그 파일들을 고치지 않는다(남의 티켓·진행 중 티켓):
- `TASK-MONO-751`(콘솔 팬 화면) — Related Contracts 줄 → `TASK-MONO-755` AC.
- `TASK-MONO-672`(스택 측정의 집) — «console-bff 가 엣지로서 `aud` 를 본다(712)» 측정 행은 삭제 뒤 잴 대상이 없어진다 → `TASK-MONO-757` AC 가 그 행에 «대상 은퇴» 를 적는다.
- `TASK-MONO-648`(포트폴리오 캡처, in-progress) — 저하 화면 셋(`/dashboards/overview` · `/dashboards/health` · `/`)이 단계 6 뒤 실제 화면이 된다 → `TASK-MONO-758` AC 가 648 에 다시 찍을 수 있다고 알린다.
- `TASK-MONO-697`(audience 검사 섀도→거절) Out of Scope 의 «console-bff» 항목은 대상이 사라질 뿐 결정이 바뀌지 않는다 → `TASK-MONO-757` AC 에서 한 줄 정리.
