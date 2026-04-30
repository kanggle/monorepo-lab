package com.example.security.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.nio.charset.StandardCharsets;

/**
 * String deserializer that additionally validates the payload is parseable JSON.
 *
 * <p>Wired behind {@link org.springframework.kafka.support.serializer.ErrorHandlingDeserializer}
 * so that malformed payloads surface as a genuine {@code DeserializationException},
 * which the {@code DefaultErrorHandler} routes to {@code <topic>.dlq} after retries.
 * This keeps downstream consumer methods simple (they still receive {@code String})
 * while moving poison-pill detection to the deserialization boundary.</p>
 *
 * <p>Empty/null payloads pass through unchanged — those are handled by consumer
 * logic, not treated as poison pills.</p>
 */
public class StrictJsonStringDeserializer extends StringDeserializer {

    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    @Override
    public String deserialize(String topic, byte[] data) {
        if (data == null || data.length == 0) {
            return super.deserialize(topic, data);
        }
        try (JsonParser parser = JSON_FACTORY.createParser(data)) {
            // Walk the token stream to ensure the bytes parse as well-formed JSON.
            while (parser.nextToken() != null) {
                // no-op — presence of an exception is what we care about
            }
        } catch (Exception e) {
            throw new SerializationException(
                    "Invalid JSON payload on topic=" + topic + " (" + data.length + " bytes): " + e.getMessage(), e);
        }
        return new String(data, StandardCharsets.UTF_8);
    }
}
