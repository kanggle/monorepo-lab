# ADR-003: `Money`/`Currency` 값객체를 account-service·ledger-service 중복에서 프로젝트 범위 공유 위치로

- **Status**: ACCEPTED (2026-07-30)
- **Date**: 2026-07-30
- **Authors**: architecture (naming-convention 감사 산물 — finance-platform 세 번째 프로젝트 ADR)
- **Supersedes**: —
- **Superseded by**: —
- **History**: PROPOSED 2026-07-30 — 8-프로젝트 네이밍 컨벤션 감사(2026-07-30)가 `account-service`/`ledger-service` 양쪽에 거의 동일한 `Money`/`Currency` 값객체가 독립 선언돼 있음을 발견. `ledger-service`의 `Money.java` 자체 javadoc 이 "Mirrors account-service's `Money`... a single source of truth would be a shared lib in a later increment; first-increment parity is intentional" 이라고 이미 이 부채를 인지·유예해 왔다. 공유 코드를 어디에 둘지는 `platform/shared-library-policy.md § Decision Rule`(기술/공통 vs 도메인-소유 판정)이 걸리는 아키텍처 결정이고, 이 저장소 8개 프로젝트 전체에 **프로젝트 범위(project-scoped) 공유 모듈 선례가 전무**(전부 repo-root `libs/` 아니면 프로젝트 내부 각 서비스 로컬)하므로 코드로 묵시 결정하면 HARDSTOP-09 위반 → 결정을 먼저 기록하고 PAUSE. **ACCEPTED 전환 + 구현은 별도 user-explicit-intent 태스크. Self-ACCEPT 금지.** · **ACCEPTED 2026-07-30** — 사용자가 sibling `ADR-MONO-058`과 함께 검토 후 `AskUserQuestion`에서 `"ADR-003 ACCEPTED"`를 명시하는 옵션을 선택(같은 스레드에서 앞서 나온 맨 "진행"은 이 ADR 자신의 게이트에 따라 불충분으로 판단해 재확인함). §2의 Option A(프로젝트 범위 신규 Gradle 모듈)가 **byte-unchanged 확정** — ACCEPTED는 *확정*이지 재결정이 아님. 실행(§6: 모듈 신설 → account-service 전환 → ledger-service 전환 → CLAUDE.md 문서 정합)은 별도 post-ACCEPTED task. **NOT self-ACCEPT** — 작성 에이전트(본 세션)가 아니라 사용자가 별도 턴에서 승인.

---

## 1. Context

### 1.1 현재 상태 (조사 결과, factual)

`account-service`와 `ledger-service`가 각각 독립적으로 `domain/money/Money.java` + `domain/money/Currency.java`를 선언한다:

- **`Money`**: `long minorUnits` + `Currency` — F5 불변식(정수 minor-unit만, `float`/`double` 전무), 통화 불일치 시 `CurrencyMismatchException`(422 `CURRENCY_MISMATCH`). Pure Java, Spring/JPA 의존 없음.
- **`Currency`**: v1 지원 통화 집합을 하드코딩한 enum — `KRW(0), USD(2), EUR(2), JPY(0)` (minor-unit scale 포함). 미지원 코드는 `UnsupportedCurrencyException`(→ `CURRENCY_MISMATCH`).

두 서비스의 사본은 **near-byte-identical**이며, `ledger-service`가 메서드 1개(`absoluteDifference`/`ofOrThrow`)를 추가로 얹은 정도의 차이만 있다. `ledger-service`의 javadoc이 이미 이 상태를 "mirrors account-service's `X`... 첫 증분 parity는 의도적"이라고 자인하고 있어, 이 중복은 **사고가 아니라 알려진 채로 유예된 부채**다.

같은 감사가 발견한 더 넓은 계열(`ApiEnvelope`/`ApiErrorBody`/`ClockPort`/`ClockConfig`/`SecurityConfig`/`GlobalExceptionHandler`/`AuditLog`/`ActorContextResolver` 등 — account/ledger 양쪽에 유사 중복)은 **이 ADR의 범위가 아니다**. 그 계열은 8-프로젝트 전체에 걸친 **기술 스캐폴딩**(technical scaffolding) 중복이고, 이미 존재하는 [`ADR-MONO-058`](../../../../docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md)(PROPOSED, ACCEPT 대기)의 D1(actor/JWT)·D2(에러 envelope)·D4(security-chain)가 정확히 그 계열을 다룬다. `Money`/`Currency`는 **finance 도메인 값객체**이고 ADR-MONO-058의 8개 패턴 표(§ 1.1)에 포함돼 있지 않다 — 별개 축의 결정이라 본 ADR로 분리했다.

### 1.2 기록되지 않은 것 (이 ADR 이 필요한 이유)

