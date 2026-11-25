package io.github.alejorom.demandflow;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static io.github.alejorom.demandflow.PostgresTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MigrationEvolutionIT {
    @TempDir Path temporary;

    static Path migrations(Path directory) throws Exception {
        Files.createDirectories(directory);
        Files.copy(Path.of("src/main/resources/db/migration/V1__initialize_application_namespace.sql"),
                directory.resolve("V1__initialize_application_namespace.sql"));
        return directory;
    }

    static String location(Path directory) {
        return "--spring.flyway.locations=filesystem:" + directory.toAbsolutePath().toString().replace('\\', '/');
    }

    static String history(java.sql.Connection admin) throws Exception {
        return scalar(admin, "select string_agg(version || ':' || checksum || ':' || installed_rank || ':' "
                + "|| installed_on || ':' || success, ',' order by installed_rank) from infra.flyway_schema_history");
    }

    @Test
    void newVersionPreservesV1AndChecksumChangesAreRejectedWithoutRepair() throws Exception {
        var secrets = new Secrets(temporary);
        Path migrations = migrations(temporary.resolve("migrations"));
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            try (var initial = application(port, secrets, location(migrations));
                 var admin = connect(port, "postgres", secrets.admin)) {
                String original = history(admin);
                initial.close();
                Files.copy(Path.of("src/test/resources/migrations/valid/V2__comment_namespace.sql"),
                        migrations.resolve("V2__comment_namespace.sql"));
                try (var upgraded = application(port, secrets, location(migrations))) {
                    assertThat(upgraded.containsBean("entityManagerFactory")).isTrue();
                    assertThat(history(admin)).startsWith(original + ",2:");
                    assertThat(scalar(admin, "select obj_description('demandflow'::regnamespace)"))
                            .isEqualTo("DemandFlow technical migration fixture V2");
                }
                String upgradedHistory = history(admin);
                Files.writeString(migrations.resolve("V1__initialize_application_namespace.sql"), "\n-- changed checksum\n",
                        java.nio.file.StandardOpenOption.APPEND);
                assertThrows(Exception.class, () -> application(port, secrets, location(migrations)));
                assertThat(history(admin)).isEqualTo(upgradedHistory);
            }
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }

    @Test
    void failedV2RollsBackItsCommentAndDoesNotRecordSuccess() throws Exception {
        var secrets = new Secrets(temporary);
        Path migrations = migrations(temporary.resolve("migrations"));
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            try (var initial = application(port, secrets, location(migrations));
                 var admin = connect(port, "postgres", secrets.admin)) {
                String original = history(admin);
                initial.close();
                Files.copy(Path.of("src/test/resources/migrations/failing/V2__fail_namespace_comment.sql"),
                        migrations.resolve("V2__fail_namespace_comment.sql"));
                assertThrows(Exception.class, () -> application(port, secrets, location(migrations)));
                assertThat(history(admin)).isEqualTo(original);
                assertThat(scalar(admin, "select coalesce(obj_description('demandflow'::regnamespace),'absent')"))
                        .isEqualTo("absent");
                assertThat(scalar(admin, "select count(*) from infra.flyway_schema_history where version='2' and success"))
                        .isEqualTo("0");
            }
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }
}
