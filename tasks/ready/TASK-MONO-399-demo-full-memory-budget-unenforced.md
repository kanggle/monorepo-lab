# Task ID

TASK-MONO-399

# Title

`profile=full` 의 메모리 예산은 **아무것도 강제하지 않는 선언**이다 — 그리고 erp/finance 가 왜 죽었는지 아무도 모른다

# Status

ready

# Owner

monorepo

# Task Tags

- infra
- demo
- observability

---

# 🔬 실측 결과 (2026-07-23, 데모 호스트 `i-070c54a...`, AMI `ami-0b6b962d3f3f23865` = as-baked `f5288a7b1`)

**AC-0~AC-5 완료 · AC-6 은 [`TASK-ERP-BE-035`](../../projects/erp-platform/tasks/ready/TASK-ERP-BE-035-shared-erp-db-flyway-history-collision.md) 머지 후로 연기(사용자 판단 2026-07-23 — 이중 bake 회피).**

1회 기동(`terraform apply` → `/start` → SSM 측정 → `terraform destroy`), 실비용 ≈ 인스턴스 27분 $0.23(packer 미수행). 원시 로그: 세션 scratchpad `measure_out.txt`.

## AC-0 — 인계 숫자 재측정 (달랐다 — 그 사실이 먼저다)

| 항목 | 인계(397/366) | 실측(origin/main) |
|---|---|---|
| 리밋 선언 프로젝트 | "ecommerce 하나뿐" | **ecommerce + finance 2개** |
| 리밋 선언 lines | 34 | **35** (ecommerce 34 = 전 서비스 + finance kafka 1G) |
| profile=full 총 서비스 | "50"/"90" 혼재 | **86** 선언 / **98** 컨테이너(런타임, 95 running) |

finance kafka 1G 는 커밋 `47d2a6bc1`(FIN-BE-059, *"add the missing Kafka broker"*)가 **AMI 구운 뒤** 추가 — as-baked `f5288a7b1` 엔 finance kafka 브로커 자체가 없다.

## AC-1 — 재시작 근인 (증거 기반 분류, "재시작=원인" 금지)

| 컨테이너 | 재시작 | exit/oom/memlim | 근인 (증거) | main 이 고치나 |
|---|---|---|---|---|
| ecommerce-kafka | 41+ | 0 / false / **512M** | **cgroup OOM** — dmesg `oom-kill:constraint=CONSTRAINT_MEMCG`, task=java, 동일 cgroup 다수 | ✅ MONO-397 1G (재굽기 필요) |
| finance-ledger | 17 | 0 / false / 0 | **미싱 브로커** — `ConfigException: No resolvable bootstrap urls given in bootstrap.servers` | ✅ FIN-BE-059 (재굽기 필요) |
| erp-masterdata / erp-notification | 47 / 41 | 0 / false / 0 | 🆕 **Flyway 체크섬 충돌** — 공유 `erp_db` 단일 `flyway_schema_history`, `FlywayValidateException: checksum mismatch v1/v2/v3` | ❌ **안 고쳐짐 → [`TASK-ERP-BE-035`](../../projects/erp-platform/tasks/ready/TASK-ERP-BE-035-shared-erp-db-flyway-history-collision.md)** |

`erp-gateway`/`finance-gateway`/`scm-gateway` 1~2회 = 웜업 transient. **`OOMKilled=false, ExitCode=0` 이 cgroup OOM 을 숨긴다는 397 실측을 재확인** — dmesg 가 권위.

## AC-2 — 실제 메모리 (문서 정정, 가드 아님)

`free -m`: **used ~28GB / total 31.5GB / available ~2.8GB** (vs MONO-366 *"26GB / 여유 5.5GB"*). ⚠️ **as-baked 스택은 3중 크래시루프로 완전 안정화하지 않으므로**(kafka+finance+erp) 이 값은 루프 중 관측치다 — clean 측정은 세 fix 배포(AC-6) 후에야 가능. 호스트 의존 값이라 **가드로 승격하지 않고 문서만 고친다**(397 이 이 축에서 두 번 실패).

## AC-3 — 512M→1G 인과 확인 ✅

`docker update --memory 1G ecommerce-kafka` 라이브 적용 후 **150초간 재시작 0회**(41 고정), status=running, oom=false, dmesg 신규 OOM 없음. → MONO-397 의 1G 가 데모 호스트에서 OOM 루프를 멈춘다.

