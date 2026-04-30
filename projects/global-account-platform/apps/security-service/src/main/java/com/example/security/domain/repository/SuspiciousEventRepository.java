package com.example.security.domain.repository;

import com.example.security.domain.suspicious.SuspiciousEvent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SuspiciousEventRepository {

    void save(SuspiciousEvent event);

    Optional<SuspiciousEvent> findById(String id);

    /** Reverse-chronological by detectedAt, bounded by accountId and [from, to). */
    List<SuspiciousEvent> findByAccountAndRange(String accountId, Instant from, Instant to, int limit);
}
