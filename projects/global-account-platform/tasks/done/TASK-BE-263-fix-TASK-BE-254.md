# Task ID

TASK-BE-263

# Title

Fix TASK-BE-254: sync consumer-integration-guide.md discovery table with auth-api.md contract

# Status

ready

# Owner

backend

# Task Tags

- docs

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

Fix issue found in TASK-BE-254 review.

`specs/features/consumer-integration-guide.md` § Phase 3 — 표준 OIDC Discovery + JWKS 설정의 discovery 필드 테이블이
`specs/contracts/http/auth-api.md`의 `GET /.well-known/openid-configuration` 응답 예시와 일치하지 않는다.

구체적 불일치:
- 가이드 테이블은 `revocation_endpoint` (`${issuer}/oauth2/revoke`) 와
  `introspection_endpoint` (`${issuer}/oauth2/introspect`) 를 discovery 문서 광고 필드로 나열한다.
- `auth-api.md` 의 discovery 응답 JSON 예시에는 이 두 필드가 누락되어 있다.

Spring Authorization Server 1.x 는 기본적으로 두 필드를 discovery 문서에 포함하므로
가이드의 내용이 런타임상 정확하지만, 계약서(higher-priority source) 에는 해당 내용이 없다.
CLAUDE.md Source of Truth Priority § 6 (contracts > features) 에 따라 계약서를 정합성 소스로 갱신해야 한다.

원본 태스크: TASK-BE-254.

---

# Scope

## In Scope

- `specs/contracts/http/auth-api.md` § `GET /.well-known/openid-configuration` 응답 JSON 예시에
  `revocation_endpoint` 와 `introspection_endpoint` 두 필드 추가 (SAS 1.x 기본 동작 반영).
- 두 필드의 값 패턴을 인접 엔드포인트 정의(`POST /oauth2/revoke`, `POST /oauth2/introspect`) 와 정합하게 명시.

## Out of Scope

- `consumer-integration-guide.md` 자체 수정 — 가이드 내용이 정확하므로 수정 불필요.
  계약서만 갱신하면 정합성이 해소된다.
- Spring Authorization Server 설정 코드 변경 — 기본 동작을 그대로 사용.
- 기타 OIDC discovery 필드 추가 (예: `end_session_endpoint`) — 별도 태스크로 필요 시 도입.

---

# Acceptance Criteria

- [ ] `auth-api.md` § `GET /.well-known/openid-configuration` 응답 JSON 예시에
  `"revocation_endpoint": "https://gap.example.com/oauth2/revoke"` 추가.
- [ ] 동일 응답 예시에
  `"introspection_endpoint": "https://gap.example.com/oauth2/introspect"` 추가.
- [ ] 추가 후 `consumer-integration-guide.md` Phase 3 테이블 값과 정확히 일치하는지 grep 검증.
- [ ] 다른 계약 문서(gateway-api.md, auth-events.md 등)에서 discovery 필드를 참조하는 곳이 있으면 동기 갱신.

---

# Related Specs

- `specs/features/consumer-integration-guide.md` § Phase 3 (수정 불필요, 참조용)
- `docs/adr/ADR-001-oidc-adoption.md` § 3 Option A (SAS 기본 동작 근거)

---

# Related Contracts

- `specs/contracts/http/auth-api.md` (수정 대상 — discovery 응답 예시)

---

# Edge Cases

- **`introspection_endpoint` 는 confidential client 전용** — discovery 문서에는 광고하되, auth-api.md 의 기존 주석
  "public client는 introspect 불가" 도 유지해야 한다. discovery 필드 추가가 이 제약을 덮어쓰지 않도록 주석 위치 확인.
- **`revocation_endpoint` 와 `POST /oauth2/revoke` 경로 일치 확인** — 이미 auth-api.md § `POST /oauth2/revoke` 에 정의된 경로를 그대로 참조한다.

---

# Failure Scenarios

- **계약서 갱신 후 가이드와 여전히 불일치** — grep 크로스체크(`revocation_endpoint`, `introspection_endpoint`)로 검증.
- **다른 문서의 discovery 예시와 부분 중복** — gateway-api.md 등에서 discovery 필드 목록을 재인용하는 경우
  동기 갱신 범위에 포함한다.

---

# Test Requirements

해당 없음 (문서 수정). 변경 후 `auth-api.md` 와 `consumer-integration-guide.md` 의 discovery 관련 필드를 grep으로 대조 검증.

---

# Definition of Done

- [ ] `auth-api.md` discovery 응답 예시에 두 필드 추가 완료
- [ ] `consumer-integration-guide.md` Phase 3 테이블과 1:1 정합 확인
- [ ] Ready for review
