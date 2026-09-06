# Task ID

TASK-MONO-630

# Title

🙋 **AMI 재굽기는 소유자 몫인데 이미 두 번 전달됐다** — 세 번째 집을 짓고, 그 김에 「끝난 일이 대기 중으로 적혀 있는」 기록 셋을 고친다

# Status

review

# Owner

monorepo

# Task Tags

- infra
- demo
- owner-gated
- record-drift

---

# 🙋 OWNER GATE (read first)

**이 티켓의 본체는 에이전트가 못 한다.** `bash infra/demo/aws/packer/bake.sh` 는
~55분이 걸리고 **과금**되며, 뒤따르는 `terraform apply` 는 EC2 를 **교체**한다.
소유자 승인·실행 사안이다.

🔵 **그럼에도 티켓인 이유**: 이 의무는 지금까지 **두 번 전달됐고 두 번 다 `done/` 안에서
전달됐다.**

| 홉 | 어디서 | 무슨 일이 있었나 |
|---|---|---|
| ① | `TASK-MONO-627` § AC-7 이 ⚪ 로 남김 | `done/` 으로 갔다 — **frozen, 다시 안 읽힌다** |
| ② | `TASK-MONO-628` § 「남은 것」이 받아 적음 | 그 티켓도 이제 `done/` 이다 |
| ③ | **여기** | `ready/` — **읽히는 큐** |

🔴 **산문에는 게이트가 없다.** ①②는 둘 다 「적어 뒀으니 됐다」였고, 그 사이 재굽기는
일어나지 않았다. `ready/` 에 있는 것만이 다음 세션의 큐 스캔에 걸린다.

🔴 **에이전트가 이 티켓을 「구현」하려 들면 안 된다.** AC-0 을 돌려 상태만 재고,
소유자 행위가 아직이면 **STOP** 이 올바른 결과다(no-op). 재굽기가 끝난 뒤에야 AC-1~AC-3 이
열린다.

---

# Goal

1. **소유자 행위를 읽히는 큐에 둔다** — 재굽기 · `terraform apply` · `deployed-ami.env` 커밋.
2. **재굽기 뒤 기동 창에서 눈으로 확인할 것 3개**를 함께 들고 있는다(627→628 이 넘긴 목록).
3. 🔴 **끝난 일이 「대기 중」으로 적힌 기록 셋을 고친다** — `ADR-MONO-067` 단계 1 의
   `terraform apply` 는 **이미 실행됐다**(§ 실측). 셋 다 아직 대기 중이라고 말한다.

# Scope

**In**

- 소유자 절차의 **정확한 순서**와 각 단계의 판정(이 파일 § AC-1~AC-3)
- 재굽기 뒤 기동 창 확인 항목 3개
- `ADR-MONO-067` 단계 1 의 상태 정정 — 기록 셋(§ AC-4)

**Out**

- 🔴 **재굽기 자체의 실행.** 소유자 몫이고 이 티켓은 그것을 **대행하지 않는다**.
- `check-ami-generation.sh` · `check-launcher-fresh.sh` 의 수정. 둘 다 `TASK-MONO-628`/`602`
  에서 닫혔고 **지금 옳게 동작한다** — `rc=1` 은 결함이 아니라 판정이다.
- 🔴 **`ADR-MONO-067` 의 남은 단계(2·3·4)**. 별개 축이고 이 티켓은 **단계 1 의 기록만** 만진다.

# 🔍 실측 (2026-09-06 UTC — 기안 시점. 🔴 착수 시 AC-0 이 다시 잰다)

## ① 어긋남은 실재한다 — `check-ami-generation.sh` **rc=1 · 4건**

```
핀 = ami-0fc6e4b7ac34c4ef5 · 구운 커밋 afebc937158f · 출처 = operator-record
서빙 세대 = ab586c35c

✖ 어긋남 4건
  · infra/demo/aws/site/index.html      (내용 다름)
  · infra/demo/demo-up.sh               (내용 다름)
  · infra/demo/projects.sh              (내용 다름)
  · infra/demo/console-vercel.override.yml (구운 세대에 없다)
```

