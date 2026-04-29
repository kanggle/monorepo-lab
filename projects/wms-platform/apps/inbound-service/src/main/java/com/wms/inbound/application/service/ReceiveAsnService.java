package com.wms.inbound.application.service;

import com.example.common.id.UuidV7;
import com.wms.inbound.application.command.ReceiveAsnCommand;
import com.wms.inbound.application.port.in.ReceiveAsnUseCase;
import com.wms.inbound.application.port.out.AsnNoSequencePort;
import com.wms.inbound.application.port.out.AsnPersistencePort;
import com.wms.inbound.application.port.out.InboundEventPort;
import com.wms.inbound.application.port.out.MasterReadModelPort;
import com.wms.inbound.application.result.AsnResult;
import com.wms.inbound.domain.event.AsnReceivedEvent;
import com.wms.inbound.domain.exception.AsnNoDuplicateException;
import com.wms.inbound.domain.exception.PartnerInvalidTypeException;
import com.wms.inbound.domain.exception.SkuInactiveException;
import com.wms.inbound.domain.exception.WarehouseNotFoundInReadModelException;
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
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceiveAsnService implements ReceiveAsnUseCase {

    private static final String ROLE_INBOUND_WRITE = "ROLE_INBOUND_WRITE";
    private static final String SYSTEM_ACTOR = "system:erp-webhook";

    private static final Logger log = LoggerFactory.getLogger(ReceiveAsnService.class);

    private final AsnPersistencePort asnPersistence;
    private final AsnNoSequencePort asnNoSequence;
    private final InboundEventPort eventPort;
    private final MasterReadModelPort masterReadModel;
    private final Clock clock;

    public ReceiveAsnService(AsnPersistencePort asnPersistence,
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
    public AsnResult receive(ReceiveAsnCommand command) {
        if (!isSystemActor(command.actorId())) {
            requireRole(command.callerRoles(), ROLE_INBOUND_WRITE);
        }

        WarehouseSnapshot warehouse = masterReadModel.findWarehouse(command.warehouseId())
                .orElseThrow(() -> new WarehouseNotFoundInReadModelException(command.warehouseId()));
        if (!warehouse.isActive()) {
            throw new WarehouseNotFoundInReadModelException(command.warehouseId());
        }

        PartnerSnapshot partner = masterReadModel.findPartner(command.supplierPartnerId())
                .orElseThrow(() -> new PartnerInvalidTypeException(command.supplierPartnerId(), "not found in read model"));
        if (!partner.canSupply()) {
            throw new PartnerInvalidTypeException(command.supplierPartnerId(),
                    "status=" + partner.status() + " type=" + partner.partnerType());
        }

        String asnNo = (command.asnNo() == null || command.asnNo().isBlank())
                ? asnNoSequence.nextAsnNo()
                : command.asnNo();

        if (asnPersistence.existsByAsnNo(asnNo)) {
            throw new AsnNoDuplicateException(asnNo);
        }

        Instant now = clock.instant();
        UUID asnId = UuidV7.randomUuid();
        List<AsnLine> lines = new ArrayList<>();
        int lineNo = 1;
        for (ReceiveAsnCommand.Line cmdLine : command.lines()) {
            SkuSnapshot sku = masterReadModel.findSku(cmdLine.skuId())
                    .orElseThrow(() -> new SkuInactiveException(cmdLine.skuId()));
            if (!sku.isActive()) {
                throw new SkuInactiveException(cmdLine.skuId());
            }
            lines.add(new AsnLine(UuidV7.randomUuid(), asnId, lineNo++,
                    cmdLine.skuId(), cmdLine.lotId(), cmdLine.expectedQty()));
        }

        Asn asn = new Asn(asnId, asnNo, AsnSource.valueOf(command.source()),
                command.supplierPartnerId(), command.warehouseId(),
                command.expectedArriveDate(), command.notes(),
                AsnStatus.CREATED, 0L, now, command.actorId(), now, command.actorId(), lines);

        Asn saved = asnPersistence.save(asn);

        List<AsnReceivedEvent.Line> eventLines = buildEventLines(saved, partner, masterReadModel);
        AsnReceivedEvent event = new AsnReceivedEvent(
                saved.getId(), saved.getAsnNo(), saved.getSource().name(),
                saved.getSupplierPartnerId(), partner.partnerCode(),
                saved.getWarehouseId(), saved.getExpectedArriveDate(),
                eventLines, now, command.actorId());
        eventPort.publish(event);

        log.info("asn_received asnId={} asnNo={} source={}", saved.getId(), saved.getAsnNo(), saved.getSource());
        return toResult(saved);
    }

    private static boolean isSystemActor(String actorId) {
        return actorId != null && actorId.startsWith("system:");
    }

    private static void requireRole(java.util.Set<String> roles, String required) {
        if (roles == null || !roles.contains(required)) {
            throw new AccessDeniedException("Role required: " + required);
        }
    }

    private static List<AsnReceivedEvent.Line> buildEventLines(Asn asn,
                                                                PartnerSnapshot partner,
                                                                MasterReadModelPort masterReadModel) {
        return asn.getLines().stream().map(line -> {
            String skuCode = masterReadModel.findSku(line.getSkuId())
                    .map(SkuSnapshot::skuCode).orElse(null);
            return new AsnReceivedEvent.Line(
                    line.getId(), line.getLineNo(),
                    line.getSkuId(), skuCode,
                    line.getLotId(), line.getExpectedQty());
        }).toList();
    }

    static AsnResult toResult(Asn asn) {
        List<AsnResult.Line> lines = asn.getLines().stream()
                .map(l -> new AsnResult.Line(l.getId(), l.getLineNo(),
                        l.getSkuId(), l.getLotId(), l.getExpectedQty()))
                .toList();
        return new AsnResult(asn.getId(), asn.getAsnNo(), asn.getSource().name(),
                asn.getSupplierPartnerId(), asn.getWarehouseId(),
                asn.getExpectedArriveDate(), asn.getNotes(),
                asn.getStatus().name(), asn.getVersion(),
                asn.getCreatedAt(), asn.getCreatedBy(), asn.getUpdatedAt(), lines);
    }
}
