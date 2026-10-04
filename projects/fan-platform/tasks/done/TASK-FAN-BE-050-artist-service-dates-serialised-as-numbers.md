# Task ID

TASK-FAN-BE-050

# Title

artist-service 가 날짜를 **숫자로** 내보낸다 — 실효 `ObjectMapper` 를 고쳐 계약대로 ISO 문자열로

# Status

done

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

- [x] **AC-0 (실측 먼저)** — 고치기 전에 현재 형식을 **실제 서비스 매퍼로** 잰다: 슬라이스/통합 테스트에서 `GET /api/v1/agencies`(그리고 artists · groups · fandom 하나씩)의 본문을 찍어 `createdAt` 등이 숫자인지, `LocalDate` 가 배열(`[2020,1,1]`)인지 적는다. 기전이 다르면(이미 문자열이면) STOP — 콘솔 실패의 원인을 다시 찾는다.
  - **측정 (2026-10-04 UTC)**: 고치기 전 코드로 `git stash` 해 원래 `RedisCacheConfig` 를 되돌리고, `ArtistObjectMapperDateFormatContractTest`(컨텍스트에서 꺼낸 실 매퍼, `AgencyView`/`ArtistView`/`ArtistGroupView`/`FandomView` 전부)를 돌렸다 — **4개 테스트 전부 RED**. 단언 실패 메시지가 실제 값을 담았다: `Instant` 필드(`createdAt`/`updatedAt`/`publishedAt`/`joinedAt`) → JSON 숫자 `1.785370282333E9`(= `1785370282.333000000`초, `GlobalExceptionHandlerEnvelopeContractTest` 의 기존 javadoc 측정치와 일치) · `LocalDate` 필드(`debutDate`/`foundedAt`) → 3원소 JSON 배열 `[2024,5,1]`. **이미 문자열이 아니었다 — STOP 조건 미충족, 구현 진행.**
- [x] **AC-1** — 고친 뒤 같은 요청의 본문에서 `Instant` = ISO-8601 문자열 · `LocalDate` = `yyyy-MM-dd`. `artist-api.md` 의 예시와 일치.
  - 매퍼 수리(아래 구현 노트) 후 같은 테스트 4개 **전부 GREEN**. `assertIsoInstant`/`assertLocalDate` 가 `.isTextual()` + 포맷 정규식(`\d{4}-\d{2}-\d{2}`, 끝 `Z`)을 단언.
- [x] **AC-2** — 회귀 테스트: 서비스가 **실제로 해석하는** `ObjectMapper`(컨텍스트에서 꺼낸 것, 손으로 만든 것 아님)로 `AgencyView`·`ArtistView`·`ArtistGroupView`·`FandomView` 를 직렬화해 날짜 필드가 문자열임을 단언. bite: 매퍼를 옛 것으로 되돌리면 빨강.
  - 신설 `ArtistObjectMapperDateFormatContractTest`(네 뷰 전부, `ApplicationContextRunner` + 실 `RedisCacheConfig` 빈 구성으로 컨텍스트 매퍼를 꺼낸다 — 손으로 만든 매퍼 아님). **bite**: AC-0 측정이 곧 이 bite 다 — `RedisCacheConfig` 를 옛 버전(버그 있는 `ObjectMapper` `@Bean`)으로 되돌린 상태에서 돌려 4/4 RED, 복원 후 4/4 GREEN. `GlobalExceptionHandlerEnvelopeContractTest`(TASK-FAN-BE-038, 기존 에러봉투 계약 테스트)의 전제 단언도 뒤집어 갱신 — 과거엔 "숫자를 낸다" 를 가드했고 지금은 "ISO 문자열을 낸다" 를 가드.