## AC-4 — KRaft 로그 손상 인과: **미증명으로 종결** (세 번째 추측 없음)

512M OOM-kill 후 매 재시작 kafka 는 `Recovering unflushed segment ... N/N recovered`(unclean shutdown 정상 복구)만 수행, **`corrupt`/`CorruptRecord`/`LogRecoveryError` 마커 0건**. ⇒ **이 재현에서 512M OOM 은 로그를 손상시키지 않는다.** 389(잘린 웜업)도 397(OOM)도 손상 인과로 증명되지 않음 — 판정 불가를 판정 불가로 적는다.

## AC-5 — 정리 ✅

`terraform destroy` 29개 전량 삭제, 인스턴스 terminated. AMI+스냅샷 존치(재굽기 미수행이므로 불변).

## AC-6 — 연기 (blocked on ERP-BE-035)

재굽기(main 기준)는 kafka 1G + finance 브로커 2개는 고치나 **erp Flyway 충돌은 못 고친다**(main 소스 결함). 지금 재굽기하면 2/3 데모 + 나중 재굽기 = 이중 bake. **사용자 판단: ERP-BE-035 먼저 머지 → 한 번의 재굽기로 3개 동시 배포.** 착수 조건 = ERP-BE-035 `done`.

---

# Goal

`TASK-MONO-397` 이 `ecommerce-kafka` 의 512 MiB 를 고치면서 **답하지 못하고 남긴 세 가지 질문**에 데모 호스트 실측으로 답한다.

397 은 자기가 적은 원인 중 **하나만** 증명했다. 나머지는 **재시작 횟수를 보고 원인을 붙인 것**이었고, 티켓이 스스로 그 사실을 기록했다. 이 task 는 그 공백을 메우는 것이지, 397 을 다시 하는 것이 아니다.

## 세 질문

1. **erp/finance 컨테이너는 왜 재시작했는가.**
   397 의 AC-1 이 밝힌 것: **메모리 리밋을 선언하는 프로젝트는 `ecommerce` 하나뿐**이다(50 서비스 중 34 선언). 나머지 **7개 프로젝트 90 서비스는 리밋이 0개**다. ⇒ **리밋 없는 컨테이너는 `CONSTRAINT_MEMCG` OOM 이 날 수 없다.** 그런데 데모 호스트에서 erp-masterdata **14회** · erp-approval **14회** · erp-notification **12회** · finance-ledger **5회** 재시작했다. **원인 미상.**
   후보(전부 미검증): (a) `depends_on: service_healthy` 를 만족한 *뒤에* 의존성이 다시 죽어서 · (b) 애플리케이션 레벨 시작 실패 + `restart: unless-stopped` 루프 · (c) kafka 가 죽는 동안 컨슈머가 죽음 · (d) 호스트 전역 OOM(당시 호스트 여유 7.9GB 였으므로 가능성 낮음).

2. **`profile=full` 의 실제 메모리 예산은 얼마인가.**
   `MONO-366` 이 *"96컨테이너 / ~26GB / m6i.2xlarge(32GB)에서 여유 5.5GB"* 라고 적었다. **그러나 7개 프로젝트가 리밋을 0개 선언하므로 그 26GB 는 아무것도 강제하지 않는다** — 실제 사용량이 그보다 크든 작든 저장소의 어떤 것도 그것을 붙잡지 않는다. **숫자가 선언과 진실 중 어느 쪽인지 모른다.**

3. **`MONO-389` 의 kafka KRaft 로그 손상은 무엇이 만들었는가.**
   389 는 **잘린 웜업**(유휴 가드가 부팅 중 인스턴스를 정지시킴)을, 397 은 **OOM 킬**을 후보로 든다. **둘 다 증명되지 않았다.** 389 는 유휴 가드 수정을 독립적으로 증명했고, 397 은 kafka 리밋을 독립적으로 증명했다 — 그러나 **손상의 인과는 어느 쪽도 건드리지 않았다.**

## 그리고 네 번째 — 397 의 수정을 실제로 배포한다 (AC-6)

**397 의 kafka 1G 는 `main` 에 있지만 데모에는 도달하지 않았다.** compose 는 AMI 안에 구워져 있고 AMI 는 397 이전 커밋에 동결돼 있다 ⇒ **오늘 데모를 켜면 여전히 512 MiB kafka 가 뜬다.** 상세는 아래 § *"고쳤는데 데모에는 도달하지 않았다"*. **측정이 끝난 뒤** AMI 를 다시 굽는다(같은 기동에서 처리하므로 인스턴스 비용을 두 번 내지 않는다).

