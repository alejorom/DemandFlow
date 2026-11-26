package io.github.alejorom.demandflow;

import javax.sql.DataSource;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.data.repository.Repository;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultStartupTests {

    @ParameterizedTest
    @ValueSource(strings = {"default", "unrelated", "connection-properties"})
    void persistenceRequiresDeliberateActivation(String mode) {
        var application = new SpringApplicationBuilder(DemandFlowApplication.class)
                .web(WebApplicationType.NONE);
        if (mode.equals("unrelated")) {
            application.profiles("unrelated");
        }
        String[] arguments = mode.equals("connection-properties")
                ? new String[] {"--spring.datasource.url=jdbc:postgresql://127.0.0.1:1/unused",
                    "--spring.datasource.username=unused", "--spring.datasource.password=unused",
                    "--spring.flyway.url=jdbc:postgresql://127.0.0.1:1/unused",
                    "--logging.level.root=ERROR", "--debug=false"}
                : new String[] {"--logging.level.root=ERROR", "--debug=false"};
        try (var context = application.run(arguments)) {
            assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
            assertThat(context.containsBean("flyway")).isFalse();
            assertThat(context.containsBean("entityManagerFactory")).isFalse();
            assertThat(context.containsBean("catalogPersistenceConfiguration")).isFalse();
            assertThat(context.containsBean("catalogProfileProbe")).isFalse();
            assertThat(context.containsBean("catalogAuditWriter")).isFalse();
            assertThat(context.containsBean("catalogAuditInsert")).isFalse();
            assertThat(context.containsBean("catalogAuditClock")).isFalse();
            assertThat(context.getBeansOfType(Repository.class)).isEmpty();
            assertThat(context.getEnvironment().getProperty("server.address")).isEqualTo("127.0.0.1");
            assertThat(ConditionEvaluationReport.get(context.getBeanFactory()).getExclusions())
                    .contains("org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
                            "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration");
        }
    }
}
