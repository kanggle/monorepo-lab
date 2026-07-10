# infra/demo — 온디맨드 포트폴리오 통합 데모 (TASK-MONO-336)

8개 프로젝트 전체(iam · ecommerce · wms · scm · fan · finance · erp · platform-console)를
**한 명령**으로 공유 Traefik 위에 기동한다. 온디맨드 데모 호스트(EC2 scale-to-zero)의
`demo-stack.service` 가 부팅 시 `demo-up.sh full` 을 호출하는 것을 전제로 한다.

## 왜 단일 compose 파일이 아니라 래퍼인가

`docker compose` 의 `include:` 와 `-f` 는 **같은 서비스 키를 조용히 하나로 병합**한다
(실측: `include`=첫째 승, `-f`=마지막 승, 에러 없음). 8개 프로젝트는 서로 다른
컨테이너인데도 제네릭 키를 공유한다 — `redis`×7, `kafka`×6, `postgres`×3, `mysql`×3,
`grafana`×3, `notification-service`×3. 따라서 단일 병합 파일은 7개 redis 중 6개를
소리없이 잃어 대부분 도메인이 뜨지 않는다.

해결: **각 프로젝트를 자신의 compose 프로젝트(`-p <slug>`)로** 띄운다. 프로젝트
네임스페이스가 키를 분리하므로 충돌이 사라지고, **각 프로젝트의
`docker-compose.yml` 은 한 바이트도 수정하지 않는다**(byte-unchanged 불변식).

전제 조건은 이미 충족돼 있다(코드 조사 결과):
- 모든 `container_name` 이 프로젝트 슬러그로 프리픽스됨 → 컨테이너명 충돌 0
- host `ports:` 는 traefik(80/443/8080)·ecommerce jaeger(16686)뿐 → 포트 충돌 0
- `traefik-net` 은 8개 프로젝트가 모두 `external: true` 로 참조, 정의자는 `infra/traefik`

## 사용법

```bash
# 핵심 경로만 (면접 콜드스타트 최소화: iam + ecommerce + wms + console)
bash infra/demo/demo-up.sh demo-core

# 전체 8개 프로젝트
bash infra/demo/demo-up.sh full

# 개발 중 이미지 빌드까지 (AMI 는 prebaked 라 데모 호스트에선 불필요)
DEMO_BUILD=1 bash infra/demo/demo-up.sh full

# 종료 (프로파일 무관 전체)
bash infra/demo/demo-down.sh
KEEP_TRAEFIK=1 bash infra/demo/demo-down.sh   # traefik-net 유지
```

기동 후 호스트네임 라우팅(Traefik):
`console.local` · `web.ecommerce.local` · `ecommerce.local` · `scm.local` ·
`fan-platform.local` · `finance.local` · `erp.local` · `kafka.<domain>.local` 등.

## 프로파일

| 프로파일 | 프로젝트 | 용도 |
|---|---|---|
| `demo-core` | iam · ecommerce · wms · console | 면접 핵심 데모. 콘솔은 부분 federation(iam+wms) |
| `full` | 8개 전부 | 콘솔 5/5 federated 포함 전체 |

> 리소스 주의: `full`(41 JVM 동시)은 RAM ~32–48GB. 저사양/로컬에서는 OOM/exit137
> 위험이 있으니 `demo-core` 부터 확인할 것. 실제 데모 호스트는 `m6i.4xlarge`(64GB) 기준.

## 프로젝트당 compose 파일이 여러 개일 수 있다 (TASK-MONO-342/344)

저장소에는 두 패턴이 공존한다:

| 패턴 | base | 풀스택 | 프로젝트 |
|---|---|---|---|
| 1 | 인프라 전용 | `docker-compose.e2e.yml` | **iam · wms** |
| 2 | 앱까지 전부 | — | scm · fan · finance · erp · ecommerce · console |

패턴 1 에 base 만 주면 **DB 만 뜨고 앱이 0개**다. iam 은 OIDC IdP 이므로 그 경우
전 도메인의 토큰 검증이 무너진다. 그래서 `projects.sh` 의 `COMPOSE[slug]` 는
**공백 구분 파일 목록**이고, 아래 가드 (e)가 회귀를 막는다.

크로스-프로젝트 env(무비밀번호 redis, wms→iam OIDC, 스텁 URL)는
[`demo.env`](demo.env) 에 있고 `demo-up.sh` 가 source 한다. 프로젝트 compose 는
byte-unchanged 로 둔다.

