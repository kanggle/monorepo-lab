package com.wms.master.adapter.in.web.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RequestBodyCanonicalizerTest {

    private final RequestBodyCanonicalizer canonicalizer = new RequestBodyCanonicalizer(new ObjectMapper());

    @Test
    void emptyBodyCanonicalizesToEmptyString() {
        assertThat(canonicalizer.canonicalize(null)).isEmpty();
        assertThat(canonicalizer.canonicalize(new byte[0])).isEmpty();
    }

    @Test
    void objectFieldsAreSortedAlphabetically() {
        byte[] a = "{\"b\":1,\"a\":2}".getBytes(StandardCharsets.UTF_8);
        byte[] b = "{\"a\":2,\"b\":1}".getBytes(StandardCharsets.UTF_8);

        assertThat(canonicalizer.canonicalize(a)).isEqualTo(canonicalizer.canonicalize(b));
        assertThat(canonicalizer.canonicalize(a)).isEqualTo("{\"a\":2,\"b\":1}");
    }

    @Test
    void whitespaceIsOmitted() {
        byte[] spaced = "{  \"a\" :  1  }".getBytes(StandardCharsets.UTF_8);
        byte[] tight = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        assertThat(canonicalizer.canonicalize(spaced)).isEqualTo(canonicalizer.canonicalize(tight));
    }

    @Test
    void nestedObjectsAreSortedRecursively() {
        byte[] a = "{\"x\":{\"b\":1,\"a\":2},\"y\":3}".getBytes(StandardCharsets.UTF_8);
        assertThat(canonicalizer.canonicalize(a)).isEqualTo("{\"x\":{\"a\":2,\"b\":1},\"y\":3}");
    }

    @Test
    void arrayOrderIsPreserved() {
        byte[] a = "[3,1,2]".getBytes(StandardCharsets.UTF_8);
        assertThat(canonicalizer.canonicalize(a)).isEqualTo("[3,1,2]");
    }

    @Test
    void nonJsonBodyFallsBackToUtf8String() {
        byte[] garbage = "not-json".getBytes(StandardCharsets.UTF_8);
        assertThat(canonicalizer.canonicalize(garbage)).isEqualTo("not-json");
    }
}
