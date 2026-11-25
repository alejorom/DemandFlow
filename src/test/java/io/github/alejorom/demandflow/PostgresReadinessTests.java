package io.github.alejorom.demandflow;

import java.time.Duration;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.wait.strategy.WaitStrategyTarget;

import static io.github.alejorom.demandflow.PostgresTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PostgresReadinessTests {
    @TempDir Path temporary;

    @Test
    void unavailableIsolatedPortFailsAtTheReadinessDeadlineWithoutDockerOrFallback() throws Exception {
        var target = mock(WaitStrategyTarget.class);
        // Reserve/release a new loopback port, never the configured development port.
        when(target.getMappedPort(5432)).thenReturn(JarTestSupport.freePort());
        var secrets = new Secrets(temporary);
        var wait = new JdbcReadiness(secrets).withStartupTimeout(Duration.ofMillis(400));
        var failure = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> assertThrows(ContainerLaunchException.class, () -> wait.waitUntilReady(target)));
        assertThat(failure.getMessage()).contains("readiness deadline exceeded").doesNotContain("jdbc:");
        secrets.assertNotLeaked(failure.toString());
    }
}
