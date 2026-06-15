# TASK-FIN-BE-032 — FX rate feed consumption: rate-omission cache fallback + staleness guard

- **Status**: done
- **Project**: finance-platform
- **Service**: ledger-service
- **Domain / traits**: fintech / [transactional, regulated, audit-heavy]
- **Increment**: 24th ledger increment (ADR-002 D3/D4 — first operator-path change consuming the feed)
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus (손익경로·regulated fail-closed·net-zero 경계)

## Goal

ADR-002 실행 2단계: 재평가·결제 운영자 경로가 **환율을 생략**하면 FIN-BE-031 의 `fx_rate_quote` 캐시에서
**fresh quote 로 폴백**하고, 캐시가 **stale/부재/피드 disabled** 면 `FX_RATE_UNAVAILABLE`(422)로 **fail-closed**
(추정환율 손익 인식 금지)한다. 운영자가 환율을 **제공**하면 종전과 **byte-identical**(net-zero). F8(운영자
트리거·자동 분개 없음) 불변.

## Scope

**In scope** (ledger-service + 1 shared registry edit):

1. **요청 환율 optional 화** — `RevaluationRequest.parseRate(closingRate)` / `SettlementRequest.parseRate(settlementRate)`:
   blank/null → **`null` 반환**(현재는 "required" `IllegalArgumentException`); **비-숫자 문자열은 그대로 422**
   (기존 non-numeric 가드 유지). 커맨드의 `closingRate`/`settlementRate`(BigDecimal)는 이미 nullable.
2. **환율 해석 서비스** `application/ResolveEffectiveFxRate` (또는 `FxRateResolver`):
   - `record ResolvedFxRate(BigDecimal rate, boolean fromFeed, String sourceDescription)`.
   - `ResolvedFxRate resolve(Currency base, Currency foreign, BigDecimal providedRate)`:
     - `providedRate != null` → `ResolvedFxRate(providedRate, false, "manual")` (net-zero).
     - `null` + `!feedEnabled` → `FxRateUnavailableException`("no rate supplied and FX rate feed is disabled").
     - `null` + enabled + 캐시 `findLatest(base,foreign)` empty → `FxRateUnavailableException`("no cached FX rate quote …").
     - `null` + enabled + **stale**(`now − quote.asOf > maxAge`) → `FxRateUnavailableException`("cached FX rate quote is stale …").
     - `null` + enabled + fresh → `ResolvedFxRate(quote.rate, true, "feed:" + quote.source + "@" + quote.asOf)`.
   - 의존 = `FxRateQuoteRepository`(domain port) + `FxRateFeedSettings`(app port; **확장**) + `ClockPort`.
     base 는 항상 KRW(`LedgerReportingCurrency.BASE` → `Currency`).
3. **`FxRateFeedSettings` 확장** — `boolean feedEnabled()` + `java.time.Duration staleAfter()`(또는 `long maxAgeMinutes()`).
   `FxRateFeedProperties` 에 `maxAgeMinutes`(default 1440=24h) 필드 + 두 메서드 구현. (FIN-BE-031 의 net-zero 기본
   `enabled=false` 유지 — feedEnabled()=false 면 폴백 비활성, 종전 동작.)
4. **`FxRateUnavailableException`** — `LedgerErrors` 에 추가, code `"FX_RATE_UNAVAILABLE"`.
   `GlobalExceptionHandler.STATUS_BY_CODE` 에 `Map.entry("FX_RATE_UNAVAILABLE", UNPROCESSABLE_ENTITY)` 추가(422).
5. **운영자 use-case 배선** (net-zero 경계 — 아래 순서 엄수):
   - `SettleForeignPositionUseCase`: 포지션 로드 + `foreignBalance==0` no-op + `settleForeignMinor` 검증 **이후**,
     compute **이전**에 `ResolvedFxRate r = resolver.resolve(BASE, cmd.currency(), cmd.settlementRate())` 호출.
     `FxSettlementPolicy.settle`/`computeFifo`/fallback 에 넘기던 `cmd.settlementRate()` 를 **`r.rate()`** 로 교체.
     감사 reason 은 `r.fromFeed()` 일 때만 source 주석 append(manual 경로 reason byte-identical).
   - `RevalueForeignBalanceUseCase`: `NO_POSITION` no-op **이후**, `FxRevaluationPolicy.revalue` **이전**에 resolve.
     `revalue` + `distributeRevaluationToLots`/`markToSpot` 의 `cmd.closingRate()` 를 **`r.rate()`** 로 교체.
     감사 reason 은 `r.fromFeed()` 일 때만 source append.
   - **순서 근거(net-zero)**: replay/no-position no-op 은 환율 불필요 → resolve 보다 **앞**(생략 환율로도 200 no-op).
     환율 제공 시 resolve 는 그 값을 그대로 반환 → 모든 경로 byte-identical.
