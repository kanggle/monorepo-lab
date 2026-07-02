package com.example.product.application.dto;

/**
 * KST calendar-period-to-date product counts for the current tenant.
 *
 * @param today  products created today (since KST midnight)
 * @param week   products created this ISO week (since Monday KST midnight)
 * @param month  products created this calendar month (since 1st KST midnight)
 * @param total  all non-deleted products in the tenant (all-time)
 */
public record ProductPeriodSummary(long today, long week, long month, long total) {
}
