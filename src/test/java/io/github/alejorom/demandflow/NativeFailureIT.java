package io.github.alejorom.demandflow;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static io.github.alejorom.demandflow.PostgresTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(OutputCaptureExtension.class)
class NativeFailureIT {
    @TempDir Path temporary;

    @Test
    @Timeout(240)
    void nativeLimitsCoverBothConnectionsAndFailuresPropagate(CapturedOutput output) throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            try (var context = application(port, secrets)) {
                var pool = context.getBean(HikariDataSource.class);
                assertThat(pool.getConnectionTimeout()).isEqualTo(5000);
                assertThat(pool.getValidationTimeout()).isEqualTo(2000);
                assertThat(pool.getInitializationFailTimeout()).isEqualTo(1);
                var configuration = context.getBean(Flyway.class).getConfiguration();
                assertThat(configuration.getConnectRetries()).isZero();
                assertThat(configuration.getLockRetryCount()).isEqualTo(3);
                assertThat(configuration.isCleanDisabled()).isTrue();
                try (var migration = configuration.getDataSource().getConnection();
                     var runtime = pool.getConnection()) {
                    assertThat(migration.getMetaData().getURL()).isEqualTo(url(port));
                    for (var connection : new java.sql.Connection[] {migration, runtime}) {
                        assertThat(scalar(connection, "show statement_timeout")).isEqualTo("15s");
                        assertThat(scalar(connection, "show lock_timeout")).isEqualTo("5s");
                    }
                }
                assertThat(pool.getJdbcUrl()).isEqualTo(url(port));
            }
            assertBoundedFailure(1, secrets);
            Files.writeString(temporary.resolve("spring/spring.datasource.password"), "rejected-" + UUID.randomUUID());
            assertBoundedFailure(port, secrets);
            Files.writeString(temporary.resolve("spring/spring.datasource.password"), secrets.runtime);
            Files.writeString(temporary.resolve("spring/spring.flyway.password"), "rejected-" + UUID.randomUUID());
            assertBoundedFailure(port, secrets);
            secrets.assertNotLeaked(postgres.getLogs());
            secrets.assertNotLeaked(output.getAll());
            assertThat(output.getAll()).contains("database connection unavailable or rejected")
                    .doesNotContain("jdbc:postgresql:");
        }
    }

    private void assertBoundedFailure(int port, Secrets secrets) {
        long start = System.nanoTime();
        assertThrows(Exception.class, () -> application(port, secrets));
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(75));
    }
}
