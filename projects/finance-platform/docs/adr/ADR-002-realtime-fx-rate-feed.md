# ADR-002: 실시간 FX 환율 피드 — 수동 환율 입력에서 config-gated 외부 피드 + 캐시·staleness 가드로

- **Status**: ACCEPTED (2026-06-15)
- **Date**: 2026-06-14
- **Authors**: architecture (ledger-service 22번째 증분 방향 — finance-platform 두 번째 프로젝트 ADR, 첫 외부 HTTP 통합)
- **Supersedes**: —
- **Superseded by**: —
- **History**: PROPOSED 2026-06-14 — 사용자가 ledger 후속으로 **"실시간 환율 feed"** 를 명시 선택(ADR-001 FIFO/lot 체인 21증분 완결 직후, 충돌 없는 finance 평면). 21증분(FIN-BE-007~029)까지 재평가·결제의 환율은 **운영자가 요청마다 손으로 입력**(`closingRate`/`settlementRate` BigDecimal)해 왔다. 본 ADR 은 (a) 현 수동입력 모델을 회고적으로 기록하고 (b) **외부 환율 피드 + 캐시 + staleness 가드**로의 전환을 **config-gated·opt-in·net-zero**(미설정 환경엔 무영향)로 결정한다. 외부 HTTP 의존은 **새 실패모드(타임아웃·stale·제공자 장애)·캐싱/감사 정책·환율 출처의 신뢰성** 을 도입하는 아키텍처 결정이므로 코드로 묵시 결정하면 HARDSTOP-09 + regulated/audit-heavy 위반 → 결정을 먼저 기록하고 PAUSE. **ACCEPTED 전환 + 구현은 별도 user-explicit-intent 태스크**(sibling ADR-001 / ADR-MONO-019/020/032/033 staged-child 패턴). **Self-ACCEPT 금지.** · ACCEPTED 2026-06-15 — 사용자가 PROPOSED ADR(D1~D6) 검토 후 AskUserQuestion "ADR-002 게이트" = **"ACCEPTED 승급 + 구현 시작"** 명시 선택(sibling ADR-001 동일-세션 PROPOSED→ACCEPTED 선례). D1~D6 CHOSEN-PROPOSED 방향 **byte-unchanged 확정** — ACCEPTED 는 *확정*이지 재결정이 아님; § 1 Context + § 2 Decisions + § 3 Consequences + § 4 Alternatives + § 5 byte-identical, flip = Status + 본 절 + § 6 ACCEPTED row + § 3.1 실행 로드맵 UNPAUSED. **PROPOSED 와 동일 PR(FIN-BE-030)에서 전환** — 사용자가 PROPOSED 가 독립 머지되기 전에 ACCEPTED 했으므로 staged-child governance trail 을 PR *내*에 보존(§ 6 PROPOSED+ACCEPTED 2행). 실행(§ 3.1: FIN-BE-031 port+캐시+폴러 shadow → 032 소비+staleness)은 별도 post-ACCEPTED 태스크 — 본 PR 에 코드 없음. **NOT self-ACCEPT** — ACCEPTED 전환이 사용자 직접 지시.

---

## 1. Context

### 1.1 현재 상태 (조사 결과, factual)

`ledger-service` 의 외화 환율은 **두 운영자 경로에서만, 요청 파라미터로** 들어온다(외부 환율 출처·캐시·자동조회 전무):

| 증분 | 진입점 | 환율 입력 |
|---|---|---|
| 9th (FIN-BE-015) | `RevalueForeignBalanceCommand.closingRate` | 운영자가 마감(spot) 환율을 요청 body 로 직접 입력 |
| 10th (FIN-BE-016) | `SettleForeignPositionCommand.settlementRate` | 운영자가 결제(spot) 환율을 요청 body 로 직접 입력 |

```java
// RevalueForeignBalanceCommand — closingRate 는 운영자 입력 (base-minor-per-foreign-minor, > 0)
BigDecimal closingRate,
// SettleForeignPositionCommand — settlementRate 동일
BigDecimal settlementRate,
```

