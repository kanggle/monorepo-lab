# Taxonomy-Based Rule System

> 🛑 **이 디렉터리는 템플릿 인프라입니다.** 아래 [domains/](domains/)과 [traits/](traits/)의 규칙 파일은 모든 프로젝트 간 재사용되는 **규칙 라이브러리**입니다. 프로젝트 특화 경로(`apps/my-service/`)나 프로젝트별 적용 범위를 본문에 쓰지 마세요 — 드리프트의 원인이 됩니다. 새 규칙 파일은 [../TEMPLATE.md](../TEMPLATE.md)의 on-demand 정책에 따라 작성합니다.

이 디렉토리는 `PROJECT.md`에서 선언한 **domain**과 **traits**에 따라 조립되는 규칙 계층이다. 모든 프로젝트는 이 시스템을 통해 "자신에게 적용되는 규칙"을 결정한다.

---

## Routing Layer (`.claude/config/`) — 보조

**이 디렉토리(`rules/`)가 진실 소스**이지만, 에이전트 런타임 라우팅을 위해 `.claude/config/` 아래에 **짧은 디스패치 카탈로그**가 함께 존재한다:

- [`../.claude/config/domains.md`](../.claude/config/domains.md) — 38 domain 카탈로그 (리스트·규칙·예시)
- [`../.claude/config/traits.md`](../.claude/config/traits.md) — 11 trait 카탈로그
- [`../.claude/config/activation-rules.md`](../.claude/config/activation-rules.md) — trait/domain → 활성화 규칙 카테고리 디스패치 표. 각 항목에서 이 디렉토리의 상세 파일로 링크

**역할 분리**:

- `.claude/config/*.md` — "X가 유효한 값인가? 활성화되는 카테고리는 무엇인가?"에 대한 **짧은 답**. 에이전트가 빠르게 훑음.
- `rules/` (이 디렉토리) — "X가 무엇을 의미하는가? 어떤 규칙을 반드시 지켜야 하는가?"에 대한 **상세 답**. 구현·리뷰 시 참조.

**drift 방지**: 신규 trait/domain 추가 시 반드시 `.claude/config/*.md` 3파일과 [taxonomy.md](taxonomy.md) + 해당 `domains/<d>.md` 또는 `traits/<t>.md`(있는 경우)를 **같은 PR에서 갱신**. 어느 한쪽만 수정하는 것은 drift 시작 신호.

---

## Three Layers

규칙은 3계층으로 분리된다. 각 계층은 **추가적(additive)** 이며, 상위 계층은 하위 계층을 특화하거나 보강한다.

### 1. Common (모든 프로젝트에 적용)

- 위치: [common.md](common.md) — **인덱스 전용**, 규칙 문장 작성 금지
- 내용: 기존 [platform/](../platform/) 아래 14개 canonical 파일을 가리키는 인덱스
- 특징:
  - 모든 프로젝트가 항상 로드
  - 이 계층의 규칙을 domain/trait 계층에서 완화(loosen)하려면 명시적 override 선언이 필요
  - 암시적 충돌 시 **common이 우선**, 그래도 해결 안 되면 Hard Stop

### 2. Domain (프로젝트의 primary domain 하나에만 적용)

- 위치: [domains/<domain>.md](domains/) — 예: [domains/ecommerce.md](domains/ecommerce.md)
- 내용: 해당 도메인의 bounded context, ubiquitous language, 표준 에러 코드 섹션 참조, 통합 경계, 체크리스트
- 로딩 규칙: `PROJECT.md`의 `domain:` 값에 해당하는 파일 **정확히 1개**만 로드
- 파일 부재 시: 추가 제약 없음 (on-demand 원칙). 새 프로젝트가 해당 domain을 선언하면 그 프로젝트에서 이 파일을 함께 추가한다

### 3. Traits (선언된 trait 각각 적용)

- 위치: [traits/<trait>.md](traits/) — 예: [traits/transactional.md](traits/transactional.md)
- 내용: 해당 특성이 요구하는 필수 규칙, 금지 패턴, 필수 산출물, common과의 상호작용, 체크리스트
- 로딩 규칙: `PROJECT.md`의 `traits: []` 배열의 각 값에 대해 파일 로드 (모두)
- 파일 부재 시: 추가 제약 없음 (on-demand 원칙)

---

## Resolution Order (규칙 해결 순서)

AI 에이전트와 개발자는 다음 순서로 규칙을 로드·적용한다:

