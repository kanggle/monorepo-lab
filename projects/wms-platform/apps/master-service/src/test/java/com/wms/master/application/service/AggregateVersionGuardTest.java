package com.wms.master.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.wms.master.domain.exception.ConcurrencyConflictException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class AggregateVersionGuardTest {

    // -----------------------------------------------------------------------
    // requireMatch
    // -----------------------------------------------------------------------

    @Test
    void requireMatch_whenVersionsDiffer_throwsConcurrencyConflictException() {
        UUID id = UUID.randomUUID();
        long expected = 3L;
        long actual = 7L;

        assertThatThrownBy(() ->
                AggregateVersionGuard.requireMatch("Warehouse", id, expected, actual))
                .isInstanceOf(ConcurrencyConflictException.class)
                .hasMessageContaining("Warehouse")
                .hasMessageContaining(id.toString())
                .hasMessageContaining(String.valueOf(expected))
                .hasMessageContaining(String.valueOf(actual));
    }

    @Test
    void requireMatch_whenVersionsMatch_doesNotThrow() {
        UUID id = UUID.randomUUID();
        long version = 5L;

        assertThatNoException().isThrownBy(() ->
                AggregateVersionGuard.requireMatch("Zone", id, version, version));
    }

    // -----------------------------------------------------------------------
    // saveWithOptimisticLock
    // -----------------------------------------------------------------------

    @Test
    void saveWithOptimisticLock_whenSupplierThrowsOptimisticLockingFailure_throwsConcurrencyConflictException() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() ->
                AggregateVersionGuard.saveWithOptimisticLock("Sku", id, () -> {
                    throw new ObjectOptimisticLockingFailureException("Sku", id);
                }))
                .isInstanceOf(ConcurrencyConflictException.class)
                .hasMessageContaining("Sku")
                .hasMessageContaining(id.toString());
    }

    @Test
    void saveWithOptimisticLock_happyPath_returnsSupplierValue() {
        UUID id = UUID.randomUUID();
        String sentinel = "saved-entity";

        String result = AggregateVersionGuard.saveWithOptimisticLock("Lot", id, () -> sentinel);

        assertThat(result).isEqualTo(sentinel);
    }

    @Test
    void saveWithOptimisticLock_whenSupplierThrowsUnrelatedRuntimeException_propagatesUnchanged() {
        UUID id = UUID.randomUUID();
        IllegalStateException unrelated = new IllegalStateException("unrelated");

        assertThatThrownBy(() ->
                AggregateVersionGuard.saveWithOptimisticLock("Location", id, () -> {
                    throw unrelated;
                }))
                .isSameAs(unrelated);
    }
}