6. **레지스트리 3종 갱신** (각 도메인 코드가 자라온 선례 — 9th REVALUATION_RATE_INVALID / 10th SETTLEMENT_RATE_INVALID):
   - `specs/contracts/http/ledger-api.md` § Error codes + § 10/11(재평가·결제) — 환율 optional + FX_RATE_UNAVAILABLE 문서화.
   - `platform/error-handling.md` ledger-service 도메인 섹션 — `| FX_RATE_UNAVAILABLE | 422 | … (ledger-service `FxRateUnavailableException`) |` 행 추가
     (**shared 파일 — ledger 섹션에만 도메인 코드 추가; 다른 도메인·공통 텍스트 무수정**).
   - `specs/services/ledger-service/architecture.md` — FX revaluation/settlement 섹션에 "rate omission → feed fallback + staleness" 명시.

**Out of scope**: 콘솔 표면, 실 공개 FX API 어댑터, `fx_rate_quote_history`, per-tenant 특수환율 override, 폴러/캐시 구조 변경(FIN-BE-031 그대로).

## Acceptance Criteria

- **AC-1 — net-zero (manual rate).** 환율을 **제공**한 모든 재평가·결제는 종전과 byte-identical(엔트리·감사 reason·로트
  분배·실현/미실현 손익·멱등·no-op 동일). 기존 settlement/FIFO/revaluation/reconciliation IT 전부 GREEN. 기본
  설정(feed disabled)에서 환율 제공 경로 무변경.
- **AC-2 — 생략 + fresh 캐시 → 폴백.** feed enabled + `mode=stub`(또는 캐시 적재됨) + 환율 생략 → 캐시 quote 로 결제/재평가
  성공. 적용 quote 의 source/as_of 가 audit_log reason(또는 엔트리 reference)에 기록(추적성, audit-heavy).
- **AC-3 — 생략 + (disabled | 부재 | stale) → fail-closed.** 환율 생략 + (피드 disabled OR 캐시 없음 OR stale) →
  `422 FX_RATE_UNAVAILABLE`; **아무것도 persist 안 함, 멱등키 미소비**. 추정/stale 환율로 손익 인식 0.
- **AC-4 — staleness 경계.** `now − quote.asOf ≤ maxAge` = fresh(폴백 적용), 초과 = stale(거부). maxAge 경계 단위테스트로 고정.
- **AC-5 — no-op 환율 불필요.** 환율 생략 + (no-position | replay) → 종전 200 no-op(환율 해석 미진입, 422 아님).
- **AC-6 — 레지스트리 정합.** `FX_RATE_UNAVAILABLE` 가 `LedgerErrors` + `GlobalExceptionHandler.STATUS_BY_CODE`(422) +
  `ledger-api.md` § Error codes + `platform/error-handling.md` ledger 섹션 **4곳 모두** 등재(드리프트 0). F8(운영자 트리거)
  불변 — 피드는 환율만 공급, 분개 자동생성 0.

## Related Specs

- `projects/finance-platform/docs/adr/ADR-002-realtime-fx-rate-feed.md` (D3/D4 — 본 태스크 근거)
- `projects/finance-platform/tasks/done/TASK-FIN-BE-031-fx-rate-feed-port-cache-poller.md` (캐시·포트·폴러 토대)
- `projects/finance-platform/specs/services/ledger-service/architecture.md` (FX revaluation/settlement)
- `platform/error-handling.md` (§ ledger-service 도메인 에러 레지스트리 — line 826 "Error codes must be registered before use")

## Related Contracts

- `projects/finance-platform/specs/contracts/http/ledger-api.md` — § Error codes + § 10/11(환율 optional, FX_RATE_UNAVAILABLE)

## Edge Cases

- 환율 생략 + 캐시 fresh 지만 quote.rate ≤ 0(이론상) → 기존 `FxSettlementPolicy`/`FxRevaluationPolicy` 의 rate>0
  가드가 SETTLEMENT/REVALUATION_RATE_INVALID(422)로 잡음(해석은 값만 공급, 검증은 policy 유지).
- 재평가 AT_SPOT 판정은 환율 필요 → 생략 시 resolve 먼저(폴백/거부) 후 AT_SPOT 계산. 캐시 fresh 면 정상 AT_SPOT no-op 가능.
- maxAge 경계 정확히 같을 때 = fresh(≤). `as_of` 기준(제공자 명시 시점); 구현 시 `as_of` vs `fetched_at` 택1 명시(권장 as_of).

## Failure Scenarios

- 환율 생략 + 피드 장애로 캐시 stale → 422 FX_RATE_UNAVAILABLE, 운영자는 수동 환율로 재시도(graceful).
- 환율 제공 비-숫자 문자열 → 기존 422(unchanged).
- 동시 제출(멱등) — resolve 는 read-only, 멱등키 처리는 기존 경로 그대로(net-zero).