- **프로젝트 범위 공유 모듈 선례가 이 저장소에 전무**: 루트 `settings.gradle`을 전수 확인 — 모든 `libs:*` 모듈은 8-프로젝트 공통(project-agnostic) 원칙 하에 repo-root에만 존재(`libs/java-common`, `libs/java-security`, `libs/payment-core` 등). `projects/<name>/libs/` 형태의 프로젝트-내부 공유 모듈은 어느 프로젝트에도 없다. 이 부채를 "프로젝트 범위 공유 모듈"로 풀면 이 저장소 최초의 새 구조적 패턴을 만드는 것이다.
- **`shared-library-policy.md § Decision Rule`의 2번 질문("technical/common rather than domain-owned?")이 애매하다**: `Money`의 산술 메커니즘(정수 minor-unit, 통화 불일치 가드) 자체는 `libs/java-common`의 기존 입주자(`PageResult`/`UuidV7`/`PeriodSummary`)와 형태가 같은 순수 기술 값객체다. 그러나 `Currency` enum이 박제한 **"v1 지원 통화 4종"은 finance-platform의 제품 결정**이지 기술적 사실이 아니다 — 만약 이대로 repo-root `libs/java-common`에 승격하면, finance 도메인이 아닌 미래 소비 프로젝트(예: ecommerce의 해외결제)가 이 whitelist에 갇히거나, whitelist 자체가 8-프로젝트 공용 lib 안에 finance 전용 제품 결정을 심는 모양이 된다(`shared-library-policy.md § Forbidden`: "business rules owned by a single service/project"에 근접).
- **두 서비스는 같은 프로젝트, 다른 bounded context**: `account-service`와 `ledger-service`는 `finance-platform` 프로젝트 안에서 서로 다른 서비스이지만, 둘 다 정확히 같은 `Money`/`Currency` 개념을 공유(같은 통화 whitelist, 같은 F5 불변식)한다. `shared-library-policy.md § Ownership Rule`("한 bounded context에 속하면 그 서비스 안에 있어야 한다")는 이 경우 **어느 쪽에도 안 맞는다** — 이 값객체는 애초에 어느 한 서비스 소유가 아니라 finance-platform 프로젝트의 공유 vocabulary다.

---

## 2. Decision (proposed)

세 옵션을 검토했다. 각 옵션은 **어디에 둘지**만 결정하며, `Money`/`Currency`의 shape·불변식·API(§1.1)는 **byte-unchanged**로 이관한다 — 이 ADR은 재설계가 아니다.

### Option A — 프로젝트 범위 신규 Gradle 모듈 (권장, chosen-proposed)

`projects:finance-platform:libs:finance-common` (가칭)을 신설, `Money`/`Currency`(+ 두 예외 타입)를 이관. `account-service`/`ledger-service` 양쪽이 의존.

- **장점**: `Currency`의 v1 통화 whitelist가 finance-platform 제품 결정인 채로 남아도 정책 위반이 아니다 — repo-root `libs/`의 "8-프로젝트 project-agnostic" 원칙을 건드리지 않는다. `shared-library-policy.md § Ownership Rule`의 원 취지("도메인 소유가 재사용 편의보다 우선")를 지키면서도, 그 도메인이 "finance-platform 프로젝트"라는 올바른 크기로 좁혀진다.
- **단점**: 이 저장소 최초의 "프로젝트 범위 공유 모듈" 패턴 — `settings.gradle`에 새 include 경로 관례를 만들고, `CLAUDE.md § Repository Layout`의 "Shared vs project boundary"가 현재 repo-root만 "shared"로 규정하는 서술을 프로젝트-내부 shared 서브트리까지 확장 해석해야 한다(문서 정합 후속 필요). fan-platform도 독립적으로 동일 패턴을 원할 개연성이 있다(§4 참고) — 이 ADR의 선택이 그쪽에도 사실상 선례가 된다.

### Option B — repo-root `libs/java-common`으로 승격

기존 `libs/java-common`(이미 `PageResult`/`UuidV7`/`PeriodSummary` 같은 순수 기술 값객체를 보유)에 `Money`/`Currency`를 추가.

- **장점**: 신규 구조 패턴이 필요 없다 — 기존 8-프로젝트 공용 lib에 항목만 추가. `libs/payment-core`가 "현재 소비자 0"인 채로 project-agnostic하게 미리 만들어진 선례(ADR-MONO-056)와 같은 결이다.
- **단점**: `Currency`의 v1 whitelist(제품 결정)를 8-프로젝트 공용 lib에 심게 된다. 승격 시점에 whitelist를 제거하고 임의 ISO-4217 코드를 허용하도록 일반화할지, 아니면 finance 전용 whitelist를 그대로 얹을지 **별도 설계 결정**이 필요(이 ADR이 대신 정하지 않음 — D2가 유사 문제를 defer한 ADR-MONO-058의 선례를 따름). 다른 7개 프로젝트 중 현재 화폐 연산이 필요한 곳이 없어 "즉시 재사용"이 아니라 "선제적 배치"에 가깝다.

### Option C — 현행 유지(중복 존속, 문서화만)

아무것도 옮기지 않고, 이미 있는 `ledger-service`의 자인 주석을 두 서비스 모두에 동일하게 남겨 "의도적으로 유예된 부채"임을 명문화.

