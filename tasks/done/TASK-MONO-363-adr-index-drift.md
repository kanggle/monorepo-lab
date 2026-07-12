# Task ID

TASK-MONO-363

# Title

`docs/adr/INDEX.md` 가 **2026-05-15 이후 죽어 있다** — ADR 파일 53개 vs 표 15행, **38개 누락**. 백필 + 재발 가드

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

- **발굴 출처**: [`TASK-MONO-364`](TASK-MONO-364-adr-framework-neutral-security-module.md) — `ADR-MONO-049` 를 등재하려다 표가 죽어 있는 걸 발견했다. **049 만 추가하지 않았다**: 38개가 틀린 표에 한 줄 더 얹으면 **표가 최신인 것처럼 보이게** 만드는 것이고, 그게 정확히 이 계열의 결함이다.
- **같은 계열 (선례 = 가드 형태의 참조 구현)**: `TASK-MONO-345`(service map — `settings.gradle` ↔ `docs/project-overview.md`) · `TASK-MONO-352`(error registry) · `TASK-MONO-360`(gateway 선언 ↔ 모듈 ↔ 노출). **`scripts/check-service-map-drift.sh` 가 거의 그대로 재사용 가능한 형태다** — 디렉터리의 파일 ↔ 표의 행, 양방향.

---

# Goal

`docs/adr/INDEX.md` 는 스스로를 *"Monorepo-level ADR Index"* 라 선언하고, **Authoring Convention 까지 못박아 둔 문서**다. 그런데 그 표는 **`ADR-MONO-012a`(2026-05-15)에서 멈춰 있다.**

## 착수 전 실측 (2026-07-12, `f89c7425f`)

| | |
|---|---|
| `docs/adr/ADR-MONO-*.md` 파일 | **53** |
| `INDEX.md` 표의 행 | **15** |
| **누락** | **38** |

누락 목록에는 `ADR-MONO-007a` 와 **`013` ~ `048` 전량**이 들어간다 — `ADR-MONO-013`(platform-console Model B) · `019`(테넌트 모델) · `022`(ecommerce↔wms 풀필먼트) · `030`(마켓플레이스 SaaS) · `043`(notification 통합) · `047`(org-node 계층) · `048`(공유 게이트웨이 라이브러리) 처럼 **이 저장소의 뼈대를 이루는 결정들이 통째로** 목록에 없다.

**왜 이게 진짜 비용인가**: 이 표는 *"어떤 결정이 이미 내려졌는가"* 를 찾을 때 읽는 유일한 진입점이다. 사람이든 에이전트든 여기서 못 찾으면 **"그런 ADR 은 없다"** 고 결론짓는다 — 그리고 없다고 믿고 **이미 내려진 결정을 다시 내린다**. `TASK-MONO-347` 이 정확히 그런 오독 위에서 몇 달을 앉아 있었고, `MONO-345`(service map) · `MONO-352`(error registry) 는 같은 형태의 손-유지 목록이 조용히 틀려 있던 사례다.

**그리고 이건 코드가 아니라 문서라서, 아무것도 실패하지 않는다.**

---

# Scope

## 구현

1. **백필** — `docs/adr/` 의 53개 ADR 을 전부 표에 넣는다. 각 행의 `Status` / `Date` 는 **각 ADR 파일의 헤더에서 읽어 온다**(추측 금지 — 파일이 권위).
   - `SUPERSEDED` / `DEFERRED` / `REJECTED` / `PROPOSED` 상태가 섞여 있다. **`PROPOSED` 를 `ACCEPTED` 로 승격시키지 말 것** — 그건 표를 고치는 게 아니라 결정을 위조하는 것이다.
   - `007a` · `003a` · `003b` · `012a` 처럼 **접미사 붙은 ADR** 이 있다. 정렬·파싱이 이걸 흘리지 않게 할 것.
2. **재발 가드** — `scripts/check-adr-index-drift.sh` + CI 잡. **양방향**:
   - `docs/adr/ADR-MONO-*.md` 파일 → `INDEX.md` 표에 행이 있는가 (**이번에 38개가 샌 방향**)
   - `INDEX.md` 표의 행 → 실제 파일이 있는가 (유령 행)
   - **`Status` 필드가 파일 헤더와 표에서 일치하는가** — 표가 `PROPOSED` 인데 파일이 `ACCEPTED` 면(또는 반대면) 그 표는 **거짓말을 하고 있는 것**이고, 이 저장소에서 그 차이는 **ACCEPT 게이트 자체**다.
3. **CI 배선** — `dorny/paths-filter` **pure-positive**(negation 금지 — MONO-074/075 quirk), **`code-changed` 와 AND 금지**(이 드리프트는 **markdown-only 로 도착한다** — ADR 파일 하나 추가가 전부다. MONO-345 의 주석 · MONO-360 이 실측한 실패 모드).

---

# Acceptance Criteria