🔵 **이 빨강에 「예상된 것」이라는 면죄부를 주지 마라** — 실제 상태다. 그리고
`check-launcher-fresh.sh` 는 **rc=0**(서빙 바이트 = `origin/main`, 커밋 `77725f050`)이므로
어긋남은 **론처가 아니라 AMI 쪽**이다.

## ② 🔴🔴 `ADR-MONO-067` 단계 1 의 `terraform apply` 는 **이미 끝났다**

세 곳이 「소유자 대기」라고 적고 있는데(§ AC-4), **state 를 직접 읽으니 아니었다**:

```
infra/demo/aws/terraform/terraform.tfstate         (serial 94)  managed 25개 — cloudfront/s3 0건
infra/demo/aws/terraform/terraform.tfstate.backup  (serial 89)               — cloudfront/s3 0건
versions.tf 에 backend 블록 없음 ⇒ 이 파일이 유일한 state
git log -S aws_cloudfront_distribution -- .../main.tf
  → df6eb9b04 (추가, MONO-389) · b679c5396 (삭제, MONO-579)
```

⇒ 이 스택이 한때 CloudFront/S3 를 관리했고 **지금 state 에 없다** ⇒ 파괴적 apply 는 실행됐다.

🔴 **먼저 시도한 판별은 버렸다 — 판별자가 대조군에서 깨졌기 때문이다.** CloudFront 주소가
사는지 DNS/HTTP 로 재려 했더니 **대상 · 날조한 이름 · 임의 이름 셋이 같은 응답**을 냈다
(이 호스트는 DNS64/NAT64). 「응답이 질의와 무관하게 동일」은 **조회기가 모를 때 그럴듯한
답을 내는 지문**이다. ⇒ 대리지표를 버리고 **state 라는 1차 자료**로 갔다.

🔵 **단서**: 로컬 state 스냅샷(mtime `2026-09-04T12:32`)을 읽은 것이고 `terraform plan` 은
안 돌렸다(소유자 축). 원격 backend 가 없으므로 terraform 자신이 보는 것과 같은 파일이다.

## ③ 인스턴스는 **stopped** 다

`TASK-MONO-628` AC-0 실측: `i-022ca131b94ac2d1a` · **stopped** ·
`ImageId=ami-0fc6e4b7ac34c4ef5`.

🔴 **state 파일의 `instance_state` 를 믿지 마라** — 그 필드는 마지막 refresh 시점의 값이라
`running` 으로 적혀 있다. **09-04 스냅샷이고 09-06 실측이 stopped 다.** 기동 여부는
`aws ec2 describe-instances` 로 그때 재라.

# Acceptance Criteria

- [x] **AC-0 (착수 시 실측 — 🔴 상속 금지 · verify-then-act)**

  이 순서로 확인한다. **①이 「아직」이면 STOP** — 아무것도 하지 말고 티켓을 그대로 둔다.

  | # | 확인 | 방법 | 「아니오」면 |
  |---|---|---|---|
  | ① | **재굽기가 일어났는가** | `bash infra/demo/aws/check-ami-generation.sh` | `rc=1` = 아직 → **STOP(no-op)**. AC-4 만 진행 가능 |
  | ② | 🔴 **어긋남 목록이 그대로인가** | 같은 출력의 파일 목록 | 위 § ① 의 4건을 **상속하지 마라.** 늘었으면 늘어난 대로 적는다 |
  | ③ | 🔴 **단계 1 상태를 다시 잰다** | state 의 cloudfront/s3 리소스 수 | 위 § ② 를 상속하지 마라 — 그 사이 누가 apply 했을 수 있다 |
  | ④ | **인스턴스 상태** | `aws ec2 describe-instances` (state 파일 **아님**) | § ③ 의 함정 |

  🔴 ①이 `rc=0` 이면 **왜 초록인지**(누가 언제 구웠는지)를 적는다 — 「어긋남이 없다」와
  「어긋남을 잰다」는 다른 명제다.

- [x] **AC-1 — 🙋 소유자: 재굽기.** `bash infra/demo/aws/packer/bake.sh` (~55분 · 과금).
  🔴 **맨 `packer build` 는 거절된다** — `repo_commit` 이 기본값 없는 필수 변수다
  (`TASK-MONO-628` 이 그렇게 만들었다: 굽는 커밋을 남기게 하려고).
  🔵 굽기 뒤 `infra/demo/aws/deployed-ami.env` 는 **스크립트가 갱신**하니 커밋만 하면 된다.

