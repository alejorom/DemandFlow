package io.github.alejorom.demandflow.config;

import java.time.Clock;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration(proxyBeanMethods = false)
@Profile("persistence")
@ComponentScan(basePackages = {"io.github.alejorom.demandflow.tariffs", "io.github.alejorom.demandflow.audit"})
@EntityScan(basePackages = {"io.github.alejorom.demandflow.tariffs.domain", "io.github.alejorom.demandflow.audit.persistence"})
@EnableJpaRepositories(basePackages = {"io.github.alejorom.demandflow.tariffs.persistence", "io.github.alejorom.demandflow.audit.persistence"})
public class CatalogPersistenceConfiguration {
    @Bean
    Clock catalogAuditClock() {
        return Clock.systemUTC();
    }
}
