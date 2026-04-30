package com.example.community.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PostMediaUrlsSerializerTest {

    private PostMediaUrlsSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new PostMediaUrlsSerializer(new ObjectMapper());
    }

    @Test
    @DisplayName("mediaUrls가 null이면 null을 반환한다")
    void serialize_nullInput_returnsNull() {
        String result = serializer.serialize(null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("mediaUrls가 빈 리스트이면 null을 반환한다")
    void serialize_emptyList_returnsNull() {
        String result = serializer.serialize(List.of());

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("mediaUrls가 정상이면 JSON 배열 문자열을 반환한다")
    void serialize_validList_returnsJsonArray() {
        String result = serializer.serialize(List.of(
                "https://cdn.example.com/a.png",
                "https://cdn.example.com/b.jpg"
        ));

        assertThat(result).isEqualTo(
                "[\"https://cdn.example.com/a.png\",\"https://cdn.example.com/b.jpg\"]"
        );
    }
}
