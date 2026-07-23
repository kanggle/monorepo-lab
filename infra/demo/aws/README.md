# infra/demo/aws — 온디맨드 포트폴리오 데모 호스트 (TASK-MONO-366)

방문자가 **Start Demo** 를 누르면 EC2 가 깨어나 통합 데모(8 프로젝트 / 96 컨테이너)를 올리고, 유휴해지면 스스로 꺼진다. **scale-to-zero** — 아무도 안 쓰면 컴퓨트 비용이 0 이다.

> 프로젝트 수의 출처는 `infra/demo/projects.sh` 의 `FULL` 배열이다(iam · wms · scm · finance · erp · ecommerce · fan · console = **8**). 여기에 숫자를 다시 적지 말고 그 파일을 세라.

이 디렉터리는 그 호스트를 **저장소만으로 재현**하기 위해 존재한다. 이전에는 이 코드가 세션 스코프 scratchpad 에만 있어서, `README.md` 가 온디맨드 데모를 포트폴리오로 내세우는데 **그걸 만드는 코드는 저장소에 없었다** — 검증 불가능한 주장이었다.

---

## 구성

```
infra/demo/aws/
├── packer/demo-ami.pkr.hcl   ← 도커 + 저장소 + 프리빌드 이미지 + systemd 유닛을 구운 AMI
├── terraform/                ← EC2 + API Gateway + Lambda + IAM + SG
│   ├── lambda/handler.py     ← /start /stop /status /heartbeat + 유휴정지 + 월 예산 가드
│   └── terraform.tfvars.example
├── tests/test_handler.py     ← Lambda 단위 테스트 11건 (예산 가드 포함)
└── site/index.html           ← 정적 "Start Demo" 페이지
```

부팅 계약 자체는 여기 없다 — **`infra/demo/demo-boot.sh` + `infra/demo/demo-stack.service`** 가 저장소 본체에 있고, AMI 는 그것을 **복사할 뿐**이다. 사본을 굽던 옛 구조에서는 저장소가 계약을 바꿔도 유닛이 몰랐고, 그래서 스택이 96개 healthy 한 채로 `*.local` 에 떠서 아무도 도달할 수 없었다(TASK-MONO-358/366).

---

## 재현 절차

```bash
# 0) 자격증명 (비밀 키를 파일이나 채팅에 붙여넣지 말 것)
aws configure                     # 또는 AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY
#    필요 권한은 아래 "배포 주체 권한" 절 — 없으면 apply 가 절반만 하고 멈춘다

# 1) AMI 굽기 (~55분 — 이미지 빌드 + 100GB 스냅샷 등록이 대부분)
cd infra/demo/aws/packer
packer init . && packer build -var "repo_ref=main" demo-ami.pkr.hcl

# 2) 인프라 + 정적 사이트 (CloudFront 배포 때문에 apply 가 5~15분 걸린다)
cd ../terraform
cp terraform.tfvars.example terraform.tfvars   # ami_id + admin_ssh_cidr 채우기
terraform init && terraform apply

# 3) 방문자가 열 주소 — 이게 데모의 정문이다
terraform output site_url
```

**3단계에 손으로 넣을 값이 없다.** 제어 API 의 URL 은 `terraform` 이 `config.js` 로
렌더해 사이트에 자동 주입한다(`aws_s3_object.config`). 저장소에는 그 URL 이 **없다.**

> 예전에는 여기가 *"`terraform output api_endpoint` 를 `site/index.html` 의 `API_BASE` 에
> 넣는다"* 였다. **`api_endpoint` 라는 output 은 존재한 적이 없고**(실제 이름은 `api_base_url`),
> 그 리터럴은 API Gateway id 가 재생성마다 바뀌므로 **커밋되는 순간부터 썩고 있었다.**
> 아무도 이 절차를 끝까지 밟은 적이 없어서 둘 다 살아남았다 — `TASK-MONO-389`.

`terraform destroy` 로 전부 되돌릴 수 있다 — 상태를 EBS 볼륨에만 두므로 재생성이 안전하다.
**AMI 와 스냅샷은 packer 산출물이라 terraform 관리 밖이고 살아남는다** ⇒ 되살리는 데
`terraform apply` 2분이면 되고 **AMI 를 다시 구울 필요가 없다** — **다만 이것은 *복구*에 대해서만
참이다. *코드를 고친 경우*는 다르다. 바로 아래 절을 읽어라.**

⚠️ `destroy` 는 SSM 의 **월 사용량 카운터도 지운다**(예산 가드 리셋).

---

## 🔴 코드를 고쳤다 — 그게 데모에 도달하는가? (배포 층이 둘이다)

**AMI 는 저장소의 *스냅샷*이다.** packer 가 bake 때 `git clone --depth 1 --branch main` 하고
(`packer/demo-ami.pkr.hcl`), **`demo-boot.sh` 는 부팅 시 `git pull` 을 하지 않는다** — 도메인만
파생하고 `/opt/monorepo-lab` 을 **있는 그대로** 쓴다.