- 두 환율 모두 **strictly-positive `BigDecimal`**(base-minor-per-foreign-minor)이고, ≤0 이면 `SETTLEMENT_RATE_INVALID` / 재평가 등가물(422). money 는 정수 minor(F5)지만 환율만 exact `BigDecimal`.
- **환율을 보관하는 테이블·외부조회 어댑터·스케줄러 전무.** `journal_line.exchange_rate DECIMAL(20,8)`(V5)는 **포스팅 시점에 적용된** 환율을 행 단위로 박제할 뿐, "현재 시장환율" 출처가 아니다.
- **외부 HTTP 통합 선례 = 0(ledger 내)**. 단 monorepo 전역엔 확립된 패턴 존재: skill `backend/external-http-integration`(fan-platform EMAIL/PUSH, ecommerce carrier, erp Slack 추출) — **config-gated outbound port + 실어댑터 + noop 기본(net-zero) + `ResilienceClientFactory`(libs/java-common) + MockWebServer 테스트**.
- **DB**: MySQL 8 / InnoDB / utf8mb4. money=`BIGINT` minor, 시각=`DATETIME(6)`, id=`VARCHAR(36)`, 환율=`DECIMAL`. Migrations V1~V11(V11=`fx_cost_flow_account_config`).

### 1.2 기록되지 않은 것 (이 ADR 이 필요한 이유)

수동 환율 입력은 21증분 동안 "구현된 사실"일 뿐 **선택으로 기록된 적이 없다.** 더욱이 ledger architecture.md 의 Increment Scope preamble 은 이 전환을 **명시적으로 예약**해 두었다 — "A FIFO / lot-level cost basis, a bulk/period-close revaluation hook, **a live FX rate feed** … remain forward-declared". 즉 본 ADR 은 spec 이 예약한 미래 증분의 **방향을 형식화**한다. 외부 환율 피드 도입은 다음을 건드린다:

1. **새 실패모드** — 외부 제공자 타임아웃·5xx·연결거부, 그리고 **stale quote**(받았으나 오래됨). regulated 도메인에서 **stale/추정 환율로 손익을 인식하면 안 된다** — 자동조회 실패 시의 거동을 명시 결정해야 한다.
2. **감사 추적성(audit-heavy)** — 자동 적용된 환율은 **출처(provider)·as-of 시각·fetch 시각** 이 추적가능해야(regulated). 손익 한 줄이 "어디서 온 환율인가"에 답할 수 있어야.
3. **F8(자동 포스팅 금지) 보존** — 재평가·결제는 **여전히 운영자 트리거**여야 한다. 환율 피드는 "환율을 제공"할 뿐 **스스로 분개를 만들지 않는다**(자동 mark-to-market 데몬은 본 ADR 범위 밖).
4. **외부 latency 가 운영자 요청을 막으면 안 된다** — 동기 외부호출을 운영자 경로에 넣으면 제공자 장애가 결제·재평가를 블록. 디커플링 결정 필요.

코드로 이 넷을 묵시 결정하면 외부의존·감사정책을 silently bake → **HARDSTOP-09**. `fintech` / `regulated` / `audit-heavy` trait 상 명시 기록 필수.

### 1.3 결정 드라이버

- **net-zero 규율** — 21증분 전부 이전 동작 byte-identical 보존. 피드 도입도 **미설정 환경(=기본 noop·수동입력 유지)엔 무영향**이어야.
- **감사 추적성(audit-heavy)** — 자동 적용 환율은 출처·시각이 영속·추적가능.
- **장애 격리(regulated)** — 외부 제공자 장애·stale 가 **손익 정확성을 오염시키지 않는다**(fail-closed: 자동조회 실패 → 수동입력 요구로 폴백, 추정환율 금지). 단 fail-closed 는 **자동조회 한정**이지 전체 작업을 막지 않는다.
- **포트폴리오 표면** — finance 의 첫 외부 HTTP 통합으로 "외부 시장데이터 연동 + 캐시 + staleness 거버넌스" 를 시연.

---

## 2. Decisions

### D1 — 환율 출처 = **outbound port `FxRateProviderPort`**, config-gated 실어댑터 + **기본 noop**(net-zero), 운영자 수동입력은 영구 유지

새 application 포트 `FxRateProviderPort.latestQuote(base, foreign) → Optional<FxRateQuote>`. 실어댑터(공개 FX API 또는 결정론적 stub)는 `financeplatform.ledger.fxrate.mode` 로 선택, **기본 `noop`(matchIfMissing) + `enabled=false`** — 미설정 서비스는 어떤 외부호출도 안 하고 수동입력 그대로(net-zero). `ResilienceClientFactory` 로만 `RestClient` 구성(skill 규약).

- **왜**: skill `external-http-integration` 동형(config-gated·noop 기본). 운영자 수동입력(`closingRate`/`settlementRate`)은 **제거하지 않는다** — 피드는 그것을 **선택적으로 채워줄 뿐** 대체 아님(§ D3).
- **버린 대안**: (B) 운영자 경로에서 **동기 외부호출** — 제공자 latency/장애가 결제·재평가를 블록(§ D4 디커플링 위반). 거부. (C) 외부의존 **하드와이어**(noop 게이트 없음) — net-zero 위반·로컬/CI 에 외부 의존 강제. 거부.

