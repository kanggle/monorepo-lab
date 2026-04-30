# Internal HTTP Contract: community-service → account-service

**호출 방향**: community-service (client) → account-service (server)

---

## GET /internal/accounts/{accountId}

아티스트 팔로우 전 계정 존재 여부 확인.

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
| 404 | ArtistNotFoundException — 호출자가 ARTIST_NOT_FOUND(404) 반환 |
| 5xx / timeout | fail-open — 예외 삼킴, 로그 warn, 팔로우 허용 |

## Caller Constraints (community-service)
- Internal endpoint, gateway가 외부에 노출하지 않음
- 404만 엄격히 처리; 5xx/timeout은 가용성 coupling 방지를 위해 fail-open
