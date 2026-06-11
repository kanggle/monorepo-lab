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
