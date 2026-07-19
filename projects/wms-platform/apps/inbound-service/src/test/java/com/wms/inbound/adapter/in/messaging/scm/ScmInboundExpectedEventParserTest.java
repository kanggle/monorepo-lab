package com.wms.inbound.adapter.in.messaging.scm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wms.inbound.application.command.CreateScmInboundExpectationCommand;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ScmInboundExpectedEventParser#toCreateCommand} line-quantity parsing
 * (ADR-MONO-050 D9). scm emits {@code expectedQty} as a decimal <em>string</em>
 * ({@code BigDecimal.toPlainString()}); the parser must accept it, reject fractional/non-positive
 * values, and still tolerate a raw JSON number.
 */
class ScmInboundExpectedEventParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ScmInboundExpectedEventParser parser = new ScmInboundExpectedEventParser(objectMapper);

    @Test
    void expectedQtyAsDecimalStringIsAccepted() {
        CreateScmInboundExpectationCommand cmd = parser.toCreateCommand(body("\"100\""));
        assertThat(cmd.lines()).singleElement().satisfies(l -> {
            assertThat(l.skuCode()).isEqualTo("SKU-A");
            assertThat(l.expectedQty()).isEqualTo(100);
        });
    }

    @Test
    void expectedQtyAsRawNumberIsStillAccepted() {
        CreateScmInboundExpectationCommand cmd = parser.toCreateCommand(body("42"));
        assertThat(cmd.lines()).singleElement()
                .satisfies(l -> assertThat(l.expectedQty()).isEqualTo(42));
    }

    @Test
    void expectedQtyFractionalStringIsRejected() {
        assertThatThrownBy(() -> parser.toCreateCommand(body("\"1.5\"")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expectedQty");
    }

    @Test
    void expectedQtyNonPositiveStringIsRejected() {
        assertThatThrownBy(() -> parser.toCreateCommand(body("\"0\"")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expectedQty");
    }

    @Test
    void expectedQtyNonNumericStringIsRejected() {
        assertThatThrownBy(() -> parser.toCreateCommand(body("\"abc\"")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expectedQty");
    }

    private JsonNode body(String expectedQtyJson) {
        String json = """
                {
                  "poId": "%s",
                  "poNumber": "SCM-PO-2026-00187",
                  "supplierId": "SUP-0043",
                  "destinationWarehouseId": "WH-SEOUL-01",
                  "destinationNodeType": "WMS_WAREHOUSE",
                  "expectedArrivalDate": "2026-07-24",
                  "lines": [
                    { "skuCode": "SKU-A", "expectedQty": %s, "uom": "EA" }
                  ]
                }
                """.formatted(java.util.UUID.randomUUID(), expectedQtyJson);
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
