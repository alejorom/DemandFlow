package io.github.alejorom.demandflow;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** T7: real chain, rendered safe error page and embedded servlet error dispatch, without a database. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"logging.level.root=ERROR", "debug=false"})
@ActiveProfiles("web-safety-test")
@Import(WebSafetyTests.Fixtures.class)
@ExtendWith(OutputCaptureExtension.class)
class WebSafetyTests {
    private static final String CSP = "default-src 'self'; style-src 'self'; script-src 'none'; object-src 'none'; "
            + "base-uri 'self'; frame-ancestors 'none'; form-action 'self'";
    // Deliberately synthetic hostile input; never a real credential.
    private static final String POISON = "SELECT synthetic_secret FROM private_table; password=synthetic-only <script>bad()</script>";

    @Autowired WebApplicationContext context;
    @Autowired Environment environment;
    MockMvc mvc;

    @BeforeEach
    void realSecurityChain() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void runningServerBindsOnlyToLoopback() throws Exception {
        WebSecurityTests.assertEffectiveLoopbackBinding(context);
    }

    @Test
    void headersProtectSuccessStaticResourcesMissingRoutesCsrfAndUnexpectedErrors() throws Exception {
        List<MockHttpServletRequestBuilder> requests = List.of(get("/safety-probe"),
                get("/security-probe.css"), get("/missing"), post("/tariffs/rates"), get("/safety-probe/failure"));
        int[] statuses = {200, 200, 404, 403, 500};
        for (int index = 0; index < requests.size(); index++) {
            mvc.perform(requests.get(index)).andExpect(status().is(statuses[index]))
                    .andExpect(header().string("Content-Security-Policy", CSP))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("X-Frame-Options", "DENY"))
                    .andExpect(header().doesNotExist("Strict-Transport-Security"))
                    .andExpect(header().doesNotExist("WWW-Authenticate"));
        }
        mvc.perform(get("/security-probe.css")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("body { color: black; }")));
    }

    @Test
    void unexpectedExceptionsRenderConstantHtmlWithoutLeakingIntoLogs(CapturedOutput output) throws Exception {
        for (String path : new String[] {"/safety-probe/failure", "/safety-probe/checked"}) {
            String body = mvc.perform(get(path).param("trace", "true").param("message", "true")
                            .param("errors", "true").param("input", POISON))
                    .andExpect(status().isInternalServerError())
                    .andExpect(view().name("error"))
                    .andExpect(content().contentTypeCompatibleWith("text/html"))
                    .andReturn().getResponse().getContentAsString();
            assertSafe(body);
            assertSafeErrorStructure(body);
        }
        assertThat(output.getAll()).contains("Unexpected web request failure");
        assertSafe(output.getAll());
    }

    @Test
    void mvcAccessDeniedRemainsForbiddenWithoutExposingDetails(CapturedOutput output) throws Exception {
        mvc.perform(get("/safety-probe/denied"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Security-Policy", CSP))
                .andExpect(header().doesNotExist("WWW-Authenticate"));
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build()) {
            for (String accept : new String[] {"text/html", "application/json"}) {
                var response = send(client, "/safety-probe/denied?trace=true&message=true", accept);
                assertThat(response.statusCode()).isEqualTo(403);
                assertThat(response.headers().firstValue("Content-Security-Policy")).contains(CSP);
                assertThat(response.headers().firstValue("WWW-Authenticate")).isEmpty();
                assertSafe(response.body());
                assertThat(response.body()).doesNotContain("AccessDeniedException", "\"message\"", "\"trace\"");
                if (accept.equals("text/html")) {
                    assertSafeErrorStructure(response.body());
                }
            }
        }
        assertSafe(output.getAll());
        assertThat(output.getAll()).doesNotContain("Unexpected web request failure");
    }

    @Test
    void realServletErrorsKeepStatusAndSuppressDetailsInHtmlAndJson(CapturedOutput output) throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build()) {
            for (String accept : new String[] {"text/html", "application/json"}) {
                for (String path : new String[] {"/missing-sensitive-path", "/safety-probe/status", "/safety-probe/failure"}) {
                    int expected = path.endsWith("/status") ? 400 : path.endsWith("/failure") ? 500 : 404;
                    var response = send(client, path + "?trace=true&message=true&errors=true&exception=true", accept);
                    assertThat(response.statusCode()).isEqualTo(expected);
                    assertThat(response.headers().firstValue("Content-Security-Policy")).contains(CSP);
                    assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
                    assertThat(response.headers().firstValue("X-Frame-Options")).contains("DENY");
                    assertThat(response.headers().firstValue("Strict-Transport-Security")).isEmpty();
                    assertSafe(response.body());
                    assertThat(response.body()).doesNotContain("missing-sensitive-path", "\"path\"", "\"trace\"",
                            "\"exception\"", "\"message\"", "\"errors\"");
                    if (accept.equals("text/html")) {
                        assertSafeErrorStructure(response.body());
                    }
                }
            }
            // Real CSRF rejection is dispatched through the container error page too.
            var request = HttpRequest.newBuilder(uri("/tariffs/rates?message=true&trace=true"))
                    .timeout(Duration.ofSeconds(5)).header("Accept", "text/html")
                    .POST(HttpRequest.BodyPublishers.ofString(POISON)).build();
            var forbidden = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(forbidden.statusCode()).isEqualTo(403);
            assertSafeErrorStructure(forbidden.body());
            assertSafe(forbidden.body());
        }
        assertSafe(output.getAll());
    }

    private HttpResponse<String> send(HttpClient client, String path, String accept) throws Exception {
        var request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(5))
                .header("Accept", accept).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + environment.getRequiredProperty("local.server.port") + path);
    }

    private static void assertSafe(String text) {
        assertThat(text).doesNotContain(POISON, "synthetic_secret", "private_table", "password=",
                "java.sql.SQLException", "java.lang.IllegalStateException", "jdbc:postgresql:",
                "at io.github.alejorom");
    }

    private static void assertSafeErrorStructure(String html) {
        assertThat(html).contains("No se pudo completar la solicitud");
        assertThat(html).doesNotContainPattern("(?is)<\\s*(?:style|script)\\b|\\sstyle\\s*=|\\son[a-z]+\\s*=")
                .doesNotContainPattern("(?is)\\s(?:src|href|srcset)\\s*=\\s*[\"']?\\s*(?:https?:|data:|javascript:|//)");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Fixtures {
        @Bean Probe safetyProbe() { return new Probe(); }
    }

    @RestController
    @TestComponent
    @Profile("web-safety-test")
    static class Probe {
        @GetMapping("/safety-probe") String ok() { return "ok"; }
        @GetMapping("/safety-probe/denied") String denied() { throw new AccessDeniedException(POISON); }
        @GetMapping("/safety-probe/failure") String failure() {
            throw new IllegalStateException(POISON, new SQLException(POISON));
        }
        @GetMapping("/safety-probe/checked") String checked() throws Exception { throw new Exception(POISON); }
        @GetMapping("/safety-probe/status") String status() {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, POISON);
        }
    }
}
