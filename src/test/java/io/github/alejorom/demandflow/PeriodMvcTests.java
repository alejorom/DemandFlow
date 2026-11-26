package io.github.alejorom.demandflow;

import java.time.LocalDate;
import java.util.List;

import io.github.alejorom.demandflow.tariffs.application.CatalogFailureTranslator;
import io.github.alejorom.demandflow.tariffs.application.TariffPeriodService;
import io.github.alejorom.demandflow.tariffs.application.TariffPeriodService.PeriodView;
import io.github.alejorom.demandflow.tariffs.domain.CatalogConflictException;
import io.github.alejorom.demandflow.tariffs.web.PeriodController;
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

/** TP1.S1-S5/TP2/TP3/TP5/TP6: real MVC, validation, views and security; service boundary mocked. */
@SpringBootTest(properties = {"logging.level.root=ERROR", "debug=false"})
@Import({PeriodController.class, CatalogFailureTranslator.class})
class PeriodMvcTests {
    @Autowired WebApplicationContext context;
    @MockitoBean TariffPeriodService periods;
    MockMvc mvc;
    static final LocalDate START = LocalDate.of(2027, 1, 1);
    static final LocalDate END = LocalDate.of(2027, 12, 31);

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(periods.get(1L)).thenReturn(period("Especial", true, 4L));
    }

    static PeriodView period(String label, boolean active, Long version) {
        return new PeriodView(1L, label, START, END, active, version);
    }

    @Test void allReadsRenderInactiveRecordsEscapedTextNavigationAndTokensWithoutWrites() throws Exception {
        when(periods.list()).thenReturn(List.of(period("<img src=x onerror=bad()>", false, 4L)));
        when(periods.get(1L)).thenReturn(period("<img src=x onerror=bad()>", false, 4L));
        for (String route : List.of("", "/1", "/new", "/1/edit")) {
            String html = mvc.perform(get("/tariffs/periods" + route)).andExpect(status().isOk())
                    .andExpect(header().string("X-Frame-Options", "DENY"))
                    .andReturn().getResponse().getContentAsString();
            safeHtml(html);
            assertThat(html).contains("/tariffs/rates", "/tariffs/periods");
            if (!route.equals("/new")) { assertThat(html).contains("&lt;img").doesNotContain("<img"); }
            if (!route.isEmpty()) { csrfToken(html); }
        }
        mvc.perform(get("/")).andExpect(status().isNotFound());
        verify(periods, never()).create(anyString(), any(), any());
        verify(periods, never()).edit(any(), any(), anyString(), any(), any());
        verify(periods, never()).deactivate(any(), any());
        verify(periods, never()).reactivate(any(), any());
    }

    @Test void generatedFormTokenWorksAndCreationRedirectsAfterServiceReturns() throws Exception {
        var form = mvc.perform(get("/tariffs/periods/new")).andReturn();
        String token = csrfToken(form.getResponse().getContentAsString());
        when(periods.create("Especial", START, END)).thenReturn(period("Especial", true, 0L));
        mvc.perform(create().param("label", "  Especial  ").param("_csrf", token)
                        .session((MockHttpSession) form.getRequest().getSession(false)))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/tariffs/periods/1"));
        verify(periods).create("Especial", START, END);
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", "\t\n"})
    void missingOrBlankLabelsAreRejectedAndValidDatesSurvive(String label) throws Exception {
        var request = create();
        if (label != null) { request.param("label", label); }
        var result = mvc.perform(request.with(csrf())).andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "validLabel")).andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("2027-01-01", "2027-12-31");
        verify(periods, never()).create(anyString(), any(), any());
    }

    @ParameterizedTest @ValueSource(ints = {100, 101})
    void codePointLimitDoesNotTreatSupplementaryCharactersAsTwo(int count) throws Exception {
        String label = "😀".repeat(count);
        when(periods.create(label, START, END)).thenReturn(period(label, true, 0L));
        var result = mvc.perform(create().param("label", " " + label + " ").with(csrf()));
        if (count == 100) { result.andExpect(status().is3xxRedirection()); verify(periods).create(label, START, END); }
        else { result.andExpect(status().isOk()).andExpect(model().hasErrors()); verify(periods, never()).create(any(), any(), any()); }
    }

    @ParameterizedTest @CsvSource({"2027-01-01,2027-12-31", "2027-01-01,2027-06-30",
            "2027-03-01,2027-03-17", "2027-03-01,2027-03-01", "-4712-01-01,-4712-01-01"})
    void flexibleInclusiveDurationsReachApplication(String start, String end) throws Exception {
        when(periods.create(eq("Especial"), any(), any())).thenReturn(period("Especial", true, 0L));
        mvc.perform(post("/tariffs/periods").param("label", "Especial").param("startDate", start)
                        .param("endDate", end).with(csrf())).andExpect(status().is3xxRedirection());
        verify(periods).create("Especial", LocalDate.parse(start), LocalDate.parse(end));
    }

    @ParameterizedTest @CsvSource({"bad,2027-12-31", "2027-02-29,2027-12-31", ",2027-12-31",
            "2027-01-01,", "2028-01-01,2027-12-31", "-4713-12-31,2027-12-31",
            "+5874897-12-31,+5874897-12-31"})
    void invalidDatesAreRejectedInServer(String start, String end) throws Exception {
        var request = post("/tariffs/periods").param("label", "Especial").with(csrf());
        if (start != null) request.param("startDate", start);
        if (end != null) request.param("endDate", end);
        mvc.perform(request).andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "validDates"));
        verify(periods, never()).create(any(), any(), any());
    }

    @Test void editAndStateActionsPassOnlyExplicitFieldsAndVersion() throws Exception {
        when(periods.edit(1L, 4L, "Especial", START, END)).thenReturn(period("Especial", true, 5L));
        when(periods.deactivate(1L, 4L)).thenReturn(period("Especial", false, 5L));
        when(periods.reactivate(1L, 4L)).thenReturn(period("Especial", true, 5L));
        mvc.perform(editRequest().with(csrf())).andExpect(redirectedUrl("/tariffs/periods/1"));
        for (String action : List.of("deactivate", "reactivate")) {
            mvc.perform(post("/tariffs/periods/1/" + action).param("expectedVersion", "4").with(csrf()))
                    .andExpect(redirectedUrl("/tariffs/periods/1"));
        }
        verify(periods).edit(1L, 4L, "Especial", START, END);
        verify(periods).deactivate(1L, 4L);
        verify(periods).reactivate(1L, 4L);
    }

    @Test void conflictsKeepSubmittedVersionAndInputsWithoutRetryOrRawMessages() throws Exception {
        when(periods.get(1L)).thenReturn(period("Current", true, 9L));
        when(periods.edit(any(), any(), anyString(), any(), any()))
                .thenThrow(new OptimisticLockingFailureException("SELECT secret password=synthetic"));
        String html = mvc.perform(editRequest().with(csrf())).andExpect(status().isConflict()).andReturn()
                .getResponse().getContentAsString();
        assertThat(html).contains("value=\"4\"", "Especial", "Recargar registro")
                .doesNotContain("SELECT", "password=", "OptimisticLockingFailureException");
        verify(periods).edit(1L, 4L, "Especial", START, END);
        when(periods.create(any(), any(), any())).thenThrow(new CatalogConflictException(CatalogConflictException.Reason.PERIOD_OVERLAP));
        mvc.perform(create().param("label", "Especial").with(csrf())).andExpect(status().isConflict());
        when(periods.deactivate(any(), any())).thenThrow(new CatalogConflictException(CatalogConflictException.Reason.REDUNDANT_STATE));
        mvc.perform(post("/tariffs/periods/1/deactivate").param("expectedVersion", "4").with(csrf()))
                .andExpect(status().isConflict());
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"bad", "-1"})
    void missingOrInvalidVersionNeverWritesEvenIfOtherFieldsAreUnchanged(String version) throws Exception {
        var request = post("/tariffs/periods/1/edit").param("label", "Especial")
                .param("startDate", "2027-01-01").param("endDate", "2027-12-31");
        if (version != null) request.param("expectedVersion", version);
        mvc.perform(request.with(csrf())).andExpect(status().isOk())
                .andExpect(view().name("tariffs/periods/form"))
                .andExpect(model().attributeHasFieldErrors("form", "expectedVersion"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Recarga el registro")));
        verify(periods, never()).edit(any(), any(), anyString(), any(), any());
    }

    @ParameterizedTest @ValueSource(strings = {"active", "id", "version", "actorOrigin", "occurredAt", "beforeValue", "label.extra"})
    void protectedParametersAreRejectedBeforeCommands(String field) throws Exception {
        mvc.perform(create().param("label", "Especial").param(field, "synthetic-secret").with(csrf()))
                .andExpect(status().isOk()).andExpect(model().hasErrors());
        mvc.perform(editRequest().param(field, "synthetic-secret").with(csrf()))
                .andExpect(status().isOk()).andExpect(model().hasErrors());
        for (String action : List.of("deactivate", "reactivate")) {
            mvc.perform(post("/tariffs/periods/1/" + action).param("expectedVersion", "4")
                            .param(field, "synthetic-secret").with(csrf()))
                    .andExpect(status().isOk()).andExpect(model().hasErrors());
        }
        verify(periods, never()).create(any(), any(), any());
        verify(periods, never()).edit(any(), any(), any(), any(), any());
        verify(periods, never()).deactivate(any(), any());
        verify(periods, never()).reactivate(any(), any());
    }

    @ParameterizedTest @ValueSource(strings = {"", "/1/edit", "/1/deactivate", "/1/reactivate"})
    void everyPostRequiresValidCsrf(String route) throws Exception {
        when(periods.create(any(), any(), any())).thenReturn(period("Especial", true, 0L));
        when(periods.edit(any(), any(), any(), any(), any())).thenReturn(period("Especial", true, 5L));
        when(periods.deactivate(any(), any())).thenReturn(period("Especial", false, 5L));
        when(periods.reactivate(any(), any())).thenReturn(period("Especial", true, 5L));
        mvc.perform(action(route)).andExpect(status().isForbidden());
        mvc.perform(action(route).with(csrf().useInvalidToken())).andExpect(status().isForbidden());
        verify(periods, never()).create(any(), any(), any());
        verify(periods, never()).edit(any(), any(), any(), any(), any());
        verify(periods, never()).deactivate(any(), any());
        verify(periods, never()).reactivate(any(), any());
        mvc.perform(action(route).with(csrf())).andExpect(status().is3xxRedirection());
    }

    @Test void missingReferenceUsesSafe404() throws Exception {
        when(periods.get(99L)).thenThrow(new EntityNotFoundException("SELECT secret"));
        mvc.perform(get("/tariffs/periods/99")).andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SELECT"))));
    }

    private MockHttpServletRequestBuilder create() {
        return post("/tariffs/periods").param("startDate", "2027-01-01").param("endDate", "2027-12-31");
    }
    private MockHttpServletRequestBuilder editRequest() {
        return post("/tariffs/periods/1/edit").param("label", "Especial").param("startDate", "2027-01-01")
                .param("endDate", "2027-12-31").param("expectedVersion", "4");
    }
    private MockHttpServletRequestBuilder action(String route) {
        if (route.isEmpty()) { return create().param("label", "Especial"); }
        if (route.equals("/1/edit")) { return editRequest(); }
        return post("/tariffs/periods" + route).param("expectedVersion", "4");
    }
}
