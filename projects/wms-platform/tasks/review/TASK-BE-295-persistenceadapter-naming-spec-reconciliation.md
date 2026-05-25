# Task ID

TASK-BE-295

# Title

wms `*PersistenceAdapter` naming — spec vs naming-conventions 충돌 결정 (spec task)

# Status

review

# Owner

backend

# Task Tags

- spec

---

# Goal

`projects/wms-platform/specs/services/{master,outbound,inventory}-service/architecture.md` 가 명시적으로 `*PersistenceAdapter` 접미사를 선언하는 반면, `platform/naming-conventions.md` 는 `*RepositoryImpl` 을 표준으로 요구한다. `## Overrides` 블록 없음 → CLAUDE.md Source of Truth Priority 충돌 → HARDSTOP-04 잠재 trigger.

이 task 는 **spec 결정만** 수행. 코드 변경 없음.

선택지 둘 중 하나로 정리:
- **A**: 각 architecture.md 에 `## Overrides` 섹션 추가 — `*PersistenceAdapter` 가 common naming-conventions 의 `*RepositoryImpl` 을 의도적으로 override 하는 근거 명시. 영향 받는 wms 어댑터 N개는 그대로 유지.
- **B**: architecture.md 본문을 수정 — `*PersistenceAdapter` 표현을 `*RepositoryImpl` 로 갱신. 후속 task 로 wms 어댑터 N개 rename (별 sweep task).

선택 후 architecture.md 갱신 (A 또는 B) 까지가 이 task scope. rename 자체는 후속 task.

---

# Decision (2026-05-25) — **B 선택**

## 정량 분석

- **`*PersistenceAdapter.java` production class 파일** = 30개 (master 6 + outbound 9 + inventory 7 + inbound 5 + notification 3, admin/gateway 0). 본 task 초기 추정 "3 service" 는 stale — 실제 5 service.
- **wms `architecture.md` 의 `*PersistenceAdapter` mention** = **2 곳만** (master L97 + inventory L131). outbound/inbound/notification architecture.md 는 mention 0. **명시적·일관된 wms convention 으로 선언되지 않음** — 두 comment 모두 directory tree 의 옆 주석 (`# *PersistenceAdapter implementing out ports`) 형식, 본문 설명 무.
- **`platform/naming-conventions.md:21`**: `| Repository (impl) | PascalCase + RepositoryImpl | UserRepositoryImpl |` — authoritative 표준.
- **ADR 검색** (`docs/adr/` 전체) — `PersistenceAdapter` / `RepositoryImpl` 결정 ADR 0건.
- **다른 6 프로젝트** (2026-05-25 8-PR sweep 직후): 전부 `*RepositoryImpl`. wms 만 outlier.

## 결정 근거 (B = architecture.md 본문 수정 + 후속 rename task)

1. **competing convention 부재** = ADR-trigger 아님 (BE-302 의 메타 규칙 `project_refactor_sweep_status.md § Data store drift closure` 참조). 두 architecture.md 의 directory-tree comment 는 "competing convention" 이 아니라 **incidental drift** — 본문 어디에도 "wms 는 의도적으로 PersistenceAdapter 를 사용한다" 명시 무. 단순 narrative drift = reality-alignment 영역.
2. **naming-conventions.md = SoT layer 2** (rules/common 직계) > wms architecture.md = SoT layer 7. 충돌 시 common 이 이김 (`rules/README.md` resolution order).
3. **monorepo-wide consistency** = 6/7 프로젝트 = `*RepositoryImpl`. wms 가 outlier 유지 → 향후 모든 cross-project refactor / scan / agent dispatch 에서 분기 처리 비용 누적. BE-302/303/305 의 "post-sweep backlog=0" discipline 와 정면 충돌.
4. **위험도**: 30 file mechanical rename, GAP PR #806 / 8-PR sweep 6개 선례 그대로 답습. Spring bean 이름 변경 영향 grep + string-based `getBean(...)` 검색 선행만 하면 risk 0.

A (Override + retain) 거부 사유: directory tree comment 가 override 라는 spec-narrative 가 어디에도 없음. `## Overrides` 를 작성하려 해도 "왜 이 변형이 더 나은가" 의 도메인 근거 무 — 정당화 없이 override 만 추가하는 것은 spec-debt 누적.

## 실행

