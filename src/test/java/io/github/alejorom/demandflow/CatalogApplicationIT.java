package io.github.alejorom.demandflow;

import java.nio.file.Path;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.github.alejorom.demandflow.tariffs.application.TariffPeriodService;
import io.github.alejorom.demandflow.tariffs.application.HourlyRateService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static io.github.alejorom.demandflow.PostgresTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Group 5: real proxied commands; every observation uses a new connection after commit/rollback. */
class CatalogApplicationIT {
    @TempDir Path temporary;

    @Test
    void periodsValidateNormalizeAndAuditFlexibleIntervalsWithoutAutomaticRates() throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            try (var context = application(port, secrets)) {
                var service = context.getBean(TariffPeriodService.class);
                var annual = service.create("  Repetida  ", date("2027-01-01"), date("2027-12-31"));
                var semester = service.create("Repetida", date("2028-01-01"), date("2028-06-30"));
                service.create("Especial", date("2028-07-01"), date("2028-07-13"));
                service.create("Un dia", date("2028-07-14"), date("2028-07-14"));
                service.create("x".repeat(100), date("2028-07-15"), date("2028-07-15"));
                assertThat(annual.label()).isEqualTo("Repetida");
                assertThat(service.list()).hasSize(5);
                assertThat(service.get(annual.id())).isEqualTo(annual);
                try (var independent = connect(port, "demandflow_app", secrets.runtime)) {
                    assertThat(scalar(independent, "select count(*) from demandflow.hourly_rate")).isEqualTo("0");
                    assertThat(scalar(independent, "select count(*) from demandflow.catalog_audit_event")).isEqualTo("20");
                }
                // Identical, contained, containing, left/right partial, shared inclusive endpoint.
                for (var interval : List.of(new String[]{"2027-01-01", "2027-12-31"},
                        new String[]{"2027-02-01", "2027-03-01"}, new String[]{"2026-01-01", "2028-01-01"},
                        new String[]{"2026-12-31", "2027-01-02"}, new String[]{"2027-12-30", "2028-01-02"},
                        new String[]{"2026-12-01", "2027-01-01"})) {
                    assertThrows(IllegalStateException.class, () -> service.create("Solapada", date(interval[0]), date(interval[1])));
                    assertThrows(IllegalStateException.class, () -> service.edit(semester.id(), semester.version(),
                            "Solapada", date(interval[0]), date(interval[1])));
                }
                for (String label : new String[]{null, "", "  ", "x".repeat(101)}) {
                    assertThrows(IllegalArgumentException.class, () -> service.create(label, date("2030-01-01"), date("2030-02-01")));
                }
                assertThrows(IllegalArgumentException.class, () -> service.create("Sin fecha", null, date("2030-02-01")));
                assertThrows(IllegalArgumentException.class, () -> service.create("Sin fecha", date("2030-01-01"), null));
                assertThrows(IllegalArgumentException.class, () -> service.create("Invertida", date("2030-03-01"), date("2030-02-01")));
                var edited = service.edit(annual.id(), annual.version(), "  Editada  ", annual.startDate(), annual.endDate());
                assertThat(edited.version()).isEqualTo(1L);
                var dates = service.edit(edited.id(), edited.version(), edited.label(), date("2027-02-01"), date("2027-11-30"));
                assertThat(dates.version()).isEqualTo(2L);
                try (var independent = connect(port, "demandflow_app", secrets.runtime)) {
                    assertThat(scalar(independent, "select count(*) from demandflow.tariff_period")).isEqualTo("5");
                    assertThat(scalar(independent, "select count(*) from demandflow.catalog_audit_event")).isEqualTo("23");
                    assertThat(scalar(independent, "select before_value || ':' || after_value from demandflow.catalog_audit_event "
                            + "where operation='UPDATE' and field='label'")).isEqualTo("Repetida:Editada");
                    assertThat(scalar(independent, "select start_date || ':' || end_date || ':' || version from demandflow.tariff_period "
                            + "where id=" + annual.id())).isEqualTo("2027-02-01:2027-11-30:2");
                }
            }
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }

    private static LocalDate date(String value) { return LocalDate.parse(value); }

    @Test
    void periodStatePreservesIndividualRatesAndRejectsRedundantTransitions() throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            try (var context = application(port, secrets)) {
                var service = context.getBean(TariffPeriodService.class);
                var period = service.create("Estado", date("2027-01-01"), date("2027-12-31"));
                // Fixture only: group 5.3 has not implemented the rate creation command yet.
                try (var runtime = connect(port, "demandflow_app", secrets.runtime);
                     var statement = runtime.prepareStatement("insert into demandflow.hourly_rate "
                             + "(period_id,type_id,amount_cop,active,version) values (?,1,12.30,true,0),(?,2,45.60,false,0)")) {
                    statement.setLong(1, period.id());
                    statement.setLong(2, period.id());
                    statement.executeUpdate();
                }
                assertRateStates(port, secrets, "1");
                var inactive = service.deactivate(period.id(), period.version());
                assertThat(inactive.active()).isFalse();
                assertThat(inactive.version()).isEqualTo(1L);
                assertThat(service.list()).containsExactly(inactive);
                assertRateStates(port, secrets, "0");
                assertThrows(IllegalStateException.class, () -> service.deactivate(inactive.id(), inactive.version()));
                for (var interval : List.of(new String[]{"2027-01-01", "2027-12-31"},
                        new String[]{"2027-02-01", "2027-03-01"}, new String[]{"2026-01-01", "2028-01-01"},
                        new String[]{"2026-12-31", "2027-01-02"}, new String[]{"2027-12-30", "2028-01-02"},
                        new String[]{"2026-12-01", "2027-01-01"})) {
                    assertThrows(IllegalStateException.class, () -> service.create("Inactiva reservada", date(interval[0]), date(interval[1])));
                }
                var active = service.reactivate(inactive.id(), inactive.version());
                assertThat(active.active()).isTrue();
                assertThat(active.version()).isEqualTo(2L);
                assertRateStates(port, secrets, "1");
                assertThrows(IllegalStateException.class, () -> service.reactivate(active.id(), active.version()));
                assertThat(service.list()).containsExactly(active);
                try (var independent = connect(port, "demandflow_app", secrets.runtime)) {
                    assertThat(scalar(independent, "select count(*) from demandflow.catalog_audit_event")).isEqualTo("6");
                    assertThat(scalar(independent, "select count(*) from demandflow.catalog_audit_event where entity_type='HOURLY_RATE'"))
                            .isEqualTo("0");
                    assertThat(scalar(independent, "select string_agg(operation || ':' || field || ':' || before_value || ':' || after_value, ',' order by id) "
                            + "from demandflow.catalog_audit_event where operation<>'CREATE'"))
                            .isEqualTo("DEACTIVATE:active:true:false,REACTIVATE:active:false:true");
                    assertThat(scalar(independent, "select version from demandflow.tariff_period")).isEqualTo("2");
                }
            }
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }

    private static void assertRateStates(int port, Secrets secrets, String availableCount) throws Exception {
        try (var independent = connect(port, "demandflow_app", secrets.runtime)) {
            assertThat(scalar(independent, "select string_agg(type_id || ':' || amount_cop || ':' || active || ':' || version, ',' order by type_id) "
                    + "from demandflow.hourly_rate")).isEqualTo("1:12.30:true:0,2:45.60:false:0");
            assertThat(scalar(independent, "select count(*) from demandflow.hourly_rate r join demandflow.tariff_period p on p.id=r.period_id "
                    + "where r.active and p.active")).isEqualTo(availableCount);
        }
    }

    @Test
    void ratesRequireFixedExistingReferencesActivePeriodExactAmountAndGlobalUniqueness() throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            try (var context = application(port, secrets)) {
                var periods = context.getBean(TariffPeriodService.class);
                var rates = context.getBean(HourlyRateService.class);
                var period = periods.create("Tarifas", date("2027-01-01"), date("2027-12-31"));
                assertThat(rates.list()).isEmpty();
                assertThat(rates.listTypes()).extracting(HourlyRateService.TypeView::name).containsExactly("Soporte", "Feature");
                assertThrows(IllegalArgumentException.class, () -> rates.create(null, (short) 1, new BigDecimal("1.00")));
                assertThrows(IllegalArgumentException.class, () -> rates.create(0L, (short) 1, new BigDecimal("1.00")));
                assertThrows(EntityNotFoundException.class, () -> rates.create(Long.MAX_VALUE, (short) 1, new BigDecimal("1.00")));
                for (Short type : new Short[]{null, 0, 3, -1, Short.MAX_VALUE}) {
                    assertThrows(IllegalArgumentException.class, () -> rates.create(period.id(), type, new BigDecimal("1.00")));
                }
                for (BigDecimal value : new BigDecimal[]{null, BigDecimal.ZERO, new BigDecimal("-0.01"),
                        new BigDecimal("12.345"), new BigDecimal("12.340"), new BigDecimal("10000000000000.00")}) {
                    assertThrows(IllegalArgumentException.class, () -> rates.create(period.id(), (short) 1, value));
                }
                var support = rates.create(period.id(), (short) 1, new BigDecimal("0.01"));
                var feature = rates.create(period.id(), (short) 2, new BigDecimal("9999999999999.99"));
                assertThat(support.amountCop()).isEqualTo(new BigDecimal("0.01"));
                assertThat(feature.amountCop()).isEqualTo(new BigDecimal("9999999999999.99"));
                assertThat(support.available()).isTrue();
                assertThat(rates.get(support.id())).isEqualTo(support);
                assertThrows(IllegalStateException.class, () -> rates.create(period.id(), (short) 1, new BigDecimal("2.00")));
                // Synthetic inactive fixture to verify global reservation before 5.4 implements the state command.
                try (var runtime = connect(port, "demandflow_app", secrets.runtime);
                     var statement = runtime.prepareStatement("update demandflow.hourly_rate set active=false where id=?")) {
                    statement.setLong(1, support.id());
                    statement.executeUpdate();
                }
                assertThrows(IllegalStateException.class, () -> rates.create(period.id(), (short) 1, new BigDecimal("2.00")));
                var inactiveRate = rates.get(support.id());
                assertThat(inactiveRate.active()).isFalse();
                assertThat(inactiveRate.available()).isFalse();
                assertThat(rates.list()).hasSize(2);
                assertThat(periods.get(period.id()).version()).isZero();
                var inactivePeriod = periods.create("Inactiva", date("2028-01-01"), date("2028-12-31"));
                periods.deactivate(inactivePeriod.id(), inactivePeriod.version());
                for (short type : new short[]{1, 2}) {
                    assertThrows(IllegalStateException.class, () -> rates.create(inactivePeriod.id(), type, new BigDecimal("1.00")));
                }
                // The command accepts no requested state: every creation is active and the period gate is unconditional.
                assertThat(HourlyRateService.class.getMethod("create", Long.class, Short.class, BigDecimal.class).getParameterCount()).isEqualTo(3);
                try (var independent = connect(port, "demandflow_app", secrets.runtime)) {
                    assertThat(scalar(independent, "select count(*) from demandflow.hourly_rate")).isEqualTo("2");
                    assertThat(scalar(independent, "select count(*) from demandflow.catalog_audit_event where entity_type='HOURLY_RATE'"))
                            .isEqualTo("8");
                    assertThat(scalar(independent, "select string_agg(amount_cop::text, ',' order by type_id) from demandflow.hourly_rate"))
                            .isEqualTo("0.01,9999999999999.99");
                }
            }
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }

    @Test
    void correctionsPreserveIdentityAssociationsAndAllStateCombinationsWhileTransitionsStayExplicit() throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            try (var context = application(port, secrets)) {
                var periods = context.getBean(TariffPeriodService.class);
                var rates = context.getBean(HourlyRateService.class);
                for (boolean periodActive : new boolean[]{true, false}) {
                    for (boolean rateActive : new boolean[]{true, false}) {
                        int year = 2027 + (periodActive ? 0 : 2) + (rateActive ? 0 : 1);
                        var period = periods.create("Combinacion", LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
                        var initial = rates.create(period.id(), (short) 1, new BigDecimal("12.30"));
                        if (!rateActive) {
                            rates.deactivate(initial.id(), initial.version());
                        }
                        if (!periodActive) {
                            periods.deactivate(period.id(), period.version());
                        }
                        var before = rates.get(initial.id());
                        var corrected = rates.correctAmount(before.id(), before.version(), new BigDecimal("99.99"));
                        assertThat(corrected.id()).isEqualTo(initial.id());
                        assertThat(corrected.periodId()).isEqualTo(period.id());
                        assertThat(corrected.typeId()).isEqualTo((short) 1);
                        assertThat(corrected.amountCop()).isEqualTo(new BigDecimal("99.99"));
                        assertThat(corrected.active()).isEqualTo(rateActive);
                        assertThat(corrected.periodActive()).isEqualTo(periodActive);
                        assertThat(corrected.available()).isEqualTo(periodActive && rateActive);
                        assertThat(corrected.version()).isEqualTo(before.version() + 1);
                        if (rateActive) {
                            assertThrows(IllegalStateException.class, () -> rates.reactivate(corrected.id(), corrected.version()));
                            var inactive = rates.deactivate(corrected.id(), corrected.version());
                            assertThat(inactive.active()).isFalse();
                            assertThrows(IllegalStateException.class, () -> rates.deactivate(inactive.id(), inactive.version()));
                        } else {
                            assertThrows(IllegalStateException.class, () -> rates.deactivate(corrected.id(), corrected.version()));
                        }
                        var inactive = rates.get(initial.id());
                        if (periodActive) {
                            var reactivated = rates.reactivate(inactive.id(), inactive.version());
                            assertThat(reactivated.active()).isTrue();
                            assertThat(reactivated.available()).isTrue();
                            assertThat(reactivated.amountCop()).isEqualTo(new BigDecimal("99.99"));
                        } else {
                            assertThrows(IllegalStateException.class, () -> rates.reactivate(inactive.id(), inactive.version()));
                            assertThat(rates.get(inactive.id())).isEqualTo(inactive);
                        }
                    }
                }
                // No command/domain mutator accepts new associations; forged HTTP fields are covered in group 8.
                assertThat(HourlyRateService.class.getMethod("correctAmount", Long.class, Long.class, BigDecimal.class).getParameterCount()).isEqualTo(3);
                assertThat(java.util.Arrays.stream(io.github.alejorom.demandflow.tariffs.domain.HourlyRate.class.getMethods())
                        .map(java.lang.reflect.Method::getName)).doesNotContain("setPeriod", "setType", "setActive");
                try (var independent = connect(port, "demandflow_app", secrets.runtime)) {
                    assertThat(scalar(independent, "select count(*) from demandflow.hourly_rate")).isEqualTo("4");
                    assertThat(scalar(independent, "select count(*) from demandflow.catalog_audit_event "
                            + "where entity_type='HOURLY_RATE' and operation='UPDATE' and field='amount_cop' "
                            + "and before_value='12.30' and after_value='99.99'")).isEqualTo("4");
                    assertThat(scalar(independent, "select count(*) from demandflow.catalog_audit_event "
                            + "where entity_type='HOURLY_RATE' and operation<>'CREATE' and field in ('period_id','type_id')")).isEqualTo("0");
                    CatalogSchemaIT.rejected(independent, "42501", "update demandflow.hourly_rate set type_id=2");
                    CatalogSchemaIT.rejected(independent, "42501", "update demandflow.hourly_rate set period_id=999999");
                }
            }
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }

    @Test
    void normalizedNoOpsAndEveryRejectionFamilyPreserveRowsVersionsAndAuditExactly() throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            try (var context = application(port, secrets)) {
                var periods = context.getBean(TariffPeriodService.class);
                var rates = context.getBean(HourlyRateService.class);
                var initialPeriod = periods.create("Base", date("2027-01-01"), date("2027-12-31"));
                var p = periods.edit(initialPeriod.id(), initialPeriod.version(), "Editada", initialPeriod.startDate(), initialPeriod.endDate());
                var initialRate = rates.create(p.id(), (short) 1, new BigDecimal("10.00"));
                var r = rates.correctAmount(initialRate.id(), initialRate.version(), new BigDecimal("12.30"));
                var other = periods.create("Otra", date("2028-01-01"), date("2028-12-31"));
                var baseline = databaseSnapshot(port, secrets);
                assertThat(periods.edit(p.id(), p.version(), "  Editada  ", p.startDate(), p.endDate())).isEqualTo(p);
                assertThat(rates.correctAmount(r.id(), r.version(), new BigDecimal("12.3"))).isEqualTo(r);
                assertThat(databaseSnapshot(port, secrets)).isEqualTo(baseline);

                for (Long version : new Long[]{null, 0L, -1L}) {
                    for (Runnable command : List.<Runnable>of(
                            () -> periods.edit(p.id(), version, "  Editada  ", p.startDate(), p.endDate()),
                            () -> periods.deactivate(p.id(), version), () -> periods.reactivate(p.id(), version),
                            () -> rates.correctAmount(r.id(), version, new BigDecimal("12.3")),
                            () -> rates.deactivate(r.id(), version), () -> rates.reactivate(r.id(), version))) {
                        rejectedWithoutChanges(port, secrets, OptimisticLockingFailureException.class, command);
                    }
                }
                for (String label : new String[]{null, "", "  ", "x".repeat(101)}) {
                    rejectedWithoutChanges(port, secrets, IllegalArgumentException.class,
                            () -> periods.edit(p.id(), p.version(), label, p.startDate(), p.endDate()));
                }
                for (Runnable command : List.<Runnable>of(
                        () -> periods.edit(p.id(), p.version(), p.label(), null, p.endDate()),
                        () -> periods.edit(p.id(), p.version(), p.label(), p.endDate(), p.startDate()),
                        () -> rates.create(null, (short) 1, new BigDecimal("1.00")),
                        () -> rates.create(p.id(), null, new BigDecimal("1.00")),
                        () -> rates.create(p.id(), (short) 3, new BigDecimal("1.00")))) {
                    rejectedWithoutChanges(port, secrets, IllegalArgumentException.class, command);
                }
                for (BigDecimal value : new BigDecimal[]{null, BigDecimal.ZERO, new BigDecimal("-1"),
                        new BigDecimal("12.345"), new BigDecimal("12.340"), new BigDecimal("10000000000000.00")}) {
                    rejectedWithoutChanges(port, secrets, IllegalArgumentException.class,
                            () -> rates.correctAmount(r.id(), r.version(), value));
                    rejectedWithoutChanges(port, secrets, IllegalArgumentException.class,
                            () -> rates.create(other.id(), (short) 2, value));
                }
                for (Runnable command : List.<Runnable>of(
                        () -> periods.get(Long.MAX_VALUE),
                        () -> periods.edit(Long.MAX_VALUE, 0L, "Ausente", p.startDate(), p.endDate()),
                        () -> rates.get(Long.MAX_VALUE), () -> rates.deactivate(Long.MAX_VALUE, 0L),
                        () -> rates.create(Long.MAX_VALUE, (short) 1, new BigDecimal("1.00")))) {
                    rejectedWithoutChanges(port, secrets, EntityNotFoundException.class, command);
                }
                for (Runnable command : List.<Runnable>of(
                        () -> periods.create("Solapada", p.startDate(), p.endDate()),
                        () -> periods.edit(other.id(), other.version(), "Solapada", p.startDate(), p.endDate()),
                        () -> rates.create(p.id(), (short) 1, new BigDecimal("1.00")),
                        () -> periods.reactivate(p.id(), p.version()), () -> rates.reactivate(r.id(), r.version()))) {
                    rejectedWithoutChanges(port, secrets, IllegalStateException.class, command);
                }
                var inactiveRate = rates.deactivate(r.id(), r.version());
                rejectedWithoutChanges(port, secrets, IllegalStateException.class,
                        () -> rates.deactivate(inactiveRate.id(), inactiveRate.version()));
                rejectedWithoutChanges(port, secrets, IllegalStateException.class,
                        () -> rates.create(p.id(), (short) 1, new BigDecimal("1.00")));
                var inactivePeriod = periods.deactivate(p.id(), p.version());
                rejectedWithoutChanges(port, secrets, IllegalStateException.class,
                        () -> periods.deactivate(inactivePeriod.id(), inactivePeriod.version()));
                rejectedWithoutChanges(port, secrets, IllegalStateException.class,
                        () -> rates.reactivate(inactiveRate.id(), inactiveRate.version()));
                rejectedWithoutChanges(port, secrets, IllegalStateException.class,
                        () -> rates.create(p.id(), (short) 2, new BigDecimal("1.00")));
                var finalBaseline = databaseSnapshot(port, secrets);
                rates.get(r.id()); rates.list(); rates.listTypes(); periods.get(p.id()); periods.list();
                assertThat(databaseSnapshot(port, secrets)).isEqualTo(finalBaseline);
                try (var independent = connect(port, "demandflow_app", secrets.runtime)) {
                    assertThat(scalar(independent, "select before_value || ':' || after_value from demandflow.catalog_audit_event "
                            + "where entity_type='HOURLY_RATE' and operation='UPDATE'"))
                            .isEqualTo("10.00:12.30");
                }
            }
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }

    static List<String> databaseSnapshot(int port, Secrets secrets) throws Exception {
        try (var independent = connect(port, "demandflow_app", secrets.runtime)) {
            var snapshot = new java.util.ArrayList<String>();
            for (String table : List.of("tariff_period", "hourly_rate", "requirement_type", "catalog_audit_event")) {
                // Table names are a fixed test-owned allowlist, never request values.
                snapshot.add(scalar(independent, "select coalesce(json_agg(row_to_json(t) order by id)::text, '[]') from demandflow." + table + " t"));
            }
            return snapshot;
        }
    }

    private static void rejectedWithoutChanges(int port, Secrets secrets, Class<? extends Throwable> failure, Runnable command) throws Exception {
        var before = databaseSnapshot(port, secrets);
        assertThrows(failure, command::run);
        assertThat(databaseSnapshot(port, secrets)).isEqualTo(before);
    }
}