### D2 — 환율 보관 = **`fx_rate_quote` 캐시 테이블**(provider·as-of·fetched-at 영속, 감사·staleness 토대)

| 컬럼 | 타입 | 의미 |
|---|---|---|
| `base_currency` | VARCHAR(3) | 보고통화(KRW) |
| `foreign_currency` | VARCHAR(3) | 외화 |
| `rate` | DECIMAL(20,8) | base-minor-per-foreign-minor (또는 unit rate; 구현 태스크 단위 명시) |
| `as_of` | DATETIME(6) | 제공자가 명시한 환율 시점 (staleness 판정 기준) |
| `source` | VARCHAR(64) | 제공자 식별자(감사) |
| `fetched_at` | DATETIME(6) | 우리가 가져온 시각 |
| PK | `(base_currency, foreign_currency)` | 통화쌍당 **최신 1행**(last-write-wins upsert) |

- **왜**: 자동 적용 환율의 **출처·시점 영속**(audit-heavy). 운영자 경로는 **캐시만 읽고**(동기 외부호출 0) → 외부 latency/장애 디커플링(§ D4). per-tenant 아님 — 시장환율은 테넌트 무관(reconciliation_fx_tolerance 와 달리 글로벌). 이력 보존이 필요하면 append-only `fx_rate_quote_history` 는 deferred(§ 3 로드맵).
- **버린 대안**: (B) 캐시 없이 매 요청 외부조회 — § D4 위반. (C) 통화쌍당 전체이력 테이블 1차 도입 — over-scope; 최신-only + (필요시)history 분리. 거부.

### D3 — 운영자 경로 소비 = **수동입력 우선, 생략 시에만 캐시 폴백**(F8·net-zero 보존)

재평가·결제 요청의 `closingRate`/`settlementRate` 를:

- **제공(비-null)** → 기존과 **byte-identical**(운영자 입력 그대로; 피드 무관). **net-zero 의 핵심.**
- **생략(null) + 피드 enabled + 캐시 fresh** → `fx_rate_quote` 의 최신 quote 를 환율로 사용. 적용된 quote 의 출처·as-of 를 **audit_log + 엔트리 reference 에 기록**(어디서 온 환율인지 추적).
- **생략 + 피드 disabled, 또는 캐시 stale/부재** → **명확한 에러로 거부**(`FX_RATE_UNAVAILABLE` 422 등) — **추정/stale 환율로 손익 인식 금지**(regulated fail-closed). 운영자는 수동입력으로 재시도.

- **불변식 보존**: 재평가·결제는 **여전히 운영자 트리거**(F8 — 피드가 분개를 자동 생성하지 않음). 엔트리 shape·이중기입 KRW 균형·멱등 불변. **환율의 출처만** 운영자-손입력 → (선택적) 캐시-조회.
- **버린 대안**: (B) 피드 환율이 **수동입력을 덮어씀** — 운영자 의도 무시·net-zero 위반. 거부. (C) stale quote 를 **그대로 사용** — regulated 위반(추정환율 손익). 거부 → staleness 가드 필수(§ D4).

### D4 — refresh = **스케줄드 폴러**(캐시 적재), 운영자 경로는 캐시-읽기 전용; staleness 가드

- **갱신**: 별도 스케줄드 태스크(skill `backend/scheduled-tasks`, ShedLock 단일리더)가 주기적으로 `FxRateProviderPort.latestQuote` 를 호출해 `fx_rate_quote` 를 upsert. **best-effort, never-throw**(제공자 장애 시 캐시는 직전 quote 유지·로그). 운영자 경로는 **이 캐시만 읽고 동기 외부호출 0** → 외부 latency/장애가 결제·재평가를 절대 블록 안 함.
- **staleness 가드**: per-environment `max-age`(예: 24h). 캐시 quote 의 `as_of`(또는 `fetched_at`)가 max-age 초과 → **stale → § D3 의 폴백 거부 경로**(자동적용 안 함). 추정환율 인식 금지.
- **버린 대안**: (B) 운영자 경로에서 lazy 동기 fetch — latency 결합·재시도 인라인. 거부. (C) staleness 무시하고 마지막 quote 영구 사용 — regulated 위반. 거부.

### D5 — provider 추상화 + **결정론적 stub 어댑터**(데모·테스트 무외부의존)