- [x] **AC-2 — 🙋 소유자: `terraform.tfvars` 의 `ami_id` 갱신 → `terraform apply`.**

  🔴 **plan 에 EC2 `destroy` + `create` 가 뜬다. 그것이 정상이다** — `ami` 는 force-new
  속성이고 `terraform.tfvars` 자신이 *"ami_id 변경은 인스턴스를 **교체**한다"* 라고 적고 있다.
  🔴 **`TASK-MONO-579` AC-5 의 «EC2 가 destroy 로 뜨면 즉시 중단» 을 여기에 적용하지 마라** —
  그 문장은 *"**이 변경의** apply 는 파괴적이다"* 로 시작하는, **단계 1 apply 에 한정된**
  규칙이다. 이 티켓의 apply 에서 EC2 교체는 **요구된 것**이다.
  🔵 그 대신 이 apply 에서 멈춰야 할 지문: **Lambda / API Gateway / SSM 파라미터가
  destroy 로 뜨는 것**. 그것들은 `ami_id` 와 무관하다.

  🔴 **순서**: 매니페스트/핀 커밋을 `apply` **앞**에. `apply` 는 「핀 갱신 + 기동」이고,
  뒤로 미루면 서빙 중인 것과 저장소가 어긋난 창이 생긴다.

- [x] **AC-3 — 재굽기 뒤 기동 창에서 눈으로 확인할 것 3개** (627 → 628 → 여기로 온 목록)

  | # | 확인할 것 | 왜 |
  |---|---|---|
  | 1 | 부팅 로그가 **`✔ HTTP 표면 1/1: iam/login=200`** 을 찍는가 | 🔴 **옛 지문을 기다리면 창이 영원히 안 열린다.** 창 #3 까지 `console=307 web.fan-platform=307`(2/2) → 그다음 `console=307`(1/1) → **재굽기 뒤에야** `iam/login=200`(1/1). 🔴 재굽기 **전** 에 `iam/login` 을 기다리면 안 나온다 |
  | 2 | `check-suppressed-containers.sh` 가 `console-web` 을 **도는 컨테이너 0** 으로 판정하는가 | 🔴 `profiles:` 로 가려진 컨테이너는 `down --remove-orphans` 가 **안 지우고** `restart=unless-stopped` 로 되살아난다(`TASK-MONO-617` 실측). 렌더는 그동안 계속 초록이다 |
  | 3 | `console.hubwang.com` 이 여전히 살아 있는가 (**음성 대조군과 함께**) | 🔴 데모 사본을 껐으므로 Vercel 판이 **유일한 콘솔**이다. 죽어 있으면 방문자는 어느 쪽에서도 못 본다 |

  🔴 **1번은 이 축의 첫 실사용례다** — 그 지문이 안 나오면 그것이 바로
  「론처 세대 ≠ `demo-up.sh` 세대」의 증상이고, `check-ami-generation.sh` 가 찍는 것과
  같은 것을 부팅 로그가 말하는 것이다.

  🔵 **기동은 예산을 쓴다.** 이 확인들은 **다음 기동 창에 얹어라** — 이것 때문에 따로
  기동하지 마라. AC-2 의 `apply` 가 어차피 기동을 만든다.

