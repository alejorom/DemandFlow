package io.github.alejorom.demandflow;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Stream;

import io.github.alejorom.demandflow.tariffs.application.CatalogFailureTranslator;
import io.github.alejorom.demandflow.tariffs.application.HourlyRateService;
import io.github.alejorom.demandflow.tariffs.application.TariffPeriodService;
import io.github.alejorom.demandflow.tariffs.domain.CatalogConflictException;
import io.github.alejorom.demandflow.tariffs.web.CatalogWebExceptionHandler;
import io.github.alejorom.demandflow.tariffs.web.PeriodController;
import io.github.alejorom.demandflow.tariffs.web.RateController;
import io.github.alejorom.demandflow.tariffs.web.PeriodForm;
import io.github.alejorom.demandflow.tariffs.web.RateForm;
import jakarta.persistence.EntityNotFoundException;
import org.hibernate.exception.GenericJDBCException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 8.4/T4: real HTTP boundary with failing service calls, without a database profile. */
@SpringBootTest(properties = {"logging.level.root=ERROR", "debug=false"})
@Import({PeriodController.class, RateController.class, CatalogFailureTranslator.class, CatalogWebExceptionHandler.class})
@ExtendWith(OutputCaptureExtension.class)
class CatalogHttpMvcTests {
    private static final String POISON = "SELECT synthetic_secret password=synthetic SQLException";
    @Autowired WebApplicationContext context;
    @MockitoBean TariffPeriodService periods;
    @MockitoBean HourlyRateService rates;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(periods.get(1L)).thenReturn(PeriodMvcTests.period("Current", true, 9L));
        when(rates.get(1L)).thenReturn(RateMvcTests.rate(true, true, "99.99", 9L));
    }

    static Stream<Arguments> failures() {
        return Stream.of("periods", "rates").flatMap(resource -> Stream.of(
                Arguments.of(resource, new OptimisticLockingFailureException(POISON), 409),
                Arguments.of(resource, new CatalogConflictException(CatalogConflictException.Reason.REDUNDANT_STATE), 409),
                Arguments.of(resource, new CatalogConflictException(CatalogConflictException.Reason.PERIOD_INACTIVE), 409),
                Arguments.of(resource, sql("23P01", "ex_tariff_period_dates"), 409),
                Arguments.of(resource, sql("23505", "uq_hourly_rate_period_type"), 409),
                Arguments.of(resource, sql("23503", "fk_hourly_rate_period"), 404),
                Arguments.of(resource, sql("55P03", null), 409),
                Arguments.of(resource, sql("40P01", null), 409),
                Arguments.of(resource, sql("57014", null), 409),
                Arguments.of(resource, new IllegalArgumentException(POISON), 200),
                Arguments.of(resource, new EntityNotFoundException(POISON), 404),
                Arguments.of(resource, new IllegalStateException(POISON), 500),
                Arguments.of(resource, sql("23505", "unrecognized_constraint"), 500),
                Arguments.of(resource, new GenericJDBCException(POISON, new SQLException(POISON, "P0001"),
                        "insert into demandflow.catalog_audit_event (field) values (?)"), 500)));
    }

    @ParameterizedTest @MethodSource("failures")
    void finalMappingPreservesInputsAndVersionWithoutRetryOrRedirect(String resource, RuntimeException failure, int status) throws Exception {
        when(periods.edit(any(), any(), any(), any(), any())).thenThrow(failure);
        when(rates.correctAmount(any(), any(), any())).thenThrow(failure);
        var request = post("/tariffs/" + resource + "/1/edit").param("expectedVersion", "4").with(csrf());
        if (resource.equals("periods")) {
            request.param("label", "Submitted").param("startDate", "2027-01-01").param("endDate", "2027-12-31");
        } else { request.param("amountCOP", "13.45"); }
        var result = mvc.perform(request).andExpect(status().is(status))
                .andExpect(header().doesNotExist("Location")).andReturn();
        var page = result.getModelAndView();
        String html = result.getResponse().getContentAsString();
        assertThat(html).doesNotContain("SELECT", "synthetic_secret", "password=", "SQLException", "unrecognized_constraint");
        if (status == 200 || status == 409) {
            assertThat(page.getViewName()).isEqualTo("tariffs/" + resource + "/form");
            assertThat(html).contains("value=\"4\"", resource.equals("periods") ? "Submitted" : "13.45");
            Object form = page.getModel().get("form");
            assertThat(resource.equals("periods") ? ((PeriodForm.Edit) form).getExpectedVersion()
                    : ((RateForm.Edit) form).getExpectedVersion()).isEqualTo(4L);
            if (status == 409) { assertThat(html).contains("Recargar registro", "Recarga los datos"); }
        } else { assertThat(page.getViewName()).isEqualTo("error"); }
        if (resource.equals("periods")) {
            verify(periods, times(1)).edit(1L, 4L, "Submitted", PeriodMvcTests.START, PeriodMvcTests.END);
            verifyNoInteractions(rates);
        } else { verify(rates, times(1)).correctAmount(eq(1L), eq(4L), any()); }
    }

    @ParameterizedTest @ValueSource(strings = {"periods", "rates"})
    void accessDenialFromTheServiceRemains403(String resource) throws Exception {
        var failure = new AccessDeniedException(POISON);
        when(periods.edit(any(), any(), any(), any(), any())).thenThrow(failure);
        when(rates.correctAmount(any(), any(), any())).thenThrow(failure);
        mvc.perform(editRequest(resource).with(csrf())).andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @ParameterizedTest @ValueSource(strings = {"periods", "rates"})
    void springErrorResponseRetainsItsStatusInsteadOfBecoming500(String resource) throws Exception {
        var failure = new ResponseStatusException(HttpStatus.GONE, "Controlled Spring status");
        when(periods.edit(any(), any(), any(), any(), any())).thenThrow(failure);
        when(rates.correctAmount(any(), any(), any())).thenThrow(failure);
        var response = mvc.perform(editRequest(resource).with(csrf())).andExpect(status().isGone())
                .andExpect(header().doesNotExist("Location")).andReturn();
        assertThat(response.getResolvedException()).isSameAs(failure);
    }

    @ParameterizedTest @CsvSource({"periods,false", "rates,false", "periods,true", "rates,true"})
    void unexpectedAndAuditFailuresLogOnlyTheFixedMessage(String resource, boolean audit, CapturedOutput output) throws Exception {
        RuntimeException failure = audit
                ? new GenericJDBCException(POISON, new SQLException(POISON, "P0001"),
                        "insert into demandflow.catalog_audit_event (field) values (?)")
                : new IllegalStateException(POISON);
        when(periods.edit(any(), any(), any(), any(), any())).thenThrow(failure);
        when(rates.correctAmount(any(), any(), any())).thenThrow(failure);
        var response = mvc.perform(editRequest(resource).with(csrf()))
                .andExpect(status().isInternalServerError()).andExpect(view().name("error"))
                .andExpect(header().doesNotExist("Location")).andReturn();
        assertThat(response.getResponse().getContentAsString()).contains("No se pudo completar la solicitud")
                .doesNotContain("SELECT", "synthetic_secret", "password=", "SQLException", "Submitted", "13.45");
        var logLines = output.getAll().lines().filter(line -> line.contains("CatalogWebSupport")).toList();
        assertThat(logLines).hasSize(1);
        assertThat(logLines.getFirst()).endsWith("Unexpected web request failure");
        assertThat(output.getAll()).doesNotContain("SELECT", "synthetic_secret", "password=", "SQLException",
                "Submitted", "13.45", "expectedVersion", "GenericJDBCException");
    }

    @ParameterizedTest @ValueSource(strings = {"periods", "rates"})
    void stateConflictsKeepOriginalVersionForBothActions(String resource) throws Exception {
        var failure = new OptimisticLockingFailureException(POISON);
        when(periods.deactivate(any(), any())).thenThrow(failure);
        when(periods.reactivate(any(), any())).thenThrow(failure);
        when(rates.deactivate(any(), any())).thenThrow(failure);
        when(rates.reactivate(any(), any())).thenThrow(failure);
        for (String operation : List.of("deactivate", "reactivate")) {
            var result = mvc.perform(post("/tariffs/" + resource + "/1/" + operation)
                            .param("expectedVersion", "4").with(csrf()))
                    .andExpect(status().isConflict()).andExpect(header().doesNotExist("Location"))
                    .andExpect(view().name("tariffs/" + resource + "/detail")).andReturn();
            assertThat(result.getResponse().getContentAsString()).contains("value=\"4\"", "Recargar registro")
                    .doesNotContain("SELECT", "password=", "synthetic_secret");
        }
        if (resource.equals("periods")) {
            verify(periods, times(1)).deactivate(1L, 4L);
            verify(periods, times(1)).reactivate(1L, 4L);
            verifyNoInteractions(rates);
        } else {
            verify(rates, times(1)).deactivate(1L, 4L);
            verify(rates, times(1)).reactivate(1L, 4L);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"periods", "rates"})
    void creationConflictsKeepValidInputsAndReferenceOptions(String resource) throws Exception {
        when(periods.create(any(), any(), any())).thenThrow(
                new CatalogConflictException(CatalogConflictException.Reason.PERIOD_OVERLAP));
        when(rates.create(any(), any(), any())).thenThrow(
                new CatalogConflictException(CatalogConflictException.Reason.RATE_DUPLICATE));
        when(periods.list()).thenReturn(List.of(PeriodMvcTests.period("Period option", true, 9L)));
        when(rates.listTypes()).thenReturn(List.of(new HourlyRateService.TypeView((short) 1, "Soporte", "Soporte")));
        var request = post("/tariffs/" + resource);
        if (resource.equals("periods")) {
            request.param("label", "Submitted").param("startDate", "2027-01-01").param("endDate", "2027-12-31");
        } else { request.param("periodId", "1").param("typeId", "1").param("amountCOP", "13.45"); }
        var result = mvc.perform(request.with(csrf())).andExpect(status().isConflict())
                .andExpect(header().doesNotExist("Location")).andReturn();
        String html = result.getResponse().getContentAsString();
        assertThat(html).contains("Recarga los datos");
        if (resource.equals("periods")) {
            assertThat(html).contains("Submitted", "2027-01-01", "2027-12-31");
            verify(periods, times(1)).create("Submitted", PeriodMvcTests.START, PeriodMvcTests.END);
        } else {
            assertThat(html).contains("13.45", "Period option", "Soporte", "selected=\"selected\"");
            verify(rates, times(1)).create(eq(1L), eq((short) 1), any());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"periods", "rates"})
    void sharedAdviceMapsMissingReadAndFormContextWithoutChangingSecurity(String resource) throws Exception {
        when(periods.get(99L)).thenThrow(new EntityNotFoundException(POISON));
        when(rates.get(99L)).thenThrow(new EntityNotFoundException(POISON));
        for (String suffix : List.of("", "/edit")) {
            mvc.perform(get("/tariffs/" + resource + "/99" + suffix))
                    .andExpect(status().isNotFound()).andExpect(view().name("error"));
        }
        mvc.perform(post("/tariffs/" + resource + "/99/deactivate").param("expectedVersion", "0").with(csrf()))
                .andExpect(status().isNotFound()).andExpect(header().doesNotExist("Location"));
        mvc.perform(post("/tariffs/" + resource + "/99/deactivate").param("expectedVersion", "0"))
                .andExpect(status().isForbidden());
        verify(periods, never()).deactivate(any(), any());
        verify(rates, never()).deactivate(any(), any());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder editRequest(String resource) {
        var request = post("/tariffs/" + resource + "/1/edit").param("expectedVersion", "4");
        if (resource.equals("periods")) {
            request.param("label", "Submitted").param("startDate", "2027-01-01").param("endDate", "2027-12-31");
        } else { request.param("amountCOP", "13.45"); }
        return request;
    }

    private static RuntimeException sql(String state, String constraint) {
        String metadata = "SERROR\0C" + state + "\0M" + POISON + "\0"
                + (constraint == null ? "" : "n" + constraint + "\0") + "\0";
        return new RuntimeException(POISON, new PSQLException(new ServerErrorMessage(metadata)));
    }
}
