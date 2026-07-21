# TASK-BE-521 — 소셜 로그인 SAS-브리지 하드닝 2건 (세션 픽세이션 방어 부재 · state↔provider 미대조)

**Status:** ready

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (#1 은 한 줄 세션 회전 + 통합 테스트 단언, #2 는 한 줄 대조. 다만 #1 의 "다른 완화가 없는지" 재측정이 실질)

> 2026-07-21 소셜 로그인 코드 리뷰 산물. auth-service 의 SAS 브라우저-세션 소셜 로그인 브리지([`SocialLoginBrowserController`](../../apps/auth-service/src/main/java/com/example/auth/presentation/SocialLoginBrowserController.java), ADR-006 option B, TASK-BE-396)에서 나온 저~중 심각도 2건. 전체 흐름(state 단일사용·redirect_uri allowlist·HTTP outside-txn·born-unified 계정·테넌트=개시 클라이언트)은 견고하나, **수동 세션 확립 경로가 Spring 자동 방어를 우회**한다.

---

## Goal

소셜 콜백은 Spring 표준 인증 필터(`UsernamePasswordAuthenticationFilter`)를 거치지 않고 `@GetMapping` 컨트롤러에서 **수동으로** SecurityContext 를 세션에 저장한다. 그래서 필터가 자동 적용하는 두 가지 방어가 이 경로엔 없다.

### 항목 A (Medium, 보안) — 소셜 콜백에 세션 픽세이션 방어 부재

[`SocialLoginBrowserController.establishSession()`](../../apps/auth-service/src/main/java/com/example/auth/presentation/SocialLoginBrowserController.java) 는:

```java
SecurityContext context = SecurityContextHolder.createEmptyContext();
context.setAuthentication(authentication);
SecurityContextHolder.setContext(context);
securityContextRepository.saveContext(context, request, response);   // 기존 세션에 저장, ID 회전 없음
```

`HttpSessionSecurityContextRepository.saveContext` 는 **현재(픽세이션 가능) 세션**에 컨텍스트를 저장할 뿐 **세션 ID 를 회전하지 않는다.** 비밀번호 form-login 경로는 `UsernamePasswordAuthenticationFilter` 가 `SessionFixationProtectionStrategy`(SS6 기본 `changeSessionId`)를 자동 적용하지만, **소셜 GET 콜백은 그 필터를 통과하지 않으므로 회전이 일어나지 않는다.**

- **실측 확인**: auth-service `src/main` 전체 grep 결과 `changeSessionId` / `sessionFixation` / `server.servlet.session` **0건**. 컨테이너/필터 레벨 완화도 없다.
- **위협**: 공격자가 피해자의 auth-service `JSESSIONID` 를 사전 고정하면, 피해자가 소셜 로그인을 완료해도 세션 ID 가 그대로라 **그 세션이 인증된 SAS 세션이 된다** → 공격자가 자기 클라이언트로 `/oauth2/authorize` 를 재개해 피해자 자격의 토큰을 얻을 수 있다(고전적 세션 픽세이션). 형제 경로(비밀번호)와의 **방어 불균형(straggler)** — 같은 SAS 세션을 확립하는데 한쪽만 회전한다.
- **테스트 사각**: `SocialLoginSasBrowserIntegrationTest` 는 최초 `/oauth2/authorize` 로 만든 **세션을 콜백·재개까지 그대로 재사용**(`toMockSession(session)`)하고 **세션 ID 회전을 단언하지 않는다** — 즉 픽세이션 시나리오 자체가 검증되지 않는다.

### 항목 B (Low, 방어심층) — state 가 콜백 provider 와 대조되지 않음

[`OAuthLoginUseCase.resolveSocialLogin()`](../../apps/auth-service/src/main/java/com/example/auth/application/OAuthLoginUseCase.java) 는:

```java
if (oAuthStateStore.consumeAtomic(command.state()).isEmpty()) {   // 반환된 provider 를 버림
    throw new InvalidOAuthStateException();
}
```

`OAuthStateStore.consumeAtomic` 은 **저장 시 바인딩된 `OAuthProvider` 를 반환**([OAuthStateStore.java:34](../../apps/auth-service/src/main/java/com/example/auth/domain/repository/OAuthStateStore.java)) — 정확히 대조하라고 돌려주는데 호출부는 `.isEmpty()` 만 보고 **provider 를 버린다.** ⇒ provider A 로 발급된 state 를 `/login/oauth/{B}/callback` 에서 소비 가능(state↔provider 바인딩 미강제). state 는 단일사용·128-bit UUIDv7 이고 실제 `code` 가 B 에 유효해야 하므로 실익은 낮지만, **스토어가 제공하는 무료 바인딩 검증이 빠져 있다.**

---

## Scope

**In scope**

1. **항목 A** — 소셜 콜백에서 SAS 세션 확립 **직전에 세션 ID 회전**(`request.changeSessionId()`, 또는 saveContext 전 새 세션으로 마이그레이션). 비밀번호 경로와 동일한 픽세이션 자세로 맞춘다. **회전을 단언하는 통합/컨트롤러 테스트**(콜백 전 세션 ID ≠ 콜백 후 세션 ID) 추가.
2. **항목 B** — `consumeAtomic` 이 돌려준 provider 가 `command.provider()`(파싱된 `OAuthProvider`)와 일치하는지 확인, 불일치 시 `InvalidOAuthStateException`.

**Out of scope**

- 레거시 커스텀-JWT 경로(`callback()`) — `TASK-BE-398` 이 통째로 일몰 제거. 항목 A 는 **브라우저 SAS 경로 한정**(레거시 JSON 경로는 세션 없음).
- state 를 세션에 바인딩하는 별도 login-CSRF 하드닝 — 별건(§ Notes).
- redirect_uri allowlist / HTTP outside-txn / born-unified 등 정상 확인된 부분 변경 0.

---

## Acceptance Criteria

- **AC-0 (gate — 재측정, 코드가 이긴다)** — 착수 시 실측 재확인. **본문 인용은 2026-07-21 기준 가설이며 그동안 고쳐졌을 수 있다:**
  - 항목 A: auth-service `src/main` 에 여전히 `changeSessionId`/`sessionFixation`/`server.servlet.session` 0건인지, 소셜 콜백이 여전히 회전 없이 `saveContext` 하는지, **서블릿 컨테이너/전역 필터 레벨의 다른 완화가 정말 없는지** 확인. 이미 회전이 있으면 phantom 종료.
  - 항목 B: `consumeAtomic` 이 여전히 provider 를 반환하고 호출부가 여전히 버리는지 확인.
- **AC-1 (A)** — 소셜 콜백이 세션 확립 전 세션 ID 를 회전한다. **콜백 전후 JSESSIONID 가 다름을 단언하는 테스트**(픽세이션 시나리오 재현 — 기존 IT 가 세션을 재사용해 못 잡던 바로 그 축).
- **AC-2 (A)** — 회전 후에도 저장된 `/oauth2/authorize` 재개가 정상 동작(회전이 saved-request 를 잃지 않음 — `HttpSessionRequestCache` 는 세션 attribute 이므로 `changeSessionId` 는 보존하지만 반드시 회귀 확인).
- **AC-3 (B)** — state 의 바인딩 provider ≠ 콜백 provider 면 `InvalidOAuthStateException`. 일치 경로는 기존대로 통과.
- **AC-4** — 회전이 form-login(비밀번호) 경로의 기존 픽세이션 방어를 바꾸지 않음(그 경로는 이미 필터가 처리 — 건드리지 말 것).
- **AC-5** — 전체 `./gradlew :projects:iam-platform:apps:auth-service:test` GREEN(로컬 Testcontainers 불가 시 CI 가 권위).

---

## Related Specs / Contracts

- `projects/iam-platform/docs/adr/ADR-006-external-idp-login-sas-integration.md` — SAS 세션 브리지 설계(option B) 정경
- `projects/iam-platform/specs/services/auth-service/security.md` — 세션/인증 자세(있으면 픽세이션 정책 확인)
- 계약 변경 0 — 순수 서버 하드닝, HTTP 계약·토큰 형태 불변.

---

## Edge Cases

1. **saved-request 보존** — `changeSessionId` 는 세션 attribute 를 유지하므로 `HttpSessionRequestCache` 의 저장된 `/oauth2/authorize` 는 살아남아야 한다. `newSession()`(attribute 미이전) 방식이면 saved-request 를 잃어 재개가 깨진다 → `changeSessionId` 권장. AC-2 가 방어.
2. **신규 계정 vs 기존 계정** — 회전은 born-unified 신규/기존 양쪽에 동일 적용.
3. **항목 B — code 재사용** — state↔provider 대조가 정상 경로(같은 provider)를 막지 않는지 확인(false-reject 금지).

---

## Failure Scenarios

- **F1 — 회전을 추가했다면서 테스트는 "로그인 성공"만 단언.** 그러면 지금과 구분이 안 된다(현재도 성공한다). **테스트는 콜백 전후 세션 ID 가 다른지**를 봐야 한다(대리지표 금지 — 성질 자체를 물어라).
- **F2 — `newSession` 방식으로 회전해 saved-request 유실 → `/oauth2/authorize` 재개가 "/"로 떨어짐.** AC-2 가 잡는다.
- **F3 — 항목 B 대조를 과하게 만들어 정상 provider 콜백을 막음.** AC-3 짝 단언 필수.

---

## Definition of Done

- [ ] AC-0 재측정 (session 회전 부재·다른 완화 없음·consumeAtomic 반환 버림 확인)
- [ ] 항목 A: 소셜 콜백 세션 ID 회전 + 전후 ID 상이 단언 테스트
- [ ] 항목 A: saved `/oauth2/authorize` 재개 회귀 확인
- [ ] 항목 B: state↔provider 대조 + 짝 단언
- [ ] `auth-service:test` GREEN (CI 권위)

---

## Notes

- **분량**: small. 실질 변경 2~3줄 + 테스트. 판단은 #1 의 회전 방식(`changeSessionId` vs 마이그레이션)과 saved-request 보존.
- **자매 하드닝(별건, 참고)**: state 가 **세션에 바인딩되지 않고** 전역 Redis 키다(단일사용). 고전적 OAuth login-CSRF(공격자가 자기 state+code 로 피해자를 자기 계정에 로그인시킴) 관점의 추가 하드닝은 별도 판단이 필요 — 이 티켓 범위 밖.
- **이 task 가 방어하는 실패 모드**: **"형제 경로가 프레임워크 자동 방어를 받는데, 손으로 세션을 확립하는 경로만 그 방어를 우회한다."** 비밀번호 로그인은 필터가 세션을 회전시키지만 소셜 콜백은 컨트롤러가 직접 세션을 확립하며 회전을 빠뜨렸다 — 같은 SAS 세션인데 방어 비대칭. (TASK-PC-FE-253/MONO-456 과 같은 "같은 개념, 한쪽만 배선" 클래스의 보안판.)
