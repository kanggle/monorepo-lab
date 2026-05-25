# Task ID

TASK-ERP-BE-003

# Title

erp masterdata-service refactor sweep (2026-05-25 scan)

# Status

done

# Owner

backend

# Task Tags

- code

---

# Goal

2026-05-25 scan 에서 erp-platform masterdata-service 에 식별된 L1~L6 hotspot 을 단일 PR 로 정리. 외부 동작 변경 없음.

---

# Scope

## In Scope

| L | 대상 | 변경 |
|---|---|---|
| L1 | `apps/masterdata-service/src/main/java/com/example/erp/masterdata/infrastructure/persistence/jpa/*RepositoryAdapter.java` (6개) | → `*RepositoryImpl` (GAP PR #806 패턴) |
| L2 | `apps/masterdata-service/.../application/MasterdataApplicationService.java:708` `toJson()` | `new ObjectMapper()` per-call 제거 — 생성자 주입 (`MasterdataEventPublisher` 와 동일 패턴) |
| L2 | `apps/masterdata-service/.../application/MasterdataApplicationService.java:80-84` AGG_* 상수 5개 | `MasterdataEventPublisher.AGG_*` 참조로 교체, 중복 상수 제거 |
| L6 | `apps/masterdata-service/.../presentation/controller/{Department,Employee,JobGrade,CostCenter,BusinessPartner}Controller.list()` (5개) | `ApiEnvelope.ofList(data, page, size)` 팩토리 추가 → 5 site 동시 사용 |
| L6 | `apps/masterdata-service/.../application/MasterdataApplicationService.java:668-696` `ensureActiveDepartment/CostCenter/JobGrade` 3개 | 제네릭 `ensureActive(repo, id, tenantId, label)` 1개로 통합 (또는 `Function<String, Optional<T>>` lambda 시그니처) |

## Out of Scope

- `MasterdataApplicationService.java` 전체 778-line L7 후보 분리 (single `@Transactional` command boundary 의도 가능성 — `specs/services/masterdata-service/architecture.md` 확인 후 별건)
- API/event contract 변경

---

# Acceptance Criteria

- [ ] 6개 `*RepositoryAdapter` → `*RepositoryImpl` rename, 호출처 import 전부 갱신
- [ ] `MasterdataApplicationService` 가 `ObjectMapper` 를 생성자 주입으로 보관, `toJson()` 내부 `new ObjectMapper()` 0건 (grep)
- [ ] `MasterdataApplicationService` 의 AGG_* 5개 `private static final` 제거, `MasterdataEventPublisher.AGG_*` 참조 사용
- [ ] `ApiEnvelope.ofList()` 도입, 5 controller `list()` 의 `LinkedHashMap` 래핑 inline 코드 제거 (grep `new java.util.LinkedHashMap` 0)
- [ ] `ensureActive*` 3 메서드 → 1 helper 통합
- [ ] `./gradlew :projects:erp-platform:apps:masterdata-service:check` BUILD SUCCESSFUL
- [ ] contract / schema 변경 0건

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0 따라 `projects/erp-platform/PROJECT.md` 읽고 rule layer 로드.

- `platform/refactoring-policy.md`
- `platform/naming-conventions.md` — `*RepositoryImpl` 표준
- `platform/coding-rules.md`
- `projects/erp-platform/specs/services/masterdata-service/architecture.md`

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md`

# Related Contracts

- 없음

---

# Target Services

- masterdata-service

---

# Implementation Notes

- L1 rename 먼저 (가장 mechanical).
- `ObjectMapper` 주입은 Spring Boot autoconfigured singleton 사용 (`spring.jackson` 설정 반영됨).
- `ApiEnvelope.ofList` 시그니처는 기존 5 controller 의 `LinkedHashMap` 구조와 동일한 JSON 출력이 되도록 검증 — contract test (있으면) 통과.
- `ensureActive` 제네릭화: `<T extends DomainEntity> T ensureActive(Optional<T> found, String label)` 형태로 단순화 검토.

---

# Edge Cases

- `MasterdataEventPublisher.AGG_*` 가 `package-private` 이면 visibility 조정 필요.
- `ApiEnvelope.ofList` 추가 위치 — `libs/common` 또는 service-local. service-local 권장 (libs 변경 회피).
- `ensureActive` 제네릭화 시 repository 타입 bound 가 안 잡히면 `Function<String, Optional<T>>` lambda 시그니처로.

---

# Failure Scenarios

- ObjectMapper 주입 후 `@JsonInclude` 등 instance config 가 손상되면 outbox payload drift → contract test 실패.
- AGG_* 상수 visibility 변경 후 다른 호출처가 영향 받으면 build break.

---

# Test Requirements

- 기존 단위 + IT 전부 통과
- `ApiEnvelope.ofList` 추가 시 controller test 가 동일 JSON shape 반환 검증

Test command:

```
./gradlew :projects:erp-platform:apps:masterdata-service:check
```

---

# Definition of Done

- [ ] 5개 변경 항목 전부 구현
- [ ] `:check` BUILD SUCCESSFUL
- [ ] 테스트 logic 변경 0건
- [ ] commit + push
- [ ] Ready for review
