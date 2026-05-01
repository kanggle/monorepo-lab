package com.example.auth.infrastructure.oauth2.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

/**
 * JPA AttributeConverter that maps {@code List<String>} ↔ a JSON array string.
 *
 * <p>Used instead of {@code @JdbcTypeCode(SqlTypes.JSON)} for the JSON columns on
 * {@link OAuthClientEntity} and {@link OAuthConsentEntity} so that the mapping works
 * identically on both MySQL 8 (native JSON column) and H2 (VARCHAR in test mode).
 *
 * <p>TASK-BE-252.
 */
@Converter
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot serialize List<String> to JSON", e);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(dbData, LIST_TYPE);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Cannot deserialize string to java type: java.util.List<java.lang.String>", e);
        }
    }
}