⇒ **무엇을 고쳤느냐에 따라 재굽기 필요 여부가 갈린다:**

| 고친 것 | 도달 경로 | 재굽기 |
|---|---|---|
| `terraform/**` · `terraform/lambda/handler.py` · `site/index.html` · `ec2/user-data.sh` | **`terraform apply` 가 저장소에서 그때 읽는다** (`file()` / `archive_file` / `aws_s3_object`) | ❌ 불필요 |
| `infra/demo/*.sh` · `demo-stack.service` · **`projects/*/docker-compose.yml`** · 앱 소스(Java/TS) | **AMI 에 구워져 있다** | ✅ **필요** (`packer build`, ~55분) |

**⚠️ 그래서 `main` 이 초록인 것은 데모가 고쳐졌다는 증거가 아니다.**

실제로 그런 상태다 — `TASK-MONO-397` 이 `ecommerce` 의 kafka 메모리 리밋을 **512M → 1G** 로
고쳤지만 그건 `docker-compose.yml` 이고, **현재 AMI 는 그 커밋 이전에 구워졌다.** 즉 **지금 데모를
켜면 여전히 512 MiB kafka 가 뜨고, 부하가 붙으면 OOM 으로 재시작한다.** 인수 = `TASK-MONO-399` AC-6.

**이것은 `TASK-MONO-367` 이 이름 붙인 실패 모드다** — *"일을 잘못한다" 가 아니라 **"일을 끝내는 것이
일을 끝낸 것처럼 보인다"***. 머지는 절반이다.

**실험만 할 거라면 재굽기는 필요 없다**: compose 의 *값* 하나(리밋·env)를 바꿔 보는 것뿐이면
인스턴스 안에서 그 줄을 고치고 `docker compose up -d` 하면 된다(이미지는 안 바뀐다).
**단 `terraform destroy` 하면 사라진다** — 영속시키려면 저장소에 커밋하고 **재굽기**해야 한다.

**그리고 구운 것을 믿지 마라.** `--branch main` 은 ***bake 시점의*** main 이므로, **성공한 bake 가
옛 커밋을 담을 수 있다.** 새 AMI 로 띄운 뒤 **인스턴스 안에서 런타임으로** 확인하라 —
`git -C /opt/monorepo-lab log -1` 과 `docker inspect ecommerce-kafka --format '{{.HostConfig.Memory}}'`.
선언이 아니라 **실행 중인 값**을 물어야 한다.

---

## 배포 주체 권한

`terraform apply` 를 실행하는 IAM 주체에게 필요한 것:

| | |
|---|---|
| 관리형 정책 | `AmazonEC2FullAccess` · `AWSLambda_FullAccess` · `AmazonAPIGatewayAdministrator` · `AmazonSSMFullAccess` · `AmazonEventBridgeFullAccess` · `CloudWatchLogsFullAccess` · `IAMFullAccess`<br>(IAM 은 이 스택이 EC2/Lambda 의 **역할을 직접 만들기** 때문에 필요하다) |
| 인라인 정책 | [`iam/deployer-site-policy.json`](iam/deployer-site-policy.json) — 정문(S3 + CloudFront) |

```bash
aws iam put-user-policy --user-name <deployer> \
  --policy-name portfolio-demo-site \
  --policy-document file://iam/deployer-site-policy.json
```

정문 정책을 따로 둔 이유는 **관리형 `AmazonS3FullAccess` 가 계정의 모든 버킷을 여는데, 이 스택은 자기 버킷 하나만 필요**하기 때문이다. 그래서 `portfolio-demo-site-*` 접두사로 좁혔다. CloudFront 는 생성 시점에 ARN 을 알 수 없어 리소스 스코프가 성립하지 않는다(`Resource: "*"`).

> 이 절은 **한 번도 존재한 적이 없었고**, 그래서 정문을 추가한 첫 `apply` 가 29개 중 14개만
> 만들고 `AccessDenied` 로 멈췄다. 재현 절차가 자격증명을 *"`aws configure` 하라"* 로만
> 말하면, **그 자격증명이 무엇을 할 수 있어야 하는지는 아무도 말하지 않은 것이다** — TASK-MONO-389.

---

## 비용

| 상태 | 비용 |
|---|---|
| 아무도 안 씀 (인스턴스 destroy) | **~$2/월** (AMI 스냅샷) |
| 인스턴스 정지 | ~$11/월 (100GB EBS gp3) |
| 가동 중 (m6i.2xlarge) | ~$0.5/시간 |

**`/start` 는 공개 엔드포인트다 — 가드가 없으면 무제한 지출 버튼이다.** 세 겹으로 막는다:

1. **`idle_minutes`** (기본 20) — 마지막 하트비트 후 유휴하면 정지.
2. **`max_runtime_minutes`** (기본 180) — 한 세션의 절대 상한. 하트비트가 계속 와도 정지.
3. **`monthly_budget_minutes`** (기본 600) — **월 누적 가동 분.** 소진되면 `/start` 가 429 를 반환하고 인스턴스를 켜지 않는다.

