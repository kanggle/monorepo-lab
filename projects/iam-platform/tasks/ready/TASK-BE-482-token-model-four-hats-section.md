# Task ID

TASK-BE-482

# Title

operator-auth-token-model.md 가이드에 "하나의 계정, 4개의 모자" 절 추가 — 한 로그인 계정이 관계에 따라 쓰는 4유형(소비자 / 내가 운영하는 회사 / 내가 다니는 회사 / 내 회사가 운영하는 다른 회사) 정리, 토큰 축(1축·operator·2축)에 매핑 (human-reference, 기존 가이드 확장)

# Status

ready

# Owner

backend (docs)

# Task Tags

- docs
- guide
- iam

---

# Dependency Markers

- **builds on**: TASK-BE-481(operator-auth-token-model.md 가이드 신설 — 1축/operator/2축 토큰 모델·교환·"어느 토큰을 언제"·cross-org cap). 본 절은 그 토큰 축 설명에 **관계(모자) 축**을 얹는 확장이다.
- **note (동기)**: 세션 중 사용자와 정리한 "한 사람이 한 로그인으로 오가는 4개 관계"(소비자 계정 / 내가 운영하는 회사 테넌트 / 내가 다니는 회사 테넌트 / 내가 다니는 회사에서 운영하는 다른 회사 테넌트)를 가이드에 명문화. 기존 가이드는 토큰이 목적별로 갈라지는 **세로축(authn/authz)**만 설명했고, 이 4유형이라는 **가로축(관계)**이 빠져 있었다.

# Goal

`operator-auth-token-model.md`에 **"하나의 계정, 4개의 모자"** 절을 추가한다. 통합 IAM 계정(account_id)은 하나지만 처하는 관계에 따라 얹히는 인가가 달라진다는 멘탈 모델을, 기존 토큰 3종(1축 로그인 / operator token / 2축 assume-tenant)에 1:1 매핑해 설명한다.

- 4유형: ① 소비자 계정 ② 내가 운영하는 회사(owner/admin) ③ 내가 다니는 회사(assigned operator) ④ 내 회사가 운영하는 다른 회사(cross-org partnership participant).
- 각 모자 → 정체성/역할 · 저장 위치 · 필요한 토큰을 표로.
- ②↔③(owner vs 직원), ③↔④(intra-org vs cross-org) 구분을 명시.
- 모자 ④는 기존 cross-org 절(§ 6 → § 7로 이동)의 **상세**로 연결.

SoT 아님: 기존 가이드와 동일하게 human-reference. 세부 규약은 § 9(권위 스펙)로 링크.

# Scope

## In Scope

- `projects/iam-platform/docs/guides/operator-auth-token-model.md`:
  - 신규 **`## 6. 하나의 계정, 4개의 모자`** 절(현 § 5 인증-vs-인가 와 현 § 6 cross-org 사이 삽입) — 4유형 표(모자 / 관계 / 정체성·역할 / 저장 / 토큰) + ②↔③·③↔④ 구분 문단.
  - 현 `## 6. cross-org 확장` → **`## 7. cross-org 확장 — 모자 ④ 상세 (ADR-MONO-045)`** 로 리넘버+제목에 "모자 ④ 상세" 명시.
  - 현 `## 7. 자주 헷갈리는 지점` → `## 8`, 현 `## 8. 권위 스펙` → `## 9` 리넘버.
  - 상단 배너의 "§ 8 의 권위 스펙" 참조 → "§ 9" 동반 갱신.

## Out of Scope

- 코드·스펙·계약·시드·테스트 변경 0 (순수 문서 확장).
- 토큰 모델 자체(1축/operator/2축) 재서술 — 기존 § 1~5 유지, 관계 축만 추가.
- 콘솔 화면(iam-guide/AssignOperatorForm) 반영 — 별건 platform-console task(PC-FE).
- 스펙 내용 복제(SoT 는 스펙; 가이드는 개념+링크).

# Acceptance Criteria

- [ ] **AC-1** `operator-auth-token-model.md`에 `## 6. 하나의 계정, 4개의 모자` 절 신설, 4유형 표(①~④) 포함.
- [ ] **AC-2** 각 모자가 토큰 3종에 정확히 매핑: ①=1축만 ②=1축+operator token ③=1축+operator+2축 ④=2축(cap, admin 없음).
- [ ] **AC-3** ②↔③(owner vs assigned operator), ③↔④(intra-org 배정 vs cross-org 파트너십) 구분이 문단으로 명시.
- [ ] **AC-4** 모자 ④가 cross-org 상세 절(리넘버된 § 7)로 연결되고, admin scope 불방출·delegated cap 이 그 절과 모순 없음.
- [ ] **AC-5** 리넘버(6→7→8→9) 정확 + 상단 배너 "§ 8"→"§ 9" 갱신, 문서 내 다른 `§ N` 참조(§ 1 다이어그램·§ 4 링크) 무손상.
- [ ] **AC-6** 코드/스펙/계약/시드/테스트 변경 0. CI markdown fast-lane.

# Related Specs

- `projects/iam-platform/docs/guides/operator-auth-token-model.md` (대상 — BE-481 신설분).
- `projects/iam-platform/specs/services/admin-service/rbac.md` § Cross-Org Partner Delegation Confinement (모자 ④ 근거 — cap·admin 불방출).
- `projects/platform-console/specs/services/console-web/architecture.md` § Per-domain credential selection (토큰 축 링크 — 기존 유지).

# Related Contracts

- 변경 없음(개념 가이드 확장).

# Edge Cases

- 리넘버 시 문서 내 상호 참조(§ N) 정합: 배너의 "§ 8"만 실제 참조이며 § 9로 이동, § 1/§ 4 참조는 불변 — AC-5 로 가드.
- 모자 ④ 표의 토큰 서술이 cross-org 절과 어긋나지 않게(entitled=host-ACTIVE ∩ delegated.domains, roles verbatim, admin 없음) — 개념 수준으로 유지, 수치는 스펙 링크 위임.

# Failure Scenarios

- 스펙-only 문서라 런타임 실패 없음. CI = markdown lane. 리넘버 누락/§ 참조 깨짐만 주의(AC-5 확인).
- 4유형이 스펙의 실제 role 모델과 어긋날 위험 → 각 모자를 기존 가이드 토큰 축·rbac.md 개념에 맞춰 서술(복제 아님, 링크).

# Definition of Done

- [ ] `## 6. 하나의 계정, 4개의 모자` 절 추가 + cross-org 절 리넘버(§ 7)·"모자 ④ 상세" 연결
- [ ] 4유형 ↔ 토큰 3종 매핑 정확, ②↔③·③↔④ 구분 명시, 배너 § 참조 갱신
- [ ] 코드/스펙/계약 변경 0
- [ ] Acceptance Criteria 충족
- [ ] Ready for review
