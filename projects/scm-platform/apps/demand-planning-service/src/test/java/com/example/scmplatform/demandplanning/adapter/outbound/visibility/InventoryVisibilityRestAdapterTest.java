package com.example.scmplatform.demandplanning.adapter.outbound.visibility;

import com.example.scmplatform.demandplanning.application.port.outbound.InventoryVisibilityPort.SkuWarehouseQty;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test for {@link InventoryVisibilityRestAdapter} (TASK-SCM-BE-026): it maps
 * the IVS internal-snapshot envelope to {@code SkuWarehouseQty}, skips malformed
 * rows, tolerates an empty data set, and surfaces transport/5xx failures (so the
 * sweep can skip the run).
 */
class InventoryVisibilityRestAdapterTest {

    private MockWebServer ivs;
    private InventoryVisibilityRestAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        ivs = new MockWebServer();
        ivs.start();
        String baseUrl = "http://" + ivs.getHostName() + ":" + ivs.getPort();
        adapter = new InventoryVisibilityRestAdapter(baseUrl, 2000, 5000);
    }

    @AfterEach
    void tearDown() throws IOException {
        ivs.shutdown();
    }

    private void enqueueJson(String body) {
        ivs.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body));
    }

    @Test
    void mapsRows_andSkipsMalformed() {
        UUID node = UUID.randomUUID();
        enqueueJson("{\"data\":["
                + "{\"sku\":\"SKU-1\",\"nodeId\":\"" + node + "\",\"availableQty\":4},"
                + "{\"sku\":\"SKU-NO-NODE\",\"availableQty\":1},"          // missing nodeId → skip
                + "{\"sku\":\"SKU-BAD-NODE\",\"nodeId\":\"not-a-uuid\",\"availableQty\":1}" // bad UUID → skip
                + "],\"meta\":{\"count\":3}}");

        List<SkuWarehouseQty> result = adapter.findAllBelowReorderPoint("scm");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).skuCode()).isEqualTo("SKU-1");
        assertThat(result.get(0).warehouseId()).isEqualTo(node);
        assertThat(result.get(0).availableQty()).isEqualTo(4);
    }

    /**
     * ADR-MONO-050 D9 / TASK-SCM-BE-037: the additive {@code warehouseCode} is parsed
     * when present. It is what lets a BATCH-origin PO address its wms inbound-expected
     * by code rather than uuid.
     */
    @Test
    void parsesWarehouseCode_whenPresent() {
        UUID node = UUID.randomUUID();
        enqueueJson("{\"data\":["
                + "{\"sku\":\"SKU-1\",\"nodeId\":\"" + node + "\",\"availableQty\":4,"
                + "\"warehouseCode\":\"WH01\"}"
                + "],\"meta\":{\"count\":1}}");

        List<SkuWarehouseQty> result = adapter.findAllBelowReorderPoint("scm");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).warehouseCode()).isEqualTo("WH01");
    }

    /**
     * ADR-MONO-050 D9: the field is ADDITIVE — an older IVS build omits it entirely and
     * a node that has not yet learned a code serialises it as JSON null. Neither may fail
     * the row: the candidate is still returned, only with a null code.
     */
    @Test
    void warehouseCodeAbsentOrNull_yieldsNullCode_rowStillReturned() {
        UUID absentNode = UUID.randomUUID();
        UUID nullNode = UUID.randomUUID();
        enqueueJson("{\"data\":["
                + "{\"sku\":\"SKU-ABSENT\",\"nodeId\":\"" + absentNode + "\",\"availableQty\":4},"
                + "{\"sku\":\"SKU-NULL\",\"nodeId\":\"" + nullNode + "\",\"availableQty\":7,"
                + "\"warehouseCode\":null}"
                + "],\"meta\":{\"count\":2}}");

        List<SkuWarehouseQty> result = adapter.findAllBelowReorderPoint("scm");

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(r -> assertThat(r.warehouseCode()).isNull());
        assertThat(result).extracting(SkuWarehouseQty::skuCode)
                .containsExactly("SKU-ABSENT", "SKU-NULL");
    }

    /**
     * ADR-MONO-055 §D2/§D3 (TASK-SCM-BE-048): the additive {@code nodeType} is parsed when
     * present. It is what lets a below-reorder {@code THIRD_PARTY_LOGISTICS} node drive a
     * replenishment suggestion addressed to that 3PL node.
     */
    @Test
    void parsesNodeType_whenPresent() {
        UUID wmsNode = UUID.randomUUID();
        UUID tplNode = UUID.randomUUID();
        enqueueJson("{\"data\":["
                + "{\"sku\":\"SKU-WMS\",\"nodeId\":\"" + wmsNode + "\",\"availableQty\":4,"
                + "\"warehouseCode\":\"WH01\",\"nodeType\":\"WMS_WAREHOUSE\"},"
                + "{\"sku\":\"SKU-3PL\",\"nodeId\":\"" + tplNode + "\",\"availableQty\":2,"
                + "\"nodeType\":\"THIRD_PARTY_LOGISTICS\"}"
                + "],\"meta\":{\"count\":2}}");

        List<SkuWarehouseQty> result = adapter.findAllBelowReorderPoint("scm");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).nodeType()).isEqualTo("WMS_WAREHOUSE");
        assertThat(result.get(1).nodeType()).isEqualTo("THIRD_PARTY_LOGISTICS");
        assertThat(result.get(1).warehouseCode()).isNull();
    }

    /**
     * ADR-MONO-055: the field is ADDITIVE — an older IVS build omits it and a node absent
     * from the registry serialises it as JSON null. Neither may fail the row: the candidate
     * is still returned with a null type (the suggestion normalises it to WMS_WAREHOUSE).
     */
    @Test
    void nodeTypeAbsentOrNull_yieldsNullType_rowStillReturned() {
        UUID absentNode = UUID.randomUUID();
        UUID nullNode = UUID.randomUUID();
        enqueueJson("{\"data\":["
                + "{\"sku\":\"SKU-ABSENT\",\"nodeId\":\"" + absentNode + "\",\"availableQty\":4},"
                + "{\"sku\":\"SKU-NULL\",\"nodeId\":\"" + nullNode + "\",\"availableQty\":7,"
                + "\"nodeType\":null}"
                + "],\"meta\":{\"count\":2}}");

        List<SkuWarehouseQty> result = adapter.findAllBelowReorderPoint("scm");

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(r -> assertThat(r.nodeType()).isNull());
    }

    @Test
    void emptyData_returnsEmptyList() {
        enqueueJson("{\"data\":[],\"meta\":{\"count\":0}}");
        assertThat(adapter.findAllBelowReorderPoint("scm")).isEmpty();
    }

    @Test
    void serverError_throws_soSweepCanSkip() {
        ivs.enqueue(new MockResponse().setResponseCode(503));
        assertThatThrownBy(() -> adapter.findAllBelowReorderPoint("scm"))
                .isInstanceOf(RuntimeException.class);
    }
}
