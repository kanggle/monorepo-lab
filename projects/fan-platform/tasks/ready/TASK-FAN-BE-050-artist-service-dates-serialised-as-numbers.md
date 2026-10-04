# Task ID

TASK-FAN-BE-050

# Title

artist-service 가 날짜를 **숫자로** 내보낸다 — 실효 `ObjectMapper` 를 고쳐 계약대로 ISO 문자열로

# Status

ready

# Owner

fan-platform

# Task Tags

- backend
- contract
- demo

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 — 설정 한 곳 + 응답 회귀 테스트 + 소비자 전수. 🔴 데모 반영은 **AMI 재굽기** 뒤(소유자 승인).

---

# 배경 (2026-10-04 UTC, 19차 데모 창 실측)

콘솔 `/fan/agencies` 가 «팬 디렉터리 정보를 일시적으로 불러올 수 없습니다». Vercel 로그에서 같은 요청이 `fan_ok status=200 path=/api/v1/agencies` 다음 **1ms 뒤** `fan_error` — artist-service 는 200 을 줬고, 콘솔이 **본문 파싱**에서 실패했다(`TASK-MONO-759` § 데모 창).

기전은 저장소에 이미 적혀 있다: `config/RedisCacheConfig` 가 `ObjectMapper` `@Bean` 을 내고, Boot 의 `JacksonAutoConfiguration` 은 `@ConditionalOnMissingBean` 이라 물러난다. 그 매퍼는 `WRITE_DATES_AS_TIMESTAMPS` 가 켜져 있어 `Instant` 가 `1785370282.333000000` 로 나간다(`GlobalExceptionHandlerEnvelopeContractTest` 의 javadoc 이 실측으로 적음). TASK-FAN-BE-038 은 **오류 봉투만** 문자열로 바꿨고 «매퍼 자체 수리는 범위 밖» 으로 남겼다 — 그 남은 것이 성공 응답의 `AgencyView`·`ArtistView`·`ArtistGroupView`·`FandomView` 의 `java.time` 필드다.

계약: `projects/fan-platform/specs/contracts/http/artist-api.md` 는 시각을 ISO-8601 문자열로 적는다. 콘솔 `fan-types.ts` 도 문자열을 기대한다. ⇒ **생산자가 계약을 어긴다.** 소유자 결정(2026-10-04): 콘솔이 숫자를 받아주는 우회는 하지 않고 생산자를 고친다.

⚪ 응답 본문 자체는 직접 보지 못했다(콘솔 운영자 토큰 없이는 못 받는다). AC-0 이 그 공백을 먼저 메운다.

# Goal

artist-service 의 모든 HTTP 성공 응답에서 `Instant` 는 ISO-8601 문자열, `LocalDate` 는 `yyyy-MM-dd` 문자열로 나간다 — Boot 기본과 같게. 콘솔 팬 디렉터리가 열린다.

# Scope

## In Scope

- 실효 `ObjectMapper` 수리: `RedisCacheConfig` 의 빈을 없애거나(Boot 자동설정에 맡김) `WRITE_DATES_AS_TIMESTAMPS` 를 끈 매퍼로. 어느 쪽이든 **실행 중 서비스가 쓰는 그 매퍼**가 기준이다.
- 응답 형식을 고정하는 회귀 테스트(아래 AC-2).
- 소비자 전수(AC-3)와, 숫자/배열 형식에 맞춰져 있던 소비자가 있으면 그 수정.
- Redis 디렉터리 캐시: 같은 매퍼를 쓰므로 옛 형식으로 저장된 항목을 새 코드가 읽을 수 있는지(또는 캐시 키 버전을 올리는지) 정한다.

## Out of Scope

- 콘솔 쪽 스키마 완화(소유자 결정으로 하지 않음).
- 다른 fan 서비스의 매퍼(그쪽은 Boot 기본 — 필요하면 AC-3 에서 확인만).
- Kafka 이벤트 계약(`ArtistEventPublisherAdapter` 는 이미 `.toString()` 으로 문자열).

