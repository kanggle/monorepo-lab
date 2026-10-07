# Task ID

TASK-MONO-772

# Title

`ADR-MONO-080` 단계 3 (D6 · R4) — 운영자 규칙을 **«대상 테넌트 계정» 에서 «초대 → 인증된 본인 수락»** 으로 · 풀 계정의 콘솔 진입 · 셀프 온보딩 운영자 풀 이동

# Status

ready

# Owner

monorepo

# Task Tags

- iam
- platform-console
- security

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (운영자 권한 평면 · 권한이 처음 붙는 지점)

---

# Dependency Markers

- **선행**: `TASK-MONO-770` · `TASK-MONO-771` `done/` (ADR-080 D2).
- **후속**: `TASK-MONO-773`(D9 테넌트 생성 하나) · (`TASK-MONO-774` 가 연결 쓰기를 «초대 수락의 부산물» 로 고르면 774).

# Goal

지금 풀 계정 → 회사 운영자의 길은 세 곳에서 닫혀 있다(ADR-080 Context): 생성(`TASK-MONO-334` — 대상 테넌트에 가입 계정 필요, 풀 멤버 안 셈, `CreateOperatorUseCase.java:86-108`) · 로그인(`TASK-BE-615` D-5 — 풀 세션은 콘솔 토큰 없음) · 셀프 온보딩(044 — 풀 계정은 `platform-console-web` 토큰을 못 받음, `OnboardingController.java:27-28, 57`). 셋을 연다:

- 운영자(회사 관리자)가 이메일로 **초대** → 그 이메일을 **인증한** 풀 계정이 **로그인한 상태로** 수락 → 운영자 측면 생성. 이메일 일치만으로는 안 붙는다(ADR-034 § 1.3).
- 내부 프로비저닝(`/internal/tenants/{tenantId}/accounts`, 회사 테넌트에 사이트 계정 생성)을 **풀 계정 초대**로 바꾼다.
- 풀 세션의 콘솔 진입 — **운영자 측면이 있는** 풀 계정만 운영자 토큰을 받는다.
- 사이트 계정으로 남아 있는 셀프 온보딩 운영자를 풀로 옮기고, 그 이메일의 풀 가입 거절(`TASK-BE-614` AC-6)을 걷는다.

라이더 R4(기본값): 초대 = **1회용 · 만료(기본 `P7D`) · 토큰 원문 미저장**(셀러 구성원 초대와 같은 모양).

# Scope

## In Scope

- 계약 먼저: `admin-api.md` 운영자 초대 · 수락(신설), 운영자 생성(`POST /api/admin/operators`)의 334 규칙 대체 방식 · `admin-to-account.md` · `multi-tenancy.md` § 소비자 계정 풀 § 3 운영자 측면 표
- admin-service: 초대 저장(해시) · 만료 · 수락(770 의 공용 인증 술어 재사용) · 334 계정 확인 대체 · 테넌트 범위 제한(ADR-024 D2)과 역할 무상승(D3) 그대로
- auth-service: 운영자 측면 있는 풀 계정에 `platform-console-web` 토큰 발급(615 D-5 갱신)
- 데이터 이동: 셀프 온보딩 운영자 사이트 계정 → 풀(`oidc_subject` 보존 방침 포함) · AC-6 거절 해제
- 콘솔: 운영자 관리 화면의 «등록» 을 «초대» 로(대기 중 초대 목록 · 취소 · 재발송)

## Out of Scope

- «테넌트 생성» 화면 통합(773) · 직원 마스터 연결(774) · 플랫폼 관리자(`'*'`) 모델(D1 — 그대로)

# Acceptance Criteria

- [ ] **AC-0** — 착수 시 재측정: 334 규칙 file:line · 615 D-5 거절 지점 · 온보딩 audience 요구 · 사이트 계정으로 남은 셀프 온보딩 운영자 수(정적 · 라이브 ⚪ 가능).
- [ ] **AC-1** — 🔴 대조군: 인증 안 된 계정 · 다른 이메일 계정 · 만료/재사용 초대 → 수락 거절. 같은 시험에서 인증된 본인 수락만 성공.
- [ ] **AC-2** — 🔴 닫힌 경로(D9 = T1 반영): 운영자 측면 **없는** 풀 계정은 **운영자 토큰을 받지 못한다**(셸 진입 여부가 아니라 토큰 · 관리 API 로 단언).
- [ ] **AC-3** — 퇴사(운영자 측면 회수) 뒤 그 풀 계정의 스토어 · 팬 로그인은 그대로이고 콘솔 운영자 토큰은 없다(ADR-080 D5).
- [ ] **AC-4** — 파트너십 해지 → 협력사 직원(풀 계정)의 회사 A 진입이 다음 요청에서 거절.
- [ ] **AC-5** — 셀프 온보딩 운영자 이동 뒤 같은 사람이 같은 `sub` 로 콘솔에 들어오고(또는 방침대로) · 그 이메일의 풀 가입 거절이 사라진다.
- [ ] **AC-6** — 셀프 온보딩으로 만든 새 조직의 관리자가 직원을 **초대 → 수락**으로 운영자로 만들 수 있다(이 대화가 찾은 빈틈의 종단 확인, 라이브 ⚪ 가능).

# Related Specs

- `docs/adr/ADR-MONO-080-workforce-on-the-consumer-pool.md` D5 · D6 · R4 · § Verification · § ACCEPT 가 만든 새 의무
- `docs/adr/ADR-MONO-035-operator-auth-unification-model.md` § 8 (334 개정 — 이 티켓이 대체)
- `docs/adr/ADR-MONO-044-self-service-tenant-onboarding.md` D5
- `projects/iam-platform/specs/features/multi-tenancy.md` § 소비자 계정 풀

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` · `internal/admin-to-account.md` · `onboarding-api.md`

# Edge Cases

- 같은 사람이 여러 회사에 초대 — 운영자 측면은 테넌트마다(`(tenant_id, email)` 유니크, V0025).
- 초대 이메일을 아직 풀에 가입하지 않은 사람 — 수락 화면이 가입 → 인증 → 수락으로 안내.

# Failure Scenarios

1. 334 를 걷고 초대 수락에 인증 술어를 빠뜨린다 — ADR-080 위험 ① 이 그대로 열린다.
2. 풀 계정 전부에 콘솔 토큰을 준다 — 운영자 측면 없는 소비자가 관리 API 표면에 닿는다.
