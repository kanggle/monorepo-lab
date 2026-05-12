package com.wms.master.application.service;

import com.example.common.page.PageResult;
import com.wms.master.application.command.CreatePartnerCommand;
import com.wms.master.application.command.DeactivatePartnerCommand;
import com.wms.master.application.command.ReactivatePartnerCommand;
import com.wms.master.application.command.UpdatePartnerCommand;
import com.wms.master.application.port.in.PartnerCrudUseCase;
import com.wms.master.application.port.in.PartnerQueryUseCase;
import com.wms.master.application.port.out.DomainEventPort;
import com.wms.master.application.port.out.PartnerPersistencePort;
import com.wms.master.application.query.ListPartnersQuery;
import com.wms.master.application.result.PartnerResult;
import com.wms.master.domain.event.PartnerCreatedEvent;
import com.wms.master.domain.event.PartnerDeactivatedEvent;
import com.wms.master.domain.event.PartnerReactivatedEvent;
import com.wms.master.domain.event.PartnerUpdatedEvent;
import com.wms.master.domain.exception.PartnerNotFoundException;
import com.wms.master.domain.model.Partner;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Partner application service. Mirrors {@code SkuService} layering:
 * aggregate-scoped transactional boundary; domain events published inside the
 * same transaction as the state change (outbox adapter handles the rest).
 *
 * <p>Partner has no parent aggregate reference and (in v1) no cross-aggregate
 * deactivation check — {@code Lot.supplier_partner_id} is a soft reference;
 * the hard-FK enforcement is deferred to v2 per
 * {@code domain-model.md} §5 "v1: no such link". This keeps the deactivate
 * path symmetric with the simple Sku/Warehouse case for now.
 *
 * <p>Optimistic locking is consolidated through {@link AggregateVersionGuard}
 * (TASK-BE-141 utility) — same shape as every other master aggregate.
 */
@Service
@Transactional
public class PartnerService implements PartnerCrudUseCase, PartnerQueryUseCase {

    private static final String AGGREGATE_TYPE = "Partner";

    private final PartnerPersistencePort persistencePort;
    private final DomainEventPort eventPort;

    public PartnerService(PartnerPersistencePort persistencePort,
                          DomainEventPort eventPort) {
        this.persistencePort = persistencePort;
        this.eventPort = eventPort;
    }

    @Override
    @PreAuthorize("hasRole('MASTER_WRITE') or hasRole('MASTER_ADMIN')")
    public PartnerResult create(CreatePartnerCommand command) {
        Partner created = Partner.create(
                command.partnerCode(),
                command.name(),
                command.partnerType(),
                command.businessNumber(),
                command.contactName(),
                command.contactEmail(),
                command.contactPhone(),
                command.address(),
                command.actorId());
        Partner saved = persistencePort.insert(created);
        eventPort.publish(List.of(PartnerCreatedEvent.from(saved)));
        return PartnerResult.from(saved);
    }

    @Override
    @PreAuthorize("hasRole('MASTER_WRITE') or hasRole('MASTER_ADMIN')")
    public PartnerResult update(UpdatePartnerCommand command) {
        Partner loaded = loadOrThrow(command.id());
        // Reject immutable-field attempts BEFORE the version check so that a
        // PATCH with a bad partnerCode fails with IMMUTABLE_FIELD (422) even
        // when the version is correct — symmetric with SkuService.
        loaded.rejectImmutableChange(command.partnerCodeAttempt());
        AggregateVersionGuard.requireMatch(AGGREGATE_TYPE, command.id(), command.version(), loaded.getVersion());

        List<String> changedFields = collectChangedFields(loaded, command);
        loaded.applyUpdate(
                command.name(),
                command.partnerType(),
                command.businessNumber(),
                command.contactName(),
                command.contactEmail(),
                command.contactPhone(),
                command.address(),
                command.actorId());

        Partner saved = AggregateVersionGuard.saveWithOptimisticLock(
                AGGREGATE_TYPE, loaded.getId(), () -> persistencePort.update(loaded));

        if (!changedFields.isEmpty()) {
            eventPort.publish(List.of(PartnerUpdatedEvent.from(saved, changedFields)));
        }
        return PartnerResult.from(saved);
    }

