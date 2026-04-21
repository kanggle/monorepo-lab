package com.example.search.adapter.outbound.elasticsearch;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "search.index")
public record IndexProperties(String name) {
}
