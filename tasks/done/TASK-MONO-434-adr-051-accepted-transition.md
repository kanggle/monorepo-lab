# Task ID

TASK-MONO-434

# Title

ADR-MONO-051 PROPOSED → ACCEPTED governance flip (user-explicit ADR-naming intent, 2026-07-20)

# Status

done

# Owner

architecture / docs

# Task Tags

- docs
- governance
- adr

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

[ADR-MONO-051](../../docs/adr/ADR-MONO-051-master-data-stays-federated.md) 「Master data stays federated (no central MDM hub)」을 `PROPOSED` → `ACCEPTED` 로 전환한다.

**게이트 근거**: 사용자 2026-07-20 발화 **"ADR-MONO-051 ACCEPT"** — [`platform/architecture-decision-rule.md`](../../platform/architecture-decision-rule.md) § The ACCEPTED Gate 가 요구하는 **정확형 ADR-naming intent**. 작성자(TASK-MONO-433)와 인가자를 분리하는 원칙에 따라 **별 task** 로 처리한다(TASK-MONO-430 → 431 선례 동형).

## 무엇이 인가되는가

ADR-051 은 "아무것도 바꾸지 않는다"가 결론이므로, ACCEPT 는 **새 구현을 승인하는 것이 아니라 다음 세 가지를 구속력 있게 만드는 것**이다:

1. **D2 (cross-project 식별자 = 비즈니스 CODE)** — 이후 모든 cross-project seam 설계에 적용되는 규칙. ADR-050 §7 D9 의 전역 일반화.
2. **D6 (standalone 추출 제약)** — 5개 프로젝트가 식별자 해석을 위해 호출해야 하는 컴포넌트 제안은 `sync-portfolio.sh` 추출 생존을 **먼저 실증**해야 하며, 미실증 시 추가 논증 없이 기각 가능.
3. **D5 (트리거)** — 공급자 정체성이 두 프로젝트에서 동시에 같은 것을 의미해야 하는 순간, 그리고 그때의 답(허브 아님 / scm v2 `supplier-service` + erp 소유 유지).

D4(공급자 3벌 수용)는 **조건부 결정**임을 유지한다 — ACCEPT 가 그것을 영구 승인으로 바꾸지 않는다.

---

# Scope

## In Scope (impl PR 가 수행)

### 1. `docs/adr/ADR-MONO-051-master-data-stays-federated.md` governance flip

- `**Status:**` `PROPOSED` → `ACCEPTED`.
- `**History:**` 에 `ACCEPTED 2026-07-20 (TASK-MONO-434 — user-explicit ADR-naming intent "ADR-MONO-051 ACCEPT")` 추가. **`Date:` 는 이동 금지** — `docs/adr/INDEX.md` 규약상 `Date` = 제안일이며 ACCEPT 로 움직이지 않는다(그 규약이 없어서 12개 ADR 이 Date 를 잃은 전례).
- §8 Status history 표에 ACCEPTED 행 추가.
- §8 「ACCEPT gate」 문단을 **게이트가 해소된 사실**로 갱신 — self-ACCEPT 가 아니었음(작성=433, 인가=사용자, 전환=434)을 남긴다.
- §7 Outstanding follow-ups: D2 의 [`platform/service-boundaries.md`](../../platform/service-boundaries.md) 승격이 **이제 후보에서 적격(eligible)으로 전환**됨을 기록. 단 본 task 는 승격을 수행하지 않는다.

### 2. `docs/adr/INDEX.md` 051 행 Status 갱신

`PROPOSED` → `ACCEPTED`. **Date 열은 `2026-07-20` 유지**(제안일). `scripts/check-adr-index-drift.sh` GREEN 유지.

### 3. Lifecycle

본 task `ready/` → `review/` + root `tasks/INDEX.md` 반영.

## Out of Scope

- **본문 결정(D1–D6) 의미 변경 0** — ACCEPT 는 governance flip 이지 재작성이 아니다. 오탈자·링크 외 §1~§6 문장 수정 금지.
- **D2 의 `platform/service-boundaries.md` 승격 0** — §7 이 적격으로 표시할 뿐, 승격은 별 task.
- **D5 파생 작업 0** — 트리거 미발화. supplier 스키마 통합·`businesspartner.changed.v1` 구독자 추가·scm v2 `supplier-service` 착수 전부 범위 밖.
- TASK-MONO-433 파일 수정 0 (`review/` 이후 수정 금지 — `tasks/INDEX.md` § Review Rules).
- 코드/projects/빌드/CI 0.

---

# Acceptance Criteria

0. **AC-0 게이트 재확인** — 사용자 발화가 ADR 을 **이름으로 지목**했는지 확인(“ADR-MONO-051 ACCEPT”). bare “진행”/“해줘”였다면 **STOP** — self-ACCEPT 금지.
1. ADR-051 `**Status:** ACCEPTED`, `History` 에 PROPOSED·ACCEPTED 두 전환 모두 날짜와 함께 존재.
2. `**Date:** 2026-07-20` **불변**(제안일).
3. §8 표에 ACCEPTED 행 + 게이트 해소 서술(작성 433 / 인가 사용자 / 전환 434).
4. `docs/adr/INDEX.md` 051 행 Status = `ACCEPTED`, Date = `2026-07-20`.
5. `scripts/check-adr-index-drift.sh` **GREEN** (Status·Date 가 ADR 파일과 일치).
6. §1~§6 본문 의미 변경 0 — `git diff` 로 확인(§7·§8 및 헤더 필드 외 변경 없음).
7. doc-only diff: ADR + ADR INDEX + lifecycle(task 파일 + root INDEX). 코드/projects/빌드/CI 0.

