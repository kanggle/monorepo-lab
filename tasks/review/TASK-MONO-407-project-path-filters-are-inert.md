# Task ID

TASK-MONO-407

# Title

프로젝트별 paths-filter 8개가 **아무것도 걸러내지 않는다** — 코드가 바뀌면 항상 통합 레인 **9개 전부**가 돈다 (`MONO-074/075` 가 만든 규칙이 정작 그 규칙이 지키려던 필터엔 적용되지 않았다)

# Status

review

# Owner

monorepo

# Task Tags

- ci
- infra

---

# 이건 "필터가 부정확하다" 가 아니라 **"필터가 없는 것과 같다"** 이다

`.github/workflows/ci.yml` 은 프로젝트별 필터 8개(`wms` · `ecommerce` · `iam` · `fan` · `scm` · `finance` · `erp` · `platform-console`)를 선언하고, 통합/E2E 레인을 그 출력으로 게이팅한다. **그 필터들은 전부 항상 참이다.**

## 실측 (2026-07-14)

**결정적 표본 — `PR #2516`**: `projects/iam-platform/` **만** 건드렸다(코드 19파일, **공유파일 0개**). 그런데 **Testcontainers 통합 레인 9개가 전부 실행**됐다 — wms×2 · iam · fan · scm · finance · erp · console · ecommerce.

**그리고 이건 단발이 아니다.** 최근 머지된 PR **20개**의 통합 레인 실행 수를 세면:

| 실행된 통합 레인 수 | PR 수 |
|---|---|
| **0개** (전부 SKIPPED) | 14 |
| **9개** (전부 실행) | 6 |
| 1~8개 (일부만) | **0** |

**중간이 없다.** 20/20 이 완전한 이분법이다. ⇒ **실제로 작동하는 게이트는 `code-changed` 하나뿐**이고(문서만 바꾸면 0, 코드가 한 줄이라도 바뀌면 전부), **프로젝트별 필터 8개는 그 판단에 아무 기여도 하지 않는다.**

---

# 원인 — negation 패턴 15개 (그리고 그건 **이미 금지된 구문**이다)

```yaml
wms:
  - 'projects/wms-platform/**'
  - '!projects/wms-platform/tasks/**'      # ← 이 줄이 필터를 항상-참으로 만든다
  - '!projects/wms-platform/**/*.md'       # ← 이 줄도
```

`dorny/paths-filter` 에서 필터는 **변경된 파일 중 *하나라도* 패턴 하나에 매칭하면 참**이다. 그리고 `!x` 는 **x 가 아닌 모든 것에 매칭한다.** ⇒ `scripts/docker-cleanup.sh` 든 `projects/iam-platform/Foo.java` 든, **`projects/wms-platform/tasks/` 아래가 아니므로 `!projects/wms-platform/tasks/**` 에 매칭한다 → `wms` = true.**

**즉 "wms 밖의 파일" 이 정확히 wms 필터를 켠다.** 의도의 정반대다.

`changes` 잡 자신의 출력이 이걸 말한다 (`PR #2559`, 바꾼 파일 = `scripts/docker-cleanup.sh` + `.md` 2개, **프로젝트 0개**):

```
Filter libs = true          Filter wms = true        Filter ecommerce = true
Filter iam = true           Filter fan = true        Filter scm = true
Filter finance = true       Filter erp = true        Filter code-changed = true
```

## 🔴 그리고 이 저장소는 **이미 이 규칙을 알고 있었다**

`TASK-MONO-074/075` 가 바로 이 quirk 를 겪고 **"negation 금지 → pure-positive 필터 + outputs 레이어에서 AND 합성"** 을 규칙으로 세웠다. 그 규칙은 **절반만 적용됐다**:

- ✅ `code-changed` 는 pure-positive 로 만들어졌다.
- ❌ **정작 그 AND 가 보호하려던 8개 프로젝트 필터에는 negation 15개가 그대로 남았다.**

AND 합성(`libs && code-changed`)은 **문서-only PR** 은 정확히 막는다 — 그래서 위 표의 "0개" 열이 존재하고, **필터가 작동하는 것처럼 보였다.** 하지만 AND 는 *"iam 변경인데 왜 wms 레인이 도나"* 는 못 막는다. **가려진 절반이 15개월치 CI 시간이다.**

> 이 저장소가 반복해 배우는 것의 또 다른 얼굴 — **규칙을 쓰는 것과 규칙을 적용하는 것은 다르고, 부분 적용은 전체 적용처럼 보인다**(`MONO-392` 의 사본, `MONO-406` 의 Forbidden 열과 같은 축).

---

# 청구서 (정직하게 — 과장하지 않는다)

최근 20개 중 9레인을 돌린 6개를 **실제 변경 범위로 분류**하면:

