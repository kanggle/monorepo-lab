# Task ID

TASK-BE-459

# Title

Author ADR-008 (PROPOSED) — `/api/internal/**` 내부 엔드포인트 인증 경계를 네트워크-단독 → 앱-레이어 방어심층으로 승격

# Status

done

# Owner

backend

# Task Tags

- adr
- docs

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

2026-06-29 code-marker discovery 스윕에서, `auth-service` `SecurityConfig` 의 `TASK-BE-118-fix-002` TODO("내부 클러스터 IP 대역 제한(NetworkPolicy) 또는 mTLS/서비스계정 토큰 기반 인증을 별도 ADR로 승격 후 여기서 강제할 것")가 **deferred 후 미티켓** 상태로 잔존함이 확인되었다. `/api/internal/**`(auth republish 등)는 현재 필터에서 `permitAll()` 이라 **앱-레이어 인증/인가/감사가 0**이고 보안 자세가 단일 네트워크 경계에 전적으로 의존한다.

이 결정(어느 메커니즘을 어디서 강제할지)은 서비스 경계·운영 워크플로·비밀 관리 자세를 확정하므로 코드로 즉흥 결정 불가 → **ADR(HARDSTOP-09)**. 본 task 는 그 결정을 `ADR-008 PROPOSED` 로 정식화한다. **ACCEPTANCE 와 per-service 구현은 별개 후속 task**(user-gated).

---

# Scope

## In Scope

- `projects/ecommerce-microservices-platform/docs/adr/ADR-008-internal-endpoint-auth-boundary.md` (PROPOSED) 작성 — Context(현행 permitAll + 네트워크-단독 경계, 위협 모델) / Decision(D2 네트워크경계+앱-레이어 토큰 게이트, D3 constant-time 공유시크릿 + prod fail-closed, D4 NetworkPolicy 승격, D5 감사, D6 적용범위) / 버린 대안(네트워크-단독·mTLS/메시·IAM-JWT) / Consequences.
- ADR README 인덱스 행 추가(ADR-008, Proposed).

## Out of Scope

- ADR ACCEPTANCE(user-gated, 별도).
- 실제 구현(필터 토큰 게이트, NetworkPolicy 매니페스트, 감사 로깅) — ACCEPTED 후 per-service task(auth/order/batch-worker).
- `platform/security-rules.md`/서비스 `architecture.md` 내부-경로 섹션 정합 — 구현 task 범위.
- `order-service`/`batch-worker` 의 현행 필터 자세 변경(채택 task 에서 감사·적용).

---

# Acceptance Criteria

- [ ] `ADR-008-internal-endpoint-auth-boundary.md` 가 ADR 포맷(Status/Date/Authors/History + Context + Decision + 버린 대안 + Consequences)을 따르며 Status=`PROPOSED`.
- [ ] 결정(D2)이 방어심층(네트워크 레이어 유지 + 앱-레이어 게이트)을 명시하고, 3개 버린 대안(네트워크-단독·mTLS·IAM-JWT)을 각 기각 사유와 함께 기록.
- [ ] `scale_tier=startup` / `data_sensitivity=internal`(PROJECT.md) 을 비례성 근거로 인용.
- [ ] ADR README 인덱스에 ADR-008 행(Proposed) 추가.
- [ ] doc-only — 서비스 코드/스펙/매니페스트 무변경(`git diff --stat` 가 `docs/adr/` 2파일 + 본 task 만).

---

# Related Specs

> Step 0: `PROJECT.md`(domain=ecommerce... 실제 frontmatter 확인) + `rules/common.md` + 해당 domain/trait. 본 task 는 ecommerce 프로젝트-내부 doc 작업.

- `projects/ecommerce-microservices-platform/apps/auth-service/.../config/SecurityConfig.java` (트리거 TODO + 현행 permitAll)
- `projects/ecommerce-microservices-platform/apps/auth-service/.../controller/AdminUserRepublishController.java` (영향 표면)
- `platform/security-rules.md` (공유 내부-경로 가이드 정합 대상 — 구현 시)

# Related Skills

- ADR 작성(`.claude/skills/...` ADR 관련) — 해당 시.

---

# Related Contracts

- 없음 — 와이어/이벤트 컨트랙트 변경 없음(결정 기록 only).

---

# Edge Cases

- `order-service`/`batch-worker` 의 `/api/internal/**` 현행 자세가 auth-service 와 다를 수 있음 → ADR 은 현재상태 단정을 검증된 auth-service 로 한정하고, 정책은 전 서비스 적용으로 기술(per-service 감사는 채택 task).
- prod fail-closed 결정은 시크릿 미주입 배포를 의도적으로 막음 → ACCEPTED 후 배포 체크리스트 반영 필요(ADR Consequences 에 명시).

---

# Failure Scenarios

- ADR 이 특정 메커니즘을 ACCEPTED 인 양 단정 → PROPOSED 자세 위반. 본 task 는 PROPOSED + user-gated ACCEPTANCE 를 명시해야 함.
- 버린 대안 누락(특히 "현행 네트워크-단독 유지") → 결정 근거 불완전. 3개 대안 모두 기록 필수.

---

# Test Requirements

- 없음(doc-only). 검증 = ADR 포맷/내용 리뷰 + README 인덱스 정합.

---

# Definition of Done

- [ ] ADR-008 PROPOSED + README 행 작성
- [ ] AC 전부 충족
- [ ] doc-only 확인
- [ ] Ready for review
