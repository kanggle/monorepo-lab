package com.example.scmplatform.demandplanning.adapter.inbound.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Optional body for POST /suggestions/{id}/dismiss.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DismissRequest(String reason) {
}
