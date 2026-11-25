package io.github.alejorom.demandflow;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Small process assertions shared by the JAR and Compose integration checks. */
final class JarTestSupport {
    static int freePort() throws Exception {
        try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }

    static Process start(Path output, int httpPort, int databasePort,
                         PostgresTestSupport.Secrets secrets, String... extra) throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-jar", Path.of("target/demandflow-0.0.1-SNAPSHOT.jar").toAbsolutePath().toString(),
                "--spring.profiles.active=persistence", "--server.port=" + httpPort,
                "--DEMANDFLOW_DB_PORT=" + databasePort,
                "--DEMANDFLOW_SECRETS_DIR=" + secrets.directory.toAbsolutePath().toString().replace('\\', '/'),
                "--spring.main.banner-mode=off", "--debug=false"));
        for (String argument : extra) {
            int separator = argument.indexOf('=');
            if (argument.startsWith("--") && separator > 0) {
                String key = argument.substring(0, separator + 1);
                command.removeIf(existing -> existing.startsWith(key));
            }
            command.add(argument);
        }
        var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile());
        builder.environment().keySet().removeIf(JarTestSupport::configurationVariable);
        // An accidentally activated Docker integration must not discover the user's daemon.
        builder.environment().put("DOCKER_HOST", "tcp://127.0.0.1:1");
        return builder.start();
    }

    static boolean configurationVariable(String key) {
        String name = key.toUpperCase(Locale.ROOT);
        return name.startsWith("SPRING_") || name.startsWith("DEMANDFLOW_") || name.startsWith("DOCKER_")
                || name.startsWith("TESTCONTAINERS_") || name.startsWith("LOGGING_")
                || name.startsWith("SERVER_") || List.of("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS",
                        "SPRING_APPLICATION_JSON", "CLASSPATH").contains(name);
    }

    static void ready(Process process, Path output, int port, PostgresTestSupport.Secrets secrets) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(75).toNanos();
        boolean ready = false;
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        while (process.isAlive() && System.nanoTime() < deadline) {
            String log = Files.readString(output);
            secrets.assertNotLeaked(log);
            if (log.contains("Started DemandFlowApplication")) {
                try {
                    var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/"))
                            .timeout(Duration.ofSeconds(1)).GET().build(), HttpResponse.BodyHandlers.discarding());
                    ready = response.statusCode() == 404;
                    if (ready) break;
                } catch (java.io.IOException unavailable) {
                    // The socket can become available just after the startup log is flushed.
                }
            }
            Thread.sleep(100);
        }
        assertTrue(ready, "JAR did not become available spontaneously within 75 seconds");
        assertThat(process.isAlive()).isTrue();
    }

    static void stopped(Process process) throws Exception {
        if (process.isAlive()) {
            process.destroy();
            boolean ordinary = process.waitFor(10, TimeUnit.SECONDS);
            if (!ordinary) {
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
            assertTrue(ordinary, "JAR required forced termination; ordinary shutdown exceeded 10 seconds");
        }
    }

    private JarTestSupport() { }
}