- [x] **AC-3** — 소비자 전수: artist-service 응답을 파싱하는 곳(콘솔 `features/fan-directory`, 팬 웹 `projects/fan-platform/web/fan-platform-web`, 다른 fan 서비스의 HTTP 클라이언트, 캐시)을 grep 으로 모으고, 숫자·배열 날짜를 전제로 한 곳이 있으면 이 PR 에서 함께 고친다. 결과 목록을 이 파일에 적는다(0건이면 «0건 + 어디를 봤는지»).
  - **0건 — 수정 불필요.** 어디를 봤는지:
    1. 콘솔 `projects/platform-console/apps/console-web/src/features/fan-directory/api/fan-types.ts` — `AgencySchema`/`ArtistSchema`/`ArtistGroupSchema`/`GroupMemberSchema` 의 모든 날짜 필드가 이미 `nullableString`(`z.string().nullable().optional()`) — 숫자/배열을 전제한 곳 없음.
    2. 팬 웹 `projects/fan-platform/web/fan-platform-web/src/entities/artist/types.ts` — `Artist`/`Fandom` 의 날짜 필드가 이미 `string | null` — 같음.
    3. 팬 웹 `features/artist/ui/ArtistCard.tsx` 등 UI 컴포넌트 — `new Date(...)` 등 날짜 파싱 패턴 grep, 매치 없음(표시용 문자열을 그대로 보여줄 뿐).
    4. 다른 fan 서비스의 HTTP 클라이언트 — grep `artist-service|/api/artists|/api/agencies|/api/artist-groups|/api/fandoms` across `projects/fan-platform/apps/*/src/main`: artist-service 자신 외에는 **community-service 의 `HttpArtistAccountChecker`** 뿐이며, 그 경로(`/internal/artists/exists`)는 `{ "exists": boolean }` 만 받는다 — 날짜 필드 없음, 영향 없음.
    5. Redis 디렉터리 캐시(`ArtistDirectoryCacheAdapter`) — 같은 주입 `ObjectMapper` 를 쓰므로 매퍼 수리로 함께 고쳐짐. 호환성은 AC-4.
    6. Kafka 아웃박스(`ArtistEventPublisherAdapter`) — 범위 밖으로 문서화된 그대로 전 필드(`occurredAt`/`publishedAt`/`archivedAt`/`debutDate`)가 이미 `.toString()` 으로 맵에 들어가 매퍼를 거치지 않음 — 확인만, 변경 없음.
- [x] **AC-4** — Redis 캐시 호환: 옛 형식 항목을 새 코드가 읽는 테스트, 또는 캐시 키 버전 상향 + 그 이유.
  - **키 버전 상향 불필요 — 테스트로 증명.** 신설 `ArtistDirectoryCacheOldFormatCompatibilityTest`: AC-0 이 측정한 그 옛 포맷(숫자 `Instant`, 배열 `LocalDate`) 그대로 손으로 적은 JSON 을 수리된(Boot 자동설정) 매퍼로 `objectMapper.readValue(..., DirectorySearchResult.class)` 해 값까지(= `Instant.parse(...)`, `LocalDate.of(2024,5,1)`) 일치함을 단언 — **GREEN**. 이유: `WRITE_DATES_AS_TIMESTAMPS` 는 **쓰기**에만 영향을 주고, `jackson-datatype-jsr310` 의 `Instant`/`LocalDate` 역직렬화기는 토큰 타입(숫자 vs 문자열, 배열 vs 문자열)을 보고 분기하므로 옛 항목도 새 매퍼로 읽힌다.
- [ ] **AC-5 (데모 창, 재굽기 뒤)** — 콘솔 `platform@demo.com` / `fan-platform` → `/fan/agencies` · `/fan/artists` · `/fan/groups` 가 목록으로 뜬다. 같은 창에서 `TASK-MONO-759` 의 판정(소속사에 `default` 셀러 연결 → 200 · `store_seller_id` 변경)을 이어서 한다.
  - ⚪ **의도적으로 열어 둠.** AMI 재굽기 뒤, 소유자 승인 창에서만 판정 가능 — 이 PR 의 범위가 아니다(원 태스크 본문의 지시와 일치).

# 구현 기록 (2026-10-04 UTC)

- **수리**: `config/RedisCacheConfig` 의 `ObjectMapper @Bean`(`new ObjectMapper().findAndRegisterModules()`, `@ConditionalOnMissingBean`)을 **삭제**. 그 결과 Boot 의 `JacksonAutoConfiguration` 이 유일한 `ObjectMapper` 공급자가 되어 `WRITE_DATES_AS_TIMESTAMPS=false`(Boot 기본값) + `JavaTimeModule` 이 적용된다. `ArtistDirectoryCacheAdapter`·`ArtistEventPublisherAdapter` 는 둘 다 생성자 주입으로 같은 빈을 받으므로 별도 배선 변경 없이 수리된 매퍼를 받는다.
- 필드별 `@JsonFormat` 은 쓰지 않았다(엣지케이스 3 — 새 필드에서 다시 빠지는 함정).
- `GlobalExceptionHandlerEnvelopeContractTest`(TASK-FAN-BE-038)와 `ApiErrorBody` 의 javadoc을 과거형으로 갱신 — 둘 다 "artist-service 의 매퍼가 숫자를 낸다" 는 전제가 이 수리로 거짓이 되었음을 반영.
- **빌드/테스트**: `./gradlew :projects:fan-platform:apps:artist-service:test`(기본 `test` 태스크, `@Tag("integration")` 제외) **rc=0**, 전체 GREEN. Testcontainers 통합 테스트(`ArtistServiceIntegrationTest` 등, `@Tag("integration")`)는 이 Windows 호스트에 Docker 가 없어(`docker info` rc=1) **로컬에서 실행하지 못했다** — CI 에서 돈다는 전제로 남겨 둔다.
- **영향 범위 확인**: 콘솔/팬웹/타 서비스 수정 **0건**(AC-3). 캐시 키 버전 변경 **없음**(AC-4, 호환 테스트로 증명). 데모 반영은 AMI 재굽기 뒤(AC-5, ⚪).

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

