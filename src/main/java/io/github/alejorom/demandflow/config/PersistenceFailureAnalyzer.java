package io.github.alejorom.demandflow.config;

import java.sql.SQLException;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.hibernate.HibernateException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.context.config.ConfigDataLocationNotFoundException;
import org.springframework.boot.context.config.ConfigDataResourceNotFoundException;

public final class PersistenceFailureAnalyzer extends AbstractFailureAnalyzer<Throwable> {
    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, Throwable cause) {
        String category = null;
        Throwable current = cause;
        for (int depth = 0; current != null && depth < 32; depth++, current = current.getCause()) {
            if (current instanceof PersistenceConfiguration.IncompletePersistenceConfiguration
                    || current instanceof ConfigDataLocationNotFoundException
                    || current instanceof ConfigDataResourceNotFoundException) {
                category = "configuration incomplete";
                break;
            }
            // Hibernate can discard the JDBC cause when metadata discovery fails.
            if (current instanceof HibernateException && current.getMessage() != null
                    && current.getMessage().startsWith("Unable to determine Dialect without JDBC metadata")) {
                category = "database connection unavailable or rejected";
                break;
            }
            if (current instanceof FlywayException) {
                category = "migration initialization failed";
            }
            if (current instanceof FlywayValidateException) {
                category = "migration validation failed";
                break;
            }
            if (current instanceof SQLException sql) {
                String state = sql.getSQLState();
                if (state != null && (state.startsWith("08") || state.startsWith("28"))) {
                    category = "database connection unavailable or rejected";
                    break;
                }
                if (category != null && state != null) {
                    category = "migration execution failed";
                }
            }
        }
        if (category == null) {
            return null;
        }
        return new FailureAnalysis("DemandFlow persistence: " + category + ".",
                "Check local database availability, external configuration and migration history. "
                        + "Do not disclose credentials or repair migration history automatically.", rootFailure);
    }
}
