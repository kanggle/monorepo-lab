# Task ID

TASK-MONO-435

# Title

ADR-MONO-051 D2 승격 — cross-project 식별자 = 비즈니스 CODE 규칙을 `platform/service-boundaries.md` 정경으로

# Status

ready

# Owner

architecture / platform

# Task Tags

- docs
- governance
- platform

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

[ADR-MONO-051](../../docs/adr/ADR-MONO-051-master-data-stays-federated.md) **D2** — "프로젝트 경계를 넘는 식별자는 내부 UUID PK 가 아니라 **비즈니스 코드**이며, 소비자가 consume 시점에 code → 자기 PK 로 해석한다" — 를 [`platform/service-boundaries.md`](../../platform/service-boundaries.md) 로 승격한다.

ADR-051 §7 이 ACCEPT 시점에 이 승격을 **적격(eligible)** 으로 표시했다. 승격이 필요한 이유는 **규칙이 ADR 1곳에만 있으면 사실상 없는 규칙**이기 때문이다 — 에이전트와 개발자가 실제로 읽는 층은 `CLAUDE.md` → `platform/` 이고, ADR 은 결정의 *기록*이지 상시 로드되는 규칙 표면이 아니다. 같은 클래스의 실패를 이 저장소가 반복해 왔다(`MONO-402`·`405`·`407`: 규칙은 문서에 있고 실행 경로에는 없다).

## 🔴 착수 전 재측정에서 이미 드러난 것 (범위를 좁힌다)

**`platform/service-boundaries.md:86` 이 이미 D1·D3 를 담고 있다:**

> Master/reference data (catalog-style data that multiple services need to read) is owned by **exactly one service** and distributed **via events for local caching**.

즉 ADR-051 의 D1(연합 소유)·D3(소비자 소유 프로젝션)는 **이미 정경에 있다 — 갈라진 것이 아니라 전파된 상태**다. 반사적으로 "ADR 결정 6개를 전부 승격"하면 있는 것을 다시 쓰게 된다.

**실제로 없는 것은 D2 뿐이다.** `platform/` 전역 grep(`cross-service|cross-project` × `code|uuid`, `identifiers are codes`) → **0건**. 식별자가 어떤 *형태*로 경계를 넘는지는 어디에도 선언돼 있지 않다.

⇒ 본 task 범위 = **D2 한 줄 계열의 추가**. D1/D3 재서술 금지.

---

# Scope

## In Scope (impl PR 가 수행)

### 1. `platform/service-boundaries.md` § Data Boundaries 에 D2 추가

기존 master/reference data 불릿(86행) **직후**에, 그 규칙의 형제로 배치한다(같은 절, 같은 층위 — 소유·전파는 있는데 *식별자 형태*만 비어 있으므로).

내용 요건:

- 프로젝트 경계를 넘는 식별자는 **비즈니스 코드**(`skuCode`, `warehouseCode`, `partnerCode`, `poNumber` 류)이며 내부 UUID PK 가 아니다.
- 소비자는 consume 시점에 **code → 자기 PK** 로 해석한다. 생산자는 소비자별 매핑 테이블을 두지 않는다.
- **근거 한 줄**: 코드는 상대 시스템이 부재하거나 재시드되거나 standalone 으로 추출돼도 살아남는 계약이지만, UUID PK 는 생산자가 안정성을 약속한 적 없는 구현 세부다.
- 출처 back-link: `docs/adr/ADR-MONO-051` (전역 결정) + `ADR-MONO-050` §7 D9 (최초 leg).

**프로젝트 고유명 금지** — `platform/` 은 project-agnostic 이어야 한다(HARDSTOP-03). `skuCode` 같은 필드명은 *패턴 예시*로만 쓰되 서비스명·프로젝트명은 넣지 않는다. 예시조차 위험하면 일반형(`<entity>Code`)으로.

### 2. ADR-051 §7 갱신

"적격(eligible)" → **승격 완료**로 전환하고 `platform/service-boundaries.md` 를 가리킨다. **§1~§6·§8 무수정** — ACCEPTED 본문은 amendment 없이 재작성하지 않는다(§8 규정).

