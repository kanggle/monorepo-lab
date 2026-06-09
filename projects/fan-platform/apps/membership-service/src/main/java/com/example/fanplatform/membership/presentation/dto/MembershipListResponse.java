package com.example.fanplatform.membership.presentation.dto;

import com.example.fanplatform.membership.application.MembershipView;

import java.time.Instant;
import java.util.List;

/**
 * List response: {@code { content: [ item, ... ] }}. Each item is the slim
 * listing shape from the membership-api contract (no tenantId / accountId /
 * paymentRef). {@code active} is the read-time evaluation.
 */
public record MembershipListResponse(List<Item> content) {

    public static MembershipListResponse from(List<MembershipView> views) {
        return new MembershipListResponse(views.stream().map(Item::from).toList());
    }

    public record Item(
            String membershipId,
            String tier,
            String status,
            Instant validFrom,
            Instant validTo,
            int planMonths,
            boolean active,
            Instant createdAt,
            Instant canceledAt) {

        static Item from(MembershipView v) {
            return new Item(
                    v.membershipId(), v.tier().name(), v.status().name(),
                    v.validFrom(), v.validTo(), v.planMonths(),
                    v.active(), v.createdAt(), v.canceledAt());
        }
    }
}
