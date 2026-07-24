package com.example.scmplatform.inventoryvisibility.application.service;

import com.example.scmplatform.inventoryvisibility.application.port.outbound.ClockPort;
import com.example.scmplatform.inventoryvisibility.domain.error.NodeTypeConflictException;
import com.example.scmplatform.inventoryvisibility.domain.node.InventoryNode;
import com.example.scmplatform.inventoryvisibility.domain.node.NodeId;
import com.example.scmplatform.inventoryvisibility.domain.node.NodeType;
import com.example.scmplatform.inventoryvisibility.domain.node.repository.InventoryNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Explicit registration use case for a THIRD_PARTY_LOGISTICS inventory node
 * (ADR-MONO-054 §D2 / TASK-SCM-BE-046).
 *
 * <p>Unlike a wms warehouse node (auto-registered on first {@code wms.inventory.*}
 * mutation event), a 3PL node has no event stream to be born from — a 3PL
 * relationship is an onboarding fact, so it is registered explicitly via this
 * use case (exposed by {@code NodeRegistrationController}).
 *
 * <p>Idempotent on {@code (tenantId, nodeExternalId)}: a repeat registration of
 * the same external id, when it already carries {@code THIRD_PARTY_LOGISTICS},
 * is a no-op returning the existing node — never a duplicate row, never a 500.
 * An external id already registered under a **different** node type (e.g. a wms
 * auto-registered warehouse) is a genuine conflict ({@link NodeTypeConflictException},
 * 409) — a 3PL relationship must not silently overwrite an unrelated node.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterThirdPartyLogisticsNodeService {

    private final InventoryNodeRepository nodeRepository;
    private final ClockPort clock;

    /**
     * Register (or idempotently no-op) a THIRD_PARTY_LOGISTICS node.
     *
     * @throws IllegalArgumentException  if {@code nodeExternalId} or {@code name} is blank
     * @throws NodeTypeConflictException if the external id is already registered under
     *                                    a different {@link NodeType}
     */
    @Transactional
    public RegisterThirdPartyLogisticsNodeResult register(String tenantId, String nodeExternalId, String name) {
        if (nodeExternalId == null || nodeExternalId.isBlank()) {
            throw new IllegalArgumentException("nodeExternalId must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }

        Optional<InventoryNode> existing = nodeRepository.findByTenantIdAndExternalId(tenantId, nodeExternalId);
        if (existing.isPresent()) {
            return existingOrConflict(existing.get(), nodeExternalId);
        }

        InventoryNode toRegister = InventoryNode.registerThirdPartyLogistics(
                NodeId.of(UUID.randomUUID()), tenantId, nodeExternalId, name, clock.now());
        try {
            InventoryNode saved = nodeRepository.save(toRegister);
            log.info("registered THIRD_PARTY_LOGISTICS node: externalId={} tenant={} nodeId={}",
                    nodeExternalId, tenantId, saved.getId());
            return new RegisterThirdPartyLogisticsNodeResult(saved, true);
        } catch (DataIntegrityViolationException e) {
            // Concurrent-registration race on uq_inventory_nodes_tenant_external — find-or-register,
            // not a client error (Edge Case: Idempotent registration, TASK-SCM-BE-046).
            log.info("concurrent registration race on externalId={} tenant={}; re-reading existing node",
                    nodeExternalId, tenantId);
            InventoryNode raced = nodeRepository.findByTenantIdAndExternalId(tenantId, nodeExternalId)
                    .orElseThrow(() -> e);
            return existingOrConflict(raced, nodeExternalId);
        }
    }

    private RegisterThirdPartyLogisticsNodeResult existingOrConflict(InventoryNode node, String nodeExternalId) {
        if (node.getNodeType() != NodeType.THIRD_PARTY_LOGISTICS) {
            throw new NodeTypeConflictException(nodeExternalId, node.getNodeType(), NodeType.THIRD_PARTY_LOGISTICS);
        }
        return new RegisterThirdPartyLogisticsNodeResult(node, false);
    }

    /** Result of a registration attempt: the resolved node, and whether it was newly created. */
    public record RegisterThirdPartyLogisticsNodeResult(InventoryNode node, boolean created) {
    }
}