# Scope

**In scope**

- 데모 호스트 1회 기동(`terraform apply` → `POST /start`)으로 **세 질문에 같은 실행에서** 답한다.
- 답이 코드 변경을 요구하면 그 변경(예: erp/finance 재시작의 진짜 원인 수정, `MONO-366` 의 사이징 주장 정정, 필요 시 가드).
- 답이 *"고칠 것이 없다"* 이면 **그 사실을 기록하고 끝낸다** — 0건도 결과다.
- **AMI 재굽기 (AC-6).** 아래 § *"고쳤는데 데모에는 도달하지 않았다"* 참조. **측정이 끝난 뒤**에 한다.

**Out of scope**

- **다른 프로젝트에 메모리 리밋을 새로 추가하는 것.** 397 의 D3 가 이미 결정했다: **리밋은 상한이 아니라 *설정*이다** — `KAFKA_HEAP_OPTS`/`JAVA_OPTS` 가 없는 JVM 은 cgroup 리밋의 **25%** 를 힙으로 잡으므로, 리밋을 넣는 순간 지금 무제한인 JVM 90개가 전부 25% 힙으로 **재설정된다**. **일관성이 새 결함을 만든다.** 이 task 가 그 결정을 뒤집으려면 **실측 근거**가 있어야 한다.

## 🔴 397 은 고쳤는데, 데모에는 도달하지 않았다

**`TASK-MONO-397` 의 kafka 1G 수정은 `docker-compose.yml` 이고, compose 는 AMI 안에 구워져 있다.**
현재 AMI(`ami-0b6b962d3f3f23865`)는 main **`f5288a7b1`** 에 동결돼 있으며 — **부팅 시 `git pull` 이 없다**
(`demo-boot.sh` 는 도메인만 파생하고 `/opt/monorepo-lab` 을 그대로 쓴다).

⇒ **지금 누가 `terraform apply` 로 데모를 켜면 여전히 512 MiB kafka 가 뜬다.** `main` 은 초록이고
CI 가드도 물지만, **살아 있는 포트폴리오 데모는 크래시루프하는 브로커로 돈다.**

**이것은 `MONO-389` 가 가르친 구분의 재현이다 — *"고쳤다"* 와 *"방문자가 고쳐진 것을 본다"* 는 다른 명제다.**
389 는 `/start` 가 200 을 내는 것과 방문자가 도달하는 것이 다르다는 걸 배웠다. 여기서는 **커밋이 main 에
있는 것과 그 커밋이 실행되는 것**이 다르다.

**배포 층이 둘이라서 생긴다** — 어느 층이냐에 따라 재굽기 필요 여부가 갈린다:

| 고친 것 | 도달 경로 | 재굽기 |
|---|---|---|
| `terraform/` · `lambda/handler.py` · `site/index.html` · `ec2/user-data.sh` | `terraform apply` 가 **저장소에서 그때 읽는다** | ❌ |
| `docker-compose.yml` · `demo-boot.sh` · 앱 소스(Java/TS) | **AMI 에 구워져 있다** | ✅ |

**순서 규율: 측정이 먼저, 재굽기가 나중.** as-baked AMI 는 **결함을 보인 바로 그 대상**이므로 AC-1~AC-4 의
옳은 피험체다. 먼저 구워 버리면 **재현할 대상을 없애고 시작하는 것**이다.

# Acceptance Criteria

**AC-0 — 착수 전 재측정 (verify-then-act).**
397 의 실측 숫자(리밋 선언 34/50, 나머지 90 서비스 0개)를 **`origin/main` 에서 다시 센다.** 인계된 숫자는 출처가 아니라 **가설**이다. 다르면 그 사실이 먼저다.

**AC-1 — 재시작의 원인을 *지점*으로 분류한다 (질문 1).**
부팅 완주 후 재시작한 모든 컨테이너에 대해 **원인을 증거로 분류**한다:
- `docker inspect` 의 `OOMKilled` / `ExitCode` — **단, `OOMKilled=false, ExitCode=0` 도 cgroup OOM 일 수 있다**(397 실측: cgroup 이 JVM 스레드를 죽이고 컨테이너는 깨끗이 종료). **`dmesg` 의 `constraint=` 와 cgroup id 가 권위.**
- `docker logs --previous` — 애플리케이션이 무엇을 말하고 죽었는가.
- **"재시작했다" 를 원인으로 쓰지 않는다.** 397 이 정확히 그래서 틀렸다.
- **분류 불가는 "분류 불가"로 적는다.** 그럴듯한 원인을 붙이지 않는다.

