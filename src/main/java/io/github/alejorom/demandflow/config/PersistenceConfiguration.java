package io.github.alejorom.demandflow.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("persistence")
class PersistenceConfiguration {
    @Bean
    static BeanFactoryPostProcessor validatePersistenceProperties(Environment environment) {
        return beanFactory -> {
            for (String property : new String[] {"spring.datasource.url", "spring.datasource.username",
                    "spring.datasource.password", "spring.flyway.url", "spring.flyway.user", "spring.flyway.password"}) {
                String value = environment.getProperty(property);
                if (value == null || value.isBlank()) {
                    throw new IncompletePersistenceConfiguration(property);
                }
            }
            for (String property : new String[] {"spring.datasource.url", "spring.flyway.url"}) {
                String url = environment.getRequiredProperty(property);
                if (!url.startsWith("jdbc:postgresql://127.0.0.1:")
                        || url.contains("password=") || url.contains("user=")) {
                    throw new IncompletePersistenceConfiguration(property);
                }
            }
        };
    }

    static final class IncompletePersistenceConfiguration extends IllegalStateException {
        IncompletePersistenceConfiguration(String property) {
            // Only the property name is safe to include, never its value.
            super("DemandFlow persistence configuration incomplete: " + property);
        }
    }
}
