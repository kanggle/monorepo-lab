# Task ID

TASK-BE-067

# Title

TASK-BE-064 후속 보완 — gateway architecture.md 불변식 기록 + admin-login rate-limit 결정 문서화

# Status

ready

# Owner

backend

# Task Tags

- code
- api

# depends_on

TASK-BE-064

---

# Goal

TASK-BE-064 리뷰에서 발견된 두 가지 보완 사항을 반영한다.

1. **Warning**: `specs/services/gateway-service/architecture.md` 에 admin 이중 검증 위임 불변식이 기록되지 않았다. YAML 코드 주석으로만 명시된 상태이며, 아키텍처 스펙의 Change Rule 은 게이트웨이 인증 규칙 변경 시 이 파일을 먼저 수정하도록 요구한다. 향후 `OperatorAuthenticationFilter` 가 deprecated 될 경우 gateway 의 admin public-paths 가 완전 무인증으로 노출되는 위험을 스펙 레벨에서 선제적으로 기록해야 한다.

2. **Suggestion**: TASK-BE-064 Scope(C) — `RouteConfig.resolveRateLimitScope` 가 `/api/admin/auth/login` 에 별도 rate-limit 스코프를 적용하지 않는 상태이며, 해당 결정(적용 여부)을 코드 주석 또는 문서에 기록하지 않은 채로 태스크가 종료됐다.

---

# Scope

## In Scope

**A. `specs/services/gateway-service/architecture.md` 보완**

`## Integration Rules` 또는 `## security/` 섹션에 아래 내용을 추가:

- `/api/admin/**` 및 `/.well-known/admin/**` 서브트리는 gateway 입장에서 public-paths 로 취급된다.
- operator JWT 검증은 admin-service `OperatorAuthenticationFilter` 에 위임되는 **플랫폼 불변식**이다.
- 이 위임 관계가 해소(OperatorAuthenticationFilter deprecated 또는 gateway 이전)되면 본 파일 + `specs/contracts/http/gateway-api.md §Admin Routes` 개정이 선행되어야 한다.
- 참조: `specs/contracts/http/gateway-api.md §Admin Routes (second-layer auth)`

**B. `RouteConfig.resolveRateLimitScope` 결정 문서화**

`RouteConfig.java` 의 `resolveRateLimitScope` 메서드에 admin login rate-limit 결정을 주석으로 명시:

```
// admin login (/api/admin/auth/login) 은 현재 global 스코프만 적용 (전용 스코프 없음).
// 전용 스코프 추가가 필요하다면 별도 태스크로 구현할 것.
// 근거: TASK-BE-064 Scope(C) 결정 — 구현은 후속 태스크 분리.
```

## Out of Scope

- admin-login rate-limit 실제 구현 (본 태스크는 결정 문서화까지)
- 그 밖의 기능 변경

---

# Acceptance Criteria

- [ ] `specs/services/gateway-service/architecture.md` 에 admin second-layer auth 위임 불변식이 문장으로 기록됨
- [ ] `RouteConfig.resolveRateLimitScope` 에 admin-login rate-limit 미적용 결정 주석이 추가됨
- [ ] `./gradlew :apps:gateway-service:test :apps:gateway-service:check` green

---

# Related Specs

- `specs/services/gateway-service/architecture.md`
- `specs/contracts/http/gateway-api.md` §Admin Routes (second-layer auth)

---

# Related Contracts

- 참조: `specs/contracts/http/gateway-api.md`

---

# Edge Cases

- architecture.md 수정이 기존 내용과 중복되지 않도록 확인 (gateway-api.md §Admin Routes 에 이미 불변식이 기술돼 있으므로 참조 링크 방식으로 기술 가능)

---

# Failure Scenarios

- 주석 내용이 실제 구현과 불일치할 경우 혼란 야기 → 코드와 스펙을 동시에 확인

---

# Test Requirements

- 코드 및 문서 변경만이므로 기존 테스트 suite pass 확인으로 충분
