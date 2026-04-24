package com.example.common.page;

/**
 * Common pagination request. Used across services as a cross-service technical DTO.
 * <p>
 * Constraints:
 * <ul>
 *   <li>{@code page} must be >= 0</li>
 *   <li>{@code size} must be >= 1 and <= {@value #MAX_SIZE}</li>
 * </ul>
 */
public record PageQuery(
        int page,
        int size,
        String sortBy,
        String sortDirection
) {
    public static final int MAX_SIZE = 100;

    public PageQuery {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0, got: " + page);
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be >= 1, got: " + size);
        }
        if (size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be <= " + MAX_SIZE + ", got: " + size);
        }
    }

    /**
     * Creates a PageQuery with sanitized values (clamps to valid range).
     */
    public static PageQuery of(int page, int size, String sortBy, String sortDirection) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(Math.min(size, MAX_SIZE), 1);
        return new PageQuery(safePage, safeSize, sortBy, sortDirection);
    }
}
