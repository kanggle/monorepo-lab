package com.wms.outbound.adapter.in.web.dto.response;

import java.util.List;

/**
 * Generic paginated response envelope used by the list endpoints.
 */
public record PagedResponse<T>(List<T> items, int page, int size, long total) {
}
