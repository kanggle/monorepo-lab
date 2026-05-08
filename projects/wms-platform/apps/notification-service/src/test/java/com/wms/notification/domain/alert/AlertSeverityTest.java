package com.wms.notification.domain.alert;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AlertSeverityTest {

    @Test
    void infoIsAtLeastInfoOnly() {
        assertThat(AlertSeverity.INFO.isAtLeast(AlertSeverity.INFO)).isTrue();
        assertThat(AlertSeverity.INFO.isAtLeast(AlertSeverity.WARNING)).isFalse();
        assertThat(AlertSeverity.INFO.isAtLeast(AlertSeverity.CRITICAL)).isFalse();
    }

    @Test
    void warningIsAtLeastInfoAndWarning() {
        assertThat(AlertSeverity.WARNING.isAtLeast(AlertSeverity.INFO)).isTrue();
        assertThat(AlertSeverity.WARNING.isAtLeast(AlertSeverity.WARNING)).isTrue();
        assertThat(AlertSeverity.WARNING.isAtLeast(AlertSeverity.CRITICAL)).isFalse();
    }

    @Test
    void criticalIsAtLeastEverything() {
        assertThat(AlertSeverity.CRITICAL.isAtLeast(AlertSeverity.INFO)).isTrue();
        assertThat(AlertSeverity.CRITICAL.isAtLeast(AlertSeverity.WARNING)).isTrue();
        assertThat(AlertSeverity.CRITICAL.isAtLeast(AlertSeverity.CRITICAL)).isTrue();
    }
}
