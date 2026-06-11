package com.example.scmplatform.demandplanning.domain;

import com.example.scmplatform.demandplanning.domain.model.ReorderPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReorderPolicyTest {

    private static final ReorderPolicy POLICY = new ReorderPolicy(
            "SKU-001", 10, 5, 100, "scm", 0, Instant.now());

    @ParameterizedTest(name = "availableQty={0} -> shouldReorder={1}")
    @CsvSource({
            "0,  true",
            "5,  true",
            "10, true",    // exactly at reorderPoint → raise
            "11, false",
            "100, false"
    })
    void shouldReorder_evaluatesAgainstReorderPoint(int availableQty, boolean expected) {
        assertThat(POLICY.shouldReorder(availableQty)).isEqualTo(expected);
    }

    @Test
    void constructor_rejectsNegativeReorderPoint() {
        assertThatThrownBy(() -> new ReorderPolicy("S", -1, 0, 10, "scm", 0, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejectsZeroReorderQty() {
        assertThatThrownBy(() -> new ReorderPolicy("S", 10, 0, 0, "scm", 0, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
