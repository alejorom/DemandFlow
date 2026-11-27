package io.github.alejorom.demandflow;

import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"logging.level.root=ERROR", "debug=false"})
@ActiveProfiles("web-security-test")
@Import(WebSecurityTests.Fixtures.class)
@ExtendWith(OutputCaptureExtension.class)
class WebSecurityTests {

    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void realSecurityChain() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    static void assertEffectiveLoopbackBinding(WebApplicationContext context) throws Exception {
        var server = (TomcatWebServer) ((WebServerApplicationContext) context).getWebServer();
        var protocol = server.getTomcat().getConnector().getProtocolHandler();
        // Test-only inspection of the actual bound socket, rather than the configured address.
        var endpoint = ReflectionTestUtils.getField(protocol, "endpoint");
        var socket = (ServerSocketChannel) ReflectionTestUtils.getField(endpoint, "serverSock");
        assertThat(socket).isNotNull();
        assertThat(socket.isOpen()).isTrue();
        var bound = (InetSocketAddress) socket.getLocalAddress();
        assertThat(bound.getAddress().getHostAddress()).isEqualTo("127.0.0.1");
        assertThat(bound.getAddress().isLoopbackAddress()).isTrue();
        assertThat(bound.getAddress().isAnyLocalAddress()).isFalse();
        assertThat(bound.getPort()).isPositive().isEqualTo(server.getPort());
    }

    @Test
    void anonymousAccessNeedsNeitherDatabaseNorGeneratedUser(CapturedOutput output) throws Exception {
        mvc.perform(get("/security-probe")).andExpect(status().isOk())
                .andExpect(content().string("ok"))
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'self'; style-src 'self'; img-src 'self' data:; script-src 'none'; object-src 'none'; "
                        + "base-uri 'self'; frame-ancestors 'none'; form-action 'self'"))
                .andExpect(header().doesNotExist("WWW-Authenticate"));
        for (String path : new String[] {"/", "/login", "/logout", "/tariffs/periods", "/tariffs/rates"}) {
            mvc.perform(get(path)).andExpect(status().isNotFound())
                    .andExpect(header().doesNotExist("Location"))
                    .andExpect(header().doesNotExist("WWW-Authenticate"));
        }
        assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
        assertThat(context.getBeansOfType(UserDetailsService.class)).isEmpty();
        assertThat(context.containsBean("entityManagerFactory")).isFalse();
        assertThat(context.containsBean("flyway")).isFalse();
        assertThat(ConditionEvaluationReport.get(((ConfigurableApplicationContext) context).getBeanFactory()).getExclusions())
                .contains("org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration");
        assertThat(output.getAll()).doesNotContain("Using generated security password");
    }

    @Test
    void validCsrfReachesHandlerAndRejectedRequestsNeverDo() throws Exception {
        var probe = context.getBean(Probe.class);
        int before = probe.writes;
        mvc.perform(post("/security-probe")).andExpect(status().isForbidden());
        mvc.perform(post("/security-probe").with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
        assertThat(probe.writes).isEqualTo(before);
        mvc.perform(post("/security-probe").with(csrf())).andExpect(status().isOk());
        assertThat(probe.writes).isEqualTo(before + 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/tariffs/periods", "/tariffs/periods/1/edit",
            "/tariffs/periods/1/deactivate", "/tariffs/periods/1/reactivate",
            "/tariffs/rates", "/tariffs/rates/1/edit",
            "/tariffs/rates/1/deactivate", "/tariffs/rates/1/reactivate"})
    void catalogHasNoCsrfExemptionsEvenBeforeControllersExist(String path) throws Exception {
        mvc.perform(post(path)).andExpect(status().isForbidden());
        mvc.perform(post(path).with(csrf().useInvalidToken())).andExpect(status().isForbidden());
        mvc.perform(post(path).with(csrf())).andExpect(status().isNotFound());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Fixtures {
        @Bean Probe securityProbe() { return new Probe(); }
    }

    @RestController
    @TestComponent
    @Profile("web-security-test")
    static class Probe {
        int writes;
        @GetMapping("/security-probe") String get() { return "ok"; }
        @PostMapping("/security-probe") String post() { writes++; return "ok"; }
    }
}
