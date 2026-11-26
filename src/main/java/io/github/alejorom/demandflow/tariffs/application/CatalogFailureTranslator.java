package io.github.alejorom.demandflow.tariffs.application;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.alejorom.demandflow.tariffs.domain.CatalogConflictException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import org.hibernate.JDBCException;
import org.postgresql.util.PSQLException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** T4/T7; RF-CON-005. Invoked by a caller only after the service proxy has committed or rolled back. */
@Component
public class CatalogFailureTranslator {
    // Only the known Hibernate-generated INSERT target supplies context; never parse raw error messages or values.
    private static final Pattern AUDIT_INSERT = Pattern.compile(
            "\\A\\s*insert\\s+into\\s+demandflow\\.catalog_audit_event\\s*\\(", Pattern.CASE_INSENSITIVE);
    public enum Code {
        PERIOD_OVERLAP, RATE_DUPLICATE, REFERENCE_CONFLICT, VERSION_CONFLICT, REDUNDANT_STATE,
        PERIOD_INACTIVE, LOCK_TIMEOUT, DEADLOCK, STATEMENT_CANCELLED, AUDIT_FAILURE, VALIDATION, NOT_FOUND, UNEXPECTED
    }
    /** Only controlled codes/messages leave this boundary; never SQL, constraint names, causes or input values. */
    public record Failure(Code code, String message) { }

    public Failure translate(Throwable failure) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Classify catalog failures only after transaction completion");
        }
        var causes = causes(failure);
        // Contention still requests manual retry when the statement being cancelled is an audit INSERT.
        for (var cause : causes) {
            if (cause instanceof SQLException sql) {
                if ("55P03".equals(sql.getSQLState())) { return result(Code.LOCK_TIMEOUT); }
                if ("40P01".equals(sql.getSQLState())) { return result(Code.DEADLOCK); }
                if ("57014".equals(sql.getSQLState())) { return result(Code.STATEMENT_CANCELLED); }
            }
        }
        if (causes.stream().anyMatch(c -> c instanceof JDBCException jdbc && jdbc.getSQL() != null
                && AUDIT_INSERT.matcher(jdbc.getSQL()).find())) { return result(Code.AUDIT_FAILURE); }
        if (causes.stream().anyMatch(c -> c instanceof OptimisticLockingFailureException || c instanceof OptimisticLockException)) {
            return result(Code.VERSION_CONFLICT);
        }
        for (var cause : causes) {
            if (cause instanceof SQLException sql) {
                String state = sql.getSQLState();
                if (sql instanceof PSQLException pg && pg.getServerErrorMessage() != null) {
                    String constraint = pg.getServerErrorMessage().getConstraint();
                    if ("23P01".equals(state) && "ex_tariff_period_dates".equals(constraint)) { return result(Code.PERIOD_OVERLAP); }
                    if ("23505".equals(state) && "uq_hourly_rate_period_type".equals(constraint)) { return result(Code.RATE_DUPLICATE); }
                    if ("23503".equals(state) && Set.of("fk_hourly_rate_period", "fk_hourly_rate_type").contains(constraint == null ? "" : constraint)) {
                        return result(Code.REFERENCE_CONFLICT);
                    }
                }
            }
        }
        for (var cause : causes) {
            if (cause instanceof CatalogConflictException conflict) {
                return result(switch (conflict.reason()) {
                    case PERIOD_OVERLAP -> Code.PERIOD_OVERLAP;
                    case RATE_DUPLICATE -> Code.RATE_DUPLICATE;
                    case PERIOD_INACTIVE -> Code.PERIOD_INACTIVE;
                    case REDUNDANT_STATE -> Code.REDUNDANT_STATE;
                });
            }
            if (cause instanceof EntityNotFoundException) { return result(Code.NOT_FOUND); }
        }
        // An unknown persistence error must not become a validation error because an internal cause is IllegalArgumentException.
        if (causes.stream().noneMatch(c -> c instanceof SQLException || c instanceof jakarta.persistence.PersistenceException)
                && causes.stream().anyMatch(IllegalArgumentException.class::isInstance)) { return result(Code.VALIDATION); }
        return result(Code.UNEXPECTED);
    }

    private static List<Throwable> causes(Throwable failure) {
        var result = new ArrayList<Throwable>();
        var pending = new ArrayDeque<Throwable>();
        var visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        if (failure != null) { pending.add(failure); }
        while (!pending.isEmpty()) {
            var current = pending.removeFirst();
            if (!visited.add(current)) { continue; }
            result.add(current);
            if (current.getCause() != null) { pending.add(current.getCause()); }
            if (current instanceof SQLException sql && sql.getNextException() != null) { pending.add(sql.getNextException()); }
        }
        return result;
    }

    private static Failure result(Code code) {
        return new Failure(code, switch (code) {
            case PERIOD_OVERLAP -> "Las fechas se superponen con otro período. Revise el intervalo.";
            case RATE_DUPLICATE -> "Ya existe una tarifa para el período y tipo seleccionados.";
            case REFERENCE_CONFLICT -> "Una referencia del catálogo no está disponible. Recargue los datos.";
            case VERSION_CONFLICT -> "El registro cambió o falta su versión. Recargue antes de continuar.";
            case REDUNDANT_STATE -> "El registro ya tiene el estado solicitado.";
            case PERIOD_INACTIVE -> "El período debe estar activo para crear o reactivar una tarifa.";
            case LOCK_TIMEOUT -> "Se agotó la espera por otro cambio. Recargue y vuelva a intentarlo.";
            case DEADLOCK -> "Se canceló el cambio por operaciones concurrentes. Recargue y vuelva a intentarlo.";
            case STATEMENT_CANCELLED -> "La operación fue cancelada. Recargue y vuelva a intentarlo.";
            case AUDIT_FAILURE -> "No se pudo registrar la auditoría. El cambio no fue guardado.";
            case VALIDATION -> "Los datos del catálogo no son válidos. Revise los campos.";
            case NOT_FOUND -> "El registro solicitado no existe.";
            case UNEXPECTED -> "No se pudo confirmar la operación. Recargue los datos antes de continuar.";
        });
    }
}
