package io.github.alejorom.demandflow;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;

import io.github.alejorom.demandflow.tariffs.application.HourlyRateService;
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

/** HU-002/HR1/HR3/HR8/CA2.S3 with real transactions, fixed references and server-derived audit. */
class RateWebIT {
    @TempDir Path temporary;

    @Test void rateMaintenanceKeepsReferencesStatesPrecisionAndServerAudit() throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            try (var context = application(port, secrets, "--spring.main.web-application-type=servlet", "--server.port=0")) {
                var mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).apply(springSecurity()).build();
                var periods = context.getBean(TariffPeriodService.class);
                var rates = context.getBean(HourlyRateService.class);
                // Period transitions are fixtures through existing services, not the full web journey of 8.4.
                var period = periods.create("<b>Fixture</b>", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));
                var page = mvc.perform(get("/tariffs/rates/new")).andExpect(status().isOk()).andReturn();
                assertThat(page.getResponse().getContentAsString()).contains("Soporte", "Feature", "&lt;b&gt;Fixture&lt;/b&gt;");
                var snapshot = databaseSnapshot(port, secrets);
                mvc.perform(post("/tariffs/rates").param("periodId", period.id().toString()).param("typeId", "1").param("amountCOP", "12.30"))
                        .andExpect(status().isForbidden());
                mvc.perform(post("/tariffs/rates").param("periodId", period.id().toString()).param("typeId", "1").param("amountCOP", "12.30")
                                .with(csrf().useInvalidToken())).andExpect(status().isForbidden());
                mvc.perform(post("/tariffs/rates").param("periodId", period.id().toString()).param("typeId", "3").param("amountCOP", "12.30")
                                .with(csrf())).andExpect(status().isOk()).andExpect(model().hasErrors());
                mvc.perform(post("/tariffs/rates").param("periodId", "99999").param("typeId", "1").param("amountCOP", "12.30")
                                .with(csrf())).andExpect(status().isNotFound());
                mvc.perform(post("/tariffs/rates").param("periodId", period.id().toString()).param("typeId", "1").param("amountCOP", "12.30")
                                .param("actorOrigin", "SYSTEM").with(csrf())).andExpect(status().isOk()).andExpect(model().hasErrors());
                assertThat(databaseSnapshot(port, secrets)).isEqualTo(snapshot);

                var created = mvc.perform(post("/tariffs/rates").param("periodId", period.id().toString()).param("typeId", "1")
                                .param("amountCOP", "12.30").param("_csrf", csrfToken(page.getResponse().getContentAsString()))
                                .session((MockHttpSession) page.getRequest().getSession(false)))
                        .andExpect(status().is3xxRedirection()).andReturn();
                String path = created.getResponse().getRedirectedUrl();
                long id = Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
                mvc.perform(post("/tariffs/rates").param("periodId", period.id().toString()).param("typeId", "2").param("amountCOP", "0.01")
                                .with(csrf())).andExpect(status().is3xxRedirection());
                assertThat(rates.list()).hasSize(2);
                assertThat(rates.listTypes()).extracting(HourlyRateService.TypeView::code).containsExactly("Soporte", "Feature");
                assertThat(rates.get(id).amountCop()).isEqualTo(new BigDecimal("12.30"));
                snapshot = databaseSnapshot(port, secrets);
                for (String route : new String[] {"/tariffs/rates", path, path + "/edit"}) {
                    String html = mvc.perform(get(route)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                    safeHtml(html);
                    assertThat(html).contains("&lt;b&gt;Fixture&lt;/b&gt;").doesNotContain("<b>Fixture</b>");
                }
                assertThat(databaseSnapshot(port, secrets)).isEqualTo(snapshot);
                for (String operation : new String[] {"edit", "deactivate", "reactivate"}) {
                    var absent = post(path + "/" + operation).param("expectedVersion", "0");
                    if (operation.equals("edit")) absent.param("amountCOP", "13.45");
                    mvc.perform(absent).andExpect(status().isForbidden());
                    var invalid = post(path + "/" + operation).param("expectedVersion", "0").with(csrf().useInvalidToken());
                    if (operation.equals("edit")) invalid.param("amountCOP", "13.45");
                    mvc.perform(invalid).andExpect(status().isForbidden());
                    var protectedRequest = post(path + "/" + operation).param("expectedVersion", "0").param("periodId", "99999").with(csrf());
                    if (operation.equals("edit")) protectedRequest.param("amountCOP", "13.45");
                    mvc.perform(protectedRequest).andExpect(status().isOk()).andExpect(model().hasErrors());
                }
                mvc.perform(post(path + "/edit").param("amountCOP", "12.340").param("expectedVersion", "0").with(csrf()))
                        .andExpect(status().isOk()).andExpect(model().hasErrors());
                mvc.perform(post(path + "/edit").param("amountCOP", "13.45").with(csrf())).andExpect(status().isOk())
                        .andExpect(view().name("tariffs/rates/form"))
                        .andExpect(model().attributeHasFieldErrors("form", "expectedVersion"))
                        .andExpect(content().string(org.hamcrest.Matchers.containsString("Recarga el registro")));
                mvc.perform(post(path + "/edit").param("amountCOP", "13.45").param("expectedVersion", "9").with(csrf()))
                        .andExpect(status().isConflict());
                mvc.perform(post("/tariffs/rates").param("periodId", period.id().toString()).param("typeId", "1").param("amountCOP", "99.99")
                                .with(csrf())).andExpect(status().isConflict());
                assertThat(databaseSnapshot(port, secrets)).isEqualTo(snapshot);

                mvc.perform(post(path + "/edit").param("amountCOP", "13.45").param("expectedVersion", "0").with(csrf()))
                        .andExpect(redirectedUrl(path));
                mvc.perform(post(path + "/deactivate").param("expectedVersion", "1").with(csrf())).andExpect(redirectedUrl(path));
                mvc.perform(get(path)).andExpect(content().string(org.hamcrest.Matchers.containsString("Inactiva")));
                snapshot = databaseSnapshot(port, secrets);
                mvc.perform(post("/tariffs/rates").param("periodId", period.id().toString()).param("typeId", "1").param("amountCOP", "99.99")
                                .with(csrf())).andExpect(status().isConflict());
                assertThat(databaseSnapshot(port, secrets)).isEqualTo(snapshot);
                mvc.perform(post(path + "/reactivate").param("expectedVersion", "2").with(csrf())).andExpect(redirectedUrl(path));

                periods.deactivate(period.id(), period.version());
                mvc.perform(post(path + "/edit").param("amountCOP", "14.55").param("expectedVersion", "3").with(csrf()))
                        .andExpect(redirectedUrl(path));
                var corrected = rates.get(id);
                assertThat(corrected.id()).isEqualTo(id);
                assertThat(corrected.periodId()).isEqualTo(period.id());
                assertThat(corrected.typeId()).isEqualTo((short) 1);
                assertThat(corrected.active()).isTrue();
                assertThat(corrected.periodActive()).isFalse();
                assertThat(corrected.available()).isFalse();
                assertThat(corrected.amountCop()).isEqualTo(new BigDecimal("14.55"));
                mvc.perform(post(path + "/deactivate").param("expectedVersion", "4").with(csrf())).andExpect(redirectedUrl(path));
                mvc.perform(post(path + "/edit").param("amountCOP", "15.55").param("expectedVersion", "5").with(csrf()))
                        .andExpect(redirectedUrl(path));
                assertThat(rates.get(id).active()).isFalse();
                assertThat(rates.get(id).available()).isFalse();
                snapshot = databaseSnapshot(port, secrets);
                mvc.perform(post(path + "/reactivate").param("expectedVersion", "6").with(csrf())).andExpect(status().isConflict());
                assertThat(databaseSnapshot(port, secrets)).isEqualTo(snapshot);
                var other = periods.create("Inactive fixture", LocalDate.of(2028, 1, 1), LocalDate.of(2028, 12, 31));
                periods.deactivate(other.id(), other.version());
                snapshot = databaseSnapshot(port, secrets);
                mvc.perform(post("/tariffs/rates").param("periodId", other.id().toString()).param("typeId", "1").param("amountCOP", "12.30")
                                .with(csrf())).andExpect(status().isConflict());
                for (String route : new String[] {"/tariffs/types", "/tariffs/audit", path + "/history"}) {
                    mvc.perform(get(route)).andExpect(status().isNotFound());
                    mvc.perform(post(route).with(csrf())).andExpect(status().isNotFound());
                }
                assertThat(databaseSnapshot(port, secrets)).isEqualTo(snapshot);
                try (var connection = connect(port, "demandflow_app", secrets.runtime)) {
                    assertThat(scalar(connection, "select count(*) from demandflow.catalog_audit_event where actor_origin <> 'LOCAL_USER'"))
                            .isEqualTo("0");
                    assertThat(scalar(connection, "select before_value || ':' || after_value from demandflow.catalog_audit_event "
                            + "where entity_type='HOURLY_RATE' and field='amount_cop' and operation='UPDATE' order by id desc limit 1"))
                            .isEqualTo("14.55:15.55");
                }
            }
        }
    }
}
