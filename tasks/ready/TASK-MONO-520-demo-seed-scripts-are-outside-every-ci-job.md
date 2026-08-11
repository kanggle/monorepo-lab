# Task ID

TASK-MONO-520

# Title

데모 시드 스크립트 8개(2,168줄)가 **모든 CI 잡 바깥**에 있다 — 그런데 그것을 고치면 도는 잡이 **초록으로 보고한다**

# Status

ready

# Owner

monorepo

# Task Tags

- ci
- guard

---

# 배경 — `TASK-SCM-BE-059` 리뷰 중 발굴

`TASK-SCM-BE-059` 는 AC-5(시드)를 `[~]` 로 닫으며 *"남은 고유 위험은 **bash 파싱**과
V6 백필 둘"* 이라 적었다. 그 위험이 CI 로 덮이는지 확인하려고 워크플로를 열었더니,
덮이기는커녕 **`bash -n` 조차 돌지 않고 있었다.**

## 실측 (2026-08-11, 추론 아님)

```
$ grep -rn "infra/demo/seed" .github/workflows/
ci.yml:1149:   infra/demo/seed-demo-domain.sh \      ← 다른 경로(infra/demo/, seed/ 아님)
(그 외 0건)

$ wc -l infra/demo/seed/*.sh
  322 lib.sh · 304 seed-ecommerce.sh · 375 seed-erp.sh · 319 seed-fan.sh
  184 seed-finance.sh · 356 seed-scm.sh · 246 seed-wms.sh · 62 seed.sh
 2168 total
```

`ci.yml` § `Demo wrapper smoke (infra/demo)` 의 `bash -n` 루프는 **6개 파일**만 센다 —
`projects.sh` · `demo-up.sh` · `demo-down.sh` · `demo-boot.sh` · `seed-demo-domain.sh` ·
`verify-demo-wrapper.sh`. `infra/demo/seed/` 아래 **8개는 하나도 없다.**

`federation-hardening-e2e.yml` · `nightly-e2e.yml` 의 `seed-*.sql` 히트는 **전혀 다른
파일**(`tests/e2e/fixtures/*.sql`)이고 이 스크립트들과 무관하다. ⇒ **전 워크플로 통틀어
커버리지 0.**

## 🔴🔴 그런데 잡은 *돈다* — 그리고 초록을 낸다

`Demo wrapper smoke` 의 경로 필터는 `infra/demo/**` 다. 즉 시드 스크립트를 고치면
**그 변경 때문에 잡이 깨어나서**, **바뀐 파일을 한 번도 열지 않고**, SUCCESS 를 보고한다.

이것은 "커버리지가 없다" 보다 나쁘다. 커버리지가 없으면 아무도 초록을 근거로 쓰지
않는다. 여기서는 **PR 체크에 `Demo wrapper smoke ✅` 가 뜨고**, 그것이 방금 고친
시드에 대한 판정으로 읽힌다. 실측 사례: PR #3264 (`TASK-SCM-BE-059` 구현)가
`infra/demo/seed/seed-scm.sh` 를 42+/26- 로 고쳤고 `Demo wrapper smoke` 는 **SUCCESS**
였다 — 그 파일을 열지 않은 채로.

🔵 **이 저장소가 이미 이름 붙인 실패 모드다**: `MONO-360` *"첫날 RED 인 가드는 꺼지고,
꺼진 가드는 없는 가드보다 나쁘다 — skip 이 초록으로 보고되니까."* 여기서는 skip 이
아니라 **범위 밖**이 같은 일을 한다.

## 🔴 형제 파리티 낙오

같은 디렉토리 트리에서 **래퍼는 지키고 도메인 스크립트는 안 지킨다**. 래퍼 6개가
`bash -n` 에 들어간 것은 누군가 그 축을 이미 옳다고 판단했다는 뜻이고, 시드 8개가 빠진
것은 결정이 아니라 **`MONO-510` 이 6회차에 걸쳐 스크립트를 하나씩 추가하는 동안 아무도
목록을 다시 세지 않은** 결과로 보인다(AC-0 이 확인한다).

---

# Goal

`infra/demo/seed/` 의 셸 스크립트가 CI 에서 **실제로 검사되게** 한다. 최소선은 문법이고,
그 이상 무엇을 강제할 수 있는지는 AC-2 가 측정해서 정한다 — **가드가 무는지**까지
확인한다(도입만 하고 안 무는 가드는 이 티켓이 논박하는 그 상태를 재생산한다).

# Scope

## In Scope

- `.github/workflows/ci.yml` § `Demo wrapper smoke (infra/demo)` 의 검사 대상 확장
- 목록을 **손으로 세지 않는 형태**로 바꾸기(글롭/`find`) — 아래 AC-1 참조
- `infra/demo/README.md` 또는 시드 `README.md` 에 "무엇이 CI 로 강제되는가" 한 줄

## Out of Scope

- **시드 스크립트의 라이브 실행을 CI 에 넣는 것.** 도메인 스택 기동이 필요하고
  (`MONO-510` 실측: erp 슬라이스만 8컨 3.85 GiB), `MONO-360` 기준으로 **타임아웃 나는
  가드는 꺼진다**. 라이브는 별도 판단.
