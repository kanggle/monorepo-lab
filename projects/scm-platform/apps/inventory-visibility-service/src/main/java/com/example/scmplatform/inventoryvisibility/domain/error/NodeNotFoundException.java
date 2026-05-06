package com.example.scmplatform.inventoryvisibility.domain.error;

public class NodeNotFoundException extends RuntimeException {
    public NodeNotFoundException(String nodeId) {
        super("Inventory node not found: " + nodeId);
    }
}
