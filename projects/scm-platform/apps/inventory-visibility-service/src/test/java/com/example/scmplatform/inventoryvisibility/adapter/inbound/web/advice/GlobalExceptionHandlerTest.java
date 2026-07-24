package com.example.scmplatform.inventoryvisibility.adapter.inbound.web.advice;

import com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto.ApiErrorBody;
import com.example.scmplatform.inventoryvisibility.domain.error.NodeTypeConflictException;
import com.example.scmplatform.inventoryvisibility.domain.error.ReadModelCorruptException;
import com.example.scmplatform.inventoryvisibility.domain.node.NodeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GlobalExceptionHandler}. Invokes each handler directly
 * (no Spring MVC) — fastest feedback for a pure exception→envelope mapper.
 *
 * <p>Focus: the TASK-SCM-BE-021 / TASK-MONO-171 split — corrupt persisted
 * read-model data is a server fault (500 + logged), while client-boundary
 * validation stays a 422.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("ReadModelCorruptException → 500 INTERNAL_ERROR (server data-integrity fault, not a client 422)")
    void readModelCorrupt() {
        ResponseEntity<ApiErrorBody> r = handler.handleReadModelCorrupt(
                new ReadModelCorruptException("inventory_nodes.id", "node-001",
                        new IllegalArgumentException("Invalid UUID string: node-001")));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().code()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    @DisplayName("IllegalArgumentException → 422 VALIDATION_ERROR (genuine client-boundary validation still 422)")
    void illegalArgument() {
        ResponseEntity<ApiErrorBody> r = handler.handleIllegalArgument(
                new IllegalArgumentException("nodeId must not be blank"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("NodeTypeConflictException → 409 NODE_TYPE_CONFLICT (TASK-SCM-BE-046)")
    void nodeTypeConflict() {
        ResponseEntity<ApiErrorBody> r = handler.handleNodeTypeConflict(
                new NodeTypeConflictException("WH-EXT-1", NodeType.WMS_WAREHOUSE, NodeType.THIRD_PARTY_LOGISTICS));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().code()).isEqualTo("NODE_TYPE_CONFLICT");
    }
}
