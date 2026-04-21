# Task ID

TASK-INT-021

# Title

Kubernetes 보안 강화 — SecurityContext 추가, NetworkPolicy 정의, secrets 관리 개선

# Status

done

# Owner

integration

# Task Tags

- deploy
- code

# Goal

K8s 매니페스트에 보안 설정이 누락되어 프로덕션 배포 시 보안 위험이 존재한다.

현재 문제:
1. 모든 디플로이먼트에 `securityContext` 미설정 → 컨테이너가 root 권한으로 실행 가능
2. `NetworkPolicy` 미정의 → 모든 pod 간 무제한 통신 가능
3. `secrets.yaml`에 평문 `"CHANGE_ME_IN_PRODUCTION"` 커밋됨 → 시크릿 관리 부재
4. 이미지 태그가 `latest` → 재현 불가능한 배포
5. `imagePullPolicy` 미설정 → 예측 불가능한 이미지 풀링
6. `checksum/config` 어노테이션이 `"PLACEHOLDER"` → ConfigMap 변경 감지 불가

# Scope

## In Scope

- 전체 K8s 디플로이먼트에 `securityContext` 추가
- 서비스 간 통신 제한을 위한 `NetworkPolicy` 정의
- `secrets.yaml`에 SealedSecrets 또는 외부 시크릿 참조 패턴 적용
- 이미지 태그를 버전 기반으로 변경 (또는 템플릿 변수화)
- `imagePullPolicy` 명시
- `checksum/config` 어노테이션 실효성 확보

## Out of Scope

- Vault/AWS Secrets Manager 통합 (인프라 의존)
- RBAC 전면 개선
- Service Mesh 도입
- PersistentVolumeClaim 정의 (별도 태스크)

# Acceptance Criteria

- [ ] 모든 디플로이먼트에 `runAsNonRoot: true`, `capabilities.drop: ["ALL"]` 설정이 적용된다
- [ ] 서비스 간 필요한 통신만 허용하는 NetworkPolicy가 정의된다
- [ ] `secrets.yaml`에 평문 시크릿이 제거되고 외부 참조 또는 SealedSecrets 패턴이 적용된다
- [ ] 이미지 태그가 `latest` 대신 버전 변수(`${IMAGE_TAG}`)를 사용한다
- [ ] `imagePullPolicy: IfNotPresent`가 명시된다
- [ ] `checksum/config` 어노테이션이 실제 ConfigMap 해시를 참조하도록 변경된다

# Related Specs

- `specs/platform/deployment-policy.md`
- `specs/platform/security-rules.md`

# Related Skills

- `.claude/skills/infra/kubernetes.md`

# Related Contracts

없음

# Participating Components

- `k8s/base/secrets.yaml`
- `k8s/*/deployment.yaml` (전체 서비스)
- `k8s/ingress/ingress.yaml`

# Trigger

수동 — 보안 리뷰 결과 반영

# Expected Flow

1. SecurityContext 템플릿 정의
2. 전체 디플로이먼트에 SecurityContext 적용
3. 서비스 간 통신 매트릭스 정의
4. NetworkPolicy YAML 생성
5. secrets.yaml 리팩토링
6. 이미지 태그 및 pullPolicy 수정
7. checksum 어노테이션 수정

# Edge Cases

- init container가 필요한 경우 별도 securityContext
- readOnlyRootFilesystem 설정 시 쓰기가 필요한 경로에 emptyDir 마운트
- NetworkPolicy 적용 후 서비스 간 통신 단절

# Failure Scenarios

- SecurityContext로 인한 pod 시작 실패 → 로그 확인 후 필요 권한 추가
- NetworkPolicy 잘못 설정 시 서비스 간 통신 차단 → 점진적 적용
- SealedSecrets 복호화 실패 → 컨트롤러 로그 확인

# Test Requirements

- K8s 매니페스트 lint (kubeval 또는 kube-score)
- NetworkPolicy 적용 후 서비스 간 통신 검증
- pod 정상 시작 확인

# Definition of Done

- [ ] Integration flow implemented
- [ ] Contracts updated first if needed
- [ ] Failure handling covered
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