### 3. Lifecycle

본 task `ready/` → `review/` + root `tasks/INDEX.md` 반영.

## Out of Scope

- **D1·D3 재서술 0** — 86행에 이미 있다. 중복 추가는 정경을 갈라지게 만든다.
- **D6 승격 0** — D6(허브형 제안은 standalone 추출 생존 선실증)는 배포·추출 전략 규칙이라 `service-boundaries.md` 가 아니라 `TEMPLATE.md § Discovery → Distribution` 계열이 자연스러운 집이다. **본 task 에서 판단하지 않고 남긴다** — 필요하면 별 task.
- **기존 컨트랙트 수정 0** — D2 는 기술(記述)적 승격이다. 위반 지점이 발견되면 고치지 말고 티켓팅(§Failure B).
- **프로젝트 스펙/코드 0** — `projects/**` 무변경.
- ADR-051 §1~§6·§8 수정 0.

---

# Acceptance Criteria

0. **AC-0 재측정** — 착수 시 다시 확인한다: (a) `service-boundaries.md` 86행이 여전히 D1·D3 를 담고 있는가, (b) `platform/` 전역에 D2 상당 규칙이 정말 0건인가. **있으면 범위를 다시 좁힌다 — 코드가 이긴다.** (선행 숫자는 출처가 아니라 가설.)
1. `platform/service-boundaries.md` § Data Boundaries 에 D2 규칙 존재, master/reference data 불릿의 형제로 배치.
2. 규칙문에 **근거**(코드는 상대 부재·재시드·standalone 추출을 견디는 계약 / UUID PK 는 약속된 적 없는 구현 세부)가 포함.
3. ADR-051 / ADR-050 §7 D9 back-link 존재.
4. **project-agnostic**: `platform/service-boundaries.md` diff 에 서비스명·프로젝트명 0건 (`wms`, `scm`, `erp`, `ecommerce` 등). 필드명은 패턴 예시로만.
5. **D1·D3 중복 서술 0** — 86행 불릿 무수정, 같은 내용 재진술 없음.
6. ADR-051 §7 이 승격 완료 + 링크로 갱신, §1~§6·§8 무변경(`git diff` 로 확인).
7. doc-only: `platform/` 1파일 + `docs/adr/` 1파일 + lifecycle. `projects/**`·코드·빌드·CI 0.
8. `bash scripts/check-adr-index-drift.sh` GREEN (Status·Date 불변 확인 — 본 task 는 ADR Status 를 건드리지 않는다).

---

# Related Specs

- [ADR-MONO-051](../../docs/adr/ADR-MONO-051-master-data-stays-federated.md) — D2 출처, §7 이 승격을 적격 표시
- [ADR-MONO-050](../../docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md) §7 D9 — 최초 leg 결정(전역화 이전)
- [`platform/service-boundaries.md`](../../platform/service-boundaries.md) § Data Boundaries — 승격 대상 위치 (86행 master/reference data 불릿이 형제)
- [`platform/shared-library-policy.md`](../../platform/shared-library-policy.md) — project-agnostic 요건(HARDSTOP-03) 근거
- [`rules/README.md`](../../rules/README.md) — 규칙 해석 순서(왜 `platform/` 이 실효 표면인가)

---

# Related Contracts

- 없음 — 규칙 문서 승격. 컨트랙트 변경 0. (기존 컨트랙트가 D2 를 이미 따르고 있음은 ADR-051 §6 검증표가 근거.)

---

# Target Service / Component

- `platform/service-boundaries.md` (§ Data Boundaries)
- `docs/adr/ADR-MONO-051-master-data-stays-federated.md` (§7 only)
- (no production / project / build / CI change)

---

# Edge Cases

