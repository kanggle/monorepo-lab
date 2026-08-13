# Task ID

TASK-MONO-525

# Title

프로젝트 내부 ADR 은 **어떤 색인 가드도 보지 않는다** — iam 은 ADR 6개에 색인이 아예 없고, Status 헤더는 저장소 안에서 **세 가지 형식**으로 갈려 있다

# Status

done

# Owner

monorepo

# Task Tags

- docs
- ci
- guard

---

# 배경 — `ADR-ERP-001` ACCEPT(#3287) 검증 중에 밟았다

프로젝트 ADR 하나(`projects/erp-platform/docs/adr/ADR-001`)의 Status 를
`Proposed → Accepted — D` 로 넘기는 PR 을 냈는데, CI 에서 `check-adr-index-drift` 잡이
**skip** 됐다. 처음엔 경로 필터 누락으로 의심했다(필터가 `docs/adr/**` 뿐이다).

🔴 **물려 보고 나서야 필터 문제가 아니라는 게 드러났다.** 그 PR 의 `README.md` 행을
`Proposed` 로 되돌린 채 가드를 돌렸더니 **여전히 `rc=0`** 이었다. 즉 필터를 고쳐도
그 가드는 프로젝트 ADR 을 **못 본다.** 스크립트가 그렇게 적어 두고 있다:

> `scripts/check-adr-index-drift.sh` L70 — *"Project-internal ADRs under
> `projects/<name>/docs/adr/`. The index's own preamble scopes itself to
> monorepo-level ADRs; pulling them in would be red on day one."*

⇒ **CI 의 skip 은 정상 동작이다.** 결함은 필터가 아니라 **그 모집단을 보는 가드가 하나도
없다**는 것이다.

## 실측 (2026-08-12)

### ① 프로젝트 ADR 의 Status 는 지금 안 틀렸다 — 하지만 아무도 안 본다

프로젝트 ADR **23개** 전수를 형제 `README.md` 행과 대조:

```
비교 가능 17건 → 17건 전부 일치 (DRIFT 0)
비교 불가  6건 → 전부 iam-platform (아래 ②)
```

🔵 **0건이 "가드가 필요 없다"는 뜻은 아니다.** 지금 안 틀렸을 뿐이고, 틀려도 아무것도
울리지 않는다. 루트 ADR 은 같은 종류의 드리프트를 잡는 가드를 갖고 있다(`MONO-363`).

### ② `iam-platform` 은 ADR 6개에 **색인이 없다**

```
ecommerce-microservices-platform  ADR 8개  README 있음
erp-platform                      ADR 1개  README 있음
fan-platform                      ADR 4개  README 있음
finance-platform                  ADR 3개  README 있음
scm-platform                      ADR 1개  README 있음
iam-platform                      ADR 6개  README 🔴 없음
```

ADR 수로는 두 번째로 많은 프로젝트인데 진입점이 없다. 루트 `docs/adr/INDEX.md` 는
자기 서문에서 monorepo-level 로 범위를 한정하므로 이 6개는 **어느 색인에도 없다.**

### ③ 🔴 Status 헤더가 **세 가지 형식**이고, 그것이 ①의 "비교 불가" 진짜 원인이다

```
루트 docs/adr/     :  **Status:**    64건 / 64건      ← 완전 균일
프로젝트 docs/adr/ :  - **Status**:  11건
                      **Status**:    11건
                      **Status:**     1건 (iam/ADR-001)
```

루트 가드는 `**Status:** <VALUE>` 를 찾는다(L108 의 `NO-STATUS` 메시지). 그 술어로
프로젝트 ADR 을 재면 **23건 중 22건이 "Status 없음"** 으로 잡힌다 ⇒ 스크립트가 말한
*"red on day one"* 은 **맞는 말이었다.** 다만 이유는 Status 값이 틀려서가 아니라
**표기가 갈려서**다. 이 구분이 이 티켓의 전부다: 값은 멀쩡하고 **형식만 정리하면**
제외 사유가 사라진다.

🔴 **정정 — #3287 의 PR 본문이 틀렸다.** 거기에 *"`iam/ADR-001-oidc-adoption.md` 는
Status 헤더가 없다"* 라고 적었는데 **있다.** `**Status:** ACCEPTED` 로, 루트와 같은
형식이다. 내 스캐너의 정규식이 콜론이 볼드 **안**에 있는 형태를 못 읽었을 뿐이다.
🔵 부재를 보고할 때 **계측기가 못 읽은 것과 실제로 없는 것**을 가르지 않은 실수이고,
이 티켓의 AC-0 이 같은 실수를 반복하지 않도록 술어를 먼저 검증하게 한다.

