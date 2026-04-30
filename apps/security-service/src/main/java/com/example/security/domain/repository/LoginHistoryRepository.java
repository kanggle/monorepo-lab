package com.example.security.domain.repository;

import com.example.security.domain.history.LoginHistoryEntry;

import java.util.Optional;

public interface LoginHistoryRepository {

    void save(LoginHistoryEntry entry);

    boolean existsByEventId(String eventId);

    /**
     * Find the most recent successful login for the given account,
     * excluding the current event. Used by ImpossibleTravelRule.
     */
    Optional<LoginHistoryEntry> findLatestSuccessByAccountId(String accountId);
}