| PR | 변경 범위 | 9레인이 정당한가 |
|---|---|---|
| #2542 · #2545 · #2547 · #2551 | 공유 경로(`libs`/`platform`/`rules`/`.claude`/`.github`) | ✅ **정당** |
| #2555 | 5개 프로젝트 + 공유 7파일 | ✅ **정당** |
| **#2516** | `iam-platform` 만 (공유 0) | ❌ **낭비** |
| **#2559** | `scripts/*.sh` + `.md` (프로젝트 0) | ❌ **낭비** |

**⚠️ 이 표본에서 낭비는 2/20 이다. 그걸 "매 PR 이 낭비" 로 부풀리지 말 것.** 최근 창(window)이 **모노레포-레벨 작업에 치우쳐** 있어서 프로젝트-스코프 PR 이 드물었을 뿐이다. **낭비는 프로젝트-스코프 PR(`TASK-BE-xxx` · `TASK-PC-FE-xxx`) 수에 비례**하고 그게 이 저장소의 평시 모드다. **착수 시 더 넓은 창에서 빈도를 재측정할 것**(그 숫자가 이 티켓의 우선순위를 정한다).

레인 1회 비용: `PR #2559` 기준 **9 Testcontainers 레인 + E2E 4개 ≈ 21분**(10:42 → 11:03). `ecommerce`·`iam` 레인은 `MONO-403` 이후 `--no-parallel` 로 직렬화돼 있어 가장 느리다.

---

# Scope

## In Scope

- **`.github/workflows/ci.yml` — 프로젝트 필터 8개의 negation 패턴 15개 제거.** pure-positive 만 남긴다:
  ```yaml
  wms:
    - 'projects/wms-platform/**'
  ```
  **negation 이 표현하려던 것은 AND 합성이 이미 해 준다** — `projects/wms-platform/tasks/x.md` 변경 → `wms`=true 이지만 `code-changed`=false → 출력 `wms && code-changed` = **false**. 동작 동일, quirk 없음.
- **`libs` 필터의 `- '!**/*.md'` 도 같은 이유로 제거** (이것 때문에 `libs` 가 **md 아닌 모든 파일**에 켜진다 — `scripts/*.sh` 가 `libs` 를 켠 이유가 정확히 이것이다).
- 제거 후 **negation 패턴 0개** 를 grep 으로 확증.

## Out of Scope

- **게이팅 `if:` OR-블록 hoist** — `TASK-MONO-328`(DEFERRED)의 일이다. **⚠️ 다만 328 은 *"필터 정의 무수정(MONO-074/075 quirk)"* 이라고 적어 뒀다 — 그 문장은 quirk 가 *봉인돼 있다*는 전제였고, 이 티켓이 그 전제를 반증한다.** 328 을 열 때 이 티켓을 참조할 것. (328 을 여기서 실행하지는 않는다.)
- **`code-changed` 목록 수정** — `**/*.sh`·`**/*.ps1`·`**/*.yml` 은 `MONO-366`/`374`/`405` 가 **가드 도달성을 위해 일부러 넣었다**. 빼면 그 가드들이 자기가 감시하는 파일에 도달하지 못한다(= `MONO-360` 이 측정한 실패 모드). **건드리지 말 것.**
- 새 잡 추가, 러너 변경, 레인 병합.

---

# Acceptance Criteria

- [x] **AC-1 — negation 0개.** `.github/workflows/ci.yml` 의 `filters:` 블록에 `- '!` 로 시작하는 줄이 **0개**. (현재 15개.)
  **✅ 15 → 0.** grep 이 아니라 **파싱된 YAML** 로 확인했다(`js-yaml` 로 `ci.yml` 을 로드 → `filters` 문자열을 다시 파싱 → 필터별 패턴 열거). 프로젝트 필터 7개가 전부 **패턴 1개짜리 pure-positive** 가 됐고, 이는 `platform-console`(패턴 4개, negation 0)과 같은 모양이다. **텍스트 grep 만 믿지 않은 이유**: 이 티켓의 전제 자체가 *"패턴이 실제로 어떻게 해석되는가"* 이므로, 검증도 텍스트가 아니라 **해석된 구조**에 물어야 한다.
