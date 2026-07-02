package com.example.product.application.dto;

/**
 * KST calendar-period-to-date seller counts for the current tenant.
 *
 * @param today  sellers created today (since KST midnight)
 * @param week   sellers created this ISO week (since Monday KST midnight)
 * @param month  sellers created this calendar month (since 1st KST midnight)
 * @param total  all sellers in the tenant (all-time)
 */
public record SellerPeriodSummary(long today, long week, long month, long total) {
}
