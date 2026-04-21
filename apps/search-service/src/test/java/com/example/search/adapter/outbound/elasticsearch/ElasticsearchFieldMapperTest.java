package com.example.search.adapter.outbound.elasticsearch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ElasticsearchFieldMapper 단위 테스트")
class ElasticsearchFieldMapperTest {

    @Nested
    @DisplayName("getString")
    class GetStringTest {

        @Test
        @DisplayName("키가 존재하면 문자열로 변환한다")
        void getString_existingKey_returnsString() {
            Map<String, Object> source = Map.of("name", "노트북");

            String result = ElasticsearchFieldMapper.getString(source, "name");

            assertThat(result).isEqualTo("노트북");
        }

        @Test
        @DisplayName("키가 없으면 null을 반환한다")
        void getString_missingKey_returnsNull() {
            Map<String, Object> source = Map.of();

            String result = ElasticsearchFieldMapper.getString(source, "name");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("값이 null이면 null을 반환한다")
        void getString_nullValue_returnsNull() {
            Map<String, Object> source = new HashMap<>();
            source.put("name", null);

            String result = ElasticsearchFieldMapper.getString(source, "name");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("숫자 값을 문자열로 변환한다")
        void getString_numericValue_returnsStringRepresentation() {
            Map<String, Object> source = Map.of("id", 12345);

            String result = ElasticsearchFieldMapper.getString(source, "id");

            assertThat(result).isEqualTo("12345");
        }
    }

    @Nested
    @DisplayName("toLong")
    class ToLongTest {

        @Test
        @DisplayName("null이면 0을 반환한다")
        void toLong_null_returnsZero() {
            assertThat(ElasticsearchFieldMapper.toLong(null)).isEqualTo(0L);
        }

        @Test
        @DisplayName("Number 타입이면 long으로 변환한다")
        void toLong_number_returnsLong() {
            assertThat(ElasticsearchFieldMapper.toLong(1000000)).isEqualTo(1000000L);
            assertThat(ElasticsearchFieldMapper.toLong(1000000.5)).isEqualTo(1000000L);
        }

        @Test
        @DisplayName("파싱 가능한 문자열이면 long으로 변환한다")
        void toLong_parsableString_returnsLong() {
            assertThat(ElasticsearchFieldMapper.toLong("999")).isEqualTo(999L);
        }

        @Test
        @DisplayName("파싱 불가능한 문자열이면 0을 반환한다")
        void toLong_unparsableString_returnsZero() {
            assertThat(ElasticsearchFieldMapper.toLong("abc")).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("toInt")
    class ToIntTest {

        @Test
        @DisplayName("null이면 0을 반환한다")
        void toInt_null_returnsZero() {
            assertThat(ElasticsearchFieldMapper.toInt(null)).isEqualTo(0);
        }

        @Test
        @DisplayName("Number 타입이면 int로 변환한다")
        void toInt_number_returnsInt() {
            assertThat(ElasticsearchFieldMapper.toInt(50)).isEqualTo(50);
            assertThat(ElasticsearchFieldMapper.toInt(50.7)).isEqualTo(50);
        }

        @Test
        @DisplayName("파싱 가능한 문자열이면 int로 변환한다")
        void toInt_parsableString_returnsInt() {
            assertThat(ElasticsearchFieldMapper.toInt("42")).isEqualTo(42);
        }

        @Test
        @DisplayName("파싱 불가능한 문자열이면 0을 반환한다")
        void toInt_unparsableString_returnsZero() {
            assertThat(ElasticsearchFieldMapper.toInt("xyz")).isEqualTo(0);
        }
    }
}
