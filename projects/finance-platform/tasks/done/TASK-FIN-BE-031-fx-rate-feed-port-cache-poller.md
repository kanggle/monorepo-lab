# TASK-FIN-BE-031 — FX rate feed: provider port + quote cache + scheduled poller (shadow)

- **Status**: done
- **Project**: finance-platform
- **Service**: ledger-service
- **Domain / traits**: fintech / [transactional, regulated, audit-heavy]
- **Increment**: 23rd ledger increment (ADR-002 D1/D2/D5/D6 — first external HTTP integration, shadow)
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus (외부의존·인프라·새 데이터모델)

## Goal

ADR-002 실행 1단계: 외부 FX 환율을 가져오는 **outbound port + config-gated 어댑터(noop/stub/http) +
`fx_rate_quote` 캐시 테이블 + 스케줄드 폴러**를 **shadow**(캐시 적재만; 운영자 경로 미참조)로 도입한다.
운영자 결제·재평가 경로는 **한 줄도 바뀌지 않는다** — net-zero. 캐시를 실제로 소비하는 폴백·staleness 는
FIN-BE-032(D3/D4).

## Scope

**In scope** (ledger-service only):

1. **Outbound port** `application/port/outbound/FxRateProviderPort` — `Optional<RateQuote> latestQuote(Currency base, Currency foreign)`;
   nested value `record RateQuote(BigDecimal rate, Instant asOf, String source)` (rate = base-minor-per-foreign-minor,
   기존 `closingRate`/`settlementRate` 와 **동일 단위 규약**). 외부 호출 실패/미지원 통화쌍 → `Optional.empty()`.
2. **세 어댑터** (`infrastructure/fxrate/`), 정확히 하나만 활성(`financeplatform.ledger.fxrate.mode`):
   - `NoopFxRateProviderAdapter` — `@ConditionalOnProperty(name=...mode, havingValue="noop", matchIfMissing=true)` →
     항상 `Optional.empty()` (기본·net-zero: 어떤 외부호출도 없음).
   - `StubFxRateProviderAdapter` — `havingValue="stub"` → properties 의 고정 환율맵에서 반환(`asOf=clock.now()`,
     `source="stub"`). 외부 의존 없이 데모·IT.
   - `HttpFxRateProviderAdapter` — `havingValue="http"` → 설정된 base URL 을 **`ResilienceClientFactory.buildRestClient`**
     (libs/java-common; **절대 `new RestTemplate()` 금지**)로 호출, 단순 JSON(`{ "rate": "1300.0", "asOf": "<ISO>" }`
     형태 — 구현 시 정확 명시) 파싱. **best-effort·never-throw**: 비-2xx/연결거부/타임아웃/파싱실패 → `Optional.empty()`
     (catch-all). `source="http:<host>"`.
3. **캐시 테이블 + 도메인** — 추가 마이그레이션 `V12__add_fx_rate_quote.sql`:
   `fx_rate_quote(base_currency VARCHAR(3), foreign_currency VARCHAR(3), rate DECIMAL(20,8) NOT NULL,
   as_of DATETIME(6) NOT NULL, source VARCHAR(64) NOT NULL, fetched_at DATETIME(6) NOT NULL,
   PRIMARY KEY (base_currency, foreign_currency))` — InnoDB/utf8mb4, **신규 테이블만**, backfill 0(빈 캐시=자동적용 비가용=net-zero).
   엔티티 `domain/journal/FxRateQuote`(`@IdClass FxRateQuoteId`(baseCurrency, foreignCurrency)) + 포트
   `FxRateQuoteRepository`(`findLatest(base, foreign)→Optional`, `save` upsert, `findAll` 또는 list — IT 검증용) +
   JPA 어댑터 + Spring Data repo. (per-tenant 아님 — 시장환율은 테넌트 무관, PK 에 tenant 없음.)
4. **적재 use-case** `application/RefreshFxRateQuotesUseCase` (`@Transactional`) — 설정된 통화쌍 목록을 순회,
   `FxRateProviderPort.latestQuote` 호출, 결과 present 면 `fx_rate_quote` upsert(`fetched_at=clock.now()`,
   `as_of`/`source`/`rate`=어댑터 값). 개별 쌍 실패가 다른 쌍을 막지 않게(쌍별 try). 반환=upsert 된 쌍 수.
5. **스케줄드 폴러** `infrastructure/fxrate/FxRateFeedPoller` — `@Component @ConditionalOnProperty(
   name="financeplatform.ledger.fxrate.enabled", havingValue="true")` (**matchIfMissing 없음 → 기본 OFF = net-zero**;
   `LedgerOutboxPublisher` 의 `@Scheduled`+게이트 패턴 미러, 단 outbox 는 default ON 인 반면 feed 는 default OFF).
   `@Scheduled(fixedDelayString="${financeplatform.ledger.fxrate.poll-interval-ms:60000}", initialDelayString=...)` →
   `RefreshFxRateQuotesUseCase` 호출, **never-throw**(catch-all 로그). (ShedLock 단일리더는 이 서비스에 미도입 —
   현 `@Scheduled`+게이트 패턴 유지; multi-instance 단일리더 가드는 deferred. ADR-002 D4 의 "ShedLock" 은 sketch.)
