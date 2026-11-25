package io.github.alejorom.demandflow;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PersistenceConfigurationTests {
    @TempDir Path temporary;

    @ParameterizedTest
    @ValueSource(strings = {"spring.datasource.url", "spring.datasource.username", "spring.datasource.password",
            "spring.flyway.url", "spring.flyway.user", "spring.flyway.password"})
    void incompleteConfigurationFailsBeforeOpeningConnections(String missing) throws Exception {
        var directory = temporary.resolve("spring");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("spring.datasource.password"), "unused-synthetic");
        Files.writeString(directory.resolve("spring.flyway.password"), "unused-synthetic");
        var error = assertThrows(Exception.class, () -> new SpringApplicationBuilder(DemandFlowApplication.class)
                .web(WebApplicationType.NONE).profiles("persistence")
                .run("--DEMANDFLOW_SECRETS_DIR=" + temporary.toString().replace('\\', '/'),
                        "--" + missing + "=", "--logging.level.root=OFF", "--debug=false"));
        Throwable root = error;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        assertThat(root.getMessage()).isEqualTo("DemandFlow persistence configuration incomplete: " + missing);
    }
}