- [ ] **AC-2 — 🔴 양성: 자기 레인은 여전히 돈다.** 한 프로젝트의 **코드 파일만** 바꾼 실제 PR 에서 **그 프로젝트의 통합 레인이 실행(SUCCESS)** 된다. **이게 이 티켓의 가장 위험한 지점이다** — 과도-교정하면 레인이 skip 되고 **skip 은 초록으로 보고된다**(`MONO-360`·`MONO-405`). **"돌았다" 를 `conclusion=SUCCESS` 로 확인하라. SKIPPED 를 통과로 읽지 말 것.**
- [ ] **AC-3 — 음성: 남의 레인은 안 돈다.** 같은 PR 에서 **다른 프로젝트들의 통합 레인은 SKIPPED**. (현재는 9개 전부 SUCCESS 다 — 이 대조가 결함이 실제로 고쳐졌음을 보이는 유일한 증거다.)
- [ ] **AC-4 — 공유 변경은 여전히 전부 깨운다.** `libs/**` 또는 `platform/**` 을 건드리는 PR 에서 **통합 레인 9개 전부 SUCCESS**. (필터를 조여서 **공유 라이브러리 회귀를 놓치면** 이 티켓은 순손실이다.)
- [ ] **AC-5 — 문서-only 는 여전히 0개.** `.md`/`tasks/` 만 바꾼 PR → 통합 레인 **9개 전부 SKIPPED**(현행 동작 보존).
- [ ] **AC-6 — 실제 PR 에서 확인.** AC-2~5 는 **로컬 추론이 아니라 실행된 PR 의 `statusCheckRollup`** 으로 확인한다. *"필터를 고쳤다" ≠ "필터가 그렇게 동작한다"* — `dorny/paths-filter` 의 매칭 의미론이 이 티켓의 전제 전부이고, **그 의미론을 문서가 아니라 실행에서 확인한 것이 이 티켓의 근거**다.

---

# Edge Cases

- **`platform-console` 필터는 오늘 `false` 였다** (`PR #2559` 의 `changes` 출력). 다른 7개가 전부 true 인데 이것만 false 라면 **패턴 형태가 다르다는 뜻**이다 — 착수 시 그 차이를 먼저 읽어라. **그 필터가 "고장 나지 않은 표본"일 수 있고, 그렇다면 고칠 모양이 이미 저장소 안에 있다.**
  **✅ 구현 확인 (2026-07-14): 정확히 그랬다.** `platform-console` 에는 negation 이 **하나도 없고**, 그 위에 이렇게 적혀 있다: *"`TASK-PC-BE-001`: **pure-positive** filter … **No negation patterns (MONO-074/075 quirk rule)**"*. ⇒ **규칙은 알려져 있었고, 딱 한 필터에만 적용됐다.** 나머지 7개는 아무도 손대지 않았다. **고칠 모양을 발명할 필요가 없었다 — 저장소 안에 이미 있었다.**
- **`!` 를 지우면 프로젝트의 `.md`/`tasks/` 변경이 필터를 켠다** — 그러나 출력단 AND 가 막는다. **단 AND 되지 않는 raw 출력(`service-type` · `ci-baseline` · `hooks` · `jwt-claims`)에 프로젝트 필터를 새로 물리지 말 것** — 그 넷은 **일부러** AND 를 안 한다(각 주석 참조: markdown-only 편집이 그 가드가 감시하는 바로 그 편집이다).
- **`workflows` 필터(`.github/workflows/**`)는 negation 이 없다** — 이미 pure-positive 다. 건드릴 것 없음.
- **CI 자기 자신을 바꾸는 PR** 은 `workflows` 필터로 전 레인을 깨운다 ⇒ **이 티켓의 PR 자체는 9레인을 돌린다**(정당하다 — 게이팅을 바꿨으니). AC-2/AC-3 은 **그 PR 이 아니라 그 뒤의 프로젝트-스코프 PR** 에서 확인해야 한다. **머지 직후 첫 프로젝트 PR 을 관찰하라.**

# Failure Scenarios

- **F1 — 과도 교정 → 레인이 조용히 꺼진다.** 필터를 잘못 좁혀 `iam` 변경에 `iam` 레인이 skip 되면, **CI 는 초록이고 아무도 모른다.** 이 저장소가 이미 세 번(`MONO-360` · `387` · `405`) 만난 문장 — ***관측되지 않는 성질은 아무도 지키고 있지 않은 성질.*** AC-2 가 **SUCCESS 를 요구**하고 SKIPPED 를 거부하는 이유.
- **F2 — 공유 변경이 좁아진다.** `libs`/`platform` 변경이 일부 레인만 깨우게 되면 **shared-library 회귀가 통과한다**(`MONO-406` 이 통합 레인 9개 전부에서만 보였던 결함을 상기하라 — 컴파일러도 유닛테스트도 못 보는 결함이 있다). AC-4.
- **F3 — negation 을 "고쳐서" 다시 넣음** (`!` 대신 다른 표현으로 같은 의도를 재도입). **의도 자체가 불필요하다** — AND 가 이미 그 일을 한다. 새 문법을 발명하지 말 것.
- **F4 — 이 티켓을 "CI 비용 절감" 으로만 읽음.** 비용은 **증상**이다. 병은 **선언된 게이팅이 실제 게이팅과 다르다**는 것 — `ci.yml` 은 프로젝트별로 레인을 고른다고 *말하지만* 실제로는 고르지 않는다. **그 갭이 다음 사람에게 거짓 모델을 준다**(*"내 PR 은 iam 레인만 도니까 wms 는 안 봐도 된다"* — 지금은 반대로 다 돌고, 고친 뒤엔 정말로 안 돈다).