**AC-2 — `full` 의 실제 메모리 사용량을 잰다 (질문 2).**
안정화 후(모든 컨테이너 healthy, 재시작 정지) `docker stats --no-stream` 전량 + `free -m` 을 찍는다. **`MONO-366` 의 26GB 와 대조**하고, 다르면 **저장소의 그 숫자를 정정한다**(주석/문서/메모리). 컨테이너 수도 다시 센다.
⚠️ **호스트에 따라 달라지는 값이므로 임계 가드로 만들지 않는다** — `MONO-397` 이 이 축에서 **두 번** 실패했다(내 노트북 RSS 83% ↔ 러너 38%). **관측으로 찍고 문서를 고친다.**

**AC-3 — 397 의 1G 수정을 데모 호스트에서 대조한다.**
같은 인스턴스에서 compose 의 kafka 리밋을 **512M(as-baked) → 1G** 로 바꾸고 스택을 재기동해 **kafka 재시작 횟수 0** 을 확인한다. **512M 판에서 재시작이 재현되지 않으면 그것도 결과다** — 397 의 인과가 데모 호스트에서 확인되지 않았다는 뜻이고, 그렇게 적는다.

**AC-4 — kafka 로그 손상의 인과를 판정하거나, 판정 불가를 선언한다 (질문 3).**
389(잘린 웜업)와 397(OOM 킬) 중 어느 쪽이 KRaft 로그 디렉터리를 망가뜨렸는가. **처녀 인스턴스에서 512M kafka 가 OOM-킬된 뒤 로그 디렉터리가 손상되는지**를 본다.
**증명할 수 없으면 "증명할 수 없다"로 끝낸다** — 두 티켓이 이미 각자 후보를 적어 뒀으므로, **세 번째 추측을 추가하는 것은 저장소를 더 나쁘게 만든다.**

**AC-5 — 비용을 지킨다.**
1회 기동 = 약 **$0.3**(m6i.2xlarge × 40분). 측정이 끝나면 **`terraform destroy` 로 즉시 복귀**한다(월 ~$1.8). AMI·스냅샷은 packer 산출물이라 존치된다.

**AC-6 — AMI 를 다시 굽는다 (측정이 끝난 뒤).**
`packer build -var "repo_ref=main" demo-ami.pkr.hcl` (~57분). **AC-1~AC-4 가 끝난 뒤**에 한다 — as-baked AMI 가 재현 대상이므로 먼저 구우면 피험체를 없애는 것이다.

**그리고 구운 것을 믿지 않고 확인한다.** bake 는 조용히 옛 커밋을 clone 할 수 있다(`--depth 1 --branch main` 은 *그 시점의* main 이다). 새 AMI 로 인스턴스를 띄운 뒤 **인스턴스 안에서 직접** 단언한다:
- `git -C /opt/monorepo-lab log -1` 이 **`TASK-MONO-397` 이후 커밋**인가
- `projects/ecommerce-microservices-platform/docker-compose.yml` 의 kafka `memory:` 가 **`1G`** 인가
- 부팅 후 `docker inspect ecommerce-kafka` 의 `Memory` 가 **`1073741824`** 인가 (**선언이 아니라 런타임에서** 확인 — 이 티켓의 전제 자체가 *"선언은 아무것도 강제하지 않는다"* 이다)
- kafka **재시작 0회**

**뒷정리**: `terraform.tfvars` 의 `ami_id` 를 새 AMI 로 갱신(= 인스턴스 replace 강제, 상태는 매 부팅 새로 만들어지므로 무해) → **옛 AMI + 그 스냅샷 삭제**(100GB 스냅샷은 정지 중에도 과금된다).

⚠️ **`ami_description` 에 non-ASCII(em dash 등)를 넣지 말 것** — `MONO-379` 에서 EC2 `ModifyImageAttribute` 가 거부했고, packer 는 그것을 **다 구운 뒤에** 설정하며 거부를 빌드 실패로 보아 **AMI 를 deregister + 스냅샷 삭제**한다. **40분치 산출물이 날아갔다.** `packer validate` 는 통과한다. 가드 (o) 가 이것을 잡는다.

⚠️ **`packer build` 는 사용자 승인이 필요한 동작이다.**

