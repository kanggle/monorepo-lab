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

## 검증 상태 (이 task)

- ✅ 8개 프로젝트 compose + traefik 가 각각 `docker compose config` 로 정상 렌더(문법·구조)
- ✅ 래퍼 스크립트 `bash -n` 문법 통과
- ✅ include/-f 병합이 중복 키를 잃는다는 실측 확인(위 근거)
- ⏳ **full/demo-core 실기동 스모크는 EC2/CI 권위** — 로컬 Windows 는 Docker/Testcontainers
  FLAKY + 41 JVM OOM 위험이라 이 host 에서 실행하지 않음(프로젝트 규율).

## 남은 작업 (federation env 배선 — 별도 증분)

이 래퍼는 각 프로젝트를 **표준 구성**으로 띄운다. 콘솔이 5/5 도메인을 실제
`available:true` 로 렌더하려면 cross-domain OIDC issuer / per-domain base URL /
seed 등 런타임 federation env 배선이 필요하며, 이는 `tests/federation-hardening-e2e/`
데모 오버레이(MONO-170/174)가 이미 6도메인에 대해 해둔 것과 겹친다. 데모 호스트
정식화 시 그 오버레이 env 를 이 래퍼의 프로젝트별 `.env` 로 승격/재사용한다
(중복 재구현 금지). AWS Terraform/AMI/start-stop 은 scratchpad PoC `ondemand-demo/` 참조.
