# Task ID

TASK-MONO-550

# Title

온디맨드 데모 호스트가 **자력으로 뜨지 못한다** — `env-preflight` 가 fresh clone 에서 부팅을 중단시킨다

# Status

ready

# Owner

monorepo

# Task Tags

- infra
- demo

---

# 배경 — 2026-08-17 라이브 실측 (TASK-MONO-477 AC-7 중 발견)

재굽기한 AMI(`ami-008695099ec898477`, main `d7ded4429`)로 `terraform apply` 한 뒤,
**컨테이너가 0개**였다. 9개 도메인 전부 `{"state":"down","healthy":0,"total":0}`.

```
[env-preflight] ✖ wms 기동을 중단합니다 — /opt/monorepo-lab/projects/wms-platform/.env 가 없습니다.
[env-preflight] ✖ ecommerce 기동을 중단합니다 — .../.env 가 없습니다.
systemd: demo-stack.service: Failed with result 'exit-code'
```

`infra/demo/check-env-preflight.sh`(**TASK-MONO-548**)가 `demo-up.sh:89` 에서 `|| exit 1` 로
하드 중단한다. **AMI 는 fresh clone 이고 `projects/*/.env` 는 gitignored 라 존재할 수 없다.**

## 🔴 preflight 이 틀린 게 아니다 — 측정해서 확인했다

`demo-ami.pkr.hcl` 헤더는 이렇게 적고 있다:

> 4) 프로젝트 .env 는 gitignored 라 fresh clone 에 없다. **demo.env 가 값을 제공한다**(MONO-346).
>    여기서 별도 seeding 을 하지 않는 이유다.

**그 전제가 부분적으로만 참이다.** `infra/demo/demo.env` 에서 preflight 이 지목한 자격 변수를 세면:

| 변수 | demo.env 에 있나 |
|---|---|
| `POSTGRES_ROOT_PASSWORD` | **0건** |
| `MASTER_DB_PASSWORD` | **0건** |
| `INBOUND_DB_PASSWORD` | **0건** |
| `INVENTORY_DB_PASSWORD` | **0건** |
| `OUTBOUND_DB_PASSWORD` | **0건** |
| `ADMIN_DB_PASSWORD` | **0건** |
| `MINIO_ROOT_PASSWORD` | **0건** |
| `NOTIFICATION_DB_PASSWORD` | 1건 |
| `GRAFANA_ADMIN_PASSWORD` | 1건 |

⇒ **데모 호스트는 그동안 compose 폴백 자격(`${VAR:-fallback}`)으로 떠 왔다.** MONO-548 은 그것을
정확히 발견하고 거부한 것이다. **가드를 무력화하는 방향으로 고치지 말 것.**

## 🔴 왜 아무 가드도 이걸 못 봤나

`verify-demo-wrapper.sh` 가드 (g)는 *"미설정 compose 변수 0건"* 을 본다. 그런데 `${VAR:-fallback}` 은
**해소된다** — 폴백으로. 즉 (g)는 *"값이 정해진다"* 를 보고 *"의도한 값이 공급된다"* 를 보지 않는다.
**대리지표다.** 두 명제의 차이가 이 티켓이다.

## 지금 데모가 떠 있는 이유 (⚠️ 저장소의 상태가 아니다)

MONO-477 AC-7 실증을 위해 **SSM 으로 인스턴스에서 직접** `cp .env.example .env` 를 돌렸다.
그 파일들은 루트 EBS 볼륨에 있으므로 **stop/start 는 견디지만 `terraform destroy` 하면 사라진다.**
⇒ **저장소만으로는 데모가 재현되지 않는다.** 이 티켓이 닫히기 전까지 그 상태다.

# Goal

`terraform apply` → `/start` 만으로 **사람 손 0** 으로 데모가 뜬다. preflight 을 우회하지 않고,
그것이 요구하는 것을 **정당하게 충족**해서.

