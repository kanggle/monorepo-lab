# Task ID

TASK-BE-399

# Title

프런트엔드 OIDC 소비자 동작 파리티 계약 명문화 (consumer-integration-guide.md § Phase 4.5)

# Status

ready

# Owner

backend

# Task Tags

- spec
- docs

---

# Goal

IAM SAS 를 IdP 로 쓰는 브라우저/SPA/BFF 프런트엔드(ecommerce `web-store`, `platform-console` console-web, `fan-platform` web)들이 로그인·세션·refresh·logout·에러 처리를 **제각각** 구현해 사용자 체감·보안 자세가 갈리는 문제를, **관찰 가능한 동작(observable behavior) 계약**으로 표준화한다.

구현 메커니즘(NextAuth vs 자작 핸들러, 쿠키 네이밍, 라우트 보호 방식)은 **자유**로 두되, F1~F7(로그인 개시 / 토큰 기밀성 / 토큰 갱신 / 로그아웃 / 역할가드·에러 / 복귀 / SSO)을 MUST/SHOULD 로 못박아, 각 앱 정렬 task(TASK-FE-075, TASK-PC-FE-115)가 conform 할 단일 계약을 제공한다.

이 계약은 **L1 파리티**(계약·동작 패리티)의 keystone 이다. 더 강한 쪽으로 수렴(silent refresh·토큰 server-only·표준 에러 어휘+fallback·복귀 보존)을 규정한다.

---

# Acceptance Criteria

- [ ] `specs/features/consumer-integration-guide.md` 에 **§ Phase 4.5 — 프런트엔드 OIDC 소비자 동작 계약** 추가 (Phase 4 와 Phase 5 사이).
- [ ] F1 로그인 개시(전체 리다이렉트+PKCE S256+state, embedded/ROPC 금지) 규정.
- [ ] F2 토큰 기밀성(access/refresh/id 전부 HttpOnly server-only, 클라 JS 노출 금지) 규정.
- [ ] F3 토큰 갱신(반응형 silent refresh + rotation 보관 + 실패 시에만 재인증 폴백, 동시 401 dedupe SHOULD) 규정.
- [ ] F4 로그아웃(RP-initiated end_session + id_token_hint + local-only 폴백 + 전체 쿠키 정리 + RFC7009 revoke SHOULD) 규정.
- [ ] F5 역할가드 + **표준 에러 코드 어휘 테이블** + unknown-code **generic fallback 필수** + 레거시 코드 마이그레이션 매핑.
- [ ] F6 로그인 후 의도 목적지 복귀(목적지 유실 금지, same-site 정규화) 규정.
- [ ] F7 SSO 허용(불필요한 prompt 강제 없음) + `prompt=none` 사일런트 인증 MAY.
- [ ] 적용 범위 밖(라이브러리/쿠키구조/라우트보호/operator·assumed token 등 도메인 특화 토큰) 명시.
- [ ] 프런트 소비자 배포 전 파리티 체크리스트 7항.

---

# Scope

## In Scope

- `specs/features/consumer-integration-guide.md` 에 Phase 4.5 섹션 신설 (docs/spec only, production code 0).

## Out of Scope

- 각 앱의 실제 코드 정렬 — TASK-FE-075(web-store), TASK-PC-FE-115(console) 로 분리.
- IAM 백엔드 SAS/엔드포인트 변경 — 본 계약은 기존 표준 OIDC 표면(`/oauth2/*`, `/connect/logout`, `/oauth2/revoke`)만 참조하며 신규 백엔드 표면을 요구하지 않는다.
- `prompt=none` 의 IAM 측 지원 검증 — 표준 OIDC 동작(SAS 기본 지원) 가정. 미지원 판명 시 별도 task.

---

# Related Specs

> **Before reading**: `platform/entrypoint.md` Step 0 — `PROJECT.md` → `rules/common.md` + `rules/domains/saas.md` + 선언된 trait 파일들.

- `specs/features/consumer-integration-guide.md` (본 task 가 갱신)
- `specs/features/authentication.md` (refresh rotation·token TTL — F3 정합)
- `docs/adr/ADR-001-oidc-adoption.md`
- `docs/adr/ADR-006-external-idp-login-sas-integration.md` (소셜 통합 — F1 와 정합)

# Related Contracts

- `specs/contracts/http/auth-api.md` (`/oauth2/token`·`/oauth2/revoke`·`/connect/logout` 형태 — F3/F4 참조처)

---

# Target Service

- N/A (consumer-facing spec 계약; IAM 백엔드 코드 무변경)

---

# Edge Cases

- console 은 operator token(RFC 8693)·assumed-tenant token 을 추가로 가짐 — 이는 **도메인 특화 토큰**으로 파리티 대상 밖임을 명시(과도한 통일 요구 금지).
- web-store/fan 은 소셜 4종(Google/Kakao/Microsoft/Naver)을 IAM `/login` 위임으로 사용 — F1 의 "자체 폼 비호스팅" 과 정합(소셜 버튼은 IAM 화면에 존재).
- 에러 코드 어휘는 **의미·키 공유**가 계약이고 **메시지 문구는 앱별 자유** — 과한 통일(문구까지 강제)로 해석되지 않도록 문구.

---

# Failure Scenarios

- 계약을 메커니즘 통일(둘 다 NextAuth 등)로 오해 → 동작하는 코드 불필요 재작성. 본 계약은 **동작만** 규정(범위 밖 절로 방지).
- F2 를 "토큰을 절대 다운스트림에 못 붙임" 으로 과해석 → same-origin 프록시/server-only 헬퍼 경유는 허용임을 문구로 방지.

---

# Test Requirements

- 스펙 문서 변경 — 코드 테스트 없음. doc lint(링크·헤더 정합) + validate-rules 통과.

---

# Definition of Done

- [ ] Phase 4.5 섹션 머지 (F1~F7 + 범위밖 + 체크리스트 + 에러 어휘 테이블)
- [ ] 후속 정렬 task 2건(FE-075, PC-FE-115) 이 본 계약을 Related Specs 로 참조
- [ ] Ready for review
