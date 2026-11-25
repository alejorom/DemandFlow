package io.github.alejorom.demandflow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.flywaydb.core.Flyway;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;
import org.awaitility.core.ConditionTimeoutException;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.awaitility.Awaitility.await;

final class PostgresTestSupport {
    static final String IMAGE = "postgres:17.12-trixie";
    static final String V1 = "V1__initialize_application_namespace.sql";

    /** Copies only the technical baseline, independent of later production migrations or JPA. */
    static Path technicalV1(Path directory) throws IOException {
        Path source = Path.of(System.getProperty("demandflow.test.v1-source",
                "src/main/resources/db/migration/" + V1));
        if (!Files.isRegularFile(source)) {
            throw new IOException("Isolated persistence prerequisite missing: V1 migration source");
        }
        Files.createDirectories(directory);
        Files.copy(source, directory.resolve(V1));
        return directory;
    }

    /** Uses only the port of the test-created container; never discovers development settings. */
    static Flyway technicalFlyway(int port, Secrets secrets, Path migrations) {
        return Flyway.configure()
                .dataSource(url(port), "demandflow_migrator", secrets.migrator)
                .locations("filesystem:" + migrations.toAbsolutePath().toString().replace('\\', '/'))
                .defaultSchema("infra").schemas("infra").createSchemas(false)
                .validateOnMigrate(true).baselineOnMigrate(false).outOfOrder(false)
                .cleanDisabled(true).failOnMissingLocations(true)
                .connectRetries(0).lockRetryCount(3).load();
    }

    static final class Secrets {
        final Path directory;
        final String admin = "df-admin-" + UUID.randomUUID();
        final String migrator = "df-migrator-'" + UUID.randomUUID();
        final String runtime = "df-runtime-" + UUID.randomUUID();

        Secrets(Path directory) throws IOException {
            this.directory = directory;
            Files.createDirectories(directory.resolve("bootstrap"));
            Files.createDirectories(directory.resolve("spring"));
            Files.writeString(directory.resolve("bootstrap/postgres.password"), admin);
            Files.writeString(directory.resolve("spring/spring.flyway.password"), migrator);
            Files.writeString(directory.resolve("spring/spring.datasource.password"), runtime);
        }

        void assertNotLeaked(String output) {
            assertFalse(output.contains(admin), "Administrative secret leaked");
            assertFalse(output.contains(migrator), "Migration secret leaked");
            assertFalse(output.contains(runtime), "Runtime secret leaked");
        }
    }

    static GenericContainer<?> postgres(Secrets secrets) {
        return new GenericContainer<>(DockerImageName.parse(IMAGE))
                .withEnv("POSTGRES_DB", "demandflow")
                .withEnv("POSTGRES_USER", "postgres")
                .withEnv("POSTGRES_PASSWORD_FILE", "/run/secrets/postgres_admin_password")
                .withEnv("POSTGRES_HOST_AUTH_METHOD", "scram-sha-256")
                .withEnv("POSTGRES_INITDB_ARGS", "--auth-host=scram-sha-256")
                .withCopyFileToContainer(MountableFile.forHostPath(
                        secrets.directory.resolve("bootstrap/postgres.password"), 0444),
                        "/run/secrets/postgres_admin_password")
                .withCopyFileToContainer(MountableFile.forHostPath(
                        secrets.directory.resolve("spring/spring.flyway.password"), 0444),
                        "/run/secrets/postgres_migrator_password")
                .withCopyFileToContainer(MountableFile.forHostPath(
                        secrets.directory.resolve("spring/spring.datasource.password"), 0444),
                        "/run/secrets/postgres_runtime_password")
                .withCopyFileToContainer(MountableFile.forHostPath("docker/postgres/10-bootstrap.sh", 0755),
                        "/docker-entrypoint-initdb.d/10-bootstrap.sh")
                .withExposedPorts(5432)
                .withCreateContainerCmdModifier(command -> command.getHostConfig().withPortBindings(
                        new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1", 0), new ExposedPort(5432))))
                .withStartupAttempts(1)
                .waitingFor(new JdbcReadiness(secrets).withStartupTimeout(Duration.ofSeconds(120)));
    }

    /** Waits for the final TCP server and bootstrapped roles, rather than an open forwarding port. */
    static final class JdbcReadiness extends AbstractWaitStrategy {
        private final Secrets secrets;

        JdbcReadiness(Secrets secrets) {
            this.secrets = secrets;
        }

        @Override
        protected void waitUntilReady() {
            int port = waitStrategyTarget.getMappedPort(5432);
            try {
                await().alias("temporary PostgreSQL JDBC readiness")
                        .pollDelay(Duration.ZERO).pollInterval(Duration.ofMillis(100))
                        .atMost(startupTimeout)
                        .until(() -> jdbcReady(port, "demandflow_migrator", secrets.migrator)
                                && jdbcReady(port, "demandflow_app", secrets.runtime));
            } catch (ConditionTimeoutException timeout) {
                throw new ContainerLaunchException("Temporary PostgreSQL JDBC readiness deadline exceeded");
            }
        }
    }

    static boolean jdbcReady(int port, String role, String password) {
        var properties = new Properties();
        properties.setProperty("user", role);
        properties.setProperty("password", password);
        // Probe-only native bounds; application JDBC URLs and production timeouts remain unchanged.
        properties.setProperty("connectTimeout", "1");
        properties.setProperty("loginTimeout", "1");
        properties.setProperty("socketTimeout", "1");
        properties.setProperty("cancelSignalTimeout", "1");
        try (var connection = DriverManager.getConnection(
                "jdbc:postgresql://127.0.0.1:" + port + "/demandflow", properties);
             var statement = connection.createStatement()) {
            statement.setQueryTimeout(1);
            try (var result = statement.executeQuery("select current_user, 1")) {
                if (!result.next() || !role.equals(result.getString(1)) || result.getInt(2) != 1) {
                    throw new ContainerLaunchException("Temporary PostgreSQL JDBC readiness returned an invalid result");
                }
                return true;
            }
        } catch (SQLException failure) {
            // Only startup/transport states can mean not ready yet. Authentication and SQL failures abort.
            if (failure.getSQLState() != null && List.of("57P03", "08001", "08006").contains(failure.getSQLState())) {
                return false;
            }
            throw new ContainerLaunchException("Temporary PostgreSQL JDBC readiness failed; SQLSTATE="
                    + failure.getSQLState());
        }
    }

    static String url(int port) {
        return "jdbc:postgresql://127.0.0.1:" + port
                + "/demandflow?connectTimeout=5&loginTimeout=5&socketTimeout=15&cancelSignalTimeout=2";
    }

    static Connection connect(int port, String user, String password) throws SQLException {
        return DriverManager.getConnection(url(port), user, password);
    }

    static String scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            if (!result.next()) {
                throw new SQLException("Expected a result row");
            }
            return result.getString(1);
        }
    }

    static ConfigurableApplicationContext application(int port, Secrets secrets, String... extra) {
        var arguments = new ArrayList<>(List.of("--DEMANDFLOW_DB_PORT=" + port,
                "--DEMANDFLOW_SECRETS_DIR=" + secrets.directory.toString().replace('\\', '/'),
                "--spring.main.banner-mode=off", "--logging.level.root=ERROR", "--debug=false"));
        arguments.addAll(List.of(extra));
        return new SpringApplicationBuilder(DemandFlowApplication.class)
                .web(WebApplicationType.NONE).profiles("persistence").run(arguments.toArray(String[]::new));
    }

    private PostgresTestSupport() { }
}
