package com.wms.inbound.adapter.in.rest.dto;

public record CancelAsnRequest(
        String reason,
        long version
) {}
