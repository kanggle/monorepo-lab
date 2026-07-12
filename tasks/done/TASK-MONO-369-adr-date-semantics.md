# Task ID

TASK-MONO-369

# Title

ADR 의 `Date` 축이 기계 검사 불가능하다 — **Convention 이 `Date` 의 의미를 정의한 적이 없어서**다. 의미를 정하고, 빠진 12개를 채우고, 그제서야 가드한다

# Status

done

# Owner

monorepo

# Task Tags

- docs
- guard
- ci

---

# Dependency Markers

- **발굴 출처**: [`TASK-MONO-363`](../done/TASK-MONO-363-adr-index-drift.md) — ADR 인덱스를 백필하며 `Date` 를 가드하려다 **막혔다**. 그 이유를 스크립트 § *WHAT THIS DOES NOT GUARD* 와 `docs/adr/INDEX.md` 본문에 적어두고 **"별건"** 이라 미뤘다. **이 task 가 그 별건이다** — 티켓 없는 후속 노트는 가드 없는 표면과 똑같이 썩는다.
- **선례 (형태 재사용)**: `scripts/check-adr-index-drift.sh` (MONO-363) 를 **확장**한다. 새 스크립트가 아니다.

---

# Goal

`TASK-MONO-363` 이 `docs/adr/INDEX.md` 를 53행으로 백필하고 **ID 양방향 + Status** 를 가드로 묶었다. 그런데 **`Date` 열 53개는 아무도 지키지 않는 손-유지 데이터로 남았다** — 이 저장소가 반복해서 대가를 치른 바로 그 모양이다(`MONO-339` · `341` · `345` · `352` · `360` · `363`).

가드하려 했으나 **실측이 막았다.** 그리고 막은 이유가 이 task 의 핵심이다.

## 착수 전 실측 (2026-07-12, `036ed081f`) — 파일이 권위

| | |
|---|---|
| `**Date:**` 헤더가 **아예 없는** ADR | **12** (`020` `021` `032` `033` `034` `035` `036` `037` `038` `039` `042` `043`) |
| `Date` 필드가 **자기 `History` 와 어긋나는** ADR | **3** (`008` `018` `019`) |
| 날짜를 **복구할 수 없는** ADR | **0** — 12개 전부 자기 `**History:**` 에 날짜가 있다. **추측할 필요가 없다** |

## 진짜 원인 — "누가 틀렸나" 가 아니다

`docs/adr/INDEX.md § Authoring Convention` 은 `Date` 를 **required 필드로 나열만 하고, 그게 무엇의 날짜인지 한 번도 말하지 않는다.** 그래서 저자마다 다르게 썼다:

- 어긋나는 3개(`008` `018` `019`)는 **셋 다 일관되게 *제안* 날짜**를 적었다 (`008`: Date=2026-05-13, 실제 ACCEPTED=2026-05-18).
- 나머지는 대부분 PROPOSED 와 ACCEPTED 가 **같은 날**이라 두 해석이 **구별되지 않는다** — 그래서 아무도 모순을 못 느꼈다.
- 한편 `MONO-363` 이 만든 **INDEX 의 `Date` 열은 *현재 Status 에 도달한 날짜*** 를 쓴다(결정 인덱스가 답해야 하는 건 "언제 결정됐나" 이므로).

⇒ **둘은 서로 다른 두 필드이고, 둘 다 정당하다.** 문제는 **Convention 이 그 구분을 말한 적이 없다는 것**이고, 그래서 기계가 대조할 수 없다. **의미를 정하기 전에 가드를 붙이면 오탐 공장이 된다** — `MONO-360` 이 못박은 실패 모드(*첫날 RED 인 가드는 꺼지고, 꺼진 가드는 없는 가드보다 나쁘다 — skip 이 초록으로 보고되니까*).

---

# Scope

## In Scope

1. **의미를 못박는다** — `docs/adr/INDEX.md § Authoring Convention` 에 두 필드를 **명시적으로 구분해 정의**:
   - ADR 파일의 `**Date:**` = **그 결정 기록이 작성(제안)된 날짜** (기존 3개의 de-facto 용법을 승인 — 소급해서 뒤집지 않는다).
   - INDEX 의 `Date` 열 = **현재 `Status` 에 도달한 날짜** (`**History:**` 에서 파생; 전이가 없었으면 `**Date:**` 와 같다).
   - **둘이 다를 수 있다는 사실 자체를 적는다** — 이게 없어서 15개가 갈라졌다.
