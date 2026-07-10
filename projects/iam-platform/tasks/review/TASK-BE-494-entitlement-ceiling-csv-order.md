# Task ID

TASK-BE-494

# Title

fix(account-service): `EntitlementCeiling.domainsCsv()` 가 canonical sorted 가 아니다 — `Set.copyOf` 해시 순서가 정렬을 지운다 (main CI RED 복구)

# Status

review

# Owner

backend

# Task Tags

- code
- test
- bug

---

# Dependency Markers

- **선행**: `TASK-BE-491`(ADR-047 step 2a, PR #2370 머지) — 이 버그가 도입된 커밋. 본 태스크는 그 회귀를 고친다.
- **관련**: `TASK-BE-492`(admin-service org-node command gateway) — 같은 ADR 이지만 다른 모듈(admin-service). 충돌 없음.
- **차단 중**: `TASK-PC-FE-238`(PR #2372) — main 이 RED 라 close chore 불가. 본 태스크가 GREEN 을 복구해야 진행 가능.

---

# Goal

`main`(`954ecfef3`) 의 CI 가 **RED** 다. `Build & Test (JDK 21, Linux)` → `Build and check IAM backend (Docker-free)` →
`:projects:iam-platform:apps:account-service:test` 에서
`EntitlementCeilingTest > BOUNDED round-trips through a canonical, sorted CSV` 가 실패한다
(`EntitlementCeilingTest.java:179`, 477 tests / 1 failed).

원인은 테스트가 아니라 **프로덕션 코드**다. `EntitlementCeiling` 의 compact constructor:

```java
domains = (domains == null) ? Set.of() : Set.copyOf(new TreeSet<>(domains));
```

`TreeSet` 으로 정렬한 뒤 `Set.copyOf` 로 감싸는데, `Set.copyOf` 가 돌려주는 `ImmutableCollections.SetN` 은
**iteration order 를 보존하지 않는다** — 순회 순서가 원소 해시와 **JVM 기동마다 랜덤화되는 salt**
(`ImmutableCollections.SALT`, JDK 9+)의 함수다. 따라서 `String.join(",", domains)` 인 `domainsCsv()` 는
같은 ceiling 을 어떤 JVM 에서는 `erp,wms` 로, 다음 JVM 에서는 `wms,erp` 로 인코딩한다.

로컬 재현(동일 입력, JVM 8회 기동): `wms,erp` 4회 / `erp,wms` 4회 — **테스트는 약 50% 확률로 실패**한다.
PR #2370 은 운 좋은 실행에서 GREEN 으로 머지되었고, 이후 main 실행이 불운한 쪽에 걸려 RED 가 되었다.

이것은 flaky 테스트가 아니라 **flaky 프로덕션 인코딩**이다. 클래스 javadoc 이 명시한 계약
("Domain keys are stored in a canonical sorted order so the persisted CSV is deterministic")을
구현이 지키지 못하고 있고, 그 결과 `org_node.ceiling_domains` 컬럼에 **같은 ceiling 이 서로 다른 문자열로**
쓰인다.

---

# Scope

## In Scope

- `EntitlementCeiling` compact constructor — 정렬 순서를 필드까지 보존한다:
  `Collections.unmodifiableSet(new LinkedHashSet<>(new TreeSet<>(domains)))`.
  (`TreeSet` 을 그대로 두면 `Set<String>` 필드에 `SortedSet` 이 들어가 직렬화/동등성 의도가 흐려지므로,
  삽입 순서를 보존하는 `LinkedHashSet` 으로 sorted view 를 고정한 뒤 불변 래핑.)
- `EntitlementCeilingTest` 회귀 테스트 2개 추가:
  - `domainsCsvIsSortedNotHashOrdered` — 6개 도메인으로 canonical 순서를 단언(우연 통과 확률 1/720).
    필드 자체의 순회 순서(`domains()`)와 storage round-trip 도 함께 고정.
  - `intersectStaysCanonical` — `intersect` 결과도 canonical 순서를 유지.

## Out of Scope

- `ceiling_domains` 에 이미 비정규 순서로 저장된 행의 백필 — v1 은 아직 어떤 org-node 도 프로덕션에
  없고(ADR-047 seed = inert), `fromStorage` 는 순서와 무관하게 파싱하므로 읽기는 안전하다.
  실제 데이터가 생기기 전이므로 마이그레이션 불필요.
- `Set.copyOf` 를 쓰는 다른 클래스 감사 — 별개 스윕(순서 계약이 있는 곳만 문제).
- ADR-047 의 나머지 step(BE-492 / PC-FE-237).

---

# Acceptance Criteria

- [ ] **AC-1** `EntitlementCeiling.bounded(...)` 의 `domains()` 순회 순서와 `domainsCsv()` 가 **항상** 사전순이다 — JVM 기동과 무관.
- [ ] **AC-2** 기존 `boundedRoundTrip` 이 100% 통과한다(반복 실행으로 확인).
- [ ] **AC-3** `intersect` 결과의 CSV 도 canonical 순서다.
- [ ] **AC-4** `UNBOUNDED` ↔ `BOUNDED(∅)` 대립 불변식과 `fromStorage` 동작은 **불변**(기존 테스트 무수정 통과).
- [ ] **AC-5** `:projects:iam-platform:apps:account-service:test` 전체 GREEN. `--rerun-tasks` 로 3회 연속 GREEN(살트 의존이 사라졌음을 실증).
- [ ] **AC-6** main CI `Build & Test (JDK 21, Linux)` 가 GREEN 으로 복구된다.

---

# Related Specs

- `docs/adr/ADR-MONO-047-org-node-tenant-hierarchy.md` § D2/D3 (deny-only ceiling, 교집합 대수)
- `projects/iam-platform/specs/services/account-service/` (org-node 저장 모델)

# Related Contracts

- 없음(도메인 내부 인코딩 — 외부 계약 표면 없음).

---

# Target Service

- `iam-platform` / `account-service`

---

# Edge Cases

- `UNBOUNDED` 는 domains 를 갖지 않으므로(`Set.of()`) CSV 는 항상 `""` — 이 경로는 변경 없음.
- `BOUNDED(∅)` 도 CSV `""` 이지만 mode 로 구분된다 — `fromStorage` 가 mode 를 먼저 본다. 불변.
- record `equals`/`hashCode` 는 `Set.equals` 로 **순서 무관** — 정렬 도입이 동등성 의미를 바꾸지 않는다.
- `Collections.unmodifiableSet` 은 뷰 래퍼다. 백킹 `LinkedHashSet` 을 생성자 지역에서만 만들어 밖으로
  새지 않게 한다(현재 구현이 그러함) — 그래야 실질 불변이 유지된다.

---

# Failure Scenarios

- `TreeSet` 을 필드에 그대로 넣으면 순서는 맞지만 `Set<String>` 계약에 `SortedSet` 이 섞여 이후 리팩토링이
  순서를 우연히 의존하게 된다. Guard: `LinkedHashSet` + 불변 래핑으로 "정렬은 생성 시 1회" 를 고정.
- 2개 원소 테스트만으로 회귀를 잡으려 하면 salt 에 따라 우연히 통과한다(현재 테스트가 정확히 그 상태).
  Guard: AC-1 의 6원소 테스트.
- 백필을 시도하면 아직 존재하지 않는 행을 건드린다. Guard: Out of Scope 명시.
