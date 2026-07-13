# infra/demo/aws — 온디맨드 포트폴리오 데모 호스트 (TASK-MONO-366)

방문자가 **Start Demo** 를 누르면 EC2 가 깨어나 통합 데모(9 프로젝트 / 96 컨테이너)를 올리고, 유휴해지면 스스로 꺼진다. **scale-to-zero** — 아무도 안 쓰면 컴퓨트 비용이 0 이다.

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
`terraform apply` 2분이면 되고 **AMI 를 다시 구울 필요가 없다.**

⚠️ `destroy` 는 SSM 의 **월 사용량 카운터도 지운다**(예산 가드 리셋).

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

## 사이징 — 실측으로 정정된 값

`full` = **96 컨테이너 / 메모리 ~26GB** (JVM 40+).

이전 문서에 적혀 있던 *"43 컨테이너 / 21GB 여유"* 는 **고장난 스택을 잰 값이었다** — `bitnami/kafka:3.7` 이 Docker Hub 에서 삭제되어 33개만 뜬 상태였고(TASK-MONO-353), 죽은 이미지 하나가 나머지 6개 프로젝트를 통째로 막고 있었다. m6i.2xlarge(32GB)에서 **실제 여유는 5.5GB 뿐**이다.

---

## 도메인 — 왜 HTTP 인가

호스트명은 부팅 시 **IMDSv2 로 공인 IP 를 읽어 파생**한다(`<a-b-c-d>.sslip.io`). EIP 를 붙이지 않으므로 재시작마다 IP 가 바뀌고, `demo-boot.sh` 가 그것을 매 부팅 따라간다.

**TLS 는 실 도메인 없이는 불가능하다** — `sslip.io` 는 **Public Suffix List 에 없다**(리스트 대조 확인). PSL 에 없으면 Let's Encrypt 가 `sslip.io` **전체를 하나의 등록 도메인**으로 취급하므로 주당 50장 한도를 전 세계 사용자와 공유하게 되고, 발급이 성립하지 않는다.

그래서 데모는 평문 HTTP 이고, `CONSOLE_COOKIE_SECURE=false` 가 **필요하다** — 브라우저는 `http://` 로 온 `Secure` 쿠키를 **localhost 가 아닌 오리진에서 저장조차 하지 않으므로**, 켜 두면 PKCE/state 쿠키가 사라지고 모든 로그인이 `invalid_state` 로 튕긴다(TASK-MONO-358 에서 실측).

실 도메인을 구매하면 `CONSOLE_COOKIE_SECURE` 를 **지우기만 하면**(기본값 `true`) 강화된다. 가드 (m) 이 `https` 오리진과 `Secure=false` 의 조합을 막는다 — 그 하나만이 진짜 다운그레이드다.

---

## 비밀

`terraform.tfvars`(본인 공인 IP CIDR) · `*.tfstate` · `.terraform/` 은 **gitignore** 다. `terraform.tfvars.example` 만 커밋되며, 그것이 재현 계약이다. **AWS 비밀 키를 tfvars 에 적지 말 것** — 평문이다. 환경변수나 `~/.aws/credentials` 를 쓴다.

