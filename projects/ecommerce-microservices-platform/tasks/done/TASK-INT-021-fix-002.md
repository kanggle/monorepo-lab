# Task ID

TASK-INT-021-fix-002

# Title

TASK-INT-021 리뷰 수정 2차 — Ingress server-snippets 대체, batch-worker NetworkPolicy kubelet probe 허용, web-store/admin-dashboard SSR egress 확인

# Status

done

# Owner

integration

# Task Tags

- deploy
- code

# Goal

TASK-INT-021 + fix-001 완료 후 2차 리뷰에서 발견된 보안 및 안정성 이슈 수정.

1. Ingress `server-snippets` 어노테이션이 Ingress NGINX Controller 1.9.0+에서 CVE-2021-25742 대응으로 기본 비활성화됨 → 대체 방식으로 전환 필요
2. batch-worker NetworkPolicy `ingress: []`가 CNI 구현에 따라 kubelet health probe를 차단할 수 있음 → 명시적 허용 또는 문서화 필요
3. web-store/admin-dashboard NetworkPolicy에 gateway-service egress 미정의 → SSR 사용 시 API 호출 불가

# Scope

## In Scope

- ingress.yaml의 `server-snippets` 어노테이션을 개별 헤더 어노테이션 또는 Ingress NGINX ConfigMap 글로벌 설정으로 대체
- batch-worker NetworkPolicy에 kubelet health probe ingress 허용 규칙 추가 (또는 CNI 동작 확인 후 문서화)
- web-store/admin-dashboard가 SSR인 경우 gateway-service로의 egress 규칙 추가

## Out of Scope

- SealedSecret 실제 값 교체 (운영 환경 의존)
- TLS Secret 생성 (cert-manager 등 외부 도구 관리)
- RBAC 변경

# Acceptance Criteria

- [ ] ingress.yaml에서 `server-snippets` 어노테이션이 제거되고 대체 방식으로 보안 헤더가 적용된다
- [ ] batch-worker가 NetworkPolicy 적용 후에도 kubelet health probe가 정상 동작한다
- [ ] web-store/admin-dashboard의 SSR 여부에 따라 NetworkPolicy egress가 올바르게 설정된다

# Related Specs

- `specs/platform/deployment-policy.md`
- `specs/platform/security-rules.md`

# Related Skills

- `.claude/skills/infra/kubernetes-deploy.md`

# Related Contracts

없음

# Participating Components

- `k8s/ingress/ingress.yaml`
- `k8s/network-policies/batch-worker.yaml`
- `k8s/network-policies/web-store.yaml`
- `k8s/network-policies/admin-dashboard.yaml`

# Trigger

수동 — 2차 보안 리뷰 결과 반영

# Expected Flow

1. Ingress NGINX 보안 헤더 적용 방식 조사 및 대체
2. batch-worker NetworkPolicy kubelet probe 허용 규칙 추가
3. web-store/admin-dashboard SSR 여부 확인 및 NetworkPolicy 수정
4. validate-security.sh에 신규 검증 항목 추가 (필요 시)

# Edge Cases

- Ingress NGINX Controller 버전에 따라 지원되는 어노테이션이 다름
- CNI별 kubelet probe 동작 차이 (Calico, Cilium, Flannel 등)

# Failure Scenarios

- 보안 헤더 대체 적용 후 헤더가 응답에 포함되지 않는 경우 → 브라우저 응답 헤더 확인
- kubelet probe 허용 범위가 너무 넓어 보안 약화 → 최소 범위(node CIDR + probe port)만 허용

# Test Requirements

- Ingress 응답 헤더에 HSTS, X-Frame-Options 등 포함 확인
- batch-worker pod 정상 시작 및 health probe 동작 확인
- validate-security.sh 전체 PASS

# Definition of Done

- [ ] Integration flow implemented
- [ ] Contracts updated first if needed
- [ ] Failure handling covered
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