# Scope

## In Scope

- 데모 부팅 경로가 `check-env-preflight.sh` 를 **정당하게 통과**하도록 만드는 것. 선택지(구현자가
  AC-0 에서 재확인 후 결정, 근거를 티켓에 적을 것):
  - (a) **bake 시점에 `.env` 생성** — packer 가 각 프로젝트의 `.env.example` 을 `.env` 로 복사.
    preflight 자신의 안내(`cp .env.example .env`)와 같은 처방이고, 자격이 명시적이 된다.
  - (b) **`demo.env` 가 그 자격들을 공급** — 폴백과 `.env.example` 이 갈라지는 변수를 전부 채운다.
    `set -a; source demo.env` 가 preflight 보다 먼저 돌므로 compose 는 그 값을 보간한다.
    단 preflight 은 **파일**을 보므로 술어도 함께 고쳐야 한다(환경에 이미 있으면 통과).
  - (c) `.env` 를 데모 레이어가 소유(`infra/demo/env/<project>.env` 를 커밋하고 bake 가 배치).
- 재발 방지 가드. **(g)로는 안 된다**(위 § 참조). 저장소만 보고 *"fresh clone 상태에서 데모 부팅이
  preflight 을 통과하는가"* 를 판정해야 한다 — 워킹트리에 개발자의 `.env` 가 있어도 통과가 바뀌지
  않아야 한다(그게 바로 이 결함이 로컬에서 안 보였던 이유다).
- 🔴 **재굽기 필요** — `infra/demo/` 와 `projects/*/` 는 AMI 에 구워지는 층이다.

## Out of Scope

- `check-env-preflight.sh` 의 **중단 자체를 없애는 것**. 그것은 진짜 위험(볼륨에 각인되는 자격)을
  막고 있고, 이 티켓은 그 위험을 해소하는 것이지 감추는 것이 아니다.
- 데모 자격의 **강도** 개선(약한 예시 비밀번호 → 강한 비밀번호). 별개 판단이고, 현재도 폴백을
  쓰고 있으므로 이 티켓이 보안 수준을 낮추지 않는다. 필요하면 별도 티켓.

# Acceptance Criteria

**AC-0 — 재확인 (verify-then-act).**
`origin/main` 에서 `check-env-preflight.sh`·`demo-up.sh`·`demo.env`·`demo-ami.pkr.hcl` 을 다시 읽는다.
위 표의 "0건" 을 **다시 센다** — 인계된 숫자는 출처가 아니라 가설이다. MONO-548 이 그 사이 바뀌었을
수 있다.

**AC-1 — fresh clone 조건에서 preflight 이 통과한다.**
개발자 워킹트리의 `.env` 유무와 **무관하게** 판정되어야 한다. 예: 임시 디렉터리에 `git archive`/
`git clone` 한 트리에서 `demo-up.sh` 의 preflight 구간만 돌려 rc=0 을 확인한다.

**AC-2 — 가드.** 저장소만 보고 AC-1 의 명제를 지킨다. **bite 필수**: 고침을 되돌리면(예: 생성한
`.env` 를 지우거나 `demo.env` 에서 자격을 빼면) 빨개져야 한다. 가드는 정적 구간에 두어 CI
"Demo wrapper smoke" 와 packer 7단계가 **실제로 돌린다**(러너 없는 가드는 썩는다 — MONO-477 가 (t)로 배웠다).

**AC-3 — 재굽기 + 라이브 실증.** 새 AMI 로 `terraform apply` → **손대지 않고** `/domains` 가
도메인들을 `up` 으로 보고하는지 확인한다. **SSM 으로 손을 대는 순간 이 AC 는 무효다** —
이 티켓의 명제가 정확히 "사람 손 0" 이기 때문이다.
⚠️ `packer build`/`terraform apply` 는 사용자 승인 필요.

