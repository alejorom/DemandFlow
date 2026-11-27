package io.github.alejorom.demandflow.tariffs.web;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import io.github.alejorom.demandflow.tariffs.application.CatalogFailureTranslator;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.servlet.ModelAndView;

/** T4: form binding and HTTP outcomes, always outside the service transaction. */
final class CatalogWebSupport {
    private static final Logger LOG = LoggerFactory.getLogger(CatalogWebSupport.class);

    static void bindOnly(WebDataBinder binder, String... fields) {
        var allowed = new HashSet<>(Arrays.asList(fields));
        allowed.add("_csrf");
        binder.setAllowedFields(allowed.toArray(String[]::new));
        binder.setFieldMarkerPrefix(null);
        binder.setFieldDefaultPrefix(null);
    }

    static void rejectUnexpected(HttpServletRequest request, BindingResult errors, String... fields) {
        Set<String> allowed = new HashSet<>(Arrays.asList(fields));
        allowed.add("_csrf");
        if (request.getParameterMap().entrySet().stream()
                .anyMatch(entry -> !allowed.contains(entry.getKey()) || entry.getValue().length != 1)) {
            errors.reject("protectedFields", "La solicitud contiene campos no permitidos o repetidos.");
        }
    }

    static ModelAndView form(String view, BindingResult errors) {
        return new ModelAndView(view, errors.getModel());
    }

    static ModelAndView submit(ModelAndView page, BindingResult errors, CatalogFailureTranslator translator,
                               Supplier<ModelAndView> command) {
        if (errors.hasErrors()) { return page; }
        try {
            // The service proxy must finish commit before the redirect is constructed.
            return command.get();
        } catch (RuntimeException failure) {
            // Preserve the security filter chain and Spring MVC's own HTTP statuses.
            if (failure instanceof AccessDeniedException || failure instanceof ErrorResponse) {
                throw failure;
            }
            var safe = translator.translate(failure);
            var status = switch (safe.code()) {
                case VALIDATION -> HttpStatus.OK;
                case NOT_FOUND, REFERENCE_CONFLICT -> HttpStatus.NOT_FOUND;
                case AUDIT_FAILURE, UNEXPECTED -> HttpStatus.INTERNAL_SERVER_ERROR;
                case PERIOD_OVERLAP, RATE_DUPLICATE, VERSION_CONFLICT, REDUNDANT_STATE,
                        PERIOD_INACTIVE, LOCK_TIMEOUT, DEADLOCK, STATEMENT_CANCELLED -> HttpStatus.CONFLICT;
            };
            if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
                LOG.error("Unexpected web request failure");
            }
            if (status == HttpStatus.NOT_FOUND || status == HttpStatus.INTERNAL_SERVER_ERROR) {
                return new ModelAndView("error", java.util.Map.of(), status);
            }
            errors.reject(safe.code().name(), safe.message() + (status == HttpStatus.CONFLICT
                    ? " Recarga los datos antes de volver a intentarlo." : ""));
            page.setStatus(status);
            return page; // Keep the original BindingResult/DTO, including the submitted version.
        }
    }

    private CatalogWebSupport() { }
}