1. **D1/D3 중복 유혹**: 86행을 못 보고 "마스터는 한 서비스가 소유" 를 새로 쓰면 정경이 두 곳에서 갈라진다. **추가 전 86행을 읽는다.**
2. **project-specific 누출**: 근거를 설득력 있게 쓰려다 `wms master-service` 같은 실례를 넣으면 HARDSTOP-03. 실례는 ADR 에 있고 `platform/` 은 가리키기만 한다.
3. **ADR 본문 재작성 유혹**: ACCEPTED ADR 이므로 §7 외 수정 금지. 개선 필요 시 amendment 섹션(ADR-050 §7 패턴).
4. **D6 동시 승격 유혹**: 집이 다르다. 범위 밖으로 남기고 판단도 하지 않는다.
5. **승격이 무손실이라는 가정**: 옮긴 뒤 **원본과 대조**한다 — 과거 정경 승격에서 규칙 1개가 유실된 전례가 있다(console UI 컨벤션 건). D2 의 두 구성요소(코드로 넘어간다 + 소비자가 로컬 해석한다) 가 **둘 다** 살아남았는지 확인.
6. **stacked PR 재발**: spec 머지 → `fetch` → **새 main 에서** impl 브랜치. 검증은 `git diff origin/main...HEAD --name-status` 의 `R###` + 트리(`git ls-tree`).

---

# Failure Scenarios

## A. `platform/` 에 D2 상당 규칙이 이미 있음이 AC-0 에서 드러남

→ 승격 불필요. **phantom 으로 기록하고 task 를 종료**한다(없는 병에 수술 금지). ADR-051 §7 만 "이미 정경에 존재" 로 갱신.

## B. D2 를 위반하는 기존 컨트랙트 발견

→ D2 는 이미 저장소가 하던 것의 승격이므로, 위반 발견은 **기존 결함의 발견**이다. 본 task 에서 고치지 말 것(범위 폭발) — 별 task 로 티켓팅하고 규칙 승격은 그대로 진행. 규칙을 위반에 맞춰 약화시키지 않는다.

## C. project-agnostic 표현이 불가능하다고 판단될 때

→ 규칙 자체가 project-specific 이라는 뜻이므로 승격 대상이 아니다. STOP + 재검토. (D2 는 실제로는 일반 규칙이므로 이 시나리오는 표현 문제일 가능성이 높다.)

---

# Test Requirements

- `platform/service-boundaries.md` diff 에 서비스명/프로젝트명 grep → 0건.
- 86행 master/reference data 불릿 무변경(`git diff` 확인).
- ADR-051 `git diff` 범위 = §7 only.
- `bash scripts/check-adr-index-drift.sh` GREEN.
- 트리 검증: `git ls-tree -r --name-only origin/main tasks/ready tasks/review | grep 435` → 정확히 1개 경로.
- markdown lint green.

---

# Definition of Done

- [ ] AC-0 재측정 (86행 D1/D3 존재 · D2 부재 재확인)
- [ ] D2 규칙 `platform/service-boundaries.md` § Data Boundaries 에 추가 (근거 + back-link 포함)
- [ ] project-agnostic (서비스·프로젝트명 0건)
- [ ] D1/D3 중복 0
- [ ] 승격 무손실 대조 (D2 두 구성요소 생존 확인)
- [ ] ADR-051 §7 승격 완료로 갱신, §1~§6·§8 무변경
- [ ] doc-only diff scope
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: 분석=Opus 4.8 / **구현 권장=Sonnet** (범위가 AC-0 재측정으로 이미 좁혀졌고, 남은 것은 규칙문 1개 작성 + 위치 배치 + 링크 정합).
- **분량**: small — 파일 2개.
- **dependency**:
  - `선행`: TASK-MONO-433(ADR 작성) · TASK-MONO-434(ACCEPT) — 둘 다 `done/`, main 랜딩 완료(`afec7c68c` · `c5817dd16` · close `0fd9a55b2`).
  - `후속`: 없음. D6 승격은 별건이며 본 task 가 판단하지 않는다.
- **이 task 가 방어하는 실패 모드**: ADR 에만 존재하는 규칙은 결정이 내려졌다는 기록일 뿐 강제력이 없다. 다음 cross-project seam 을 설계하는 사람은 `platform/` 을 읽지 ADR 55개를 읽지 않는다.
