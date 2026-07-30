# Task ID

TASK-MONO-490

# Title

`iam.local` / `wms.local` 는 표준 `<project>:up` 흐름에서 Traefik 라우팅되지 않음 — 브링업 문서 정정

# Status

review

# Owner

monorepo

# Task Tags

- infra
- docs

---

# Required Sections (must exist)

- Goal
- Scope
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

`TEMPLATE.md § Local bring-up sequence` / `§ Per-project bring-up matrix`는 `pnpm iam:up` 이후 `pnpm <consumer>:up`(ecommerce/fan-platform/scm/erp/finance)을 실행하면 OIDC consumer 게이트웨이가 부팅 시 `http://iam.local/oauth2/jwks`를 검증(fail-fast)한다고 명시한다. 그러나 실제로는:

- `projects/iam-platform/docker-compose.yml`과 `projects/wms-platform/docker-compose.yml`은 **인프라(mysql/postgres/redis/kafka 등)만** 포함한다. `gateway-service`/`auth-service` 같은 앱 서비스는 이 파일에 없고 Traefik 라벨도 전혀 없다 — 호스트 `./gradlew bootRun` 또는 격리망(`docker-compose.e2e.yml`, 자체 `iam-e2e`/`wms-e2e` 네트워크 + 호스트 포트 `18080` 등)으로만 뜬다.
- `pnpm iam:up`(=`docker compose --project-directory projects/iam-platform up -d`, `package.json`)이 올리는 컨테이너 중 `Host(\`iam.local\`)` 라우터를 가진 것은 없다(grep 확인: `iam-kafka-ui`/`iam-grafana` 라우터만 존재). `wms.local`도 동일 패턴 — `docker-compose.e2e.yml`에만 `wms` 라우터가 있고 `docker-compose.yml`엔 없다.
- 즉 문서가 시키는 대로 프로젝트별 `:up` 스크립트만으로 "모든 서비스를 한 번에" 띄우면, ecommerce/fan-platform/scm/erp/finance의 게이트웨이는 `iam.local`에 도달할 방법이 없어 JWKS 조회에 실패 → fail-fast 가능성이 높다.

이 갭 자체는 이미 `TASK-MONO-358`(DONE)이 의도적으로 **데모 전용**(`infra/demo/iam-traefik.override.yml`)으로만 해소했고, 일반 로컬 개발 흐름의 project compose는 "바이트 동일 유지"가 그 task의 Acceptance Criteria였다(§ Out of Scope 참조) — 즉 이것은 미해결 버그가 아니라 **의도된 설계**다. 문제는 `TEMPLATE.md`의 브링업 문서가 이 제약을 명시하지 않아, 문서만 보고 "전체 기동"을 시도하는 개발자가 원인 불명의 fail-fast를 만나게 된다는 점이다. (이 문서 갭이 실제로 `projects/iam-platform/docker-compose.iamlocal.yml` 등 임시·미커밋 로컬 데모 오버레이가 반복 발명되는 근본 원인으로 보인다.)

**이 task는 아키텍처를 바꾸지 않는다** — `iam-platform`/`wms-platform`의 앱 서비스를 기본 compose에 컨테이너화할지 여부는 별도 설계 결정(HARDSTOP-09 대상)이다. 이 task는 **현재 동작을 정확히 문서화**해서, "전체 기동" 시도가 어디서 왜 멈추는지 예측 가능하게 만드는 것까지만 다룬다.

---

# Scope

## In Scope

- `TEMPLATE.md § Local bring-up sequence` 3단계("IAM 먼저 띄우고 consumer gateway가 fail-fast 검증") 옆에 **명시적 제약**을 추가: `pnpm iam:up`/`pnpm wms:up`은 인프라만 올리며 `iam.local`/`wms.local`은 Traefik에 라우팅되지 않는다. Consumer 게이트웨이가 실제로 기동에 성공하려면 IAM/WMS 앱 서비스가 별도로(`bootRun` 또는 데모 오버레이 경유) 떠 있어야 한다.
- `TEMPLATE.md § Per-project bring-up matrix`의 iam-platform/wms-platform 행에 각주 추가 — "Primary hostname"이 `pnpm <name>:up`만으로는 응답하지 않음을 표시.
- `infra/demo/iam-traefik.override.yml`(TASK-MONO-358 산출물)을 로컬 개발자가 필요 시 참조할 수 있는 기존 해법으로 문서에서 명시적으로 가리킨다(새 파일 신설 아님, 기존 파일 링크).

## Out of Scope

- `iam-platform`/`wms-platform`의 `docker-compose.yml`에 앱 서비스 컨테이너화 + Traefik 라벨을 추가하는 것 (아키텍처 결정 — 별도 ADR/task).
- 새로운 "로컬 전용 iam Traefik 오버레이" 공식 파일 신설.
- `docker-compose.e2e.yml`(CI 소유) 변경.
- fan-platform의 `JWT_JWKS_URI` 기본값 오류(별도 task — `TASK-FAN-BE-037`, `projects/fan-platform/tasks/ready/`).

---

# Acceptance Criteria

- [ ] `TEMPLATE.md`의 Local bring-up sequence 3단계에 iam/wms 앱 서비스 컨테이너화 부재 및 그 결과(consumer fail-fast 가능성)가 명시된다.
- [ ] Per-project bring-up matrix의 iam-platform/wms-platform 행에서 "Primary hostname"이 무조건적 사실이 아님이 드러난다(각주 또는 비고).
- [ ] 문서 변경 외 코드/compose 변경 없음(`git diff --stat`으로 확인 — `TEMPLATE.md`만 변경).
- [ ] `infra/demo/iam-traefik.override.yml` 링크가 정확한 상대경로로 걸린다(파일 존재 확인).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — 이 task는 모노레포 레벨(공유 `TEMPLATE.md`)이므로 `rules/common.md`만 적용된다(project domain/traits 무관).

- `TEMPLATE.md § Local Network Convention`, `§ Local bring-up sequence`, `§ Per-project bring-up matrix`
- `CLAUDE.md § Local Network Convention` (요약본 — 마스터인 `TEMPLATE.md`가 바뀌면 이 요약이 stale 해지지 않는지 함께 확인)

# Related Skills

- (없음 — 순수 문서 수정)

---

# Related Contracts

- 없음 (문서 전용 변경, API/이벤트 계약 무관)

---

# Edge Cases

- 개발자가 `iam.local`을 bootRun 없이도 도달 가능하다고 오해하고 디버깅 시간을 낭비하는 경우 — 문서에 "왜"까지 적어 원인을 즉시 알 수 있게 한다.
- `infra/demo/iam-traefik.override.yml`은 AWS 데모 토폴로지 전제(예: `DEMO_DOMAIN`, network alias)로 작성돼 있어 로컬에 그대로 재사용 시 조정이 필요할 수 있다 — 이를 "그대로 복붙 가능"이 아니라 "참고 구현"으로 정확히 표현한다.

---

# Failure Scenarios

- 문서만 고치고 실제 동작(어느 컨테이너가 뜨는지)이 향후 바뀌었는데 문서가 다시 stale해지는 것을 방지하기 위해, 이 task의 근거(grep 결과)를 커밋 메시지/PR 본문에 남긴다.