- 시드 스크립트의 로직 수정. 이 티켓은 **가드**를 고치지 시드를 고치지 않는다.
  검사를 켜서 기존 위반이 나오면 **그 사실을 적고 별건 티켓으로** 돌린다.

---

# Acceptance Criteria

- [ ] **AC-0 (모집단 + 왜 빠졌나)** — 착수 시 위 실측을 **다시 센다**
      (`grep -rn "infra/demo/seed" .github/workflows/` · `bash -n` 목록 · 파일 수).
      🔴 **"6개 래퍼는 있는데 8개 시드는 없다" 가 결정이었는지 누락이었는지 확인**한다
      (`git log -- .github/workflows/ci.yml` 에서 `bash -n` 목록이 마지막으로 바뀐
      커밋과, 시드 스크립트들이 추가된 `MONO-510` 커밋들의 선후). 결정이었다면 사유를
      찾아 적고 이 티켓의 방향을 다시 짠다.
- [ ] **AC-1 (목록을 손으로 세지 않는다)** — 검사 대상을 하드코딩 목록이 아니라
      글롭/`find` 로 바꾼다. 🔴 **이 티켓의 결함 자체가 "손으로 유지되는 목록이
      갈라진 것"** 이므로, 파일 8개를 목록에 더하는 것은 **같은 결함을 8줄 더 크게 만드는
      것**이지 고치는 것이 아니다. 다음에 `seed-<새도메인>.sh` 가 생기면 자동으로 들어와야
      한다.
- [ ] **AC-2 (무엇을 강제할 수 있는지 측정해서 정한다)** — 최소 `bash -n`.
      그 위로 `shellcheck` 가 **현 상태에서 몇 건을 내는지 먼저 재고**, 0 이 아니면
      켜지 말고 건수와 대표 사례를 적는다(첫날 RED = `MONO-360`). `set -euo pipefail`
      선언 여부 같은 정적 규약도 후보 — 채택/미채택 **양쪽 다 사유를 적는다**.
- [ ] **AC-3 (가드가 무는가 — 양방향)** — 시드 스크립트 하나에 **의도적 문법 오류**를
      넣고 잡이 **실패**하는 것, 되돌리면 **통과**하는 것을 둘 다 확인해 로그를 붙인다.
      🔴 "잡이 초록이다" 는 증거가 아니다 — 이 티켓이 존재하는 이유가 정확히 그것이다.
- [ ] **AC-4 (경로 필터 정합)** — `demo-wrapper` 필터가 `infra/demo/**` 라 잡은 이미
      깨어난다(위 실측). 필터를 **건드리지 않는다**는 것을 명시적으로 확인하고 적는다
      — `MONO-074/075` negation quirk 재발 방지. 필터가 아니라 **잡의 술어**가 문제였다.
- [ ] **AC-5 (기존 위반 처리)** — 착수 시점 8개 파일이 새 검사를 통과하는지 먼저 잰다
      (본 티켓 작성 시 `bash -n` 8/8 통과 실측 — 그러나 그건 **오늘의** 상태이지
      가드가 아니다). 위반이 나오면 시드를 고치지 말고 **건수 + 별건 티켓 ID** 를 적는다.

# Related Specs

- 없음 (CI 배선 — 스펙 계층 없음)

# Related Contracts

- 없음

# Edge Cases

- **`lib.sh` 는 실행 스크립트가 아니라 소스 대상**이다. `bash -n` 은 문제없지만
  `shellcheck` 는 `source` 관계를 모르면 미정의 변수로 오탐할 수 있다 —
  AC-2 의 "건수 먼저 재기" 가 이것을 잡는다.
- **`TASK-FIN-BE-068-seed-finance.patch.md`** 가 같은 디렉토리에 있다(`.md`).
  글롭을 `*.sh` 로 잡으면 자연히 빠지지만, `find`+`-name '*'` 류로 잡으면 들어온다.
- **새 도메인 추가 시** — AC-1 의 글롭이 자동으로 집어야 한다. 이것이 AC-1 이
  하드코딩 목록을 금지하는 이유다.

# Failure Scenarios

- **8개 파일을 `bash -n` 목록에 그냥 추가하고 닫는다** → 손으로 유지되는 목록이
  더 길어졌을 뿐이고, 7번째 도메인 시드가 생기는 날 같은 결함이 재발한다. AC-1 이 막는다.
- **`shellcheck` 를 재보지 않고 켠다** → 첫날 RED, 잡이 꺼지거나 `|| true` 가 붙고,
  꺼진 가드의 skip 이 초록으로 보고된다(`MONO-360`). AC-2 가 막는다.
- **가드를 넣고 bite 확인을 안 한다** → 이 티켓이 고발한 상태(도는데 아무것도 안 보는
  잡)를 다른 이름으로 재생산한다. AC-3 이 막는다.

# Definition of Done

- [ ] AC-0 ~ AC-5 전부 충족
- [ ] `infra/demo/seed/*.sh` 가 CI 에서 실제로 검사되고, **bite 가 양방향으로 실측**됨
- [ ] 검사 대상 목록이 손으로 유지되지 않음
