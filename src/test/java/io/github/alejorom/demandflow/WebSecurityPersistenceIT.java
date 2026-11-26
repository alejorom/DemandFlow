package io.github.alejorom.demandflow;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static io.github.alejorom.demandflow.PostgresTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** T7/T8: same real security chain with Flyway/JPA, exclusively temporary PostgreSQL. */
class WebSecurityPersistenceIT {
    @TempDir Path temporary;

    @Test
    void persistentModeHasNoGeneratedUserAndKeepsCsrfEnabled() throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            try (var context = application(postgres.getMappedPort(5432), secrets,
                    "--spring.main.web-application-type=servlet", "--server.port=0")) {
                assertThat(context.containsBean("flyway")).isTrue();
                assertThat(context.containsBean("entityManagerFactory")).isTrue();
                assertThat(context.getBeansOfType(UserDetailsService.class)).isEmpty();
                assertThat(ConditionEvaluationReport.get(context.getBeanFactory()).getExclusions())
                        .contains("org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration");
                var mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context)
                        .apply(springSecurity()).build();
                WebSecurityTests.assertEffectiveLoopbackBinding((WebApplicationContext) context);
                // Register only test controllers; no production authentication/authorization mechanism.
                var mappings = context.getBean(RequestMappingHandlerMapping.class);
                var probe = new WebSafetyTests.Probe();
                for (String operation : new String[] {"denied", "failure"}) {
                    mappings.registerMapping(RequestMappingInfo.paths("/security-persistence-probe/" + operation)
                                    .methods(RequestMethod.GET).options(mappings.getBuilderConfiguration()).build(),
                            probe, WebSafetyTests.Probe.class.getDeclaredMethod(operation));
                }
                mvc.perform(get("/security-persistence-probe/denied"))
                        .andExpect(status().isForbidden())
                        .andExpect(header().doesNotExist("WWW-Authenticate"));
                String failureBody = mvc.perform(get("/security-persistence-probe/failure"))
                        .andExpect(status().isInternalServerError()).andExpect(view().name("error"))
                        .andReturn().getResponse().getContentAsString();
                assertThat(failureBody).contains("No se pudo completar la solicitud")
                        .doesNotContain("synthetic_secret", "password=", "SQLException", "<script");
                for (String path : new String[] {"/", "/login", "/logout", "/tariffs/periods", "/tariffs/rates"}) {
                    mvc.perform(get(path)).andExpect(status().isNotFound())
                            .andExpect(header().doesNotExist("WWW-Authenticate"))
                            .andExpect(header().doesNotExist("Location"))
                            .andExpect(header().string("Content-Security-Policy",
                                    "default-src 'self'; style-src 'self'; script-src 'none'; object-src 'none'; "
                                    + "base-uri 'self'; frame-ancestors 'none'; form-action 'self'"))
                            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                            .andExpect(header().string("X-Frame-Options", "DENY"))
                            .andExpect(header().doesNotExist("Strict-Transport-Security"));
                }
                for (String path : new String[] {"/tariffs/periods", "/tariffs/rates"}) {
                    mvc.perform(post(path)).andExpect(status().isForbidden());
                    mvc.perform(post(path).with(csrf().useInvalidToken())).andExpect(status().isForbidden());
                    mvc.perform(post(path).with(csrf())).andExpect(status().isNotFound());
                }
            }
        }
    }
}
