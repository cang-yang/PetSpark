package com.petspark.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordResetNotifierTest {

    @Test
    void fallbackNotifierReportsThatDeliveryIsUnavailable() {
        PasswordResetNotifier notifier = new NoopPasswordResetNotifier();

        assertThat(notifier.isAvailable()).isFalse();
    }
}
