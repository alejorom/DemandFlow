package io.github.alejorom.demandflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ComponentScan(excludeFilters = {
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
        // Persistent modules are scanned only by the profile-scoped catalog configuration.
        @ComponentScan.Filter(type = FilterType.REGEX,
                pattern = "io\\.github\\.alejorom\\.demandflow\\.(tariffs|audit)\\..*")
})
public class DemandFlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemandFlowApplication.class, args);
    }
}