2. **빠진 12개를 채운다** — `**Date:**` 헤더 추가. **값은 각 ADR 자신의 `**History:**` 에서 읽는다**(첫 `PROPOSED` 날짜). git 로그·추측 금지 — **파일이 권위**.
3. **`**History:**` 를 required 로 승격** — 현재 Convention 에 없다. 그런데 INDEX 의 `Date` 열이 이제 여기서 파생되므로, 없으면 파생이 불가능하다.
4. **가드 확장** (`scripts/check-adr-index-drift.sh`, 새 스크립트 아님):
   - 모든 ADR 이 `**Date:**` 를 **선언하는가** (12개가 어긴 그 규칙)
   - 모든 ADR 이 `**History:**` 를 **선언하는가**
   - INDEX 의 `Date` 열 == 파일에서 파생한 현재-Status 날짜

## Out of Scope

- **`008`/`018`/`019` 의 `**Date:**` 를 승인 날짜로 고치는 것** — 그들의 용법이 새 Convention 상 **옳다**(제안 날짜). 고칠 것은 INDEX 열이 아니라 **Convention 의 침묵**이었고, INDEX 열은 `MONO-363` 이 이미 올바르게 채웠다. **소급 변경은 기록을 다시 쓰는 것이다.**
- **`Status` 값 변경** — 어떤 ADR 도 승격/강등하지 않는다. **`PROPOSED` → `ACCEPTED` 는 사람의 게이트**이고, 문서 정리 중의 승격은 **결정 위조**다(`009` · `046` · `049` 는 `PROPOSED` 로 남는다).
- **프로젝트-내부 ADR** (`projects/*/docs/adr/`) — INDEX 서문이 스스로 범위 밖이라 선언. 끌어들이면 첫날 RED.
- **`Title` 열 가드** — INDEX 제목은 큐레이팅된 요약이지 ADR H1 의 복사가 아니다(여러 H1 은 완전한 문장). 대조하면 오탐 공장.

---

# Acceptance Criteria

- [ ] **AC-1 — Convention 이 `Date` 의 의미를 말한다.** 두 필드(파일 `**Date:**` vs INDEX `Date` 열)가 **다를 수 있음을 포함해** 명시적으로 정의된다. 이게 이 결함의 뿌리다.
- [ ] **AC-2 — 12개 ADR 이 `**Date:**` 를 갖는다.** 값은 각자의 `**History:**` 에서 읽는다. **git 로그·추측 금지.** 실측: `020` `021` `032` `033` `034` `035` `036` `037` `038` `039` `042` `043`.
- [ ] **AC-3 — 가드가 Date 축을 검사한다.** `check-adr-index-drift.sh` **확장**(새 스크립트 아님): ①모든 ADR 이 `**Date:**` 선언 ②모든 ADR 이 `**History:**` 선언 ③INDEX `Date` 열 == 파일에서 파생한 현재-Status 날짜.
- [ ] **AC-4 — mutation, 전부 물어야 함** (그리고 **주입이 실제로 적용됐는지 먼저 확인** — 이 저장소가 두 번 당했다):
  1. 한 ADR 에서 `**Date:**` 줄 삭제 → **RED**(그 ADR 을 지목)
  2. 한 ADR 에서 `**History:**` 줄 삭제 → **RED**
  3. INDEX 의 `Date` 셀 하나를 파일과 다르게 조작 → **RED**
  4. **`008` 을 그대로 두고 통과해야 한다** — 파일 `**Date:**`=2026-05-13(제안) vs INDEX=2026-05-18(승인)은 **새 Convention 상 정상**이다. **이게 오탐 가드였다면 여기서 터진다.**
  - **vacuity**: 현 트리 baseline exit=0.
