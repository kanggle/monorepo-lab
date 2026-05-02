# Internal HTTP Contract: community-service → account-service

**호출 방향**: community-service (client) → account-service (server)
**노출 경로**: `/internal/accounts/*` — 게이트웨이에 노출 금지
**인증** (TASK-BE-253 변경): `Authorization: Bearer <oauth2-access-token>`
- 기존 `X-Internal-Token` 헤더는 **deprecated**.
- community-service는 GAP에서 `client_credentials` grant 로 발급한 access token 을 사용한다.
  - 등록 client: `community-service-client` (`tenant_id=fan-platform`, `allowed_grants=[client_credentials]`, `allowed_scopes=[account.read, membership.read]`).
- account-service 는 표준 OAuth2 Resource Server 로서 토큰 서명·만료·issuer를 검증하고, 필요 시 `account.read` scope를 추가로 요구할 수 있다 (TASK-BE-253 § Implementation Notes "scope enforcement").

---

## GET /internal/accounts/{accountId}

아티스트 팔로우 전 계정 존재 여부 확인.

**Required headers**:
| Header | Value | 설명 |
|---|---|---|
| `Authorization` | `Bearer <access-token>` | `client_credentials` 토큰. `tenant_id=fan-platform` claim 포함, scope 에 `account.read` 포함 |

**Response 200**:
```json
{
  "id": "string (UUID)",
  "email": "string",
  "status": "string",
  "createdAt": "2026-04-30T00:00:00Z",
  "profile": { "displayName": "string | null", "phoneMasked": "string | null" }
}
```

**Response 404**: 계정 미존재

**Errors**:
| Status | 처리 |
|---|---|
| 401 | 토큰 없음/만료/서명 불일치 — caller 가 token endpoint 재호출 후 재시도 |
| 403 | tenant_id mismatch 또는 scope 부족 — caller 코드 정정 필요 |
| 404 | ArtistNotFoundException — 호출자가 ARTIST_NOT_FOUND(404) 반환 |
| 5xx / timeout | fail-open — 예외 삼킴, 로그 warn, 팔로우 허용 |

## GET /internal/accounts/{accountId}/profile

작성자 표시명 조회 (community-service `AccountProfileLookup`).

**Required headers**: 위와 동일.

**Response 200**:
```json
{ "accountId": "string", "displayName": "string | null" }
```

**Errors**:
| Status | 처리 |
|---|---|
| 401 / 403 | 위와 동일 |
| 5xx / timeout | fail-open — `null` 반환, 로그 warn |

## Caller Constraints (community-service)

- Internal endpoint, gateway가 외부에 노출하지 않음
- 404만 엄격히 처리; 5xx/timeout은 가용성 coupling 방지를 위해 fail-open
- (TASK-BE-253) Spring Security `OAuth2AuthorizedClientManager` (혹은 동등) 가 access token 만료/401 응답 감지 시 자동 재발급 → 1회 재시도. 재시도 후에도 401 이면 fail-open 정책에 따라 처리 (404 는 별도)
