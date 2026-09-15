package com.example.fanplatform.community.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Converts the {@code mediaRefs} list to and from its JSON-array column value. Centralized so
 * PublishPost, UpdatePost and every read path share the same null/empty handling.
 *
 * <p><strong>What a media ref is (TASK-MONO-679 ⓐ, owner decision 2026-09-15).</strong> An
 * absolute {@code https://} URL a browser can render as-is — the convention artist-service
 * already follows for {@code profileImageRef}. It used to be documented as an "S3 / MinIO key",
 * but nothing in fan-platform ever resolved a key (there is no upload path and no storage
 * client), so that meaning had no consumer. The request DTOs enforce the https shape, which is
 * also what stops a key from being stored here by mistake.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostMediaRefSerializer {

    private static final TypeReference<List<String>> LIST_OF_STRING = new TypeReference<>() {
    };

    /** Parsing a list of strings needs no application modules — a plain mapper keeps it static. */
    private static final ObjectMapper PARSER = JsonMapper.builder().build();

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

    /**
     * Column value → list. {@code null} or blank → an empty list, never {@code null}: the response
     * contract carries {@code mediaRefs: []} for a post without media, so a client never has to
     * tell "no photos" apart from "field missing".
     *
     * <p>A malformed value also yields an empty list rather than failing the read. Only
     * {@link #serialize} writes this column, so a malformed value means corruption — and a post
     * whose photos cannot be parsed is still a readable post. It is logged, not swallowed silently.
     */
    public static List<String> deserialize(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> refs = PARSER.readValue(json, LIST_OF_STRING);
            return refs == null ? List.of() : refs.stream().filter(Objects::nonNull).toList();
        } catch (JsonProcessingException e) {
            log.warn("posts.media_refs is not a JSON string array; serving the post without media: {}",
                    e.getOriginalMessage());
            return List.of();
        }
    }
}