1. **`PROJECT.md` 읽기** — domain, traits 확인
2. **Common 계층 로드** — [common.md](common.md)에 인덱싱된 모든 파일
3. **Domain 계층 로드** — [domains/<declared-domain>.md](domains/) (있는 경우)
4. **Traits 계층 로드** — [traits/<each-declared-trait>.md](traits/) (있는 경우, 여러 개)
5. **Service-Type 계층 로드** — 구현 대상 서비스의 `architecture.md`에서 선언한 Service Type에 해당하는 [../platform/service-types/](../platform/service-types/) 파일 (정확히 1개)
6. **기존 `platform/` 나머지** — Core/Auxiliary spec (entrypoint.md 기준)

> Service-type 축은 domain/trait과 **직교(orthogonal)** 하다. 즉 domain이 무엇이든 trait이 무엇이든, 서비스 타입은 독립적으로 선택되며 각자 고유 규칙을 가진다.

---

## Conflict Rules

규칙 간 충돌이 감지되면:

1. **Common이 우선**. Domain/Trait 파일이 common 규칙을 완화하려면 해당 규칙 ID/섹션을 명시적으로 참조하여 "override" 선언을 포함해야 한다. 예:

   ```markdown
   ## Overrides
   - overrides: ../common.md → architecture.md#rule-3
   - reason: 이 도메인에서는 ... 이유로 대안 적용
   - scope: 이 도메인의 모든 서비스
   ```

2. **Trait 간 충돌** — 두 trait이 서로 모순된 요구를 하면 (예: `real-time` vs `batch-heavy`) 먼저 [taxonomy.md](taxonomy.md)의 Incompatibilities 표를 확인한다. 공존이 허용되지만 경고된 조합이면 `PROJECT.md`의 `## Overrides` 섹션에 공존 정당화를 기록한다.

3. **해결 불가 시 Hard Stop** — [CLAUDE.md](../CLAUDE.md)의 Hard Stop Rules에 따라 구현을 중단하고 보고한다.

---

## On-Demand Generation Policy

taxonomy.md는 38개 domain과 11개 trait을 카탈로그로 등록하지만, 이 디렉토리에 **모든 파일이 사전 생성되어 있지는 않다**. 원칙:

- **파일 부재 = 추가 제약 없음**: 해당 domain/trait을 선언한 프로젝트가 아직 이 저장소에 없으면, 규칙 파일도 없는 것이 정상이다.
- **자동 생성 금지**: 빈 stub 파일을 미리 생성하지 않는다. 파일 폭발(38 × 11)을 막기 위함.
- **생성 시점**: 새 프로젝트가 특정 domain/trait을 선언할 때, 해당 프로젝트 PR에서 규칙 파일을 **같은 변경**으로 함께 추가한다. taxonomy.md 카탈로그에도 이미 존재하는 태그만 선언 가능.
- **생성 포맷**: [domains/ecommerce.md](domains/ecommerce.md)와 [traits/transactional.md](traits/transactional.md)의 섹션 구조를 따른다.

---

## Relationship to `platform/service-types/`

Service-types 축은 이 분류 시스템과 **독립적인 직교 축**이다.

| 축 | 값의 개수 | 선택 단위 | 파일 위치 |
|---|---|---|---|
| **Domain** | 프로젝트당 1개 | `PROJECT.md` 선언 | `domains/<domain>.md` |
| **Traits** | 프로젝트당 다수 | `PROJECT.md` 선언 | `traits/<trait>.md` |
| **Service Type** | 서비스당 1개 | `specs/services/<service>/architecture.md` 선언 | `../platform/service-types/<type>.md` |

한 프로젝트 안에 여러 서비스가 있고, 각 서비스가 서로 다른 service-type을 가질 수 있다. 반면 domain/traits는 프로젝트 전체에 일괄 적용된다.

---

## Index File Rule (중요)

[common.md](common.md)는 **인덱스 전용 파일**이다:

- 14개 common 파일의 경로와 1줄 요약만 포함
- **실제 규칙 문장을 복사하지 말 것**
- 중복 발견 시 이 파일에서 삭제 (canonical은 항상 원본 파일)
- 새 규칙 파일을 [../platform/](../platform/) 아래 추가하면 이 인덱스도 함께 갱신

이 원칙이 깨지면 "이중 진실 소스" 문제가 발생하고 drift가 시작된다.

---

## Validation

이 시스템의 무결성은 다음 수단으로 검증한다:

- **Manual grep**: `PROJECT.md`의 domain/traits 값이 [taxonomy.md](taxonomy.md)에 등록되어 있는지
- **File existence**: 선언된 domain/traits에 대응하는 파일이 있거나, 없어도 "추가 제약 없음" 상태인지
- **`/validate-rules` skill**: 향후 이 규칙 계층까지 검사 범위 확장 예정 (현재 v0.1 스코프 외)
