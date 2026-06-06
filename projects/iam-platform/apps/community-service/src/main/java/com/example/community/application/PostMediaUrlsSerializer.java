package com.example.community.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Shared JSON serializer for post {@code mediaUrls} payloads.
 *
 * <p>Centralizes the duplicated seven-line block previously inlined in
 * {@code PublishPostUseCase} and {@code UpdatePostUseCase}: skip {@code null}/empty inputs,
 * delegate serialization to {@link ObjectMapper}, and translate any
 * {@link JsonProcessingException} into the existing
 * {@code IllegalArgumentException("Invalid mediaUrls")} surface.
 *
 * <p>Package-private by design — only application-layer use-cases in this package consume it.
 */
@Component
@RequiredArgsConstructor
class PostMediaUrlsSerializer {

    private final ObjectMapper objectMapper;

    /**
     * Serializes a {@code mediaUrls} list to its JSON array string representation.
     *
     * @param mediaUrls list of media URLs (may be {@code null} or empty)
     * @return JSON array string when {@code mediaUrls} is non-empty, otherwise {@code null}
     * @throws IllegalArgumentException if Jackson fails to serialize the list
     */
    String serialize(List<String> mediaUrls) {
        if (mediaUrls == null || mediaUrls.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(mediaUrls);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid mediaUrls");
        }
    }
}