---

# Goal

프로젝트 내부 ADR 이 **색인을 갖고**, 그 색인과 ADR 본문의 Status 가 **갈라지면 RED** 가
되게 한다. 루트 ADR 이 이미 갖고 있는 보증을 같은 성질로 프로젝트에도 준다.

# Scope

## In Scope

- `projects/iam-platform/docs/adr/README.md` 신설(ADR 6개)
- 프로젝트 ADR 의 Status 헤더 표기 통일
- `scripts/check-adr-index-drift.sh` 의 프로젝트 ADR 제외를 **해제**하거나, 별도 가드 추가
- `.github/workflows/ci.yml` 의 경로 필터에 `projects/*/docs/adr/**` 추가

## Out of Scope

- ADR **내용**. 이 티켓은 색인·표기·가드만 건드린다. 어떤 ADR 의 Status 값도 바꾸지 않는다
- 루트 `docs/adr/INDEX.md` 의 범위 선언. monorepo-level 한정은 그대로 두고, 프로젝트는
  **자기 README** 를 색인으로 쓴다(현재 5개 프로젝트가 이미 그 형태다)
- `platform-console` 등 `docs/adr/` 자체가 없는 프로젝트에 ADR 디렉터리를 만드는 일

---

# Acceptance Criteria

- [x] **AC-0 (술어부터 검증 — 착수 첫 작업)** — 위 ①②③ 숫자를 **다시 잰다.** 🔴 그리고
      재기 전에 **계측기를 먼저 물려라**: Status 를 읽는 정규식으로 **알려진 3형식 전부**를
      뽑아내는지 확인한다(각 형식의 실제 파일 1건씩). 이 티켓의 배경 자체가 정규식이 한
      형식을 못 읽어 "헤더가 없다" 는 **거짓 부재**를 보고한 사건이다.
      🔴 "23개" 도 물려받지 말 것 — 그 사이 ADR 이 늘 수 있다.
- [x] **AC-1 (표기 통일)** — 프로젝트 ADR 의 Status 헤더를 **한 형식**으로 맞춘다. 루트가
      64/64 로 `**Status:**` 를 쓰므로 그쪽이 기본 후보지만, 고른 근거를 적는다.
      🔴 **Status 값은 건드리지 않는다** — 표기만 바꾼다. diff 에 값 변경이 한 줄도 없음을
      보인다(`git diff` 에서 값 부분이 unchanged).
- [x] **AC-2 (iam 색인 신설)** — `projects/iam-platform/docs/adr/README.md` 를 만든다.
      형식은 형제 5개 프로젝트의 README 와 같게(제목 표 + 상태 열). 🔴 6개 ADR 의 Status 는
      **파일에서 읽어서** 채운다 — 기억이나 추측으로 쓰지 말 것
- [x] **AC-3 (가드)** — 프로젝트 ADR 의 Status 가 형제 README 행과 갈라지면 **RED**.
      기존 `check-adr-index-drift.sh` 의 제외를 푸는 쪽과 별도 스크립트 쪽 중 하나를 고르고
      근거를 적는다. 🔵 제외를 푸는 쪽이면 그 L70 주석도 함께 갱신한다 — 안 그러면 다음
      사람이 "제외돼 있다" 는 주석을 믿는다
- [x] **AC-4 (가드가 무는지)** — 프로젝트 ADR 하나의 README 행 상태를 되돌려 **RED**,
      복구해서 **GREEN**. 🔴 이 티켓은 *"돌렸더니 초록이었는데 알고 보니 그 파일을 본 적이
      없었다"* 에서 시작했다. 초록을 근거로 쓰기 전에 **그 초록이 무엇을 봤는지** 보여라
- [x] **AC-5 (도는 레인)** — `.github/workflows/ci.yml` 의 경로 필터에
      `projects/*/docs/adr/**` 를 추가하고, **프로젝트 ADR 만 건드리는 PR 에서 그 잡이
      실제로 실행되는지**(skip 아님) 확인한다. 🔴 술어가 맞아도 레인이 안 깨어나면
      영원히 초록이다(`MONO-518`·`MONO-524` 에서 두 번)
