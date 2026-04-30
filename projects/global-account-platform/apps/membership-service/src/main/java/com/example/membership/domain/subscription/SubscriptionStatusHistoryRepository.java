package com.example.membership.domain.subscription;

public interface SubscriptionStatusHistoryRepository {

    void append(SubscriptionStatusHistoryEntry entry);
}
