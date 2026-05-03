package com.example.fanplatform.community.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Serializes the {@code mediaRefs} list (S3 / MinIO keys, raw uploads are v2)
 * to its JSON-array string representation. Centralized so PublishPost and
 * UpdatePost share the same null/empty handling and error mapping.
 */
@Component
@RequiredArgsConstructor
public class PostMediaRefSerializer {

    private final ObjectMapper objectMapper;

    public String serialize(List<String> mediaRefs) {
        if (mediaRefs == null || mediaRefs.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(mediaRefs);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid mediaRefs");
        }
    }
}
