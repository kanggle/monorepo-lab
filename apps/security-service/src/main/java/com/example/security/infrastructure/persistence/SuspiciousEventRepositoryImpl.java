package com.example.security.infrastructure.persistence;

import com.example.security.domain.detection.RiskLevel;
import com.example.security.domain.repository.SuspiciousEventRepository;
import com.example.security.domain.suspicious.SuspiciousEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SuspiciousEventRepositoryImpl implements SuspiciousEventRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final SuspiciousEventJpaRepository jpaRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void save(SuspiciousEvent event) {
        String evidenceJson = writeEvidence(event.getEvidence());
        SuspiciousEventJpaEntity existing = jpaRepository.findById(event.getId()).orElse(null);
        if (existing != null) {
            // idempotent update for lock_request_result transitions
            existing.updateLockRequestResult(event.getLockRequestResult());
            jpaRepository.save(existing);
            return;
        }
        SuspiciousEventJpaEntity entity = SuspiciousEventJpaEntity.create(
                event.getId(),
                event.getAccountId(),
                event.getRuleCode(),
                event.getRiskScore(),
                event.getActionTaken().name(),
                evidenceJson,
                event.getTriggerEventId(),
                event.getDetectedAt(),
                event.getLockRequestResult()
        );
        jpaRepository.save(entity);
    }

    @Override
    public Optional<SuspiciousEvent> findById(String id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<SuspiciousEvent> findByAccountAndRange(String accountId, Instant from, Instant to, int limit) {
        return jpaRepository
                .findByAccountIdAndDetectedAtBetweenOrderByDetectedAtDesc(accountId, from, to)
                .stream()
                .limit(limit <= 0 ? 100 : limit)
                .map(this::toDomain)
                .toList();
    }

    private SuspiciousEvent toDomain(SuspiciousEventJpaEntity e) {
        Map<String, Object> evidence = readEvidence(e.getEvidence());
        SuspiciousEvent event = SuspiciousEvent.create(
                e.getId(), e.getAccountId(), e.getRuleCode(), e.getRiskScore(),
                RiskLevel.valueOf(e.getActionTaken()), evidence, e.getTriggerEventId(), e.getDetectedAt());
        return event.withLockRequestResult(e.getLockRequestResult());
    }

    private String writeEvidence(Map<String, Object> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(evidence);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize evidence", e);
        }
    }

    private Map<String, Object> readEvidence(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }
    }
}
