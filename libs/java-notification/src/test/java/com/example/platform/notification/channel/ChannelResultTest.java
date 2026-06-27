package com.example.platform.notification.channel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelResultTest {

    @Test
    void delivered_setsRefAndClearsError() {
        ChannelResult r = ChannelResult.delivered("msg-1");

        assertThat(r.delivered()).isTrue();
        assertThat(r.permanent()).isFalse();
        assertThat(r.ref()).isEqualTo("msg-1");
        assertThat(r.error()).isNull();
    }

    @Test
    void transientFailure_isNonPermanentWithError() {
        ChannelResult r = ChannelResult.transientFailure("503");

        assertThat(r.delivered()).isFalse();
        assertThat(r.permanent()).isFalse();
        assertThat(r.ref()).isNull();
        assertThat(r.error()).isEqualTo("503");
    }

    @Test
    void permanentFailure_isPermanentWithError() {
        ChannelResult r = ChannelResult.permanentFailure("404");

        assertThat(r.delivered()).isFalse();
        assertThat(r.permanent()).isTrue();
        assertThat(r.ref()).isNull();
        assertThat(r.error()).isEqualTo("404");
    }
}
