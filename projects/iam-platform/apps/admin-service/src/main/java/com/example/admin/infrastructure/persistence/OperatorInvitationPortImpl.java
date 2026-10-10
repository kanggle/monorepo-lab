package com.example.admin.infrastructure.persistence;

import com.example.admin.application.exception.OperatorInvitationAlreadyPendingException;
import com.example.admin.application.port.OperatorInvitationPort;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * TASK-MONO-772 S2 — JPA-backed {@link OperatorInvitationPort}. Internal operator ids are translated to the
 * external UUIDs here (one batched lookup per page), so nothing above infrastructure sees a surrogate key.
 */
@Component
@RequiredArgsConstructor
public class OperatorInvitationPortImpl implements OperatorInvitationPort {

    /** The UNIQUE key on the generated {@code pending_key} column (V0050). */
    static final String PENDING_KEY_CONSTRAINT = "uk_operator_invitation_pending_key";

    private final OperatorInvitationJpaRepository repository;
    private final AdminOperatorJpaRepository operatorRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean existsPending(String tenantId, String email) {
        return repository.existsByTenantIdAndEmailAndStatus(tenantId, email, "PENDING");
    }

    @Override
    @Transactional
    public InvitationView create(NewInvitation row) {
        OperatorInvitationJpaEntity entity = OperatorInvitationJpaEntity.create(
                row.invitationId(), row.tenantId(), row.email(), row.displayName(),
                joinRoles(row.roles()), row.tokenHash(), row.expiresAt(), row.invitedByInternalId(), row.now());
        try {
            entity = repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            if (mentionsPendingKey(e)) {
                // Two creates for the same (tenant, email) raced past the pre-check; the DB kept one.
                throw new OperatorInvitationAlreadyPendingException(
                        "A pending invitation for this email already exists in the tenant — resend it instead");
            }
            throw e;
        }
        return toViews(List.of(entity)).get(0);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InvitationView> findByInvitationId(String invitationId) {
        if (invitationId == null || invitationId.isBlank()) {
            return Optional.empty();
        }
        return repository.findByInvitationId(invitationId).map(e -> toViews(List.of(e)).get(0));
    }

    @Override
    @Transactional(readOnly = true)
    public InvitationPage findPage(String tenantId, String status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<OperatorInvitationJpaEntity> rows = tenantId == null
                ? repository.findByStatusOrderByCreatedAtDesc(status, pageable)
                : repository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status, pageable);
        return new InvitationPage(toViews(rows.getContent()), rows.getTotalElements(),
                rows.getNumber(), rows.getSize(), rows.getTotalPages());
    }

    @Override
    @Transactional
    public boolean cancelIfPending(long internalId, long cancelledByInternalId, Instant at) {
        return repository.cancelIfPending(internalId, cancelledByInternalId, at) == 1;
    }

    @Override
    @Transactional
    public boolean rotateIfPending(long internalId, int expectedVersion, String newTokenHash, Instant newExpiresAt,
                                   long invitedByInternalId, Instant at) {
        return repository.rotateIfPending(
                internalId, expectedVersion, newTokenHash, newExpiresAt, invitedByInternalId, at) == 1;
    }

    @Override
    @Transactional
    public void recordDelivery(long internalId, String tokenHash, String deliveryStatus, Instant at) {
        repository.recordDelivery(internalId, tokenHash, deliveryStatus, at);
    }

    // ── mapping ──────────────────────────────────────────────────────────────

    static String joinRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return "";
        }
        return String.join(",", roles.stream().filter(r -> r != null && !r.isBlank()).distinct().sorted().toList());
    }

    static List<String> splitRoles(String roles) {
        if (roles == null || roles.isBlank()) {
            return List.of();
        }
        return Arrays.stream(roles.split(",")).map(String::trim).filter(r -> !r.isEmpty()).toList();
    }

    private static boolean mentionsPendingKey(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String m = t.getMessage();
            if (m != null && m.toLowerCase(Locale.ROOT).contains(PENDING_KEY_CONSTRAINT)) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    private List<InvitationView> toViews(Collection<OperatorInvitationJpaEntity> rows) {
        Set<Long> operatorIds = new HashSet<>();
        for (OperatorInvitationJpaEntity e : rows) {
            operatorIds.add(e.getInvitedBy());
            if (e.getAcceptedOperatorId() != null) {
                operatorIds.add(e.getAcceptedOperatorId());
            }
        }
        Map<Long, String> uuidById = new HashMap<>();
        if (!operatorIds.isEmpty()) {
            for (AdminOperatorJpaEntity op : operatorRepository.findAllById(operatorIds)) {
                uuidById.put(op.getId(), op.getOperatorId());
            }
        }
        List<InvitationView> out = new ArrayList<>(rows.size());
        for (OperatorInvitationJpaEntity e : rows) {
            out.add(new InvitationView(
                    e.getId(), e.getInvitationId(), e.getTenantId(), e.getEmail(), e.getDisplayName(),
                    splitRoles(e.getRoles()), e.getStatus(), e.getExpiresAt(),
                    e.getInvitedBy(), uuidById.get(e.getInvitedBy()),
                    e.getLastDeliveryStatus(), e.getLastDeliveryAt(),
                    e.getAcceptedAt(),
                    e.getAcceptedOperatorId() == null ? null : uuidById.get(e.getAcceptedOperatorId()),
                    e.getCancelledAt(), e.getCreatedAt(), e.getVersion(), e.getTokenHash()));
        }
        return out;
    }
}