- [x] **AC-4 — 🔴 기록 정정: 「단계 1 대기 중」이라고 말하는 세 곳** (🔵 **AC-1~AC-3 과 독립.
  소유자를 안 기다리고 지금 할 수 있다**)

  | # | 어디 | 지금 뭐라고 적혀 있나 | 처방 |
  |---|---|---|---|
  | 1 | `tasks/done/TASK-MONO-628-….md` 496~498행 | *"3. `ADR-MONO-067` 단계 1 의 `terraform apply`(CloudFront 이중 배포 정리) — 별개 축, 무관"* | 🔴 **`done/` 은 frozen** ⇒ 고칠 수 없다. **여기(이 티켓)가 정정본이다.** 그것이 이 칸이 존재하는 이유 |
  | 2 | 개인 메모리 `project_vercel_surface_migration_adr067` | *"🔴 `terraform apply` 는 소유자 대기(파괴적)"* | 실측으로 갱신 |
  | 3 | `infra/demo/aws/README.md` 배포 층 표 | 🔴 **열어서 확인하라** — 단계 1 이후 상태를 반영하는지 안 재고 고치지 마라 | 어긋나면 고치고, 맞으면 **「맞았다」고 적는다** |

  🔴 **1번이 이 AC 의 요점이다**: `done/` 이 틀린 것을 말하고 있는데 고칠 수 없다면,
  **읽히는 자리에 정정본을 두는 것**이 유일한 처방이다. 「메모리에만 적은 정정은
  landed 가 아니다」(`TASK-MONO-578` 의 교훈)의 반대 방향 — **저장소에만 적고 메모리를
  안 고치면 다음 세션이 또 틀린다.** 셋 다 해야 한다.

- [x] **AC-5 — 닫을 때: 이 티켓도 남의 의무를 들고 있는지 확인한다.**
  🔴 이 목록은 **세 번 전달됐다.** 닫기 전에 「지금 이 티켓에만 있는 의무가 있는가」를
  묻고, 있으면 **`ready/` 에 집을 지어 주고** 닫는다. 없으면 **없다고 적는다.**

# Related Specs

- [`docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md`](../../docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md)
- `infra/demo/aws/README.md` — 배포 층 표
- `CLAUDE.md` § Task Rules — 「`done/` 로 닫기 전에 남의 의무부터 집을 줘라」

# Related Contracts

없음.

# Related Tasks

- `TASK-MONO-628` — **직전 홉.** 판정자(`check-ami-generation.sh`)를 만들었고 의무를 넘겼다
- `TASK-MONO-627` — **최초 홉.** § AC-7 이 확인 3개를 ⚪ 로 남겼다
- `TASK-MONO-579` — 단계 1(론처의 집을 Vercel 하나로). 🔴 그 AC-5 의 중단 규칙은
  **그 티켓의 apply 한정**이다 — AC-2 참조
- `TASK-MONO-581` — *"one ami rebake settles five pending verifications"*. 같은 부류의 선례
- `TASK-MONO-617` — `profiles:` 컨테이너가 되살아나는 사슬(AC-3 의 2번)
- `TASK-MONO-602` — `check-launcher-fresh.sh`(서빙 론처 축). 형제

# Edge Cases

① 🔴 **재굽기가 끝나면 `check-ami-generation.sh` 가 초록이 되고, 그러면 이 티켓의 § 실측이
   공허해 보인다.** 그것이 정상이다 — 이 티켓의 명제는 「지금 빨갛다」가 아니라
   **빨강을 닫는 행위가 소유자 몫인데 그 집이 없었다** 는 것이다.

② 🔴 **소유자가 재굽기를 하고 `deployed-ami.env` 커밋을 잊으면** 판정자는 **여전히 빨갛다**
   (핀 파일이 옛 커밋을 가리키므로). 증상은 「재굽기가 안 됐다」와 **구별되지 않는다** —
   AC-0 ①의 「왜 초록/빨강인지 적는다」가 그 구별을 강제한다.

③ 🔴 **AC-4 만 하고 닫으면 안 된다.** 그것은 기록 정정이고, 이 티켓의 본체는 AC-1~AC-3 이다.
   AC-4 는 소유자를 기다리지 않아도 되므로 먼저 랜딩할 수 있지만, **랜딩했다고 티켓이
   닫히지 않는다.**

④ 🔴 **인스턴스가 `running` 인 채로 apply 하면** 교체가 기동 중인 데모를 죽인다. AC-0 ④ 가
   그것을 먼저 재는 이유다. 🔵 `stopped` 에서 `ami_id` 를 바꾸는 것이 정상 경로다.

# Failure Scenarios

① **에이전트가 「구현」하려 든다** → AC-1 은 과금·55분이고 AC-2 는 EC2 를 교체한다.
   🔴 OWNER GATE 와 AC-0 이 그것을 막는다. **STOP 이 올바른 결과다.**

