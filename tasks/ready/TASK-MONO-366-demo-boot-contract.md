# Task ID

TASK-MONO-366

# Title

온디맨드 데모를 **사람 손 없이** 부팅 가능하게 — `DEMO_DOMAIN` 부팅 파생 + AWS PoC 저장소 승격

# Status

ready

# Owner

monorepo

# Task Tags

- infra
- demo

---

# Goal

`TASK-MONO-358` 은 저장소 쪽 계약을 이행했다: **`DEMO_DOMAIN` 을 주면 그 도메인으로 뜨고 로그인까지 된다.** EC2 에서 왕복으로 증명됐다.

**그런데 부팅 자동화가 그 계약을 쓰지 않는다.** 방문자가 "Start Demo" 를 눌러 인스턴스가 뜨면 스택은 여전히 **`*.local` 로 올라온다** — 아무도 도달할 수 없다. 358 의 검증은 매번 내가 SSM 으로 들어가 손으로 재기동해서 얻은 것이다. **자동 경로는 한 번도 동작한 적이 없다.**

## 결함 1 — systemd 유닛이 `DEMO_DOMAIN` 을 모른다

```ini
# ec2/demo-stack.service (현재 — scratchpad PoC)
ExecStart=/usr/bin/bash /opt/monorepo-lab/infra/demo/demo-up.sh ${DEMO_PROFILE}
```

`DEMO_DOMAIN` 이 **없다** → `demo.env` 의 기본값 `local` 이 먹는다 → 라우터가 전부 `Host(\`x.local\`)` → 방문자 브라우저는 `Host: <공인IP>` 를 보내므로 **전 도메인 404**. 인스턴스는 재시작마다 공인 IP 가 바뀌므로(EIP 없음) 도메인은 **부팅 시점에 IMDSv2 로 파생**해야 한다.

## 결함 2 — AMI 가 옛 이미지를 굽고 있다

현재 AMI 에 prebake 된 것:

- **옛 `console-web`** — `Secure` 쿠키 + 빌드타임에 구워진 `console.local` (MONO-358 결함 G·H). 358 검증 때 EC2 에서 **손으로 재빌드**해야 했다.
- **Traefik v3.2** — Docker Engine 29 와 API 협상 실패 → **라우터 0개** (결함 C).

즉 AMI 를 다시 굽지 않으면 358 의 수정이 부팅 경로에 **하나도 반영되지 않는다.**

## 결함 3 — 데모를 재현할 코드가 저장소에 없다

Packer / Terraform / Lambda / 정적 사이트가 전부 **scratchpad 에만** 있다. 저장소만 가지고는 데모 호스트를 **다시 만들 수 없다.**

이건 이 저장소가 계속 쫓아온 결함 클래스의 또 다른 얼굴이다 — **아티팩트가 문서의 주장을 뒷받침하지 못하는 상태.** `README.md` 는 온디맨드 데모를 포트폴리오의 일부로 내세우는데, 그것을 만드는 코드가 저장소에 없으면 그 주장은 검증 불가능하다. scratchpad 는 세션 스코프이고 언제든 사라진다.

---

# 설계

## 1) `infra/demo/demo-boot.sh` (신설) — 부팅 진입점

systemd 가 `demo-up.sh` 를 직접 부르지 않는다. 도메인 파생이라는 **부팅 전용 관심사**를 데모 래퍼에 섞지 않기 위해 얇은 진입점을 하나 둔다.

```
IMDSv2 토큰 → public-ipv4 → DEMO_DOMAIN=<a-b-c-d>.sslip.io → export → demo-up.sh <profile>
```

- **IMDSv2 필수** — 토큰 없는 `curl 169.254.169.254` 는 401.
- **AWS 밖에서도 안전해야 한다** — 메타데이터 서비스가 없으면(로컬 개발자가 실수로 실행) **`local` 로 폴백**하고 그 사실을 로그로 말한다. 조용히 빈 문자열이 되면 `Host(\`x.\`)` 같은 쓰레기 라우터가 생긴다.
- **하이픈 표기** (`1-2-3-4.sslip.io`) — `web.ecommerce.${DEMO_DOMAIN}` 처럼 이미 2단인 것과 합쳐지므로 점 표기는 레이블이 길어진다.

## 2) `infra/demo/demo-stack.service` (신설) — 유닛을 저장소가 소유한다

현재 유닛은 Packer 템플릿 옆(scratchpad)에 있어 **저장소의 스크립트와 따로 논다.** 유닛이 `demo-up.sh` 를 직접 부르는 지금 구조에서는, 저장소가 부팅 계약을 바꿔도 유닛은 모른다. 유닛을 저장소로 가져오고 AMI 빌드가 **저장소에서 복사**하게 한다 — 그러면 계약이 한 곳에 있다.

