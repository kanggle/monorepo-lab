# Task ID

TASK-BE-294

# Title

wms refactor sweep — controller boilerplate + dead port (2026-05-25 scan)

# Status

done

# Owner

backend

# Task Tags

- code

---

# Goal

2026-05-25 scan 에서 wms-platform 3 서비스에 식별된 mechanical L2/L5/L6 hotspot 을 단일 PR 로 정리. `*PersistenceAdapter` naming 충돌은 spec 결정 필요 (별 task TASK-BE-295) 로 분리.

---

# Scope

## In Scope

| L | 대상 | 변경 |
|---|---|---|
| L6 | `apps/master-service/.../controller/{Warehouse,Sku,Zone,Lot,Partner,Location,LocationCreate}Controller.java` (7개) — `etag()` / `sortField()` / `sortDirection()` / `parseStatus()` 4 메서드 × 7 컨트롤러 = ~115회 중복 | service-local `ControllerSupport` 유틸 클래스 (static method 또는 default-method interface) 추출 → 7 컨트롤러는 helper 호출만 |
| L1 | `apps/master-service/.../adapter/out/messaging/OutboxDomainEventPortAdapter.java:13` | → `OutboxDomainEventAdapter` (또는 `OutboxDomainEventPortImpl`) — `*PortAdapter` 잔재 정리 |
| L5 | `apps/master-service/.../adapter/in/web/filter/IdempotencyFilter.java:82` `doFilterInternal` 110 line | `lockAndProceed()` 등 private 메서드 분리, 메서드별 line 수 < 40 |
| L2 | `apps/inbound-service/.../application/port/out/OutboxWriterPort.java` | `@Deprecated` 빈 marker 인터페이스 삭제 — 구현체 0, 참조 0, 자체 javadoc 에 `InboundEventPort` 사용 권장 명시 |
| L1 | `apps/notification-service/.../application/port/{outbound,inbound}/` 디렉토리 | → `out` / `in` (다른 wms 서비스와 일관) |

## Out of Scope (별 task)

- `apps/outbound-service`, `apps/inventory-service`, `apps/master-service` 의 `*PersistenceAdapter` naming (architecture.md 명시 vs naming-conventions.md 충돌, `## Overrides` 없음) → TASK-BE-295 spec 결정 선행
- `ConfirmShippingService.java:75` 10-param ctor 분리 (M 크기, `ShippingContext` record 도입은 별 설계 결정)
- `admin-service` Layered 패턴 — clean 판정, 변경 없음

---

# Acceptance Criteria

- [ ] `ControllerSupport` 클래스 도입, 4 메서드 (etag/sortField/sortDirection/parseStatus) 가 7 controller 에서 중복 정의되지 않음 — grep `private.*etag\(` / `parseStatus\(` 결과 master-service 본문에서 ≤1 (helper 정의)
- [ ] `OutboxDomainEventPortAdapter` → `OutboxDomainEventAdapter` (또는 `*PortImpl`) rename, config/bean 참조 갱신
- [ ] `IdempotencyFilter.doFilterInternal` line 수 < 60, `lockAndProceed` 등 private 메서드 ≥1
- [ ] `OutboxWriterPort.java` 파일 삭제, 호출처 / 구현체 grep 결과 0
- [ ] notification-service 의 `port/outbound` → `port/out`, `port/inbound` → `port/in` 패키지 rename 완료, 모든 import 갱신
- [ ] `./gradlew :projects:wms-platform:apps:{master,inbound,notification}-service:check` 3개 모두 GREEN
- [ ] contract / schema 변경 0건

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0.

- `platform/refactoring-policy.md`
- `platform/naming-conventions.md` — `*RepositoryImpl` / `*Impl` / `*Adapter` 표준; port 패키지 `port/in`, `port/out` 표준
- `platform/coding-rules.md`
- `projects/wms-platform/specs/services/{master,inbound,notification,outbound,inventory,admin,gateway}-service/architecture.md`

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md` — Extract Class / Rename / Dead Code Removal

# Related Contracts

- 없음

---

# Target Services

- master-service, inbound-service, notification-service

---

# Implementation Notes

- `ControllerSupport` 위치: `apps/master-service/src/main/java/com/example/wms/master/presentation/support/ControllerSupport.java` (또는 `presentation/web/ControllerSupport`). Static method utility 또는 abstract base controller 둘 다 가능 — static 권장 (Spring DI 영향 없음).
- `OutboxDomainEventPortAdapter` rename 후 Spring bean 이름이 default lowerCamel → bean 이름이 `outboxDomainEventAdapter` 로 바뀜. `getBean` string 호출 grep 필수.
- `OutboxWriterPort` 삭제 — 자체 javadoc 에 `@Deprecated` + 사용 권장 indicator 있다 했으므로, 삭제 전 monorepo-wide grep `OutboxWriterPort` 0건 확인.
- notification-service package rename 시 IDE refactor 기능 사용 권장 (grep + sed 보다 안전). 또는 PowerShell `Get-ChildItem -Recurse` + `-replace`.

---

# Edge Cases

- `ControllerSupport.parseStatus` 가 7 controller 별로 다른 enum 을 받으면 generic `parseStatus(String, Class<T extends Enum<T>>)` 시그니처 필요. 또는 controller 별 helper.
- `IdempotencyFilter` 의 lock-wait 루프 분리 시 timeout / interrupt 처리 누락 위험 — 기존 동작 그대로 옮김 보장.
- notification-service 의 `port/outbound` 안에 다른 서비스가 import 하는 클래스가 있으면 cross-project rename 필요 — grep `import.*notification.*port.outbound` monorepo-wide.

---

# Failure Scenarios

- `ControllerSupport` 추출 후 `parseStatus` 의 enum 타입 추론 실패로 컴파일 깨짐 → generic 시그니처 또는 controller-local override.
- `OutboxDomainEventPortAdapter` rename 후 어떤 `@Qualifier("outboxDomainEventPortAdapter")` 가 있으면 NoSuchBeanDefinition.
- `OutboxWriterPort` 삭제 후 사실 어떤 reflection 호출이 있다면 ClassNotFoundException — 충분히 grep.
- notification port package rename 후 `META-INF/spring.factories` 또는 ServiceLoader config 가 옛 패키지 참조하면 startup 실패.

---

# Test Requirements

- 기존 단위 + IT 전부 통과
- `ControllerSupport.parseStatus` 단위 테스트 1개 (valid + invalid enum)
- `IdempotencyFilter` 의 lock-wait 시나리오 IT 가 이미 있으면 그대로 통과

Test command:

```
./gradlew :projects:wms-platform:apps:master-service:check :projects:wms-platform:apps:inbound-service:check :projects:wms-platform:apps:notification-service:check
```

---

# Definition of Done

- [ ] 5개 변경 항목 전부 구현
- [ ] 3 service `:check` BUILD SUCCESSFUL
- [ ] `*PersistenceAdapter` naming 은 TASK-BE-295 로 deferred 명시
- [ ] commit + push
- [ ] Ready for review