- [ ] **AC-1 — 백필**: `INDEX.md` 표의 행 수 == `docs/adr/ADR-MONO-*.md` 파일 수. 현재 15 → **53**(+ `ADR-MONO-049` 포함 시 54).
- [ ] **AC-2 — Status 는 파일에서 읽는다**: 각 행의 `Status` 가 해당 ADR 파일 헤더의 `**Status:**` 와 일치한다. **`PROPOSED` 를 임의로 승격시키지 않는다**(특히 `ADR-MONO-049` — self-ACCEPT 금지).
- [ ] **AC-3 — 가드**: `scripts/check-adr-index-drift.sh` 가 **양방향 + Status 일치**를 검사한다. 현 트리에서 GREEN.
- [ ] **AC-4 — mutation, 전부 물어야 함**:
  1. 표에서 한 행 삭제 → **RED**(파일은 있는데 목록에 없음 — **이번에 38번 일어난 그것**)
  2. 표에 유령 행 추가 → **RED**
  3. 한 행의 `Status` 를 파일과 다르게 조작(`PROPOSED` → `ACCEPTED`) → **RED**
  4. 접미사 ADR(`012a`) 행 삭제 → **RED**(파서가 접미사를 흘리지 않는지)
  - **vacuity**: baseline exit=0.
- [ ] **AC-5 — 도달 가능성**(MONO-359/360 의 교훈): 필터가 `code-changed` 와 **AND 되지 않는다**. **이 드리프트는 `docs/adr/*.md` 파일 하나 추가로 도착한다 — markdown-only 다.** AND 하면 **존재 이유인 바로 그 변경에서 가드가 꺼지고, skip 은 초록으로 보고된다.** `TASK-MONO-360` 이 markdown-only PR 로 실측한 그 실패 모드.
- [ ] **AC-6 — 오탐 0**: `docs/adr/INDEX.md` 자신과 프로젝트-내부 ADR(`projects/*/docs/adr/`)은 대상이 아니다. **첫날부터 RED 인 가드는 꺼진다.**
- [ ] **AC-7** — `./gradlew check` 무영향. 코드 변경 0(스크립트/워크플로/문서만).

---

# Related Specs

- `docs/adr/INDEX.md` — **고칠 대상이자, 스스로 Authoring Convention 을 못박아 둔 문서**
- `platform/architecture-decision-rule.md` — INDEX 가 참조하는 규칙
- `scripts/check-service-map-drift.sh` (MONO-345) — **가드의 참조 구현**(디렉터리 ↔ 표, 양방향)
- `scripts/check-gateway-drift.sh` (MONO-360) — pure-positive 필터 · `code-changed` 비-AND · mutation 규율의 최근 선례
- `.github/workflows/ci.yml` L165-169 (MONO-345 의 `code-changed` 비-AND 주석)

# Related Contracts

없음 — 문서 + CI 가드.

---

# Edge Cases

- **접미사 ADR** (`003a` · `003b` · `007a` · `012a`) — 정렬·파싱·mutation 4 가 이걸 노린다. `ADR-MONO-012` 와 `ADR-MONO-012a` 는 **다른 결정**이다.
- **`PROPOSED` 승격 유혹** — 표를 "정리" 하면서 오래된 `PROPOSED`(예: `ADR-MONO-008` finance bootstrap, `009` chrome-devtools)를 `ACCEPTED` 로 바꾸면 **결정을 위조하는 것**이다. 파일이 권위. AC-2.
- **`ADR-MONO-049` 는 `PROPOSED` 다** — 이 task 가 표에 넣되 **`PROPOSED` 로** 넣는다. 사용자의 `ADR-MONO-049 ACCEPTED` 없이는 승격 불가.
- **프로젝트-내부 ADR** — `projects/<name>/docs/adr/` 은 이 표의 대상이 **아니다**(INDEX 서문이 명시). 가드가 이걸 끌어들이면 첫날 RED. AC-6.

# Failure Scenarios

- **`ADR-MONO-049` 한 줄만 추가하고 끝낸다** → 38개가 틀린 표를 **최신인 것처럼 보이게** 만든다. **그게 이 결함의 원형이다.** Guard: AC-1.
- **가드를 `code-changed` 와 AND** → ADR 추가는 markdown-only 이므로 **가드가 꺼진 채 초록**. Guard: AC-5.
- **Status 를 표에서만 고친다** → 표와 파일이 다른 말을 하고, 이 저장소에서 그 차이는 **ACCEPT 게이트 자체**다. Guard: AC-2/AC-4-3.

---

# Provenance

발굴 2026-07-12 — `TASK-MONO-364`(ADR-MONO-049 저술) 중 049 를 표에 등재하려다 발견. **표가 두 달간 죽어 있었고 아무것도 실패하지 않았다.**

이 저장소가 반복해서 잡아온 결함의 또 한 사례다: **사람이 손으로 유지하는 선언 ↔ 기계가 아는 진실**(`MONO-339` fed-e2e 목록 · `MONO-341` 데모 래퍼 맵 · `MONO-345` service map · `MONO-352` error registry · `MONO-360` gateway 선언). **이번엔 그 대상이 "우리가 무슨 결정을 내렸는가" 의 목록 자체다.**

분석=Opus 4.8 / 구현 권장=Sonnet (백필은 기계적 · 가드는 MONO-345/360 의 형태 재사용 — 단 **AC-4 mutation 4방향과 AC-5 도달성 검증을 생략하면 무의미하다**).
</content>
