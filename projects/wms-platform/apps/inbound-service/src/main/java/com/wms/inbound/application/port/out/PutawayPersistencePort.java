package com.wms.inbound.application.port.out;

import com.wms.inbound.domain.model.PutawayConfirmation;
import com.wms.inbound.domain.model.PutawayInstruction;
import java.util.Optional;
import java.util.UUID;

/**
 * Out-port for the Putaway aggregate. Backed by
 * {@code PutawayPersistenceAdapter} which uses
 * {@code @Lock(PESSIMISTIC_WRITE)} on
 * {@link #findByIdForUpdateOrThrow(UUID)} to serialise concurrent last-line
 * confirmations against the same instruction.
 */
public interface PutawayPersistencePort {

    PutawayInstruction save(PutawayInstruction instruction);

    Optional<PutawayInstruction> findById(UUID id);

    Optional<PutawayInstruction> findByAsnId(UUID asnId);

    /**
     * Pessimistic lock variant. Use only inside {@code @Transactional} confirm /
     * skip use-cases — guarantees that two concurrent last-line attempts cannot
     * both observe {@code pending == 0L} and emit duplicate completion events.
     */
    PutawayInstruction findByIdForUpdateOrThrow(UUID id);

    /**
     * Append-only persist of a {@link PutawayConfirmation}. The adapter must
     * INSERT only (no merge / no update) to honour the W2 invariant from
     * {@code domain-model.md} §4.
     */
    PutawayConfirmation saveConfirmation(PutawayConfirmation confirmation);

    Optional<PutawayConfirmation> findConfirmationByLineId(UUID lineId);
}