# Related Specs

- `infra/demo/aws/README.md` — 재현 절차
- `infra/demo/demo-boot.sh` · `infra/demo/demo-up.sh` · `infra/demo/projects.sh`
- `projects/ecommerce-microservices-platform/docker-compose.yml` — 유일하게 리밋을 선언하는 compose

# Related Contracts

없음 (인프라 전용, API/이벤트 계약 무변경).

# Edge Cases

- **`OOMKilled=false, ExitCode=0` 이 OOM 을 숨긴다.** 397 실측. `dmesg` 가 권위.
- **`docker stats` 의 RSS 는 호스트 회계에 따라 크게 다르다**(397: WSL2 ↔ ubuntu 러너 **2.2배**). 데모 호스트(ubuntu on EC2)의 숫자를 **다른 호스트의 판정에 쓰지 않는다.**
- **`terraform destroy` 는 SSM 월 사용량 카운터를 리셋하고 `api_base_url` 을 바꾼다.** 재생성 시 `config.js` 도 다시 렌더된다.
- **인스턴스는 EIP 가 없어 재시작마다 공인 IP 가 바뀐다.** 도메인은 매 부팅 IMDSv2 에서 파생된다.
- **as-baked AMI 의 `/opt/monorepo-lab` 은 `f5288a7b1` 에 동결**돼 있다(부팅 시 `git pull` 없음). compose 를 손으로 고칠 때 **내가 무엇을 바꿨는지 인스턴스에서 직접 확인**한다 — 로컬 저장소의 내용을 가정하지 않는다.
- **웜업은 약 10분**이다(389 실측). 4분 시점에 console 은 시작도 안 했다. **성급한 측정은 "안정화 후" 가 아니다.**

# Failure Scenarios

- **가장 위험: 세 번째 추측을 저장소에 심는 것.** 389 는 후보 하나를, 397 은 다른 하나를 적었다. **증거 없이 셋째를 추가하면 다음 사람은 세 개의 가설을 물려받고 그중 어느 것도 검증되지 않았다는 사실을 모른다.** 판정 불가는 **판정 불가로 적는다.**
- **AC-6 을 빠뜨리는 것 — 그러면 이 task 는 자기가 고친 것을 배포하지 않고 끝난다.** 세 질문에 완벽히 답하고 코드를 고쳐도, **AMI 를 다시 굽지 않으면 데모는 여전히 옛 커밋으로 돈다.** `MONO-367` 이 이름 붙인 실패 모드가 정확히 이것이다: ***"일을 잘못한다" 가 아니라 "일을 끝내는 것이 일을 끝낸 것처럼 보인다".*** `main` 이 초록인 것은 데모가 고쳐졌다는 증거가 **아니다.**
- **AC-6 을 먼저 하는 것.** 재굽기가 **재현 대상을 없앤다** — as-baked AMI 가 결함을 보인 바로 그 대상이다.
- **새 AMI 가 무엇을 담았는지 확인하지 않는 것.** `git clone --depth 1 --branch main` 은 *bake 시점의* main 이고, **성공한 bake 가 옛 커밋을 담았을 수 있다.** 부팅 후 인스턴스 안에서 kafka 리밋을 **런타임(`docker inspect`)에서** 확인한다 — *선언은 아무것도 강제하지 않는다*는 것이 이 티켓의 전제다.
- **"재시작 횟수 = 원인" 을 다시 하는 것.** 397 이 이걸로 틀렸다. AC-1 은 그것을 금지한다.
- **관측값을 가드로 승격시키는 것.** 397 이 이 축에서 두 번 실패했다(RSS·JVM 힙 둘 다 러너에서 결함을 통과시켰다). 호스트 의존 측정값은 **문서를 고치는 데 쓰고 술어로 쓰지 않는다.**
- **인스턴스를 켠 채로 잊는 것.** AC-5. max-runtime 가드(3시간)와 월 예산 가드(600분)가 있지만 **그것은 안전망이지 계획이 아니다.**

# Notes

- 분석·구현 = **Opus** (실측 설계가 핵심이다 — 397 이 보여줬듯, 이 클래스의 작업은 *무엇을 재느냐*에서 틀린다).
- 선행: `TASK-MONO-366`(데모 승격) · `TASK-MONO-389`(정문) · `TASK-MONO-397`(kafka 512M).
- **`terraform apply` / `destroy` 는 사용자 승인이 필요한 동작이다.**
