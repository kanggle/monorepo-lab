# Task ID

TASK-INT-021-fix-001

# Title

TASK-INT-021 리뷰 수정 — default-deny Egress 추가, seccompProfile, automountServiceAccountToken, PSS 레이블, Ingress 보안 헤더, runAsUser/runAsGroup, PDB, 검증 스크립트 보완

# Status

done

# Owner

integration

# Task Tags

- deploy
- code

# Goal

TASK-INT-021 리뷰에서 발견된 보안 개선사항 수정.

1. default-deny NetworkPolicy에 Egress 차단 누락 → 미정의 pod의 아웃바운드 무제한 허용
2. seccompProfile: RuntimeDefault 미설정 → restricted PSS 미충족
3. automountServiceAccountToken: false 미설정 → 불필요한 K8s API 토큰 노출
4. 네임스페이스에 Pod Security Standards 레이블 미적용
5. runAsUser/runAsGroup 미지정 → UID 비결정적
6. Ingress 보안 헤더 부족 (HSTS, X-Frame-Options 등)
7. PodDisruptionBudget 미정의 → 노드 드레인 시 가용성 위험
8. validate-security.sh에 신규 보안 항목 검증 누락

# Scope

## In Scope

- default-deny.yaml에 Egress policyType 추가
- 전체 deployment에 seccompProfile, automountServiceAccountToken, runAsUser, runAsGroup 추가
- namespace.yaml에 PSS 레이블 추가
- ingress.yaml에 보안 헤더 어노테이션 추가
- 전체 서비스 PDB 생성
- validate-security.sh 검증 항목 추가

## Out of Scope

- SealedSecret 실제 값 교체 (운영 환경 의존)
- RBAC 변경
- Service Mesh 도입

# Acceptance Criteria

- [ ] default-deny NetworkPolicy가 Ingress와 Egress를 모두 차단한다
- [ ] 모든 deployment에 seccompProfile: RuntimeDefault가 설정된다
- [ ] 모든 deployment에 automountServiceAccountToken: false가 설정된다
- [ ] 모든 deployment에 runAsUser: 1000, runAsGroup: 1000이 설정된다
- [ ] ecommerce 네임스페이스에 pod-security.kubernetes.io/enforce: restricted 레이블이 적용된다
- [ ] Ingress에 HSTS, X-Frame-Options, X-Content-Type-Options 등 보안 헤더가 설정된다
- [ ] replicas >= 2인 서비스에 PodDisruptionBudget이 정의된다
- [ ] validate-security.sh가 신규 보안 항목을 검증한다

# Related Specs

- `specs/platform/deployment-policy.md`
- `specs/platform/security-rules.md`

# Related Skills

- `.claude/skills/infra/kubernetes-deploy.md`

# Related Contracts

없음

# Participating Components

- `k8s/network-policies/default-deny.yaml`
- `k8s/services/*/deployment.yaml`
- `k8s/base/namespace.yaml`
- `k8s/ingress/ingress.yaml`
- `k8s/services/*/pdb.yaml` (신규)
- `k8s/scripts/validate-security.sh`

# Edge Cases

- PDB minAvailable 값이 replicas 수보다 크면 드레인 불가
- configuration-snippet 어노테이션이 ingress controller 버전에 따라 미지원 가능

# Failure Scenarios

- seccompProfile 설정 후 특정 syscall 필요 시 pod 시작 실패 → RuntimeDefault로 대부분 커버
- PSS enforce: restricted 적용 후 기존 비준수 pod 배포 거부 → 이미 restricted 조건 충족하므로 문제 없음

# Test Requirements

- validate-security.sh 실행 시 모든 신규 항목 PASS
- kubectl apply --dry-run=client 성공

# Definition of Done

- [ ] Integration flow implemented
- [ ] Contracts updated first if needed
- [ ] Failure handling covered
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
