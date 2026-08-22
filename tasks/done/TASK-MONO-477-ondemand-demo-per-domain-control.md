# Task ID

TASK-MONO-477

# Title

온디맨드 데모에 **도메인별 선택 기동/정지**를 얹는다 — 항상-뜬 페이지에서 전체 또는 개별 도메인을 켜고 끈다

# Status

done

# Owner

monorepo

# Task Tags

- infra
- demo

---

# 진행 상황 (2026-07-29 재확인, AC-0 재검증)

**항목 1~8(로컬 스크립트 + 컨트롤 플레인 전체) = 이미 구현·병합 완료.** `ready/`에 남아 있었던 이유는
구현이 안 끝나서가 아니라 **태스크 lifecycle(ready→done, INDEX)이 안 닫혔기 때문**이다 — 두 PR 모두
이 태스크 파일을 건드리지 않았다.

- **PR [#2937](https://github.com/kanggle/monorepo-lab/pull/2937)** (2026-07-24 MERGED) — 항목 1~4
  (로컬 스크립트: `projects.sh` DEPS/`resolve_deps`, `demo-up.sh`/`demo-down.sh` 도메인 인자,
  `demo-status.sh` 신규). AC-1~3 충족.
- **PR [#2940](https://github.com/kanggle/monorepo-lab/pull/2940)** (2026-07-24 MERGED) — 항목 5~8
  (컨트롤 플레인: `handler.py` 3신규 액션 + 명령 주입 화이트리스트 + VM-stopped 409 + 예산 429 상속,
  `main.tf` IAM/라우트/스냅샷 파라미터, `site/index.html` 도메인 그리드, `test_handler.py` 24건).
  AC-4~6 충족.
- 3차원 병합 검증(`gh pr view --json state,mergedAt,mergeCommit,statusCheckRollup`) 통과 — 둘 다
  `state=MERGED`, git log에 머지 커밋 존재, 필요 체크 전부 SUCCESS/SKIPPED(FAILURE 0).

**남은 것 = AC-7(AWS 실증) · AC-8(재굽기, `TASK-MONO-399` AC-6과 병합) 뿐이다.**

## ✅✅ 2026-08-17 — AC-7 · AC-8 라이브 실증 **완료** (AMI `ami-008695099ec898477`, main `d7ded4429`)

`packer build`(59분33초, `PACKER_RC=0`) → `terraform apply`(34 added, 0 error) → 라이브 검증.
`site_url = https://d38c06ry1h6rnn.cloudfront.net` · `instance i-0968541e8f2b80b4c`.

**AC-8 재굽기 · MONO-399 AC-6 — 구운 것을 믿지 않고 인스턴스 안에서 단언했다:**

| 단언 | 실측값 |
|---|---|
| `git -C /opt/monorepo-lab log -1` | **`d7ded44298686d36ca6fe41225b5c154fc76e14e`** = 구우려던 커밋 |
| `docker inspect ecommerce-kafka .HostConfig.Memory` | **`1073741824`** — MONO-397 의 1G 가 마침내 데모에 도달 |
| 그 컨테이너 `RestartCount` | **0** |
| `finance-platform-kafka` | **Up (healthy)** — FIN-BE-059 브로커 존재 |
| `aws` / `node` | `aws-cli/2.36.24` / `v18.19.1` |
| `df -h /` | 96G 중 **24G 사용** ← 다음 bake 의 `volume_gb` 근거(인계된 36GB 가 아니다) |

🔵 `git log` 는 처음에 **빈 출력**이었다 — root 가 ubuntu 소유 repo 를 읽어 `dubious ownership` 로
죽었는데 stderr 를 안 봤다. `finance-kafka` 도 `NO_CONTAINER` 였는데 실제 이름은
`finance-platform-kafka` 였다. **둘 다 부재가 아니라 계측 실패다.**

**AC-7 라이브 실증 — 발행자와 토글이 처음으로 실행됐다:**

- SSM 파라미터 Version **7→8→9, 35초 간격 갱신**. `systemctl list-timers` = active, NEXT 28s.
  간격이 ~60초가 **아닌 것**이 `AccuracySec=5s` 가 일한다는 증거다.
- `POST /domain/stop {scm}` → scm `up 9/9` → `up 1/1` → **`down 0/0`**.
  그 동안 **traefik `up 1/1` · iam `partial 14/15` 유지** ⇒ **마지막 소비자 가드가 실제로 물었다(AC-2).**
- `POST /domain/start {scm}` → 되살아남. iam 15/15 · finance 7/7 · erp 8/8 · fan 9/9 · console 2/2.
- **내려가는 방향을 봤다** — 올라가는 방향만 보면 한 방향으로만 고장 난 게이트를 못 잡는다.

⚠️ **행사되지 못한 것 = 예산 소진 429.** 600분을 태우지 않고는 라이브에서 행사할 수 없다
(`test_handler.py` 가 목으로 덮는다). **"검증됨" 으로 적지 않는다.**

🔴 **그러나 데모는 자력으로 뜨지 못했다** — 첫 부팅에서 컨테이너 **0개**. `env-preflight`(MONO-548)이
fresh clone 에 `.env` 가 없다며 부팅을 중단시켰고, 위 실증은 **SSM 으로 손수 `cp .env.example .env`**
한 뒤에야 가능했다. 그 파일은 볼륨에만 있다 ⇒ **저장소만으로는 데모가 재현되지 않는다.**
⇒ **[`TASK-MONO-550`](TASK-MONO-550-demo-host-cannot-boot-env-preflight.md) 로 분리해 파일했다.**
이 티켓의 토글·헬스 기전은 증명됐고, 부팅 경로 결함은 별건이다.

~~**남은 뒷정리(사용자 결정 대기)**~~ 🔴 **2026-08-22 종결 — 아래 § 참조.** (옛 AMI·스냅샷은 이미 없었고, `destroy` 는 하지 않기로 명시 결정.)
`snap-0e96353c6bb20a2e8` 삭제. `tfvars` 의 `ami_id` 갱신은 완료. destroy 는 되돌릴 수 없고 현재
인스턴스의 손수 만든 `.env` 도 함께 사라지므로 승인 대상으로 남긴다.

## ✅✅ 2026-08-22 UTC — **뒷정리 종결. `terraform destroy` 는 하지 않는다 (명시적 결정).**

이 티켓에 남아 있던 것은 재굽기가 아니라 **되돌릴 수 없는 정리 결정** 두 가지였다. 실측으로
다시 세었더니 하나는 **이미 끝나 있었고**, 하나는 **하지 않는 것이 맞다**.

### (a) 옛 AMI · 스냅샷 정리 — ✅ 완료

이 티켓이 지목한 `ami-0b6b962d3f3f23865` / `snap-0e96353c6bb20a2e8` 은 **이미 존재하지 않았다**
(2026-08-22 실측: 소유 AMI·스냅샷이 각각 1개뿐). 🔴 *"남은 뒷정리"* 라는 문장을 근거로 큐를
읽으면 틀린다 — **목록은 물려받지 말고 다시 세라.**

그 자리에 새로 생긴 세대 교체는 이번에 처리했다: `TASK-MONO-552` AC-4 를 위해 구운
`ami-08df900798bc99c83` 가 **라이브에서 증명된 뒤**(9/9 도메인 up · 표면 3/3 · 시드 2회차 rc=0)
직전 판 `ami-0c768f12eb9a024ce` 와 `snap-0990edf3308871871` 을 삭제했다.
🔵 **증명 전에는 지우지 않았다** — 그것이 유일한 롤백 판이었다. 사후 확인: AMI 1개 · 스냅샷 1개.

### (b) `terraform destroy` — 🔴 **하지 않는다**

`destroy` 는 데모 스택 자체를 없앤다. 이 데모는 **포트폴리오의 전시물**이고, 온디맨드 설계
(`/start` → 유휴 20분 자동 정지 → 월 600분 상한)가 이미 비용을 묶고 있다:

| 축 | 실측 (2026-08-22) |
|---|---|
| 월 누적 사용 | **486 / 600분** (9월 1일 리셋) |
| 상시 비용 | EBS 100GB gp3 — `variables.tf` 주석 기준 월 $9 |
| 가동 비용 | 600분 × r6i.2xlarge ≈ 월 $5 |

즉 *"쓰지 않으면 거의 들지 않는다"* 가 이미 성립한다. `destroy` 로 얻을 것은 EBS 상시분뿐이고,
잃는 것은 **면접관이 링크를 눌렀을 때 뜨는 데모 전체**다. ⇒ **유지가 옳다.**

⚠️ 이 결정을 뒤집으려면(계정 정리·장기 미사용 등) 새 티켓으로 근거를 적고 하라. 이 문장이
있는 한, *"뒷정리가 안 끝났다"* 로 이 티켓을 다시 열지 않는다.

**⇒ AC-1~AC-8 전부 완료 + 뒷정리 결정 완료. 이 티켓은 닫는다.**

---

## ✅ 2026-08-17 — 헬스 발행자 작성 완료 (AC-7 선행 작업)

아래 "알려진 갭"이 가리키던 조각이 이제 저장소에 있다:

| 파일 | 역할 |
|---|---|
| `infra/demo/demo-status-publish.sh` | `demo-status.sh` 출력을 SSM 파라미터에 발행. 리전은 IMDSv2 로 파생(하드코딩 없음) |
| `infra/demo/demo-status.service` | oneshot. 저장소 소유 유닛(MONO-366 과 같은 이유) |
| `infra/demo/demo-status.timer` | `OnUnitActiveSec=30s` + **`AccuracySec=5s`** |
| `demo-ami.pkr.hcl` | AWS CLI 설치(+`aws --version` 확증) · 두 유닛을 저장소 경로에서 설치 · **타이머**를 enable |
| `verify-demo-wrapper.sh` 가드 (z) | 발행 경로 5고리 + **파라미터 이름 3곳 대조** |

### 🔴 그리고 그 PR 은 한 번도 실행된 적 없는 줄을 실었다 — 가드가 그것을 핀으로 고정했다

`②packer build` 착수 **직전**에 새로 넣은 `sudo apt-get install -y awscli` 한 줄을 같은 베이스
OS(`ubuntu:24.04`)에서 돌려봤다. **없는 패키지였다:**

```
E: Package 'awscli' has no installation candidate      (rc=100)
```

universe 는 켜져 있다 — 저장소 구성 문제가 아니라 **noble 에 그 패키지가 없다.** 클라우드 이미지도
같은 아카이브를 쓰므로 **빌드는 1단계에서 죽었을 것이다**(57분짜리 빌드의 3분 지점).

이 사건의 값어치는 실패 자체가 아니라 **가드가 초록이었다는 것**이다. 가드 (z)는
`apt-get install -y awscli` 라는 **문자열의 존재**를 물었고, 그 문자열은 거기 있었다.
⇒ 가드가 지킨 것은 동작이 아니라 **내가 적은 문장**이다. 이 저장소가 반복해서 배우는 명제의
또 한 사례다 — *정적 검사 통과 ≠ 동작*, 그리고 *핀은 자기가 지키려던 결함을 얼릴 수 있다.*

**고침**: apt 저장소 구성에 의존하지 않는 **AWS CLI v2 공식 설치기**(`awscli-exe-linux-x86_64.zip`)
로 교체. 가드 (z)의 술어도 설치기 + `aws --version` 확증 **양쪽**을 요구하도록 바꿨고,
이번엔 **`ubuntu:24.04` 컨테이너에서 실제로 설치·실행되는 것을 확인한 뒤** 커밋했다.

🔵 교훈(다음 착수자에게): **AMI 에 새 패키지를 추가할 때는 커밋 전에 그 베이스 OS 컨테이너에서
한 번 돌려라.** `packer validate` 도 가드도 이 층을 보지 못한다. 비용은 몇 분, 안 하면 57분이다.

### 🔴 1차 bake 실패 — 그리고 그건 내 변경이 아니라 **한 달 묵은 잠복 결함**이었다

`packer build` 1차(2026-08-17): 1단계 통과(`aws-cli/2.36.24 … ubuntu.24` — 위 고침이 실환경에서
확증됨), bootJar 42개 통과, **8개 프로젝트 이미지 전부 통과**, 그리고 **7단계에서 죽었다.**

```
[verify] (t) 페이지가 만드는 데모 도메인이 부팅이 파생하는 것과 같은가
  FAIL: (t) node 가 없습니다 — 이 가드는 페이지의 규칙을 **실행**해서 대조합니다.
```

20분 6초 / 산출물 0개 / 실비 ≈ $0.23.

**가드 (t) 는 `node -e` 로 페이지의 `demoHost()` 를 실행해 부팅 파생과 대조한다(MONO-389).
그런데 packer 는 node 를 설치한 적이 없다.** (t) 가 추가된 날은 **마지막 성공 bake 의 다음 날**
(07-13 bake → 07-14 MONO-389)이라, **그 뒤로 아무도 굽지 않는 동안 한 달 넘게 잠복**해 있었다.
*"커밋이 main 에 있다 ≠ 실행된다"* 의 거울상이다 — **가드가 main 에 있다 ≠ 그 가드가 도는 환경이
그것을 지탱한다.**

**고침 두 가지:**

1. packer 1단계에 `nodejs` 설치 + `node --version` 확증.
2. **가드 (z2) 신설 — 도구 이름을 박지 않는다.** `nodejs` 하나만 요구하면 다음에 추가되는 도구는
   또 못 잡는다. 그래서 **이 스크립트 자신의 정적 구간에서 `command -v` 선언을 뽑아** packer 의
   설치 목록과 대조한다. 새 요구가 생기면 자동으로 범위에 들어온다. 추출 0건은 "의존 없음" 이
   아니라 **실패**로 본다.

**대조군이 곧 아는 결함이다**: (z2)의 술어를 **머지 전 `origin/main` 의 packer 템플릿**(= 방금
20분을 태운 바로 그 버전)에 대고 돌리면 `node(→nodejs) 미설치` 를 보고한다. 즉 이 가드가 있었다면
그 빌드는 **시작 전에** 빨개졌다.

🔵 **패키지 존재 확인법 — `packages.ubuntu.com` 을 믿지 마라.** 그 페이지는 **없는 `awscli` 에도
200** 을 돌려준다(대조군으로 확인). apt 가 실제로 읽는 인덱스를 봐라:

```
curl -s http://archive.ubuntu.com/ubuntu/dists/noble/universe/binary-amd64/Packages.gz \
  | zcat | grep -c '^Package: <pkg>$'
```
실측(main+universe/amd64): `nodejs=1` · `unzip=1` · **`awscli=0`**(아는 결함과 일치 ⇒ 계측기 유효).

**설계 판단 두 가지 (근거를 남긴다):**

1. **실패 시 `{}` 를 쓴다 — 직전 값을 남기지 않는다.** 남기면 파라미터가 마지막으로 성공한
   "전부 up" 에 얼어붙고 페이지는 죽은 도메인을 초록으로 그린다(이 티켓 Failure Scenario 가
   금지하는 fail-open). `{}` 는 `handler.py:domains()` 가 빈 dict 로 읽고 페이지는 "확인 중" 을
   그린다 — 이미 있는 소비자 동작을 그대로 쓴다. 발행 **자체**가 실패하면 non-zero 로 죽어
   journald 에 남기고 30초 뒤 재시도한다(성공한 척하지 않는다).
2. **`AccuracySec=5s` 는 장식이 아니다.** systemd 기본 AccuracySec 은 **1분**이라 그것 없이
   `OnUnitActiveSec=30s` 만 적으면 실제 주기가 ~1분이 된다 — 유닛의 선언과 실제가 벌어지고,
   "표시 지연을 정직하게 적는다" 는 AC-5 요구의 근거가 무너진다. 가드 (z)가 이 줄을 지킨다.

**검증한 것 / 안 한 것:**

- ✅ `bash -n` · `packer init && packer validate` (rc=0) · 정적 `verify-demo-wrapper.sh` 전체 PASS
- ✅ **bite 4/4** — ① `AccuracySec` 제거 ② 발행자 파라미터명만 변경(3곳 대조) ③ packer 가
  timer 대신 service 를 enable ④ 실제 `put-parameter` 호출만 무력화하고 **주석은 남김**.
  ④ 는 순진한 substring grep 이라면 통과했을 조건이다(주석 1건이 남아 있다) — 주석 제거 후
  본문을 무는 술어라야 문다는 것을 그 자리에서 확인했다.
- ❌ **라이브 검증 0** — `systemctl status demo-status.timer`, SSM `GetParameter` 값 갱신,
  페이지 배지 반영은 **전부 AC-7 에서 처음 돈다.** 이 티켓 자신의 규율대로 *미리 써두고 나중에
  믿지 않는다* — 위의 초록은 전부 정적이다. `packer validate` 통과가 동작을 뜻하지 않는다는
  것은 이 PoC 가 이미 다섯 번 배운 명제다.

---

**🔴 (해소됨 — 위 절 참조) 알려진 갭 — 인스턴스 헬스 발행자가 코드에 없다.** PR #2940 본문이 명시적으로 이렇게 적었다:
> 인스턴스 헬스 발행자(`demo-status-publish.sh` + `demo-status.timer` + packer + user_data)는 baked
> 라 살아있는 인스턴스에서만 검증 가능하므로 AWS/재굽기 증분(AC-7/8, MONO-399 AC-6 와 합침)으로 연기.

즉 `main.tf`의 인스턴스 IAM(`ssm:PutParameter`, `aws_iam_role_policy.ec2_health`)과 SSM 파라미터
(`aws_ssm_parameter.health`)는 이미 있지만, 그것을 실제로 채우는 **systemd 타이머 스크립트가 아직 없다**
— `/domains`는 apply 직후 terraform 초기값 `{}` 만 반환한다(도메인 "확인 중" 표시로 정직하게 처리됨,
빈 값을 "전부 up"으로 오독하지 않음). 이 조각은 **의도적으로** AC-7/8과 함께 라이브 인스턴스에서
검증하기로 미뤄졌다 — 이 PoC가 "packer validate는 통과하는데 실제로는 안 됨" 함정에 여러 번 데었기
때문(`project_ondemand_demo_aws_poc` 메모리). 다음 착수자는 이 스크립트를 **AC-7/8 착수 시점에** 함께
작성하고 라이브 인스턴스에서 바로 검증할 것 — 미리 써두고 나중에 믿지 말 것.

---

# 배경 — 이미 있는 것과 없는 것

온디맨드 데모(`MONO-366/379/380/389/397`)는 **평소 꺼두고 방문자가 버튼을 누르면 EC2 를 켜는** 구조로
수명주기 전 구간이 실증됐다. 그러나 기동 단위가 **두 층 모두 all-or-nothing** 이다:

1. `/start` → `ec2:StartInstances` = **VM 통째** (EC2 start/stop 의 최소 단위 — 불가피).
2. 부팅 시 systemd → `demo-boot.sh` → `demo-up.sh full` = **8개 프로젝트 96 컨테이너 전부**.

원하는 것: **항상-뜬 페이지에서 도메인(iam/wms/scm/finance/erp/ecommerce/fan/console)을 골라 켜고 끄기.**
면접관에게는 필요한 도메인만 보여 주고, 본인 테스트 시에도 부분 기동으로 편하게.

## 🔑 설계 통찰 — "도메인별"은 켜진 VM *안에서* 일어난다

EC2 start/stop 은 VM 이 최소 단위다. 따라서 "도메인별"이 "도메인별 VM"이 될 수 없다(인스턴스 N개 =
EBS·복잡도 폭증). 자연스러운 이층 구조:

```
VM on/off        = 큰 비용 스위치        ← 이미 완성 (/start·/stop + 월 예산 가드)
도메인별 up/down  = 켜진 VM 안에서         ← SSM SendCommand 로 demo-up.sh <도메인> 호출
```

결정적으로 **인스턴스에 SSM 권한이 이미 붙어 있다**(`main.tf` `aws_iam_role_policy_attachment.ec2_ssm`
= `AmazonSSMManagedInstanceCore`). 통로는 뚫려 있고, Lambda 쪽 `ssm:SendCommand` 만 열면 된다.

## 🔴 비용 현실 — 도메인 선택은 *돈 절약이 아니다*

**도메인 몇 개를 켜든 VM 과금은 동일하다**(VM 이 running 이면 인스턴스 시간 전액 청구). 도메인 선택의 실익은:

- ① **OOM 회피** — `MONO-399` AC-2 실측: `full` 이 31.5GB 중 **available ~2.8GB** (3중 크래시루프 중 관측).
  부분 기동은 여유를 크게 벌린다.
- ② **웜업 단축** — `full` 웜업 실측 ~10분(389). 서브셋이면 훨씬 빠르다.
- ③ 면접 집중 / 본인 테스트 편의.

⇒ **월 예산 가드(`MONTHLY_BUDGET_MINUTES`)는 그대로 둔다.** 실질 지출 상한은 여전히 "VM running 분"이고,
도메인 카운트와 무관하다. **도메인별 예산 로직을 새로 만들지 않는다.**

# Goal

항상-뜬 정문 페이지에서 **8개 도메인을 개별 토글**(+ "전체")할 수 있게 하고, 각 도메인의 헬스를 표시한다.
VM on/off 는 기존 컨트롤 플레인을 그대로 쓰고, 도메인별 up/down 은 **켜진 VM 안에서 SSM SendCommand →
기존 `demo-up.sh`/`demo-down.sh`** 로 수행한다. `projects.sh` 가 **도메인 의존성(DEPS)의 단일 출처**가 된다.

# Scope

## In Scope

**로컬 스크립트 계층 (🔴 AMI 에 구워짐 → 재굽기 필요)**

1. `infra/demo/projects.sh` — **DEPS 맵 추가.** iam 은 전원의 OIDC IdP(필수), console 은 federation
   소비자, wms↔ecommerce 풀필먼트 루프 등. 서브셋 선택 시 의존 도메인을 **자동 포함**한다. 도메인 맵의
   단일 출처가 여기이므로 DEPS 도 여기 소유.
2. `infra/demo/demo-up.sh` — 현재 `demo-core|full` 외에 **임의 도메인 리스트** 인자를 받는다
   (`demo-up.sh iam fan console`). DEPS 로 의존 도메인을 확장한 뒤 기존 `-p <slug>` 로직을 태운다.
   iam 포함 시 `seed-demo-domain.sh` 호출 유지.
3. `infra/demo/demo-down.sh` — **특정 도메인만** down 하는 인자를 받는다(현재는 DOWN_ORDER 전량).
   다른 도메인이 아직 떠 있으면 **iam/traefik 을 내리지 않는 가드**(마지막 소비자가 사라질 때만).
4. `infra/demo/demo-status.sh` (신규) — 도메인별 `docker compose -p <slug> ps` 를 읽어 **헬스 스냅샷 JSON**
   (`{slug: {state, healthy, total}}`)을 발행한다. 발행처 = SSM 파라미터(§ 어려운 부분 1).

**컨트롤 플레인 계층 (🟢 `terraform apply` 로 도달 — 재굽기 불필요)**

5. `infra/demo/aws/terraform/lambda/handler.py` — 신규 액션:
   - `GET /domains` — 도메인별 상태(4번의 스냅샷을 SSM 에서 읽어 반환).
   - `POST /domain/start` `{name}` — VM 이 stopped 면 먼저 start(또는 "데모를 먼저 켜세요" 안내),
     running 이면 `ssm.send_command` 로 `demo-up.sh <name>`.
   - `POST /domain/stop` `{name}` — `ssm.send_command` 로 `demo-down.sh <name>`.
   - 예산 가드 상속(도메인 start 도 예산 소진 시 429).
6. `infra/demo/aws/terraform/main.tf` — ① Lambda IAM 에 **`ssm:SendCommand`(AWS-RunShellScript 문서 +
   인스턴스 ARN 스코프) + `ssm:GetCommandInvocation`**, ② 도메인 헬스 스냅샷용 **SSM 파라미터** 추가(그
   ARN 을 Lambda GetParameter/PutParameter 스코프에 포함), ③ 신규 라우트 `GET /domains`·
   `POST /domain/start`·`POST /domain/stop`.
7. `infra/demo/aws/site/index.html` — 단일 start/stop 버튼 → **도메인 그리드**(8토글 + "전체"), 각 헬스
   배지. 기존 config.js 실패 처리·웜업 정직성 문구·`demoHost()` 대시 표기 유지.
8. `infra/demo/aws/tests/test_handler.py` — 신규 액션 단위 테스트(SSM 클라이언트 목).

## Out of Scope

- **도메인별 EC2 인스턴스 분리** — VM 은 하나. 도메인 격리는 compose `-p` 레벨(기존 로컬 모델과 동일).
- **도메인별 월 예산** — 비용은 VM running 분이 결정하므로 기존 단일 예산 가드로 충분(위 § 비용 현실).
- **`MONO-399` AC-6(kafka 1G + finance 브로커 + erp Flyway 배포용 재굽기)** — 별개 티켓. 단 **본 task 도
  재굽기가 필요**하므로(항목 1~4가 baked) 두 재굽기를 **한 번으로 합칠 수 있다**(§ 착수 순서 참조).

# 🔴 어려운 부분 (여기서 버그가 숨는다)

**1. SSM SendCommand 는 비동기 — 즉시 헬스를 못 받는다.**
페이지가 도메인 상태를 실시간 폴링하려면, 매 요청마다 SendCommand→GetCommandInvocation 을 도는 것은
느리고 취약하다. **인스턴스가 주기적으로(예: cron/systemd timer 30초) `demo-status.sh` 를 돌려 헬스
스냅샷을 SSM 파라미터에 발행**하고, Lambda `/domains` 는 그 파라미터를 **읽기만** 한다. 이 status-publish
패턴이 최대 설계 결정. (대안: 인스턴스가 Traefik 경유로 `/demo-status` 를 서빙 — 그러나 SSM 파라미터가
더 단순하고 이미 상태 저장에 쓰고 있다.)

**2. 의존성 그래프 — 서브셋이 조용히 데모를 깬다.**
이 PoC 역사에는 *"96 컨테이너 전부 healthy 인데 로그인 불가"*(iam 엣지 누락, `MONO-358`) 사건이 반복된다.
`console` 만 골라 켜면 OIDC 검증이 무너진다. **DEPS 를 `projects.sh` 가 소유**하고, `demo-up.sh` 가 선택
집합에 의존을 자동 추가해야 한다. 누락 시 "healthy 인데 안 됨" 이라는 최악의 무증상 실패.

**3. AMI 재굽기 시퀀스 — 항목 1~4는 구워진다.**
`demo-up.sh`/`demo-down.sh`/`projects.sh`/`demo-status.sh` 는 AMI 안 `/opt/monorepo-lab` 에 동결된다
(부팅 시 `git pull` 없음). 최종 도달에는 **스크립트 수정 → 재굽기(~57분) → `terraform apply`** 순서.
개발 중에는 **인스턴스에서 스크립트를 직접 고쳐** SSM 경로를 검증할 수 있으나, destroy 하면 사라진다.

# Acceptance Criteria

**AC-0 — 재사용 지점 재확인 (verify-then-act). ✅ 완료 (2026-07-29 재검증).**
착수 시 `infra/demo/` 와 `infra/demo/aws/` 를 `origin/main` 에서 다시 읽는다. 본 티켓의 파일 목록·라인
참조는 출처가 아니라 **가설**이다. SSM 인스턴스 프로파일이 여전히 붙어 있는지(`main.tf`), `demo-up.sh` 의
프로파일 인자 파싱이 바뀌지 않았는지 확인한다.

**AC-1 — `projects.sh` DEPS 맵. ✅ 완료 (#2937).**
각 도메인의 의존을 선언한다(최소: 전원→iam, console→federation 소비 도메인, wms↔ecommerce 루프).
`resolve_deps <slug...>` 가 선택 집합의 전이적 폐포를 **기동 순서(iam 먼저, console 마지막)** 로 반환한다.
단위 가드: `resolve_deps console` 이 `iam` 을 포함하는지.

**AC-2 — `demo-up.sh` / `demo-down.sh` 도메인 리스트 인자. ✅ 완료 (#2937).**
`demo-up.sh iam fan console` 이 DEPS 확장 후 해당 프로젝트만 `-p` 로 띄운다. `demo-down.sh console` 이
console 만 내리되, **다른 떠 있는 도메인이 iam 을 소비 중이면 iam·traefik 은 유지**한다. 기존
`demo-core|full` 인자는 **하위 호환**으로 계속 동작한다.

**AC-3 — `demo-status.sh` 헬스 스냅샷. ✅ 스크립트 완료 (#2937) — 단 SSM 파라미터 발행(systemd 타이머)은 미구현, AC-7/8 로 이월.**
`docker compose -p <slug> ps --format json` 을 집계해 도메인별 `{state, healthy, total}` JSON 을
표준출력 + 지정 SSM 파라미터에 발행한다. 떠 있지 않은 도메인은 `state=down` 으로 명시(누락 아님).

**AC-4 — Lambda 신규 액션 + IAM. ✅ 완료 (#2940).**
`GET /domains` 가 SSM 스냅샷을 반환. `POST /domain/start`·`/domain/stop` 이 running VM 에 SendCommand 로
`demo-up.sh`/`demo-down.sh <name>` 을 실행. VM stopped 시 `/domain/start` 는 먼저 VM 을 켜거나(선택)
명확한 안내를 반환. **예산 소진 시 도메인 start 도 429.** IAM 은 `ssm:SendCommand`(문서·인스턴스 ARN
스코프)+`GetCommandInvocation`+스냅샷 파라미터 R/W 만 추가(최소 권한).

**AC-5 — 페이지 도메인 그리드. ✅ 완료 (#2940).**
`site/index.html` 이 8개 도메인 토글 + "전체"를 렌더하고 각 헬스 배지를 `/domains` 폴링으로 갱신한다.
기존 안전장치 유지: config.js 미로드 시 크게 실패, 웜업 정직 문구(도메인별 예상 시간), `demoHost()` 대시
표기(점 표기 404 함정), `ok/status` 확인(429 무시 금지).

**AC-6 — 테스트. ✅ 완료 (#2937/#2940).**
`test_handler.py` 가 신규 3액션을 커버(SendCommand 목, 예산 소진 시 도메인 start 429, VM stopped 분기,
24건 통과). 로컬 도커 왕복은 스텁 통합 테스트(#2937) + CI "Demo wrapper smoke"(Linux, 이 저장소의 권위
있는 실행 환경)가 두 PR 모두 SUCCESS로 커버. **로컬 Windows 는 IT 권위 아님** — 스크립트 로직은 로컬,
AWS 경로는 인스턴스 실증.

**AC-7 — AWS 실증 (1회 기동). ⏳ 잔존 — 여기서부터 착수.**
`terraform apply` → 페이지에서 도메인 토글 → SSM 경로로 부분 기동/정지가 실제로 동작하고 헬스가
페이지에 반영되는지 브라우저(Playwright/headless fetch)로 실증. **끝나면 `terraform destroy` 즉시 복귀.**
⚠️ `terraform apply`/`destroy`/`packer build` 는 **사용자 승인 필요**.

**🔴 AC-7 착수 전 선행 작업 — 인스턴스 헬스 발행자. ✅ 작성 완료 (2026-08-17, 위 절 참조).**
`demo-status-publish.sh` + `demo-status.service`/`.timer` + `demo-ami.pkr.hcl` 배선 + 가드 (z) 가 저장소에
들어왔다. **그러나 이 파일들은 한 번도 실행된 적이 없다** — 정적 검사와 bite 만 통과했다.
AC-7 실증이 곧 이 스크립트의 **첫 실검증**이므로 **써뒀다고 믿지 말 것.** 라이브 인스턴스에서 이 순서로
그 자리에서 확인한다:

1. `systemctl status demo-status.timer` — active(waiting), 다음 발화 시각이 30초 안쪽인가
2. `systemctl status demo-status.service` + `journalctl -u demo-status` — `[status-publish] … ← N bytes`
3. `aws ssm get-parameter --name /portfolio-demo/domains-health` — **값이 `{}` 가 아니고**,
   `LastModifiedDate` 가 **30초 이내로 계속 갱신되는가**(한 번 찍힌 값과 갱신되는 값은 다른 명제다)
4. 페이지의 8개 배지가 그 값을 반영하는가 — 그리고 **도메인 하나를 내렸을 때 배지가 따라 내려가는가**
   (올라가는 방향만 보면 한 방향으로만 고장 난 게이트를 못 잡는다)
5. `awscli` 가 실제로 AMI 에 들어왔는가 — `command -v aws`. 없으면 발행자가 매 30초 죽고 증상은
   "배지가 안 뜬다" 라서 페이지 결함처럼 보인다

**AC-8 — 재굽기 (측정·실증이 끝난 뒤, MONO-399 와 합침 가능). ⏳ 잔존.**
항목 1~4가 baked 이므로 최종 도달에 재굽기 필요. `MONO-399` AC-6 이 이미 재굽기를 예약했으므로 **가능하면
한 번의 bake 로 합친다**(ERP-BE-035 머지 후). 새 AMI 로 인스턴스를 띄운 뒤 **인스턴스 안에서 직접**
`git -C /opt/monorepo-lab log -1` 과 `demo-up.sh` 의 도메인 인자 동작을 런타임 확인 — *구운 것을 믿지 않는다*.

# Related Specs

- `infra/demo/aws/README.md` — 재현 절차 (SoT)
- `infra/demo/projects.sh` · `demo-up.sh` · `demo-down.sh` · `demo-boot.sh` — 로컬 오케스트레이션
- `infra/demo/aws/terraform/main.tf` · `lambda/handler.py` · `site/index.html` — 컨트롤 플레인
- `TEMPLATE.md § Local Network Convention` — 호스트명 라우팅(Traefik) 규약
- 선행/자매: `TASK-MONO-399`(재굽기 공유), `TASK-MONO-366/389`(데모·정문 토대)

# Related Contracts

없음 (인프라 전용 — HTTP/이벤트 도메인 계약 무변경. 신규 API 라우트는 데모 컨트롤 플레인 내부 계약).

# Edge Cases

- **console 만 선택** — DEPS 로 iam 자동 포함되지 않으면 "healthy 인데 로그인 불가"(358 재현). AC-1 가드.
- **VM stopped 상태에서 도메인 start** — SendCommand 대상이 없다. 먼저 VM start 하거나 명확히 안내(AC-4).
- **SSM SendCommand 비동기** — 완료 전에 페이지가 폴링하면 옛 헬스. 스냅샷 발행 주기·표시 지연 정직하게(§1).
- **부분 down 후 traefik/iam** — 마지막 소비자 판정 오류 시 남은 도메인 라우팅이 죽는다. AC-2 가드.
- **재굽기 안 함** — 항목 1~4 수정이 데모에 도달 안 함. `MONO-399` AC-6 이 가르친 "커밋이 main 에 있다 ≠
  실행된다". AC-8.
- **EIP 없음** — 재시작마다 공인 IP 변경, 도메인은 매 부팅 IMDSv2 파생. 페이지 `demoHost()` 는 대시 표기.
- **`docker compose ps --format json`** — compose 버전에 따라 출력 형태 차이. as-baked 버전에서 확인(가정 금지).

# Failure Scenarios

- **의존성 누락으로 무증상 데모 붕괴** — 최악. 컨테이너 전부 healthy, 페이지 초록, 그런데 로그인·조회 불가.
  `projects.sh` DEPS 가 SoT 이고 `demo-up.sh` 가 자동 확장한다. 형제 서비스 배선을 대조한다(straggler 규칙).
- **SSM 권한 과다 부여** — `ssm:*` 나 `Resource="*"` 는 최소권한 위반. 문서·인스턴스 ARN 으로 스코프.
- **`/domain/start` 가 예산 가드를 우회** — VM 을 도메인 start 가 켜면 예산 소진 후에도 running 이 될 수 있다.
  도메인 start 도 예산 검사 상속(AC-4). 안 하면 `/start` 인증 부재의 지출 구멍이 재현된다.
- **스냅샷 손상값을 "전부 healthy"로 읽음** — fail-open 금지. 파싱 실패/미존재 스냅샷은 `unknown`/`down` 으로.
- **재굽기를 먼저** — MONO-399 AC-1~4 재현 대상(as-baked AMI)을 없앤다. 순서: 측정·실증 먼저, 재굽기 나중.
- **로컬 Windows 초록을 AWS 권위로 착각** — Testcontainers/도커 로컬은 권위 아님. AWS 인스턴스 실증이 심판.

# Notes

- 분석 = **Opus 4.8** / 구현 권장 = **Opus** — bash(의존성 그래프) + python(비동기 status) + terraform(IAM/
  라우트) + FE(그리드) 4계층 크로스커팅 + AWS 실증 꼬리. 단순 fix 아님.
- **착수 순서 권장**: (a) 항목 1~4 로컬 스크립트 + AC-1~3 가드 → 로컬 왕복 실증 → (b) 항목 5~8 컨트롤
  플레인 → (c) `MONO-399` AC-6 와 **재굽기 합침**(ERP-BE-035 머지 후) → 단일 bake 로 AC-8 + 399 AC-6 동시 배포.
- **`terraform apply`/`destroy`/`packer build` 는 사용자 승인 필요 동작.**
- 관련 메모리: `project_ondemand_demo_aws_poc`(배포 이층·재굽기), `env_compose_include_dup_key_silent_merge`
  (`-p` 분리 근거), `project_enforcement_straggler_sibling_parity`(의존성 배선 대조).
