package com.se_lab.project.global;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseHealthIndicatorTest {

    @Test
    void reportsUpWhenDatabaseCheckSucceeds() {
        try (DatabaseHealthIndicator indicator =
                     new DatabaseHealthIndicator(Duration.ofSeconds(1), () -> true)) {
            assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        }
    }

    @Test
    void reportsDownWhenDatabaseCheckFails() {
        try (DatabaseHealthIndicator indicator =
                     new DatabaseHealthIndicator(Duration.ofSeconds(1), () -> false)) {
            assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
        }
    }

    @Test
    void boundsBlockedChecksAndRejectsConcurrentProbeWithoutBlocking() {
        CountDownLatch blocked = new CountDownLatch(1);
        try (DatabaseHealthIndicator indicator =
                     new DatabaseHealthIndicator(Duration.ofMillis(100), () -> {
                         blocked.await();
                         return true;
                     })) {
            long firstStarted = System.nanoTime();
            assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
            assertThat(Duration.ofNanos(System.nanoTime() - firstStarted)).isLessThan(Duration.ofSeconds(1));

            long secondStarted = System.nanoTime();
            assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
            assertThat(Duration.ofNanos(System.nanoTime() - secondStarted)).isLessThan(Duration.ofSeconds(1));
        } finally {
            blocked.countDown();
        }
    }

    @Test
    void rejectsNonPositiveTimeout() {
        assertThatThrownBy(() -> new DatabaseHealthIndicator(Duration.ZERO, () -> true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
