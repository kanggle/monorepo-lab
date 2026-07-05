# Task ID

TASK-BE-481

# Title

운영자 인증/인가 토큰 모델 가이드 신설 — 로그인(인증) → operator token / assume-tenant(인가) 2갈래, 교환(exchange), "어느 토큰을 언제", cross-org cap 개념 설명 (human-reference, 스펙 링크 전용·SoT 아님)

# Status

review

# Owner

backend

# Task Tags

- docs
- guide
- iam

---

# Goal

콘솔·도메인 서비스 개발자가 반복적으로 헷갈리는 **"어느 토큰을 왜 쓰나"** 를 한 곳에서 읽고 이해할 수 있는 **개념 가이드**를 IAM `docs/guides/` 에 신설한다. 세션 중 사용자와 정리한 멘탈 모델(1축 로그인=인증 → 2축 assume-tenant=인가, operator token 옆가지, 교환의 의미, cross-org cap)을 human-readable 내러티브로 옮긴다.

**배치 판정(사용자 확정)**: 루트 `docs/guides/` 가 아니라 **IAM `projects/iam-platform/docs/guides/`**. 이유: (a) 루트 guides 는 project-agnostic 강제 대상(CLAUDE.md shared 경계)이나 토큰 모델은 `auth-service`/`admin-service`/`/oauth2/token`/`assume-tenant` 를 명시해야 설명돼 부적격; (b) 토큰 발급·교환·스코핑은 IAM 도메인 소유. 

**SoT 아님**: 가이드는 권위 스펙(auth-service architecture.md, admin-service security.md, console-web architecture.md § Per-domain credential selection, console-integration-contract.md, ADR-014/020/045/023)을 **엮고 링크**하는 human-reference. 스펙 내용을 복제하지 않고 개념/멘탈모델 + "언제 무엇" 표 + 링크로 구성.

# Scope

## In scope

- `projects/iam-platform/docs/guides/operator-auth-token-model.md` (신규) — 섹션:
  1. **한눈에** — 1축(로그인=인증)·2축(assume-tenant=인가)·operator token(IAM 관리용 옆가지) 요약 다이어그램.
  2. **토큰 3종** — 로그인 토큰(base IAM OIDC), operator token(ADR-014 교환), assume-tenant 토큰(ADR-020 RFC 8693 교환) 각 성격·용도·수명.
  3. **교환(exchange)이란** — 로그인 증명 → 목적용 권한 토큰 재발급(여권→출입증 비유), 교환 시 서버 검증(operator 존재/assignment/entitlement), fail-CLOSED.
  4. **어느 토큰을 언제** — IAM 백엔드(`/api/admin/**`)=operator token / 도메인 게이트웨이=IAM OIDC(로그인 or assume-tenant) 결정표. console-web § Per-domain credential selection + #569 trust-boundary 로 링크(복제 금지).
  5. **인증 vs 인가** — 1축=authn, 2축·operator=authz 구분.
  6. **cross-org 확장(ADR-045)** — 협력사 assume 시 2축 토큰이 delegated slice 로 cap(BE-478), admin scope 불방출 한 문단 + ADR-045/rbac.md 링크.
  7. **권위 스펙 링크 목록** — 이 가이드는 개념, 정확한 규약은 각 스펙.
- (선택) `projects/iam-platform/docs/guides/` 인덱스/README 가 있으면 한 줄 등록.

## Out of scope

- 코드·스펙·계약 변경 0 (순수 신규 human-guide).
- console-side "자격 선택" 규칙 재서술(platform-console 소유 — 링크만).
- 스펙 내용 복제(SoT 는 스펙; 가이드는 내러티브+링크).
- 루트 `docs/guides/` 배치(project-agnostic 위반으로 배제).

# Acceptance Criteria

- [ ] **AC-1** `projects/iam-platform/docs/guides/operator-auth-token-model.md` 신설, 위 7섹션 포함, human-readable(다이어그램/표/비유).
- [ ] **AC-2** 토큰 3종(로그인/operator/assume-tenant)의 성격·교환 관계·용도가 명확히 구분되고, 1축=authn / 2축·operator=authz 로 정리.
- [ ] **AC-3** "어느 토큰을 언제" 결정표가 IAM 백엔드=operator token / 도메인=OIDC 로 정확(#569 불변식 언급), console-web § Per-domain credential selection 로 링크(복제 아님).
- [ ] **AC-4** cross-org cap(BE-478/ADR-045) 한 문단 — 2축 토큰이 delegated slice 로 cap·admin scope 불방출, ADR-045/rbac.md 링크.
- [ ] **AC-5** 가이드가 SoT 아님을 명시 + 권위 스펙 링크 목록(auth-service architecture.md, admin-service security.md, console-web architecture.md, console-integration-contract.md, ADR-MONO-014/020/045/023) — 모든 링크 경로 실재 확인.
- [ ] **AC-6** 코드/스펙/계약/시드/테스트 변경 0. CI markdown fast-lane.

# Related Specs

- `projects/iam-platform/specs/services/auth-service/architecture.md` § Assume-Tenant Exchange (2축 발급 흐름 — 이번 세션 BE-480 에서 cross-org cap step 추가됨).
- `projects/iam-platform/specs/services/admin-service/security.md` § IAM OIDC Subject-Token Validation (operator token 교환).
- `projects/platform-console/specs/services/console-web/architecture.md` § Per-domain credential selection (console 자격 선택 규칙 — 링크 대상).
- `projects/iam-platform/specs/contracts/http/internal/auth-to-admin.md` § delegatedScope (assignment-check cross-org 블록).
- `docs/adr/ADR-MONO-014`(operator-identity exchange), `ADR-MONO-020`(assume-tenant assignment), `ADR-MONO-045`(cross-org partner delegation), `ADR-MONO-023`(entitlement/IAM plane 분리).

# Related Contracts

- 변경 없음(개념 가이드).

# Edge Cases

- 가이드가 스펙과 어긋나지 않도록: 수치·claim 이름 등 세부는 스펙 링크로 위임하고, 가이드 본문은 개념 수준 유지(스펙 갱신 시 가이드가 stale 되는 표면 최소화).
- 링크 anchor 정확성(ADR/스펙 파일 실재 + 섹션명) — AC-5 에서 확인.

# Failure Scenarios

- 스펙-only 라 런타임 실패 없음. CI = markdown lane. 링크 깨짐만 주의(실재 확인).
- 가이드가 SoT 로 오인돼 스펙과 이중 유지되는 위험 → 상단에 "human-reference, 정확한 규약은 스펙" 배너 명시로 완화.
