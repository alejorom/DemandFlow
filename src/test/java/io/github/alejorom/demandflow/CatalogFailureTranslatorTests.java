package io.github.alejorom.demandflow;

import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.util.stream.Stream;

import io.github.alejorom.demandflow.tariffs.application.CatalogFailureTranslator;
import io.github.alejorom.demandflow.tariffs.application.CatalogFailureTranslator.Code;
import io.github.alejorom.demandflow.tariffs.domain.CatalogConflictException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import org.hibernate.exception.GenericJDBCException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CatalogFailureTranslatorTests {
    private static final String UNTRUSTED = "select secret from table; password=synthetic; JDBC trace ex_tariff_period_dates";
    private final CatalogFailureTranslator translator = new CatalogFailureTranslator();

    @ParameterizedTest
    @MethodSource("databaseErrors")
    void sqlStateAndStructuredConstraintMustMatchWithoutInspectingMessages(String state, String constraint, Code expected) {
        var error = postgresError(state, constraint);
        var translated = translator.translate(new RuntimeException(UNTRUSTED, error));
        assertThat(translated.code()).isEqualTo(expected);
        assertSafe(translated);
    }

    static Stream<Arguments> databaseErrors() {
        return Stream.of(
                Arguments.of("23P01", "ex_tariff_period_dates", Code.PERIOD_OVERLAP),
                Arguments.of("23P01", "other_exclusion", Code.UNEXPECTED),
                Arguments.of("23P01", null, Code.UNEXPECTED),
                Arguments.of("23505", "uq_hourly_rate_period_type", Code.RATE_DUPLICATE),
                Arguments.of("23505", "other_unique", Code.UNEXPECTED),
                Arguments.of("23505", "ex_tariff_period_dates", Code.UNEXPECTED),
                Arguments.of("23503", "fk_hourly_rate_period", Code.REFERENCE_CONFLICT),
                Arguments.of("23503", "fk_hourly_rate_type", Code.REFERENCE_CONFLICT),
                Arguments.of("23503", "other_fk", Code.UNEXPECTED),
                Arguments.of("23514", "uq_hourly_rate_period_type", Code.UNEXPECTED),
                Arguments.of("55P03", null, Code.LOCK_TIMEOUT),
                Arguments.of("40P01", null, Code.DEADLOCK),
                Arguments.of("57014", null, Code.STATEMENT_CANCELLED));
    }

    @ParameterizedTest
    @EnumSource(CatalogConflictException.Reason.class)
    void businessConflictsUseExplicitReasons(CatalogConflictException.Reason reason) {
        var failure = translator.translate(new CatalogConflictException(reason));
        assertThat(failure.code().name()).isEqualTo(reason.name());
        assertSafe(failure);
    }

    @Test
    void wrappersBatchesUnknownErrorsAndCyclesCannotLeakOrHideContext() {
        assertCode(new OptimisticLockingFailureException(UNTRUSTED), Code.VERSION_CONFLICT);
        assertCode(new OptimisticLockException(UNTRUSTED), Code.VERSION_CONFLICT);
        assertCode(new EntityNotFoundException(UNTRUSTED), Code.NOT_FOUND);
        assertCode(new IllegalArgumentException(UNTRUSTED), Code.VALIDATION);
        assertCode(new IllegalStateException(UNTRUSTED), Code.UNEXPECTED);
        assertCode(null, Code.UNEXPECTED);
        assertCode(new jakarta.persistence.PersistenceException(new IllegalArgumentException(UNTRUSTED)), Code.UNEXPECTED);
        assertCode(new GenericJDBCException(UNTRUSTED, postgresError("23505", "uq_catalog_audit_mutation_field"),
                "insert into demandflow.catalog_audit_event (field) values (?)"), Code.AUDIT_FAILURE);
        assertCode(new GenericJDBCException(UNTRUSTED, new SQLException(UNTRUSTED, "P0001"),
                "insert into demandflow.catalog_audit_event_extra (field) values (?)"), Code.UNEXPECTED);
        for (var state : new String[]{"55P03", "40P01", "57014"}) {
            Code expected = switch (state) {
                case "55P03" -> Code.LOCK_TIMEOUT;
                case "40P01" -> Code.DEADLOCK;
                default -> Code.STATEMENT_CANCELLED;
            };
            assertCode(new GenericJDBCException(UNTRUSTED, new SQLException(UNTRUSTED, state),
                    "insert into demandflow.catalog_audit_event (field) values (?)"), expected);
        }
        var batch = new BatchUpdateException(UNTRUSTED, "23505", new int[0]);
        batch.setNextException(postgresError("23505", "uq_hourly_rate_period_type"));
        assertCode(batch, Code.RATE_DUPLICATE);
        // A plain JDBC message containing a known name does not provide structured constraint metadata.
        assertCode(new SQLException(UNTRUSTED, "23P01"), Code.UNEXPECTED);
        var a = new RuntimeException(UNTRUSTED); var b = new RuntimeException(UNTRUSTED, a); a.initCause(b);
        assertCode(a, Code.UNEXPECTED);
        assertThat(CatalogFailureTranslator.Failure.class.getRecordComponents()).extracting(java.lang.reflect.RecordComponent::getType)
                .containsExactly(Code.class, String.class);
    }

    @Test
    void translationInsideAnActiveTransactionIsRejected() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        try {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            assertThrows(IllegalStateException.class, () -> translator.translate(new IllegalStateException(UNTRUSTED)));
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private void assertCode(Throwable input, Code code) {
        var result = translator.translate(input);
        assertThat(result.code()).isEqualTo(code);
        assertSafe(result);
    }

    private static void assertSafe(CatalogFailureTranslator.Failure failure) {
        assertThat(failure.toString()).doesNotContain("select ", "password", "synthetic", "JDBC", "trace", "ex_tariff", "uq_hourly", "fk_hourly");
    }

    private static PSQLException postgresError(String state, String constraint) {
        String metadata = "SERROR\0C" + state + "\0M" + UNTRUSTED + "\0"
                + (constraint == null ? "" : "n" + constraint + "\0") + "\0";
        return new PSQLException(new ServerErrorMessage(metadata));
    }
}
