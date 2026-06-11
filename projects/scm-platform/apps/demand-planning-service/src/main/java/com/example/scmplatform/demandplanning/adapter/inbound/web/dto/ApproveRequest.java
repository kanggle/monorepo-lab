package com.example.scmplatform.demandplanning.adapter.inbound.web.dto;

/**
 * Optional request body for {@code POST /suggestions/{id}/approve}.
 * The {@code note} is advisory operator context; the approve itself takes no
 * required parameters.
 */
public record ApproveRequest(String note) {
}
