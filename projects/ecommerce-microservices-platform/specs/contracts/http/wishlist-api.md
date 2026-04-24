# HTTP Contract: user-service (Wishlist)

## Overview
Published HTTP API for wishlist management within user-service.
All endpoints require authentication via `X-User-Id` header (set by gateway-service after token validation).

---

## Base Path
`/api/wishlists`

---

## Endpoints

### POST /api/wishlists
Add a product to the user's wishlist.

**Headers**
- `X-User-Id` (required) — authenticated user ID (UUID)

**Request Body**
```json
{
  "productId": "string (UUID)"
}
```

**Response 201**
```json
{
  "wishlistItemId": "string (UUID)",
  "productId": "string (UUID)"
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Missing or invalid productId |
| 401 | UNAUTHORIZED | X-User-Id header is missing |
| 404 | USER_PROFILE_NOT_FOUND | user_profiles 행이 없는 유저 요청 |
| 409 | ALREADY_IN_WISHLIST | Product is already in the wishlist |
| 409 | DATA_INTEGRITY_VIOLATION | Data integrity constraint was violated (backstop for concurrent duplicate insert) |
| 409 | WISHLIST_LIMIT_EXCEEDED | Wishlist has reached the maximum limit of 100 items |

---

### GET /api/wishlists/me
Get the authenticated user's wishlist with pagination.

**Headers**
- `X-User-Id` (required) — authenticated user ID (UUID)

**Query Parameters**
- `page` (default: 0) — page number
- `size` (default: 20) — page size (max: 100)

**Response 200**
```json
{
  "content": [
    {
      "wishlistItemId": "string (UUID)",
      "productId": "string (UUID)",
      "productName": "string | null",
      "productPrice": 0,
      "productStatus": "string",
      "addedAt": "string (ISO 8601)"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 100
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 401 | UNAUTHORIZED | X-User-Id header is missing |

---

### DELETE /api/wishlists/{wishlistItemId}
Remove an item from the user's wishlist.

**Headers**
- `X-User-Id` (required) — authenticated user ID (UUID)

**Path Parameters**
- `wishlistItemId` (required) — wishlist item ID (UUID)

**Response 204**
No content.

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 401 | UNAUTHORIZED | X-User-Id header is missing |
| 403 | ACCESS_DENIED | Wishlist item does not belong to the user |
| 404 | WISHLIST_ITEM_NOT_FOUND | Wishlist item with given ID does not exist |

---

### GET /api/wishlists/me/check
Check if a product is in the user's wishlist.

**Headers**
- `X-User-Id` (required) — authenticated user ID (UUID)

**Query Parameters**
- `productId` (required) — product ID (UUID)

**Response 200**
```json
{
  "productId": "string (UUID)",
  "inWishlist": true,
  "wishlistItemId": "string (UUID) | null"
}
```

- `wishlistItemId` is returned when `inWishlist` is `true`. When `inWishlist` is `false`, `wishlistItemId` is `null`.

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Missing required parameter: productId |
| 401 | UNAUTHORIZED | X-User-Id header is missing |

---

## Error Response Format
```json
{
  "code": "string",
  "message": "string",
  "timestamp": "string (ISO 8601)"
}
```

## Notes
- Product information (name, price, status) in the wishlist list response is fetched from product-service at query time.
- If product-service is unavailable or the product has been deleted, the product is returned with status "DELETED" and null name.
- Internal stack traces must not appear in error responses.