> ⚠️ **선행 빌드 필수**: Java 서비스 Dockerfile 은 `COPY build/libs/<svc>.jar` 다
> (도커 안에서 컴파일하지 않는다). `DEMO_BUILD=1` 전에 각 서비스 `bootJar` 와
> `monorepo/java-service-base:v1` 이미지가 준비돼야 한다. AMI 가 이를 prebake 한다.

## 회귀 방어 (TASK-MONO-341/344)

프로젝트 맵은 [`projects.sh`](projects.sh) **단일 출처** — `demo-up.sh` / `demo-down.sh` /
`verify-demo-wrapper.sh` 가 공통 source 한다.

```bash
bash infra/demo/verify-demo-wrapper.sh          # 정적 (a)~(e),(g)
bash infra/demo/verify-demo-wrapper.sh --live   # + (f) 실기동 증명 (redis 2개, 자동 teardown)
```

래퍼가 의존하는 불변식을 검증한다 — 하나라도 무너지면 데모가 **소리없이** 불완전 부팅된다:

| # | 불변식 | 깨지면 |
|---|---|---|
| (a) | 모든 compose 조합이 렌더된다 | 해당 프로젝트 미기동 |
| (b) | `container_name` 전역 유일 | docker 가 중복 이름 거부 |
| (c) | host `ports:` 전역 무충돌 | 포트 바인딩 실패 |
| (d) | 모든 `projects/*/docker-compose.yml` 이 맵에 등록 | **신규 프로젝트가 데모에서 조용히 누락** |
| (e) | **각 프로젝트가 `build:` 서비스를 ≥1개 기여** | **DB 만 뜨고 앱이 0개** (MONO-342 가 겪은 결함) |
| (g) | **미설정 compose 변수 0건** | **빈 비밀번호 → postgres 초기화 거부** (MONO-346 이 겪은 결함) |
| (f) | 같은 키 `redis` 가 별도 `-p` 로 공존 | 누군가 `include:` 로 되돌림 = 침묵 병합 회귀 |

> (g)는 **fresh clone 에서 권위**를 갖는다. 프로젝트 `.env` 는 gitignored 이므로
> 로컬에 실 `.env` 를 가진 개발자는 결손을 보지 못한다 — CI 러너와 데모 AMI 는 본다.
> 데모에 필요한 값은 전부 [`demo.env`](demo.env) 에 있어야 한다.

CI 잡 `demo-wrapper-smoke` (`.github/workflows/ci.yml`) 가 `infra/demo/**` ·
`infra/traefik/**` · `projects/*/docker-compose.yml` 변경 PR 에서 위를 자동 검증한다.
(필터는 순수-positive + `code-changed` AND → README-only 변경은 skip.)

## 검증 상태

- ✅ 9개 compose `docker compose config` 렌더 / `bash -n` / container_name 91개 유일 / host port 무충돌
- ✅ 커버리지 가드 네거티브 테스트 — 맵에서 프로젝트 제거 시 FAIL 확인
- ✅ (g) 네거티브 테스트 — `demo.env` 에서 `SETTLEMENT_DB_PASSWORD` 제거 시 exit 1 + 변수명 지목
- ✅ (g) fresh clone(=`.env` 부재) 조건에서 8 프로젝트 + traefik 미설정 변수 0건
- ✅ 실기동 증명 — `scm-platform-redis` + `fan-platform-redis` 동시 healthy (같은 compose 키 `redis`)
- ✅ include/-f 가 중복 키를 잃는다는 실측 확인(위 근거)
- ⏳ **`full`(41 JVM) 실기동 healthcheck 스모크는 EC2 권위** — GH 러너(16GB)·로컬 Windows
  (Docker VM 11.68GiB) 모두 물리적 불가. `m6i.4xlarge`(64GB) 필요.

## 남은 작업 (federation env 배선 — 별도 증분)

이 래퍼는 각 프로젝트를 **표준 구성**으로 띄운다. 콘솔이 5/5 도메인을 실제
`available:true` 로 렌더하려면 cross-domain OIDC issuer / per-domain base URL /
seed 등 런타임 federation env 배선이 필요하며, 이는 `tests/federation-hardening-e2e/`
데모 오버레이(MONO-170/174)가 이미 6도메인에 대해 해둔 것과 겹친다. 데모 호스트
정식화 시 그 오버레이 env 를 이 래퍼의 프로젝트별 `.env` 로 승격/재사용한다
(중복 재구현 금지). AWS Terraform/AMI/start-stop 은 scratchpad PoC `ondemand-demo/` 참조.
