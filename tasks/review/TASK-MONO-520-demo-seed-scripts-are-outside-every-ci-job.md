# Task ID

TASK-MONO-520

# Title

데모 시드 스크립트 8개(2,168줄)가 **모든 CI 잡 바깥**에 있다 — 그런데 그것을 고치면 도는 잡이 **초록으로 보고한다**

# Status

review

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

- [x] **AC-0 (모집단 + 왜 빠졌나) — 완료 2026-08-11. 🔴 이 티켓의 숫자가 틀렸다.**
      재측정: `grep -rn "infra/demo/seed" .github/workflows/` → **0건** 유지.
      그러나 미보호는 **8개가 아니라 16개 중 10개**다 — `seed/` 8 + `demo-status.sh`
      + `aws/ec2/user-data.sh`. 뒤 둘도 목록 작성 이후에 추가됐다.
      **누락 확정(결정 아님)**: `bash -n` 목록은 `39f816c1a` **2026-07-10**
      (`TASK-MONO-341` #2371)에 작성됐고, `git ls-tree -r 39f816c1a -- infra/demo/seed/`
      가 **0 파일** ⇒ 그 디렉토리는 **존재하지도 않았다**. 시드 8개는 4주 뒤
      2026-08-05~07 `MONO-510` 이 넣었다. 목록을 다시 센 사람이 없었을 뿐이다
- [x] **AC-1 (목록을 손으로 세지 않는다) — 완료.** 하드코딩 6줄 → `find infra/demo
      -type f -name '*.sh'`. 이름 10개를 더하지 **않았다** — 손유지 목록이 디렉토리와
      갈라지는 것이 결함 자체이므로 더하면 열한 번째 스크립트가 같은 일을 반복한다.
      🔵 **발견이 아무것도 못 찾으면 빈 집합 위에서 초록을 낸다** ⇒ 도달성 술어 2개:
      집합이 비지 않을 것 + **`infra/demo/seed/` 에 실제로 닿을 것**(이 티켓의 부재 지점).
      CI 실측: `checked 16 script(s) under infra/demo/` (이전 6)
- [x] **AC-2 (무엇을 강제할 수 있는지 측정해서 정한다) — 완료. shellcheck 는 켜지 않는다.**
      최소선 `bash -n` 채택. 🔴 **shellcheck 는 재고 나서 뺐다**(로컬 미설치 + docker
      데몬 down 이라 1회성 비-게이팅 스텝으로 러너에서 계측, run `31478823405`):
      전 16개 대상 **총 27건 = note 21 · warning 6 · error 0**. 0 이 아니므로 켜면
      **첫날 RED** 이고, 첫날 RED 인 가드는 꺼지며 꺼진 잡의 skip 은 초록으로 보고된다
      (`MONO-360`) — 이 티켓이 없애려는 바로 그 상태를 더 그럴듯한 체크 하나 얻자고
      재생산할 수는 없다. 🔵 **분포가 "결함" 이 아니라 "구조" 라고 말한다**: warning 6 중
      **5건이 `SC2034 appears unused`** 이고 전부 `projects.sh`/`lib.sh` — **source 되는
      라이브러리**라 그 변수는 source 하는 쪽이 쓴다(shellcheck 가 source 를 안 따라가면
      볼 수 없다). `SC1091 Not following: ./demo.env` 4건도 같은 실명. ⚠️ **가설이지
      주장 아님**: `shellcheck -x` 면 상당수가 사라질 것 — **돌려보지 않았다**. 위 숫자는
      plain shellcheck 기준이다. 진짜 냄새는 1건(`lib.sh:146` `SC2155`)이고 이 티켓은
      가드를 고치지 시드를 고치지 않으므로 손대지 않았다. `set -euo pipefail` 규약은
      **미채택** — 대상 16개가 서로 다른 실행 맥락(sourced lib · EC2 user-data · 래퍼)을
      갖고, 일괄 강제는 이 티켓이 범위 밖으로 둔 "시드 로직 수정" 이 된다
- [x] **AC-3 (가드가 무는가 — 양방향) — 완료·실측.** 로컬 하네스, 결과 전문:
      control(무수정) `ok 16` → **`seed/seed-scm.sh` 에 파스 오류 주입 → FAIL(line 360)**
      → 복원 `ok 16` → `.sh` 는 있으나 `seed/` 없음 → **ASSERT-2 발화** → 빈 트리 →
      **ASSERT-1 발화**. 🔵 주입 대상을 `seed/` 밑으로 잡아 **옛 목록이 못 보던 부류에
      닿는 것**까지 함께 증명했다. 🔴🔴 **첫 주입은 문법 오류가 아니었다** —
      `if [ 1 -eq 1 ; then echo x; fi` 는 `[` 가 명령어라 `]` 누락이 **런타임** 오류이고
      `bash -n` 은 통과시킨다. 하네스가 *"주입이 됐나"* 를 먼저 검사하게 짜 둬서
      **"가드가 안 문다" 로 오판하지 않았다.** 복원은 `git checkout --` 가 아니라
      바이트 사본(그 하네스가 작업분을 지운 사건이 있다)
- [x] **AC-4 (경로 필터 정합) — 완료. 필터는 두 번 다 안 건드렸다.** 🔴🔴 **그리고 이
      AC 의 전제가 절반만 맞았다**: *"필터가 `infra/demo/**` 라 잡은 이미 깨어난다"* 는
      **시드를 고칠 때만** 참이고, **변경 대상이 잡 자신일 때는 거짓**이다. 이 티켓의
      첫 푸시가 `ci.yml` 만 바꿨는데 잡이 **SKIPPED** 였고 PR 은 초록이었다
      (27 SUCCESS / 14 SKIPPED / 0 fail) ⇒ **가드를 고쳐 놓고 한 번도 실행하지 않은 채
      머지할 수 있었다.** 이 파일 헤더 ~83행이 이미 *"workflow self-change → `workflows`
      플래그가 잡아서 모든 잡이 활성화"* 라고 **주장**하는데, `outputs.workflows` 를 보는
      잡 술어가 **24곳**이고 이 잡만 없었다 — **형제 파리티 낙오**. `if:` 에 그 플래그를
      더했다(필터 무수정). ⇒ **두 번 다 필터가 아니라 잡의 술어가 결함이었다.**
      실측 확증: 수정 후 런에서 `Demo wrapper smoke` = `COMPLETED / SUCCESS`
- [x] **AC-5 (기존 위반 처리) — 완료. 위반 0.** 착수 시점 `bash -n` 로컬 16/16 통과,
      CI 에서도 `checked 16 script(s)` 로 초록 ⇒ 기존 위반 0 으로 켜진다.
      🔵 그것이 **오늘의 상태이지 가드가 아니라는** 것이 이 티켓의 논지였고, 이제
      가드가 있다

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

- [x] AC-0 ~ AC-5 전부 충족
- [x] `infra/demo/seed/*.sh` 가 CI 에서 실제로 검사되고(`checked 16 script(s)`),
      **bite 가 양방향으로 실측**됨(주입 선검증 포함)
- [x] 검사 대상 목록이 손으로 유지되지 않음(`find` 발견 + 도달성 술어 2개)
- [x] 🔴 **티켓에 없던 결함 1건 추가 수정** — `Demo wrapper smoke` 가 `outputs.workflows`
      를 보지 않아(24곳 중 이 한 곳만) **잡 자신의 변경으로는 깨어나지 않던** 형제 파리티
      낙오. 이 티켓의 첫 푸시가 스스로 드러냈다(가드를 고쳤는데 그 가드가 안 돌고 초록)
- [x] 🔵 **남는 것(조용한 누락 없이)** — shellcheck 미채택(27건, 첫날 RED 회피) ·
      `shellcheck -x` 가설 **미검증** · `lib.sh:146 SC2155` 미수정(시드 로직은 범위 밖)
