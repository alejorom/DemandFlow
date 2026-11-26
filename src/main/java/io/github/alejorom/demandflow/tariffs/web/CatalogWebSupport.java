package io.github.alejorom.demandflow.tariffs.web;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import io.github.alejorom.demandflow.tariffs.application.CatalogFailureTranslator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.servlet.ModelAndView;

/** Shared form boundary, not an application transaction or a general HTTP exception adapter. */
final class CatalogWebSupport {
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

    static ModelAndView conflict(ModelAndView page, BindingResult errors, RuntimeException failure,
                                 CatalogFailureTranslator translator) {
        var safe = translator.translate(failure); // Outside the completed service transaction.
        errors.reject(safe.code().name(), safe.message());
        page.setStatus(HttpStatus.CONFLICT);
        return page;
    }

    private CatalogWebSupport() { }
}
