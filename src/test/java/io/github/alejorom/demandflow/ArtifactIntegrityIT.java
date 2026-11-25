package io.github.alejorom.demandflow;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ArtifactIntegrityIT {
    @Test
    void deliverableContainsOnlyProductionMigrationAndNoSecretsOrTestFixtures() throws Exception {
        try (var jar = new ZipFile(Path.of("target/demandflow-0.0.1-SNAPSHOT.jar").toFile())) {
            var names = jar.stream().map(java.util.zip.ZipEntry::getName).toList();
            assertThat(names).noneMatch(name -> name.contains("testcontainers") || name.contains("/migrations/")
                    || name.contains(".secrets") || name.contains(".password") || name.contains("V2__")
                    || name.endsWith("schema.sql") || name.endsWith("data.sql") || name.endsWith("IT.class"));
            assertThat(names.stream().filter(name -> name.startsWith("BOOT-INF/classes/db/migration/")
                    && name.endsWith(".sql")).toList())
                    .containsExactly("BOOT-INF/classes/db/migration/V1__initialize_application_namespace.sql");
            for (var entry : jar.stream().filter(item -> item.getName().startsWith("BOOT-INF/classes/")
                    && !item.isDirectory()).toList()) {
                String content = new String(jar.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
                assertFalse(content.contains("df-admin-") || content.contains("df-runtime-")
                        || content.contains("df-migrator-"), "Synthetic secret marker found in deliverable");
                assertFalse(content.contains("POSTGRES_PASSWORD") || content.contains("postgres.password")
                        || content.contains("PASSWORD '"), "Administrative secret or assignment found in deliverable");
            }
        }
    }
}
