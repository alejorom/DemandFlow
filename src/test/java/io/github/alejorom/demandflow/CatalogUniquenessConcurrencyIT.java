package io.github.alejorom.demandflow;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;

import io.github.alejorom.demandflow.tariffs.application.HourlyRateService;
import io.github.alejorom.demandflow.tariffs.application.TariffPeriodService;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.alejorom.demandflow.CatalogConcurrencySupport.*;
import static io.github.alejorom.demandflow.PostgresTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/** T2/T4/T9; TP4.S5, HR2.S3, CA3.S3/CA4.S3. */
class CatalogUniquenessConcurrencyIT {
    @TempDir Path temporary;

    @ParameterizedTest
    @CsvSource({"create,true", "create,false", "edit,true", "edit,false"})
    void concurrentOverlappingPeriodsReachExclusionAndOnlyOneCommits(String operation, boolean active) throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            try (var context = application(port, secrets)) {
                var periods = context.getBean(TariffPeriodService.class);
                LocalDate start = LocalDate.of(2028, 1, 1), end = LocalDate.of(2028, 12, 31);
                Race result;
                if (operation.equals("create")) {
                    result = commands(context, port, secrets, () -> {
                        var created = periods.create("Winner", start, end);
                        return active ? created : periods.deactivate(created.id(), created.version());
                    }, () -> periods.create("Loser", start, end));
                } else {
                    var a = periods.create("First", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));
                    var b = periods.create("Second", LocalDate.of(2029, 1, 1), LocalDate.of(2029, 12, 31));
                    if (!active) { a = periods.deactivate(a.id(), a.version()); b = periods.deactivate(b.id(), b.version()); }
                    var first = a; var second = b;
                    result = commands(context, port, secrets,
                            () -> periods.edit(first.id(), first.version(), "Winner", start, end),
                            () -> periods.edit(second.id(), second.version(), "Loser", start, end));
                    assertThat(periods.get(second.id())).isEqualTo(second);
                }
                assertThat(result.first().failure()).isNull();
                assertSqlFailure(result.second().failure(), "23P01", "ex_tariff_period_dates");
                var winner = (TariffPeriodService.PeriodView) result.first().value();
                assertThat(periods.get(winner.id())).isEqualTo(winner);
                assertThat(winner.active()).isEqualTo(active);
                assertThat(winner.version()).isEqualTo(operation.equals("create") ? (active ? 0L : 1L) : (active ? 1L : 2L));
                try (var independent = connect(port, "demandflow_app", secrets.runtime)) {
                    assertThat(scalar(independent, "select count(*) from demandflow.tariff_period where label='Loser'")).isEqualTo("0");
                    assertThat(scalar(independent, "select count(*) from demandflow.tariff_period")).isEqualTo(operation.equals("create") ? "1" : "2");
                    assertThat(scalar(independent, "select count(*) from demandflow.catalog_audit_event where after_value='Loser'")).isEqualTo("0");
                    int events = operation.equals("create") ? (active ? 4 : 5) : (active ? 11 : 13);
                    assertThat(scalar(independent, "select count(*) from demandflow.catalog_audit_event")).isEqualTo(Integer.toString(events));
                    assertThat(scalar(independent, "select count(*) from demandflow.tariff_period a join demandflow.tariff_period b "
                            + "on a.id<b.id and a.start_date<=b.end_date and a.end_date>=b.start_date")).isEqualTo("0");
                }
            }
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void duplicateRateCommandsSerializeAndPreserveActiveOrInactiveWinner(boolean active) throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            try (var context = application(port, secrets)) {
                var periods = context.getBean(TariffPeriodService.class);
                var rates = context.getBean(HourlyRateService.class);
                var period = periods.create("Rates", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));
                var result = commands(context, port, secrets, () -> {
                    var created = rates.create(period.id(), (short) 1, new BigDecimal("12.30"));
                    return active ? created : rates.deactivate(created.id(), created.version());
                }, () -> rates.create(period.id(), (short) 1, new BigDecimal("99.99")));
                assertThat(result.first().failure()).isNull();
                assertThat(result.second().failure()).isInstanceOf(IllegalStateException.class).hasMessageContaining("already exists");
                var winner = (HourlyRateService.RateView) result.first().value();
                assertThat(rates.get(winner.id())).isEqualTo(winner);
                assertThat(winner.active()).isEqualTo(active);
                assertThat(winner.version()).isEqualTo(active ? 0L : 1L);
                assertThat(periods.get(period.id())).isEqualTo(period);
                try (var independent = connect(port, "demandflow_app", secrets.runtime)) {
                    assertThat(scalar(independent, "select count(*) from demandflow.hourly_rate")).isEqualTo("1");
                    assertThat(scalar(independent, "select count(*) from demandflow.catalog_audit_event")).isEqualTo(active ? "8" : "9");
                    assertThat(scalar(independent, "select count(*) from demandflow.catalog_audit_event where after_value='99.99'")).isEqualTo("0");
                }
            }
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void independentJdbcInsertsReachUniqueWithoutApplicationPeriodLock(boolean active) throws Exception {
        var secrets = new Secrets(temporary);
        try (var postgres = postgres(secrets)) {
            postgres.start();
            int port = postgres.getMappedPort(5432);
            try (var context = application(port, secrets)) {
                var period = context.getBean(TariffPeriodService.class).create("JDBC", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));
                var result = race(port, secrets, (pid, ready, release) -> {
                    try (var runtime = connect(port, "demandflow_app", secrets.runtime)) {
                        runtime.setAutoCommit(false);
                        pid.set(backendPid(runtime));
                        insert(runtime, period.id(), active, "12.30");
                        ready.countDown(); gate(release);
                        runtime.commit();
                        return true;
                    }
                }, pid -> {
                    try (var runtime = connect(port, "demandflow_app", secrets.runtime)) {
                        runtime.setAutoCommit(false);
                        pid.set(backendPid(runtime));
                        try { insert(runtime, period.id(), !active, "99.99"); runtime.commit(); return true; }
                        catch (SQLException failure) { runtime.rollback(); throw failure; }
                    }
                });
                assertThat(result.first().failure()).isNull();
                assertSqlFailure(result.second().failure(), "23505", "uq_hourly_rate_period_type");
                try (var independent = connect(port, "demandflow_app", secrets.runtime)) {
                    assertThat(scalar(independent, "select count(*) from demandflow.hourly_rate")).isEqualTo("1");
                    assertThat(scalar(independent, "select amount_cop || ':' || active || ':' || version from demandflow.hourly_rate"))
                            .isEqualTo("12.30:" + active + ":0");
                    // Direct JDBC here proves only physical UNIQUE; application audit is verified in the command races above.
                    assertThat(scalar(independent, "select count(*) from demandflow.catalog_audit_event where entity_type='HOURLY_RATE'"))
                            .isEqualTo("0");
                }
            }
            secrets.assertNotLeaked(postgres.getLogs());
        }
    }

    private static void insert(Connection connection, Long periodId, boolean active, String amount) throws Exception {
        try (var statement = connection.prepareStatement("insert into demandflow.hourly_rate (period_id,type_id,amount_cop,active,version) values (?,1,?,?,0)")) {
            statement.setLong(1, periodId); statement.setBigDecimal(2, new BigDecimal(amount)); statement.setBoolean(3, active);
            statement.executeUpdate();
        }
    }

    static void assertSqlFailure(Throwable failure, String state, String constraint) {
        assertThat(failure).isNotNull();
        Throwable sql = failure;
        while (!(sql instanceof SQLException) && sql.getCause() != null) { sql = sql.getCause(); }
        assertThat(sql).isInstanceOf(SQLException.class).hasMessageContaining(constraint);
        assertThat(((SQLException) sql).getSQLState()).isEqualTo(state);
    }
}
