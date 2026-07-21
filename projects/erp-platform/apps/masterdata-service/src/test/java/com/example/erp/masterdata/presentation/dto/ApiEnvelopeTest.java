package com.example.erp.masterdata.presentation.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link ApiEnvelope#ofList} — pins the TASK-ERP-BE-033 fix: the
 * list envelope's {@code meta.totalElements} carries the repository-supplied
 * TRUE total, NOT {@code data.size()} (masterdata-api.md § PageMeta).
 */
class ApiEnvelopeTest {

    @Test
    @DisplayName("AC-2: ofList meta.totalElements is the supplied total, not the page length")
    void totalElementsIsTrueTotalNotPageLength() {
        // A page slice of 2 rows out of a 25-row result.
        List<String> pageContent = List.of("a", "b");

        ApiEnvelope<List<String>> envelope = ApiEnvelope.ofList(pageContent, 0, 2, 25L);

        assertThat(envelope.data()).hasSize(2);
        assertThat(envelope.meta())
                .containsEntry("page", 0)
                .containsEntry("size", 2)
                // The bug being fixed: totalElements MUST be 25 (the true total),
                // NOT 2 (the page length / data.size()).
                .containsEntry("totalElements", 25L);
        assertThat(envelope.meta().get("totalElements")).isNotEqualTo((long) pageContent.size());
        assertThat(envelope.meta()).containsKey("timestamp");
    }

    @Test
    @DisplayName("ofList with an empty page still reports the true total")
    void emptyPageKeepsTrueTotal() {
        ApiEnvelope<List<String>> envelope = ApiEnvelope.ofList(List.of(), 3, 20, 45L);

        assertThat(envelope.data()).isEmpty();
        assertThat(envelope.meta()).containsEntry("totalElements", 45L);
    }
}
