package io.github.alejorom.demandflow;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import io.github.alejorom.demandflow.tariffs.application.HourlyRateService.RateView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static io.github.alejorom.demandflow.CatalogApplicationIT.databaseSnapshot;
import static io.github.alejorom.demandflow.CatalogMvcAssertions.csrfToken;
import static io.github.alejorom.demandflow.CatalogSchemaIT.execute;
import static io.github.alejorom.demandflow.PostgresTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 8.4/T4/T5; TP1/TP6, HR5/HR7/HR8, CA3/CA4. No encompassing test transaction. */
class CatalogHttpIT {
    @TempDir Path temporary;

    @Test void completeHttpJourneyConfirmsAuditAndKeepsRejectedRequestsUnchanged() throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start(); int port = postgres.getMappedPort(5432);
            try (var context = application(port, secrets, "--spring.main.web-application-type=servlet", "--server.port=0")) {
                var mvc = mvc((WebApplicationContext) context);
                String p = generatedSubmit(mvc, "/tariffs/periods/new", periodCreate("<b>Web</b>", "2027"));
                assertScalar(port, secrets, "select count(*) from demandflow.catalog_audit_event", "4");
                assertScalar(port, secrets, "select count(*) from demandflow.hourly_rate", "0");
                String r = generatedSubmit(mvc, "/tariffs/rates/new", rateCreate(id(p), "1"));
                String s = generatedSubmit(mvc, "/tariffs/rates/new", rateCreate(id(p), "2"));
                generatedSubmit(mvc, s, state(s, "deactivate", "0"));
                generatedSubmit(mvc, p, state(p, "deactivate", "0"));
                // Both individual states under the inactive period, via real forms and generated session tokens.
                generatedSubmit(mvc, r + "/edit", rateEdit(r, "0", "13.45"));
                generatedSubmit(mvc, s + "/edit", rateEdit(s, "1", "14.55"));
                assertScalar(port, secrets, "select active || ':' || version from demandflow.tariff_period", "false:1");
                assertScalar(port, secrets, "select amount_cop || ':' || active || ':' || version from demandflow.hourly_rate where type_id=1", "13.45:true:1");
                assertScalar(port, secrets, "select amount_cop || ':' || active || ':' || version from demandflow.hourly_rate where type_id=2", "14.55:false:2");
                assertScalar(port, secrets, "select count(*) from demandflow.catalog_audit_event", "16");
                assertScalar(port, secrets, "select count(*) from demandflow.catalog_audit_event where actor_origin <> 'LOCAL_USER'", "0");
                assertScalar(port, secrets, "select before_value || ':' || after_value from demandflow.catalog_audit_event where rate_id="
                        + id(r) + " and operation='UPDATE' and field='amount_cop'", "12.30:13.45");
                assertScalar(port, secrets, "select before_value || ':' || after_value from demandflow.catalog_audit_event where rate_id="
                        + id(s) + " and operation='UPDATE' and field='amount_cop'", "12.30:14.55");
                for (String path : List.of(r, s)) {
                    var response = mvc.perform(get(path)).andExpect(status().isOk()).andReturn();
                    var rate = (RateView) response.getModelAndView().getModel().get("rate");
                    assertThat(rate.available()).isFalse();
                    assertThat(rate.periodActive()).isFalse();
                    assertThat(rate.periodId().toString()).isEqualTo(id(p));
                    assertThat(response.getResponse().getContentAsString()).contains("&lt;b&gt;Web&lt;/b&gt;", rate.amountCop().toPlainString());
                }

                rejected(mvc, port, secrets, periodEdit(p, "0"), 409);
                rejected(mvc, port, secrets, rateEdit(r, "0", "99.99"), 409);
                rejected(mvc, port, secrets, state(p, "deactivate", "1"), 409);
                rejected(mvc, port, secrets, state(s, "deactivate", "2"), 409);
                rejected(mvc, port, secrets, state(s, "reactivate", "2"), 409);
                rejected(mvc, port, secrets, rateCreate(id(p), "1"), 409);
                rejected(mvc, port, secrets, periodCreate("Overlap", "2027"), 409);
                rejected(mvc, port, secrets, rateEdit(r, "1", "12.340"), 200);
                rejected(mvc, port, secrets, rateEdit(r, "1", "15.55").param("periodId", "99999"), 200);
                rejected(mvc, port, secrets, post(r + "/edit").param("amountCOP", "15.55"), 200);
                rejected(mvc, port, secrets, rateEdit("/tariffs/rates/99999", "0", "15.55"), 404);
                rejected(mvc, port, secrets, periodEdit("/tariffs/periods/99999", "0"), 404);
                var before = databaseSnapshot(port, secrets);
                mvc.perform(rateEdit(r, "1", "99.99")).andExpect(status().isForbidden());
                mvc.perform(rateEdit(r, "1", "99.99").with(csrf().useInvalidToken())).andExpect(status().isForbidden());
                assertThat(databaseSnapshot(port, secrets)).isEqualTo(before);
                // No-op succeeds without a new version or event; the proxy still returns before PRG.
                mvc.perform(rateEdit(r, "1", "13.45").with(csrf())).andExpect(redirectedUrl(r));
                assertThat(databaseSnapshot(port, secrets)).isEqualTo(before);

                generatedSubmit(mvc, p, state(p, "reactivate", "1"));
                generatedSubmit(mvc, s, state(s, "reactivate", "2"));
                generatedSubmit(mvc, p + "/edit", periodEdit(p, "2"));
                assertScalar(port, secrets, "select label || ':' || active || ':' || version from demandflow.tariff_period", "Edited:true:3");
                assertScalar(port, secrets, "select count(*) from demandflow.hourly_rate r join demandflow.tariff_period p on p.id=r.period_id where r.active and p.active", "2");
                assertScalar(port, secrets, "select count(*) from demandflow.catalog_audit_event", "19");
            }
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }

    @Test void everyHttpCommandRollsBackOnAuditBusinessAndDeferredCommitFailures() throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start(); int port = postgres.getMappedPort(5432);
            try (var context = application(port, secrets, "--spring.main.web-application-type=servlet", "--server.port=0")) {
                var mvc = mvc((WebApplicationContext) context);
                String p = saved(mvc, periodCreate("Active", "2027"));
                String q = saved(mvc, periodCreate("Inactive", "2028"));
                String free = saved(mvc, periodCreate("Free", "2029"));
                String r = saved(mvc, rateCreate(id(p), "1"));
                String s = saved(mvc, rateCreate(id(p), "2"));
                saved(mvc, state(q, "deactivate", "0"));
                saved(mvc, state(s, "deactivate", "0"));
                var commands = List.of(
                        new Command("TARIFF_PERIOD", "CREATE", "label", periodCreate("Failed", "2035")),
                        new Command("TARIFF_PERIOD", "CREATE", "start_date", periodCreate("Failed", "2035")),
                        new Command("HOURLY_RATE", "CREATE", "period_id", rateCreate(id(free), "1")),
                        new Command("HOURLY_RATE", "CREATE", "type_id", rateCreate(id(free), "1")),
                        new Command("TARIFF_PERIOD", "UPDATE", "start_date", post(p + "/edit").param("label", "Edited")
                                .param("startDate", "2027-02-01").param("endDate", "2027-12-31").param("expectedVersion", "0")),
                        new Command("TARIFF_PERIOD", "DEACTIVATE", "active", state(p, "deactivate", "0")),
                        new Command("TARIFF_PERIOD", "REACTIVATE", "active", state(q, "reactivate", "1")),
                        new Command("HOURLY_RATE", "UPDATE", "amount_cop", rateEdit(r, "0", "99.99")),
                        new Command("HOURLY_RATE", "DEACTIVATE", "active", state(r, "deactivate", "0")),
                        new Command("HOURLY_RATE", "REACTIVATE", "active", state(s, "reactivate", "1")));
                try (var migrator = connect(port, "demandflow_migrator", secrets.migrator)) {
                    auditBody(migrator, "RETURN NEW;");
                    execute(migrator, "create trigger test_http_audit_failure before insert on demandflow.catalog_audit_event for each row execute function demandflow.test_http_audit_failure()");
                    for (var command : commands) {
                        // Test-owned constants only. The second field proves rollback of an already-inserted event.
                        auditBody(migrator, """
                                IF NEW.entity_type='%s' AND NEW.operation='%s' AND NEW.field='%s' THEN
                                    RAISE EXCEPTION 'isolated HTTP audit failure' USING ERRCODE='P0001';
                                END IF; RETURN NEW;
                                """.formatted(command.entity(), command.operation(), command.field()));
                        rejected(mvc, port, secrets, command.request(), 500);
                    }
                    execute(migrator, "drop trigger test_http_audit_failure on demandflow.catalog_audit_event");
                    execute(migrator, """
                            create function demandflow.test_http_business_failure() returns trigger language plpgsql as $$
                            begin raise exception 'isolated HTTP persistence failure' using errcode='P0001'; end $$
                            """);
                    for (String table : List.of("tariff_period", "hourly_rate")) {
                        execute(migrator, "create trigger test_http_business_failure before insert or update on demandflow."
                                + table + " for each row execute function demandflow.test_http_business_failure()");
                    }
                    for (var command : commands) { rejected(mvc, port, secrets, command.request(), 500); }
                    for (String table : List.of("tariff_period", "hourly_rate")) {
                        execute(migrator, "drop trigger test_http_business_failure on demandflow." + table);
                    }
                    // Commit failure after all business and audit writes, not merely a failing flush.
                    execute(migrator, """
                            create function demandflow.test_http_commit_failure() returns trigger language plpgsql as $$
                            begin
                                if (select count(*) from demandflow.catalog_audit_event where mutation_id=NEW.mutation_id) = 4 then
                                    raise exception 'isolated HTTP commit failure after four events' using errcode='P0001';
                                end if;
                                return NEW;
                            end $$
                            """);
                    execute(migrator, "create constraint trigger test_http_commit_failure after insert on demandflow.catalog_audit_event deferrable initially deferred for each row execute function demandflow.test_http_commit_failure()");
                    rejected(mvc, port, secrets, periodCreate("Commit failed", "2035"), 500);
                    rejected(mvc, port, secrets, rateCreate(id(free), "1"), 500);
                    execute(migrator, "drop trigger test_http_commit_failure on demandflow.catalog_audit_event");
                }
                // Successful retry is explicit and happens only after removing the isolated fault fixture.
                saved(mvc, rateEdit(r, "0", "99.99"));
                assertScalar(port, secrets, "select amount_cop || ':' || version from demandflow.hourly_rate where id=" + id(r), "99.99:1");
            }
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }

    @Test void physicalConstraintsAfterPrechecksReturnSafe409Or404WithoutPartialWrites() throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start(); int port = postgres.getMappedPort(5432);
            try (var context = application(port, secrets, "--spring.main.web-application-type=servlet", "--server.port=0")) {
                var mvc = mvc((WebApplicationContext) context);
                String p = saved(mvc, periodCreate("Existing", "2027"));
                saved(mvc, rateCreate(id(p), "1"));
                try (var migrator = connect(port, "demandflow_migrator", secrets.migrator)) {
                    // Inject conflicting physical values only in this disposable DB, after application prechecks.
                    // PostgreSQL's real exclusion/UNIQUE/FK, not a fabricated SQLException, then rejects the write.
                    execute(migrator, """
                            create function demandflow.test_http_constraint() returns trigger language plpgsql as $$
                            begin
                                if TG_TABLE_NAME='tariff_period' then
                                    NEW.start_date=DATE '2027-01-01'; NEW.end_date=DATE '2027-12-31';
                                else NEW.type_id=1;
                                end if;
                                return NEW;
                            end $$
                            """);
                    for (String table : List.of("tariff_period", "hourly_rate")) {
                        execute(migrator, "create trigger test_http_constraint before insert on demandflow." + table
                                + " for each row execute function demandflow.test_http_constraint()");
                    }
                    rejected(mvc, port, secrets, periodCreate("Physical overlap", "2028"), 409);
                    rejected(mvc, port, secrets, rateCreate(id(p), "2"), 409);
                    execute(migrator, """
                            create or replace function demandflow.test_http_constraint() returns trigger language plpgsql as $$
                            begin NEW.period_id=9223372036854775807; return NEW; end $$
                            """);
                    rejected(mvc, port, secrets, rateCreate(id(p), "2"), 404);
                }
            }
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }

    private static MockMvc mvc(WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static String id(String path) { return path.substring(path.lastIndexOf('/') + 1); }

    private static MockHttpServletRequestBuilder periodCreate(String label, String year) {
        return post("/tariffs/periods").param("label", label).param("startDate", year + "-01-01").param("endDate", year + "-12-31");
    }

    private static MockHttpServletRequestBuilder periodEdit(String path, String version) {
        return post(path + "/edit").param("label", "Edited").param("startDate", "2027-01-01")
                .param("endDate", "2027-12-31").param("expectedVersion", version);
    }

    private static MockHttpServletRequestBuilder rateCreate(String period, String type) {
        return post("/tariffs/rates").param("periodId", period).param("typeId", type).param("amountCOP", "12.30");
    }

    private static MockHttpServletRequestBuilder rateEdit(String path, String version, String amount) {
        return post(path + "/edit").param("amountCOP", amount).param("expectedVersion", version);
    }

    private static MockHttpServletRequestBuilder state(String path, String operation, String version) {
        return post(path + "/" + operation).param("expectedVersion", version);
    }

    private static String generatedSubmit(MockMvc mvc, String formPath, MockHttpServletRequestBuilder request) throws Exception {
        var page = mvc.perform(get(formPath)).andExpect(status().isOk()).andReturn();
        return redirect(mvc.perform(request.param("_csrf", csrfToken(page.getResponse().getContentAsString()))
                        .session((MockHttpSession) page.getRequest().getSession(false)))
                .andExpect(status().is3xxRedirection()).andReturn());
    }

    private static String saved(MockMvc mvc, MockHttpServletRequestBuilder request) throws Exception {
        return redirect(mvc.perform(request.with(csrf())).andExpect(status().is3xxRedirection()).andReturn());
    }

    private static String redirect(MvcResult result) {
        assertThat(result.getResponse().getRedirectedUrl()).isNotNull();
        return result.getResponse().getRedirectedUrl();
    }

    private static void rejected(MockMvc mvc, int port, Secrets secrets, MockHttpServletRequestBuilder request, int status) throws Exception {
        var before = databaseSnapshot(port, secrets);
        var result = mvc.perform(request.with(csrf())).andExpect(status().is(status))
                .andExpect(header().doesNotExist("Location")).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("SQLException", "SQLSTATE", "P0001", "isolated HTTP", "ex_tariff", "uq_hourly", "fk_hourly");
        assertThat(databaseSnapshot(port, secrets)).as("committed rows, versions and all events after rejected HTTP")
                .isEqualTo(before);
        if (status == 409) { assertThat(result.getResponse().getContentAsString()).contains("Recarga los datos"); }
        if (status == 500) { assertThat(result.getModelAndView().getViewName()).isEqualTo("error"); }
    }

    private static void assertScalar(int port, Secrets secrets, String sql, String expected) throws Exception {
        try (var independent = connect(port, "demandflow_app", secrets.runtime)) {
            assertThat(scalar(independent, sql)).isEqualTo(expected);
        }
    }

    private static void auditBody(Connection migrator, String body) throws Exception {
        execute(migrator, "create or replace function demandflow.test_http_audit_failure() returns trigger language plpgsql as $$ begin " + body + " end $$");
    }

    private record Command(String entity, String operation, String field, MockHttpServletRequestBuilder request) { }
}