# Acceptance Criteria

- [ ] **AC-0 (실측 먼저)** — 고치기 전에 현재 형식을 **실제 서비스 매퍼로** 잰다: 슬라이스/통합 테스트에서 `GET /api/v1/agencies`(그리고 artists · groups · fandom 하나씩)의 본문을 찍어 `createdAt` 등이 숫자인지, `LocalDate` 가 배열(`[2020,1,1]`)인지 적는다. 기전이 다르면(이미 문자열이면) STOP — 콘솔 실패의 원인을 다시 찾는다.
- [ ] **AC-1** — 고친 뒤 같은 요청의 본문에서 `Instant` = ISO-8601 문자열 · `LocalDate` = `yyyy-MM-dd`. `artist-api.md` 의 예시와 일치.
- [ ] **AC-2** — 회귀 테스트: 서비스가 **실제로 해석하는** `ObjectMapper`(컨텍스트에서 꺼낸 것, 손으로 만든 것 아님)로 `AgencyView`·`ArtistView`·`ArtistGroupView`·`FandomView` 를 직렬화해 날짜 필드가 문자열임을 단언. bite: 매퍼를 옛 것으로 되돌리면 빨강.
- [ ] **AC-3** — 소비자 전수: artist-service 응답을 파싱하는 곳(콘솔 `features/fan-directory`, 팬 웹 `projects/fan-platform/web/fan-platform-web`, 다른 fan 서비스의 HTTP 클라이언트, 캐시)을 grep 으로 모으고, 숫자·배열 날짜를 전제로 한 곳이 있으면 이 PR 에서 함께 고친다. 결과 목록을 이 파일에 적는다(0건이면 «0건 + 어디를 봤는지»).
- [ ] **AC-4** — Redis 캐시 호환: 옛 형식 항목을 새 코드가 읽는 테스트, 또는 캐시 키 버전 상향 + 그 이유.
- [ ] **AC-5 (데모 창, 재굽기 뒤)** — 콘솔 `platform@demo.com` / `fan-platform` → `/fan/agencies` · `/fan/artists` · `/fan/groups` 가 목록으로 뜬다. 같은 창에서 `TASK-MONO-759` 의 판정(소속사에 `default` 셀러 연결 → 200 · `store_seller_id` 변경)을 이어서 한다.

# Related Specs

- `projects/fan-platform/specs/services/artist-service/architecture.md`
- `platform/error-handling.md`(timestamp 문자열 — 같은 원칙)

# Related Contracts

- `projects/fan-platform/specs/contracts/http/artist-api.md` (§ Agencies · § Artists · § Artist groups · § Fandom)

# Edge Cases

1. `RedisCacheConfig` 빈을 지우면 Boot 매퍼가 돌아오지만, 그 빈에 기대던 다른 코드(캐시 직렬화기)가 있으면 그 경로도 Boot 매퍼를 써야 한다.
2. `LocalDate` 배열 형식을 팬 웹이 이미 처리하고 있다면, 형식이 바뀌는 순간 팬 웹 화면이 깨진다 — AC-3 이 이것을 잡는다.
3. `@JsonFormat` 을 필드마다 붙이는 해법은 새 필드에서 다시 빠진다 — 매퍼 수준에서 고친다.

# Failure Scenarios

1. 테스트가 손으로 만든 `new ObjectMapper()` 로 단언해 초록인데 서비스는 여전히 숫자를 낸다 — AC-2 가 «컨텍스트의 매퍼» 를 요구하는 이유(BE-038 javadoc 이 같은 함정을 적었다).
2. 캐시에 남은 옛 항목을 못 읽어 첫 요청들이 500 — AC-4.
3. 재굽기 전에 «고쳐졌다» 고 닫는다 — 데모 판정(AC-5)은 재굽기 창에서만.
