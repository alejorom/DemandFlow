package io.github.alejorom.demandflow;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static io.github.alejorom.demandflow.PostgresTestSupport.*;
import static io.github.alejorom.demandflow.CatalogSchemaIT.catalogMigrations;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MigrationEvolutionIT {
    @TempDir Path temporary;

    static Path migrations(Path directory) throws Exception {
        return technicalV1(directory);
    }

    static String location(Path directory) {
        return "--spring.flyway.locations=filesystem:" + directory.toAbsolutePath().toString().replace('\\', '/');
    }

    static String history(java.sql.Connection admin) throws Exception {
        return scalar(admin, "select string_agg(version || ':' || checksum || ':' || installed_rank || ':' "
                + "|| installed_on || ':' || success, ',' order by installed_rank) from infra.flyway_schema_history");
    }

    private static void pendingCatalog(Path directory) throws Exception {
        for (String name : new String[] {"V2__create_tariff_catalog.sql", "V3__create_catalog_audit.sql"}) {
            Files.copy(Path.of("src/main/resources/db/migration", name), directory.resolve(name));
        }
    }

    private static void productionVersions(java.sql.Connection connection) throws Exception {
        assertThat(scalar(connection, "select string_agg(version,',' order by installed_rank) "
                + "from infra.flyway_schema_history where success")).isEqualTo("1,2,3");
        assertThat(scalar(connection, "select count(*) from pg_tables where schemaname='demandflow'"))
                .isEqualTo("4");
        assertThat(scalar(connection, "select count(*) from demandflow.requirement_type")).isEqualTo("2");
    }

    @Test
    void emptyDatabaseMigratesExactlyV1V2V3AndSecondRunDoesNothing() throws Exception {
        var secrets = new Secrets(temporary);
        Path migrations = catalogMigrations(temporary.resolve("migrations"), 3);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            var flyway = technicalFlyway(port, secrets, migrations);
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(3);
            try (var admin = connect(port, "postgres", secrets.admin)) {
                productionVersions(admin);
                String original = history(admin);
                assertThat(flyway.migrate().migrationsExecuted).isZero();
                assertThat(history(admin)).isEqualTo(original);
                assertThat(scalar(admin, "select count(*) from demandflow.tariff_period")).isEqualTo("0");
                assertThat(scalar(admin, "select count(*) from demandflow.hourly_rate")).isEqualTo("0");
                assertThat(scalar(admin, "select count(*) from demandflow.catalog_audit_event")).isEqualTo("0");
            }
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }

    @Test
    void newVersionPreservesV1AndChecksumChangesAreRejectedWithoutRepair() throws Exception {
        var secrets = new Secrets(temporary);
        Path migrations = migrations(temporary.resolve("migrations"));
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            assertThat(technicalFlyway(port, secrets, migrations).migrate().migrationsExecuted).isEqualTo(1);
            try (var admin = connect(port, "postgres", secrets.admin)) {
                String original = history(admin);
                pendingCatalog(migrations);
                assertThat(technicalFlyway(port, secrets, migrations).migrate().migrationsExecuted).isEqualTo(2);
                productionVersions(admin);
                assertThat(history(admin)).startsWith(original + ",2:");
                String catalogHistory = history(admin);
                Files.copy(Path.of("src/test/resources/migrations/valid/V4__comment_namespace.sql"),
                        migrations.resolve("V4__comment_namespace.sql"));
                assertThat(technicalFlyway(port, secrets, migrations).migrate().migrationsExecuted).isEqualTo(1);
                assertThat(history(admin)).startsWith(catalogHistory + ",4:");
                assertThat(scalar(admin, "select obj_description('demandflow'::regnamespace)"))
                        .isEqualTo("DemandFlow technical migration fixture V4");
                String upgradedHistory = history(admin);
                Files.writeString(migrations.resolve("V1__initialize_application_namespace.sql"), "\n-- changed checksum\n",
                        java.nio.file.StandardOpenOption.APPEND);
                assertThrows(org.flywaydb.core.api.FlywayException.class,
                        () -> technicalFlyway(port, secrets, migrations).migrate());
                assertThat(history(admin)).isEqualTo(upgradedHistory);
            }
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }

    @Test
    void failedV4RollsBackItsCommentAndDoesNotRecordSuccess() throws Exception {
        var secrets = new Secrets(temporary);
        Path migrations = catalogMigrations(temporary.resolve("migrations"), 3);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            technicalFlyway(port, secrets, migrations).migrate();
            try (var admin = connect(port, "postgres", secrets.admin)) {
                String original = history(admin);
                Files.copy(Path.of("src/test/resources/migrations/failing/V4__fail_namespace_comment.sql"),
                        migrations.resolve("V4__fail_namespace_comment.sql"));
                assertThrows(org.flywaydb.core.api.FlywayException.class,
                        () -> technicalFlyway(port, secrets, migrations).migrate());
                assertThat(history(admin)).isEqualTo(original);
                assertThat(scalar(admin, "select coalesce(obj_description('demandflow'::regnamespace),'absent')"))
                        .isEqualTo("absent");
                assertThat(scalar(admin, "select count(*) from infra.flyway_schema_history where version='4' and success"))
                        .isEqualTo("0");
            }
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }

    @Test
    void failedV3RollsBackItsDdlAndGrantsButKeepsCommittedV2AndPreventsStartup() throws Exception {
        var secrets = new Secrets(temporary);
        Path migrations = migrations(temporary.resolve("migrations"));
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            technicalFlyway(port, secrets, migrations).migrate();
            try (var admin = connect(port, "postgres", secrets.admin)) {
                String original = history(admin);
                pendingCatalog(migrations);
                Files.writeString(migrations.resolve("V3__create_catalog_audit.sql"),
                        "\nSELECT demandflow_missing_technical_function();\n", java.nio.file.StandardOpenOption.APPEND);
                // The real persistence initializer must fail before it can return an available application.
                assertThrows(Exception.class, () -> application(port, secrets, location(migrations)));
                assertThat(history(admin)).startsWith(original + ",2:");
                assertThat(scalar(admin, "select count(*) from infra.flyway_schema_history "
                        + "where version='3' and success")).isEqualTo("0");
                assertThat(scalar(admin, "select count(*) from pg_tables where schemaname='demandflow'"))
                        .isEqualTo("3");
                assertThat(scalar(admin, "select to_regclass('demandflow.catalog_audit_event') is null"))
                        .isEqualTo("t");
                assertThat(scalar(admin, "select has_any_column_privilege('demandflow_app',"
                        + "'demandflow.tariff_period','INSERT')")).isEqualTo("f");
                assertThat(scalar(admin, "select count(*) from demandflow.requirement_type")).isEqualTo("2");
            }
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }
}
