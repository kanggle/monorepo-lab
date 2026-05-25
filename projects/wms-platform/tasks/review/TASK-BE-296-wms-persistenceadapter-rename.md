# Task ID

TASK-BE-296

# Title

wms `*PersistenceAdapter` → `*RepositoryImpl` rename (30 file mechanical sweep — TASK-BE-295 follow-up)

# Status

review

# Owner

backend

# Task Tags

- code

---

# Goal

TASK-BE-295 의 결정 (B 선택: `naming-conventions.md` 의 `*RepositoryImpl` 표준에 wms 정렬) 에 따라 wms 5 service 의 30개 `*PersistenceAdapter.java` production class 를 `*RepositoryImpl` 로 mechanical rename. 외부 동작 / contract / schema 변경 0.

---

# Scope

## In Scope

| Service | 파일 수 | 비고 |
|---|---|---|
| master-service | 6 | `apps/master-service/src/main/java/.../adapter/out/persistence/adapter/*PersistenceAdapter.java` |
| outbound-service | 9 | `apps/outbound-service/src/main/java/.../adapter/out/persistence/adapter/*PersistenceAdapter.java` |
| inventory-service | 7 | `apps/inventory-service/src/main/java/.../adapter/out/persistence/adapter/*PersistenceAdapter.java` |
| inbound-service | 5 | `apps/inbound-service/src/main/java/.../*PersistenceAdapter.java` |
| notification-service | 3 | `apps/notification-service/src/main/java/.../*PersistenceAdapter.java` |
| **Total** | **30** | — |

각 파일: `git mv <X>PersistenceAdapter.java <X>RepositoryImpl.java` + 클래스명 본문 변경 + 호출처 import 갱신.

## Out of Scope

- Spring bean default name 변경에 따른 string-based `getBean(...)` 호출 (사전 grep 으로 0 확인 후 진행; 발견 시 별 task)
- 패키지 디렉토리 `adapter/` 자체 rename (스코프 외, persistence/adapter 위치 유지)
- 테스트 logic 변경 (rename 에 따른 mock target import 갱신만 허용)
- API / event contract / DB schema 변경

---

# Acceptance Criteria

- [ ] 30 production class 파일 모두 `*RepositoryImpl.java` 로 rename (grep `*PersistenceAdapter.java` find 결과 0)
- [ ] 본문 `class XxxPersistenceAdapter` → `class XxxRepositoryImpl` 변경 (grep `class.*PersistenceAdapter` 0)
- [ ] 모든 호출처 import 갱신 (`import.*PersistenceAdapter` grep 0)
- [ ] Spring bean string lookup grep 0 (`getBean.*"[a-z]+PersistenceAdapter"` 0)
- [ ] `./gradlew :projects:wms-platform:apps:master-service:check :projects:wms-platform:apps:outbound-service:check :projects:wms-platform:apps:inventory-service:check :projects:wms-platform:apps:inbound-service:check :projects:wms-platform:apps:notification-service:check` 5개 모두 BUILD SUCCESSFUL
- [ ] API endpoint / event contract / Flyway schema 변경 0건 (`git diff` 에 0)

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0 따라 `projects/wms-platform/PROJECT.md` 읽고 rule layer 로드.

- `platform/naming-conventions.md:21` — `*RepositoryImpl` 표준
- `platform/refactoring-policy.md` — Rename 카테고리
- `platform/coding-rules.md`
- TASK-BE-295 (선행) — spec 결정 + 2 architecture.md 갱신 결과
- `projects/wms-platform/specs/services/{master,outbound,inventory,inbound,notification}-service/architecture.md`

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md` — Rename 섹션, Worktree Dispatch Verification

# Related Contracts

- 없음

---

# Target Services

- master-service, outbound-service, inventory-service, inbound-service, notification-service

---

# Implementation Notes

- **선행 grep**: `getBean.*PersistenceAdapter` / `@Qualifier.*PersistenceAdapter` monorepo-wide grep → 0 확인 후 진행. 1+ 발견 시 STOP 후 별 처리 결정.
- **rename 순서**: 1 service 씩 처리 권장 (각자 `:check` GREEN 확인 후 다음으로). 또는 mechanical 이라 일괄 처리 후 5 service `:check` 한 번에.
- **테스트 파일 영향**: test 파일이 `XxxPersistenceAdapter` 를 import 하면 동시 갱신. assertion logic 변경 금지.
- **GAP PR #806 + 8-PR sweep 6개 (BE-293/ERP-BE-003/FAN-BE-008/FIN-BE-006/BE-315/SCM-BE-016)** 의 동일 패턴 선례 — wms 만 미적용 outlier 해소.

## 분산 처리 옵션

5 service 가 독립적이라 5 worktree 병렬 dispatch 가능 (BE-294 와 동일 패턴). 단일 PR 1개로 묶는 게 review 부담 적음 — 처음부터 1 worktree 1 sweep 권장.

---

# Edge Cases

- **Spring bean default name 충돌**: rename 후 default name 이 lowerCamel (`outboxJpaRepositoryImpl` 같은) 다른 component 와 충돌 위험. 사전 grep + 충돌 시 `@Component("specific-name")` 명시.
- **`@Repository` annotation 보존**: PersistenceAdapter 가 `@Repository` 갖고 있으면 RepositoryImpl 에도 그대로 유지 (Spring exception translation 보장).
- **JPA repository naming convention**: 만약 PersistenceAdapter 가 JpaRepository 직접 implements 라면 `*RepositoryImpl` 이 어색할 수 있음 — 클래스 내부 structure 보고 적절 suffix 결정 (`*JpaRepositoryImpl` 또는 `*RepositoryImpl`).

---

# Failure Scenarios

- string-based bean lookup 누락 → application context 로딩 실패 → IT fail. 사전 grep 누락 시 발생.
- `@Qualifier("xxxPersistenceAdapter")` 누락 → NoSuchBeanDefinitionException. grep 필수.
- 테스트의 `@MockBean(name = "...PersistenceAdapter")` 누락 → silent ignore + bean injection fail.

---

# Test Requirements

- 기존 단위 + IT 전부 통과 (assertion 변경 0)

Test command:

```
./gradlew :projects:wms-platform:apps:master-service:check :projects:wms-platform:apps:outbound-service:check :projects:wms-platform:apps:inventory-service:check :projects:wms-platform:apps:inbound-service:check :projects:wms-platform:apps:notification-service:check
```

---

# Definition of Done

- [ ] 30 파일 rename 완료
- [ ] 5 service `:check` BUILD SUCCESSFUL
- [ ] grep verification (PersistenceAdapter literal 0)
- [ ] commit + push
- [ ] Ready for review
