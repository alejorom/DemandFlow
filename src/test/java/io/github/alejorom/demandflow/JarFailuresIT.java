package io.github.alejorom.demandflow;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static io.github.alejorom.demandflow.PostgresTestSupport.*;
import static io.github.alejorom.demandflow.JarTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JarFailuresIT {
    @TempDir Path temporary;

    @Test
    void independentProcessesFailSpontaneouslyWithoutAvailabilityOrCredentialLeaks() throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            failure("unreachable", freePort(), secrets, "database connection unavailable or rejected");
            Files.writeString(temporary.resolve("spring/spring.datasource.password"), "rejected-" + java.util.UUID.randomUUID());
            failure("runtime-rejected", port, secrets, "database connection unavailable or rejected");
            Files.writeString(temporary.resolve("spring/spring.datasource.password"), secrets.runtime);
            Files.writeString(temporary.resolve("spring/spring.flyway.password"), "rejected-" + java.util.UUID.randomUUID());
            failure("migrator-rejected", port, secrets, "database connection unavailable or rejected");
            Files.writeString(temporary.resolve("spring/spring.flyway.password"), secrets.migrator);
            for (String property : new String[] {"spring.datasource.url", "spring.datasource.username",
                    "spring.datasource.password", "spring.flyway.url", "spring.flyway.user", "spring.flyway.password"}) {
                failure("empty-" + property, port, secrets, "configuration incomplete", "--" + property + "=");
            }
            failure("missing-directory", port, secrets, "configuration incomplete",
                    "--DEMANDFLOW_SECRETS_DIR=" + temporary.resolve("absent").toString().replace('\\', '/'));
            Files.writeString(temporary.resolve("spring/spring.datasource.password"), "");
            failure("empty-file", port, secrets, "configuration incomplete");
            Files.writeString(temporary.resolve("spring/spring.datasource.password"), secrets.runtime);

            Path migrations = MigrationEvolutionIT.migrations(temporary.resolve("migrations"));
            String location = MigrationEvolutionIT.location(migrations);
            try (var initialized = application(port, secrets)) {
                assertThat(initialized.containsBean("entityManagerFactory")).isTrue();
            }
            try (var admin = connect(port, "postgres", secrets.admin)) {
                String original = MigrationEvolutionIT.history(admin);
                Files.writeString(migrations.resolve("V1__initialize_application_namespace.sql"), "\n-- changed checksum\n",
                        java.nio.file.StandardOpenOption.APPEND);
                failure("checksum", port, secrets, "migration validation failed", location);
                assertThat(MigrationEvolutionIT.history(admin)).isEqualTo(original);
                Files.copy(Path.of("src/main/resources/db/migration/V1__initialize_application_namespace.sql"),
                        migrations.resolve("V1__initialize_application_namespace.sql"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Files.copy(Path.of("src/test/resources/migrations/failing/V2__fail_namespace_comment.sql"),
                        migrations.resolve("V2__fail_namespace_comment.sql"));
                failure("sql-execution", port, secrets, "migration execution failed", location);
                assertThat(MigrationEvolutionIT.history(admin)).isEqualTo(original);
                assertThat(scalar(admin, "select coalesce(obj_description('demandflow'::regnamespace),'absent')"))
                        .isEqualTo("absent");
            }
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }

    private void failure(String scenario, int port, Secrets secrets, String category, String... extra) throws Exception {
        Path output = temporary.resolve(scenario + ".log");
        int httpPort = freePort();
        long started = System.nanoTime();
        Process process = start(output, httpPort, port, secrets, extra);
        try {
            boolean spontaneous = process.waitFor(75, TimeUnit.SECONDS);
            assertTrue(spontaneous, "JAR exceeded 75 seconds in " + scenario + "; cleanup does not count as success");
            assertThat(process.exitValue()).as(scenario).isNotZero();
            String log = Files.readString(output);
            secrets.assertNotLeaked(log);
            assertFalse(log.contains("rejected-"), "Rejected synthetic credential leaked");
            assertFalse(log.contains("jdbc:postgresql:"), "Complete JDBC URL leaked");
            assertFalse(log.contains("PASSWORD '"), "Password assignment SQL leaked");
            assertThat(log).contains(category).doesNotContain("Started DemandFlowApplication");
            System.out.printf("JAR failure %s: spontaneous exit=%d elapsedMs=%d%n", scenario, process.exitValue(),
                    Duration.ofNanos(System.nanoTime() - started).toMillis());
        } finally {
            stopped(process);
        }
    }
}
