# HTTP Contract: review-service

## Overview
Published HTTP API for review-service.
All endpoints are accessible through gateway-service only.
Product review listing and summary are public endpoints (no auth required).
Write operations require an authenticated user (Bearer token).

---

## Base Path
`/api/reviews`

---

## Endpoints

### POST /api/reviews
Create a review for a purchased product.

**Request Body**
```json
{
  "productId": "string (UUID)",
  "rating": 5,
  "title": "string",
  "content": "string"
}
```

**Response 201**
```json
{
  "reviewId": "string (UUID)"
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | INVALID_REVIEW_REQUEST | Missing or invalid field |
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 409 | REVIEW_ALREADY_EXISTS | User already reviewed this product |
| 422 | PRODUCT_NOT_PURCHASED | User has not purchased this product |

---

### GET /api/reviews/products/{productId}
List reviews for a product. Public endpoint (no auth required).

**Query Parameters**
- `page` (default: 0) — page number
- `size` (default: 20) — page size
- `sort` (default: `createdAt,desc`) — sort field and direction

**Response 200**
```json
{
  "content": [
    {
      "reviewId": "string (UUID)",
      "userId": "string (UUID)",
      "rating": 5,
      "title": "string",
      "content": "string",
      "createdAt": "string (ISO 8601)",
      "updatedAt": "string (ISO 8601)"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 15,
  "averageRating": 4.3,
  "totalReviews": 15
}
```

---

### GET /api/reviews/products/{productId}/summary
Get rating summary for a product. Public endpoint (no auth required).

**Response 200**
```json
{
  "productId": "string (UUID)",
  "averageRating": 4.3,
  "totalReviews": 15,
  "ratingDistribution": {
    "1": 1,
    "2": 0,
    "3": 2,
    "4": 5,
    "5": 7
  }
}
```

---

### GET /api/reviews/me
List reviews written by the authenticated user.

**Query Parameters**
- `page` (default: 0) — page number
- `size` (default: 20) — page size

**Response 200**
```json
{
  "content": [
    {
      "reviewId": "string (UUID)",
      "productId": "string (UUID)",
      "productName": "string",
      "rating": 5,
      "title": "string",
      "content": "string",
      "createdAt": "string (ISO 8601)"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 3
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 401 | UNAUTHORIZED | Missing or invalid access token |

---

### PUT /api/reviews/{reviewId}
Update a review. Only the review author may update.

**Request Body**
```json
{
  "rating": 4,
  "title": "string",
  "content": "string"
}
```

**Response 200**
```json
{
  "reviewId": "string (UUID)"
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | INVALID_REVIEW_REQUEST | Missing or invalid field |
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 403 | ACCESS_DENIED | Not the review author |
| 404 | REVIEW_NOT_FOUND | Review does not exist |

---

### DELETE /api/reviews/{reviewId}
Delete a review. Only the review author may delete.

**Response 204** No content.

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 403 | ACCESS_DENIED | Not the review author |
| 404 | REVIEW_NOT_FOUND | Review does not exist |

---

## Rating Values

| Value | Description |
|---|---|
| 1 | Very poor |
| 2 | Poor |
| 3 | Average |
| 4 | Good |
| 5 | Excellent |

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
- `userId` is extracted from the authentication token, not from the request body.
- Product review listing and summary are publicly accessible (no authentication required).
- Purchase verification is done via synchronous call to order-service.
- Internal stack traces must not appear in error responses.
