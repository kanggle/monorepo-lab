package com.example.fanplatform.community.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-MONO-679 — the column ↔ list conversion every read path now depends on.
 */
class PostMediaRefSerializerTest {

    private final PostMediaRefSerializer serializer = new PostMediaRefSerializer(new ObjectMapper());

    @Test
    @DisplayName("왕복: serialize 한 값을 deserialize 하면 같은 목록이 순서대로 돌아온다")
    void roundTrip_preservesOrder() {
        List<String> refs = List.of("https://images.example.com/a.jpg", "https://images.example.com/b.jpg");

        assertThat(PostMediaRefSerializer.deserialize(serializer.serialize(refs))).containsExactlyElementsOf(refs);
    }

    @Test
    @DisplayName("사진 없음은 null 이 아니라 빈 목록이다 — NULL 컬럼 · 빈 문자열 · 빈 배열 모두")
    void absent_isEmptyList() {
        assertThat(PostMediaRefSerializer.deserialize(null)).isNotNull().isEmpty();
        assertThat(PostMediaRefSerializer.deserialize("  ")).isNotNull().isEmpty();
        assertThat(PostMediaRefSerializer.deserialize("[]")).isNotNull().isEmpty();
        // 🔵 serialize 는 빈 목록을 NULL 로 저장한다 — 왕복해도 «빈 목록» 으로 돌아온다.
        assertThat(PostMediaRefSerializer.deserialize(serializer.serialize(List.of()))).isEmpty();
    }

    @Test
    @DisplayName("🔴 깨진 값은 글 읽기를 죽이지 않는다 — 사진 없는 글로 읽힌다")
    void malformed_yieldsEmptyList() {
        assertThat(PostMediaRefSerializer.deserialize("not-json")).isEmpty();
        assertThat(PostMediaRefSerializer.deserialize("{\"a\":1}")).isEmpty();
    }

    @Test
    @DisplayName("배열 안의 null 원소는 버린다 — 화면이 src=null 을 받지 않게")
    void nullElements_areDropped() {
        assertThat(PostMediaRefSerializer.deserialize("[\"https://images.example.com/a.jpg\", null]"))
                .containsExactly("https://images.example.com/a.jpg");
    }
}
