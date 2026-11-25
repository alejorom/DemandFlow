package io.github.alejorom.demandflow;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static io.github.alejorom.demandflow.PostgresTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Technical smoke check only: no business schema, application startup or JPA. */
class PersistenceSupportIT {
    @TempDir Path temporary;

    @Test
    void isolatedTechnicalBaselineMigratesWithoutStartingTheApplication() throws Exception {
        Path migrations = technicalV1(temporary.resolve("migrations"));
        assertThat(Files.readAllBytes(migrations.resolve(V1)))
                .isEqualTo(Files.readAllBytes(Path.of(System.getProperty("demandflow.test.v1-source",
                        "src/main/resources/db/migration/" + V1))));
        try (var entries = Files.list(migrations)) {
            assertThat(entries.map(path -> path.getFileName().toString()).toList()).containsExactly(V1);
        }
        var secrets = new Secrets(temporary.resolve("secrets"));
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            var flyway = technicalFlyway(port, secrets, migrations);
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            try (var admin = connect(port, "postgres", secrets.admin);
                 var runtime = connect(port, "demandflow_app", secrets.runtime)) {
                String history = MigrationEvolutionIT.history(admin);
                assertThat(scalar(admin, "show server_version")).startsWith("17.12");
                assertThat(scalar(admin, "select pg_get_userbyid(nspowner) from pg_namespace "
                        + "where nspname='demandflow'")).isEqualTo("demandflow_migrator");
                assertThat(scalar(admin, "select count(*) from pg_tables where schemaname='demandflow'"))
                        .isEqualTo("0");
                assertThat(scalar(runtime, "select current_user")).isEqualTo("demandflow_app");
                assertThat(scalar(runtime, "select has_schema_privilege(current_user,'demandflow','USAGE')"))
                        .isEqualTo("t");
                assertThat(scalar(runtime, "select has_schema_privilege(current_user,'demandflow','CREATE')"))
                        .isEqualTo("f");
                assertThat(flyway.migrate().migrationsExecuted).isZero();
                assertThat(MigrationEvolutionIT.history(admin)).isEqualTo(history);
                assertThat(flyway.info().applied()).hasSize(1);
                assertThat(flyway.info().applied()[0].getVersion().toString()).isEqualTo("1");
            }
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }
}