② **`done/` 안에서 또 전달한다** → 네 번째 홉. 이 티켓 자신이 그 실패에서 태어났다.
   AC-5 가 닫을 때 그것을 묻는다.

③ **AC-4 를 메모리에만 적는다** → 다음 세션의 저장소 독자가 또 「단계 1 대기 중」을 읽는다.
   반대로 저장소에만 적으면 다음 세션의 **메모리 독자**가 틀린다. **셋 다** 여야 한다.

④ **재굽기 뒤 옛 부팅 지문을 기다린다** → 창이 안 열린다. AC-3 의 1번이 정확히 그것을
   경고한다(`console=307` ↔ `iam/login=200` 을 반대로 기다리는 것).

# Definition of Done

- [x] AC-0 을 **착수 시점에** 돌렸고 그 결과(초록/빨강 + 왜)가 적혀 있다
- [x] 🙋 재굽기 · `apply` · `deployed-ami.env` 커밋이 **끝났고**, `check-ami-generation.sh`
  가 **rc=0** 이다 (또는 못 했으면 **왜 못 했는지**가 적혀 있다)
- [x] AC-3 의 3개가 **다음 기동 창에서** 확인됐다(⚪ 로 남기면 **누가 언제** 볼지 적는다)
- [x] AC-4 의 세 자리가 **전부** 정정됐다(저장소 · 메모리 · README)
- [x] AC-5 — 이 티켓이 들고 있던 남의 의무가 **없거나, 집을 받았다**

---

분석=Opus 5 / 구현 권장=Sonnet (AC-4 기록 정정은 단순 편집이다. 🔴 단, **AC-0 의
verify-then-act 와 AC-2 의 「무엇이 destroy 로 뜨면 멈추나」는 판단이 필요하고, 본체인
AC-1~AC-3 은 애초에 소유자 몫이라 모델 선택과 무관하다.**)

---

---

# 구현 (2026-09-06 UTC) — **소유자가 재굽기·apply 를 실행했고, 모든 AC 가 닫혔다**

## AC-0 — 착수 시 실측 (상속 안 함)

| # | 확인 | 결과 |
|---|---|---|
| ① | 재굽기가 일어났는가 | **아니오** — `check-ami-generation.sh` **rc=1** ⇒ AC-1~3 진행 |
| ② | 어긋남 목록이 그대로인가 | **4건 그대로**. 🔵 서빙 세대만 `ab586c35c` → `3bc182ecd` 로 움직였고 구운 세대 `afebc9371` 는 불변 |
| ③ | 단계 1 상태 | **끝난 상태 유지** — state serial 94, cloudfront/s3 **0건** |
| ④ | 인스턴스 상태 (state 파일 **아님**) | `aws ec2 describe-instances` → `i-022ca131b94ac2d1a` **stopped** ⇒ `ami_id` 교체에 안전 |

전제 점검(전부 비파괴): `aws` ✔(`portfolio-demo-deployer`) · `packer` ✔ · `terraform` ✔ ·
`bake.sh --dry-run` **rc=0**.

## AC-1 — 재굽기 ✅

```
bash infra/demo/aws/packer/bake.sh        # 51분 27초, rc=0
→ ami-029c40fb18c63816b (portfolio-demo-1788718249)
```

🔴 **스크립트의 자기 보고를 그대로 믿지 않고 AWS 에 따로 물었다**:

```
aws ec2 describe-images --owners self --image-ids ami-029c40fb18c63816b
→ available · 2026-09-06T18:23:58Z · Tags[RepoCommit]=3bc182ecd4e8c7f36647ff1c2e7019033e597d96
```

🔵 **`REPO_COMMIT_PROVENANCE` 가 `operator-record` → `ami-tag` 로 올라갔다.** 8차까지 「구운
커밋」은 사람이 적은 값이었다. 9차부터는 packer 2단계가 클론 직후 `git rev-parse HEAD` 를
대조해 다르면 빌드를 죽이고, **통과한 값만** 태그로 발행된다 ⇒ **이미지가 스스로 한 말**이다.
이 티켓이 닫는 가장 큰 축이 이것이다 — 판정의 **출처**가 바뀌었다.

