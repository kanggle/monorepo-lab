package com.wms.outbound.adapter.in.web.dto.response;

import java.util.List;

/**
 * Non-paginated {@code { "content": [...] }} envelope for the §2.4
 * {@code GET /orders/{id}/picking-requests} response.
 *
 * <p>Intentionally different from {@link PagedResponse} (which forces
 * page-metadata). v1: at most one element; the array shape is forward-compatible
 * with v2 wave/partial-picking where multiple requests per order may exist.
 */
public record PickingRequestListResponse(List<PickingRequestResponse> content) {
}
