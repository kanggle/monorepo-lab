# Task ID

TASK-MONO-525

# Title

프로젝트 내부 ADR 은 **어떤 색인 가드도 보지 않는다** — iam 은 ADR 6개에 색인이 아예 없고, Status 헤더는 저장소 안에서 **세 가지 형식**으로 갈려 있다

# Status

ready

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

- [ ] **AC-0 (술어부터 검증 — 착수 첫 작업)** — 위 ①②③ 숫자를 **다시 잰다.** 🔴 그리고
      재기 전에 **계측기를 먼저 물려라**: Status 를 읽는 정규식으로 **알려진 3형식 전부**를
      뽑아내는지 확인한다(각 형식의 실제 파일 1건씩). 이 티켓의 배경 자체가 정규식이 한
      형식을 못 읽어 "헤더가 없다" 는 **거짓 부재**를 보고한 사건이다.
      🔴 "23개" 도 물려받지 말 것 — 그 사이 ADR 이 늘 수 있다.
- [ ] **AC-1 (표기 통일)** — 프로젝트 ADR 의 Status 헤더를 **한 형식**으로 맞춘다. 루트가
      64/64 로 `**Status:**` 를 쓰므로 그쪽이 기본 후보지만, 고른 근거를 적는다.
      🔴 **Status 값은 건드리지 않는다** — 표기만 바꾼다. diff 에 값 변경이 한 줄도 없음을
      보인다(`git diff` 에서 값 부분이 unchanged).
- [ ] **AC-2 (iam 색인 신설)** — `projects/iam-platform/docs/adr/README.md` 를 만든다.
      형식은 형제 5개 프로젝트의 README 와 같게(제목 표 + 상태 열). 🔴 6개 ADR 의 Status 는
      **파일에서 읽어서** 채운다 — 기억이나 추측으로 쓰지 말 것
- [ ] **AC-3 (가드)** — 프로젝트 ADR 의 Status 가 형제 README 행과 갈라지면 **RED**.
      기존 `check-adr-index-drift.sh` 의 제외를 푸는 쪽과 별도 스크립트 쪽 중 하나를 고르고
      근거를 적는다. 🔵 제외를 푸는 쪽이면 그 L70 주석도 함께 갱신한다 — 안 그러면 다음
      사람이 "제외돼 있다" 는 주석을 믿는다
- [ ] **AC-4 (가드가 무는지)** — 프로젝트 ADR 하나의 README 행 상태를 되돌려 **RED**,
      복구해서 **GREEN**. 🔴 이 티켓은 *"돌렸더니 초록이었는데 알고 보니 그 파일을 본 적이
      없었다"* 에서 시작했다. 초록을 근거로 쓰기 전에 **그 초록이 무엇을 봤는지** 보여라
- [ ] **AC-5 (도는 레인)** — `.github/workflows/ci.yml` 의 경로 필터에
      `projects/*/docs/adr/**` 를 추가하고, **프로젝트 ADR 만 건드리는 PR 에서 그 잡이
      실제로 실행되는지**(skip 아님) 확인한다. 🔴 술어가 맞아도 레인이 안 깨어나면
      영원히 초록이다(`MONO-518`·`MONO-524` 에서 두 번)
- [ ] **AC-6 (0건은 통과가 아니다)** — 가드가 프로젝트 ADR 을 **한 건도 못 찾으면 RED**
      가 되게 한다. 글롭이 어긋나거나 디렉터리가 옮겨졌을 때 조용히 초록이 되는 것과
      "전부 정상" 은 구별돼야 한다
- [ ] **AC-7 (안 하는 것도 산출물)** — 루트 `docs/adr/INDEX.md` 의 monorepo-level 범위
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

- [ ] AC-0 ~ AC-7 충족
- [ ] `iam-platform` ADR 6개가 색인에 있고, 그 Status 가 파일과 일치
- [ ] 프로젝트 ADR 만 바꾸는 PR 에서 가드 잡이 **실행**되고 통과
