# Task ID

TASK-MONO-477

# Title

온디맨드 데모에 **도메인별 선택 기동/정지**를 얹는다 — 항상-뜬 페이지에서 전체 또는 개별 도메인을 켜고 끈다

# Status

ready

# Owner

monorepo

# Task Tags

- infra
- demo

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

**AC-0 — 재사용 지점 재확인 (verify-then-act).**
착수 시 `infra/demo/` 와 `infra/demo/aws/` 를 `origin/main` 에서 다시 읽는다. 본 티켓의 파일 목록·라인
참조는 출처가 아니라 **가설**이다. SSM 인스턴스 프로파일이 여전히 붙어 있는지(`main.tf`), `demo-up.sh` 의
프로파일 인자 파싱이 바뀌지 않았는지 확인한다.

**AC-1 — `projects.sh` DEPS 맵.**
각 도메인의 의존을 선언한다(최소: 전원→iam, console→federation 소비 도메인, wms↔ecommerce 루프).
`resolve_deps <slug...>` 가 선택 집합의 전이적 폐포를 **기동 순서(iam 먼저, console 마지막)** 로 반환한다.
단위 가드: `resolve_deps console` 이 `iam` 을 포함하는지.

**AC-2 — `demo-up.sh` / `demo-down.sh` 도메인 리스트 인자.**
`demo-up.sh iam fan console` 이 DEPS 확장 후 해당 프로젝트만 `-p` 로 띄운다. `demo-down.sh console` 이
console 만 내리되, **다른 떠 있는 도메인이 iam 을 소비 중이면 iam·traefik 은 유지**한다. 기존
`demo-core|full` 인자는 **하위 호환**으로 계속 동작한다.

**AC-3 — `demo-status.sh` 헬스 스냅샷.**
`docker compose -p <slug> ps --format json` 을 집계해 도메인별 `{state, healthy, total}` JSON 을
표준출력 + 지정 SSM 파라미터에 발행한다. 떠 있지 않은 도메인은 `state=down` 으로 명시(누락 아님).

**AC-4 — Lambda 신규 액션 + IAM.**
`GET /domains` 가 SSM 스냅샷을 반환. `POST /domain/start`·`/domain/stop` 이 running VM 에 SendCommand 로
`demo-up.sh`/`demo-down.sh <name>` 을 실행. VM stopped 시 `/domain/start` 는 먼저 VM 을 켜거나(선택)
명확한 안내를 반환. **예산 소진 시 도메인 start 도 429.** IAM 은 `ssm:SendCommand`(문서·인스턴스 ARN
스코프)+`GetCommandInvocation`+스냅샷 파라미터 R/W 만 추가(최소 권한).

**AC-5 — 페이지 도메인 그리드.**
`site/index.html` 이 8개 도메인 토글 + "전체"를 렌더하고 각 헬스 배지를 `/domains` 폴링으로 갱신한다.
기존 안전장치 유지: config.js 미로드 시 크게 실패, 웜업 정직 문구(도메인별 예상 시간), `demoHost()` 대시
표기(점 표기 404 함정), `ok/status` 확인(429 무시 금지).

**AC-6 — 테스트.**
`test_handler.py` 가 신규 3액션을 커버(SendCommand 목, 예산 소진 시 도메인 start 429, VM stopped 분기).
로컬에서 `demo-up.sh iam fan console` → `demo-status.sh` → `demo-down.sh console` 왕복을 실증(도커 기동
가능 환경에서). **로컬 Windows 는 IT 권위 아님** — 스크립트 로직은 로컬, AWS 경로는 인스턴스 실증.

**AC-7 — AWS 실증 (1회 기동).**
`terraform apply` → 페이지에서 도메인 토글 → SSM 경로로 부분 기동/정지가 실제로 동작하고 헬스가
페이지에 반영되는지 브라우저(Playwright/headless fetch)로 실증. **끝나면 `terraform destroy` 즉시 복귀.**
⚠️ `terraform apply`/`destroy`/`packer build` 는 **사용자 승인 필요**.

**AC-8 — 재굽기 (측정·실증이 끝난 뒤, MONO-399 와 합침 가능).**
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
