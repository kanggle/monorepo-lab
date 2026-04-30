package com.example.messaging.outbox;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "outbox")
@Validated
public class OutboxProperties {

    @NotEmpty
    private Map<String, String> topicMapping = new HashMap<>();

    public Map<String, String> getTopicMapping() {
        return topicMapping;
    }

    public void setTopicMapping(Map<String, String> topicMapping) {
        this.topicMapping = topicMapping;
    }
}
