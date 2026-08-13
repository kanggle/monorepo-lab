# Task ID

TASK-MONO-521

# Title

`WorkloadIdentityAuthoritiesConverter` 가 두 벌이 됐다 — 승격 후보이되, **승격 자체가 결정**이라 먼저 재야 한다

# Status

review

# Owner

monorepo

# Task Tags

- shared-library
- security

---

# 배경

`TASK-FAN-BE-045`(PR #3270) 가 artist-service 에 `/internal/**` 워크로드 체인을 신설하면서
membership-service 의 `WorkloadIdentityAuthoritiesConverter` 와 **구조적으로 동일한** 클래스를
두 번째로 만들었다. 그때 승격하지 않은 이유는 `libs/` 가 monorepo-level 공유 경로라 **root
태스크가 필요**했기 때문이고(CLAUDE.md § Task Rules), 그 티켓 범위에서 몰래 하지 않고
클래스 주석에 후보라고 이름만 남겼다. 이 티켓이 그 이름을 받는다.

## 🔴 "두 벌이니 승격" 은 결론이 아니라 가설이다

이 저장소의 규칙은 *"1곳뿐인 규칙은 없는 규칙"* 이지만 **중복이 곧 병은 아니다**. 승격을
정당화하려면 두 사본이 **같은 것을 하기 때문에** 같은 것이지, 우연히 닮은 것이 아님을 보여야
한다. 실제로 둘은 한 축에서 **다르다**:

| | membership-service | artist-service |
|---|---|---|
| `REQUIRED_WORKLOAD_SCOPE` | `membership.read` | `artist.read` |

즉 공유 가능한 것은 **메커니즘**(scope 클레임을 세 가지 모양 — 배열/공백구분 문자열/`scp` —
으로 읽어 하나의 필수 스코프와 대조하고 `ROLE_INTERNAL` 을 부여)이고, **정책**(어느 스코프인가)은
서비스별이다. 승격한다면 스코프를 **생성자 파라미터**로 받는 형태여야 한다.

🔴 그리고 이 클래스는 **보안 판정기**다. 잘못 공유하면 한 서비스의 완화가 전 서비스에
퍼진다 — 승격의 대가가 다른 유틸리티와 다르다.

---

# 🟢 착수 (2026-08-13 UTC) — 승격 결정, 전 AC 완료

## ① AC-0 — 모집단 재측정: **2가 아니었다**

`ROLE_INTERNAL` 을 부여하는 지점을 저장소 전체에서 세니 **7개**이고, 세 부류로 갈린다:

| 부류 | 지점 | 축 | 이 티켓의 모집단인가 |
|---|---|---|---|
| **A. scope 판정기** | fan membership-service (`membership.read`)<br>fan artist-service (`artist.read`) | 필수 scope | ✅ **2건** |
| **B. subject allow-list** | ecommerce order-service `SystemClientSubjectValidator` | `sub` 화이트리스트 | ❌ |
| **C. dev/test 바이패스** | iam auth / admin / account `InternalApiFilter`<br>iam security-service `InternalAuthFilter` | 프로파일 게이트 | ❌ |

🔵 **B 를 모집단에서 뺀 것은 근거가 있다.** `platform/security-rules.md` 는 machine 토큰
인가를 *"subject allow-list **OR** required scope"* — **둘 중 정확히 하나**로 규정한다.
B 는 다른 쪽 절반이고, Spring 확장점도 다르다(`OAuth2TokenValidator` = 디코더 레벨 401,
이쪽은 `Converter` = 체인 레벨 403). 공유 클래스로 합쳐질 수 있는 대상이 아니다.

🔴 **C 는 미끼였다.** 이름과 부여하는 authority 가 같아서 grep 에 걸리지만, 판정기가
아니라 **프로파일 게이트된 바이패스**다(비종단 · 거절하지 않음). 배경이 경계하라고 적은
`SystemClientSubjectValidator` 계열 확인이 실제로 값을 했다.

## ② AC-1 — 행 단위 대조: **다른 축은 정확히 한 줄**

두 사본의 본문을 diff 한 결과 차이는 `REQUIRED_WORKLOAD_SCOPE` **한 줄뿐**이다
(`convert` · `isWorkloadIdentity` · `scopes` 전부 바이트 동일). 배경의 가설이 측정으로
확인됐다 — 공유 가능한 것은 메커니즘, 서비스별인 것은 스코프 하나.

## ③ AC-2 — 결정: **승격한다.** 단, 목표 모듈은 이 티켓이 적은 곳이 아니다

🔴 **이 티켓의 `libs/java-security` 는 틀렸다.** 올바른 자리는 **`libs/java-security-servlet`**:

- 이 클래스의 `Converter<Jwt, AbstractAuthenticationToken>` 는 **서블릿** Resource Server
  의 계약이다(리액티브는 `Converter<Jwt, Mono<...>>`). `libs/java-security` 는
  ADR-MONO-049 § D3 이 빌드로 강제하는 **framework-neutral** 모듈이고 **리액티브 게이트웨이
  6개**가 소비한다.
- 같은 확장점의 선례가 **이미 그 모듈에 있다** — `ActorContextJwtAuthenticationConverter`
  (메커니즘 공유 + 정책 주입, ADR-MONO-058 § D1). 이 승격은 그 패턴의 두 번째 사례다.
- 🔵 **소비 서비스 두 곳 모두 이미 `libs:java-security-servlet` 을 의존한다** — 새 모듈도,
  새 gradle 의존도 필요 없다.

## ④ AC-3 — 정책은 주입, 메커니즘만 공유

스코프는 각 서비스 `SecurityConfig` 의 `public static final REQUIRED_WORKLOAD_SCOPE` 로
남겼다. artist 의 *"왜 `artist.read` 이고 `fan-platform.artist.read` 가 아닌가"* 논증도
그 상수 옆으로 함께 옮겼다 — 그 판단은 서비스 정책이지 라이브러리 것이 아니다.
🔵 라이브러리에 스코프 문자열 리터럴 **0건**(테스트도 `svc.read` 등 일반 이름) ⇒ HARDSTOP-03 무해.

추가로 **빈 스코프는 생성 시점에 예외**를 던지게 했다. 빈 문자열이면 아무 토큰도 매치하지
않아 조용히 전건 403 이 되는데, 그건 배선 실수가 **장애처럼 보이는** 모양이다 — 사고의
착지점을 부팅 실패 쪽으로 옮겼다.

## ⑤ AC-4 — 판정: 초록으로 끝내지 않고 **물리는지** 확인했다

두 서비스 `:check` 전부 통과(BUILD SUCCESSFUL). 그러나 초록은 가설이므로 공유 클래스의
`isWorkloadIdentity` 를 `return true` 로 **주입**하고 다시 돌렸다:

```
artist-service      169 tests, 4 failed   ← 🔴 "fan-platform.artist.read 는 STILL 403" 포함
membership-service  148 tests, 3 failed
```

⇒ AC-4 가 지목한 *엔드유저 스코프 거절* 케이스가 **살아 있고 공유 클래스에 물린다.**
주입 원복 후 `git diff` 공백 확인(byte-exact).

## ⑥ AC-5 — 🔴🔴 CI 표면: 승격 대상 모듈은 **CI 에서 테스트되지 않고 있었다**

AC-5 가 경고한 그대로였고, 읽지 않고 **쟀다**:

```
:projects:fan-platform:apps:artist-service:check --dry-run
  → :libs:java-security-servlet:compileJava / processResources / classes / jar   (여기까지)
  → test 없음, check 없음, assertClasspathNeutrality 없음
```

워크플로 전체에서 `:libs:java-security-servlet:check` 는 **0건**이었다. 이 티켓이
`ci.yml` libs 잡에 추가했다. 🔵 대조군으로 계측기를 검증했다 — 같은 dry-run 에서
`:libs:java-security:check` 는 `test` + 가드를 정상적으로 끌고 온다.

🔴 **그리고 이건 이 모듈만의 문제가 아니었다** — repo-root libs 13개 중 **6개만** CI 에서
자기 `check` 를 돈다. `assertNoServletOnReactiveEdge`(java-gateway) 와 루트
`assertNoApiOnSharedLibs` 도 실행되지 않으며, 후자는 자기 주석에 *"이 단언은 매 변경마다
도달 가능하다"* 고 적어 두었다. 이 티켓 범위를 넘으므로 **`TASK-MONO-527`** 로 분리했다.

---

# Goal

`WorkloadIdentityAuthoritiesConverter` 를 `libs/java-security` 로 승격할지 **결정하고**,
승격한다면 정책(필수 스코프)을 주입 가능한 형태로 옮기고 두 서비스를 그 위로 옮긴다.

---

# Scope

## In Scope

- 두 사본의 **행 단위 비교** — 메커니즘이 정말 같은지, 다른 축이 스코프 하나뿐인지
- 모집단 재확인: 다른 프로젝트(wms/scm/finance/erp/ecommerce)에 같은 모양의 워크로드
  판정기가 더 있는지 — 🔴 **2벌이라고 가정하고 시작하지 말 것**
- 결정: 승격 / 유지 / 다른 형태. 유지도 산출물이다
- 승격 시: `libs/java-security` 이동 + 두(또는 그 이상) 서비스 전환 + 테스트 이전

## Out of Scope

- 각 서비스가 **어느 스코프를 요구하는가** — 그것은 서비스 정책이고 이 티켓이 바꾸지 않는다
- 새 `/internal/**` 표면 신설
- `platform/security-rules.md` 의 "subject allow-list OR required scope" 규칙 자체 변경

---

# Acceptance Criteria

- [x] **AC-0 (모집단 재측정)** — 완료(§①). **7건**이었고 세 부류로 갈렸다: scope 판정기 2
      (= 진짜 모집단), subject allow-list 1(`SystemClientSubjectValidator` — 규정상 *다른 절반*),
      dev/test 바이패스 4(iam `InternalApiFilter`×3 + `InternalAuthFilter` — 🔴 이름만 같은 미끼).
      **2벌 가정은 틀렸고, 승격 대상은 그중 2건이 맞았다** — 결론은 같아도 근거는 여기서 처음 생겼다
- [x] **AC-1 (같음의 근거)** — 완료(§②). 본문 diff 결과 다른 축은 `REQUIRED_WORKLOAD_SCOPE`
      **한 줄뿐**(나머지 전부 바이트 동일)
- [x] **AC-2 (결정)** — **승격.** 🔴 다만 목표 모듈은 이 티켓이 적은 `libs/java-security` 가
      아니라 **`libs/java-security-servlet`** 이다(§③) — 서블릿 Converter 형태 + 동일 확장점의
      선례가 이미 그 모듈에 있고, 두 소비자 모두 이미 그 모듈을 의존한다
- [x] **AC-3 (승격했다면)** — 완료(§④). 스코프는 각 서비스 `SecurityConfig` 의 public 상수로
      남고 생성자로 주입된다. 라이브러리 내 스코프 리터럴 **0건** ⇒ HARDSTOP-03 무해
- [x] **AC-4 (승격했다면 — 판정)** — 완료(§⑤). 양쪽 `:check` 통과 + **bite 로 물림 확인**
      (공유 클래스를 `return true` 로 느슨하게 하자 artist 4건 / membership 3건 RED,
      🔴 `fan-platform.artist.read` 거절 케이스 포함). 원복은 byte-exact
- [x] **AC-5 (CI 표면)** — 완료(§⑥). 🔴 승격 대상 모듈은 **어느 워크플로에서도 자기 `check`
      를 돌지 않고 있었다** — dry-run 그래프가 `jar` 에서 멈춘다. `ci.yml` 에 추가했고,
      대조군으로 계측기를 검증했다. 같은 부류의 나머지 6개 모듈 + 루트 가드는
      **`TASK-MONO-527`** 로 분리

---

# Related Specs

- `platform/shared-library-policy.md`
- `platform/security-rules.md` (machine 토큰 인가 축)
- `platform/contracts/jwt-standard-claims.md`
- `projects/fan-platform/apps/membership-service/.../infrastructure/security/WorkloadIdentityAuthoritiesConverter.java`
- `projects/fan-platform/apps/artist-service/.../config/WorkloadIdentityAuthoritiesConverter.java`

# Related Contracts

- 없음 — 내부 인가 메커니즘이고 HTTP 계약을 바꾸지 않는다

# Edge Cases

- `scp` 배열 / 공백 구분 `scope` 문자열 / JSON 배열 — 세 모양을 다 받는 것이 현재 동작이다.
  승격 시 **하나라도 빠지면 issuer 모양이 다른 배포에서 조용히 403** 이 된다
- 🔴 `tenant_id` **부재를 판정에 쓰지 않는다** 는 것이 두 사본에 모두 주석으로 박혀 있다
  (`TASK-FAN-BE-029` 의 사고). 승격 시 그 주석도 함께 옮길 것 — 이유를 잃으면 다시 넣는다

# Failure Scenarios

- 🔴 **중복이라는 이유만으로 승격한다** — 정책까지 함께 올라가면 한 서비스의 완화가 전부에
  퍼진다. 올라가는 것은 메커니즘뿐이어야 한다
- 🔴 **모집단을 2로 가정한다** — AC-0 이 그래서 있다
- 🔴 **승격하고 테스트는 한쪽에만 남긴다** — 공유 클래스는 두 서비스의 매트릭스가 **각각**
  통과해야 의미가 있다

# Definition of Done

- [x] AC-0 모집단 실측 — 7건, 3부류
- [x] 사본 행 단위 대조 결과 — 다른 축 1줄
- [x] 승격/유지 결정 기록 — 승격, `libs/java-security-servlet`
- [x] 이동 + 전환 + 양쪽 매트릭스 통과 + bite + CI 표면 확인
- [x] Ready for review

---

분석=Opus 5 / 구현 권장=**Opus** — 공유 라이브러리 경계 + 보안 판정기라 결정 비용이 크다.