- **장점**: 리스크 0, 즉시 실행 가능(코드 변경 없음).
- **단점**: `ADR-MONO-058`의 Context가 이미 지적한 실패 패턴("같은 결함이 한 사본에서만 고쳐지고 형제 사본엔 안 퍼진다")에 그대로 노출된 채로 남는다 — 예를 들어 v1 통화 whitelist에 통화를 추가하는 변경이 두 사본 중 하나에서만 반영될 위험이 실재.

**권장(chosen-proposed): Option A.** `Currency`의 whitelist가 finance-platform의 제품 결정이라는 사실이 Option B를 어색하게 만들고, 이 저장소에 프로젝트 범위 공유 모듈 패턴이 없다는 사실만으로 Option A를 기각할 이유는 되지 않는다 — 오히려 정확히 이런 "같은 프로젝트, 다른 서비스, 같은 도메인 개념" 형태를 위해 그 패턴이 필요하다는 첫 사례로 본다.

---

## 3. Consequences

**Positive**
- `Money`/`Currency`의 미래 수정(예: 통화 추가, 반올림 정책 변경)이 한 곳에서만 일어나고 두 서비스에 자동으로 반영 — `ledger-service`가 이미 자인한 "first-increment parity" 부채가 종결.
- `Currency` whitelist가 finance-platform 프로젝트 경계 안에 남아 `shared-library-policy.md`의 project-agnostic 원칙과 충돌하지 않는다.
- 향후 finance-platform에 세 번째 서비스가 추가되면(현재 계획엔 없음) 처음부터 공유 모듈을 참조 — 세 번째 복사본이 생기지 않는다.

**Negative / risks**
- 이 저장소 최초의 "프로젝트 범위 공유 모듈" 구조 — `settings.gradle` 관례, `CLAUDE.md`의 shared/project 경계 서술 갱신이 별도 후속 필요(본 ADR의 ACCEPT 범위에는 포함하되, 문서 정합은 구현 task에서).
- `account-service`/`ledger-service` 양쪽의 빌드 의존성 그래프가 바뀐다 — 두 서비스 모두 이관 작업 중 컴파일 확인 필요(비침습적 rename이 아니라 모듈 경계 이동이라 `TASK-BE-565`류 순수 rename보다 리스크가 크다).
- ADR-MONO-058(PROPOSED)이 이후 ACCEPT되면 그쪽 D2(에러 envelope)도 finance-platform의 `ApiErrorBody` 계열을 건드리게 된다 — 이 ADR의 실행과 ADR-MONO-058의 실행이 같은 두 서비스에서 겹칠 수 있으니, 실행 순서를 조율할 필요(비-바인딩 권고, §6 참고).

---

## 4. Alternatives considered (fan-platform과의 관계)

같은 감사가 fan-platform에서도 유사한 패턴(`ApiEnvelope`/`ApiErrorBody`/`PublicPaths`/`ActorContextResolver`/`ActorContextJwtAuthenticationConverter`가 4개 서비스에 걸쳐 byte-identical 중복)을 발견했다. 그러나 fan-platform의 그 계열은 **기술 스캐폴딩**(JWT claim 추출, 에러 envelope, public-path 메커니즘)이라 이미 `ADR-MONO-058`의 D1/D2/D5 범위에 정확히 들어간다 — **fan-platform용 신규 ADR은 불필요**하며, 그 발견은 이미 존재하는 ADR-MONO-058의 ACCEPT 여부에 귀속된다. 두 ADR을 분리한 것은 우연이 아니라, `Money`/`Currency`가 "도메인 값객체"이고 fan-platform의 계열이 "기술 메커니즘"이라는 `shared-library-policy.md § Decision Rule` 2번 질문의 정확한 경계선을 따른 결과다.

---

## 5. What acceptance binds

PROPOSED 기록은 어떤 코드도 승인하지 않는다. 소유자 ACCEPT(정확형 `"ADR-003 ACCEPTED"`, finance-platform 프로젝트 범위) 시 구속 범위는 정확히 §2의 Option A(선택된 경우)다 — `Money`/`Currency`의 shape·API·불변식은 byte-unchanged 확정이며, 이관 방식(모듈 이름, Gradle include 경로)은 구현 task에서 세부화한다.

---

## 6. 실행 순서(비-바인딩, 참고용)

1. `projects:finance-platform:libs:finance-common` (또는 ACCEPT 시 확정될 이름) Gradle 모듈 신설, `account-service`의 `Money`/`Currency`(+ 예외 타입)를 원본으로 이관(더 완성된 쪽 — `ledger-service`가 추가한 메서드는 이관 후 병합).
2. `account-service`가 신규 모듈을 참조하도록 로컬 사본 삭제 + import 교체, 빌드/테스트 GREEN 확인.
3. `ledger-service`도 동일하게 전환, 빌드/테스트 GREEN 확인 — 이 단계에서 `ledger-service`가 추가한 메서드(`absoluteDifference` 등)가 공유 모듈에 반영됐는지 재확인.
4. `CLAUDE.md § Repository Layout`에 프로젝트 범위 공유 서브트리 관례를 문서화(신규 패턴이므로 후속 프로젝트가 참조할 수 있도록).
