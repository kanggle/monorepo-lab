# Task ID

TASK-MONO-031

# Title

libs/ audit — 사용 빈도 + 중복 + dead code + project-specific leak 검증

# Status

ready

# Owner

backend

# Task Tags

- audit
- libs
- chore

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Edge Cases
- Failure Scenarios

---

# Goal

공통규칙·스펙 정리 시리즈 5개 task 중 세 번째. `libs/java-{common, messaging, security, test-support, web}` 등 shared 라이브러리의 cross-project 사용 패턴 audit. Rule of Three 충족 시점 (4 프로젝트 동거) 에서 어느 라이브러리 클래스가 진짜 범용인지 / 한두 프로젝트 편향인지 / dead 인지 분류.

`platform/shared-library-policy.md` 준수 검증 — project-specific 도메인 용어 / 엔티티가 libs 안에 leak 됐는지.

---

# Scope

## In Scope

### 1. libs/ 모듈별 사용 빈도

각 libs/java-* 모듈의 public class 가 어느 service 에서 import 되는지 grep:

```
for lib in libs/java-*; do
  for class_file in $(find $lib/src/main/java -name "*.java"); do
    class_name=$(basename $class_file .java)
    package=$(grep "^package " $class_file | head -1 | sed 's/package //;s/;//')
    fqn="${package}.${class_name}"
    importers=$(grep -rln "import $fqn" projects/*/apps/*/src/main/java 2>/dev/null | wc -l)
    echo "$fqn: $importers"
  done
done
```

결과:
- **0 importers**: dead code 후보
- **1-2 importers**: 한두 프로젝트 편향, libs 가치 낮음
- **3+ importers (3+ projects)**: Rule of Three 충족 — 진짜 범용

### 2. 중복 검출

- libs 사이 중복 (예: java-common 의 `BaseEvent` ↔ java-messaging 의 유사 base class)
- libs ↔ project 중복 (예: 어느 프로젝트가 자체 `OutboxPollingScheduler` 를 갖고 있는데 libs 에도 있음)

### 3. project-specific leak 검출

`platform/shared-library-policy.md` 룰 위반:
- libs 안에 ecommerce / wms / fan-platform / GAP 도메인 용어 (Product, Warehouse, Post, Account 등) 등장 grep
- libs 안에 `com.{ecommerce,wms,fanplatform}.*` 같은 project-specific package 참조

### 4. dead code candidate 분류

**0 importer** 클래스 → 다음으로 분류:
- (a) 명백히 dead — 즉시 삭제 후보 (본 PR)
- (b) 향후 사용 예상 (예: 새 trait / domain 부트스트랩 시 사용 예정) — 보존 + 메모 추가
- (c) 테스트 fixture / utility — 검증 후 결정

### 5. shared-library-policy 준수 매트릭스

policy 의 각 룰 항목 ↔ 현 libs 상태 매핑.

## Out of Scope

- 실제 dead code 삭제 (본 task 는 카탈로그까지만, 삭제는 별도 fix task)
- libs 모듈 통합 / 재구조화 (예: java-common 을 더 잘게 쪼개기) — 별도 refactor task
- npm/pnpm packages/ 영역 — 본 task 는 java libs 만 (frontend libs 는 별도)

---

# Acceptance Criteria

- [ ] libs/java-* 의 public class 별 importer 카운트 매트릭스 PR body 첨부.
- [ ] dead code 후보 (0 importer) 카탈로그 + 분류 (a/b/c).
- [ ] 중복 후보 카탈로그.
- [ ] project-specific leak 검출 결과.
- [ ] shared-library-policy.md 준수 PASS / FAIL 항목별 결과.
- [ ] Critical (정책 위반 / 빌드 깨짐) fix 본 PR.
- [ ] Warning (3+ project 미사용 / 중복) follow-up task spec 발행 또는 본 PR fix.

---

# Related Specs

- `platform/shared-library-policy.md`
- `tasks/done/TASK-MONO-029-rules-validation-audit.md`
- `tasks/done/TASK-MONO-030-spec-drift-audit.md` (선행)

---

# Related Skills

- `simplify` (libs 코드 reuse / 품질 검토)
- `refactor-code` (필요 시)

---

# Target Component

- `libs/java-*` 전체
- `platform/shared-library-policy.md`

---

# Architecture

audit + chore. 코드 변경은 명백한 dead code (분류 a) 만 본 PR.

---

# Implementation Notes

- import 카운트 grep 패턴 위 In Scope 1 참조.
- frontend libs (`packages/`) 는 별도 task 권장. java libs 만 본 task.
- 4 프로젝트 모두 direct-include 패턴 (composite-build 폐기됨) 이라 settings.gradle 의 module path 가 단일 source.

---

# Edge Cases

- **테스트만 사용 (test-support)**: java-test-support 의 클래스는 production code 가 아닌 테스트에서 import. importer 카운트 시 test source set 도 포함.
- **reflection / Spring auto-config 사용**: import 안 보이지만 런타임에 사용. spring.factories / META-INF/spring/ 도 grep.
- **신규 프로젝트 부트스트랩 직후 (fan-platform)**: 아직 사용 안 한 libs 도 의도적 reserve 일 수 있음. 분류 b 권장.

---

# Failure Scenarios

- **삭제했는데 reflection 사용 중**: dead code 삭제 후 빌드 PASS / 테스트 PASS 하지만 런타임 NoClassDefFound. 본 task 의 "분류 a 즉시 삭제" 는 명백한 경우만.
- **leak 검출이 false positive**: 도메인 용어가 우연히 일치 (예: `Account` 가 일반 용어로도 GAP 도메인 용어로도 사용). 검출 후 매뉴얼 재확인.

---

# Test Requirements

- audit 자체가 검증.
- 분류 a 삭제 후 root `./gradlew build` PASS 확인.

---

# Definition of Done

- [ ] importer 카운트 매트릭스 + 4 카탈로그 (dead / 중복 / leak / policy) PR body.
- [ ] Critical fix.
- [ ] Ready for review.

---

# Prerequisites

- ✅ TASK-MONO-029 완료
- 권장: TASK-MONO-030 후 진행 (spec drift 가 libs 사용 패턴에 영향)
