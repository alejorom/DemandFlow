package io.github.alejorom.demandflow;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.alejorom.demandflow.tariffs.application.CatalogFailureTranslator;
import io.github.alejorom.demandflow.tariffs.application.CatalogFailureTranslator.Code;
import io.github.alejorom.demandflow.tariffs.application.HourlyRateService;
import io.github.alejorom.demandflow.tariffs.application.TariffPeriodService;
import io.github.alejorom.demandflow.tariffs.domain.HourlyRate;
import io.github.alejorom.demandflow.tariffs.domain.RequirementType;
import io.github.alejorom.demandflow.tariffs.domain.TariffPeriod;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static io.github.alejorom.demandflow.CatalogApplicationIT.databaseSnapshot;
import static io.github.alejorom.demandflow.CatalogConcurrencySupport.backendPid;
import static io.github.alejorom.demandflow.CatalogSchemaIT.execute;
import static io.github.alejorom.demandflow.PostgresTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** T4/T7: translate only outside the failed transaction, then observe rollback independently. */
class CatalogFailureTranslationIT {
    @TempDir Path temporary;

    @Test
    void realConstraintsBusinessRulesAndAuditFailuresAreDistinctAndSafeAfterRollback() throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start(); int port = postgres.getMappedPort(5432);
            try (var context = application(port, secrets)) {
                var translator = context.getBean(CatalogFailureTranslator.class);
                var periods = context.getBean(TariffPeriodService.class);
                var rates = context.getBean(HourlyRateService.class);
                var factory = context.getBean(EntityManagerFactory.class);
                var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                var p = periods.create("Safe", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));
                var r = rates.create(p.id(), (short) 1, new BigDecimal("12.30"));
                check(port, secrets, translator, Code.PERIOD_OVERLAP, () -> tx.executeWithoutResult(status ->
                        EntityManagerFactoryUtils.getTransactionalEntityManager(factory).persist(new TariffPeriod("Conflict", p.startDate(), p.endDate()))));
                check(port, secrets, translator, Code.RATE_DUPLICATE, () -> tx.executeWithoutResult(status -> {
                    var em = EntityManagerFactoryUtils.getTransactionalEntityManager(factory);
                    em.persist(new HourlyRate(em.getReference(TariffPeriod.class, p.id()), em.getReference(RequirementType.class, (short) 1), new BigDecimal("99.99")));
                }));
                for (boolean missingPeriod : new boolean[]{true, false}) {
                    check(port, secrets, translator, Code.REFERENCE_CONFLICT, () -> tx.executeWithoutResult(status -> {
                        var em = EntityManagerFactoryUtils.getTransactionalEntityManager(factory);
                        em.persist(new HourlyRate(em.getReference(TariffPeriod.class, missingPeriod ? Long.MAX_VALUE : p.id()),
                                em.getReference(RequirementType.class, missingPeriod ? (short) 2 : Short.MAX_VALUE), new BigDecimal("99.99")));
                    }));
                }
                check(port, secrets, translator, Code.PERIOD_OVERLAP, () -> periods.create("Conflict", p.startDate(), p.endDate()));
                check(port, secrets, translator, Code.RATE_DUPLICATE, () -> rates.create(p.id(), (short) 1, new BigDecimal("99.99")));
                check(port, secrets, translator, Code.REDUNDANT_STATE, () -> rates.reactivate(r.id(), r.version()));
                check(port, secrets, translator, Code.VERSION_CONFLICT, () -> rates.correctAmount(r.id(), null, new BigDecimal("12.3")));
                check(port, secrets, translator, Code.VERSION_CONFLICT, () -> periods.edit(p.id(), 99L, p.label(), p.startDate(), p.endDate()));
                check(port, secrets, translator, Code.VALIDATION, () -> rates.correctAmount(r.id(), r.version(), new BigDecimal("12.340")));
                check(port, secrets, translator, Code.NOT_FOUND, () -> periods.get(Long.MAX_VALUE));
                periods.deactivate(p.id(), p.version());
                check(port, secrets, translator, Code.PERIOD_INACTIVE, () -> rates.create(p.id(), (short) 2, new BigDecimal("1.00")));
                // Enforce the boundary even if a future caller accidentally attempts classification inside a transaction.
                assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> translator.translate(new RuntimeException("unsafe"))));
                try (var migrator = connect(port, "demandflow_migrator", secrets.migrator)) {
                    execute(migrator, """
                            create function demandflow.test_translation_audit_failure() returns trigger language plpgsql as $$
                            begin raise exception 'synthetic secret SQL trace' using errcode='P0001'; end $$
                            """);
                    execute(migrator, "create trigger test_translation_audit_failure before insert on demandflow.catalog_audit_event "
                            + "for each row execute function demandflow.test_translation_audit_failure()");
                }
                check(port, secrets, translator, Code.AUDIT_FAILURE, () -> rates.correctAmount(r.id(), r.version(), new BigDecimal("99.99")));
                check(port, secrets, translator, Code.AUDIT_FAILURE, () -> periods.create("Audit failure", LocalDate.of(2030, 1, 1), LocalDate.of(2030, 12, 31)));
            }
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }

    @Test
    void realLockTimeoutAndDeadlockRequestManualRetryWithoutChangingConfiguredBounds() throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start(); int port = postgres.getMappedPort(5432);
            try (var context = application(port, secrets)) {
                var translator = context.getBean(CatalogFailureTranslator.class);
                var periods = context.getBean(TariffPeriodService.class);
                var factory = context.getBean(EntityManagerFactory.class);
                var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                var p = periods.create("First", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));
                var q = periods.create("Second", LocalDate.of(2028, 1, 1), LocalDate.of(2028, 12, 31));
                var baseline = databaseSnapshot(port, secrets);
                var pool = Executors.newFixedThreadPool(2);
                try {
                    var pid = new AtomicInteger();
                    try (var holder = connect(port, "demandflow_app", secrets.runtime);
                         var observer = connect(port, "demandflow_app", secrets.runtime)) {
                        assertThat(scalar(holder, "show lock_timeout")).isEqualTo("5s");
                        assertThat(scalar(holder, "show statement_timeout")).isEqualTo("15s");
                        tx.executeWithoutResult(status -> {
                            var em = EntityManagerFactoryUtils.getTransactionalEntityManager(factory);
                            assertThat(em.createNativeQuery("show lock_timeout", String.class).getSingleResult()).isEqualTo("5s");
                            assertThat(em.createNativeQuery("show statement_timeout", String.class).getSingleResult()).isEqualTo("15s");
                        });
                        holder.setAutoCommit(false); lock(holder, p.id());
                        int blocker = backendPid(holder);
                        var waiting = pool.submit(() -> assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(status -> {
                            pid.set(((Number) EntityManagerFactoryUtils.getTransactionalEntityManager(factory)
                                    .createNativeQuery("select pg_backend_pid()", Integer.class).getSingleResult()).intValue());
                            periods.edit(p.id(), p.version(), "Blocked", p.startDate(), p.endDate());
                        })));
                        try (var statement = observer.prepareStatement("select ? = any(pg_blocking_pids(?))")) {
                            statement.setQueryTimeout(2);
                            await().pollDelay(Duration.ZERO).pollInterval(Duration.ofMillis(10)).atMost(Duration.ofSeconds(3)).until(() -> {
                                if (pid.get() == 0) { return false; }
                                statement.setInt(1, blocker); statement.setInt(2, pid.get());
                                try (var rows = statement.executeQuery()) { return rows.next() && rows.getBoolean(1); }
                            });
                        }
                        // Keep the holder locked until the actual 5 s server timeout completes; no retry or timeout override.
                        var failure = waiting.get(8, TimeUnit.SECONDS);
                        assertTranslated(translator, failure, Code.LOCK_TIMEOUT);
                        holder.rollback();
                    }
                    assertThat(databaseSnapshot(port, secrets)).isEqualTo(baseline);
                    var barrier = new CyclicBarrier(2);
                    var a = pool.submit(() -> deadlockParticipant(port, secrets, p.id(), q.id(), barrier));
                    var b = pool.submit(() -> deadlockParticipant(port, secrets, q.id(), p.id(), barrier));
                    var results = java.util.Arrays.asList(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
                    assertThat(results.stream().filter(java.util.Objects::isNull).count()).isEqualTo(1);
                    var victim = results.stream().filter(java.util.Objects::nonNull).findFirst().orElseThrow();
                    assertTranslated(translator, victim, Code.DEADLOCK);
                    assertThat(databaseSnapshot(port, secrets)).isEqualTo(baseline);
                } finally {
                    pool.shutdownNow(); assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
                }
            }
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }

    private static SQLException deadlockParticipant(int port, Secrets secrets, long first, long second, CyclicBarrier barrier) throws Exception {
        // Intentionally reversed JDBC locks are an isolated negative fixture, not the application's period→rate strategy.
        try (var connection = connect(port, "demandflow_app", secrets.runtime)) {
            connection.setAutoCommit(false);
            lock(connection, first); barrier.await(5, TimeUnit.SECONDS);
            try { lock(connection, second); connection.commit(); return null; }
            catch (SQLException failure) { connection.rollback(); return failure; }
        }
    }

    private static void lock(Connection connection, long id) throws Exception {
        try (var statement = connection.prepareStatement("select id from demandflow.tariff_period where id=? for update")) {
            statement.setLong(1, id); try (var rows = statement.executeQuery()) { assertThat(rows.next()).isTrue(); }
        }
    }

    private static void check(int port, Secrets secrets, CatalogFailureTranslator translator, Code code, Runnable command) throws Exception {
        var before = databaseSnapshot(port, secrets);
        var failure = assertThrows(RuntimeException.class, command::run);
        assertTranslated(translator, failure, code);
        assertThat(databaseSnapshot(port, secrets)).isEqualTo(before);
    }

    private static void assertTranslated(CatalogFailureTranslator translator, Throwable failure, Code code) {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        var result = translator.translate(failure);
        assertThat(result.code()).isEqualTo(code);
        assertThat(result.toString()).doesNotContain("synthetic", "secret", "SQL", "trace", "23505", "23P01", "demandflow.", "jdbc:");
    }
}
