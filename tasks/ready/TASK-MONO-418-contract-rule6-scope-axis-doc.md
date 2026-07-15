# Task ID

TASK-MONO-418

# Title

계약 규칙 6 이 머신 토큰의 인가 축(scope)을 말하지 않는다 — 규칙만 읽고 게이트웨이를 만들면 role-only 로 게이트해 `client_credentials` 트래픽을 403 시킨다

# Status

ready

# Owner

monorepo

# Task Tags

- docs
- contract

---

# Goal

`platform/contracts/jwt-standard-claims.md:130`(규칙 6)은 admission 을 **role** 로만 서술한다:

> Admit iff the token carries ≥ 1 role valid for the requested surface; otherwise respond `403 Forbidden`.

그러나 실제 구현과 다른 스펙은 **role 또는 scope** 다:

- `TASK-MONO-416` 이 배선한 `RoleAdmissionFilter`(fan·erp·scm·finance)는 `RoleAdmissions.roleOrScope()` — role 없어도 **scope 있으면 통과**.
- 그 근거는 계약이 아니라 **서비스 스펙**에 있다: `projects/scm-platform/specs/integration/iam-integration.md` Edge Case E3 / line 129(*"유효 토큰이지만 scope/role 부족 → 403 FORBIDDEN"*). scm 은 backend-only v1 이라 `client_credentials`(scope 있음, roles 없음)가 **primary** 호출 형태다.

⇒ **규칙 6 만 읽고 새 게이트웨이를 만드는 사람은 role-only 로 게이트해 머신 트래픽을 전부 403** 시킨다(TASK-MONO-416 구현자가 실제로 빠질 뻔한 덫). 계약이 **정경(우선순위 2~5)** 인데 규칙 6 이 이 축을 침묵하는 것은 *정경이 진실의 절반만 말하는* 드리프트다([[feedback_repo_knows_what_it_does_not_say]]).

이건 **새 규칙 신설이 아니라 이미 작동 중인 동작(scm E3 + MONO-416 코드)의 문서화 정합**이다 — 따라서 ADR 게이트(HARDSTOP-09)가 아니라 문서 편집이다. 단 규칙 6 본문을 건드리므로 문구는 보수적으로.

---

# 🔴 AC-0 (착수 = 재현)

- [ ] `RoleAdmissions.roleOrScope()` 의 현재 술어(role 또는 scope)와 규칙 6 본문의 차이를 실측 재확인. scm `iam-integration.md` E3/line 129 가 여전히 scope 축을 명시하는지 확인(정경 홈).
- [ ] 다른 게이트웨이(wms/ecommerce)의 admission 이 scope 를 어떻게 다루는지 확인 — 규칙 6 문구가 그들과도 정합해야 한다.

# Scope

## In Scope

- `platform/contracts/jwt-standard-claims.md` 규칙 6 에 **머신(client_credentials) 토큰은 role 이 아니라 `scope` 축으로 인가되며 게이트웨이 admission 은 "role 또는 scope 부재 시 403"** 이라는 정합 문장 추가(한두 줄). scm E3/line 129 를 정경 근거로 인용.

## Out of Scope

- 규칙 6 의 role 기반 프레이밍 재작성 — role 축은 그대로, scope 축을 **명시적으로 추가**만.
- 코드 변경(MONO-416 이 이미 role-or-scope 구현). scope 검증의 세부(어느 scope 가 유효한가)는 다운스트림 서비스 몫이라 계약 편집 대상 아님.
- 게이트웨이별 유효 scope 목록 나열(도메인 스펙 몫).

---

# Acceptance Criteria

- [ ] **AC-0** 위 재현.
- [ ] **AC-1** 규칙 6 이 role 축과 scope 축(머신 토큰)을 모두 서술한다 — role-only 로 읽힐 여지 제거. 정경 근거(scm E3/line 129) 인용.
- [ ] **AC-2 (드리프트 없음)** 편집이 `JWT claims registry` CI 잡(minted claims vs jwt-standard-claims.md)과 어긋나지 않는다.
- [ ] **AC-3** doc-lint / 관련 링크 무손상.

---

# Related Specs

- `platform/contracts/jwt-standard-claims.md` (편집 대상, 규칙 6)
- `projects/scm-platform/specs/integration/iam-integration.md` (Edge Case E3 / line 129 — scope 축 정경 근거)

# Related Contracts

- `platform/contracts/jwt-standard-claims.md`

# Target Service

없음(계약 문서 편집).

---

# Edge Cases

- **정경 계층 충돌 없음** — 규칙 6 은 계약(정경)이고 scm E3 는 서비스 스펙(하위)이다. 계약이 서비스 스펙의 동작을 **문서화**하는 것이므로 방향이 맞다(하위를 상위로 승격이 아니라 상위가 하위 동작을 인정).
- **wms/ecommerce** — 이들이 scope 를 안 쓰면 문장이 그들에겐 no-op(무해). 재측정에서 확인.

# Failure Scenarios

- **규칙 6 을 통째로 재작성한다** → 다른 게이트웨이 행/의미 회귀. 완화 = scope 축 **추가**만, role 프레이밍 보존.
- **계약에 유효 scope 목록을 박는다** → 도메인 지식이 공유 계약에 누출(HARDSTOP-03 계열). 완화 = "scope 축으로 인가" 만, 세부는 도메인.

---

# Provenance

`TASK-MONO-416` 이 남긴 문서 정합 후속. 416 은 규칙 6 무변경(finance 행만) 제약 하에 role-or-scope 를 구현했고, 그 결과 계약 본문이 구현·scm E3 와 어긋난 채 남았다. 저위험 doc.

분석=Opus 4.8 / 구현 권장=**Sonnet** (문서 정합, 결정 없음).

[[project_gateway_role_admission_gap_2026_07_15]] · [[feedback_repo_knows_what_it_does_not_say]]
