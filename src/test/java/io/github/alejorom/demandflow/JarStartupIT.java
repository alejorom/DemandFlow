package io.github.alejorom.demandflow;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static io.github.alejorom.demandflow.PostgresTestSupport.*;
import static io.github.alejorom.demandflow.JarTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class JarStartupIT {
    @TempDir Path temporary;

    @Test
    void packagedApplicationInitializesPersistenceBeforeAvailabilityAndPreservesV1() throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int databasePort = postgres.getMappedPort(5432);
            String history = null;
            for (int attempt = 0; attempt < 2; attempt++) {
                int httpPort = freePort();
                Path output = temporary.resolve("startup-" + attempt + ".log");
                Process process = start(output, httpPort, databasePort, secrets,
                        "--logging.level.org.springframework.orm.jpa=INFO");
                try {
                    ready(process, output, httpPort, secrets);
                    try (var admin = connect(databasePort, "postgres", secrets.admin)) {
                        String current = scalar(admin, "select version || ':' || checksum || ':' || installed_rank "
                                + "|| ':' || installed_on from infra.flyway_schema_history where version='1' and success");
                        if (history == null) history = current;
                        else assertThat(current).isEqualTo(history);
                        assertThat(scalar(admin, "select count(*) from pg_tables where schemaname in ('infra','demandflow')"))
                                .isEqualTo("1");
                    }
                    String log = java.nio.file.Files.readString(output);
                    assertThat(log.indexOf("Initialized JPA EntityManagerFactory"))
                            .isGreaterThanOrEqualTo(0).isLessThan(log.indexOf("Started DemandFlowApplication"));
                    assertThat(log).doesNotContain("jdbc:postgresql:");
                    assertLoopbackListener(process, httpPort);
                } finally {
                    stopped(process);
                }
            }
            // Configuration and a live database must not activate persistence implicitly.
            for (String profile : new String[] {"default", "unrelated"}) {
                int httpPort = freePort();
                Path output = temporary.resolve("without-persistence-" + profile + ".log");
                Process process = start(output, httpPort, databasePort, secrets,
                        "--spring.profiles.active=" + profile,
                        "--spring.config.import=configtree:" + secrets.directory.toAbsolutePath()
                                .toString().replace('\\', '/') + "/spring/",
                        "--spring.datasource.url=" + url(databasePort),
                        "--spring.datasource.username=demandflow_app",
                        "--spring.flyway.url=" + url(databasePort),
                        "--spring.flyway.user=demandflow_migrator");
                try {
                    ready(process, output, httpPort, secrets);
                    assertLoopbackListener(process, httpPort);
                    try (var admin = connect(databasePort, "postgres", secrets.admin)) {
                        assertThat(scalar(admin, "select version || ':' || checksum || ':' || installed_rank "
                                + "|| ':' || installed_on from infra.flyway_schema_history where version='1' and success"))
                                .isEqualTo(history);
                        assertThat(scalar(admin, "select count(*) from pg_stat_activity "
                                + "where usename in ('demandflow_app','demandflow_migrator')"))
                                .isEqualTo("0");
                    }
                    assertThat(java.nio.file.Files.readString(output))
                            .doesNotContain("Initialized JPA EntityManagerFactory", "jdbc:postgresql:");
                } finally {
                    stopped(process);
                }
            }
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }

    private void assertLoopbackListener(Process application, int port) throws Exception {
        // Windows is the local runtime delivered by this repository.
        if (System.getProperty("os.name").startsWith("Windows")) {
            Process netstat = new ProcessBuilder("netstat", "-ano", "-p", "tcp").start();
            String sockets = new String(netstat.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(netstat.waitFor()).isZero();
            var listeners = sockets.lines().filter(line -> line.contains("LISTENING")
                    && line.trim().endsWith(" " + application.pid())).toList();
            assertThat(listeners).hasSize(1);
            assertThat(listeners.getFirst().trim().split("\\s+")[1]).isEqualTo("127.0.0.1:" + port);
        }
    }
}