- [x] **AC-6 (0건은 통과가 아니다)** — 가드가 프로젝트 ADR 을 **한 건도 못 찾으면 RED**
      가 되게 한다. 글롭이 어긋나거나 디렉터리가 옮겨졌을 때 조용히 초록이 되는 것과
      "전부 정상" 은 구별돼야 한다
- [x] **AC-7 (안 하는 것도 산출물)** — 루트 `docs/adr/INDEX.md` 의 monorepo-level 범위
      선언을 **왜 안 바꾸는지** 적는다(프로젝트는 자기 README 를 색인으로 쓰고, 5개
      프로젝트가 이미 그 형태다). 안 적으면 다음 사람이 두 색인을 합치려 든다

# Related Specs

- `platform/architecture-decision-rule.md` (ADR 라이프사이클 · ACCEPTED 게이트)
- `docs/adr/INDEX.md` § Authoring Convention (루트 표기 규약 — 통일의 기준 후보)

# Related Contracts

- 없음 (문서/CI 층)

# Edge Cases

- **`**Status:**` vs `**Status**:` vs `- **Status**:`** — 셋 다 사람 눈엔 같아 보이고
  정규식엔 다르다. 통일하지 않으면 가드가 **거짓 부재**를 낸다(이 티켓의 발생 경로).
- **README 행의 상태 열 위치** — 프로젝트마다 표 열 수가 다를 수 있다. 열 인덱스를
  하드코딩하면 한 프로젝트에서만 조용히 틀린다. 헤더 행에서 열을 찾아라.
- **`Accepted — D` 처럼 선택지가 붙은 상태** — 값 비교를 완전 문자열 등호로 하면
  `**Accepted — D**`(README, 볼드) vs `Accepted — **D**`(ADR, 부분 볼드)가 갈린다.
  정규화 규칙을 정하고 적어라.
- **ADR 이 0개인 프로젝트** — `docs/adr/` 자체가 없는 프로젝트는 검사 대상이 아니다.
  "디렉터리는 있는데 README 가 없다" 와 "디렉터리가 없다" 를 구별하라.

# Failure Scenarios

- **표기 통일 없이 제외만 푼다** → 23건 중 22건이 `NO-STATUS` 로 RED. 스크립트가 예고한
  *"red on day one"* 이 그대로 재현된다. AC-1 이 AC-3 보다 먼저다.
- **가드를 만들고 필터를 안 건다** → 프로젝트 ADR 만 바꾸는 PR 에서 skip. 이 티켓을
  낳은 상황과 **같은 자리**로 돌아간다. AC-5.
- **AC-0 을 건너뛴다** → 계측기가 한 형식을 못 읽는 채로 "드리프트 0" 을 보고하고,
  그 숫자가 다음 결정의 근거가 된다. 이 티켓의 배경이 그 실수의 기록이다.

# Definition of Done

- [x] AC-0 ~ AC-7 충족
- [x] `iam-platform` ADR 6개가 색인에 있고, 그 Status 가 파일과 일치
- [x] 프로젝트 ADR 만 바꾸는 PR 에서 가드 잡이 **실행**되고 통과

---

# 구현 기록 (2026-08-13)

## AC-0 — 계측기를 먼저 물리고, 숫자는 **전부 재확인됐다**

이번엔 티켓의 숫자가 셋 다 맞았다. 그것도 결과다 — 재계수는 반증만이 아니라 **확인**도 한다.

| 티켓이 적은 것 | 재측정 |
|---|---|
| 프로젝트 ADR 23개 | **23개** (ecommerce 8 · iam 6 · fan 4 · finance 3 · erp 1 · scm 1) |
| 표기 `- **Status**:` 11 / `**Status**:` 11 / `**Status:**` 1 | **11 / 11 / 1** — 완전 일치 |
| `iam-platform` 만 README 없음 | 그대로 (형제 5개는 전부 있음) |
| 비교 가능 건은 드리프트 0 | 새 가드로 전수 재검 → **드리프트 0** |

