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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceiveAsnService implements ReceiveAsnUseCase {

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
            AuthorizationGuards.requireRole(command.callerRoles(), InboundRoles.ROLE_INBOUND_WRITE);
        }

        resolveActiveWarehouseOrThrow(command.warehouseId());
        PartnerSnapshot partner = resolveSupplierPartnerOrThrow(command.supplierPartnerId());
        String asnNo = resolveAsnNoOrThrow(command.asnNo());

        Instant now = clock.instant();
        UUID asnId = UuidV7.randomUuid();
        List<AsnLine> lines = buildAsnLines(command.lines(), asnId);

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

    private void resolveActiveWarehouseOrThrow(UUID warehouseId) {
        WarehouseSnapshot warehouse = masterReadModel.findWarehouse(warehouseId)
                .orElseThrow(() -> new WarehouseNotFoundInReadModelException(warehouseId));
        if (!warehouse.isActive()) {
            throw new WarehouseNotFoundInReadModelException(warehouseId);
        }
    }

    private PartnerSnapshot resolveSupplierPartnerOrThrow(UUID supplierPartnerId) {
        PartnerSnapshot partner = masterReadModel.findPartner(supplierPartnerId)
                .orElseThrow(() -> new PartnerInvalidTypeException(supplierPartnerId, "not found in read model"));
        if (!partner.canSupply()) {
            throw new PartnerInvalidTypeException(supplierPartnerId,
                    "status=" + partner.status() + " type=" + partner.partnerType());
        }
        return partner;
    }

    private String resolveAsnNoOrThrow(String suppliedAsnNo) {
        String asnNo = (suppliedAsnNo == null || suppliedAsnNo.isBlank())
                ? asnNoSequence.nextAsnNo()
                : suppliedAsnNo;
        if (asnPersistence.existsByAsnNo(asnNo)) {
            throw new AsnNoDuplicateException(asnNo);
        }
        return asnNo;
    }

    private List<AsnLine> buildAsnLines(List<ReceiveAsnCommand.Line> cmdLines, UUID asnId) {
        List<AsnLine> lines = new ArrayList<>();
        int lineNo = 1;
        for (ReceiveAsnCommand.Line cmdLine : cmdLines) {
            SkuSnapshot sku = masterReadModel.findSku(cmdLine.skuId())
                    .orElseThrow(() -> new SkuInactiveException(cmdLine.skuId()));
            if (!sku.isActive()) {
                throw new SkuInactiveException(cmdLine.skuId());
            }
            lines.add(new AsnLine(UuidV7.randomUuid(), asnId, lineNo++,
                    cmdLine.skuId(), cmdLine.lotId(), cmdLine.expectedQty()));
        }
        return lines;
    }

    private static boolean isSystemActor(String actorId) {
        return actorId != null && actorId.startsWith(InboundRoles.SYSTEM_ACTOR_PREFIX);
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
