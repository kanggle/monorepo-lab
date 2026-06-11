package com.example.scmplatform.demandplanning.adapter.inbound.web.dto;

/**
 * Pagination metadata for list responses.
 */
public record PageMeta(int page, int size, long totalElements, int totalPages) {
}