계측기 검증은 정규식을 손으로 확인하는 대신 **가드 안에 박았다**: 파서는 세 형식을 전부
읽고(`STATUS_ANY`), 정규형 강제는 **별도 항목**(`STATUS_CANON`)으로 분리했다. 자기검증의
`NOTATION` 케이스가 옛 표기를 되살려 넣어 *"읽기는 되는데 표기 위반으로 잡히는가"* 를
실측한다 — 즉 **거짓 부재가 불가능한 구조**다. 이 티켓의 배경(#3287 이 "Status 헤더가
없다" 고 적었으나 있었던 사건)이 반복될 수 없다.

곁가지 실측 2건(티켓에 없던 것): 프로젝트 ADR 23개는 **전부 Status 헤더가 정확히 1건**이고
**전부 Date 헤더를 갖는다**. 앞은 "본문 다른 곳의 Status 를 잘못 읽을 위험 0" 을, 뒤는
"Date 축을 나중에 붙일 수 있다" 를 뜻한다(이번 범위에서는 안 붙였다 — README 에 Date 열이
없다).

## AC-1 — 표기 통일: **콜론 위치만**, 불릿은 보존

정규형은 `**Status:**` (루트 64/64 가 쓰는 형태). 다만 세 "형식" 은 사실 **헤더 블록 전체의
스타일 차이**였다:

```
- **Status**: X      ecommerce · finance   (불릿 블록)
**Status**: X        erp · fan · iam · scm (평문 블록)
**Status:** X        iam/ADR-001 만
```

그래서 바꾼 것은 **키 토큰의 콜론 위치뿐**이고 불릿은 그대로 뒀다. 불릿까지 없애면 같은
블록의 `- **Date**:` · `- **Tags**:` 와 어긋나 파일이 깨져 보이고, 그 줄들은 이 티켓이
요구하지 않은 곳이다. 🔵 **남은 비일관은 알고 남긴 것**: 불릿 블록 파일에서 `**Status:**`
와 `**Date**:` 의 콜론 위치가 다르다. 헤더 키 전체를 통일하는 것은 별건이고, 조용히
넓히는 대신 여기에 적는다.

값 불변은 주장이 아니라 **검사**로 했다 — 변환 스크립트가 변환 전후의 값 문자열을 비교해
하나라도 다르면 종료하고, 커밋 전 `git diff` 로도 확인했다:

```
파일당 +/-  :  22 파일 전부 1/1
값 짝맞춤   :  삭제줄/추가줄의 값 부분이 전부 짝을 이룸 ⇒ 값 변경 0
통일 후     :  **Status:** 12 · - **Status:** 11  (콜론 위치 100% 통일)
```

## AC-2 — iam 색인 신설

`projects/iam-platform/docs/adr/README.md` — 형제 5개와 같은 3열 표(`| # | 제목 | 상태 |`)
+ 같은 "ADR 작성 원칙" 절. **6개 ADR 의 상태는 전부 파일에서 읽어 채웠다**(추측 0):
001/002/005/006 `ACCEPTED`, 003 `ACCEPTED — 옵션 B closure`, 004 `ACCEPTED — Phase 2 옵션 1`.

## AC-3 — **별도 스크립트**를 골랐다

`scripts/check-project-adr-index-drift.sh` (신규). 기존 `check-adr-index-drift.sh` 의 제외를
푸는 쪽을 고르지 않은 이유:

1. **id 가 전역 유일이 아니다.** 루트는 `ADR-MONO-<n>` 한 이름공간이지만 프로젝트에는
   `ADR-001` 이 **다섯 개** 있다(ecommerce · erp · fan · finance · scm). 루트 스크립트의
   id-keyed 맵에 넣으면 서로 덮어쓴다 — 조용히.
2. **색인의 위치·모양이 다르다.** 루트는 단일 `INDEX.md`(4열, Date 포함), 프로젝트는
   **디렉터리마다 README.md**(3열, Date 열 없음).
3. 루트 스크립트는 Date 축을 함께 본다. 프로젝트엔 그 열이 없어 조건 분기가 전체에 퍼진다.

⇒ 두 모집단은 "같은 규칙의 두 사례" 가 아니라 **다른 자료 구조**다.

🔵 그리고 루트 스크립트의 그 주석을 갱신했다 — 다만 **그 주석이 함의하던 것도 정정했다**:
*"pulling them in would be red on day one"* 은 맞았지만, 이유는 Status **값**이 틀려서가
아니라 **표기가 갈려서**였다(값 드리프트는 0이었다). 고칠 것이 결정 재검토가 아니라
표기였다는 구분이 이 티켓의 전부이므로, 주석에도 그 구분을 적었다.

**정규화 규칙**(Edge Case 답): `**`·`~~`·백틱 제거 → 첫 알파벳 토큰 대문자 → **em-dash 뒤에
홀로 선 대문자 한 글자**가 있으면 옵션으로 붙임(`Accepted — D` → `ACCEPTED-D`).
🔴 하이픈까지 보면 `ADR-MONO-031` 의 `-M` 을 옵션으로 읽어 ecommerce ADR-003 이 오탐이 된다.
🔴 뒤에 글자가 더 오면 옵션이 아니다(`— Phase 2 …` 의 `P`). 옵션 축이 없으면 README 가
`— A` 인데 본문이 `— D` 여도 통과한다 — 이 저장소에서 옵션 글자는 결정의 일부다.

## AC-4 / AC-6 — 무는지, 그리고 그 초록이 무엇을 봤는지

실물 트리에서(픽스처 아님) erp README 의 `**Accepted — D**` → `Proposed`:

```
✗ STATUS  projects/erp-platform/docs/adr/ADR-001 —
          README 는 'Proposed'(→ PROPOSED), 파일은 'ACCEPTED-D' 입니다.
          **파일이 권위입니다** — 행을 고치십시오, ADR 을 고치지 마십시오.
rc=1  →  복구 후 rc=0 (프로젝트 6개 · ADR 23건)
```

자기검증 **8/8**, 픽스처는 **실제 ADR 트리를 복사·변형**해서 만든다:
원본 통과 / 상태 되돌림(STATUS) / **색인 없음(NO-INDEX — iam 이 걸린 결함)** / 행 누락(MISSING)
/ 유령 행(PHANTOM) / 옛 표기(NOTATION) / **옵션 글자 `— D` vs `— A`** / **ADR 0건 = 실패**(AC-6).

fail-closed 두 곳을 더 넣었다: 표에서 `상태` 열 헤더를 못 찾으면 실패(열 인덱스 하드코딩
금지 — Edge Case), 헤더가 선언한 열 수와 다른 행이 있으면 실패(셀 안 이스케이프 안 된 `|`).
둘 다 "추측하면 한 프로젝트에서만 조용히 틀린다" 를 막는다.

## AC-5 — 레인, 그리고 **이 PR 이 증명하지 못하는 것**

`ci.yml` 에 순수 positive 필터 `project-adr-index`(`projects/*/docs/adr/**` + 가드 스크립트)
+ outputs + 잡 3스텝(`bash -n` → `--self-test` → 본검사). `code-changed` 와 AND 하지 않았다 —
ADR 도 색인도 마크다운이라 이 가드의 결함 클래스 **100%** 에서 `code-changed` 가 false 다.

🔴 **정직하게 적을 것**: 이 PR 에서 잡이 도는 것은 **ADR 글롭이 맞다는 증거가 아니다.**
필터에 `scripts/check-project-adr-index-drift.sh` 도 들어 있어, ADR 글롭이 틀려도 이 PR 은
잡을 깨운다. 지금 있는 근거는 두 가지다: ① 같은 워크플로가 `projects/*/specs/contracts/**`,
`projects/*/tasks/**`, `projects/*/apps/*/src/main/resources/db/**` 등 **같은 형태의 글롭을
이미 쓰고 실제로 발화**한다 ② `dorny/paths-filter` 의 `*` 는 한 세그먼트, `**` 는 나머지
전부다. 완전한 증거는 **프로젝트 ADR 만 건드리는 다음 PR** 이 준다 — 그때 한 번 확인할 것.

## AC-7 — 루트 INDEX 범위를 **안 바꾸는** 이유 (안 하는 것도 산출물)

`docs/adr/INDEX.md` 서문에 적었다. 요지: 합치면 **두 색인을 검사 가능하게 만드는 성질이
깨진다** — 프로젝트 ADR id 가 전역 유일이 아니라(`ADR-001` ×5) 하나의 id-keyed 표는 파일
어디에도 없는 접두사를 지어내야 한다. 두 모집단은 같은 수준으로 **따로** 지켜진다.

## 남긴 것

- 헤더 키 전체(`Date`/`Tags`/…)의 콜론 위치 통일 — 이 티켓 범위 밖, 위 AC-1 § 참조.
- 프로젝트 README 에 Date 열 추가 + Date 축 검사 — 데이터는 이미 있다(23/23 Date 보유).
