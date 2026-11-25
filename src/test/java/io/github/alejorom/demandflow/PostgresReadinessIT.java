package io.github.alejorom.demandflow;

import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.ContainerLaunchException;

import static io.github.alejorom.demandflow.PostgresTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class PostgresReadinessIT {
    @TempDir Path temporary;

    @Test
    void startRequiresBothJdbcRolesAndRejectedCredentialsFailImmediately() throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            assertThat(jdbcReady(port, "demandflow_migrator", secrets.migrator)).isTrue();
            assertThat(jdbcReady(port, "demandflow_app", secrets.runtime)).isTrue();
            var failure = assertTimeoutPreemptively(Duration.ofSeconds(5),
                    () -> assertThrows(ContainerLaunchException.class,
                            () -> jdbcReady(port, "demandflow_app", "rejected-synthetic-value")));
            assertThat(failure.getMessage()).contains("SQLSTATE=28P01").doesNotContain("jdbc:", "rejected-synthetic-value");
            secrets.assertNotLeaked(failure.toString());
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }
}