## 3) `infra/demo/aws/` (신설) — PoC 승격

```
infra/demo/aws/
├── README.md              ← 비용·수명주기·주의사항
├── packer/demo-ami.pkr.hcl
├── terraform/{main,variables,outputs,versions}.tf
│   ├── terraform.tfvars.example     (실 tfvars 는 gitignore)
│   └── lambda/handler.py            (start/stop/status/heartbeat + 월 예산 가드)
├── tests/test_handler.py            (11 tests)
└── site/index.html                  (정적 "Start Demo" 페이지)
```

**비밀은 올리지 않는다** — `terraform.tfvars`(내 공인 IP CIDR 포함) · `*.tfstate` · `.terraform/` 은 gitignore. `.example` 만 커밋.

## 4) 가드 (n) — 부팅 경로가 도메인을 실제로 설정하는가

`verify-demo-wrapper.sh` 에 추가한다. 세 가지를 본다:

1. `demo-stack.service` 의 `ExecStart` 가 **`demo-boot.sh` 를 부른다**(`demo-up.sh` 직접 호출 금지 — 그게 지금의 결함이다).
2. `demo-boot.sh` 가 `demo-up.sh` **호출 전에** `DEMO_DOMAIN` 을 export 한다.
3. Packer 템플릿이 유닛을 **저장소 경로에서** 설치한다(scratchpad 사본 금지).

**mutation 필수.** 358 에서 가드 두 개가 **실제 트리에서는 통과하면서 무는 능력이 없었다**(자기 주석 매치 / YAML field-split). 통과는 증거가 아니다.

---

# Scope

## In Scope

- **`infra/demo/demo-boot.sh`** 신설 — IMDSv2 도메인 파생 + 폴백 + `demo-up.sh` 위임.
- **`infra/demo/demo-stack.service`** 신설 — 저장소가 유닛을 소유. `demo-boot.sh` 호출.
- **`infra/demo/aws/`** 신설 — scratchpad PoC(packer / terraform / lambda / tests / site) 승격. 비밀 제외.
- **`infra/demo/verify-demo-wrapper.sh`** — 가드 (n).
- **`.gitignore`** — `infra/demo/aws/terraform/{terraform.tfvars,*.tfstate*,.terraform/}`.
- **AMI 재빌드** — 현재 main 에서 이미지 재굽기(console-web 수정 + Traefik v3.6 포함).
- `infra/demo/README.md` · `README.md`(포트폴리오 허브) 갱신.

## Out of Scope

- **TLS/HTTPS** — MONO-358 이 실측으로 배제했다: `sslip.io` 가 PSL 에 없어 Let's Encrypt 가 도메인 전체를 한 덩어리로 묶는다. 실 도메인 구매는 **별개 결정**(사람).
- EIP — 정지 중에도 과금(~$3.6/월). IMDSv2 파생이 IP 변동을 흡수하므로 불필요.
- 데모 데이터 시딩(테넌트·주문 샘플) — 별개 증분.
- `wms-notification-service` unhealthy · 관측 컨테이너 healthcheck 부재 — 별개 티켓.

---

# Acceptance Criteria

- [ ] **`demo-boot.sh` 가 AWS 밖에서 안전하다** — 메타데이터 서비스 부재 시 `DEMO_DOMAIN=local` 로 폴백하고 그 사실을 출력한다. **빈 문자열이 되지 않는다**(빈 값이면 `Host(\`console.\`)` 같은 라우터가 생기고 아무도 도달하지 못한다).
- [ ] **가드 (n) — mutation 필수.** 통과만으로는 무는지 알 수 없다:
      - 유닛의 `ExecStart` 를 `demo-up.sh` 직접 호출로 되돌리면 **FAIL**
      - `demo-boot.sh` 에서 `export DEMO_DOMAIN` 을 지우면 **FAIL**
      - Packer 가 유닛을 저장소 밖에서 복사하면 **FAIL**
      - vacuity: 정상 트리에서 PASS (항상-FAIL 하는 가드가 아님)
- [ ] **AMI 가 현재 main 을 굽는다** — 새 AMI 의 `console-web` 이미지가 `CONSOLE_PUBLIC_ORIGIN` / `CONSOLE_COOKIE_SECURE` 를 알고, Traefik 이 v3.6 이다.
- [ ] **실기동 증명 — 사람 손 0. 이것만이 진짜 검증이다.**
      `terraform apply` → 정적 사이트의 **Start Demo** 클릭(또는 `POST /start`) → **SSH·SSM 접속 없이** 브라우저로 `http://console.<ip>.sslip.io/` → **OIDC 로그인 왕복 성공.**
      **SSM 으로 들어가 손으로 재기동하면 그건 증명이 아니다** — 358 이 정확히 그 상태였다.