---

# Related Specs

- [ADR-MONO-051](../../docs/adr/ADR-MONO-051-master-data-stays-federated.md) — 전환 대상
- [`platform/architecture-decision-rule.md`](../../platform/architecture-decision-rule.md) § The ACCEPTED Gate — 게이트 규약(정경)
- [`docs/adr/INDEX.md`](../../docs/adr/INDEX.md) — `Date` = 제안일 규약(§ 필드 설명)
- TASK-MONO-433 (`tasks/review/`) — 작성 task, 인가와 분리
- TASK-MONO-430 → 431 (ADR-050) — 작성↔ACCEPT 2단계 선례
- [ADR-MONO-050](../../docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md) §7 D9 — D2 의 출처

---

# Related Contracts

- 없음 — governance flip. 컨트랙트 변경 0.

---

# Target Service / Component

- `docs/adr/ADR-MONO-051-master-data-stays-federated.md` (헤더 + §7 + §8)
- `docs/adr/INDEX.md` (1행 Status)
- (no production / project / build / CI change)

---

# Edge Cases

1. **Date 이동 유혹**: ACCEPT 일자를 `Date:` 에 쓰면 INDEX 규약 위반. 전환일은 `History` 와 §8 에만.
2. **drift 스크립트가 Status 를 읽는 방식**: `check-adr-index-drift.sh` 는 ADR 파일과 INDEX 행을 **양방향** 대조하므로 둘 중 하나만 고치면 RED. **같은 커밋에서 함께** 바꾼다.
3. **본문 재작성 유혹**: ACCEPT 시점에 D4 서술을 강화하고 싶어질 수 있으나, 인가된 것은 **읽힌 그 문장**이다. 개선은 후속 amendment(ADR-050 §7 선례)로.
4. **D5 를 착수 신호로 오독**: ACCEPT 는 트리거를 **발화시키지 않는다**. 트리거는 여전히 미발화이며 파생 task 를 만들지 않는다.
5. **stacked squash 재발**: TASK-MONO-433 impl PR 이 spec 에 스택된 채 리타겟되어 rename 의 삭제 half 가 유실됐다(#2720 로 정정). 본 task 는 **spec 머지 후 새 main 에서 impl 브랜치를 딴다** — 스택 금지. 검증은 diff 가 아니라 **트리**(`git ls-tree`)로.

---

# Failure Scenarios

## A. drift 스크립트 RED

→ ADR 파일과 INDEX 행의 Status/Date 불일치. 둘을 같은 커밋에서 정렬. 스크립트 출력이 권위이며 "고쳤다" 진술은 증거가 아니다.

## B. ACCEPT 후 D2 가 기존 컨트랙트와 충돌 발견

→ D2 는 기술(記述)적이다 — 이미 저장소가 하고 있는 것을 규칙으로 승격한 것이므로 신규 충돌이 나오면 그것은 **기존 위반의 발견**이다. ADR 을 되돌리지 말고 위반 지점을 별 task 로 티켓팅.

## C. 게이트 근거가 불충분하다고 판단될 때

→ STOP. `PROPOSED` 유지하고 사용자에게 정확형 intent 를 요청. 인가는 대리 판단 대상이 아니다.

---

# Test Requirements

- `bash scripts/check-adr-index-drift.sh` → GREEN (Status·Date 일치).
- `git diff` 범위 = ADR 헤더/§7/§8 + INDEX 1행 + lifecycle. §1~§6 무변경.
- ADR 파일 내 `Status:` 라인이 `ACCEPTED` 단일.
- 트리 검증: `git ls-tree -r --name-only origin/main tasks/ready tasks/review | grep 434` → **정확히 1개 경로**(`review/`). (#2720 재발 방지)
- markdown lint green.

---

# Definition of Done

- [ ] AC-0 게이트 재확인 (정확형 ADR-naming intent)
- [ ] ADR-051 Status = ACCEPTED, History 2전환, Date 불변
- [ ] §8 게이트 해소 서술 (작성 433 / 인가 사용자 / 전환 434)
- [ ] §7 D2 승격 = 적격 표시 (수행은 별건)
- [ ] `docs/adr/INDEX.md` Status 갱신 + drift GREEN
- [ ] §1~§6 의미 변경 0
- [ ] 트리 검증 통과 (lifecycle 사본 1개)
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: 분석=Opus 4.8 / **구현 권장=Sonnet** (governance flip — 결정은 이미 내려졌고 남은 것은 기계적 상태 전환 + drift 정합. TASK-MONO-431 동형).
- **분량**: small — 파일 2개, 라인 10 내외.
- **dependency**:
  - `선행`: TASK-MONO-433 (ADR 작성, `review/`, main 랜딩 완료 — `afec7c68c` + `8c2ae04b3`).
  - `후속`: (선택) D2 의 `platform/service-boundaries.md` 승격 task. D5 트리거 발화 시의 supplier-service 작업은 **후속 아님** — 조건부 미래.
- **PR 순서 규율**: spec PR 머지 → `git fetch` → **새 main 에서** impl 브랜치 생성. #2719 처럼 스택하지 않는다.