- 본 task: 2 architecture.md 의 comment 갱신 (`# *PersistenceAdapter implementing out ports` → `# *RepositoryImpl implementing out ports`). 본문 0 byte 외 변경 0.
- 후속 `TASK-BE-296-wms-persistenceadapter-rename` 신규 생성 (ready/) — 30 file rename + 호출처 import 갱신 + 5 service `:check` GREEN + Spring bean string lookup 사전 grep.

---

# Scope

## In Scope

- 분석:
  - 영향 받는 모든 `*PersistenceAdapter` 클래스 파일 수 (master / outbound / inventory 합계) 정량
  - architecture.md 가 `*PersistenceAdapter` 를 명시한 이유 (도메인 의도, 헥사고날 적용 결정) 추적 — 가능하면 ADR 발견
  - common naming-conventions.md 의 `*RepositoryImpl` 표준 도입 배경 + 다른 프로젝트 (GAP PR #806, scm sweep 등) 적용 사례 정리
- 결정 문서:
  - 선택지 A 또는 B 를 본 task 본문에 결론 + 근거로 명시
  - 선택지 A 채택 시 → 3 architecture.md 에 `## Overrides` 섹션 추가
  - 선택지 B 채택 시 → 3 architecture.md 본문의 `*PersistenceAdapter` 표현을 `*RepositoryImpl` 로 update, 후속 rename task 신규 생성 (`TASK-BE-296-wms-persistenceadapter-rename`)
- 별 ADR 가 필요한지 판단:
  - 결정의 monorepo 전체 파급 (다른 프로젝트의 `*Adapter` / `*PersistenceAdapter` 사용 사례) 있다면 `docs/adr/ADR-MONO-XXX.md` 추가 권장

## Out of Scope

- 어떤 코드 파일도 rename 하지 않음 — TASK-BE-296 (만약 B 선택) 에서 진행

---

# Acceptance Criteria

- [ ] 영향 범위 정량 (파일 수 + 패키지 수) 본 task 본문에 기록
- [ ] 선택지 A 또는 B 결론 + 근거 본 task 본문에 기록
- [ ] A 선택 시: 3 architecture.md 에 `## Overrides` 섹션 추가, common 규칙 (`naming-conventions.md` 의 `*RepositoryImpl`) 을 명시적으로 relax 하는 근거 기술
- [ ] B 선택 시: 3 architecture.md 본문 update + 후속 rename task 파일 (`TASK-BE-296-...md`) 신규 생성
- [ ] 필요 시 ADR 1개 작성 (`docs/adr/ADR-MONO-XXX-wms-persistence-adapter-naming.md`)

---

# Related Specs

- `CLAUDE.md` — Source of Truth Priority layer 2~4 conflict resolution (`## Overrides` 규칙)
- `platform/naming-conventions.md`
- `platform/hardstop-rules.md#hardstop-04`
- `projects/wms-platform/specs/services/master-service/architecture.md`
- `projects/wms-platform/specs/services/outbound-service/architecture.md`
- `projects/wms-platform/specs/services/inventory-service/architecture.md`
- `rules/README.md` — domain/trait override 규칙

# Related Skills

- `.claude/skills/common/refactor-spec/SKILL.md` (있다면)

---

# Target

- specs/services/master|outbound|inventory architecture.md (해당 시 update)
- 또는 ADR 신규

---

# Implementation Notes

- 분석은 read-only — `Grep` 으로 `*PersistenceAdapter` 클래스 enumerate, `architecture.md` 안의 해당 단어 frequency / 문맥 추적.
- ADR 가치 판단: 다른 프로젝트가 `*Adapter` / `*PortAdapter` / `*JpaAdapter` 등 변형을 가지고 있어 monorepo-wide 명명 결정이 필요하다면 ADR 작성. 단일 프로젝트 내부 결정이면 architecture.md `## Overrides` 만으로 충분.

---

# Failure Scenarios

- 결정 미루면 wms 어댑터들이 무한정 conflict 상태 유지 → 다음 refactor scan 마다 같은 hotspot 재발견.
- A 선택 후 다른 프로젝트가 같은 패턴을 따라 하면 monorepo-wide 일관성 깨짐 → ADR 권장.

---

# Definition of Done

- [ ] 본 task 본문에 결정 + 근거 기록
- [ ] A 또는 B 선택에 따른 spec/문서 변경 완료
- [ ] commit + push
- [ ] Ready for review
