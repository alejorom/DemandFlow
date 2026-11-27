package io.github.alejorom.demandflow;

import java.math.BigDecimal;
import java.util.List;

import io.github.alejorom.demandflow.tariffs.application.CatalogFailureTranslator;
import io.github.alejorom.demandflow.tariffs.application.HourlyRateService;
import io.github.alejorom.demandflow.tariffs.application.HourlyRateService.RateView;
import io.github.alejorom.demandflow.tariffs.application.HourlyRateService.TypeView;
import io.github.alejorom.demandflow.tariffs.application.TariffPeriodService;
import io.github.alejorom.demandflow.tariffs.domain.CatalogConflictException;
import io.github.alejorom.demandflow.tariffs.web.RateController;
import io.github.alejorom.demandflow.tariffs.web.CatalogWebExceptionHandler;
import io.github.alejorom.demandflow.tariffs.web.RateForm;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static io.github.alejorom.demandflow.CatalogMvcAssertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HR1/HR3/HR8/CA2.S3: real MVC/security/rendering, without database or a mock catalog in production. */
@SpringBootTest(properties = {"logging.level.root=ERROR", "debug=false"})
@Import({RateController.class, CatalogFailureTranslator.class, CatalogWebExceptionHandler.class})
class RateMvcTests {
    @Autowired WebApplicationContext context;
    @MockitoBean HourlyRateService rates;
    @MockitoBean TariffPeriodService periods;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(rates.get(1L)).thenReturn(rate(true, true, "12.30", 4L));
        when(periods.list()).thenReturn(List.of(PeriodMvcTests.period("<img src=x onerror=bad()>", true, 0L)));
        when(rates.listTypes()).thenReturn(List.of(new TypeView((short) 1, "Soporte", "Soporte"),
                new TypeView((short) 2, "Feature", "Feature")));
    }

    static RateView rate(boolean active, boolean periodActive, String amount, long version) {
        return new RateView(1L, 1L, "<img src=x onerror=bad()>", (short) 1, "Soporte",
                new BigDecimal(amount), active, periodActive, active && periodActive, version);
    }

    @Test void allReadsShowImmutableReferencesInactiveStateAndAvailabilityWithEscapedLabels() throws Exception {
        when(rates.get(1L)).thenReturn(rate(false, false, "12.30", 4L));
        when(rates.list()).thenReturn(List.of(rate(false, false, "12.30", 4L)));
        for (String route : List.of("", "/new", "/1", "/1/edit")) {
            String html = mvc.perform(get("/tariffs/rates" + route)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            safeHtml(html);
            assertThat(html).contains("&lt;img", "Soporte").doesNotContain("<img");
            if (!route.isEmpty()) csrfToken(html);
            if (route.isEmpty() || route.equals("/1")) assertThat(html).contains("Inactiva", "No disponible", "12.30");
            if (route.equals("/new")) assertThat(html).contains("Feature", "value=\"1\"", "value=\"2\"");
            if (route.equals("/1/edit")) assertThat(html).contains("inmutable")
                    .doesNotContain("name=\"periodId\"", "name=\"typeId\"", "name=\"active\"");
        }
        verify(rates, never()).create(any(), any(), any());
        verify(rates, never()).correctAmount(any(), any(), any());
        verify(rates, never()).deactivate(any(), any());
        verify(rates, never()).reactivate(any(), any());
    }

    @ParameterizedTest @CsvSource({"1,0.01", "2,9999999999999.99", "1,12.3"})
    void onlyFixedTypesAndExactValidAmountsReachCreate(short type, String value) throws Exception {
        BigDecimal amount = new BigDecimal(value).setScale(2);
        when(rates.create(1L, type, amount)).thenReturn(rate(true, true, amount.toPlainString(), 0L));
        mvc.perform(create().param("typeId", Short.toString(type)).param("amountCOP", value).with(csrf()))
                .andExpect(redirectedUrl("/tariffs/rates/1"));
        verify(rates).create(1L, type, amount);
    }

    @Test void generatedCsrfFieldCanSubmitTheRealForm() throws Exception {
        var page = mvc.perform(get("/tariffs/rates/new")).andReturn();
        when(rates.create(any(), any(), any())).thenReturn(rate(true, true, "12.30", 0L));
        mvc.perform(create().param("typeId", "1").param("amountCOP", "12.30")
                        .param("_csrf", csrfToken(page.getResponse().getContentAsString()))
                        .session((MockHttpSession) page.getRequest().getSession(false)))
                .andExpect(status().is3xxRedirection());
    }

    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings = {"0", "-1", "no-number", "12.345", "12.340", "10000000000000.00", "NaN", "Infinity", " "})
    void invalidAmountsAreNeverRoundedAndRawInputSurvivesBothForms(String value) throws Exception {
        for (boolean editing : List.of(false, true)) {
            var request = editing ? post("/tariffs/rates/1/edit").param("expectedVersion", "4")
                    : create().param("typeId", "1");
            if (value != null) request.param("amountCOP", value);
            var response = mvc.perform(request.with(csrf())).andExpect(status().isOk())
                    .andExpect(model().attributeHasFieldErrors("form", "validAmount")).andReturn();
            assertThat(((RateForm) response.getModelAndView().getModel().get("form")).getAmountCOP()).isEqualTo(value);
            assertThat(response.getResponse().getContentAsString()).doesNotContain("NumberFormatException", "java.math", "SQLSTATE");
        }
        verify(rates, never()).create(any(), any(), any());
        verify(rates, never()).correctAmount(any(), any(), any());
    }

    @ParameterizedTest @CsvSource({",1", "0,1", "-1,1", "bad,1", "1,", "1,0", "1,-1", "1,3", "1,999999"})
    void invalidOrMissingReferencesAreValidatedOnServer(String period, String type) throws Exception {
        var request = post("/tariffs/rates").param("amountCOP", "12.30");
        if (period != null) request.param("periodId", period);
        if (type != null) request.param("typeId", type);
        mvc.perform(request.with(csrf())).andExpect(status().isOk()).andExpect(model().hasErrors());
        verify(rates, never()).create(any(), any(), any());
    }

    @ParameterizedTest @ValueSource(strings = {"periodId", "typeId", "active", "available", "periodActive", "id",
            "version", "actorOrigin", "occurredAt", "mutationId", "beforeValue", "afterValue"})
    void protectedFieldsAreRejectedOnEditAndStateAndNeverControlAudit(String field) throws Exception {
        for (String route : List.of("edit", "deactivate", "reactivate")) {
            var request = post("/tariffs/rates/1/" + route).param("expectedVersion", "4").param(field, "untrusted-secret");
            if (route.equals("edit")) request.param("amountCOP", "13.45");
            String html = mvc.perform(request.with(csrf())).andExpect(status().isOk()).andExpect(model().hasErrors())
                    .andReturn().getResponse().getContentAsString();
            assertThat(html).doesNotContain("untrusted-secret");
        }
        if (!field.equals("periodId") && !field.equals("typeId")) {
            mvc.perform(create().param("typeId", "1").param("amountCOP", "12.30").param(field, "untrusted-secret").with(csrf()))
                    .andExpect(status().isOk()).andExpect(model().hasErrors());
        }
        verify(rates, never()).create(any(), any(), any());
        verify(rates, never()).correctAmount(any(), any(), any());
        verify(rates, never()).deactivate(any(), any());
        verify(rates, never()).reactivate(any(), any());
    }

    @ParameterizedTest @CsvSource({"true,true", "true,false", "false,true", "false,false"})
    void correctionWorksIndependentlyOfBothStatesWithoutBindingAssociations(boolean active, boolean periodActive) throws Exception {
        when(rates.get(1L)).thenReturn(rate(active, periodActive, "12.30", 4L));
        when(rates.correctAmount(1L, 4L, new BigDecimal("13.45"))).thenReturn(rate(active, periodActive, "13.45", 5L));
        mvc.perform(editRequest().with(csrf())).andExpect(redirectedUrl("/tariffs/rates/1"));
        verify(rates).correctAmount(1L, 4L, new BigDecimal("13.45"));
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"bad", "-1"})
    void missingAndInvalidVersionsNeverWrite(String version) throws Exception {
        for (String route : List.of("edit", "deactivate", "reactivate")) {
            var request = post("/tariffs/rates/1/" + route);
            if (route.equals("edit")) request.param("amountCOP", "13.45");
            if (version != null) request.param("expectedVersion", version);
            mvc.perform(request.with(csrf())).andExpect(status().isOk())
                    .andExpect(view().name(route.equals("edit") ? "tariffs/rates/form" : "tariffs/rates/detail"))
                    .andExpect(model().attributeHasFieldErrors("form", "expectedVersion"))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("Recarga el registro")));
        }
        verify(rates, never()).correctAmount(any(), any(), any());
        verify(rates, never()).deactivate(any(), any());
        verify(rates, never()).reactivate(any(), any());
    }

    @Test void knownBusinessConflictsRetainFormAndSubmittedVersionWithoutRetry() throws Exception {
        when(rates.get(1L)).thenReturn(rate(true, false, "99.99", 9L));
        when(rates.correctAmount(any(), any(), any())).thenThrow(new OptimisticLockingFailureException("SELECT password=synthetic"));
        String html = mvc.perform(editRequest().with(csrf())).andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("value=\"4\"", "13.45", "Recargar registro").doesNotContain("SELECT", "password=");
        verify(rates).correctAmount(1L, 4L, new BigDecimal("13.45"));
        when(rates.create(any(), any(), any())).thenThrow(new CatalogConflictException(CatalogConflictException.Reason.RATE_DUPLICATE));
        mvc.perform(create().param("typeId", "1").param("amountCOP", "12.30").with(csrf())).andExpect(status().isConflict());
        when(rates.reactivate(any(), any())).thenThrow(new CatalogConflictException(CatalogConflictException.Reason.PERIOD_INACTIVE));
        mvc.perform(post("/tariffs/rates/1/reactivate").param("expectedVersion", "4").with(csrf())).andExpect(status().isConflict());
        when(rates.deactivate(any(), any())).thenThrow(new CatalogConflictException(CatalogConflictException.Reason.REDUNDANT_STATE));
        mvc.perform(post("/tariffs/rates/1/deactivate").param("expectedVersion", "4").with(csrf())).andExpect(status().isConflict());
    }

    @ParameterizedTest @ValueSource(strings = {"", "/1/edit", "/1/deactivate", "/1/reactivate"})
    void everyPostRejectsAbsentAndInvalidCsrfAndAcceptsValidCsrf(String route) throws Exception {
        when(rates.create(any(), any(), any())).thenReturn(rate(true, true, "12.30", 0L));
        when(rates.correctAmount(any(), any(), any())).thenReturn(rate(true, true, "13.45", 5L));
        when(rates.deactivate(any(), any())).thenReturn(rate(false, true, "12.30", 5L));
        when(rates.reactivate(any(), any())).thenReturn(rate(true, true, "12.30", 5L));
        mvc.perform(action(route)).andExpect(status().isForbidden());
        mvc.perform(action(route).with(csrf().useInvalidToken())).andExpect(status().isForbidden());
        verifyNoInteractions(rates, periods);
        mvc.perform(action(route).with(csrf())).andExpect(status().is3xxRedirection());
    }

    @Test void nonexistentReferencesStaySafeAndThereAreNoTypeOrAuditCrudRoutes() throws Exception {
        when(rates.get(99L)).thenThrow(new EntityNotFoundException("SELECT private"));
        mvc.perform(get("/tariffs/rates/99")).andExpect(status().isNotFound());
        when(rates.create(eq(99L), any(), any())).thenThrow(new EntityNotFoundException("SELECT private"));
        mvc.perform(post("/tariffs/rates").param("periodId", "99").param("typeId", "1").param("amountCOP", "12.30").with(csrf()))
                .andExpect(status().isNotFound()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SELECT"))));
        for (String route : List.of("/tariffs/types", "/tariffs/types/new", "/tariffs/audit", "/tariffs/rates/1/history")) {
            mvc.perform(get(route)).andExpect(status().isNotFound());
            mvc.perform(post(route).with(csrf())).andExpect(status().isNotFound());
        }
    }

    @Test void repeatedParametersAreNotSilentlyChosen() throws Exception {
        mvc.perform(editRequest().param("amountCOP", "99.99").with(csrf()))
                .andExpect(status().isOk()).andExpect(model().hasErrors());
        verify(rates, never()).correctAmount(any(), any(), any());
    }

    private MockHttpServletRequestBuilder create() { return post("/tariffs/rates").param("periodId", "1"); }
    private MockHttpServletRequestBuilder editRequest() {
        return post("/tariffs/rates/1/edit").param("amountCOP", "13.45").param("expectedVersion", "4");
    }
    private MockHttpServletRequestBuilder action(String route) {
        if (route.isEmpty()) return create().param("typeId", "1").param("amountCOP", "12.30");
        if (route.equals("/1/edit")) return editRequest();
        return post("/tariffs/rates" + route).param("expectedVersion", "4");
    }
}
