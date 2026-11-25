package io.github.alejorom.demandflow;

import java.nio.file.Path;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.utility.MountableFile;

import static io.github.alejorom.demandflow.PostgresTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BootstrapIT {
    @TempDir Path temporary;

    @Test
    void bootstrapSeparatesRolesAndRejectsInvalidProvisioningWithoutLeaks() throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            assertThat(postgres.getContainerInfo().getNetworkSettings().getPorts()
                    .getBindings().values()).allSatisfy(bindings -> {
                        for (var binding : bindings) {
                            assertThat(binding.getHostIp()).isEqualTo("127.0.0.1");
                        }
                    });
            try (var admin = connect(port, "postgres", secrets.admin);
                 var runtime = connect(port, "demandflow_app", secrets.runtime);
                 var migrator = connect(port, "demandflow_migrator", secrets.migrator)) {
                assertThat(scalar(admin, "show server_version")).startsWith("17.12");
                assertThat(scalar(runtime, "select current_user")).isEqualTo("demandflow_app");
                assertThat(scalar(migrator, "select current_user")).isEqualTo("demandflow_migrator");
                assertThat(scalar(admin, "select count(*) from pg_roles where rolname in "
                        + "('demandflow_app','demandflow_migrator') and "
                        + "(rolsuper or rolcreatedb or rolcreaterole or rolreplication or rolbypassrls)"))
                        .isEqualTo("0");
                assertThat(scalar(runtime, "select has_database_privilege(current_user,'demandflow','CREATE')"))
                        .isEqualTo("f");
                assertThat(scalar(runtime, "select has_database_privilege(current_user,'demandflow','TEMP')"))
                        .isEqualTo("f");
                assertThat(scalar(runtime, "select has_schema_privilege(current_user,'infra','USAGE')"))
                        .isEqualTo("f");
                assertThat(scalar(runtime, "show statement_timeout")).isEqualTo("15s");
                assertThat(scalar(migrator, "show lock_timeout")).isEqualTo("5s");
                assertThrows(SQLException.class, () -> runtime.createStatement()
                        .execute("CREATE SCHEMA forbidden_runtime_schema"));
            }
            var repeated = postgres.execInContainer("bash", "/docker-entrypoint-initdb.d/10-bootstrap.sh");
            assertThat(repeated.getExitCode()).isNotZero();
            secrets.assertNotLeaked(repeated.getStdout() + repeated.getStderr());

            postgres.execInContainer("mv", "/run/secrets/postgres_runtime_password", "/run/secrets/saved_runtime");
            var missing = postgres.execInContainer("bash", "/docker-entrypoint-initdb.d/10-bootstrap.sh");
            assertThat(missing.getExitCode()).isNotZero();
            secrets.assertNotLeaked(missing.getStdout() + missing.getStderr());
            var empty = temporary.resolve("empty-secret");
            java.nio.file.Files.writeString(empty, "");
            postgres.copyFileToContainer(MountableFile.forHostPath(empty, 0444),
                    "/run/secrets/postgres_runtime_password");
            var blank = postgres.execInContainer("bash", "/docker-entrypoint-initdb.d/10-bootstrap.sh");
            assertThat(blank.getExitCode()).isNotZero();
            secrets.assertNotLeaked(blank.getStdout() + blank.getStderr());
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }
}
