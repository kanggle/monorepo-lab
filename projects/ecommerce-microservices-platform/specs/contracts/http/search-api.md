# HTTP Contract: search-service

> 🔴 **업무 인가 — TASK-MONO-795 (ADR-MONO-083 P8 · 2026-10-16 UTC)**: 이 계약의 **운영자** 엔드포인트는 서비스가 영역 업무 역할로 다시 판정한다 — `ECOMMERCE_PRODUCT_WRITE`(재색인 — 검색 전용 역할은 없다, 소유자 결정 Q1). 조회(`GET`) = READ ∨ WRITE · 변경 = WRITE. `ECOMMERCE_OPERATOR`(진입 역할) 단독 · `CUSTOMER` · 빈 `X-User-Role` → `403`. `'*'` 와일드카드에는 열지 않는다. 아래 본문의 «`X-User-Role` ∋ `ECOMMERCE_OPERATOR`» · «gateway `roles ∋ ECOMMERCE_OPERATOR` … Service applies no additional ecommerce-local RBAC» 문장은 운영자 엔드포인트에 한해 이 노트로 **대체**된다(게이트웨이 판정 자체는 그대로). 정본: [`iam-integration.md` § 업무 역할](../../integration/iam-integration.md#업무-역할-adr-mono-083-p8--task-mono-795--정본). 소비자 · 공개 · 워크로드 엔드포인트는 무변경.

## Overview
Published HTTP API for search-service.
All endpoints are accessible through gateway-service only.
All endpoints are public — no authentication required.
Search results are eventually consistent with product-service data.

---

## Base Path
`/api/search`

---

## Endpoints

### GET /api/search/products
Search products by keyword and filters.

**Query Parameters**
- `q` (required) — search keyword
- `categoryId` (optional) — filter by category
- `minPrice` (optional) — minimum price filter
- `maxPrice` (optional) — maximum price filter
- `status` (optional, default: `ON_SALE`) — product status filter
- `sort` (optional, default: `relevance`) — sort order: `relevance`, `price_asc`, `price_desc`, `newest`
- `page` (default: 0) — page number
- `size` (default: 20, max: 100) — page size

**Response 200**
```json
{
  "query": "string",
  "content": [
    {
      "productId": "string (UUID)",
      "name": "string",
      "price": 10000,
      "status": "ON_SALE",
      "thumbnailUrl": "string",
      "categoryId": "string (UUID)",
      "score": 1.23
    }
  ],
  "facets": {
    "categories": [
      { "categoryId": "string (UUID)", "name": "string", "count": 10 }
    ],
    "priceRanges": [
      { "min": 0, "max": 10000, "count": 5 }
    ]
  },
  "page": 0,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | INVALID_SEARCH_REQUEST | Search request is invalid (missing or blank q parameter, invalid size) |

---

### POST /api/search/admin/reindex (Admin — internal only)
Full reindex of the Elasticsearch product index from product-service data. Intended for
operational recovery when the index diverges from the DB (e.g. event loss, seed data).
**Not exposed through the public gateway** — accessible only on the internal service port (8085).

**Authorization**: `X-User-Role: ECOMMERCE_OPERATOR` header required (validated in-service).

**Query Parameters**
- `batchSize` (default: 50, must be > 0) — number of products fetched per batch

**Response 200**
```json
{
  "indexed": 123,
  "batchSize": 50
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | `batchSize` is not a positive integer |
| 403 | ACCESS_DENIED | Admin role required |

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
- Results reflect the search index state, which may lag behind product-service by a short delay.
- If a product was recently updated, the search index may not reflect it immediately.
- `q` must not be empty. Minimum 1 character required.
- `score` indicates relevance and is for informational purposes only.
