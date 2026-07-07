# Task ID

TASK-BE-485

# Title

operator-auth-token-model.md 가이드 § 6 에 "admin-console 롤은 누가 받나 — 대표·직원·협력업체" 하위 절 추가 — 6개 seed 롤(SUPER_ADMIN/SUPPORT_READONLY/SUPPORT_LOCK/SECURITY_ANALYST/TENANT_ADMIN/TENANT_BILLING_ADMIN)을 관계(모자 ②③④)에 매핑, platform-scope vs tenant-scope 구분 (human-reference, 기존 가이드 확장)

# Status

done

# Owner

backend (docs)

# Task Tags

- docs
- guide
- iam

---

# Dependency Markers

- **builds on**: TASK-BE-482(operator-auth-token-model.md § 6 "하나의 계정, 4개의 모자" 신설), TASK-BE-483(§ 6 "② 만드는 법 — self-service 온보딩" 하위 절). 본 절은 그 4모자(관계 축)에 **각 관계에서 받는 admin-console seed 롤**을 얹는 확장이다.
- **note (동기)**: 세션 중 사용자 질문 "SUPER_ADMIN·SUPPORT_READONLY·SUPPORT_LOCK·SECURITY_ANALYST·TENANT_ADMIN·TENANT_BILLING_ADMIN 중 내가 대표일 때/직원일 때/협력업체일 때 어떤 롤을 받나?"의 정답을 가이드에 명문화. § 6 은 모자(관계)와 토큰만 매핑했고, 그 관계가 **어떤 IAM 관리 롤을 받는지**(그리고 4개 platform-scope 롤은 고객이 아무도 못 받는다는 점)가 빠져 있었다.

# Goal

`operator-auth-token-model.md` § 6 에 **"### admin-console 롤은 누가 받나 — 대표·직원·협력업체"** 하위 절을 추가한다. 6개 seed 롤을 scope 로 두 부류(platform-scope 4종 = 플랫폼 운영팀 전용 / tenant-scope 2종 = 고객사)로 가르고, 고객 페르소나(대표=모자 ②, 직원=③, 협력업체=④)가 각각 어떤 롤을 받는지 표+문단으로 정리한다.

- platform-scope 4종(SUPER_ADMIN·SUPPORT_READONLY·SUPPORT_LOCK·SECURITY_ANALYST)은 고객(대표/직원/협력업체) **아무도 못 받음** 명시.
- 대표(②) = TENANT_ADMIN + TENANT_BILLING_ADMIN 자동(ADR-044 D6, 내 테넌트 scope only).
- 직원(③) = 기본 이 6개 중 없음(도메인 롤 별도), 위임 시 TENANT_ADMIN(△).
- 협력업체(④) = host 에선 이 6개 중 없음(admin scope 조직 경계 불방출, 403 · § 7 연결), capped 도메인 롤만.
- admin-console 롤 ≠ 도메인 운영 롤(별개 축) 강조.

SoT 아님: 기존 가이드와 동일 human-reference. 롤 정의·권한 권위는 admin-service rbac.md·seed 롤(§ 9 링크).

# Scope

## In Scope

- `projects/iam-platform/docs/guides/operator-auth-token-model.md`:
  - § 6 안, "③ ↔ ④ 구분" 문단 + "공통" blockquote 뒤 · "### ② 만드는 법 — self-service 온보딩" 앞에 신규 하위 절 **`### admin-console 롤은 누가 받나 — 대표·직원·협력업체`** 삽입 — 6롤×3페르소나 표(scope·원래 대상 포함) + platform/tenant scope 구분 문단 + 페르소나별(대표/직원/협력업체) 문단.

## Out of Scope

- 코드·스펙·계약·시드·테스트 변경 0 (순수 문서 확장).
- 섹션 리넘버 0 (§ 6 내부 하위 절만 추가 — §7/8/9 및 배너 참조 불변).
- 도메인 롤(WMS_OPERATOR 등) 카탈로그 재서술 — 별개 축임만 명시, 열거는 링크 위임.
- 콘솔 화면(iam-guide/AssignOperatorForm) 반영 — 별건 platform-console task(PC-FE).
- 롤 권한 키(permissions)의 완전 열거 — 개념만, 상세는 rbac.md·seed 롤.

# Acceptance Criteria

- [ ] **AC-1** § 6 에 `### admin-console 롤은 누가 받나 — 대표·직원·협력업체` 하위 절 신설, 6롤×(대표/직원/협력업체) 매핑 표 포함.
- [ ] **AC-2** platform-scope 4종(SUPER_ADMIN·SUPPORT_READONLY·SUPPORT_LOCK·SECURITY_ANALYST)이 고객 3 페르소나 전원 ✕(플랫폼 운영팀 전용)로 표기.
- [ ] **AC-3** 대표(②)=TENANT_ADMIN+TENANT_BILLING_ADMIN 자동(내 테넌트 scope only, ADR-044 D6), 직원(③)=기본 없음·위임 시 TENANT_ADMIN(△), 협력업체(④)=host 에서 없음(admin scope 불방출·403)이 표+문단으로 정확.
- [ ] **AC-4** admin-console 롤이 도메인 운영 롤과 **별개 축**임을 명시(직원/협력업체 실제 일감=도메인 롤).
- [ ] **AC-5** 협력업체 문단이 리넘버 없는 기존 § 7(cross-org 상세)로 연결되고 admin scope 불방출과 모순 없음. 문서 내 §N 참조·배너 무손상(리넘버 0).
- [ ] **AC-6** 코드/스펙/계약/시드/테스트 변경 0. CI markdown fast-lane.

# Related Specs

- `projects/iam-platform/docs/guides/operator-auth-token-model.md` (대상 — BE-481/482/483 확장분).
- `projects/iam-platform/specs/services/admin-service/rbac.md` (seed 롤·scope·cross-org confinement 근거 — SoT).

# Related Contracts

- 변경 없음(개념 가이드 확장).

# Edge Cases

- 6롤의 scope 표기가 seed 롤 실제 정의와 어긋나지 않게(SUPER_ADMIN/SUPPORT_*/SECURITY_ANALYST=platform, TENANT_*=tenant) — 개념 수준, 권한 수치는 rbac.md 위임.
- 직원 △(위임)이 무조건 부여로 오독되지 않게 "기본 아님, 위임 시" 명확화.
- 협력업체 ✕ 가 "자기 회사 안에서도 롤 없음"으로 오독되지 않게 "host 테넌트 기준, 자기 테넌트에선 그쪽 ②" 단서.

# Failure Scenarios

- 스펙-only 문서라 런타임 실패 없음. CI = markdown lane. 리넘버 0 이므로 §참조 깨짐 위험 낮음(AC-5 확인).
- 롤×페르소나 매핑이 rbac.md 실제 모델과 어긋날 위험 → seed 롤 scope·ADR-044 D6·ADR-045 cap 에 맞춰 서술(복제 아님, 링크).

# Definition of Done

- [ ] § 6 에 `### admin-console 롤은 누가 받나` 하위 절 추가 (6롤×3페르소나 표 + scope 2부류 + 페르소나 문단)
- [ ] platform-scope 4종 고객 전원 ✕, 대표=둘 다 자동·직원=△·협력업체=✕ 정확, 도메인 롤과 별개 축 명시
- [ ] 리넘버 0·코드/스펙/계약 변경 0
- [ ] Acceptance Criteria 충족
- [ ] Ready for review