- `mode=stub` 어댑터 = 설정/시드된 고정 환율 반환 → **실제 외부 API 없이** 피드 전체 거동(폴러→캐시→자동적용→staleness)을 데모·IT 로 시연. `mode=<real>` 는 공개 FX API(예: ECB/exchangerate.host류) 어댑터로 **config 한 줄 전환**. MockWebServer 로 2xx/5xx/연결거부/stale 커버.
- **왜**: 외부 third-party 의존을 CI/로컬에 강제하지 않으면서(net-zero) 패턴 실증. skill 의 noop/실어댑터 분기와 동형(+stub).

### D6 — 마이그레이션 net-zero (additive)

- **V12**: `fx_rate_quote` 신설(additive, 기존 테이블·행 byte-unchanged). backfill 없음(빈 캐시 = 자동적용 비가용 = 수동입력 유지 = net-zero).
- 기존 동작·IT byte-unchanged — 피드를 켜고(`enabled=true`+`mode`) 운영자가 환율을 **생략**한 경로에서만 거동 변화.

---

## 3. Consequences

**긍정**:
- 외부 시장환율 자동조회 + 출처·시점 감사(audit-heavy) — 손익 환율의 출처 영속.
- config-gated·noop 기본·net-zero — 미설정 환경 무영향, 포트폴리오상 "외부 시장데이터 연동 + staleness 거버넌스" 표면(finance 첫 외부 HTTP).
- 외부 latency/장애 디커플링(캐시-읽기 + 스케줄드 폴러) — 제공자 장애가 결제·재평가를 막지 않음.

**부정/리스크**:
- 새 인프라(스케줄드 폴러 + 캐시 테이블 + 외부 어댑터) — 운영 복잡도↑.
- **staleness 판정 정확성** — max-age 경계·시간대·as-of vs fetched-at 의미를 IT 로 강하게 고정해야(추정환율 누출 0).
- 환율 단위(base-minor-per-foreign-minor vs unit rate)·반올림을 기존 `closingRate`/`settlementRate` 와 **정확히 동일 규약**으로 맞춰야(자동적용=수동입력과 산식 동치).

**불변식(F-invariants) 영향: 없음** — 이중기입·KRW 균형·F8(운영자 트리거·자동포스팅 금지)·엔트리 shape·멱등 모두 불변. 피드는 `closingRate`/`settlementRate` **공급원**일 뿐.

---

## 4. Alternatives Considered (요약)

- **D1**: (B) 동기 외부호출 = latency 결합; (C) 하드와이어 = net-zero 위반. 거부.
- **D2**: (B) 캐시리스 매요청 조회 = latency; (C) 전체이력 1차 = over-scope. 거부.
- **D3**: (B) 피드가 수동입력 덮어씀 = net-zero 위반; (C) stale 사용 = regulated 위반. 거부.
- **D4**: (B) lazy 동기 fetch; (C) staleness 무시. 모두 거부.
- **전체**: "자동 mark-to-market 데몬(피드가 재평가를 자동 트리거)" = F8 위반·범위초과. 본 ADR 밖(영구 deferred 가능).

---

## 5. 관계 (ADR-MONO-008 / ADR-001 / external-http-integration skill)

| | ADR-MONO-008 (finance bootstrap) | ADR-001 (FX cost-flow FIFO/lot) | FxRevaluation/Settlement (9th/10th) | skill external-http-integration |
|---|---|---|---|---|
| 관계 | **하위** — 부트스트랩 ledger 도메인의 외부 시장데이터 연동 결정 | **직교** — cost-flow 는 처분*원가* 산정, 본 ADR 은 *환율 출처*; 둘 다 `C`/`realized` 에 영향하나 결정축이 다름 | **공급-대상** — `closingRate`/`settlementRate` 의 (선택적) 공급원, 경로·shape 불변 | **차용** — config-gated outbound port + noop 기본 + ResilienceClientFactory + MockWebServer 패턴 |

---

## 6. Status Transition History

