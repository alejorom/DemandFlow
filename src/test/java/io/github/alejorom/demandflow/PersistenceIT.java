package io.github.alejorom.demandflow;

import java.nio.file.Path;
import java.sql.SQLException;
import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;

import static io.github.alejorom.demandflow.PostgresTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PersistenceIT {
    @TempDir Path temporary;

    @Test
    void flywayOwnsTheTechnicalSchemaAndRepeatedStartupPreservesHistory() throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            String history;
            try (var context = application(port, secrets);
                 var runtime = context.getBean(DataSource.class).getConnection();
                 var admin = connect(port, "postgres", secrets.admin)) {
                assertThat(context.containsBean("flyway")).isTrue();
                assertThat(context.getBean(EntityManagerFactory.class).getMetamodel().getEntities()).isEmpty();
                assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
                assertThat(ConditionEvaluationReport.get(context.getBeanFactory()).getExclusions()).isEmpty();
                assertThat(scalar(runtime, "select current_user")).isEqualTo("demandflow_app");
                assertThat(scalar(admin, "select pg_get_userbyid(nspowner) from pg_namespace where nspname='demandflow'"))
                        .isEqualTo("demandflow_migrator");
                assertThat(scalar(runtime, "select has_schema_privilege(current_user,'demandflow','USAGE')"))
                        .isEqualTo("t");
                assertThat(scalar(runtime, "select has_schema_privilege(current_user,'demandflow','CREATE')"))
                        .isEqualTo("f");
                assertThat(scalar(admin, "select count(*) from pg_tables where schemaname in ('demandflow','infra') "
                        + "and tablename <> 'flyway_schema_history'"))
                        .isEqualTo("0");
                assertThrows(SQLException.class, () -> runtime.createStatement()
                        .executeQuery("select * from infra.flyway_schema_history"));
                assertThat(scalar(admin, "select count(*) from infra.flyway_schema_history where version='1' and success"))
                        .isEqualTo("1");
                history = scalar(admin, "select version || ':' || checksum || ':' || installed_rank || ':' || installed_on "
                        + "from infra.flyway_schema_history where version='1'");
            }
            try (var context = application(port, secrets);
                 var admin = connect(port, "postgres", secrets.admin)) {
                assertThat(context.containsBean("entityManagerFactory")).isTrue();
                assertThat(scalar(admin, "select version || ':' || checksum || ':' || installed_rank || ':' || installed_on "
                        + "from infra.flyway_schema_history where version='1'"))
                        .isEqualTo(history);
            }
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }
}