## AC-2 — `tfvars` + `terraform apply` ✅

`terraform.tfvars` 에 9차 블록을 추가하고 핀을 옮겼다(gitignored 로컬 파일).
🔴 그 파일의 8차 블록이 *"이 AMI 로 갈면 부팅 지문이 `1/1 (console)`"* 이라고 적고 있어서,
**그건 8차의 지문이고 9차는 `iam/login=200`** 이라는 것을 새 블록에 명시했다 — 안 적으면
apply 하는 사람이 열어 보고 **옛 지문을 기다린다**(이 티켓 AC-3 이 경고한 바로 그것).

**`terraform plan`(읽기 전용)으로 먼저 쟀다:**

```
Plan: 1 to add, 2 to change, 1 to destroy.
  # aws_instance.demo          must be replaced   ← ami 가 forces replacement
  # aws_iam_role_policy.lambda will be updated in-place
  # aws_lambda_function.control will be updated in-place
```

🔵 **계획에 등장하는 리소스는 이 셋이 전부**다. `aws_apigatewayv2_*` · `aws_ssm_parameter*` 는
**0건** ⇒ 이 티켓이 정한 중단 지문에 걸리는 것이 없다.
🔵 **in-place 둘은 왜 바뀌는지 유추하지 않고 `main.tf` 를 열었다** — 둘 다 **교체의 하류**다:

```
main.tf:243  "arn:aws:ec2:*:…:instance/${aws_instance.demo.id}"   ← IAM 정책이 인스턴스 id 를 품는다
main.tf:268  INSTANCE_ID = aws_instance.demo.id                    ← Lambda env
```

새 인스턴스 id 는 apply 전에 모르므로 둘 다 `(known after apply)` 다. 독립 변경이 아니다.

**소유자 실행 결과**: `Apply complete! Resources: 1 added, 2 changed, 1 destroyed.` — 계획과 일치.
새 인스턴스 `i-0394b45b62cdd1fc6` · **running** · `ami-029c40fb18c63816b` · 19:29:17Z.

## 🎯 판정 — `check-ami-generation.sh --with-aws` **rc=0** (처음)

```
구운 세대 3bc182ecd  vs  서빙 세대 3bc182ecd     ✔ 계약 파일 6개 전부 일치
AMI 태그 RepoCommit = 3bc182ecd4e8
인스턴스가 물고 있는 AMI = ami-029c40fb18c63816b  ✔ 핀과 같음
```

🔴 **plain 모드와 `--with-aws` 가 갈리는 창을 실제로 관측했다** — 굽기 직후·apply 전에
plain 은 **rc=0**(핀 vs 저장소만 본다), `--with-aws` 는 **rc=1**(인스턴스가 옛 AMI).
**CI 는 AWS 자격이 없어 plain 으로 돈다** ⇒ 그 창에 핀을 커밋했으면 CI 는 초록인데 데모
호스트는 옛 세대를 서빙했을 것이다. 선언 파일과 런타임 상태가 갈리는 그 모양이다.
🔵 그래서 **핀 커밋을 apply 뒤로 미뤘다**. `bake.sh` 의 단계 순서(1 tfvars → 2 apply →
3 핀 커밋 → 4 판정)도 같은 말을 하고 있었다.

## AC-3 — 기동 창 확인 3개 ✅ (627 → 628 → 630, 세 홉 만에 실제로 관측)

SSM 이 **Online** 이 됐다 — 인스턴스가 `stopped` 일 때는 관리 대상 0건이라 없던 경로다.

| # | 확인 | 결과 |
|---|---|---|
| 1 | 부팅 로그 지문 | ✅ `Sep 06 19:41:42 … [demo] ✔ HTTP 표면 1/1: iam/login=200` — **새 세대의 지문**이 실제로 찍혔다(옛 `console=307` 이 아니라) |
| 2 | 억제 컨테이너 | ✅ `[suppressed] OK — 억제 대상 3개(검사 3건) 모두 컨테이너가 존재하지 않습니다.` |
| 3 | `console.hubwang.com` 생존 | ✅ 307 → `/dashboards/overview` → **200** at `/login?redirect=…` · Server: Vercel |