**AC-4 — 볼륨 자격 오염이 없음을 확인.** 고침 적용 후 첫 부팅에서 DB 가 초기화되는 값과 앱이 쓰는
값이 같은지 확인한다(preflight 이 막으려던 실패 모드가 재현되지 않는지). 최소 1개 프로젝트에서
`psql` 로 롤 인증이 성공하는 것을 본다.

# Related Specs

- `infra/demo/check-env-preflight.sh` (TASK-MONO-548) — 중단의 주체
- `infra/demo/demo-up.sh` L43(`source demo.env`) · L89(preflight 호출) — 순서가 load-bearing
- `infra/demo/demo.env` · `projects/*/.env.example`
- `infra/demo/aws/packer/demo-ami.pkr.hcl` § 선행조건 4 — **이 티켓이 그 주석의 전제를 정정한다**
- `infra/demo/verify-demo-wrapper.sh` 가드 (g) — 왜 못 잡았는지의 근거

# Related Contracts

없음 (인프라 전용 — HTTP/이벤트 계약 무변경).

# Edge Cases

- **개발자 로컬은 `.env` 가 있어서 통과한다** — 이 결함이 로컬에서 한 번도 안 보인 이유다. 가드는
  fresh clone 조건을 재현해야 하고, 그렇지 않으면 또 AMI 에서만 터진다.
- **`.env.example` 이 늘어난다** — 새 프로젝트가 생기면 자동으로 범위에 들어와야 한다. 목록을
  손으로 나열하면 그 순간 드리프트가 시작된다(이 저장소가 이미 두 번 데인 실패 모드).
- **`.env` 를 커밋하는 선택지(c)** — gitignore 와 충돌한다. `infra/demo/env/` 처럼 **데모 전용
  경로**를 쓰고 프로젝트의 `.env` 자리에 bake 가 배치하는 형태여야 한다.
- **stop/start 는 볼륨을 보존한다** — 현재 인스턴스에 손으로 만든 `.env` 가 남아 있어 "고쳐진 것처럼"
  보일 수 있다. 판정은 반드시 **새 인스턴스**에서.

# Failure Scenarios

- **preflight 을 스위치로 끄고 닫는다** — 이 티켓이 명시적으로 금지하는 해법. 볼륨 자격 각인은
  재기동으로 절대 고쳐지지 않는 부류이고(그 스크립트 자신이 설명한다), 증상은 앱 크래시 루프라
  원인을 엉뚱한 곳에서 찾게 된다.
- **로컬 초록을 근거로 닫는다** — 개발자 트리에는 `.env` 가 있다. AC-1 의 fresh clone 조건이
  없으면 가드는 **아무것도 보지 않으면서 초록**이다.
- **재굽기 없이 닫는다** — `infra/demo/`·`projects/*/` 는 baked 층이다. main 이 초록인 것은 데모가
  고쳐졌다는 증거가 아니다(MONO-399 가 가르친 명제).
- **`.env` 를 만들되 값이 `.env.example` 과 달라진다** — preflight 이 막으려던 바로 그 갈라짐을
  새로 만드는 것이다. 생성원은 `.env.example` 하나여야 한다.

# Notes

- 분석 = **Opus 5** / 구현 권장 = **Opus** — bash(부팅 경로) + packer(bake 층) + 가드 설계 +
  라이브 실증 꼬리. 단순 fix 아님. 특히 **가드가 fresh clone 조건을 재현해야** 하는 부분이 설계다.
- 선행: 없음. 후속: 이 티켓이 닫히기 전까지 **온디맨드 데모는 저장소만으로 재현되지 않는다**
  (현재 인스턴스는 손으로 만든 `.env` 에 의존).
- 관련: `TASK-MONO-477`(이 결함을 발견한 AC-7 실증), `TASK-MONO-548`(중단의 주체),
  `TASK-MONO-346`(demo.env 가 값을 제공한다는 원래 전제).