- [ ] **저장소만으로 데모 호스트를 재현할 수 있다** — `infra/demo/aws/README.md` 의 절차만 따라 AMI→인프라→기동이 완주.
- [ ] 비밀 미커밋 — `terraform.tfvars` · `*.tfstate` · `.terraform/` 이 트리에 없다.
- [ ] CI GREEN.

---

# Edge Cases

- **IMDSv2 는 토큰 필수** — `PUT /latest/api/token` 선행. 토큰 없이 치면 401 이고, 그걸 무시하면 `DEMO_DOMAIN` 이 빈 문자열이 된다.
- **`demo.env` 의 `DEMO_DOMAIN=${DEMO_DOMAIN:-local}`** — **bare 대입이면 caller 의 export 를 덮어쓴다**(358 에서 실제로 당했다: `set -a; source demo.env` 가 파생값을 지웠다). 이 형태를 유지해야 `demo-boot.sh` 의 export 가 살아남는다.
- **systemd `Environment=`** 는 셸이 아니다 — 명령 치환이 안 된다. 도메인 파생은 반드시 **스크립트 안**에서.
- **첫 부팅과 재시작** — `user_data` 는 첫 부팅에만 돈다. 재시작에도 뜨려면 `systemctl enable` 이 핵심이고, 도메인은 **매 부팅** 다시 파생돼야 한다(IP 가 바뀐다).
- **AMI 재빌드 시간** — 100GB 스냅샷은 기본 waiter(10분)를 넘긴다. `aws_polling` 필요(PoC 에 이미 반영).

# Failure Scenarios

- **도메인이 빈 문자열** → `Host(\`console.\`)` 라우터 생성 → Traefik 은 에러를 내지 않고 그냥 아무도 매치하지 않는다. **404 인데 원인이 안 보인다.** → 폴백 + 가드.
- **유닛이 `demo-up.sh` 를 계속 직접 부름** → 스택은 healthy 하게 뜨고 컨테이너 96개가 전부 초록인데 **아무도 로그인할 수 없다.** 358 이 겪은 그 모양이고, **healthcheck 로는 절대 안 잡힌다.**
- **AMI 를 안 굽고 넘어감** → 저장소는 고쳐졌는데 부팅 경로는 옛 이미지를 쓴다 → 로그인 실패가 그대로 재현되고, **저장소를 아무리 읽어도 원인이 안 보인다**(이미지가 코드와 다르다).
- **tfvars 를 실수로 커밋** → 공인 IP CIDR 노출. gitignore + AC 로 봉인.

# Test Requirements

- 정적: `verify-demo-wrapper.sh` 가드 (a)~(n) PASS + **(n) mutation 4방향**.
- 단위: `infra/demo/aws/tests/test_handler.py` 11건(Lambda 월 예산 가드) — 승격과 함께 CI 에 붙인다.
- `bash -n` / shellcheck — `demo-boot.sh`.
- **실기동**: 새 AMI 로 `terraform apply` → `/start` → **사람 개입 없이** 브라우저 로그인 왕복.

# Definition of Done

- [ ] 위 AC 전부
- [ ] CI GREEN
- [ ] `tasks/INDEX.md` done entry
- [ ] scratchpad `ondemand-demo/` 는 승격 후 참조용으로만 남긴다(저장소가 단일 출처)

---

# Provenance

2026-07-12, `TASK-MONO-358` 을 EC2 에서 검증하며 드러났다. 358 의 로그인 왕복은 **매번 SSM 으로 들어가 `demo-down` → `demo-up` 을 `DEMO_DOMAIN` 과 함께 손으로 돌려서** 얻은 것이고, **자동 부팅 경로는 한 번도 그 계약을 쓴 적이 없다.** 358 이 저장소 쪽 계약을 이행했다면, 이 task 는 **그 계약을 실제로 소비하게** 만든다.

선행: `TASK-MONO-358`(데모 도메인 + iam 엣지 + 로그인). 그것 없이는 도메인을 파생해도 로그인이 안 된다.

**교훈 계승**: 358 에서 가드 두 개가 **실제 트리에서는 통과하면서 무는 능력이 없었다**((k) 자기 주석 매치 / (m) YAML field-split 이 `http://` 를 스킴에서 자름). 가드 (n) 은 **mutation 을 주입해 실제로 무는지 확인**해야 한다.

분석=Opus 4.8 / 구현 권장=Opus (IMDSv2·systemd·Packer·Terraform 이 얽히고, **잘못 고치면 스택은 전부 healthy 한데 아무도 로그인할 수 없는 상태**가 된다 — 정확히 358 이 시작한 지점이다)