6. **Config properties** `infrastructure/fxrate/FxRateFeedProperties` (`@ConfigurationProperties("financeplatform.ledger.fxrate")`):
   `enabled`(default false), `mode`(default "noop"), `pollIntervalMs`, `pairs`(폴링할 통화쌍 목록, 예 `["USD","EUR","JPY"]`
   — base 는 KRW 고정), `stub`(통화→rate 맵), `http`(baseUrl, connectTimeoutMs=2000, readTimeoutMs=5000).
7. **Docs** — `specs/services/ledger-service/architecture.md` 에 "FX rate feed (ADR-002, shadow)" 섹션 추가
   (port/어댑터/캐시/폴러/net-zero·shadow 명시); `specs/contracts/http/ledger-api.md` 변경 **없음**(EP 0 — shadow,
   외부 채널은 contract-http 아님; 필요시 architecture 만).

**Out of scope** (FIN-BE-032 이후): 재평가·결제의 환율-생략 폴백, staleness 가드, `FX_RATE_UNAVAILABLE`, 적용 quote
감사기록, 특정 공개 FX API 의 정확한 응답 스키마(http 어댑터는 generic), 콘솔 표면.

## Acceptance Criteria

- **AC-1 — net-zero/shadow (가장 중요).** `SettleForeignPositionUseCase`·`RevalueForeignBalanceUseCase`·
  `FxSettlementPolicy`·`FxRevaluationPolicy` 및 그 어떤 운영자 경로도 **수정 0**. 기존 settlement/FIFO/revaluation/
  reconciliation IT 전부 GREEN. 기본 설정(`mode=noop`, `enabled=false`)이면 폴러 빈 미생성·외부호출 0·캐시 빈 채로.
- **AC-2 — V12 additive.** 신규 `fx_rate_quote` 테이블만 생성(복합 PK). 기존 테이블/CHECK 무변경, backfill 0.
  Testcontainers 부팅 시 Flyway clean.
- **AC-3 — 어댑터 선택.** `mode=noop`(기본)→`empty()`; `mode=stub`→설정 환율; `mode=http`→`ResilienceClientFactory`
  RestClient 호출. 정확히 하나의 어댑터 빈만 활성(disjoint `havingValue`, 하나만 `matchIfMissing`).
- **AC-4 — http 어댑터 best-effort (MockWebServer 단위/슬라이스).** 2xx→파싱 quote; 5xx/연결거부/타임아웃/파싱실패→
  `empty()` (절대 throw 안 함). `ResilienceClientFactory.buildRestClient` 사용(직접 RestTemplate 금지).
- **AC-5 — 폴러+적재 IT (Testcontainers).** 게이트 enabled + `mode=stub` 에서 `RefreshFxRateQuotesUseCase`(또는 폴러 1틱)
  실행 후 `fx_rate_quote` 가 설정 통화쌍별 행 보유(rate/as_of/source="stub"/fetched_at 채워짐). 같은 쌍 재적재=upsert
  (행 수 불변, fetched_at 갱신). 그리고 **운영자 경로 미변경 확인**(이 IT 내 manual-rate settlement 은 종전대로 동작).
- **AC-6 — best-effort 폴러.** 개별 통화쌍 조회 실패가 폴러를 죽이지 않음(다른 쌍은 적재; 폴러 catch-all).

## Related Specs

- `projects/finance-platform/docs/adr/ADR-002-realtime-fx-rate-feed.md` (D1/D2/D5/D6 — 본 태스크의 근거)
- `projects/finance-platform/specs/services/ledger-service/architecture.md` (Increment Scope — live FX rate feed)
- `.claude/skills/backend/external-http-integration/SKILL.md` (config-gated port + noop 기본 + ResilienceClientFactory + MockWebServer)
- `.claude/skills/backend/scheduled-tasks/SKILL.md` (스케줄드 폴러 게이트·멱등)

## Related Contracts

- 없음 (shadow; 외부 아웃바운드 채널, contract-http EP 0). architecture.md 에만 기록.

## Edge Cases

- 미지원 통화쌍 / 어댑터 empty → 그 쌍은 캐시에 쓰지 않음(부분 적재 허용).
- `mode=http` baseUrl 미설정/blank → 어댑터가 `empty()` 반환(fail-soft), 폴러 정상.
- 폴러 게이트 ON + `mode=noop` → 폴러는 돌지만 항상 empty → 캐시 빈 채(무해).
- 동일 통화쌍 동시 upsert → 복합 PK last-write-wins.

## Failure Scenarios

- 외부 제공자 5xx/타임아웃/연결거부 → http 어댑터 `empty()`(never-throw), 캐시는 직전 quote 유지, 폴러 로그.
- 폴러 틱 중 예외 → catch-all 로그, 다음 틱 정상(스케줄러 죽지 않음).
- Flyway V12 충돌 → 없음(신규 테이블, 버전 V11 다음).
