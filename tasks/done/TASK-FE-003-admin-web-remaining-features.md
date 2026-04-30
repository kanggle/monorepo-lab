# Task ID

TASK-FE-003-admin-web-remaining-features

# Title

admin-web — 미구현 4개 기능 추가 (2FA 등록 UI, 세션 관리, Bulk Lock, 운영자 관리)

# Status

ready

# Owner

frontend

# Task Tags

- frontend
- feature

# depends_on

- TASK-BE-052 (E2E 통과, 2FA enroll bootstrap token 수정 포함)

---

# Goal

admin-web의 부트스트랩(FE-001/002) 이후 미구현된 4개 기능을 추가하여 admin-service API의 전체 표면을 커버한다.

1. **2FA 등록 UI** — 최초 로그인 시 ENROLLMENT_REQUIRED 응답 처리, QR 코드 표시, TOTP 검증 플로우
2. **세션 관리** — 특정 계정의 세션 강제 종료 (POST /api/admin/sessions/{accountId}/revoke)
3. **Bulk Lock UI** — 여러 계정 일괄 잠금 다이얼로그 (POST /api/admin/accounts/bulk-lock)
4. **운영자 관리 페이지** — 현재 로그인한 운영자 정보 표시 (/console/operators)

---

# Scope

## In Scope

### 2FA 등록 UI
- LoginForm에서 ENROLLMENT_REQUIRED 401 응답 시 2FA 등록 플로우로 전환
- QR 코드 표시 (otpauthUri → qrcode 라이브러리 또는 otpauth:// 링크)
- Recovery codes 표시 + 복사/다운로드
- TOTP 코드 입력 후 verify 호출
- verify 성공 후 자동 로그인

### 세션 관리
- 계정 상세 페이지에 "세션 강제 종료" 버튼 추가
- RevokeSessionDialog 컴포넌트 (사유 입력 필수)
- POST /api/admin/sessions/{accountId}/revoke 호출
- 성공 시 revoked 세션 수 표시

### Bulk Lock
- accounts 페이지에 "일괄 잠금" 버튼 추가
- BulkLockDialog 컴포넌트 (account ID 목록 + 사유 입력)
- 결과 테이블 (LOCKED, NOT_FOUND, ALREADY_LOCKED, FAILURE 각각 표시)
- Idempotency-Key 자동 생성

### 운영자 관리
- /console/operators 페이지 (현재 운영자 정보: ID, 이메일, 역할)
- 현재 세션 정보 표시

## Out of Scope

- 운영자 생성/삭제/역할 변경 (admin-api에 해당 엔드포인트 없음)
- 계정 검색 기능 개선 (이미 구현됨)

---

# Acceptance Criteria

- [ ] ENROLLMENT_REQUIRED 시 2FA 등록 플로우가 동작
- [ ] QR 코드 표시 + recovery codes 표시/복사
- [ ] TOTP verify 후 자동 로그인
- [ ] 계정 상세에서 세션 강제 종료 가능
- [ ] Bulk Lock 다이얼로그에서 여러 계정 잠금 + 결과 확인
- [ ] /console/operators 페이지에서 현재 운영자 정보 표시
- [ ] 기존 기능 (로그인, 계정 관리, 감사 로그) 비파괴

---

# Related Specs

- specs/contracts/http/admin-api.md
- specs/services/admin-web/architecture.md

---

# Related Contracts

- admin-api.md §POST /api/admin/auth/login (ENROLLMENT_REQUIRED)
- admin-api.md §POST /api/admin/auth/2fa/enroll
- admin-api.md §POST /api/admin/auth/2fa/verify
- admin-api.md §POST /api/admin/sessions/{accountId}/revoke
- admin-api.md §POST /api/admin/accounts/bulk-lock

---

# Edge Cases

- 2FA 등록 중 bootstrap token 만료 시 재로그인 안내
- Bulk Lock에서 100건 초과 입력 시 클라이언트 사전 검증
- Bulk Lock 부분 실패 결과 표시

---

# Failure Scenarios

- 네트워크 오류 시 에러 메시지 표시
- 권한 부족 시 403 핸들링

---

# Test Requirements

- 기존 빌드 통과 (next build)

---

# Definition of Done

- [ ] 4개 기능 구현 + 빌드 통과
- [ ] Ready for review