- [ ] **AC-5 — 오탐 0.** 53개 ADR 전부에 대해 baseline GREEN. **첫날 RED 인 가드는 꺼진다.**
- [ ] **AC-6 — 도달 가능성.** 필터(`adr-index`)는 이미 `docs/adr/**` 로 pure-positive 이고 **`code-changed` 와 AND 되어 있지 않다**(`MONO-363` 이 PR #2453 으로 **실측**). **AND 로 바꾸지 말 것.** 이 task 는 필터를 건드릴 필요가 없다 — 건드렸다면 뭔가 잘못한 것이다.
- [ ] **AC-7** — 코드 변경 0 → `./gradlew check` 무영향.

---

# Related Specs

- `docs/adr/INDEX.md` — **고칠 대상**(§ Authoring Convention 의 침묵이 뿌리)
- `scripts/check-adr-index-drift.sh` — **확장 대상**. § *WHAT THIS DOES NOT GUARD* 가 이 task 를 예고하고 있다
- `tasks/done/TASK-MONO-363-adr-index-drift.md` — 발굴 출처
- `tasks/done/TASK-MONO-360-*.md` — 오탐 0 · 도달성 실측 규율

# Related Contracts

없음 — 문서 + CI 가드.

---

# Edge Cases

- **`SUPERSEDED` ADR (`003` · `021`)** — 현재-Status 날짜는 *superseded 된* 날짜다(`021`: 2026-06-14). `003` 의 INDEX 셀은 `2026-05-08 → 2026-05-13` 처럼 **큐레이팅된 범위**다 — 파생 검사가 이걸 오탐으로 잡으면 안 된다. **셀이 파생 날짜를 *포함* 하는지**로 볼 것.
- **접미사 ADR** (`003a` · `003b` · `007a` · `012a`) — `012` 와 `012a` 는 **다른 결정**. 파서가 접미사를 흘리면 안 된다(`MONO-363` 의 mutation 4 가 이미 이걸 지킨다 — 확장이 그 성질을 깨뜨리지 말 것).
- **PROPOSED 인 ADR** (`009` · `046` · `049`) — `History` 에 `ACCEPTED` 줄이 **없다**. 파생 로직이 이때 빈 값을 뱉고 조용히 통과하면 **vacuous** 하다. `PROPOSED` 의 현재-Status 날짜 = `PROPOSED` 날짜.

# Failure Scenarios

- **F1 — 의미를 정하지 않고 가드부터 붙인다** → 15개 ADR 에서 첫날 RED → 가드가 꺼지고, **skip 이 초록으로 보고된다**. 그게 이 저장소가 반복해서 배운 실패다. Guard: AC-1 이 AC-3 보다 **먼저**.
- **F2 — `008`/`018`/`019` 의 `**Date:**` 를 "고친다"** → 결정 기록을 소급 변경하는 것이고, 게다가 **그들이 옳다**. Guard: § Out of Scope + AC-4-4.
- **F3 — 정리하면서 `PROPOSED` 를 승격** → **결정 위조**. Guard: § Out of Scope.
- **F4 — 12개 날짜를 git 로그에서 가져온다** → 파일이 권위다. 커밋 날짜 ≠ 결정 날짜(문서가 나중에 커밋될 수 있다). Guard: AC-2.

---

# Test Requirements

- `bash scripts/check-adr-index-drift.sh` — 현 트리 GREEN(비-vacuous), mutation 4방향 RED.
- CI 잡 `adr-index-drift` 가 이 변경에서 실행되어 GREEN.

---

# Definition of Done

- [ ] AC-1 ~ AC-7.
- [ ] `tasks/INDEX.md` done entry.

---

# Provenance

발굴 2026-07-12 — `TASK-MONO-363` 구현 중. Date 를 가드하려다 **12개 결측 + 3개 불일치**를 실측하고, 그 자리에서 붙이면 **오탐 가드**가 됨을 확인해 **의도적으로 유예**했다(스크립트와 INDEX 에 그 사실을 명시). **유예를 티켓으로 바꾸지 않으면 그 노트는 썩는다** — 이 저장소가 `MONO-458`(sweep 이 "티켓 미생성" 을 발견) 에서 이미 겪은 형태다.

**이 task 의 값어치는 12개 헤더를 채우는 데 있지 않다.** `Date` 열 53개가 지금 **아무도 지키지 않는 손-유지 데이터**이고, 이 저장소가 그런 표면에서 **여섯 번** 대가를 치렀다는 데 있다(`339` `341` `345` `352` `360` `363`).

분석=Opus 4.8 / 구현 권장=Sonnet (기계적 — 단 **AC-1 을 AC-3 보다 먼저** 하고 **AC-4 mutation 4방향**(특히 4-4 오탐 확인)을 생략하면 무의미하다).
