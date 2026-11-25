package io.github.alejorom.demandflow;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static io.github.alejorom.demandflow.PostgresTestSupport.*;
import static io.github.alejorom.demandflow.JarTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One Compose check: persistent recreation and restoration, always on disposable projects. */
class ComposePersistenceIT {
    @TempDir Path temporary;
    private Secrets secrets;

    @Test
    void deliveredComposePreservesItsBindDirectoryAndRestoresATechnicalBackup() throws Exception {
        secrets = new Secrets(temporary);
        String project = "demandflow-it-" + UUID.randomUUID().toString().replace("-", "");
        String restoreProject = project + "-restore";
        Files.createDirectories(dataDirectory(project));
        Files.createDirectories(dataDirectory(restoreProject));
        assertThat(docker("context", "inspect", "--format", "{{.Endpoints.docker.Host}}"))
                .matches("(?s)(npipe:.*|unix:.*|tcp://(127\\.0\\.0\\.1|localhost):[0-9]+)\\s*");
        assertThat(docker("volume", "ls", "--filter", "label=com.docker.compose.project=" + project,
                "--format", "{{.Name}}").trim()).isEmpty();
        try {
            compose(project, "config", "--quiet");
            // Image download has a distinct 300 s budget, before startup acceptance.
            compose(project, "pull");
            assertThat(docker("image", "inspect", IMAGE, "--format", "{{json .RepoDigests}}"))
                    .contains("postgres@sha256:87c8b42f423b45d5247e6aa8fdb2e7762fec164c68a50fe3eb07e7bfcaf4dbcf");
            compose(project, "up", "--wait", "--wait-timeout", "120");
            String originalContainer = container(project);
            String originalMount = dataMount(originalContainer);
            assertThat(Files.readString(dataDirectory(project).resolve("PG_VERSION")).trim()).isEqualTo("17");
            Files.writeString(dataDirectory(project).resolve(".demandflow-test-identity"), project);
            assertThat(compose(project, "exec", "-T", "postgres", "cat",
                    "/var/lib/postgresql/data/.demandflow-test-identity")).isEqualTo(project);
            int port = port(project);
            String expected;
            runJar(project, port, "first");
            try (var admin = connect(port, "postgres", secrets.admin)) {
                assertThat(scalar(admin, "show server_version")).startsWith("17.12");
                admin.createStatement().execute("COMMENT ON SCHEMA demandflow IS 'DemandFlow technical backup verification'");
                expected = snapshot(admin);
            }
            compose(project, "up", "--force-recreate", "--wait", "--wait-timeout", "120");
            assertThat(container(project)).isNotEqualTo(originalContainer);
            assertThat(dataMount(container(project))).isEqualTo(originalMount);
            assertThat(Files.readString(dataDirectory(project).resolve(".demandflow-test-identity")))
                    .isEqualTo(project);
            port = port(project);
            runJar(project, port, "recreated");
            try (var admin = connect(port, "postgres", secrets.admin)) {
                assertThat(snapshot(admin)).isEqualTo(expected);
            }
            assertThat(compose(project, "logs", "--no-color", "postgres"))
                    .contains("Skipping initialization").doesNotContain("roles and infrastructure schema initialized");

            compose(project, "exec", "-T", "postgres", "pg_dump", "-U", "postgres", "-d", "demandflow",
                    "--format=custom", "--file=/tmp/demandflow.dump");
            Path backup = temporary.resolve("demandflow.dump");
            docker("cp", container(project) + ":/tmp/demandflow.dump", backup.toString());
            compose(restoreProject, "up", "--wait", "--wait-timeout", "120");
            assertThat(dataMount(container(restoreProject))).isNotEqualTo(originalMount);
            assertThat(Files.readString(dataDirectory(restoreProject).resolve("PG_VERSION")).trim()).isEqualTo("17");
            docker("cp", backup.toString(), container(restoreProject) + ":/tmp/demandflow.dump");
            compose(restoreProject, "exec", "-T", "postgres", "pg_restore", "-U", "postgres", "-d", "demandflow",
                    "--clean", "--if-exists", "--exit-on-error", "/tmp/demandflow.dump");
            int restoredPort = port(restoreProject);
            try (var admin = connect(restoredPort, "postgres", secrets.admin)) {
                assertThat(snapshot(admin)).isEqualTo(expected);
            }
            runJar(restoreProject, restoredPort, "restored");
            runJar(restoreProject, restoredPort, "restored-second");
            try (var admin = connect(restoredPort, "postgres", secrets.admin);
                 var runtime = connect(restoredPort, "demandflow_app", secrets.runtime)) {
                assertThat(snapshot(admin)).isEqualTo(expected);
                assertThat(scalar(runtime, "select has_schema_privilege(current_user,'demandflow','CREATE')"))
                        .isEqualTo("f");
                assertThat(scalar(runtime, "select has_schema_privilege(current_user,'infra','USAGE')"))
                        .isEqualTo("f");
            }
            System.out.println("Compose PostgreSQL digest: " + docker("image", "inspect", IMAGE,
                    "--format", "{{json .RepoDigests}}").trim());
            System.out.println("Compose recreation/restore passed: " + project + " / " + restoreProject);
        } finally {
            try {
                cleanup(restoreProject);
            } finally {
                cleanup(project);
            }
        }
    }

    private void runJar(String project, int databasePort, String stage) throws Exception {
        int httpPort = freePort();
        Path output = temporary.resolve("compose-" + stage + ".log");
        Process process = start(output, httpPort, databasePort, secrets);
        try {
            ready(process, output, httpPort, secrets);
        } finally {
            stopped(process);
        }
        secrets.assertNotLeaked(Files.readString(output));
        secrets.assertNotLeaked(compose(project, "logs", "--no-color", "postgres"));
    }

