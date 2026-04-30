package com.example.admin.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperatorContextTest {

    @Test
    void holds_operator_id_and_jti() {
        OperatorContext ctx = new OperatorContext("op-1", "jti-abc");
        assertThat(ctx.operatorId()).isEqualTo("op-1");
        assertThat(ctx.jti()).isEqualTo("jti-abc");
    }

    @Test
    void jti_may_be_null_for_legacy_tokens() {
        OperatorContext ctx = new OperatorContext("op-1", null);
        assertThat(ctx.jti()).isNull();
    }
}