---

## CORRECTION (2026-10-05 UTC) — 20차 창 판정 (2026-10-04 UTC · i-0c4859442f56d70e0 · ami-0d78d476824493d77 · f0927bcd0)

> 위 AC-5 의 `[ ]` 와 «⚪ 의도적으로 열어 둠» 은 **이제 사실이 아니다.** 동결 파일이라 체크박스는 고치지 않고 여기서 닫는다. 이 절이 현재 상태다. 분석=Opus 5.5.

**재굽기 확인.** 이 티켓의 머지 `eebeda112`(#4139)는 20차 AMI 의 구운 커밋 `f0927bcd0` 의 조상이다(`git merge-base --is-ancestor` 참, AMI 태그 `RepoCommit` = `f0927bcd0`, provenance `ami-tag`) ⇒ 이 창의 artist-service 는 수리본이다. Failure Scenario 3(«재굽기 전에 닫기»)에 걸리지 않는다.

### AC-5

| AC-5 요구 | 판정 | 근거 |
|---|---|---|
| 콘솔 `platform@demo.com` / `fan-platform` → `/fan/agencies` 가 목록으로 뜬다 | ✅ | 소유자 화면: 소속사 목록 렌더(19차의 «팬 디렉터리 정보를 일시적으로 불러올 수 없습니다» 가 사라짐) |
| `/fan/artists` 가 목록으로 뜬다 | ✅ | 소유자 화면: 아티스트 목록 렌더 |
| `/fan/groups` 가 목록으로 뜬다 | ✅ | 소유자 화면: 그룹 화면 렌더. 🔵 이 화면은 `TASK-MONO-751` 이탈 1 대로 «ID 로 열기 + 생성» 화면이다(생산자에 그룹 목록 API 가 없다) — «목록» 은 그 화면이 오류 없이 뜬 것으로 읽는다 |
| 같은 창에서 `TASK-MONO-759` 판정을 잇는다 | ✅ | 소속사 하나에 `default` 연결 → 현재 값 `default`, ecommerce 게이트웨이 `GET /internal/sellers/default 200 308ms`(15:27:22Z) — 판정은 `TASK-MONO-759` 의 20차 절 |

- 🔵 «목록이 뜬다» 는 콘솔 zod 스키마(`AgencySchema` 등 — 날짜 필드 `nullableString`)가 artist-service 본문을 **받아들였다**는 뜻이다. 19차의 실패 기전(`fan_ok status=200` 1ms 뒤 `fan_error` — 본문 파싱 실패)이 이번에는 화면에 나타나지 않았다.
- ⚪ 응답 본문의 날짜 필드를 원문으로 찍어 보지는 않았다(콘솔 토큰 없이는 못 받는다 — 배경 절의 같은 공백). 판정은 «소비자 스키마가 수락했다» 는 결과 상태이고, 형식 자체는 AC-0~AC-2 의 컨텍스트 매퍼 시험이 고정한다.

### 4차원 (close chore)

| 차원 | 결과 |
|---|---|
| (a) `gh pr view 4139` | `state=MERGED` · mergedAt 2026-10-04T07:45:54Z · mergeCommit `eebeda112` |
| (b) origin/main 조상 | 참(origin/main `92a6320eb`) |
| (c) 머지 시점 실패 체크 | `statusCheckRollup` 65건 = SUCCESS 16 · SKIPPED 49 · **FAILURE 0** |
| (d) `# Acceptance Criteria` | AC-0 ~ AC-4 `[x]`(본문) · **AC-5 = 이 절에서 닫힘** — 동사 «목록으로 뜬다» 세 화면 + «759 판정을 이어서 한다» 를 위 표로 확인 |

⇒ **`review/` → `done/`.**
