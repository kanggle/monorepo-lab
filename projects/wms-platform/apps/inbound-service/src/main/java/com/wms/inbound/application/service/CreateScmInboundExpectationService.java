package com.wms.inbound.application.service;

import com.example.common.id.UuidV7;
import com.wms.inbound.application.command.CreateScmInboundExpectationCommand;
import com.wms.inbound.application.exception.InboundExpectationRejectedException;
import com.wms.inbound.application.port.in.CreateScmInboundExpectationUseCase;
import com.wms.inbound.application.port.out.AsnNoSequencePort;
import com.wms.inbound.application.port.out.AsnPersistencePort;
import com.wms.inbound.application.port.out.InboundEventPort;
import com.wms.inbound.application.port.out.MasterReadModelPort;
import com.wms.inbound.domain.event.AsnReceivedEvent;
import com.wms.inbound.domain.model.Asn;
import com.wms.inbound.domain.model.AsnLine;
import com.wms.inbound.domain.model.AsnSource;
import com.wms.inbound.domain.model.AsnStatus;
import com.wms.inbound.domain.model.masterref.PartnerSnapshot;
import com.wms.inbound.domain.model.masterref.SkuSnapshot;
import com.wms.inbound.domain.model.masterref.WarehouseSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADR-MONO-050 D3/D5 — create a wms inbound expectation (ASN) from an scm confirmed-PO event.
 *
 * <p>Realises the ADR's conceptual {@code InboundExpectation(EXPECTED)} as the existing
 * {@link Asn} aggregate in its initial {@link AsnStatus#CREATED} state (the "expected, not yet
 * received" state), with {@code source = SCM_PROCUREMENT} and the additive {@code poNumber} /
 * {@code poId} trace. Downstream (검수 → 적치 → {@code wms.inventory.received.v1}) is the existing
 * flow, unchanged — it enters at {@code CREATED}. This service creates the promise only; it makes
 * <strong>no</strong> stock mutation.
 *
 * <h2>Warehouse addressing (D3)</h2>
 * The destination is resolved from {@code destinationWarehouseCode} carried on the event —
 * single- and multi-warehouse deployments run the same code path with no branch on warehouse
 * count. Unknown/inactive destinations fail closed (→ DLT).
 */
@Service
public class CreateScmInboundExpectationService implements CreateScmInboundExpectationUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateScmInboundExpectationService.class);

    /** v1 accepts only own-warehouse destinations (ADR-MONO-050 D4). */
    private static final String NODE_TYPE_WMS_WAREHOUSE = "WMS_WAREHOUSE";
    /** System actor for scm-sourced expectations (skips the human role gate). */
    private static final String SCM_ACTOR = InboundRoles.SYSTEM_ACTOR_PREFIX + "scm-procurement";

    private final AsnPersistencePort asnPersistence;
    private final AsnNoSequencePort asnNoSequence;
    private final InboundEventPort eventPort;
    private final MasterReadModelPort masterReadModel;
    private final Clock clock;

    public CreateScmInboundExpectationService(AsnPersistencePort asnPersistence,
                                              AsnNoSequencePort asnNoSequence,
                                              InboundEventPort eventPort,
                                              MasterReadModelPort masterReadModel,
                                              Clock clock) {
        this.asnPersistence = asnPersistence;
        this.asnNoSequence = asnNoSequence;
        this.eventPort = eventPort;
        this.masterReadModel = masterReadModel;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Optional<UUID> create(CreateScmInboundExpectationCommand command) {
        requireText(command.poNumber(), "poNumber");
        if (command.lines() == null || command.lines().isEmpty()) {
            throw new InboundExpectationRejectedException("inbound-expected has no lines: po=" + command.poNumber());
        }

        // D4 — defensive node-type gate (scm filters 3PL producer-side; reject if one arrives).
        if (!NODE_TYPE_WMS_WAREHOUSE.equals(command.destinationNodeType())) {
            throw new InboundExpectationRejectedException(
                    "unsupported destinationNodeType=" + command.destinationNodeType()
                            + " (v1 accepts only WMS_WAREHOUSE); po=" + command.poNumber());
        }

        // D3 — resolve the addressed destination warehouse (single AND multi share this path).
        WarehouseSnapshot warehouse = resolveActiveWarehouseOrReject(command.destinationWarehouseCode(),
                command.poNumber());

        // D6.2 — business dedup: don't create a second open expectation for the same PO.
        if (asnPersistence.existsOpenByPoNumber(command.poNumber())) {
            log.info("scm_inbound_expected_business_duplicate poNumber={} — open expectation exists, skipping",
                    command.poNumber());
            return Optional.empty();
        }

        PartnerSnapshot supplier = resolveActiveSupplierOrReject(command.supplierCode(), command.poNumber());

        Instant now = clock.instant();
        UUID asnId = UuidV7.randomUuid();
        List<AsnLine> lines = buildLines(command.lines(), asnId, command.poNumber());

        String asnNo = asnNoSequence.nextAsnNo();
        Asn asn = new Asn(asnId, asnNo, AsnSource.SCM_PROCUREMENT,
                supplier.id(), warehouse.id(),
                command.expectedArrivalDate(), null,
                command.poNumber(), command.poId(),
                AsnStatus.CREATED, 0L, now, SCM_ACTOR, now, SCM_ACTOR, lines);

        Asn saved = asnPersistence.save(asn);

        eventPort.publish(new AsnReceivedEvent(
                saved.getId(), saved.getAsnNo(), saved.getSource().name(),
                saved.getSupplierPartnerId(), supplier.partnerCode(),
                saved.getWarehouseId(), saved.getExpectedArriveDate(),
                buildEventLines(saved), now, SCM_ACTOR));

        log.info("scm_inbound_expected_created asnId={} asnNo={} poNumber={} warehouseId={} warehouseCode={}",
                saved.getId(), saved.getAsnNo(), command.poNumber(), warehouse.id(), warehouse.warehouseCode());
        return Optional.of(saved.getId());
    }

    private WarehouseSnapshot resolveActiveWarehouseOrReject(String warehouseCode, String poNumber) {
        requireText(warehouseCode, "destinationWarehouseId");
        WarehouseSnapshot warehouse = masterReadModel.findWarehouseByCode(warehouseCode)
                .orElseThrow(() -> new InboundExpectationRejectedException(
                        "unknown destinationWarehouseId=" + warehouseCode + "; po=" + poNumber));
        if (!warehouse.isActive()) {
            throw new InboundExpectationRejectedException(
                    "inactive destinationWarehouseId=" + warehouseCode + "; po=" + poNumber);
        }
        return warehouse;
    }

    private PartnerSnapshot resolveActiveSupplierOrReject(String supplierCode, String poNumber) {
        requireText(supplierCode, "supplierId");
        PartnerSnapshot partner = masterReadModel.findPartnerByCode(supplierCode)
                .orElseThrow(() -> new InboundExpectationRejectedException(
                        "unknown supplierId=" + supplierCode + "; po=" + poNumber));
        if (!partner.canSupply()) {
            throw new InboundExpectationRejectedException(
                    "supplier cannot supply supplierId=" + supplierCode
                            + " status=" + partner.status() + " type=" + partner.partnerType()
                            + "; po=" + poNumber);
        }
        return partner;
    }

    private List<AsnLine> buildLines(List<CreateScmInboundExpectationCommand.Line> cmdLines,
                                     UUID asnId, String poNumber) {
        List<AsnLine> lines = new ArrayList<>();
        int lineNo = 1;
        for (CreateScmInboundExpectationCommand.Line cmdLine : cmdLines) {
            requireText(cmdLine.skuCode(), "lines[].skuCode");
            if (cmdLine.expectedQty() <= 0) {
                throw new InboundExpectationRejectedException(
                        "non-positive expectedQty for sku=" + cmdLine.skuCode() + "; po=" + poNumber);
            }
            SkuSnapshot sku = masterReadModel.findSkuByCode(cmdLine.skuCode())
                    .orElseThrow(() -> new InboundExpectationRejectedException(
                            "unknown skuCode=" + cmdLine.skuCode() + "; po=" + poNumber));
            if (!sku.isActive()) {
                throw new InboundExpectationRejectedException(
                        "inactive skuCode=" + cmdLine.skuCode() + "; po=" + poNumber);
            }
            lines.add(new AsnLine(UuidV7.randomUuid(), asnId, lineNo++, sku.id(), null, cmdLine.expectedQty()));
        }
        return lines;
    }

    private List<AsnReceivedEvent.Line> buildEventLines(Asn asn) {
        return asn.getLines().stream().map(line -> {
            String skuCode = masterReadModel.findSku(line.getSkuId())
                    .map(SkuSnapshot::skuCode).orElse(null);
            return new AsnReceivedEvent.Line(line.getId(), line.getLineNo(),
                    line.getSkuId(), skuCode, line.getLotId(), line.getExpectedQty());
        }).toList();
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InboundExpectationRejectedException("missing required field: " + field);
        }
    }
}