| Date | Transition | Decision summary | Trigger | PR |
|---|---|---|---|---|
| 2026-06-14 | created PROPOSED | D1 = `FxRateProviderPort` config-gated 실어댑터 + noop 기본(net-zero), 수동입력 영구유지(동기호출 B·하드와이어 C 거부). D2 = `fx_rate_quote` 캐시(provider·as-of·fetched-at 감사; 캐시리스 B·전체이력 C 거부). D3 = 수동입력 우선·생략시에만 fresh 캐시 폴백·stale/disabled→`FX_RATE_UNAVAILABLE` fail-closed(덮어쓰기 B·stale사용 C 거부; F8 보존). D4 = 스케줄드 폴러 적재 + 운영자 캐시-읽기 + staleness max-age 가드(lazy동기 B·staleness무시 C 거부). D5 = provider 추상화 + 결정론적 stub 어댑터(무외부의존 데모·IT). D6 = additive V12 `fx_rate_quote`, backfill 0, net-zero. | 사용자 명시 선택 — ledger 후속 "실시간 환율 feed" (2026-06-14, ADR-001 21증분 완결 직후 충돌 없는 finance 방향) | (this) |
| 2026-06-15 | PROPOSED → ACCEPTED | D1~D6 CHOSEN-PROPOSED 방향 **byte-unchanged 확정**(ACCEPTED 는 확정이지 재결정 아님); § 1~5 byte-identical, flip = Status + History ACCEPTED 절 + 본 row + § 3.1 실행 로드맵 UNPAUSED. PROPOSED 와 동일 PR(FIN-BE-030)에서 전환(사용자가 PROPOSED 독립 머지 전 ACCEPTED) — governance trail PR 내 보존(2행). 코드 없음. | "ACCEPTED 승급 + 구현 시작" (사용자 AskUserQuestion "ADR-002 게이트" 명시 선택; sibling ADR-001 동일-세션 PROPOSED→ACCEPTED) | (this) |

> **ACCEPTED 2026-06-15.** § 3.1 실행 로드맵이 **UNPAUSED** — FIN-BE-031~032 가 본 ACCEPTED main 에서 의존-correct 순서로 진행한다. 각 단계는 별도 태스크이며 D1~D6 는 확정·실행 시 재결정하지 않는다. ADR-MONO-008 의 도메인/trait 는 재결정되지 않는다(본 ADR 은 ledger 환율-출처만 결정).

### 3.1 실행 로드맵 (post-ACCEPTED; ACCEPTED 시 확정 — UNPAUSED)

1. **`TASK-FIN-BE-031`** (D1/D2/D5/D6) — `FxRateProviderPort` + noop/stub 어댑터 + `ResilienceClientFactory` 배선 + V12 `fx_rate_quote` 캐시 + 스케줄드 폴러(ShedLock) **shadow**(캐시 적재만, 운영자 경로 미참조). MockWebServer IT. Model = **Opus**(외부의존·인프라).
2. **`TASK-FIN-BE-032`** (D3/D4) — 재평가·결제 use-case 의 환율-생략 시 캐시 폴백 + staleness 가드 + `FX_RATE_UNAVAILABLE` + 적용 quote 출처 감사기록. net-zero(입력 제공 경로 byte-identical). Model = **Opus**(손익경로·regulated fail-closed).
3. **`TASK-FIN-BE-038`** (DONE) — 실제 공개 FX API 어댑터(`mode=real`, **Frankfurter** no-key/ECB). `RealFxRateProviderAdapter` + `FxRateFeedProperties.Real` 블록(`from=foreign&to=base` 방향 매핑) + MockWebServer IT. additive·net-zero(기본 `mode=noop` 불변). 잔여 deferred(당시): append-only `fx_rate_quote_history` · 콘솔 "환율 대시보드 + 수동 refresh" 표면 · per-tenant override(특수 계약환율).
4. **`TASK-FIN-BE-039`** (DONE) — append-only `fx_rate_quote_history` 감사 이력. V13 신규 테이블(surrogate id AUTO_INCREMENT PK, 통화쌍당 N 행) + `FxRateQuoteHistory` 도메인 + `FxRateQuoteHistoryRepository` 포트 + JPA 어댑터 3종 + 폴러 additive append(기존 upsert 호출 byte-unchanged). additive·net-zero.
5. **`TASK-FIN-BE-040`** (DONE) — per-pair FX rate history **read/drill** REST 엔드포인트. `GET /api/finance/ledger/fx-rates/{foreignCurrency}/history` (base=KRW 고정, `?limit` default 50/cap 500/floor 1, `fetched_at DESC, id DESC` 정렬, unknown pair→200 empty). 도메인 포트 `findHistory(Currency,Currency,int)` Spring-free; JPA 어댑터 `PageRequest.of(0,limit)` 변환; `GetFxRateHistoryUseCase`; `FxRateHistoryResponse` DTO(F5 rate string). additive·net-zero. 잔여 deferred: 콘솔 "환율 대시보드 + 수동 refresh" 표면(별도 PC-FE task) · per-tenant override · ShedLock 단일리더.

> **FIN-BE-030 = 본 ADR 캐리어**(doc-only — ADR + README, 코드 0). 구현은 FIN-BE-031(shadow port+캐시+폴러)부터.