    private String snapshot(Connection admin) throws Exception {
        assertThat(scalar(admin, "select count(*) from pg_tables where schemaname in ('infra','demandflow')"))
                .isEqualTo("1");
        return scalar(admin, "select string_agg(nspname || ':' || pg_get_userbyid(nspowner) || ':' "
                + "|| coalesce(nspacl, acldefault('n', nspowner))::text "
                + "|| ':' || coalesce(obj_description(oid),'absent'), ',' order by nspname) from pg_namespace "
                + "where nspname in ('infra','demandflow')") + "|" + MigrationEvolutionIT.history(admin);
    }

    private String container(String project) throws Exception {
        String id = compose(project, "ps", "--quiet", "postgres").trim();
        assertThat(id).matches("[0-9a-f]{12,64}");
        assertThat(docker("inspect", "--format", "{{json .Config.Labels}}", id))
                .contains("\"com.docker.compose.project\":\"" + project + "\"");
        return id;
    }

    private int port(String project) throws Exception {
        String published = compose(project, "port", "postgres", "5432").trim();
        assertThat(published).matches("127\\.0\\.0\\.1:[0-9]+");
        assertThat(docker("inspect", "--format", "{{range .NetworkSettings.Ports}}{{range .}}"
                + "{{.HostIp}}:{{.HostPort}}{{end}}{{end}}", container(project)).trim()).isEqualTo(published);
        return Integer.parseInt(published.substring(published.lastIndexOf(':') + 1));
    }

    private void cleanup(String project) throws Exception {
        assertThat(project).matches("demandflow-it-[0-9a-f]{32}(-restore)?");
        String volumes = docker("volume", "ls", "--filter", "label=com.docker.compose.project=" + project,
                "--format", "{{.Name}}").trim();
        assertThat(volumes).isEmpty();
        String ids = docker("ps", "-a", "--filter", "label=com.docker.compose.project=" + project,
                "--format", "{{.ID}}").trim();
        for (String id : ids.lines().filter(line -> !line.isBlank()).toList()) {
            assertThat(docker("inspect", "--format", "{{json .Config.Labels}}", id))
                    .contains("\"com.docker.compose.project\":\"" + project + "\"");
        }
        compose(project, "down");
        // Compose must leave the host directory intact; JUnit removes only its own TempDir.
        assertThat(Files.isDirectory(dataDirectory(project))).isTrue();
        assertThat(docker("ps", "-a", "--filter", "label=com.docker.compose.project=" + project,
                "--format", "{{.ID}}").trim()).isEmpty();
        assertThat(docker("network", "ls", "--filter", "label=com.docker.compose.project=" + project,
                "--format", "{{.ID}}").trim()).isEmpty();
        assertThat(docker("volume", "ls", "--filter", "label=com.docker.compose.project=" + project,
                "--format", "{{.Name}}").trim()).isEmpty();
    }

    private Path dataDirectory(String project) {
        assertThat(project).matches("demandflow-it-[0-9a-f]{32}(-restore)?");
        Path directory = temporary.resolve(project.endsWith("-restore") ? "restore-pgdata" : "source-pgdata")
                .toAbsolutePath().normalize();
        assertThat(directory.startsWith(temporary.toAbsolutePath().normalize())).isTrue();
        assertThat(directory.startsWith(Path.of("D:/DOCKER_DATA").toAbsolutePath().normalize())).isFalse();
        return directory;
    }

    private String dataMount(String containerId) throws Exception {
        var mounts = docker("inspect", "--format",
                "{{range .Mounts}}{{.Type}}|{{.Source}}|{{.Destination}}{{println}}{{end}}", containerId)
                .lines().filter(line -> line.endsWith("|/var/lib/postgresql/data")).toList();
        assertThat(mounts).hasSize(1);
        assertThat(mounts.getFirst()).startsWith("bind|");
        return mounts.getFirst();
    }

    private String compose(String project, String... arguments) throws Exception {
        var command = new ArrayList<>(List.of("compose", "--file", Path.of("compose.yaml").toAbsolutePath().toString(),
                "--project-name", project));
        command.addAll(List.of(arguments));
        return docker(command.toArray(String[]::new));
    }

    private String docker(String... arguments) throws Exception {
        var command = new ArrayList<>(List.of("docker"));
        command.addAll(List.of(arguments));
        Path output = Files.createTempFile(temporary, "docker-", ".log");
        var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile());
        builder.environment().put("DEMANDFLOW_DB_PORT", "0");
        builder.environment().put("DEMANDFLOW_SECRETS_DIR", secrets.directory.toAbsolutePath().toString());
        String composeProject = "demandflow-it-probe";
        for (int i = 0; i + 1 < arguments.length; i++) {
            if (arguments[i].equals("--project-name")) composeProject = arguments[i + 1];
        }
        builder.environment().put("DEMANDFLOW_DATA_DIR", composeProject.equals("demandflow-it-probe")
                ? temporary.toAbsolutePath().normalize().toString() : dataDirectory(composeProject).toString());
        builder.environment().remove("COMPOSE_FILE");
        builder.environment().remove("COMPOSE_PROFILES");
        Process process = builder.start();
        int timeout = List.of(arguments).contains("pull") ? 300 : 150;
        boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        assertTrue(finished, "Docker command exceeded its finite budget");
        String result = Files.readString(output);
        secrets.assertNotLeaked(result);
        assertThat(process.exitValue()).as(String.join(" ", arguments) + " : " + result).isZero();
        return result;
    }
}
