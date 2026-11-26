package io.github.alejorom.demandflow.config;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;

/** Generic fallback only; catalog-specific HTTP outcomes belong to the catalog web layer. */
@ControllerAdvice
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SafeWebExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(SafeWebExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    ModelAndView unexpectedFailure(Exception failure) throws Exception {
        // Let the security filter chain handle access denial and MVC retain its standard statuses.
        if (failure instanceof AccessDeniedException || failure instanceof ErrorResponse
                || AnnotatedElementUtils.hasAnnotation(failure.getClass(), ResponseStatus.class)) {
            throw failure;
        }
        // Do not pass the exception, request, SQL, parameters or credentials to the logger/view.
        LOG.error("Unexpected web request failure");
        return new ModelAndView("error", Map.of(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
