package io.github.alejorom.demandflow;

import java.nio.file.Path;

import io.github.alejorom.demandflow.tariffs.application.TariffPeriodService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static io.github.alejorom.demandflow.CatalogApplicationIT.databaseSnapshot;
import static io.github.alejorom.demandflow.CatalogMvcAssertions.*;
import static io.github.alejorom.demandflow.PostgresTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HU-001/T6/T8: actual controller -> committed application transaction -> fresh JDBC observations. */
class PeriodWebIT {
    @TempDir Path temporary;

    @Test void periodPagesWriteOnlyValidatedVersionedCommandsWithTransactionalAudit() throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            try (var context = application(port, secrets, "--spring.main.web-application-type=servlet", "--server.port=0")) {
                var web = (WebApplicationContext) context;
                var mvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
                var periods = context.getBean(TariffPeriodService.class);
                var page = mvc.perform(get("/tariffs/periods/new")).andExpect(status().isOk()).andReturn();
                String token = csrfToken(page.getResponse().getContentAsString());
                var created = mvc.perform(post("/tariffs/periods").param("label", "  <b>Especial</b>  ")
                                .param("startDate", "2027-01-01").param("endDate", "2027-12-31")
                                .param("_csrf", token).session((MockHttpSession) page.getRequest().getSession(false)))
                        .andExpect(status().is3xxRedirection()).andReturn();
                String path = created.getResponse().getRedirectedUrl();
                long id = Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
                assertThat(periods.get(id).label()).isEqualTo("<b>Especial</b>");
                try (var connection = connect(port, "demandflow_app", secrets.runtime)) {
                    assertThat(scalar(connection, "select count(*) from demandflow.catalog_audit_event")).isEqualTo("4");
                    assertThat(scalar(connection, "select count(*) from demandflow.hourly_rate")).isEqualTo("0");
                }
                var snapshot = databaseSnapshot(port, secrets);
                for (String route : new String[] {"/tariffs/periods", path, path + "/edit"}) {
                    String html = mvc.perform(get(route)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                    safeHtml(html);
                    assertThat(html).contains("&lt;b&gt;Especial&lt;/b&gt;").doesNotContain("<b>Especial</b>");
                }
                mvc.perform(get("/")).andExpect(status().isNotFound());
                assertThat(databaseSnapshot(port, secrets)).isEqualTo(snapshot);

                mvc.perform(post(path + "/edit").param("label", "Edited").param("startDate", "2028-01-01")
                                .param("endDate", "2028-12-31").param("expectedVersion", "0").with(csrf()))
                        .andExpect(redirectedUrl(path));
                assertThat(periods.get(id).version()).isEqualTo(1L);
                mvc.perform(post(path + "/deactivate").param("expectedVersion", "1").with(csrf()))
                        .andExpect(redirectedUrl(path));
                assertThat(periods.get(id).active()).isFalse();
                mvc.perform(get(path)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Inactivo")));
                snapshot = databaseSnapshot(port, secrets);
                mvc.perform(post(path + "/deactivate").param("expectedVersion", "2").with(csrf()))
                        .andExpect(status().isConflict());
                mvc.perform(post(path + "/reactivate").param("expectedVersion", "1").with(csrf()))
                        .andExpect(status().isConflict());
                mvc.perform(post(path + "/reactivate").with(csrf())).andExpect(status().isOk())
                        .andExpect(view().name("tariffs/periods/detail"))
                        .andExpect(model().attributeHasFieldErrors("form", "expectedVersion"))
                        .andExpect(content().string(org.hamcrest.Matchers.containsString("Recarga el registro")));
                mvc.perform(post(path + "/reactivate").param("expectedVersion", "2"))
                        .andExpect(status().isForbidden());
                mvc.perform(post(path + "/reactivate").param("expectedVersion", "2").with(csrf().useInvalidToken()))
                        .andExpect(status().isForbidden());
                mvc.perform(post(path + "/reactivate").param("expectedVersion", "2").param("actorOrigin", "SYSTEM").with(csrf()))
                        .andExpect(status().isOk()).andExpect(model().hasErrors());
                mvc.perform(post("/tariffs/periods").param("label", "Overlap").param("startDate", "2028-12-31")
                                .param("endDate", "2029-01-01").with(csrf())).andExpect(status().isConflict());
                mvc.perform(post("/tariffs/periods").param("label", "x".repeat(101)).param("startDate", "2029-01-01")
                                .param("endDate", "2029-12-31").with(csrf())).andExpect(status().isOk()).andExpect(model().hasErrors());
                assertThat(databaseSnapshot(port, secrets)).isEqualTo(snapshot);
                mvc.perform(post(path + "/reactivate").param("expectedVersion", "2").with(csrf()))
                        .andExpect(redirectedUrl(path));
                assertThat(periods.get(id).version()).isEqualTo(3L);
                mvc.perform(post("/tariffs/periods").param("label", "Edited").param("startDate", "2029-03-01")
                                .param("endDate", "2029-03-01").with(csrf())).andExpect(status().is3xxRedirection());
                assertThat(periods.list()).hasSize(2);
                mvc.perform(get("/tariffs/periods/99999")).andExpect(status().isNotFound());
            }
        }
    }
}