**3번이 없으면 1·2번만으로는 부족하다** — 누가 매일 세 시간씩 켜도 각 세션은 규칙을 완벽히 지킨다. 상한은 **세션이 아니라 지출**에 걸어야 한다. API Gateway 스테이지에 throttling(5 rps / burst 10)도 함께 건다.

---

## 사이징 — **1회 관측치이지, 아무것도 강제하지 않는 예산이다**

`full` ≈ **96 컨테이너 / 메모리 ~26GB** (JVM 40+). m6i.2xlarge(32GB)에서 **여유 ~5.5GB.**
`TASK-MONO-366` 이 실행 중인 스택을 **한 번 재서 얻은 값**이다.

이전 문서에 적혀 있던 *"43 컨테이너 / 21GB 여유"* 는 **고장난 스택을 잰 값이었다** — `bitnami/kafka:3.7` 이 Docker Hub 에서 삭제되어 33개만 뜬 상태였고(TASK-MONO-353), 죽은 이미지 하나가 나머지 6개 프로젝트를 통째로 막고 있었다.

> 🔴 **이 숫자를 상한으로 읽지 마라.** 메모리 리밋을 선언하는 것은 **35개(2개 프로젝트)뿐** —
> `ecommerce` 34개(전 서비스) + `finance` kafka 1개(FIN-BE-059). 나머지 **6개 프로젝트(iam·wms·
> scm·erp·fan·console)는 리밋을 하나도 선언하지 않는다** ⇒ 그 컨테이너들은 **천장이 없다.** 26GB 도,
> 5.5GB 여유도 **저장소의 무엇도 강제하지 않는 관측치**이며, 다음 변경이 그 여유를 먹어도
> **아무 게이트도 멈춰 세우지 않는다.**
>
> ✅ **재측정 완료 = `TASK-MONO-399` AC-2 (2026-07-23, 데모 호스트 실측).** `full` = **86 서비스 선언 /
> 98 컨테이너**, `free -m` **used ~28GB / 여유 ~2.8GB** (26GB/5.5GB 보다 높고 여유는 작다). ⚠️ **단
> as-baked 스택은 3중 크래시루프(kafka 512M OOM · finance 미싱브로커 · erp Flyway 충돌)로 완전
> 안정화하지 않으므로 이 값은 루프 중 관측치다** — clean 측정은 세 fix 배포(MONO-399 AC-6 재굽기,
> ERP-BE-035 선행) 후에야 가능. 호스트 의존 값이라 **가드로 승격하지 않는다**(397 이 이 축에서 두 번 실패).
>
> **그리고 "일관성 있게" 다른 프로젝트에 리밋을 추가하지 마라** — 컨테이너 메모리 리밋은
> **상한이 아니라 *설정*이다.** `JAVA_OPTS` 가 없는 JVM 은 cgroup 리밋을 읽어 힙을 **그 25%** 로
> 잡는다(`MaxRAMPercentage` 기본값). 리밋을 넣는 순간 지금 무제한인 JVM 들이 조용히 25% 힙으로
> **재설정된다.** `TASK-MONO-397` 이 정확히 그 함정에서 나왔다(`512M` = "128 MiB 힙으로 카프카를
> 돌려라"). **일관성이 새 결함을 만든다.**

---

## 도메인 — 왜 HTTP 인가

호스트명은 부팅 시 **IMDSv2 로 공인 IP 를 읽어 파생**한다(`<a-b-c-d>.sslip.io`). EIP 를 붙이지 않으므로 재시작마다 IP 가 바뀌고, `demo-boot.sh` 가 그것을 매 부팅 따라간다.

**TLS 는 실 도메인 없이는 불가능하다** — `sslip.io` 는 **Public Suffix List 에 없다**(리스트 대조 확인). PSL 에 없으면 Let's Encrypt 가 `sslip.io` **전체를 하나의 등록 도메인**으로 취급하므로 주당 50장 한도를 전 세계 사용자와 공유하게 되고, 발급이 성립하지 않는다.

그래서 데모는 평문 HTTP 이고, `CONSOLE_COOKIE_SECURE=false` 가 **필요하다** — 브라우저는 `http://` 로 온 `Secure` 쿠키를 **localhost 가 아닌 오리진에서 저장조차 하지 않으므로**, 켜 두면 PKCE/state 쿠키가 사라지고 모든 로그인이 `invalid_state` 로 튕긴다(TASK-MONO-358 에서 실측).

실 도메인을 구매하면 `CONSOLE_COOKIE_SECURE` 를 **지우기만 하면**(기본값 `true`) 강화된다. 가드 (m) 이 `https` 오리진과 `Secure=false` 의 조합을 막는다 — 그 하나만이 진짜 다운그레이드다.

---

## 비밀

`terraform.tfvars`(본인 공인 IP CIDR) · `*.tfstate` · `.terraform/` 은 **gitignore** 다. `terraform.tfvars.example` 만 커밋되며, 그것이 재현 계약이다. **AWS 비밀 키를 tfvars 에 적지 말 것** — 평문이다. 환경변수나 `~/.aws/credentials` 를 쓴다.