`demo-stack.service` = `active`, 기동 시작 19:29:17Z → 판정 19:41:42Z (약 12분).

🔴 **③ 을 `code==200` 하나로 판정하지 않았다** — 세션 없는 요청이 로그인 페이지를 200 으로
돌려주므로 그것만 보면 fail-closed 를 PASS 로 읽는다. 근거 사슬로 판정했다:
양성 대조군 `hubwang.com`=200(34,812B) · **음성 대조군 `nosuchscreen.hubwang.com`=404** ·
대상은 307→로그인 200 ⇒ **죽은 배포(404)와 구별된다.**

🔴🔴 **AC-3 ②에서 예상과 다른 수를 봤고, 그게 옳은 값이다** — `tfvars` 의 8차 블록은
*"억제 대상은 **2개**여야 한다(`ecommerce/web-store` + `fan/fan-platform-web`). 1 이면 이 핀이
안 먹은 것"* 이라고 적는다. 관측은 **3개**다. 늘어난 하나가 `console-vercel.override.yml` 이고,
그것이 바로 AC-0 ②의 어긋남 4건 중 *"구운 세대에 없다"* 였던 파일이다. ⇒ **3 은 9차가 콘솔
억제를 실제로 굽었다는 증거**다. 🔵 그 「2개」 문구는 8차 블록의 것이라 그대로 두었다(그 세대에
대해서는 참이다) — 9차 블록에 새 기대값을 적었다.

## AC-4 — 기록 정정 3곳 ✅

| # | 어디 | 결과 |
|---|---|---|
| 1 | `done/TASK-MONO-628` 496~498행 | 🔴 frozen 이라 못 고친다 — **이 티켓이 정정본**이고, 628 의 CORRECTION 이 여기를 가리킨다 |
| 2 | 개인 메모리 `project_vercel_surface_migration_adr067` | ✅ 실측 근거(state serial 94/89 · backend 없음 · `git log -S`)와 함께 갱신 |
| 3 | `infra/demo/aws/README.md` 배포 층 표 | 🔴 **열어 보니 README 가 자기 자신과 모순이었다** — 아래 |

🔴🔴 **③ 에서 진짜 결함을 찾았다.** README 는 한 곳(54~56행 인용 블록)에서 *"`TASK-MONO-579`
가 그 사본을 폐기했다"* 고 **과거형으로** 적으면서, **표 두 행에서는 현재형으로** 이렇게 적고
있었다:

- 92행 *"`terraform apply` 는 **CloudFront 사본**도 갱신하지만…"*
- 137행 *"`terraform apply` 는 CloudFront **사본**을 갱신할 뿐이고…"*

⇒ **579 가 산문은 고치고 표는 안 고쳤다.** 같은 사실이 두 절에 있으면 한쪽만 고쳐진다는 그
부류다. 실측으로 확정했다: `main.tf` 에 `aws_cloudfront*`·`aws_s3_object` **grep 0건**, state 에도
**0건**. 두 행을 고치고 93행의 `aws_s3_object` 언급도 뺐다(존재하지 않는 리소스였다).

## AC-5 — 이 티켓이 들고 있는 남의 의무: **없다**

- 재굽기·apply·핀 커밋·기동 창 확인 3개 — **전부 이 창에서 닫혔다.** 넘길 것이 없다.
- 🔵 남는 것은 **의무가 아니라 상태**다: 8차 AMI `ami-0fc6e4b7ac34c4ef5` 를 롤백 경로로
  유지 중이고, 그 정리 시점은 `tfvars` 의 기존 관례(다음 세대가 부팅 몇 번 검증된 뒤)를 따른다 —
  **열린 티켓이 요구하는 작업이 아니다.**
- ⚠️ 예산: 이 창에서 기동했으므로 사용량이 올라간다(판정 시점 346/600분). idle 정지 20분 ·
  최대 180분이 걸려 있다.

## 남은 것

없다. `check-ami-generation.sh` 는 이제 **plain·`--with-aws` 둘 다 rc=0** 이고, 다음 nightly 의
`ami-generation-watch` 는 이 핀을 기준으로 초록을 봐야 한다.
