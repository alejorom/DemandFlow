package io.github.alejorom.demandflow;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.github.alejorom.demandflow.audit.application.CatalogAuditWriter;
import io.github.alejorom.demandflow.audit.application.CatalogAuditWriter.ActorOrigin;
import io.github.alejorom.demandflow.audit.application.CatalogAuditWriter.Operation;
import io.github.alejorom.demandflow.audit.application.CatalogAuditWriter.PeriodState;
import io.github.alejorom.demandflow.audit.application.CatalogAuditWriter.RateState;
import io.github.alejorom.demandflow.audit.persistence.CatalogAuditEvent;
import io.github.alejorom.demandflow.audit.persistence.CatalogAuditInsert;
import io.github.alejorom.demandflow.tariffs.domain.HourlyRate;
import io.github.alejorom.demandflow.tariffs.domain.TariffPeriod;
import io.github.alejorom.demandflow.tariffs.persistence.HourlyRateRepository;
import io.github.alejorom.demandflow.tariffs.persistence.RequirementTypeRepository;
import io.github.alejorom.demandflow.tariffs.persistence.TariffPeriodRepository;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static io.github.alejorom.demandflow.CatalogSchemaIT.rejected;
import static io.github.alejorom.demandflow.CatalogSchemaIT.execute;
import static io.github.alejorom.demandflow.PostgresTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** T1/T4/T5/T8: real writer, runtime role and independent committed observations. */
class CatalogAuditWriterIT {
    @TempDir Path temporary;