# Test Requirements

- AC-1: `grep -c "^\s*- '!" .github/workflows/ci.yml` → **0**.
- AC-2/AC-3: 머지 후 첫 **프로젝트-스코프 PR** 의 `gh pr view <n> --json statusCheckRollup` 에서 자기 레인 `SUCCESS` + 남의 레인 `SKIPPED`.
- AC-4: `libs/` 를 건드리는 PR(또는 의도적 no-op 커밋)에서 9레인 `SUCCESS`.
- AC-5: 문서-only PR 에서 9레인 `SKIPPED`.
- **레퍼런스 (수정 전, 2026-07-14)**: 최근 20 PR = 0레인 14건 / 9레인 6건 / **일부만 = 0건**. 수정 후 이 분포가 깨져야 한다 — **"일부만" 이 나타나는 것이 성공의 지문**이다.

# Definition of Done

- [ ] AC-1~6
- [ ] `ci.yml` 필터 블록에 **왜 negation 을 쓰면 안 되는지** 주석 1줄(`MONO-074/075` + 이 티켓 참조) — 다음 사람이 "가독성" 을 이유로 되돌리지 않도록
- [ ] `tasks/INDEX.md` done entry

---

# Dependency Markers

- **선행 (done)**: `TASK-MONO-074/075`(negation quirk 를 최초 발견하고 pure-positive + AND 규칙을 세웠다 — **이 티켓은 그 규칙을 남은 절반에 적용하는 것**) · `TASK-MONO-366`(`code-changed` 에 `.sh`/`.ps1` 추가 — **건드리지 말 것**) · `TASK-MONO-405`(`.claude/**` 를 `libs` 에서 빼 같은 클래스의 낭비를 제거 — **같은 병의 다른 표면**).
- **연관 (deferred)**: `TASK-MONO-328` — 게이팅 `if:` hoist. **그 티켓의 *"필터 정의 무수정"* 전제를 이 티켓이 반증한다.**

# Related Specs

- `.github/workflows/ci.yml` § `changes` 잡 (필터 정의 + outputs AND 합성)

# Related Contracts

- 없음 (CI 게이팅 — API/이벤트 계약 무관)

---

# Provenance

발굴 2026-07-14, `TASK-MONO-391`(주간 도커 청소) 구현 중. **셸 스크립트 한 파일**을 바꿨는데 Testcontainers 9레인 + E2E 가 20분 돌기에 *"왜?"* 를 물었다.

**첫 진단은 틀렸다.** 나는 `scripts/**` 가 `libs` 필터에 통째로 들어 있을 거라 추측했다(`MONO-405` 가 `.claude/**` 에서 고친 모양). **필터 정의를 읽어보니 `scripts/**` 는 어디에도 없었다.** 그래서 `changes` 잡의 **실제 출력**을 읽었고, 거기서 **프로젝트 7개가 전부 `true`** 인 것을 봤다 — 프로젝트를 **하나도** 안 건드린 PR 에서.

**그리고 내 PR 하나로 결론 내지 않았다.** `#2538`(iam 전용처럼 보였다)은 `settings.gradle` 을 건드려 **증거가 못 됐고**(그건 `libs` 의 정당한 경로다), `#2516`(공유파일 0, 단일 프로젝트)이 **깨끗한 표본**이었다. 그 다음 **반증을 찾으러** 최근 20 PR 의 레인 실행 수를 셌다 — *일부만 도는 PR 이 하나라도 있으면 내 모델이 틀린 것*이었다. **0건이었다.** 완전한 이분법이 모델을 확정했다.

**덤 — stale 한 주석 2개**: `MONO-374`(`ci-baseline`)와 `MONO-405`(`hooks`)의 주석이 *"a workflow file and a bash guard / a PowerShell hook is not 'code' by that filter's definition"* 이라 적었는데, **`MONO-366` 이 `**/*.sh`·`**/*.ps1`·`**/*.yml` 을 `code-changed` 에 넣은 뒤로 거짓**이다. 두 주석이 정당화하는 결정(raw 필터, AND 안 함)은 **여전히 안전**하므로(더 보수적이다) 라이브 결함은 없다 — 하지만 **문장은 틀렸고, 이 저장소는 틀린 문장에 값을 치러 왔다.** 이 티켓에서 함께 고칠 것.

분석=Opus 4.8 / 구현 권장=**Sonnet** (판단은 이 티켓에서 끝났다 — 남은 건 15줄 삭제와 **실제 PR 에서의 양방향 확인**이다. 다만 AC-2 의 *"SKIPPED 를 통과로 읽지 말 것"* 은 기계적이지 않으니 주의 깊게).
