---
name: oauth-provider
description: Multi-provider OAuth 2.0 integration (Google, Naver)
category: backend
---

# Skill: OAuth Provider

Patterns for multi-provider OAuth 2.0 integration.

Prerequisite: read `platform/security-rules.md` and `platform/error-handling.md` before using this skill.

---

## Provider Interface

```java
// domain/service/OAuthProvider.java
public interface OAuthProvider {
    String provider();
    String buildAuthorizationUrl(String state, String redirectUri);
    OAuthUserInfo fetchUserInfo(String code, String redirectUri);

    record OAuthUserInfo(String email, String name) {}
}
```

---

## OAuth Client Implementation (Google Example)

```java
@Slf4j
@Component
public class GoogleOAuthClient implements OAuthProvider {

    private static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v3/userinfo";

    private final GoogleOAuthProperties props;
    private final RestClient restClient;

    @Override
    public String provider() { return "google"; }

    @Override
    public String buildAuthorizationUrl(String state, String redirectUri) {
        return UriComponentsBuilder.fromUriString(AUTH_ENDPOINT)
            .queryParam("client_id", props.getClientId())
            .queryParam("redirect_uri", redirectUri)
            .queryParam("response_type", "code")
            .queryParam("scope", "openid email profile")
            .queryParam("state", state)
            .queryParam("access_type", "online")
            .encode().build().toUriString();
    }

    @Override
    public OAuthUserInfo fetchUserInfo(String code, String redirectUri) {
        // Step 1: authorization code → access token
        Map<String, Object> tokenResponse = restClient.post()
            .uri(TOKEN_ENDPOINT)
            .body(Map.of(
                "code", code,
                "client_id", props.getClientId(),
                "client_secret", props.getClientSecret(),
                "redirect_uri", redirectUri,
                "grant_type", "authorization_code"
            ))
            .retrieve().body(Map.class);

        String accessToken = (String) tokenResponse.get("access_token");

        // Step 2: access token → user info
        Map<String, Object> userInfo = restClient.get()
            .uri(USERINFO_ENDPOINT)
            .header("Authorization", "Bearer " + accessToken)
            .retrieve().body(Map.class);

        return new OAuthUserInfo((String) userInfo.get("email"), (String) userInfo.get("name"));
    }
}
```

---

## Adding a New Provider

1. Create properties class (`{Provider}OAuthProperties`).
2. Implement `OAuthProvider` interface.
3. Register as `@Component` — auto-discovered via injection.
4. Add provider-specific endpoints and scope.

Current providers: **Google**, **Naver**.

---

## OAuth State Management

Use a state store to prevent CSRF and replay attacks.

```java
public interface OAuthStateStore {
    void save(String state, long ttlSeconds);
    boolean consumeIfValid(String state);
}
```

Redis implementation stores state as a key with TTL. `consumeIfValid` atomically checks and deletes.

---

## OAuth Flow

```
1. Frontend → GET /api/auth/oauth/{provider}/authorize
2. Backend → generate state, save to store, return authorization URL
3. User → redirected to provider, grants consent
4. Provider → redirects to callback with code + state
5. Frontend → POST /api/auth/oauth/{provider}/callback {code, state, redirectUri}
6. Backend → validate state, exchange code for token, fetch user info
7. Backend → find or create user, generate JWT tokens, return login response
```

---

## Configuration

```yaml
oauth:
  google:
    client-id: ${GOOGLE_CLIENT_ID}
    client-secret: ${GOOGLE_CLIENT_SECRET}
  naver:
    client-id: ${NAVER_CLIENT_ID}
    client-secret: ${NAVER_CLIENT_SECRET}
```

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Missing state validation | Always verify state matches before processing callback |
| Token response missing access_token | Throw clear exception — don't proceed with null |
| Hardcoded client secrets | Use environment variables |
| No standalone fallback for state store | Provide in-memory `OAuthStateStore` for `@Profile("standalone")` |
| Error handling for provider downtime | Catch RestClient exceptions and return user-friendly error |