    @Test
    void runtimeWriterCommitsExactMetadataAndPreservesSuccessiveEventsWithAppendOnlyPermissions() throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            try (var context = application(port, secrets)) {
                var writer = context.getBean(CatalogAuditWriter.class);
                var periods = context.getBean(TariffPeriodRepository.class);
                var rates = context.getBean(HourlyRateRepository.class);
                var types = context.getBean(RequirementTypeRepository.class);
                var factory = context.getBean(EntityManagerFactory.class);
                var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneOffset.UTC);
                Instant started = Instant.now();
                Long rateId = tx.execute(status -> {
                    var period = periods.save(new TariffPeriod("Normalizada", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31)));
                    writer.recordPeriod(period.getId(), Operation.CREATE, null, snapshot(period), ActorOrigin.LOCAL_USER);
                    var rate = rates.save(new HourlyRate(period, types.findById((short) 1).orElseThrow(), new BigDecimal("12.30")));
                    writer.recordRate(rate.getId(), Operation.CREATE, null, snapshot(rate), ActorOrigin.SYSTEM);
                    return rate.getId();
                });
                List<AuditRow> original;
                try (var runtime = connect(port, "demandflow_app", secrets.runtime)) {
                    original = readRows(runtime);
                    assertThat(original).hasSize(8);
                    assertThat(scalar(runtime, "select current_user")).isEqualTo("demandflow_app");
                }
                for (String amount : new String[] {"99.99", "100.01"}) {
                    tx.executeWithoutResult(status -> {
                        var rate = rates.findById(rateId).orElseThrow();
                        var before = snapshot(rate);
                        rate.correctAmount(new BigDecimal(amount));
                        EntityManagerFactoryUtils.getTransactionalEntityManager(factory).flush();
                        writer.recordRate(rateId, Operation.UPDATE, before, snapshot(rate), ActorOrigin.LOCAL_USER);
                    });
                }
                // Compare all mapped values, including the exact Instant, with an independent JDBC read after commit.
                List<AuditRow> mapped = tx.execute(status -> EntityManagerFactoryUtils.getTransactionalEntityManager(factory)
                        .createQuery("select e from CatalogAuditEvent e order by e.id", CatalogAuditEvent.class)
                        .getResultList().stream().map(AuditRow::from).toList());
                try (var runtime = connect(port, "demandflow_app", secrets.runtime)) {
                    var actual = readRows(runtime);
                    assertThat(actual).isEqualTo(mapped).hasSize(10);
                    assertThat(actual.subList(0, 8)).isEqualTo(original);
                    assertThat(actual).allSatisfy(row -> {
                        assertThat(row.instant()).isBetween(started.minusNanos(1000), Instant.now());
                        assertThat(row.instant().getNano() % 1000).isZero();
                        assertThat(row.mutation()).isNotNull();
                        assertThat(row.field()).isNotEqualTo("version");
                    });
                    assertThat(actual.subList(0, 4)).allSatisfy(row -> {
                        assertThat(row.mutation()).isEqualTo(actual.getFirst().mutation());
                        assertThat(row.instant()).isEqualTo(actual.getFirst().instant());
                        assertThat(row.origin()).isEqualTo("LOCAL_USER");
                        assertThat(row.entity()).isEqualTo("TARIFF_PERIOD");
                        assertThat(row.before()).isNull();
                    });
                    assertThat(actual.subList(4, 8)).allSatisfy(row -> {
                        assertThat(row.mutation()).isEqualTo(actual.get(4).mutation());
                        assertThat(row.instant()).isEqualTo(actual.get(4).instant());
                        assertThat(row.origin()).isEqualTo("SYSTEM");
                        assertThat(row.entity()).isEqualTo("HOURLY_RATE");
                    });
                    assertThat(actual).extracting(AuditRow::mutation).containsOnly(actual.get(0).mutation(),
                            actual.get(4).mutation(), actual.get(8).mutation(), actual.get(9).mutation());
                    assertThat(actual.stream().map(AuditRow::mutation).distinct()).hasSize(4);
                    assertThat(actual.subList(8, 10)).extracting(AuditRow::before).containsExactly("12.30", "99.99");
                    assertThat(actual.subList(8, 10)).extracting(AuditRow::after).containsExactly("99.99", "100.01");
                    assertThat(scalar(runtime, "select amount_cop || ':' || version from demandflow.hourly_rate"))
                            .isEqualTo("100.01:2");
                    rejected(runtime, "42501", "update demandflow.catalog_audit_event set after_value='alterado'");
                    rejected(runtime, "42501", "delete from demandflow.catalog_audit_event");
                    rejected(runtime, "42501", "truncate demandflow.catalog_audit_event");
                    assertThat(readRows(runtime)).isEqualTo(actual);
                }
            }
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }

    @Test
    void callerTransactionRollsBackBusinessAndEveryAuditRowOnInitialIntermediateBusinessAndCommitFailures() throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            try (var context = application(port, secrets)) {
                var writer = context.getBean(CatalogAuditWriter.class);
                var insert = context.getBean(CatalogAuditInsert.class);
                var periods = context.getBean(TariffPeriodRepository.class);
                var rates = context.getBean(HourlyRateRepository.class);
                var types = context.getBean(RequirementTypeRepository.class);
                var factory = context.getBean(EntityManagerFactory.class);
                var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                assertThrows(IllegalTransactionStateException.class, () -> writer.recordPeriod(1L,
                        Operation.CREATE, null, AuditWriterTests.period("Sin transaccion", true), ActorOrigin.LOCAL_USER));
                assertThrows(IllegalTransactionStateException.class, () -> insert.append(new CatalogAuditEvent(
                        UUID.randomUUID(), "TARIFF_PERIOD", 1L, null, "CREATE", "label", null,
                        "Sin transaccion", Instant.now(), "LOCAL_USER")));
                Long baseId = tx.execute(status -> periods.save(new TariffPeriod("Base",
                        LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31))).getId());
                installIsolatedFailureTriggers(port, secrets);

                // IDENTITY sends each insert immediately. The trigger itself proves whether a prior row exists.
                for (String field : new String[] {"label", "start_date", "period_id", "type_id"}) {
                    var failure = assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(status -> {
                        setFailure(factory, field);
                        if (field.equals("label") || field.equals("start_date")) {
                            var period = periods.save(new TariffPeriod("Fallida", LocalDate.of(2030, 1, 1), LocalDate.of(2030, 12, 31)));
                            writer.recordPeriod(period.getId(), Operation.CREATE, null, snapshot(period), ActorOrigin.LOCAL_USER);
                        } else {
                            var rate = rates.save(new HourlyRate(periods.findById(baseId).orElseThrow(),
                                    types.findById((short) 1).orElseThrow(), new BigDecimal("12.30")));
                            writer.recordRate(rate.getId(), Operation.CREATE, null, snapshot(rate), ActorOrigin.LOCAL_USER);
                        }
                    }));
                    assertSqlFailure(failure, "P0001", field.equals("label") || field.equals("period_id")
                            ? "isolated first audit failure" : "isolated middle audit failure");
                    assertBaseline(port, secrets, false);
                }

                // Existing-row edits must also roll back their version and previously sent audit fields.
                var editFailure = assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(status -> {
                    setFailure(factory, "start_date");
                    var period = periods.findById(baseId).orElseThrow();
                    var before = snapshot(period);
                    period.edit("Correccion fallida", LocalDate.of(2027, 2, 1), period.getEndDate());
                    EntityManagerFactoryUtils.getTransactionalEntityManager(factory).flush();
                    writer.recordPeriod(baseId, Operation.UPDATE, before, snapshot(period), ActorOrigin.LOCAL_USER);
                }));
                assertSqlFailure(editFailure, "P0001", "isolated middle audit failure");
                assertBaseline(port, secrets, false);
                var stateFailure = assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(status -> {
                    setFailure(factory, "active");
                    var period = periods.findById(baseId).orElseThrow();
                    var before = snapshot(period);
                    period.deactivate();
                    EntityManagerFactoryUtils.getTransactionalEntityManager(factory).flush();
                    writer.recordPeriod(baseId, Operation.DEACTIVATE, before, snapshot(period), ActorOrigin.LOCAL_USER);
                }));
                assertSqlFailure(stateFailure, "P0001", "isolated first audit failure");
                assertBaseline(port, secrets, false);

                Long rateId = tx.execute(status -> rates.save(new HourlyRate(periods.findById(baseId).orElseThrow(),
                        types.findById((short) 1).orElseThrow(), new BigDecimal("12.30"))).getId());
                var correctionFailure = assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(status -> {
                    setFailure(factory, "amount_cop");
                    var rate = rates.findById(rateId).orElseThrow();
                    var before = snapshot(rate);
                    rate.correctAmount(new BigDecimal("99.99"));
                    EntityManagerFactoryUtils.getTransactionalEntityManager(factory).flush();
                    writer.recordRate(rateId, Operation.UPDATE, before, snapshot(rate), ActorOrigin.LOCAL_USER);
                }));
                assertSqlFailure(correctionFailure, "P0001", "isolated first audit failure");
                assertBaseline(port, secrets, true);

                var businessFailure = assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(status -> {
                    var period = periods.save(new TariffPeriod("Antes del conflicto", LocalDate.of(2030, 1, 1), LocalDate.of(2030, 12, 31)));
                    writer.recordPeriod(period.getId(), Operation.CREATE, null, snapshot(period), ActorOrigin.LOCAL_USER);
                    assertAuditCountInCaller(factory, 4L);
                    periods.save(new TariffPeriod("Solapado", period.getStartDate(), period.getEndDate()));
                }));
                assertSqlFailure(businessFailure, "23P01", "ex_tariff_period_dates");
                assertBaseline(port, secrets, true);

                var commitFailure = assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(status -> {
                    setFailure(factory, "commit");
                    var period = periods.save(new TariffPeriod("Fallo al confirmar", LocalDate.of(2030, 1, 1), LocalDate.of(2030, 12, 31)));
                    writer.recordPeriod(period.getId(), Operation.CREATE, null, snapshot(period), ActorOrigin.LOCAL_USER);
                    assertAuditCountInCaller(factory, 4L);
                    // The deferred database trigger fails only when TransactionTemplate actually commits.
                }));
                assertSqlFailure(commitFailure, "P0001", "isolated audit commit failure");
                assertBaseline(port, secrets, true);

                // SET LOCAL cannot leak to a subsequent caller; the same writer can commit normally.
                tx.executeWithoutResult(status -> {
                    var period = periods.save(new TariffPeriod("Confirmado", LocalDate.of(2030, 1, 1), LocalDate.of(2030, 12, 31)));
                    writer.recordPeriod(period.getId(), Operation.CREATE, null, snapshot(period), ActorOrigin.LOCAL_USER);
                });
                try (var independent = connect(port, "demandflow_app", secrets.runtime)) {
                    assertThat(scalar(independent, "select count(*) from demandflow.tariff_period")).isEqualTo("2");
                    assertThat(readRows(independent)).hasSize(4);
                }
            }
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }

    private static void installIsolatedFailureTriggers(int port, Secrets secrets) throws Exception {
        try (var migrator = connect(port, "demandflow_migrator", secrets.migrator)) {
            execute(migrator, """
                    create function demandflow.test_reject_audit() returns trigger language plpgsql as $$
                    begin
                        if TG_WHEN = 'BEFORE' and NEW.field = current_setting('demandflow.test_fail_field', true) then
                            if exists (select 1 from demandflow.catalog_audit_event where mutation_id=NEW.mutation_id) then
                                raise exception 'isolated middle audit failure' using errcode='P0001';
                            else
                                raise exception 'isolated first audit failure' using errcode='P0001';
                            end if;
                        elsif TG_WHEN = 'AFTER' and NEW.field = 'active'
                                and current_setting('demandflow.test_fail_field', true) = 'commit' then
                            raise exception 'isolated audit commit failure' using errcode='P0001';
                        end if;
                        return NEW;
                    end $$
                    """);
            execute(migrator, "create trigger test_reject_audit before insert on demandflow.catalog_audit_event "
                    + "for each row execute function demandflow.test_reject_audit()");
            execute(migrator, "create constraint trigger test_reject_audit_at_commit after insert on demandflow.catalog_audit_event "
                    + "deferrable initially deferred for each row execute function demandflow.test_reject_audit()");
        }
    }

    private static void setFailure(EntityManagerFactory factory, String field) {
        EntityManagerFactoryUtils.getTransactionalEntityManager(factory)
                .createNativeQuery("select set_config('demandflow.test_fail_field', :field, true)", String.class)
                .setParameter("field", field).getSingleResult();
    }

    private static void assertAuditCountInCaller(EntityManagerFactory factory, long expected) {
        assertThat(EntityManagerFactoryUtils.getTransactionalEntityManager(factory)
                .createQuery("select count(e) from CatalogAuditEvent e", Long.class).getSingleResult()).isEqualTo(expected);
    }

    private static void assertSqlFailure(Throwable failure, String state, String message) {
        Throwable current = failure;
        while (!(current instanceof SQLException) && current.getCause() != null) {
            current = current.getCause();
        }
        assertThat(current).isInstanceOf(SQLException.class).hasMessageContaining(message);
        assertThat(((SQLException) current).getSQLState()).isEqualTo(state);
    }

    private static void assertBaseline(int port, Secrets secrets, boolean rateExists) throws Exception {
        // Always called after TransactionTemplate has completed/rolled back, with a new physical connection.
        try (var independent = connect(port, "demandflow_app", secrets.runtime)) {
            assertThat(scalar(independent, "select count(*) from demandflow.tariff_period")).isEqualTo("1");
            assertThat(scalar(independent, "select label || ':' || start_date || ':' || end_date || ':' || active || ':' || version "
                    + "from demandflow.tariff_period")).isEqualTo("Base:2027-01-01:2027-12-31:true:0");
            assertThat(scalar(independent, "select count(*) from demandflow.hourly_rate")).isEqualTo(rateExists ? "1" : "0");
            if (rateExists) {
                assertThat(scalar(independent, "select amount_cop || ':' || active || ':' || version from demandflow.hourly_rate"))
                        .isEqualTo("12.30:true:0");
            }
            assertThat(readRows(independent)).isEmpty();
        }
    }

    static PeriodState snapshot(TariffPeriod period) {
        return new PeriodState(period.getLabel(), period.getStartDate(), period.getEndDate(), period.isActive());
    }

    static RateState snapshot(HourlyRate rate) {
        return new RateState(rate.getPeriod().getId(), rate.getType().getId(), rate.getAmountCop(), rate.isActive());
    }

    private record AuditRow(Long id, UUID mutation, String entity, Long periodId, Long rateId,
                            String operation, String field, String before, String after, Instant instant, String origin) {
        static AuditRow from(CatalogAuditEvent event) {
            return new AuditRow(event.getId(), event.getMutationId(), event.getEntityType(), event.getPeriodId(),
                    event.getRateId(), event.getOperation(), event.getField(), event.getBeforeValue(), event.getAfterValue(),
                    event.getOccurredAt(), event.getActorOrigin());
        }
    }

    private static List<AuditRow> readRows(Connection connection) throws Exception {
        var rows = new ArrayList<AuditRow>();
        try (var statement = connection.prepareStatement("select * from demandflow.catalog_audit_event order by id");
             var result = statement.executeQuery()) {
            while (result.next()) {
                rows.add(new AuditRow(result.getLong("id"), result.getObject("mutation_id", UUID.class),
                        result.getString("entity_type"), result.getObject("period_id", Long.class),
                        result.getObject("rate_id", Long.class), result.getString("operation"), result.getString("field"),
                        result.getString("before_value"), result.getString("after_value"),
                        result.getTimestamp("occurred_at").toInstant(), result.getString("actor_origin")));
            }
        }
        return rows;
    }
}