    @Override
    @PreAuthorize("hasRole('MASTER_ADMIN')")
    public PartnerResult deactivate(DeactivatePartnerCommand command) {
        Partner loaded = loadOrThrow(command.id());
        AggregateVersionGuard.requireMatch(AGGREGATE_TYPE, command.id(), command.version(), loaded.getVersion());

        // v1: no cross-aggregate referential check. Lot.supplier_partner_id is
        // a soft reference per domain-model.md §5; hard FK activation = v2.
        loaded.deactivate(command.actorId());

        Partner saved = AggregateVersionGuard.saveWithOptimisticLock(
                AGGREGATE_TYPE, loaded.getId(), () -> persistencePort.update(loaded));
        eventPort.publish(List.of(PartnerDeactivatedEvent.from(saved, command.reason())));
        return PartnerResult.from(saved);
    }

    @Override
    @PreAuthorize("hasRole('MASTER_ADMIN')")
    public PartnerResult reactivate(ReactivatePartnerCommand command) {
        Partner loaded = loadOrThrow(command.id());
        AggregateVersionGuard.requireMatch(AGGREGATE_TYPE, command.id(), command.version(), loaded.getVersion());

        loaded.reactivate(command.actorId());
        Partner saved = AggregateVersionGuard.saveWithOptimisticLock(
                AGGREGATE_TYPE, loaded.getId(), () -> persistencePort.update(loaded));
        eventPort.publish(List.of(PartnerReactivatedEvent.from(saved)));
        return PartnerResult.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('MASTER_READ') or hasRole('MASTER_WRITE') or hasRole('MASTER_ADMIN')")
    public PartnerResult findById(UUID id) {
        return PartnerResult.from(loadOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('MASTER_READ') or hasRole('MASTER_WRITE') or hasRole('MASTER_ADMIN')")
    public PartnerResult findByCode(String partnerCode) {
        if (partnerCode == null || partnerCode.isBlank()) {
            throw new PartnerNotFoundException("<blank>");
        }
        Partner partner = persistencePort.findByCode(partnerCode.strip())
                .orElseThrow(() -> new PartnerNotFoundException(partnerCode));
        return PartnerResult.from(partner);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('MASTER_READ') or hasRole('MASTER_WRITE') or hasRole('MASTER_ADMIN')")
    public PageResult<PartnerResult> list(ListPartnersQuery query) {
        return persistencePort.findPage(query.criteria(), query.pageQuery())
                .map(PartnerResult::from);
    }

    private Partner loadOrThrow(UUID id) {
        return persistencePort.findById(id)
                .orElseThrow(() -> new PartnerNotFoundException(id.toString()));
    }

    private static List<String> collectChangedFields(Partner current, UpdatePartnerCommand cmd) {
        List<String> fields = new ArrayList<>();
        if (cmd.name() != null && !Objects.equals(cmd.name(), current.getName())) {
            fields.add("name");
        }
        if (cmd.partnerType() != null && !Objects.equals(cmd.partnerType(), current.getPartnerType())) {
            fields.add("partnerType");
        }
        if (cmd.businessNumber() != null
                && !Objects.equals(cmd.businessNumber(), current.getBusinessNumber())) {
            fields.add("businessNumber");
        }
        if (cmd.contactName() != null
                && !Objects.equals(cmd.contactName(), current.getContactName())) {
            fields.add("contactName");
        }
        if (cmd.contactEmail() != null
                && !Objects.equals(cmd.contactEmail(), current.getContactEmail())) {
            fields.add("contactEmail");
        }
        if (cmd.contactPhone() != null
                && !Objects.equals(cmd.contactPhone(), current.getContactPhone())) {
            fields.add("contactPhone");
        }
        if (cmd.address() != null && !Objects.equals(cmd.address(), current.getAddress())) {
            fields.add("address");
        }
        return fields;
    }
}
