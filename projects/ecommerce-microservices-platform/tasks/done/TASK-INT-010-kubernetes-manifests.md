# Task ID

TASK-INT-010

# Title

Kubernetes 매니페스트 작성 — 프로덕션 배포를 위한 Deployment, Service, Ingress 구성

# Status

done

# Owner

backend

# Task Tags

- deploy

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

프로덕션 환경 배포를 위한 Kubernetes 매니페스트를 작성한다. 각 서비스별 Deployment, Service, ConfigMap, Ingress를 구성하고, 리소스 제한, 헬스체크 프로브, 롤링 업데이트 전략을 적용한다.

---

# Scope

## In Scope

### 디렉토리 구조
- `k8s/base/` — 공통 매니페스트
- `k8s/services/` — 서비스별 매니페스트
- `k8s/ingress/` — Ingress 규칙

### 백엔드 서비스 (7개)
- Deployment: replicas, resource limits/requests, liveness/readiness/startup probes
- Service: ClusterIP
- ConfigMap: 환경변수 (비밀번호 제외)

### 프론트엔드 서비스 (2개)
- Deployment: resource limits, health probes
- Service: ClusterIP

### Ingress
- gateway-service 라우팅
- web-store, admin-dashboard 라우팅
- TLS 설정 (placeholder)

### 인프라 서비스
- 인프라(Kafka, Redis, Elasticsearch, PostgreSQL)는 매니페스트에서 제외 (관리형 서비스 사용 가정)
- 외부 서비스 연결을 위한 ExternalName Service 또는 ConfigMap 정의

## Out of Scope

- Helm 차트 작성
- ArgoCD / Flux 등 GitOps 설정
- 실제 클러스터 배포 및 검증
- Secret 관리 도구 연동 (Vault, AWS Secrets Manager)

---

# Acceptance Criteria

- [ ] 모든 애플리케이션 서비스(백엔드 7개 + 프론트엔드 2개)에 Deployment, Service가 정의된다
- [ ] 리소스 requests/limits가 docker-compose 설정과 일관성 있게 적용된다
- [ ] liveness, readiness, startup probe가 모든 서비스에 설정된다
- [ ] 롤링 업데이트 전략이 적용된다 (maxSurge, maxUnavailable)
- [ ] Ingress 규칙이 정의된다
- [ ] `kubectl apply --dry-run=client -f k8s/` 검증을 통과한다

---

# Related Specs

- `specs/platform/deployment-policy.md`
- `specs/platform/observability.md`

# Related Skills

_(없음)_

---

# Related Contracts

_(없음)_

---

# Target Service

- `k8s/` 디렉토리 전체 (신규)

---

# Architecture

_(해당 없음)_

---

# Edge Cases

- 서비스 간 의존성 순서 (initContainer 또는 depends-on 대안)
- ConfigMap 변경 시 Pod 재시작 전략 (annotation hash)
- 프론트엔드 빌드 타임 환경변수 처리 (이미지 빌드 시 주입)

---

# Failure Scenarios

- Pod OOMKilled 시 resource limits 조정 필요
- Probe 실패 시 Pod 재시작 반복 (CrashLoopBackOff) → startup probe 타임아웃 조정
- Ingress controller 미설치 시 Ingress 리소스 무효 → README에 사전 요구사항 명시

---

# Test Requirements

- `kubectl apply --dry-run=client` 로 매니페스트 유효성 검증
- `kubeval` 또는 `kubeconform` 으로 스키마 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
