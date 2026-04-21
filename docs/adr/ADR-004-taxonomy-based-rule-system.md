# ADR-004: 분류(domain/trait) 기반 규칙 시스템을 도입한 이유

- **Status**: Accepted
- **Date**: 2026-03
- **Tags**: governance, rules, ai-agent, automation

## Context

AI 에이전트(Claude Code 등)와 함께 작업하는 모노레포에서 다음 문제가 반복된다:

1. **규칙이 모든 프로젝트에 무차별 적용**됨. 예: 금융 도메인에만 필요한 규제 대응 규칙이 쇼핑몰 토이 프로젝트에도 따라붙음.
2. **규칙 문서가 비대해짐**. 한 CLAUDE.md가 전부를 담으려다 3000줄이 되고, 결국 에이전트가 중간부터 무시.
3. **"이 규칙이 이 프로젝트에 왜 적용되는가"** 에 대한 답이 명시적이지 않음 → 사람도 에이전트도 자의적으로 해석.
4. **프로젝트마다 같은 규칙을 복붙**하면 drift가 시작됨.

일반적 해결책:

- **A. 거대한 중앙 CLAUDE.md + "relevant sections만 읽기"**: AI의 선별력에 의존, 실패율 높음.
- **B. 프로젝트별 완전 독립 CLAUDE.md**: 복붙·drift 문제 그대로.
- **C. 분류 기반 조립(Taxonomy-based composition)**: 프로젝트가 자신의 **분류 태그**만 선언하면, 해당 태그에 연결된 규칙 층이 자동 조립됨.

## Decision

**C안을 채택**하고 다음 구조를 확립한다.

### 1. 프로젝트는 [PROJECT.md](../../PROJECT.md) 하나로 자기 분류를 선언

```yaml
---
domain: ecommerce
traits: [transactional, content-heavy, read-heavy, integration-heavy]
service_types: [rest-api, event-consumer, batch-job, frontend-app]
---
```

### 2. 규칙은 3계층으로 자동 조립

| 계층 | 위치 | 로딩 조건 |
|---|---|---|
| **Common** | [specs/rules/common.md](../../specs/rules/common.md) | 항상 |
| **Domain** | [specs/rules/domains/\<domain\>.md](../../specs/rules/domains/) | `domain` 선언 시 |
| **Trait** | [specs/rules/traits/\<trait\>.md](../../specs/rules/traits/) | 해당 trait 선언 시 |

상위 계층은 **additive**. 하위를 완화하려면 명시적 `## Overrides` 블록을 쓴다.

### 3. 라우팅과 의미를 분리

| 용도 | 위치 |
|---|---|
| "이 태그 존재하나? 어떤 규칙 카테고리를 켜나?" — **빠른 디스패치** | [.claude/config/](../../.claude/config/) |
| "그 태그가 무엇을 의미하나? 어떤 규칙을 반드시 지키나?" — **상세 정의** | [specs/rules/](../../specs/rules/) |

두 계층은 PR에서 **함께 갱신 강제**. 한쪽만 고치면 drift 시작.

### 4. 서비스 단위의 하위 분류 (Service Type)

각 서비스는 [specs/services/\<service\>/architecture.md](../../specs/services/)에 `Service Type` 선언. 구현 시 이 타입과 일치하는 [specs/platform/service-types/\<type\>.md](../../specs/platform/service-types/) 파일 **단 하나만** 읽는다. 여러 타입 파일 동시 참조는 금지 — 규칙 충돌 방지.

## Consequences

### Positive
- **규칙의 과잉 적용 차단**: 본 프로젝트는 `regulated`·`audit-heavy`·`multi-tenant`를 선언하지 않음. 이들 trait 파일이 존재해도 로딩되지 않음.
- **"왜 이 규칙이 적용되나?" 질문에 기계적 답**: `PROJECT.md`의 tag → [specs/rules/taxonomy.md](../../specs/rules/taxonomy.md) 의 정의 → `traits/<trait>.md`의 구체 규칙.
- **재사용성**: 같은 `taxonomy.md`를 다음 프로젝트에도 그대로 복사 가능. 거기서 선언하는 tag만 바꾸면 규칙 세트가 자동 교체.
- **drift 방지 메커니즘 내장**: `.claude/config/`와 `specs/rules/`의 동기화가 PR 정책으로 강제.

### Negative
- **초기 설계 비용 큼**: taxonomy + 계층화 + Overrides 문법을 정의하는 데 상당한 문서 작업 필요.
- **러닝 커브**: 신규 기여자가 "왜 이 파일부터 읽어야 하는가"를 이해하려면 [CLAUDE.md의 Project Classification 섹션](../../CLAUDE.md#project-classification-read-first)을 먼저 통과해야 함.
- **tag 미싱 시 Hard Stop**: 선언되지 않은 tag나 파싱 불가 frontmatter는 즉시 중단 — 엄격하지만 알림 많음.

### 버린 대안: "CLAUDE.md 한 파일에 조건부 블록"
- **왜 안 택했나**: Markdown에 "if domain=fintech then ..." 같은 조건 분기를 표현하는 순간 문서가 아니라 **DSL**이 됨. 가독성 무너지고 AI도 일관되게 해석 못 함.

### 버린 대안: "모든 규칙을 모든 프로젝트에 보내되 skip 권장"
- **왜 안 택했나**: 에이전트가 "무시해도 좋음" 블록을 무시하지 못하는 경우가 많음. 특히 context가 긴 상황에서.

## 검증: 실제로 이 시스템이 작동하는가

본 저장소가 **자신의 첫 dogfood 프로젝트**. `PROJECT.md`에 ecommerce + 4 traits 선언, 규칙 파일들은 그 선언에만 반응하도록 구성. 지금까지 AI 에이전트가 생성한 PR들은 **선언된 trait의 규칙만 적용**했음이 커밋·리뷰 이력에서 확인됨 (예: `transactional` trait의 Saga·Idempotency 규칙이 order/payment/product 서비스에서 반복 적용).

## References

- [CLAUDE.md § Project Classification](../../CLAUDE.md)
- [specs/rules/README.md](../../specs/rules/README.md)
- [specs/rules/taxonomy.md](../../specs/rules/taxonomy.md)
- [.claude/config/activation-rules.md](../../.claude/config/activation-rules.md)
