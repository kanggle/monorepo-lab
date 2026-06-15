# TASK-FIN-BE-033 — FX rate quote read endpoint (operator feed visibility + staleness)

- **Status**: ready
- **Project**: finance-platform
- **Service**: ledger-service
- **Domain / traits**: fintech / [transactional, regulated, audit-heavy]
- **Increment**: 25th ledger increment (ADR-002 follow-up — operator read surface over the fx_rate_quote cache)
- **Analysis model**: Opus 4.8 / **Implementation model**: Sonnet 4.6 (additive read EP — mirror FIN-BE-028 lots read)

## Goal

운영자가 FX 환율 피드 캐시(`fx_rate_quote`, FIN-BE-031)의 **현재 상태**를 조회하는 read-only 엔드포인트.
각 통화쌍의 환율 + 출처 + as-of/fetched-at + **나이(age)와 stale 여부**(FIN-BE-032 staleness 가드와 동일 기준)를
노출해, "피드가 지금 어떤 환율을 들고 있고 얼마나 오래됐나"(= `FX_RATE_UNAVAILABLE` 폴백 성패의 근거)를
가시화한다. **read-only·net-zero·마이그레이션 0**. FIN-BE-028(lots read EP) 패턴을 그대로 미러.

## Scope

**In scope** (ledger-service only):

1. **read use-case** `application/GetFxRatesUseCase` (`@Transactional(readOnly = true)`):
   `fxRateQuoteRepository.findAll()` → 각 quote 를 staleness 계산과 함께 view 로 매핑.
   stale 판정 = `now − asOf > settings.staleAfter()`(FIN-BE-032 의 `ResolveEffectiveFxRate` 와 **동일 경계**;
   `==` 은 fresh). `age` = `now − asOf`(초). `now` = `ClockPort`. 결정적 정렬(base, foreign ASC).
   의존 = `FxRateQuoteRepository` + `FxRateFeedSettings`(feedEnabled()/staleAfter()) + `ClockPort`.
2. **views** `application/view/FxRatesView` + `FxRateView`:
   - `FxRatesView(boolean feedEnabled, List<FxRateView> rates)` — top-level 에 피드 활성 여부(폴백 자체가 켜져
     있는지) 노출.
   - `FxRateView(String baseCurrency, String foreignCurrency, BigDecimal rate, Instant asOf, String source,
     Instant fetchedAt, long ageSeconds, boolean stale)`.
3. **DTOs** `presentation/dto/FxRatesResponse` + `FxRateResponse`:
   - rate = **문자열**(exact decimal, F5 규약 — `closingRate`/`settlementRate` wire 와 동일하게 정수 minor 가
     아니라 환율은 decimal 문자열), times = ISO Instant, `ageSeconds`(number), `stale`/`feedEnabled`(boolean).
4. **controller** `presentation/controller/FxRateController` — `GET /api/finance/ledger/fx-rates`,
   `ApiEnvelope<FxRatesResponse>`. `ActorContextResolver.currentOrThrow()` 로 인증 강제(다른 EP 동일);
   **테넌트 필터 없음**(시장환율은 테넌트-무관 글로벌 — `fx_rate_quote` 에 tenant 컬럼 없음). 멱등키 없음(순수 read).
   보안 체인이 `/api/finance/ledger/**` 를 `.authenticated()` 로 이미 커버하는지 확인(커버 시 추가 설정 불필요).
5. **Docs** — `specs/contracts/http/ledger-api.md` 에 신규 섹션(§ FX rates read) + `architecture.md` FX rate feed
   섹션에 read 표면 1문단 추가.

**Out of scope**: 콘솔 프런트(별도 PC-FE 태스크), 수동 refresh 트리거(별도), per-pair drill / history(`fx_rate_quote_history` deferred), 마이그레이션/쓰기 경로 일체.

## Acceptance Criteria

- **AC-1 — read-only·net-zero.** 어떤 쓰기 경로·기존 use-case·마이그레이션도 수정 0. 기존 IT 전부 GREEN.
  빈 캐시 → `{ feedEnabled, rates: [] }`(200, 404 아님).
- **AC-2 — quote 노출.** 적재된 각 통화쌍에 대해 base/foreign/rate(문자열)/source/asOf/fetchedAt 정확 반환,
  (base, foreign) ASC 정렬.
- **AC-3 — staleness.** `now − asOf ≤ staleAfter` → `stale=false`, 초과 → `stale=true`. `ageSeconds` 정확.
  경계(`==staleAfter`)=fresh. FIN-BE-032 `ResolveEffectiveFxRate` 와 동일 기준(드리프트 0).
- **AC-4 — feedEnabled.** top-level `feedEnabled` 가 `FxRateFeedSettings.feedEnabled()` 반영(폴백 활성 여부 가시화).
- **AC-5 — 인증.** 미인증 호출 거부(다른 ledger EP 와 동일 `.authenticated()` 체인); 인증된 운영자는 글로벌 quote
  조회(테넌트 필터 없음).
- **AC-6 — rate 문자열(F5).** wire 에서 rate 는 decimal **문자열**(float 금지). times 는 ISO.

## Related Specs

- `projects/finance-platform/docs/adr/ADR-002-realtime-fx-rate-feed.md` (D2 캐시 — 본 read 표면의 대상)
- `projects/finance-platform/tasks/done/TASK-FIN-BE-031-fx-rate-feed-port-cache-poller.md` (캐시·repo·settings 토대)
- `projects/finance-platform/tasks/done/TASK-FIN-BE-032-fx-rate-feed-consumption-staleness.md` (staleness 기준 동일)
- `projects/finance-platform/tasks/done/TASK-FIN-BE-028-fx-position-lots-read-endpoint.md` (미러할 read EP 패턴)

## Related Contracts

- `projects/finance-platform/specs/contracts/http/ledger-api.md` — 신규 § FX rates read EP

## Edge Cases

- 빈 캐시(피드 미적재) → 빈 배열 + `feedEnabled` 그대로(200).
- 피드 disabled 인데 캐시에 옛 quote 잔존 → quote 는 반환하되 `feedEnabled=false`(폴백 비활성 신호) + 대개 `stale=true`.
- `as_of` 가 미래(시계 skew) → `ageSeconds` 음수 가능 → 그대로 노출(stale=false), 클램프하지 않음(진단 투명성).

## Failure Scenarios

- 미인증 → 401/403(보안 체인). read 경로엔 도메인 실패 없음(순수 조회).
